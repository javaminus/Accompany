package com.example.services;


import com.example.models.Models;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class BackgroundService {
    
    @Autowired
    private FileOpsService fileOps;
    
    private final Map<String, Models.BackgroundTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Models.BackgroundNotification> notifications = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public String run(String command, int timeout) {
        String tid = UUID.randomUUID().toString().substring(0, 8);
        tasks.put(tid, new Models.BackgroundTask(tid, "running", command, null));
        
        executor.submit(() -> {
            String result;
            String status;
            try {
                result = fileOps.runBash(command); // reuse bash logic for timeout
                status = "completed";
            } catch (Exception e) {
                result = e.getMessage();
                status = "error";
            }
            tasks.put(tid, new Models.BackgroundTask(tid, status, command, result));
            notifications.add(new Models.BackgroundNotification(tid, status, result.substring(0, Math.min(result.length(), 500))));
        });
        
        return "Background task " + tid + " started: " + command.substring(0, Math.min(command.length(), 80));
    }

    public String check(String tid) {
        if (tid != null) {
            Models.BackgroundTask t = tasks.get(tid);
            return t != null ? "[" + t.status() + "] " + (t.result() != null ? t.result() : "(running)") : "Unknown: " + tid;
        }
        StringBuilder sb = new StringBuilder();
        tasks.forEach((k, v) -> sb.append(k).append(": [").append(v.status()).append("] ").append(v.command()).append("\n"));
        return sb.length() > 0 ? sb.toString() : "No bg tasks.";
    }

    public List<Models.BackgroundNotification> drain() {
        List<Models.BackgroundNotification> list = new ArrayList<>();
        while (!notifications.isEmpty()) {
            list.add(notifications.poll());
        }
        return list;
    }
}