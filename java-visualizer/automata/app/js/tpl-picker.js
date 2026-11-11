/*
 * tpl-picker.js - "תבנית…" בעורך הנוסחה (סבב 14): שורה אחת שנפתחת לרשימה נגללת (עד 280px), בקבוצות (רגולריות, חסרות הקשר,
 * עם תנאים, איחוד ושרשור, לא חסרות הקשר). כל פריט: הנוסחה בכתיבה יפה ושם קצר באפור. סינון בהקלדה, ↑ ↓ Enter Esc.
 * בחירה ממלאת את השדה (onPick) וסוגרת את הרשימה. במקום ענן הכפתורים של התבניות.
 */
(function (root) {
  'use strict';
  const L = root.AutomataLang, I = root.Icons;
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  let PRETTY = null;
  const pretty = () => {
    // בלי "L =" והסוגריים שמסביב, ובתכונה של מילה רק התנאי: קצר מספיק לשורה אחת
    const short = (h) => h.replace(/^L = /, '').replace(/^\{ w ∈ \{[^}]*\}\* \| (.*) \}$/, '$1').replace(/^\{ (.*) \}$/, '$1');
    if (!PRETTY) PRETTY = L.TEMPLATES.map((t) => { try { const p = L.pretty(L.parse(t.text)); return { html: short(p.html), text: p.text }; } catch (e) { return { html: esc(t.text), text: t.text }; } });
    return PRETTY;
  };

  class TplPicker {
    /** host: האלמנט שבתוכו נבנה הרכיב; onPick(text) */
    constructor(host, onPick) {
      this.onPick = onPick;
      this.el = host;
      this.el.classList.add('tpl');
      this.el.innerHTML = '<button type="button" class="tpl-btn" aria-haspopup="listbox" aria-expanded="false">' + I.get('list', 15) + '<span>תבנית…</span>' + I.get('chevronDown', 14) + '</button>' +
        '<div class="tpl-pop" hidden><input type="text" class="tpl-filter" placeholder="סינון: aⁿbⁿ, פלינדרום, מכילה…" aria-label="סינון התבניות" autocomplete="off" spellcheck="false">' +
        '<div class="tpl-list" role="listbox" aria-label="תבניות"></div></div>';
      this.btn = this.el.querySelector('.tpl-btn');
      this.pop = this.el.querySelector('.tpl-pop');
      this.filter = this.el.querySelector('.tpl-filter');
      this.list = this.el.querySelector('.tpl-list');
      this.btn.addEventListener('click', () => (this.pop.hidden ? this.open() : this.close()));
      this.filter.addEventListener('input', () => { this.active = 0; this.render(); });
      this.el.addEventListener('keydown', (e) => this.onKey(e));
      this.list.addEventListener('mousedown', (e) => e.preventDefault());
      this.list.addEventListener('click', (e) => { const o = e.target.closest('[data-i]'); if (o) this.pick(+o.dataset.i); });
      this.list.addEventListener('mousemove', (e) => { const o = e.target.closest('[data-k]'); if (o && +o.dataset.k !== this.active) { this.active = +o.dataset.k; this.mark(); } });
      this.outside = (e) => { if (!this.el.contains(e.target)) this.close(); };
    }
    isOpen() { return !this.pop.hidden; }
    open() {
      this.pop.hidden = false;
      // position: fixed לפי הכפתור, כדי שלא ייחתך בתוך החלון הנגלל של העורך
      const z = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--ui-zoom')) || 1, r = this.btn.getBoundingClientRect();
      const left = Math.max(8, Math.min(r.left / z, innerWidth / z - this.pop.offsetWidth - 8)), top = r.bottom / z + 4;
      Object.assign(this.pop.style, { left: left + 'px', top: Math.min(top, innerHeight / z - this.pop.offsetHeight - 8) + 'px' });
      this.btn.setAttribute('aria-expanded', 'true');
      this.filter.value = '';
      this.active = 0;
      this.render();
      this.filter.focus();
      document.addEventListener('pointerdown', this.outside, true);
    }
    close(focusBtn) {
      if (this.pop.hidden) return;
      this.pop.hidden = true;
      this.btn.setAttribute('aria-expanded', 'false');
      document.removeEventListener('pointerdown', this.outside, true);
      if (focusBtn) this.btn.focus();
    }
    /** הפריטים שעוברים את הסינון, בקבוצות */
    groups() {
      const q = this.filter.value.trim().toLowerCase(), P = pretty();
      return L.templateGroups().map((g) => ({ title: g.title, items: g.items.filter((t) => !q || [t.name, t.he, t.text, P[t.i].text, g.title].join(' ').toLowerCase().includes(q)) })).filter((g) => g.items.length);
    }
    render() {
      const P = pretty();
      let k = 0;
      this.flat = [];
      const gs = this.groups();
      this.list.innerHTML = gs.map((g) => '<div class="tpl-group" role="presentation">' + esc(g.title) + '</div>' + g.items.map((t) => {
        this.flat.push(t.i);
        return '<div class="tpl-opt" role="option" id="tpl-o' + k + '" data-k="' + (k++) + '" data-i="' + t.i + '" aria-selected="false"><bdi class="tpl-f" dir="ltr">' + P[t.i].html + '</bdi><span class="tpl-he">' + esc(t.he) + '</span></div>';
      }).join('')).join('') || '<div class="tpl-none">אין תבנית כזו</div>';
      this.mark();
    }
    mark() {
      this.list.querySelectorAll('.tpl-opt').forEach((o) => { const on = +o.dataset.k === this.active; o.classList.toggle('on', on); o.setAttribute('aria-selected', String(on)); if (on) { this.filter.setAttribute('aria-activedescendant', o.id); o.scrollIntoView({ block: 'nearest' }); } });
    }
    pick(i) { this.close(); this.onPick(L.TEMPLATES[i].text); }
    onKey(e) {
      if (!this.isOpen()) { if ((e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') && e.target === this.btn) { e.preventDefault(); this.open(); } return; }
      e.stopPropagation();
      if (e.key === 'Escape') { e.preventDefault(); this.close(true); }
      else if (e.key === 'ArrowDown') { e.preventDefault(); this.active = Math.min(this.flat.length - 1, this.active + 1); this.mark(); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); this.active = Math.max(0, this.active - 1); this.mark(); }
      else if (e.key === 'Enter') { e.preventDefault(); if (this.flat.length) this.pick(this.flat[this.active]); }
      else if (e.key === 'Tab') this.close();
    }
  }

  root.TplPicker = TplPicker;
})(typeof globalThis !== 'undefined' ? globalThis : this);
