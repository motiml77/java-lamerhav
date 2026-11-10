/*
 * popover.js - החלון הצף של מעבר: בוחרים אילו אותיות (או כללים) יש על הקשת.
 * נפתח אחרי "לוחצים על מצב ואז על מצב אחר", ובלחיצה על קשת קיימת.
 *   אס"ד / אסל"ד: צ'יפים של Σ (בחירה מרובה), ε באסל"ד, ושדה "אות חדשה".
 *   אוטומט מחסנית: שורה לכל כלל (קוראים, שולפים, דוחפים). מכונת טיורינג: קוראים, כותבים, כיוון.
 * Enter = אישור, Esc = ביטול. מעבר חדש לא נוצר עד האישור.
 */
(function (root) {
  'use strict';
  const M = root.AutomataModel, I = root.Icons;
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const show = (s) => (s === '' ? 'ε' : s);
  const fromCode = (code) => (/^Key[A-Z]$/.test(code) ? code.slice(3).toLowerCase() : /^Digit\d$/.test(code) ? code.slice(5) : null);

  class TransitionPopover {
    constructor(app, el) {
      this.app = app;
      this.el = el;
      this.tid = null;
      el.addEventListener('pointerdown', (e) => e.stopPropagation());
      el.addEventListener('click', (e) => this.onClick(e));
      el.addEventListener('keydown', (e) => this.onKey(e));
      el.addEventListener('input', (e) => { if (e.target.matches('.rule input')) this.readRows(); this.setError(''); });
    }
    get m() { return this.app.machine; }
    isOpen() { return !this.el.hidden; }

    /** פותח עבור קשת קיימת (tid) או קשת חדשה (from, to). */
    open(o) {
      const m = this.m;
      const t = o.tid ? M.getTransition(m, o.tid) : null;
      this.tid = t ? t.id : null;
      this.from = t ? t.from : o.from;
      this.to = t ? t.to : o.to;
      this.extra = [];
      this.suggest = (m.type === 'dfa' || m.type === 'nfa') && !M.inputAlphabet(m).length;
      const labels = t ? t.labels.map((l) => Object.assign({}, l)) : [];
      if (m.type === 'pda') this.rows = labels.length ? labels : [{ read: '', pop: '', push: '' }];
      else if (m.type === 'tm') this.rows = labels.length ? labels : [{ read: '', write: '', move: 'R' }];
      else this.picked = new Set(labels.map((l) => l.read));
      this.app.canvas.preview = t ? null : { from: this.from, to: this.to };
      this.el.hidden = false;
      this.app.canvas.host.classList.add('popping');
      this.app.hint(null);
      this.app.canvas.render();
      this.draw();
      this.reposition();
      const first = this.el.querySelector('.sym-chip, .rule input, .new-sym');
      (first || this.el).focus();
    }

    sigma() {
      const base = M.inputAlphabet(this.m);
      for (const x of this.extra) if (!base.includes(x)) base.push(x);
      return base;
    }

    draw() {
      const m = this.m, type = m.type;
      const name = (id) => esc(M.stateName(m, id));
      const loop = this.from === this.to;
      let h = '<div class="pop-head"><div><div class="pop-kicker">' + (this.tid ? 'עריכת מעבר' : 'מעבר חדש') + '</div>' +
        '<div class="pop-title" dir="ltr">' + name(this.from) + ' <span>' + (loop ? '↻' : '→') + '</span> ' + (loop ? '' : name(this.to)) + '</div></div>' +
        '<button type="button" class="pop-x" data-act="cancel" aria-label="ביטול">' + I.get('x', 16) + '</button></div>';
      if (type === 'dfa' || type === 'nfa') h += this.faHTML();
      else h += this.rulesHTML();
      h += '<div class="pop-err" role="alert"></div>' +
        '<div class="pop-foot"><button type="button" class="btn primary sm" data-act="ok">אישור</button><button type="button" class="btn secondary sm" data-act="cancel">ביטול</button>' +
        '<span class="pop-keys"><bdi><kbd>Enter</kbd></bdi> אישור · <bdi><kbd>Esc</kbd></bdi> ביטול</span></div>';
      this.el.innerHTML = h;
      this.updateWarn();
    }

    faHTML() {
      const sig = this.sigma();
      let h = '<p class="pop-q">אילו אותיות קוראים במעבר הזה?</p>';
      const eps = this.m.type === 'nfa' ? '<button type="button" class="sym-chip eps" data-sym="" aria-pressed="' + this.picked.has('') + '" title="מעבר בלי לקרוא אות">ε</button>' : '';
      // כשהאלפבית היה ריק בפתיחה: הצעות מוכנות (a, b, 0, 1) עד שבוחרים
      const sugg = this.suggest ? ['a', 'b', '0', '1'].filter((x) => !sig.includes(x)).map((x) => '<button type="button" class="sym-chip ghost" data-add="' + x + '" title="הוספה לאלפבית">+ ' + x + '</button>').join('') : '';
      if (!sig.length) h += '<p class="pop-empty">עוד אין אותיות באלפבית. לוחצים על הצעה, או מקלידים אות חדשה:</p>';
      h += '<div class="chips-pick" dir="ltr">' + sig.map((x) => '<button type="button" class="sym-chip" data-sym="' + esc(x) + '" aria-pressed="' + this.picked.has(x) + '">' + esc(x) + '</button>').join('') + eps + sugg + '</div>';
      h += '<div class="new-row"><input class="new-sym" type="text" maxlength="1" dir="ltr" placeholder="אות חדשה" aria-label="אות חדשה לאלפבית" spellcheck="false" autocomplete="off">' +
        '<button type="button" class="btn ghost sm" data-act="add">' + I.get('plus', 16) + '<span>הוספה ל-Σ</span></button></div>' +
        '<div class="pop-warn" role="status"></div>';
      return h;
    }

    rulesHTML() {
      const pda = this.m.type === 'pda';
      const blank = this.m.blank || '_';
      const head = pda ? '<span>קוראים</span><span>שולפים</span><span>דוחפים</span>' : '<span>קוראים</span><span>כותבים</span><span>זזים</span>';
      const rows = this.rows.map((r, i) => {
        const f = (k, ph, max) => '<input type="text" dir="ltr" data-k="' + k + '" value="' + esc(r[k] === '' ? '' : r[k]) + '" placeholder="' + ph + '"' + (max ? ' maxlength="' + max + '"' : '') + ' spellcheck="false" autocomplete="off" aria-label="' + k + '">';
        const cells = pda ? f('read', 'ε', 2) + f('pop', 'ε', 2) + f('push', 'ε', 12)
          : f('read', blank, 1) + f('write', blank, 1) + '<span class="moves">' + [['L', '←'], ['S', '•'], ['R', '→']].map(([mv, ar]) => '<button type="button" data-move="' + mv + '" aria-pressed="' + (r.move === mv) + '" title="' + ({ L: 'שמאלה', S: 'נשאר', R: 'ימינה' })[mv] + '">' + ar + ' ' + mv + '</button>').join('') + '</span>';
        return '<div class="rule" data-i="' + i + '" dir="ltr">' + cells + '<button type="button" class="rule-del" data-act="del-row" aria-label="מחיקת הכלל"' + (this.rows.length < 2 ? ' disabled' : '') + '>' + I.get('x', 14) + '</button></div>';
      }).join('');
      return '<p class="pop-q">' + (pda ? 'כללים: קוראים אות, שולפים מהמחסנית, דוחפים' : 'כללים: קוראים, כותבים, ואז הראש זז') + '</p>' +
        '<div class="rules ' + (pda ? 'pda' : 'tm') + '"><div class="rule-head" dir="ltr">' + head + '</div>' + rows + '</div>' +
        '<button type="button" class="btn ghost sm add-rule" data-act="add-row">' + I.get('plus', 16) + '<span>כלל נוסף</span></button>' +
        '<p class="pop-hint">' + (pda ? 'שדה ריק = ε. בדחיפה, התו השמאלי יהיה בראש המחסנית.' : 'שדה ריק = הסמל הריק <bdi class="sym">' + esc(blank) + '</bdi>.') + '</p>';
    }

    /** מעדכן את rows מהשדות (בלי לצייר מחדש, כדי לא לאבד פוקוס). */
    readRows() {
      if (!this.rows) return;
      this.el.querySelectorAll('.rule').forEach((row) => {
        const r = this.rows[+row.dataset.i];
        row.querySelectorAll('input').forEach((inp) => { r[inp.dataset.k] = inp.value.trim(); });
      });
    }

    /** אזהרה באס"ד: אות שכבר יוצאת מהמצב הזה במעבר אחר. */
    updateWarn() {
      const w = this.el.querySelector('.pop-warn');
      if (!w) return;
      const m = this.m;
      const clash = [];
      for (const t of m.transitions) {
        if (t.from !== this.from || t.id === this.tid) continue;
        for (const l of t.labels) if (this.picked.has(l.read)) clash.push({ sym: l.read, to: t.to });
      }
      this.el.querySelectorAll('.sym-chip[data-sym]').forEach((c) => c.classList.toggle('clash', clash.some((x) => x.sym === c.dataset.sym)));
      if (m.type === 'dfa' && clash.length) {
        const c = clash[0];
        w.innerHTML = I.get('warn', 14) + '<span>מ-<bdi class="sym">' + esc(M.stateName(m, this.from)) + '</bdi> כבר יש מעבר עם <bdi class="sym">' + esc(show(c.sym)) + '</bdi> (אל <bdi class="sym">' + esc(M.stateName(m, c.to)) + '</bdi>). באס"ד מותר מעבר אחד לכל אות.</span>';
      } else w.innerHTML = '';
    }

    setError(t) { const e = this.el.querySelector('.pop-err'); if (e) e.textContent = t || ''; }

    addSymbol(raw) {
      const s = Array.from(String(raw || '').trim())[0];
      if (!s) return;
      if (s === ',' || s === '/' || s === '|') { this.setError('התו "' + s + '" שמור ואי אפשר להשתמש בו כאות'); return; }
      if (M.isEps(s)) { if (this.m.type === 'nfa') this.picked.add(''); else this.setError('באס"ד אין מעברי ε'); this.draw(); return; }
      if (!this.sigma().includes(s)) this.extra.push(s);
      this.picked.add(s);
      this.draw();
      const inp = this.el.querySelector('.new-sym');
      if (inp) inp.focus();
    }

    onClick(e) {
      const b = e.target.closest('button');
      if (!b || b.disabled) return;
      if (b.dataset.sym != null) { const s = b.dataset.sym; if (this.picked.has(s)) this.picked.delete(s); else this.picked.add(s); b.setAttribute('aria-pressed', String(this.picked.has(s))); this.updateWarn(); this.setError(''); return; }
      if (b.dataset.add != null) { this.addSymbol(b.dataset.add); return; }
      if (b.dataset.move) { this.readRows(); this.rows[+b.closest('.rule').dataset.i].move = b.dataset.move; this.draw(); return; }
      const act = b.dataset.act;
      if (act === 'ok') this.confirm();
      else if (act === 'cancel') this.cancel();
      else if (act === 'add') { const inp = this.el.querySelector('.new-sym'); this.addSymbol(inp.value); }
      else if (act === 'add-row') { this.readRows(); this.rows.push(this.m.type === 'pda' ? { read: '', pop: '', push: '' } : { read: '', write: '', move: 'R' }); this.draw(); const ins = this.el.querySelectorAll('.rule input'); ins[ins.length - (this.m.type === 'pda' ? 3 : 2)].focus(); }
      else if (act === 'del-row') { this.readRows(); this.rows.splice(+b.closest('.rule').dataset.i, 1); this.draw(); }
    }

    onKey(e) {
      e.stopPropagation();
      if (e.key === 'Escape') { e.preventDefault(); this.cancel(); return; }
      const inNew = e.target.classList && e.target.classList.contains('new-sym');
      if (e.key === 'Enter') {
        e.preventDefault();
        if (inNew && e.target.value.trim()) { this.addSymbol(e.target.value); e.target.value = ''; return; }
        this.confirm();
        return;
      }
      // באס"ד/אסל"ד: הקשה על אות מסמנת אותה (גם כשהמקלדת בעברית: לפי המקש)
      if (this.picked && !e.target.matches('input') && !e.ctrlKey && !e.metaKey && !e.altKey && e.key.length === 1) {
        const sig = this.sigma();
        const cand = [e.key, fromCode(e.code)].find((c) => c && sig.includes(c));
        if (cand) { e.preventDefault(); if (this.picked.has(cand)) this.picked.delete(cand); else this.picked.add(cand); this.draw(); const c = this.el.querySelector('.sym-chip[data-sym="' + cand + '"]'); if (c) c.focus(); }
        else if (/^[a-z0-9]$/i.test(e.key)) { e.preventDefault(); this.addSymbol(e.key); }
      }
    }

    /** האוסף הסופי של התוויות, או שגיאה. */
    collect() {
      const type = this.m.type;
      if (type === 'dfa' || type === 'nfa') {
        const order = this.sigma().concat(['']);
        const labels = order.filter((s) => this.picked.has(s)).map((s) => ({ read: s }));
        return labels.length ? { labels } : { error: 'בחרו לפחות אות אחת' };
      }
      this.readRows();
      const blank = this.m.blank || '_';
      const text = this.rows.map((r) => type === 'pda'
        ? (r.read || 'ε') + ',' + (r.pop || 'ε') + '/' + (r.push || 'ε')
        : (r.read || blank) + '/' + (r.write || blank) + ',' + (r.move || 'R')).join('\n');
      const p = M.parseLabels(type, text);
      return p.errors.length ? { error: p.errors[0] } : { labels: p.labels };
    }

    confirm() {
      const res = this.collect();
      if (res.error) { this.setError(res.error); this.el.classList.remove('shake'); void this.el.offsetWidth; this.el.classList.add('shake'); return; }
      const app = this.app, m = this.m, from = this.from, to = this.to, tid = this.tid;
      const derived = M.inputAlphabet(m), extra = this.extra.slice();
      let id = tid;
      this.close();
      app.change(tid ? 'עריכת מעבר' : 'מעבר חדש', () => {
        if (extra.length && (m.alphabet.length || extra.some((x) => !res.labels.some((l) => l.read === x)))) {
          m.alphabet = Array.from(new Set((m.alphabet.length ? m.alphabet : derived).concat(extra)));
        }
        if (tid) M.setTransitionLabels(m, tid, res.labels);
        else id = M.addTransition(m, from, to, res.labels).id;
      });
      app.select([], [id], true);
    }

    cancel() { this.close(); }

    close() {
      if (this.el.hidden) return;
      this.el.hidden = true;
      this.el.innerHTML = '';
      this.app.canvas.host.classList.remove('popping');
      this.tid = null;
      this.picked = null;
      this.rows = null;
      this.app.canvas.preview = null;
      this.app.canvas.render();
      this.app.coach();
    }

    /** ליד התווית של הקשת: מתחתיה אם יש מקום, אחרת מעליה. */
    reposition() {
      if (this.el.hidden) return;
      const cv = this.app.canvas;
      const r = cv.routes.get(this.tid || '__preview') || (this.tid ? null : cv.routes.get(M.findTransition(this.m, this.from, this.to)?.id));
      if (!r) return;
      const z = cv.view.zoom;
      const c = cv.toScreen({ x: r.label.cx, y: r.label.cy });
      const w = this.el.offsetWidth, h = this.el.offsetHeight, lh = (r.label.h / 2) * z + 12, lw = (r.label.w / 2) * z + 14;
      const best = cv.placeBox([
        { x: c.x - w / 2, y: c.y + lh }, { x: c.x - w / 2, y: c.y - lh - h },
        { x: c.x + lw, y: c.y - h / 2 }, { x: c.x - lw - w, y: c.y - h / 2 },
        { x: c.x + lw, y: c.y + lh }, { x: c.x - lw - w, y: c.y + lh }, { x: c.x + lw, y: c.y - lh - h }, { x: c.x - lw - w, y: c.y - lh - h },
      ], w, h);
      this.el.style.transform = 'translate(' + Math.round(best.x) + 'px,' + Math.round(best.y) + 'px)';
    }
  }

  root.TransitionPopover = TransitionPopover;
})(typeof globalThis !== 'undefined' ? globalThis : this);
