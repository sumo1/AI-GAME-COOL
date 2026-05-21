# Step 4：文档收尾

## 背景

oracle 已工作，写文档让别人能用、让后续任务能调。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `scripts/README.md`（Step 1 已写骨架，本 step 写完整）
  - `docs/engineering/testing.md`（新增 §1.5 "游戏可玩性自动验证"段）
  - `docs/engineering/conventions.md`（§14.6 标完成）
  - `docs/task/260521-playability-oracle/progress.md`（4 个 step 全标完）
  - `docs/task/260521-playability-oracle/memory/SUMMARY.md`（dreamer 风格汇总，可选）

- **不可改文件**：scripts/ 下脚本（不再改实现）

### 产出清单

#### `scripts/README.md`

```markdown
# scripts/

本目录工具脚本，独立于 backend / frontend。

## playability-oracle.sh

游戏可玩性自动判定 oracle。输入 HTML 文件，机器化判定"是否真能玩"。

### 用法
\```bash
./scripts/playability-oracle.sh <path/to/game.html>
\```

### 退出码
- 0：PASS（按键后画面/数字/DOM 有变化，且不是自动动画）
- 1：FAIL（30 次按键后无任何变化，或 JS 错误）
- 2：工具错误（文件不存在、browser-harness 挂等）

### 诊断包
每次 run 写到 `/tmp/playability-oracle/run-<timestamp>/`，含：
- `game.html`：被测 HTML 拷贝
- `screenshot-baseline.png` / `screenshot-after-keys.png`：初始/终态截屏
- `result.json`：信号采集原始数据
- `verdict.txt`：判定理由

### 自验
\```bash
./scripts/playability-oracle-self-test.sh
\```
跑 keytest（PASS）/ dead-page（FAIL）/ snake-v0（不限）三个 fixture 验 oracle 鉴别力。

### 设计原理
详见 `docs/engineering/testing.md §1.5`。
```

#### `docs/engineering/testing.md` 新增章节

在 §1.4 之后加 §1.5：

```markdown
### 1.5 游戏可玩性自动验证（任务 260521-playability-oracle 引入）

存在的问题：LLM 生成的游戏 HTML，光靠"长度 > 100 + 含 DOCTYPE"完全验不出"是否真能玩"。需要一个能机器化判定可玩性的 oracle。

工具：`scripts/playability-oracle.sh <html-path>` —— 独立 bash + browser-harness。

判定原理（**严格模式**）：
1. browser-harness 加载 HTML（file:// 协议，避开前端干扰）
2. Pre-flight：扫 DOM 找"开始 / Start"按钮，找到则 click
3. baseline 自然变化采样（1 秒不发按键，看自动动画的元素，记入"已知会变白名单"）
4. 真正 baseline：截屏 + canvas hash + 数字文本 + DOM innerText
5. 驱动：WASD + 方向键交替发 30 次（每次 200ms 间隔）
6. final：截屏 + 三类信号
7. 判定：baseline 与 final 比对，**排除自然变化白名单**后任一信号变 → PASS；全无变化 → FAIL

三类信号 OR 关系，覆盖不同游戏类型：
- canvas hash：贪吃蛇、躲避类等用 canvas 渲染的
- 数字文本：DOM 类游戏的分数 / 长度 / 计数
- innerText hash：UI 大变化（题目切换、关卡推进）

诊断包帮人判读："为什么 FAIL"通过 baseline 截屏 vs final 截屏 + result.json 一目了然。

调用约定：
- 不进 mvn / npm，独立 bash
- 不依赖运行中 backend / frontend
- 退出码 0/1/2 区分 PASS/FAIL/工具错

适用场景：
- 任务收口端到端（验证生成的游戏真能玩）
- SKILL.md 演进循环（生成 → oracle 验证 → 调试 → 蒸馏 SKILL）
- CI（远期，独立任务）

不适用：
- 给 LLM 自评的内置评分（那是 GameEvaluator 的活）
- 单元测试覆盖率统计
```

#### `docs/engineering/conventions.md` §14.6

把"游戏可玩性自动验证"从待补章节中划掉：

```markdown
## 14. 待补章节

- [ ] 14.1 RAG 与 VectorStore 实现选择指南
- [ ] 14.2 ChatModelRegistry 扩展规范
- [ ] 14.3 前端组件分层（pages / components / services）
- [ ] 14.4 Probe 脚本扩展指南
- [ ] 14.5 日志格式与可观察性
- [x] 14.6 游戏可玩性自动验证（见 testing.md §1.5，任务 260521-playability-oracle 完成）
```

#### `docs/task/260521-playability-oracle/progress.md`

把 4 个 step 全标 [x]，决策表加最终 commit hash。

#### `memory/SUMMARY.md`（可选 dreamer 风格汇总）

如果本任务过程中积累了 ≥ 3 条 memory，则写 SUMMARY 索引；否则跳过。

### 约束

- 不改 oracle 实现
- 不引入新章节到 conventions.md（除已规划的 §14.6）
- testing.md §1.5 不超过 60 行
- README.md 不超过 50 行（用例够清楚就行）

### 依赖

- Step 1-3 全部完成

## 【验收契约（Evaluator 输入）】

### 文档存在性 + 结构对齐（coder 自验）

```bash
[ -f scripts/README.md ] || exit 1
grep -q "playability-oracle.sh" scripts/README.md || exit 1
grep -q "## 1.5" docs/engineering/testing.md || exit 1
grep -q "14.6 游戏可玩性自动验证" docs/engineering/conventions.md || exit 1

# progress.md 4 个 step 全标完
grep -c '\[x\] \*\*Step' docs/task/260521-playability-oracle/progress.md
# 应输出 4
```

### doc-refresher 检视

可选：spawn doc-refresher 验证文档与代码（scripts 下的实际脚本）一致。

### 任务收口

push 后任务关闭。下个任务 260521-snake-skill-evolve 才启动。
