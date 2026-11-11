/*
 * dialogs.js - חלון אישור (למחיקה) וחלון קיצורי המקלדת.
 * Dialogs.confirm({ title, html, ok, cancel, danger, single }) מחזיר Promise<boolean> (single: בלי כפתור ביטול). Enter = אישור, Esc = ביטול.
 */
(function (root) {
  'use strict';
  const I = root.Icons;
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const kbd = (k) => '<bdi class="kbd-iso"><kbd dir="ltr">' + esc(k) + '</kbd></bdi>';

  let current = null;

  function confirm(o) {
    if (current) current.finish(false);
    return new Promise((resolve) => {
      const back = document.createElement('div');
      back.className = 'dialog-backdrop';
      back.innerHTML = '<div class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="dlg-t" aria-describedby="dlg-b">' +
        '<div class="dialog-icon' + (o.danger ? ' danger' : '') + '">' + I.get(o.danger ? 'trash' : 'info', 20) + '</div>' +
        '<div class="dialog-text"><h2 id="dlg-t">' + o.title + '</h2><div id="dlg-b">' + (o.html || '') + '</div></div>' +
        '<div class="dialog-foot"><button type="button" class="btn ' + (o.danger ? 'danger' : 'primary') + '" data-r="1">' + esc(o.ok || 'אישור') + '</button>' +
        (o.single ? '' : '<button type="button" class="btn secondary" data-r="0">' + esc(o.cancel || 'ביטול') + '</button>') + '</div></div>';
      const prev = document.activeElement;
      const finish = (v) => {
        if (!current || current.back !== back) return;
        current = null;
        document.removeEventListener('keydown', onKey, true);
        back.classList.add('out');
        setTimeout(() => back.remove(), 140);
        if (prev && prev.focus) prev.focus();
        resolve(v);
      };
      const onKey = (e) => {
        if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); finish(false); }
        else if (e.key === 'Enter') { e.preventDefault(); e.stopPropagation(); finish(document.activeElement && document.activeElement.dataset.r === '0' ? false : true); }
        else if (e.key === 'Tab') {
          const bs = back.querySelectorAll('button');
          const i = Array.prototype.indexOf.call(bs, document.activeElement);
          e.preventDefault();
          bs[(i + (e.shiftKey ? bs.length - 1 : 1)) % bs.length].focus();
        } else e.stopPropagation();
      };
      back.addEventListener('click', (e) => {
        const b = e.target.closest('[data-r]');
        if (b) finish(b.dataset.r === '1');
        else if (e.target === back) finish(false);
      });
      document.addEventListener('keydown', onKey, true);
      document.body.appendChild(back);
      current = { back, finish };
      back.querySelector('[data-r="1"]').focus();
    });
  }

  function isOpen() { return !!current; }

  /** תוכן חלון קיצורי המקלדת. */
  function helpHTML() {
    const rows = [
      ['בונים', [
        ['לחיצה כפולה על הקנבס', 'מצב חדש'],
        ['לחיצה על מצב, ואז על מצב אחר', 'מעבר חדש'],
        ['לחיצה על מצב, ואז שוב עליו', 'לולאה'],
        ['לחיצה על קשת או על התווית שלה', 'עריכת האותיות'],
        ['ריחוף, ואז הפח האדום', 'מחיקה'],
        ['גרירת מצב', 'הזזה (נצמד לרשת; Alt: בלי)'],
      ]],
      ['מקשים בזמן עריכה', [
        ['A', 'מצב מקבל'], ['I', 'מצב התחלתי'], ['F2', 'שינוי שם'], ['Delete', 'מחיקה (עם אישור)'],
        ['Ctrl+Z', 'ביטול'], ['Ctrl+Shift+Z', 'חזרה'], ['Ctrl+A', 'בחירת כל המצבים'], ['Esc', 'ביטול מעבר שהתחיל / ביטול בחירה'],
      ]],
      ['תצוגה', [
        ['גרירת הרקע', 'הזזת התצוגה'], ['Shift + גרירה', 'בחירה במלבן'], ['גלגלת', 'זום'],
        ['F', 'התאמה למסך'], ['L', 'סידור אוטומטי'], ['Alt+1', 'אס"ד (עד Alt+4: טיורינג)'],
      ]],
      ['הרצה וקבצים', [
        ['Enter', 'בשדה המילה: הרצה'], ['P', 'הרצה / עצירה'], ['→', 'צעד קדימה'], ['←', 'צעד אחורה'],
        ['Ctrl+S', 'שמירה לקובץ'], ['Ctrl+O', 'פתיחת קובץ'],
      ]],
    ];
    const key = (k) => (/^[A-Za-z0-9+←→ ]+$/.test(k) ? kbd(k) : '<span>' + esc(k) + '</span>');
    return '<div class="help-grid">' + rows.map(([t, list]) => '<section><h3>' + t + '</h3><dl>' +
      list.map(([k, v]) => '<div><dt>' + key(k) + '</dt><dd>' + esc(v) + '</dd></div>').join('') + '</dl></section>').join('') + '</div>' +
      '<p class="muted small">הכול נשמר אוטומטית בדפדפן הזה. כדי להעביר למחשב אחר: שמירה לקובץ.</p>';
  }

  root.Dialogs = { confirm, isOpen, helpHTML, kbd };
})(typeof globalThis !== 'undefined' ? globalThis : this);
