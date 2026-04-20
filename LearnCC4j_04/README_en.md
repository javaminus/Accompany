# LearnCC4j_04 - Subagent Architecture and Tool Configuration

[![Java Version](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/) [![Maven](https://img.shields.io/badge/Maven-3.8%2B-green)](https://maven.apache.org/) [![License](https://img.shields.io/badge/License-Apache%202.0-red)](https://www.apache.org/licenses/LICENSE-2.0)

> **Project Overview**: LearnCC4j_04 is an educational project demonstrating multi-layer Agent architecture in the Spring AI framework, focusing on parent-child Agent collaboration, tool system configuration, and context isolation techniques.

## 📋 Project Overview

LearnCC4j_04 implements a hierarchical intelligent agent (Agent) system containing a **parent Agent** and dynamically creatable **child Agents**. The core objective of this project is to demonstrate how to build secure, scalable, and context-isolated multi-Agent systems within the Spring AI framework.

Unlike traditional single-layer Agents, this project implements **recursive task decomposition** through the `taskTool` - the parent Agent can delegate complex tasks to child Agents, while child Agents return only summary results, maintaining the purity of the parent Agent's context.

## 🧩 Core Components

### 1. `SubagentApplication.java` - Main Application Entry Point
- Spring Boot startup class implementing the `CommandLineRunner` interface
- Creates the parent Agent's `ChatClient` configured with the complete toolset (including `taskTool`) 
- Provides an interactive command-line interface where users can interact with the system via the `s04 >>` prompt
- Supports exit commands: `q`, `exit`, or empty input

### 2. `AgentToolsConfig.java` - Tool System Configuration
- Configuration class using `@Configuration` annotation to define Spring Beans
- **Base Toolset** (shared by parent/child Agents):
  - `bashTool`: Safely execute shell commands (automatically blocks dangerous commands like `rm -rf /`)
  - `readFileTool`: Read file contents (supports line limit)
  - `writeFileTool`: Write content to files (automatically creates directories)
  - `editFileTool`: Replace specified text in files
- **Subagent Dispatch Tool** (available only to parent Agent):
  - `taskTool`: Dynamically create isolated child Agent instances for task delegation
  - Key feature: Assigns independent `InMemoryChatMemory` to each child Agent for context isolation
  - Child Agents don't have `taskTool`, preventing infinite recursion

### 3. `AgentTemplate.java` - Agent Template Parser (Educational Purpose)
- **Educational demonstration class**: Shows architectural design of real commercial products (like Anthropic Claude Code)
- Parses Markdown files with YAML Frontmatter (e.g., `.claude/agents/*.md`)
- Supported format:
  ```markdown
  ---
  name: DatabaseExpert
  model: claude-3-5-sonnet
  tools: [read_file, write_file, sql_query]
  ---
  You are an expert at database performance tuning.
  Always check the slow query log first.
  ```
- Extracts YAML configuration into `Map<String, String>` and extracts body as System Prompt
- **Note**: This class is not actually used in this project, retained solely for architectural education

## ⚙️ Quick Start

### Requirements
- Java 17+
- Maven 3.8+
- Spring Boot 3.x

### Running Steps
1. Clone the project and navigate to the directory:
   ```bash
   cd LearnCC4j_04
   ```

2. Compile the project:
   ```bash
   mvn clean compile
   ```

3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

4. Try the following commands in the interactive terminal:
   ```
   s04 >> List all Java files in the current directory
   s04 >> Create a file named test.txt with content "Hello World"
   s04 >> Read the first 10 lines of README_en.md
   s04 >> Use subagent to examine project structure
   ```

## 📁 Directory Structure
```
LearnCC4j_04/
├── src/
│   └── main/
│       └── java/
│           └── com/example/
│               ├── SubagentApplication.java    # Main application entry point
│               ├── AgentToolsConfig.java       # Tool system configuration
│               └── AgentTemplate.java        # Agent template parser (educational purpose)
├── README_zh.md                              # Chinese documentation
├── README_en.md                              # English documentation
└── README.md                                 # Multilingual main documentation
```

## 🔒 Security Features
- **Path Safety**: All file operations validated through `safePath()` method to prevent path traversal attacks
- **Command Safety**: `bashTool` automatically blocks dangerous commands (`rm -rf /`, `sudo`, `shutdown`, `reboot`, etc.)
- **Memory Limits**: File reading and output content automatically truncated (50,000 character limit)
- **Timeout Control**: Shell command execution timeout set to 120 seconds
- **Context Isolation**: Each subagent has independent memory, preventing information leakage

## 🤝 Contribution Guidelines
Contributions are welcome! Please follow these guidelines:
- Report bugs with detailed reproduction steps
- Discuss design approaches before implementing new features
- Follow existing code style conventions

## 📜 License
Apache License 2.0

---
> 📝 **Note**: This project is designed for educational purposes. Production environments require additional security hardening and performance optimization based on specific requirements.