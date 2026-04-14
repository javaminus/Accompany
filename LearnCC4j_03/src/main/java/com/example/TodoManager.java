package com.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TodoManager {

    // 对应 Python 中的 PlanItem 结构
    public record PlanItem(
            String content,
            String status, // "pending", "in_progress", "completed"
            @JsonProperty("activeForm") 
            @JsonPropertyDescription("Optional present-continuous label.") 
            String activeForm
    ) {}

    private List<PlanItem> items = new ArrayList<>();
    private int toolCallsSinceUpdate = 0; // 记录距上次更新经历了多少次工具调用

    public synchronized String update(List<PlanItem> newItems) {
        if (newItems.size() > 12) {
            throw new IllegalArgumentException("Keep the session plan short (max 12 items)");
        }

        List<PlanItem> normalized = new ArrayList<>();
        int inProgressCount = 0;

        for (int i = 0; i < newItems.size(); i++) {
            PlanItem item = newItems.get(i);
            String content = item.content() != null ? item.content().trim() : "";
            String status = item.status() != null ? item.status().toLowerCase() : "pending";
            String activeForm = item.activeForm() != null ? item.activeForm().trim() : "";

            if (content.isEmpty()) {
                throw new IllegalArgumentException("Item " + i + ": content required");
            }
            if (!List.of("pending", "in_progress", "completed").contains(status)) {
                throw new IllegalArgumentException("Item " + i + ": invalid status '" + status + "'");
            }
            if ("in_progress".equals(status)) {
                inProgressCount++;
            }

            normalized.add(new PlanItem(content, status, activeForm));
        }

        if (inProgressCount > 1) {
            throw new IllegalArgumentException("Only one plan item can be in_progress");
        }

        this.items = normalized;
        this.toolCallsSinceUpdate = 0; // 重置提醒计数器
        return render();
    }

    /**
     * 如果大模型沉迷于调用其他工具而忘记更新计划，则返回 Reminder
     */
    public synchronized String getReminderAndIncrement() {
        toolCallsSinceUpdate++;
        // 设置间隔：连续3次工具调用没更新 todo，就带上提醒字符串喂回给模型
        if (!items.isEmpty() && toolCallsSinceUpdate >= 3) {
            return "\n\n<reminder>Refresh your current plan before continuing.</reminder>";
        }
        return "";
    }

    /**
     * 格式化输出任务列表，反馈给大模型
     */
    private String render() {
        if (items.isEmpty()) {
            return "No session plan yet.";
        }

        StringBuilder sb = new StringBuilder();
        int completed = 0;
        for (PlanItem item : items) {
            String marker = switch (item.status()) {
                case "pending" -> "[ ]";
                case "in_progress" -> "[>]";
                case "completed" -> "[x]";
                default -> "[ ]";
            };
            
            sb.append(marker).append(" ").append(item.content());
            if ("in_progress".equals(item.status()) && !item.activeForm().isEmpty()) {
                sb.append(" (").append(item.activeForm()).append(")");
            }
            sb.append("\n");
            
            if ("completed".equals(item.status())) completed++;
        }

        sb.append("\n(").append(completed).append("/").append(items.size()).append(" completed)");
        return sb.toString();
    }
}