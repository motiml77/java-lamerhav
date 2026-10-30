// "שאלת בגרות לקוד": הקבצים מוכנים בדפדפן (Word נפתח כאן, תמונות מוקטנות), נשלחים לשרת של DSViz,
// והקוד נבנה כאן מהתשובה (scaffold.js — אותו בונה כמו ב-Eclipse). כמו ExerciseFiles ו-WorkerClient בתוסף.
(function () {
  'use strict';
  var SERVER = 'https://dsviz-exercise.motiml77.workers.dev';
  var JSZIP = 'https://cdnjs.cloudflare.com/ajax/libs/jszip/3.10.1/jszip.min.js';
  var MAX_ITEMS = 10, MAX_TOTAL = 14 * 1024 * 1024, MAX_FILE = 10 * 1024 * 1024, MAX_SIDE = 2000, MAX_TEXT = 60000;

  var $ = function (sel) { return document.querySelector(sel); };
  var form = $('[data-form]'), drop = $('[data-drop]'), input = $('[data-input]'), chosen = $('[data-chosen]');
  var send = $('[data-send]'), status = $('[data-status]'), result = $('[data-result]');
  var items = [];      // {name, parts: [{text} | {blob, mime}]}
  var current = null;  // {exercise, sources, pasted}

  function say(text, bad) {
    status.textContent = text || '';
    status.classList.toggle('bad', !!bad);
  }
  function ext(name) { var i = name.lastIndexOf('.'); return i < 0 ? '' : name.slice(i + 1).toLowerCase(); }
  function size(n) { return n < 1024 * 1024 ? Math.max(1, Math.round(n / 1024)) + ' KB' : (n / 1024 / 1024).toFixed(1) + ' MB'; }
  function itemBytes(it) { return it.parts.reduce(function (a, p) { return a + (p.blob ? p.blob.size : p.text.length * 2); }, 0); }

  // ---------------------------------------------------------------- הכנת הקבצים

  function head(blob, n) {
    return blob.slice(0, n).arrayBuffer().then(function (b) { return new Uint8Array(b); });
  }
  function isPdf(b) { return b[0] === 0x25 && b[1] === 0x50 && b[2] === 0x44 && b[3] === 0x46; }
  function isPng(b) { return b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47; }
  function isJpeg(b) { return b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff; }
  function isWebp(b) { return b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46 && b[8] === 0x57 && b[9] === 0x45; }
  function isZip(b) { return b[0] === 0x50 && b[1] === 0x4b; }

  function fail(msg) { var e = new Error(msg); e.user = true; return e; }

  function fromFile(file) {
    var name = file.name || 'קובץ';
    if (file.size > MAX_FILE) return Promise.reject(fail('הקובץ ' + name + ' גדול מדי (עד 10MB).'));
    return head(file, 12).then(function (b) {
      var x = ext(name);
      if (x === 'doc') throw fail('הקובץ ' + name + ' הוא Word ישן (doc). שומרים אותו כ-docx או כ-PDF ושולחים שוב.');
      if (isPdf(b)) return { name: name, parts: [{ blob: file, mime: 'application/pdf' }] };
      if ((x === 'docx' || x === 'docm') && isZip(b)) return fromDocx(name, file);
      if (isPng(b) || isJpeg(b) || isWebp(b) || /^image\//.test(file.type)) {
        return image(name, file).then(function (p) { return { name: name, parts: [p] }; });
      }
      throw fail('את הקובץ ' + name + ' אי אפשר לשלוח. שולחים PDF, Word (docx) או תמונה.');
    });
  }

  // תמונה: עד 2000 פיקסלים בצד. צילום מסך נשאר PNG (טקסט חד) אם הוא לא גדול מדי, אחרת JPEG
  function image(name, blob) {
    return head(blob, 12).then(function (b) {
      if (isWebp(b)) {
        if (blob.size > 4 * 1024 * 1024) throw fail('התמונה ' + name + ' גדולה מדי.');
        return { blob: blob, mime: 'image/webp' };
      }
      return createImageBitmap(blob).catch(function () { throw fail('הקובץ ' + name + ' הוא לא תמונה שאפשר לקרוא.'); }).then(function (img) {
        var w = img.width, h = img.height, small = Math.max(w, h) <= MAX_SIDE;
        if (small && isJpeg(b) && blob.size <= 2 * 1024 * 1024) return { blob: blob, mime: 'image/jpeg', w: w, h: h };
        if (small && isPng(b) && blob.size <= 1536 * 1024) return { blob: blob, mime: 'image/png', w: w, h: h };
        var k = small ? 1 : MAX_SIDE / Math.max(w, h);
        var c = document.createElement('canvas');
        c.width = Math.max(1, Math.round(w * k));
        c.height = Math.max(1, Math.round(h * k));
        var g = c.getContext('2d');
        g.fillStyle = '#fff';   // שקיפות → רקע לבן
        g.fillRect(0, 0, c.width, c.height);
        g.imageSmoothingQuality = 'high';
        g.drawImage(img, 0, 0, c.width, c.height);
        var out = function (type, q) { return new Promise(function (ok) { c.toBlob(ok, type, q); }); };
        var asJpeg = function () { return out('image/jpeg', 0.88).then(function (j) { return { blob: j, mime: 'image/jpeg', w: c.width, h: c.height }; }); };
        if (isPng(b) || !isJpeg(b)) {
          return out('image/png').then(function (p) { return p && p.size <= 1536 * 1024 ? { blob: p, mime: 'image/png', w: c.width, h: c.height } : asJpeg(); });
        }
        return asJpeg();
      });
    });
  }

  var zipLib = null;
  function loadZip() {
    if (window.JSZip) return Promise.resolve(window.JSZip);
    if (!zipLib) {
      zipLib = new Promise(function (ok, bad) {
        var s = document.createElement('script');
        s.src = JSZIP;
        s.onload = function () { ok(window.JSZip); };
        s.onerror = function () { zipLib = null; bad(fail('לא הצלחתי לטעון את הכלי לקובצי Word ו-ZIP. בודקים את החיבור ומנסים שוב.')); };
        document.head.appendChild(s);
      });
    }
    return zipLib;
  }

  // Word: הטקסט (פסקה בכל שורה, טבלאות עם | בין התאים) + עד 6 תמונות שבתוכו, בלי סמלים קטנים
  function fromDocx(name, file) {
    return loadZip().then(function (JSZip) { return JSZip.loadAsync(file); })
      .catch(function (e) { throw e && e.user ? e : fail('לא הצלחתי לקרוא את קובץ ה-Word ' + name + '. אפשר לשמור אותו כ-PDF ולשלוח.'); })
      .then(function (z) {
        var doc = z.file('word/document.xml');
        if (!doc) throw fail('הקובץ ' + name + ' הוא לא קובץ Word תקין. אפשר לשמור אותו כ-PDF ולשלוח.');
        var media = z.file(/^word\/media\/.+\.(png|jpe?g|gif|bmp)$/i).slice(0, 12);
        return Promise.all([doc.async('string')].concat(media.map(function (m) { return m.async('blob'); })));
      })
      .then(function (all) {
        var text = docxText(all[0]).trim();
        var parts = text ? [{ text: text.slice(0, MAX_TEXT) }] : [];
        return all.slice(1).reduce(function (chain, blob, i) {
          return chain.then(function () {
            if (parts.filter(function (p) { return p.blob; }).length >= 6) return;
            return image(name + ' (' + (i + 1) + ')', blob).then(function (p) {
              if (p.w >= 80 && p.h >= 40) parts.push(p);
            }, function () { /* תמונה שאי אפשר לקרוא — מדלגים */ });
          });
        }, Promise.resolve()).then(function () {
          if (!parts.length) throw fail('בקובץ ' + name + ' אין טקסט או תמונות.');
          return { name: name, parts: parts };
        });
      });
  }

  function docxText(xml) {
    var d = new DOMParser().parseFromString(xml, 'application/xml');
    if (d.getElementsByTagName('parsererror').length) throw fail('קובץ ה-Word פגום. אפשר לשמור אותו כ-PDF ולשלוח.');
    var out = [];
    (function walk(n) {
      var ln = n.localName;
      if (ln === 'p') { paragraph(n, out); out.push('\n'); return; }
      for (var c = n.firstChild; c; c = c.nextSibling) if (c.nodeType === 1) walk(c);
      if (ln === 'tc') {
        while (out.length && out[out.length - 1] === '\n') out.pop();
        out.push(' | ');
      } else if (ln === 'tr') out.push('\n');
    })(d.documentElement);
    return out.join('').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n');
  }
  function paragraph(n, out) {
    for (var c = n.firstChild; c; c = c.nextSibling) {
      if (c.nodeType !== 1) continue;
      var ln = c.localName;
      if (ln === 't') out.push(c.textContent);
      else if (ln === 'tab') out.push('\t');
      else if (ln === 'br' || ln === 'cr') out.push('\n');
      else if (ln === 'p') { paragraph(c, out); out.push('\n'); }
      else paragraph(c, out);
    }
  }

  // ---------------------------------------------------------------- רשימת הקבצים

  var pastedCount = 0;
  function add(files) {
    files = Array.prototype.slice.call(files || []);
    if (!files.length) return;
    say('מכין את הקבצים…');
    files.reduce(function (chain, f) {
      return chain.then(function () {
        if (items.length >= MAX_ITEMS) throw fail('אפשר לשלוח עד ' + MAX_ITEMS + ' קבצים.');
        return fromFile(f).then(function (it) { items.push(it); render(); });
      });
    }, Promise.resolve()).then(function () { say(''); }, function (e) {
      say(e && e.user ? e.message : 'לא הצלחתי להכין את הקובץ.', true);
      render();
    });
  }

  function render() {
    chosen.innerHTML = '';
    items.forEach(function (it, i) {
      var li = document.createElement('li');
      var n = document.createElement('span');
      n.className = 'fname';
      n.textContent = it.name;
      var meta = document.createElement('span');
      meta.className = 'fmeta';
      var kinds = it.parts.map(function (p) { return p.text ? 'טקסט' : p.mime === 'application/pdf' ? 'PDF' : 'תמונה'; });
      meta.textContent = (kinds.length > 1 ? kinds.length + ' חלקים' : kinds[0]) + ', ' + size(itemBytes(it));
      var x = document.createElement('button');
      x.type = 'button';
      x.className = 'remove';
      x.textContent = 'הסרה';
      x.setAttribute('aria-label', 'הסרת ' + it.name);
      x.onclick = function () { items.splice(i, 1); render(); };
      li.appendChild(n); li.appendChild(meta); li.appendChild(x);
      chosen.appendChild(li);
    });
    send.disabled = !items.length;
  }

  drop.addEventListener('click', function (e) { if (e.target.tagName !== 'LABEL' && e.target.tagName !== 'INPUT') input.click(); });
  drop.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); input.click(); } });
  input.addEventListener('change', function () { add(input.files); input.value = ''; });
  ['dragenter', 'dragover'].forEach(function (t) {
    drop.addEventListener(t, function (e) { e.preventDefault(); drop.classList.add('over'); });
  });
  ['dragleave', 'drop'].forEach(function (t) {
    drop.addEventListener(t, function (e) { e.preventDefault(); drop.classList.remove('over'); });
  });
  drop.addEventListener('drop', function (e) { add(e.dataTransfer.files); });
  document.addEventListener('paste', function (e) {
    if (!result.hidden || /^(TEXTAREA|INPUT)$/.test((e.target || {}).tagName || '')) return;
    var cd = e.clipboardData;
    if (!cd) return;
    var files = Array.prototype.slice.call(cd.files || []);
    if (files.length) {
      e.preventDefault();
      add(files.map(function (f) {
        pastedCount++;
        return new File([f], 'צילום מסך ' + pastedCount + '.' + (f.type.split('/')[1] || 'png'), { type: f.type });
      }));
      return;
    }
    var t = cd.getData('text/plain');
    if (t && t.trim().length > 20) {
      e.preventDefault();
      if (items.length >= MAX_ITEMS) { say('אפשר לשלוח עד ' + MAX_ITEMS + ' קבצים.', true); return; }
      pastedCount++;
      items.push({ name: 'טקסט מודבק ' + pastedCount, parts: [{ text: t.trim().slice(0, MAX_TEXT) }] });
      render();
    }
  });

  // ---------------------------------------------------------------- שליחה

  function language() { return (form.querySelector('input[name=lang]:checked') || {}).value === 'csharp' ? 'csharp' : 'java'; }

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    if (!items.length) return;
    var total = items.reduce(function (a, it) { return a + itemBytes(it); }, 0);
    if (total > MAX_TOTAL) { say('הקבצים גדולים מדי ביחד (עד 14MB). מורידים קבצים או שולחים PDF במקום צילומים.', true); return; }
    if (!window.DSVizScaffold) { say('הדף לא נטען עד הסוף. מרעננים ומנסים שוב.', true); return; }
    var texts = [], names = [], blobs = [], k = 0;
    items.forEach(function (it) {
      var n = 0;
      it.parts.forEach(function (p) {
        if (p.text) texts.push({ name: it.name, text: p.text });
        else {
          k++;
          names.push(it.parts.length > 1 ? it.name + ' (' + (++n) + ')' : it.name);
          blobs.push([new Blob([p.blob], { type: p.mime }), 'file' + k + '.' + (p.mime === 'application/pdf' ? 'pdf' : p.mime.split('/')[1])]);   // השם האמיתי (בעברית) — ב-meta
        }
      });
    });
    var lang = language();
    var meta = { client: 'dsviz-site/1.0', language: lang, note: (form.note.value || '').trim().slice(0, 600), texts: texts, names: names };
    var body = new FormData();
    body.append('meta', new Blob([JSON.stringify(meta)], { type: 'application/json' }));
    blobs.forEach(function (f) { body.append('file', f[0], f[1]); });

    send.disabled = true;
    form.classList.add('busy');
    var t0 = Date.now();
    var tick = setInterval(function () {
      var s = Math.round((Date.now() - t0) / 1000);
      say('קורא את השאלה ובונה את הקוד… ' + s + ' שניות' + (s > 40 ? '. בשאלות ארוכות זה לוקח עד שתי דקות.' : ''));
    }, 1000);
    say('שולח…');
    var ctl = 'AbortController' in window ? new AbortController() : null;
    var timer = setTimeout(function () { if (ctl) ctl.abort(); }, 200000);
    fetch(SERVER + '/v1/site/exercise', { method: 'POST', body: body, signal: ctl ? ctl.signal : undefined })
      .then(function (r) {
        return r.json().catch(function () { return {}; }).then(function (j) {
          if (!r.ok || !j.ok) throw fail(j.error || ('השרת החזיר שגיאה (' + r.status + '). נסו שוב בעוד דקה.'));
          return j.exercise;
        });
      })
      .then(function (raw) {
        say('');
        show(raw, items.map(function (it) { return it.name; }));
      })
      .catch(function (e) {
        if (e && e.user) say(e.message, true);
        else if (e && e.name === 'AbortError') say('השרת לא ענה בזמן. נסו שוב, או שלחו פחות עמודים.', true);
        else if (!navigator.onLine) say('אין חיבור לאינטרנט. בודקים את החיבור ומנסים שוב.', true);
        else say('לא הצלחתי להגיע לשרת. נסו שוב בעוד דקה.', true);
      })
      .then(function () {
        clearInterval(tick);
        clearTimeout(timer);
        send.disabled = !items.length;
        form.classList.remove('busy');
      });
  });

  // ---------------------------------------------------------------- התוצאה

  var tabs = $('[data-tabs]'), codeEl = $('[data-code]'), withSol = $('[data-with-sol]');
  var shown = 0;

  function files() {
    var S = window.DSVizScaffold, e = current.exercise;
    return S.build(e, current.pkg, current.sources, withSol.checked);
  }

  function show(raw, sources) {
    var S = window.DSVizScaffold;
    var e = S.read(raw);
    current = { exercise: e, sources: sources, pkg: S.folderName(e.topic, new Set(), e.cs) };
    $('[data-title]').textContent = e.title || 'התרגיל';
    $('[data-summary]').textContent = e.summary || '';
    withSol.checked = false;
    shown = 0;
    drawFiles();
    var where = e.cs
      ? 'ב-Visual Studio: יוצרים פרויקט Console בשם ' + current.pkg + ', ומוסיפים לו את הקבצים. הספרייה Unit4.CollectionsLib מגיעה עם DSViz.'
      : 'ב-Eclipse: מחלצים את ה-ZIP לתיקייה src של הפרויקט (נוצרת חבילה ' + current.pkg + '). הספרייה unit4.collectionsLib נוספת לפרויקט לבד כש-DSViz מותקן.';
    $('[data-where]').textContent = where;

    var exp = $('[data-expected-box]');
    exp.hidden = !e.expectedOutput;
    $('[data-expected]').textContent = e.expectedOutput || '';

    var sols = e.tasks.filter(function (t) { return t.solution; });
    $('[data-sols-box]').hidden = !sols.length;
    var box = $('[data-sols]');
    box.innerHTML = '';
    sols.forEach(function (t) {
      var d = document.createElement('details');
      var s = document.createElement('summary');
      s.textContent = 'סעיף ' + (t.label || '') + (t.methodName ? ': ' + t.methodName : '');
      var pre = document.createElement('pre');
      pre.dir = 'ltr';
      pre.className = 'code';
      pre.textContent = t.solution;
      d.appendChild(s); d.appendChild(pre);
      box.appendChild(d);
    });

    var others = e.others || [];
    $('[data-others-box]').hidden = !others.length;
    $('[data-others]').innerHTML = '';
    others.forEach(function (o) {
      var li = document.createElement('li');
      var b = document.createElement('b');
      b.textContent = (o[0] || '') + (o[0] ? '. ' : '');
      li.appendChild(b);
      li.appendChild(document.createTextNode(o[1] || ''));
      $('[data-others]').appendChild(li);
    });

    form.hidden = true;
    result.hidden = false;
    result.scrollIntoView({ behavior: 'smooth', block: 'start' });
    $('#r-title').setAttribute('tabindex', '-1');
    $('#r-title').focus({ preventScroll: true });
  }

  function drawFiles() {
    var list = files();
    tabs.innerHTML = '';
    list.forEach(function (f, i) {
      var b = document.createElement('button');
      b.type = 'button';
      b.setAttribute('role', 'tab');
      b.setAttribute('aria-selected', String(i === shown));
      b.dir = 'ltr';
      b.textContent = f.className + (f.cs ? '.cs' : '.java');
      b.onclick = function () { shown = i; drawFiles(); };
      tabs.appendChild(b);
    });
    var f = list[Math.min(shown, list.length - 1)];
    codeEl.textContent = f ? f.code : '';
  }

  withSol.addEventListener('change', drawFiles);

  $('[data-copy]').addEventListener('click', function () {
    var b = this, t = codeEl.textContent;
    var done = function () { b.textContent = 'הועתק'; setTimeout(function () { b.textContent = 'העתקה'; }, 1600); };
    if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(t).then(done, function () {});
    else {
      var r = document.createRange(); r.selectNodeContents(codeEl);
      var s = getSelection(); s.removeAllRanges(); s.addRange(r);
      try { document.execCommand('copy'); done(); } catch (e) { /* מסמנים, והתלמיד מעתיק לבד */ }
    }
  });

  $('[data-zip]').addEventListener('click', function () {
    var b = this;
    b.disabled = true;
    loadZip().then(function (JSZip) {
      var z = new JSZip(), dir = z.folder(current.pkg);
      files().forEach(function (f) { dir.file(f.className + (f.cs ? '.cs' : '.java'), f.code); });
      return z.generateAsync({ type: 'blob' });
    }).then(function (blob) {
      var a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = current.pkg + (withSol.checked ? '-solution' : '') + '.zip';
      document.body.appendChild(a);
      a.click();
      setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
    }).catch(function (e) {
      alert(e && e.user ? e.message : 'לא הצלחתי ליצור את קובץ ה-ZIP.');
    }).then(function () { b.disabled = false; });
  });

  $('[data-again]').addEventListener('click', function () {
    items = [];
    current = null;
    render();
    result.hidden = true;
    form.hidden = false;
    form.note.value = '';
    say('');
    drop.focus();
  });

  render();
})();
