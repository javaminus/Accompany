package com.example.core;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProtocolTracker {
    public record RequestInfo(String target, String status) {}
    public record PlanInfo(String from, String plan, String status) {}

    // 使用 request_id 作为 Key
    public final Map<String, RequestInfo> shutdownRequests = new ConcurrentHashMap<>();
    public final Map<String, PlanInfo> planRequests = new ConcurrentHashMap<>();
}