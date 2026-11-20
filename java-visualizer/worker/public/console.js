// עמדת הניהול של המורה (בדף /account): שני טאבים — "מחשבי המעבדה" ו"תלמידים" — וחלונית ההגשות עם הקוד ובדיקת ההרצה.
// השרת: src/access/classroom.js (GET /v1/me/console וכו'). הכול נבנה עם h() — טקסט תמיד כטקסט (אין הזרקת קוד מנתונים של תלמידים).
import { api, h, icon, ICONS, ago, fmtDate, daysSince, busy, toast } from '/static/common.js';
import { attachPreview, hidePreview } from '/static/preview.js';

const S = { tab: 'st', folder: 'all', open: null, subs: [], cur: null, curData: null, fileIdx: 0, ask: null, renaming: false, adding: false, q: '',
  view: 'students', onlyNew: false };
let data = null, root = null, onRelease = null, onData = null;

/** מציג את עמדת הניהול בתוך container. requestRelease(station) — "בקשה לשחרר" להנהלה (כשנגמרו השחרורים של החודש) */
export async function mountConsole(container, { requestRelease, dataChanged } = {}) {
  root = container;
  onRelease = requestRelease;
  onData = dataChanged;
  await reload();
}

async function reload(keepOpen) {
  data = await api('GET', '/v1/me/console');
  if (onData) onData(data);
  if (!keepOpen) { S.open = null; S.cur = null; S.curData = null; }
  if (S.folder !== 'all' && S.folder !== '' && !data.classes.some(c => c.id === S.folder)) S.folder = 'all';
  render();
}

const pill = (cls, text) => h('span', { class: 'pill ' + cls }, text);
/** התוצאה של בדיקת ההרצה (מהתקציר של התלמיד): צבע וטקסט */
const RESULT = { ok: ['green', 'הפלט תואם'], diff: ['orange', 'הפלט שונה'], err: ['red', 'לא מתקמפל'], time: ['orange', 'נעצר אחרי 10 שניות'], none: ['gray', 'בלי בדיקת הרצה'] };
const resultPill = r => pill((RESULT[r] || RESULT.none)[0], (RESULT[r] || RESULT.none)[1]);
/** עיגול אדום קטן עם מספר ההגשות החדשות (שהמורה עוד לא פתח) */
const newDot = n => (n ? h('span', { class: 'newdot', title: n === 1 ? 'הגשה חדשה אחת' : n + ' הגשות חדשות' }, String(n)) : null);
const newWord = n => (n === 1 ? 'הגשה חדשה' : n + ' הגשות חדשות');
/** ממוצע הציונים שהמורה נתן לתלמיד ({avg, n}), או null כשעוד אין ציונים */
function gradeAvg(s) {
  const g = (s.work || []).filter(w => w.grade != null).map(w => w.grade);
  return g.length ? { avg: Math.round(g.reduce((a, b) => a + b, 0) / g.length), n: g.length } : null;
}
const collator = new Intl.Collator('he', { numeric: true });
function className(id) { const c = data.classes.find(x => x.id === id); return c ? c.name : 'בלי כיתה'; }
function inFolder(f) { return data.students.filter(s => f === 'all' || s.classId === f); }
function seenText(iso) { return daysSince(iso) > 30 ? 'לא נראה ' + daysSince(iso) + ' יום' : 'נראה ' + ago(iso); }

