package com.example;


import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SkillRegistry {

    private final Map<String, SkillDocument> documents = new HashMap<>();

    @PostConstruct
    public void init() {
        loadAll();
    }

    private void loadAll() {
        if (!Files.exists(WorkspaceContext.SKILLS_DIR)) return;

        try (var paths = Files.walk(WorkspaceContext.SKILLS_DIR)) {
            paths.filter(p -> p.getFileName().toString().equals("SKILL.md"))
                 .forEach(this::parseAndStore);
        } catch (IOException e) {
            System.err.println("Failed to read skills directory: " + e.getMessage());
        }
    }

    private void parseAndStore(Path path) {
        try {
            String text = Files.readString(path);
            Matcher m = Pattern.compile("^---\n(.*?)\n---\n(.*)", Pattern.DOTALL).matcher(text);

            Map<String, String> meta = new HashMap<>();
            String body = text;

            if (m.find()) {
                String frontmatter = m.group(1).trim();
                for (String line : frontmatter.split("\\r?\\n")) {
                    if (!line.contains(":")) continue;
                    String[] parts = line.split(":", 2);
                    meta.put(parts[0].trim(), parts[1].trim());
                }
                body = m.group(2).trim();
            }

            String name = meta.getOrDefault("name", path.getParent().getFileName().toString());
            String description = meta.getOrDefault("description", "No description");
            SkillManifest manifest = new SkillManifest(name, description, path);
            documents.put(name, new SkillDocument(manifest, body));

        } catch (IOException e) {
            System.err.println("Failed to read skill file: " + path);
        }
    }

    public String describeAvailable() {
        if (documents.isEmpty()) return "(no skills available)";
        return documents.values().stream()
                .map(d -> "- " + d.manifest().name() + ": " + d.manifest().description())
                .sorted()
                .collect(Collectors.joining("\n"));
    }

    public String loadFullText(String name) {
        SkillDocument document = documents.get(name);
        if (document == null) {
            String known = documents.keySet().stream().sorted().collect(Collectors.joining(", "));
            if (known.isEmpty()) known = "(none)";
            return "Error: Unknown skill '" + name + "'. Available skills: " + known;
        }
        return String.format("<skill name=\"%s\">\n%s\n</skill>", document.manifest().name(), document.body());
    }
}