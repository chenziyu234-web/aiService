package com.logistics.backend.furniture.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wework.aibot")
public record WeworkAibotProperties(String token, String aesKey, String corpId) {

    public boolean isConfigured() {
        return token != null && !token.isBlank()
                && aesKey != null && !aesKey.isBlank();
    }
}
