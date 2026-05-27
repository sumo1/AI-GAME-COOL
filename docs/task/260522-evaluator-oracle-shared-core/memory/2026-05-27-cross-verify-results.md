# Step 4 交叉验证结果（部分完成）

> 日期：2026-05-27
> 关联：plan/step4-cross-verify.md / 2026-05-27-evaluator-score-regression.md

## TL;DR

**任务分成两半**：
- ✅ **GameEvaluator Java 端** 在共享 JS 上跑通了，shared/playability/ 注入正常
- ⚠️ **oracle 端** 需要 browser-harness daemon CDP 授权（用户在 Chrome click Allow），coder 环境跑不了，需要用户本地完成
- ⚠️ **evalScore 出现非线性偏差**：dead-page=55、keytest=55、snake-v0=35、snake-fixed=35。比预期更糟——不只是"系统性下降"，而是"snake 被错算成越界"

## 实测数据（仅 Java 端，2026-05-27 16:36）

| fixture | evaluator totalScore | runnability | layout | interactivity | completeness | education | 备注 |
|---|---|---|---|---|---|---|---|
| dead-page.html  | 55 | 20 | 20 | 0  | 0  | 15 | 无 JS、无越界，layout 拉满 |
| keytest.html    | 55 | (待实测分维度) | (同) | 0 | 0 | 15 | 与 dead-page 同分 → 鉴别力下降 |
| snake-v0.html   | 35 | (待实测) | (低) | 0 | 0 | 15 | canvas 被算入越界元素？ |
| snake-fixed.html| 35 | 同上 | 同上 | 0 | 0 | 15 | 同 v0 |

## 根本问题

plan §约束已预警 events / stateChanges 暂空导致 interactivity + completeness = 0；但**比预期更糟**：

1. **dead-page 与 keytest 同分** —— 共享 probe 不监听 click/keydown 事件，纯键盘游戏与无 JS 死页面在五维上长得一模一样
2. **snake 反而比 dead-page 还低** —— canvas 元素的 getBoundingClientRect 与 viewport 比对疑似把"覆盖整个游戏区"当成"越界"，layout 维度被拉低。需要核对 GameEvaluator 的越界检测 JS 逻辑

## 根因诊断（待人工跟进）

1. **interactivity / completeness 全 0** = 已知代价（plan §背景表）。OK，不是惊喜
2. **snake layout 被拉低** = **新发现**。需要：
   - 看 `GameEvaluator.buildOutOfBoundsJs()` 究竟怎么判定越界
   - snake 的 canvas（600×600px 在 1024×768 viewport 内）按理不该算越界
   - 可能是新 harvestProbe 路径里 outOfBoundsElements 被错填了
3. **dead-page 偏高** = 五维公式特性。无错 + 无越界 + 教育 15 = 55，分数下限就是 50+，不是真正"低分"

## 当前结论

**任务不能直接 collapse 成"4/4 方向一致"**——鉴别力没了。

## 退路（按 plan §退路）

- 临时调 `AgentLoop.QUALITY_GATE_SCORE = 50`：让所有生成都能进 fix 循环且 50 分能过门禁
- 留独立任务 `260527-evaluator-keyboard-explore` 恢复评分准确度：
  - 共享 probe 加键盘探索分支（监听 click/keydown 计数）
  - 或 GameEvaluator 端用 page.evaluate 注册 click counter
  - 修 outOfBounds 误判（snake canvas 不应算越界）

## 用户需要做的

1. **跑一次 oracle self-test** 验证 Step 3 改造（需要 Chrome CDP 授权，我做不到）：
   ```bash
   ./scripts/playability-oracle-self-test.sh
   ```
   期望：keytest PASS、dead-page FAIL、snake-v0 PASS

2. **跑完整交叉验证**（需要 backend + browser-harness）：
   ```bash
   ./scripts/cross-verify.sh
   ```
   期望：4/4 oracle 方向一致（即使 evaluator 分数有偏差）

3. **决定退路**：
   - 接受当前 evalScore 偏差 + 调 QUALITY_GATE_SCORE = 50（短期）
   - 还是开新任务彻底修评分（中期）

## 已完成的部分

- shared/playability/ 共享 JS 库可在 Java + Python 双端注入
- GameEvaluator 接入零编译 / 启动错误
- oracle-driver.py 接入语法 OK（self-test 待用户跑）
- GameEvaluatorMain 命令行入口落地，给 cross-verify 用
- scripts/cross-verify.sh 交叉验证脚本落地

## 未完成的部分（卡点：Chrome CDP 授权）

- oracle self-test 跑通确认（PASS/FAIL 鉴别力）
- 4 fixture 真双评估器对比
- 决定 QUALITY_GATE_SCORE 是否调 50

这两件需要用户在自己 Chrome 上 click Allow 让 browser-harness daemon 起来。
