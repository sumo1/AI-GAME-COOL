# Step 1：抽 shared/playability/ 共享 JS 核心

## 背景

把"模拟交互 + 信号采集 + 自然变化白名单"——目前在 `game-agent-backend/src/main/resources/probe/game-probe.js` 和 `scripts/lib/oracle-driver.py` 内联 JS 各有一份——抽成一份纯 JS 库，给 GameEvaluator + oracle 共享。

设计原则：**纯 JS 零项目依赖**，能在浏览器（Playwright Chromium / 用户 Chrome）里跑；不引入 npm 包；通过 `window.__PLAYABILITY__` 暴露 API。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件（新建）**：
  - `shared/playability/playability-probe.js` — 信号采集 + 错误 hook + 白名单计算
  - `shared/playability/playability-driver.js` — pre-flight 找开始按钮 + 探索策略
  - `shared/playability/README.md` — 库的接入契约 + 示例

- **不可改文件**：
  - 任何 Java 代码（Step 2 才动）
  - 任何 Python / bash 脚本（Step 3 才动）
  - `game-agent-backend/src/main/resources/probe/game-probe.js`（保留作 v1 兼容）
  - `scripts/lib/oracle-driver.py`（Step 3 改）
  - 任何 SKILL.md

### 产出清单

#### `shared/playability/playability-probe.js`（约 200 行）

```js
/**
 * Playability Probe — 浏览器内信号采集
 *
 * 暴露 window.__PLAYABILITY__：
 *   - collect()                → 当前快照 {canvases, numeric, bodyText, bodyTextLen, bodyTextHash}
 *   - getErrors()              → JS 错误列表
 *   - computeWhitelist(b, a)   → 自然变化白名单计算
 *   - hasNewKeyword(b, f)      → bodyText 关键词差异检测
 *
 * 没有 Java / python / shell 依赖。一次性注入到页面（addInitScript / safe_js）。
 */
(function() {
  'use strict';
  if (window.__PLAYABILITY__) return; // 防重复注入

  // ===== 1. 错误捕获 hook =====
  const errors = [];
  window.addEventListener('error', e => {
    errors.push({
      type: 'error',
      msg: e.message || 'unknown',
      file: e.filename || '',
      line: e.lineno || 0,
      ts: Date.now()
    });
  });
  window.addEventListener('unhandledrejection', e => {
    errors.push({
      type: 'unhandledrejection',
      msg: String((e.reason && (e.reason.message || e.reason)) || ''),
      ts: Date.now()
    });
  });

  // ===== 2. 简单 hash（避免外部 lib）=====
  function simpleHash(s) {
    let h = 0;
    for (let i = 0; i < s.length; i++) {
      h = ((h << 5) - h + s.charCodeAt(i)) | 0;
    }
    return String(h);
  }

  // ===== 3. CSS path（用于 numeric 节点定位）=====
  function cssPath(el) {
    if (!el) return '';
    const tag = el.tagName ? el.tagName.toLowerCase() : '?';
    const id = el.id ? '#' + el.id : '';
    const cls = (typeof el.className === 'string' && el.className.trim())
      ? '.' + el.className.trim().split(/\s+/)[0]
      : '';
    return tag + id + cls;
  }

  // ===== 4. collect — 三类信号采集 =====
  function collect() {
    // canvas hashes（取 toDataURL 末尾 40 字符即够鉴别变化）
    const canvases = [...document.querySelectorAll('canvas')].map((c, idx) => {
      let hash = '';
      try { hash = c.toDataURL().slice(-40); } catch (e) {}
      return { idx, hash };
    });

    // 数字文本节点（包含分数、计时器、长度等）
    const numeric = [];
    const all = document.querySelectorAll('*');
    for (let i = 0; i < all.length && numeric.length < 50; i++) {
      const el = all[i];
      if (el.children.length > 0) continue;
      const t = (el.textContent || '').trim();
      if (/^-?\d+\.?\d*$/.test(t) && t.length < 8) {
        numeric.push({ path: cssPath(el), val: t });
      }
    }

    // bodyText（截断 1500 字符给关键词识别 + hash 给变化检测）
    const bodyText = ((document.body && document.body.innerText) || '');
    return {
      canvases,
      numeric,
      bodyText: bodyText.slice(0, 1500),
      bodyTextLen: bodyText.length,
      bodyTextHash: simpleHash(bodyText.slice(0, 5000))
    };
  }

  // ===== 5. 自然变化白名单 =====
  function computeWhitelist(before, after) {
    const autoCanvases = [];
    const autoPaths = [];
    if (!before || !after) return { autoCanvases, autoPaths };
    const beforeCnv = Object.fromEntries((before.canvases || []).map(c => [c.idx, c.hash]));
    for (const c of (after.canvases || [])) {
      if (beforeCnv[c.idx] !== c.hash) autoCanvases.push(c.idx);
    }
    const beforeNum = Object.fromEntries((before.numeric || []).map(n => [n.path, n.val]));
    for (const n of (after.numeric || [])) {
      if (beforeNum[n.path] !== n.val) autoPaths.push(n.path);
    }
    return { autoCanvases, autoPaths };
  }

  // ===== 6. game over / 分数等关键词识别 =====
  const GAME_KEYWORDS = [
    '游戏结束', 'game over', 'gameover',
    '失败', 'you win', '获胜',
    '分数', '得分',
    '再来', 'play again', 'restart'
  ];

  function hasNewKeyword(beforeText, afterText) {
    const b = (beforeText || '').toLowerCase();
    const f = (afterText || '').toLowerCase();
    return GAME_KEYWORDS.find(kw => f.includes(kw) && !b.includes(kw)) || null;
  }

  // ===== 暴露 =====
  window.__PLAYABILITY__ = {
    collect,
    getErrors: () => errors.slice(),
    computeWhitelist,
    hasNewKeyword,
    // 元数据，便于上层判断版本兼容
    version: '1.0.0'
  };
})();
```

