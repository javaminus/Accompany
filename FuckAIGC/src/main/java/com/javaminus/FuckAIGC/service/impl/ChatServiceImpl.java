package com.javaminus.FuckAIGC.service.impl;

import com.javaminus.FuckAIGC.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.javaminus.FuckAIGC.ai.prompt.system.SystemPrompts.*;


@Service
@Slf4j
public class ChatServiceImpl implements ChatService {
    @Autowired 
    private ChatModel chatModel;
    
    public String chat(String userInput, String systemPrompt) {
        if (systemPrompt.equals("CN")) {
            systemPrompt = FUCK_AIGC_CN;
        }else{
            systemPrompt = FUCK_AIGC_EN;
        }
        // 构建消息
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userInput)
        ));
        
        // 调用LLM
        ChatResponse response = chatModel.call(prompt);
        String content = response.getResult().getOutput().getContent();
        // 打印 Token 消耗情况
        var usage = response.getMetadata().getUsage();
        log.info("=== 任务完成 ===");
        log.info("Token 统计 -> 总计: {}, 输入: {}, 输出: {}",
                usage.getTotalTokens(), usage.getPromptTokens(), usage.getGenerationTokens());

        return content;
    }
    
}