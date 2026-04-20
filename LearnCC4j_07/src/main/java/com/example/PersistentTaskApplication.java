package com.example;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.util.Scanner;

/**
 * s03 的 TodoManager 只是内存中的扁平清单: 没有顺序、没有依赖、状态只有做完没做完。真实目标是有结构的 -- 任务 B 依赖任务 A, 任务 C 和 D 可以并行, 任务 E 要等 C 和 D 都完成。
 *
 * 没有显式的关系, Agent 分不清什么能做、什么被卡住、什么能同时跑。而且清单只活在内存里, 上下文压缩 (s06) 一跑就没了。
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class PersistentTaskApplication implements CommandLineRunner {

    private final ChatClient chatClient;

    public PersistentTaskApplication(ChatClient.Builder builder) {
        String sysPrompt = "You are a coding agent at " + System.getProperty("user.dir") + ". Use task tools to plan and track work.";

        this.chatClient = builder
                .defaultSystem(sysPrompt)
                // 挂载全部 8 个系统工具与任务工具
                .defaultFunctions("bashTool", "readFileTool", "writeFileTool", "editFileTool",
                        "taskCreateTool", "taskUpdateTool", "taskListTool", "taskGetTool")
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PersistentTaskApplication.class);
        app.setLogStartupInfo(false);
        app.run(args);
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\033[36ms07 >> \033[0m");
            if (!scanner.hasNextLine()) break;

            String query = scanner.nextLine().trim();
            if (query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit") || query.isEmpty()) {
                break;
            }

            try {
                // 模型自主推理循环：
                // 发出请求 -> 模型发现需要创建或更新任务 -> 调用对应 Tool -> TaskManagerService 将 json 写入磁盘 -> 结果返回模型
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