#### `shared/playability/playability-driver.js`（约 100 行）

```js
/**
 * Playability Driver — pre-flight 找开始按钮 + 双重 click 兜底
 *
 * 暴露 window.__PLAYABILITY_DRIVER__：
 *   - findStartButton()          → {x, y, txt} | null
 *   - clickByJS(txt[])           → 找到含关键词的元素并 .click()
 *
 * 不绑 specific 游戏类型，纯通用键盘游戏 / 点击启动游戏的 pre-flight。
 */
(function() {
  'use strict';
  if (window.__PLAYABILITY_DRIVER__) return;

  const START_KEYWORDS = [
    '开始', '开始游戏', '点击开始', '开 始',
    'Start', 'Play', 'GO', 'START', 'PLAY',
    '再来一局', '再来一次', '再玩一次', '重新开始',
    'Retry', 'Restart', 'Replay'
  ];

  const SELECTOR_TAGS = ['button', 'a', '[role=button]', 'div', 'span'];

  function findStartButton() {
    const all = [...document.querySelectorAll(SELECTOR_TAGS.join(','))];
    // 精确匹配优先
    for (const txt of START_KEYWORDS) {
      const el = all.find(e => ((e.textContent || '').trim() === txt));
      if (el) {
        const r = el.getBoundingClientRect();
        if (r.width > 0 && r.height > 0) {
          return { x: r.x + r.width / 2, y: r.y + r.height / 2, txt };
        }
      }
    }
    // 包含匹配兜底
    for (const txt of START_KEYWORDS) {
      const el = all.find(e => {
        const t = (e.textContent || '').trim();
        return t.length < 30 && t.includes(txt);
      });
      if (el) {
        const r = el.getBoundingClientRect();
        if (r.width > 0 && r.height > 0) {
          return { x: r.x + r.width / 2, y: r.y + r.height / 2, txt };
        }
      }
    }
    return null;
  }

  function clickByJS() {
    const all = [...document.querySelectorAll(SELECTOR_TAGS.join(','))];
    for (const txt of START_KEYWORDS) {
      const el = all.find(e => {
        const t = (e.textContent || '').trim();
        return t === txt || (t.length < 30 && t.includes(txt));
      });
      if (el && el.click) {
        el.click();
        return txt;
      }
    }
    return null;
  }

  window.__PLAYABILITY_DRIVER__ = {
    findStartButton,
    clickByJS,
    startKeywords: START_KEYWORDS.slice(),
    version: '1.0.0'
  };
})();
```

#### `shared/playability/README.md`

简短 + 可执行：
- 一句话定位
- 暴露的 window.__PLAYABILITY__ / __PLAYABILITY_DRIVER__ API
- Java（Playwright）接入：`page.addInitScript(Files.readString(Path.of(...)))`
- Python（browser-harness）接入：`safe_js(open(SHARED_PATH).read(), "inject")`
- 注入时机：必须在业务 HTML JS 执行**之前**

### 约束（已冻结）

