package com.example.tools;

import com.example.core.MessageBus;
import com.example.core.TeammateManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class TeamCommunicationTools {

    public record SpawnReq(String name, String role, String prompt) {}
    @Bean
    @Description("Spawn a persistent teammate that runs in its own thread.")
    Function<SpawnReq, String> spawnTeammateTool(TeammateManager tm) {
        return req -> tm.spawn(req.name(), req.role(), req.prompt());
    }

    public record ListTeammatesReq() {}
    @Bean
    @Description("List all teammates with name, role, status.")
    Function<ListTeammatesReq, String> listTeammatesTool(TeammateManager tm) {
        return req -> tm.listAll();
    }

    public record SendMsgReq(String sender, String to, String content, String msg_type) {}
    @Bean
    @Description("Send a message to a teammate's inbox. Use 'message' as default msg_type.")
    Function<SendMsgReq, String> sendMessageTool(MessageBus bus) {
        return req -> bus.send(
                req.sender() == null ? "lead" : req.sender(),
                req.to(),
                req.content(),
                req.msg_type() != null ? req.msg_type() : "message",
                null
        );
    }

    public record ReadInboxReq(String owner) {}
    @Bean
    @Description("Read and drain your inbox.")
    Function<ReadInboxReq, String> readInboxTool(MessageBus bus) {
        return req -> bus.readInboxJson(req.owner());
    }

    public record BroadcastReq(String content) {}
    @Bean
    @Description("Send a message to all teammates.")
    Function<BroadcastReq, String> broadcastTool(MessageBus bus, TeammateManager tm) {
        return req -> bus.broadcast("lead", req.content(), tm.getMemberNames());
    }
}