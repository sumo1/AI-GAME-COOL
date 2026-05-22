# Playability Oracle Fixtures

| 文件 | 用途 | 期望 oracle verdict |
|---|---|---|
| `keytest.html` | 最小键盘响应页（按 ArrowRight/d 计数器加 1） | PASS |
| `dead-page.html` | 静态死页面（无 JS） | FAIL |
| `snake-v0.html` | 贪吃蛇 v0 占位（含典型 LLM 偏差，如键盘绑到 button） | 不确定 |

## snake-v0 来源

由 Claude Opus 4.7 子 agent 生成（2026-05-22），prompt：
"做一个简单的贪吃蛇游戏，4-8岁玩"。

由于 DashScope free tier 当夜耗尽（403 AllocationQuota.FreeTierOnly），
未走 AgentLoop 实际链路，但 prompt 与 V2 系统等价。
文件大小 8632 字节（303 行），含：
- `<canvas>` 渲染
- `keydown` 监听（方向键 + WASD + 空格暂停）
- `setInterval` 180ms tick
- 屏幕方向 pad（手机兼容）

## 自动 setInterval 注意

很多贪吃蛇 v0 在加载后会自动移动（即使不按键），oracle 的 baseline
1 秒自然变化采样会把这个动作记入白名单，验证时排除——
所以即使按键不生效，oracle 也应判 FAIL（被动动画排除后无变化）。
