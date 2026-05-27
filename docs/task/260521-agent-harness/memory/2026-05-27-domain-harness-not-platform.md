# 为什么 AI-GAME 选领域型 harness，不迁 yuntoo-smartcode 通用平台 harness

> 日期：2026-05-27
> 关联：plan/step1-state-context.md、step2-evaluation-observation.md、step3-control-trace.md
> 触发：harness 改造时多次面临"要不要把 yuntoo-smartcode 的 SubAgent / YAML ToolCommand / 动态场景注册全搬过来"的诱惑

## 背景

`yuntoo-smartcode` 是参考实现，它做的是平台型 harness：多场景、多租户、SubAgent 调度、YAML/XML ToolCommand 协议、数据库驱动的场景注册、SSE trace UI、计费 / 工具白名单。

`AI-GAME` 的产品定位是**儿童教育游戏生成器**——单领域、单租户、固定工具集（4-5 个 @Tool）、固定 5 轮 80 分门禁。

## 决策

**不迁**任何下面的内容：

| yuntoo 有的 | AI-GAME 不要的原因 |
|---|---|
| YAML/XML ToolCommand 协议 | Spring AI 原生 Function Calling 已经够用；自定义协议带来翻译层、错误模式、调试成本 |
| SubAgent 调度 | 教育游戏不需要 task 拆分；外层 5 轮 + Skill loadSkill 已能覆盖 |
| 动态场景注册（DB 驱动） | Skill 系统已经是文件驱动的"场景"（resources/skills/*/SKILL.md），AgentSkills.io 规范，运行时只读 |
| 多租户 / 工具白名单 / 计费 | 产品是单人项目，零运维优先 |
| SSE trace UI | runtime trace 仅内存驻留即可，前端不需要看 |
| Planner / Executor / Runtime 抽象层 | 主循环只需要 5 行：buildPrompt → call → record → checkGate → loop。再加抽象就是为了像 harness 而 harness |

## 借鉴的部分

只吸收 **思想**，不搬代码：

| yuntoo 的做法 | AI-GAME 的等价物 |
|---|---|
| 三层记忆（Semantic / Procedural / Working） | AgentPrompts.SYSTEM_PROMPT / SkillLoader+SKILL.md / WorkingMemory |
| WorkingMemoryCursors（事实先 Java 结构化） | WorkingMemory 字段 + getter/setter，**禁止**直接拼 prompt（Step 1 拆出 ContextRenderer 之后这条变成强约束） |
| Observation（环境反馈结构化） | EvaluationObservation + ObservationIssue（Step 2） |
| ControlSignals / RunTrace（控制权留 Java） | 同名实现（Step 3），但只做最小集合：5 个布尔信号 + recent(3) trace |

## 红线

- 不引入 SubAgent / Planner / Executor 抽象
- 不复制 ToolCommand 协议
- 不持久化 trace 到 DB（plan §约束）
- 不让 prompt 成为唯一事实源
- 不让 LLM 单方面绕过 QUALITY_GATE_SCORE
- 不为"未来扩展性"提前抽象

## 检验标准

如果改造后主循环用一句话能讲清，就对了：「每轮 build prompt → call LLM → record trace → check gate」。如果讲不清，说明抽象错了。

## 后续

- 如果出现真实需求驱动（如多个不同领域要共用一个 harness），再考虑**抽出来**而不是**预先抽**
- 任务 260524-skill-distillation-evidence 是 harness 的下游消费者；它写库、不改 runtime，验证了"harness 内存层 + 持久化层分离"的合理性