function render() {
  hidePreview();
  const t = data.teacher;
  const newSt = data.stations.reduce((n, s) => n + s.unseen, 0);
  const newSu = data.students.reduce((n, s) => n + (s.unseen || 0), 0);
  const goTab = id => { S.tab = id; S.open = null; S.cur = null; S.curData = null; S.ask = null; S.renaming = false; S.adding = false; render(); };
  // שורה דקה למעלה: רק מה שצריך בזמן העבודה — הקוד (לתת לתלמידים), ההגשות החדשות וההיתרים. הפרטים הכלליים — בטאב "קוד ועמדות" בדף
  const copy = h('button', { type: 'button', class: 'btn sm ghost', id: 'copy-code' }, 'העתקה');
  copy.onclick = async () => { try { await navigator.clipboard.writeText(t.code); toast('הקוד הועתק'); } catch (e) { toast(t.code); } };
  // "הגשות חדשות": לחיצה מובילה ישר למקום שבו הן מחכות (תלמידים — רק אלה עם הגשה חדשה; אחרת המחשבים)
  const fresh = h('button', { type: 'button', class: 'cs-item link', id: 'stat-new', disabled: !(newSt + newSu),
    onclick: () => { if (newSu) { S.view = 'students'; S.folder = 'all'; S.onlyNew = true; goTab('su'); } else goTab('st'); } },
    h('span', { class: 'small muted' }, 'הגשות חדשות'), h('b', { class: newSt + newSu ? 'hot' : null }, String(newSt + newSu)),
    h('span', { class: 'small muted' }, newSt + newSu ? `${newSu} מתלמידים · ${newSt} ממחשבי המעבדה` : 'אין הגשות שמחכות'));
  const head = h('div', { class: 'con-strip' },
    h('span', { class: 'cs-item' }, h('span', { class: 'small muted' }, 'הקוד שלי'), h('b', { class: 'mono con-code' }, t.code), copy),
    fresh,
    h('span', { class: 'cs-item' }, h('span', { class: 'small muted' }, 'היתרים בשימוש'), h('b', null, `${t.used} מתוך ${t.seats}`)));
  const tab = (id, label, n, k) => h('button', { type: 'button', role: 'tab', class: 'con-tab', 'aria-selected': String(S.tab === id), id: 'tab-' + id,
    onclick: () => { S.onlyNew = false; goTab(id); } }, label, h('span', { class: 'n' }, String(n)), newDot(k));
  const tabs = h('div', { class: 'con-tabs', role: 'tablist' }, tab('st', 'מחשבי המעבדה', data.stations.length, newSt), tab('su', 'תלמידים', data.students.length, newSu));
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
      h('td', { title: s.client || '' }, h('b', null, s.name)),
      h('td', null, pill(daysSince(s.lastSeen) > 30 ? 'orange' : 'green', seenText(s.lastSeen))),
      h('td', null, s.pending ? pill('blue', s.pending === 1 ? 'הגשה אחת ממתינה' : `${s.pending} הגשות ממתינות`) : h('span', { class: 'muted' }, 'אין')),
      h('td', null, h('div', { class: 'acts' },
        h('button', { type: 'button', class: 'btn sm', onclick: () => openRow('st', s.id) }, 'הגשות'),
        h('button', { type: 'button', class: 'btn sm', id: 'rename-st-' + s.id, onclick: () => { S.ask = { kind: 'rename', id: s.id }; render(); } }, 'שינוי שם'),
        r.left > 0
          ? h('button', { type: 'button', class: 'btn sm danger', onclick: () => { S.ask = { kind: 'release', id: s.id }; render(); } }, 'שחרור')
          : h('button', { type: 'button', class: 'btn sm', onclick: () => onRelease && onRelease(s) }, 'בקשה לשחרר')))));
    if (S.ask && S.ask.kind === 'rename' && S.ask.id === s.id) {
      const inp = h('input', { class: 'field sm grow', id: 'rename-st', value: s.name, 'aria-label': 'שם חדש למחשב', maxlength: '60' });
      const save = h('button', { type: 'button', class: 'btn sm primary' }, 'שמירה');
      save.onclick = () => busy(save, async () => {
        const name = inp.value.trim();
        if (!name) { inp.focus(); return; }
        await api('POST', '/v1/me/station/rename', { id: s.id, name });
        S.ask = null; await reload(true); toast(`השם נשמר: ״${name}״`);
      });
      inp.onkeydown = e => { if (e.key === 'Enter') save.click(); if (e.key === 'Escape') { S.ask = null; render(); } };
      rows.push(h('tr', null, h('td', { colspan: '4' }, h('div', { class: 'confirm calm' },
        h('span', null, 'שם חדש:'), inp, save, h('button', { type: 'button', class: 'btn sm ghost', onclick: () => { S.ask = null; render(); } }, 'ביטול'),
        h('span', { class: 'small muted', style: { flexBasis: '100%' } }, 'השם יופיע גם במחשב עצמו (בתחתית מאגר השאלות) בפעם הבאה שהמאגר נפתח בו, וגם בהגשות החדשות ממנו.')))));
      setTimeout(() => { const el = root.querySelector('#rename-st'); if (el) { el.focus(); el.select(); } }, 0);
    }
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
      ? h('div', { class: 'tablewrap' }, h('table', { class: 'con-table compact', id: 'st-table' },
        h('thead', null, h('tr', null, ['מחשב', 'מצב', 'הגשות', ''].map(x => h('th', null, x)))), h('tbody', null, rows)))
      : h('div', { class: 'empty' }, 'עוד אין מחשבים מחוברים. בכל מחשב במעבדה: בחלון הכניסה של DSViz בוחרים "עמדת מחשב" ומקלידים את הקוד.'));
}

