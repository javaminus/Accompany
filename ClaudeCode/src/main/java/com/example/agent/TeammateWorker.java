package com.example.agent;


import com.example.config.AppConfig;
import com.example.models.Models;
import com.example.services.MessageBusService;
import com.example.services.TaskManagerService;
import com.example.services.TeamManagerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Scope("prototype")
public class TeammateWorker implements Runnable {

    @Autowired
    private ChatModel chatModel;
    @Autowired
    private TeamManagerService teamMgr;
    @Autowired
    private MessageBusService bus;
    @Autowired
    private TaskManagerService taskMgr;
    @Autowired
    private ObjectMapper mapper;

    private String name;
    private String role;
    private String promptText;
    private String teamName;

    public void init(String name, String role, String promptText, String teamName) {
        this.name = name;
        this.role = role;
        this.promptText = promptText;
        this.teamName = teamName;
    }

    @Override
    public void run() {
        String sysPrompt = String.format("You are '%s', role: %s, team: %s, at %s. Use send_message to talk. Use claim_task to claim work.", 
                name, role, teamName, AppConfig.WORKDIR.toString());

        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem(sysPrompt)
                .defaultFunctions("bash", "read_file", "write_file", "edit_file", "send_message", "claim_task")
                .build();

        List<Message> history = new ArrayList<>();
        history.add(new UserMessage(promptText));

        while (true) {
            // WORK PHASE
            for (int i = 0; i < 50; i++) {
                List<Models.MessageDto> inbox = bus.readInbox(name);
                for (Models.MessageDto msg : inbox) {
                    if ("shutdown_request".equals(msg.type())) {
                        teamMgr.setStatus(name, "shutdown");
                        return;
                    }
                    try { history.add(new UserMessage(mapper.writeValueAsString(msg))); } catch (Exception e){}
                }

                try {
                    // Spring AI 1.0.0-M2 会自动执行绑定的函数并将结果加入历史。
                    // 为了简化，我们使用带有历史记录的 prompt 调用
                    String response = client.prompt(new Prompt(history)).call().content();
                    history.add(new AssistantMessage(response));
                    
                    // 假设 AI 返回了需要进入 idle 的文字 (由于没有注册 idle tool，我们约定它回复特定的短语)
                    if (response.contains("Entering idle phase") || response.contains("<idle>")) {
                        break;
                    }
                } catch (Exception e) {
                    teamMgr.setStatus(name, "shutdown");
                    return;
                }
            }

            // IDLE PHASE
            teamMgr.setStatus(name, "idle");
            boolean resume = false;
            for (int i = 0; i < AppConfig.IDLE_TIMEOUT / Math.max(AppConfig.POLL_INTERVAL, 1); i++) {
                try { Thread.sleep(AppConfig.POLL_INTERVAL * 1000L); } catch (Exception e){}
                
                List<Models.MessageDto> inbox = bus.readInbox(name);
                if (!inbox.isEmpty()) {
                    for (Models.MessageDto msg : inbox) {
                        if ("shutdown_request".equals(msg.type())) {
                            teamMgr.setStatus(name, "shutdown");
                            return;
                        }
                        try { history.add(new UserMessage(mapper.writeValueAsString(msg))); } catch (Exception e){}
                    }
                    resume = true;
                    break;
                }

                List<Models.Task> unclaimed = taskMgr.getUnclaimedTasks();
                if (!unclaimed.isEmpty()) {
                    Models.Task t = unclaimed.get(0);
                    taskMgr.claim(t.id(), name);
                    if (history.size() <= 3) {
                        history.add(0, new UserMessage("<identity>You are '" + name + "', role: " + role + ".</identity>"));
                        history.add(1, new AssistantMessage("I am " + name + ". Continuing."));
                    }
                    history.add(new UserMessage("<auto-claimed>Task #" + t.id() + ": " + t.subject() + "\n" + t.description() + "</auto-claimed>"));
                    history.add(new AssistantMessage("Claimed task #" + t.id() + ". Working on it."));
                    resume = true;
                    break;
                }
            }
            if (!resume) {
                teamMgr.setStatus(name, "shutdown");
                return;
            }
            teamMgr.setStatus(name, "working");
        }
    }
}