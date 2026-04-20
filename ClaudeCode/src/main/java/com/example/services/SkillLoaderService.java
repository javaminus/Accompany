package com.example.services;


import com.example.config.AppConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class SkillLoaderService {
    private final Map<String, Map<String, String>> skills = new HashMap<>();

    @PostConstruct
    public void init() {
        if (Files.exists(AppConfig.SKILLS_DIR)) {
            try (Stream<Path> paths = Files.walk(AppConfig.SKILLS_DIR)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().equals("SKILL.md"))
                     .forEach(this::parseSkillFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void parseSkillFile(Path path) {
        try {
            String text = Files.readString(path);
            Pattern pattern = Pattern.compile("^---\\n(.*?)\\n---\\n(.*)", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(text);
            Map<String, String> meta = new HashMap<>();
            String body = text;

            if (matcher.find()) {
                String metaStr = matcher.group(1).trim();
                for (String line : metaStr.split("\\n")) {
                    if (line.contains(":")) {
                        String[] parts = line.split(":", 2);
                        meta.put(parts[0].trim(), parts[1].trim());
                    }
                }
                body = matcher.group(2).trim();
            }
            String name = meta.getOrDefault("name", path.getParent().getFileName().toString());
            skills.put(name, Map.of("meta", meta.toString(), "body", body, "description", meta.getOrDefault("description", "-")));
        } catch (Exception e) {}
    }

    public String descriptions() {
        if (skills.isEmpty()) return "(no skills)";
        StringBuilder sb = new StringBuilder();
        skills.forEach((name, data) -> sb.append("  - ").append(name).append(": ").append(data.get("description")).append("\n"));
        return sb.toString();
    }

    public String load(String name) {
        if (!skills.containsKey(name)) return "Error: Unknown skill. Available: " + String.join(", ", skills.keySet());
        return "<skill name=\"" + name + "\">\n" + skills.get(name).get("body") + "\n</skill>";
    }
}