// ------------------------------------------------------------------ תלמידים

function studentsPane() {
  const sw = h('button', { type: 'button', class: 'sw', 'aria-pressed': String(data.teacher.joinOpen), id: 'join-open' }, h('i'), 'הצטרפות פתוחה');
  sw.onclick = () => busy(sw, async () => { await api('POST', '/v1/me/settings', { joinOpen: !data.teacher.joinOpen }); await reload(true); });
  const seg = (id, label) => h('button', { type: 'button', role: 'tab', 'aria-selected': String(S.view === id), id: 'view-' + id,
    onclick: () => { S.view = id; S.open = null; S.cur = null; S.curData = null; S.ask = null; render(); } }, label);
  return h('div', { class: 'con-pane' },
    h('div', { class: 'con-tools' },
      h('div', { class: 'seg', role: 'tablist', 'aria-label': 'איך להציג' }, seg('students', 'לפי תלמיד'), seg('ex', 'לפי תרגיל')),
      h('span', { class: 'grow' }), sw,
      h('span', { class: 'small muted' }, data.teacher.joinOpen ? `תלמיד שמקליד ${data.teacher.code} אחרי הכניסה עם Google מצטרף ל"בלי כיתה"` : 'הקוד לא מצרף תלמידים חדשים')),
    h('div', { class: 'con-cls' }, folders(), h('div', { class: 'con-main' }, classHead(), S.view === 'ex' ? exercisesTable() : studentsTable())));
}

