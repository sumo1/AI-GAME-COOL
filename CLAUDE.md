# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 实现前必读

在修改代码或编写新功能之前，按需读取：

1. **`docs/engineering/conventions.md`** — 工程规范（包结构、命名、Tool/Skill 设计、错误处理、AgentLoop 边界、评估系统规范）
2. **`docs/review/code-check.md`** — 工程审查标准
3. **`docs/knowledge/principles/agent-system-philosophy.md`** — Agent 系统设计哲学（双 SSOT + 三时间方向）
4. **当前活跃任务目录**（`docs/task/` 下按时间戳倒序，取最新的）：
   - `memory/` — 决策备忘录（优先级高于早期 plan）
   - `progress.md` — 当前进展和待办
   - `plan/` — 子任务设计与契约

## Agent 体系

本仓库使用 7 个独立工种协作完成开发（流程 SSOT 在 `agents/`，平台薄引用在 `.claude/agents/`）：

| Agent | 角色 | 触发 |
|-------|------|------|
| `task-designer` | 把需求拆为带双契约的可执行计划 | 收到新需求时 |
| `coder` | 按契约精准施工 | 子任务 plan 就绪后 |
| `evaluator` | 独立复跑命令、机器化验收 | coder 交付后 |
| `code-reviewer` | 工程标准 + 任务专项审查 | 提交前 |
| `doc-refresher` | 业务知识 SSOT 反漂移哨兵 | 每次提交 |
| `dreamer` | 蒸馏 memory 上浮 knowledge | 阶段收尾 / memory ≥ 10 条 |
| `ci-pre-checker` (git-push) | 提交流水线守门 | 用户说"提交"/"push" |

## Git 提交

使用 `git-push` skill 触发完整提交流程。直接说"push"或"提交"即可。

## 内容分层

| 层 | 位置 | 说明 |
|----|------|------|
| 工程标准 | `docs/engineering/` + `docs/review/` | 跨任务长期有效 |
| Agent 流程 | `agents/` | skill 和 subagent 的流程 SSOT |
| 平台配置 | `.claude/` | Claude Code 薄引用，指向 `agents/` |
| 任务文档 | `docs/task/{YYMMDD}-{name}/` | 架构、进度、决策、审查历史 |
| 跨任务知识 | `docs/knowledge/` | 由 dreamer 上浮维护 |

## Project Overview

This is a Children's Game Generation Agent Framework (儿童游戏生成Agent框架) that uses AI to generate educational games for children through natural language conversations. The project follows a plugin-based architecture with Spring Boot backend and React frontend.

## Build and Run Commands

### Backend (Spring Boot)
```bash
# Compile backend
cd game-agent-backend
mvn clean compile

# Run tests
mvn test

# Run backend server
mvn spring-boot:run

# Build JAR
mvn clean package -DskipTests

# Run specific test
mvn test -Dtest=TestClassName
mvn test -Dtest=TestClassName#methodName
```

### Frontend (React + Vite)
```bash
# Install dependencies
cd game-agent-frontend
npm install

# Run development server (port 5173)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Type checking
tsc
```

### Full Project Startup
```bash
# Interactive startup (recommended) - prompts for storage type
./start.sh

# Quick start with memory storage (no Docker required)
./quick-start.sh

# With specific RAG configuration
AGENT_RAG_TYPE=elasticsearch ./start.sh  # Use Elasticsearch (requires Docker)
AGENT_RAG_TYPE=memory ./start.sh         # Use memory storage (no Docker)
AGENT_RAG_ENABLED=false ./start.sh       # Disable RAG completely

# Configuration setup
./configure.sh  # Interactive environment setup
```

### Elasticsearch Management
```bash
# Using management script (interactive menu)
./es-manage.sh

# Using docker-compose directly
docker-compose up -d elasticsearch       # Start
docker-compose stop elasticsearch        # Stop
docker-compose logs -f elasticsearch     # View logs
docker-compose down                      # Stop and remove

# Check Elasticsearch status
curl -s http://localhost:9200/_cluster/health
```

