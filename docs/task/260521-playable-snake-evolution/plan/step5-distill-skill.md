# Step 5：蒸馏出 snake-adventure SKILL.md

## 背景

Step 4 产出了 `debug-log.md`——v0 → fixed 的所有改动 + 每条的 why + 普适性判定。本 step 把"普适"那部分蒸馏进新建的 `resources/skills/snake-adventure/SKILL.md`，让 LLM 下次见到"贪吃蛇"关键词就用这条 SKILL，避开当年踩过的坑。

## 【实现契约（主会话执行）】

### 范围

- **可改文件（新建）**：
  - `game-agent-backend/src/main/resources/skills/snake-adventure/SKILL.md`
  - `game-agent-backend/src/main/resources/skills/snake-adventure/assets/template.html`（可选，用 snake-fixed 作为模板）

- **不可改文件**：
  - 其它 SKILL.md（math-adventure / memory-master 等不动）
  - debug-log.md（Step 4 产出，本 step 只读）
  - snake-v0.html / snake-fixed.html（fixture 不动）
  - SkillLoader.java / SkillDefinition.java —— 这些已支持自动扫描 `resources/skills/*/SKILL.md`

### 产出清单

#### `SKILL.md`（按 AgentSkills.io 规范）

```markdown
---
name: snake-adventure
description: 生成贪吃蛇互动游戏，支持键盘控制、计分、碰撞检测。当用户提到"贪吃蛇"、"snake"等关键词时使用。
metadata:
  ageGroup: "4-12"
  gameType: action
  tags: [贪吃蛇, snake, 键盘, 经典, 动作]
---

# 贪吃蛇

经典贪吃蛇游戏，键盘控制方向，吃食物增长身体，撞墙或撞自身结束。

## 何时使用

用户提到 **贪吃蛇 / snake / 蛇 / 吃豆豆（带方向键控制）** 等关键词。

## 生成步骤

1. 准备 canvas 元素（推荐 400×400 px 内）
2. 用 setInterval 200-300ms 循环驱动游戏
3. **键盘事件必须绑到 document**（不要绑 canvas / button）
4. 同时支持方向键（ArrowUp/Down/Left/Right）和 WASD
5. 每个 tick 移动蛇头、检测食物碰撞、检测边界/自撞
6. 显示分数（专门的 DOM 元素 `<div id="score">`，便于自动化测试钩子）
7. 食物随机重生

## 评估重点

- 键盘事件**必须**绑到 document（绑 button / canvas 会导致需要先 focus 才能控制）
- 蛇移动逻辑必须在 setInterval 里，不能仅靠 keydown 触发（否则不按就不动）
- 反向键应被忽略（蛇向右走时按左键不能立刻反向，否则瞬间自撞）
- 食物碰撞必须真实增长 body 数组（不是只更新分数显示）
- canvas 必须每帧 clearRect 重绘，不能累积绘制
- 游戏结束态必须可见（覆盖层 / alert / DOM 状态变化）

## 常见问题

> （以下条目根据 Step 4 debug-log.md 的"普适"改动蒸馏。
> 实际写时按 Step 4 真实产出填，这里给模板。）

- **按键无响应** → 检查 keydown 是否绑到 document；如果绑到 button/canvas，需要先 click focus
- **蛇不动** → 移动逻辑应在 setInterval 里，不要只在 keydown 时移动
- **吃了食物分数没涨** → 检查 score 变量更新后是否同步更新 DOM
- **canvas 拖影** → 每个 tick 必须 clearRect 整个画布
- **瞬间死亡** → 反向键应忽略（dx == -prevDx 时不切换方向）
```

> **注意**："常见问题"段的具体内容**取决于 Step 4 debug-log.md 实际记下来的改动**，不是事先编出来。本 plan 只给结构模板。

#### `assets/template.html`（可选）

把 `test/fixtures/playability/snake-fixed.html` 拷贝过来作为参考模板，加几行注释说明"这是经过验证的可玩贪吃蛇基线"。

> **判定要不要放 template.html**：如果 fixed.html 比较通用（不依赖 v0 的特殊命名/结构），值得放；如果 fixed 是为 v0 量身定制的修正补丁，则不放（让 LLM 自己根据 SKILL 生成）。Step 5 执行时再判断。

### 约束

- **只蒸馏 debug-log.md 标记为"✅ 普适"的改动**——个案改动不进 SKILL（避免过拟合）
- **SKILL.md body 不超过 80 行**（dreamer 风格：超过说明没蒸馏透）
- **frontmatter 严格按 AgentSkills.io 规范**：name + description 必填，其它进 metadata
- **不动 SkillLoader 代码**——加 SKILL 是新建文件，不需要改加载器（已支持自动扫描）

### 复用

- 现有 SKILL.md 风格参考 `resources/skills/math-adventure/SKILL.md`（已上线）
- 蒸馏取舍参考 `agents/dreamer/dreamer.md`（"上浮判断的硬标准"）

### 依赖

- Step 4 完成（debug-log.md 就绪）

## 【验收契约】

### coder 自验

- [ ] 新文件 `resources/skills/snake-adventure/SKILL.md` 存在
- [ ] frontmatter 含 `name: snake-adventure` + `description`
- [ ] body 含 `## 何时使用` / `## 生成步骤` / `## 评估重点` / `## 常见问题` 四段
- [ ] body ≤ 80 行
- [ ] 所有"常见问题"条目能在 debug-log.md 找到对应的"✅ 普适"改动
- [ ] 启动 backend 后，启动日志含 `加载 Skill: snake-adventure`

### 命令验收

```bash
[ -f game-agent-backend/src/main/resources/skills/snake-adventure/SKILL.md ] || exit 1

# frontmatter 校验
head -1 game-agent-backend/src/main/resources/skills/snake-adventure/SKILL.md | grep -q '^---$'
grep -q "^name: snake-adventure$" game-agent-backend/src/main/resources/skills/snake-adventure/SKILL.md
grep -q "^description:" game-agent-backend/src/main/resources/skills/snake-adventure/SKILL.md

# body 四段
for sec in "## 何时使用" "## 生成步骤" "## 评估重点" "## 常见问题"; do
  grep -qF "$sec" game-agent-backend/src/main/resources/skills/snake-adventure/SKILL.md || exit 1
done

# body 长度
SECTIONS=$(awk '/^---$/{n++; next} n==2' game-agent-backend/src/main/resources/skills/snake-adventure/SKILL.md | wc -l)
[ "$SECTIONS" -le 100 ] || { echo "body too long: $SECTIONS"; exit 1; }
```

### 启动验证

```bash
# 启 backend，看 SKILL 是否被加载
( cd game-agent-backend && mvn spring-boot:run -q ) > /tmp/skill-load.log 2>&1 &
PID=$!
for i in $(seq 1 60); do
  curl -sf http://localhost:8088/api/game/agents > /dev/null && break
  sleep 1
done
sleep 2
grep -q "加载 Skill: snake-adventure" /tmp/skill-load.log || { echo "Skill 未加载"; exit 1; }
kill $PID; wait $PID 2>/dev/null
```

### 剩余风险

- 如果 debug-log.md 几乎全部是"个案"，蒸馏不出 SKILL → 标 ⚠️ "本次演进无普适收益"，但仍创建一个最小 SKILL（at least 让 LLM 知道有 snake 这个 SKILL 可用）
- 蒸馏过度 → "常见问题"段把 v0 的特殊问题写成普适规则，未来其它贪吃蛇变种生成时反而被误导

## 后续 Step 依赖

Step 6 用本 step 产出的 SKILL 跑 9 次 LLM 多采样验证。
