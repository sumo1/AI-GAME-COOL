# shared/playability

GameEvaluator（Java + Playwright）与 oracle（bash + browser-harness）共享的「可玩性判定底层」，纯 JS 零依赖。

任务 `docs/task/260522-evaluator-oracle-shared-core/` 抽出。两个模块都基于这一份 JS 跑信号采集 + pre-flight，各自加自己的评分 / verdict 逻辑。

## 文件

| 文件 | 暴露的全局 | 用途 |
|---|---|---|
| `playability-probe.js` | `window.__PLAYABILITY__` | 信号采集（canvas/numeric/bodyText）+ JS 错误 hook + 自然变化白名单 + 关键词差异 |
| `playability-driver.js` | `window.__PLAYABILITY_DRIVER__` | pre-flight 找开始按钮 + JS click 兜底 |

## API

### `window.__PLAYABILITY__`（probe）

```js
__PLAYABILITY__.collect()
// → { canvases:[{idx,hash}], numeric:[{path,val}], bodyText:string<=1500, bodyTextLen, bodyTextHash }

__PLAYABILITY__.getErrors()
// → [{ type:'error'|'unhandledrejection', msg, file, line, ts }]

__PLAYABILITY__.computeWhitelist(beforeSnapshot, afterSnapshot)
// → { autoCanvases:[idx], autoPaths:[cssPath] }
//   两次快照之间「自然变化」的元素，后续真实交互不应再算入信号

__PLAYABILITY__.hasNewKeyword(beforeText, afterText)
// → '游戏结束' | 'gameover' | ... | null
//   bodyText 中新出现的游戏关键词
```

### `window.__PLAYABILITY_DRIVER__`（driver）

```js
__PLAYABILITY_DRIVER__.findStartButton()
// → { x, y, txt } | null   坐标可直接给 Input.dispatchMouseEvent / click_at_xy

__PLAYABILITY_DRIVER__.clickByJS()
// → 命中的关键词 | null    直接 element.click()，绕过坐标命中盲区
```

## 接入

### Java（Playwright）

```java
import java.nio.file.Files;
import java.nio.file.Path;

String probeJs  = Files.readString(Path.of("../shared/playability/playability-probe.js"));
String driverJs = Files.readString(Path.of("../shared/playability/playability-driver.js"));

// 必须在 navigate 之前注册，确保业务 JS 之前执行
page.addInitScript(probeJs);
page.addInitScript(driverJs);

// 业务 JS 跑完后采集
Object snapshot = page.evaluate("window.__PLAYABILITY__.collect()");
```

### Python（browser-harness）

```python
from pathlib import Path
SHARED = Path("/Users/sumo/workplace/ai/AI-GAME/shared/playability")

new_tab(url)
wait_for_load()
js((SHARED / "playability-probe.js").read_text())
js((SHARED / "playability-driver.js").read_text())

snapshot = js("JSON.stringify(window.__PLAYABILITY__.collect())")
```

## 注入时机

**必须在业务 HTML JS 执行之前**——否则 `error` / `unhandledrejection` 事件会漏掉。Playwright 用 `page.addInitScript`（在 navigate 前注册），browser-harness 用 `safe_js` 在 `wait_for_load` 之后立即注入即可（页面 JS 还没真跑很多帧）。

## 防重复注入

两个文件开头都有：

```js
if (window.__PLAYABILITY__) return;
```

addInitScript 在每次 navigate 都会重跑，没有这一行会重复绑定 error listener。

## 不要做的事

- 不要往这里加 npm / 任何外部 lib（共享层零依赖是核心约束）
- 不要把 GameEvaluator / oracle 的评分 / verdict 逻辑塞进来（那些是上层应用各自的事）
- 不要修改 API 签名（一旦 Step 2/3 用上即冻结）

## 版本

- v1.0.0（2026-05-27）：初版，由 `260522-evaluator-oracle-shared-core` Step 1 抽出

未来若需要 break API：bump major + 在 `version` 字段反映；上层用 feature detect（`if (window.__PLAYABILITY__.version >= '2.0.0')`）。
