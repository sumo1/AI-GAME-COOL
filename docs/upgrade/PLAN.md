# AI-GAME-COOL Agent Loop 升级计划

> 本文档是升级项目的**规划中心**，由 Planner Agent 维护。
> 所有设计决策、任务拆分、进度追踪汇集于此。

## 0. 项目背景

### 当前架构（v1）

```
用户输入 → IntentAnalyzer（规则引擎）→ GameGeneratorAgent（编排器）
    → selectAgent（按 GameType 枚举匹配）→ 子 Agent.run()
        ├── MathGameAgent（内置模板）
        ├── MemoryGameAgent（内置模板）
        ├── EnglishLearningAgent（内置模板）
        ├── TrafficSafetyGameAgent（内置模板）
        └── UniversalGameAgent（LLM 单次生成 HTML）
```

**痛点**：
1. 单次调用，无法迭代优化——游戏生成一锤子买卖，质量不稳定
2. 硬编码的子 Agent——新增游戏类型要写新类
3. 规则引擎意图识别——无法理解复杂/模糊的需求
4. 无自主纠错——生成的 HTML 可能有 bug（越界、不可交互等），没有检测和修复机制
5. 无可玩性评估——Agent 无法"看到"游戏效果

### 目标架构（v2）

```
用户输入 → AgentLoop（多轮迭代）
    → LLM（Function Calling）→ 选择工具
        ├── generate_game（生成 HTML 游戏）
        ├── evaluate_game（评估可玩性：区域越界、交互响应、视觉效果）
        ├── fix_game（修复问题）
        ├── load_skill（加载内置游戏技能模板）
        └── ... 可扩展
    → 评估结果不满意 → 自动修复 → 继续迭代
    → 评估通过 → 返回最终游戏
```

**核心升级点**：
1. **Agent Loop 多轮迭代**：参考 Agent Harness 的 AgentLoop 设计，支持多轮 Think-Act-Observe
2. **Function Calling**：用 LLM 原生 tool_calls 协议替代文本解析
3. **Skill 化**：内置游戏模板变为 Skill（技能），通过工具加载，而非硬编码子 Agent
4. **自主纠错**：生成 → 评估 → 修复的闭环，Agent 能"看到"游戏并修复问题
5. **可玩性评估**：检测区域越界、碰撞逻辑、交互响应性、视觉完整性等

---

## 1. 设计决策记录

### ADR-001：LLM 选型与 Function Calling 支持

**状态**：✅ 已确认（2026-03-28）

**决定**：继续使用 Spring AI + DashScope（阿里云百炼），利用其 Function Calling 支持。

**理由**：通义千问/Qwen3 对 FC 支持够用，Spring AI 的 ChatClient 原生支持 functions() 注册和 tool_calls 解析。评估环节引入多模态模型看截图。

### ADR-002：游戏评估方案

**状态**：✅ 已确认（2026-03-28）

**决定**：Headless Browser 截图 + 视觉模型评估。

**方案**：
- 用 Playwright（Java 版）渲染生成的 HTML 游戏
- 截取游戏画面，提交给多模态 LLM（如 Qwen-VL）评估可玩性
- 评估维度：区域越界、碰撞逻辑、交互响应性、视觉完整性、布局合理性

**技术选型**：Playwright for Java（微软官方 Java 绑定，无需 Node.js 中间层）

### ADR-003：Skill 系统设计

**状态**：✅ 已确认（2026-03-28）

**决定**：YAML 格式描述文件。

**理由**：可读性好、支持多行文本（prompt 模板天然友好）、Java 有 SnakeYAML 支持。

**Skill 结构**：
```yaml
name: math_adventure
display_name: 数学冒险
description: 10以内加减法互动游戏
age_group: "4-8"
difficulty: easy
tags: [数学, 加法, 减法]
template: |
  <!DOCTYPE html>
  ... (内置 HTML 模板)
prompt_hint: |
  生成一个数学练习游戏，要求...
evaluation_criteria:
  - 数学题目难度是否匹配年龄段
  - 答对/答错是否有明确反馈
  - 是否有计分系统
```

**存储位置**：`src/main/resources/skills/` 目录下，每个 Skill 一个 YAML 文件

