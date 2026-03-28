package com.logistics.backend.furniture.controller;

import com.logistics.backend.furniture.config.WeworkAibotProperties;
import com.logistics.backend.furniture.service.WeworkAibotService;
import com.logistics.backend.furniture.wecom.WeworkAibotCryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WeworkAibotController.class)
@EnableConfigurationProperties(WeworkAibotProperties.class)
@TestPropertySource(
        properties = {
                "wework.aibot.token=test-token",
                "wework.aibot.aes-key=abcdefghijklmnopqrstuvwxyz0123456789012345678901",
                "wework.aibot.corp-id=test-corp"
        })
class WeworkAibotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WeworkAibotCryptoService crypto;

    @MockBean
    private WeworkAibotService aibotService;

    @Test
    void getVerifyReturnsPlainEcho() throws Exception {
        when(crypto.isReady()).thenReturn(true);
        when(crypto.verifyUrl(anyString(), anyString(), anyString(), anyString())).thenReturn("plain-echo");

        mockMvc.perform(get("/callback/wework/aibot")
                        .param("msg_signature", "sig")
                        .param("timestamp", "1")
                        .param("nonce", "n")
                        .param("echostr", "enc"))
                .andExpect(status().isOk())
                .andExpect(content().string("plain-echo"));
    }
}
