# Step 7：文档收尾（含演进 SOP 抽象）

## 背景

完整闭环（Step 1-6）跑完，本 step 把流程文档化：让别人能用 oracle、能跑 multisample、能复制本任务的"生成→验证→演进"循环到其它 SKILL。

## 【实现契约】

### 范围

- **可改文件（新增/修改）**：
  - `scripts/README.md`（完整版，含 oracle + multisample 用法）
  - `docs/engineering/testing.md`（新增 §1.5 "游戏可玩性自动验证"段）
  - `docs/engineering/conventions.md`（§14.6 标完成）
  - `docs/knowledge/principles/skill-evolution-sop.md`（**新增**：可复用的 SKILL 演进 SOP）
  - `docs/task/260521-playable-snake-evolution/progress.md`（7 个 step 全标完 + commit hash）
  - `docs/task/260521-playable-snake-evolution/memory/SUMMARY.md`（dreamer 风格汇总）

- **不可改文件**：scripts/ 下脚本实现；resources/skills/snake-adventure/（不再改）

### 产出清单

#### `scripts/README.md`

```markdown
# scripts/

工程脚本，独立于 backend / frontend。

## playability-oracle.sh

游戏可玩性自动判定 oracle。

### 用法
\```bash
./scripts/playability-oracle.sh <path/to/game.html>
\```

### 退出码
- 0：PASS（按键后画面/数字/DOM 有变化，且不是自动动画）
- 1：FAIL（30 次按键后无任何变化，或 JS 错误）
- 2：工具错误

### 诊断包
每次 run 写到 `/tmp/playability-oracle/run-<timestamp>/`：game.html / screenshot-baseline.png / screenshot-after-keys.png / result.json / verdict.txt

### 自验
\```bash
./scripts/playability-oracle-self-test.sh
\```

## snake-skill-multisample.sh

多采样验证 snake-adventure SKILL 演进有效。

### 用法
\```bash
./scripts/snake-skill-multisample.sh [round] [samples]
# 默认 3 round × 3 samples = 9 次 LLM
\```

### 退出码
- 0：通过率 ≥ 2/3，SKILL 演进有效
- 1：通过率 < 2/3，需回 Step 4 调试 SKILL

### 输出
`/tmp/snake-multisample/run-<timestamp>/`：summary.md / 每次的 game-r{N}-s{N}.html / oracle-r{N}-s{N}.txt
```

#### `docs/engineering/testing.md` §1.5（约 60 行）

```markdown
### 1.5 游戏可玩性自动验证 + SKILL 演进闭环

存在的问题：LLM 生成的游戏，光看"HTML 长度 > 100"完全验不出"是否真能玩"。需要 oracle + 多采样验证 SKILL 演进有效。

#### 工具

| 脚本 | 用途 |
|---|---|
| `scripts/playability-oracle.sh <html>` | 单次判定一个 HTML 能否玩 |
| `scripts/snake-skill-multisample.sh` | 用某个 SKILL 跑 N 次 LLM 生成，统计通过率 |

#### oracle 判定原理（严格模式）

详细原理见 `docs/knowledge/principles/skill-evolution-sop.md`。简略：
1. browser-harness 加载 HTML（file://）
2. Pre-flight click 开始按钮（如有）
3. baseline 1 秒采集自然变化（白名单）
4. WASD + 方向键 30 次驱动
5. 三类信号 OR：canvas hash / 数字文本 / innerText
6. 排除自然变化白名单后任一信号变 → PASS

#### 不适用场景

- 给 LLM 自评的内置评分 → 那是 GameEvaluator 的活
- 单元测试覆盖率 → mvn test
- 点击/拖拽类游戏 → 当前 oracle 偏键盘类（独立后续任务扩展）
```

#### `docs/knowledge/principles/skill-evolution-sop.md`（**核心新文档**）

