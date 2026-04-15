package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Configuration
public class ToolsConfig {

    private void logTool(String tool, String output) {
        System.out.println("  [Tool] " + tool + ": " + output.substring(0, Math.min(output.length(), 120)).replace("\n", " "));
    }

    // -- 基础 Shell 和文件工具 --

    public record BashReq(String command) {
    }

    @Bean
    @Description("Run a shell command.")
    Function<BashReq, String> bashTool() {
        return req -> {
            try {
                Process process = new ProcessBuilder("bash", "-c", req.command())
                        .directory(new File(System.getProperty("user.dir")))
                        .redirectErrorStream(true).start();
                if (!process.waitFor(120, TimeUnit.SECONDS)) {
                    process.destroy();
                    return "Error: Timeout";
                }
                String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                String res = out.isEmpty() ? "(no output)" : out;
                logTool("bash", res);
                return res;
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        };
    }

    public record ReadFileReq(String path) {
    }

    @Bean
    @Description("Read file contents.")
    Function<ReadFileReq, String> readFileTool() {
        return req -> {
            try {
                return Files.readString(Paths.get(System.getProperty("user.dir")).resolve(req.path()));
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        };
    }

    public record WriteFileReq(String path, String content) {
    }

    @Bean
    @Description("Write content to file.")
    Function<WriteFileReq, String> writeFileTool() {
        return req -> {
            try {
                Path p = Paths.get(System.getProperty("user.dir")).resolve(req.path());
                p.getParent().toFile().mkdirs();
                Files.writeString(p, req.content());
                return "Wrote file: " + req.path();
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        };
    }

    public record EditFileReq(String path, String old_text, String new_text) {
    }

    @Bean
    @Description("Replace exact text in file.")
    Function<EditFileReq, String> editFileTool() {
        return req -> {
            try {
                Path p = Paths.get(System.getProperty("user.dir")).resolve(req.path());
                String content = Files.readString(p);
                if (!content.contains(req.old_text())) return "Error: old_text not found";
                Files.writeString(p, content.replaceFirst(java.util.regex.Pattern.quote(req.old_text()), req.new_text()));
                return "Edited " + req.path();
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        };
    }

    // -- 通讯与团队管理工具 --

    public record SpawnReq(String name, String role, String prompt) {
    }

    @Bean
    @Description("Spawn a persistent teammate.")
    Function<SpawnReq, String> spawnTeammateTool(TeammateManager tm) {
        return req -> tm.spawn(req.name(), req.role(), req.prompt());
    }

    public record ListTeammatesReq() {
    }

    @Bean
    @Description("List all teammates.")
    Function<ListTeammatesReq, String> listTeammatesTool(TeammateManager tm) {
        return req -> tm.listAll();
    }

    public record SendMsgReq(String sender, String to, String content, String msg_type) {
    }

    @Bean
    @Description("Send message to a teammate. Use 'message' as default msg_type.")
    Function<SendMsgReq, String> sendMessageTool(MessageBus bus) {
        return req -> bus.send(req.sender(), req.to(), req.content(), req.msg_type() != null ? req.msg_type() : "message", null);
    }

    public record ReadInboxReq(String owner) {
    }

    @Bean
    @Description("Read and drain your inbox.")
    Function<ReadInboxReq, String> readInboxTool(MessageBus bus) {
        return req -> bus.readInboxJson(req.owner());
    }

    public record BroadcastReq(String content) {
    }

    @Bean
    @Description("Send a message to all teammates.")
    Function<BroadcastReq, String> broadcastTool(MessageBus bus, TeammateManager tm) {
        return req -> bus.broadcast("lead", req.content(), tm.getMemberNames());
    }

    // -- 协议交互工具 (Shutdown 关机协议) --

    public record ShutdownReq(String teammate) {
    }

    @Bean
    @Description("Lead: Request a teammate to shut down gracefully. Returns request_id.")
    Function<ShutdownReq, String> shutdownRequestTool(ProtocolTracker tracker, MessageBus bus) {
        return req -> {
            String reqId = UUID.randomUUID().toString().substring(0, 8);
            tracker.shutdownRequests.put(reqId, new ProtocolTracker.RequestInfo(req.teammate(), "pending"));
            bus.send("lead", req.teammate(), "Please shut down gracefully.", "shutdown_request", Map.of("request_id", reqId));
            logTool("shutdown_request", "Sent to " + req.teammate() + " (reqId=" + reqId + ")");
            return "Shutdown request " + reqId + " sent to '" + req.teammate() + "' (status: pending)";
        };
    }

    public record ShutdownCheckReq(String request_id) {
    }

    @Bean
    @Description("Lead: Check the status of a shutdown request by request_id.")
    Function<ShutdownCheckReq, String> shutdownStatusTool(ProtocolTracker tracker) {
        return req -> {
            ProtocolTracker.RequestInfo info = tracker.shutdownRequests.get(req.request_id());
            return info != null ? "Status: " + info.status() : "Error: not found";
        };
    }

    public record ShutdownRespondReq(String request_id, boolean approve, String reason, String sender) {
    }

    @Bean
    @Description("Teammate: Respond to a shutdown request. Approve to shut down.")
    Function<ShutdownRespondReq, String> shutdownRespondTool(ProtocolTracker tracker, MessageBus bus, TeammateManager tm) {
        return req -> {
            String status = req.approve() ? "approved" : "rejected";
            tracker.shutdownRequests.computeIfPresent(req.request_id(), (k, v) -> new ProtocolTracker.RequestInfo(v.target(), status));
            bus.send(req.sender(), "lead", req.reason() != null ? req.reason() : "", "shutdown_response",
                    Map.of("request_id", req.request_id(), "approve", req.approve()));
            if (req.approve()) tm.markAsShutdown(req.sender()); // 更新为 Shutdown 状态，后续结束生命周期
            logTool("shutdown_respond", req.sender() + " " + status);
            return "Shutdown " + status;
        };
    }

    // -- 协议交互工具 (Plan Approval 计划审批协议) --

    public record PlanSubmitReq(String plan, String sender) {
    }

    @Bean
    @Description("Teammate: Submit a plan for lead approval. Provide plan text.")
    Function<PlanSubmitReq, String> planSubmitTool(ProtocolTracker tracker, MessageBus bus) {
        return req -> {
            String reqId = UUID.randomUUID().toString().substring(0, 8);
            tracker.planRequests.put(reqId, new ProtocolTracker.PlanInfo(req.sender(), req.plan(), "pending"));
            bus.send(req.sender(), "lead", req.plan(), "plan_approval_response", Map.of("request_id", reqId, "plan", req.plan()));
            logTool("plan_submit", req.sender() + " submitted plan (reqId=" + reqId + ")");
            return "Plan submitted (request_id=" + reqId + "). Waiting for lead approval.";
        };
    }

    public record PlanApproveReq(String request_id, boolean approve, String feedback) {
    }

    @Bean
    @Description("Lead: Approve or reject a teammate's plan. Provide request_id + approve + feedback.")
    Function<PlanApproveReq, String> planApproveTool(ProtocolTracker tracker, MessageBus bus) {
        return req -> {
            ProtocolTracker.PlanInfo info = tracker.planRequests.get(req.request_id());
            if (info == null) return "Error: Unknown plan request_id";
            String status = req.approve() ? "approved" : "rejected";
            tracker.planRequests.put(req.request_id(), new ProtocolTracker.PlanInfo(info.from(), info.plan(), status));
            String feedback = req.feedback() != null ? req.feedback() : "";
            bus.send("lead", info.from(), feedback, "plan_approval_response",
                    Map.of("request_id", req.request_id(), "approve", req.approve(), "feedback", feedback));
            logTool("plan_approve", status + " for " + info.from());
            return "Plan " + status + " for '" + info.from() + "'";
        };
    }
}
