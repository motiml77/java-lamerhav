/*
 * lang-strip.js - הפס שמעל הקנבס: השפה שהמכונה אמורה לקבל, בכתיבה יפה, עם תיאור של שורה אחת ומילים להרצה בלחיצה.
 * אותו רכיב משמש את הדוגמאות המוכנות, את השפה שהמשתמש כתב בלשונית "שפה", ואת קובץ התרגיל של מורה.
 * אפשר לקפל אותו לשורה דקה (ההעדפה נשמרת), כדי שלא ייקח מקום מהקנבס.
 *
 * המילים: המילים שבטבלת הבדיקות של המכונה (בדוגמה: המילים של הדוגמה). אם הטבלה ריקה, המילים הקצרות שבשפה ושמחוצה לה.
 * הצבע של כל מילה (בשפה / לא בשפה) מחושב מהנוסחה, לא מהמכונה.
 */
(function (root) {
  'use strict';
  const L = root.AutomataLang, M = root.AutomataModel, I = root.Icons;
  const KEY = 'dsviz.automata.strip';
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* אין אחסון: ההעדפה לא נשמרת */ } },
  };

  class LangStrip {
    constructor(app, el) {
      this.app = app;
      this.el = el;
      this.collapsed = store.get(KEY) === '1';
      this.sig = null;
      el.addEventListener('click', (e) => this.onClick(e));
    }

    /** המילים להרצה מהירה: [{ word, inLang }], עד 9. */
    words(ast) {
      const m = this.app.machine;
      let list = Array.from(new Set(m.tests.map((t) => t.word)));
      if (!list.length) { const s = L.sample(ast, { nIn: 4, nOut: 3 }); list = s.ins.concat(s.outs); }
      return list.slice(0, 9).map((word) => ({ word, inLang: L.accepts(ast, word) }));
    }

    render() {
      const app = this.app, m = app.machine, lang = m.language, el = this.el;
      let ast = null;
      if (lang && lang.text && lang.text.trim()) { try { ast = L.parse(lang.text, { alphabet: M.inputAlphabet(m) }); } catch (e) { ast = null; } }
      const words = ast ? this.words(ast) : [];
      const sig = ast ? JSON.stringify([lang.text, lang.desc, lang.locked, this.collapsed, words]) : '';
      const same = this.machine === m;
      this.machine = m;
      if (sig === this.sig) return;
      const first = this.sig === null, before = el.hidden ? 0 : el.offsetHeight;
      this.sig = sig;
      el.hidden = !ast;
      el.classList.toggle('collapsed', this.collapsed);
      if (ast) {
        const p = L.pretty(ast);
        const chips = words.map((w) => '<button type="button" class="w-chip ' + (w.inLang ? 'in' : 'out') + '" data-word="' + esc(w.word) + '" title="' + (w.inLang ? 'בשפה' : 'לא בשפה') + '. לחיצה מריצה את המילה">' + (w.word === '' ? 'ε' : esc(w.word)) + '</button>').join('');
        el.innerHTML = '<div class="ls-main"><button type="button" class="ls-formula" data-s="open" title="לחיצה פותחת את לשונית השפה">' +
          '<span class="ls-tag">' + (lang.locked ? 'תרגיל' : 'השפה') + '</span><bdi class="ls-f" dir="ltr" id="ls-formula">' + p.html + '</bdi></button>' +
          '<button type="button" class="btn icon ghost sm ls-toggle" data-s="toggle" aria-expanded="' + !this.collapsed + '" aria-controls="ls-sub" title="' + (this.collapsed ? 'הצגת התיאור והמילים' : 'קיפול הפס') + '" aria-label="' + (this.collapsed ? 'הצגת התיאור והמילים' : 'קיפול הפס') + '">' + I.get('chevronDown', 16) + '</button></div>' +
          '<div class="ls-sub" id="ls-sub">' + (lang.desc ? '<p class="ls-desc">' + esc(lang.desc) + '</p>' : '') +
          '<div class="ls-words"><span class="ls-wlabel">מילים להרצה</span><span class="ls-chips" dir="ltr">' + chips + '</span></div></div>';
      } else el.innerHTML = '';
      // הפס שינה גובה באותה מכונה (קיפול, או שנכתבה שפה): הקנבס קטן או גדל, ומזיזים את התצוגה כדי שהשרטוט יישאר במרכזו
      const after = el.hidden ? 0 : el.offsetHeight;
      if (!first && same && after !== before && app.canvas) { m.view.y -= (after - before) / 2; app.canvas.applyView(); }
    }

    onClick(e) {
      const app = this.app;
      const w = e.target.closest('[data-word]');
      if (w) { app.runWord(w.dataset.word); return; }
      const b = e.target.closest('[data-s]');
      if (!b) return;
      if (b.dataset.s === 'open') app.side.show('lang');
      else if (b.dataset.s === 'toggle') {
        this.collapsed = !this.collapsed;
        store.set(KEY, this.collapsed ? '1' : '0');
        this.render();
        const t = this.el.querySelector('.ls-toggle');
        if (t) t.focus();
      }
    }
  }

  root.LangStrip = LangStrip;
})(window);