```markdown
# SKILL 演进 SOP（Standard Operating Procedure）

> 由任务 260521-playable-snake-evolution 蒸馏。
> 适用：当某个 SKILL.md 让 LLM 生成的游戏"勉强能跑但不够好玩"时，按此 SOP 演进。

## 核心思路

LLM 生成 → oracle 验证 → 失败时离线修 → 蒸馏成 SKILL.md 改动 → 多采样验证 SKILL 真的更好

## 6 步流程

### Step A：基线采样
跑 1 次 LLM，拿 v0 HTML

### Step B：oracle 验证
跑 oracle，看 PASS / FAIL + 诊断包

### Step C：离线调试 v0 → fixed
LLM（或人）看诊断包推理 v0 的 bug，最小改动到 fixed.html。fixed 必须 oracle PASS。
**关键**：每条改动必须记 why + 普适性判定（普适 / 个案）

### Step D：蒸馏
把"普适"改动写进 SKILL.md 的"评估重点"或"常见问题"段。
"个案"改动留 task memory 不进 SKILL。
SKILL.md body ≤ 80 行（dreamer 标准）

### Step E：多采样验证
用新 SKILL 跑 9 次 LLM 生成 + 9 次 oracle。通过率 ≥ 2/3 → 演进有效。
每轮 3 次采样早停：第 1 轮 100% PASS 直接停。

### Step F：失败处理
3 轮（9 次）都 < 2/3 → 标 ⚠️ "未收敛"，提交当前 SKILL（仍有部分改进价值）+ 上报。**不硬循环**。

## 反模式

- 修了 v0 不记 why → Step D 没法蒸馏，演进无方向
- 把"个案"改动也塞 SKILL → SKILL 越写越长、过拟合
- 不做多采样、跑 1 次过就提交 → LLM 非确定性，单次 PASS 可能是巧合
- 演进失败硬循环 → 浪费配额，不如标 ⚠️ 让人介入

## 适用边界

- 适用：键盘类游戏（贪吃蛇、躲避类、按键反应游戏）
- 部分适用：点击类游戏（oracle 需扩展点击驱动）
- 不适用：拖拽类、多模态、需登录的游戏

## 案例：snake-adventure SKILL 的演进

详见 `docs/task/260521-playable-snake-evolution/`。
首次 LLM 通过率：X/9（X% PASS）→ 经过 N 轮演进 → 最终通过率：Y/9（Y% PASS）
```

> 实际 X / Y / N 在 Step 6 跑完后填回。

#### `progress.md` 更新

7 个 step 全部 [x] + 加最终 commit hash。决策表加 Step 6 多采样实际通过率。

#### `memory/SUMMARY.md`（dreamer 风格汇总）

按 dreamer 主题聚类，不按时间。每主题：核心结论 + 关键证据 + 被否决方案 + 相关 commit。

主题示例：
- 主题 1：oracle 设计（严格判定、自然变化白名单、三类信号）
- 主题 2：v0 → fixed 调试发现的普适规则（哪些进了 SKILL）
- 主题 3：多采样统计学（早停 / budget / 收敛判定）
- 主题 4：演进失败的处理（如有）

### 约束

- testing.md §1.5 ≤ 80 行（精简）
- skill-evolution-sop.md 是**通用 SOP**，不许写 snake 专属内容（snake 案例只在末尾点一句"详见……"）
- 不动 oracle / multisample 实现脚本

### 依赖

- Step 1-6 全部完成

## 【验收契约】

### 文档存在性 + 结构对齐

```bash
[ -f scripts/README.md ] || exit 1
grep -q "playability-oracle.sh" scripts/README.md
grep -q "snake-skill-multisample.sh" scripts/README.md

grep -q "## 1.5" docs/engineering/testing.md
grep -q "14.6 游戏可玩性自动验证" docs/engineering/conventions.md

[ -f docs/knowledge/principles/skill-evolution-sop.md ] || exit 1
grep -qE "^# SKILL 演进 SOP" docs/knowledge/principles/skill-evolution-sop.md

# progress.md 7 个 step 全标完
grep -c '^\d\. \[x\]' docs/task/260521-playable-snake-evolution/progress.md
# 应输出 7
```

### doc-refresher 检视（可选）

spawn doc-refresher 验证文档与代码（scripts、SKILL.md）一致。

### 任务收口

push 后任务关闭。
