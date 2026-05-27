# 蒸馏工作流的关键边界：人工 vs 自动

> 日期：2026-05-27
> 关联：plan/step6-distillation-workflow.md / docs/knowledge/principles/skill-evolution-sop.md

## 决策

蒸馏工作流的 8 个节点中，**5 个必须人工**、3 个自动化。具体分界写在 step6-distillation-workflow.md §2-3。

## 为什么人工是底线

SKILL.md 是 LLM 的"知识 SSOT"，权威性由人工把关。把任意环节自动化都会让 SKILL 失去权威：

| 自动化诱惑 | 真实风险 |
|---|---|
| 自动从 evidence 提炼候选 | 一次性 bug 被当成"普适规则"，未来生成都被误导 |
| 自动改 SKILL.md | 知识改动绕过 git review，回溯困难 |
| 自动判通过率 | 通过率 2/3 vs 3/3 边界很重要，机器判定容易踩 |

哪怕**所有数据**都说应该改 SKILL.md，最后那一刀必须工程师自己拍板。这跟"代码自动化测试"不一样——代码是行为真相，错了能立刻发现；SKILL.md 是知识真相，错了要几个月后才能从 LLM 生成质量退化中察觉，反馈环路太长。

## 自动化的部分是哪三个

1. **证据写入**（runtime）—— 这是 Step 4 闭环
2. **样本筛选**（CLI / API）—— 这是 Step 5 闭环
3. **状态机推进**（promote/accept/reject 端点）—— 这是 Step 5 的 REST API

它们都有共同特征：**幂等、可回滚、不直接改 SSOT 文件**。

## 哪些是"门"

每一道人工门都对应一份产出：

| 门 | 产出 | 权威性来源 |
|---|---|---|
| 提炼候选 | "普适 vs 个案"判定 | 工程师对游戏本身的认知 |
| 写 debug-log.md | 改动溯源 | task memory 是后人的审计入口 |
| 改 SKILL.md | 知识规则 | git commit + code review |
| 多采样判读 | 改动有效性 | 通过率 ≥ 2/3 不是机器算就完事，要看具体失败模式 |
| accept-reject | 最终落地 | 责任在人 |

## 反模式

写在 step6-distillation-workflow.md §6。本 memory 强调最致命一条：

**"用 SQL 直接改 SKILL.md（绕过 git review）"** —— 这是把"运行时数据"和"知识 SSOT"混淆的最严重表现。SKILL.md 必须留在 git 中，跟代码一起 commit/review/rollback。

## 副决策

- accept/reject 后的 `note` 字段必须写完整理由（不是"通过"/"不通过"两字了事）
- candidate 状态机允许 reject → accept 反悔（人工可改主意），但每次改 status 都刷新 updated_at 留痕
- raw → 不直接到 accepted（plan §4 状态机硬性约束）

## 与 SOP 的对齐

`docs/knowledge/principles/skill-evolution-sop.md` 现在加了"证据驱动版本"段，引用本工作流。后续任何改 SOP 的工作必须同步更新这条引用，否则就是 SSOT 漂移。
