/*
 * lang-panel.js - עורך הנוסחה (סבב 14): חלון אחד לשני המצבים, "התוכנה בונה" (נוסחה -> מכונה) ו"אני בונה" (התלמיד בונה, התוכנה בודקת).
 * בו: השדה, מקלדת הסימנים, תבניות, כתיבה יפה (חזקה נערכת בלחיצה), סיווג, מילים בשפה ומחוצה לה, ולמטה הפעולה של המצב:
 * "בנה אוטומט" (אס"ד מינימלי או אסל"ד לשפה רגולרית) או "התחלה" (עם סוג המכונה). העורך עובד על טיוטה; המשימה נשמרת במכונה רק בפעולה.
 * בלשונית "שפה" בפאנל הצד: סיכום של המשימה (summaryHTML) עם "עריכה". המנוע: js/lang/ (AutomataLang). המשימה: js/task.js.
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

  const TT = (t) => M.TYPE_INFO[t].short;
  /** אסל"ד מאס"ד: אותה מכונה בלי מצב המלכודת והקשתות אליו (קטן יותר, ועדיין מקבל בדיוק את השפה) */
  function nfaOf(dfa) {
    const m = JSON.parse(JSON.stringify(dfa)), trap = M.trapStates(dfa);
    m.type = 'nfa';
    m.states = m.states.filter((s) => !trap.has(s.id));
    m.transitions = m.transitions.filter((t) => !trap.has(t.from) && !trap.has(t.to));
    return m;
  }

  class LangPanel {
    constructor(app) {
      this.app = app;
      this.draft = { text: '', desc: '', descOwn: false, mode: 'auto' };
      this.el = null;
    }
    /** הנוסחה השתנתה: תיאור שבא עם המשימה כבר לא מתאר אותה, ולכן נמחק. תיאור שנכתב כאן נשאר. */
    setText(text) {
      const d = this.draft, stale = d.desc && !d.descOwn;
      d.text = text;
      if (stale) { d.desc = ''; const el = this.body && this.body.querySelector('#lang-desc'); if (el) el.value = ''; }
    }
    get lang() { return { text: this.draft.text, desc: this.draft.desc, locked: false, allowBuild: true }; }
    setLang(patch) { Object.assign(this.draft, patch); }
    sigma() { const a = this.ast ? this.ast.alphabet : M.inputAlphabet(this.app.machine); return a.length ? a : ['a', 'b']; }
    isOpen() { return !!(this.el && !this.el.hidden); }

    /* ---------- החלון ---------- */
    /** mode: 'auto' | 'practice' (ברירת מחדל: המצב של המשימה, או "התוכנה בונה"). opts.autoBuild: בונים מיד ("בנייה מחדש") */
    open(mode, opts) {
      const m = this.app.machine, t = root.AutomataTask.of(m);
      const fresh = opts && opts.fresh;
      this.draft = { text: t && !fresh ? t.formula : '', desc: t && !fresh ? t.desc : '', descOwn: false, mode: mode || (t && t.mode === 'practice' ? 'practice' : 'auto') };
      this.target = t && !fresh ? 'edit' : 'new';
      this.kind = null; this.flash = null; this.pickType = null;
      if (!this.el) {
        this.el = document.createElement('div');
        this.el.className = 'lang-dialog-back';
        this.el.innerHTML = '<div class="lang-dialog" role="dialog" aria-modal="true" aria-labelledby="ld-title"></div>';
        document.body.appendChild(this.el);
        const box = this.el.firstChild;
        this.bind(box);
        this.el.addEventListener('pointerdown', (e) => { if (e.target === this.el) this.downOut = true; });
        this.el.addEventListener('click', (e) => { if (e.target === this.el && this.downOut) this.close(); this.downOut = false; });
        this.el.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !this.exp && !(this.tpl && this.tpl.isOpen())) { e.stopPropagation(); this.close(); } else if (!e.target.closest('input, textarea')) e.stopPropagation(); });
      }
      this.el.hidden = false;
      this.body = this.el.firstChild;
      this.body.innerHTML = this.html();
      this.input = this.body.querySelector('#lang-input');
      // "תבנית…": רשימה נגללת בקבוצות (tpl-picker.js) במקום ענן הכפתורים
      this.tpl = new root.TplPicker(this.body.querySelector('#ld-tpl'), (text) => { this.setText(text); this.input.value = text; this.live(); this.input.focus(); });
      this.live();
      this.input.focus();
      this.input.setSelectionRange(this.input.value.length, this.input.value.length);
      root.Guide.update();
      if (opts && opts.autoBuild) this.build();
    }
    close() { if (this.el) { this.closeExp(); this.el.hidden = true; root.Guide.update(); } }

    html() {
      const d = this.draft, seg = (k, t) => '<button type="button" role="radio" aria-checked="' + (d.mode === k) + '" data-mode="' + k + '">' + t + '</button>';
      return '<header class="ld-head"><h2 id="ld-title">שפה פורמלית</h2><div class="ld-seg" role="radiogroup" aria-label="מי בונה את המכונה">' + seg('auto', 'התוכנה בונה') + seg('practice', 'אני בונה') + '</div>' +
        '<button type="button" class="btn icon ghost sm" data-l="close" aria-label="סגירה" title="סגירה (Esc)">' + I.get('x', 16) + '</button></header>' +
        '<div class="ld-body"><p class="ld-lead" id="ld-lead">' + this.leadText() + '</p>' +
        '<div class="lang-head"><h4>הנוסחה</h4><div class="ld-tpl" id="ld-tpl"></div><span class="lang-class" id="lang-class"></span></div>' +
        '<textarea id="lang-input" class="lang-input" dir="ltr" rows="2" spellcheck="false" autocomplete="off" placeholder="a^n b^m | n >= 2" aria-label="השפה, בכתיב פורמלי" aria-describedby="lang-pretty">' + esc(d.text) + '</textarea>' +
        '<div class="lang-kb" dir="ltr" role="group" aria-label="מקלדת סימנים"><span class="lk-group" id="lang-letters"></span>' + keyRows(KEYS_MAIN) +
          '<details class="lang-more"' + (this.keysOpen ? ' open' : '') + '><summary><bdi>עוד סימנים: תכונות של מילה, ביטוי רגולרי</bdi></summary><div class="lk-more">' + keyRows(KEYS_MORE) + '</div></details></div>' +
        '<div class="lang-pretty" id="lang-pretty" aria-live="polite"></div>' +
        '<input type="text" id="lang-desc" class="lang-desc" value="' + esc(d.desc || '') + '" placeholder="תיאור במילים, לפס שמעל הקנבס (לא חובה)" aria-label="תיאור השפה במילים" autocomplete="off">' +
        '<div class="lang-live" id="lang-live"></div></div>' +
        '<footer class="ld-foot" id="ld-foot"></footer>';
    }
    leadText() { return this.draft.mode === 'auto' ? 'כותבים נוסחה של שפה, ומקבלים אוטומט מוכן.' : 'כותבים נוסחה, בונים את המכונה לבד, ובודקים אם היא נכונה.'; }

    bind(box) {
      box.addEventListener('input', (e) => {
        if (e.target.id === 'lang-desc') { this.draft.desc = e.target.value; this.draft.descOwn = true; }
        if (e.target.id === 'lang-input') { this.setText(e.target.value); clearTimeout(this.t); this.t = setTimeout(() => this.live(), 120); }
      });
      box.addEventListener('keydown', (e) => {
        if (e.target.matches && e.target.matches('sup.ex') && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); e.stopPropagation(); this.openExp(e.target); return; }
        if (e.target.id === 'lang-input' && e.key === 'Enter') { e.preventDefault(); clearTimeout(this.t); this.live(); if (e.ctrlKey || e.metaKey) this.primary(); }
      });
      box.addEventListener('mousedown', (e) => { if (e.target.closest('.lk, .ll')) e.preventDefault(); });      // לא לאבד את הפוקוס בשדה
      box.addEventListener('click', (e) => this.onClick(e));
      box.addEventListener('change', (e) => { if (e.target.name === 'ld-kind') this.kind = e.target.value; if (e.target.id === 'ld-type') this.pickType = e.target.value; });
      box.addEventListener('toggle', (e) => { if (e.target.classList && e.target.classList.contains('lang-more')) this.keysOpen = e.target.open; }, true);
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
      this.renderFoot();
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

    /** הפעולה של המצב, למטה: "בנה אוטומט" (או למה אי אפשר) / "התחלה" עם סוג המכונה */
    renderFoot() {
      const el = this.body && this.body.querySelector('#ld-foot');
      if (!el) return;
      const lead = this.body.querySelector('#ld-lead');
      if (lead) lead.textContent = this.leadText();
      const ast = this.ast, flash = this.flash ? '<p class="ld-flash ' + this.flash.kind + '">' + esc(this.flash.text) + '</p>' : '';
      if (!ast) { el.innerHTML = flash + '<span class="muted small">' + (this.error ? 'קודם מתקנים את הנוסחה (ההסבר מעליה).' : 'כותבים נוסחה, או בוחרים תבנית.') + '</span><span class="ld-sp"></span><button type="button" class="btn primary" disabled>' + (this.draft.mode === 'auto' ? 'בנה אוטומט' : 'התחלה') + '</button>'; return; }
      const an = L.analyze(ast);
      if (this.draft.mode === 'auto') {
        const r = this.plan = L.build(ast);
        if (!r.ok) { el.innerHTML = flash + '<p class="ld-why">' + mixed(r.reason) + '</p><span class="ld-sp"></span><button type="button" class="btn primary" data-l="to-practice">לבנות לבד ולבדוק</button>'; return; }
        const kind = this.kind || 'dfa';
        const choice = r.kind === 'dfa' ? '<span class="ld-kind" role="radiogroup" aria-label="סוג האוטומט"><label><input type="radio" name="ld-kind" value="dfa"' + (kind === 'dfa' ? ' checked' : '') + '><span>אס"ד מינימלי</span></label>' +
          '<label><input type="radio" name="ld-kind" value="nfa"' + (kind === 'nfa' ? ' checked' : '') + '><span>אסל"ד</span></label></span>' : '<span class="muted small">ייבנה ' + esc(TT(r.kind)) + '</span>';
        el.innerHTML = flash + choice + '<span class="ld-sp"></span><button type="button" class="btn primary" data-l="build">' + I.get('transition', 16) + '<span>בנה אוטומט</span></button>';
      } else {
        const type = this.pickType || root.AutomataTask.typeFor(an.cls === 'unknown' && this.plan && this.plan.ok ? (this.plan.kind === 'dfa' ? 'regular' : 'cf') : an.cls);
        el.innerHTML = flash + '<label class="ld-type"><span>בונים</span><select id="ld-type">' + M.TYPES.map((t) => '<option value="' + t + '"' + (t === type ? ' selected' : '') + '>' + esc(M.TYPE_INFO[t].long) + '</option>').join('') + '</select></label>' +
          '<span class="ld-sp"></span><button type="button" class="btn primary" data-l="start">' + I.get('play', 16) + '<span>התחלה</span></button>';
      }
    }
    primary() { if (this.draft.mode === 'auto') this.build(); else this.start(); }

    onClick(e) {
      const key = e.target.closest('[data-ins]');
      if (key) { this.insert(key.dataset.ins); return; }
      const sup = e.target.closest('#lang-pretty sup.ex');
      if (sup) { this.openExp(sup); return; }
      const w = e.target.closest('[data-word]');
      if (w) { this.app.runWord(w.dataset.word); return; }
      const md = e.target.closest('[data-mode]');
      if (md) { this.draft.mode = md.dataset.mode; this.flash = null; this.body.querySelectorAll('[data-mode]').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.mode === this.draft.mode))); this.renderFoot(); return; }
      const b = e.target.closest('[data-l]');
      if (!b) return;
      const act = b.dataset.l;
      if (act === 'close') this.close();
      else if (act === 'build') this.build();
      else if (act === 'start') this.start();
      else if (act === 'to-practice') { this.draft.mode = 'practice'; this.body.querySelectorAll('[data-mode]').forEach((x) => x.setAttribute('aria-checked', String(x.dataset.mode === 'practice'))); this.renderFoot(); }
    }

    /** הלשונית של הסוג כבר תפוסה: להחליף / לשמור קודם לקובץ ואז להחליף (keep: להמשיך עם המכונה שעל הקנבס) */
    async askTarget(type, keepLabel) {
      const target = this.app.machines[type];
      if (!target.states.length) return 'replace';
      const r = await D.confirm({ title: 'בלשונית "' + esc(TT(type)) + '" כבר יש מכונה', html: keepLabel ? 'אפשר להמשיך לבנות על המכונה שכבר שם, או להתחיל ממכונה ריקה (אפשר לבטל עם ' + D.kbd('Ctrl+Z') + ').' : 'המכונה תוחלף במכונה שנבנתה מהנוסחה. אפשר לבטל עם ' + D.kbd('Ctrl+Z') + ', או לשמור אותה קודם לקובץ.',
        ok: keepLabel ? 'להתחיל ממכונה ריקה' : 'להחליף את המכונה הנוכחית', alt: keepLabel || 'לשמור לקובץ ואז להחליף' });
      if (r === 'alt' && !keepLabel) { this.app.switchType(type); this.app.saveFile(); return 'replace'; }
      return r === true ? 'replace' : r === 'alt' ? 'keep' : null;
    }

    async build() {
      const ast = this.ast;
      if (!ast) return;
      const app = this.app, r = L.build(ast);
      if (!r.ok) { this.flash = { text: r.reason, kind: 'err' }; this.renderFoot(); return; }
      let m = r.machine;
      if (r.kind === 'dfa' && (this.kind || 'dfa') === 'nfa') m = nfaOf(m);
      const how = await this.askTarget(m.type);
      if (!how) return;
      root.AutomataTask.attach(m, this.draft.text, 'auto', this.draft.desc ? { desc: this.draft.desc } : {});
      app.loadMachine(m, 'בניית אוטומט מהשפה', (m.tests[0] || { word: '' }).word);
      // הבדיקה של הבנייה: הוכחה כשאפשר, אחרת הבדיקה היסודית (כמו למכונה של תלמיד, בתקציב קטן יותר כדי שהבנייה לא תתעכב)
      let res = null, deep = null;
      if (L.canExact(m, ast)) { try { res = L.checkExact(m, ast); } catch (e) { res = null; } }
      if (!res) { try { deep = L.check(m, ast, { budget: m.type === 'tm' ? undefined : 20000 }); } catch (e) { deep = null; } }
      root.AutomataTask.setCheck(m, res || deep || { ok: true, exact: false, missing: [], extra: [], maxLen: r.checked.maxLen, count: r.checked.count });
      app.autosave();
      this.close();
      app.refresh();
      app.toast('נבנה ' + TT(m.type) + ' עם ' + m.states.length + ' מצבים' + (m.type === 'dfa' ? ' (המינימלי)' : '') + (res ? '. הוכח שהוא מקבל בדיוק את השפה.' : '. הוא נבדק מול הנוסחה על ' + (deep ? deep.count + (deep.long ? deep.long.count : 0) : r.checked.count).toLocaleString('he') + ' מילים.'), 'ok');
    }

    /** "אני בונה": מכונה ריקה מהסוג שנבחר (או זו שכבר שם), המשימה, ומילים לדוגמה בטבלת הבדיקה */
    async start() {
      const ast = this.ast;
      if (!ast) return;
      const app = this.app, T = root.AutomataTask, type = (this.body.querySelector('#ld-type') || {}).value || 'dfa';
      const how = await this.askTarget(type, 'להמשיך עם המכונה שכבר שם');
      if (!how) return;
      const m = how === 'keep' ? app.machines[type] : M.createMachine(type, { name: 'משימה', alphabet: ast.alphabet.slice() });
      const s = L.sample(ast, { nIn: 3, nOut: 3 }), have = new Set(m.tests.map((t) => t.word));
      const tests = s.ins.map((w) => ({ word: w, expect: true })).concat(s.outs.map((w) => ({ word: w, expect: false }))).filter((t) => !have.has(t.word));
      const apply = () => { T.attach(m, this.draft.text, 'practice', this.draft.desc ? { desc: this.draft.desc } : {}); m.tests.push(...tests); for (const c of ast.alphabet) if (!m.alphabet.includes(c)) m.alphabet.push(c); };
      if (how === 'keep') { if (app.active !== type) app.switchType(type); app.change('משימה חדשה', apply, null, true); } else { apply(); app.loadMachine(m, 'משימה חדשה', ''); }
      this.close();
      app.refresh();
      app.toast('המשימה מוכנה: בונים ' + TT(type) + ' לשפה, ולוחצים "בדיקה"', 'ok');
    }
  }

  root.LangPanel = LangPanel;
})(typeof globalThis !== 'undefined' ? globalThis : this);
