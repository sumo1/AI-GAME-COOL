# Step 4：v0 → fixed 离线调试

## 背景

Step 3 自验跑完后，如果 snake-v0 oracle 输出 FAIL（或 PASS 但人工检视截屏发现"不太能玩"），说明 LLM 的 v0 输出有 bug。本 step 由主会话（LLM）亲自看诊断包推理修改，产出真正能玩的 snake-fixed.html，并把每一条改动的"为什么"记录下来——这是 Step 5 蒸馏 SKILL 的原始素材。

## 【实现契约（主会话执行 — 不分给 subagent）】

### 范围

- **可改文件**：
  - `test/fixtures/playability/snake-fixed.html`（新建）
  - `docs/task/260521-playable-snake-evolution/memory/2026-05-21-debug-log.md`（新建，调试日志）

- **不可改文件**：
  - `test/fixtures/playability/snake-v0.html`（v0 是历史 baseline，不许改）
  - 其它 fixture（dead-page / keytest）
  - oracle 工具脚本

### 产出清单

#### `snake-fixed.html`

调试方法：
1. 主会话亲自 Read `test/fixtures/playability/snake-v0.html`
2. Read 最近 oracle run dir 下的 `verdict.txt` + `result.json` + 两个截屏
3. 基于 LLM 的 HTML/JS 推理能力，找出 v0 不能玩的根因（按键不响应、canvas 不更新、状态机错……）
4. 从 v0 复制成 fixed.html，**只改最小必要内容**修复
5. 跑 `./scripts/playability-oracle.sh test/fixtures/playability/snake-fixed.html`，必须 PASS
6. 如 fixed 仍 FAIL → 看新诊断包再调；最多 3 次迭代

#### `debug-log.md`

记录每条改动 + why。格式：

```markdown
# Snake v0 → fixed 调试日志

> 时间：2026-05-21
> v0 来源：commit <hash>，`qwen3.6-plus` 生成
> oracle 对 v0 verdict：FAIL（理由：<oracle 给的理由>）

## 改动 1：<改动名>

**v0 原文（关键片段）**：
\```js
// 行号 X
<原代码>
\```

**fixed 修改后**：
\```js
<新代码>
\```

**why（关键）**：
- 现象：<oracle 诊断包看到的什么>
- 根因：<LLM 推理：为什么这样写不能玩>
- 修法：<改的逻辑>
- 普适性：✅ 普适（任何贪吃蛇都该这样写） / ⚠️ 个案（只有 v0 这种特定写法才需要）

## 改动 2：...

## 总结

| 改动 | 类型 | 是否 SKILL.md 候选 |
|------|------|--------------------|
| 1 | 普适 | ✅ 应进 SKILL "评估重点"或"常见问题" |
| 2 | 个案 | ❌ 不进 SKILL |
| 3 | 普适 | ✅ 应进 SKILL "生成步骤"补充 |
```

普适性判定标准（Step 5 用）：
- ✅ 普适：去掉 v0 上下文后结论仍成立（如"keydown 必须绑 document，不能绑 button"）
- ❌ 个案：只对这个 v0 的特定写法有效（如"v0 用了 setInterval，但忘了 clearInterval"——下次 LLM 可能不用 setInterval）

### 约束

- **只改 fixed.html，不改 v0**——v0 是历史标本
- **每条改动必须能通过 oracle 增量验证**：v0 base + 改动 1 → 跑 oracle / + 改动 2 → 跑 oracle ……如果某条改动加了不影响 verdict，是"无效改动"，应剔除
- **debug-log.md 必须诚实**：LLM 推错了根因（修了但 oracle 仍 FAIL），必须记下来"假设 X 错了，因为……"
- **30 分钟 budget**：超过 30 分钟仍未让 fixed PASS，标 ⚠️ 报告"v0 难以修复"，进 Step 5 / Step 6 时酌情回炉

### 复用模式

- 看诊断包：参考 testing.md §3 工具速查
- HTML 修改：直接用 Read + Edit 工具
- oracle 验证：跑 `./scripts/playability-oracle.sh`

### 依赖

- Step 3（oracle 工作正常）

## 【验收契约】

### coder 自验

- [ ] `snake-fixed.html` 存在
- [ ] `./scripts/playability-oracle.sh test/fixtures/playability/snake-fixed.html` 退出码 = 0（PASS）
- [ ] `debug-log.md` 存在，至少 1 条改动 + 普适性标记
- [ ] `snake-v0.html` 与 `snake-fixed.html` 内容确实不同（diff 非空）
- [ ] 没改 v0（git diff 只动 fixed + debug-log）

### 命令验收

```bash
# fixed 必须 PASS
./scripts/playability-oracle.sh test/fixtures/playability/snake-fixed.html
[ $? -eq 0 ] || exit 1

# v0 不可改
git diff HEAD -- test/fixtures/playability/snake-v0.html | wc -l
# 应输出 0

# debug-log 至少有一条改动
grep -c "^## 改动" docs/task/260521-playable-snake-evolution/memory/2026-05-21-debug-log.md
# 应 ≥ 1
```

### 剩余风险

- LLM 修不出来：标 ⚠️ 进入 Step 5 / Step 6 时考虑回炉

## 后续 Step 依赖

Step 5 蒸馏 SKILL 用 debug-log.md 作为素材。
