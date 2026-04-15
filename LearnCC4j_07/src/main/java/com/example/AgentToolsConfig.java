package com.example;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    // ================= Schema 定义 =================
    public record BashRequest(String command) {}
    public record ReadFileRequest(String path, Integer limit) {}
    public record WriteFileRequest(String path, String content) {}
    public record EditFileRequest(String path, String old_text, String new_text) {}

    // Task 相关 Schema
    public record TaskCreateRequest(String subject, String description) {}
    public record TaskUpdateRequest(
            @JsonProperty("task_id") Integer taskId, 
            String status, 
            List<Integer> addBlockedBy, 
            List<Integer> removeBlockedBy
    ) {}
    // 无参数请求使用占位符
    public record TaskListRequest() {} 
    public record TaskGetRequest(@JsonProperty("task_id") Integer taskId) {}

    // ================= 辅助方法 =================
    private Path safePath(String p) {
        Path target = workDir.resolve(p).toAbsolutePath().normalize();
        if (!target.startsWith(workDir)) throw new IllegalArgumentException("Path escapes workspace");
        return target;
    }

    private String printAndReturn(String name, String output) {
        System.out.println("\033[33m> " + name + ":\033[0m");
        System.out.println(output.length() > 200 ? output.substring(0, 200) + "..." : output);
        return output;
    }

    // ================= 系统基础工具 =================
    @Bean
    @Description("Run a shell command.")
    public Function<BashRequest, String> bashTool() {
        return req -> {
            if (List.of("rm -rf /", "sudo", "shutdown").stream().anyMatch(req.command()::contains)) return "Error: Dangerous command blocked";
            try {
                Process p = new ProcessBuilder("bash", "-c", req.command()).directory(workDir.toFile()).redirectErrorStream(true).start();
                if (!p.waitFor(120, TimeUnit.SECONDS)) { p.destroyForcibly(); return printAndReturn("bash", "Error: Timeout (120s)"); }
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                out = out.isEmpty() ? "(no output)" : (out.length() > 50000 ? out.substring(0, 50000) : out);
                return printAndReturn("bash", out);
            } catch (Exception e) { return printAndReturn("bash", "Error: " + e.getMessage()); }
        };
    }

    @Bean
    @Description("Read file contents.")
    public Function<ReadFileRequest, String> readFileTool() {
        return req -> {
            try {
                List<String> lines = Files.readAllLines(safePath(req.path()), StandardCharsets.UTF_8);
                String text = String.join("\n", req.limit() != null && req.limit() < lines.size() ? lines.subList(0, req.limit()) : lines);
                return printAndReturn("read_file", text.length() > 50000 ? text.substring(0, 50000) : text);
            } catch (Exception e) { return printAndReturn("read_file", "Error: " + e.getMessage()); }
        };
    }

    @Bean
    @Description("Write content to file.")
    public Function<WriteFileRequest, String> writeFileTool() {
        return req -> {
            try {
                Path path = safePath(req.path()); Files.createDirectories(path.getParent());
                Files.writeString(path, req.content(), StandardCharsets.UTF_8);
                return printAndReturn("write_file", "Wrote " + req.content().length() + " bytes");
            } catch (Exception e) { return printAndReturn("write_file", "Error: " + e.getMessage()); }
        };
    }

    @Bean
    @Description("Replace exact text in file.")
    public Function<EditFileRequest, String> editFileTool() {
        return req -> {
            try {
                Path path = safePath(req.path()); String c = Files.readString(path, StandardCharsets.UTF_8);
                int idx = c.indexOf(req.old_text());
                if (idx == -1) return printAndReturn("edit_file", "Error: Text not found in " + req.path());
                Files.writeString(path, c.substring(0, idx) + req.new_text() + c.substring(idx + req.old_text().length()), StandardCharsets.UTF_8);
                return printAndReturn("edit_file", "Edited " + req.path());
            } catch (Exception e) { return printAndReturn("edit_file", "Error: " + e.getMessage()); }
        };
    }

    // ================= Task 持久化工具 =================
    @Bean
    @Description("Create a new task.")
    public Function<TaskCreateRequest, String> taskCreateTool(TaskManagerService service) {
        return req -> {
            try { return printAndReturn("task_create", service.create(req.subject(), req.description())); }
            catch (Exception e) { return printAndReturn("task_create", "Error: " + e.getMessage()); }
        };
    }

    @Bean
    @Description("Update a task's status or dependencies.")
    public Function<TaskUpdateRequest, String> taskUpdateTool(TaskManagerService service) {
        return req -> {
            try { return printAndReturn("task_update", service.update(req.taskId(), req.status(), req.addBlockedBy(), req.removeBlockedBy())); }
            catch (Exception e) { return printAndReturn("task_update", "Error: " + e.getMessage()); }
        };
    }

    @Bean
    @Description("List all tasks with status summary.")
    public Function<TaskListRequest, String> taskListTool(TaskManagerService service) {
        return req -> {
            try { return printAndReturn("task_list", service.listAll()); }
            catch (Exception e) { return printAndReturn("task_list", "Error: " + e.getMessage()); }
        };
    }

    @Bean
    @Description("Get full details of a task by ID.")
    public Function<TaskGetRequest, String> taskGetTool(TaskManagerService service) {
        return req -> {
            try { return printAndReturn("task_get", service.get(req.taskId())); }
            catch (Exception e) { return printAndReturn("task_get", "Error: " + e.getMessage()); }
        };
    }
}