package com.example.services;


import com.example.config.AppConfig;
import com.example.models.Models;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MessageBusService {

    @Autowired
    private ObjectMapper mapper;

    public MessageBusService() {
        try { Files.createDirectories(AppConfig.INBOX_DIR); } catch (Exception e) {}
    }

    public String send(String sender, String to, String content, String msgType, Map<String, Object> extra) {
        try {
            Models.MessageDto msg = new Models.MessageDto(msgType, sender, content, System.currentTimeMillis() / 1000, extra);
            Path path = AppConfig.INBOX_DIR.resolve(to + ".jsonl");
            Files.writeString(path, mapper.writeValueAsString(msg) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "Sent " + msgType + " to " + to;
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    public List<Models.MessageDto> readInbox(String name) {
        Path path = AppConfig.INBOX_DIR.resolve(name + ".jsonl");
        List<Models.MessageDto> msgs = new ArrayList<>();
        if (!Files.exists(path)) return msgs;
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                if (!line.isBlank()) msgs.add(mapper.readValue(line, Models.MessageDto.class));
            }
            Files.writeString(path, ""); // Drain
        } catch (Exception e) {}
        return msgs;
    }

    public String broadcast(String sender, String content, List<String> names) {
        int count = 0;
        for (String n : names) {
            if (!n.equals(sender)) {
                send(sender, n, content, "broadcast", null);
                count++;
            }
        }
        return "Broadcast to " + count + " teammates";
    }
}