package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskManagerService {

    private final Path tasksDir = Paths.get(System.getProperty("user.dir"), ".tasks");
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private int nextId;

    // 内部任务数据结构，映射 Python 的 dict
    public static class TaskData {
        public int id;
        public String subject;
        public String description;
        public String status;
        public List<Integer> blockedBy = new ArrayList<>();
        public String owner = "";
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(tasksDir);
        this.nextId = getMaxId() + 1;
    }

    private int getMaxId() throws IOException {
        int max = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tasksDir, "task_*.json")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                String num = name.substring(5, name.length() - 5);
                max = Math.max(max, Integer.parseInt(num));
            }
        }
        return max;
    }

    private TaskData load(int taskId) throws IOException {
        Path path = tasksDir.resolve("task_" + taskId + ".json");
        if (!Files.exists(path)) throw new IllegalArgumentException("Task " + taskId + " not found");
        return mapper.readValue(path.toFile(), TaskData.class);
    }

    private void save(TaskData task) throws IOException {
        Path path = tasksDir.resolve("task_" + task.id + ".json");
        mapper.writeValue(path.toFile(), task);
    }

    public String create(String subject, String description) throws Exception {
        TaskData task = new TaskData();
        task.id = nextId++;
        task.subject = subject;
        task.description = description != null ? description : "";
        task.status = "pending";
        save(task);
        return mapper.writeValueAsString(task);
    }

    public String get(int taskId) throws Exception {
        return mapper.writeValueAsString(load(taskId));
    }

    public String update(int taskId, String status, List<Integer> addBlockedBy, List<Integer> removeBlockedBy) throws Exception {
        TaskData task = load(taskId);
        
        if (status != null) {
            if (!List.of("pending", "in_progress", "completed").contains(status)) {
                throw new IllegalArgumentException("Invalid status: " + status);
            }
            task.status = status;
            if ("completed".equals(status)) {
                clearDependency(taskId);
            }
        }
        
        if (addBlockedBy != null) {
            Set<Integer> set = new HashSet<>(task.blockedBy);
            set.addAll(addBlockedBy);
            task.blockedBy = new ArrayList<>(set);
        }
        
        if (removeBlockedBy != null) {
            task.blockedBy.removeAll(removeBlockedBy);
        }
        
        save(task);
        return mapper.writeValueAsString(task);
    }

    private void clearDependency(int completedId) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tasksDir, "task_*.json")) {
            for (Path p : stream) {
                TaskData task = mapper.readValue(p.toFile(), TaskData.class);
                if (task.blockedBy.contains(completedId)) {
                    task.blockedBy.remove(Integer.valueOf(completedId));
                    save(task);
                }
            }
        }
    }

    public String listAll() throws Exception {
        List<TaskData> tasks = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tasksDir, "task_*.json")) {
            for (Path p : stream) {
                tasks.add(mapper.readValue(p.toFile(), TaskData.class));
            }
        }
        if (tasks.isEmpty()) return "No tasks.";
        
        tasks.sort(Comparator.comparingInt(t -> t.id));
        
        return tasks.stream().map(t -> {
            String marker = switch (t.status) {
                case "pending" -> "[ ]";
                case "in_progress" -> "[>]";
                case "completed" -> "[x]";
                default -> "[?]";
            };
            String blocked = t.blockedBy.isEmpty() ? "" : " (blocked by: " + t.blockedBy + ")";
            return marker + " #" + t.id + ": " + t.subject + blocked;
        }).collect(Collectors.joining("\n"));
    }
}