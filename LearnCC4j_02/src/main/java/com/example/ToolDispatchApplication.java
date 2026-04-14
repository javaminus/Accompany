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
public class ToolDispatchApplication implements CommandLineRunner {

    private final ChatClient chatClient;

    public ToolDispatchApplication(ChatClient.Builder chatClientBuilder) {
        String workDir = System.getProperty("user.dir");
        String systemPrompt = "You are a coding agent at " + workDir + ". Use tools to solve tasks. Act, don't explain.";

        // 初始化 ChatClient：自动绑定所有工具、自动维护对话历史
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultFunctions("bashTool", "readFileTool", "writeFileTool", "editFileTool")
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ToolDispatchApplication.class);
        app.setLogStartupInfo(false); // 保持 CLI 界面整洁
        app.run(args);
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\033[36ms02 >> \033[0m"); // 打印青色提示符
            if (!scanner.hasNextLine()) {
                break;
            }

            String query = scanner.nextLine().trim();

            if (query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit") || query.isEmpty()) {
                break;
            }

            try {
                // .call() 会自动处理循环：模型请求工具 -> 执行工具 -> 将结果合并回请求 -> 最终返回文本
                String response = chatClient.prompt()
                        .user(query)
                        .call()
                        .content();

                System.out.println(response);
                System.out.println();
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}