// עמדת הניהול של המורה (בדף /account): שני טאבים — "מחשבי המעבדה" ו"תלמידים" — וחלונית ההגשות עם הקוד ובדיקת ההרצה.
// השרת: src/access/classroom.js (GET /v1/me/console וכו'). הכול נבנה עם h() — טקסט תמיד כטקסט (אין הזרקת קוד מנתונים של תלמידים).
import { api, h, icon, ICONS, ago, fmtDate, daysSince, busy, toast } from '/static/common.js';

const S = { tab: 'st', folder: 'all', open: null, subs: [], cur: null, curData: null, fileIdx: 0, ask: null, renaming: false, adding: false, q: '' };
let data = null, root = null, onRelease = null;

/** מציג את עמדת הניהול בתוך container. requestRelease(station) — "בקשה לשחרר" להנהלה (כשנגמרו השחרורים של החודש) */
export async function mountConsole(container, { requestRelease } = {}) {
  root = container;
  onRelease = requestRelease;
  await reload();
}

async function reload(keepOpen) {
  data = await api('GET', '/v1/me/console');
  if (!keepOpen) { S.open = null; S.cur = null; S.curData = null; }
  if (S.folder !== 'all' && S.folder !== '' && !data.classes.some(c => c.id === S.folder)) S.folder = 'all';
  render();
}

const pill = (cls, text) => h('span', { class: 'pill ' + cls }, text);
const collator = new Intl.Collator('he', { numeric: true });
function className(id) { const c = data.classes.find(x => x.id === id); return c ? c.name : 'בלי כיתה'; }
function inFolder(f) { return data.students.filter(s => f === 'all' || s.classId === f); }
function seenText(iso) { return daysSince(iso) > 30 ? 'לא נראה ' + daysSince(iso) + ' יום' : 'נראה ' + ago(iso); }

function render() {
  const t = data.teacher;
  const unseen = data.stations.reduce((n, s) => n + s.unseen, 0);
  const head = h('div', { class: 'con-head' },
    h('div', { class: 'con-stat' }, h('span', { class: 'small muted' }, 'הקוד שלי (קבוע)'), h('b', { class: 'mono con-code' }, t.code)),
    h('div', { class: 'con-stat' }, h('span', { class: 'small muted' }, 'היתרים בשימוש'), h('b', null, `${t.used} מתוך ${t.seats}`),
      h('span', { class: 'bar' }, h('span', { style: { width: (t.seats ? Math.min(100, Math.round(100 * t.used / t.seats)) : 100) + '%' } }))),
    h('div', { class: 'con-stat' }, h('span', { class: 'small muted' }, 'הגשות מעמדות שלא נפתחו'), h('b', null, String(unseen))),
    h('div', { class: 'con-stat' }, h('span', { class: 'small muted' }, 'תלמידים'), h('b', null, String(data.students.filter(s => !s.left).length))));
  const tab = (id, label, n) => h('button', { type: 'button', role: 'tab', class: 'con-tab', 'aria-selected': String(S.tab === id), id: 'tab-' + id,
    onclick: () => { S.tab = id; S.open = null; S.cur = null; S.curData = null; S.ask = null; S.renaming = false; S.adding = false; render(); } }, label, h('span', { class: 'n' }, String(n)));
  const tabs = h('div', { class: 'con-tabs', role: 'tablist' }, tab('st', 'מחשבי המעבדה', data.stations.length), tab('su', 'תלמידים', data.students.length));
  const pane = S.tab === 'st' ? stationsPane() : studentsPane();
  root.replaceChildren(h('section', { class: 'card con' }, head, tabs, pane, detailPane()));
}

// ------------------------------------------------------------------ מחשבי המעבדה

