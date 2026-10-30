/*
 * מנוע סרטוני ההדרכה של DSViz. כל סרטון הוא תסריט (scripts/<id>.js) שמתאר מה קורה ומתי:
 * העכבר זז ולוחץ, נפתח חלון (צילום אמיתי של החלון של התוסף), נכנס קוד לעורך, חלון ההרצה מתקדם צעד אחר צעד...
 * render(t) מצייר את המצב בזמן t (בלי מצב פנימי) — וכך shoot.js מצלם כל פריים בנפרד, כמו בסרטון הפרומו.
 */
(function () {
  const $ = id => document.getElementById(id);
  const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
  const p = (t, a, b) => clamp((t - a) / (b - a));
  const lerp = (a, b, x) => a + (b - a) * x;
  const eo = x => 1 - Math.pow(1 - x, 3);
  const eo5 = x => 1 - Math.pow(1 - x, 5);
  const eio = x => x < .5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
  const back = x => { const c1 = 1.8, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); };
  function css(el, op, tf, blur) {
    el.style.opacity = op;
    el.style.transform = tf || 'none';
    el.style.filter = blur > 0.05 ? `blur(${blur}px)` : 'none';
  }
  function slam(t, t0, from = 2.4, dur = .42) { const x = p(t, t0, t0 + dur); return { op: p(t, t0, t0 + .1), s: lerp(from, 1, eo5(x)), blur: (1 - eo(x)) * 24 }; }
  function burst(t, t1, dur = .3) { const x = p(t, t1, t1 + dur); return { op: 1 - x, s: 1 + eio(x) * .9, blur: x * 22 }; }
  function show(el, t, t0, t1, opt = {}) {
    if (t < t0 - .01 || t > t1 + .4) { el.style.opacity = 0; return; }
    const a = slam(t, t0, opt.from ?? 2.4, opt.dur ?? .42), b = burst(t, t1);
    css(el, a.op * b.op, `scale(${a.s * b.s}) ${opt.extra || ''}`, a.blur + b.blur);
  }

  const APP_W = 1300, APP_H = 880, LINE_H = 28, GUTTER = 64;
  const LENS_H = 22;   // שורת "💡 הצעה לפתרון" מעל שורה בקוד (CodeLens ב-VS Code / code mining ב-Eclipse)
  /** כמה פיקסלים שורות ה-lens דוחפות למטה את השורה l (lens שמעל l או מעל שורה לפניה) */
  const lensOff = (items, l) => (items || []).filter(x => x.line <= l).length * LENS_H;
  // הסביבה: eclipse (ברירת מחדל) / vscode / vs — המסגרת, הכפתורים והתפריט של כל אחת
  const IDE = new URLSearchParams(location.search).get('ide') || 'eclipse';
  const SKINS = {
    vscode: `
      <div class="titlebar"><div class="menus"><span>File</span><span>Edit</span><span>Selection</span><span>View</span><span>Go</span><span>Run</span><span>Terminal</span><span>Help</span></div>
        <div class="name" id="ideTitle">Main.java — DataStructures — Visual Studio Code</div></div>
      <div class="act"><i class="on" id="actFiles"></i><i></i><i></i><i></i><i></i><i id="actDs" style="background:#7c5cf0"></i></div>
      <div class="side"><div class="h" id="sideH">EXPLORER</div><div class="sec" id="sideSec">⌄ DATASTRUCTURES</div><div id="tree"></div>
        <div class="dsview" id="dsview">יש דף תרגיל (PDF, Word או צילום מסך)? שולחים אותו ומקבלים קוד מוכן — עם 💡 הצעה לפתרון לכל סעיף:
          <span class="b" id="dsSubmit">שליחת תרגיל...</span>בחרו מבנה — וייפתח תרגיל מוכן, ונשאר רק לכתוב את הפעולה:
          <span class="b alt" id="dsStack">מחסנית (Stack)</span><span class="b alt" id="dsQueue">תור (Queue)</span><span class="b alt" id="dsList">רשימה מקושרת (Node)</span>
          <span class="b alt" id="dsTree">עץ בינארי (BinNode)</span><span class="b" id="dsExamples">דוגמאות פתורות</span></div></div>
      <div class="ed"><div class="tabs"><div class="tab" id="tabName">Main.java</div>
          <div class="acts"><span class="ic" id="tbSubmit" style="background:#7c5cf0">➤</span><span class="ic" id="tbNew" style="background:#2f6fed">+</span>
            <span class="ic" id="tbAdd" style="background:#7c3aed">▦</span><span class="ic" id="tbRun" style="background:#16a34a">▶</span><span class="ic" id="tbHelp" style="background:#f59e0b">?</span></div></div>
        <div class="crumbs" id="crumbs">src › Main.java</div>
        <div id="code"><div id="lines"></div><div id="caret"></div></div></div>
      <div class="status"><span class="sbi">⎇ main</span><span class="sbi">⊗ 0 ⚠ 0</span>
        <span class="sbi" id="sbSubmit">➤ שליחת תרגיל</span><span class="sbi" id="sbRun">▶ הרצה ויזואלית</span><span class="sbi" id="sbAdd">⊕ הוסף מבנה נתונים</span><span class="sbi" id="sbHelp">?</span></div>`,
    vs: `
      <div class="titlebar"><div class="logo"></div><div class="menus"><span>File</span><span>Edit</span><span>View</span><span>Git</span><span>Project</span><span>Build</span><span>Debug</span><span>Test</span><span>Analyze</span><span>Tools</span><span>Extensions</span><span>Window</span><span>Help</span></div>
        <div class="sln" id="ideTitle">DataStructures-CS</div></div>
      <div class="toolbar"><div class="tb-ico"></div><div class="tb-ico"></div><div class="tb-ico"></div><div class="tb-sep"></div><div class="tb-ico"></div><div class="tb-ico"></div>
        <div class="tb-sep"></div><div class="tb-btn" style="font-size:15px">Debug ▾</div><div class="tb-btn" style="font-size:15px">Any CPU ▾</div>
        <div class="tb-btn run" id="tbRun"><i>▶</i>הרצה ויזואלית ▾</div><div class="tb-sep"></div><div class="tb-ico"></div><div class="tb-ico"></div><div class="tb-ico"></div></div>
      <div class="body">
        <div class="editor"><div class="tabs"><div class="tab" id="tabName">Program.cs</div></div>
          <div id="code"><div id="lines"></div><div id="caret"></div></div></div>
        <div class="explorer"><div class="h">Solution Explorer</div><div id="tree"></div></div>
      </div>
      <div class="taskbar"><span class="tk" id="tkStart" style="background:#2f6fed">⊞</span><span class="tk" style="background:#865fc5">∞</span><span class="tk" style="background:#e5b84b">▭</span>
        <span class="tk" style="background:#0ea5e9">◉</span></div>`
  };
  // התפריט של כל סביבה: Eclipse — "מבני נתונים" בשורת התפריטים; VS Code — לחיצה ימנית בעורך ← "מבני נתונים";
  // Visual Studio — תפריט התחל של Windows ← התיקייה "מבני נתונים" (הקיצורים שההתקנה יוצרת)
  const MENUS = {
    eclipse: ['תרגיל חדש: מחסנית (Stack)...', 'תרגיל חדש: תור (Queue)...', 'תרגיל חדש: רשימה מקושרת (Node)...', 'תרגיל חדש: עץ בינארי (BinNode)...',
      'תרגיל חדש: עץ חיפוש בינארי...', 'תרגיל חדש: מערך...', 'תרגיל חדש: מערך דו-ממדי...', '---', 'שליחת תרגיל (PDF / Word / תמונה)...', 'הצעה לפתרון לסעיף שבו נמצא הסמן...',
      '---', 'הוסף מבנה נתונים...', '---', 'הרצה ויזואלית', '---', 'דוגמאות פתורות...', 'הדרכה לשימוש (?)...'],
    vscode: ['שליחת תרגיל', 'הצעה לפתרון', '---', 'הוסף מבנה נתונים', 'הוסף מבנה ‹', '---', 'הרצה ויזואלית', '---', 'הדרכה לשימוש'],
    vs: ['תרגיל חדש ב-C#', 'הוסף מבנה נתונים ל-C#', 'שליחת תרגיל ב-C#', 'הצעה לפתרון ב-C#', '---', 'תרגילי C# (Visual Studio)', 'הדרכה לשימוש']
  };
  const DLG_TITLE = 32;
  const VIEW = { w: 1440, h: 828, s: 1240 / 1440, next: [1280, 30] };   // חלון ההרצה (צילום 1440x828), הכפתור "הבא"

  // ------------------------------------------------------------ הדגשת תחביר (בערך כמו ב-Eclipse)
  const KW = new Set(('public private protected static void int boolean char double new return if else while for class import null true false this final do break continue '
    + 'using namespace string bool var foreach in internal out ref').split(' '));
  const TY = new Set('Stack Queue Node BinNode Student Car String Integer Double Character System Math Arrays Console Helpers'.split(' '));
  function esc(s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
  function highlight(line) {
    let out = '', i = 0;
    const ci = line.indexOf('//');
    let code = line, cm = '';
    // הערה: מה-// הראשון שלא בתוך מחרוזת
    let inStr = false;
    for (let k = 0; k < line.length - 1; k++) {
      if (line[k] === '"' && line[k - 1] !== '\\') inStr = !inStr;
      if (!inStr && line[k] === '/' && line[k + 1] === '/') { code = line.slice(0, k); cm = line.slice(k); break; }
    }
    const re = /("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\b[A-Za-z_]\w*\b|\d+(?:\.\d+)?|\s+|.)/g;
    let m;
    while ((m = re.exec(code))) {
      const w = m[0];
      if (w[0] === '"' || w[0] === "'") out += `<span class="st">${esc(w)}</span>`;
      else if (KW.has(w)) out += `<span class="kw">${w}</span>`;
      else if (TY.has(w)) out += `<span class="ty">${w}</span>`;
      else out += esc(w);
    }
    if (cm) out += /[֐-׿]/.test(cm) ? `<span class="cm">//</span><span class="cm hebcm">${esc(cm.slice(2))}</span>` : `<span class="cm">${esc(cm)}</span>`;
    return out;
  }

  // ------------------------------------------------------------ בניית התסריט
  class Builder {
    constructor(meta) {
      this.meta = meta;
      this.ev = [];
      this.now = 0;
      this.cur = { x: 760, y: 560 };
      this.scroll = 0;
      this.lenses = [];
      this.codeLines = [];
      this.dlg = null;      // { asset, x, y }
      this.view = null;     // { x, y, s }
      this.introEnd = 0;
      this.outroAt = null;
    }
    add(e) { e.t = e.t ?? this.now; this.ev.push(e); return e; }
    wait(s) { this.now += s; return this; }
    at(t) { this.now = t; return this; }

    // --- מטרות (נקודות במסך של האפליקציה)
    pt(target) {
      if (typeof target === 'object') return target;
      const [kind, ...rest] = target.split(':');
      const arg = rest.join(':');
      if (kind === 'tb' || kind === 'sb' || kind === 'ds' || kind === 'tk') {   // כפתור: tb:add (סרגל), sb:run (שורת המצב של VS Code), ds:examples (הלוח של VS Code), tk:start
        return this.rectOf(tbEl(kind, arg), 'center');
      }
      if (kind === 'menu') return this.rectOf(menuAnchor(), 'center');   // פותחים את התפריט של הסביבה
      if (kind === 'line') {   // line:L או line:L:C (C = עמודה, מ-0)
        const [l, c] = arg.split(':').map(Number);
        const r = this.codeRect();
        return { x: r.x + GUTTER + (c || 0) * this.charW + (c === undefined ? 120 : 0), y: r.y + (l - 1 - this.scroll) * LINE_H + lensOff(this.lenses, l) + LINE_H / 2 };
      }
      if (kind === 'lens') { const q = this.lensRect(+arg); return { x: q.x + q.w / 2, y: q.y + q.h / 2 }; }
      if (kind === 'menuitem') {   // menuitem:טקסט (חלק מהטקסט)
        const m = this.menuGeom();
        const i = MENU_ITEMS.findIndex(t => t !== '---' && t.includes(arg));
        if (i < 0) throw new Error('menu item not found: ' + arg);
        return { x: m.left + MENU_W / 2, y: m.top + m.ys[i] + MI_H / 2 };
      }
      if (kind === 'tree') {   // tree:שם הקובץ
        for (const row of document.querySelectorAll('#tree .row')) if (row.textContent.trim() === arg || (row.querySelector('span:last-child') || row).textContent.trim() === arg) return this.rectOf(row.querySelector('span:last-child') || row, 'center');
        throw new Error('tree row not found: ' + arg);
      }
      if (kind === 'view') {
        const v = this.view;
        if (arg === 'next') return { x: v.x + VIEW.next[0] * v.s, y: v.y + DLG_TITLE + VIEW.next[1] * v.s };
        const [xx, yy] = arg.split(',').map(Number);   // view:x,y — נקודה בצילום של חלון ההרצה
        return { x: v.x + xx * v.s, y: v.y + DLG_TITLE + yy * v.s };
      }
      if (kind === 'dlg') {   // dlg:chip:טקסט  או  dlg:spin@0.4,0.5 (נקודה יחסית בתוך הרכיב)
        const [sel, frac] = arg.split('@');
        const r = this.dlgRect(sel);
        const [fx, fy] = frac ? frac.split(',').map(Number) : [.5, .5];
        return { x: r.x + r.w * fx, y: r.y + r.h * fy };
      }
      throw new Error('bad target ' + target);
    }
    /** מלבן של רכיב בחלון: chip:טקסט · round:טקסט · check:חלק · text:אינדקס · label:חלק · spin */
    dlgRect(arg) {
      const d = this.dlg, a = ASSETS[d.asset];
      const [k, ...rr] = arg.split(':');
      const v = rr.join(':');
      const types = { chip: ['Chip'], round: ['Round'], check: ['Button'], text: ['Text'], label: ['Label'], spin: ['Spinner'], combo: ['Combo'] }[k];
      const c = a.controls.find(c => types.includes(c.type) && (k === 'text' ? String(c.index) === v : k === 'spin' || k === 'combo' ? true : k === 'chip' || k === 'round' ? c.text === v : (c.text || '').includes(v)));
      if (!c) throw new Error('control not found ' + arg + ' in ' + d.asset);
      return { x: d.x + c.x, y: d.y + (d.th ?? DLG_TITLE) + c.y, w: c.w, h: c.h };
    }
    /** מלבן שמכיל כמה רכיבים בחלון (להדגשה / זום) */
    area(sels, pad = 8, growH = 0) {
      const rs = sels.map(x => this.dlgRect(x));
      const x0 = Math.min(...rs.map(r => r.x)), y0 = Math.min(...rs.map(r => r.y));
      const x1 = Math.max(...rs.map(r => r.x + r.w)), y1 = Math.max(...rs.map(r => r.y + r.h));
      return { x: x0 - pad, y: y0 - pad, w: x1 - x0 + 2 * pad, h: y1 - y0 + 2 * pad + growH };
    }
        rectOf(el, mode) {
      const app = $('cam').getBoundingClientRect(), r = el.getBoundingClientRect();
      const x = r.left - app.left, y = r.top - app.top;
      return mode === 'center' ? { x: x + r.width / 2, y: y + r.height / 2 } : { x, y, w: r.width, h: r.height };
    }
    menuGeom() {
      const ds = this.rectOf(menuAnchor(), 'rect');
      const ys = []; let y = MENU_PAD;
      for (const t of MENU_ITEMS) { ys.push(y); y += t === '---' ? SEP_H : MI_H; }
      if (IDE === 'vs') return { left: Math.max(10, ds.x - 40), top: ds.y - y - 6, ys, h: y + MENU_PAD };   // תפריט התחל — נפתח למעלה
      if (IDE === 'vscode') return { left: ds.x + ds.w / 2 - MENU_W, top: ds.y + ds.h / 2, ys, h: y + MENU_PAD };   // לחיצה ימנית בעורך
      return { left: Math.max(10, ds.x + ds.w - MENU_W + 40), top: ds.y + ds.h + 2, ys, h: y + MENU_PAD };
    }
    /** VS Code: הלוח בצד — קבצים (explorer) או הלוח של מבני נתונים (dsviz) */
    side(view) {
      this.add({ type: 'side', view });
      const ds = view === 'dsviz';   // גם עכשיו (בזמן הבנייה) — כדי שהמדידות של הכפתורים בלוח יהיו נכונות
      $('tree').style.display = ds ? 'none' : 'block';
      $('dsview').style.display = ds ? 'block' : 'none';
      $('sideSec').style.display = ds ? 'none' : 'flex';
      return this;
    }
    /** מרכז של רכיב במסגרת (למשל '#actDs') — מטרה ללחיצה */
    el(sel) { const r = this.rectOf(document.querySelector(sel), 'rect'); return { x: r.x + r.w / 2, y: r.y + r.h / 2 }; }
    menuOpen() { this.add({ type: 'menu', geom: this.menuGeom() }); this.now += .35; return this; }
    menuHot(text) { const i = MENU_ITEMS.findIndex(t => t !== '---' && t.includes(text)); this.add({ type: 'menuHot', idx: i }); return this; }
    menuClose(after = .3) { this.add({ type: 'menuClose' }); this.now += after; return this; }
    /** מלבן (בקואורדינטות האפליקציה) של מטרה — להדגשה סביב מה שלוחצים עליו */
    rectOfTarget(target) {
      if (typeof target === 'object') return target.w ? target : { x: target.x - 24, y: target.y - 18, w: 48, h: 36 };
      const [kind, ...rest] = target.split(':');
      const arg = rest.join(':');
      if (kind === 'tb' || kind === 'sb' || kind === 'ds' || kind === 'tk') return this.rectOf(tbEl(kind, arg), 'rect');
      if (kind === 'menu') return this.rectOf(menuAnchor(), 'rect');
      if (kind === 'menuitem') { const m = this.menuGeom(), i = MENU_ITEMS.findIndex(t => t !== '---' && t.includes(arg)); return { x: m.left + 4, y: m.top + m.ys[i], w: MENU_W - 8, h: MI_H }; }
      if (kind === 'dlg') { const r = this.dlgRect(arg.split('@')[0]); return { x: r.x, y: r.y, w: r.w, h: r.h }; }
      if (kind === 'view') { const q = this.pt(target); return { x: q.x - 62, y: q.y - 20, w: 124, h: 40 }; }
      if (kind === 'line') { const q = this.pt(target), r = this.codeRect(); return { x: r.x + GUTTER, y: q.y - LINE_H / 2, w: r.w - GUTTER - 6, h: LINE_H }; }
      if (kind === 'lens') return this.lensRect(+arg);
      if (kind === 'tree') { const q = this.pt(target); return { x: q.x - 90, y: q.y - 14, w: 180, h: 28 }; }
      const q = this.pt(target); return { x: q.x - 24, y: q.y - 18, w: 48, h: 36 };
    }
    /** העכבר מגיע למטרה, מסביבה מסגרת מהבהבת ותווית "לחץ כאן", מחכים שיספיקו לקרוא — ואז לוחצים */
    press(target, label, opt = {}) {
      const r = this.rectOfTarget(target), move = opt.move ?? .9, hold = opt.hold ?? 1.5;
      const low = r.y > APP_H - 120;   // שורת המצב / שורת המשימות למטה — הבועה מעל
      const side = opt.side || (typeof target === 'string' && target.startsWith('menuitem') ? (IDE === 'eclipse' ? 'right' : 'left') : low ? 'top' : typeof target === 'string' && target.startsWith('dlg:') && r.y < 150 ? 'bottom' : typeof target === 'string' && (/^(tb|ds):/.test(target) || target.startsWith('view:') || target.startsWith('menu')) ? 'bottom' : 'top');
      this.add({ type: 'hl', rect: { x: r.x - 5, y: r.y - 4, w: r.w + 10, h: r.h + 8 }, dur: move + hold + .3 });
      const at = side === 'top' ? { x: r.x + r.w / 2, y: r.y - 4 } : side === 'bottom' ? { x: r.x + r.w / 2, y: r.y + r.h + 4 } : side === 'left' ? { x: r.x - 4, y: r.y + r.h / 2 } : { x: r.x + r.w + 4, y: r.y + r.h / 2 };
      this.add({ type: 'bubble', text: `<span class="cta">${opt.verb || 'לחץ כאן'}</span>${label ? '<span class="lbl">' + label + '</span>' : ''}`, at, side, kind: 'click', dur: move + hold + .3 });
      this.move(target, move).wait(hold).click(opt.after ?? .35);
      return this;
    }
    /** שמים את הסמן (קליק בקוד) במקום מדויק — עם הסבר */
    place(target, label, opt = {}) {
      const r = this.rectOfTarget(target), move = opt.move ?? 1.0, hold = opt.hold ?? 1.8;
      this.add({ type: 'hl', rect: { x: r.x, y: r.y - 2, w: r.w, h: r.h + 4 }, dur: move + hold + .3 });
      this.add({ type: 'bubble', text: `<span class="cta">${opt.verb || 'שים את הסמן כאן'}</span>${label ? '<span class="lbl">' + label + '</span>' : ''}`, at: { x: r.x + r.w * (opt.bx ?? .5), y: opt.side === 'top' ? r.y - 4 : r.y + r.h + 4 }, side: opt.side || 'bottom', kind: opt.kind || 'place', dur: move + hold + .3 });
      this.move(target, move).wait(hold).click(.3);
      return this;
    }
    /** מסבירים משהו בלי ללחוץ (רק מסמנים ומסבירים) */
    explain(target, text, opt = {}) {
      const rr = typeof target === 'string' && /^(el:|lines:|view:\d)/.test(target) ? this.dlgRectOrEl(target) : this.rectOfTarget(target);
      const dur = opt.dur ?? 3.0;
      this.add({ type: 'hl', rect: { x: rr.x - 4, y: rr.y - 4, w: rr.w + 8, h: rr.h + 8 }, dur });
      const side = opt.side || 'bottom';
      const at = side === 'top' ? { x: rr.x + rr.w / 2, y: rr.y - 4 } : side === 'bottom' ? { x: rr.x + rr.w / 2, y: rr.y + rr.h + 4 } : side === 'left' ? { x: rr.x - 4, y: rr.y + rr.h / 2 } : { x: rr.x + rr.w + 4, y: rr.y + rr.h / 2 };
      this.add({ type: 'bubble', text, at, side, kind: opt.kind || 'info', dur });
      this.now += opt.wait ?? dur;
      return this;
    }
    codeRect() { return this.rectOf($('code'), 'rect'); }

    // --- פעולות
    intro(dur = 2.8) { this.introEnd = dur; this.now = dur + .6; return this; }
    step(i, extra = 0) { this.add({ type: 'step', idx: i }); this.now += extra; return this; }
    files(rows) { this.add({ type: 'tree', rows }); $('tree').innerHTML = treeHtml(rows); return this; }
    title(text) { this.add({ type: 'title', text }); return this; }
    /** שורות "💡 הצעה לפתרון" מעל שורות בקוד: [{ line, text }] — השורות שאחריהן זזות למטה; lens([]) מוחק. מטרה: 'lens:<line>' */
    lens(items) { this.lenses = items.map(x => ({ line: x.line, text: x.text })); this.add({ type: 'lens', items: this.lenses }); return this; }
    lensRect(l) {
      const r = this.codeRect(), it = this.lenses.find(x => x.line === l);
      if (!it) throw new Error('no lens above line ' + l);
      const ind = ((this.codeLines[l - 1] || '').match(/^ */) || [''])[0].length;
      return { x: r.x + GUTTER + ind * this.charW, y: r.y + (l - 1 - this.scroll) * LINE_H + lensOff(this.lenses, l) - LENS_H, w: it.text.length * 8.6 + 34, h: LENS_H };
    }
    code(lines, opt = {}) {
      this.codeLines = lines.slice();
      this.add({ type: 'code', lines: lines.slice(), file: opt.file, newFrom: opt.newFrom, newTo: opt.newTo, edit: opt.edit });
      if (opt.file) this.add({ type: 'tab', name: opt.file, crumbs: opt.crumbs });
      return this;
    }
    caret(line, col) { this.add({ type: 'caret', line, col }); return this; }
    noCaret() { this.add({ type: 'caret', line: null }); return this; }
    scrollTo(line, dur = .6) { this.add({ type: 'scroll', from: this.scroll, to: line, dur }); this.scroll = line; this.now += dur; return this; }
    move(target, dur = .8) {
      const to = this.pt(target);
      this.add({ type: 'move', from: { ...this.cur }, to, dur });
      this.cur = { ...to };
      this.now += dur;
      return this;
    }
    click(after = .35) { this.add({ type: 'click', x: this.cur.x, y: this.cur.y }); CLICKS.push(this.now); this.now += after; return this; }
    moveClick(target, dur = .8, after = .35) { return this.move(target, dur).wait(.12).click(after); }
    hot(el, dur = .5) { this.add({ type: 'hot', el, dur }); return this; }
    dialog(asset, title, opt = {}) {
      const a = ASSETS[asset];
      let x = opt.x ?? Math.round((APP_W - a.w) / 2), y = opt.y ?? Math.max(40, Math.round((APP_H - a.h - DLG_TITLE) / 2) + 20), th = DLG_TITLE;
      if (opt.dock) {   // VS Code: ה-Webview נפתח כלשונית בעורך (בלי מסגרת של חלון)
        const r = this.rectOf(document.querySelector('#ide .ed'), 'rect');
        x = r.x; y = r.y + 35; th = 0;
        this.add({ type: 'tab', name: title });
      }
      if (opt.bare) th = 0;   // בלי מסגרת של חלון (למשל ה-QuickPick של VS Code) — x/y מהתסריט
      this.dlg = { asset, x, y, th };
      this.add({ type: 'dialog', asset, x, y, title, w: a.w, h: a.h, dock: !!opt.dock, bare: !!opt.bare });
      this.now += opt.wait ?? .7;
      return this;
    }
    swap(asset) { this.dlg = { ...this.dlg, asset }; this.add({ type: 'swap', asset }); return this; }
    closeDialog(after = .5) { this.add({ type: 'dialogClose' }); this.now += after; return this; }
    type(field, text, dur, after) {   // field: text:אינדקס
      const r = this.dlgRect(field);
      dur = dur ?? Math.max(.6, text.length * .07);
      this.add({ type: 'typing', rect: r, text, dur });
      this.now += dur;
      if (after) this.swap(after);
      return this;
    }
    viewer(img, title, opt = {}) {
      const s = opt.s ?? VIEW.s;
      const x = opt.x ?? Math.round((APP_W - VIEW.w * s) / 2), y = opt.y ?? 46;
      this.view = { x, y, s };
      this.add({ type: 'viewer', img, title, x, y, s });
      this.now += opt.wait ?? .8;
      return this;
    }
    frame(img) { this.add({ type: 'vframe', img }); return this; }
    /** לוחצים "הבא" כמה פעמים ומחליפים צילומים */
    steps(imgs, gap = .55) {
      for (const im of imgs) { this.move('view:next', .25).click(.05).frame(im).wait(gap); }
      return this;
    }
    closeViewer(after = .5) { this.add({ type: 'viewerClose' }); this.now += after; return this; }
    hl(rect, dur = 1.6, label) { this.add({ type: 'hl', rect: typeof rect === 'string' ? this.dlgRectOrEl(rect) : rect, dur }); return this; }
    dlgRectOrEl(s) {
      if (s.startsWith('dlg:')) { const r = this.dlgRect(s.slice(4)); return { x: r.x - 8, y: r.y - 6, w: r.w + 16, h: r.h + 12 }; }
      if (s.startsWith('el:')) { const r = this.rectOf(document.querySelector(s.slice(3)), 'rect'); return { x: r.x - 6, y: r.y - 4, w: r.w + 12, h: r.h + 8 }; }
      if (s.startsWith('lines:')) {
        const [a, b] = s.slice(6).split('-').map(Number), r = this.codeRect();
        const oa = lensOff(this.lenses, a), ob = lensOff(this.lenses, b);
        return { x: r.x + GUTTER - 6, y: r.y + (a - 1 - this.scroll) * LINE_H + oa - 2, w: r.w - GUTTER - 10, h: (b - a + 1) * LINE_H + (ob - oa) + 4 };
      }
      if (s.startsWith('view:')) {
        const [x, y, w, h] = s.slice(5).split(',').map(Number), v = this.view;
        return { x: v.x + x * v.s, y: v.y + DLG_TITLE + y * v.s, w: w * v.s, h: h * v.s };
      }
      throw new Error('bad rect ' + s);
    }
    bubble(text, target, opt = {}) {
      let at;
      if (typeof target === 'string' && /^(dlg:|el:|lines:|view:\d)/.test(target)) {
        const r = this.dlgRectOrEl(target);
        const side = opt.side || 'top';
        at = side === 'top' ? { x: r.x + r.w / 2, y: r.y } : side === 'bottom' ? { x: r.x + r.w / 2, y: r.y + r.h } : side === 'left' ? { x: r.x, y: r.y + r.h / 2 } : { x: r.x + r.w, y: r.y + r.h / 2 };
      } else at = this.pt(target);
      this.add({ type: 'bubble', text, at, side: opt.side || 'top', kind: opt.kind || 'info', dur: opt.dur ?? 2.2 });
      if (opt.wait) this.now += opt.wait;
      return this;
    }
    zoom(rect, dur = .8, maxS = 2) {
      const r = typeof rect === 'string' ? this.dlgRectOrEl(rect) : rect;
      const s = Math.min(maxS, APP_W / r.w * .92, APP_H / r.h * .92);
      this.add({ type: 'zoom', to: { s, cx: r.x + r.w / 2, cy: r.y + r.h / 2 }, dur });
      this.now += dur;
      return this;
    }
    unzoom(dur = .7) { this.add({ type: 'zoom', to: { s: 1, cx: APP_W / 2, cy: APP_H / 2 }, dur }); this.now += dur; return this; }
    outro(sub, dur = 6.5) { this.outroAt = this.now + .4; this.now = this.outroAt + dur; return this; }
  }
  const CLICKS = [];
  const MENU_ITEMS = MENUS[IDE] || MENUS.eclipse;
  const TB = { tb: { add: 'tbAdd', new: 'tbNew', run: 'tbRun', help: 'tbHelp', submit: 'tbSubmit', power: 'tbPower' },
               sb: { submit: 'sbSubmit', run: 'sbRun', add: 'sbAdd', help: 'sbHelp' },
               ds: { submit: 'dsSubmit', examples: 'dsExamples', stack: 'dsStack', queue: 'dsQueue', list: 'dsList', tree: 'dsTree' },
               tk: { start: 'tkStart' } };
  function tbEl(kind, name) {
    const el = $(TB[kind] && TB[kind][name]);
    if (!el) throw new Error('no button ' + kind + ':' + name + ' in ' + IDE);
    return el;
  }
  function menuAnchor() {
    if (IDE === 'vs') return $('tkStart');
    if (IDE === 'vscode') return $('code');
    return document.querySelector('#ide .menubar .ds');
  }
  const MI_H = 32, SEP_H = 10, MENU_PAD = 4, MENU_W = 360;

  // ------------------------------------------------------------ ציור
  let B, META, EV, DUR;
  const imgCache = {};
  function imgEl(src) {
    if (!imgCache[src]) { const im = new Image(); im.src = src; imgCache[src] = im; }
    return imgCache[src];
  }

  function last(type, t, pred) {
    let r = null;
    for (const e of EV) if (e.t <= t + 1e-6 && e.type === type && (!pred || pred(e))) r = e;
    return r;
  }

  // רקע — כמו בסרטון הפרומו
  let DOTS = [];
  function bgInit() {
    let seed = 11; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
    for (let i = 0; i < 46; i++) {
      const d = document.createElement('div'); d.className = 'dot';
      DOTS.push({ el: d, x: rnd() * 1920, y: rnd() * 1080, v: 10 + rnd() * 40, r: .4 + rnd() * 1.4, ph: rnd() * 6 });
      $('dots').appendChild(d);
    }
    for (let i = 0; i < 7; i++) $('bars').appendChild(document.createElement('div'));
  }
  function renderBg(t) {
    const [c1, c2, c3] = META.pal;
    [[$('b1'), c1, 260, -300, .23], [$('b2'), c2, 1250, 300, .17], [$('b3'), c3, 500, 500, .29]].forEach(([el, c, x, y, w], i) => {
      el.style.background = `radial-gradient(circle, ${c}cc 0%, ${c}55 35%, transparent 68%)`;
      el.style.left = (x + Math.sin(t * w + i * 2) * 260) + 'px';
      el.style.top = (y + Math.cos(t * w * 1.3 + i) * 200) + 'px';
      el.style.opacity = .5;
    });
    $('grid').style.transform = 'perspective(900px) rotateX(72deg)';
    $('grid').style.backgroundPosition = `0 ${(t * 60) % 120}px`;
    for (const d of DOTS) {
      const y = (d.y - t * d.v + 1080 * 40) % 1080;
      d.el.style.transform = `translate(${d.x + Math.sin(t + d.ph) * 20}px, ${y}px) scale(${d.r})`;
      d.el.style.opacity = .2 + .3 * Math.sin(t * 2 + d.ph) ** 2;
    }
  }

  function renderIntro(t) {
    const end = B.introEnd;
    $('intro').style.display = t < end + .5 ? 'block' : 'none';
    if (t >= end + .5) return;
    $('inum').textContent = '';
    $('ititle').textContent = META.title;
    $('isub').textContent = META.goal || META.sub;
    $('ikick').textContent = 'הדרכה ' + META.num;
    const a = p(t, .1, .6) * (1 - p(t, end - .35, end + .1));
    for (const id of ['ikick', 'ititle', 'isub']) {
      const el = $(id), d = id === 'ikick' ? 0 : id === 'ititle' ? .15 : .35;
      const k = eo(p(t, .1 + d, .6 + d));
      css(el, a * k, `translateY(${(1 - k) * 22}px)`, 0);
    }
  }

  function renderTop(t) {
    const x = eo5(p(t, B.introEnd + .2, B.introEnd + .8));
    const out = B.outroAt != null ? p(t, B.outroAt, B.outroAt + .4) : 0;
    $('tbadge').textContent = 'הדרכה ' + META.num;
    $('tttl').textContent = META.title;
    css($('top'), x * (1 - out), `translateY(${lerp(-40, 0, x)}px)`, (1 - x) * 8);
  }

  function renderApp(t) {
    const t0 = B.introEnd + .15;
    const w = eo(p(t, t0, t0 + .7));
    const out = B.outroAt != null ? eio(p(t, B.outroAt - .1, B.outroAt + .45)) : 0;
    $('app3d').style.opacity = p(t, t0, t0 + .5) * (1 - out);
    $('app').style.transform = `translateY(${lerp(30, 0, w) + out * 20}px) scale(${lerp(.97, 1, w)})`;
    $('app').style.filter = 'none';

    // מצלמה (זום)
    let cam = { s: 1, cx: APP_W / 2, cy: APP_H / 2 }, prev = { ...cam };
    for (const e of EV) {
      if (e.type !== 'zoom' || e.t > t) continue;
      const k = eio(p(t, e.t, e.t + e.dur));
      prev = { ...cam };
      cam = { s: lerp(prev.s, e.to.s, k), cx: lerp(prev.cx, e.to.cx, k), cy: lerp(prev.cy, e.to.cy, k) };
      if (k >= 1) cam = { ...e.to };
    }
    $('cam').style.transform = `translate(${APP_W / 2 - cam.cx * cam.s}px, ${APP_H / 2 - cam.cy * cam.s}px) scale(${cam.s})`;

    renderIde(t);
    renderLayers(t);
    renderFx(t);
  }

  let lastCodeKey = null;
  /** שורות עץ הקבצים (גם בזמן בניית התסריט — כדי ש-tree:שם ימצא את השורה) */
  function treeHtml(rows) {
    return rows.map(r => {
        const [depth, name, sel] = r;
        const file = /\.(java|cs|csproj)$/.test(name);
        const icon = name.endsWith('.java') ? '<span style="display:inline-block;width:14px;height:14px;border-radius:3px;background:#3d7be0;margin-right:6px"></span>'
                   : name.endsWith('.cs') ? '<span style="display:inline-block;width:16px;height:14px;border-radius:3px;background:#68217a;color:#fff;font-size:9px;line-height:14px;text-align:center;margin-right:6px;font-weight:800">C#</span>'
                   : name.endsWith('.csproj') || name.endsWith('.sln') ? '<span style="display:inline-block;width:14px;height:14px;border-radius:3px;background:#865fc5;margin-right:6px"></span>'
                   : '<span style="display:inline-block;width:14px;height:12px;border-radius:2px;background:#e5b84b;margin-right:6px"></span>';
        return `<div class="row${sel ? ' sel' : ''}" style="padding-left:${12 + depth * 16}px">${file ? '' : '▾ '}${icon}<span>${name}</span></div>`;
      }).join('');
  }
  function renderIde(t) {
    const tr = last('tree', t);
    if (tr && $('tree').dataset.k !== String(EV.indexOf(tr))) {
      $('tree').dataset.k = String(EV.indexOf(tr));
      $('tree').innerHTML = treeHtml(tr.rows);
    }
    const tab = last('tab', t);
    if (tab) {
      $('tabName').textContent = tab.name;
      if (IDE === 'eclipse') $('ideTitle').textContent = `DataStructures - ${tab.name} - Eclipse IDE`;
      if (IDE === 'vscode') { $('ideTitle').textContent = `${tab.name} — DataStructures — Visual Studio Code`; $('crumbs').textContent = (tab.crumbs || 'src') + ' › ' + tab.name; }
    }
    if (IDE === 'vscode') {   // הלוח בצד: קבצים או "מבני נתונים"
      const sd = last('side', t), ds = sd && sd.view === 'dsviz';
      $('tree').style.display = ds ? 'none' : 'block';
      $('dsview').style.display = ds ? 'block' : 'none';
      $('sideSec').style.display = ds ? 'none' : 'flex';
      $('sideH').textContent = ds ? 'מבני נתונים' : 'EXPLORER';
      $('actDs').classList.toggle('on', !!ds);
      $('actFiles').classList.toggle('on', !ds);
    }

    const ce = last('code', t);
    if (ce) {
      const key = EV.indexOf(ce);
      const le = last('lens', t), lens = le ? le.items : [];
      const lkey = key + ':' + (le ? EV.indexOf(le) : -1);
      if (lkey !== lastCodeKey) {
        lastCodeKey = lkey;
        $('lines').innerHTML = ce.lines.map((l, i) => `<div class="ln" style="top:${i * LINE_H + lensOff(lens, i + 1)}px"><span class="newbg" style="opacity:0"></span><span class="curbg" style="opacity:0"></span><span class="no" style="position:relative">${i + 1}</span><span class="tx" style="position:relative;display:inline-block">${highlight(l)}</span></div>`).join('')
          + lens.map(x => `<div class="lensrow" style="top:${(x.line - 1) * LINE_H + lensOff(lens, x.line) - LENS_H}px"><span class="no"></span><span class="lens" style="padding-left:${12 + ((ce.lines[x.line - 1] || '').match(/^ */)[0].length) * CHAR_W}px">${esc(x.text)}</span></div>`).join('');
      }
      const rows = $('lines').querySelectorAll('.ln');
      for (let i = 0; i < rows.length; i++) {
        const ln = i + 1, isNew = ce.newFrom && ln >= ce.newFrom && ln <= ce.newTo;
        const nb = rows[i].children[0], tx = rows[i].children[3];
        if (isNew) {
          const k = i - ce.newFrom + 1;
          const rev = eo(p(t, ce.t + k * .07, ce.t + k * .07 + .35));
          tx.style.clipPath = `inset(0 ${100 - rev * 100}% 0 0)`;
          nb.style.opacity = rev * (1 - p(t, ce.t + 2.2, ce.t + 3.6)) * .9;
        } else { tx.style.clipPath = 'none'; nb.style.opacity = 0; }
      }
    }
    // גלילה
    let sc = 0;
    for (const e of EV) if (e.type === 'scroll' && e.t <= t) sc = lerp(e.from, e.to, eio(p(t, e.t, e.t + e.dur)));
    $('lines').style.transform = `translateY(${-sc * LINE_H}px)`;
    // סמן כתיבה
    const ca = last('caret', t);
    const caret = $('caret');
    if (ca && ca.line != null) {
      caret.style.display = 'block';
      caret.style.left = (GUTTER + ca.col * CHAR_W) + 'px';
      const cl = last('lens', t);
      caret.style.top = ((ca.line - 1 - sc) * LINE_H + lensOff(cl && cl.items, ca.line) + 2) + 'px';
      caret.style.opacity = Math.floor((t - ca.t) * 2.2) % 2 === 0 ? 1 : 0;
      // השורה של הסמן — רקע כחול בהיר
      const rows = $('lines').querySelectorAll('.ln');
      for (let i = 0; i < rows.length; i++) rows[i].children[1].style.opacity = i === ca.line - 1 ? 1 : 0;
    } else {
      caret.style.display = 'none';
      for (const r of $('lines').querySelectorAll('.ln')) r.children[1].style.opacity = 0;
    }
    // תפריט נפתח
    const mo = last('menu', t), mc = last('menuClose', t), menu = $('menu');
    if (mo && (!mc || mc.t < mo.t)) {
      const g = mo.geom, k = eo(p(t, mo.t, mo.t + .18));
      if (menu.dataset.k !== String(EV.indexOf(mo))) {
        menu.dataset.k = String(EV.indexOf(mo));
        menu.innerHTML = MENU_ITEMS.map(x => x === '---' ? '<div class="sepl"></div>' : `<div class="mi">${x}</div>`).join('');
      }
      menu.style.display = 'block'; menu.style.left = g.left + 'px'; menu.style.top = g.top + 'px'; menu.style.width = MENU_W + 'px';
      menu.style.opacity = k; menu.style.transform = `translateY(${(1 - k) * -8}px)`;
      const hot = last('menuHot', t, e => e.t >= mo.t);
      const items = menu.querySelectorAll('.mi');
      let n = 0;
      MENU_ITEMS.forEach((x, i) => { if (x !== '---') { items[n].classList.toggle('hot', !!hot && hot.idx === i); n++; } });
    } else menu.style.display = 'none';
    // כפתורים "חמים" בסרגל
    for (const el of document.querySelectorAll('#ide .hot')) el.classList.remove('hot');
    for (const e of EV) if (e.type === 'hot' && t >= e.t && t <= e.t + e.dur) document.querySelector(e.el).classList.add('hot');
  }

  // חלון קופץ וחלון ההרצה
  let layersBuilt = false;
  const DLG = {}, VW = {};
  function buildLayers() {
    const L = $('layers');
    L.innerHTML = `<div class="win" id="dlgWin"><div class="wt"><span id="dlgTitle"></span><div class="x">✕</div></div><div id="dlgImgs" style="position:relative"></div><div id="dlgType"></div></div>
                   <div class="win" id="viewWin"><div class="wt"><span id="viewTitle"></span><div class="x">✕</div></div><div id="viewImgs" style="position:relative"></div></div>`;
    const assets = new Set(EV.filter(e => e.type === 'dialog' || e.type === 'swap').map(e => e.asset));
    for (const a of assets) { const im = document.createElement('img'); im.src = ASSETS[a].src; im.style.display = 'none'; $('dlgImgs').appendChild(im); DLG[a] = im; }
    const views = new Set(EV.filter(e => e.type === 'viewer' || e.type === 'vframe').map(e => e.img));
    for (const v of views) { const im = document.createElement('img'); im.src = v; im.style.display = 'none'; $('viewImgs').appendChild(im); VW[v] = im; }
    layersBuilt = true;
  }
  function renderLayers(t) {
    if (!layersBuilt) buildLayers();
    // דיאלוג
    const open = last('dialog', t), close = last('dialogClose', t);
    const win = $('dlgWin');
    if (open && (!close || close.t < open.t || t < close.t + .3)) {
      const closing = close && close.t >= open.t ? p(t, close.t, close.t + .25) : 0;
      const k = back(p(t, open.t, open.t + .38));
      win.style.left = open.x + 'px'; win.style.top = open.y + 'px';
      $('dlgTitle').textContent = open.title;
      win.querySelector('.wt').style.display = open.dock || open.bare ? 'none' : 'flex';
      win.style.background = open.bare ? 'transparent' : '';
      win.style.borderRadius = open.dock ? '0' : '10px';
      win.style.boxShadow = open.dock ? 'none' : '';
      if (open.dock) css(win, p(t, open.t, open.t + .15) * (1 - closing), 'none', 0);
      else css(win, p(t, open.t, open.t + .12) * (1 - closing), `scale(${lerp(.82, 1, k) * (1 - closing * .08)})`, 0);
      let asset = open.asset;
      for (const e of EV) if (e.type === 'swap' && e.t >= open.t && e.t <= t) asset = e.asset;
      for (const a in DLG) DLG[a].style.display = a === asset ? 'block' : 'none';
      // הקלדה
      const ty = EV.filter(e => e.type === 'typing' && e.t <= t && e.t >= open.t).pop();
      const tl = $('dlgType');
      const swappedAfter = ty && EV.some(e => e.type === 'swap' && e.t >= ty.t + ty.dur - 1e-6 && e.t <= t);
      if (ty && !swappedAfter) {
        const n = Math.round(p(t, ty.t, ty.t + ty.dur) * ty.text.length);
        const r = ty.rect;
        tl.innerHTML = `<div class="typing" style="left:${r.x - open.x + 2}px;top:${r.y - open.y + 2}px;width:${r.w - 4}px;height:${r.h - 4}px">${esc(ty.text.slice(0, n))}<span style="display:inline-block;width:1.5px;height:16px;background:#111;margin-left:1px;opacity:${Math.floor(t * 3) % 2 ? 1 : .2}"></span></div>`;
      } else tl.innerHTML = '';
    } else win.style.opacity = 0;

    // חלון ההרצה
    const vo = last('viewer', t), vc = last('viewerClose', t);
    const vw = $('viewWin');
    if (vo && (!vc || vc.t < vo.t || t < vc.t + .3)) {
      const closing = vc && vc.t >= vo.t ? p(t, vc.t, vc.t + .25) : 0;
      const k = eo5(p(t, vo.t, vo.t + .5));
      vw.style.left = vo.x + 'px'; vw.style.top = vo.y + 'px';
      $('viewTitle').textContent = vo.title;
      let img = vo.img;
      for (const e of EV) if (e.type === 'vframe' && e.t >= vo.t && e.t <= t) img = e.img;
      for (const v in VW) { VW[v].style.display = v === img ? 'block' : 'none'; VW[v].style.width = (VIEW.w * vo.s) + 'px'; }
      css(vw, p(t, vo.t, vo.t + .15) * (1 - closing), `translateY(${lerp(60, 0, k)}px) scale(${lerp(.9, 1, k)})`, (1 - k) * 6);
    } else vw.style.opacity = 0;
  }

  function renderFx(t) {
    const fx = [];
    // הדגשות
    for (const e of EV) if (e.type === 'hl' && t >= e.t && t <= e.t + e.dur + .3) {
      const a = p(t, e.t, e.t + .25) * (1 - p(t, e.t + e.dur, e.t + e.dur + .3));
      const pulse = 1 + Math.sin((t - e.t) * 7) * .012;
      fx.push(`<div class="hl" style="left:${e.rect.x}px;top:${e.rect.y}px;width:${e.rect.w}px;height:${e.rect.h}px;opacity:${a};transform:scale(${pulse})"></div>`);
    }
    // בועות
    for (const e of EV) if (e.type === 'bubble' && t >= e.t && t <= e.t + e.dur + .3) {
      const a = p(t, e.t, e.t + .2) * (1 - p(t, e.t + e.dur, e.t + e.dur + .3));
      const k = back(p(t, e.t, e.t + .35));
      const off = 22;
      const at = (e.side === 'top' || e.side === 'bottom') ? { x: Math.max(300, Math.min(APP_W - 300, e.at.x)), y: e.at.y } : e.at;
      const pos = { top: `left:${at.x}px;top:${at.y - off}px;transform:translate(-50%,-100%) scale(${k})`,
                    bottom: `left:${at.x}px;top:${at.y + off}px;transform:translate(-50%,0) scale(${k})`,
                    left: `left:${at.x - off}px;top:${at.y}px;transform:translate(-100%,-50%) scale(${k})`,
                    right: `left:${at.x + off}px;top:${at.y}px;transform:translate(0,-50%) scale(${k})` }[e.side];
      const tip = { top: 'left:calc(50% - 9px);bottom:-8px', bottom: 'left:calc(50% - 9px);top:-8px', left: 'right:-8px;top:calc(50% - 9px)', right: 'left:-8px;top:calc(50% - 9px)' }[e.side];
      fx.push(`<div class="bubble ${e.kind}" style="${pos};${e.side === 'left' ? `max-width:${Math.max(200, Math.min(560, at.x - off - 16))}px;` : e.side === 'right' ? `max-width:${Math.max(200, Math.min(560, APP_W - at.x - off - 16))}px;` : ''}opacity:${a};transform-origin:${e.side === 'top' ? '50% 100%' : e.side === 'bottom' ? '50% 0' : e.side === 'left' ? '100% 50%' : '0 50%'}"><div class="tip" style="${tip}"></div>${e.text}</div>`);
    }
    // לחיצות
    for (const e of EV) if (e.type === 'click' && t >= e.t && t <= e.t + .55) {
      const k = p(t, e.t, e.t + .55), r = lerp(8, 46, eo(k));
      fx.push(`<div class="ripple" style="left:${e.x - r}px;top:${e.y - r}px;width:${r * 2}px;height:${r * 2}px;opacity:${1 - k}"></div>`);
    }
    $('fx').innerHTML = fx.join('');
    // העכבר
    let pos = { x: 760, y: 560 };
    for (const e of EV) {
      if (e.type !== 'move' || e.t > t) continue;
      const k = eio(p(t, e.t, e.t + e.dur));
      // מסלול מעט מעוגל — כמו יד אמיתית
      const mx = (e.from.x + e.to.x) / 2, my = (e.from.y + e.to.y) / 2 - Math.min(80, Math.hypot(e.to.x - e.from.x, e.to.y - e.from.y) * .12);
      pos = { x: (1 - k) * (1 - k) * e.from.x + 2 * (1 - k) * k * mx + k * k * e.to.x, y: (1 - k) * (1 - k) * e.from.y + 2 * (1 - k) * k * my + k * k * e.to.y };
    }
    let press = 0;
    for (const e of EV) if (e.type === 'click' && t >= e.t - .06 && t <= e.t + .16) press = 1;
    const cur = $('cursor');
    cur.style.transform = `translate(${pos.x - 3}px, ${pos.y - 3}px) scale(${press ? .86 : 1})`;
    cur.style.opacity = p(t, B.introEnd + 1.0, B.introEnd + 1.3);
  }

  function renderSteps(t) {
    const st = last('step', t);
    const box = $('steps');
    const x = eo5(p(t, B.introEnd + .5, B.introEnd + 1.1));
    const out = B.outroAt != null ? p(t, B.outroAt, B.outroAt + .4) : 0;
    css(box, x * (1 - out), `translateX(${lerp(-80, 0, x)}px)`, 0);
    const steps = META.steps;
    const idx = st ? st.idx : 0;
    const k = st ? eo5(p(t, st.t, st.t + .45)) : 1;
    $('stepN').textContent = `שלב ${idx + 1} מתוך ${steps.length}`;
    $('stepT').innerHTML = steps[idx][0];
    $('stepS').innerHTML = steps[idx][1];
    css($('stepCard'), k, `translateX(${lerp(-60, 0, k)}px)`, (1 - k) * 10);
    $('stepList').innerHTML = steps.map((s, i) => `<div class="it ${i < idx ? 'done' : i === idx ? 'cur' : ''}"><div class="c">${i < idx ? '✓' : i + 1}</div><div>${s[2] || s[0]}</div></div>`).join('');
  }

  function renderOutro(t) {
    const u = B.outroAt != null ? t - B.outroAt : -1;
    $('outro').style.display = u > -.2 ? 'block' : 'none';
    if (u <= -.2) return;
    $('o1').textContent = 'סיכום — מה עשינו';
    $('o2').innerHTML = META.steps.map((st, i) => `<div class="sum" style="opacity:${eo(p(u, .3 + i * .25, .7 + i * .25))};transform:translateX(${(1 - eo(p(u, .3 + i * .25, .7 + i * .25))) * 40}px)"><div class="c">${i + 1}</div><div>${st[2] || st[0]}</div></div>`).join('');
    $('o1').style.opacity = eo(p(u, 0, .4));
    $('o3').style.opacity = 0;
    $('o4').style.opacity = eo(p(u, .6 + META.steps.length * .25, 1.1 + META.steps.length * .25));
    $('o4').textContent = 'אפשר לחזור להדרכה בכל רגע — כפתור ? בסרגל הכלים';
  }

  function renderBars(t) {
    const cuts = [];
    let vis = false;
    const bars = $('bars').children;
    for (const cut of cuts) {
      const d = t - cut;
      if (d < -.45 || d > .45) continue;
      vis = true;
      [...bars].forEach((b, i) => {
        const lag = i * .035;
        const inn = eio(p(d, -.45 + lag, -.05 + lag)), out = eio(p(d, .02 + lag, .42 + lag));
        b.style.left = (i * 300 - 200) + 'px';
        b.style.background = `linear-gradient(180deg, ${META.pal[i % 3]}, ${META.pal[(i + 1) % 3]})`;
        b.style.transform = `translateX(${lerp(2300, 0, inn) - out * 2600}px) skewX(-14deg)`;
      });
    }
    $('bars').style.display = vis ? 'block' : 'none';
    $('fade').style.opacity = (DUR ? p(t, DUR - .5, DUR) : 0) + (1 - p(t, 0, .3));
  }

  function render(t) {
    renderBg(t); renderIntro(t); renderTop(t); renderApp(t); renderSteps(t); renderOutro(t); renderBars(t);
  }

  let CHAR_W = 10.2;
  window.Engine = {
    async start() {
      await document.fonts.ready;
      const probe = document.createElement('span');
      probe.style.cssText = 'font-family:JBMono;font-size:17px;position:absolute;visibility:hidden;white-space:pre';
      probe.textContent = 'x'.repeat(100);
      document.body.appendChild(probe);
      CHAR_W = probe.getBoundingClientRect().width / 100;
      probe.remove();
      META = window.META;
      if (SKINS[IDE]) { $('ide').innerHTML = SKINS[IDE]; $('ide').classList.add(IDE); }
      document.getElementById('stage').style.setProperty('--c1', META.pal[0]);
      document.getElementById('stage').style.setProperty('--c2', META.pal[1]);
      document.getElementById('stage').style.setProperty('--c3', META.pal[2]);
      document.getElementById('stage').style.setProperty('--glow', META.pal[0] + '88');
      bgInit();
      B = new Builder(META);
      B.ide = IDE;
      B.cs = IDE === 'vs';
      B.charW = CHAR_W;
      // התסריט צריך מדידות של העורך — מציגים אותו בלי טרנספורמציות בזמן הבנייה
      $('app').style.transform = 'none';
      window.SCRIPT(B);
      EV = B.ev.slice().sort((a, b) => a.t - b.t);
      DUR = B.now;
      window.DURATION = DUR;
      window.CLICK_TIMES = CLICKS.slice();
      window.CUT_TIMES = [];
      await Promise.all([...document.images].map(i => i.decode().catch(() => 0)));
      buildLayers();
      await Promise.all([...document.images].map(i => i.decode().catch(() => 0)));
      render(0);
      window.READY = true;
    },
    render: t => render(t),
    Builder
  };
  window.render = t => render(t);
})();
