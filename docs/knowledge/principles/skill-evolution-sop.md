# SKILL 演进 SOP（Standard Operating Procedure）

> 由任务 `260521-playable-snake-evolution` 蒸馏。
> 适用：当某个 SKILL.md 让 LLM 生成的游戏"勉强能跑但不够好玩"时，按此 SOP 演进。
> 抽象后**通用**——任何 SKILL（math / memory / english / shape-colors / 未来新建的）都可走此流程。

## 核心思路

LLM 生成 → oracle 验证 → 失败时离线修 → 蒸馏成 SKILL.md 改动 → 多采样验证 SKILL 真的更好

```
   ┌────────────────────────────────────────┐
   │  LLM + 当前 SKILL  →  v0 生成          │
   └────────────────────────────────────────┘
                  ↓ oracle.sh
            FAIL / PASS
                  ↓
   ┌────────────────────────────────────────┐
   │  人工 / LLM 看诊断包 → 调试 v0→fixed   │
   │  记 debug-log.md（每条改动 + 普适性）   │
   └────────────────────────────────────────┘
                  ↓ diff
   ┌────────────────────────────────────────┐
   │  普适改动 → 进 SKILL.md                │
   │  个案改动 → 留 task memory 不进 SKILL  │
   └────────────────────────────────────────┘
                  ↓ LLM + 新 SKILL
   ┌────────────────────────────────────────┐
   │  N 次采样生成 + N 次 oracle 验证       │
   │  通过率 ≥ 2/3 → 演进有效（收敛）       │
   │  否则回到调试 / 再蒸馏（最多 3 轮）    │
   └────────────────────────────────────────┘
```

## 6 步流程

### Step A：基线采样
- LLM 用现有 SKILL 生成 1 个 v0
- 存到 `test/fixtures/playability/{skill-name}-v0.html`

### Step B：oracle 验证
- 跑 `./scripts/playability-oracle.sh <fixture>`
- 看 verdict.txt + 双截屏 + result.json
- PASS：基线已达标，演进可能不必要——但仍可走 C-D 看是否有可改进项
- FAIL：进 Step C

### Step C：离线调试 v0 → fixed
- LLM（或人）看诊断包推理 v0 的 bug
- 最小改动到 fixed.html，跑 oracle 必须 PASS
- **每条改动记 why + 普适性判定**（普适 / 个案）

### Step D：蒸馏
- 把"普适"改动写进 SKILL.md
  - 写进"评估重点"段（关键检查项）
  - 或"常见问题"段（症状 → 修法对）
  - 或"生成步骤"段（结构性建议）
- "个案"改动留 task memory 不进 SKILL
- SKILL.md body ≤ 80 行（dreamer 标准）

### Step E：多采样验证
- 用新 SKILL 跑 N 次 LLM 生成（建议每轮 3 次）+ 跑 oracle
- **通过率 ≥ 2/3** → 演进有效，收敛
- **早停优化**：第 1 轮 100% PASS 直接停（不浪费 LLM）

### Step F：失败处理
- N 轮（建议 ≤ 3 轮 = 最多 9 次 LLM）通过率仍 < 2/3
- 标 ⚠️ "未收敛"，提交当前 SKILL（仍有部分改进价值）+ 上报
- **不硬循环**——浪费配额，留待人工介入或后续任务

## 反模式

| 反模式 | 后果 |
|---|---|
| 修了 v0 不记 why | Step D 没法蒸馏，演进无方向 |
| 把"个案"改动也塞 SKILL | SKILL 越写越长、过拟合，未来不同变种生成被误导 |
| 不做多采样、跑 1 次过就提交 | LLM 非确定性，单次 PASS 可能是巧合 |
| 演进失败硬循环 | 浪费配额 |
| oracle 自己有 bug 时硬塞 SKILL | 假阴/假阳信号污染演进，越演越乱。先修 oracle 再演 SKILL |

## 适用边界

- ✅ 适用：键盘类游戏（贪吃蛇、躲避类、按键反应游戏）
- ⚠️ 部分适用：点击类游戏（oracle 需扩展点击驱动；当前驱动主要是按键）
- ❌ 不适用：拖拽类、多模态、需登录的游戏

## 典型 SKILL 蒸馏的"普适规则"模式

通用的"普适改动"经验（任何游戏 SKILL 都可借鉴的常见 LLM 偏差）：

1. **事件绑到 document，不要绑 button/canvas**——典型 LLM 偏差，导致需要先 click focus
2. **dir + nextDir 解耦**——避免反向键瞬间反向自撞
3. **持续 tick 驱动（setInterval/raf）**——避免"按键时才动"
4. **每帧清画布 / 重绘**——避免拖影
5. **score 用专门 DOM id**——便于人 + 自动化测试都能读
6. **游戏结束态可见 + 鼓励文案 + 重启入口**——儿童友好 + 可恢复
7. **初始状态"准备开始"非"游戏结束"**——避免页面加载就显示 game over 让用户 / 测试误判

这些是从 snake-adventure 演进里提炼的，但其它键盘游戏（赛车、空战躲避等）大多数都适用。

## 案例：snake-adventure SKILL 的演进

详见 `docs/task/260521-playable-snake-evolution/`：
- 首次 LLM 通过率：5/5（4 个 LLM 生成样本 + 1 个 v0 fixture）
- 经过 1 轮（无需多轮迭代）SKILL 蒸馏 + 1 轮 oracle 自身 driver 改进
- 最终通过率：3/4 = 75% ≥ 2/3 阈值

关键发现：
- 真实 LLM 生成的偏差是**"初始状态错误为 game over"**——SKILL 加第 10 条规则后修正
- oracle 自身需要"坐标 click + JS click() 双重兜底"才能可靠触发开始按钮
- "canvas auto-changing" 白名单太粗，需要靠 bodyText 关键词作为强信号补足

## 来源

- 原始任务 progress：`docs/task/260521-playable-snake-evolution/progress.md`
- 调试日志：`docs/task/260521-playable-snake-evolution/memory/2026-05-22-debug-log.md`
- 多采样结果：`docs/task/260521-playable-snake-evolution/memory/2026-05-22-multisample-results.md`
