/**
 * Playability Probe — 浏览器内信号采集（任务 260522-evaluator-oracle-shared-core）
 *
 * 暴露 window.__PLAYABILITY__：
 *   - collect()                → 当前快照 {canvases, numeric, bodyText, bodyTextLen, bodyTextHash}
 *   - getErrors()              → JS 错误列表
 *   - computeWhitelist(b, a)   → 自然变化白名单计算（baseline → after-baseline）
 *   - hasNewKeyword(b, f)      → bodyText 关键词差异检测（识别游戏结束 / 分数 / 重启等）
 *
 * 设计原则：纯 JS、零项目依赖、能在 Playwright Chromium 与用户 Chrome 中跑。
 * 一次性注入到页面（addInitScript / safe_js）。防重复注入。
 *
 * @see ../README.md（接入契约 + 上层调用示例）
 */
(function () {
  'use strict';
  if (window.__PLAYABILITY__) return; // 防重复注入

  // ===== 1. 错误捕获 hook =====
  const errors = [];
  window.addEventListener('error', function (e) {
    errors.push({
      type: 'error',
      msg: (e && e.message) || 'unknown',
      file: (e && e.filename) || '',
      line: (e && e.lineno) || 0,
      ts: Date.now()
    });
  });
  window.addEventListener('unhandledrejection', function (e) {
    errors.push({
      type: 'unhandledrejection',
      msg: String((e && e.reason && (e.reason.message || e.reason)) || ''),
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
    const canvases = [].slice.call(document.querySelectorAll('canvas')).map(function (c, idx) {
      let hash = '';
      try { hash = c.toDataURL().slice(-40); } catch (e) { /* tainted canvas */ }
      return { idx: idx, hash: hash };
    });

    // 数字文本节点（包含分数、计时器、长度等）
    const numeric = [];
    const all = document.querySelectorAll('*');
    for (let i = 0; i < all.length && numeric.length < 50; i++) {
      const el = all[i];
      if (el.children && el.children.length > 0) continue;
      const t = (el.textContent || '').trim();
      if (/^-?\d+\.?\d*$/.test(t) && t.length < 8) {
        numeric.push({ path: cssPath(el), val: t });
      }
    }

    // bodyText（截断 1500 字符给关键词识别 + hash 给变化检测）
    const bodyText = ((document.body && document.body.innerText) || '');
    return {
      canvases: canvases,
      numeric: numeric,
      bodyText: bodyText.slice(0, 1500),
      bodyTextLen: bodyText.length,
      bodyTextHash: simpleHash(bodyText.slice(0, 5000))
    };
  }

  // ===== 5. 自然变化白名单 =====
  function computeWhitelist(before, after) {
    const autoCanvases = [];
    const autoPaths = [];
    if (!before || !after) return { autoCanvases: autoCanvases, autoPaths: autoPaths };

    const beforeCnv = {};
    (before.canvases || []).forEach(function (c) { beforeCnv[c.idx] = c.hash; });
    (after.canvases || []).forEach(function (c) {
      if (beforeCnv[c.idx] !== c.hash) autoCanvases.push(c.idx);
    });

    const beforeNum = {};
    (before.numeric || []).forEach(function (n) { beforeNum[n.path] = n.val; });
    (after.numeric || []).forEach(function (n) {
      if (beforeNum[n.path] !== n.val) autoPaths.push(n.path);
    });

    return { autoCanvases: autoCanvases, autoPaths: autoPaths };
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
    for (let i = 0; i < GAME_KEYWORDS.length; i++) {
      const kw = GAME_KEYWORDS[i];
      if (f.indexOf(kw) !== -1 && b.indexOf(kw) === -1) return kw;
    }
    return null;
  }

  // ===== 暴露 =====
  window.__PLAYABILITY__ = {
    collect: collect,
    getErrors: function () { return errors.slice(); },
    computeWhitelist: computeWhitelist,
    hasNewKeyword: hasNewKeyword,
    // 元数据，便于上层判断版本兼容
    version: '1.0.0'
  };
})();
