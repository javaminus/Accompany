package com.example;

import java.nio.file.Path;

public record SkillManifest(String name, String description, Path path) {
}