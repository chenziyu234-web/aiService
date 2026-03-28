package com.logistics.backend.furniture.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.backend.furniture.config.WeworkAibotProperties;
import com.logistics.backend.furniture.service.WeworkAibotService;
import com.logistics.backend.furniture.wecom.WeworkAibotCryptoException;
import com.logistics.backend.furniture.wecom.WeworkAibotCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/callback/wework/aibot")
public class WeworkAibotController {

    private static final Logger log = LoggerFactory.getLogger(WeworkAibotController.class);

    private final WeworkAibotProperties props;
    private final WeworkAibotCryptoService crypto;
    private final WeworkAibotService aibotService;
    private final ObjectMapper objectMapper;

    public WeworkAibotController(
            WeworkAibotProperties props,
            WeworkAibotCryptoService crypto,
            WeworkAibotService aibotService,
            ObjectMapper objectMapper) {
        this.props = props;
        this.crypto = crypto;
        this.aibotService = aibotService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestParam String echostr) throws WeworkAibotCryptoException {
        if (!props.isConfigured() || !crypto.isReady()) {
            return ResponseEntity.status(503).body("wework aibot not configured");
        }
        String plain = crypto.verifyUrl(msgSignature, timestamp, nonce, echostr);
        return ResponseEntity.ok(plain);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> callback(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestBody String body) {
        if (!props.isConfigured() || !crypto.isReady()) {
            return ResponseEntity.status(503).build();
        }
        try {
            String decrypted = crypto.decryptCallbackJson(msgSignature, timestamp, nonce, body);
            if (isStreamMessage(decrypted)) {
                return ResponseEntity.ok(crypto.buildEmptyEncryptedResponse(timestamp, nonce));
            }
            String msgid = extractMsgId(decrypted);
            if (msgid != null && aibotService.registerMsgIdOnce(msgid)) {
                return ResponseEntity.ok(crypto.buildEmptyEncryptedResponse(timestamp, nonce));
            }
            aibotService.handleInboundAsync(decrypted);
            return ResponseEntity.ok(crypto.buildEmptyEncryptedResponse(timestamp, nonce));
        } catch (WeworkAibotCryptoException e) {
            log.warn("wework aibot decrypt failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("{\"error\":\"decrypt\"}");
        } catch (Exception e) {
            log.error("wework aibot callback error", e);
            return ResponseEntity.internalServerError().body("{\"error\":\"internal\"}");
        }
    }

    private boolean isStreamMessage(String decryptedJson) {
        try {
            return "stream".equals(objectMapper.readTree(decryptedJson).path("msgtype").asText());
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private String extractMsgId(String decryptedJson) {
        try {
            JsonNode n = objectMapper.readTree(decryptedJson).path("msgid");
            if (n.isMissingNode() || n.isNull()) {
                return null;
            }
            String v = n.asText();
            return v.isBlank() ? null : v;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
