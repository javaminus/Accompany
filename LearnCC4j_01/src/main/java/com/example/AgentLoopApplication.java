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
public class AgentLoopApplication implements CommandLineRunner {

    private final ChatClient chatClient;

    public AgentLoopApplication(ChatClient.Builder chatClientBuilder) {
        String currentDir = System.getProperty("user.dir");
        String systemPrompt = """
                You are a coding agent at %s.
                Use bash to inspect and change the workspace. Act first, then report clearly.
                """.formatted(currentDir);

        // 初始化 ChatClient 并配置默认行为
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                // 注册上面的 Bash 工具
                .defaultFunctions("bashTool")
                // 使用 Advisor 自动维护上下文历史 (代替 Python 中的 history.append)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    public static void main(String[] args) {
        // 关闭 Spring Boot 默认的 Logo 打印，让 CLI 界面更干净
        SpringApplication app = new SpringApplication(AgentLoopApplication.class);
        app.setLogStartupInfo(false);
        app.run(args);
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("\033[36ms01 >> \033[0m");
            if (!scanner.hasNextLine()) {
                break;
            }
            
            String query = scanner.nextLine().trim();

            if (query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit") || query.isEmpty()) {
                break;
            }

            try {
                // Spring AI 的 .call() 方法内部已经实现了 Python 脚本中的 while 循环：
                // 发送提问 -> 模型要求调用 Bash -> 自动执行 Java Bash 方法 -> 自动将结果追加回历史 -> 重新请求模型 -> 直到输出文本
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