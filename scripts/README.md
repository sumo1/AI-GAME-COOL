# scripts/

工程类辅助脚本。本目录脚本都是独立工具，不依赖运行中的后端 / 前端。

---

## playability-oracle.sh

游戏可玩性自动判定 oracle —— 给一个 HTML 文件，判定它对键盘输入是否有可观测响应。

### 用法

```bash
./scripts/playability-oracle.sh path/to/game.html
```

### 退出码

| 码 | 含义 |
|----|------|
| 0  | PASS — 按键后页面有可观测变化（且不是自动动画） |
| 1  | FAIL — 48 次按键后页面无任何变化 |
| 2  | tooling error — 入参错误 / 文件不存在 / browser-harness 不可用 |

### 诊断包

每次运行写入 `/tmp/playability-oracle/run-{timestamp}/`，五件套：

| 文件 | 内容 |
|------|------|
| `game.html`                 | 被测 HTML 副本 |
| `screenshot-baseline.png`   | 加载 + 点击开始按钮后的基线截图 |
| `screenshot-after-keys.png` | 48 次按键后的截图 |
| `result.json`               | 完整信号采样（baseline / final / auto-changing 元素 / JS 错误） |
| `verdict.txt`               | 人类可读判定 + 详细对比 |

附带 `doctor.txt` / `driver-stdout.log` / `driver-stderr.log` 用于排查 browser-harness 异常。

### 工作原理

1. browser-harness 用 `file://` 加载 HTML
2. **Pre-flight**：扫 DOM 找含 "开始 / Start / 再来一局 / Restart" 等关键词的可点击元素，**坐标 click + JS .click() 双重兜底**（防 overlay/z-index 遮挡）
3. 静等 1 秒，记录"自动变化"的元素（timer / 动画），后续判定时排除它们
4. 采样 baseline 信号：所有 canvas 的像素 hash + 所有数字文本节点 + body innerText 长度/hash + body innerText 截断版（用于关键词检测）
5. 发 **48 次按键**（前 18 次混合方向键 + WASD 探索响应，后 30 次连按 ArrowRight 让蛇横穿棋盘撞墙触发 game over）
6. 采样 final 信号
7. **判定**（任一成立 → PASS）：
   - canvas hash 变化（且 canvas 不在自动变化白名单）
   - 数字文本变化（且不在白名单）
   - bodyText 长度变化 > 20 字符
   - bodyText 中出现新的"游戏关键词"（游戏结束 / game over / 失败 / 分数 / 再来 等）

### 已知约束

- 只跑 `file://`，不连本地 HTTP 服务（oracle 不依赖后端）
- 不调 LLM、不写 DB
- 48 次按键 + 200ms 间隔 ≈ 9.6 秒驱动期
- 每次跑都开新 tab，不复用现有标签页
- 不修改 browser-harness 本身

### 实现文件

- `scripts/playability-oracle.sh` — 主入口（入参校验 + 调度）
- `scripts/lib/oracle-driver.py` — 驱动核心（browser-harness 上下文）
- `scripts/lib/oracle-verdict.sh` — 判定 + 报告生成

---

## playability-oracle-self-test.sh

oracle 鉴别力自验。跑三个 fixture：keytest（应 PASS）、dead-page（应 FAIL）、snake-v0（不限）。

```bash
./scripts/playability-oracle-self-test.sh
```

退出 0 表示鉴别力对症（keytest 真 PASS、dead-page 真 FAIL）。

---

## snake-skill-multisample.sh

多样本 SKILL 演进有效性验证。给一个目录含若干 LLM 生成样本，跑 oracle 统计通过率。

```bash
./scripts/snake-skill-multisample.sh <samples-dir>
```

samples-dir 中放 `sample-*.html` 文件。脚本对每个跑 oracle，**通过率 ≥ 2/3** 即退出 0（演进有效）。

输出到 `/tmp/snake-multisample/run-{ts}/summary.md`。

### 用法示例

```bash
mkdir -p /tmp/my-samples
# 用 LLM 同一 prompt + 同一 SKILL 生成多个 HTML 写入 sample-r{N}-s{M}.html
./scripts/snake-skill-multisample.sh /tmp/my-samples
```
