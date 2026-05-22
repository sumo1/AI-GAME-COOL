# oracle-driver.py
# 在 browser-harness -c 上下文中执行。
# helpers (new_tab / wait_for_load / capture_screenshot / press_key / wait / js / ensure_real_tab)
# 已被 browser-harness 预导入。
#
# 通过环境变量接参：
#   ORACLE_HTML_PATH       — 待测 HTML 绝对路径（已拷到 run-dir）
#   ORACLE_BASELINE_PNG    — 基线截图输出路径
#   ORACLE_AFTER_PNG       — 按键后截图输出路径
#   ORACLE_RESULT_JSON     — result.json 输出路径
#
# 输出：写 result.json，包含 baseline / final / auto_changing_paths / keys_pressed / errors

import os
import json
import sys
import traceback

HTML_PATH = os.environ["ORACLE_HTML_PATH"]
BASELINE_PNG = os.environ["ORACLE_BASELINE_PNG"]
AFTER_PNG = os.environ["ORACLE_AFTER_PNG"]
RESULT_JSON = os.environ["ORACLE_RESULT_JSON"]

errors = []

# JS：收集信号 + 安装错误捕获 + 找开始按钮
COLLECT_JS = r"""
(() => {
  function simpleHash(s) {
    let h = 5381;
    for (let i = 0; i < s.length; i++) { h = ((h << 5) + h + s.charCodeAt(i)) | 0; }
    return String(h);
  }
  function cssPath(el) {
    if (!el || el.nodeType !== 1) return '';
    const parts = [];
    let cur = el;
    let depth = 0;
    while (cur && cur.nodeType === 1 && depth < 6) {
      let part = cur.tagName.toLowerCase();
      if (cur.id) { part += '#' + cur.id; parts.unshift(part); break; }
      if (cur.className && typeof cur.className === 'string') {
        const cls = cur.className.trim().split(/\s+/).slice(0, 2).join('.');
        if (cls) part += '.' + cls;
      }
      const parent = cur.parentNode;
      if (parent && parent.children) {
        const sibs = [...parent.children].filter(s => s.tagName === cur.tagName);
        if (sibs.length > 1) {
          const idx = sibs.indexOf(cur) + 1;
          part += ':nth-of-type(' + idx + ')';
        }
      }
      parts.unshift(part);
      cur = cur.parentNode;
      depth++;
    }
    return parts.join('>');
  }

  const canvases = [...document.querySelectorAll('canvas')].map((c, i) => {
    let hash = '';
    try { hash = c.toDataURL().slice(-40); } catch (e) { hash = 'ERR:' + (e && e.message || 'unknown'); }
    return { idx: i, hash };
  });

  const numeric = [];
  const all = document.querySelectorAll('*');
  for (let i = 0; i < all.length && numeric.length < 200; i++) {
    const el = all[i];
    if (el.children && el.children.length > 0) continue;
    const t = (el.textContent || '').trim();
    if (t.length > 0 && t.length < 8 && /^-?\d+\.?\d*$/.test(t)) {
      numeric.push({ path: cssPath(el), val: t });
    }
  }

  const bodyText = (document.body && document.body.innerText) || '';
  return JSON.stringify({
    canvases: canvases,
    numeric: numeric,
    bodyTextLen: bodyText.length,
    bodyTextHash: simpleHash(bodyText.slice(0, 5000)),
    bodyText: bodyText.slice(0, 1500)
  });
})()
"""

INSTALL_ERROR_HOOK_JS = r"""
(() => {
  if (window.__oracleErrors) return 'already';
  window.__oracleErrors = [];
  window.addEventListener('error', (e) => {
    try { window.__oracleErrors.push({ type: 'error', msg: String(e.message || e), src: String(e.filename || '') }); } catch (_) {}
  });
  window.addEventListener('unhandledrejection', (e) => {
    try { window.__oracleErrors.push({ type: 'unhandledrejection', msg: String((e.reason && (e.reason.message || e.reason)) || '') }); } catch (_) {}
  });
  return 'installed';
})()
"""

