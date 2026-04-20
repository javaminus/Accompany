package com.example.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Component
public class EventBus {
    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper();

    public EventBus(ProjectEnv env) {
        this.path = env.getRepoRoot().resolve(".worktrees").resolve("events.jsonl");
        this.path.getParent().toFile().mkdirs();
        if (!Files.exists(this.path)) {
            try { Files.writeString(this.path, ""); } catch (Exception ignored) {}
        }
    }

    public synchronized void emit(String event, Map<String, Object> task, Map<String, Object> worktree, String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("ts", System.currentTimeMillis() / 1000.0);
        payload.put("task", task != null ? task : Map.of());
        payload.put("worktree", worktree != null ? worktree : Map.of());
        if (error != null) payload.put("error", error);

        try {
            Files.writeString(path, mapper.writeValueAsString(payload) + "\n", StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    public String listRecent(int limit) {
        int n = Math.max(1, Math.min(limit, 200));
        try {
            List<String> lines = Files.readAllLines(path);
            List<Map<String, Object>> items = new ArrayList<>();
            int start = Math.max(0, lines.size() - n);
            for (int i = start; i < lines.size(); i++) {
                try { items.add(mapper.readValue(lines.get(i), Map.class)); }
                catch (Exception e) { items.add(Map.of("event", "parse_error", "raw", lines.get(i))); }
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(items);
        } catch (Exception e) { return "[]"; }
    }
}