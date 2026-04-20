package com.example.services;

import com.example.config.AppConfig;
import com.example.models.Models;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class TaskManagerService {

    @Autowired
    private ObjectMapper mapper;

    public TaskManagerService() {
        try { Files.createDirectories(AppConfig.TASKS_DIR); } catch (Exception e) {}
    }

    private int nextId() throws Exception {
        int max = 0;
        try (Stream<Path> paths = Files.list(AppConfig.TASKS_DIR)) {
            for (Path p : paths.toList()) {
                String name = p.getFileName().toString();
                if (name.startsWith("task_") && name.endsWith(".json")) {
                    int id = Integer.parseInt(name.split("_")[1].replace(".json", ""));
                    max = Math.max(max, id);
                }
            }
        }
        return max + 1;
    }

    private void save(Models.Task task) throws Exception {
        Files.writeString(AppConfig.TASKS_DIR.resolve("task_" + task.id() + ".json"), mapper.writeValueAsString(task));
    }

    private Models.Task load(int tid) throws Exception {
        Path p = AppConfig.TASKS_DIR.resolve("task_" + tid + ".json");
        if (!Files.exists(p)) throw new IllegalArgumentException("Task " + tid + " not found");
        return mapper.readValue(p.toFile(), Models.Task.class);
    }

    public String create(String subject, String description) {
        try {
            Models.Task task = new Models.Task(nextId(), subject, description, "pending", null, new ArrayList<>());
            save(task);
            return mapper.writeValueAsString(task);
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    public String get(int tid) {
        try { return mapper.writeValueAsString(load(tid)); }
        catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    public String update(int tid, String status, List<Integer> addBlockedBy, List<Integer> removeBlockedBy) {
        try {
            Models.Task task = load(tid);
            if (status != null) {
                if (status.equals("deleted")) {
                    Files.deleteIfExists(AppConfig.TASKS_DIR.resolve("task_" + tid + ".json"));
                    return "Task " + tid + " deleted";
                }
                task = new Models.Task(task.id(), task.subject(), task.description(), status, task.owner(), task.blockedBy());
                if (status.equals("completed")) {
                    try (Stream<Path> paths = Files.list(AppConfig.TASKS_DIR)) {
                        for (Path p : paths.toList()) {
                            Models.Task t = mapper.readValue(p.toFile(), Models.Task.class);
                            if (t.blockedBy() != null && t.blockedBy().contains(tid)) {
                                List<Integer> newBlocked = new ArrayList<>(t.blockedBy());
                                newBlocked.remove((Integer) tid);
                                save(new Models.Task(t.id(), t.subject(), t.description(), t.status(), t.owner(), newBlocked));
                            }
                        }
                    }
                }
            }
            List<Integer> currentBlocked = new ArrayList<>(task.blockedBy() != null ? task.blockedBy() : new ArrayList<>());
            if (addBlockedBy != null) {
                for (Integer b : addBlockedBy) if (!currentBlocked.contains(b)) currentBlocked.add(b);
            }
            if (removeBlockedBy != null) currentBlocked.removeAll(removeBlockedBy);

            Models.Task updated = new Models.Task(task.id(), task.subject(), task.description(), task.status(), task.owner(), currentBlocked);
            save(updated);
            return mapper.writeValueAsString(updated);
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    public String listAll() {
        try {
            StringBuilder sb = new StringBuilder();
            try (Stream<Path> paths = Files.list(AppConfig.TASKS_DIR)) {
                List<Models.Task> tasks = paths.filter(p -> p.getFileName().toString().startsWith("task_"))
                        .map(p -> {
                            try { return mapper.readValue(p.toFile(), Models.Task.class); } catch (Exception e) { return null; }
                        }).toList();
                if (tasks.isEmpty()) return "No tasks.";
                for (Models.Task t : tasks) {
                    if (t == null) continue;
                    String m = switch (t.status()) {
                        case "pending" -> "[ ]";
                        case "in_progress" -> "[>]";
                        case "completed" -> "[x]";
                        default -> "[?]";
                    };
                    String owner = t.owner() != null ? " @" + t.owner() : "";
                    String blocked = (t.blockedBy() != null && !t.blockedBy().isEmpty()) ? " (blocked by: " + t.blockedBy() + ")" : "";
                    sb.append(m).append(" #").append(t.id()).append(": ").append(t.subject()).append(owner).append(blocked).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    public String claim(int tid, String owner) {
        try {
            Models.Task task = load(tid);
            Models.Task updated = new Models.Task(task.id(), task.subject(), task.description(), "in_progress", owner, task.blockedBy());
            save(updated);
            return "Claimed task #" + tid + " for " + owner;
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }
    
    public List<Models.Task> getUnclaimedTasks() {
        List<Models.Task> unclaimed = new ArrayList<>();
        try (Stream<Path> paths = Files.list(AppConfig.TASKS_DIR)) {
             paths.filter(p -> p.getFileName().toString().startsWith("task_")).forEach(p -> {
                 try {
                     Models.Task t = mapper.readValue(p.toFile(), Models.Task.class);
                     if ("pending".equals(t.status()) && t.owner() == null && (t.blockedBy() == null || t.blockedBy().isEmpty())) {
                         unclaimed.add(t);
                     }
                 } catch (Exception e) {}
             });
        } catch (Exception e) {}
        return unclaimed;
    }
}