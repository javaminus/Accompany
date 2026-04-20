package com.example;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MemoryCompactService {

    private final ChatMemory chatMemory;
    private final ChatModel chatModel;
    private boolean manualCompactTriggered = false;

    private static final int KEEP_RECENT = 3;
    private static final int TOKEN_THRESHOLD = 50000;
    private final Path transcriptDir = Paths.get(System.getProperty("user.dir"), ".transcripts");

    public MemoryCompactService(ChatMemory chatMemory, ChatModel chatModel) {
        this.chatMemory = chatMemory;
        this.chatModel = chatModel;
    }

    public void setManualCompactTriggered(boolean triggered) { this.manualCompactTriggered = triggered; }
    public boolean isManualCompactTriggered() { return manualCompactTriggered; }

    public void microCompact(String conversationId) {
        // 修复：必须传入获取的历史记录条数 (这里用 1000 取全部)
        List<Message> history = chatMemory.get(conversationId, 1000);
        if (history == null || history.isEmpty()) return;

        List<Message> newHistory = new ArrayList<>();
        long toolResponseCount = history.stream().filter(m -> m instanceof ToolResponseMessage).count();

        for (Message msg : history) {
            if (msg instanceof ToolResponseMessage trm) {
                if (toolResponseCount > KEEP_RECENT) {
                    List<ToolResponseMessage.ToolResponse> compactedResponses = new ArrayList<>();
                    for (ToolResponseMessage.ToolResponse response : trm.getResponses()) {
                        String toolName = response.name();
                        String data = response.responseData();

                        if ("readFileTool".equals(toolName) || (data != null && data.length() <= 100)) {
                            compactedResponses.add(response);
                        } else {
                            compactedResponses.add(new ToolResponseMessage.ToolResponse(
                                    response.id(), toolName, "[Previous: used " + toolName + "]"
                            ));
                        }
                    }
                    newHistory.add(new ToolResponseMessage(compactedResponses));
                    toolResponseCount--;
                } else {
                    newHistory.add(trm);
                }
            } else {
                newHistory.add(msg);
            }
        }

        chatMemory.clear(conversationId);
        chatMemory.add(conversationId, newHistory);
    }

    public void autoCompactIfNeeded(String conversationId) {
        List<Message> history = chatMemory.get(conversationId, 1000); // 修复
        if (history == null) return;

        long totalChars = history.stream()
                .map(Message::getContent)
                .filter(content -> content != null)
                .mapToLong(String::length)
                .sum();

        long estimatedTokens = totalChars / 4;
        if (estimatedTokens > TOKEN_THRESHOLD) {
            System.out.println("\033[35m[auto_compact triggered]\033[0m");
            forceCompact(conversationId);
        }
    }

    public void forceCompact(String conversationId) {
        List<Message> history = chatMemory.get(conversationId, 1000); // 修复
        if (history == null || history.isEmpty()) return;

        saveTranscript(history);

        String conversationText = history.stream()
                .map(m -> m.getMessageType().name() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        if (conversationText.length() > 80000) {
            conversationText = conversationText.substring(conversationText.length() - 80000);
        }

        String prompt = "Summarize this conversation for continuity. Include: \n" +
                "1) What was accomplished, 2) Current state, 3) Key decisions made. \n" +
                "Be concise but preserve critical details.\n\n" + conversationText;

        String summary = chatModel.call(prompt);

        chatMemory.clear(conversationId);
        chatMemory.add(conversationId, new UserMessage("[Conversation compressed.]\n\n" + summary));
    }

    private void saveTranscript(List<Message> messages) {
        try {
            Files.createDirectories(transcriptDir);
            Path filePath = transcriptDir.resolve("transcript_" + System.currentTimeMillis() + ".jsonl");
            String dump = messages.stream()
                    .map(m -> m.getMessageType().name() + " | " + m.getContent())
                    .collect(Collectors.joining("\n"));
            Files.writeString(filePath, dump, StandardOpenOption.CREATE);
            System.out.println("\033[35m[transcript saved: " + filePath + "]\033[0m");
        } catch (Exception e) {
            System.err.println("Failed to save transcript: " + e.getMessage());
        }
    }
}