package com.example;

import com.example.s12.core.ProjectEnv;
import com.example.s12.core.WorktreeManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.util.Scanner;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class S12WorktreeTaskIsolationApplication implements CommandLineRunner {

    private final ChatClient chatClient;
    private final WorktreeManager worktreeManager;

    public S12WorktreeTaskIsolationApplication(ChatClient.Builder builder, ProjectEnv env, WorktreeManager worktreeManager) {
        this.worktreeManager = worktreeManager;
        String systemPrompt = String.format("""
                You are a coding agent at %s.
                Use task + worktree tools for multi-task work.
                For parallel or risky changes: create tasks, allocate worktree lanes,
                run commands in those lanes, then choose keep/remove for closeout.
                Use worktree_events when you need lifecycle visibility.
                """, env.getWorkDir());

        this.chatClient = builder
                .defaultSystem(systemPrompt)
                .defaultFunctions(
                        // 基础工具
                        "bashTool", "readFileTool", "writeFileTool", "editFileTool",
                        // Task 任务工具
                        "taskCreateTool", "taskListTool", "taskGetTool", "taskUpdateTool", "taskBindWorktreeTool",
                        // Worktree 工作树隔离工具
                        "worktreeCreateTool", "worktreeListTool", "worktreeStatusTool", 
                        "worktreeRunTool", "worktreeRemoveTool", "worktreeKeepTool", "worktreeEventsTool"
                )
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(S12WorktreeTaskIsolationApplication.class);
        app.setLogStartupInfo(false);
        app.run(args);
    }

    @Override
    public void run(String... args) {
        System.out.println("Repo root for s12: " + worktreeManager.getRepoRoot());
        if (!worktreeManager.isGitAvailable()) {
            System.err.println("Note: Not in a git repo. worktree_* tools will return errors.");
        }

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\033[36ms12 >> \033[0m");
            if (!scanner.hasNextLine()) break;
            String query = scanner.nextLine().trim();

            if (query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit") || query.isEmpty()) {
                break;
            }

            try {
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
        System.exit(0);
    }
}