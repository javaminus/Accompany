package com.javaminus.FuckAIGC.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TodoItem {
    private String id;
    private String text;
    private String status; // pending, in_progress, completed （未完成的、正在进行的、已完成的）
}