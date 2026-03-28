package com.logistics.backend.furniture.wecom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.backend.furniture.config.WeworkAibotProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

/**
 * 企业自建智能机器人：receiveid 空串；加解密见 {@link WeworkCallbackCryptography}（与官方方案一致，无第三方 qq 包）。
 */
@Service
public class WeworkAibotCryptoService {

    private static final String EMPTY_JSON = "{}";

    private final WeworkAibotProperties props;
    private final ObjectMapper objectMapper;

    private volatile WeworkCallbackCryptography crypt;

    public WeworkAibotCryptoService(WeworkAibotProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        if (!props.isConfigured()) {
            return;
        }
        crypt = new WeworkCallbackCryptography(props.token(), props.aesKey(), "");
    }

    public boolean isReady() {
        return crypt != null;
    }

    public String verifyUrl(String msgSignature, String timestamp, String nonce, String echoStr)
            throws WeworkAibotCryptoException {
        ensureReady();
        return crypt.verifyUrl(msgSignature, timestamp, nonce, echoStr);
    }

    public String decryptCallbackJson(String msgSignature, String timestamp, String nonce, String jsonBody)
            throws WeworkAibotCryptoException, JsonProcessingException {
        ensureReady();
        JsonNode root = objectMapper.readTree(jsonBody);
        String enc = root.path("encrypt").asText(null);
        if (enc == null || enc.isBlank()) {
            throw new IllegalArgumentException("missing encrypt field");
        }
        return crypt.decryptEncryptField(msgSignature, timestamp, nonce, enc);
    }

    public String buildEncryptedResponse(String timestamp, String nonce, String plaintextJson)
            throws WeworkAibotCryptoException {
        ensureReady();
        WeworkCallbackCryptography.EncryptedPassivePacket p =
                crypt.encryptPassivePlaintext(plaintextJson, timestamp, nonce);
        return objectMapper.createObjectNode()
                .put("encrypt", p.encrypt())
                .put("msgsignature", p.msgSignature())
                .put("timestamp", p.timestamp())
                .put("nonce", p.nonce())
                .toString();
    }

    public String buildEmptyEncryptedResponse(String timestamp, String nonce) throws WeworkAibotCryptoException {
        return buildEncryptedResponse(timestamp, nonce, EMPTY_JSON);
    }

    private void ensureReady() {
        if (crypt == null) {
            throw new IllegalStateException("wework.aibot not configured (token/aes-key)");
        }
    }
}