- **零项目依赖**：纯 JS、不能引用 Java / Python / 任何外部 lib（需要 hash 就自己写 simpleHash）
- **不破坏老 game-probe.js**：本 step 不删，作 v1 兼容备份
- **window.__PLAYABILITY__ 与 window.__PLAYABILITY_DRIVER__ 两个全局**：一个采集器、一个驱动器，分开命名避免与老 `__GAME_PROBE__` 冲突
- **防重复注入**：开头检查 `if (window.__PLAYABILITY__) return;`（addInitScript 在 navigate 时多次调用是常见场景）
- **API 暴露后不许换签名**：一旦 Step 2/3 用上，本库的 API 就是契约
- **代码 ≤ 350 行**（probe ≤ 200 + driver ≤ 100 + 注释 ≤ 50）：超过说明分得不清

### 复用现有模式

- 信号采集逻辑参考 `scripts/lib/oracle-driver.py` 第 75-90 行（COLLECT_JS）
- 错误 hook 参考 `game-agent-backend/src/main/resources/probe/game-probe.js` 第 31-50 行
- pre-flight 找按钮参考 `oracle-driver.py` 第 105-135 行（FIND_START_BUTTON_JS）

### 依赖

无（首步）

## 【验收契约（Evaluator 输入）】

### 文件结构验证

- [ ] `shared/playability/playability-probe.js` 存在
- [ ] `shared/playability/playability-driver.js` 存在
- [ ] `shared/playability/README.md` 存在
- [ ] probe.js 防重复注入逻辑：`grep -q "if (window.__PLAYABILITY__) return" shared/playability/playability-probe.js`
- [ ] probe.js 暴露 collect / getErrors / computeWhitelist / hasNewKeyword 四个 API
- [ ] driver.js 暴露 findStartButton / clickByJS 两个 API

### 命令验收（coder 自验，不强制独立 evaluator）

```bash
# JS 语法
node -e "$(cat shared/playability/playability-probe.js)" 2>&1 | head -5
# 应没有 SyntaxError；运行时没 window 会抛 ReferenceError 是正常的

# 浏览器内加载验证
browser-harness -c "
new_tab('about:blank')
wait_for_load()
import os
js(open('/Users/sumo/workplace/ai/AI-GAME/shared/playability/playability-probe.js').read())
js(open('/Users/sumo/workplace/ai/AI-GAME/shared/playability/playability-driver.js').read())
print('probe API:', js('Object.keys(window.__PLAYABILITY__).sort().join(\",\")'))
print('driver API:', js('Object.keys(window.__PLAYABILITY_DRIVER__).sort().join(\",\")'))
"
# 应输出含 collect/computeWhitelist/getErrors/hasNewKeyword 和 findStartButton/clickByJS

# 防重复注入
browser-harness -c "
new_tab('about:blank')
wait_for_load()
import os
script = open('/Users/sumo/workplace/ai/AI-GAME/shared/playability/playability-probe.js').read()
js(script)
v1 = js('window.__PLAYABILITY__.version')
js(script)  # 第二次注入
v2 = js('window.__PLAYABILITY__.version')
print('防重复:', v1 == v2)  # True 且 1.0.0
"
```

### 数据/字段验证

- [ ] probe.collect() 返回 5 个 key：canvases / numeric / bodyText / bodyTextLen / bodyTextHash
- [ ] probe.computeWhitelist(null, null) 返回 `{autoCanvases: [], autoPaths: []}`（不抛）
- [ ] driver.findStartButton() 在 about:blank 返回 null（不抛）
- [ ] hasNewKeyword('hello', 'hello game over') 返回 'game over'

### 端到端 SSOT 验证（轻量自验）

```bash
# 在真贪吃蛇上测三类信号采集
browser-harness -c "
import os
new_tab('file:///Users/sumo/workplace/ai/AI-GAME/test/fixtures/playability/snake-v0.html')
wait_for_load()
wait(0.5)
js(open('/Users/sumo/workplace/ai/AI-GAME/shared/playability/playability-probe.js').read())
import json
sig = json.loads(js('JSON.stringify(window.__PLAYABILITY__.collect())'))
print('canvases:', len(sig['canvases']))
print('numeric:', len(sig['numeric']))
print('bodyTextLen:', sig['bodyTextLen'])
"
# 期望：canvases ≥ 1, numeric ≥ 1, bodyTextLen > 30
```

### 剩余风险

- 注入时机问题留 Step 2/3 处理
- 与老 game-probe.js 的并存策略留 Step 2 决策

## 后续 Step 依赖

Step 2 / 3 都直接读这两个 JS 文件 + 注入到各自的浏览器执行环境。
