# 260521-playability-oracle — 游戏可玩性自动验证 oracle

## 目标

做一个**通用的游戏可玩性 oracle**——独立 bash 工具，输入一段 HTML，能机器化判定"这个游戏是否真能玩"，输出 PASS/FAIL + 完整诊断包。

本期首个 case 是贪吃蛇（v0 来自真 LLM 生成）。oracle 设计为**与游戏类型解耦**——后续点击类、拖拽类游戏可以靠扩展驱动器复用同一套 oracle。

## 范围（本任务做什么、不做什么）

✅ 做：
- `scripts/playability-oracle.sh` 工具本身
- 通用 driver（WASD + 方向键交替发）+ pre-flight start 按钮检测 + 三类信号采集（canvas hash / 数字文本 / DOM innerText）
- **严格判定**：必须出现"按键后变化"才 PASS；过滤"自动播放但不响应"的伪游戏
- 完整诊断包写到 `/tmp/playability-oracle/run-{ts}/`：HTML、初始/终态截屏、键盘日志、DOM 扫描、verdict
- 用 1 次真 LLM 生成贪吃蛇 fixture v0（本任务里 v0 不要求能玩，只要求能作为 oracle 自验材料）
- 自验：oracle 跑 fixture v0 + 故意做坏的静态 HTML → 鉴别力（PASS/FAIL 都对症）
- 文档：`scripts/README.md` 解释 oracle 用法；`docs/engineering/testing.md` 加"游戏可玩性验证"段

❌ 不做（独立后续任务）：
- v0 → fixed 调试 + SKILL.md 改动 + 多采样验证 → 独立任务 `260521-snake-skill-evolve`
- "生成→验证→演进"循环本身的 SOP 文档化 → 独立任务 `260521-skill-evolution-loop`
- 点击类 / 拖拽类 oracle 适配
- 集成到 mvn test 或 CI
- 替代 GameEvaluator 给 LLM 看的评分（**不替代**，那是另一回事）

## 三任务依赖链（已规划）

```
260521-playability-oracle           ← 本任务（独立可交付：oracle 工具）
         ↓ 依赖
260521-snake-skill-evolve            ← 后续：用 oracle 调 v0→fixed，改 SKILL，多采样验证
         ↓ 依赖
260521-skill-evolution-loop          ← 后续：抽象演进 SOP 到 docs/knowledge/
```

## 步骤

1. [ ] **Step 1：oracle 核心** — `scripts/playability-oracle.sh` + 三类信号采集 + 严格判定 + 诊断包
2. [ ] **Step 2：fixture v0** — 真 LLM 调一次贪吃蛇，存到 `test/fixtures/playability/snake-v0.html`
3. [ ] **Step 3：自验（鉴别力）** — oracle 对 v0 + 静态死页面分别给出预期方向的判定
4. [ ] **Step 4：文档** — `scripts/README.md` + `docs/engineering/testing.md` 新增 oracle 用法段

## 验证节奏（重要约定）

按 `docs/engineering/testing.md §1.4`，本任务采用**中间轻验、收口重验**：

- Step 1：coder 自验跑通脚本能加载 fixture / 输出诊断包即可，**不**强制独立 evaluator 复跑
- Step 2：LLM 调用本身就是验证（响应 success=true 即过）
- Step 3：**这一步本身就是任务收口的端到端**——oracle 对 v0 + 死页面给出对症判定，是本任务真正的 SSOT 信号
- Step 4：纯文档同步，无验收

也就是说本任务的"任务收口端到端"=Step 3。一次跑通就过。不再额外开 step 跑收口。

## 决策记录

| 决策 | 日期 | 说明 |
|------|------|------|
| oracle 是独立 bash 脚本（不进 Java、不集成 mvn） | 2026-05-21 | 站在系统外的黑盒 oracle；不与 GameEvaluator 抢职能（那是给 LLM 自评的，本 oracle 是给端到端验证的） |
| 严格判定：按键后变化才算 PASS | 2026-05-21 | 拒绝"自动动画但不响应键盘"的伪游戏；用 baseline vs final 对比+按键事件日志保证信号是因果链 |
| WASD + 方向键都发 | 2026-05-21 | 兼容 LLM 可能生成的不同控制方案 |
| 拆三个独立任务 | 2026-05-21 | "做 oracle"/"演进 SKILL"/"SOP 化"各自能独立交付，避免一锅炖 |
| 任务收口 LLM 失败也可通过 | 2026-05-21 | Step 2 LLM 失败时 v0 用最小 HTML 占位；oracle 自验对 v0 失败照样能验证（只要 oracle 输出 FAIL + 诊断包对症即可） |
| 调试 v0 → fixed 由 LLM（我）做 | 2026-05-21 | 已在任务 2 的范围；本任务（任务 1）不涉及 |

## 不变的边界（本任务不许动）

- `agent/loop/*` / `agent/tools/*` / `agent/evaluation/*` / `agent/skill/*`
- `infra/db/*` / `infra/storage/*`
- `api/*`
- 前端任何文件
- `pom.xml` / `application.yml`（不引入新后端依赖）
- `package.json`（不引入新前端依赖）
- 任何 SKILL.md（演进留任务 2）

## 风险登记

- **R1：LLM 生成的贪吃蛇 v0 不能玩** → 不阻塞本任务。Step 3 自验设计为 v0 能玩 PASS / 不能玩 FAIL+诊断包对症 都算 oracle 工作正常
- **R2：DashScope free tier 当天耗尽** → Step 2 阻塞。退路：用任意一个跑得通的 fixture（甚至手写最小贪吃蛇）+ "故意做坏的静态页"互为对照
- **R3：browser-harness daemon 不健康** → oracle 启动时跑 `--doctor`，daemon 死自动 `--setup`
- **R4：通用数字扫描误报（页面有自动跳的计时器）** → baseline 阶段先采集 1 秒"自然变化扫描"，把那段时间内会自动变的元素加入"已知会变"白名单，验证时排除这些

## 涉及人/责任

- task-designer：本目录 + plan
- coder：按 plan/{step}.md 实现
- evaluator：按各 step 验收契约复跑
- code-reviewer：提交前审查
- doc-refresher：Step 4 触发文档同步
- dreamer：阶段收尾后整理 memory