function folders() {
  const box = h('div', { class: 'folders' });
  const item = (id, label, n) => box.append(h('button', { type: 'button', class: 'folder', 'aria-current': String(S.folder === id),
    onclick: () => { S.folder = id; S.onlyNew = false; S.open = null; S.cur = null; S.curData = null; S.ask = null; S.renaming = false; render(); } },
    h('span', null, label), newDot(inFolder(id).reduce((k, x) => k + (x.unseen || 0), 0)), h('span', { class: 'c' }, String(n))));
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
  const all = inFolder(S.folder);
  const fresh = all.filter(s => s.unseen);
  const list = S.onlyNew && fresh.length ? fresh : all;
  if (S.onlyNew && !fresh.length) S.onlyNew = false;
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
      // מתי הצטרף — ברמז על השם (לא עמודה: הטבלה נשארת צרה); תלמיד שעזב מסומן ליד השם
      h('td', { title: 'הצטרף ב-' + fmtDate(s.joinedAt) }, h('b', null, s.name || s.email), s.left ? [' ', pill('gray', 'עזב ' + ago(s.leftAt))] : null, h('div', { class: 'sub ltr' }, s.email)),
      h('td', null, pick),
      h('td', null, s.lastSubmitAt ? [h('div', null, s.lastTitle), h('div', { class: 'sub' }, ago(s.lastSubmitAt))] : h('span', { class: 'muted' }, 'עוד אין')),
      h('td', { class: 'num' }, String(s.submits), s.unseen ? [' ', pill('blue', s.unseen === 1 ? 'חדשה' : s.unseen + ' חדשות')] : null),
      h('td', { class: 'num avg' }, gradeAvg(s) ? [h('b', null, String(gradeAvg(s).avg)), h('div', { class: 'sub' }, gradeAvg(s).n === 1 ? 'ציון אחד' : gradeAvg(s).n + ' ציונים')] : h('span', { class: 'muted' }, '—')),
      h('td', null, h('div', { class: 'acts' },
        h('button', { type: 'button', class: 'btn sm' + (s.unseen ? ' primary' : ''), onclick: () => openRow('su', s.email) }, 'תרגילים'),
        h('button', { type: 'button', class: 'btn sm danger', onclick: () => { S.ask = { kind: 'student', id: s.email }; render(); } }, 'מחיקה')))));
    if (S.ask && S.ask.kind === 'student' && S.ask.id === s.email) {
      const go = h('button', { type: 'button', class: 'btn sm danger' }, 'מחיקה לצמיתות');
      go.onclick = () => busy(go, async () => {
        let r;
        do r = await api('POST', '/v1/me/member', { op: 'delete', student: s.email }); while (!r.done);
        S.ask = null; if (S.open && S.open.id === s.email) { S.open = null; S.cur = null; S.curData = null; } await reload(true); toast(`${s.name || s.email} נמחק מהרשימה`);
      });
      rows.push(confirmRow(6, [h('span', { class: 'grow' }, `למחוק את ${s.name || s.email}? הוא יוצא מהרשימה שלך, ו`,
        h('b', null, s.submits === 1 ? 'ההגשה שלו נמחקת' : `כל ${s.submits} ההגשות שלו נמחקות`), gradeAvg(s) ? ' עם הציונים שנתת' : '', ' לצמיתות. אי אפשר לשחזר. (אם ירצה, יוכל להצטרף שוב עם הקוד — מההתחלה.)'),
        go, h('button', { type: 'button', class: 'btn sm ghost', onclick: () => { S.ask = null; render(); } }, 'ביטול')]));
    }
  }
  const table = h('div', { class: 'tablewrap' }, h('table', { class: 'con-table' },
    h('thead', null, h('tr', null, ['תלמיד', 'כיתה', 'ההגשה האחרונה', 'הגשות', 'ממוצע ציונים', ''].map(x => h('th', null, x)))), h('tbody', null, rows)));
  if (!fresh.length) return table;
  // סינון: רק מי שיש לו הגשה שעוד לא נפתחה
  const only = h('button', { type: 'button', class: 'sw', 'aria-pressed': String(S.onlyNew), id: 'only-new', onclick: () => { S.onlyNew = !S.onlyNew; render(); } },
    h('i'), `רק תלמידים עם הגשה חדשה (${fresh.length})`);
  return h('div', { class: 'stack', style: { gap: '8px' } }, h('div', { class: 'con-tools' }, only), table);
}

// ------------------------------------------------------------------ לפי תרגיל: מי הגיש, ומה יצא לו

/** התרגילים שהוגשו בתיקייה (כיתה / כולם / בלי כיתה), מהאחרון שהוגש. לכל תרגיל: מי הגיש (לפי המייל) ומה התוצאה */
function exercisesOf(list) {
  const by = new Map();
  for (const s of list) {
    for (const w of s.work || []) {
      const key = w.bankId || 'כותרת:' + w.title;
      let e = by.get(key);
      if (!e) by.set(key, e = { key, title: w.title, topic: w.topic, level: w.level, last: '', rows: new Map() });
      e.rows.set(s.email, w);
      if (String(w.at) > e.last) e.last = String(w.at);
    }
  }
  return Array.from(by.values()).sort((a, b) => b.last.localeCompare(a.last));
}

/**
 * "לפי תרגיל" — טבלת הגשות (כמו יומן ציונים): שורה לכל תלמיד, עמודה לכל תרגיל, ובכל תא סימן קטן של התוצאה.
 * רואים במבט אחד מי הגיש מה ומה יצא; לחיצה על תא פותחת את הקוד של אותו תלמיד למטה. הרבה תרגילים — גוללים לצד, והשמות נשארים.
 */
const MARK = { ok: '✓', diff: '≠', err: '✕', time: '!', none: '•' };

