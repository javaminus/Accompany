package com.example.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Component
public class SubAgentHelper {
    
    private final ChatModel chatModel;

    public SubAgentHelper(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String runSubagent(String prompt, String agentType) {
        try {
            ChatClient.Builder builder = ChatClient.builder(chatModel)
                    .defaultFunctions("bash", "read_file");
            
            if (!"Explore".equalsIgnoreCase(agentType)) {
                builder.defaultFunctions("bash", "read_file", "write_file", "edit_file");
            }
            
            ChatClient client = builder.build();
            return client.prompt().user(prompt).call().content();
        } catch (Exception e) {
            return "(subagent failed: " + e.getMessage() + ")";
        }
    }
}