#!/usr/bin/env bash
# 用法：./scripts/playability-oracle.sh <html-path>
# 输出：
#   stdout: PASS / FAIL + 一句话理由
#   exit code: 0=PASS, 1=FAIL, 2=tooling error (browser-harness 挂、文件不存在等)
#   /tmp/playability-oracle/run-{ts}/: 完整诊断包
set -euo pipefail

# ---------- 入参校验 ----------
if [ $# -ne 1 ]; then
  echo "Usage: $0 <html-path>" >&2
  echo "  exit code: 0=PASS, 1=FAIL, 2=tooling error" >&2
  exit 2
fi

HTML_INPUT="$1"

if [ ! -f "$HTML_INPUT" ]; then
  echo "[oracle] tooling error: HTML file not found: $HTML_INPUT" >&2
  exit 2
fi

case "$HTML_INPUT" in
  *.html|*.htm) : ;;
  *)
    echo "[oracle] tooling error: not an .html file: $HTML_INPUT" >&2
    exit 2
    ;;
esac

# 解析绝对路径（macOS 没有 readlink -f，用 python3 兜底）
ABS_HTML=$(python3 -c "import os,sys; print(os.path.abspath(sys.argv[1]))" "$HTML_INPUT")

# ---------- 解析脚本目录 ----------
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
DRIVER_PY="$SCRIPT_DIR/lib/oracle-driver.py"
VERDICT_SH="$SCRIPT_DIR/lib/oracle-verdict.sh"

if [ ! -f "$DRIVER_PY" ]; then
  echo "[oracle] tooling error: driver not found: $DRIVER_PY" >&2
  exit 2
fi
if [ ! -f "$VERDICT_SH" ]; then
  echo "[oracle] tooling error: verdict not found: $VERDICT_SH" >&2
  exit 2
fi

# ---------- 创建 run 目录 ----------
TS=$(date +%Y%m%d-%H%M%S)
RUN_DIR="/tmp/playability-oracle/run-$TS"
mkdir -p "$RUN_DIR"

# 拷 HTML
cp "$ABS_HTML" "$RUN_DIR/game.html"
RUN_HTML="$RUN_DIR/game.html"

echo "[oracle] run dir: $RUN_DIR"
echo "[oracle] html: $ABS_HTML"

# ---------- browser-harness 自检 ----------
if ! command -v browser-harness >/dev/null 2>&1; then
  echo "[oracle] tooling error: browser-harness not on PATH" >&2
  exit 2
fi

# --doctor 不一定全绿，但只要 daemon alive 我们就能跑；不强制 setup
DOCTOR_OUT=$(browser-harness --doctor 2>&1 || true)
echo "$DOCTOR_OUT" > "$RUN_DIR/doctor.txt"

if echo "$DOCTOR_OUT" | grep -q "daemon alive"; then
  :
else
  echo "[oracle] daemon not alive, attempting setup..." >&2
  if ! browser-harness --setup >> "$RUN_DIR/doctor.txt" 2>&1; then
    echo "[oracle] tooling error: browser-harness daemon unavailable" >&2
    cat "$RUN_DIR/doctor.txt" >&2
    exit 2
  fi
fi

# ---------- 调 driver 跑测试 ----------
RESULT_JSON="$RUN_DIR/result.json"
BASELINE_PNG="$RUN_DIR/screenshot-baseline.png"
AFTER_PNG="$RUN_DIR/screenshot-after-keys.png"

# driver 通过环境变量传参（避免 shell 转义噩梦）
export ORACLE_HTML_PATH="$RUN_HTML"
export ORACLE_BASELINE_PNG="$BASELINE_PNG"
export ORACLE_AFTER_PNG="$AFTER_PNG"
export ORACLE_RESULT_JSON="$RESULT_JSON"

# 一次性把 driver.py 内容塞给 browser-harness -c
DRIVER_CODE=$(cat "$DRIVER_PY")

# 用 python3 把脚本内容做基本校验
python3 -c "import ast,sys; ast.parse(open('$DRIVER_PY').read())" || {
  echo "[oracle] tooling error: driver-py syntax invalid" >&2
  exit 2
}

set +e
browser-harness -c "$DRIVER_CODE" > "$RUN_DIR/driver-stdout.log" 2> "$RUN_DIR/driver-stderr.log"
DRIVER_EXIT=$?
set -e

if [ $DRIVER_EXIT -ne 0 ]; then
  echo "[oracle] tooling error: driver exited $DRIVER_EXIT" >&2
  cat "$RUN_DIR/driver-stderr.log" >&2 || true
  exit 2
fi

if [ ! -f "$RESULT_JSON" ]; then
  echo "[oracle] tooling error: driver did not produce result.json" >&2
  cat "$RUN_DIR/driver-stderr.log" >&2 || true
  exit 2
fi

# ---------- 调 verdict 判定 ----------
set +e
bash "$VERDICT_SH" "$RUN_DIR"
VERDICT_EXIT=$?
set -e

# ---------- 输出 ----------
if [ -f "$RUN_DIR/verdict.txt" ]; then
  head -2 "$RUN_DIR/verdict.txt"
fi
echo "[oracle] full verdict: $RUN_DIR/verdict.txt"

exit $VERDICT_EXIT
