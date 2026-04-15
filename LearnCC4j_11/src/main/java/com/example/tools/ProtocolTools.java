package com.example.tools;


import com.example.core.MessageBus;
import com.example.core.ProtocolTracker;
import com.example.core.TeammateManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Configuration
public class ProtocolTools {

    private void logProtocol(String action, String detail) {
        System.out.println("  [Protocol] " + action + ": " + detail);
    }

    // ==========================================
    // 1. Shutdown Protocol (关机协议)
    // ==========================================

    public record LeadShutdownReq(String teammate) {}
    @Bean
    @Description("Lead: Request a teammate to shut down gracefully. Returns request_id.")
    Function<LeadShutdownReq, String> leadShutdownRequestTool(ProtocolTracker tracker, MessageBus bus) {
        return req -> {
            String reqId = UUID.randomUUID().toString().substring(0, 8);
            tracker.shutdownRequests.put(reqId, new ProtocolTracker.RequestInfo(req.teammate(), "pending"));
            bus.send("lead", req.teammate(), "Please shut down gracefully.", "shutdown_request", Map.of("request_id", reqId));
            logProtocol("Shutdown Request", "Sent to " + req.teammate() + " (reqId=" + reqId + ")");
            return "Shutdown request " + reqId + " sent to '" + req.teammate() + "' (status: pending)";
        };
    }

    public record LeadShutdownCheckReq(String request_id) {}
    @Bean
    @Description("Lead: Check the status of a shutdown request by request_id.")
    Function<LeadShutdownCheckReq, String> leadShutdownCheckTool(ProtocolTracker tracker) {
        return req -> {
            ProtocolTracker.RequestInfo info = tracker.shutdownRequests.get(req.request_id());
            return info != null ? "Status: " + info.status() : "Error: not found";
        };
    }

    public record TeammateShutdownResp(String request_id, boolean approve, String reason, String sender) {}
    @Bean
    @Description("Teammate: Respond to a shutdown request. Approve to shut down.")
    Function<TeammateShutdownResp, String> teammateShutdownRespondTool(ProtocolTracker tracker, MessageBus bus, TeammateManager tm) {
        return req -> {
            String status = req.approve() ? "approved" : "rejected";
            tracker.shutdownRequests.computeIfPresent(req.request_id(), (k, v) -> new ProtocolTracker.RequestInfo(v.target(), status));
            
            bus.send(req.sender(), "lead", req.reason() != null ? req.reason() : "", "shutdown_response",
                    Map.of("request_id", req.request_id(), "approve", req.approve()));
            
            if (req.approve()) {
                tm.markAsShutdown(req.sender()); // 更新为 Shutdown，触发线程安全退出
            }
            logProtocol("Shutdown Respond", req.sender() + " " + status);
            return "Shutdown " + status;
        };
    }

    // ==========================================
    // 2. Plan Approval Protocol (计划审批协议)
    // ==========================================

    public record TeammatePlanSubmitReq(String plan, String sender) {}
    @Bean
    @Description("Teammate: Submit a plan for lead approval. Provide plan text.")
    Function<TeammatePlanSubmitReq, String> teammatePlanSubmitTool(ProtocolTracker tracker, MessageBus bus) {
        return req -> {
            String reqId = UUID.randomUUID().toString().substring(0, 8);
            tracker.planRequests.put(reqId, new ProtocolTracker.PlanInfo(req.sender(), req.plan(), "pending"));
            bus.send(req.sender(), "lead", req.plan(), "plan_approval_response", Map.of("request_id", reqId, "plan", req.plan()));
            logProtocol("Plan Submit", req.sender() + " submitted (reqId=" + reqId + ")");
            return "Plan submitted (request_id=" + reqId + "). Waiting for lead approval.";
        };
    }

    public record LeadPlanApproveReq(String request_id, boolean approve, String feedback) {}
    @Bean
    @Description("Lead: Approve or reject a teammate's plan. Provide request_id + approve + feedback.")
    Function<LeadPlanApproveReq, String> leadPlanApproveTool(ProtocolTracker tracker, MessageBus bus) {
        return req -> {
            ProtocolTracker.PlanInfo info = tracker.planRequests.get(req.request_id());
            if (info == null) return "Error: Unknown plan request_id";
            String status = req.approve() ? "approved" : "rejected";
            
            tracker.planRequests.put(req.request_id(), new ProtocolTracker.PlanInfo(info.from(), info.plan(), status));
            String feedback = req.feedback() != null ? req.feedback() : "";
            
            bus.send("lead", info.from(), feedback, "plan_approval_response",
                    Map.of("request_id", req.request_id(), "approve", req.approve(), "feedback", feedback));
            
            logProtocol("Plan Approve", status + " for " + info.from());
            return "Plan " + status + " for '" + info.from() + "'";
        };
    }
}