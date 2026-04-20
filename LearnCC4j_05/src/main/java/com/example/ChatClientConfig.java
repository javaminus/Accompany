package com.example;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, SkillRegistry skillRegistry) {
        
        String systemPrompt = """
            You are a coding agent at %s.
            Use loadSkill when a task needs specialized instructions before you act.
            
            Skills available:
            %s
            """.formatted(WorkspaceContext.WORKDIR, skillRegistry.describeAvailable());

        return chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultFunctions("bash", "readFile", "writeFile", "editFile", "loadSkill")
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }
}