---

## V2 Architecture (Current)

### Design Philosophy: "LLM 负责理解，代码只做代码该做的事"

V2 的核心设计转变：

| 维度 | V1 做法 | V2 做法 |
|---|---|---|
| **任务决策** | Java 规则引擎（IntentAnalyzer + GameType 枚举匹配） | LLM 通过 Function Calling 自主决策 |
| **游戏生成** | 硬编码子 Agent（MathGameAgent 等），单次调用 | AgentLoop 多轮迭代，生成 → 评估 → 修复闭环 |
| **质量保障** | 无（生成即交付） | Playwright headless 评估 + 代码级检查 + 自动修复 |
| **扩展方式** | 写新 Java 类 | 写一个 SKILL.md 文件（操作手册） |
| **Skill 是什么** | 不存在 | SKILL.md 操作手册（LLM 读原文理解怎么做） |

**核心原则**：
- **Frontmatter 给机器用**（发现 + 过滤），**Body 给 LLM 读**（理解 + 执行）
- **代码级检查只做 LLM 做不到的事**（HTML 结构检查、Probe 运行时数据分析）
- **修复策略、评估标准、生成步骤全部写在 SKILL.md 中**，LLM 自己阅读理解

### Package Structure

```
com.sumo.agent/
├── Application.java
│
├── api/                              # REST 端点
│   ├── GameChatController.java       #   POST /api/game/generate (v1)
│   └── GameStorageController.java    #   POST /api/game/v2/generate (v2)
│
├── infra/                            # 基础设施
│   ├── model/                        #   LLM 模型配置
│   │   ├── ChatModelRegistry.java    #     模型路由（DashScope/Kimi/Deepseek/OpenAI）
│   │   ├── DashScopeConfig.java
│   │   └── ...
│   ├── config/                       #   应用配置（Jackson, RestClient）
│   └── storage/                      #   游戏存储（GameStorageService, SavedGame）
│
├── knowledge/                        # RAG 知识层
│   ├── VectorStore.java
│   └── ...（ES/Memory/Embedded 实现）
│
├── agent/                            # Agent 核心域
│   ├── loop/                         #   执行引擎
│   │   ├── AgentLoop.java            #     多轮迭代 + 质量门禁
│   │   ├── AgentLoopResult.java
│   │   └── WorkingMemory.java        #     工作记忆（游戏状态追踪）
│   ├── tools/                        #   Tool 层（5 个独立 Bean）
│   │   ├── ToolContext.java           #     共享状态桥梁（WorkingMemory + ActiveSkill）
│   │   ├── skill/                     #     SkillListTool / SkillLoadTool
│   │   ├── generation/                #     GameGenerationTool / GameFixTool
│   │   └── evaluation/                #     GameEvaluationTool
│   ├── skill/                        #   Skill 系统（对齐 AgentSkills.io 规范）
│   │   ├── SkillDefinition.java      #     数据结构（name + description + metadata + instructions）
│   │   └── SkillLoader.java          #     加载器（解析 SKILL.md frontmatter + body）
│   └── evaluation/                   #   游戏评估
│       ├── GameEvaluator.java        #     Playwright headless 渲染 + Probe 数据收割
│       ├── EvaluationCheck.java      #     通用代码级检查（函数式接口）
│       └── ProbeReport.java          #     评估报告
│
└── legacy/                           # V1 遗留代码（@Deprecated）
    ├── core/                         #   BaseAgent, GameGeneratorAgent, GameConfig
    ├── analyzer/                     #   IntentAnalyzer
    ├── games/                        #   MathGameAgent, MemoryGameAgent, UniversalGameAgent
    └── impl/                         #   EnglishLearningGameAgent, TrafficSafetyGameAgent
```

### Skill System (AgentSkills.io 规范)

