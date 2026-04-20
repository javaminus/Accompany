package com.example;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.util.Scanner;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class SkillAgentApplication implements CommandLineRunner {

    private final ChatClient chatClient;

    public SkillAgentApplication(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public static void main(String[] args) {
        SpringApplication.run(SkillAgentApplication.class, args);
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\033[36ms05 >> \033[0m");
            if (!scanner.hasNextLine()) break;
            
            String query = scanner.nextLine().trim();
            if (query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit") || query.isEmpty()) {
                break;
            }

            try {
                String response = chatClient.prompt()
                        .user(query)
                        .call()
                        .content();

                if (response != null && !response.isBlank()) {
                    System.out.println(response);
                }
            } catch (Exception e) {
                System.err.println("Error during agent loop: " + e.getMessage());
            }
            System.out.println();
        }
    }
}