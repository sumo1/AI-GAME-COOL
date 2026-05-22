# 260521-playable-snake-evolution — 可玩贪吃蛇 + SKILL 演进闭环

## 目标

**完整闭环一次交付**：
- 一个能机器化判定"游戏是否真能玩"的 oracle 工具（playability-oracle）
- 一个真正能玩的贪吃蛇 fixture（snake-fixed.html，从 LLM v0 调试到能玩）
- 一份蒸馏后的 snake-adventure SKILL.md（让 LLM 一次生成就能玩）
- 多采样验证：用新 SKILL.md 跑 9 次 LLM 生成（3 轮 × 3 采样），通过率 ≥ 2/3
- 演进 SOP 抽到 docs/knowledge/principles/，未来其它 SKILL 可复用流程

## 范围（本任务做什么、不做什么）

✅ 做：
- oracle 工具（scripts/playability-oracle.sh + lib/）
- snake-v0 fixture（LLM 真生成）
- oracle 自验（鉴别力测试）
- v0 → fixed 离线调试（LLM 看诊断包推理修改）
- diff 蒸馏成 SKILL.md 改动
- 多采样验证 SKILL 演进有效（9 次 LLM）
- 演进 SOP 文档化

❌ 不做：
- 点击类 / 拖拽类游戏的 oracle 适配（独立后续任务）
- oracle 集成到 mvn test / CI（独立后续任务）
- 替代 GameEvaluator 给 LLM 看的评分（**完全不替代**）
- 其他 Skill（除 snake 外）的演进（本任务专注 snake，但产出的 SOP 通用）

## 步骤（7 step 完整闭环）

1. [x] **Step 1：oracle 核心** — `scripts/playability-oracle.sh` + driver.py + verdict.sh + 诊断包五件套（coder subagent 6 分钟交付）
2. [x] **Step 2：fixture v0** — Claude Opus 4.7 子 agent 生成 8632 字节贪吃蛇（DashScope free tier 当夜耗尽，未走 V2 链路）
3. [x] **Step 3：自验** — keytest PASS / dead-page FAIL / snake-v0 PASS（含 oracle 自身 verdict 改进：bodyText 阈值 50→20 + 关键词识别 + driver bodyText 增加截断版字段）
4. [x] **Step 4：v0 → fixed** — v0 已是高质量贪吃蛇（无需修），cp 一份成 fixed.html；debug-log 记录"v0 做对了什么 → 8 条普适改动 → SKILL 候选"
5. [x] **Step 5：蒸馏 SKILL** — `resources/skills/snake-adventure/SKILL.md` 含 10 条生成步骤 + 8 条评估重点 + 7 条常见问题；启动日志确认加载
6. [x] **Step 6：多采样** — 早停于 4 个样本（3 个 R1 + 1 个 R2），通过率 3/4 = 75% ≥ 2/3 阈值。**SKILL 演进有效**。期间 oracle 增加坐标 click + JS click 兜底、driver 末尾 30 次 ArrowRight 撞墙、SKILL 加第 10 条"初始状态非 game over"
7. [x] **Step 7：文档** — `scripts/README.md`（重写）+ `docs/engineering/testing.md §1.5` + `docs/engineering/conventions.md §14.6` 标完成 + `docs/knowledge/principles/skill-evolution-sop.md`（核心 SOP 抽象）

## 验证节奏

按 `docs/engineering/testing.md §1.4`：

- **Step 1, 2, 4, 5**：coder 自验，**不**强制独立 evaluator 复跑
- **Step 3**：oracle 鉴别力自验脚本（短 + 不烧 LLM）
- **Step 6**：**这一步本身就是任务收口端到端**——9 次 LLM 调用、9 次 oracle 跑，通过率即任务交付信号
- **Step 7**：纯文档同步，无验收

## 决策记录

| 决策 | 日期 | 说明 |
|------|------|------|
| 合为单大任务（不再拆三任务） | 2026-05-21 | 用户希望看到完整闭环一次交付。"oracle done 但贪吃蛇还不能玩"的中间态对用户无价值 |
| oracle 是独立 bash 脚本（不进 Java、不集成 mvn） | 2026-05-21 | 站系统外的黑盒 oracle；不与 GameEvaluator 抢职能 |
| 严格判定：按键后变化才算 PASS | 2026-05-21 | 拒绝"自动播放但不响应键盘"伪游戏 |
| WASD + 方向键都发 | 2026-05-21 | 兼容 LLM 可能生成的不同控制方案 |
| 调试 v0 → fixed 由 LLM（我）做 | 2026-05-21 | 主会话拥有完整 HTML + 诊断包 + 推理能力 |
| 多采样 budget：3 轮 × 3 采样 = 9 次 LLM | 2026-05-21 | 平衡 LLM 配额与统计有效性；超 budget 不收敛则 ⚠️ 上报 |
| Step 6 是任务收口 | 2026-05-21 | "通过率 ≥ 2/3" 是本任务唯一硬交付信号 |

## 不变的边界（本任务不许动）

- `agent/loop/*` / `agent/tools/*` / `agent/evaluation/*` —— 不动 AgentLoop / GameEvaluator
- `infra/db/*` / `infra/storage/*` —— 不动持久化
- `api/*` —— 不动 HTTP 端点
- 前端任何文件
- `pom.xml` / `package.json` —— 不引入新依赖
- 其它 SKILL.md（math-adventure / memory-master 等）—— 本任务只新建 snake-adventure，不动既有

## 风险登记

- **R1：LLM 生成的贪吃蛇 v0 不能玩** → 不阻塞，是 Step 4-5 的工作目标
- **R2：DashScope free tier 当天耗尽** → Step 2 / Step 6 阻塞。退路：
  - Step 2 用任意能跑通的 v0（本任务不要求 v0 一定来自当次 LLM 生成）
  - Step 6 配额回来再跑，或换 base_url
- **R3：3 轮多采样仍未收敛**（通过率 < 2/3） → 标 ⚠️ "本次未达成"，提交当前进展 + 待人工裁决；不硬循环
- **R4：browser-harness daemon 不健康** → oracle 启动时 `--doctor`，daemon 死自动 `--setup`
- **R5：通用 DOM 数字扫描误报** → baseline 阶段先采 1 秒"自然变化"，加白名单
- **R6：v0 → fixed 调试很难（LLM 推不出 bug 在哪）** → Step 4 budget 30 分钟，超时人工接手

## 涉及人/责任

- **主会话（项目经理）**：管理进度、监督子任务、不每步问用户；Step 4 / Step 6 必须亲自做
- **subagent**：仅用于 Step 1 / Step 7（明确产出物，不依赖深度推理）
- **dreamer**：阶段收尾后 Step 7 一并整理
