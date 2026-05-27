# 260524-skill-distillation-evidence — Skill 蒸馏证据层

## 目标

把当前运行时从“只保存最终游戏结果”升级为“保存可复盘、可筛选、可蒸馏的证据数据”，为第二层 Skill 蒸馏提供稳定输入。

本任务不是让 Java 运行时自动改 `SKILL.md`。真正目标是先把证据沉淀下来：哪个 Skill、哪个模型、哪次输入、每轮生成了什么、评测发现了什么、最终为什么成功或失败。

## 背景

当前后端已经有 `sessions / messages / game_runs` 三表，能保存会话、assistant 内容、最终 HTML、最终评分和迭代次数。但这套数据结构服务的是“历史查看 / 收藏 / 回放”，不是“Skill 演进”：

- `game_runs` 只保存成功生成的最终 HTML，不保存失败样本
- `eval_score` 是最终分，不保存结构化 `ProbeReport` / issue 明细
- `ToolContext.activeSkill` 只存在运行时 ThreadLocal，不落库
- `WorkingMemory.openIssues` 只在单次 AgentLoop 内存中流转，不进入持久化
- 缺少“原始证据 → 候选规则 → 人工确认 → 合并 Skill”的生命周期状态

这意味着现在的数据能回答“生成过什么游戏”，但很难回答“这个 Skill 为什么应该演进”。

## 范围（本任务做什么、不做什么）

✅ 本任务做：

- 梳理并冻结 Skill 蒸馏证据的数据契约
- 优化 Skill 选择主路径：运行时注入 Skill Index，`listSkills` 降级为 fallback，`loadSkill` 继续按需加载完整 `SKILL.md`
- 记录每次运行的 `skill_name / model_key / success / error_type / eval_score / issues`
- 设计持久化结构，支持保存最终结果、失败结果和关键评测摘要
- 为每轮迭代保留轻量 trace：输入、工具调用、评测观察、状态变化
- 增加可查询的蒸馏候选状态：`raw / candidate / accepted / rejected`
- 与已有 `260521-agent-harness` 的 `EvaluationObservation / RunTrace` 思路对齐
- 与已有 `260521-game-storage-db` 的 SQLite 持久化边界兼容

❌ 本任务不做：

- 不自动修改任何 `resources/skills/*/SKILL.md`
- 不把 Skill 系统迁到数据库
- 不引入 SubAgent / 多 Agent 调度
- 不做 RAG / few-shot 召回策略
- 不改游戏评分算法
- 不改变前端主交互形态，除非后续仅加只读调试入口
- 不破坏现有 `/api/sessions/*`、`/api/game/storage/*` 返回结构

## 步骤

