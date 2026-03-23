package com.logistics.backend.controller;

import com.logistics.backend.model.ChatRequest;
import com.logistics.backend.service.AIService;
import com.logistics.backend.service.LogisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class LogisticsController {

    @Autowired
    private AIService aiService;

    @Autowired
    private LogisticsService logisticsService;

    @PostMapping("/chat/send")
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        // In a real scenario, we would use the AI service to interpret the intent.
        // For this demo, if the AI key is invalid, we might want a fallback logic here or inside AIService.
        String response = aiService.getChatResponse(request.getUserId(), request.getMessage());
        return Map.of("response", response);
    }
    
    // Direct endpoints for testing without AI
    @GetMapping("/order/{id}")
    public Object getOrder(@PathVariable String id) {
        return logisticsService.getOrder(id);
    }
}