function stationsPane() {
  const r = data.releases;
  const q = S.q.trim();
  const list = data.stations.filter(s => !q || s.name.includes(q));
  const rows = [];
  for (const s of list) {
    const sel = S.open && S.open.kind === 'st' && S.open.id === s.id;
    rows.push(h('tr', { class: sel ? 'sel' : null },
      h('td', null, h('b', null, s.name), h('div', { class: 'sub' }, s.client || '')),
      h('td', null, pill(daysSince(s.lastSeen) > 30 ? 'orange' : 'green', seenText(s.lastSeen))),
      h('td', null, s.pending ? pill('blue', s.pending === 1 ? 'הגשה אחת ממתינה' : `${s.pending} הגשות ממתינות`) : h('span', { class: 'muted' }, 'אין')),
      h('td', null, h('div', { class: 'acts' },
        h('button', { type: 'button', class: 'btn sm', onclick: () => openRow('st', s.id) }, 'הגשות'),
        r.left > 0
          ? h('button', { type: 'button', class: 'btn sm danger', onclick: () => { S.ask = { kind: 'release', id: s.id }; render(); } }, 'שחרור')
          : h('button', { type: 'button', class: 'btn sm', onclick: () => onRelease && onRelease(s) }, 'בקשה לשחרר')))));
    if (S.ask && S.ask.kind === 'release' && S.ask.id === s.id) {
      const go = h('button', { type: 'button', class: 'btn sm danger' }, 'שחרור');
      go.onclick = () => busy(go, async () => {
        await api('POST', '/v1/me/station/release', { id: s.id });
        S.ask = null; await reload(); toast(`״${s.name}״ שוחררה — ההיתר חזר אליך`);
      });
      rows.push(confirmRow(4, [h('span', { class: 'grow' }, `לשחרר את ״${s.name}״? ההיתר חוזר אליך מיד, והמחשב ננעל בבדיקה הבאה שלו מול השרת. ההגשות שממתינות ממנו נמחקות. נשארו ${r.left} שחרורים החודש.`),
        go, h('button', { type: 'button', class: 'btn sm ghost', onclick: () => { S.ask = null; render(); } }, 'ביטול')]));
    }
  }
  const search = h('input', { class: 'field grow', type: 'search', id: 'q-st', placeholder: 'חיפוש מחשב', 'aria-label': 'חיפוש מחשב', value: S.q });
  search.oninput = () => { S.q = search.value; const p = stationsPane(); root.querySelector('.con-pane').replaceWith(p); const s2 = p.querySelector('#q-st'); s2.focus(); s2.setSelectionRange(s2.value.length, s2.value.length); };
  return h('div', { class: 'con-pane' },
    h('div', { class: 'con-tools' }, search,
      h('span', { class: 'small muted' }, `מחשב חדש מתחבר בחלון "עמדת מחשב" עם הקוד ${data.teacher.code}`),
      h('span', { class: 'small muted' }, `שחרורים החודש: ${r.used} מתוך ${r.limit}`)),
    data.stations.length
      ? h('div', { class: 'tablewrap' }, h('table', { class: 'con-table' },
        h('thead', null, h('tr', null, ['מחשב', 'מצב', 'הגשות', ''].map(x => h('th', null, x)))), h('tbody', null, rows)))
      : h('div', { class: 'empty' }, 'עוד אין מחשבים מחוברים. בכל מחשב במעבדה: בחלון הכניסה של DSViz בוחרים "עמדת מחשב" ומקלידים את הקוד.'));
}

// ------------------------------------------------------------------ תלמידים

function studentsPane() {
  const sw = h('button', { type: 'button', class: 'sw', 'aria-pressed': String(data.teacher.joinOpen), id: 'join-open' }, h('i'), 'הצטרפות פתוחה');
  sw.onclick = () => busy(sw, async () => { await api('POST', '/v1/me/settings', { joinOpen: !data.teacher.joinOpen }); await reload(true); });
  return h('div', { class: 'con-pane' },
    h('div', { class: 'con-tools' }, sw,
      h('span', { class: 'small muted' }, data.teacher.joinOpen ? `תלמיד שמקליד ${data.teacher.code} אחרי הכניסה עם Google מצטרף ל"בלי כיתה"` : 'הקוד לא מצרף תלמידים חדשים')),
    h('div', { class: 'con-cls' }, folders(), h('div', { class: 'con-main' }, classHead(), studentsTable())));
}

