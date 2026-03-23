package com.logistics.backend.furniture.controller;

import com.logistics.backend.furniture.model.FurnitureChatRequest;
import com.logistics.backend.furniture.service.FurnitureChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/furniture")
@CrossOrigin(origins = "*")
public class FurnitureChatController {

    @Autowired
    private FurnitureChatService chatService;

    @PostMapping("/chat/send")
    public Map<String, String> chat(@RequestBody FurnitureChatRequest request) {
        String response = chatService.chat(request.getSessionId(), request.getMessage());
        return Map.of("response", response);
    }
}
