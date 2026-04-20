package com.example;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

@Configuration
public class AgentTools {

    // --- Records for Tool Parameters ---

    public record LoadSkillRequest(String name) {}
    public record BashRequest(String command) {}
    public record ReadFileRequest(String path, Integer limit) {}
    public record WriteFileRequest(String path, String content) {}
    public record EditFileRequest(String path, String old_text, String new_text) {}

    // --- Tool Beans ---

    @Bean
    @Description("Load the full body of a named skill into the current context.")
    public Function<LoadSkillRequest, String> loadSkill(SkillRegistry skillRegistry) {
        return request -> {
            System.out.println("> load_skill: " + request.name());
            return skillRegistry.loadFullText(request.name());
        };
    }

    @Bean
    @Description("Run a shell command.")
    public Function<BashRequest, String> bash() {
        return request -> {
            String cmdStr = request.command();
            System.out.println("> bash: " + (cmdStr.length() > 50 ? cmdStr.substring(0, 50) + "..." : cmdStr));
            
            List<String> dangerous = List.of("rm -rf /", "sudo", "shutdown", "reboot", "> /dev/");
            if (dangerous.stream().anyMatch(cmdStr::contains)) {
                return "Error: Dangerous command blocked";
            }

            try {
                ProcessBuilder pb = new ProcessBuilder();
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    pb.command("cmd.exe", "/c", cmdStr);
                } else {
                    pb.command("bash", "-c", cmdStr);
                }
                pb.directory(new File(WorkspaceContext.WORKDIR.toString()));
                pb.redirectErrorStream(true);

                Process process = pb.start();
                boolean finished = process.waitFor(120, TimeUnit.SECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    return "Error: Timeout (120s)";
                }

                String output = new String(process.getInputStream().readAllBytes()).trim();
                return output.isEmpty() ? "(no output)" : WorkspaceContext.truncateOutput(output);
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        };
    }

    @Bean
    @Description("Read file contents.")
    public Function<ReadFileRequest, String> readFile() {
        return request -> {
            System.out.println("> read_file: " + request.path());
            try {
                Path path = WorkspaceContext.safePath(request.path());
                List<String> lines = Files.readAllLines(path);

                if (request.limit() != null && request.limit() > 0 && request.limit() < lines.size()) {
                    int remaining = lines.size() - request.limit();
                    lines = lines.subList(0, request.limit());
                    lines.add("... (" + remaining + " more lines)");
                }

                return WorkspaceContext.truncateOutput(String.join("\n", lines));
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        };
    }

    @Bean
    @Description("Write content to a file.")
    public Function<WriteFileRequest, String> writeFile() {
        return request -> {
            System.out.println("> write_file: " + request.path());
            try {
                Path path = WorkspaceContext.safePath(request.path());
                Files.createDirectories(path.getParent());
                Files.writeString(path, request.content());
                return "Wrote " + request.content().getBytes().length + " bytes to " + request.path();
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        };
    }

    @Bean
    @Description("Replace exact text in a file once.")
    public Function<EditFileRequest, String> editFile() {
        return request -> {
            System.out.println("> edit_file: " + request.path());
            try {
                Path path = WorkspaceContext.safePath(request.path());
                String content = Files.readString(path);

                if (!content.contains(request.old_text())) {
                    return "Error: Text not found in " + request.path();
                }

                String updatedContent = content.replaceFirst(Pattern.quote(request.old_text()), request.new_text());
                Files.writeString(path, updatedContent);
                return "Edited " + request.path();
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        };
    }
}