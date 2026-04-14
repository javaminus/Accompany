package com.example;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.util.Scanner;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class PlanningAgentApplication implements CommandLineRunner {

    private final ChatClient chatClient;

    public PlanningAgentApplication(ChatClient.Builder chatClientBuilder) {
        String workDir = System.getProperty("user.dir");
        
        // 更新了 System Prompt，强调使用 Todo 工具进行多步规划
        String systemPrompt = """
                You are a coding agent at %s.
                Use the todo tool for multi-step work.
                Keep exactly one step in_progress when a task has multiple steps.
                Refresh the plan as work advances. Prefer tools over prose.
                """.formatted(workDir);

        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                // 注册所有可用工具，包含新增的 todoTool
                .defaultFunctions("bashTool", "readFileTool", "writeFileTool", "editFileTool", "todoTool")
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PlanningAgentApplication.class);
        app.setLogStartupInfo(false);
        app.run(args);
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\033[36ms03 >> \033[0m"); // 青色命令行提示符
            if (!scanner.hasNextLine()) {
                break;
            }

            String query = scanner.nextLine().trim();

            if (query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit") || query.isEmpty()) {
                break;
            }

            try {
                // Spring AI 内部自动处理：请求 -> 调用工具1 -> 工具1附加Reminder -> 模型发现需要更新计划 -> 调用TodoTool -> ... -> 最终回复文本
                String response = chatClient.prompt()
                        .user(query)
                        .call()
                        .content();

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