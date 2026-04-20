package com.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class AppConfig {

    public static final Path WORKDIR = Paths.get(System.getProperty("user.dir"));
    public static final Path TEAM_DIR = WORKDIR.resolve(".team");
    public static final Path INBOX_DIR = TEAM_DIR.resolve("inbox");
    public static final Path TASKS_DIR = WORKDIR.resolve(".tasks");
    public static final Path SKILLS_DIR = WORKDIR.resolve("skills");
    public static final Path TRANSCRIPT_DIR = WORKDIR.resolve(".transcripts");
    
    public static final int TOKEN_THRESHOLD = 100000;
    public static final int POLL_INTERVAL = 5;
    public static final int IDLE_TIMEOUT = 60;

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}