package com.example.core;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class TeammateManager {
    private final ChatClient.Builder builder;
    private final Map<String, Map<String, String>> members = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // 使用 @Lazy 避免循环依赖
    public TeammateManager(@Lazy ChatClient.Builder builder) {
        this.builder = builder;
        // 注意：真实场景可以在这里加载/保存 .team/config.json
    }

    public String spawn(String name, String role, String prompt) {
        Map<String, String> member = members.computeIfAbsent(name,
                k -> new ConcurrentHashMap<>(Map.of("name", name, "role", role, "status", "idle")));

        if (!"idle".equals(member.get("status")) && !"shutdown".equals(member.get("status"))) {
            return "Error: '" + name + "' is currently " + member.get("status");
        }
        member.put("status", "working");
        member.put("role", role);

        // 在新线程中启动子 Agent 循环
        executor.submit(() -> runTeammateLoop(name, role, prompt));
        return "Spawned '" + name + "' (role: " + role + ")";
    }

    private void runTeammateLoop(String name, String role, String prompt) {
        String sysPrompt = String.format("""
                You are '%s', role: %s, at %s.
                Use send_message to communicate. Complete your task.
                Always use '%s' as your sender name in tools.
                """, name, role, System.getProperty("user.dir"), name);

        ChatClient tmClient = builder
                .defaultSystem(sysPrompt)
                // 组员的权限较少 (没有 spawn, list, broadcast 等能力)
                .defaultFunctions(
                        "bashTool", "readFileTool", "writeFileTool", "editFileTool",
                        "sendMessageTool", "readInboxTool"
                )
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();

        try {
            // 内部自动执行多轮 Tool Calling 循环
            tmClient.prompt().user(prompt).call();
        } catch (Exception e) {
            System.err.println("  [" + name + "] error: " + e.getMessage());
        } finally {
            if (!"shutdown".equals(members.get(name).get("status"))) {
                members.get(name).put("status", "idle");
            }
        }
    }

    public List<String> getMemberNames() {
        return new ArrayList<>(members.keySet());
    }

    public String listAll() {
        if (members.isEmpty()) return "No teammates.";
        StringBuilder sb = new StringBuilder("Team members:\n");
        for (Map<String, String> m : members.values()) {
            sb.append(String.format("  %s (%s): %s\n", m.get("name"), m.get("role"), m.get("status")));
        }
        return sb.toString();
    }
}