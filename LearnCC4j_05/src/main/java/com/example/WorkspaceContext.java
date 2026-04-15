package com.example;

import java.nio.file.Path;
import java.nio.file.Paths;

public class WorkspaceContext {

    public static final Path WORKDIR = Paths.get(System.getProperty("user.dir")).normalize().toAbsolutePath();
    public static final Path SKILLS_DIR = WORKDIR.resolve("skills");

    public static Path safePath(String pathStr) {
        Path path = WORKDIR.resolve(pathStr).normalize().toAbsolutePath();
        if (!path.startsWith(WORKDIR)) {
            throw new IllegalArgumentException("Path escapes workspace: " + pathStr);
        }
        return path;
    }

    public static String truncateOutput(String output) {
        return (output.length() > 50000) ? output.substring(0, 50000) : output;
    }
}