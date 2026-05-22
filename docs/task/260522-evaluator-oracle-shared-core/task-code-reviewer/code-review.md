# 任务专项审查规则：260522-evaluator-oracle-shared-core

> 任务期间额外冻结边界。任务收尾后归档。

## 适用范围

- 改动涉及 `shared/playability/*`
- 改动涉及 `game-agent-backend/src/main/java/com/sumo/agent/agent/evaluation/GameEvaluator.java` 或 `ProbeReport.java`
- 改动涉及 `game-agent-backend/src/main/resources/probe/*`
- 改动涉及 `scripts/lib/oracle-driver.py` 或 `scripts/lib/oracle-verdict.sh`
- 改动涉及 `scripts/cross-verify.sh`

不在范围按 `docs/review/code-check.md` 工程标准审查。

## 已冻结边界（命中即"高风险"）

1. **shared/playability/* 严禁引用项目代码** —— 不许 import Java、不许 require Python、不许调 Spring AI / SkillLoader 等。命中即拦
2. **shared 文件必须纯 JS + 零依赖** —— 不引入 npm 包、第三方 lib（包括 hash 库；要 hash 自己写 simpleHash）
3. **window.__PLAYABILITY__ / __PLAYABILITY_DRIVER__ API 签名一旦定下不许换** —— Step 1 之后任何 collect / getErrors / computeWhitelist / hasNewKeyword / findStartButton / clickByJS 的参数和返回类型变化即拦
4. **ProbeReport 字段名不许动** —— GameEvaluator.computeScores 依赖它；改了五维评分公式会无声崩
5. **GameEvaluator 五维评分公式不许动** —— `runnability + layout + interactivity + completeness + 15`。要改去 follow-up 任务（evaluator-skill-signals）
6. **oracle 退出码语义不许动** —— 0/1/2 是契约
7. **oracle result.json 字段名不许动** —— verdict.sh 依赖
8. **不引入新依赖** —— pom.xml / package.json diff 必须 = 0
9. **老 game-probe.js 不许删** —— 保留作 v1 兼容；删它要单独任务
10. **AgentLoop / Tool / Skill / SkillLoader 文件不许触碰** —— 本任务只动评估器层

## 触发"中风险"的反模式

- shared/playability/* 单文件超过 250 行（应拆 utils）
- Java 端 `Files.readString` 用相对路径（cwd 敏感）
- python 端 `open(...)` 用相对路径
- shared/playability/* 缺 `if (window.__X__) return;` 防重复注入
- ProbeReport 字段虽然结构没变但语义变了（如 events 现在永远空）—— 必须在 memory 显式记录
- evalScore 系统性下降但没改 QUALITY_GATE_SCORE / 没在 memory 记录退路

## 触发"低风险"的提醒

- shared 注释不全（API 契约文档不清）
- README 缺接入示例
- bash 脚本无 `set -e`（虽然部分需要继续跑用 `set -uo pipefail`）

## 审查输出额外段

```text
【任务专项检查】
- 已冻结边界违反：N 处
  - {文件}:{行}: {问题} — 命中规则 {N}

【与 plan 契约对齐】
- coder 改动是否在 plan §可改文件 范围内？✅/❌
- shared/playability/* 是否真的零项目依赖？✅/❌
- API 签名是否与 plan 一致？✅/❌
- 评分系统性下降的退路是否在 memory 中？✅/❌
```
