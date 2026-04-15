# LearnCC4j_04 - 子 Agent 架构与工具配置

[![Java Version](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/) [![Maven](https://img.shields.io/badge/Maven-3.8%2B-green)](https://maven.apache.org/) [![License](https://img.shields.io/badge/License-Apache%202.0-red)](https://www.apache.org/licenses/LICENSE-2.0)

> **项目简介**：LearnCC4j_04 是一个演示 Spring AI 框架下多层 Agent 架构设计的教学项目，重点展示父 Agent 与子 Agent 的协作机制、工具系统配置以及上下文隔离技术。

## 📋 项目概述

LearnCC4j_04 实现了一个分层的智能体（Agent）系统，其中包含一个**父 Agent**和可动态创建的**子 Agent**。该项目的核心目标是演示如何在 Spring AI 框架中构建安全、可扩展且上下文隔离的多 Agent 系统。

与传统单层 Agent 不同，本项目通过 `taskTool` 工具实现了**递归式任务分解**能力——父 Agent 可以将复杂任务委托给子 Agent 执行，而子 Agent 在完成任务后仅返回摘要结果，保持父 Agent 上下文的纯净性。

## 🧩 核心组件

### 1. `SubagentApplication.java` - 主应用入口
- Spring Boot 启动类，实现 `CommandLineRunner` 接口
- 创建父 Agent 的 `ChatClient`，配置完整的工具集（包括 `taskTool`）
- 提供交互式命令行界面，用户可通过 `s04 >>` 提示符与系统交互
- 支持退出命令：`q`、`exit` 或空输入

### 2. `AgentToolsConfig.java` - 工具系统配置
- 使用 `@Configuration` 注解定义 Spring Bean 配置类
- **基础工具集**（父/子 Agent 共享）：
  - `bashTool`: 安全执行 shell 命令（自动拦截危险命令如 `rm -rf /`）
  - `readFileTool`: 读取文件内容（支持行数限制）
  - `writeFileTool`: 写入文件内容（自动创建目录）
  - `editFileTool`: 替换文件中的指定文本
- **子 Agent 调度工具**（仅父 Agent 拥有）：
  - `taskTool`: 动态创建隔离的子 Agent 实例，实现任务委派
  - 关键特性：为每个子 Agent 分配独立的 `InMemoryChatMemory`，确保上下文隔离
  - 子 Agent 不具备 `taskTool`，防止无限递归创建

### 3. `AgentTemplate.java` - Agent 模板解析器（教学用途）
- **教学演示类**：展示真实商业级产品（如 Anthropic Claude Code）的架构设计
- 解析带有 YAML Frontmatter 的 Markdown 文件（如 `.claude/agents/*.md`）
- 支持格式：
  ```markdown
  ---
  name: DatabaseExpert
  model: claude-3-5-sonnet
  tools: [read_file, write_file, sql_query]
  ---
  You are an expert at database performance tuning.
  Always check the slow query log first.
  ```
- 提取 YAML 配置为 `Map<String, String>`，提取正文为 System Prompt
- **注意**：本项目中未实际使用该类，仅为架构教学目的保留

## ⚙️ 快速开始

### 环境要求
- Java 17+
- Maven 3.8+
- Spring Boot 3.x

### 运行步骤
1. 克隆项目并进入目录：
   ```bash
   cd LearnCC4j_04
   ```

2. 编译项目：
   ```bash
   mvn clean compile
   ```

3. 运行应用：
   ```bash
   mvn spring-boot:run
   ```

4. 在交互式终端中尝试以下命令：
   ```
   s04 >> 列出当前目录下的所有 Java 文件
   s04 >> 创建一个名为 test.txt 的文件，内容为 "Hello World"
   s04 >> 读取 README_zh.md 文件的前 10 行
   s04 >> 使用子 Agent 检查项目结构
   ```

## 📁 目录结构
```
LearnCC4j_04/
├── src/
│   └── main/
│       └── java/
│           └── com/example/
│               ├── SubagentApplication.java    # 主应用入口
│               ├── AgentToolsConfig.java       # 工具系统配置
│               └── AgentTemplate.java        # Agent 模板解析器（教学用途）
├── README_zh.md                              # 中文文档
├── README_en.md                              # 英文文档
└── README.md                                 # 多语言主文档
```

## 🔒 安全特性
- **路径安全**：所有文件操作均通过 `safePath()` 方法验证，防止路径遍历攻击
- **命令安全**：`bashTool` 自动拦截危险命令（`rm -rf /`, `sudo`, `shutdown`, `reboot` 等）
- **内存限制**：文件读取和输出内容自动截断（50,000 字符上限）
- **超时控制**：shell 命令执行超时设置为 120 秒
- **上下文隔离**：每个子 Agent 拥有独立的内存，防止信息泄露

## 🤝 贡献指南
欢迎提交 Issue 和 Pull Request！
- 报告 Bug 时请提供详细的复现步骤
- 新功能开发请先讨论设计思路
- 代码风格请遵循项目现有规范

## 📜 许可证
Apache License 2.0

---
> 📝 **注**：本项目为学习目的设计，实际生产环境需根据具体需求进行安全加固和性能优化。