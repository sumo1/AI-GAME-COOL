# Step 2：GameEvaluator 改造引用共享库

## 背景

GameEvaluator 当前用自己的 `game-probe.js`（注入 HTML head）+ Java 端的 `simulateInteractions`（只点击）。本 step 把信号采集换成共享 probe（保留五维评分公式不动），把找开始按钮换成共享 driver。**多维评分逻辑不动**，只换底层。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `game-agent-backend/src/main/java/com/sumo/agent/agent/evaluation/GameEvaluator.java`
  - `game-agent-backend/src/main/resources/probe/playability-loader.js`（**新建**——把 shared/playability/*.js 在 build 时复制过来的产物，本 step 用一个 maven-resources-plugin 做事或人肉 cp）
  - `game-agent-backend/src/main/resources/probe/`（拷贝 shared/playability/*.js 进来）

- **不可改文件**：
  - `agent/loop/*` / `agent/tools/*` / `agent/skill/*` / `infra/*` / `api/*`
  - `pom.xml`（**不引入新插件**——用 `Files.copy` 或 `@PostConstruct` 加载）
  - `agent/evaluation/ProbeReport.java`（数据结构稳定）
  - 五维评分公式（GameEvaluator.computeScores 不动）
  - 老 `game-probe.js`（保留作 v1 兼容备份，本任务不删但不再注入）
  - shared/playability/*.js（Step 1 产出，稳定）

### 产出清单

#### `GameEvaluator.java` 改造（精确 4 处）

**1. 静态加载共享 JS**（替换 init() 里的 game-probe.js 加载）

```java
private String playabilityProbeJs;
private String playabilityDriverJs;

@PostConstruct
public void init() throws IOException {
    // 直接读 ../shared/playability/*.js（项目根的相对路径）
    Path projectRoot = findProjectRoot();
    playabilityProbeJs = Files.readString(
        projectRoot.resolve("shared/playability/playability-probe.js"),
        StandardCharsets.UTF_8);
    playabilityDriverJs = Files.readString(
        projectRoot.resolve("shared/playability/playability-driver.js"),
        StandardCharsets.UTF_8);
    log.info("Playability shared JS 加载完成 ({} + {} chars)",
        playabilityProbeJs.length(), playabilityDriverJs.length());

    // 老的 game-probe.js 不再加载（保留文件兼容）
}

private Path findProjectRoot() {
    Path p = Path.of(System.getProperty("user.dir"));
    // mvn spring-boot:run cwd 是 game-agent-backend，向上找一级即项目根
    while (p != null && !Files.exists(p.resolve("shared/playability"))) {
        p = p.getParent();
    }
    if (p == null) throw new IllegalStateException("找不到 shared/playability/ 目录");
    return p;
}
```

**2. injectProbe 改造**（不再用替换 head 的方式注入 game-probe，改成在浏览器 navigate 之后用 evaluate 注入；这块原有 injectProbe 方法可以删）

注入时机改为 `runInBrowser` 里 `page.navigate()` **之前**用 `page.addInitScript()`：

```java
private ProbeReport runInBrowser(Path htmlFile) throws Exception {
    try (Playwright playwright = Playwright.create()) {
        Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setViewportSize(1024, 768));

        // 注入共享 probe + driver（在所有业务 JS 之前执行）
        context.addInitScript(playabilityProbeJs);
        context.addInitScript(playabilityDriverJs);

        Page page = context.newPage();
        page.navigate("file://" + htmlFile.toAbsolutePath());
        page.waitForLoadState(LoadState.NETWORKIDLE,
                new Page.WaitForLoadStateOptions().setTimeout(PAGE_LOAD_TIMEOUT_MS));
        // ...

        // 收割 probe 数据：调共享 collect()
        Object probeRaw = page.evaluate("() => window.__PLAYABILITY__ && JSON.stringify(window.__PLAYABILITY__.collect())");
        Object errorsRaw = page.evaluate("() => JSON.stringify(window.__PLAYABILITY__.getErrors())");
        // 转 ProbeReport（见下）
    }
}
```

**3. simulateInteractions 改造**（保留点击逻辑，但用共享 driver 找开始按钮）

```java
private void simulateInteractions(Page page) {
    // 用共享 driver.findStartButton + 双重 click 兜底
    boolean startClicked = tryClickStart(page);
    if (startClicked) {
        log.info("已点击开始按钮（共享 driver）");
        page.waitForTimeout(STEP_INTERVAL_MS);
    }
    // 后续仍点击其它交互元素（原逻辑保留）
    for (int step = 0; step < MAX_INTERACTION_STEPS; step++) {
        boolean clicked = tryClickInteractiveElement(page);
        if (!clicked) break;
        page.waitForTimeout(STEP_INTERVAL_MS);
    }
}

private boolean tryClickStart(Page page) {
    try {
        Object btnJson = page.evaluate("() => { const b = window.__PLAYABILITY_DRIVER__.findStartButton(); return b ? JSON.stringify(b) : null; }");
        if (btnJson != null) {
            Map<String, Object> btn = objectMapper.readValue((String) btnJson, Map.class);
            double x = ((Number) btn.get("x")).doubleValue();
            double y = ((Number) btn.get("y")).doubleValue();
            page.mouse().click(x, y);
            // JS 兜底
            page.evaluate("() => window.__PLAYABILITY_DRIVER__.clickByJS()");
            return true;
        }
    } catch (Exception e) {
        log.debug("findStartButton 失败: {}", e.getMessage());
    }
    return false;
}
```

**4. 数据 → ProbeReport 转换**

老 game-probe 的 `__GAME_PROBE__` 字段（events/stateChanges/outOfBoundsElements/finalState 等）现在共享 probe 不直接给。**要保留 ProbeReport 的字段不变**（不破坏 GameEvaluator.computeScores），所以：

| ProbeReport 老字段 | 新数据来源 |
|---|---|
| `pageLoaded` | 仍由 `isPageLoaded(page)` 提供 |
| `errors` | `__PLAYABILITY__.getErrors()` |
| `events` | **暂时为空数组**——因为共享 probe 不主动监听 click/keydown（它的设计哲学是"采集瞬时信号"而非"监听事件流"）。GameEvaluator 端如需 click 事件计数，自己用 `page.locator(...).count()` 或 page.evaluate 监听 |
| `stateChanges` | **暂时为空数组**——状态变化推断本来就不可靠（ID 命名易愚弄），先去掉。computeScores 中"hasScoreChanges"对应分会受影响——见下文 |
| `outOfBoundsElements` | 直接在 evaluate 里跑越界检测 JS（仍内联在 GameEvaluator，不抽到共享层因为只有 evaluator 用） |
| `domMutationsCount` | 暂为 0——共享 probe 不挂 MutationObserver。后续若需要可加但不在本任务 |
| `stateTransitions` | 暂为空字符串列表 |
| `finalState` | 仍由 `getScoreValue` 提供（用共享 probe.collect().numeric 取） |

**评分影响声明**（必须放进 task memory）：
- 改造后 GameEvaluator 的 `interactivity` 分会**系统性下降**——因为 events 暂为空
- `completeness` 分也会下降——hasStateTransitions / hasScoreChanges 都减弱
- 这是**已知代价**——本任务的目标是"抽公共底层"而非"提升评分准确度"
- 后续任务（260522-evaluator-keyboard-explore）会专门加键盘探索 + click 事件计数 + Skill 信号驱动评分，把这两维分恢复并真正变准

### 约束

- **保留 ProbeReport 字段不变**——computeScores 不改，但部分字段会变成空/0
- **保留五维评分公式**——`runnability + layout + interactivity + completeness + 15`
- **保留 evaluateGame Tool 的接口**（@Tool 描述、参数、返回）不变
- **不引入 maven-resources-plugin / 第三方依赖**——直接 Files.readString 读项目根
- **删除 injectProbe 方法**（注释说明"由 page.addInitScript 替代"）但保留 `probeScript` 字段为 deprecated 兼容
- **老 game-probe.js 文件保留**（作 v1 兼容备份）

### 复用模式

- Playwright `addInitScript` 用法参考 Playwright 官方 doc
- Files.readString 已在项目其他地方用过（参考 `agent/skill/SkillLoader.java`）
- ObjectMapper 反序列化已有

### 依赖

- Step 1（shared/playability/*.js 必须就绪）

## 【验收契约（Evaluator 输入）】

### 代码结构

- [ ] `GameEvaluator.java` 含 `playabilityProbeJs / playabilityDriverJs` 字段
- [ ] `init()` 加载这两个 JS 文件，长度 > 1000 字符
- [ ] `runInBrowser()` 调 `context.addInitScript(playabilityProbeJs)` 和 driver.js
- [ ] `tryClickStart()` 调 `__PLAYABILITY_DRIVER__.findStartButton`
- [ ] 老 `injectProbe` 方法删除或显式 deprecated
- [ ] `ProbeReport` 字段未变

### 命令验收

```bash
( cd game-agent-backend && mvn compile -q ) && echo "✓ compile"
# mvn 编译过

# 启 backend，看 SKILL 加载 + 共享 JS 加载
( cd game-agent-backend && mvn spring-boot:run -q ) > /tmp/aigame-step2.log 2>&1 &
PID=$!
for i in $(seq 1 60); do curl -sf http://localhost:8088/api/game/agents > /dev/null && break; sleep 1; done
sleep 2
grep -q "Playability shared JS 加载完成" /tmp/aigame-step2.log
# 应有日志输出
kill $PID; wait $PID 2>/dev/null
```

### 数据/字段验证

- [ ] 跑一次 `evaluateGame(<贪吃蛇 HTML>)`，返回值含 totalScore + 五维分数
- [ ] errors 字段不为空（如果 HTML 有 JS 错）或为空数组（HTML 干净）
- [ ] events / stateChanges / domMutationsCount 已知会变空/0（这是已知代价）

### 端到端轻量验证

```bash
# 临时跑一次 evaluate（不依赖 LLM 配额，传现成 fixture HTML）
( cd game-agent-backend && cat > /tmp/test-evaluate.java <<'JAVA'
// 用一个 ApplicationContext 测试调 GameEvaluator.evaluate(snake-v0 内容)
JAVA
)
# 或更简单：跑一个真 LLM 调用（如配额允许），看 evalScore 是否合理
```

注：因为 events / stateChanges 暂空，evalScore 会比改造前**降低 20-40 分**（interactivity / completeness 维度都会降）。这是**预期行为**，不是 bug。

### 剩余风险

- evalScore 系统性下降——AgentLoop QUALITY_GATE_SCORE = 80 可能让所有生成都失败
- 退路：本 step 完成时，如果发现 80 门禁让所有 LLM 生成都进入 5 轮迭代仍 fail，临时把 QUALITY_GATE_SCORE 调到 50（在本 step 改 application.yml 或常量）+ memory 记录"待 evaluator-keyboard-explore 任务恢复评分后调回"

## 后续 Step 依赖

Step 4 交叉验证用本 step 的 GameEvaluator + Step 3 的 oracle 跑同一 fixture。
