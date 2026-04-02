package com.logistics.backend.furniture.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.backend.furniture.model.AddressResult;
import com.logistics.backend.furniture.model.SurchargeResult;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Service
public class FurnitureChatService {

    private static final Logger log = LoggerFactory.getLogger(FurnitureChatService.class);

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
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "chat-stream");
        t.setDaemon(true);
        return t;
    });

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

    /**
     * 流式聊天：LLM token 逐个推送到 SseEmitter，命中 QUERY_SURCHARGE 时中止流并推送查询结果。
     */
    public SseEmitter chatStream(String sessionId, String userMessage) {
        SseEmitter emitter = new SseEmitter(120_000L);

        if (apiKey == null || apiKey.contains("placeholder")) {
            streamExecutor.execute(() -> {
                try {
                    String answer = mockChat(userMessage);
                    emitter.send(SseEmitter.event().data(answer));
                    emitter.send(SseEmitter.event().name("done").data(""));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            });
            return emitter;
        }

        String systemPrompt = buildSystemPrompt();
        List<Map<String, String>> history = sessionHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(Map.of("role", "user", "content", userMessage));
        if (history.size() > 20) {
            List<Map<String, String>> trimmed = new ArrayList<>(history.subList(history.size() - 20, history.size()));
            sessionHistory.put(sessionId, trimmed);
            history = trimmed;
        }
        final List<Map<String, String>> historyRef = history;
        final List<Map<String, String>> snapshot = new ArrayList<>(history);

        streamExecutor.execute(() -> {
            StringBuilder full = new StringBuilder();
            int[] sentChars = {0};
            boolean[] commandDetected = {false};
            final String CMD_PREFIX = "QUERY_SURCHARGE:";

            try {
                callLLMStream(systemPrompt, snapshot, chunk -> {
                    full.append(chunk);
                    if (commandDetected[0]) {
                        return;
                    }
                    String accumulated = full.toString();
                    if (accumulated.contains(CMD_PREFIX)) {
                        commandDetected[0] = true;
                        return;
                    }
                    String trimmedAcc = accumulated.trim();
                    if (!trimmedAcc.isEmpty() && CMD_PREFIX.startsWith(trimmedAcc)) {
                        return;
                    }
                    String unsent = accumulated.substring(sentChars[0]);
                    if (!unsent.isEmpty()) {
                        try {
                            emitter.send(SseEmitter.event().data(unsent));
                            sentChars[0] = accumulated.length();
                        } catch (IOException ignored) {}
                    }
                });

                String aiOutput = full.toString().trim();
                historyRef.add(Map.of("role", "assistant", "content", aiOutput));

                if (commandDetected[0] || aiOutput.contains(CMD_PREFIX)) {
                    String address = extractAddress(aiOutput);
                    if (address != null && !address.isBlank()) {
                        try {
                            String result = querySurchargeFlow(address);
                            emitter.send(SseEmitter.event().name("replace").data(result));
                        } catch (IOException e) {
                            emitter.send(SseEmitter.event().name("replace").data(scriptService.getContent("system_busy")));
                        }
                    }
                } else {
                    String unsent = full.toString().substring(sentChars[0]);
                    if (!unsent.isEmpty()) {
                        emitter.send(SseEmitter.event().data(unsent));
                    }
                }
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                log.error("stream chat error", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(scriptService.getContent("system_busy")));
                    emitter.complete();
                } catch (IOException ignored) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    private String buildSystemPrompt() {
        return """
                你是家具物流专属智能客服，你的唯一核心任务是帮助客户查询家具配送超区费。
                
                【工作流程】
                1. 客户打招呼或首次咨询时，友好告知可以帮助查询超区费，引导提供收货地址
                2. 客户提供了包含省/市/区(县)+详细街道或小区的**真实完整**地址时，直接输出指令
                3. 客户地址不完整（缺省/市/区或缺详细地址），友好引导补充
                4. 客户问超区费以外的问题，告知联系人工客服
                
                【指令格式】
                当确认客户提供了真实完整收货地址，回复中**仅**输出以下指令（不要加任何其他文字）：
                QUERY_SURCHARGE:完整地址字符串
                
                例如客户说"帮我查一下广东省深圳市南山区科技园南路18号翠园小区"，你应回复：
                QUERY_SURCHARGE:广东省深圳市南山区科技园南路18号翠园小区
                
                【地址有效性判断——非常重要】
                以下情况说明地址不完整或无效，**绝对不能**输出 QUERY_SURCHARGE 指令，必须友好引导客户补充真实信息：
                - 包含占位符或示意字符：X、XX、**、某某、某、xxx、OO、##、___、…… 等
                - 仅有省市区没有具体街道/小区/门牌号
                - 明显是示例或测试地址（如"XX路XX号""某某小区"）
                遇到这些情况，用友好自然的语气告诉客户需要真实详细的门牌地址才能查询。
                
                【语气与表达】
                1. 像店里热情的客服一样自然聊天，用「您」称呼，少用公文腔、套话
                2. 可适当用少量表情符号点缀（每条回复 0～2 个即可），例如问候用 👋😊，地址用 📍📦，顺利时用 ✅；不要堆砌表情
                3. 客户打招呼（如「你好」「在吗」）时，先热情回应一两句，再轻轻引导对方发完整收货地址
                
                【严格规则】
                1. 永远不要在回复中暴露 QUERY_SURCHARGE 指令格式，客户不应知道它的存在
                2. 仅在确认地址是**真实且足够完整**时才输出指令
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

    private void callLLMStream(String systemPrompt, List<Map<String, String>> history,
                               Consumer<String> onChunk) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("stream", true);

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
            if (!response.isSuccessful()) {
                throw new IOException("API responded " + response.code());
            }
            if (response.body() == null) {
                throw new IOException("empty body");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if (payload.equals("[DONE]")) {
                        break;
                    }
                    try {
                        JsonNode delta = objectMapper.readTree(payload)
                                .path("choices").path(0).path("delta").path("content");
                        if (!delta.isMissingNode() && !delta.isNull()) {
                            String text = delta.asText();
                            if (!text.isEmpty()) {
                                onChunk.accept(text);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
