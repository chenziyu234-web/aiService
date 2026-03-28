package com.logistics.backend.furniture.wecom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeworkCallbackCryptographyTest {

    /** 43 字符 EncodingAESKey（Base64 解码为 32 字节，仅用于单测） */
    private static final String KEY_43 = "abcdefghijklmnopqrstuvwxyz01234567890123456";

    @Test
    void passiveEncryptThenDecryptWithMatchingSignature() throws Exception {
        var c = new WeworkCallbackCryptography("test-token", KEY_43, "");
        String ts = "1700000000";
        String nonce = "nonce1";
        WeworkCallbackCryptography.EncryptedPassivePacket p = c.encryptPassivePlaintext("{}", ts, nonce);
        String plain = c.decryptEncryptField(p.msgSignature(), ts, nonce, p.encrypt());
        assertEquals("{}", plain);
    }
}
