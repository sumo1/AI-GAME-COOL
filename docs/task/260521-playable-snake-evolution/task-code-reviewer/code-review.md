# 任务专项审查规则：260521-playability-oracle

> 本任务期间 code-reviewer 在工程标准之上额外检查的"已冻结边界"。
> 任务收尾后规则归档。

## 适用范围

- 改动涉及 `scripts/playability-oracle.sh` / `scripts/lib/oracle-*.{sh,py}` / `scripts/README.md`
- 改动涉及 `test/fixtures/playability/*`
- 改动涉及 `docs/engineering/testing.md` 中 oracle 段

不在以上范围按 `docs/review/code-check.md` 工程标准审查。

## 已冻结边界（命中即"高风险"）

1. **不进 Java**：不许在 `game-agent-backend/src/` 下加任何与 oracle 相关的代码 → 拦
2. **不进 mvn / npm 依赖**：`pom.xml` / `package.json` diff = 0 → 否则拦
3. **不依赖运行中 backend / frontend**：oracle 必须用 `file://` 加载 HTML，不许 `http://localhost:8088/...` 或 `http://localhost:5173/...` → 拦
4. **不修改 browser-harness 本身**：~/Developer/browser-harness 是独立工具，oracle 只能用它的 helpers，不能改它的内部 → 拦
5. **fixture 路径冻结**：`test/fixtures/playability/{snake-v0,dead-page,keytest}.html` 路径不许改 → 拦
6. **退出码语义冻结**：0=PASS, 1=FAIL, 2=工具错 → 不许重新分配 → 拦
7. **诊断包结构冻结**：`/tmp/playability-oracle/run-{ts}/` 下必须有 `game.html / screenshot-baseline.png / screenshot-after-keys.png / result.json / verdict.txt` 五个文件 → 拦
8. **oracle 不许调 LLM**：oracle 是纯外部判定器，不许内部调 ChatModel / Spring AI / DashScope → 拦
9. **不替代 GameEvaluator**：oracle 不写 evalScore 到 DB / 不影响 AgentLoop → 拦

## 触发"中风险"的反模式

- bash 脚本无 `set -e`（错误静默吞）
- python 代码用 print 不用 sys.stdout（影响管道）
- HTML fixture 不带 charset=UTF-8
- `oracle-driver.py` 嵌入 HTML 字符串时不做 escape（XSS 不算威胁但会语法错）
- 诊断包覆盖（不带 ts 后缀，重跑覆盖前次结果）

## 触发"低风险"的提醒

- bash 脚本路径用相对而非绝对（cwd 敏感）
- 截屏命名不用 `.png` 后缀
- README 缺退出码说明
- testing.md §1.5 超过 60 行（应精简）

## 审查输出模板（在工程标准基础上额外段）

```text
【任务专项检查】
- 已冻结边界违反：N 处
  - {文件}:{行}: {问题} — 命中规则 {N}
- 反模式：N 处
- 提醒：N 处

【与 plan 契约对齐】
- coder 改动是否在 plan §可改文件 范围内？✅/❌
- 是否触碰 §不可改文件？{若 ❌ 列出}
- 退出码 / 诊断包结构是否与契约一致？✅/❌
```
