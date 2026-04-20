package com.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
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

    @Bean
    public ChatMemory chatMemory(BackgroundManagerService bgService) {
        // 使用我们的包装类套住原生的 InMemoryChatMemory
        return new NotificationAwareChatMemory(new InMemoryChatMemory(), bgService);
    }

    // ================= Schema =================
    public record BashRequest(String command) {}
    public record ReadFileRequest(String path, Integer limit) {}
    public record WriteFileRequest(String path, String content) {}
    public record EditFileRequest(String path, String old_text, String new_text) {}
    
    public record BackgroundRunRequest(String command) {}
    public record CheckBackgroundRequest(@JsonProperty("task_id") String taskId) {}

    // ================= Tools =================
    @Bean
    @Description("Run command in background thread. Returns task_id immediately.")
    public Function<BackgroundRunRequest, String> backgroundRunTool(BackgroundManagerService bg) {
        return req -> bg.run(req.command());
    }

    @Bean
    @Description("Check background task status. Omit task_id to list all.")
    public Function<CheckBackgroundRequest, String> checkBackgroundTool(BackgroundManagerService bg) {
        return req -> bg.check(req.taskId());
    }

    @Bean
    @Description("Run a shell command (blocking).")
    public Function<BashRequest, String> bashTool() {
        return req -> {
            try {
                if (List.of("rm -rf /", "sudo", "reboot").stream().anyMatch(req.command()::contains)) return "Error: Blocked";
                Process p = new ProcessBuilder("bash", "-c", req.command()).directory(workDir.toFile()).redirectErrorStream(true).start();
                if (!p.waitFor(120, TimeUnit.SECONDS)) { p.destroyForcibly(); return "Error: Timeout"; }
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                System.out.println("\033[33m> bash:\033[0m\n" + (out.length() > 200 ? out.substring(0, 200) : out));
                return out.isEmpty() ? "(no output)" : (out.length() > 50000 ? out.substring(0, 50000) : out);
            } catch (Exception e) { return "Error: " + e.getMessage(); }
        };
    }

    // 省略 read_file, write_file, edit_file (与之前章节完全一致)
    @Bean @Description("Read file contents.") public Function<ReadFileRequest, String> readFileTool() { return r -> "Simulated read"; }
    @Bean @Description("Write content to file.") public Function<WriteFileRequest, String> writeFileTool() { return r -> "Simulated write"; }
    @Bean @Description("Replace exact text in file.") public Function<EditFileRequest, String> editFileTool() { return r -> "Simulated edit"; }
}