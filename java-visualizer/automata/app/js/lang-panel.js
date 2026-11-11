/*
 * lang-panel.js - לשונית "שפה" בפאנל הצד: כותבים שפה בצורה פורמלית, רואים מיד מה היא אומרת (כתיבה יפה, סיווג,
 * המילים הקצרות שבשפה ושמחוצה לה), בונים ממנה אוטומט, ובודקים מולה את האוטומט שעל הקנבס.
 * המנוע: js/lang/ (AutomataLang). השפה נשמרת ב-machine.language = { text, desc, allowBuild, locked }, ומוצגת גם בפס שמעל הקנבס (lang-strip.js).
 */
(function (root) {
  'use strict';
  const L = root.AutomataLang, M = root.AutomataModel, I = root.Icons, D = root.Dialogs;
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const wordChip = (w, cls) => '<button type="button" class="w-chip ' + (cls || '') + '" data-word="' + esc(w) + '" title="הרצה של המילה הזו">' + (w === '' ? 'ε' : esc(w)) + '</button>';
  const CLS = { regular: 'ok', cf: 'cf', cfany: 'cf', noncf: 'noncf', unknown: 'unk' };
  const TYPE = { dfa: 'אס"ד', pda: 'אוטומט מחסנית', tm: 'מכונת טיורינג' };

  // המקלדת: [מה שמוצג, מה שנכנס לשדה, הסבר]. השורה הראשונה תמיד גלויה, השאר ב"עוד סימנים".
  // ⟦…⟧: החלק שנבחר אחרי ההכנסה (מקלידים ומחליפים מיד), ‸: הסמן. סבב 13: כל חזקה נכנסת כ-^( ) כדי שאפשר לשנות אותה לכל ביטוי.
  const KEYS_MAIN = [
    [['^( )', '^(‸)', 'חזקה: כותבים בתוך הסוגריים כל ביטוי, למשל a^(m+k+2)'], ['ⁿ', '^(⟦n⟧)', 'בחזקת n (נבחר: מקלידים ומחליפים)'], ['ᵐ', '^(⟦m⟧)', 'בחזקת m (נבחר: מקלידים ומחליפים)'], ['ᵏ', '^(⟦k⟧)', 'בחזקת k (נבחר: מקלידים ומחליפים)'], ['²ⁿ', '^(⟦2n⟧)', 'בחזקת 2n (נבחר: מקלידים ומחליפים)'], ['*', '*', 'אפס פעמים או יותר'], ['⁺', '⁺', 'פעם אחת או יותר']],
    [['+', '+', 'פלוס, בחזקה או בתנאי'], ['−', '-', 'מינוס, למשל b^(n-m)'], ['·', '·', 'כפל, למשל a^(n·m)'], ['²', '^⟦2⟧', 'בריבוע, למשל a^(n^2)']],
    [['|', ' | ', 'כך ש: אחריו באים התנאים'], ['≥', ' ≥ '], ['≤', ' ≤ '], ['≠', ' ≠ '], ['=', ' = '], ['<', ' < '], ['>', ' > '], ['%', ' % ', 'שארית חלוקה: n % 3 = 0'], ['mod', ' mod ', 'שארית חלוקה: n mod 3 = 1']],
    [[',', ', ', 'עוד תנאי (כמו "וגם"): n ≥ 1, m ≥ 1'], ['∈ ℕ', ' ∈ ℕ', 'מספר טבעי: n ∈ ℕ (כלומר n ≥ 0)'], ['וגם', ' וגם '], ['או', ' או '], ['לא', 'לא '], ['זוגי', ' זוגי'], ['אי-זוגי', ' אי-זוגי'], ['(', '('], [')', ')']],
  ];
  const KEYS_MORE = [
    [['w ∈ Σ*', 'w ∈ {$S}* | ', 'מילה כלשהי מעל האלפבית'], ['|w|', '|w|', 'אורך המילה'], ['#a(w)', '#$a(w)', 'כמה פעמים האות מופיעה במילה']],
    [['מתחילה ב-', 'מתחילה ב-'], ['מסתיימת ב-', 'מסתיימת ב-'], ['מכילה', 'מכילה '], ['פלינדרום', 'פלינדרום'], ['מאוזנת', 'מאוזנת', 'סוגריים מאוזנים: w ∈ {(,)}* | מאוזנת']],
    [['≡', ' ≡ ', 'שקילות: n ≡ 1 (mod 3)'], ['(mod 3)', ' (mod 3)', 'למשל n ≡ 1 (mod 3)']],
    [['ε', 'ε', 'המילה הריקה'], ['∪', ' ∪ ', 'איחוד: של ביטויים רגולריים, או של שפות { aⁿbⁿ } ∪ { bⁿaⁿ }'], ['{ }·{ }', ' · ', 'שרשור של שפות: { aⁿbⁿ } · { cᵏ }'], ['wᴿ', 'wᴿ', 'המילה w בסדר הפוך. למשל w wᴿ'], ['2ⁿ', '^(⟦2^n⟧)', 'חזקה של 2 בתוך חזקה: a^(2^n)']],
  ];
  const SUPL = { '^( )': '<i>x</i><sup class="lk-box">( )</sup>', 'ⁿ': '<i>x</i><sup>n</sup>', 'ᵐ': '<i>x</i><sup>m</sup>', 'ᵏ': '<i>x</i><sup>k</sup>', '²ⁿ': '<i>x</i><sup>2n</sup>', '⁺': '<i>x</i><sup>+</sup>', '*': '<i>x</i>*', '²': '<i>n</i><sup>2</sup>', '{ }·{ }': '{}·{}' };
  /** מקשים שממשיכים את החזקה שנבחרה (הסמן בסוף הבחירה, בתוך הסוגריים). כל מקש אחר ממשיך אחרי החזקה */
  const ARITH = new Set(['+', '-', '·', '^⟦2⟧']);
  const keyRows = (rows) => rows.map((list) => '<span class="lk-group">' + list.map(([show, ins, tip]) =>
    '<button type="button" class="lk" data-ins="' + esc(ins) + '"' + (tip ? ' title="' + esc(tip) + '"' : '') + ' aria-label="' + esc(tip || show) + '" tabindex="-1"><bdi>' + (SUPL[show] || esc(show)) + '</bdi></button>').join('') + '</span>').join('');
  /** טקסט עברי עם קטעי נוסחה: הקטעים הלטיניים נעטפים ב-bdi כדי שלא יתהפכו. */
  const mixed = (t) => String(t).split(/([A-Za-z0-9^#|(){}∈*+<>=≥≤≠_][A-Za-z0-9^#|(){}∈*+<>=≥≤≠_, ]*[A-Za-z0-9^#|(){}∈*+<>=≥≤≠_)]|[A-Za-z0-9])/).map((x, i) => (i % 2 ? '<bdi dir="ltr" class="sym">' + esc(x) + '</bdi>' : esc(x))).join('');

  class LangPanel {
    constructor(app) { this.app = app; this.result = null; this.info = null; this.busy = null; this.ownDesc = new WeakSet(); }
    /** הנוסחה השתנתה: תיאור שבא עם דוגמה או עם קובץ כבר לא מתאר אותה, ולכן נמחק. תיאור שהמשתמש כתב כאן נשאר. */
    setText(text) {
      const stale = this.lang.desc && !this.ownDesc.has(this.app.machine);
      this.setLang(stale ? { text, desc: '' } : { text });
      const d = this.body && this.body.querySelector('#lang-desc');
      if (stale && d) d.value = '';
    }
    get lang() { const m = this.app.machine; return m.language || { text: '', allowBuild: true, locked: false }; }
    setLang(patch) { const m = this.app.machine; m.language = Object.assign({ text: '', allowBuild: true, locked: false }, m.language, patch); this.app.autosave(); }
    sigma() { const a = this.ast ? this.ast.alphabet : M.inputAlphabet(this.app.machine); return a.length ? a : ['a', 'b']; }

    html() {
      const lang = this.lang, locked = lang.locked;
      return '<section class="sec lang"><div class="lang-head"><h4>השפה</h4><span class="lang-class" id="lang-class"></span></div>' +
        (locked ? '<p class="muted small">זו השפה של התרגיל. בונים אוטומט שמקבל בדיוק אותה, ובודקים.</p>' : '') +
        '<textarea id="lang-input" class="lang-input" dir="ltr" rows="2" spellcheck="false" autocomplete="off" placeholder="a^n b^m | n >= 2"' + (locked ? ' readonly' : '') + ' aria-label="השפה, בכתיב פורמלי" aria-describedby="lang-pretty">' + esc(lang.text) + '</textarea>' +
        (locked ? '' : '<div class="lang-kb" dir="ltr" role="group" aria-label="מקלדת סימנים"><span class="lk-group" id="lang-letters"></span>' + keyRows(KEYS_MAIN) +
          '<details class="lang-more"' + (this.keysOpen ? ' open' : '') + '><summary><bdi>עוד סימנים: תכונות של מילה, ביטוי רגולרי</bdi></summary><div class="lk-more">' + keyRows(KEYS_MORE) + '</div></details></div>') +
        '<div class="lang-pretty" id="lang-pretty" aria-live="polite"></div>' +
        (locked ? '' : '<input type="text" id="lang-desc" class="lang-desc" value="' + esc(lang.desc || '') + '" placeholder="תיאור במילים, לפס שמעל הקנבס (לא חובה)" aria-label="תיאור השפה במילים" autocomplete="off">') +
        '<div class="lang-live" id="lang-live"></div>' +
        '<div class="lang-actions">' + (locked && !lang.allowBuild ? '' : '<button type="button" class="btn secondary sm" data-l="build">' + I.get('transition', 16) + '<span>בנה אוטומט</span></button>') +
        '<button type="button" class="btn primary sm" data-l="check">' + I.get('check', 16) + '<span>בדיקה מול השפה</span></button></div>' +
        '<div class="lang-result" id="lang-result" aria-live="polite"></div></section>' +
        (locked ? '<section class="sec"><p class="muted small">קובץ תרגיל: השפה נעולה.</p><button type="button" class="link" data-l="unlock">ביטול הנעילה (למורים)</button></section>'
          : '<section class="sec"><h5>תבניות להתחלה</h5><div class="lang-templates">' + L.TEMPLATES.map((t, i) => '<button type="button" class="lt" data-tpl="' + i + '" dir="rtl"><bdi>' + esc(t.name) + '</bdi></button>').join('') + '</div></section>' +
          '<section class="sec"><h5>למורים: קובץ תרגיל</h5><p class="muted small">קובץ עם השפה בלבד וקנבס ריק. התלמיד בונה אוטומט ובודק אותו מול השפה.</p>' +
          '<label class="check"><input type="checkbox" data-l="allow"' + (lang.allowBuild ? ' checked' : '') + '><span>לאפשר לתלמיד את "בנה אוטומט"</span></label>' +
          '<button type="button" class="btn secondary sm" data-l="exercise">' + I.get('save', 16) + '<span>שמירת קובץ תרגיל</span></button></section>');
    }

    mount(body) {
      this.body = body;
      this.input = body.querySelector('#lang-input');
      if (!this.bound) {
        this.bound = true;
        body.addEventListener('input', (e) => { if (e.target.id === 'lang-desc') { this.ownDesc.add(this.app.machine); this.setLang({ desc: e.target.value }); this.app.strip.render(); } });
        body.addEventListener('keydown', (e) => { if (e.target.id === 'lang-desc') e.stopPropagation(); });
        body.addEventListener('keydown', (e) => { if (e.target.matches && e.target.matches('sup.ex') && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); e.stopPropagation(); this.openExp(e.target); } });
        body.addEventListener('input', (e) => { if (e.target.id === 'lang-input') { this.setText(e.target.value); clearTimeout(this.t); this.t = setTimeout(() => this.live(), 120); } });
        body.addEventListener('keydown', (e) => { if (e.target.id === 'lang-input') { e.stopPropagation(); if (e.key === 'Enter') { e.preventDefault(); clearTimeout(this.t); this.live(); } } });
        body.addEventListener('mousedown', (e) => { if (e.target.closest('.lk, .ll')) e.preventDefault(); });      // לא לאבד את הפוקוס בשדה
        body.addEventListener('click', (e) => this.onClick(e));
        body.addEventListener('change', (e) => { if (e.target.dataset.l === 'allow') this.setLang({ allowBuild: e.target.checked }); });
        body.addEventListener('toggle', (e) => { if (e.target.classList && e.target.classList.contains('lang-more')) this.keysOpen = e.target.open; }, true);
      }
      this.live();
    }

    /** מפענח את מה שכתוב, ומעדכן: כתיבה יפה או שגיאה, סיווג, מילים לדוגמה, אותיות המקלדת. */
    live() {
      const q = (id) => this.body.querySelector('#' + id);
      if (!q('lang-pretty')) return;
      this.closeExp();
      const text = this.lang.text, pretty = q('lang-pretty'), live = q('lang-live'), cls = q('lang-class');
      this.ast = null; this.error = null;
      if (text.trim()) { try { this.ast = L.parse(text, { alphabet: M.inputAlphabet(this.app.machine) }); } catch (e) { if (!(e instanceof L.LangError)) throw e; this.error = e; } }
      this.input.classList.toggle('bad', !!this.error);
      if (this.error) {
        const e = this.error, a = Math.min(e.pos, text.length), b = Math.min(Math.max(e.end, a), text.length);
        pretty.className = 'lang-pretty err';
        pretty.innerHTML = '<div class="le-src" dir="ltr">' + esc(text.slice(0, a)) + '<mark>' + (esc(text.slice(a, b)) || '&nbsp;') + '</mark>' + esc(text.slice(b)) + '</div><b>' + mixed(e.message) + '</b>' + (e.hint ? '<span>' + mixed(e.hint) + '</span>' : '');
        live.innerHTML = ''; cls.textContent = ''; cls.className = 'lang-class';
      } else if (!this.ast) {
        pretty.className = 'lang-pretty empty';
        pretty.innerHTML = 'עוד לא נכתבה שפה. אפשר להתחיל מתבנית, או להקליד, למשל <bdi dir="ltr" class="sym">a^n b^n</bdi>';
        live.innerHTML = ''; cls.textContent = ''; cls.className = 'lang-class';
      } else {
        const an = L.analyze(this.ast), s = L.sample(this.ast);
        pretty.className = 'lang-pretty';
        pretty.innerHTML = '<div class="lp-math" dir="ltr">' + L.pretty(this.ast, { edit: !this.lang.locked }).html + '</div>' + this.defaults();
        pretty.querySelectorAll('sup.ex').forEach((x) => { x.title = 'לחיצה לעריכת החזקה'; x.setAttribute('role', 'button'); x.tabIndex = 0; });
        cls.className = 'lang-class ' + CLS[an.cls];
        cls.textContent = an.label;
        cls.title = an.why;
        const none = '<span class="muted small">אין (עד האורך שנבדק)</span>';
        live.innerHTML = '<div class="ll-row"><span class="ll-title in">בשפה</span><span class="ll-words" dir="ltr">' + (s.ins.map((w) => wordChip(w, 'in')).join('') || none) + '</span></div>' +
          '<div class="ll-row"><span class="ll-title out">לא בשפה</span><span class="ll-words" dir="ltr">' + (s.outs.map((w) => wordChip(w, 'out')).join('') || none) + '</span></div>' +
          '<p class="lang-why">' + esc(an.why) + '.</p>';
      }
      const letters = q('lang-letters');
      if (letters) letters.innerHTML = this.sigma().concat(['c', '0', '1'].filter((c) => !this.sigma().includes(c)).slice(0, 2)).map((c) => '<button type="button" class="lk ll" data-ins="' + esc(c) + '" tabindex="-1">' + esc(c) + '</button>').join('');
      this.app.strip.render();
      this.renderResult();
    }

    /** בלוקים בלי תנאי: מזכירים שכל משתנה הוא מספר טבעי (n, m ≥ 0). */
    defaults() {
      const a = this.ast;
      if (!a || a.kind !== 'blocks' || a.cond || !a.vars.length) return '';
      return '<div class="lp-note">כל משתנה הוא מספר שלם אי-שלילי: <bdi dir="ltr" class="sym">' + esc(a.vars.join(', ')) + ' ≥ 0</bdi></div>';
    }

    insert(raw) {
      const el = this.input, S = this.sigma();
      let ins = raw.replace('$S', S.join(',')).replace('$a', S[0]);
      let a = el.selectionStart, b = el.selectionEnd;
      // חזקה שמקש הכניס עדיין נבחרת: מקלדת אמיתית מחליפה אותה; מקש חשבון ממשיך אותה, וכל מקש אחר בא אחריה
      const ex = this.expSel;
      if (ex && a === ex.s && b === ex.e && el.value === ex.v) a = b = ARITH.has(raw) ? ex.e : ex.after;
      this.expSel = null;
      let selS = null, selE = null;
      const o = ins.indexOf('⟦');
      if (o >= 0) { selS = o; selE = ins.indexOf('⟧') - 1; ins = ins.replace('⟦', '').replace('⟧', ''); }
      const c = ins.indexOf('‸');
      if (c >= 0) { selS = selE = c; ins = ins.replace('‸', ''); }
      const lead = ins[0] === ' ' && (a === 0 || el.value[a - 1] === ' ') ? ins.slice(1) : ins, cut = ins.length - lead.length;
      el.value = el.value.slice(0, a) + lead + el.value.slice(b);
      if (selS != null) {
        el.selectionStart = a + selS - cut; el.selectionEnd = a + selE - cut;
        if (selE > selS) this.expSel = { s: el.selectionStart, e: el.selectionEnd, after: a + lead.length, v: el.value };
      } else el.selectionStart = el.selectionEnd = a + lead.length;
      el.focus();
      el.dispatchEvent(new Event('input', { bubbles: true }));
      if (this.expSel) this.expSel.v = el.value;
    }

    /* ---------- עריכה של חזקה בלחיצה על התצוגה היפה (סבב 13, lang/exp-edit.js) ---------- */
    openExp(sup) {
      this.closeExp();
      const at = +sup.dataset.ex, src = this.ast && L.exponentAt(this.ast, at);
      if (!src) return;
      const box = this.body.querySelector('#lang-pretty'), r = sup.getBoundingClientRect(), b = box.getBoundingClientRect(), z = this.app.canvas.uiScale();
      const pop = document.createElement('div');
      pop.className = 'exp-pop';
      pop.setAttribute('role', 'dialog');
      pop.setAttribute('aria-label', 'עריכת החזקה');
      pop.innerHTML = '<label><span>החזקה</span><input type="text" dir="ltr" class="exp-input" spellcheck="false" autocomplete="off" aria-label="החזקה"></label><div class="exp-err" role="alert"></div>';
      box.appendChild(pop);
      const left = Math.max(0, Math.min(box.clientWidth - pop.offsetWidth, (r.left - b.left) / z + (r.width / z) / 2 - pop.offsetWidth / 2));
      Object.assign(pop.style, { left: left + 'px', top: ((r.bottom - b.top) / z + 6) + 'px' });
      sup.classList.add('editing');
      const input = pop.querySelector('input');
      input.value = L.exponentText(this.lang.text, src);
      this.exp = { pop, at, sup, input };
      input.addEventListener('keydown', (e) => {
        e.stopPropagation();
        if (e.key === 'Enter') { e.preventDefault(); this.applyExp(true); }
        else if (e.key === 'Escape') { e.preventDefault(); this.closeExp(); this.input.focus(); }
      });
      input.addEventListener('input', () => { pop.querySelector('.exp-err').textContent = ''; });
      input.addEventListener('blur', () => setTimeout(() => { if (this.exp && this.exp.input === input) this.applyExp(false); }, 0));
      input.focus();
      input.select();
    }
    /** strict: Enter (שגיאה נשארת על המסך). בלי strict (יציאה מהשדה): נוסחה לא תקינה פשוט לא נשמרת */
    applyExp(strict) {
      const x = this.exp;
      if (!x) return;
      const r = L.editExponent(this.lang.text, this.ast, x.at, x.input.value, { alphabet: M.inputAlphabet(this.app.machine) });
      if (!r.ok) { if (strict) { x.pop.querySelector('.exp-err').textContent = r.error; x.input.focus(); } else this.closeExp(); return; }
      this.closeExp();
      this.result = null;
      this.setText(r.text);
      this.input.value = r.text;
      this.live();
      this.input.focus();
      this.input.setSelectionRange(r.start, r.end);
    }
    closeExp() {
      const x = this.exp;
      if (!x) return;
      this.exp = null;
      x.pop.remove();
      if (x.sup) x.sup.classList.remove('editing');
    }

    onClick(e) {
      const app = this.app;
      const key = e.target.closest('[data-ins]');
      if (key) { this.insert(key.dataset.ins); return; }
      const sup = e.target.closest('#lang-pretty sup.ex');
      if (sup) { this.openExp(sup); return; }
      const w = e.target.closest('[data-word]');
      if (w) { app.runWord(w.dataset.word); return; }
      const tpl = e.target.closest('[data-tpl]');
      if (tpl) { this.result = null; this.setText(L.TEMPLATES[+tpl.dataset.tpl].text); this.input.value = this.lang.text; this.live(); this.input.focus(); return; }
      const b = e.target.closest('[data-l]');
      if (!b) return;
      const act = b.dataset.l;
      if (act === 'build') this.build();
      else if (act === 'check') this.check();
      else if (act === 'add-tests') this.addTests();
      else if (act === 'exercise') this.saveExercise();
      else if (act === 'unlock') { this.setLang({ locked: false, allowBuild: true }); app.side.render(); }
    }

    ready() {
      if (this.ast) return true;
      this.note(this.error ? 'קודם צריך לתקן את הנוסחה (ההסבר מתחת לשדה).' : 'קודם כותבים שפה, או בוחרים תבנית.');
      if (this.input) this.input.focus();
      return false;
    }
    note(text, kind) { this.result = { note: text, kind: kind || 'info' }; this.renderResult(); }

    async build() {
      if (!this.ready()) return;
      const app = this.app, r = L.build(this.ast);
      if (!r.ok) { this.note(r.reason); return; }
      const target = app.machines[r.kind];
      if (target.states.length) {
        const ok = await D.confirm({ title: 'לבנות ' + TYPE[r.kind] + ' מהשפה?', html: 'המכונה שעכשיו בלשונית "' + esc(M.TYPE_INFO[r.kind].short) + '" תוחלף. אפשר לבטל אחר כך עם ' + D.kbd('Ctrl+Z') + '.', ok: 'בנייה' });
        if (!ok) return;
      }
      r.machine.language = Object.assign({}, this.lang, { text: this.lang.text });
      if (this.ownDesc.has(app.machine)) this.ownDesc.add(r.machine);
      app.loadMachine(r.machine, 'בניית אוטומט מהשפה', (r.machine.tests[0] || { word: '' }).word);
      app.side.show('lang');
      this.note('נבנה ' + TYPE[r.kind] + ' עם ' + r.machine.states.length + ' מצבים' + (r.kind === 'dfa' ? ' (המינימלי)' : '') + '. הוא נבדק מול הנוסחה על ' + r.checked.count.toLocaleString('he') + ' מילים.', 'ok');
    }

    check() {
      if (!this.ready()) return;
      const app = this.app, m = app.machine;
      if (!m.states.length) { this.note('אין עדיין אוטומט על הקנבס. בונים אותו (לחיצה כפולה מוסיפה מצב), ואז בודקים.'); return; }
      const errs = app.issues.filter((i) => i.level === 'error');
      if (errs.length) { this.note('קודם צריך לתקן את האוטומט: ' + errs[0].text, 'err'); return; }
      const ast = this.ast, sig = app.machineSig() + '\n' + this.lang.text;
      const finish = (res) => { this.busy = null; this.result = { res, sig, hint: L.hint(res, m) }; this.renderResult(); app.toast(res.ok ? 'האוטומט עבר את הבדיקה מול השפה' : 'נמצאו מילים שהאוטומט טועה בהן', res.ok ? 'ok' : 'err'); };
      if (L.canExact(m, ast)) { try { finish(L.checkExact(m, ast)); return; } catch (e) { /* גדול מדי: בדיקה חסומה */ } }
      const c = L.checker(m, ast), t0 = performance.now();
      this.busy = c;
      const tick = () => {
        if (this.busy !== c) return;
        const t = performance.now();
        while (performance.now() - t < 30 && !c.step(10)) { /* עוד */ }
        if (!c.result.done && performance.now() - t0 > 6000) { c.result.done = true; c.result.maxLen = Math.max(0, c.result.len - 1); }
        if (c.result.done) finish(c.result); else { this.note('בודק... ' + c.result.count.toLocaleString('he') + ' מילים', 'busy'); this.busy = c; setTimeout(tick, 0); }
      };
      tick();
    }

    renderResult() {
      const el = this.body && this.body.querySelector('#lang-result');
      if (!el) return;
      const r = this.result;
      const reveal = () => { if (r && r !== this.shown) { this.shown = r; el.scrollIntoView({ block: 'nearest' }); } };
      if (!r) { el.innerHTML = ''; return; }
      setTimeout(reveal, 0);
      if (r.note) { el.innerHTML = '<div class="lr-box ' + r.kind + '">' + I.get(r.kind === 'ok' ? 'check' : r.kind === 'err' ? 'warn' : 'info', 16) + '<span>' + esc(r.note) + '</span></div>'; return; }
      if (r.sig !== this.app.machineSig() + '\n' + this.lang.text) { el.innerHTML = '<div class="lr-box info">' + I.get('info', 16) + '<span>האוטומט או השפה השתנו מאז הבדיקה. אפשר לבדוק שוב.</span></div>'; return; }
      const res = r.res, n = (x) => x.toLocaleString('he');
      const scope = res.exact ? '' : 'נבדקו ' + (res.maxLen > 0 ? 'כל ' : '') + n(res.count) + ' המילים עד אורך ' + res.maxLen + '.';
      if (res.ok) {
        el.innerHTML = '<div class="lr-box ok big">' + I.get('check', 20) + '<span><b>' + (res.exact ? 'האוטומט מקבל בדיוק את השפה' : scope + ' לא נמצאה טעות') + '</b>' +
          '<small>' + (res.exact ? 'זו הוכחה: האוטומט הושווה במלואו לאס"ד המינימלי של השפה.' : 'זו בדיקה, לא הוכחה: ייתכן שיש טעות במילה ארוכה יותר.') + (res.undecided ? ' ב-' + n(res.undecided) + ' מילים ההרצה לא הוכרעה.' : '') + '</small></span></div>';
        return;
      }
      const list = (title, ws, cls) => (ws.length ? '<div class="lr-list"><div class="lr-title ' + cls + '">' + title + '</div><div class="ll-words" dir="ltr">' + ws.map((w) => wordChip(w, cls)).join('') + '</div></div>' : '');
      el.innerHTML = '<div class="lr-box err big">' + I.get('x', 20) + '<span><b>האוטומט עוד לא מקבל את השפה</b><small>' + esc(r.hint) + '</small></span></div>' +
        list('צריכות להתקבל, ונדחו', res.missing, 'in') + list('צריכות להידחות, והתקבלו', res.extra, 'out') +
        '<p class="muted small">לחיצה על מילה מריצה אותה, כדי לראות איפה היא טועה. ' + (res.exact ? 'אלה המילים הקצרות ביותר.' : scope) + '</p>' +
        '<button type="button" class="btn secondary sm" data-l="add-tests">' + I.get('beaker', 16) + '<span>הוספה לטבלת הבדיקות</span></button>';
    }

    addTests() {
      const res = this.result && this.result.res, m = this.app.machine;
      if (!res) return;
      const have = new Set(m.tests.map((t) => t.word));
      const add = res.missing.map((w) => ({ word: w, expect: true })).concat(res.extra.map((w) => ({ word: w, expect: false }))).filter((t) => !have.has(t.word));
      this.app.change('הוספת בדיקות מהשפה', () => m.tests.push(...add), null, true);
      this.app.toast(add.length ? 'נוספו ' + add.length + ' מילים לטבלת הבדיקות' : 'המילים כבר בטבלה', 'ok');
    }

    saveExercise() {
      if (!this.ready()) return;
      const app = this.app, name = L.pretty(this.ast).text;
      const ex = M.createMachine(app.active, { name: 'תרגיל', alphabet: this.ast.alphabet });
      ex.language = { text: this.lang.text, allowBuild: !!this.lang.allowBuild, locked: true };
      if (this.lang.desc) ex.language.desc = this.lang.desc;
      app.download(new Blob([M.serialize(ex)], { type: 'application/json' }), 'תרגיל - ' + name.replace(/[\\/:*?"<>|]+/g, '_') + '.automaton.json');
      app.toast('נשמר קובץ תרגיל', 'ok');
    }
  }

  root.LangPanel = LangPanel;
})(typeof globalThis !== 'undefined' ? globalThis : this);
