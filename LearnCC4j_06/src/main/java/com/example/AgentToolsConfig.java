package com.example;

import com.example.MemoryCompactService;
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

    // 暴露为全局单例，打破循环依赖
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    public record BashRequest(String command) {}
    public record ReadFileRequest(String path, Integer limit) {}
    public record CompactRequest(@Description("What to preserve in the summary") String focus) {}

    private Path safePath(String p) {
        Path target = workDir.resolve(p).toAbsolutePath().normalize();
        if (!target.startsWith(workDir)) throw new IllegalArgumentException("Path escapes workspace");
        return target;
    }

    @Bean
    @Description("Trigger manual conversation compression.")
    public Function<CompactRequest, String> compactTool(MemoryCompactService compactService) {
        return request -> {
            compactService.setManualCompactTriggered(true);
            System.out.println("\033[33m> compact: \033[0m Compressing...");
            return "Manual compression requested.";
        };
    }

    @Bean
    @Description("Run a shell command.")
    public Function<BashRequest, String> bashTool() {
        return request -> {
            try {
                if (List.of("rm -rf /", "sudo", "reboot").stream().anyMatch(request.command()::contains))
                    return "Error: Dangerous command";
                Process p = new ProcessBuilder("bash", "-c", request.command()).directory(workDir.toFile())
                        .redirectErrorStream(true).start();
                if (!p.waitFor(120, TimeUnit.SECONDS)) { p.destroyForcibly(); return "Error: Timeout"; }
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                System.out.println("\033[33m> bash:\033[0m\n" + (out.length() > 200 ? out.substring(0, 200) : out));
                return out.isEmpty() ? "(no output)" : (out.length() > 50000 ? out.substring(0, 50000) : out);
            } catch (Exception e) { return "Error: " + e.getMessage(); }
        };
    }

    @Bean
    @Description("Read file contents.")
    public Function<ReadFileRequest, String> readFileTool() {
        return request -> {
            try {
                List<String> lines = Files.readAllLines(safePath(request.path()), StandardCharsets.UTF_8);
                String text = String.join("\n", request.limit() != null && request.limit() < lines.size()
                        ? lines.subList(0, request.limit()) : lines);
                System.out.println("\033[33m> read_file: \033[0m" + request.path());
                return text;
            } catch (Exception e) { return "Error: " + e.getMessage(); }
        };
    }
}