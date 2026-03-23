package com.logistics.backend.furniture.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.backend.furniture.model.AddressResult;
import com.logistics.backend.furniture.model.SurchargeResult;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 封装业务方提供的两个外部接口：
 * 接口1 - 超区位置点查询（地址 → 经纬度）
 * 接口2 - 超区费查询（经纬度 → 费用）
 * 当前使用 mock 实现，待业务方提供真实接口后切换。
 */
@Service
public class AddressService {

    @Value("${furniture.address-api.url:}")
    private String addressApiUrl;

    @Value("${furniture.surcharge-api.url:}")
    private String surchargeApiUrl;

    @Value("${furniture.api.mock:true}")
    private boolean mockMode;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    /**
     * 接口1：将客户地址解析为经纬度坐标
     */
    public AddressResult resolveAddress(String address) {
        if (mockMode) {
            return mockResolveAddress(address);
        }
        return callAddressApi(address);
    }

    /**
     * 接口2：根据经纬度查询超区费
     */
    public SurchargeResult querySurcharge(double longitude, double latitude) {
        if (mockMode) {
            return mockQuerySurcharge(longitude, latitude);
        }
        return callSurchargeApi(longitude, latitude);
    }

    // ========== Mock 实现（演示用，业务方接口就绪后切换 mock=false） ==========

    private AddressResult mockResolveAddress(String address) {
        if (address == null || address.trim().length() < 8) {
            return AddressResult.fail("地址信息不够详细，请提供完整的省市区+街道/小区名称");
        }

        boolean hasProvince = address.matches(".*?(省|自治区|北京|上海|天津|重庆).*");
        boolean hasCity = address.matches(".*?(市|州|盟).*");
        boolean hasDistrict = address.matches(".*?(区|县|旗).*");

        if (!hasProvince && !hasCity) {
            return AddressResult.fail("请补充省份和城市信息，例如：广东省深圳市...");
        }
        if (!hasDistrict) {
            return AddressResult.fail("请补充区/县信息，例如：南山区、番禺区...");
        }

        double baseLng = 113.9 + (address.hashCode() % 100) * 0.01;
        double baseLat = 22.5 + (address.hashCode() % 80) * 0.01;

        return AddressResult.success(baseLng, baseLat, address);
    }

    private SurchargeResult mockQuerySurcharge(double longitude, double latitude) {
        double distanceFactor = Math.abs(longitude - 113.94) + Math.abs(latitude - 22.54);

        if (distanceFactor < 0.3) {
            return SurchargeResult.inRange();
        } else if (distanceFactor < 0.6) {
            double amount = Math.round(distanceFactor * 200) / 1.0;
            return SurchargeResult.surcharge(
                    Math.max(50, amount),
                    "超出免费配送范围" + String.format("%.0f", distanceFactor * 50) + "公里"
            );
        } else {
            double amount = Math.round(distanceFactor * 300) / 1.0;
            return SurchargeResult.surcharge(
                    Math.max(150, amount),
                    "属于偏远配送区域，距配送中心" + String.format("%.0f", distanceFactor * 60) + "公里"
            );
        }
    }

    // ========== 真实接口调用（业务方提供接口文档后完善） ==========

    private AddressResult callAddressApi(String address) {
        try {
            String json = objectMapper.writeValueAsString(java.util.Map.of("address", address));
            Request request = new Request.Builder()
                    .url(addressApiUrl)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return AddressResult.fail("地址解析服务暂时不可用");
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                boolean success = root.path("success").asBoolean(false);
                if (success) {
                    return AddressResult.success(
                            root.path("longitude").asDouble(),
                            root.path("latitude").asDouble(),
                            root.path("matchedAddress").asText(address)
                    );
                } else {
                    return AddressResult.fail(root.path("suggestion").asText("地址无法识别"));
                }
            }
        } catch (IOException e) {
            return AddressResult.fail("地址解析服务连接失败");
        }
    }

    private SurchargeResult callSurchargeApi(double longitude, double latitude) {
        try {
            String json = objectMapper.writeValueAsString(
                    java.util.Map.of("longitude", longitude, "latitude", latitude));
            Request request = new Request.Builder()
                    .url(surchargeApiUrl)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return SurchargeResult.inRange();
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                boolean outOfRange = root.path("outOfRange").asBoolean(false);
                if (outOfRange) {
                    return SurchargeResult.surcharge(
                            root.path("amount").asDouble(),
                            root.path("ruleDescription").asText("")
                    );
                } else {
                    return SurchargeResult.inRange();
                }
            }
        } catch (IOException e) {
            return SurchargeResult.inRange();
        }
    }
}
