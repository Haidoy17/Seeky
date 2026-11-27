// Seeky 5.57 · the note editor: one page that is written in and is already the final note.
// The page shows the note formatted; markup() turns it back into Seeky's note marks (the same format as
// the phone, Word and PDF), which the window saves. "seeky" is the window's bridge (refs, changed).
(function () {
  var doc = document.getElementById('doc');
  var BLOCK = /^(P|DIV|H[1-6]|UL|OL|LI|BLOCKQUOTE|HR|TABLE|TBODY|THEAD|TR|TD|TH|PRE|SECTION|ARTICLE|FIGURE|HEADER|FOOTER)$/;
  var PALETTE = window.SEEKY_COLORS || [];
  function isBlock(n) { return n.nodeType === 1 && BLOCK.test(n.tagName); }
  function hasBlock(n) { for (var c = n.firstChild; c; c = c.nextSibling) if (isBlock(c)) return true; return false; }
  function sp(k) { var s = ''; for (var i = 0; i < k; i++) s += '  '; return s; }
  function lvl(n) {
    if (!n.getAttribute) return 0;
    var l = parseInt(n.getAttribute('data-level') || '0', 10) || 0;
    if (!l && n.style && n.style.marginLeft) l = Math.round((parseInt(n.style.marginLeft, 10) || 0) / 24);
    return Math.max(0, Math.min(6, l));
  }
  function align(n) {
    var a = (n.style && n.style.textAlign) || (n.getAttribute && n.getAttribute('align')) || '';
    return a === 'center' ? '[c] ' : (a === 'right' ? '[d] ' : '');
  }
  function colorKey(c) {
    var m = /rgba?\((\d+),\s*(\d+),\s*(\d+)/.exec(c), r, g, b;
    if (m) { r = +m[1]; g = +m[2]; b = +m[3]; }
    else if ((m = /^#?([0-9a-f]{6})$/i.exec(c))) { var v = parseInt(m[1], 16); r = v >> 16 & 255; g = v >> 8 & 255; b = v & 255; }
    else return '';
    var best = '', dist = 70 * 70 * 3;
    for (var i = 0; i < PALETTE.length; i++) { var p = PALETTE[i], dr = r - p[1], dg = g - p[2], db = b - p[3], d = dr * dr + dg * dg + db * db; if (d < dist) { dist = d; best = p[0]; } }
    return best;
  }
  // ---- inline: styled runs with ** * __ ~~ == {cor:…}
  function inl(n, ctx) { var s = ''; for (var c = n.firstChild; c; c = c.nextSibling) s += run(c, ctx || {}); return s; }
  function run(c, ctx) {
    if (c.nodeType === 3) return c.nodeValue.replace(/ /g, ' ').replace(/[\r\n]+/g, ' ');
    if (c.nodeType !== 1) return '';
    var t = c.tagName;
    if (t === 'BR') return '\n';
    if (t === 'IMG' || t === 'SCRIPT' || t === 'STYLE') return '';
    var st = c.style || {}, w = st.fontWeight || '', dec = (st.textDecoration || '') + ' ' + (st.textDecorationLine || '');
    var want = [];
    if ((t === 'B' || t === 'STRONG' || w === 'bold' || parseInt(w, 10) >= 600) && !ctx.b) want.push(['b', '**']);
    if ((t === 'I' || t === 'EM' || st.fontStyle === 'italic') && !ctx.i) want.push(['i', '*']);
    if ((t === 'U' || dec.indexOf('underline') >= 0) && !ctx.u) want.push(['u', '__']);
    if ((t === 'S' || t === 'STRIKE' || t === 'DEL' || dec.indexOf('line-through') >= 0) && !ctx.s) want.push(['s', '~~']);
    if (t === 'MARK' && !ctx.m) want.push(['m', '==']);
    var key = ctx.c ? '' : colorKey(c.getAttribute('color') || st.color || '');
    var inner = {}; for (var k in ctx) inner[k] = ctx[k];
    for (var i = 0; i < want.length; i++) inner[want[i][0]] = true;
    if (key) inner.c = true;
    var s = inl(c, inner); if (isBlock(c)) s = '\n' + s;
    if (!want.length && !key) return s;
    return s.split('\n').map(function (line) {
      var core = line.trim(); if (!core) return line;
      var lead = line.match(/^\s*/)[0], trail = line.match(/\s*$/)[0], open = '', close = '';
      for (var i = 0; i < want.length; i++) { open += want[i][1]; close = want[i][1] + close; }
      if (key) { open = '{cor:' + key + '}' + open; close = close + '{/cor}'; }
      return lead + open + core + close + trail;
    }).join('\n');
  }
  // ---- blocks: one line of marks per paragraph, heading, list item…
  function out(lines, first, rest, text) {
    var parts = text.split('\n'); if (parts.length > 1 && parts[parts.length - 1] === '') parts.pop();
    for (var i = 0; i < parts.length; i++) {
      var c = parts[i].replace(/^[ \t]+/, '').replace(/[ \t]+$/, ''), line = (i === 0 ? first : rest) + c;
      lines.push(line.trim() ? line : '');
    }
  }
  function image(n, lines, pre) { lines.push((pre || '') + '![' + (n.getAttribute('data-label') || 'Imagem') + '](note-img:' + n.getAttribute('data-ref') + ')'); }
  function table(n, lines) {
    var rows = n.querySelectorAll('tr');
    for (var i = 0; i < rows.length; i++) {
      var cells = rows[i].children, cs = [];
      for (var j = 0; j < cells.length; j++) cs.push(inl(cells[j]).replace(/\n/g, ' ').replace(/\|/g, '/').trim());
      lines.push('| ' + cs.join(' | ') + ' |');
      if (i === 0) lines.push('|' + cs.map(function () { return '---'; }).join('|') + '|');
    }
  }
  function list(n, lines, level) {
    var tasks = n.classList && n.classList.contains('tasks'), num = n.tagName === 'OL', k = 0;
    for (var li = n.firstChild; li; li = li.nextSibling) {
      if (li.nodeType !== 1) continue;
      if (li.tagName === 'UL' || li.tagName === 'OL') { list(li, lines, level + 1); continue; }
      if (li.tagName !== 'LI') { block(li, lines, level); continue; }
      var l = level + lvl(li); k++;
      var task = tasks || li.classList.contains('task');
      var pre = sp(l) + (task ? (li.getAttribute('data-done') === '1' ? '[x] ' : '[ ] ') : (num ? k + '. ' : '- '));
      var buf = document.createElement('div'), nested = [];
      for (var c = li.firstChild; c; c = c.nextSibling) {
        if (c.nodeType === 1 && (c.tagName === 'UL' || c.tagName === 'OL')) nested.push(c);
        else { if (c.nodeType === 1 && isBlock(c) && buf.firstChild) buf.appendChild(document.createElement('br')); buf.appendChild(c.cloneNode(true)); }
      }
      out(lines, pre, sp(l + 1), inl(buf));
      for (var x = 0; x < nested.length; x++) list(nested[x], lines, l + 1);
    }
  }
  function block(n, lines, depth) {
    var t = n.tagName;
    if (/^H[1-6]$/.test(t)) { var h = Math.min(4, +t.charAt(1)), hs = ''; for (var i = 0; i < h; i++) hs += '#'; out(lines, sp(lvl(n)) + align(n) + hs + ' ', '', inl(n)); return; }
    if (t === 'HR') { lines.push('---'); return; }
    if (t === 'UL' || t === 'OL') { list(n, lines, depth + lvl(n)); return; }
    if (t === 'BLOCKQUOTE') {
      if (hasBlock(n)) { var sub = []; walk(n, sub, 0); for (var q = 0; q < sub.length; q++) lines.push('> ' + sub[q].replace(/^>\s?/, '')); }
      else out(lines, '> ', '> ', inl(n));
      return;
    }
    if (t === 'TABLE') { table(n, lines); return; }
    if (t === 'IMG') { if (n.getAttribute('data-ref')) image(n, lines, ''); return; }
    if (hasBlock(n)) { walk(n, lines, depth + lvl(n)); return; }
    var audio = n.querySelector && n.querySelector('a.audio');
    if (audio && n.textContent.trim() === audio.textContent.trim()) { lines.push('[' + (audio.getAttribute('data-label') || '🎙 Gravação') + '](note-audio:' + audio.getAttribute('data-ref') + ')'); return; }
    var pre = sp(lvl(n) + depth) + align(n), text = inl(n);
    var imgs = n.querySelectorAll ? n.querySelectorAll('img[data-ref]') : [];
    if (text.trim() || !imgs.length) out(lines, pre, pre, text);
    for (var m = 0; m < imgs.length; m++) image(imgs[m], lines, align(n));
  }
  function walk(parent, lines, depth) {
    var buf = document.createElement('span'), has = false;
    function flush() { if (has) { out(lines, sp(depth), sp(depth), inl(buf)); buf = document.createElement('span'); has = false; } }
    for (var c = parent.firstChild; c; c = c.nextSibling) {
      if (c.nodeType === 1 && (isBlock(c) || (c.tagName === 'IMG' && c.getAttribute('data-ref')))) { flush(); block(c, lines, depth); }
      else if (c.nodeType === 3 && !c.nodeValue.trim() && !has) continue;
      else { buf.appendChild(c.cloneNode(true)); has = true; }
    }
    flush();
  }
  window.markup = function () { var lines = []; walk(doc, lines, 0); while (lines.length && !lines[lines.length - 1].trim()) lines.pop(); return lines.join('\n'); };

  // ---- caret kept as a text offset while passages are (re)marked
  function caret() {
    var s = window.getSelection(); if (!s.rangeCount) return -1;
    var r = s.getRangeAt(0), pre = document.createRange(); pre.selectNodeContents(doc);
    try { pre.setEnd(r.startContainer, r.startOffset); } catch (e) { return -1; }
    return pre.toString().length;
  }
  function place(at) {
    if (at < 0) return; var w = document.createTreeWalker(doc, NodeFilter.SHOW_TEXT, null, false), n;
    while ((n = w.nextNode())) { if (at <= n.nodeValue.length) { var r = document.createRange(); r.setStart(n, at); r.collapse(true); var s = window.getSelection(); s.removeAllRanges(); s.addRange(r); return; } at -= n.nodeValue.length; }
  }
  var lastRefs = null;
  /** Passages (João 3:16…) are found by the window (BibleReferences) and marked; a click opens them. */
  window.decorate = function () {
    if (!window.seeky) return;
    var key = String(seeky.refs(doc.textContent)); if (key === lastRefs) return; lastRefs = key;
    var at = document.activeElement === doc ? caret() : -1;
    var old = doc.querySelectorAll('span.passage');
    for (var i = 0; i < old.length; i++) { var s = old[i]; while (s.firstChild) s.parentNode.insertBefore(s.firstChild, s); s.parentNode.removeChild(s); }
    doc.normalize();
    var w = document.createTreeWalker(doc, NodeFilter.SHOW_TEXT, null, false), nodes = [], n;
    while ((n = w.nextNode())) nodes.push(n);
    for (var j = 0; j < nodes.length; j++) {
      var found = String(seeky.refs(nodes[j].nodeValue)); if (!found) continue;
      var parts = found.split('\n'), node = nodes[j];
      for (var k = parts.length - 1; k >= 0; k--) {
        var p = parts[k].split('\t'); if (p.length < 3) continue;
        var start = +p[0], end = +p[1]; if (end > node.nodeValue.length || start >= end) continue;
        var tail = node.splitText(end), mid = node.splitText(start), span = document.createElement('span');
        span.className = 'passage'; span.setAttribute('data-ref', p[2]); span.title = p[2] + ' — clique para ler';
        mid.parentNode.insertBefore(span, mid); span.appendChild(mid); void tail;
      }
    }
    place(at);
  };

  // ---- typing: save a moment after the last key, as the final note
  var timer = null;
  function changed() { if (timer) clearTimeout(timer); timer = setTimeout(function () { timer = null; window.decorate(); if (window.seeky) seeky.changed(window.markup()); }, 450); }
  window.changed = changed;
  doc.addEventListener('input', changed);
  doc.addEventListener('paste', function (e) {
    var text = e.clipboardData && e.clipboardData.getData('text/plain'); if (text == null) return;
    e.preventDefault(); document.execCommand('insertText', false, text);
  });
  doc.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') setTimeout(function () {
      var s = window.getSelection(); if (!s.rangeCount) return; var n = s.anchorNode;
      while (n && n !== doc && n.tagName !== 'LI') n = n.parentNode;
      if (n && n.tagName === 'LI' && n.parentNode.classList.contains('tasks')) { n.className = 'task'; n.setAttribute('data-done', '0'); }
    }, 0);
  });
  // A click on the box of a task ticks it.
  doc.addEventListener('mousedown', function (e) {
    var li = e.target; while (li && li !== doc && li.tagName !== 'LI') li = li.parentNode;
    if (!li || li === doc || !(li.classList.contains('task') || li.parentNode.classList.contains('tasks'))) return;
    var box = li.getBoundingClientRect(); if (e.clientX - box.left > 26) return;
    e.preventDefault(); li.setAttribute('data-done', li.getAttribute('data-done') === '1' ? '0' : '1'); changed();
  });

  // ---- the toolbar
  function current(tags) {
    var s = window.getSelection(); if (!s.rangeCount) return null; var n = s.anchorNode;
    while (n && n !== doc) { if (n.nodeType === 1 && tags.indexOf(n.tagName) >= 0) return n; n = n.parentNode; }
    return null;
  }
  function topBlock() {
    var s = window.getSelection(); if (!s.rangeCount) return null; var n = s.anchorNode;
    while (n && n.parentNode && n.parentNode !== doc) n = n.parentNode;
    return n && n !== doc && n.nodeType === 1 ? n : null;
  }
  // The toolbar is outside the page: keep the selection when the page loses focus and put it back.
  var kept = null;
  document.addEventListener('selectionchange', function () { var s = window.getSelection(); if (s.rangeCount && doc.contains(s.getRangeAt(0).startContainer)) kept = s.getRangeAt(0).cloneRange(); });
  window.cmd = function (name, value) {
    doc.focus();
    var sel = window.getSelection();
    if (kept && (!sel.rangeCount || !doc.contains(sel.getRangeAt(0).startContainer))) { sel.removeAllRanges(); sel.addRange(kept); }
    if (name === 'mark') {
      var s = window.getSelection(); if (!s.rangeCount || s.isCollapsed) return;
      var r = s.getRangeAt(0), m = document.createElement('mark'); m.appendChild(r.extractContents()); r.insertNode(m);
    } else if (name === 'block') {
      document.execCommand('formatBlock', false, '<' + value + '>');
    } else if (name === 'list') {
      var li = current(['LI']), ul = li ? li.parentNode : null;
      if (value === 'task') {
        if (ul && ul.tagName === 'UL' && ul.classList.contains('tasks')) { document.execCommand('insertUnorderedList'); }
        else {
          if (!ul || ul.tagName !== 'UL') document.execCommand('insertUnorderedList');
          var l2 = current(['LI']); if (l2) { var u2 = l2.parentNode; u2.classList.add('tasks'); for (var c = u2.firstChild; c; c = c.nextSibling) if (c.tagName === 'LI') { c.className = 'task'; if (!c.getAttribute('data-done')) c.setAttribute('data-done', '0'); } }
        }
      } else {
        if (ul && ul.classList.contains('tasks') && value === 'ul') { ul.classList.remove('tasks'); for (var d = ul.firstChild; d; d = d.nextSibling) if (d.tagName === 'LI') { d.className = ''; d.removeAttribute('data-done'); } }
        else document.execCommand(value === 'ol' ? 'insertOrderedList' : 'insertUnorderedList');
      }
    } else if (name === 'indent' || name === 'outdent') {
      if (current(['LI'])) document.execCommand(name);
      else { var b = topBlock(); if (b) { var lv = Math.max(0, Math.min(6, lvl(b) + (name === 'indent' ? 1 : -1))); b.setAttribute('data-level', lv); b.style.marginLeft = lv ? (lv * 24) + 'px' : ''; } }
    } else if (name === 'color') {
      document.execCommand('styleWithCSS', false, true); document.execCommand('foreColor', false, value); document.execCommand('styleWithCSS', false, false);
    } else if (name === 'html') {
      document.execCommand('insertHTML', false, value);
    } else {
      document.execCommand(name, false, value || null);
    }
    changed();
  };
  window.setTitleFocus = function () { doc.focus(); };
  doc.focus();
  setTimeout(window.decorate, 50);
})();
