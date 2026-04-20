package com.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在阅读或运行本章代码（s04_subagent）时，您可能会注意到代码中定义了一个 AgentTemplate 类，但在实际的主程序执行逻辑（taskTool 和 agent_loop）中并没有任何地方实例化或调用它。
 * 这是有意为之的，具体原因如下：
 * 1. 教学演示与架构揭秘
 * 这段代码源自 Anthropic 官方提供的 Claude Code 原理教学脚本。原作者保留这个类的目的是为了向开发者揭示真实商业级产品背后的设计架构。
 * 在本演示脚本中，为了保持代码的简单易懂，父 Agent 和子 Agent 的 System Prompt 都是**硬编码（Hardcoded）**的字符串（即 SYSTEM 和 SUBAGENT_SYSTEM 变量）。
 * 但在真实的 Claude Code CLI 工具中，系统并不会把 Prompt 写死在代码里。真实系统是通过读取特定目录（如 .claude/agents/*.md）下的 Markdown 文件，来动态加载不同 Agent 的身份、拥有哪些工具以及权限配置的。
 * 
 * 2. AgentTemplate 的实际作用
 * AgentTemplate 类是一个解析器原型。它展示了真实产品是如何解析带有 YAML Frontmatter 的 Markdown 文件的。
 * 例如，它可以解析如下格式的配置文件：
 * code
 * Markdown
 * ---
 * name: DatabaseExpert
 * model: claude-3-5-sonnet
 * tools: [read_file, write_file, sql_query]
 * ---
 * You are an expert at database performance tuning.
 * Always check the slow query log first.
 * 它能将顶部的 --- 包含的 YAML 属性提取为配置字典（Config Map），并将下方的文本提取为大模型的 System Prompt。
 */
public class AgentTemplate {
    private String name;
    private final Map<String, String> config = new HashMap<>();
    private String systemPrompt = "";

    public AgentTemplate(Path path) {
        this.name = path.getFileName().toString().replaceFirst("[.][^.]+$", "");
        parse(path);
    }

    private void parse(Path path) {
        try {
            String text = Files.readString(path);
            // 匹配 markdown 顶部的 --- (yaml 配置) ---
            Pattern pattern = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(text);

            if (matcher.find()) {
                String frontmatter = matcher.group(1);
                this.systemPrompt = matcher.group(2).trim();

                for (String line : frontmatter.split("\\r?\\n")) {
                    if (line.contains(":")) {
                        String[] parts = line.split(":", 2);
                        config.put(parts[0].trim(), parts[1].trim());
                    }
                }
                this.name = config.getOrDefault("name", this.name);
            } else {
                this.systemPrompt = text.trim();
            }
        } catch (Exception e) {
            this.systemPrompt = "Error reading template: " + e.getMessage();
        }
    }

    public String getName() { return name; }
    public Map<String, String> getConfig() { return config; }
    public String getSystemPrompt() { return systemPrompt; }
}