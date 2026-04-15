package com.example;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

/**
 * 自定义记忆装饰器：每次获取上下文前，自动将后台完成的任务注入历史记录
 */
public class NotificationAwareChatMemory implements ChatMemory {

    private final ChatMemory delegate;
    private final BackgroundManagerService bgService;

    public NotificationAwareChatMemory(ChatMemory delegate, BackgroundManagerService bgService) {
        this.delegate = delegate;
        this.bgService = bgService;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        delegate.add(conversationId, messages);
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        // 【核心拦截逻辑】：在提取记忆之前，检查通知队列
        List<BackgroundManagerService.Notification> notifs = bgService.drainNotifications();
        
        if (!notifs.isEmpty()) {
            StringBuilder sb = new StringBuilder("<background-results>\n");
            for (var n : notifs) {
                String res = n.result() != null ? n.result() : "(no output)";
                res = res.length() > 500 ? res.substring(0, 500) + "..." : res; // 截断超长结果
                sb.append(String.format("[bg:%s] %s: %s\n", n.taskId(), n.status(), res));
                
                System.out.println("\033[35m[Notification Injected] Task " + n.taskId() + " completed.\033[0m");
            }
            sb.append("</background-results>");

            // 偷偷将通知作为一个 UserMessage 追加到真实的记忆中
            delegate.add(conversationId, List.of(new UserMessage(sb.toString())));
        }

        // 返回包含通知的最新记忆
        return delegate.get(conversationId, lastN);
    }

    @Override
    public void clear(String conversationId) {
        delegate.clear(conversationId);
    }
}