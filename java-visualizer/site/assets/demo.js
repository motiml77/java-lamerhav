// ההדגמה בראש העמוד: ההרצה של countEven צעד אחרי צעד, מצוירת כמו בחלון של DSViz (כולל ההנפשה של push / pop).
// הצעדים מחושבים כאן מהקוד עצמו (לא מוקלטים), אז מה שרואים הוא באמת מה שהקוד עושה.
(function () {
  var root = document.querySelector('[data-demo]');
  if (!root) return;

  var CODE = [
    'public static int countEven(Stack<Integer> s) {',
    '    Stack<Integer> temp = new Stack<Integer>();',
    '    int count = 0;',
    '    while (!s.isEmpty()) {',
    '        int x = s.pop();',
    '        if (x % 2 == 0) {',
    '            count++;',
    '        }',
    '        temp.push(x);',
    '    }',
    '    while (!temp.isEmpty()) {',
    '        s.push(temp.pop());',
    '    }',
    '    return count;',
    '}'
  ];

  // כל צעד: השורה שרצה, והמצב אחריה. המחסנית נשמרת מלמטה למעלה
  function trace() {
    var steps = [];
    var s = [9, 2, 7, 4], temp = null, count = null, x = null;
    function snap(line, said, changed) {
      steps.push({ line: line, s: s.slice(), temp: temp ? temp.slice() : null, count: count, x: x, said: said, changed: changed || [] });
    }
    snap(1, 'הפעולה קיבלה מחסנית: 4 בראש, 9 בתחתית', ['s']);
    temp = [];
    snap(2, 'נוצרה מחסנית עזר ריקה', ['temp']);
    count = 0;
    snap(3, '`count` מתחיל מ-0', ['count']);
    for (;;) {
      var more = s.length > 0;
      snap(4, 'המחסנית ' + (more ? 'לא ריקה, נכנסים ללולאה' : 'ריקה, יוצאים מהלולאה'), []);
      if (!more) break;
      x = s.pop();
      snap(5, 'הוצאנו את ' + x + ' מראש המחסנית', ['s', 'x']);
      var even = x % 2 === 0;
      snap(6, '`' + x + ' % 2 == 0` ' + (even ? 'נכון, ' + x + ' זוגי' : 'לא נכון, ' + x + ' אי-זוגי'), []);
      if (even) {
        count++;
        snap(7, '`count` גדל ל-' + count, ['count']);
      }
      temp.push(x);
      snap(9, x + ' נשמר במחסנית העזר', ['temp']);
    }
    for (;;) {
      var left = temp.length > 0;
      snap(11, left ? 'מחזירים את האיברים למחסנית המקורית' : 'מחסנית העזר ריקה, המקורית חזרה להיות כמו בהתחלה', []);
      if (!left) break;
      s.push(temp.pop());
      snap(12, s[s.length - 1] + ' חזר למחסנית `s`', ['s', 'temp']);
    }
    snap(14, 'הפעולה מחזירה ' + count, ['count']);
    return steps;
  }

  var steps = trace();
  var at = 0, shown = -1, timer = null, touched = false;
  var codeEl = root.querySelector('[data-code]');
  var stacksEl = root.querySelector('[data-stacks]');
  var varsEl = root.querySelector('[data-vars]');
  var countEl = root.querySelector('[data-count]');
  var saidEl = root.querySelector('[data-said]');
  var range = root.querySelector('[data-range]');
  var prev = root.querySelector('[data-prev]');
  var next = root.querySelector('[data-next]');

  // הקוד, עם צבעים כמו בעורך
  var KW = /\b(public|static|int|new|while|if|return)\b/g;
  function color(line) {
    var t = line.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return t.replace(KW, '<b class="kw">$1</b>').replace(/\b(\d+)\b/g, '<span class="num">$1</span>');
  }
  codeEl.innerHTML = CODE.map(function (l, i) {
    return '<div class="ln" data-line="' + (i + 1) + '"><span class="no">' + (i + 1) + '</span><code>' + (color(l) || ' ') + '</code></div>';
  }).join('');
  range.max = String(steps.length - 1);

  // מחסנית מצוירת. anim: 'in' — האיבר העליון נכנס עכשיו (push); מספר — האיבר שיצא עכשיו (pop), עף למעלה ונעלם
  function stack(name, items, changed, anim) {
    var boxes = items.map(function (v, i) {
      var top = i === items.length - 1;
      var cls = 'box' + (top && changed ? ' new' : '') + (top && anim === 'in' ? ' in' : '');
      return '<div class="' + cls + '">' + v + (top ? '<span class="top">top</span>' : '') + '</div>';
    }).reverse().join('');
    var gone = typeof anim === 'number' ? '<div class="box gone" aria-hidden="true">' + anim + '</div>' : '';
    return '<figure class="stack"><figcaption><b>' + name + '</b><span class="chip">מחסנית</span></figcaption>'
      + '<div class="cup">' + gone + boxes + '</div></figure>';
  }

  // הנפשה רק בצעד אחד קדימה (כמו בחלון ההרצה): מה נכנס למחסנית ומה יצא ממנה
  function motion(before, after) {
    if (!before || !after) return null;
    if (after.length === before.length + 1) return 'in';
    if (after.length === before.length - 1) return before[before.length - 1];
    return null;
  }

  function render() {
    var st = steps[at];
    var lines = codeEl.children;
    for (var i = 0; i < lines.length; i++) lines[i].classList.toggle('now', i + 1 === st.line);
    var lineEl = lines[st.line - 1];
    var top = lineEl.offsetTop - codeEl.clientHeight / 2 + lineEl.offsetHeight / 2;
    codeEl.scrollTop = Math.max(0, top);
    var prevSt = shown === at - 1 ? steps[shown] : null;
    shown = at;
    stacksEl.innerHTML = stack('s', st.s, st.changed.indexOf('s') >= 0 && st.line === 12, prevSt && motion(prevSt.s, st.s))
      + (st.temp ? stack('temp', st.temp, st.changed.indexOf('temp') >= 0 && st.line === 9, prevSt && motion(prevSt.temp, st.temp)) : '');
    var rows = [
      ['s', '[' + st.s.slice().reverse().join(', ') + ']', 's'],
      ['temp', st.temp ? '[' + st.temp.slice().reverse().join(', ') + ']' : '', 'temp'],
      ['count', st.count === null ? '' : String(st.count), 'count'],
      ['x', st.x === null ? '' : String(st.x), 'x']
    ];
    varsEl.innerHTML = rows.filter(function (r) { return r[1] !== ''; }).map(function (r) {
      return '<tr' + (st.changed.indexOf(r[2]) >= 0 ? ' class="changed"' : '') + '><th>' + r[0] + '</th><td>' + r[1] + '</td></tr>';
    }).join('');
    countEl.textContent = 'צעד ' + (at + 1) + ' מתוך ' + steps.length;
    // קוד בתוך המשפט (בין ` `) — משמאל לימין, כדי שלא יתהפך בתוך העברית
    saidEl.innerHTML = 'שורה ' + st.line + ': ' + st.said.replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/`([^`]+)`/g, '<code dir="ltr">$1</code>');
    range.value = String(at);
    prev.disabled = at === 0;
    next.textContent = at === steps.length - 1 ? 'מההתחלה' : 'הבא';
  }

  // הפעלה / עצירה: כפתור, רווח במקלדת, או לבד פעם אחת כשההדגמה על המסך
  var play = root.querySelector('[data-play]');
  function playing(on) {
    if (!play) return;
    play.setAttribute('aria-pressed', on ? 'true' : 'false');
    play.textContent = on ? 'עצירה' : 'הפעלה';
  }
  function stop() {
    touched = true;
    if (timer) { clearInterval(timer); timer = null; }
    playing(false);
  }
  function run() {
    if (timer) clearInterval(timer);
    playing(true);
    timer = setInterval(function () {
      if (at >= steps.length - 1) { clearInterval(timer); timer = null; touched = true; playing(false); return; }
      at++;
      render();
    }, 1100);
  }
  function toggle() {
    if (timer) { stop(); return; }
    touched = true;
    if (at >= steps.length - 1) { at = 0; render(); }
    run();
  }
  prev.addEventListener('click', function () { stop(); if (at > 0) { at--; render(); } });
  next.addEventListener('click', function () { stop(); at = at === steps.length - 1 ? 0 : at + 1; render(); });
  range.addEventListener('input', function () { stop(); at = +range.value; render(); });
  if (play) play.addEventListener('click', toggle);
  // מקלדת (כשההדגמה בפוקוס): מימין לשמאל, שמאלה = קדימה; רווח מפעיל ועוצר; Home / End — הצעד הראשון והאחרון
  root.addEventListener('keydown', function (e) {
    var k = e.key;
    if (k === ' ' || k === 'Spacebar') {
      if (e.target.tagName === 'BUTTON') return;   // רווח על כפתור לוחץ עליו
      e.preventDefault();
      toggle();
      return;
    }
    if (e.target === range) return;
    if (k === 'ArrowLeft') { e.preventDefault(); next.click(); }
    else if (k === 'ArrowRight') { e.preventDefault(); prev.click(); }
    else if (k === 'Home' || k === 'End') { e.preventDefault(); stop(); at = k === 'Home' ? 0 : steps.length - 1; render(); }
  });

  render();
  root.classList.add('ready');

  // מתחיל לרוץ לבד פעם אחת כשההדגמה על המסך (לא כשמבקשים פחות תנועה, ולא אחרי שנגעו בה)
  var calm = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (calm || !('IntersectionObserver' in window)) return;
  var io = new IntersectionObserver(function (es) {
    es.forEach(function (e) {
      if (!e.isIntersecting || touched || timer) return;
      io.disconnect();
      run();
    });
  }, { threshold: 0.6 });
  io.observe(root);
})();
