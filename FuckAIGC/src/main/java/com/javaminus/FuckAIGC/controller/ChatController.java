package com.javaminus.FuckAIGC.controller;


import com.javaminus.FuckAIGC.entity.Result;
import com.javaminus.FuckAIGC.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@CrossOrigin
public class ChatController {
    @Autowired
    private ChatService chatService;
    
    @GetMapping("/chat")
    public Result chat(@RequestParam(required = true) String userInput,
                       @RequestParam(required = false, defaultValue = "CN") String systemPrompt) {
        return Result.ok(chatService.chat(userInput, systemPrompt));
    }
}
