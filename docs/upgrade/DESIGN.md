# Game Agent 顶层设计

> 本文档定义 Game Agent 的领域认知结构——它是谁、它会什么、它怎么判断自己做得好不好。
> 对标 Agent Harness 的三层记忆框架：Semantic Memory / Procedural Memory / Working Memory。

---

## 1. 领域定义：Game Agent 是谁

### 1.1 角色定位

Game Agent 是一个**儿童教育游戏设计师**。它具备以下能力：

- **需求理解**：理解自然语言描述的游戏需求，识别目标年龄段、教育目标、游戏类型
- **游戏设计**：将需求转化为可玩的 HTML5 游戏，包括玩法设计、视觉呈现、交互逻辑
- **质量把控**：能"看到"自己生成的游戏，评估可玩性，发现并修复问题
- **知识积累**：内置教育游戏设计的最佳实践（Skill），并能在此基础上创新

### 1.2 领域边界

| 在边界内 | 在边界外 |
|---------|---------|
| HTML5 单页游戏（内联 CSS/JS） | 多页面应用、需要后端的游戏 |
| 4-12 岁儿童教育场景 | 成人游戏、竞技类游戏 |
| 2D 浏览器游戏 | 3D、WebGL 复杂渲染 |
| 单人游戏 | 多人联机 |
| 交互式小游戏（5-15 分钟） | 大型开放世界 |

### 1.3 Semantic Memory（语义记忆）—— "我知道什么"

类比 Agent Harness 中的 SEMANTIC_PROMPT（412 行角色定义），Game Agent 的语义记忆应包含：

**角色与职责**：
```
你是一个儿童教育游戏设计专家。你的工作是根据用户的需求描述，
设计并生成完整的 HTML5 教育小游戏。你追求的不是"能跑"，
而是"好玩、有教育意义、没有 bug"。
```

**领域知识**：
- 不同年龄段的认知发展特点（4-6 岁：图形识别、颜色、简单计数；7-9 岁：加减法、简单逻辑；10-12 岁：乘除法、策略思维）
- 教育游戏设计原则（即时反馈、渐进难度、正向激励、容错友好）
- HTML5 游戏开发规范（响应式布局、触屏适配、性能约束、无外部依赖）
- 常见游戏类型的设计模式（匹配、记忆、计算、拼图、闯关、模拟）

**质量标准**（内化的评判标准）：
- 游戏必须有明确的开始和结束
- 必须有计分或进度反馈
- 操作必须简单直觉（点击/拖拽，不需要键盘组合键）
- 视觉元素不能超出可见区域
- 失败时给鼓励而非惩罚

---

## 2. 技能体系：Game Agent 会什么

### 2.1 Procedural Memory（程序性记忆）—— "我会做什么"

类比 Agent Harness 中的 5 个 Workflow，Game Agent 的"工作流"是不同的游戏生成策略：

| 策略 ID | 名称 | 适用场景 | 核心流程 |
|---------|------|---------|---------|
| `skill_based` | 技能模板策略 | 用户需求匹配已有 Skill | load_skill → 基于模板定制 → evaluate → 返回 |
| `creative_gen` | 创意生成策略 | 用户需求是全新玩法 | 设计游戏方案 → generate_game → evaluate → fix → 返回 |
| `iterative_refine` | 迭代优化策略 | 用户对已有游戏提修改意见 | 分析问题 → fix_game → evaluate → 返回 |

**策略选择**（类比 Agent Harness 的 WorkflowRouter）：
- 不需要 LLM routing——游戏生成的策略选择比视频创作简单
- 第一轮：LLM 根据 Skill 列表判断是否有匹配的模板
- 如果有 → skill_based
- 如果没有 → creative_gen
- 后续轮次：如果用户反馈修改意见 → iterative_refine

### 2.2 Tools（工具集）—— Agent 的"手"

