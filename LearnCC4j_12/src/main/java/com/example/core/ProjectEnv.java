package com.example.core;

import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Component
public class ProjectEnv {
    private final Path workDir;
    private final Path repoRoot;

    public ProjectEnv() {
        this.workDir = Paths.get(System.getProperty("user.dir"));
        this.repoRoot = detectRepoRoot(workDir);
    }

    private Path detectRepoRoot(Path cwd) {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--show-toplevel")
                    .directory(cwd.toFile())
                    .start();
            if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) {
                String root = new String(process.getInputStream().readAllBytes()).trim();
                Path rootPath = Paths.get(root);
                if (rootPath.toFile().exists()) return rootPath;
            }
        } catch (Exception ignored) { }
        return cwd; // fallback
    }

    public Path getWorkDir() { return workDir; }
    public Path getRepoRoot() { return repoRoot; }
}