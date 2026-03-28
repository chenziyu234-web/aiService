package com.logistics.backend.furniture.service;

import com.logistics.backend.furniture.wecom.AibotInboundPayload;
import com.logistics.backend.furniture.wecom.WeworkAibotMessageParser;
import com.logistics.backend.furniture.wecom.WeworkAibotReplyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WeworkAibotService {

    private static final Logger log = LoggerFactory.getLogger(WeworkAibotService.class);

    private final FurnitureChatService furnitureChatService;
    private final WeworkAibotMessageParser parser;
    private final WeworkAibotReplyClient replyClient;
    private final Set<String> seenMsgIds = ConcurrentHashMap.newKeySet();

    public WeworkAibotService(
            FurnitureChatService furnitureChatService,
            WeworkAibotMessageParser parser,
            WeworkAibotReplyClient replyClient) {
        this.furnitureChatService = furnitureChatService;
        this.parser = parser;
        this.replyClient = replyClient;
    }

    /**
     * @return true if this msgid was already handled (duplicate callback)
     */
    public boolean registerMsgIdOnce(String msgid) {
        if (msgid == null || msgid.isBlank()) {
            return false;
        }
        return !seenMsgIds.add(msgid);
    }

    @Async
    public void handleInboundAsync(String decryptedJson) {
        try {
            AibotInboundPayload p = parser.parse(decryptedJson);
            if ("stream".equals(p.msgtype())) {
                return;
            }
            if (p.responseUrl() == null || p.responseUrl().isBlank()) {
                log.debug("wework aibot: no response_url, skip reply");
                return;
            }
            if (p.userText().isEmpty()) {
                replyClient.sendMarkdown(p.responseUrl(), "暂不支持该消息类型，请发送文字说明。");
                return;
            }
            String sessionId = "wework:" + (p.userId() == null || p.userId().isBlank() ? "anon" : p.userId());
            String answer = furnitureChatService.chat(sessionId, p.userText().get());
            replyClient.sendMarkdown(p.responseUrl(), answer);
        } catch (Exception e) {
            log.error("wework aibot async handle failed", e);
        }
    }
}
