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
public class SubagentApplication implements CommandLineRunner {

    private final ChatClient parentChatClient;

    public SubagentApplication(ChatClient.Builder chatClientBuilder) {
        String workDir = System.getProperty("user.dir");
        String systemPrompt = "You are a coding agent at " + workDir + ". Use the task tool to delegate exploration or subtasks.";

        // 父 Agent：拥有完整的工具集（包含 taskTool）和自己的独立记忆
        this.parentChatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultFunctions("bashTool", "readFileTool", "writeFileTool", "editFileTool", "taskTool")
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    /**
     * 在 Python 脚本中，是通过给子 Agent 传递一个空的 messages=[] 数组来实现历史隔离的。在 Spring AI 中，
     * 我们可以通过在 task 工具内部动态构建一个新的 ChatClient，并为其分配一个全新的 InMemoryChatMemory 
     * 来实现相同的效果。父 Agent 的记忆（Memory）保持纯净，仅记录子 Agent 最终返回的 Summary。
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SubagentApplication.class);
        app.setLogStartupInfo(false);
        app.run(args);
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\033[36ms04 >> \033[0m");
            if (!scanner.hasNextLine()) break;
            
            String query = scanner.nextLine().trim();
            if (query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit") || query.isEmpty()) {
                break;
            }

            try {
                // 父 Agent 循环
                String response = parentChatClient.prompt()
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