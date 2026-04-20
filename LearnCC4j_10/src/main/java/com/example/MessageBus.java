package com.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Component
public class MessageBus {
    private final Path dir;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> validTypes = Set.of(
            "message", "broadcast", "shutdown_request",
            "shutdown_response", "plan_approval_response"
    );

    public MessageBus() {
        this.dir = Paths.get(System.getProperty("user.dir"), ".team", "inbox");
        this.dir.toFile().mkdirs();
    }

    public synchronized String send(String sender, String to, String content, String msgType, Map<String, Object> extra) {
        if (!validTypes.contains(msgType)) return "Error: Invalid type '" + msgType + "'";
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("type", msgType);
            msg.put("from", sender);
            msg.put("content", content);
            msg.put("timestamp", System.currentTimeMillis());
            if (extra != null) msg.putAll(extra);

            Path inboxPath = dir.resolve(to + ".jsonl");
            Files.writeString(inboxPath, mapper.writeValueAsString(msg) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "Sent " + msgType + " to " + to;
        } catch (Exception e) {
            return "Error sending message: " + e.getMessage();
        }
    }

    public synchronized String readInboxJson(String name) {
        Path inboxPath = dir.resolve(name + ".jsonl");
        if (!Files.exists(inboxPath)) return "[]";
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            for (String line : Files.readAllLines(inboxPath)) {
                if (!line.trim().isEmpty()) {
                    messages.add(mapper.readValue(line, new TypeReference<>() {
                    }));
                }
            }
            Files.writeString(inboxPath, ""); // 清空收件箱
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(messages);
        } catch (Exception e) {
            return "[]";
        }
    }

    public String broadcast(String sender, String content, List<String> teammates) {
        int count = 0;
        for (String name : teammates) {
            if (!name.equals(sender)) {
                send(sender, name, content, "broadcast", null);
                count++;
            }
        }
        return "Broadcast to " + count + " teammates";
    }
}
