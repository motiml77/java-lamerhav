// חלון קטן (בלון) עם השאלה כמו שהתלמיד רואה אותה בחלון "השאלה" בתוכנה: הנוסח, הסעיפים והציורים של המבנים.
// הנוסח לא נשלח מהתלמיד — הוא נלקח מהעותק של המאגר שבשרת (/static/bank-preview.json, נוצר מ-bank/ ב-tools/gen-assets.mjs).
// הכול נבנה עם h() — טקסט תמיד כטקסט.
import { h } from '/static/common.js';

let bank = null, loading = null, pop = null, timer = null, owner = null;
const LEVEL = { easy: 'קל', medium: 'בינוני', hard: 'קשה' };

function load() {
  if (bank) return Promise.resolve(bank);
  if (!loading) loading = fetch('/static/bank-preview.json').then(r => (r.ok ? r.json() : {})).then(j => (bank = j)).catch(() => (bank = {}));
  return loading;
}

/** כל השאלות במאגר (מזהה → {title, topic, level, java, cs}) — גם לבחירת שאלות לשיעורי בית; נטען פעם אחת */
export function bankData() {
  return load();
}

const cell = v => h('span', { class: 'pv-cell' }, v === null || v === undefined ? '' : String(v));

/** מבנה בתוך מבנה (inner): כל ערך הוא רשימה של ערכים — מצויר קטן בתוך התא */
function inner(kind, vals) {
  if (!Array.isArray(vals)) return cell(vals);
  if (kind === 'stack') return h('span', { class: 'pv-in pv-in-stack' }, vals.map(cell));
  return h('span', { class: 'pv-in pv-in-row' }, vals.map(cell));
}

function figure(f, parents) {
  const v = Array.isArray(f.values) ? f.values : [];
  const item = x => (f.inner ? inner(f.inner, x) : cell(x));
  let body;
  switch (f.type) {
    case 'stack':   // מהראש למטה — הראש למעלה
      body = h('div', { class: 'pv-stack' }, h('span', { class: 'pv-tag' }, 'ראש'), v.map(item));
      break;
    case 'queue':   // מהראש (יוצאים) לזנב — הראש משמאל
      body = h('div', { class: 'pv-row pv-queue', dir: 'ltr' }, h('span', { class: 'pv-tag' }, 'ראש'), v.map(item), h('span', { class: 'pv-tag' }, 'זנב'));
      break;
    case 'list': {
      const kids = [];
      v.forEach((x, i) => { if (i) kids.push(h('span', { class: 'pv-arrow' }, '→')); kids.push(item(x)); });
      kids.push(h('span', { class: 'pv-arrow' }, '→'), h('span', { class: 'pv-null' }, 'null'));
      body = h('div', { class: 'pv-row', dir: 'ltr' }, kids);
      break;
    }
    case 'array':
      body = h('div', { class: 'pv-array', dir: 'ltr' }, v.map((x, i) => h('span', { class: 'pv-ai' }, item(x), h('small', null, String(i)))));
      break;
    case 'matrix':
      body = h('table', { class: 'pv-matrix', dir: 'ltr' }, h('tbody', null, v.map(r => h('tr', null, (Array.isArray(r) ? r : [r]).map(x => h('td', null, String(x)))))));
      break;
    case 'tree': {   // לפי רמות, null — אין צומת; הילדים של i: 2i+1, 2i+2 (כמו NestedPreview)
      const node = i => (i < v.length && v[i] !== null && v[i] !== undefined
        ? h('div', { class: 'pv-tn' }, h('span', { class: 'pv-tv' }, String(v[i])),
          (2 * i + 1 < v.length && (v[2 * i + 1] != null || v[2 * i + 2] != null)) ? h('div', { class: 'pv-tk' }, node(2 * i + 1), node(2 * i + 2)) : null)
        : h('div', { class: 'pv-tn pv-empty' }));
      body = h('div', { class: 'pv-tree', dir: 'ltr' }, node(0));
      break;
    }
    case 'classes':
      body = h('div', { class: 'pv-classes', dir: 'ltr' }, v.map(n => h('span', { class: 'pv-cls' }, h('b', null, String(n)),
        parents && parents[n] ? h('small', null, 'extends ' + parents[n]) : null)));
      break;
    case 'text':
      body = h('pre', { class: 'pv-text', dir: 'ltr' }, v.join('\n'));
      break;
    default:
      body = h('div', { class: 'pv-row', dir: 'ltr' }, v.map(item));
  }
  return h('figure', { class: 'pv-fig' }, body, f.caption ? h('figcaption', null, f.caption) : null);
}

