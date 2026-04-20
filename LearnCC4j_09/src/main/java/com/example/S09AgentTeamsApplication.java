package com.example;

import com.example.core.MessageBus;
import com.example.core.TeammateManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.util.Scanner;


/**
 * Subagent (s04) 是一次性的: 生成、干活、返回摘要、消亡。没有身份, 没有跨调用的记忆。Background Tasks (s08) 能跑 shell 命令, 但做不了 LLM 引导的决策。
 *
 * 真正的团队协作需要三样东西: (1) 能跨多轮对话存活的持久 Agent, (2) 身份和生命周期管理, (3) Agent 之间的通信通道。
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class S09AgentTeamsApplication implements CommandLineRunner {

    private final ChatClient chatClient;
    private final MessageBus bus;
    private final TeammateManager teamManager;

    public S09AgentTeamsApplication(ChatClient.Builder builder, MessageBus bus, TeammateManager teamManager) {
        this.bus = bus;
        this.teamManager = teamManager;
        String workDir = System.getProperty("user.dir");
        String systemPrompt = """
                You are a team lead at %s.
                Spawn teammates and communicate via inboxes.
                Always read your inbox before taking major actions.
                """.formatted(workDir);

        this.chatClient = builder
                .defaultSystem(systemPrompt)
                // Lead 的专属工具池 (一共 9 个)
                .defaultFunctions(
                        "bashTool", "readFileTool", "writeFileTool", "editFileTool",
                        "spawnTeammateTool", "listTeammatesTool", "sendMessageTool",
                        "readInboxTool", "broadcastTool"
                )
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(S09AgentTeamsApplication.class);
        app.setLogStartupInfo(false);
        app.run(args);
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\033[36ms09 >> \033[0m");
            if (!scanner.hasNextLine()) break;
            String query = scanner.nextLine().trim();

            if (query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit") || query.isEmpty()) {
                break;
            }
            if (query.equals("/team")) {
                System.out.println(teamManager.listAll());
                continue;
            }
            if (query.equals("/inbox")) {
                System.out.println(bus.readInboxJson("lead"));
                continue;
            }

            try {
                // 每次对话前，自动读取主管的收件箱并拼接到 Prompt 中
                String inboxData = bus.readInboxJson("lead");
                String prompt = query;
                if (!"[]".equals(inboxData)) {
                    prompt += "\n\n<inbox>\n" + inboxData + "\n</inbox>";
                }

                String response = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                System.out.println(response);
                System.out.println();
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
        System.exit(0);
    }
}