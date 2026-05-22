---
name: snake-adventure
description: 生成贪吃蛇互动游戏，支持键盘控制、计分、碰撞检测。当用户提到贪吃蛇 / snake / 蛇 / 经典移动游戏 等关键词时使用。
metadata:
  ageGroup: "4-12"
  gameType: action
  tags: [贪吃蛇, snake, 键盘, 经典, 动作]
---

# 贪吃蛇

经典贪吃蛇：键盘控制方向，吃食物增长身体，撞墙或撞自身结束。本 SKILL 由任务 260521-playable-snake-evolution 蒸馏，每条规则都来自真实 LLM 生成偏差的修正。

## 何时使用

用户提到 **贪吃蛇 / snake / 蛇 / 吃豆豆（带方向键控制）** 等关键词。

## 生成步骤

1. **canvas 元素** 400-600 px 见方，`<canvas id="game">` 用 id 便于测试钩子
2. **键盘事件绑到 document**，**不要**绑到 button / canvas（否则需要 focus 才能控制）
3. **同时支持方向键 + WASD**：
   ```js
   if (k === 'ArrowUp' || k === 'w' || k === 'W') ...
   ```
4. **dir + nextDir 解耦** 实现反向键防呆：keydown 写 nextDir，下次 tick 判定再赋给 dir
5. **setInterval 持续驱动 tick**（180-300ms），不要"按键时才移动"
6. **每 tick 清画布**：`ctx.clearRect(0, 0, w, h)` 或填充背景色
7. **food 与蛇不重叠**：`placeFood()` 用 while 循环检测 `snake.some(s=>s.x===fx&&s.y===fy)`，重叠则重新随机
8. **score 用专门 DOM 元素**：`<span id="score">0</span>`，每次吃食物 `scoreEl.textContent = score`
9. **游戏结束态明确**：撞墙/自撞触发 `clearInterval(timer)` + 显示 overlay + 鼓励而非惩罚的文案 + 提供"再来一局"重启入口
10. **初始状态必须是"准备开始"而非"游戏结束"**：页面加载后 overlay 文案应是"准备好了吗？/ 开始游戏"，按钮文案应是"开始游戏"——**绝对不能**初始就显示"游戏结束 / 再来一局"（那让用户和测试都误以为已经死了）。只有真撞墙后才切换为结束态。

## 评估重点

- 键盘事件**必须**绑到 document（绑 button/canvas 会导致需要先 focus 才能控制）
- 反向键被防呆（蛇向右走时按左键不能瞬间反向 → 否则秒死）
- 蛇在 setInterval 里持续移动，不是只在 keydown 时移动
- canvas 每 tick 清画布，无拖影
- 食物不能生成在蛇身上
- 吃食物分数真的涨（DOM 同步更新）
- 游戏结束态可见（overlay / alert / 状态变化），不是无声死亡
- 失败文案鼓励（"再来一次"、"差一点点"），不要"你输了"这种打击

## 常见问题

- **按键无响应** → 检查 keydown 是否绑到 document；如果绑到 button/canvas 需要先 click 才能控制
- **蛇不动** → 移动逻辑应在 setInterval 里持续 tick，不要只在 keydown 时移动
- **瞬间死亡** → 反向键应忽略：用 dir + nextDir 解耦，tick 时检查 `nextDir.x !== -dir.x` 才赋值
- **吃了食物分数没涨** → score 变量更新后必须 `document.getElementById('score').textContent = score`
- **canvas 拖影** → 每个 tick 必须 clearRect 整个画布，或 fillRect 整背景
- **食物和蛇重叠** → placeFood 用 while 循环检测重叠 + 重新随机
- **撞墙后画面卡死** → gameOver 必须 clearInterval(timer) + 显示结束 overlay 而不是只 return
