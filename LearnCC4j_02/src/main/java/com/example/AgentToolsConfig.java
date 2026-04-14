package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Configuration
public class AgentToolsConfig {

    private final Path workDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();

    // =============== Schema 定义 (Records) ===============
    public record BashRequest(String command) {}
    public record ReadFileRequest(String path, Integer limit) {}
    public record WriteFileRequest(String path, String content) {}
    public record EditFileRequest(String path, String old_text, String new_text) {}

    // =============== 核心安全方法 ===============
    private Path safePath(String p) {
        Path targetPath = workDir.resolve(p).toAbsolutePath().normalize();
        if (!targetPath.startsWith(workDir)) {
            throw new IllegalArgumentException("Path escapes workspace: " + p);
        }
        return targetPath;
    }

    // =============== Tools 定义 ===============

    @Bean
    @Description("Run a shell command.")
    public Function<BashRequest, String> bashTool() {
        return request -> {
            String command = request.command();
            List<String> dangerous = List.of("rm -rf /", "sudo", "shutdown", "reboot", "> /dev/");
            if (dangerous.stream().anyMatch(command::contains)) {
                return "Error: Dangerous command blocked";
            }
            try {
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
                pb.directory(workDir.toFile());
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                if (!process.waitFor(120, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return "Error: Timeout (120s)";
                }
                
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                String result = output.isEmpty() ? "(no output)" : output;
                return result.length() > 50000 ? result.substring(0, 50000) : result;
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        };
    }

    @Bean
    @Description("Read file contents.")
    public Function<ReadFileRequest, String> readFileTool() {
        return request -> {
            try {
                Path path = safePath(request.path());
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                
                if (request.limit() != null && request.limit() < lines.size()) {
                    lines = lines.subList(0, request.limit());
                    lines.add("... (" + (lines.size() - request.limit()) + " more lines)");
                }
                
                String text = String.join("\n", lines);
                return text.length() > 50000 ? text.substring(0, 50000) : text;
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        };
    }

    @Bean
    @Description("Write content to file.")
    public Function<WriteFileRequest, String> writeFileTool() {
        return request -> {
            try {
                Path path = safePath(request.path());
                Files.createDirectories(path.getParent());
                Files.writeString(path, request.content(), StandardCharsets.UTF_8);
                return "Wrote " + request.content().length() + " bytes to " + request.path();
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        };
    }

    @Bean
    @Description("Replace exact text in file.")
    public Function<EditFileRequest, String> editFileTool() {
        return request -> {
            try {
                Path path = safePath(request.path());
                String content = Files.readString(path, StandardCharsets.UTF_8);
                
                // 模拟 Python 中的 content.replace(old_text, new_text, 1) 只替换第一次出现
                int index = content.indexOf(request.old_text());
                if (index == -1) {
                    return "Error: Text not found in " + request.path();
                }
                
                String newContent = content.substring(0, index) 
                                  + request.new_text() 
                                  + content.substring(index + request.old_text().length());
                                  
                Files.writeString(path, newContent, StandardCharsets.UTF_8);
                return "Edited " + request.path();
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        };
    }
}