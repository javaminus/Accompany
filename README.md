# Accompany

> **一句话描述（可用于仓库简介）**：一个把「AI 编程助手」拆成可学习、可复用、可扩展实战模块的 Java/Spring AI 训练场。  

Accompany 是一个以 **Spring Boot + Spring AI + Java 17** 为核心的多模块实践仓库，围绕 Agent 从基础循环到多 Agent 协作、任务系统、上下文压缩、工作树隔离等能力，提供循序渐进的示例工程。

## 为什么值得看

- 从 `LearnCC4j_01` 到 `LearnCC4j_12`，覆盖 Agent 能力演进路线；
- 每个模块都聚焦一个主题，便于拆分学习与二次改造；
- 包含完整工程结构（Maven 多模块、配置、工具注入、服务分层）；
- 适合用于 AI Coding Agent 教学、实验与原型开发。

## 仓库结构

```text
Accompany/
├── FuckAIGC/         # AIGC 检测对抗相关示例
├── ClaudeCode/       # 组合式 Agent 示例（团队协作/服务化）
├── LearnCC4j_01~12/  # 分阶段 Agent 实践模块
├── skills/           # 技能文档（Skill）
└── pom.xml           # Maven 聚合工程
```

## 主要模块速览

- **LearnCC4j_01**：基础 Agent Loop
- **LearnCC4j_02**：工具调用（Tool Use）Agent
- **LearnCC4j_03**：规划型 Agent（Todo/多步执行）
- **LearnCC4j_04**：子 Agent 架构
- **LearnCC4j_05**：Skill 加载机制
- **LearnCC4j_06**：上下文压缩
- **LearnCC4j_07**：任务系统
- **LearnCC4j_08**：后台任务管理
- **LearnCC4j_09~11**：Agent 团队协作与协议
- **LearnCC4j_12**：Worktree 任务隔离
- **ClaudeCode**：更完整的实战型 Agent 组织方式
- **FuckAIGC**：针对 AIGC 场景的实验模块

## 快速开始

### 1) 环境要求

- JDK 17+
- Maven 3.8+

### 2) 克隆并进入项目

```bash
git clone https://github.com/javaminus/Accompany.git
cd Accompany
```

### 3) 查看模块

```bash
mvn -q -DskipTests help:effective-pom
```

### 4) 运行指定模块（示例）

```bash
cd LearnCC4j_03
mvn spring-boot:run
```

## 说明

- 本仓库包含若干依赖 Spring AI 里程碑版本的模块，首次构建需确保网络可访问对应 Maven 仓库。
- 建议按模块顺序学习；每个模块目录下通常包含独立说明文档。

## 贡献

欢迎提交 Issue / PR，一起完善 AI Agent 工程化实践。