function folders() {
  const box = h('div', { class: 'folders' });
  const item = (id, label, n) => box.append(h('button', { type: 'button', class: 'folder', 'aria-current': String(S.folder === id),
    onclick: () => { S.folder = id; S.open = null; S.cur = null; S.curData = null; S.ask = null; S.renaming = false; render(); } }, h('span', null, label), h('span', { class: 'c' }, String(n))));
  item('all', 'כל התלמידים', data.students.length);
  for (const c of data.classes) item(c.id, c.name, inFolder(c.id).length);
  item('', 'בלי כיתה', inFolder('').length);
  if (S.adding) {
    const inp = h('input', { class: 'field sm', id: 'new-class', placeholder: 'שם הכיתה, למשל יא 4', 'aria-label': 'שם הכיתה החדשה', maxlength: '40' });
    const go = h('button', { type: 'button', class: 'btn sm primary' }, 'יצירה');
    const create = () => busy(go, async () => {
      const name = inp.value.trim();
      if (!name) { inp.focus(); return; }
      const r = await api('POST', '/v1/me/class', { op: 'create', name });
      S.adding = false; S.folder = r.id; await reload();
    });
    go.onclick = create;
    inp.onkeydown = e => { if (e.key === 'Enter') create(); if (e.key === 'Escape') { S.adding = false; render(); } };
    box.append(h('div', { class: 'newcls' }, inp, go));
    setTimeout(() => inp.focus(), 0);
  } else box.append(h('button', { type: 'button', class: 'folder add', onclick: () => { S.adding = true; render(); } }, '+ כיתה חדשה'));
  return box;
}

function classHead() {
  const isClass = S.folder !== 'all' && S.folder !== '';
  const title = S.folder === 'all' ? 'כל התלמידים' : className(S.folder);
  const list = inFolder(S.folder);
  const subs = list.reduce((n, s) => n + s.submits, 0);
  const out = [];
  if (isClass && S.renaming) {
    const inp = h('input', { class: 'field sm', id: 'rename-class', value: title, 'aria-label': 'שם חדש לכיתה', maxlength: '40' });
    const save = h('button', { type: 'button', class: 'btn sm primary' }, 'שמירה');
    save.onclick = () => busy(save, async () => {
      const name = inp.value.trim();
      if (!name) return;
      await api('POST', '/v1/me/class', { op: 'rename', id: S.folder, name });
      S.renaming = false; await reload(true);
    });
    inp.onkeydown = e => { if (e.key === 'Enter') save.click(); if (e.key === 'Escape') { S.renaming = false; render(); } };
    out.push(h('div', { class: 'con-tools' }, inp, save, h('button', { type: 'button', class: 'btn sm ghost', onclick: () => { S.renaming = false; render(); } }, 'ביטול')));
    setTimeout(() => inp.select(), 0);
  } else {
    out.push(h('div', { class: 'clshead' },
      h('div', null, h('h3', null, title), h('span', { class: 'small muted' }, `${list.length} תלמידים · ${subs} הגשות`)),
      isClass ? h('div', { class: 'acts' },
        h('button', { type: 'button', class: 'btn sm', onclick: () => { S.renaming = true; S.ask = null; render(); } }, 'שינוי שם'),
        h('button', { type: 'button', class: 'btn sm danger', onclick: () => { S.ask = { kind: 'class' }; render(); } }, 'מחיקת הכיתה')) : null));
  }
  if (isClass && S.ask && S.ask.kind === 'class') {
    const typed = h('input', { class: 'field sm', id: 'confirm-class', placeholder: title, 'aria-label': 'הקלידו את שם הכיתה לאישור' });
    const go = h('button', { type: 'button', class: 'btn sm danger', disabled: true }, 'מחיקה לצמיתות');
    const progress = h('span', { class: 'small' });
    typed.oninput = () => { go.disabled = typed.value.trim() !== title; };
    go.onclick = () => busy(go, async () => {
      let r;
      do {
        r = await api('POST', '/v1/me/class', { op: 'delete', id: S.folder, confirm: title });
        if (!r.done) progress.textContent = `מוחק… נשארו ${r.left} תלמידים`;
      } while (!r.done);
      S.ask = null; S.folder = 'all'; await reload(); toast(`הכיתה ״${title}״ נמחקה`);
    });
    out.push(h('div', { class: 'confirm' },
      h('span', { class: 'grow' }, `למחוק את הכיתה ״${title}״? יימחקו `, h('b', null, `${list.length} תלמידים`), ' וכל ', h('b', null, `${subs} ההגשות`), ' שלהם, לצמיתות. אי אפשר לשחזר. כדי לאשר, הקלידו את שם הכיתה:'),
      typed, go, h('button', { type: 'button', class: 'btn sm ghost', onclick: () => { S.ask = null; render(); } }, 'ביטול'), progress));
  }
  return h('div', { class: 'stack' }, out);
}