function exercisesTable() {
  const list = inFolder(S.folder);
  const active = list.filter(s => !s.left);
  const exs = exercisesOf(list);
  if (!exs.length) {
    return h('div', { class: 'tablewrap' }, h('div', { class: 'empty' }, list.length
      ? 'עוד לא הוגש אף תרגיל. תלמיד מגיש בכפתור "הגשה למורה" בחלון השאלה (או ב"סיימתי את התרגיל").'
      : 'אין כאן תלמידים עדיין.'));
  }
  // תלמיד שעזב מופיע רק אם הגיש משהו
  const who = list.filter(s => !s.left || (s.work || []).length);
  const head = h('tr', null, h('th', { class: 'gname', scope: 'col' }, 'תלמיד'), h('th', { class: 'gsum', scope: 'col', title: 'כמה מהתרגילים שבטבלה התלמיד הגיש' }, 'הגיש'),
    h('th', { class: 'gsum', scope: 'col', title: 'ממוצע הציונים שנתת לתלמיד' }, 'ממוצע'),
    exs.map(e => {
      const got = active.filter(s => e.rows.has(s.email)).length;
      const fresh = Array.from(e.rows.values()).filter(w => !w.seen).length;
      const bankId = e.key.startsWith('כותרת:') ? '' : e.key;
      // תרגיל מהמאגר: מעבר עם העכבר על השם — השאלה כמו שהתלמיד רואה אותה (preview.js), ולכן בלי title של הדפדפן
      return h('th', { class: 'gcol', scope: 'col', title: bankId ? null : [e.title, e.topic, e.level].filter(Boolean).join(' · ') + ` — הגישו ${got} מתוך ${active.length}` },
        attachPreview(h('span', { class: 'gt' }, e.title), { source: 'bank', bankId }, 'java'), h('span', { class: 'gc' }, `${got}/${active.length}`, newDot(fresh)));
    }));
  const rows = who.map(s => {
    const mine = exs.filter(e => e.rows.has(s.email)).length;
    return h('tr', null,
      h('th', { class: 'gname', scope: 'row' }, h('span', { class: 'gn' }, s.name || s.email), s.left ? pill('gray', 'עזב') : null),
      h('td', { class: 'gsum' }, `${mine}/${exs.length}`),
      h('td', { class: 'gsum gavg' }, gradeAvg(s) ? String(gradeAvg(s).avg) : '—'),
      exs.map(e => {
        const w = e.rows.get(s.email);
        if (!w) return h('td', { class: 'gcell' }, h('span', { class: 'gno', title: 'לא הוגש' }, '–'));
        const graded = w.grade != null;
        const text = `${s.name || s.email} · ${e.title} · ${(RESULT[w.result] || RESULT.none)[1]}` + (graded ? ` · ציון ${w.grade}` : '') + ` · ${ago(w.at)}`
          + (w.n > 1 ? ` · הוגש ${w.n} פעמים` : '') + (w.seen ? '' : ' · חדש');
        // תא עם ציון מציג את הציון (הצבע נשאר של בדיקת ההרצה); בלי ציון — הסימן של בדיקת ההרצה
        return h('td', { class: 'gcell' }, h('button', { type: 'button', class: 'g ' + (RESULT[w.result] ? w.result : 'none') + (graded ? ' graded' : '') + (w.seen ? '' : ' new'),
          'aria-current': String(S.cur === w.id), title: text, 'aria-label': text, onclick: () => openRow('su', s.email, w.id) }, graded ? String(w.grade) : MARK[w.result] || MARK.none));
      }));
  });
  const key = (r, label) => h('span', { class: 'gkey' }, h('span', { class: 'g ' + r, 'aria-hidden': 'true' }, MARK[r]), label);
  const legend = h('div', { class: 'glegend' }, key('ok', 'הפלט תואם'), key('diff', 'הפלט שונה'), key('err', 'לא מתקמפל'), key('time', 'נעצר'), key('none', 'בלי בדיקה'),
    h('span', { class: 'gkey' }, h('span', { class: 'gno', 'aria-hidden': 'true' }, '–'), 'לא הוגש'), h('span', { class: 'gkey' }, h('span', { class: 'g ok new', 'aria-hidden': 'true' }, MARK.ok), 'חדש'),
    h('span', { class: 'gkey' }, h('span', { class: 'g ok graded', 'aria-hidden': 'true' }, '90'), 'הציון שנתת'),
    h('span', { class: 'gkey' }, '· לחיצה על סימן פותחת את הקוד'));
  return h('div', { class: 'stack', style: { gap: '8px' } }, legend,
    h('div', { class: 'tablewrap gwrap' }, h('table', { class: 'gridt' }, h('thead', null, head), h('tbody', null, rows))));
}

