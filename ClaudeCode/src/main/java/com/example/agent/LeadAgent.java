package com.example.agent;


import com.example.config.AppConfig;
import com.example.models.Models;
import com.example.services.BackgroundService;
import com.example.services.MessageBusService;
import com.example.services.SkillLoaderService;
import com.example.services.TodoManagerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class LeadAgent {

    @Autowired
    private ChatModel chatModel;
    @Autowired
    private SkillLoaderService skills;
    @Autowired
    private BackgroundService bg;
    @Autowired
    private MessageBusService bus;
    @Autowired
    private TodoManagerService todo;
    @Autowired
    private ObjectMapper mapper;

    private List<Message> history = new ArrayList<>();
    private int roundsWithoutTodo = 0;

    public void processQuery(String query) {
        history.add(new UserMessage(query));
        agentLoop();
        
        Message lastMsg = history.get(history.size() - 1);
        if (lastMsg instanceof AssistantMessage am) {
            System.out.println(am.getContent());
        }
        System.out.println();
    }

    public void manualCompact() {
        if (!history.isEmpty()) {
            history = autoCompact(history);
        }
    }

    private void agentLoop() {
        String systemStr = String.format("You are a coding agent at %s. Use tools to solve tasks. Prefer task_create/task_update/task_list for multi-step work. Use TodoWrite for short checklists. Use task for subagent delegation. Use load_skill for specialized knowledge. Skills: %s",
                AppConfig.WORKDIR.toString(), skills.descriptions());
        
        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem(systemStr)
                // 注册所有的工具 (与 ToolsConfig 里的 Bean 名称一致)
                .defaultFunctions("bash", "read_file", "write_file", "edit_file", "TodoWrite", 
                        "task", "load_skill", "compress", "background_run", "check_background", 
                        "task_create", "task_get", "task_update", "task_list", "spawn_teammate", 
                        "list_teammates", "send_message", "read_inbox", "broadcast", 
                        "shutdown_request", "plan_approval", "claim_task")
                .build();

        while (true) {
            // s06: 估算 Token 并执行压缩
            if (estimateTokens(history) > AppConfig.TOKEN_THRESHOLD) {
                System.out.println("[auto-compact triggered]");
                history = autoCompact(history);
            }

            // s08: 检查后台通知
            List<Models.BackgroundNotification> notifs = bg.drain();
            if (!notifs.isEmpty()) {
                StringBuilder txt = new StringBuilder();
                notifs.forEach(n -> txt.append("[bg:").append(n.taskId()).append("] ").append(n.status()).append(": ").append(n.result()).append("\n"));
                history.add(new UserMessage("<background-results>\n" + txt.toString() + "\n</background-results>"));
            }

            // s10: 检查收件箱
            List<Models.MessageDto> inbox = bus.readInbox("lead");
            if (!inbox.isEmpty()) {
                try { history.add(new UserMessage("<inbox>" + mapper.writeValueAsString(inbox) + "</inbox>")); } catch (Exception e){}
            }

            // 执行大模型调用
            todo.usedTodoInCurrentRound = false;
            
            // 注意：Spring AI ChatClient 默认在返回前会自动循环调用工具直至没有 Function Call 为止。
            // 但是这段 Python 逻辑是单步执行并在返回后检查 Todo。
            // 在 Spring AI 中，我们可以让它自动处理工具链，然后我们在外部检查状态。
            org.springframework.ai.chat.model.ChatResponse response = client.prompt(new Prompt(history)).call().chatResponse();
            String content = response.getResult().getOutput().getContent();
            history.add(new AssistantMessage(content));

            // s03: 待办事项提醒逻辑
            if (todo.usedTodoInCurrentRound) {
                roundsWithoutTodo = 0;
            } else {
                roundsWithoutTodo++;
            }

            if (todo.hasOpenItems() && roundsWithoutTodo >= 3) {
                history.add(new UserMessage("<reminder>Update your todos.</reminder>"));
                // 强制多跑一圈让模型处理提醒
                continue; 
            }

            // 如果包含了压缩工具的调用关键字 (因 Spring AI 自动执行返回结果是 String，如果是我们写的空逻辑)
            if (content != null && content.contains("Compressing...")) {
                System.out.println("[manual compact]");
                history = autoCompact(history);
                return;
            }
            
            break; // 结束 Agent 循环，等待用户下一个输入
        }
    }

    private int estimateTokens(List<Message> msgs) {
        try {
            return mapper.writeValueAsString(msgs).length() / 4;
        } catch (Exception e) { return 0; }
    }

    private List<Message> autoCompact(List<Message> msgs) {
        try {
            Files.createDirectories(AppConfig.TRANSCRIPT_DIR);
            Path path = AppConfig.TRANSCRIPT_DIR.resolve("transcript_" + (System.currentTimeMillis() / 1000) + ".jsonl");
            StringBuilder fileContent = new StringBuilder();
            for (Message m : msgs) {
                fileContent.append(mapper.writeValueAsString(m)).append("\n");
            }
            Files.writeString(path, fileContent.toString());

            String convText = mapper.writeValueAsString(msgs);
            if (convText.length() > 80000) convText = convText.substring(convText.length() - 80000);

            String summary = ChatClient.create(chatModel)
                    .prompt()
                    .user("Summarize for continuity:\n" + convText)
                    .call()
                    .content();

            List<Message> newHistory = new ArrayList<>();
            newHistory.add(new UserMessage("[Compressed. Transcript: " + path.toString() + "]\n" + summary));
            return newHistory;
        } catch (Exception e) {
            e.printStackTrace();
            return msgs;
        }
    }
}