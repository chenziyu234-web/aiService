package com.logistics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.backend.model.Order;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AIService {

    @Value("${ai.model.api-key}")
    private String apiKey;

    @Value("${ai.model.base-url}")
    private String baseUrl;

    @Value("${ai.model.model-name}")
    private String modelName;

    private final LogisticsService logisticsService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient();
    
    // Simple in-memory chat history: userId -> List of messages
    private final Map<String, List<Map<String, String>>> chatHistory = new ConcurrentHashMap<>();

    public AIService(LogisticsService logisticsService) {
        this.logisticsService = logisticsService;
    }

    public String getChatResponse(String userId, String userMessage) {
        // Fallback for demo if no real key is provided
        if (apiKey.contains("placeholder")) {
            return mockAIResponse(userMessage);
        }

        // 1. Get User's Orders to inject into context
        List<Order> userOrders = logisticsService.getUserOrders(userId);
        String orderContext = "当前用户关联的订单列表:\n";
        if (userOrders.isEmpty()) {
            orderContext += "(无关联订单)";
        } else {
            for (Order o : userOrders) {
                orderContext += String.format("- 订单号: %s (状态: %s, 位置: %s)\n", o.getOrderId(), o.getStatus(), o.getCurrentLocation());
            }
        }

        // 2. Build System Prompt with Context
        String systemPrompt = "你是一个专业的智能物流客服机器人。\n\n" +
                "【用户信息】\n" +
                orderContext + "\n\n" +
                "【你的能力】\n" +
                "你可以执行以下内部指令来获取数据或操作（这些指令对用户不可见）：\n" +
                "- GET_ORDER:ID (查询订单详情)\n" +
                "- URGE_ORDER:ID (执行催单操作)\n" +
                "- REPORT_ISSUE:ID:REASON (上报异常并转人工)\n\n" +
                "【严格行为准则 - 违反将导致系统故障】\n" +
                "1. **禁止泄露指令格式**：永远不要在回复中提到 'GET_ORDER'、'URGE_ORDER' 等单词或格式。用户不应该知道这些指令的存在。\n" +
                "2. **工具调用逻辑**：\n" +
                "   - 如果你需要查询或催促某个订单，请直接在回复中**只输出**对应的指令字符串。例如：`GET_ORDER:1001`。\n" +
                "   - 不要说“正在为您查询”，直接输出指令即可。\n" +
                "3. **多订单冲突处理**：\n" +
                "   - 如果用户要催单但没指定订单，且用户有多个订单，请用亲切的口吻询问用户：“您想催哪一个订单呢？（例如 1001）”。\n" +
                "4. **语言风格**：始终使用亲切、专业的中文回复用户。";

        try {
            // 3. Update Chat History
            List<Map<String, String>> history = chatHistory.computeIfAbsent(userId, k -> new ArrayList<>());
            history.add(Map.of("role", "user", "content", userMessage));
            
            // Limit history size to last 10 messages to avoid token limit
            if (history.size() > 10) {
                history = new ArrayList<>(history.subList(history.size() - 10, history.size()));
                chatHistory.put(userId, history);
            }

            String aiOutput = callLLM(systemPrompt, history);
            
            // Record AI response
            history.add(Map.of("role", "assistant", "content", aiOutput));
            
            return processToolCall(aiOutput);
        } catch (Exception e) {
            return "AI服务暂不可用: " + e.getMessage();
        }
    }

    private String processToolCall(String aiOutput) {
        aiOutput = aiOutput.trim();
        
        // 提取指令：即使 AI 在指令前后加了废话，我们也尝试提取出指令部分
        String command = null;
        if (aiOutput.contains("GET_ORDER:")) {
             command = extractCommand(aiOutput, "GET_ORDER:");
             if (command != null) {
                 String orderId = command.split(":")[1];
                 Order order = logisticsService.getOrder(orderId);
                 if (order == null) return "抱歉，未找到订单 " + orderId;
                 return String.format("订单 %s 当前位置在 %s。状态: %s。历史轨迹: %s", 
                        orderId, order.getCurrentLocation(), order.getStatus(), order.getHistory());
             }
        } 
        
        if (aiOutput.contains("URGE_ORDER:")) {
             command = extractCommand(aiOutput, "URGE_ORDER:");
             if (command != null) {
                 String orderId = command.split(":")[1];
                 return logisticsService.urgeOrder(orderId);
             }
        } 
        
        if (aiOutput.contains("REPORT_ISSUE:")) {
             command = extractCommand(aiOutput, "REPORT_ISSUE:");
             if (command != null) {
                 String[] parts = command.split(":", 3);
                 if (parts.length < 3) return "处理问题报告时出错。";
                 return logisticsService.reportIssue(parts[1], parts[2]);
             }
        }
        
        // 如果没有提取到有效指令，或者 AI 只是在聊天，则返回原始内容
        return aiOutput;
    }

    // 辅助方法：从混杂文本中提取指令
    private String extractCommand(String text, String prefix) {
        int startIndex = text.indexOf(prefix);
        if (startIndex == -1) return null;
        
        // 寻找指令结束位置（换行符或字符串末尾）
        int endIndex = text.indexOf('\n', startIndex);
        if (endIndex == -1) endIndex = text.length();
        
        return text.substring(startIndex, endIndex).trim();
    }

    private String mockAIResponse(String message) {
        // Simple keyword matching for demo purposes
        if (message.contains("1001")) {
            if (message.contains("where") || message.contains("status") || message.contains("查")) {
                return processToolCall("GET_ORDER:1001");
            } else if (message.contains("urge") || message.contains("fast") || message.contains("催")) {
                return processToolCall("URGE_ORDER:1001");
            } else if (message.contains("bad") || message.contains("broken") || message.contains("human") || message.contains("人工")) {
                return processToolCall("REPORT_ISSUE:1001:客户要求人工客服");
            }
        }
        if (message.contains("1002")) {
             return processToolCall("GET_ORDER:1002");
        }
        return "我是智能物流助手。请提供订单号 (1001 或 1002) 并告知您的需求（查询、催单、投诉）。(当前运行在模拟模式)";
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
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            
            JsonNode root = objectMapper.readTree(response.body().string());
            return root.path("choices").get(0).path("message").path("content").asText();
        }
    }
}