function confirmRow(cols, kids) {
  return h('tr', null, h('td', { colspan: String(cols) }, h('div', { class: 'confirm' }, kids)));
}

// ------------------------------------------------------------------ חלונית ההגשות

/** פותח את חלונית ההגשות של עמדה / תלמיד. pick — ההגשה שתיפתח (אחרת: הראשונה שעוד לא נפתחה, ואם אין — האחרונה) */
async function openRow(kind, id, pick) {
  S.open = { kind, id }; S.cur = null; S.curData = null; S.subs = []; S.ask = null;
  render();
  try {
    const r = await api('GET', '/v1/me/submissions?' + (kind === 'st' ? 'station=' : 'student=') + encodeURIComponent(id));
    S.subs = r.submissions;
    const first = S.subs.find(x => x.id === pick) || S.subs.find(x => !x.seen) || S.subs[0];
    if (first) await openSub(first.id); else render();
    const d = root.querySelector('.detail');
    if (d && d.scrollIntoView) d.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  } catch (e) { toast(e.message, true); }
}

async function openSub(id) {
  S.cur = id; S.fileIdx = 0;
  try {
    S.curData = await api('GET', '/v1/me/submission?id=' + encodeURIComponent(id));
    const row = S.subs.find(x => x.id === id);
    if (row && !row.seen) {   // נפתחה עכשיו: יורדת מ"הגשות חדשות" (בעמדה ואצל התלמיד) בלי לטעון את הכול מחדש
      row.seen = true;
      const st = data.stations.find(x => S.open && S.open.kind === 'st' && x.id === S.open.id);
      if (st && st.unseen) st.unseen--;
      const su = data.students.find(x => S.open && S.open.kind === 'su' && x.email === S.open.id);
      const w = su && (su.work || []).find(x => x.id === id);
      if (w && !w.seen) { w.seen = true; su.unseen = Math.max(0, (su.unseen || 0) - 1); }
    }
    const m = S.curData.files.findIndex(f => f.path === S.curData.main);
    S.fileIdx = m >= 0 ? m : 0;
  } catch (e) { toast(e.message, true); S.curData = null; }
  render();
}

function exTitle(x) { return (x.exercise && x.exercise.title) || 'תרגיל'; }
/** כמו resultOf בשרת: התוצאה של בדיקת ההרצה במילה אחת */
function checkResult(c) {
  if (!c || !c.ran) return 'none';
  if (c.compiles === false) return 'err';
  if (c.timedOut) return 'time';
  return c.outputMatch === true ? 'ok' : c.outputMatch === false ? 'diff' : 'none';
}

function detailPane() {
  if (!S.open) return null;
  const st = S.open.kind === 'st';
  const who = st ? (data.stations.find(x => x.id === S.open.id) || {}).name : (data.students.find(x => x.email === S.open.id) || {}).name || S.open.id;
  const list = h('div', { class: 'd-list' }, h('div', { class: 'd-head' },
    h('b', null, (st ? 'הגשות מ' : 'התרגילים של ') + (who || '')),
    h('span', { class: 'small muted' }, S.subs.length ? (S.subs.length === 1 ? 'הגשה אחת' : `${S.subs.length} הגשות`) + (st ? ' ממתינות' : '') : (st ? 'אין הגשות שממתינות' : 'עוד לא הוגש כלום'))));
  for (const x of S.subs) {
    list.append(h('button', { type: 'button', class: 'd-item', 'aria-current': String(x.id === S.cur), onclick: () => openSub(x.id) },
      // מעמדת מחשב: השם שהתלמיד כתב בהגשה — בשורה משלו, ראשון (בעמדה אחת עובדים כמה תלמידים)
      st ? h('span', { class: 'who' }, h('span', { class: 'lbl' }, 'מגיש:'), h('b', null, x.who || '—')) : null,
      attachPreview(h('span', { class: 't' }, exTitle(x)), x.exercise, x.lang),
      h('span', { class: 'm' }, ago(x.createdAt), x.n > 1 ? ` · הוגש ${x.n} פעמים` : '', x.seen ? null : pill('blue', 'חדש')),
      h('span', { class: 'm' }, resultPill(checkResult(x.check)), x.grade != null ? pill('purple', 'ציון ' + x.grade) : null)));
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
    const top = h('div', { class: 'd-top' }, attachPreview(h('b', null, exTitle(x)), x.exercise, x.lang),
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
    if (!st) box.append(gradeBar(x));
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
    ? 'הגשות מעמדה נשמרות רק לאותו יום: צופים בהן ומוחקים. מה שלא נמחק נמחק לבד בחצות.'
    : 'העבודות של תלמיד נשמרות אצלך עד שמוחקים אותו או את הכיתה שלו.'));
  return box;
}

