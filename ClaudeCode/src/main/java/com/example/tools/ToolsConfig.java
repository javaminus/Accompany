package com.example.tools;


import com.example.agent.SubAgentHelper;
import com.example.models.Models;
import com.example.services.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Configuration
public class ToolsConfig {

    private final Map<String, Map<String, String>> planRequests = new HashMap<>();
    private final Map<String, Map<String, String>> shutdownRequests = new HashMap<>();

    @Bean
    @Description("Run a shell command.")
    public Function<Models.BashRequest, String> bash(FileOpsService fileOps) {
        return req -> fileOps.runBash(req.command());
    }

    @Bean
    @Description("Read file contents.")
    public Function<Models.ReadFileRequest, String> read_file(FileOpsService fileOps) {
        return req -> fileOps.read(req.path(), req.limit());
    }

    @Bean
    @Description("Write content to file.")
    public Function<Models.WriteFileRequest, String> write_file(FileOpsService fileOps) {
        return req -> fileOps.write(req.path(), req.content());
    }

    @Bean
    @Description("Replace exact text in file.")
    public Function<Models.EditFileRequest, String> edit_file(FileOpsService fileOps) {
        return req -> fileOps.edit(req.path(), req.oldText(), req.newText());
    }

    @Bean
    @Description("Update task tracking list.")
    public Function<Models.TodoWriteRequest, String> TodoWrite(TodoManagerService todo) {
        return req -> todo.update(req.items());
    }

    @Bean
    @Description("Spawn a subagent for isolated exploration or work.")
    public Function<Models.TaskSubagentRequest, String> task(SubAgentHelper subAgent) {
        return req -> subAgent.runSubagent(req.prompt(), req.agentType() != null ? req.agentType() : "Explore");
    }

    @Bean
    @Description("Load specialized knowledge by name.")
    public Function<Models.LoadSkillRequest, String> load_skill(SkillLoaderService skills) {
        return req -> skills.load(req.name());
    }

    @Bean
    @Description("Run command in background thread.")
    public Function<Models.BackgroundRunRequest, String> background_run(BackgroundService bg) {
        return req -> bg.run(req.command(), req.timeout() != null ? req.timeout() : 120);
    }

    @Bean
    @Description("Check background task status.")
    public Function<Models.CheckBackgroundRequest, String> check_background(BackgroundService bg) {
        return req -> bg.check(req.taskId());
    }

    @Bean
    @Description("Create a persistent file task.")
    public Function<Models.TaskCreateRequest, String> task_create(TaskManagerService taskMgr) {
        return req -> taskMgr.create(req.subject(), req.description() != null ? req.description() : "");
    }

    @Bean
    @Description("Get task details by ID.")
    public Function<Models.TaskGetRequest, String> task_get(TaskManagerService taskMgr) {
        return req -> taskMgr.get(req.taskId());
    }

    @Bean
    @Description("Update task status or dependencies.")
    public Function<Models.TaskUpdateRequest, String> task_update(TaskManagerService taskMgr) {
        return req -> taskMgr.update(req.taskId(), req.status(), req.addBlockedBy(), req.removeBlockedBy());
    }

    @Bean
    @Description("List all tasks.")
    public Function<Void, String> task_list(TaskManagerService taskMgr) {
        return req -> taskMgr.listAll();
    }

    @Bean
    @Description("Spawn a persistent autonomous teammate.")
    public Function<Models.SpawnTeammateRequest, String> spawn_teammate(TeamManagerService teamMgr) {
        return req -> teamMgr.spawn(req.name(), req.role(), req.prompt());
    }

    @Bean
    @Description("List all teammates.")
    public Function<Void, String> list_teammates(TeamManagerService teamMgr) {
        return req -> teamMgr.listAll();
    }

    @Bean
    @Description("Send a message to a teammate.")
    public Function<Models.SendMessageRequest, String> send_message(MessageBusService bus) {
        return req -> bus.send("lead", req.to(), req.content(), req.msgType() != null ? req.msgType() : "message", null);
    }

    @Bean
    @Description("Read and drain the lead's inbox.")
    public Function<Void, String> read_inbox(MessageBusService bus, ObjectMapper mapper) {
        return req -> {
            try { return mapper.writeValueAsString(bus.readInbox("lead")); } 
            catch (Exception e) { return "[]"; }
        };
    }

    @Bean
    @Description("Send message to all teammates.")
    public Function<Models.BroadcastRequest, String> broadcast(MessageBusService bus, TeamManagerService teamMgr) {
        return req -> bus.broadcast("lead", req.content(), teamMgr.memberNames());
    }

    @Bean
    @Description("Request a teammate to shut down.")
    public Function<Models.ShutdownRequestDto, String> shutdown_request(MessageBusService bus) {
        return req -> {
            String reqId = UUID.randomUUID().toString().substring(0, 8);
            shutdownRequests.put(reqId, Map.of("target", req.teammate(), "status", "pending"));
            bus.send("lead", req.teammate(), "Please shut down.", "shutdown_request", Map.of("request_id", reqId));
            return "Shutdown request " + reqId + " sent to '" + req.teammate() + "'";
        };
    }

    @Bean
    @Description("Approve or reject a teammate's plan.")
    public Function<Models.PlanApprovalRequest, String> plan_approval(MessageBusService bus) {
        return req -> {
            Map<String, String> plan = planRequests.get(req.requestId());
            if (plan == null) return "Error: Unknown plan request_id '" + req.requestId() + "'";
            plan.put("status", req.approve() ? "approved" : "rejected");
            bus.send("lead", plan.get("from"), req.feedback() != null ? req.feedback() : "", "plan_approval_response",
                    Map.of("request_id", req.requestId(), "approve", req.approve(), "feedback", req.feedback()));
            return "Plan " + plan.get("status") + " for '" + plan.get("from") + "'";
        };
    }

    @Bean
    @Description("Claim a task from the board.")
    public Function<Models.ClaimTaskRequest, String> claim_task(TaskManagerService taskMgr) {
        return req -> taskMgr.claim(req.taskId(), "lead");
    }

    // Compress 和 Idle 是特殊的，在 Agent 中通过逻辑拦截，但这里也提供虚拟实现防报错
    @Bean
    @Description("Manually compress conversation context.")
    public Function<Void, String> compress() {
        return req -> "Compressing..."; 
    }
}