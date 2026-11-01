// "שליחת תרגיל" בעמוד הראשי (#exercise): התרגיל נבנה (דף התרגיל + ההודעות של שורת ההתקדמות), ואז הקוד עצמו —
// בדיוק במקום שבו התלמיד כותב את התשובה, עם הסמן בסעיף א. ההצעה לפתרון נפתחת רק כשלוחצים על המנורה ליד סעיף.
// כל מה שמוצג אמיתי: שלושת הצילומים הם הדף שנשלח, EXERCISE היא התשובה של Gemini עליו (בלי שינוי), והקוד נבנה כאן
// ב-assets/scaffold.js, שמפיק בדיוק את מה שהתוסף מפיק (tools/test/scaffold-parity.js, ai/parity/5-gemini-balanced-java.json).
(function () {
  var root = document.querySelector('[data-flow]');
  if (!root) return;
  var calm = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  var EXERCISE = {"title":"תור מאוזן ומחסנית תורים","summary":"בדיקה האם תור של מספרים שלמים מאוזן (סכום המספרים החיוביים שווה לסכום הערכים המוחלטים של השליליים), וספירת תורים מאוזנים במחסנית תורים.","topic":"queues_and_stacks","mainClass":"BalancedQueues","imports":[],"classes":[],"tasks":[{"label":"א","kind":"method","className":"BalancedQueues","isStatic":true,"returnType":"boolean","methodName":"isBalanced","parameters":[{"type":"Queue<Integer>","name":"q"}],"instructions":"תור \"מאוזן\" הוא תור של מספרים שלמים שבו סכום המספרים החיוביים שווה לסכום הערכים המוחלטים של המספרים השליליים.\nכתבו פעולה isBalanced המקבלת תור של מספרים שלמים ומחזירה אמת אם התור מאוזן, ושקר אחרת. התור צריך להישאר כפי שהיה. ניתן להשתמש בפעולה sumPositive הנתונה."},{"label":"ב","kind":"method","className":"BalancedQueues","isStatic":true,"returnType":"int","methodName":"countBalanced","parameters":[{"type":"Stack<Queue<Integer>>","name":"st"}],"instructions":"כתבו פעולה המקבלת מחסנית של תורים של מספרים שלמים, ומחזירה כמה מהתורים במחסנית הם מאוזנים. השתמשו בפעולה שכתבתם בסעיף א."}],"givenCode":[{"className":"BalancedQueues","code":"public static int sumPositive(Queue<Integer> q) {\n    Queue<Integer> temp = new Queue<Integer>();\n    int sum = 0;\n    while (!q.isEmpty()) {\n        int x = q.remove();\n        if (x > 0) sum += x;\n        temp.insert(x);\n    }\n    while (!temp.isEmpty()) q.insert(temp.remove());\n    return sum;\n}"}],"otherQuestions":[{"label":"ג","text":"מהי סיבוכיות זמן הריצה של הפעולה שכתבתם בסעיף ב? נמקו."}],"mainCode":"// יצירת תור מאוזן: 5 חיובי, -2 ו- -3 סכומם בערך מוחלט 5\nQueue<Integer> q1 = new Queue<Integer>();\nq1.insert(5);\nq1.insert(-2);\nq1.insert(-3);\nSystem.out.println(\"תור 1: \" + q1);\nSystem.out.println(\"סעיף א (תור 1 מאוזן?): \" + isBalanced(q1));\n\n// יצירת תור לא מאוזן: חיובי 4, שלילי בערך מוחלט 1\nQueue<Integer> q2 = new Queue<Integer>();\nq2.insert(4);\nq2.insert(-1);\nSystem.out.println(\"תור 2: \" + q2);\nSystem.out.println(\"סעיף א (תור 2 מאוזן?): \" + isBalanced(q2));\n\n// יצירת תור מאוזן נוסף: -7 וסכום חיוביים 3+4=7\nQueue<Integer> q3 = new Queue<Integer>();\nq3.insert(-7);\nq3.insert(3);\nq3.insert(4);\n\n// יצירת מחסנית המכילה 3 תורים (מתוכם 2 מאוזנים)\nStack<Queue<Integer>> st = new Stack<Queue<Integer>>();\nst.push(q1);\nst.push(q2);\nst.push(q3);\nSystem.out.println(\"סעיף ב (מספר תורים מאוזנים במחסנית): \" + countBalanced(st));","expectedOutput":"תור 1: [5,-2,-3]\nסעיף א (תור 1 מאוזן?): true\nתור 2: [4,-1]\nסעיף א (תור 2 מאוזן?): false\nסעיף ב (מספר תורים מאוזנים במחסנית): 2","language":"java"};

  var seq = [];
  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
  function later(fn, ms) { seq.push(setTimeout(fn, ms)); }

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
  var PARTS = [];

  function lines(b) {
    var ls = b.main.code.split('\n'), cur = b.main.cursorLine, html = '', heads = {}, ans = {};
    if (ls[ls.length - 1] === '') ls.pop();
    PARTS.forEach(function (p) {
      if (!p.task) return;
      heads[p.from] = p.task;
      for (var k = p.from + 1; k < p.to; k++) ans[k] = true;   // גוף הפעולה — כאן כותבים
    });
    for (var i = 0; i < ls.length; i++) {
      var body = i === cur ? esc(ls[i]) + '<i class="caret" aria-hidden="true"></i>' : paint(ls[i]);
      var bulb = heads[i] !== undefined ? '<button type="button" class="fp-bulb" data-bulb="' + heads[i] + '" aria-label="הצעה לפתרון לסעיף ' + heads[i] + '" aria-expanded="false"><img src="assets/bulb.svg" alt="" width="14" height="14"></button>' : '';
      html += '<span class="l' + (i === cur ? ' cur' : '') + (ans[i] ? ' ans' : '') + '" data-i="' + i + '"><span class="n">' + (i + 1) + '</span>' + bulb + (body || ' ') + '</span>';
    }
    return html;
  }

  function fill(b) {
    if (b.error) { codeEl.innerHTML = '<span class="l"><span class="n"></span>// הקוד לא נטען. מרעננים את הדף ומנסים שוב.</span>'; return; }
    PARTS = parts(b);
    codeEl.innerHTML = lines(b);
    root.querySelector('[data-ftitle]').textContent = b.pkg + '/' + b.main.fileName;
    root.classList.add('fp-built');
    if (root.classList.contains('show-code')) focusAnswer();
  }

  // גוללים בתוך העורך כך שהסעיף הראשון והמקום של התשובה בראש
  function focusAnswer() {
    var first = PARTS.filter(function (p) { return p.task; })[0], ls = codeEl.children;
    if (!first || !ls[first.from]) return;
    codeEl.scrollTop = Math.max(0, ls[first.from].offsetTop - ls[first.from].offsetHeight);
  }

  // ---------------------------------------------------------------- הבנייה: ההודעות של שורת ההתקדמות, אחת אחרי השנייה
  var msgs = Array.prototype.slice.call(root.querySelectorAll('[data-fmsgs] li'));
  var bar = root.querySelector('[data-fbar]');
  function step(k) {
    msgs.forEach(function (li, n) { li.classList.toggle('done', n < k); li.classList.toggle('now', n === k); });
    bar.style.transform = 'scaleX(' + Math.min(1, (k + 1) / msgs.length) + ')';
  }
  function showCode() {
    root.classList.remove('building');
    root.classList.add('show-code');
    scaffold(function (b) { if (!root.classList.contains('fp-built')) fill(b); else focusAnswer(); });
  }
  function build() {
    root.classList.add('building');
    step(0);
    msgs.forEach(function (li, n) { if (n) later(function () { step(n); }, n * 850); });
    later(function () { step(msgs.length); showCode(); }, msgs.length * 850 + 500);
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
      solution(b.getAttribute('aria-expanded') === 'true' ? null : b.getAttribute('data-bulb'));
      var open = solBox.querySelector('[data-sol-open]:not([hidden])');
      if (open && open.scrollIntoView) open.scrollIntoView({ block: 'nearest', behavior: calm ? 'auto' : 'smooth' });
      return;
    }
    if (e.target.closest('[data-sol-close]')) { solution(null); var first = codeEl.querySelector('[data-bulb]'); if (first) first.focus(); }
  });

  // הקוד נטען כשמתקרבים לאזור; הבנייה מוצגת פעם אחת כשהאזור על המסך, ואז נשארים על הקוד.
  // בלי תנועה (או בלי IntersectionObserver) — ישר הקוד
  root.classList.add('ready');
  step(0);
  if (calm || !('IntersectionObserver' in window)) { showCode(); return; }
  var near = new IntersectionObserver(function (es) {
    if (es.some(function (x) { return x.isIntersecting; })) { near.disconnect(); scaffold(function (b) { if (!root.classList.contains('fp-built')) fill(b); }); }
  }, { rootMargin: '600px 0px' });
  near.observe(root);
  var io = new IntersectionObserver(function (es) {
    if (!es.some(function (x) { return x.isIntersecting; })) return;
    io.disconnect();
    build();
  }, { threshold: 0.5 });
  io.observe(root);
})();
