package com.example.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@Component
public class TaskManager {
    private final Path dir;
    private final ObjectMapper mapper = new ObjectMapper();
    private int nextId;

    public TaskManager(ProjectEnv env) {
        this.dir = env.getRepoRoot().resolve(".tasks");
        this.dir.toFile().mkdirs();
        this.nextId = calculateMaxId() + 1;
    }

    private int calculateMaxId() {
        int max = 0;
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path p : paths.toList()) {
                String name = p.getFileName().toString();
                if (name.startsWith("task_") && name.endsWith(".json")) {
                    try { max = Math.max(max, Integer.parseInt(name.substring(5, name.length() - 5))); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return max;
    }

    private Path getPath(int taskId) { return dir.resolve("task_" + taskId + ".json"); }

    private Map<String, Object> load(int taskId) {
        try { return mapper.readValue(getPath(taskId).toFile(), Map.class); }
        catch (Exception e) { throw new RuntimeException("Task " + taskId + " not found"); }
    }

    private void save(Map<String, Object> task) {
        try { mapper.writerWithDefaultPrettyPrinter().writeValue(getPath((Integer) task.get("id")).toFile(), task); }
        catch (Exception e) { throw new RuntimeException("Failed to save task: " + e.getMessage()); }
    }

    public synchronized String create(String subject, String description) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", nextId++);
        task.put("subject", subject);
        task.put("description", description != null ? description : "");
        task.put("status", "pending");
        task.put("owner", "");
        task.put("worktree", "");
        task.put("created_at", System.currentTimeMillis() / 1000.0);
        task.put("updated_at", System.currentTimeMillis() / 1000.0);
        save(task);
        try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(task); } catch (Exception e) { return ""; }
    }

    public String get(int taskId) {
        try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(load(taskId)); }
        catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    public boolean exists(int taskId) { return Files.exists(getPath(taskId)); }

    public synchronized String update(int taskId, String status, String owner) {
        Map<String, Object> task = load(taskId);
        if (status != null) {
            if (!Set.of("pending", "in_progress", "completed").contains(status)) throw new IllegalArgumentException("Invalid status");
            task.put("status", status);
        }
        if (owner != null) task.put("owner", owner);
        task.put("updated_at", System.currentTimeMillis() / 1000.0);
        save(task);
        try { return mapper.writeValueAsString(task); } catch (Exception e) { return ""; }
    }

    public synchronized String bindWorktree(int taskId, String worktree, String owner) {
        Map<String, Object> task = load(taskId);
        task.put("worktree", worktree);
        if (owner != null && !owner.isEmpty()) task.put("owner", owner);
        if ("pending".equals(task.get("status"))) task.put("status", "in_progress");
        task.put("updated_at", System.currentTimeMillis() / 1000.0);
        save(task);
        try { return mapper.writeValueAsString(task); } catch (Exception e) { return ""; }
    }

    public synchronized void unbindWorktree(int taskId) {
        Map<String, Object> task = load(taskId);
        task.put("worktree", "");
        task.put("updated_at", System.currentTimeMillis() / 1000.0);
        save(task);
    }

    public String listAll() {
        List<String> lines = new ArrayList<>();
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(p -> p.getFileName().toString().startsWith("task_")).sorted().forEach(p -> {
                try {
                    Map task = mapper.readValue(p.toFile(), Map.class);
                    String status = (String) task.get("status");
                    String marker = "pending".equals(status) ? "[ ]" : "in_progress".equals(status) ? "[>]" : "completed".equals(status) ? "[x]" : "[?]";
                    String ownerStr = task.get("owner") != null && !((String)task.get("owner")).isEmpty() ? " owner=" + task.get("owner") : "";
                    String wtStr = task.get("worktree") != null && !((String)task.get("worktree")).isEmpty() ? " wt=" + task.get("worktree") : "";
                    lines.add(marker + " #" + task.get("id") + ": " + task.get("subject") + ownerStr + wtStr);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
        return lines.isEmpty() ? "No tasks." : String.join("\n", lines);
    }
}