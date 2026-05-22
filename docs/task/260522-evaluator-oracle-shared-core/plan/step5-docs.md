# Step 5：文档收尾 + push

## 背景

抽公共底层完成 + 交叉验证通过，写文档让别人能用 + 让后续任务能基于此扩展。

## 【实现契约】

### 范围

- **可改文件**：
  - `shared/playability/README.md`（Step 1 已写骨架，本 step 写完整）
  - `docs/engineering/conventions.md`（新增 §15 共享 JS 库章节）
  - `docs/engineering/testing.md §1.5`（在末尾加共享底层说明）
  - `docs/task/260522-evaluator-oracle-shared-core/progress.md`（5 step 全标完 + commit hash）
  - `docs/task/260522-evaluator-oracle-shared-core/memory/SUMMARY.md`（dreamer 风格汇总，可选）

- **不可改文件**：实现层代码（不再改）

### 产出清单

#### `shared/playability/README.md`（完整版）

```markdown
# shared/playability/

通用游戏可玩性判定的共享 JS 核心。GameEvaluator 与 oracle 都基于此构建。

## 设计哲学

一份判定基础设施 + 两个上层评估器：

```
                    shared/playability/
                    ├── playability-probe.js    (信号采集 + 错误 hook + 白名单)
                    ├── playability-driver.js   (pre-flight 找开始按钮)
                    └── README.md
                              ↓                ↓
                    GameEvaluator          oracle
                    (Java + Playwright)    (bash + browser-harness)
                    + 多维评分              + PASS/FAIL 判定
                    服务 LLM 迭代           服务用户/SKILL 演进
```

## API

注入后：
- `window.__PLAYABILITY__.collect()`         → 三类信号快照
- `window.__PLAYABILITY__.getErrors()`       → JS 错误列表
- `window.__PLAYABILITY__.computeWhitelist(b, a)` → 自然变化白名单
- `window.__PLAYABILITY__.hasNewKeyword(b, f)`    → bodyText 关键词差异
- `window.__PLAYABILITY_DRIVER__.findStartButton()`  → {x, y, txt} | null
- `window.__PLAYABILITY_DRIVER__.clickByJS()`         → 找元素并 .click() 兜底

## 接入方式

### Java（Playwright）

```java
String probeJs = Files.readString(Path.of("shared/playability/playability-probe.js"));
String driverJs = Files.readString(Path.of("shared/playability/playability-driver.js"));

context.addInitScript(probeJs);
context.addInitScript(driverJs);

Page page = context.newPage();
page.navigate("file://" + htmlFile);
// ...

Object signals = page.evaluate("() => JSON.stringify(window.__PLAYABILITY__.collect())");
```

### Python（browser-harness）

```python
PROBE_JS = open('shared/playability/playability-probe.js').read()
DRIVER_JS = open('shared/playability/playability-driver.js').read()

new_tab(url)
wait_for_load()
safe_js(PROBE_JS, "inject_probe")
safe_js(DRIVER_JS, "inject_driver")

signals = json.loads(safe_js("JSON.stringify(window.__PLAYABILITY__.collect())", "collect"))
```

## 注入时机

**必须在业务 HTML JS 执行之前**：
- Java：用 `BrowserContext.addInitScript`（不是 page.evaluate）
- Python：在 `new_tab(url)` 之后立刻 `safe_js(PROBE_JS)`，不要等 wait_for_load 后才注入

否则 LLM 业务 JS 先跑了，错误 hook 没装上 → 早期错误丢失。

## 防重复注入

probe.js 和 driver.js 开头都有 `if (window.__X__) return;` 防御。Playwright addInitScript 在 navigate 时多次调用是安全的。

## 严格约束

- **零项目依赖**：纯 JS，不引用任何 Java/Python/外部 lib（需要 hash 自己写 simpleHash）
- **API 暴露后是契约**：window.__PLAYABILITY__ 与 __PLAYABILITY_DRIVER__ 的方法签名锁死
- **不主动监听事件**（click/keydown）：本库职责是"采集瞬时信号"，事件流让上层评估器自己处理

## 不适用场景

- 拖拽类游戏（无鼠标 drag 驱动）
- 多模态（语音 / 摄像头）
- 需登录的游戏（不会 fill credentials）

后续 follow-up：
- `260522-oracle-extend-click-drag`：扩展点击 / 拖拽驱动到共享层
- `260522-evaluator-keyboard-explore`：GameEvaluator 加键盘探索（共享底层完成后变容易）
```

#### `docs/engineering/conventions.md` 新增章节

加 §15：

```markdown
## 15. 共享判定基础设施

shared/playability/ 是 GameEvaluator + oracle 共享的判定核心。详见 `shared/playability/README.md`。

设计原则：
- 纯 JS、零项目依赖
- 一份信号采集 + Pre-flight 实现，两个上层评估器各自做评分 / PASS/FAIL 判定
- 严禁引用项目代码（Java / Python / Spring / browser-harness）

修改 shared/playability/* 时必须**两边同步验证**：
- 跑 GameEvaluatorMain 看 totalScore 是否合理
- 跑 oracle self-test 看 keytest PASS / dead-page FAIL
```

#### `docs/engineering/testing.md §1.5` 末尾追加

```markdown
**共享底层**：`shared/playability/`（任务 260522-evaluator-oracle-shared-core 引入）—— GameEvaluator 与 oracle 共享的判定 JS 核心。**信号采集 / Pre-flight / 自然变化白名单**逻辑只有一份。修改 shared 必须两端同步验证。
```

#### `progress.md` 全标完

7 step 全部 [x] + 加最终 commit hash + 决策表。

#### `memory/SUMMARY.md`（dreamer 风格）

按主题聚类：
- 主题 1：共享层 API 设计的取舍（为什么是两个全局对象、为什么 collect 不主动监听事件）
- 主题 2：注入时机的踩坑（Java addInitScript vs page.evaluate 的差异）
- 主题 3：评分系统性下降的退路（events 暂空 → QUALITY_GATE_SCORE 调整）

### 约束

- README ≤ 80 行
- conventions §15 ≤ 30 行
- testing §1.5 末尾追加 ≤ 5 行
- 不动 shared/* 实现

### 依赖

- Step 1-4 全部完成

## 【验收契约】

### 文档存在性

```bash
[ -f shared/playability/README.md ] || exit 1
grep -q "## 15" docs/engineering/conventions.md
grep -q "shared/playability" docs/engineering/testing.md

# progress 5 step 全标完
grep -c '^\d\. \[x\]' docs/task/260522-evaluator-oracle-shared-core/progress.md
# 应输出 5
```

### 任务收口

push 后任务关闭。下一个任务（evaluator-keyboard-explore 或 oracle-extend-click-drag）可独立启动。
