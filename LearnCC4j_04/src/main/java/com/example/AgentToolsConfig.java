package com.example;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.beans.factory.ObjectProvider;
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
    
    // 子 Agent 调度 Schema
    public record TaskRequest(String prompt, @Description("Short description of the task") String description) {}

    private Path safePath(String p) {
        Path target = workDir.resolve(p).toAbsolutePath().normalize();
        if (!target.startsWith(workDir)) throw new IllegalArgumentException("Path escapes workspace: " + p);
        return target;
    }

    // =============== 基础工具 (Parent 和 Child 共用) ===============

    @Bean
    @Description("Run a shell command.")
    public Function<BashRequest, String> bashTool() {
        return request -> {
            String cmd = request.command();
            if (List.of("rm -rf /", "sudo", "shutdown", "reboot", "> /dev/").stream().anyMatch(cmd::contains)) 
                return "Error: Dangerous command blocked";
            try {
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd).directory(workDir.toFile());
                pb.redirectErrorStream(true);
                Process process = pb.start();
                if (!process.waitFor(120, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return "Error: Timeout (120s)";
                }
                String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
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
                if (request.limit() != null && request.limit() < lines.size()) {
                    lines = lines.subList(0, request.limit());
                    lines.add("... (" + (lines.size() - request.limit()) + " more lines)");
                }
                String text = String.join("\n", lines);
                return text.length() > 50000 ? text.substring(0, 50000) : text;
            } catch (Exception e) { return "Error: " + e.getMessage(); }
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
                return "Wrote " + request.content().length() + " bytes";
            } catch (Exception e) { return "Error: " + e.getMessage(); }
        };
    }

    @Bean
    @Description("Replace exact text in file.")
    public Function<EditFileRequest, String> editFileTool() {
        return request -> {
            try {
                Path path = safePath(request.path());
                String content = Files.readString(path, StandardCharsets.UTF_8);
                int index = content.indexOf(request.old_text());
                if (index == -1) return "Error: Text not found in " + request.path();
                String newContent = content.substring(0, index) + request.new_text() + content.substring(index + request.old_text().length());
                Files.writeString(path, newContent, StandardCharsets.UTF_8);
                return "Edited " + request.path();
            } catch (Exception e) { return "Error: " + e.getMessage(); }
        };
    }

    // =============== Subagent 调度工具 (仅 Parent 拥有) ===============

    @Bean
    @Description("Spawn a subagent with fresh context. It shares the filesystem but not conversation history.")
    public Function<TaskRequest, String> taskTool(ObjectProvider<ChatClient.Builder> builderProvider) {
        return request -> {
            String desc = request.description() != null ? request.description() : "subtask";
            String preview = request.prompt().length() > 80 ? request.prompt().substring(0, 80) : request.prompt();
            System.out.println("\033[33m> task (" + desc + "): \033[0m" + preview);

            // 【核心】：利用 ObjectProvider 获取全新的 Builder，构建隔离的子 Agent
            String subAgentSystem = "You are a coding subagent at " + workDir + ". Complete the given task, then summarize your findings.";
            ChatClient childClient = builderProvider.getObject()
                    .defaultSystem(subAgentSystem)
                    // 注意：ChildTools 中不包含 taskTool，避免无限递归创建子进程！
                    .defaultFunctions("bashTool", "readFileTool", "writeFileTool", "editFileTool")
                    // 注意：赋予全新的 InMemoryChatMemory，实现上下文隔离！
                    .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                    .build();

            try {
                // 子 Agent 自动执行内部循环，直到完成任务并返回 Summary 字符串
                String summary = childClient.prompt()
                        .user(request.prompt())
                        .call()
                        .content();

                String summaryPreview = summary.length() > 200 ? summary.substring(0, 200) + "..." : summary;
                System.out.println("  " + summaryPreview);
                
                // 返回的仅仅是摘要，父 Agent 的上下文保持纯净
                return summary;
            } catch (Exception e) {
                return "Subagent Error: " + e.getMessage();
            }
        };
    }
}