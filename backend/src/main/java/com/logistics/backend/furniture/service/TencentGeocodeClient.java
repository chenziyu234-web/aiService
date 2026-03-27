package com.logistics.backend.furniture.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.backend.furniture.model.AddressResult;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 腾讯位置服务 WebService 地理编码客户端。
 * GET https://apis.map.qq.com/ws/geocoder/v1/?address=...&key=...
 */
@Component
public class TencentGeocodeClient {

    private static final Logger log = LoggerFactory.getLogger(TencentGeocodeClient.class);
    private static final String GEOCODE_URL = "https://apis.map.qq.com/ws/geocoder/v1/";

    @Value("${furniture.tencent-lbs.key:}")
    private String apiKey;

    @Value("${furniture.tencent-lbs.region:}")
    private String defaultRegion;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public AddressResult geocode(String address) {
        if (apiKey == null || apiKey.isBlank()) {
            return AddressResult.fail("地址解析服务未配置（缺少腾讯 LBS Key）");
        }

        try {
            String url = buildUrl(address);
            Request request = new Request.Builder().url(url).get().build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("腾讯地理编码 HTTP 失败: status={}", response.code());
                    return AddressResult.fail("地址解析服务暂时不可用");
                }

                String body = response.body().string();
                return parseResponse(body, address);
            }
        } catch (Exception e) {
            log.error("腾讯地理编码调用异常", e);
            return AddressResult.fail("地址解析服务连接失败");
        }
    }

    private String buildUrl(String address) {
        StringBuilder sb = new StringBuilder(GEOCODE_URL);
        sb.append("?address=").append(URLEncoder.encode(address, StandardCharsets.UTF_8));
        sb.append("&key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        sb.append("&output=json");
        if (defaultRegion != null && !defaultRegion.isBlank()) {
            sb.append("&region=").append(URLEncoder.encode(defaultRegion, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private AddressResult parseResponse(String body, String originalAddress) {
        try {
            JsonNode root = objectMapper.readTree(body);
            int status = root.path("status").asInt(-1);

            if (status != 0) {
                String message = root.path("message").asText("地址无法识别");
                log.warn("腾讯地理编码业务失败: status={}, message={}", status, message);
                return AddressResult.fail(message);
            }

            JsonNode result = root.path("result");
            JsonNode location = result.path("location");
            double lat = location.path("lat").asDouble(0);
            double lng = location.path("lng").asDouble(0);

            if (lat == 0 && lng == 0) {
                return AddressResult.fail("地址解析结果异常，请检查地址是否正确");
            }

            String matchedAddress = buildMatchedAddress(result, originalAddress);
            return AddressResult.success(lng, lat, matchedAddress);
        } catch (Exception e) {
            log.error("腾讯地理编码响应解析失败", e);
            return AddressResult.fail("地址解析结果解析失败");
        }
    }

    /**
     * 从 address_components 拼接标准化地址；拼不出来则回退到原始输入。
     */
    private String buildMatchedAddress(JsonNode result, String fallback) {
        JsonNode ac = result.path("address_components");
        if (ac.isMissingNode()) {
            return fallback;
        }
        String province = ac.path("province").asText("");
        String city = ac.path("city").asText("");
        String district = ac.path("district").asText("");
        String street = ac.path("street").asText("");
        String streetNumber = ac.path("street_number").asText("");

        String assembled = province + city + district + street + streetNumber;
        return assembled.isBlank() ? fallback : assembled;
    }
}
