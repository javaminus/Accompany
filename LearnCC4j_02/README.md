# 🧠 LearnCC4j-02: Tool-Use Agent — 可扩展工具调用智能体
注：这份README直接调用tools写的

> **A lightweight, safe, and extensible coding agent that *uses tools* — not just thinks.**  
> **一个轻量、安全、可扩展的编程智能体，真正 *使用工具*，而非仅推理。**

---

## ✨ What It Does | 它能做什么？

This project implements a **production-grade tool-use loop**, where the LLM is *not* asked to simulate actions — instead, it **declares intent via structured tool calls**, and the system **executes them safely and precisely**.

本项目实现了一个**生产级工具调用循环**：LLM 不再“模拟”操作，而是通过**结构化工具调用声明意图**，系统则**安全、精准地执行**。

### 🔧 Built-in Tools | 内置工具
| Tool | Purpose | 安全机制 |
|------|---------|----------|
| `bash` | Run shell commands (e.g., `ls`, `cat`) | ❌ Blocks `rm -rf`, `sudo`, `shutdown`, absolute/`..` paths |
| `read_file` | Read file content (with line limit) | ✅ Sanitizes path, enforces 50k char cap |
| `write_file` | Write/overwrite file | ✅ Auto-creates parent dirs, sanitizes path |
| `edit_file` | Replace *first occurrence* of `old_text` with `new_text` | ✅ Exact-match only, no regex — safe & predictable |

### 🛡️ Safety First | 安全第一
- All file paths are normalized and validated via `safe_path()` — no directory traversal.
- All bash commands are filtered against a denylist of dangerous patterns.
- Tool timeouts (120s), size limits, and exception fallbacks prevent hangs or crashes.

- 所有文件路径经 `safe_path()` 标准化校验，杜绝路径遍历。
- 所有 Bash 命令受黑名单过滤（禁止 `rm -rf` / `sudo` 等）。
- 工具执行设超时（120s）、大小限制与异常兜底，防卡死/崩溃。

---

## ▶️ Quick Start | 快速启动

```bash
# 1. Enter project dir
 cd F:\learn_ai\Accompany\LearnCC4j_02

# 2. Run the agent (requires Python 3.10+)
 python ./src/main/java/com/example/s02_tool_use.py

# 3. Type natural language instructions, e.g.:
 # → "List all .py files in current folder"
 # → "Read first 5 lines of s01_agent_loop.py"
 # → "Replace 'v0.1' with 'v0.2' in README.md"
```

---

## 🧩 Design Philosophy | 设计哲学

> **"The loop didn't change at all. I just added tools."**  
> — This agent reuses the *exact same core loop* from `s01_agent_loop.py`.  
> Tools are injected via `TOOL_HANDLERS` and `TOOLS` — zero changes to `agent_loop()`.

> **“主循环一丁点都没改，我只是加了工具。”**  
> — 本智能体复用 `s01_agent_loop.py` 的**完全相同的主循环逻辑**。  
> 工具仅通过 `TOOL_HANDLERS` 和 `TOOLS` 注入 — `agent_loop()` 函数本身**零修改**。

This makes the system highly maintainable and extensible: add a new tool by defining its schema + handler — done.

这使系统高度可维护与可扩展：只需定义新工具的 `schema` + `handler`，即可接入 — 就是这么简单。

---

## 🧭 Mind Map | 思维导图

```
LearnCC4j-02: Tool-Use Agent
├── Core Principle
│   └── "The loop didn't change — just added tools"
├── Agent Loop
│   ├── Input: Natural language instruction
│   ├── LLM: Outputs structured tool call (tool_name + args)
│   ├── Executor: Validates & runs tool safely
│   └── Loop: Observations → next LLM call → repeat
├── Tools
│   ├── bash
│   │   └── Safe command execution (denylist + timeout)
│   ├── read_file
│   │   └── Path sanitization + size limit
│   ├── write_file
│   │   └── Auto-dir creation + path sanitization
│   └── edit_file
│       └── Exact string replacement (no regex)
├── Safety Mechanisms
│   ├── Path normalization (`safe_path`)
│   ├── Command denylist (rm -rf, sudo, ...)
│   ├── Timeout (120s)
│   └── Size caps & error fallbacks
└── Extensibility
    └── Add new tool: define schema + handler → register in TOOLS/TOOL_HANDLERS
```

---

## 📚 Related | 关联项目
- [`LearnCC4j-01`](./../LearnCC4j_01): Basic agent loop (no tools) — 基础智能体循环（无工具）
- [`s02_tool_use.py`](./src/main/java/com/example/s02_tool_use.py): This file — 本项目主入口

---
