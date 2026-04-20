package com.example.services;


import com.example.agent.TeammateWorker;
import com.example.config.AppConfig;
import com.example.models.Models;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeamManagerService {

    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private ApplicationContext context; // To inject TeammateWorker prototypes
    
    private final Path configPath = AppConfig.TEAM_DIR.resolve("config.json");
    private Models.TeamConfig config;

    public TeamManagerService() {
        try { Files.createDirectories(AppConfig.TEAM_DIR); } catch (Exception e) {}
    }

    private synchronized void load() {
        try {
            if (Files.exists(configPath)) {
                config = mapper.readValue(configPath.toFile(), Models.TeamConfig.class);
            } else {
                config = new Models.TeamConfig("default", new ArrayList<>());
            }
        } catch (Exception e) { config = new Models.TeamConfig("default", new ArrayList<>()); }
    }

    private synchronized void save() {
        try { Files.writeString(configPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config)); } 
        catch (Exception e) {}
    }

    public String spawn(String name, String role, String prompt) {
        load();
        List<Models.TeamMember> members = new ArrayList<>(config.members());
        Models.TeamMember existing = members.stream().filter(m -> m.name().equals(name)).findFirst().orElse(null);
        
        if (existing != null) {
            if (!existing.status().equals("idle") && !existing.status().equals("shutdown")) {
                return "Error: '" + name + "' is currently " + existing.status();
            }
            members.remove(existing);
            members.add(new Models.TeamMember(name, role, "working"));
        } else {
            members.add(new Models.TeamMember(name, role, "working"));
        }
        config = new Models.TeamConfig(config.teamName(), members);
        save();

        TeammateWorker worker = context.getBean(TeammateWorker.class);
        worker.init(name, role, prompt, config.teamName());
        new Thread(worker).start();
        
        return "Spawned '" + name + "' (role: " + role + ")";
    }

    public synchronized void setStatus(String name, String status) {
        load();
        List<Models.TeamMember> members = new ArrayList<>(config.members());
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).name().equals(name)) {
                members.set(i, new Models.TeamMember(name, members.get(i).role(), status));
                break;
            }
        }
        config = new Models.TeamConfig(config.teamName(), members);
        save();
    }

    public String listAll() {
        load();
        if (config.members().isEmpty()) return "No teammates.";
        StringBuilder sb = new StringBuilder("Team: " + config.teamName() + "\n");
        config.members().forEach(m -> sb.append("  ").append(m.name()).append(" (").append(m.role()).append("): ").append(m.status()).append("\n"));
        return sb.toString();
    }

    public List<String> memberNames() {
        load();
        return config.members().stream().map(Models.TeamMember::name).collect(Collectors.toList());
    }
}