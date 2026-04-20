package com.example.tools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Configuration
public class FileSystemTools {

    private void logTool(String tool, String output) {
        System.out.println("  [Tool] " + tool + ": " + output.substring(0, Math.min(output.length(), 100)).replace("\n", " "));
    }

    public record BashReq(String command) {}
    @Bean
    @Description("Run a shell command.")
    Function<BashReq, String> bashTool() {
        return req -> {
            try {
                Process process = new ProcessBuilder("bash", "-c", req.command())
                        .directory(new File(System.getProperty("user.dir")))
                        .redirectErrorStream(true).start();
                if (!process.waitFor(120, TimeUnit.SECONDS)) {
                    process.destroy();
                    return "Error: Timeout";
                }
                String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                String res = out.isEmpty() ? "(no output)" : out;
                logTool("bash", res);
                return res;
            } catch (Exception e) { return "Error: " + e.getMessage(); }
        };
    }

    public record ReadFileReq(String path) {}
    @Bean
    @Description("Read file contents.")
    Function<ReadFileReq, String> readFileTool() {
        return req -> {
            try { return Files.readString(Paths.get(System.getProperty("user.dir")).resolve(req.path())); }
            catch (Exception e) { return "Error: " + e.getMessage(); }
        };
    }

    public record WriteFileReq(String path, String content) {}
    @Bean
    @Description("Write content to file.")
    Function<WriteFileReq, String> writeFileTool() {
        return req -> {
            try {
                Path p = Paths.get(System.getProperty("user.dir")).resolve(req.path());
                p.getParent().toFile().mkdirs();
                Files.writeString(p, req.content());
                return "Wrote file: " + req.path();
            } catch (Exception e) { return "Error: " + e.getMessage(); }
        };
    }

    public record EditFileReq(String path, String old_text, String new_text) {}
    @Bean
    @Description("Replace exact text in file.")
    Function<EditFileReq, String> editFileTool() {
        return req -> {
            try {
                Path p = Paths.get(System.getProperty("user.dir")).resolve(req.path());
                String content = Files.readString(p);
                if (!content.contains(req.old_text())) return "Error: old_text not found";
                Files.writeString(p, content.replaceFirst(java.util.regex.Pattern.quote(req.old_text()), req.new_text()));
                return "Edited " + req.path();
            } catch (Exception e) { return "Error: " + e.getMessage(); }
        };
    }
}