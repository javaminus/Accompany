package com.example;


import com.example.agent.LeadAgent;
import com.example.services.MessageBusService;
import com.example.services.TaskManagerService;
import com.example.services.TeamManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.util.Scanner;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class AgentApplication implements CommandLineRunner {

    @Autowired
    private LeadAgent leadAgent;
    @Autowired
    private TaskManagerService taskManager;
    @Autowired
    private TeamManagerService teamManager;
    @Autowired
    private MessageBusService messageBus;

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Full Agent Harness (Java 17 + Spring AI) Started.");
        
        while (true) {
            System.out.print("\033[36mClaudeCode >> \033[0m");
            if (!scanner.hasNextLine()) break;
            
            String query = scanner.nextLine().trim();
            if (query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit") || query.isEmpty()) {
                break;
            }

            switch (query) {
                case "/compact" -> {
                    System.out.println("[manual compact via /compact]");
                    leadAgent.manualCompact();
                }
                case "/tasks" -> System.out.println(taskManager.listAll());
                case "/team" -> System.out.println(teamManager.listAll());
                case "/inbox" -> System.out.println(messageBus.readInbox("lead"));
                default -> leadAgent.processQuery(query);
            }
        }
        System.exit(0);
    }
}