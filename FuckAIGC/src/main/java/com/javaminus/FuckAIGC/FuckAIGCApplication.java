package com.javaminus.FuckAIGC;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class FuckAIGCApplication {
    public static void main(String[] args) {
        SpringApplication.run(FuckAIGCApplication.class, args);
    }
}