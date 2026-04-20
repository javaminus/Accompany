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

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class S11TeamProtocolsApplication implements CommandLineRunner {

    private final ChatClient chatClient;
    private final MessageBus bus;
    private final TeammateManager teamManager;

    public S11TeamProtocolsApplication(ChatClient.Builder builder, MessageBus bus, TeammateManager teamManager) {
        this.bus = bus;
        this.teamManager = teamManager;
        String workDir = System.getProperty("user.dir");
        String systemPrompt = """
                You are a team lead at %s.
                Manage teammates with shutdown and plan approval protocols.
                Always read your inbox before making decisions.
                """.formatted(workDir);

        this.chatClient = builder
                .defaultSystem(systemPrompt)
                // Lead 的专属权限工具池 (包含协议的控制端工具)
                .defaultFunctions(
                        "bashTool", "readFileTool", "writeFileTool", "editFileTool",
                        "spawnTeammateTool", "listTeammatesTool", "sendMessageTool",
                        "readInboxTool", "broadcastTool",
                        "leadShutdownRequestTool", "leadShutdownCheckTool", "leadPlanApproveTool"
                )
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(S11TeamProtocolsApplication.class);
        app.setLogStartupInfo(false);
        app.run(args);
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\033[36ms11 >> \033[0m");
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