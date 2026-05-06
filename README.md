# 曾经
曾经：希望有个游戏机，里面有无限的游戏。而不是一个俄罗斯方块玩几年。

于是：
自己做一个喽。
给儿子玩。
他有了无限的游戏。

# 🎮 儿童游戏生成 Agent 框架

用自然语言，一键生成可玩的儿童教育游戏。Java + React，V2 默认走 AgentLoop + Skill 架构，支持阿里云百炼（通义千问/Kimi/DeepSeek/Qwen3），可选 RAG，保留 V1 离线模板兼容入口。

自带游戏
![img.png](img.png)

模型生成游戏

一句话生成一个贪吃蛇游戏。

![img_1.png](img_1.png)

做一个简单的英语单词拼写游戏

![img_2.png](img_2.png)

[![Java](https://img.shields.io/badge/Java-17+-red)](#)
[![SpringBoot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F)](#)
[![React](https://img.shields.io/badge/React-18-61DAFB)](#)
[![Vite](https://img.shields.io/badge/Vite-5-purple)](#)
[![Elasticsearch](https://img.shields.io/badge/RAG-Elastic-orange)](#)
[![License](https://img.shields.io/badge/License-MIT-black)](#)

## 你能用它做什么

- 亲子计划的一部分。
- 用中文描述生成教育小游戏（数学/记忆/词汇/拼图/自由玩法）。
- 设置年龄段/难度/主题，秒级预览并一键导出 HTML。
- 接入你自己的 Agent 与模板，沉淀专属内容库。
- 可选 RAG（Elasticsearch/内存）做教材/课程定制练习。
- 熟悉Agent架构与大模型集成的示例。

## 特性一览

- 🤖 智能对话生成：AgentLoop 生成完整 HTML（内联 CSS/JS），并通过评估工具自动修复。
- 🧩 兼容内置系统游戏：V1 路由仍可访问数学/记忆等离线模板。
- 🔌 插件化 Agent：注册即用、低耦合。
- 🛰️ 多模型路由（百炼）：
    - dashscope 默认（通义千问）
    - kimi-k2（Moonshot-Kimi-K2-Instruct）
    - qwen3-coder-plus（Qwen3 Coder Plus）
    - deepseek（deepseek-v3.1）
- 🧠 RAG 可选：Elasticsearch/内存，两行命令起停。
- 🔭 可观测：响应标注来源、模型名、迭代次数与评估分，日志输出提示词（DEBUG）。

## 为什么值得 Star

- 说人话就能做游戏：一句自然语言 → 可运行 HTML5 游戏。
- 默认主线清晰：前端走 V2 AgentLoop，多模型生成、评估、修复在一条链路里完成。
- 真·可观测：响应卡片显示“来源与模型”，Debug 日志输出完整提示词（System/User）。
- 真·可扩展：Skill 目录就是扩展点，新增一个 `SKILL.md` 即可接入新玩法。
- 真·可落地：脚本一键起（后端 8088），RAG/代理都是可选项。

## 快速开始

环境要求：Java 17+、Maven 3.6+、Node.js 18+（建议 20）；可选 Docker（Elasticsearch）。

### 🚀 超轻量一键启动（推荐）

```bash
# 一键启动 - 自动检查环境、安装依赖、配置参数、启动服务
./quick_start.sh

# 带 API Key 启动（跳过交互）
ALIYUN_API_KEY=你的百炼Key ./quick_start.sh
```

**特性**：
- 🔍 自动环境检查与修复建议
- 📦 自动安装依赖（Maven/npm）
- 🔑 智能 API Key 配置（环境变量/.env/交互输入）
- 🚀 并行启动前端后端，健康检查确保就绪
- 🎨 彩色输出与进度提示
- 🔄 Ctrl+C 优雅清理所有服务

### 传统启动方式

1) 使用 start.sh 脚本：
```bash
export ALIYUN_API_KEY=你的百炼Key
./start.sh
```

2) 手动启动：
```bash
# 后端
cd game-agent-backend
mvn spring-boot:run                   # 默认 8088
SERVER_PORT=8090 mvn spring-boot:run  # 指定端口

# 前端
cd ../game-agent-frontend
npm install && npm run dev            # 默认代理 http://localhost:8088
BACKEND_URL=http://localhost:8090 npm run dev
```

### 访问地址

- 前端：http://localhost:5173（Vite）
- 后端：http://localhost:8088（可用 `SERVER_PORT` 覆盖）

## 架构速览（V2 — 当前）

### 设计哲学："LLM 负责理解，代码只做代码该做的事"

| 维度 | V1（旧） | V2（现） |
|---|---|---|
| **任务决策** | Java 规则引擎匹配 GameType | LLM 通过 Function Calling 自主决策 |
| **游戏生成** | 硬编码子 Agent，单次调用 | 编排器 LLM 直接生成 HTML → saveGame 存储 → 评估 → 修复 |
| **质量保障** | 无（生成即交付） | Playwright headless + 代码检查 + 自动修复 |
| **扩展方式** | 写一个新 Java 类 | 写一个 SKILL.md 文件 |

### 执行流程

```
"生成一个记忆翻牌游戏"
  │
  ▼
AgentLoop
  └─ ChatClient.call()  ← 一次调用，Spring AI 内部自动多轮 FC
       │
       ├─ loadSkill("memory-master")
       │    → LLM 读 SKILL.md 操作手册（生成步骤/评估重点/常见问题）
       │
       ├─ LLM 直接编写完整 HTML 游戏代码
       │    → saveGame(html) → 清洗 + 存入 WorkingMemory
       │
       ├─ evaluateGame(html)
       │    → Playwright 渲染 + Probe 注入 + 五维评分
       │    → 评分 < 80 → 继续
       │
       ├─ LLM 直接修改 HTML 修复问题
       │    → saveGame(fixedHtml) → 清洗 + 存入 WorkingMemory
       │
       └─ evaluateGame(html) → 评分 ≥ 80 → 交付
```

**单层 LLM 架构**：编排器 LLM 亲自生成/修复 HTML，Tool 只做存储和评估，不再嵌套调用 LLM。

### Skill 系统（[AgentSkills.io](https://agentskills.io) 规范）

每个 Skill 是一个目录，不是代码：

```
resources/skills/
├── math-adventure/
│   ├── SKILL.md              # 操作手册（LLM 读这个理解怎么做）
│   └── assets/template.html  # HTML 参考模板
├── memory-master/
├── english-explorer/
├── traffic-safety/
├── shape-colors/
└── logic-puzzle/
```

SKILL.md 对齐 [AgentSkills.io 规范](https://agentskills.io/specification)——frontmatter 只需 `name` + `description`，领域信息放 `metadata`：
```markdown
---
name: math-adventure
description: 生成 4-8 岁儿童的数学加减法互动游戏。当用户需要数学、算术类游戏时使用。
metadata:
  ageGroup: "4-8"
  gameType: quiz
  tags: [数学, 加法, 减法]
---
# 数学冒险                    # body: LLM 读操作手册
## 何时使用 / ## 生成步骤 / ## 评估重点 / ## 常见问题
```

### 后端包结构

```
com.sumo.agent/
├── api/           # REST 端点
├── infra/         # 基础设施（模型配置/存储/Jackson）
├── knowledge/     # RAG 知识层
├── agent/         # 核心域
│   ├── loop/      #   AgentLoop + WorkingMemory + AgentPrompts
│   ├── tools/     #   4 个 Tool Bean（SkillList/SkillLoad/GameSave/GameEvaluation）
│   ├── skill/     #   SkillDefinition + SkillLoader（解析 SKILL.md）
│   └── evaluation/#   Playwright GameEvaluator
└── legacy/        # V1 遗留（@Deprecated）
```

### 扩展一个新游戏（V2 方式）

不需要写 Java 代码，创建 SKILL.md 文件即可：

```bash
mkdir -p game-agent-backend/src/main/resources/skills/my-game/assets
```

写 `skills/my-game/SKILL.md`（[AgentSkills.io 规范](https://agentskills.io/specification)）：
```markdown
---
name: my-game
description: 什么游戏、什么时候用。
metadata:
  gameType: quiz
  tags: [关键词]
---
# 我的游戏
## 何时使用 / ## 生成步骤 / ## 评估重点 / ## 常见问题
```

重启应用即可生效。

---

## V1 兼容入口（Legacy）

V1 代码保留在 `legacy/` 包中，通过 `POST /api/game/generate` 仍可访问。前端默认不再调用该入口；它只用于旧调用方、离线模板回退或兼容测试。

```
用户输入 → IntentAnalyzer（规则引擎）→ GameGeneratorAgent → 子 Agent.run()
    ├── MathGameAgent（内置 HTML 模板）
    ├── MemoryGameAgent（内置模板）
    └── UniversalGameAgent（LLM 单次生成）
```

V1 方式扩展：继承 `BaseAgent` 写 Java 类，启动后自动注册。新游戏能力优先使用 V2 Skill 方式。

---

## 配置说明

- 必填：`ALIYUN_API_KEY`（阿里云百炼 API Key）
- 可选：
  - `SERVER_PORT`：后端端口（默认 8088）
  - `AGENT_RAG_TYPE`：`memory`（默认）/ `elasticsearch` / `none`
  - `PROXY_ENABLED/TYPE/HOST/PORT`：代理配置

RAG（可选）：
```bash
docker-compose up -d elasticsearch
```

## API

前端默认调用 V2：

```bash
# V2（默认）：AgentLoop 多轮迭代
POST /api/game/v2/generate
{ "userInput": "给6岁孩子做一个10以内加法游戏" }

# V1（兼容）：传统 Agent 单次生成，不作为前端默认路径
POST /api/game/generate
{ "userInput": "...", "options": { "model": "deepseek" } }

# Agent 列表
GET /api/game/agents
```

## 路线图

- ~~可玩性自动评分器 + 策略修正回路~~ ✅ 已实现（Playwright + ProbeReport + 编排器 LLM 直接修复）
- ~~Skill 系统~~ ✅ 已实现（SKILL.md + AgentSkills.io 规范）
- 更多学科 Skill（语文/科学/艺术）
- 家长端报告/进度追踪/分级推荐
- WebAssembly 沙盒执行
- 更多模型后端与离线小模型适配

## 贡献

欢迎 PR/Issue！请保证：

- 变更聚焦，避免引入不必要复杂性
- 补充必要说明与示例
- 中文 UTF-8 注释友好

## 许可证

MIT License

## 致谢

Spring Boot / React / 阿里云百炼团队与所有贡献者

---

如果它帮你节省了哪怕 10 分钟，请给它一颗 Star。你的 Star，会让更多孩子更快玩到更好的教育游戏。🌟
