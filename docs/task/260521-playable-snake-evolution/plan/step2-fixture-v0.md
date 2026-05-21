# Step 2：贪吃蛇 fixture v0

## 背景

用一次真 LLM 生成贪吃蛇游戏，存到固定路径作为 oracle 自验材料。

**重要**：本 step 的 v0 **不要求能玩**——只要能拿到 HTML 即可。v0 能玩 → Step 3 oracle 输出 PASS；v0 不能玩 → Step 3 oracle 输出 FAIL + 诊断包对症。两种情况都验证 oracle 工作。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件（可新增）**：
  - `test/fixtures/playability/snake-v0.html`（新建，从 LLM 生成的游戏 HTML 拷贝）
  - `test/fixtures/playability/dead-page.html`（新建，最小静态页用作 oracle FAIL 对照）
  - `test/fixtures/playability/keytest.html`（新建，最小键盘响应页用作 oracle PASS 对照）
  - `test/fixtures/playability/README.md`（新建，说明三个 fixture 的用途）

- **不可改文件**：与 Step 1 相同（不动 Java/TS/pom/yml/agents/docs/scripts/）

### 产出清单

#### `snake-v0.html`

由真 LLM 生成。生成流程：

1. 启动 backend（如未启）
2. `curl -X POST /api/game/v2/generate -d '{"userInput":"做一个简单的贪吃蛇游戏，4-8岁玩"}' --max-time 300`
3. 从响应中提取 `gameData.html`
4. 写入 `test/fixtures/playability/snake-v0.html`

**LLM 失败的退路**：
- 如响应 `success=false`（free tier 耗尽 / 网络问题），不阻塞本任务
- 用一个**最小可行贪吃蛇 fixture**填充，标记为"v0 来自人工占位，待 LLM 配额恢复后重新生成"
- 占位贪吃蛇可以是 GitHub 上随便一个 ~100 行的开源贪吃蛇片段（标注来源），或自己写一个最小版

**判定 LLM 输出"已经成功"的标准**：
- HTTP 200
- 响应 JSON success=true
- 响应中含 sessionId（说明 SessionService 也写库了）
- `gameData.html` 长度 > 1000 字符（HTML 体积合理）

#### `dead-page.html`（用于 oracle FAIL 对照）

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>静态死页面（oracle FAIL 对照）</title>
</head>
<body>
  <h1>这不是游戏</h1>
  <p>本页面没有任何 JavaScript，没有键盘响应。</p>
  <p>oracle 应当判定 FAIL。</p>
</body>
</html>
```

#### `keytest.html`（用于 oracle PASS 对照）

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>最小键盘响应（oracle PASS 对照）</title>
</head>
<body>
  <div id="counter">0</div>
  <script>
    let n = 0;
    document.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowRight' || e.key === 'd') {
        n++;
        document.getElementById('counter').innerText = String(n);
      }
    });
  </script>
</body>
</html>
```

#### `test/fixtures/playability/README.md`

```markdown
# Playability Oracle Fixtures

| 文件 | 用途 | 期望 oracle verdict |
|---|---|---|
| `keytest.html` | 最小键盘响应页 | PASS |
| `dead-page.html` | 静态死页面 | FAIL |
| `snake-v0.html` | LLM 真实生成的贪吃蛇 v0 | 不确定（PASS 或 FAIL+对症诊断都算 oracle 工作） |

snake-v0 来源：<commit hash> 时由 `qwen3.6-plus` 生成，prompt: "做一个简单的贪吃蛇游戏，4-8岁玩"
```

### 约束

- **fixture 路径冻结**：`test/fixtures/playability/` 这个路径是接口契约的一部分，未来 Step 3 / 后续任务都按此查找
- **dead-page / keytest 内容冻结**：不许"为了让 oracle 工作"修改它们的内容（那是反模式）
- **不引入测试运行框架**：fixture 是静态文件，不是 jest/vitest/junit 测试用例
- LLM 调用可以失败——本 step 接受退路，但必须在 README 里标注 v0 是 LLM 生成还是人工占位

### 复用模式

- 启动 backend：参考 Step 5 中已成熟的脚本片段（`set -a; source .env; set +a; cd game-agent-backend && nohup mvn spring-boot:run -q`）
- LLM 调用 + python3 解析：参考 Step 5 的 `/tmp/last-resp.json` 处理方式
- HTML 提取：从 v2 响应的 `gameData.html` 字段读

### 依赖

- Step 1（不严格依赖——Step 2 单独跑也行；但产物要给 Step 3 用）

## 【验收契约（Evaluator 输入）】

### 验证（coder 自验，不强制 evaluator 复跑）

- [ ] 三个 fixture 文件存在
- [ ] `test/fixtures/playability/README.md` 写明 v0 来源
- [ ] `snake-v0.html` 是合法 HTML（含 `<!DOCTYPE html>`）
- [ ] `snake-v0.html` 长度 > 1000 字符（如果是 LLM 生成）或长度 ≥ 500（如果是人工占位）
- [ ] `keytest.html` / `dead-page.html` 内容与本 plan 一致

### 命令验收

```bash
# 文件存在性
[ -f test/fixtures/playability/snake-v0.html ] || exit 1
[ -f test/fixtures/playability/dead-page.html ] || exit 1
[ -f test/fixtures/playability/keytest.html ] || exit 1

# HTML 合法性
grep -q '<!DOCTYPE' test/fixtures/playability/snake-v0.html || exit 1

# v0 长度
wc -c < test/fixtures/playability/snake-v0.html | awk '{exit !($1 > 500)}'
```

### 剩余风险

- v0 来源不可控：LLM 抽风时退化为人工占位，影响 Step 3 验证的"真实性"——但不影响 oracle 自身的鉴别力验证

## 后续 Step 依赖

Step 3 用所有三个 fixture 跑 oracle，验证鉴别力。
