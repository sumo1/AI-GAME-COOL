# Step 1：oracle 核心库

## 背景

实现 `scripts/playability-oracle.sh`——独立 bash 工具，输入 HTML 文件路径，输出 PASS/FAIL + 完整诊断包。这是后续所有可玩性验证的基础设施。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件（可新增）**：
  - `scripts/playability-oracle.sh`（新建，主脚本）
  - `scripts/lib/oracle-driver.py`（新建，browser-harness 驱动逻辑，python）
  - `scripts/lib/oracle-verdict.sh`（新建，verdict 计算）
  - `scripts/README.md`（新建，oracle 用法说明，本 step 写骨架，Step 4 写完整）

- **不可改文件**：
  - 任何 Java / TS 代码
  - `pom.xml` / `package.json` / `application.yml`
  - 任何 `agents/` / `docs/engineering/` / `docs/knowledge/` 文档（Step 4 才动）
  - 任何现有 `scripts/` 下的脚本（如有）

### 产出清单

#### `scripts/playability-oracle.sh`（主入口）

```bash
#!/usr/bin/env bash
# 用法：./scripts/playability-oracle.sh <html-path>
# 输出：
#   stdout: PASS / FAIL + 一句话理由
#   exit code: 0=PASS, 1=FAIL, 2=tooling error (browser-harness 挂、文件不存在等)
#   /tmp/playability-oracle/run-{ts}/: 完整诊断包
```

主流程：
1. 入参校验（HTML 文件存在 + 是 .html 扩展名）
2. 创建 `/tmp/playability-oracle/run-{ts}/`
3. 拷贝 HTML 到 `run-{ts}/game.html`
4. browser-harness `--doctor` 自检；daemon 死则 `--setup`
5. 调用 `oracle-driver.py` 跑测试，输出 `result.json` 到 run dir
6. 调用 `oracle-verdict.sh` 读 result.json 判定
7. 输出 verdict.txt + 退出码

#### `scripts/lib/oracle-driver.py`（browser-harness 驱动核心）

通过 `browser-harness -c "<python code>"` 调用，主要职责：

```python
# 伪代码 - 实际是嵌入到 -c 字符串里
1. new_tab('file:///<html-path>')
2. wait_for_load(timeout=15)
3. capture_screenshot('<run-dir>/screenshot-baseline.png')
4. # Pre-flight: 找开始按钮
   start_btn = js("""
     (() => {
       const candidates = ['开始', '开始游戏', 'Start', 'Play', 'GO', '点击开始'];
       for (const txt of candidates) {
         const el = [...document.querySelectorAll('button, a, [role=button], div')]
           .find(e => (e.textContent||'').trim() === txt);
         if (el) {
           const r = e.getBoundingClientRect();
           return JSON.stringify({x: r.x + r.width/2, y: r.y + r.height/2, txt});
         }
       }
       return null;
     })()
   """)
   if start_btn: click_at_xy(...)
5. # Baseline 自然变化采样：1 秒内不发任何按键，看页面有什么自动变
   nat_baseline = collect_signals()
   wait(1.0)
   nat_after = collect_signals()
   auto_changing_ids = diff(nat_baseline, nat_after)  # 排除自动跳的元素
6. # 真正 baseline
   baseline = collect_signals()
7. # 驱动：30 次按键，WASD + 方向键交替
   keys = ['ArrowRight','ArrowDown','d','s','ArrowLeft','ArrowUp','a','w'] * 4
   for k in keys[:30]:
     press_key(k); wait(0.2)
8. # Final
   capture_screenshot('<run-dir>/screenshot-after-keys.png')
   final = collect_signals()
9. # 写 result.json:
   {
     baseline: {...},
     final: {...},
     auto_changing_ids: [...],
     keys_pressed: [...],
     errors: [...]  # browser-harness 异常 / js 异常
   }
```

`collect_signals()` 收集三类信号：

```python
js("""
  (() => {
    // 1. 所有 canvas 的 hash（取 toDataURL 末尾 40 字符，足以检测变化）
    const canvases = [...document.querySelectorAll('canvas')]
      .map((c, i) => ({idx: i, hash: c.toDataURL().slice(-40)}));

    // 2. 纯数字文本元素（可能是分数/计数/长度）
    const numeric = [];
    for (const el of document.querySelectorAll('*')) {
      if (el.children.length > 0) continue;  // 只看叶子节点
      const t = (el.textContent || '').trim();
      if (/^-?\\d+\\.?\\d*$/.test(t) && t.length < 8) {
        numeric.push({
          path: cssPath(el),  // 简化的选择器路径
          val: t
        });
      }
    }

    // 3. body innerText 总长度 + hash（防止结构大变）
    const bodyText = document.body.innerText || '';
    return JSON.stringify({
      canvases,
      numeric,
      bodyTextLen: bodyText.length,
      bodyTextHash: simpleHash(bodyText.slice(0, 5000))
    });
  })()
""")
```

#### `scripts/lib/oracle-verdict.sh`（判定计算）

读 result.json，按"严格判定"输出 verdict：

