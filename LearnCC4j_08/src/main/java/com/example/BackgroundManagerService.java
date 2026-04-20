package com.example;

import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class BackgroundManagerService {

    public record Notification(String taskId, String status, String command, String result) {}

    public static class TaskInfo {
        public String status = "running";
        public String command;
        public String result;
        public TaskInfo(String command) { this.command = command; }
    }

    private final Map<String, TaskInfo> tasks = new ConcurrentHashMap<>();
    private final Queue<Notification> notificationQueue = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final String workDir = System.getProperty("user.dir");

    // 提交异步任务
    public String run(String command) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        tasks.put(taskId, new TaskInfo(command));
        
        executor.submit(() -> execute(taskId, command));
        
        return "Background task " + taskId + " started: " + 
               (command.length() > 80 ? command.substring(0, 80) + "..." : command);
    }

    // 线程池中实际执行的逻辑
    private void execute(String taskId, String command) {
        TaskInfo info = tasks.get(taskId);
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.directory(new File(workDir));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            String output;
            
            if (finished) {
                output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                info.status = "completed";
            } else {
                process.destroyForcibly();
                output = "Error: Timeout (300s)";
                info.status = "timeout";
            }
            
            info.result = output.isEmpty() ? "(no output)" : 
                         (output.length() > 50000 ? output.substring(0, 50000) : output);
                         
            notificationQueue.offer(new Notification(taskId, info.status, command, info.result));
        } catch (Exception e) {
            info.status = "error";
            info.result = "Error: " + e.getMessage();
            notificationQueue.offer(new Notification(taskId, info.status, command, info.result));
        }
    }

    // 检查任务状态
    public String check(String taskId) {
        if (taskId != null && !taskId.isEmpty()) {
            TaskInfo t = tasks.get(taskId);
            if (t == null) return "Error: Unknown task " + taskId;
            return "[" + t.status + "] " + (t.command.length() > 60 ? t.command.substring(0, 60) : t.command) + "\n" + 
                   (t.result != null ? t.result : "(running)");
        }
        if (tasks.isEmpty()) return "No background tasks.";
        return tasks.entrySet().stream()
                .map(e -> e.getKey() + ": [" + e.getValue().status + "] " + 
                          (e.getValue().command.length() > 60 ? e.getValue().command.substring(0, 60) : e.getValue().command))
                .collect(Collectors.joining("\n"));
    }

    // 排空并获取所有通知 (线程安全)
    public List<Notification> drainNotifications() {
        List<Notification> notifs = new ArrayList<>();
        Notification n;
        while ((n = notificationQueue.poll()) != null) {
            notifs.add(n);
        }
        return notifs;
    }
}