| 工具 | 描述 | 输入 | 输出 |
|------|------|------|------|
| `list_skills` | 列出可用的游戏技能模板 | 可选 filter（类型/年龄段） | Skill 摘要列表 |
| `load_skill` | 加载指定 Skill 的完整内容 | skill_name | 模板代码 + prompt 提示 + 评估标准 |
| `generate_game` | 生成完整的 HTML5 游戏 | 游戏设计方案（文本描述） | HTML 代码 |
| `evaluate_game` | 评估游戏可玩性 | HTML 代码 | 评估报告（截图 + 问题列表 + 评分） |
| `fix_game` | 修复游戏中的问题 | HTML 代码 + 问题列表 | 修复后的 HTML 代码 |

**工具调用协议**：LLM Function Calling（Spring AI 原生支持）

### 2.3 Skills（内置技能模板）—— Agent 的"经验库"

从现有 4 个硬编码 Agent 转化而来，加上扩展：

| Skill | 来源 | 教育目标 | 核心玩法 |
|-------|------|---------|---------|
| `math_adventure` | MathGameAgent | 数学计算能力 | 算术题闯关 |
| `memory_master` | MemoryGameAgent | 记忆力训练 | 配对翻牌 |
| `english_explorer` | EnglishLearningAgent | 英语词汇 | 单词拼写/选择 |
| `traffic_safety` | TrafficSafetyAgent | 安全意识 | 交通规则模拟 |
| `shape_colors` | 新增 | 图形颜色认知 | 形状匹配/颜色分类 |
| `logic_puzzle` | 新增 | 逻辑思维 | 简单推理/排序 |

每个 Skill 的 YAML 结构：
```yaml
name: math_adventure
display_name: 数学冒险
description: 10以内加减法互动游戏，适合4-8岁儿童
age_group: "4-8"
difficulty: [easy, medium]
tags: [数学, 加法, 减法, 计算]
game_type: quiz

# 游戏模板（完整可运行的 HTML）
template: |
  <!DOCTYPE html>
  ...

# 生成提示词（LLM 参考此 Skill 时的额外指导）
prompt_hint: |
  参考此模板的结构和交互模式，但根据用户的具体需求调整：
  - 数学运算类型（加/减/乘/除）
  - 数值范围
  - 视觉主题
  - 难度递进方式

# 评估标准（evaluate_game 工具使用）
evaluation_criteria:
  - 数学题目难度是否匹配目标年龄段
  - 答对/答错是否有明确的视觉和文字反馈
  - 是否有计分系统且分数正确累计
  - 题目是否随机生成（非固定题库）
  - 游戏区域内所有元素是否在可见范围内
```

---

## 3. 验证体系：怎么知道做得好不好

### 3.1 Working Memory（工作记忆）—— "我现在看到什么"

类比 Agent Harness 的 WorkingMemoryCursors（accessLevel/draftVersion），Game Agent 的工作记忆追踪：

| Cursor | 含义 | 变化检测 |
|--------|------|---------|
| `game_version` | 当前游戏 HTML 的版本号 | 每次 generate/fix 后递增 |
| `eval_score` | 最近一次评估的总分 | 每次 evaluate 后更新 |
| `issue_count` | 未修复的问题数量 | evaluate 后更新，fix 后减少 |
| `iteration` | 当前迭代轮次 | 每轮 Loop 递增 |

**上下文注入**（类比 Agent Harness 的 ContextUpdateRenderer）：
```xml
<working_memory>
  <game_state>
    <version>3</version>
    <last_eval_score>72/100</last_eval_score>
    <open_issues>
      - 游戏区域底部元素被截断
      - 点击"开始"按钮无响应
    </open_issues>
    <iteration>2 of 5</iteration>
  </game_state>
</working_memory>
```

### 3.2 评估维度（evaluate_game 的评判框架）

评估分为 **5 个维度**，每个维度 20 分，满分 100：

| 维度 | 权重 | 检测方式 | 评估内容 |
|------|------|---------|---------|
| **可运行性** | 20 分 | Headless Browser 渲染 | 页面能正常加载，无 JS 报错，不白屏 |
| **视觉完整性** | 20 分 | 截图 + 视觉模型 | 元素不越界、布局合理、文字可读、颜色对比度充足 |
| **交互响应性** | 20 分 | Headless Browser 模拟点击 | 按钮可点击、有反馈、游戏状态能变化 |
| **教育匹配度** | 20 分 | 视觉模型 + 规则检查 | 内容是否匹配目标年龄和教育目标 |
| **游戏完整性** | 20 分 | 视觉模型 + 代码分析 | 有开始/结束、有计分、有胜负条件、难度合理 |