function studentsTable() {
  const list = inFolder(S.folder);
  if (!list.length) {
    return h('div', { class: 'tablewrap' }, h('div', { class: 'empty' }, S.folder === '' ? 'כל התלמידים משובצים בכיתות.'
      : S.folder === 'all' ? `עוד אין תלמידים. תלמיד נכנס עם Google בחלון הכניסה של DSViz, בוחר "יש לי קוד מורה" ומקליד ${data.teacher.code}.`
        : 'אין עדיין תלמידים בכיתה הזו. מעבירים תלמיד לכאן מהעמודה "כיתה".'));
  }
  const rows = [];
  for (const s of list) {
    const sel = S.open && S.open.kind === 'su' && S.open.id === s.email;
    const pick = h('select', { class: 'field sm', 'aria-label': 'הכיתה של ' + (s.name || s.email) },
      data.classes.map(c => h('option', { value: c.id, selected: c.id === s.classId }, c.name)), h('option', { value: '', selected: !s.classId }, 'בלי כיתה'));
    pick.onchange = async () => {
      try { await api('POST', '/v1/me/member', { op: 'move', student: s.email, classId: pick.value }); s.classId = pick.value; render(); } catch (e) { toast(e.message, true); }
    };
    rows.push(h('tr', { class: sel ? 'sel' : null },
      h('td', null, h('b', null, s.name || s.email), h('div', { class: 'sub ltr' }, s.email)),
      h('td', null, pick),
      h('td', null, s.left ? pill('gray', 'עזב ' + ago(s.leftAt)) : h('span', null, 'הצטרף ' + fmtDate(s.joinedAt))),
      h('td', null, s.lastSubmitAt ? [h('div', null, s.lastTitle), h('div', { class: 'sub' }, ago(s.lastSubmitAt))] : h('span', { class: 'muted' }, 'עוד אין')),
      h('td', { class: 'num' }, String(s.submits)),
      h('td', null, h('div', { class: 'acts' },
        h('button', { type: 'button', class: 'btn sm', onclick: () => openRow('su', s.email) }, 'תרגילים'),
        h('button', { type: 'button', class: 'btn sm danger', onclick: () => { S.ask = { kind: 'student', id: s.email }; render(); } }, 'מחיקה')))));
    if (S.ask && S.ask.kind === 'student' && S.ask.id === s.email) {
      const go = h('button', { type: 'button', class: 'btn sm danger' }, 'מחיקה לצמיתות');
      go.onclick = () => busy(go, async () => {
        let r;
        do r = await api('POST', '/v1/me/member', { op: 'delete', student: s.email }); while (!r.done);
        S.ask = null; if (S.open && S.open.id === s.email) { S.open = null; S.cur = null; S.curData = null; } await reload(true); toast(`${s.name || s.email} נמחק מהרשימה`);
      });
      rows.push(confirmRow(6, [h('span', { class: 'grow' }, `למחוק את ${s.name || s.email}? הוא יוצא מהרשימה שלך, ו`,
        h('b', null, s.submits === 1 ? 'ההגשה שלו נמחקת' : `כל ${s.submits} ההגשות שלו נמחקות`), ' לצמיתות. אי אפשר לשחזר. (אם ירצה, יוכל להצטרף שוב עם הקוד — מההתחלה.)'),
        go, h('button', { type: 'button', class: 'btn sm ghost', onclick: () => { S.ask = null; render(); } }, 'ביטול')]));
    }
  }
  return h('div', { class: 'tablewrap' }, h('table', { class: 'con-table' },
    h('thead', null, h('tr', null, ['תלמיד', 'כיתה', 'פעילות', 'ההגשה האחרונה', 'הגשות', ''].map(x => h('th', null, x)))), h('tbody', null, rows)));
}

