package com.example.services;

import com.example.config.AppConfig;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FileOpsService {

    public Path safePath(String p) {
        Path path = AppConfig.WORKDIR.resolve(p).normalize().toAbsolutePath();
        if (!path.startsWith(AppConfig.WORKDIR.toAbsolutePath())) {
            throw new IllegalArgumentException("Path escapes workspace: " + p);
        }
        return path;
    }

    public String runBash(String command) {
        List<String> dangerous = List.of("rm -rf /", "sudo", "shutdown", "reboot", "> /dev/");
        if (dangerous.stream().anyMatch(command::contains)) {
            return "Error: Dangerous command blocked";
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.directory(AppConfig.WORKDIR.toFile());
            Process process = pb.start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Error: Timeout (120s)";
            }
            String output = new String(process.getInputStream().readAllBytes()) +
                            new String(process.getErrorStream().readAllBytes());
            return truncate(output.trim(), 50000);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public String read(String pathStr, Integer limit) {
        try {
            Path path = safePath(pathStr);
            List<String> lines = Files.readAllLines(path);
            if (limit != null && limit < lines.size()) {
                lines = lines.subList(0, limit);
                lines.add("... (" + (lines.size() - limit) + " more)");
            }
            return truncate(String.join("\n", lines), 50000);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public String write(String pathStr, String content) {
        try {
            Path path = safePath(pathStr);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            return "Wrote " + content.getBytes().length + " bytes to " + pathStr;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public String edit(String pathStr, String oldText, String newText) {
        try {
            Path path = safePath(pathStr);
            String content = Files.readString(path);
            if (!content.contains(oldText)) {
                return "Error: Text not found in " + pathStr;
            }
            Files.writeString(path, content.replaceFirst(java.util.regex.Pattern.quote(oldText), newText));
            return "Edited " + pathStr;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String truncate(String text, int maxLength) {
        return (text != null && text.length() > maxLength) ? text.substring(0, maxLength) : (text == null ? "(no output)" : text);
    }
}