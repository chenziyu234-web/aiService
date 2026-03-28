package com.logistics.backend.furniture.wecom;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 主动回复：POST response_url，明文 JSON（官方 主动回复消息）。
 */
@Component
public class WeworkAibotReplyClient {

    private static final Logger log = LoggerFactory.getLogger(WeworkAibotReplyClient.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper;

    public WeworkAibotReplyClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void sendMarkdown(String responseUrl, String markdownContent) throws IOException {
        if (responseUrl == null || responseUrl.isBlank()) {
            return;
        }
        String body = objectMapper.writeValueAsString(Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("content", markdownContent)
        ));
        Request request = new Request.Builder()
                .url(responseUrl)
                .post(RequestBody.create(body, JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                log.warn("wework aibot response_url failed code={} body={}", response.code(), err);
            }
        }
    }
}
