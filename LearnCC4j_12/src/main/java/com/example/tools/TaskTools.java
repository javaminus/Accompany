package com.example.tools;

import com.example.s12.core.TaskManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class TaskTools {
    
    public record TaskCreateReq(String subject, String description) {}
    @Bean @Description("Create a new task on the shared task board.")
    public Function<TaskCreateReq, String> taskCreateTool(TaskManager tm) {
        return req -> tm.create(req.subject(), req.description());
    }

    public record TaskListReq() {}
    @Bean @Description("List all tasks with status, owner, and worktree binding.")
    public Function<TaskListReq, String> taskListTool(TaskManager tm) {
        return req -> tm.listAll();
    }

    public record TaskGetReq(int task_id) {}
    @Bean @Description("Get task details by ID.")
    public Function<TaskGetReq, String> taskGetTool(TaskManager tm) {
        return req -> tm.get(req.task_id());
    }

    public record TaskUpdateReq(int task_id, String status, String owner) {}
    @Bean @Description("Update task status or owner.")
    public Function<TaskUpdateReq, String> taskUpdateTool(TaskManager tm) {
        return req -> {
            try { return tm.update(req.task_id(), req.status(), req.owner()); }
            catch (Exception e) { return "Error: " + e.getMessage(); }
        };
    }

    public record TaskBindReq(int task_id, String worktree, String owner) {}
    @Bean @Description("Bind a task to a worktree name.")
    public Function<TaskBindReq, String> taskBindWorktreeTool(TaskManager tm) {
        return req -> {
            try { return tm.bindWorktree(req.task_id(), req.worktree(), req.owner()); }
            catch (Exception e) { return "Error: " + e.getMessage(); }
        };
    }
}