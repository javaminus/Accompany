package com.example.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class WorktreeManager {
    private final Path repoRoot;
    private final Path dir;
    private final Path indexPath;
    private final TaskManager tasks;
    private final EventBus events;
    private final boolean gitAvailable;
    private final ObjectMapper mapper = new ObjectMapper();

    public WorktreeManager(ProjectEnv env, TaskManager tasks, EventBus events) {
        this.repoRoot = env.getRepoRoot();
        this.tasks = tasks;
        this.events = events;
        this.dir = repoRoot.resolve(".worktrees");
        this.dir.toFile().mkdirs();
        this.indexPath = dir.resolve("index.json");
        if (!Files.exists(this.indexPath)) {
            try { Files.writeString(this.indexPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("worktrees", new ArrayList<>()))); } catch (Exception ignored) {}
        }
        this.gitAvailable = checkGitRepo();
    }

    public Path getRepoRoot() { return repoRoot; }
    public boolean isGitAvailable() { return gitAvailable; }

    private boolean checkGitRepo() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "--is-inside-work-tree").directory(repoRoot.toFile()).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) { return false; }
    }

    private String runGit(List<String> args) {
        if (!gitAvailable) throw new RuntimeException("Not in a git repository. worktree tools require git.");
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(args);
            Process process = new ProcessBuilder(command).directory(repoRoot.toFile()).redirectErrorStream(true).start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new RuntimeException("Timeout"); }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) throw new RuntimeException(out.isEmpty() ? "git command failed" : out);
            return out.isEmpty() ? "(no output)" : out;
        } catch (Exception e) { throw new RuntimeException(e.getMessage()); }
    }

    private Map<String, Object> loadIndex() {
        try { return mapper.readValue(indexPath.toFile(), Map.class); } catch (Exception e) { return new HashMap<>(); }
    }
    private void saveIndex(Map<String, Object> data) {
        try { mapper.writerWithDefaultPrettyPrinter().writeValue(indexPath.toFile(), data); } catch (Exception ignored) {}
    }

    private Map<String, Object> find(String name) {
        Map<String, Object> idx = loadIndex();
        List<Map<String, Object>> wts = (List<Map<String, Object>>) idx.getOrDefault("worktrees", new ArrayList<>());
        return wts.stream().filter(w -> name.equals(w.get("name"))).findFirst().orElse(null);
    }

    public synchronized String create(String name, Integer taskId, String baseRef) throws JsonProcessingException {
        if (!name.matches("[A-Za-z0-9._-]{1,40}")) throw new IllegalArgumentException("Invalid worktree name.");
        if (find(name) != null) throw new IllegalArgumentException("Worktree '" + name + "' already exists");
        if (taskId != null && !tasks.exists(taskId)) throw new IllegalArgumentException("Task " + taskId + " not found");

        Path path = dir.resolve(name);
        String branch = "wt/" + name;
        Map<String, Object> taskMap = taskId != null ? Map.of("id", taskId) : Map.of();
        events.emit("worktree.create.before", taskMap, Map.of("name", name, "base_ref", baseRef), null);

        try {
            runGit(List.of("worktree", "add", "-b", branch, path.toString(), baseRef));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("path", path.toString());
            entry.put("branch", branch);
            entry.put("task_id", taskId);
            entry.put("status", "active");
            entry.put("created_at", System.currentTimeMillis() / 1000.0);

            Map<String, Object> idx = loadIndex();
            ((List<Map<String, Object>>) idx.get("worktrees")).add(entry);
            saveIndex(idx);

            if (taskId != null) tasks.bindWorktree(taskId, name, "");

            events.emit("worktree.create.after", taskMap, entry, null);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entry);
        } catch (Exception e) {
            events.emit("worktree.create.failed", taskMap, Map.of("name", name, "base_ref", baseRef), e.getMessage());
            throw e;
        }
    }

    public String listAll() {
        List<Map<String, Object>> wts = (List<Map<String, Object>>) loadIndex().getOrDefault("worktrees", new ArrayList<>());
        if (wts.isEmpty()) return "No worktrees in index.";
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> wt : wts) {
            String suffix = wt.get("task_id") != null ? " task=" + wt.get("task_id") : "";
            lines.add(String.format("[%s] %s -> %s (%s)%s", wt.getOrDefault("status", "unknown"), wt.get("name"), wt.get("path"), wt.getOrDefault("branch", "-"), suffix));
        }
        return String.join("\n", lines);
    }

    public String status(String name) {
        Map<String, Object> wt = find(name);
        if (wt == null) return "Error: Unknown worktree '" + name + "'";
        try {
            Process p = new ProcessBuilder("git", "status", "--short", "--branch").directory(new File((String) wt.get("path"))).redirectErrorStream(true).start();
            if (!p.waitFor(60, TimeUnit.SECONDS)) return "Error: Timeout";
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return out.isEmpty() ? "Clean worktree" : out;
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    // 注意：这里是在隔离区的工作树路径下运行
    public String runCommand(String name, String command) {
        if (List.of("rm -rf /", "sudo", "shutdown", "reboot", "> /dev/").stream().anyMatch(command::contains)) {
            return "Error: Dangerous command blocked";
        }
        Map<String, Object> wt = find(name);
        if (wt == null) return "Error: Unknown worktree '" + name + "'";
        try {
            Process p = new ProcessBuilder("bash", "-c", command).directory(new File((String) wt.get("path"))).redirectErrorStream(true).start();
            if (!p.waitFor(300, TimeUnit.SECONDS)) { p.destroyForcibly(); return "Error: Timeout (300s)"; }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            out = out.length() > 50000 ? out.substring(0, 50000) : out;
            return out.isEmpty() ? "(no output)" : out;
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    public synchronized String remove(String name, boolean force, boolean completeTask) {
        Map<String, Object> wt = find(name);
        if (wt == null) return "Error: Unknown worktree '" + name + "'";

        Map<String, Object> taskMap = wt.get("task_id") != null ? Map.of("id", wt.get("task_id")) : Map.of();
        events.emit("worktree.remove.before", taskMap, Map.of("name", name, "path", wt.get("path")), null);

        try {
            List<String> args = new ArrayList<>(List.of("worktree", "remove"));
            if (force) args.add("--force");
            args.add((String) wt.get("path"));
            runGit(args);

            if (completeTask && wt.get("task_id") != null) {
                int taskId = (Integer) wt.get("task_id");
                tasks.update(taskId, "completed", null);
                tasks.unbindWorktree(taskId);
                events.emit("task.completed", Map.of("id", taskId, "status", "completed"), Map.of("name", name), null);
            }

            Map<String, Object> idx = loadIndex();
            for (Map<String, Object> item : (List<Map<String, Object>>) idx.get("worktrees")) {
                if (name.equals(item.get("name"))) {
                    item.put("status", "removed");
                    item.put("removed_at", System.currentTimeMillis() / 1000.0);
                }
            }
            saveIndex(idx);
            events.emit("worktree.remove.after", taskMap, Map.of("name", name, "status", "removed"), null);
            return "Removed worktree '" + name + "'";
        } catch (Exception e) {
            events.emit("worktree.remove.failed", taskMap, Map.of("name", name), e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    public synchronized String keep(String name) {
        Map<String, Object> wt = find(name);
        if (wt == null) return "Error: Unknown worktree '" + name + "'";

        Map<String, Object> idx = loadIndex();
        Map<String, Object> kept = null;
        for (Map<String, Object> item : (List<Map<String, Object>>) idx.get("worktrees")) {
            if (name.equals(item.get("name"))) {
                item.put("status", "kept");
                item.put("kept_at", System.currentTimeMillis() / 1000.0);
                kept = item;
            }
        }
        saveIndex(idx);
        events.emit("worktree.keep", wt.get("task_id") != null ? Map.of("id", wt.get("task_id")) : Map.of(), Map.of("name", name, "status", "kept"), null);
        try { return kept != null ? mapper.writerWithDefaultPrettyPrinter().writeValueAsString(kept) : ""; } catch (Exception e) { return ""; }
    }
}