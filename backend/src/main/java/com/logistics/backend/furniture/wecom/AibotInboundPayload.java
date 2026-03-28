package com.logistics.backend.furniture.wecom;

import java.util.Optional;

/**
 * Decrypted smart-bot callback JSON (see 智能机器人 · 接收消息).
 */
public record AibotInboundPayload(
        String msgid,
        String msgtype,
        String responseUrl,
        String userId,
        Optional<String> chatId,
        String chattype,
        Optional<String> userText
) {}
