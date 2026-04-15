package com.example.tools;

import com.example.s12.core.EventBus;
import com.example.s12.core.WorktreeManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class WorktreeTools {

    public record WtCreateReq(String name, Integer task_id, String base_ref) {}
    @Bean @Description("Create a git worktree and optionally bind it to a task.")
    public Function<WtCreateReq, String> worktreeCreateTool(WorktreeManager wm) {
        return req -> {
            try { return wm.create(req.name(), req.task_id(), req.base_ref() != null ? req.base_ref() : "HEAD"); }
            catch (Exception e) { return "Error: " + e.getMessage(); }
        };
    }

    public record WtListReq() {}
    @Bean @Description("List worktrees tracked in .worktrees/index.json.")
    public Function<WtListReq, String> worktreeListTool(WorktreeManager wm) {
        return req -> wm.listAll();
    }

    public record WtStatusReq(String name) {}
    @Bean @Description("Show git status for one worktree.")
    public Function<WtStatusReq, String> worktreeStatusTool(WorktreeManager wm) {
        return req -> wm.status(req.name());
    }

    public record WtRunReq(String name, String command) {}
    @Bean @Description("Run a shell command in a named worktree directory.")
    public Function<WtRunReq, String> worktreeRunTool(WorktreeManager wm) {
        return req -> wm.runCommand(req.name(), req.command());
    }

    public record WtRemoveReq(String name, Boolean force, Boolean complete_task) {}
    @Bean @Description("Remove a worktree and optionally mark its bound task completed.")
    public Function<WtRemoveReq, String> worktreeRemoveTool(WorktreeManager wm) {
        return req -> wm.remove(req.name(), req.force() != null && req.force(), req.complete_task() != null && req.complete_task());
    }

    public record WtKeepReq(String name) {}
    @Bean @Description("Mark a worktree as kept in lifecycle state without removing it.")
    public Function<WtKeepReq, String> worktreeKeepTool(WorktreeManager wm) {
        return req -> wm.keep(req.name());
    }

    public record WtEventsReq(Integer limit) {}
    @Bean @Description("List recent worktree/task lifecycle events from .worktrees/events.jsonl.")
    public Function<WtEventsReq, String> worktreeEventsTool(EventBus eb) {
        return req -> eb.listRecent(req.limit() != null ? req.limit() : 20);
    }
}