```bash
# 输入 result.json
# 输出：
#   verdict.txt：PASS / FAIL + 详细理由
#   exit code

判定规则（任一信号成立即"有响应"，但必须排除 auto_changing_ids）：
  changes = []
  - canvas hash 变了的（baseline 与 final 比对）→ changes += "canvas[i] 像素变了"
  - 数字文本变了的（path 在 auto_changing_ids 中则跳过）→ changes += "数字 path=X 从 a 变成 b"
  - bodyTextLen 差异 > 50 字符 OR bodyTextHash 变了 → changes += "DOM innerText 显著变化"

  如果 changes 非空 → PASS
  如果 changes 空 + errors 非空 → FAIL（理由：有 JS 错误）
  否则 → FAIL（理由：键盘按了 30 次画面无任何变化）

verdict.txt 格式：
  ===== Playability Oracle Verdict =====
  Result: PASS / FAIL
  HTML: <path>
  Run dir: /tmp/playability-oracle/run-{ts}/

  Signals after 30 keypresses:
    - canvas[0] pixel hash: <baseline hash> → <final hash>  [CHANGED / unchanged]
    - numeric path "div.score": "0" → "5"  [CHANGED]
    - numeric path "span.timer": "10" → "5"  [auto-changing, ignored]
    - bodyText length: 234 → 245 chars  [+11]
    - JS errors during test: 0

  Detected changes (caused by keypress, not auto-animation):
    - canvas[0] hash differs
    - numeric path "div.score" went 0 → 5

  Verdict reason: PASS — at least one keypress-caused change detected
```

#### `scripts/README.md`（骨架，Step 4 写完整）

包含：
- 一句话 oracle 是什么
- 如何调用 `./playability-oracle.sh path/to/game.html`
- 退出码含义
- 诊断包目录结构

### 约束（已冻结的边界）

- **不引入新依赖**：不用 npm install / pip install / brew install。所有功能用 bash + browser-harness 已有的 helpers + python3 标准库
- **browser-harness 只通过 `-c "<python>"` 调用**（不能 heredoc，会被 stdin 吞——见 testing.md §3 工具速查）
- **不修改 browser-harness 本身**——它是 ~/Developer/browser-harness 下的独立工具
- **不依赖运行中的 backend / frontend**：oracle 直接用 file:// 加载 HTML，不通过 GamePreview / 不通过 5173
- **诊断包必须每次 run 写到独立 ts 目录**：避免覆盖、便于回溯

### 复用的现有模式

- browser-harness helpers：`new_tab` / `press_key` / `js` / `capture_screenshot` / `wait_for_load`
- bash 脚本风格参考 `start.sh` / `quick-start.sh`
- 诊断包思路参考之前任务 step3 的端到端脚本

### 依赖的前置子任务

无（本 step 是首步）。

## 【验收契约（Evaluator 输入）】

> **节奏说明**：本 step 不强制独立 evaluator 复跑（按 `docs/engineering/testing.md §1.4` 任务收口才重验，本任务的"收口"是 Step 3 自验）。本契约主要给 coder 自验用。

### 代码结构验证

- [ ] `scripts/playability-oracle.sh` 存在且 chmod +x
- [ ] `scripts/lib/oracle-driver.py` 存在
- [ ] `scripts/lib/oracle-verdict.sh` 存在且 chmod +x
- [ ] `scripts/README.md` 存在（哪怕只有骨架）
- [ ] 主脚本入参校验完整：缺参 / 文件不存在 / 不是 .html → exit 2

### 命令验收（coder 自验）

| 命令 | 通过标准 |
|------|---------|
| `bash -n scripts/playability-oracle.sh` | 语法正确 |
| `python3 -c "$(cat scripts/lib/oracle-driver.py)"` 不报语法错 | py 语法正确（注：实际不能这么直接跑因为缺 browser-harness 上下文，仅校验语法） |
| `./scripts/playability-oracle.sh /tmp/non-existent.html; echo $?` | exit 2 |
| `./scripts/playability-oracle.sh README.md; echo $?` | exit 2（非 .html） |

### 自验：跑一个简单 fixture

写一个最小的"按键改 DOM"HTML（参考之前 keytest）作为临时 fixture：
```html
<!DOCTYPE html><html><body><div id="counter">0</div>
<script>let n=0;document.addEventListener('keydown',e=>{
  if(e.key==='ArrowRight'){n++;document.getElementById('counter').innerText=n;}
});</script></body></html>
```

跑 `./scripts/playability-oracle.sh /tmp/keytest.html`：
- exit code = 0
- verdict.txt 含 "PASS"
- run-{ts}/ 下含：game.html / screenshot-baseline.png / screenshot-after-keys.png / result.json / verdict.txt

### 自验：跑一个静态死页面

写一个无任何 JS 的死页面：
```html
<!DOCTYPE html><html><body><h1>静态页面</h1></body></html>
```

跑 `./scripts/playability-oracle.sh /tmp/dead.html`：
- exit code = 1
- verdict.txt 含 "FAIL"
- 理由含 "无任何变化" / "no changes"

### 端到端 SSOT 验证（仅 coder 自验，不强制 evaluator 复跑）

把上面两个自验串起来跑，覆盖 oracle 鉴别力：
```bash
# PASS case
./scripts/playability-oracle.sh /tmp/keytest.html
[ $? -eq 0 ] || { echo "FAIL: keytest 应当 PASS"; exit 1; }

# FAIL case
./scripts/playability-oracle.sh /tmp/dead.html
[ $? -eq 1 ] || { echo "FAIL: dead.html 应当 FAIL"; exit 1; }

echo "Step 1 自验通过：PASS/FAIL 鉴别力都对"
```

### 剩余风险

- 未在真贪吃蛇上验证（留 Step 3）
- canvas hash 用 toDataURL 末尾 40 字符可能有极小碰撞概率（10^-24 量级，忽略）
- 通用 numeric 扫描可能漏掉 SVG `<text>` 节点（多数游戏不用 SVG，先不处理）

## 后续 Step 依赖

Step 3 自验需要本 step 的 oracle 工作正常。
