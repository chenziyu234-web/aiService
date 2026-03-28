package com.logistics.backend.furniture.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeworkAibotMessageParserTest {

    private WeworkAibotMessageParser parser;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        parser = new WeworkAibotMessageParser(objectMapper);
    }

    @Test
    void extractsTextFromTextMsgtype() throws Exception {
        String json = """
                {"msgtype":"text","text":{"content":"@RobotA hello"}}""";
        JsonNode root = objectMapper.readTree(json);
        Optional<String> t = parser.extractUserText(root);
        assertTrue(t.isPresent());
        assertEquals("@RobotA hello", t.get());
    }

    @Test
    void parsesFullPayload() throws Exception {
        String json = """
                {
                  "msgid": "mid1",
                  "msgtype": "text",
                  "response_url": "https://qyapi.weixin.qq.com/cgi-bin/aibot/response?x=1",
                  "from": {"userid": "u1"},
                  "chatid": "c1",
                  "chattype": "group",
                  "text": {"content": "hello"}
                }
                """;
        AibotInboundPayload p = parser.parse(json);
        assertEquals("mid1", p.msgid());
        assertEquals("text", p.msgtype());
        assertEquals("u1", p.userId());
        assertTrue(p.userText().isPresent());
        assertEquals("hello", p.userText().get());
    }
}
