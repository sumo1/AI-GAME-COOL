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

**状态**：待讨论

**背景**：当前项目使用阿里云百炼（DashScope），支持通义千问/Kimi/DeepSeek/Qwen3。需要确认哪些模型支持 Function Calling。

**方案选项**：
- A) 继续使用 Spring AI + DashScope，利用其 Function Calling 支持
- B) 引入 OpenAI 兼容的 API（如 Qwen3 的 OpenAI 兼容模式）
- C) 混合方案：主循环用支持 FC 的模型，评估用视觉模型

**待确认**：
- [ ] 通义千问系列对 Function Calling 的支持程度
- [ ] Spring AI 的 ChatClient 是否原生支持 tool_calls 解析
- [ ] 是否需要引入多模态模型来"看"游戏截图

### ADR-002：游戏评估方案

**状态**：待讨论

**背景**：Agent 需要能评估生成的 HTML 游戏的可玩性。

**方案选项**：
- A) 静态分析：解析 HTML/JS，检测常见问题（碰撞逻辑、边界检查、事件绑定）
- B) 运行时评估：用 headless browser 渲染游戏，截图后用视觉模型评估
- C) 混合方案：先静态分析快速检测，再运行时验证关键逻辑
- D) LLM 自评：把生成的 HTML 代码回传给 LLM，让它自己 review

**考虑因素**：
- 静态分析速度快但覆盖面有限
- headless browser 更真实但引入新依赖（Playwright/Puppeteer）
- Java 后端集成 headless browser 的方案

### ADR-003：Skill 系统设计

**状态**：待讨论

**背景**：将现有硬编码的游戏 Agent 转变为 Skill。

**设计思路**：
- Skill = 游戏模板 + 生成提示词 + 评估标准
- 存储形式：JSON/YAML 描述文件 + 模板代码
- 加载方式：通过 load_skill 工具，将 Skill 信息注入 LLM 上下文

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
| 3.1 | 设计评估维度 | 🔲 待开始 | 越界检测、碰撞逻辑、交互响应、视觉完整性 |
| 3.2 | 实现 evaluate_game 工具（静态分析） | 🔲 待开始 | HTML/JS 代码分析 |
| 3.3 | 实现 fix_game 工具 | 🔲 待开始 | 基于评估结果的定向修复 |
| 3.4 | （可选）headless browser 集成 | 🔲 待开始 | 运行时评估 |
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
- 状态：设计讨论阶段，等待苏摩确认 ADR-001/002/003

---

## 4. 开放问题

- [ ] Function Calling 模型选型确认
- [ ] 游戏评估方案确认（静态 vs 运行时 vs 混合）
- [ ] Skill 存储位置和格式确认
- [ ] 前端是否需要同步升级（支持多轮迭代展示）
- [ ] 是否需要支持用户中途干预迭代过程（"这个方向不对，换一种"）