function content(id, lang) {
  const e = bank && bank[id];
  if (!e) return h('div', { class: 'pv-none' }, 'התרגיל לא מהמאגר (תרגיל של התלמיד), אז אין כאן נוסח.');
  const v = (lang === 'csharp' && e.cs) ? e.cs : e.java;
  const q = v.q || {};
  const figs = list => (list && list.length ? h('div', { class: 'pv-figs' }, list.map(f => figure(f, v.parents))) : null);
  return h('div', { class: 'pv-q' },
    h('div', { class: 'pv-head' }, h('b', null, e.title), h('span', { class: 'pv-meta' }, [LEVEL[e.level] || '', lang === 'csharp' ? 'C#' : 'Java'].filter(Boolean).join(' · '))),
    q.intro ? h('p', { class: 'pv-intro' }, q.intro) : null,
    figs(q.figures),
    (q.sections || []).map(s => h('section', { class: 'pv-sec' },
      h('div', { class: 'pv-label' }, 'סעיף ' + s.label + "'"),
      h('p', null, s.text || ''),
      figs(s.figures))));
}

function place(el) {
  const r = el.getBoundingClientRect();
  const W = window.innerWidth, H = window.innerHeight, pw = Math.min(520, W - 24);
  pop.style.width = pw + 'px';
  let left = Math.min(Math.max(12, r.right - pw), W - pw - 12);
  pop.style.left = left + 'px';
  const below = H - r.bottom, ph = pop.offsetHeight;
  const top = (below >= Math.min(ph, 260) + 16 || below > r.top) ? r.bottom + 8 : Math.max(12, r.top - 8 - ph);
  pop.style.top = top + 'px';
  pop.style.maxHeight = (top > r.bottom ? H - top - 12 : r.top - 20) + 'px';
}

export function hidePreview() {
  clearTimeout(timer);
  if (pop) pop.remove();
  pop = null; owner = null;
}

async function show(el, id, lang, passive) {
  await load();
  if (owner !== el) return;
  if (pop) pop.remove();
  pop = h('div', { class: 'pv-pop' + (passive ? ' passive' : ''), role: 'tooltip', id: 'ex-preview' }, content(id, lang));
  if (!passive) {
    pop.addEventListener('mouseenter', () => clearTimeout(timer));
    pop.addEventListener('mouseleave', () => { timer = setTimeout(hidePreview, 200); });
  }
  document.body.append(pop);
  place(el);
}

/**
 * מחבר את הבלון לאלמנט: מעבר עם העכבר, פוקוס במקלדת, או לחיצה (בטלפון). passive — הצצה בלבד (ברשימה שבוחרים ממנה כמה שורות
 * ברצף, כמו בחירת השאלות לשיעורי בית): הבלון לא תופס את העכבר ולא מסתיר לחיצות על השורות שמתחתיו, ונעלם כשיוצאים מהשורה
 */
export function attachPreview(el, exercise, lang, passive) {
  const id = exercise && exercise.source === 'bank' ? exercise.bankId : '';
  if (!id) return el;
  el.classList.add('pv-anchor');
  el.setAttribute('aria-describedby', 'ex-preview');
  if (!el.hasAttribute('tabindex') && !/^(BUTTON|A)$/.test(el.tagName) && !el.closest('button')) el.tabIndex = 0;
  const open = delay => { clearTimeout(timer); owner = el; timer = setTimeout(() => show(el, id, lang, passive), delay); };
  el.addEventListener('mouseenter', () => open(passive ? 450 : 250));
  el.addEventListener('mouseleave', () => { clearTimeout(timer); timer = setTimeout(() => { if (owner === el) hidePreview(); }, 200); });
  el.addEventListener('focus', () => open(0));
  el.addEventListener('blur', () => { if (owner === el) hidePreview(); });
  el.addEventListener('keydown', e => { if (e.key === 'Escape') hidePreview(); });
  return el;
}
document.addEventListener('scroll', e => { if (pop && pop.contains(e.target)) return; hidePreview(); }, { passive: true, capture: true });