READ_ERRORS_JS = "JSON.stringify(window.__oracleErrors || [])"

FIND_START_BUTTON_JS = r"""
(() => {
  const candidates = ['开始', '开始游戏', 'Start', 'Play', 'GO', '点击开始', '开 始', 'START', 'PLAY',
                       '再来一局', '再来一次', '再玩一次', 'Retry', 'Restart', '重新开始', 'Replay'];
  const tags = ['button', 'a', '[role=button]', 'div', 'span'];
  const all = [...document.querySelectorAll(tags.join(','))];
  for (const txt of candidates) {
    const el = all.find(e => ((e.textContent || '').trim() === txt));
    if (el) {
      const r = el.getBoundingClientRect();
      if (r.width > 0 && r.height > 0) {
        return JSON.stringify({ x: r.x + r.width/2, y: r.y + r.height/2, txt: txt });
      }
    }
  }
  // 兜底：内含关键词的可点元素
  for (const txt of candidates) {
    const el = all.find(e => {
      const t = (e.textContent || '').trim();
      return t.length < 30 && t.includes(txt);
    });
    if (el) {
      const r = el.getBoundingClientRect();
      if (r.width > 0 && r.height > 0) {
        return JSON.stringify({ x: r.x + r.width/2, y: r.y + r.height/2, txt: txt });
      }
    }
  }
  return null;
})()
"""


def parse_signals(raw):
    if not raw:
        return None
    try:
        return json.loads(raw)
    except Exception as e:
        errors.append({"type": "parse_signals", "msg": str(e), "raw_preview": str(raw)[:200]})
        return None


def safe_js(expr, label):
    try:
        return js(expr)  # noqa: F821 — provided by browser-harness
    except Exception as e:
        errors.append({"type": "js_call", "label": label, "msg": str(e)})
        return None