每个 Skill 是一个目录，遵循 [AgentSkills.io](https://agentskills.io) 开放规范：

```
resources/skills/
├── math-adventure/
│   ├── SKILL.md              # 操作手册（frontmatter + Markdown body）
│   └── assets/
│       └── template.html     # HTML 参考模板
├── memory-master/
├── english-explorer/
├── traffic-safety/
├── shape-colors/
└── logic-puzzle/
```

**SKILL.md 结构**：
```markdown
---
name: math-adventure                    # frontmatter: 机器用（发现、过滤）
description: 生成 4-8 岁儿童的数学加减法互动游戏...
metadata:
  ageGroup: "4-8"
  gameType: quiz
  tags: [数学, 加法, 减法]
---

# 数学冒险                              # body: LLM 读（理解、执行）

## 何时使用
用户提到数学、加法、减法...时激活。

## 生成步骤
1. 根据年龄段确定数值范围...
2. 参考 assets/template.html...
3. 调用 generateGame...

## 评估重点
- 每道题的答案必须算术正确
- 答对/答错必须有即时反馈
- ...

## 常见问题
- **答案计算错误** → 检查 JS 中使用 parseInt()...
- **难度不递进** → 确保数值范围随答对次数递增...
```

**渐进式披露（Progressive Disclosure）**：
1. **发现**：启动时只加载 frontmatter（name + description ~100 tokens），用于匹配和过滤
2. **激活**：任务匹配时，SkillLoadTool 返回完整 SKILL.md body（LLM 读操作手册）
3. **执行**：LLM 按手册调 generateGame / evaluateGame / fixGame

### Agent Loop 执行流程

```
POST /api/game/v2/generate
  │
  ▼
AgentLoop.run()
  ├─ tryPreloadSkill("记忆翻牌" → memory-master)
  │
  └─ ChatClient.call()  ← 一次调用，Spring AI 内部自动多轮 FC
       │
       ├─ FC1: loadSkill("memory-master")
       │        → 激活 activeSkill
       │        → 返回 SKILL.md 操作手册原文 + template.html
       │        → LLM 读完知道：生成步骤、评估重点、常见坑
       │
       ├─ FC2: generateGame(design)
       │        → system prompt 注入 SKILL.md instructions
       │        → LLM 在生成时就知道"会被查什么"
       │        → HTML → WorkingMemory
       │
       ├─ FC3: evaluateGame(html)
       │        → Playwright 通用五维评分（可运行/布局/交互/完整/教育）
       │        → + gameType 派生的代码检查（matching → hasMatch + hasInteraction）
       │        → 评分 < 80 → 继续
       │
       ├─ FC4: fixGame(issues)
       │        → LLM 对话历史中已有 SKILL.md "常见问题"段
       │        → 带着领域知识修复，不需要代码额外注入
       │
       ├─ FC5: evaluateGame(html)
       │        → 评分 ≥ 80 → 达标
       │
       └─ LLM 返回文本总结 → .call() 结束
  │
  └─ AgentLoopResult.success(html, message, iterations, evalScore)
```

### API Endpoints

```
POST /api/game/v2/generate       # V2: AgentLoop 多轮迭代（推荐）
POST /api/game/generate           # V1: 传统 Agent 单次生成（兼容保留）
GET  /api/game/agents             # 列出已注册的 V1 Agent
GET  /api/game/generate/stream    # SSE 流式生成（V1）
```

### Adding a New Game Type (V2)

不需要写 Java 代码。创建一个 SKILL.md 文件即可：

```bash
mkdir -p src/main/resources/skills/my-new-game/assets
```

写 `SKILL.md`（对齐 [AgentSkills.io 规范](https://agentskills.io/specification)）：
```markdown
---
name: my-new-game
description: 描述这个游戏是什么、什么时候用。
metadata:
  gameType: quiz
  tags: [关键词1, 关键词2]
---

# 我的新游戏

## 何时使用
用户提到 xxx 时激活。

## 生成步骤
1. 确定参数...
2. 调用 generateGame...

## 评估重点
- 必须有 xxx
- 不能有 yyy

## 常见问题
- **问题 A** → 解决方案 A
```

可选：在 `assets/template.html` 放一个参考模板。重启应用即可生效。

---

## V1 Architecture (Legacy, @Deprecated)

V1 代码保留在 `legacy/` 包中，通过 `POST /api/game/generate` 端点仍可访问。

### Agent Lifecycle (Template Method Pattern)

```
用户输入 → IntentAnalyzer（规则引擎）→ GameGeneratorAgent（编排器）
    → selectAgent（按 GameType 枚举匹配）→ 子 Agent.run()
        ├── MathGameAgent（内置 HTML 模板）
        ├── MemoryGameAgent（内置 HTML 模板）
        ├── EnglishLearningAgent（内置 HTML 模板）
        ├── TrafficSafetyGameAgent（内置 HTML 模板）
        └── UniversalGameAgent（LLM 单次生成）
```

- `BaseAgent` 抽象类定义生命周期：`run()` → `preHandle()` → `execute()` → `postHandle()` → `handleError()`
- 所有子 Agent 通过 `@Component` 自动注册到 `GameGeneratorAgent`

### V1 的局限（V2 解决的问题）

| 问题 | V1 | V2 |
|---|---|---|
| 单次调用无法迭代 | 生成一锤子买卖，质量不稳定 | AgentLoop 最多 5 轮迭代，评分 ≥ 80 才交付 |
| 硬编码子 Agent | 新增游戏类型要写 Java 类 | 写一个 SKILL.md 文件即可 |
| 规则引擎意图识别 | 无法理解复杂/模糊需求 | LLM 原生 Function Calling 自主决策 |
| 无质量评估 | 生成即交付，可能有 bug | Playwright 渲染 + Probe 注入 + 五维评分 + 代码检查 |
| 无自动修复 | 用户自己发现问题 | evaluateGame 发现问题 → fixGame 自动修复 → 再评估 |

### Core Data Models (V1)

```java
// GameIntent (in legacy/core/GameGeneratorAgent.java)
GameIntent(GameType gameType, String ageGroup, DifficultyLevel difficulty, ...)

// Enums (in legacy/core/GameConfig.java)
GameType: MATH, WORD, MEMORY, PUZZLE, DRAWING
DifficultyLevel: EASY, MEDIUM, HARD
Theme: ANIMALS, SPACE, FAIRY_TALE, OCEAN, DINOSAUR, SUPERHERO
```

---

## Infrastructure

### RAG Storage Architecture

**Strategy Pattern**: `VectorStore` interface in `knowledge/`

| 实现 | 用途 | 依赖 |
|---|---|---|
| `InMemoryVectorStore` | 开发环境 | 无 |
| `ElasticsearchVectorStore` | 生产环境 | Docker + ES |
| `EmbeddedVectorStore` | 本地文件 | 无 |

### Spring AI Integration

- **Version**: Spring AI 1.0.0 with Spring Boot 3.2.2
- **Primary Provider**: Alibaba DashScope（通义千问，通过 `ChatModelRegistry` 路由）
- **Multi-Model**: DashScope / Kimi K2 / Qwen3 Coder Plus / Deepseek / OpenAI
- **Function Calling**: `ChatClient.tools(...)` 原生支持，Spring AI 内部自动处理 FC 循环

### Environment Configuration

```bash
# .env file
export ALIYUN_API_KEY=your-dashscope-api-key
export AGENT_RAG_TYPE=memory    # memory | elasticsearch | embedded

# Optional
export AI_MODEL=qwen-plus       # LLM model name
export SERVER_PORT=8088          # Default: 8088
```

## Important Technical Notes

### Java Version and Dependencies
- **Java 17+**（Spring Boot 3.2.2 兼容 17~21）
- **Jakarta EE** (not javax): Use `jakarta.annotation.PostConstruct`
- **Playwright**: 首次运行 evaluateGame 时自动下载 Chromium（~120MB）

### Character Encoding
- **All files must use UTF-8 encoding**
- Chinese characters in logs and comments are expected
- Set JVM flag if needed: `-Dfile.encoding=UTF-8`
