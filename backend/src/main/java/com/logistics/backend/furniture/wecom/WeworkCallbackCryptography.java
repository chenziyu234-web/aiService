package com.logistics.backend.furniture.wecom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 企业微信「回调和回复的加解密方案」Java 实现（JDK 自带加密，不依赖第三方 SDK）。
 * <p>明文层 PKCS#7 按官方说明填充至 <b>32 字节</b> 的倍数（与 JCE 默认 AES 块 16 不同，不能用 PKCS5Padding 直接套在整段密文上）。
 * 算法：AES-256-CBC + IV=密钥前 16 字节 + NoPadding，与官方示例一致。
 * 智能机器人自建场景 receiveid 为空串。
 */
public final class WeworkCallbackCryptography {

    private static final String AES_CBC_NO_PADDING = "AES/CBC/NoPadding";
    /** 企业微信文档：PKCS#7 填充至 32 字节的倍数 */
    private static final int PKCS7_BLOCK = 32;

    private final String token;
    private final String receiveId;
    private final byte[] aesKey32;
    private final SecureRandom secureRandom = new SecureRandom();

    public WeworkCallbackCryptography(String token, String encodingAesKey, String receiveId) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token required");
        }
        if (encodingAesKey == null || encodingAesKey.isBlank()) {
            throw new IllegalArgumentException("encodingAesKey required");
        }
        this.token = token;
        this.receiveId = receiveId == null ? "" : receiveId;
        this.aesKey32 = decodeEncodingAesKey(encodingAesKey);
    }

    private static byte[] decodeEncodingAesKey(String encodingAesKey) {
        try {
            byte[] key = Base64.getDecoder().decode(encodingAesKey.trim() + "=");
            if (key.length != 32) {
                throw new IllegalArgumentException("EncodingAESKey must decode to 32 bytes, got " + key.length);
            }
            return key;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid EncodingAESKey (43-char Base64)", e);
        }
    }

    /** GET 校验：验签并解密 echostr，返回明文（即要回显的字符串）。 */
    public String verifyUrl(String msgSignature, String timestamp, String nonce, String echoStr)
            throws WeworkAibotCryptoException {
        if (!msgSignature.equalsIgnoreCase(sha1Signature(token, timestamp, nonce, echoStr))) {
            throw new WeworkAibotCryptoException("invalid msg_signature (url verify)");
        }
        return decryptToMsgUtf8(echoStr);
    }

    /** POST：验签后解密 encrypt 字段，得到明文 JSON。 */
    public String decryptEncryptField(String msgSignature, String timestamp, String nonce, String encryptBase64)
            throws WeworkAibotCryptoException {
        if (!msgSignature.equalsIgnoreCase(sha1Signature(token, timestamp, nonce, encryptBase64))) {
            throw new WeworkAibotCryptoException("invalid msg_signature (post body)");
        }
        return decryptToMsgUtf8(encryptBase64);
    }

    /**
     * 构造被动回复密文包中的 encrypt，及对应 msgsignature（timestamp/nonce 使用本次回调 URL 上的值）。
     */
    public EncryptedPassivePacket encryptPassivePlaintext(String plaintextUtf8, String timestamp, String nonce)
            throws WeworkAibotCryptoException {
        try {
            byte[] random = new byte[16];
            secureRandom.nextBytes(random);
            byte[] msg = plaintextUtf8.getBytes(StandardCharsets.UTF_8);
            byte[] rid = receiveId.getBytes(StandardCharsets.UTF_8);
            ByteBuffer inner = ByteBuffer.allocate(16 + 4 + msg.length + rid.length);
            inner.order(ByteOrder.BIG_ENDIAN);
            inner.put(random);
            inner.putInt(msg.length);
            inner.put(msg);
            inner.put(rid);

            byte[] padded = pkcs7Encode(inner.array(), PKCS7_BLOCK);
            byte[] cipher = aesCbcNoPadding(true, padded);
            String encryptB64 = Base64.getEncoder().encodeToString(cipher);
            String sig = sha1Signature(token, timestamp, nonce, encryptB64);
            long ts = Long.parseLong(timestamp.trim());
            return new EncryptedPassivePacket(encryptB64, sig, ts, nonce);
        } catch (NumberFormatException e) {
            throw new WeworkAibotCryptoException("invalid timestamp", e);
        } catch (Exception e) {
            throw new WeworkAibotCryptoException("encrypt passive reply failed", e);
        }
    }

    public record EncryptedPassivePacket(String encrypt, String msgSignature, long timestamp, String nonce) {}

    private String decryptToMsgUtf8(String encryptBase64) throws WeworkAibotCryptoException {
        try {
            byte[] cipher = Base64.getDecoder().decode(encryptBase64.trim());
            byte[] decrypted = aesCbcNoPadding(false, cipher);
            byte[] plain = pkcs7Decode(decrypted, PKCS7_BLOCK);
            return parseInnerPlain(plain);
        } catch (WeworkAibotCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new WeworkAibotCryptoException("decrypt failed", e);
        }
    }

    private String parseInnerPlain(byte[] decrypted) throws WeworkAibotCryptoException {
        if (decrypted.length < 16 + 4) {
            throw new WeworkAibotCryptoException("decrypted packet too short");
        }
        ByteBuffer buf = ByteBuffer.wrap(decrypted);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.position(16);
        int msgLen = buf.getInt();
        if (msgLen < 0 || msgLen > buf.remaining()) {
            throw new WeworkAibotCryptoException("invalid msg length: " + msgLen);
        }
        byte[] msg = new byte[msgLen];
        buf.get(msg);
        byte[] tail = new byte[buf.remaining()];
        buf.get(tail);
        String tailStr = new String(tail, StandardCharsets.UTF_8);
        if (!tailStr.equals(receiveId)) {
            throw new WeworkAibotCryptoException("receiveid mismatch");
        }
        return new String(msg, StandardCharsets.UTF_8);
    }

    private byte[] aesCbcNoPadding(boolean encrypt, byte[] input) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(aesKey32, "AES");
        IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey32, 0, 16));
        Cipher cipher = Cipher.getInstance(AES_CBC_NO_PADDING);
        cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, keySpec, iv);
        return cipher.doFinal(input);
    }

    /** PKCS#7，blockSize 为企业微信约定的 32 */
    private static byte[] pkcs7Encode(byte[] src, int blockSize) {
        int pad = blockSize - (src.length % blockSize);
        if (pad == 0) {
            pad = blockSize;
        }
        byte[] out = Arrays.copyOf(src, src.length + pad);
        Arrays.fill(out, src.length, out.length, (byte) pad);
        return out;
    }

    private static byte[] pkcs7Decode(byte[] decrypted, int blockSize) throws WeworkAibotCryptoException {
        if (decrypted.length == 0 || decrypted.length % blockSize != 0) {
            throw new WeworkAibotCryptoException("pkcs7: length not multiple of " + blockSize);
        }
        int pad = decrypted[decrypted.length - 1] & 0xff;
        if (pad < 1 || pad > blockSize) {
            throw new WeworkAibotCryptoException("pkcs7: bad padding length " + pad);
        }
        for (int i = decrypted.length - pad; i < decrypted.length; i++) {
            if ((decrypted[i] & 0xff) != pad) {
                throw new WeworkAibotCryptoException("pkcs7: inconsistent padding");
            }
        }
        return Arrays.copyOfRange(decrypted, 0, decrypted.length - pad);
    }

    private static String sha1Signature(String token, String timestamp, String nonce, String encrypt) {
        String[] arr = {token, timestamp, nonce, encrypt};
        Arrays.sort(arr);
        String joined = String.join("", arr);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