def main():
    try:
        # 1) 打开 HTML（用 file://）
        url = "file://" + HTML_PATH
        new_tab(url)  # noqa: F821
        wait_for_load(timeout=15)  # noqa: F821

        # 安装错误捕获钩子（在任何业务 JS 跑之前注入）
        safe_js(INSTALL_ERROR_HOOK_JS, "install_error_hook")

        # 2) baseline 截图
        try:
            capture_screenshot(BASELINE_PNG)  # noqa: F821
        except Exception as e:
            errors.append({"type": "screenshot", "label": "baseline", "msg": str(e)})

        # 3) Pre-flight：找开始按钮并点（坐标 click + JS click() 兜底，确保 overlay 不挡也能触发）
        start_btn_raw = safe_js(FIND_START_BUTTON_JS, "find_start_button")
        start_btn_clicked = None
        if start_btn_raw:
            try:
                btn = json.loads(start_btn_raw)
                click_at_xy(btn["x"], btn["y"])  # noqa: F821
                # JS 兜底：直接 element.click()，绕过 overlay / z-index 问题
                safe_js(r"""
                  (() => {
                    const candidates = ['开始', '开始游戏', 'Start', 'Play', 'GO', '点击开始', '开 始',
                                         'START', 'PLAY', '再来一局', '再来一次', '再玩一次',
                                         'Retry', 'Restart', '重新开始', 'Replay'];
                    const all = [...document.querySelectorAll('button, a, [role=button], div, span')];
                    for (const txt of candidates) {
                      const el = all.find(e => ((e.textContent || '').trim() === txt) ||
                                                 ((e.textContent || '').trim().includes(txt) &&
                                                  (e.textContent || '').trim().length < 30));
                      if (el && el.click) { el.click(); return 'fallback-clicked: ' + txt; }
                    }
                    return 'no-fallback-needed';
                  })()
                """, "click_start_fallback")
                start_btn_clicked = btn
                wait(0.5)  # noqa: F821
            except Exception as e:
                errors.append({"type": "click_start", "msg": str(e)})

        # 4) 自然变化采样：1 秒内不发任何按键
        nat_baseline_raw = safe_js(COLLECT_JS, "nat_baseline")
        wait(1.0)  # noqa: F821
        nat_after_raw = safe_js(COLLECT_JS, "nat_after")
        nat_baseline = parse_signals(nat_baseline_raw)
        nat_after = parse_signals(nat_after_raw)

        auto_changing_paths = []
        auto_changing_canvases = []
        if nat_baseline and nat_after:
            # numeric 对比
            base_map = {n["path"]: n["val"] for n in nat_baseline.get("numeric", [])}
            after_map = {n["path"]: n["val"] for n in nat_after.get("numeric", [])}
            for path, val in after_map.items():
                if base_map.get(path) != val:
                    auto_changing_paths.append(path)
            # canvas 对比
            base_cnv = {c["idx"]: c["hash"] for c in nat_baseline.get("canvases", [])}
            after_cnv = {c["idx"]: c["hash"] for c in nat_after.get("canvases", [])}
            for idx, h in after_cnv.items():
                if base_cnv.get(idx) != h:
                    auto_changing_canvases.append(idx)

        # 5) 真正 baseline（以自然变化采样后的状态为准）
        baseline_raw = safe_js(COLLECT_JS, "baseline")
        baseline = parse_signals(baseline_raw)

        # 6) 驱动：先 18 次混合按键（探索方向响应），再 30 次单方向（确保蛇横穿棋盘撞墙 → 触发 game over → bodyText 必变）
        explore = ['ArrowRight', 'ArrowDown', 'd', 's', 'ArrowLeft', 'ArrowUp', 'a', 'w'] * 3  # 24 个
        keys = explore[:18] + ['ArrowRight'] * 30  # 48 个，约 9.6 秒
        for k in keys:
            try:
                press_key(k)  # noqa: F821
                wait(0.2)  # noqa: F821
            except Exception as e:
                errors.append({"type": "press_key", "key": k, "msg": str(e)})

        # 7) Final
        try:
            capture_screenshot(AFTER_PNG)  # noqa: F821
        except Exception as e:
            errors.append({"type": "screenshot", "label": "after", "msg": str(e)})

        final_raw = safe_js(COLLECT_JS, "final")
        final = parse_signals(final_raw)

        # 收集 JS 运行期错误
        js_errors_raw = safe_js(READ_ERRORS_JS, "read_errors")
        js_errors = []
        if js_errors_raw:
            try:
                js_errors = json.loads(js_errors_raw)
            except Exception as e:
                errors.append({"type": "parse_js_errors", "msg": str(e)})

        result = {
            "html_path": HTML_PATH,
            "url": url,
            "baseline": baseline,
            "final": final,
            "nat_baseline": nat_baseline,
            "nat_after": nat_after,
            "auto_changing_paths": auto_changing_paths,
            "auto_changing_canvases": auto_changing_canvases,
            "keys_pressed": keys,
            "start_button_clicked": start_btn_clicked,
            "js_errors": js_errors,
            "errors": errors,
        }

        with open(RESULT_JSON, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)

        print("[driver] result.json written:", RESULT_JSON)
    except Exception:
        # 任何顶层异常也要写最少一份 result.json，避免 verdict 阶段抓瞎
        tb = traceback.format_exc()
        sys.stderr.write(tb)
        try:
            with open(RESULT_JSON, "w", encoding="utf-8") as f:
                json.dump({
                    "html_path": HTML_PATH,
                    "url": "file://" + HTML_PATH,
                    "baseline": None,
                    "final": None,
                    "nat_baseline": None,
                    "nat_after": None,
                    "auto_changing_paths": [],
                    "auto_changing_canvases": [],
                    "keys_pressed": [],
                    "start_button_clicked": None,
                    "js_errors": [],
                    "errors": errors + [{"type": "driver_top_level", "msg": tb}],
                }, f, ensure_ascii=False, indent=2)
        except Exception:
            pass
        # driver 进程不退非 0：让 verdict 阶段照常出 FAIL（写不出 json 才是 tooling error）


main()
