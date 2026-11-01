// "שליחת תרגיל", שלב אחרי שלב (העמוד הראשי, #exercise): הדף, מה ש-Gemini החזיר, החבילה, הקובץ וההרצה.
// ההצעה לפתרון היא לא שלב: היא נפתחת רק כשלוחצים על המנורה ליד סעיף בקובץ (שלב 4).
// כל מה שמוצג אמיתי: שלושת הצילומים הם הקבצים שנשלחו, EXERCISE היא התשובה של Gemini עליהם (בלי שינוי), והחבילה והקוד
// נבנים כאן מהתשובה ב-assets/scaffold.js, שמפיק בדיוק את מה שהתוסף מפיק (tools/test/scaffold-parity.js, אותו JSON
// נמצא שם ב-ai/parity/5-gemini-balanced-java.json). הצילומים של ההרצה ושל ההצעה לפתרון מ-IntelliJ IDEA, על אותו תרגיל.
(function () {
  var root = document.querySelector('[data-flow]');
  if (!root) return;
  var calm = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  var EXERCISE = {"title":"תור מאוזן ומחסנית תורים","summary":"בדיקה האם תור של מספרים שלמים מאוזן (סכום המספרים החיוביים שווה לסכום הערכים המוחלטים של השליליים), וספירת תורים מאוזנים במחסנית תורים.","topic":"queues_and_stacks","mainClass":"BalancedQueues","imports":[],"classes":[],"tasks":[{"label":"א","kind":"method","className":"BalancedQueues","isStatic":true,"returnType":"boolean","methodName":"isBalanced","parameters":[{"type":"Queue<Integer>","name":"q"}],"instructions":"תור \"מאוזן\" הוא תור של מספרים שלמים שבו סכום המספרים החיוביים שווה לסכום הערכים המוחלטים של המספרים השליליים.\nכתבו פעולה isBalanced המקבלת תור של מספרים שלמים ומחזירה אמת אם התור מאוזן, ושקר אחרת. התור צריך להישאר כפי שהיה. ניתן להשתמש בפעולה sumPositive הנתונה."},{"label":"ב","kind":"method","className":"BalancedQueues","isStatic":true,"returnType":"int","methodName":"countBalanced","parameters":[{"type":"Stack<Queue<Integer>>","name":"st"}],"instructions":"כתבו פעולה המקבלת מחסנית של תורים של מספרים שלמים, ומחזירה כמה מהתורים במחסנית הם מאוזנים. השתמשו בפעולה שכתבתם בסעיף א."}],"givenCode":[{"className":"BalancedQueues","code":"public static int sumPositive(Queue<Integer> q) {\n    Queue<Integer> temp = new Queue<Integer>();\n    int sum = 0;\n    while (!q.isEmpty()) {\n        int x = q.remove();\n        if (x > 0) sum += x;\n        temp.insert(x);\n    }\n    while (!temp.isEmpty()) q.insert(temp.remove());\n    return sum;\n}"}],"otherQuestions":[{"label":"ג","text":"מהי סיבוכיות זמן הריצה של הפעולה שכתבתם בסעיף ב? נמקו."}],"mainCode":"// יצירת תור מאוזן: 5 חיובי, -2 ו- -3 סכומם בערך מוחלט 5\nQueue<Integer> q1 = new Queue<Integer>();\nq1.insert(5);\nq1.insert(-2);\nq1.insert(-3);\nSystem.out.println(\"תור 1: \" + q1);\nSystem.out.println(\"סעיף א (תור 1 מאוזן?): \" + isBalanced(q1));\n\n// יצירת תור לא מאוזן: חיובי 4, שלילי בערך מוחלט 1\nQueue<Integer> q2 = new Queue<Integer>();\nq2.insert(4);\nq2.insert(-1);\nSystem.out.println(\"תור 2: \" + q2);\nSystem.out.println(\"סעיף א (תור 2 מאוזן?): \" + isBalanced(q2));\n\n// יצירת תור מאוזן נוסף: -7 וסכום חיוביים 3+4=7\nQueue<Integer> q3 = new Queue<Integer>();\nq3.insert(-7);\nq3.insert(3);\nq3.insert(4);\n\n// יצירת מחסנית המכילה 3 תורים (מתוכם 2 מאוזנים)\nStack<Queue<Integer>> st = new Stack<Queue<Integer>>();\nst.push(q1);\nst.push(q2);\nst.push(q3);\nSystem.out.println(\"סעיף ב (מספר תורים מאוזנים במחסנית): \" + countBalanced(st));","expectedOutput":"תור 1: [5,-2,-3]\nסעיף א (תור 1 מאוזן?): true\nתור 2: [4,-1]\nסעיף א (תור 2 מאוזן?): false\nסעיף ב (מספר תורים מאוזנים במחסנית): 2","language":"java"};

  var tabs = Array.prototype.slice.call(root.querySelectorAll('.fp-steps [role=tab]'));
  var panes = tabs.map(function (t) { return document.getElementById(t.getAttribute('aria-controls')); });
  var prev = root.querySelector('[data-fprev]');
  var next = root.querySelector('[data-fnext]');
  var play = root.querySelector('[data-fplay]');
  var now = root.querySelector('[data-fnow]');
  // כמה זמן כל שלב על המסך כשמריצים (מ"ש)
  var DUR = [3600, 5400, 3800, 5200, 4600];
  var at = 0, timer = null, playing = false, touched = false, seq = [];

  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
  function later(fn, ms) { seq.push(setTimeout(fn, ms)); }
  function clearSeq() { seq.forEach(clearTimeout); seq = []; }

  // ---------------------------------------------------------------- שלב 2: ההמתנה — ההודעות שבשורת ההתקדמות, אחת אחרי השנייה
  var msgs = Array.prototype.slice.call(root.querySelectorAll('[data-fmsgs] li'));
  var bar = root.querySelector('[data-fbar]');
  function progress() {
    var step = function (k) {
      msgs.forEach(function (li, n) { li.classList.toggle('done', n < k); li.classList.toggle('now', n === k); });
      bar.style.transform = 'scaleX(' + Math.min(1, (k + 1) / msgs.length) + ')';
    };
    if (calm) { step(msgs.length - 1); return; }
    step(0);
    msgs.forEach(function (li, n) { if (n) later(function () { step(n); }, n * 900); });
  }

  // ---------------------------------------------------------------- הקוד: נבנה בדפדפן ב-scaffold.js
  var built = null, waiting = [];
  function scaffold(cb) {
    if (built) return cb(built);
    waiting.push(cb);
    if (waiting.length > 1) return;
    var ready = function () {
      try {
        var S = window.DSVizScaffold, e = S.read(EXERCISE);
        var pkg = S.folderName(e.topic, new Set(['examples']), e.cs);
        var files = S.build(e, pkg, ['a.png', 'b.png', 'c.png'], false);
        built = { e: e, pkg: pkg, files: files, main: files[0] };
      } catch (err) { built = { error: true }; }
      var q = waiting; waiting = [];
      q.forEach(function (f) { f(built); });
    };
    if (window.DSVizScaffold) return ready();
    var s = document.createElement('script');
    s.src = 'assets/scaffold.js';
    s.async = true;
    s.onload = ready;
    s.onerror = function () { built = { error: true }; var q = waiting; waiting = []; q.forEach(function (f) { f(built); }); built = null; };
    document.head.appendChild(s);
  }

  // צביעה כמו בעורך הכהה: מילות מפתח, טיפוסים, מחרוזות, מספרים, הערות
  var KW = /^(package|import|public|private|protected|static|final|class|new|return|if|else|while|for|do|void|int|boolean|char|double|long|true|false|null|this)$/;
  function paint(line) {
    var out = '', re = /(\/\/.*$)|("(?:[^"\\]|\\.)*")|([A-Za-z_]\w*)|(\d+)|([\s\S])/g, m;
    while ((m = re.exec(line))) {
      if (m[1]) out += '<span class="c">' + esc(m[1]) + '</span>';
      else if (m[2]) out += '<span class="s">' + esc(m[2]) + '</span>';
      else if (m[3]) out += KW.test(m[3]) ? '<span class="k">' + m[3] + '</span>' : /^[A-Z]/.test(m[3]) ? '<span class="t">' + m[3] + '</span>' : m[3];
      else if (m[4]) out += '<span class="d">' + m[4] + '</span>';
      else out += esc(m[5]);
    }
    return out;
  }

  // איפה כל חלק בקובץ (מספרי שורות מ-0): הסעיפים, מה שנתון, הפלט הצפוי ו-main
  function parts(b) {
    var ls = b.main.code.split('\n'), find = function (test, from) {
      for (var i = from || 0; i < ls.length; i++) if (test(ls[i])) return i;
      return -1;
    };
    var close = function (i) { var j = find(function (l) { return l === '    }'; }, i + 1); return j < 0 ? i : j; };
    var out = [];
    b.e.tasks.forEach(function (t) {
      var i = find(function (l) { return /^    public /.test(l) && l.indexOf(' ' + t.methodName + '(') > 0; });
      if (i >= 0) out.push({ id: 'task-' + t.label, from: i, to: close(i), label: 'סעיף ' + t.label, code: t.methodName, task: t.label });
    });
    var g = find(function (l) { return l.indexOf('נתון בתרגיל') >= 0; });
    if (g >= 0) {
      var gm = find(function (l) { return /^    public /.test(l); }, g);
      out.push({ id: 'given', from: g, to: gm >= 0 ? close(gm) : g, label: 'נתון בשאלה', code: b.e.given.length ? b.e.given[0].code.match(/(\w+)\s*\(/)[1] : '' });
    }
    var x = find(function (l) { return l.indexOf('אם פתרת נכון') >= 0; });
    if (x >= 0) { var xe = x; while (xe + 1 < ls.length && /^    \/\//.test(ls[xe + 1])) xe++; out.push({ id: 'expected', from: x, to: xe, label: 'מה צריך להיות מודפס', code: '' }); }
    var mn = find(function (l) { return l.indexOf('void main(') >= 0; });
    if (mn >= 0) out.push({ id: 'main', from: mn, to: close(mn), label: 'main עם נתונים לדוגמה', code: 'main' });
    return out;
  }

  var codeEl = root.querySelector('[data-fcode]');
  var legendEl = root.querySelector('[data-flegend]');
  var PARTS = [];

  function lines(b, upto, bulbs) {
    var ls = b.main.code.split('\n'), cur = b.main.cursorLine, html = '';
    if (ls[ls.length - 1] === '') ls.pop();
    var heads = {};
    PARTS.forEach(function (p) { if (p.task) heads[p.from] = p.task; });
    for (var i = 0; i < Math.min(ls.length, upto || ls.length); i++) {
      var body = i === cur ? esc(ls[i]) + '<i class="caret" aria-hidden="true"></i>' : paint(ls[i]);
      var bulb = heads[i] !== undefined ? '<button type="button" class="fp-bulb" data-bulb="' + heads[i] + '" aria-label="הצעה לפתרון לסעיף ' + heads[i] + '"' + (bulbs ? ' aria-expanded="false"' : '') + '><img src="assets/bulb.svg" alt="" width="14" height="14"></button>' : '';
      html += '<span class="l' + (i === cur ? ' cur' : '') + '" data-i="' + i + '"><span class="n">' + (i + 1) + '</span>' + bulb + (body || ' ') + '</span>';
    }
    return html;
  }

  function fill(b) {
    if (b.error) { codeEl.innerHTML = '<span class="l"><span class="n"></span>// הקוד לא נטען. מרעננים את הדף ומנסים שוב.</span>'; return; }
    PARTS = parts(b);
    codeEl.innerHTML = lines(b, 0, true);
    legendEl.innerHTML = PARTS.map(function (p) {
      return '<button type="button" data-part="' + p.id + '"><span>' + p.label + '</span><small>' + (p.from === p.to ? 'שורה ' + (p.from + 1) : 'שורות <bdi dir="ltr">' + (p.from + 1) + '–' + (p.to + 1) + '</bdi>') + '</small></button>';
    }).join('');
    root.querySelector('[data-fpkg]').textContent = b.pkg;
    root.querySelector('[data-fpkg2]').textContent = b.pkg;
    root.querySelector('[data-ffile]').textContent = b.main.fileName;
    root.querySelector('[data-ftitle]').textContent = b.pkg + '/' + b.main.fileName;
    root.querySelector('[data-ffiles]').innerHTML = b.files.map(function (f, i) {
      return '<li class="new" style="--i:' + (i + 1) + '"><span class="ff">' + esc(f.fileName) + '</span></li>';
    }).join('');
    var n = b.e.tasks.length;
    root.querySelector('[data-fcount]').textContent = n;
    // סעיפים שעונים עליהם במילים — מופיעים בהודעה, לא בקוד
    var others = b.e.others || [], oEl = root.querySelector('[data-fothers]');
    oEl.hidden = !others.length;
    oEl.innerHTML = others.length ? 'סעיפים שעונים עליהם במילים (בלי קוד): ' + others.map(function (o) { return '<b>' + esc(o.label) + '</b> — ' + esc(o.text); }).join(' · ') : '';
    // שלב 5: מה שכתוב מעל main
    root.querySelector('[data-fexpect]').innerHTML = (b.e.expectedOutput || '').split('\n').map(function (l) { return '<span class="l" dir="auto">' + esc(l) + '</span>'; }).join('');
    root.classList.add('fp-built');
    if (at === 3) mark(PARTS[0] && PARTS[0].id, false);
  }

  // מסמן חלק בקובץ וגולל אליו (בתוך העורך בלבד)
  function mark(id, smooth) {
    var p = null;
    PARTS.forEach(function (x) { if (x.id === id) p = x; });
    var ls = codeEl.children;
    for (var i = 0; i < ls.length; i++) ls[i].classList.toggle('sel', !!p && i >= p.from && i <= p.to);
    legendEl.querySelectorAll('button').forEach(function (b) { b.setAttribute('aria-pressed', b.getAttribute('data-part') === id ? 'true' : 'false'); });
    if (!p || !ls[p.from]) return;
    var top = Math.max(0, ls[p.from].offsetTop - 2 * ls[p.from].offsetHeight);
    if (codeEl.scrollTo) codeEl.scrollTo({ top: top, behavior: smooth && !calm ? 'smooth' : 'auto' });
    else codeEl.scrollTop = top;
  }
  legendEl.addEventListener('click', function (e) {
    var b = e.target.closest('button[data-part]');
    if (!b) return;
    stop();
    mark(b.getAttribute('data-part'), true);
  });

  function tour() {
    mark(PARTS[0] && PARTS[0].id, false);
    if (!playing || calm) return;
    PARTS.slice(1).forEach(function (p, n) { later(function () { mark(p.id, true); }, 1100 + n * 950); });
  }

  // ---------------------------------------------------------------- המנורה: ההצעה נפתחת רק כשבוחרים ללחוץ
  var solBox = root.querySelector('[data-solbox]');
  function solution(task) {
    var shown = false;
    solBox.querySelectorAll('[data-sol-open]').forEach(function (el) {
      var on = el.getAttribute('data-sol-open') === task;
      el.hidden = !on;
      if (on) shown = true;
    });
    if (task && !shown) {   // סעיף שאין לו צילום — אותה הודעה כמו לסעיף ב
      var other = solBox.querySelector('[data-sol-open="ב"]');
      if (other) { other.hidden = false; shown = true; }
    }
    solBox.hidden = !shown;
    codeEl.querySelectorAll('[data-bulb]').forEach(function (b) { b.setAttribute('aria-expanded', b.getAttribute('data-bulb') === task ? 'true' : 'false'); });
  }
  root.addEventListener('click', function (e) {
    var b = e.target.closest('[data-bulb]');
    if (b) {
      stop();
      var task = b.getAttribute('data-bulb');
      var again = b.getAttribute('aria-expanded') === 'true';
      if (at !== 3) show(3);
      solution(again ? null : task);
      var open = solBox.querySelector('[data-sol-open]:not([hidden])');
      if (open && open.scrollIntoView) open.scrollIntoView({ block: 'nearest', behavior: calm ? 'auto' : 'smooth' });
      return;
    }
    if (e.target.closest('[data-sol-close]')) { solution(null); var first = codeEl.querySelector('[data-bulb]'); if (first) first.focus(); }
  });

  // ---------------------------------------------------------------- השלבים
  function show(i) {
    clearSeq();
    if (timer) { clearTimeout(timer); timer = null; }
    at = i;
    tabs.forEach(function (t, n) {
      var on = n === i;
      t.setAttribute('aria-selected', on ? 'true' : 'false');
      t.tabIndex = on ? 0 : -1;
      t.classList.toggle('done', n < i);
      panes[n].classList.toggle('on', on);
      panes[n].setAttribute('aria-hidden', on ? 'false' : 'true');
    });
    var t = tabs[i].querySelector('.st');
    now.innerHTML = '<b>' + t.querySelector('b').innerHTML + '</b> ' + t.querySelector('small').innerHTML;
    prev.disabled = i === 0;
    next.textContent = i === tabs.length - 1 ? 'מההתחלה' : 'הבא';
    if (i === 1) progress();
    if (i >= 2) scaffold(function (b) { if (!root.classList.contains('fp-built')) fill(b); if (at === 3) tour(); });
    if (i === 3) solution(null);
    if (playing) {
      root.style.setProperty('--fp-dur', DUR[i] + 'ms');
      tabs.forEach(function (x) { x.classList.remove('run'); });
      void tabs[i].offsetWidth;   // שהפס יתחיל מההתחלה
      tabs[i].classList.add('run');
      if (i < tabs.length - 1) timer = setTimeout(function () { show(i + 1); }, DUR[i]);
      else setPlaying(false);
    }
  }

  function setPlaying(on) {
    playing = on;
    root.classList.toggle('playing', on);
    play.setAttribute('aria-pressed', on ? 'true' : 'false');
    play.textContent = on ? 'עצירה' : 'הפעלה';
    tabs.forEach(function (t) { t.classList.remove('run'); });
    if (!on && timer) { clearTimeout(timer); timer = null; }
  }
  function stop() { touched = true; if (playing) setPlaying(false); }
  function start() {
    touched = true;
    setPlaying(true);
    show(at === tabs.length - 1 ? 0 : at);
  }

  tabs.forEach(function (t, n) {
    t.addEventListener('click', function () { stop(); show(n); });
  });
  prev.addEventListener('click', function () { stop(); if (at > 0) show(at - 1); });
  next.addEventListener('click', function () { stop(); show(at === tabs.length - 1 ? 0 : at + 1); });
  play.addEventListener('click', function () { if (playing) stop(); else start(); });

  // מקלדת: חצים בין השלבים (מימין לשמאל: שמאלה ולמטה = הבא), Home / End, ורווח מפעיל ועוצר
  root.addEventListener('keydown', function (e) {
    var el = e.target;
    if (el.closest && el.closest('.fp-legend, [data-solbox]')) return;
    var k = e.key, j = null;
    if (k === 'ArrowLeft' || k === 'ArrowDown') j = Math.min(at + 1, tabs.length - 1);
    else if (k === 'ArrowRight' || k === 'ArrowUp') j = Math.max(at - 1, 0);
    else if (k === 'Home') j = 0;
    else if (k === 'End') j = tabs.length - 1;
    else if ((k === ' ' || k === 'Spacebar') && !/^(BUTTON|A|INPUT)$/.test(el.tagName)) { e.preventDefault(); if (playing) stop(); else start(); return; }
    if (j === null) return;
    if (el.tagName === 'PRE' && (k === 'ArrowDown' || k === 'ArrowUp' || k === 'Home' || k === 'End')) return;   // גלילה בתוך הקוד
    e.preventDefault();
    stop();
    show(j);
    if (el.getAttribute('role') === 'tab') tabs[j].focus();
  });

  root.classList.add('ready');
  show(0);

  // הקוד נטען כשמתקרבים לאזור (לא עם העמוד); ההרצה מתחילה לבד פעם אחת כשהנגן על המסך —
  // לא כשמבקשים פחות תנועה, ולא אחרי שנגעו בו
  if (!('IntersectionObserver' in window)) { scaffold(function () {}); return; }
  var near = new IntersectionObserver(function (es) {
    if (es.some(function (x) { return x.isIntersecting; })) { near.disconnect(); scaffold(function (b) { if (!root.classList.contains('fp-built')) fill(b); }); }
  }, { rootMargin: '600px 0px' });
  near.observe(root);
  if (calm) return;
  var io = new IntersectionObserver(function (es) {
    es.forEach(function (x) {
      if (!x.isIntersecting || touched || playing) return;
      io.disconnect();
      touched = true;
      setPlaying(true);
      show(at);
    });
  }, { threshold: 0.55 });
  io.observe(root);
})();
