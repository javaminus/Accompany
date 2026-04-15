package com.example;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.util.Scanner;

/**
 * 内容压缩
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class ContextCompactApplication implements CommandLineRunner {

    private final ChatClient chatClient;
    private final MemoryCompactService memoryCompactService;
    private static final String CONVERSATION_ID = "default-session";

    // 修复：直接注入 ChatMemory，避免相互依赖
    public ContextCompactApplication(ChatClient.Builder builder,
                                     ChatMemory chatMemory,
                                     MemoryCompactService memoryCompactService) {
        this.memoryCompactService = memoryCompactService;
        String sysPrompt = "You are a coding agent at " + System.getProperty("user.dir") + ". Use tools to solve tasks.";

        this.chatClient = builder
                .defaultSystem(sysPrompt)
                .defaultFunctions("bashTool", "readFileTool", "compactTool")
                // 修复：直接使用注入进来的 chatMemory
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory, CONVERSATION_ID, 100))
                .build();
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ContextCompactApplication.class);
        app.setLogStartupInfo(false);
        app.run(args);
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\033[36ms06 >> \033[0m");
            if (!scanner.hasNextLine()) break;

            String query = scanner.nextLine().trim();
            if (query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit") || query.isEmpty()) {
                break;
            }

            try {
                // LLM 调用前压缩处理
                memoryCompactService.microCompact(CONVERSATION_ID);
                memoryCompactService.autoCompactIfNeeded(CONVERSATION_ID);

                String response = chatClient.prompt().user(query).call().content();

                if (response != null && !response.isBlank()) {
                    System.out.println(response);
                }

                // LLM 调用后压缩处理
                if (memoryCompactService.isManualCompactTriggered()) {
                    System.out.println("\033[35m[manual compact]\033[0m");
                    memoryCompactService.forceCompact(CONVERSATION_ID);
                    memoryCompactService.setManualCompactTriggered(false);
                }

                System.out.println();
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}