/**
 * ציון להגשה של תלמיד (לא מעמדת מחשב): שדה קטן מעל הקוד. המורה נותן ציון 0–100, מעדכן בכל זמן, או מוחק (שדה ריק).
 * הציון נרשם אצל המורה: ברשימת התרגילים של התלמיד, בטבלת ההגשות ובממוצע שלו
 */
function gradeBar(x) {
  const has = x.grade != null;
  const inp = h('input', { class: 'field sm grade-in', type: 'text', inputmode: 'numeric', maxlength: '3', id: 'grade-in', autocomplete: 'off',
    placeholder: '0–100', 'aria-label': 'ציון, מספר בין 0 ל-100', value: has ? String(x.grade) : '' });
  const save = h('button', { type: 'button', class: 'btn sm primary', id: 'grade-save' }, has ? 'עדכון הציון' : 'שמירת הציון');
  const go = () => busy(save, async () => {
    const raw = inp.value.trim();
    if (raw !== '' && (!/^\d{1,3}$/.test(raw) || +raw > 100)) { toast('הציון הוא מספר שלם בין 0 ל-100', true); inp.focus(); inp.select(); return; }
    if (raw === '' && !has) { inp.focus(); return; }
    const r = await api('POST', '/v1/me/submission/grade', { id: x.id, grade: raw === '' ? null : +raw });
    const set = o => { if (o) { o.grade = r.grade; o.gradedAt = r.gradedAt; o.gradedN = r.gradedN; } };
    if (S.curData && S.curData.submission.id === x.id) set(S.curData.submission);
    set(S.subs.find(y => y.id === x.id));
    for (const s of data.students) set((s.work || []).find(w => w.id === x.id));
    render();
    toast(r.grade === null ? 'הציון הוסר' : `הציון ${r.grade} נשמר`);
  });
  save.onclick = go;
  inp.onkeydown = e => { if (e.key === 'Enter') { e.preventDefault(); go(); } };
  return h('div', { class: 'd-grade' }, h('label', { for: 'grade-in' }, h('b', null, 'ציון')), inp, save,
    h('span', { class: 'small muted', id: 'grade-info' }, has ? `ניתן ${ago(x.gradedAt)} · אפשר לשנות בכל זמן` : 'מספר בין 0 ל-100. הציון נרשם אצלך, ואפשר לשנות אותו בכל זמן'),
    has && x.n > x.gradedN ? pill('orange', 'התלמיד הגיש שוב מאז הציון') : null);
}

/** בדיקת ההרצה שהתוסף עשה לפני ההגשה: הפלט של התלמיד מול הפלט הרצוי (מהמאגר), שורות שונות מסומנות */
function outView(c) {
  const box = h('div', { class: 'out' });
  if (!c || !c.ran) { box.append(h('span', { class: 'small muted' }, c && c.needsInput ? 'בדיקת ההרצה: התרגיל מחכה לקלט מהמקלדת, ולכן לא הורץ.' : 'בלי בדיקת הרצה.')); return box; }
  if (c.compiles === false) {
    const n = c.errors || 0, shown = c.error ? String(c.error).split('\n').length : 0;
    box.append(h('b', null, 'בדיקת ההרצה: הקוד לא התקמפל, ולכן לא רץ' + (n > 1 ? ` (${n} שגיאות)` : n === 1 ? ' (שגיאה אחת)' : '')),
      h('pre', { class: 'outpre' }, h('span', { class: 'ln bad' }, c.error || '')),
      n > shown && shown ? h('span', { class: 'small muted' }, `מוצגות ${shown} הראשונות.`) : null);
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