---

## 2. 任务拆分

### Phase 1：基础框架搭建（Agent Loop + Function Calling）

| # | 任务 | 状态 | 说明 |
|---|------|------|------|
| 1.1 | 设计 AgentLoop 核心类 | 🔲 待开始 | 参考 Harness 的 AgentLoop，适配 Java/Spring AI |
| 1.2 | 实现 Tool 协议和注册机制 | 🔲 待开始 | Tool 接口、ToolRegistry、ToolResult |
| 1.3 | 集成 Spring AI Function Calling | 🔲 待开始 | ChatClient 的 functions() 支持 |
| 1.4 | 实现 generate_game 工具 | 🔲 待开始 | 从 UniversalGameAgent 抽取 |
| 1.5 | 基本迭代逻辑 | 🔲 待开始 | Loop：LLM → tool_calls → execute → observe → continue |
| 1.6 | 前端适配 | 🔲 待开始 | 支持多轮迭代的流式展示 |

### Phase 2：Skill 系统

| # | 任务 | 状态 | 说明 |
|---|------|------|------|
| 2.1 | 设计 Skill 数据结构 | 🔲 待开始 | name/description/template/prompt/evaluation_criteria |
| 2.2 | 迁移现有游戏为 Skill | 🔲 待开始 | Math/Memory/English/Traffic → Skill YAML |
| 2.3 | 实现 load_skill 工具 | 🔲 待开始 | 从文件加载 Skill，注入上下文 |
| 2.4 | 实现 list_skills 工具 | 🔲 待开始 | 列出可用 Skill |

### Phase 3：自主纠错与可玩性评估

| # | 任务 | 状态 | 说明 |
|---|------|------|------|
| 3.1 | 实现 Game Runtime Probe（game-probe.js） | 🔲 待开始 | 注入式探针：事件追踪/状态变化/越界检测/错误捕获 |
| 3.2 | 实现 Playwright 集成（Java） | 🔲 待开始 | 渲染 HTML → 注入 Probe → 模拟操作 → 收割数据 |
| 3.3 | 实现 evaluate_game 工具 | 🔲 待开始 | Probe 数据 + 代码分析 → LLM 评估 → 结构化评分报告 |
| 3.4 | 实现 fix_game 工具 | 🔲 待开始 | 增量修补优先（前 3 次），全量重写兜底（第 4 次） |
| 3.5 | 闭环验证 | 🔲 待开始 | 生成 → 评估 → 修复 → 再评估的完整流程 |

### Phase 4：优化与完善

| # | 任务 | 状态 | 说明 |
|---|------|------|------|
| 4.1 | 上下文管理 | 🔲 待开始 | 多轮对话的上下文压缩 |
| 4.2 | 错误处理与容错 | 🔲 待开始 | LLM 调用失败重试、工具执行异常处理 |
| 4.3 | 性能优化 | 🔲 待开始 | 减少不必要的 LLM 调用 |
| 4.4 | 文档和测试 | 🔲 待开始 | API 文档、集成测试 |

---

## 3. 进度日志

### 2026-03-28
- 创建 feat/agent-loop-upgrade 分支
- 创建项目规划文档（PLAN.md）、实现文档（IMPL.md）、Review 文档（REVIEW.md）
- ADR-001/002/003 全部确认：FC 用 Spring AI + DashScope，评估用 Headless Browser + 视觉模型，Skill 用 YAML
- **Phase 1 正式启动**——从 1.1 AgentLoop 核心类设计开始

---

## 4. 开放问题

- [x] ~~Function Calling 模型选型确认~~ → Spring AI + DashScope
- [x] ~~游戏评估方案确认~~ → Headless Browser + 视觉模型
- [x] ~~Skill 存储位置和格式确认~~ → YAML，`resources/skills/`
- [ ] 前端是否需要同步升级（支持多轮迭代展示）
- [ ] 是否需要支持用户中途干预迭代过程（"这个方向不对，换一种"）
- [ ] Playwright for Java 的版本和依赖确认
- [ ] 视觉评估用哪个多模态模型（Qwen-VL / GPT-4o / 其他）