function confirmRow(cols, kids) {
  return h('tr', null, h('td', { colspan: String(cols) }, h('div', { class: 'confirm' }, kids)));
}

// ------------------------------------------------------------------ חלונית ההגשות

async function openRow(kind, id) {
  S.open = { kind, id }; S.cur = null; S.curData = null; S.subs = []; S.ask = null;
  render();
  try {
    const r = await api('GET', '/v1/me/submissions?' + (kind === 'st' ? 'station=' : 'student=') + encodeURIComponent(id));
    S.subs = r.submissions;
    if (S.subs.length) await openSub(S.subs[0].id); else render();
  } catch (e) { toast(e.message, true); }
}

async function openSub(id) {
  S.cur = id; S.fileIdx = 0;
  try {
    S.curData = await api('GET', '/v1/me/submission?id=' + encodeURIComponent(id));
    const row = S.subs.find(x => x.id === id);
    if (row && !row.seen) { row.seen = true; const st = data.stations.find(x => S.open && x.id === S.open.id); if (st && st.unseen) st.unseen--; }
    const m = S.curData.files.findIndex(f => f.path === S.curData.main);
    S.fileIdx = m >= 0 ? m : 0;
  } catch (e) { toast(e.message, true); S.curData = null; }
  render();
}

function exTitle(x) { return (x.exercise && x.exercise.title) || 'תרגיל'; }

function detailPane() {
  if (!S.open) return null;
  const st = S.open.kind === 'st';
  const who = st ? (data.stations.find(x => x.id === S.open.id) || {}).name : (data.students.find(x => x.email === S.open.id) || {}).name || S.open.id;
  const list = h('div', { class: 'd-list' }, h('div', { class: 'd-head' },
    h('b', null, (st ? 'הגשות מ' : 'התרגילים של ') + (who || '')),
    h('span', { class: 'small muted' }, S.subs.length ? (S.subs.length === 1 ? 'הגשה אחת' : `${S.subs.length} הגשות`) + (st ? ' ממתינות' : '') : (st ? 'אין הגשות שממתינות' : 'עוד לא הוגש כלום'))));
  for (const x of S.subs) {
    list.append(h('button', { type: 'button', class: 'd-item', 'aria-current': String(x.id === S.cur), onclick: () => openSub(x.id) },
      h('span', { class: 't' }, exTitle(x)),
      h('span', { class: 'm' }, ago(x.createdAt), st ? ' · ' + x.who : '', x.n > 1 ? ` · הוגש ${x.n} פעמים` : '', x.seen ? null : pill('blue', 'חדש'))));
  }
  if (st && S.subs.length > 1) {
    const all = h('button', { type: 'button', class: 'btn sm danger' }, 'מחיקת כל ההגשות');
    all.onclick = () => busy(all, async () => {
      await api('POST', '/v1/me/submission/delete', { station: S.open.id, all: true });
      const keep = S.open; await reload(true); await openRow(keep.kind, keep.id); toast('כל ההגשות של העמדה נמחקו');
    });
    list.append(h('div', { class: 'd-head', style: { borderTop: '1px solid var(--line)', borderBottom: '0' } }, all));
  }
  return h('div', { class: 'detail' }, list, codePane(st));
}

