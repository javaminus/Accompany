package com.example.models;

import java.util.List;
import java.util.Map;

public class Models {

    public record TodoItem(String content, String status, String activeForm) {}

    public record Task(int id, String subject, String description, String status, 
                       String owner, List<Integer> blockedBy) {}

    public record BackgroundTask(String id, String status, String command, String result) {}

    public record BackgroundNotification(String taskId, String status, String result) {}

    public record MessageDto(String type, String from, String content, long timestamp, Map<String, Object> extra) {}

    public record TeamMember(String name, String role, String status) {}

    public record TeamConfig(String teamName, List<TeamMember> members) {}
    
    // Tool Requests (用于 Spring AI 函数回调)
    public record BashRequest(String command) {}
    public record ReadFileRequest(String path, Integer limit) {}
    public record WriteFileRequest(String path, String content) {}
    public record EditFileRequest(String path, String oldText, String newText) {}
    public record TodoWriteRequest(List<TodoItem> items) {}
    public record TaskSubagentRequest(String prompt, String agentType) {}
    public record LoadSkillRequest(String name) {}
    public record BackgroundRunRequest(String command, Integer timeout) {}
    public record CheckBackgroundRequest(String taskId) {}
    public record TaskCreateRequest(String subject, String description) {}
    public record TaskGetRequest(Integer taskId) {}
    public record TaskUpdateRequest(Integer taskId, String status, List<Integer> addBlockedBy, List<Integer> removeBlockedBy) {}
    public record SpawnTeammateRequest(String name, String role, String prompt) {}
    public record SendMessageRequest(String to, String content, String msgType) {}
    public record BroadcastRequest(String content) {}
    public record ShutdownRequestDto(String teammate) {}
    public record PlanApprovalRequest(String requestId, Boolean approve, String feedback) {}
    public record ClaimTaskRequest(Integer taskId) {}
}