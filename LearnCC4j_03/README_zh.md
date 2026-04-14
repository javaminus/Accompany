# LearnCC4j_03：Spring AI 规划型智能体（Planning Agent）

> 一个可教学、可调试、可扩展的多步任务执行框架 —— 让 LLM 真正「按步骤做事」

## 🌟 项目概述

`LearnCC4j_03` 是《AI 编程伴学》系列的第 3 个实践项目，聚焦 **规划型智能体（Planning Agent）** 的工程化落地。它不依赖黑盒 Agent 框架，而是基于 **Spring AI + 自定义工具协议 + 显式状态管理**，构建一个**可控、可观测、可教学**的智能体执行环境。

核心目标：
- ✅ 将模糊的用户请求（如“帮我生成 README”）自动分解为原子步骤（分析目录 → 读取文件 → 写入内容 → 验证）；
- ✅ 每一步执行后**强制刷新计划**，确保 agent 始终基于最新上下文决策；
- ✅ 通过 `todoTool` 实现「单步推进 + 状态显式化」，彻底替代不可靠的 chain-of-thought 自由发挥。

## ⚙️ 设计哲学与关键洞察

### 为什么 `todoTool` 是本项目灵魂？
- **单步锁定（`in_progress`）**：任意时刻仅允许一个任务处于 `in_progress`，杜绝并发混乱与状态漂移；
- **动态重规划（`refresh the plan`）**：每步完成即触发计划重生成，使 agent 能响应中间结果（如发现文件不存在则改用 `bashTool` 创建）；
- **工具即协议（not just functions）**：每个 tool 都携带语义契约（如 `readFileTool` 必须传 `path`，`writeFileTool` 必须含 `content`），Spring AI 提示词严格约束调用格式。

### 内存与提示的协同设计
- `MessageChatMemoryAdvisor` 拦截所有消息，注入当前 `todoTool` 状态快照；
- 系统 Prompt 明确声明工作目录（`F:\learn_ai\Accompany`）、可用工具列表及调用规则，让 LLM 在「受控沙箱」中思考。

## 🛠️ 技术栈

| 层级 | 组件 | 说明 |
|------|------|------|
| **AI 核心** | Spring AI 1.0.0-M5 | 提供 `ChatClient`、`ChatMemory`、`Tool` 抽象 |
| **运行时** | Spring Boot 3.3.x + Java 17 | 基于 Jakarta EE 9+，支持 GraalVM 原生镜像 |
| **内存** | `InMemoryChatMemory` + 自定义 `Advisor` | 会话级上下文 + 动态状态注入 |
| **工具层** | `bashTool`, `readFileTool`, `writeFileTool`, `editFileTool`, `todoTool` | 全部注册为 Spring Bean，符合 `FunctionCallback` 协议 |

## ▶️ 快速开始

```bash
# 1. 进入项目目录
cd LearnCC4j_03

# 2. 启动应用（默认使用本地 Ollama llama3:8b）
mvn spring-boot:run

# 3. 在 CLI 中输入指令（支持中文）
s03 >> 请为当前项目生成一份专业的双语 README
```

✅ 成功标志：控制台输出 `✅ README_zh.md 和 README_en.md 已生成`，且文件内容结构完整。

## 📁 目录结构

```
LearnCC4j_03/
├── src/main/java/com/example/
│   ├── PlanningAgentApplication.java     # 主启动类，集成 CLI 交互循环
│   ├── agent/
│   │   ├── TodoManager.java              # `todoTool` 的业务实现，管理步骤状态与刷新逻辑
│   │   └── PlanningAgentConfiguration.java # ChatClient、Memory、Tools 的装配中心
│   └── tool/
│       ├── BashTool.java                 # 封装 shell 命令执行
│       ├── ReadFileTool.java             # 安全读取文件（带行数限制）
│       └── ...                           # 其他工具实现
├── src/main/resources/
│   └── application.yml                   # 配置模型端点、超时、日志级别
└── README_zh.md                          # 本文件（中文主文档）
```

## 🤝 贡献指南

欢迎提交 PR！重点关注：
- 🔧 **新工具开发**：如 `gitTool`（执行 commit/push）、`httpTool`（调用 REST API）；
- 🧠 **规划增强**：改进 `TodoManager` 的重规划策略（如引入回溯机制）；
- 🌐 **多语言支持**：为 CLI 提示、系统 Prompt 添加国际化（i18n）能力；
- 📈 **可观测性**：集成 Micrometer 输出 step duration、tool call count 等指标。

## 📜 许可证

MIT License —— 可自由用于学习、教学与非商业项目。

---
📝 *本 README 由 `LearnCC4j_03` 自身生成，验证了其规划与执行能力。*