**评估流程**：
```
HTML 代码
  → Playwright 渲染（headless Chrome）
  → 截取初始画面截图
  → 模拟点击"开始"按钮
  → 截取游戏中画面截图
  → 收集 console.error 日志
  → 打包：[截图 × 2-3 张, console 日志, HTML 源码摘要]
  → 提交给多模态 LLM 评估
  → 返回：各维度评分 + 问题列表 + 改进建议
```

### 3.3 质量门禁（Quality Gate）

Agent Loop 的迭代终止条件：

| 条件 | 阈值 | 说明 |
|------|------|------|
| 评估总分达标 | ≥ 80/100 | 质量达标，可交付 |
| 最大迭代次数 | 5 轮 | 防止无限循环 |
| 关键问题清零 | 可运行性 ≥ 16/20 | 至少能跑起来 |
| 用户满意 | 用户确认 | 用户可随时说"可以了" |

**迭代策略**：
- 第 1 轮：生成 → 评估
- 第 2-4 轮：如果评分 < 80，根据问题列表修复 → 再评估
- 第 5 轮：如果仍未达标，返回当前最佳版本 + 问题说明
- 任何时候用户说"可以了"或"就这样"，立即停止

---

## 4. 完整 Agent Loop 流程图

```
用户："做一个给6岁孩子的加法游戏"
  │
  ▼
┌─────────────────────────────────────────┐
│ Agent Loop（最多 5 轮迭代）               │
│                                         │
│  Turn 1:                                │
│  ├── LLM Think：分析需求，查看可用 Skill  │
│  ├── tool_call: list_skills(type=math)  │
│  ├── tool_call: load_skill(math_adventure)│
│  ├── LLM Think：基于 Skill 定制设计方案    │
│  ├── tool_call: generate_game(方案)      │
│  ├── tool_call: evaluate_game(HTML)      │
│  └── 评估结果：85/100 ✅ 达标            │
│      → 返回游戏                          │
│                                         │
│  --- 或者如果评估不达标 ---               │
│                                         │
│  Turn 1: 评估结果：62/100 ❌             │
│  ├── 问题：按钮无响应、元素越界           │
│  │                                      │
│  Turn 2:                                │
│  ├── LLM Think：看到问题列表，制定修复方案 │
│  ├── tool_call: fix_game(HTML, 问题列表)  │
│  ├── tool_call: evaluate_game(修复后HTML) │
│  └── 评估结果：88/100 ✅ 达标            │
│      → 返回游戏                          │
└─────────────────────────────────────────┘
```

---

## 5. 与 Agent Harness 的对标关系

| Agent Harness 概念 | Game Agent 对应 | 说明 |
|-------------------|----------------|------|
| SEMANTIC_PROMPT | Game Agent 角色定义 + 领域知识 + 质量标准 | "我是游戏设计专家，我知道什么是好游戏" |
| Workflow（seedance2/kling/sora） | 生成策略（skill_based/creative_gen/iterative_refine） | "这次该用哪种方式做游戏" |
| WorkingMemoryCursors | game_version/eval_score/issue_count/iteration | "当前游戏状态如何" |
| 36 个 Tool | 5 个 Tool（list/load/generate/evaluate/fix） | "我能做什么操作" |
| ContextUpdateDetector | 评估分数变化检测 | "游戏质量变好了还是变差了" |
| compose_sys_prompt | semantic + 当前策略 + working memory 拼接 | 系统提示词构建 |
| maxIterations = 200 | maxIterations = 5（游戏生成不需要太多轮） | 迭代上限 |

---

## 6. 待讨论

- [ ] evaluate_game 中 Playwright 的模拟交互深度：只点"开始"？还是尝试玩几步？
- [ ] 视觉评估用哪个多模态模型（Qwen-VL 免费但能力有限 vs GPT-4o 强但收费）
- [ ] 是否需要支持"用户看到截图后给反馈"的交互模式
- [ ] fix_game 是全量重写还是增量修补？（全量更简单但 token 消耗大）
