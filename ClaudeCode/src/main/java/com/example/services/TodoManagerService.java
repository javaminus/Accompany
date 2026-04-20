package com.example.services;


import com.example.models.Models;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TodoManagerService {
    private List<Models.TodoItem> items = new ArrayList<>();
    public boolean usedTodoInCurrentRound = false;

    public String update(List<Models.TodoItem> newItems) {
        this.usedTodoInCurrentRound = true;
        List<Models.TodoItem> validated = new ArrayList<>();
        int ip = 0;
        for (int i = 0; i < newItems.size(); i++) {
            Models.TodoItem item = newItems.get(i);
            if (item.content() == null || item.content().isBlank()) throw new IllegalArgumentException("Item " + i + ": content required");
            String status = item.status() != null ? item.status().toLowerCase() : "pending";
            if (!List.of("pending", "in_progress", "completed").contains(status)) {
                throw new IllegalArgumentException("Item " + i + ": invalid status '" + status + "'");
            }
            if (item.activeForm() == null || item.activeForm().isBlank()) throw new IllegalArgumentException("Item " + i + ": activeForm required");
            if (status.equals("in_progress")) ip++;
            validated.add(new Models.TodoItem(item.content().trim(), status, item.activeForm().trim()));
        }
        if (validated.size() > 20) throw new IllegalArgumentException("Max 20 todos");
        if (ip > 1) throw new IllegalArgumentException("Only one in_progress allowed");
        this.items = validated;
        return render();
    }

    public String render() {
        if (items.isEmpty()) return "No todos.";
        StringBuilder sb = new StringBuilder();
        int done = 0;
        for (Models.TodoItem item : items) {
            String m = switch (item.status()) {
                case "completed" -> "[x]";
                case "in_progress" -> "[>]";
                default -> "[ ]";
            };
            String suffix = item.status().equals("in_progress") ? " <- " + item.activeForm() : "";
            sb.append(m).append(" ").append(item.content()).append(suffix).append("\n");
            if (item.status().equals("completed")) done++;
        }
        sb.append(String.format("\n(%d/%d completed)", done, items.size()));
        return sb.toString();
    }

    public boolean hasOpenItems() {
        return items.stream().anyMatch(i -> !i.status().equals("completed"));
    }
}