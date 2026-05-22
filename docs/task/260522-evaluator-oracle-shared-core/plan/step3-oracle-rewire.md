# Step 3：oracle 改造引用共享库

## 背景

oracle 当前的 `oracle-driver.py` 把信号采集 / pre-flight / 关键词识别都内联在 python 里。本 step 把这些挪到 shared/playability/，driver.py 改成"读 shared 文件 + 注入"。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `scripts/lib/oracle-driver.py`
  - `scripts/lib/oracle-verdict.sh`（仅 verdict 字段名对齐共享 API，不改判定逻辑）

- **不可改文件**：
  - `shared/playability/*.js`（Step 1 产出）
  - `scripts/playability-oracle.sh`（主入口不动）
  - `scripts/playability-oracle-self-test.sh`（自验脚本不动）
  - `test/fixtures/playability/*`（fixture 不动）
  - `game-agent-backend/*`（Step 2 才动）

### 产出清单

#### `oracle-driver.py` 改造

精确删/换：

**1. 删 INSTALL_ERROR_HOOK_JS、READ_ERRORS_JS、COLLECT_JS、FIND_START_BUTTON_JS 等内联 JS**——全部由共享库提供。

**2. 加 SHARED_JS 加载**

```python
SHARED_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'shared', 'playability')
PROBE_JS = open(os.path.join(SHARED_DIR, 'playability-probe.js'), encoding='utf-8').read()
DRIVER_JS = open(os.path.join(SHARED_DIR, 'playability-driver.js'), encoding='utf-8').read()
```

**3. main 流程改造**

```python
new_tab(url)
wait_for_load(timeout=15)

# 注入共享库（一次性 IIFE，可重复调用，内部防重复）
safe_js(PROBE_JS, "inject_probe")
safe_js(DRIVER_JS, "inject_driver")

# 截 baseline 屏
capture_screenshot(BASELINE_PNG)

# Pre-flight 找开始按钮 + 双重 click
btn_raw = safe_js("(() => { const b = window.__PLAYABILITY_DRIVER__.findStartButton(); return b ? JSON.stringify(b) : null; })()", "find_start")
start_btn_clicked = None
if btn_raw and btn_raw != 'null':
    btn = json.loads(btn_raw)
    click_at_xy(btn["x"], btn["y"])
    safe_js("window.__PLAYABILITY_DRIVER__.clickByJS()", "click_fallback")
    start_btn_clicked = btn
    wait(0.5)

# 自然变化采样
nat_baseline = json.loads(safe_js("JSON.stringify(window.__PLAYABILITY__.collect())", "nat_b"))
wait(1.0)
nat_after = json.loads(safe_js("JSON.stringify(window.__PLAYABILITY__.collect())", "nat_a"))
wl_raw = safe_js(f"JSON.stringify(window.__PLAYABILITY__.computeWhitelist({json.dumps(nat_baseline)}, {json.dumps(nat_after)}))", "wl")
whitelist = json.loads(wl_raw)

# 真正 baseline
baseline = json.loads(safe_js("JSON.stringify(window.__PLAYABILITY__.collect())", "baseline"))

# 驱动 48 次按键（与现状一致）
keys = ['ArrowRight','ArrowDown','d','s','ArrowLeft','ArrowUp','a','w'] * 3
keys = keys[:18] + ['ArrowRight'] * 30
for k in keys:
    press_key(k)
    wait(0.2)

# Final
capture_screenshot(AFTER_PNG)
final = json.loads(safe_js("JSON.stringify(window.__PLAYABILITY__.collect())", "final"))
js_errors = json.loads(safe_js("JSON.stringify(window.__PLAYABILITY__.getErrors())", "errs"))

# 写 result.json（字段名与现状对齐）
result = {
    "html_path": HTML_PATH,
    "url": url,
    "baseline": baseline,
    "final": final,
    "nat_baseline": nat_baseline,
    "nat_after": nat_after,
    "auto_changing_paths": whitelist["autoPaths"],
    "auto_changing_canvases": whitelist["autoCanvases"],
    "keys_pressed": keys,
    "start_button_clicked": start_btn_clicked,
    "js_errors": js_errors,
    "errors": []  # python 侧的 driver-level errors
}
```

#### `oracle-verdict.sh` 微调

只验字段对齐：现状的 result.json 字段名（`baseline / final / auto_changing_paths / auto_changing_canvases / js_errors`）必须保持不变——verdict.sh 不需要改。如果 step 3 改造后字段名有别名（共享库用 `autoPaths` 但 result.json 写 `auto_changing_paths`），要保证 verdict 读到的还是老字段名。

### 约束

- **不改 oracle.sh 主入口**——退出码语义不变
- **不改 verdict.sh 判定逻辑**——共享库的 hasNewKeyword / computeWhitelist 给 driver 用，verdict 仍按 result.json 走自己的判定
- **不改 result.json 字段名**——这是 verdict.sh 的契约
- **删除 `oracle-driver.py` 内的 INSTALL_ERROR_HOOK_JS / READ_ERRORS_JS / COLLECT_JS / FIND_START_BUTTON_JS** 字符串常量
- **驱动按键序列不变**（48 次：18 探索 + 30 ArrowRight）

### 复用模式

- 共享库注入 + JSON 转换参考 Step 2 的 GameEvaluator 改造（同样思路、不同语言）

### 依赖

- Step 1（shared 必须就绪）

## 【验收契约】

### 代码结构

- [ ] `oracle-driver.py` 顶部含 `PROBE_JS = open(..../playability-probe.js).read()`
- [ ] `oracle-driver.py` 不再含 `COLLECT_JS = ...`、`FIND_START_BUTTON_JS = ...` 等大字符串常量
- [ ] `safe_js(PROBE_JS, ...)` + `safe_js(DRIVER_JS, ...)` 在 new_tab 之后立即调用

### 命令验收

```bash
# 跑 self-test，三 fixture 鉴别力对症
./scripts/playability-oracle-self-test.sh
# 期望 exit 0, 输出含 "PASS: 2 / FAIL: 0"

# 跑真贪吃蛇
./scripts/playability-oracle.sh test/fixtures/playability/snake-v0.html
# 期望 exit 0 (PASS)
```

### 数据/字段验证

- [ ] result.json 字段名兼容老的（`auto_changing_paths` / `auto_changing_canvases` / `js_errors`）
- [ ] 字段值合理（baseline.canvases / numeric / bodyText 都有）
- [ ] 自然变化白名单计算结果与 Step 1 共享库版本一致

### 端到端 SSOT 验证

```bash
# self-test 全过 = 改造未破坏功能
./scripts/playability-oracle-self-test.sh && echo "✓ Step 3 完成"
```

### 剩余风险

- shared 路径写死（`../../shared/playability`）——cwd 变了找不到，需用绝对路径
- 大字符串拼接：safe_js 调用一次能传 200+ 行 JS 吗？需 Step 1 验过

## 后续 Step 依赖

Step 4 用本 step 改造后的 oracle + Step 2 改造后的 GameEvaluator 跨 fixture 对比。
