// "נסו בעצמכם" (העמוד הראשי, #playground): מחסנית, תור, רשימה מקושרת ועץ חיפוש, עם כפתורים לפעולות.
// הציור בצבעים ובמוסכמות של חלון ההרצה (כמו ההדגמה בראש העמוד, demo.js): ירוק — נוסף, צהוב — נקרא, אדום מקווקו — יצא.
// לכל פעולה נוספת שורת Java אמיתית (unit4.collectionsLib), והשורה האחרונה מסומנת בצהוב. הכול בדפדפן, בלי שרת.
(function () {
  var root = document.querySelector('[data-playground]');
  if (!root) return;
  var calm = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  var kindsEl = Array.prototype.slice.call(root.querySelectorAll('[data-kind]'));
  var stage = root.querySelector('#pg-stage');
  var opsEl = root.querySelector('[data-pops]');
  var valEl = root.querySelector('[data-pval]');
  var saidEl = root.querySelector('[data-psaid]');
  var codeEl = root.querySelector('[data-pcode]');
  var drawEl = root.querySelector('[data-pdraw]');
  var varsEl = root.querySelector('[data-pvars]');
  var outEl = root.querySelector('[data-pout]');
  var resetEl = root.querySelector('[data-preset]');

  var MAX_LINE = 6;      // מחסנית / תור / רשימה: כמה נכנסים לציור
  var MAX_DEPTH = 3;     // עץ: 4 רמות (0..3)
  var st = null, kind = 'stack', anim = null, timers = [];

  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
  var KW = /\b(new|int|while|if|return|public|static|void|null)\b/g;
  function color(line) {
    return esc(line).replace(KW, '<b class="kw">$1</b>').replace(/(^|[^\w])(-?\d+)\b/g, '$1<span class="num">$2</span>');
  }
  function said(text) {
    saidEl.innerHTML = esc(text).replace(/`([^`]+)`/g, '<code dir="ltr">$1</code>');
  }
  function flush() { timers.forEach(clearTimeout); timers = []; }
  function later(fn, ms) { timers.push(setTimeout(fn, ms)); }

  // ---------------------------------------------------------------- המבנים
  // לכל מבנה: init (מצב ושורות התחלה), ops (כפתורים) ו-draw. כל פעולה מחזירה { line, said, anim }
  var KINDS = {
    stack: {
      file: 'StackSandbox.java',
      init: function () {
        return { items: [4, 9], x: null, lines: ['Stack<Integer> s = new Stack<Integer>();', 's.push(4);', 's.push(9);'],
          said: 'במחסנית שני איברים, ו-9 בראש. בוחרים ערך ולוחצים על פעולה.' };
      },
      ops: [
        { id: 'push', label: 'push', code: true, value: true, ok: function (s) { return s.items.length < MAX_LINE; },
          run: function (s, v) { s.items.push(v); return { line: 's.push(' + v + ');', said: v + ' נכנס לראש המחסנית', anim: { type: 'in' } }; } },
        { id: 'pop', label: 'pop', code: true, ok: function (s) { return s.items.length > 0; },
          run: function (s) {
            var x = s.items.pop(), decl = s.x === null; s.x = x;
            return { line: (decl ? 'int ' : '') + 'x = s.pop();', said: x + ' יצא מראש המחסנית, ועכשיו `x` שווה ' + x, anim: { type: 'out', v: x }, changed: 'x' };
          } },
        { id: 'top', label: 'top', code: true, ok: function (s) { return s.items.length > 0; },
          run: function (s) { var v = s.items[s.items.length - 1]; s.out.push(String(v)); return { line: 'System.out.println(s.top());', said: '`top` מחזיר את הראש, ' + v + ', בלי להוציא אותו', anim: { type: 'read' } }; } },
        { id: 'isEmpty', label: 'isEmpty', code: true, ok: function () { return true; },
          run: function (s) { var e = s.items.length === 0; s.out.push(String(e)); return { line: 'System.out.println(s.isEmpty());', said: e ? 'המחסנית ריקה: `true`' : 'יש במחסנית ' + s.items.length + ' איברים: `false`' }; } }
      ],
      vars: function (s) {
        return [['s', '[' + s.items.slice().reverse().join(',') + ']'], ['x', s.x === null ? null : String(s.x)]];
      },
      draw: function (s, a) {
        var n = s.items.length;
        var boxes = s.items.map(function (v, i) {
          var top = i === n - 1;
          var cls = 'box' + (top && a && a.type === 'in' ? ' new in' : '') + (top && a && a.type === 'read' ? ' read' : '');
          return '<div class="' + cls + '">' + v + (top ? '<span class="top">top</span>' : '') + '</div>';
        }).reverse().join('');
        var gone = a && a.type === 'out' ? '<div class="box gone" aria-hidden="true">' + a.v + '</div>' : '';
        return '<figure class="stack"><figcaption><b>s</b><span class="chip">מחסנית</span></figcaption><div class="cup">' + gone + boxes + '</div></figure>';
      }
    },

    queue: {
      file: 'QueueSandbox.java',
      init: function () {
        return { items: [3, 8], x: null, lines: ['Queue<Integer> q = new Queue<Integer>();', 'q.insert(3);', 'q.insert(8);'],
          said: 'בתור שני איברים: 3 בראש ו-8 בזנב. נכנסים בזנב ויוצאים מהראש.' };
      },
      ops: [
        { id: 'insert', label: 'insert', code: true, value: true, ok: function (s) { return s.items.length < MAX_LINE; },
          run: function (s, v) { s.items.push(v); return { line: 'q.insert(' + v + ');', said: v + ' נכנס בזנב התור', anim: { type: 'in' } }; } },
        { id: 'remove', label: 'remove', code: true, ok: function (s) { return s.items.length > 0; },
          run: function (s) {
            var x = s.items.shift(), decl = s.x === null; s.x = x;
            return { line: (decl ? 'int ' : '') + 'x = q.remove();', said: x + ' יצא מראש התור, ועכשיו `x` שווה ' + x, anim: { type: 'out', v: x }, changed: 'x' };
          } },
        { id: 'head', label: 'head', code: true, ok: function (s) { return s.items.length > 0; },
          run: function (s) { var v = s.items[0]; s.out.push(String(v)); return { line: 'System.out.println(q.head());', said: '`head` מחזיר את הראש, ' + v + ', בלי להוציא אותו', anim: { type: 'read' } }; } },
        { id: 'isEmpty', label: 'isEmpty', code: true, ok: function () { return true; },
          run: function (s) { var e = s.items.length === 0; s.out.push(String(e)); return { line: 'System.out.println(q.isEmpty());', said: e ? 'התור ריק: `true`' : 'יש בתור ' + s.items.length + ' איברים: `false`' }; } }
      ],
      vars: function (s) { return [['q', '[' + s.items.join(',') + ']'], ['x', s.x === null ? null : String(s.x)]]; },
      draw: function (s, a) {
        // כמו בחלון ההרצה: צינור פתוח, הראש משמאל (חץ יציאה) והזנב מימין (חץ כניסה), ומתחת head / tail
        var n = s.items.length;
        var cells = s.items.map(function (v, i) {
          var cls = 'box' + (i === n - 1 && a && a.type === 'in' ? ' new q-in' : '') + (i === 0 && a && a.type === 'read' ? ' read' : '');
          return '<div class="' + cls + '">' + v + '</div>';
        }).join('');
        var labels = s.items.map(function (v, i) {
          return '<span>' + (n === 1 ? 'head·tail' : i === 0 ? 'head' : i === n - 1 ? 'tail' : '') + '</span>';
        }).join('');
        var gone = a && a.type === 'out' ? '<div class="box gone-q qgone" aria-hidden="true">' + a.v + '</div>' : '';
        return '<figure class="queue"><figcaption><b>q</b><span class="chip">תור</span></figcaption>'
          + '<div class="pipe"><span class="q-arrow" aria-hidden="true"></span><div class="tube">' + gone + (cells || '<span class="q-empty">ריק</span>') + '</div><span class="q-arrow" aria-hidden="true"></span></div>'
          + '<div class="qlabels">' + labels + '</div></figure>';
      }
    },

    list: {
      file: 'ListSandbox.java',
      init: function () {
        return { items: [2, 5, 9], lost: null, lines: ['Node<Integer> lst = new Node<Integer>(9);', 'lst = new Node<Integer>(5, lst);', 'lst = new Node<Integer>(2, lst);'],
          said: 'רשימה מקושרת: 2, 5, 9. כל חוליה מצביעה על הבאה, והאחרונה על null.' };
      },
      ops: [
        { id: 'addFirst', label: 'הוספה בהתחלה', value: true, ok: function (s) { return s.items.length < MAX_LINE; },
          run: function (s, v) { s.items.unshift(v); return { line: 'lst = new Node<Integer>(' + v + ', lst);', said: 'חוליה חדשה עם ' + v + ' מצביעה על הראשונה, ו-`lst` עובר אליה', anim: { type: 'in', at: 0 } }; } },
        { id: 'addSecond', label: 'הוספה אחרי הראשונה', value: true, ok: function (s) { return s.items.length > 0 && s.items.length < MAX_LINE; },
          run: function (s, v) { s.items.splice(1, 0, v); return { line: 'lst.setNext(new Node<Integer>(' + v + ', lst.getNext()));', said: 'החוליה החדשה מצביעה על השנייה, והראשונה מצביעה עליה', anim: { type: 'in', at: 1 } }; } },
        { id: 'removeFirst', label: 'מחיקת הראשונה', ok: function (s) { return s.items.length > 0; },
          run: function (s) {
            var gone = s.items.shift();
            return { line: 'lst = lst.getNext();', said: '`lst` עובר לחוליה הבאה. אל החוליה עם ' + gone + ' כבר אין הפניה, והיא אבדה', anim: { type: 'lost', v: gone, at: 0, next: s.items.length ? 0 : -1 } };
          } },
        { id: 'removeSecond', label: 'מחיקת השנייה', ok: function (s) { return s.items.length > 1; },
          run: function (s) {
            var gone = s.items.splice(1, 1)[0];
            return { line: 'lst.setNext(lst.getNext().getNext());', said: 'הראשונה מצביעה עכשיו על השלישית, ודילגנו על ' + gone, anim: { type: 'lost', v: gone, at: 1, next: s.items.length > 1 ? 1 : -1 } };
          } }
      ],
      vars: function (s) { return [['lst', s.items.length ? 'Node [' + s.items[0] + ']' : 'null']]; },
      draw: function (s, a) {
        var W = 64, G = 34, X0 = 14, Y = 44, H = 34, n = s.items.length;
        var width = Math.max(X0 + n * (W + G) + 50, 300), lostRow = a && a.type === 'lost';
        var height = 132;
        var svg = '<svg class="pg-svg pg-list" viewBox="0 0 ' + width + ' ' + height + '" width="' + width + '" height="' + height + '" role="img">'
          + '<defs><marker id="pg-ah" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L8 4L0 8z" fill="#3B4A63"/></marker>'
          + '<marker id="pg-ah-r" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L8 4L0 8z" fill="#E06D67"/></marker></defs>';
        // lst: חץ אל החוליה הראשונה (או null)
        svg += '<text class="ptr" x="' + (X0 + 22) + '" y="14" text-anchor="middle">lst</text>';
        if (n) svg += '<line class="arr" x1="' + (X0 + 22) + '" y1="18" x2="' + (X0 + 22) + '" y2="' + (Y - 2) + '" marker-end="url(#pg-ah)"/>';
        else svg += '<text class="null" x="' + (X0 + 22) + '" y="' + (Y + 22) + '" text-anchor="middle">null</text>';
        s.items.forEach(function (v, i) {
          var x = X0 + i * (W + G), isNew = a && a.type === 'in' && a.at === i;
          svg += '<g class="node' + (isNew ? ' new in' : '') + '">'
            + '<rect class="val" x="' + x + '" y="' + Y + '" width="' + (W - 16) + '" height="' + H + '" rx="4"/>'
            + '<rect class="nx" x="' + (x + W - 16) + '" y="' + Y + '" width="16" height="' + H + '" rx="3"/>'
            + '<text x="' + (x + (W - 16) / 2) + '" y="' + (Y + 22) + '" text-anchor="middle">' + v + '</text>'
            + '<circle cx="' + (x + W - 8) + '" cy="' + (Y + H / 2) + '" r="2.6" fill="#3B4A63"/></g>';
          var x2 = i < n - 1 ? X0 + (i + 1) * (W + G) - 2 : x + W + 22;
          svg += '<line class="arr" x1="' + (x + W - 8) + '" y1="' + (Y + H / 2) + '" x2="' + x2 + '" y2="' + (Y + H / 2) + '" marker-end="url(#pg-ah)"/>';
          if (i === n - 1) svg += '<text class="null" x="' + (x + W + 26) + '" y="' + (Y + 22) + '">null</text>';
        });
        if (lostRow) {   // החוליה שאבדה: מתחת לרשימה, באדום מקווקו, עדיין מצביעה על מי שאחריה
          var lx = X0 + a.at * (W + G) + (a.at ? -G / 2 : 0), ly = Y + H + 20;
          svg += '<g class="node lost">'
            + '<rect class="val" x="' + lx + '" y="' + ly + '" width="' + (W - 16) + '" height="' + H + '" rx="4"/>'
            + '<rect class="nx" x="' + (lx + W - 16) + '" y="' + ly + '" width="16" height="' + H + '" rx="3"/>'
            + '<text x="' + (lx + (W - 16) / 2) + '" y="' + (ly + 22) + '" text-anchor="middle">' + a.v + '</text>';
          if (a.next >= 0) {
            var tx = X0 + a.next * (W + G) + (W - 16) / 2;
            svg += '<line class="arr lost-arr" x1="' + (lx + W - 8) + '" y1="' + (ly + H / 2) + '" x2="' + tx + '" y2="' + (Y + H + 3) + '" marker-end="url(#pg-ah-r)"/>';
          }
          svg += '</g>';
        }
        return '<figure class="pg-fig"><figcaption><b>lst</b><span class="chip">רשימה מקושרת</span></figcaption>' + svg + '</svg></figure>';
      }
    },

    tree: {
      file: 'TreeSandbox.java',
      init: function () {
        var s = { root: null, lines: ['BinNode<Integer> t = new BinNode<Integer>(10);'], said: 'עץ חיפוש בינארי: קטן הולך שמאלה, גדול או שווה ימינה.' };
        s.root = { v: 10, l: null, r: null };
        [5, 15, 3].forEach(function (v) { place(s.root, v); s.lines.push('insert(t, ' + v + ');'); });
        return s;
      },
      ops: [
        { id: 'insert', label: 'insert', code: true, value: true, ok: function () { return true; },
          run: function (s, v) {
            var path = [], p = s.root, depth = 0, steps = [];
            for (;;) {
              path.push(p);
              var left = v < p.v, nextNode = left ? p.l : p.r;
              steps.push(v + (left ? ' קטן מ-' + p.v + ', שמאלה' : ' לא קטן מ-' + p.v + ', ימינה'));
              if (!nextNode) break;
              p = nextNode; depth++;
            }
            if (depth + 1 > MAX_DEPTH) return { refuse: 'בציור כאן יש מקום ל-4 רמות. נסו ערך שנכנס גבוה יותר בעץ.' };
            var node = { v: v, l: null, r: null };
            if (v < p.v) p.l = node; else p.r = node;
            return { line: 'insert(t, ' + v + ');', said: steps.join('; ') + '. ' + v + ' נכנס כבן ' + (v < p.v ? 'השמאלי' : 'הימני') + ' של ' + p.v, anim: { type: 'path', path: path, node: node } };
          } },
        { id: 'inOrder', label: 'inOrder', code: true, ok: function (s) { return !!s.root; },
          run: function (s) {
            var order = []; walk(s.root, order);
            s.out.push(order.map(function (n) { return n.v; }).join(' '));
            return { line: 'inOrder(t);', said: 'סריקה תוכית: שמאל, שורש, ימין. בעץ חיפוש זה מדפיס מהקטן לגדול', anim: { type: 'walk', path: order } };
          } }
      ],
      vars: function (s) { return [['t', 'BinNode [' + s.root.v + ']']]; },
      draw: function (s, a, shownPath, done) {
        var order = []; walk(s.root, order);
        var depthOf = new Map(); (function d(n, k) { if (!n) return; depthOf.set(n, k); d(n.l, k + 1); d(n.r, k + 1); })(s.root, 0);
        var DX = 44, X0 = 26, Y0 = 26, DY = 58, R = 16;
        var width = Math.max(X0 * 2 + (order.length - 1) * DX, 300), height = Y0 * 2 + MAX_DEPTH * DY;
        var off = (width - (X0 * 2 + (order.length - 1) * DX)) / 2;
        var pos = new Map();
        order.forEach(function (n, i) { pos.set(n, { x: off + X0 + i * DX, y: Y0 + depthOf.get(n) * DY }); });
        var hidden = a && a.type === 'path' && !done ? a.node : null;
        var svg = '<svg class="pg-svg pg-tree" viewBox="0 0 ' + width + ' ' + height + '" width="' + width + '" height="' + height + '" role="img">';
        order.forEach(function (n) {
          [n.l, n.r].forEach(function (c) {
            if (!c || c === hidden) return;
            var p1 = pos.get(n), p2 = pos.get(c);
            svg += '<line class="edge' + (a && a.node === c ? ' in' : '') + '" x1="' + p1.x + '" y1="' + p1.y + '" x2="' + p2.x + '" y2="' + p2.y + '"/>';
          });
        });
        order.forEach(function (n) {
          if (n === hidden) return;
          var p = pos.get(n), cls = 'tn';
          if (shownPath && shownPath.indexOf(n) >= 0) cls += ' read';
          if (a && a.node === n) cls += ' new in';
          svg += '<g class="' + cls + '"><circle cx="' + p.x + '" cy="' + p.y + '" r="' + R + '"/><text x="' + p.x + '" y="' + (p.y + 4.5) + '" text-anchor="middle">' + n.v + '</text></g>';
        });
        return '<figure class="pg-fig"><figcaption><b>t</b><span class="chip">עץ חיפוש</span></figcaption>' + svg + '</svg></figure>';
      }
    }
  };

  function place(rootNode, v) {
    var p = rootNode;
    for (;;) {
      if (v < p.v) { if (!p.l) { p.l = { v: v, l: null, r: null }; return; } p = p.l; }
      else { if (!p.r) { p.r = { v: v, l: null, r: null }; return; } p = p.r; }
    }
  }
  function walk(n, out) { if (!n) return; walk(n.l, out); out.push(n); walk(n.r, out); }
  function has(n, v) { return !!n && (n.v === v || has(v < n.v ? n.l : n.r, v)); }

  // ---------------------------------------------------------------- ציור
  function render(changed) {
    var K = KINDS[kind];
    codeEl.innerHTML = st.lines.map(function (l, i) {
      return '<div class="ln' + (i === st.now ? ' now' : '') + '"><span class="no">' + (i + 1) + '</span><code>' + color(l) + '</code></div>';
    }).join('');
    codeEl.scrollTop = codeEl.scrollHeight;
    varsEl.innerHTML = K.vars(st).filter(function (r) { return r[1] !== null; }).map(function (r) {
      return '<tr' + (changed === r[0] || (changed === true && r[0] === K.vars(st)[0][0]) ? ' class="changed"' : '') + '><th>' + r[0] + '</th><td>' + esc(r[1]) + '</td></tr>';
    }).join('');
    outEl.textContent = st.out.slice(-3).join('\n');
    if (!(anim && (anim.type === 'path' || anim.type === 'walk'))) drawEl.innerHTML = K.draw(st, anim);
    buttons();
  }

  function buttons() {
    var K = KINDS[kind];
    opsEl.querySelectorAll('button').forEach(function (b, i) { b.disabled = !K.ops[i].ok(st); });
  }

  function value() {
    var v = parseInt(valEl.value, 10);
    if (isNaN(v) || v < -99 || v > 99) return null;
    return v;
  }
  function nextValue() {
    var v, tries = 0;
    do { v = 1 + Math.floor(Math.random() * 20); tries++; }
    while (tries < 30 && (String(v) === valEl.value || (kind === 'tree' && has(st.root, v))));
    valEl.value = String(v);
  }

  // עץ: הצמתים שבדרך נדלקים אחד אחרי השני, ואז הצומת החדש נכנס (בלי תנועה — מיד)
  function animateTree(a) {
    var K = KINDS.tree, n = a.path.length;
    if (calm) { drawEl.innerHTML = K.draw(st, a.type === 'path' ? a : null, a.path, true); anim = null; return; }
    for (var i = 0; i <= n; i++) {
      (function (k) {
        later(function () {
          var last = k === n;
          drawEl.innerHTML = K.draw(st, a.type === 'path' ? a : null, a.path.slice(0, a.type === 'walk' ? k + 1 : k + (last ? 0 : 1)), last);
          if (last) anim = null;
        }, k * (a.type === 'walk' ? 380 : 340));
      })(i);
    }
  }

  function run(op) {
    flush();
    if (anim && (anim.type === 'path' || anim.type === 'walk')) { anim = null; }
    var v = null;
    if (op.value) {
      v = value();
      if (v === null) { said('ערך בין ‎-99 ל-99'); valEl.focus(); return; }
    }
    var r = op.run(st, v);
    if (r.refuse) { said(r.refuse); return; }
    st.lines.push(r.line);
    st.now = st.lines.length - 1;
    anim = r.anim || null;
    said('שורה ' + (st.now + 1) + ': ' + r.said);
    render(r.changed || (r.anim && r.anim.type !== 'read' && r.anim.type !== 'walk' ? true : null));
    if (anim && (anim.type === 'path' || anim.type === 'walk')) animateTree(anim);
    if (op.value) nextValue();
    // מה שיצא נעלם בסוף ההנפשה (שלא יישאר מחוץ לציור)
    var g = drawEl.querySelector('.gone, .qgone');
    if (g && calm && g.parentNode) { g.parentNode.removeChild(g); g = null; }
    if (g) g.addEventListener('animationend', function () { if (g.parentNode) g.parentNode.removeChild(g); });
  }

  function pick(k, focus) {
    flush();
    kind = k;
    anim = null;
    var K = KINDS[k];
    st = K.init();
    st.out = [];
    st.now = st.lines.length - 1;
    kindsEl.forEach(function (b) {
      var on = b.getAttribute('data-kind') === k;
      b.setAttribute('aria-selected', on ? 'true' : 'false');
      b.tabIndex = on ? 0 : -1;
      if (on) { stage.setAttribute('aria-labelledby', b.id); if (focus) b.focus(); }
    });
    root.setAttribute('data-kind-now', k);
    root.querySelector('[data-pfile]').textContent = K.file;
    opsEl.innerHTML = K.ops.map(function (o) {
      return '<button type="button" data-op="' + o.id + '"' + (o.code ? ' class="mono" dir="ltr"' : '') + '>' + o.label + '</button>';
    }).join('');
    said(st.said);
    render(null);
  }

  opsEl.addEventListener('click', function (e) {
    var b = e.target.closest('button[data-op]');
    if (!b || b.disabled) return;
    var K = KINDS[kind];
    K.ops.forEach(function (o) { if (o.id === b.getAttribute('data-op')) run(o); });
  });
  valEl.addEventListener('keydown', function (e) {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    var first = KINDS[kind].ops[0];
    if (first.ok(st)) run(first);
  });
  resetEl.addEventListener('click', function () { pick(kind, false); });
  kindsEl.forEach(function (b, i) {
    b.addEventListener('click', function () { pick(b.getAttribute('data-kind'), false); });
    b.addEventListener('keydown', function (e) {
      // מימין לשמאל: שמאלה = הבא
      var k = e.key, j = k === 'ArrowLeft' ? i + 1 : k === 'ArrowRight' ? i - 1 : k === 'Home' ? 0 : k === 'End' ? kindsEl.length - 1 : null;
      if (j === null) return;
      e.preventDefault();
      pick(kindsEl[(j + kindsEl.length) % kindsEl.length].getAttribute('data-kind'), true);
    });
  });

  pick('stack', false);
  root.classList.add('ready');
})();
