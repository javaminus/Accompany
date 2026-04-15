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
    private final MessageBus bus;
    private final Map<String, Map<String, String>> members = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public TeammateManager(@Lazy ChatClient.Builder builder, MessageBus bus) {
        this.builder = builder;
        this.bus = bus;
    }

    public String spawn(String name, String role, String prompt) {
        Map<String, String> m = members.computeIfAbsent(name,
                k -> new ConcurrentHashMap<>(Map.of("name", name, "role", role, "status", "idle")));
        
        if (!"idle".equals(m.get("status")) && !"shutdown".equals(m.get("status"))) {
            return "Error: '" + name + "' is currently " + m.get("status");
        }
        m.put("status", "working");
        m.put("role", role);

        executor.submit(() -> runTeammateLoop(name, role, prompt));
        return "Spawned '" + name + "' (role: " + role + ")";
    }

    private void runTeammateLoop(String name, String role, String initialPrompt) {
        String sysPrompt = String.format("""
                You are '%s', role: %s, at %s.
                Always use '%s' for the 'sender' field in tools.
                Submit plans via teammatePlanSubmitTool before major work.
                Respond to shutdown requests with teammateShutdownRespondTool.
                """, name, role, System.getProperty("user.dir"), name);

        ChatClient tmClient = builder
                .defaultSystem(sysPrompt)
                // 组员专属工具池 (包含响应端协议)
                .defaultFunctions(
                        "bashTool", "readFileTool", "writeFileTool", "editFileTool",
                        "sendMessageTool", "readInboxTool", 
                        "teammateShutdownRespondTool", "teammatePlanSubmitTool"
                )
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();

        try {
            // 轮询 50 次，或者直到被 Shutdown 标记
            String prompt = initialPrompt;
            for (int i = 0; i < 50; i++) {
                if ("shutdown".equals(members.get(name).get("status"))) break;
                
                tmClient.prompt().user(prompt).call();
                
                // 下一轮的 Prompt 来自 Inbox
                String inbox = bus.readInboxJson(name);
                prompt = "[]".equals(inbox) ? "<status>idle, checking inbox</status>" : "<inbox>" + inbox + "</inbox>";
                Thread.sleep(2000); // 稍微挂起避免死循环消耗
            }
        } catch (Exception e) {
            System.err.println("  [" + name + "] error: " + e.getMessage());
        } finally {
            if (!"shutdown".equals(members.get(name).get("status"))) {
                members.get(name).put("status", "idle");
            }
        }
    }

    public void markAsShutdown(String name) {
        if (members.containsKey(name)) {
            members.get(name).put("status", "shutdown");
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