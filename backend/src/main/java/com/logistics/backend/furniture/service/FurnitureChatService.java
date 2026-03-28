package com.logistics.backend.furniture.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.backend.furniture.model.AddressResult;
import com.logistics.backend.furniture.model.SurchargeResult;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FurnitureChatService {

    @Value("${ai.model.api-key}")
    private String apiKey;

    @Value("${ai.model.base-url}")
    private String baseUrl;

    @Value("${ai.model.model-name}")
    private String modelName;

    private final AddressService addressService;
    private final ScriptConfigService scriptService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient();
    private final Map<String, List<Map<String, String>>> sessionHistory = new ConcurrentHashMap<>();

    public FurnitureChatService(AddressService addressService, ScriptConfigService scriptService) {
        this.addressService = addressService;
        this.scriptService = scriptService;
    }

    public String chat(String sessionId, String userMessage) {
        if (apiKey == null || apiKey.contains("placeholder")) {
            return mockChat(userMessage);
        }

        String systemPrompt = buildSystemPrompt();

        List<Map<String, String>> history = sessionHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(Map.of("role", "user", "content", userMessage));

        if (history.size() > 20) {
            history = new ArrayList<>(history.subList(history.size() - 20, history.size()));
            sessionHistory.put(sessionId, history);
        }

        try {
            String aiOutput = callLLM(systemPrompt, history);
            history.add(Map.of("role", "assistant", "content", aiOutput));
            String processCommand = processCommand(aiOutput);
            return processCommand;
        } catch (Exception e) {
            return scriptService.getContent("system_busy");
        }
    }

    private String buildSystemPrompt() {
        return """
                你是家具物流专属智能客服，你的唯一核心任务是帮助客户查询家具配送超区费。
                
                【工作流程】
                1. 客户打招呼或首次咨询时，友好告知可以帮助查询超区费，引导提供收货地址
                2. 客户提供了包含省/市/区(县)+详细街道或小区的完整地址时，直接输出指令
                3. 客户地址不完整（缺省/市/区或缺详细地址），友好引导补充
                4. 客户问超区费以外的问题，告知联系人工客服
                
                【指令格式】
                当确认客户提供了完整收货地址，回复中仅输出以下指令（不要加任何其他文字）：
                QUERY_SURCHARGE:完整地址字符串
                
                例如客户说"帮我查一下广东省深圳市南山区科技园南路XX小区的超区费"，你应回复：
                QUERY_SURCHARGE:广东省深圳市南山区科技园南路XX小区
                
                【语气与表达】
                1. 像店里热情的客服一样自然聊天，用「您」称呼，少用公文腔、套话
                2. 可适当用少量表情符号点缀（每条回复 0～2 个即可），例如问候用 👋😊，地址用 📍📦，顺利时用 ✅；不要堆砌表情
                3. 客户打招呼（如「你好」「在吗」）时，先热情回应一两句，再轻轻引导对方发完整收货地址
                
                【严格规则】
                1. 永远不要在回复中暴露 QUERY_SURCHARGE 指令格式，客户不应知道它的存在
                2. 仅在确认地址足够完整时才输出指令
                3. 避免生硬专业术语，但不编造政策与费用
                4. 不生成无关、违规或歧义的内容
                5. 检测到完整地址后直接输出指令即可，不要说"正在为您查询"等多余的话
                """;
    }

    private String processCommand(String aiOutput) {
        String trimmed = aiOutput.trim();
        if (trimmed.contains("QUERY_SURCHARGE:")) {
            String address = extractAddress(trimmed);
            if (address != null && !address.isBlank()) {
                try {
                    return querySurchargeFlow(address);
                } catch (IOException e) {
                    return scriptService.getContent("system_busy");
                }
            }
        }
        return trimmed;
    }

    private String extractAddress(String text) {
        int idx = text.indexOf("QUERY_SURCHARGE:");
        if (idx == -1) return null;
        int start = idx + "QUERY_SURCHARGE:".length();
        int end = text.indexOf('\n', start);
        if (end == -1) end = text.length();
        return text.substring(start, end).trim();
    }

    /**
     * 核心查询流程：地址 → 坐标(接口1) → 超区费(接口2) → 话术反馈
     */
    private String querySurchargeFlow(String address) throws IOException {
        AddressResult addrResult = addressService.resolveAddress(address);
        if (!addrResult.isSuccess()) {
            String script = scriptService.getContent("address_unclear");
            String suggestion = addrResult.getSuggestion();
            return suggestion != null && !suggestion.isEmpty()
                    ? script + "\n（提示：" + suggestion + "）"
                    : script;
        }

        SurchargeResult surcharge = addressService.querySurcharge(
                addrResult.getLongitude(), addrResult.getLatitude());

        if (surcharge.isOutOfRange()) {
            return scriptService.getContent("result_surcharge")
                    .replace("{amount}", String.format("%.0f", surcharge.getAmount()))
                    .replace("{rule}", surcharge.getRuleDescription());
        } else {
            return scriptService.getContent("result_no_surcharge");
        }
    }

    private String mockChat(String message) {
        if (message.matches(".*?(省|市|区|县).*?(路|街|号|小区|村|镇|大道).*")) {
            try {
                return querySurchargeFlow(message);
            } catch (IOException e) {
                return scriptService.getContent("system_busy");
            }
        }
        if (message.contains("超区") || message.contains("查询") || message.contains("费用")) {
            return scriptService.getContent("guide_address");
        }
        if (message.contains("人工") || message.contains("客服") || message.contains("投诉")) {
            return scriptService.getContent("fallback");
        }
        return scriptService.getContent("welcome");
    }

    private String callLLM(String systemPrompt, List<Map<String, String>> history) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(history);
        requestBody.put("messages", messages);

        String json = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("API responded " + response.code());
            JsonNode root = objectMapper.readTree(response.body().string());
            return root.path("choices").get(0).path("message").path("content").asText();
        }
    }
}
