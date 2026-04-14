package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

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

    // =============== Schema 定义 ===============
    public record BashRequest(String command) {}
    public record ReadFileRequest(String path, Integer limit) {}
    public record WriteFileRequest(String path, String content) {}
    public record EditFileRequest(String path, String old_text, String new_text) {}
    public record TodoRequest(List<TodoManager.PlanItem> items) {}

    private Path safePath(String p) {
        Path target = workDir.resolve(p).toAbsolutePath().normalize();
        if (!target.startsWith(workDir)) throw new IllegalArgumentException("Path escapes workspace: " + p);
        return target;
    }

    private String printAndReturn(String toolName, String output) {
        String preview = output.length() > 200 ? output.substring(0, 200) + "..." : output;
        System.out.println("\033[33m> " + toolName + ":\033[0m \n" + preview);
        return output;
    }

    // =============== 工具定义 ===============

    @Bean
    @Description("Rewrite the current session plan for multi-step work.")
    public Function<TodoRequest, String> todoTool(TodoManager todoManager) {
        return request -> {
            try {
                String result = todoManager.update(request.items());
                return printAndReturn("todo", result);
            } catch (Exception e) {
                return printAndReturn("todo", "Error: " + e.getMessage());
            }
        };
    }

    @Bean
    @Description("Run a shell command.")
    public Function<BashRequest, String> bashTool(TodoManager todoManager) {
        return request -> {
            String command = request.command();
            List<String> dangerous = List.of("rm -rf /", "sudo", "shutdown", "reboot", "> /dev/");
            if (dangerous.stream().anyMatch(command::contains)) return "Error: Dangerous command blocked";
            
            try {
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
                pb.directory(workDir.toFile());
                pb.redirectErrorStream(true);
                Process process = pb.start();
                if (!process.waitFor(120, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return printAndReturn("bash", "Error: Timeout (120s)");
                }
                String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                String result = out.isEmpty() ? "(no output)" : (out.length() > 50000 ? out.substring(0, 50000) : out);
                
                // 【核心变化】执行完常规工具后，将可能触发的 reminder 拼接到结果末尾
                return printAndReturn("bash", result) + todoManager.getReminderAndIncrement();
            } catch (Exception e) {
                return printAndReturn("bash", "Error: " + e.getMessage());
            }
        };
    }

    @Bean
    @Description("Read file contents.")
    public Function<ReadFileRequest, String> readFileTool(TodoManager todoManager) {
        return request -> {
            try {
                List<String> lines = Files.readAllLines(safePath(request.path()), StandardCharsets.UTF_8);
                if (request.limit() != null && request.limit() < lines.size()) {
                    lines = lines.subList(0, request.limit());
                    lines.add("... (" + (lines.size() - request.limit()) + " more lines)");
                }
                String text = String.join("\n", lines);
                String result = text.length() > 50000 ? text.substring(0, 50000) : text;
                return printAndReturn("read_file", result) + todoManager.getReminderAndIncrement();
            } catch (Exception e) {
                return printAndReturn("read_file", "Error: " + e.getMessage());
            }
        };
    }

    @Bean
    @Description("Write content to a file.")
    public Function<WriteFileRequest, String> writeFileTool(TodoManager todoManager) {
        return request -> {
            try {
                Path path = safePath(request.path());
                Files.createDirectories(path.getParent());
                Files.writeString(path, request.content(), StandardCharsets.UTF_8);
                String result = "Wrote " + request.content().length() + " bytes to " + request.path();
                return printAndReturn("write_file", result) + todoManager.getReminderAndIncrement();
            } catch (Exception e) {
                return printAndReturn("write_file", "Error: " + e.getMessage());
            }
        };
    }

    @Bean
    @Description("Replace exact text in a file once.")
    public Function<EditFileRequest, String> editFileTool(TodoManager todoManager) {
        return request -> {
            try {
                Path path = safePath(request.path());
                String content = Files.readString(path, StandardCharsets.UTF_8);
                int index = content.indexOf(request.old_text());
                if (index == -1) return "Error: Text not found in " + request.path();
                
                String newContent = content.substring(0, index) + request.new_text() + content.substring(index + request.old_text().length());
                Files.writeString(path, newContent, StandardCharsets.UTF_8);
                String result = "Edited " + request.path();
                return printAndReturn("edit_file", result) + todoManager.getReminderAndIncrement();
            } catch (Exception e) {
                return printAndReturn("edit_file", "Error: " + e.getMessage());
            }
        };
    }
}