1. [x] **Step 1：Skill 选择链路优化** — `WorkingMemory.skillIndex` 字段（默认空 List）；`AgentLoop.run` 在 `tryPreloadSkill` 后注入 `skillLoader.listSkills()`；`ContextRenderer` 末尾追加 `<skill_index>` 守卫块（默认空时不输出，保字节级相等基线）；description 截断 120 字符 + XML escape；`SkillListTool` `@Tool description` 改为「通常已能从 `<skill_index>` 看到摘要」语义；`SkillListTool/SkillLoadTool` 方法签名不动；ContextRendererTest 17 用例 + SkillIndexInjectionTest 3 用例 + 全量 74 用例全过。@ 2026-05-27 / 偏离：未新建 `SkillMeta` record，直接复用 `SkillDefinition`，与 plan 「不可新增的抽象」精神一致
2. [x] **Step 2：证据清点与字段契约** — `plan/step2-evidence-fields.md §字段契约` 6 节齐全：必落库 16 字段（每条标明源代码出处）/ 不落库 6 项（含理由）/ 失败样本 5 场景写入策略 / 候选生命周期 4 状态机 / 与 game_runs 分工去重表 / 冻结声明。memory `2026-05-27-evidence-contract-frozen.md` 解释为什么用 JSON 列不展开 N 列。@ 2026-05-27
3. [x] **Step 3：持久化设计** — schema.sql 末尾追加 game_run_evaluations + skill_distillation_candidates 两表（FK CASCADE + 5 索引）；4 个 Java 文件（GameRunEvaluationEntity / GameRunEvaluationRepository / SkillDistillationCandidateEntity / SkillDistillationCandidateRepository），列表 SQL 不读 *_json 大字段，写方法 synchronized；2 个 smoke test 真启 Spring + 真 SQLite，11 用例覆盖完整字段映射 / 列表字段分离 / 状态机推进 / 双层 FK CASCADE。全量 mvn test 85/85 通过 0 退化。@ 2026-05-27
4. [x] **Step 4：运行时写入** — `AgentLoopResult` record 扩到 10 字段，旧 success/failure 工厂保留；新工厂 successWithEvidence / failureWithEvidence 注入 EvaluationObservation/RunTrace/activeSkillName/errorType。`EvidenceMapper` 4 个静态方法（toScoresJson / toProbeSummaryJson / toClassifiedIssuesJson / toIterTracesJson）null 安全 + 序列化失败兜底；iter_traces_json 剔除 issuesSnapshot 大字段。`SessionService.recordEvidence` 投影 AgentLoopResult → game_run_evaluations。`AgentLoop.run` 签名不动，所有路径用新工厂；`resolveActiveSkillName` 优先 ToolContext 回退 preloadedSkill；catch 用 ErrorClassifier 取 errorType。`GameChatController.generateGameV2` 在 recordRun 后用 try/catch 调 recordEvidence；写库失败均不影响响应。`EvidenceWriteEndToEndTest` 4 用例（成功路径 + 失败路径 + 降级 + null 安全）；全量 mvn test 89/89 通过 0 退化。@ 2026-05-27
5. [x] **Step 5：候选样本查询** — `GameRunEvaluationRepository` 加 listBySkillAndScore / listByIssueCategory（JSON LIKE）/ countByConditions；`SkillDistillationCandidateRepository` 加 listByStatus / findByEvaluationId / upsertFromEvaluation（按 evaluationId 幂等）；`EvidenceQueryService` 7 方法（findCandidates / findDetail / stats / promoteToCandidate / accept / reject + null 安全 JSON 解析）；`EvidenceController` 6 端点 `/api/evidence/{candidates,stats,/{id},promote,accept,reject}`；`scripts/distillation-candidates.sh` CLI（curl + python3 无 jq 依赖，含 backend 不可达兜底）；`EvidenceQueryServiceTest` 9 用例覆盖状态机 + 幂等 + 不存在 ID 抛异常。全量 mvn test 98/98 通过 0 退化；端到端冒烟：promote → accepted 状态机闭环 + stats 反映 + FK CASCADE 删除全验。@ 2026-05-27
6. [x] **Step 6：蒸馏工作流文档** — `plan/step6-distillation-workflow.md` §蒸馏工作流 8 节点流程图 / 5 个必须人工 vs 3 个自动化分界 / 命令速查 9 条 / 与 skill-evolution-sop.md 关系映射 / 反模式 6 条 / 端到端冒烟脚本；`docs/knowledge/principles/skill-evolution-sop.md` 加「证据驱动版本」段引用本工作流；memory `2026-05-27-workflow-decision.md` 解释为什么 5 个环节人工是底线（SKILL.md 是知识 SSOT，权威性由人工把关）。@ 2026-05-27

> 任务 260524-skill-distillation-evidence 整体完结：6 step 全部 ✅；新增 4 个 Java 文件（2 entity + 2 repo）+ 1 service + 1 controller + 1 mapper + 4 test 文件 + 2 张 SQL 表 + 1 CLI 脚本；98 个 JUnit 用例覆盖；已与 harness 任务（260521-agent-harness）的 EvaluationObservation/RunTrace 集成。

> 6 个 step plan 已拆解完成 @ 2026-05-27（每 step 含「实现契约」+「验收契约」双段，可直接交 coder/evaluator 执行）。harness 任务（260521-agent-harness）已完结，提供 `EvaluationObservation` / `RunTrace` 作为本任务证据层的内存输入源。

## 链路优化子项：Skill Index 主路径

当前链路把 `listSkills` 暴露为 tool，让 LLM 自己决定是否先列出 Skill。这个做法能跑，但不稳定：模型可能忘记调用，也可能直接开始生成，导致匹配 Skill 没被加载。

目标链路：

```text
AgentLoop 启动
  -> SkillLoader.listSkills()
  -> 渲染 Skill Index: name + description + metadata.triggers(optional)
  -> 注入 system prompt / developer prompt
  -> LLM 根据 Skill Index 选择
  -> 调用 loadSkill(skillName) 加载完整 SKILL.md
  -> listSkills 只作为 fallback / debug / filter
```

边界：

- prompt 里只放 Skill meta，不放完整 `SKILL.md`
- `loadSkill` 继续作为 Function Calling tool，负责按需加载正文
- `listSkills` 不删除，避免破坏现有工具契约；只从主路径降级为兜底
- Java 侧 keyword preload 和 LLM 侧 Skill Index 应统一写入后续 `activeSkill` 证据
- 如果未来 Skill 数量过多，Skill Index 不全量注入，改成 Java 侧 top-K 检索后注入候选