function codePane(st) {
  const box = h('div', { class: 'd-code' });
  const d = S.curData;
  if (!d) {
    box.append(h('div', { class: 'empty' }, S.subs.length ? 'רגע…' : (st ? 'אין הגשות שממתינות מהמחשב הזה.' : 'כשתהיה הגשה, הקוד יופיע כאן.')));
  } else {
    const x = d.submission, c = x.check || {};
    const top = h('div', { class: 'd-top' }, h('b', null, exTitle(x)),
      x.exercise.topic ? pill('gray', x.exercise.topic) : null, x.exercise.level ? pill('gray', x.exercise.level) : null,
      c.compiles === true ? pill('green', 'מתקמפל') : c.compiles === false ? pill('red', 'לא מתקמפל') : null,
      c.outputMatch === true ? pill('green', 'הפלט תואם') : c.outputMatch === false ? pill('orange', 'הפלט שונה') : null,
      c.timedOut ? pill('orange', 'ההרצה נעצרה אחרי 10 שניות') : null,
      st ? h('span', { class: 'small muted' }, 'מגיש: ' + x.who) : null,
      h('span', { class: 'small muted' }, x.app || ''));
    if (st) {
      const del = h('button', { type: 'button', class: 'btn sm danger', style: { marginInlineStart: 'auto' } }, 'מחיקה');
      del.onclick = () => busy(del, async () => {
        await api('POST', '/v1/me/submission/delete', { id: x.id });
        const keep = S.open; await reload(true); await openRow(keep.kind, keep.id); toast('ההגשה נמחקה');
      });
      top.append(del);
    }
    box.append(top);
    if (d.files.length > 1) {
      box.append(h('div', { class: 'files' }, d.files.map((f, i) => h('button', { type: 'button', class: 'file', 'aria-current': String(i === S.fileIdx),
        onclick: () => { S.fileIdx = i; render(); } }, f.path.split('/').pop()))));
    }
    const f = d.files[S.fileIdx] || { path: '', text: '' };
    box.append(h('div', { class: 'small muted ltr', style: { padding: '6px 14px 0' } }, f.path));
    box.append(h('pre', { class: 'code' }, f.text));
    box.append(outView(c));
  }
  box.append(h('div', { class: 'keep' }, st
    ? `הגשות מעמדה לא נשמרות לאורך זמן: צופים ומוחקים. מה שלא נמחק נמחק לבד אחרי ${data.stationSubmitDays} ימים.`
    : 'העבודות של תלמיד נשמרות אצלך עד שמוחקים אותו או את הכיתה שלו.'));
  return box;
}

/** בדיקת ההרצה שהתוסף עשה לפני ההגשה: הפלט של התלמיד מול הפלט הרצוי (מהמאגר), שורות שונות מסומנות */
function outView(c) {
  const box = h('div', { class: 'out' });
  if (!c || !c.ran) { box.append(h('span', { class: 'small muted' }, c && c.needsInput ? 'בדיקת ההרצה: התרגיל מחכה לקלט מהמקלדת, ולכן לא הורץ.' : 'בלי בדיקת הרצה.')); return box; }
  if (c.compiles === false) {
    box.append(h('b', null, 'בדיקת ההרצה: הקוד לא התקמפל, ולכן לא רץ'), h('pre', { class: 'outpre' }, h('span', { class: 'ln bad' }, c.error || '')));
    return box;
  }
  const got = String(c.output || '').replace(/\s+$/, '').split('\n'), exp = c.expected ? String(c.expected).replace(/\s+$/, '').split('\n') : null;
  const norm = s => (s == null ? null : String(s).replace(/\s+$/, ''));
  const col = (title, lines, other, cls) => {
    const pre = h('pre', { class: 'outpre' });
    const n = Math.max(lines.length, other ? other.length : 0);
    for (let i = 0; i < n; i++) {
      const ln = lines[i];
      const diff = other && norm(ln) !== norm(other[i]);
      pre.append(h('span', { class: diff ? 'ln ' + cls : 'ln' }, ln == null ? '(אין שורה)' : (ln || ' ')));
    }
    return h('div', { class: 'outcol' }, h('b', null, title), pre);
  };
  box.append(h('b', null, exp ? (c.outputMatch ? 'בדיקת ההרצה: הפלט תואם לפלט הרצוי' : 'בדיקת ההרצה: הפלט שונה מהפלט הרצוי') : 'בדיקת ההרצה: הקוד רץ'),
    h('div', { class: 'outs' }, col('הפלט של התלמיד', got, exp, 'bad'),
      exp ? col('הפלט הרצוי (מהמאגר)', exp, got, 'want') : h('div', { class: 'outcol small muted' }, 'אין פלט רצוי: זה לא תרגיל מהמאגר.')));
  return box;
}
