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
 * 有些命令要跑好几分钟: npm install、pytest、docker build。阻塞式循环下模型只能干等。用户说 "装依赖, 顺便建个配置文件", Agent 却只能一个一个来。
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class BackgroundTasksApplication implements CommandLineRunner {

    private final ChatClient chatClient;
    private static final String CONVERSATION_ID = "default-session";

    public BackgroundTasksApplication(ChatClient.Builder builder, ChatMemory chatMemory) {

        String sysPrompt = "You are a coding agent at " + System.getProperty("user.dir") +
                ". Use background_run for long-running commands.";

        this.chatClient = builder
                .defaultSystem(sysPrompt)
                .defaultFunctions("bashTool", "readFileTool", "writeFileTool", "editFileTool",
                        "backgroundRunTool", "checkBackgroundTool")
                // 这里只用最标准的原生 Advisor 即可，无需任何自定义 Advisor 
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory, CONVERSATION_ID, 100))
                .build();
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BackgroundTasksApplication.class);
        app.setLogStartupInfo(false);
        app.run(args);
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\033[36ms08 >> \033[0m");
            if (!scanner.hasNextLine()) break;

            String query = scanner.nextLine().trim();
            if (query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit") || query.isEmpty()) {
                break;
            }

            try {
                String response = chatClient.prompt().user(query).call().content();
                if (response != null && !response.isBlank()) {
                    System.out.println(response);
                }
                System.out.println();
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}