# Step 3：自验（任务收口端到端）

## 背景

**这一步本身就是本任务的收口端到端**——按 `docs/engineering/testing.md §1.4` 节奏，"任务收口跑一次完整端到端"由这步覆盖。

验证目标：oracle 对三个 fixture 给出预期方向的判定，证明 oracle **既不假阴也不假阳**。

## 【实现契约（Coder 输入）】

### 范围

- **可改文件**：
  - `scripts/playability-oracle-self-test.sh`（新建，自验脚本）
  - 任务 memory：`docs/task/260521-playability-oracle/memory/2026-05-21-*.md`（如有踩坑发现）

- **不可改文件**：
  - Step 1 / Step 2 产出（不应再改）

### 产出清单

#### `scripts/playability-oracle-self-test.sh`

跑 oracle 三次，每次一个 fixture，对比预期方向：

```bash
#!/usr/bin/env bash
set -e

ORACLE=./scripts/playability-oracle.sh
FIX=./test/fixtures/playability

PASS=0
FAIL=0
WARN=0

run_case() {
  local fixture="$1"
  local expected="$2"   # PASS / FAIL / EITHER
  local desc="$3"

  echo "=== Case: $desc ==="
  $ORACLE "$fixture" || true
  local code=$?

  case "$expected" in
    PASS)
      if [ $code -eq 0 ]; then
        echo "  ✓ 期望 PASS，oracle 判 PASS"
        PASS=$((PASS+1))
      else
        echo "  ✗ 期望 PASS，oracle 判 FAIL"
        FAIL=$((FAIL+1))
      fi
      ;;
    FAIL)
      if [ $code -eq 1 ]; then
        echo "  ✓ 期望 FAIL，oracle 判 FAIL"
        PASS=$((PASS+1))
      else
        echo "  ✗ 期望 FAIL，oracle 判 PASS（假阳性！）"
        FAIL=$((FAIL+1))
      fi
      ;;
    EITHER)
      if [ $code -eq 0 ]; then
        echo "  ⚠ snake-v0 oracle 判 PASS（v0 能玩，OK）"
        WARN=$((WARN+1))
      elif [ $code -eq 1 ]; then
        echo "  ⚠ snake-v0 oracle 判 FAIL（v0 不能玩，但诊断包应对症——人工检视诊断包）"
        WARN=$((WARN+1))
      else
        echo "  ✗ snake-v0 oracle exit $code（工具自身错误！）"
        FAIL=$((FAIL+1))
      fi
      ;;
  esac
  echo ""
}

run_case "$FIX/keytest.html"   PASS   "keytest（最小键盘响应，期望 PASS）"
run_case "$FIX/dead-page.html" FAIL   "dead-page（静态死页面，期望 FAIL）"
run_case "$FIX/snake-v0.html"  EITHER "snake-v0（LLM 生成贪吃蛇，PASS/FAIL+诊断包对症皆可）"

echo "=================================="
echo "PASS: $PASS / FAIL: $FAIL / WARN: $WARN"
echo "=================================="

# 通过条件：keytest 必须 PASS、dead-page 必须 FAIL、snake-v0 不论
[ $FAIL -eq 0 ] && exit 0 || exit 1
```

### 约束

- 严格 PASS/FAIL 鉴别力：keytest 必须 PASS，dead-page 必须 FAIL——这两个是**冻结的契约**
- snake-v0 容许 PASS 或 FAIL，但若 FAIL 则人工检视诊断包必须能解释为什么
- self-test.sh 的退出码就是本任务收口的最终信号

### 复用

- Step 1 的 oracle.sh
- Step 2 的三个 fixture

### 依赖

- Step 1（oracle 必须工作）
- Step 2（fixture 必须就绪）

## 【验收契约（Evaluator 输入）】

> 本 step 即任务收口端到端，**强制**用户主会话亲自跑通（不分给 subagent）。

### 命令验收（任务收口）

```bash
chmod +x scripts/playability-oracle-self-test.sh
./scripts/playability-oracle-self-test.sh

# 期望：
#   exit 0
#   stdout 含 "PASS: 2 / FAIL: 0"（snake-v0 算 WARN）
```

### 数据验收

跑完后检查 `/tmp/playability-oracle/` 下三个 run 目录都存在：

```bash
ls -la /tmp/playability-oracle/ | grep run-
# 应有 3 个最近 5 分钟内创建的 run-{ts}/

# 每个 run dir 都应有这些文件
for d in /tmp/playability-oracle/run-*; do
  for f in game.html screenshot-baseline.png screenshot-after-keys.png result.json verdict.txt; do
    [ -f "$d/$f" ] || echo "MISSING: $d/$f"
  done
done
```

### 关键人工检视

如果 snake-v0 是 LLM 生成的：人工打开 `/tmp/playability-oracle/run-*/screenshot-baseline.png` 和 `screenshot-after-keys.png`，判断：
- 截屏看上去"像贪吃蛇"吗？
- 如果两张图明显不同（蛇移动了 / 分数变了）→ oracle 判 PASS 是对的
- 如果两张图看上去一样（按键根本没生效）→ oracle 判 FAIL 是对的；记录到 task memory，将来 Step 4 任务（snake-skill-evolve）会去解决

### 剩余风险

- 如果 snake-v0 假阳性（截屏明显不同但 oracle 漏检）→ Step 1 oracle 设计有 bug，需返工 Step 1
- 如果 snake-v0 假阴性（截屏明显同样 / 静态但 oracle 误判 PASS）→ 同上
- 都是真信号，要正视

## 任务收口判定

self-test 通过 + 诊断包齐全 + 人工检视无矛盾 = 任务交付。

进 Step 4 写文档收尾。
