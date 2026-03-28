package com.logistics.backend.furniture.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class WeworkAibotMessageParser {

    private final ObjectMapper objectMapper;

    public WeworkAibotMessageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AibotInboundPayload parse(String decryptedJson) throws Exception {
        JsonNode root = objectMapper.readTree(decryptedJson);
        String msgid = text(root, "msgid");
        String msgtype = text(root, "msgtype");
        String responseUrl = text(root, "response_url");
        String userId = root.path("from").path("userid").asText("");
        Optional<String> chatId = Optional.ofNullable(text(root, "chatid")).filter(s -> !s.isBlank());
        String chattype = root.path("chattype").asText("");
        Optional<String> userText = extractUserText(root);
        return new AibotInboundPayload(msgid, msgtype, responseUrl, userId, chatId, chattype, userText);
    }

    Optional<String> extractUserText(JsonNode root) {
        String msgtype = root.path("msgtype").asText("");
        return switch (msgtype) {
            case "text" -> Optional.ofNullable(text(root.path("text"), "content")).filter(s -> !s.isBlank());
            case "voice" -> Optional.ofNullable(text(root.path("voice"), "content")).filter(s -> !s.isBlank());
            case "mixed" -> extractMixedText(root.path("mixed"));
            default -> Optional.empty();
        };
    }

    private Optional<String> extractMixedText(JsonNode mixed) {
        List<String> parts = new ArrayList<>();
        for (JsonNode item : mixed.path("msg_item")) {
            if (!"text".equals(item.path("msgtype").asText())) {
                continue;
            }
            String c = text(item.path("text"), "content");
            if (c != null && !c.isBlank()) {
                parts.add(c);
            }
        }
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(String.join("\n", parts));
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String v = node.path(field).asText(null);
        return v;
    }
}
