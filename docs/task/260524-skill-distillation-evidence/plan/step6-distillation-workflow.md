# Step 6：蒸馏工作流文档

## 背景

证据层完成后，需要明确「证据如何变成候选规则」并最终修改 `SKILL.md`。本步骤**不写代码**，写一份操作手册：从 evidence 查询到 SKILL.md 改动的端到端工作流，明确哪些步骤自动化、哪些必须人工确认。

输出与 `docs/knowledge/principles/skill-evolution-sop.md`（260521-playable-snake-evolution 蒸馏出的 SOP）对齐——SOP 是抽象方法论，本步骤是落地操作手册（含具体 API/脚本调用）。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `docs/task/260524-skill-distillation-evidence/plan/step6-distillation-workflow.md`（本文件，写产出）
  - `docs/knowledge/principles/skill-evolution-sop.md`（如需补充"证据驱动"段落）
  - `docs/task/260524-skill-distillation-evidence/memory/2026-05-XX-workflow-decision.md`（新建）

- **不可改文件**：
  - 任何 Java / TypeScript 代码
  - schema.sql
  - 任何 SKILL.md（蒸馏工作流的产出可能改 SKILL.md，但本步骤本身不改）

### 产出清单

在本文件末尾追加 `## 蒸馏工作流` 一节，含以下子段：

#### 1. 完整流程图

```
[运行时] AgentLoop → game_run_evaluations 写入
                      ↓
[每周 / 阶段收口] 查询失败 + 低分样本（CLI 或 API）
                      ↓
[人工] 阅读 evidence 详情（scores / classified_issues / iter_traces）
                      ↓
[人工] 提炼候选规则（"普适改动" vs "个案改动"）
                      ↓
[人工] POST /promote 把 evaluation 标记为 candidate
                      ↓
[人工] 在 task memory 写 debug-log.md（why + 普适性判定）
                      ↓
[人工] 修改 SKILL.md：候选规则进"评估重点"或"常见问题"段
                      ↓
[人工] 多采样验证（参考 skill-evolution-sop.md Step E）
                      ↓
[人工] 通过 → POST /accept；不通过 → POST /reject
                      ↓
[运行时] 后续生成自动用新 SKILL.md
```

#### 2. 必须人工的环节

明确哪些步骤**严禁**自动化（防止 SKILL 腐化）：

- 候选规则的提炼（"普适" vs "个案" 判断）
- 是否合并进 SKILL.md 的决定
- 多采样验证通过率的判读
- accept/reject 的最终裁决

#### 3. 自动化的环节

- 证据写入（Step 4）
- 候选样本筛选（Step 5 API/CLI）
- 状态机推进（Step 5 promote/accept/reject 端点）
- 多采样统计（沿用 `scripts/snake-skill-multisample.sh` 风格）

#### 4. 与 skill-evolution-sop.md 的关系

本工作流是 SOP 的**操作落地**：SOP 写"基线采样 / oracle 验证 / 离线调试 / 蒸馏 / 多采样"五步原则，本工作流补充每步**具体怎么做**（哪个 API / 哪个脚本 / 哪条 SQL）。

如有 SOP 需要更新，去 `docs/knowledge/principles/skill-evolution-sop.md` 加一段「证据驱动版本：参见 docs/task/260524-skill-distillation-evidence/plan/step6-distillation-workflow.md」。

#### 5. 命令速查（在本节末尾给一张）

| 场景 | 命令 |
|---|---|
| 看本周失败样本 | `bash scripts/distillation-candidates.sh` |
| 看某 Skill 低分样本 | `curl 'http://localhost:8088/api/evidence/candidates?skill=xxx&maxScore=60'` |
| 看某 evaluation 详情 | `curl http://localhost:8088/api/evidence/{evaluationId}` |
| 提升为候选 | `curl -X POST .../api/evidence/{evaluationId}/promote -d '{"note":"..."}'` |
| 接受候选 | `curl -X POST .../api/evidence/candidates/{id}/accept -d '{"note":"..."}'` |
| 拒绝候选 | `curl -X POST .../api/evidence/candidates/{id}/reject -d '{"note":"..."}'` |
| 多采样验证 | `bash scripts/<skill>-multisample.sh`（按需新建）|
| 看候选总览 | `curl http://localhost:8088/api/evidence/stats` |

#### 6. 反模式

- ❌ 跳过人工审核直接改 SKILL.md
- ❌ 把单次失败样本当"普适规则"塞进 SKILL.md
- ❌ 不记 `debug-log.md` 就修 SKILL.md（后人看不懂改动来源）
- ❌ 多采样不到 2/3 就接受候选

#### 7. 任务收口与 memory

蒸馏成功后必做：

1. 在对应 task `memory/` 写 `debug-log.md`（参考 `260521-playable-snake-evolution/memory/2026-05-22-debug-log.md` 风格）
2. 把 `accept` 时的 candidate `note` 字段填详细——这是后续审计入口
3. 阶段收口由 `dreamer` 整理 task memory 上浮跨任务原则到 `docs/knowledge/`

### 约束（已冻结的边界）

- 文档必须能被工程师**直接照抄执行**，不写"未来会有"
- 不允许把"自动改 SKILL.md"列为目标
- API 端点 / CLI 脚本必须与 Step 5 实际产出一致

### 复用的现有模式

- `docs/knowledge/principles/skill-evolution-sop.md` 的 SOP 结构
- `260521-playable-snake-evolution/memory/2026-05-22-debug-log.md` 的 debug 日志风格

### 依赖的前置子任务

Step 1-5 已完成。

## 【验收契约（Evaluator 输入）】

### 文档结构验证

- [ ] 本文件末尾有 `## 蒸馏工作流` 节
- [ ] 流程图含 8 个节点，箭头清晰
- [ ] 必须人工 / 自动化 两段明确
- [ ] 命令速查表与 Step 5 实际产出端点 URL 一致
- [ ] 反模式 ≥ 4 条
- [ ] memory 至少 1 条新决策记录

### 命令验收

无（纯文档）。

### 端到端 SSOT 验证

照本工作流走一遍：

1. 用 mock-fixture 跑 3 次生成
2. 按工作流 Step 1-3 查询样本 / 阅读详情
3. 按工作流 Step 5 promote 一条到 candidate
4. 按工作流 Step 7-8 假装 SKILL.md 改动 + accept/reject
5. 全程**不出现需要"看代码补全"才能执行**的环节

### doc-refresher 验收

- [ ] 本文件中所有路径引用真实存在
- [ ] 命令表中的 endpoint 真实在 EvidenceController 实现
- [ ] 与 `docs/knowledge/principles/skill-evolution-sop.md` 无冲突

## 蒸馏工作流

> 待 coder 在 Step 6 施工时填入。