## 目标状态

任务完成后，数据流应当变成：

```text
UserInput
  -> loadSkill / suggested_skill 确定 active skill
  -> AgentLoop 多轮生成 / 评测 / 修复
  -> EvaluationObservation + RunTrace 结构化记录
  -> SessionService 写入最终消息和游戏结果
  -> Evidence Repository 写入运行证据和候选状态
  -> 后续蒸馏任务按 Skill 聚合失败/成功样本
  -> 人工确认后再修改 SKILL.md
```

而不是：

```text
AgentLoop 跑完
  -> 只保存最终 assistant message / html / eval_score
  -> 失败样本、issue 明细、active skill、每轮变化全部丢失
  -> 后续只能凭印象改 SKILL.md
```

## 不变的边界（已冻结）

- `AgentLoop.run(String userInput, String modelKey)` 方法签名默认不变
- `AgentLoopResult.success/failure` 语义默认不变
- 现有 `sessions / messages / game_runs` 字段语义不破坏，新增字段必须兼容旧数据
- `GameEvaluator.evaluate(String htmlCode)` 仍返回 `ProbeReport`
- `SkillLoader` 继续从 `resources/skills/*/SKILL.md` 加载 Skill
- Spring AI 原生 Function Calling 机制不替换
- `listSkills` 工具默认保留，避免破坏现有 Function Calling 工具集合
- `SKILL.md` 修改必须经过独立蒸馏任务或人工确认，不由运行时自动写入

## 风险登记

- **R1：证据层变成垃圾桶** — 什么都存，后续没人用。缓解：先冻结蒸馏最小字段，只存能支持筛选和复盘的事实。
- **R2：JSON 大字段失控** — 每轮 HTML 和完整报告全部入库会膨胀。缓解：默认存摘要，完整 HTML 只存最终版本；中间版本按预算或调试开关保留。
- **R3：破坏现有历史接口** — 给 `game_runs` 加语义太多导致老接口行为漂移。缓解：新增表优先，旧表只做兼容性增强。
- **R4：自动蒸馏污染 Skill** — 低质量样本直接写进 `SKILL.md` 会让 Skill 过拟合。缓解：运行时只产证据，蒸馏必须有人类确认或独立 review。
- **R5：与 harness 任务重复造轮子** — `EvaluationObservation / RunTrace` 可能在 harness 任务里已有设计。缓解：本任务消费或持久化那些结构，不重新发明一套平台 runtime。

## 启动前需要确认

- 证据层是先做最小 schema 扩展，还是等 `260521-agent-harness` 的 `EvaluationObservation / RunTrace` 完成后再接持久化
- 失败样本是否也要在前端可见，还是只提供后端查询 / 脚本入口
- 中间 HTML 是否全部保存，还是只保存摘要 + 最终 HTML
- 蒸馏候选状态由数据库维护，还是先用离线脚本从 evidence 表生成报告

## 关联任务

- `docs/task/260521-game-storage-db/`：当前会话与游戏结果持久化底座
- `docs/task/260521-agent-harness/`：结构化状态、评估观察和运行轨迹
- `docs/task/260521-playable-snake-evolution/`：人工闭环完成一次 Skill 蒸馏
- `docs/knowledge/principles/skill-evolution-sop.md`：Skill 演进 SOP

## 决策记录

| 决策 | 日期 | 说明 |
|------|------|------|
| 先建证据层，不自动写 Skill | 2026-05-24 | 运行时数据质量参差不齐，直接改 `SKILL.md` 会污染长期知识；先沉淀可审计证据 |
| 任务先只记录入口，不预拆 plan | 2026-05-24 | 需要等待 harness 结构化 observation/trace 的落地程度，再决定 schema 和写入点 |
| 兼容旧存储接口 | 2026-05-24 | `sessions / messages / game_runs` 已被前端和历史接口消费，新增蒸馏能力不能破坏用户空间 |
| Skill Index 进入 prompt，listSkills 降级 | 2026-05-24 | Skill meta 是确定性运行时事实，不应依赖 LLM 每次主动调用工具发现；完整 Skill 仍按需 `loadSkill` |

## 涉及人/责任

- task-designer：后续启动时补 `plan/*.md` 的实现契约和验收契约
- coder：按 plan 施工，默认不改 `SKILL.md`
- evaluator：按 SSOT 查数据库和 API，不信运行日志
- code-reviewer：重点审查数据结构是否膨胀、是否破坏旧接口
- dreamer：任务结束后把可复用的蒸馏证据原则上浮到 `docs/knowledge/`
