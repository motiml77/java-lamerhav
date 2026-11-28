// עמדת הניהול של המורה (בדף /account): שלושה טאבים — "מחשבי המעבדה", "תלמידים" ו"שיעורי בית" — וחלונית ההגשות עם הקוד ובדיקת ההרצה.
// השרת: src/access/classroom.js (GET /v1/me/console וכו'). הכול נבנה עם h() — טקסט תמיד כטקסט (אין הזרקת קוד מנתונים של תלמידים).
import { api, h, icon, ICONS, ago, fmtDate, daysSince, busy, toast } from '/static/common.js';
import { attachPreview, hidePreview, bankData } from '/static/preview.js';

const S = { tab: 'st', folder: 'all', open: null, subs: [], cur: null, curData: null, fileIdx: 0, ask: null, renaming: false, adding: false, q: '',
  view: 'students', onlyNew: false,
  // שיעורי בית: התצוגה (לפי מטלה / סיכום תלמידים), מה פתוח, הטיוטה שבעריכה ({id, classId, items, title, due, topic}), אישור מחיקה
  hwView: 'list', hwOpen: null, hwEdit: null, hwAsk: null };
let data = null, root = null, onRelease = null, onData = null;

/** מציג את עמדת הניהול בתוך container. requestRelease(station) — "בקשה לשחרר" להנהלה (כשנגמרו השחרורים של החודש) */
export async function mountConsole(container, { requestRelease, dataChanged } = {}) {
  root = container;
  onRelease = requestRelease;
  onData = dataChanged;
  // המאגר (שמות השאלות, נושאים) — לשיעורי בית; ברקע, בלי לעכב את עמדת הניהול
  loadBank().then(() => { if (root && S.tab === 'hw') render(); });
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
  const newHw = hwUnseen(data.students);
  const goTab = id => { S.tab = id; S.open = null; S.cur = null; S.curData = null; S.ask = null; S.renaming = false; S.adding = false; S.hwAsk = null; render(); };
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
  const tabs = h('div', { class: 'con-tabs', role: 'tablist' }, tab('st', 'מחשבי המעבדה', data.stations.length, newSt), tab('su', 'תלמידים', data.students.length, newSu),
    tab('hw', 'שיעורי בית', hwList().length, newHw));
  const pane = S.tab === 'st' ? stationsPane() : S.tab === 'su' ? studentsPane() : homeworkPane();
  // בשיעורי בית החלונית של הקוד נפתחת מתחת לטבלה של אותם שיעורי בית (בתוך הרשימה); אחרת — בסוף, כמו תמיד
  root.replaceChildren(h('section', { class: 'card con' }, head, tabs, pane, pane.querySelector('.detail') ? null : detailPane()));
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
      hwCell(s),
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
      rows.push(confirmRow(7, [h('span', { class: 'grow' }, `למחוק את ${s.name || s.email}? הוא יוצא מהרשימה שלך, ו`,
        h('b', null, s.submits === 1 ? 'ההגשה שלו נמחקת' : `כל ${s.submits} ההגשות שלו נמחקות`), gradeAvg(s) ? ' עם הציונים שנתת' : '', ' לצמיתות. אי אפשר לשחזר. (אם ירצה, יוכל להצטרף שוב עם הקוד — מההתחלה.)'),
        go, h('button', { type: 'button', class: 'btn sm ghost', onclick: () => { S.ask = null; render(); } }, 'ביטול')]));
    }
  }
  const table = h('div', { class: 'tablewrap' }, h('table', { class: 'con-table' },
    h('thead', null, h('tr', null, ['תלמיד', 'כיתה', 'ההגשה האחרונה'].map(x => h('th', null, x)), ['הגשות', 'ממוצע ציונים'].map(x => h('th', { class: 'num' }, x)),
      h('th', { class: 'hwcol', title: 'שיעורי בית: כמה שאלות הגיש מתוך מה שקיבל' }, 'ש״ב'), h('th', null, ''))), h('tbody', null, rows)));
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
      exs.map(e => gridCell(s, e.title, e.rows.get(s.email), false)));
  });
  return h('div', { class: 'stack', style: { gap: '8px' } }, gridLegend(false),
    h('div', { class: 'tablewrap gwrap' }, h('table', { class: 'gridt' }, h('thead', null, head), h('tbody', null, rows))));
}

/**
 * תא בטבלת ההגשות: תא עם ציון מציג את הציון (הצבע נשאר של בדיקת ההרצה); בלי ציון — הסימן של בדיקת ההרצה; "–" — לא הוגש.
 * late — הוגש אחרי המועד של שיעורי הבית (סימון קטן בפינה). לחיצה פותחת את הקוד של התלמיד למטה
 */
function gridCell(s, title, w, late) {
  if (!w) return h('td', { class: 'gcell' }, h('span', { class: 'gno', title: 'לא הוגש' }, '–'));
  const graded = w.grade != null;
  const text = `${s.name || s.email} · ${title} · ${(RESULT[w.result] || RESULT.none)[1]}` + (graded ? ` · ציון ${w.grade}` : '') + ` · ${ago(w.at)}`
    + (late ? ' · באיחור' : '') + (w.n > 1 ? ` · הוגש ${w.n} פעמים` : '') + (w.seen ? '' : ' · חדש');
  return h('td', { class: 'gcell' }, h('button', { type: 'button', class: 'g ' + (RESULT[w.result] ? w.result : 'none') + (graded ? ' graded' : '') + (w.seen ? '' : ' new') + (late ? ' late' : ''),
    'aria-current': String(S.cur === w.id), title: text, 'aria-label': text, onclick: () => openRow('su', s.email, w.id) }, graded ? String(w.grade) : MARK[w.result] || MARK.none));
}

/** המקרא של טבלת ההגשות (late — גם "באיחור", לשיעורי בית עם מועד) */
function gridLegend(late) {
  const key = (r, label) => h('span', { class: 'gkey' }, h('span', { class: 'g ' + r, 'aria-hidden': 'true' }, MARK[r]), label);
  return h('div', { class: 'glegend' }, key('ok', 'הפלט תואם'), key('diff', 'הפלט שונה'), key('err', 'לא מתקמפל'), key('time', 'נעצר'), key('none', 'בלי בדיקה'),
    h('span', { class: 'gkey' }, h('span', { class: 'gno', 'aria-hidden': 'true' }, '–'), 'לא הוגש'), h('span', { class: 'gkey' }, h('span', { class: 'g ok new', 'aria-hidden': 'true' }, MARK.ok), 'חדש'),
    h('span', { class: 'gkey' }, h('span', { class: 'g ok graded', 'aria-hidden': 'true' }, '90'), 'הציון שנתת'),
    late ? h('span', { class: 'gkey' }, h('span', { class: 'g ok late', 'aria-hidden': 'true' }, MARK.ok), 'באיחור') : null,
    h('span', { class: 'gkey' }, '· לחיצה על סימן פותחת את הקוד'));
}

function confirmRow(cols, kids) {
  return h('tr', null, h('td', { colspan: String(cols) }, h('div', { class: 'confirm' }, kids)));
}

// ------------------------------------------------------------------ שיעורי בית
// המורה בוחר שאלות מהמאגר לכל התלמידים או לכיתה. מה שהוגש לא נשמר בשיעורי הבית: מחברים את השאלות לתקציר של כל תלמיד (work, לפי
// bankId) — שאלה שהתלמיד הגיש עוד קודם נחשבת כהוגשה; "באיחור" — אחרי סוף יום ההגשה, שעון ישראל. אותו מראה כמו "לפי תרגיל".

let bank = null, topics = [], bankLoading = null;
const LEVELS = { easy: ['קל', 'green', 0], medium: ['בינוני', 'orange', 1], hard: ['קשה', 'red', 2] };
const HW_MAX_ITEMS = 10;

/** המאגר (bank-preview.json — כבר בשימוש לבלון השאלה) והנושאים, פעם אחת */
function loadBank() {
  if (!bankLoading) {
    bankLoading = Promise.all([bankData(), fetch('/static/bank-topics.json').then(r => (r.ok ? r.json() : {})).catch(() => ({}))])
      .then(([b, t]) => { bank = b || {}; topics = (t && t.topics) || []; });
  }
  return bankLoading;
}
/** זיכרון קטן בדפדפן הזה (הכיתה והנושא האחרונים) — בלי זיכרון זה פשוט לא נזכר */
function remember(k, v) {
  try { if (v === undefined) return localStorage.getItem('dsviz.hw.' + k); localStorage.setItem('dsviz.hw.' + k, v); } catch (e) { /* פרטי / חסום */ }
  return null;
}
const hwList = () => (data && Array.isArray(data.homework) ? data.homework : []);   // שרת ישן — בלי שיעורי בית
const ISRAEL = { timeZone: 'Asia/Jerusalem', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' };
function israelParts(ms) {
  return Object.fromEntries(new Intl.DateTimeFormat('en-GB', ISRAEL).formatToParts(new Date(ms)).map(x => [x.type, x.value]));
}
/** "28.11" — היום והחודש (שעון ישראל) של רגע ISO */
function dayMonth(iso) {
  const t = Date.parse(iso);
  if (isNaN(t)) return '';
  const p = israelParts(t);
  return `${+p.day}.${+p.month}`;
}
const hwTitle = hw => hw.title || 'שיעורי בית ' + dayMonth(hw.createdAt);
const dueText = due => { const [, m, d] = String(due).split('-'); return `${+d}.${+m}`; };
/** סוף יום ההגשה: חצות שאחרי due, שעון ישראל (במילישניות) */
function dueEnd(due) {
  const [y, m, d] = String(due).split('-').map(Number);
  const guess = Date.UTC(y, m - 1, d + 1);
  const p = israelParts(guess);
  return guess - (Date.UTC(+p.year, +p.month - 1, +p.day, +p.hour, +p.minute, +p.second) - guess);
}
const pastDue = hw => !!hw.due && Date.now() > dueEnd(hw.due);
const isLate = (hw, w) => !!hw.due && !!w && Date.parse(w.at) > dueEnd(hw.due);
/** שם השאלה: מהמאגר; אם עוד לא נטען — כמו שהגיע עם הגשה של תלמיד */
function qTitle(id) {
  if (bank && bank[id]) return bank[id].title;
  for (const s of data.students) { const w = (s.work || []).find(x => x.bankId === id); if (w) return w.title; }
  return id;
}
const levelPill = lv => (LEVELS[lv] ? pill(LEVELS[lv][1], LEVELS[lv][0]) : null);
/** ההגשה האחרונה של התלמיד לכל שאלה מהמאגר (work כבר ממוין מהחדש לישן) */
const byBankCache = new WeakMap();
function byBank(s) {
  let m = byBankCache.get(s);
  if (!m) {
    m = new Map();
    for (const w of s.work || []) if (w.bankId && !m.has(w.bankId)) m.set(w.bankId, w);
    byBankCache.set(s, m);
  }
  return m;
}
/** שיעורי הבית שניתנו לתלמיד: לכל התלמידים, או לכיתה שלו */
const hwOf = s => hwList().filter(hw => !hw.classId || hw.classId === s.classId);
/** שיעורי הבית שמוצגים בתיקייה: בכיתה — שלה ושל כולם; "בלי כיתה" — רק של כולם */
function hwInFolder(f = S.folder) {
  return hwList().filter(hw => f === 'all' || (f === '' ? !hw.classId : !hw.classId || hw.classId === f));
}
/** התלמידים (פעילים, בתיקייה) ששיעורי הבית ניתנו להם */
function hwStudents(hw, f = S.folder) {
  return inFolder(f).filter(s => !s.left && (!hw.classId || s.classId === hw.classId));
}
/** כמה הגישו הכול / חלק, כמה הגשות מחכות לציון, וכמה עוד לא נפתחו */
function hwStats(hw) {
  const list = hwStudents(hw);
  let full = 0, part = 0, waiting = 0, unseen = 0;
  for (const s of list) {
    const m = byBank(s);
    let k = 0;
    for (const id of hw.items) {
      const w = m.get(id);
      if (!w) continue;
      k++;
      if (w.grade == null) waiting++;
      if (!w.seen) unseen++;
    }
    if (k === hw.items.length) full++; else if (k) part++;
  }
  return { n: list.length, full, part, waiting, unseen };
}
/** הגשות של שאלות משיעורי הבית שהמורה עוד לא פתח (כל הגשה פעם אחת) */
function hwUnseen(list) {
  let n = 0;
  for (const s of list) {
    if (s.left) continue;
    const ids = new Set();
    for (const hw of hwOf(s)) for (const id of hw.items) ids.add(id);
    if (ids.size) for (const w of s.work || []) if (!w.seen && ids.has(w.bankId)) n++;
  }
  return n;
}
/** לתלמיד, על כל שיעורי הבית שלו: כמה שאלות קיבל, כמה הגיש, כמה באיחור, וממוצע הציונים */
function hwTotals(s) {
  let total = 0, done = 0, late = 0;
  const grades = [];
  for (const hw of hwOf(s)) {
    for (const id of hw.items) {
      total++;
      const w = byBank(s).get(id);
      if (!w) continue;
      done++;
      if (isLate(hw, w)) late++;
      if (w.grade != null) grades.push(w.grade);
    }
  }
  return { total, done, late, avg: grades.length ? Math.round(grades.reduce((a, b) => a + b, 0) / grades.length) : null };
}
/** העמודה "שיעורי בית" בטבלת התלמידים: "7/9" */
function hwCell(s) {
  const t = hwTotals(s);
  return h('td', { class: 'num hwcol', title: t.total ? `הגיש ${t.done} מתוך ${t.total} השאלות בשיעורי הבית` + (t.late ? ` (${t.late} באיחור)` : '') : null },
    t.total ? h('span', { class: t.done === t.total ? 'hw-all' : null }, `${t.done}/${t.total}`) : h('span', { class: 'muted' }, '—'));
}
function hwEmpty() {
  return data.students.some(s => !s.left)
    ? 'עוד אין שיעורי בית. לוחצים על "+ שיעורי בית חדשים" ובוחרים שאלות מהמאגר — התלמידים רואים אותן בחלון מאגר התרגילים, עם כפתור שמוביל אליהן.'
    : `עוד אין תלמידים שמחוברים אליך. שיעורי בית ניתנים לתלמידים שהצטרפו בקוד ${data.teacher.code} (לא לעמדות המחשב), והם רואים אותם בחלון מאגר התרגילים.`;
}

function homeworkPane() {
  const seg = (id, label) => h('button', { type: 'button', role: 'tab', 'aria-selected': String(S.hwView === id), id: 'hw-view-' + id,
    onclick: () => { S.hwView = id; S.open = null; S.cur = null; S.curData = null; S.hwAsk = null; render(); } }, label);
  const composing = S.hwEdit && !S.hwEdit.id;
  // הסינון לפי כיתה — אותה בחירה כמו בטאב "תלמידים" (S.folder), כאן כרשימה נפתחת: כל הרוחב נשאר לטבלאות
  const pick = h('select', { class: 'field sm', id: 'hw-folder', 'aria-label': 'כיתה' },
    h('option', { value: 'all', selected: S.folder === 'all' }, `כל התלמידים (${data.students.length})`),
    data.classes.map(c => h('option', { value: c.id, selected: S.folder === c.id }, `${c.name} (${inFolder(c.id).length})`)),
    h('option', { value: '', selected: S.folder === '' }, `בלי כיתה (${inFolder('').length})`));
  pick.onchange = () => { S.folder = pick.value; S.open = null; S.cur = null; S.curData = null; S.hwAsk = null; render(); };
  return h('div', { class: 'con-pane' },
    h('div', { class: 'con-tools' },
      h('div', { class: 'seg', role: 'tablist', 'aria-label': 'איך להציג' }, seg('list', 'לפי מטלה'), seg('sum', 'סיכום תלמידים')),
      h('label', { class: 'hw-f' }, h('span', { class: 'small muted' }, 'כיתה'), pick),
      h('span', { class: 'grow' }),
      composing ? null : h('button', { type: 'button', class: 'btn sm primary', id: 'hw-new', onclick: () => openComposer(null) }, '+ שיעורי בית חדשים')),
    h('div', { class: 'con-main' },
      composing ? composer() : null,
      S.hwView === 'sum' ? hwSummary() : hwListView()));
}

/** הטופס (חדשים, או עריכה של hw): הכיתה האחרונה שנבחרה, הנושא האחרון */
function openComposer(hw) {
  const last = remember('class');
  const known = id => id === '' || data.classes.some(c => c.id === id);
  const def = hw ? hw.classId : last !== null && known(last) ? last : S.folder !== 'all' && S.folder !== '' ? S.folder : '';
  S.hwEdit = { id: hw ? hw.id : '', classId: def, items: hw ? hw.items.slice() : [], title: hw ? hw.title : '', due: hw ? hw.due : '', topic: '' };
  S.hwView = 'list'; S.hwAsk = null; S.open = null; S.cur = null; S.curData = null;
  if (hw) S.hwOpen = hw.id;
  render();
  setTimeout(() => { const el = root.querySelector('#hw-composer'); if (el && el.scrollIntoView) el.scrollIntoView({ block: 'nearest' }); }, 0);
}

/**
 * בחירת השאלות: שורה עם הכיתה, המועד והכותרת; שורת נושאים (בכל אחד — כמה נבחרו בו); השאלות של הנושא כשורות דקות (שם · רמה · ✓,
 * מעבר עם העכבר — השאלה כמו שהתלמיד רואה אותה); הנבחרות כתגיות שאפשר להסיר; ושליחה. מתעדכן במקום (בלי לבנות את כל הדף)
 */
function composer() {
  const ed = S.hwEdit;
  const box = h('div', { class: 'hw-comp', id: 'hw-composer' });
  const redraw = () => { const n = composer(); box.replaceWith(n); return n; };
  box.append(h('div', { class: 'hw-comp-head' }, h('h3', null, ed.id ? 'עריכת שיעורי הבית' : 'שיעורי בית חדשים'),
    h('span', { class: 'small muted' }, 'התלמידים רואים אותם בחלון מאגר התרגילים, עם כפתור שמוביל לשאלות. מגישים כרגיל ב"הגשה למורה".')));
  if (!bank) {
    box.append(h('div', { class: 'empty' }, 'טוען את המאגר…'));
    loadBank().then(() => { if (box.isConnected) redraw(); });
    return box;
  }
  const cls = h('select', { class: 'field sm', id: 'hw-class', 'aria-label': 'לכיתה' },
    h('option', { value: '', selected: !ed.classId }, 'כל התלמידים שלי'), data.classes.map(c => h('option', { value: c.id, selected: c.id === ed.classId }, c.name)));
  cls.onchange = () => { ed.classId = cls.value; };
  const due = h('input', { class: 'field sm', type: 'date', id: 'hw-due', value: ed.due, 'aria-label': 'להגשה עד (לא חובה)' });
  due.onchange = () => { ed.due = due.value; };
  const title = h('input', { class: 'field sm hw-title-in', id: 'hw-title', maxlength: '40', value: ed.title, autocomplete: 'off',
    placeholder: 'שיעורי בית ' + dayMonth(new Date().toISOString()), 'aria-label': 'כותרת (לא חובה)' });
  title.oninput = () => { ed.title = title.value; };
  box.append(h('div', { class: 'hw-fields' },
    h('label', { class: 'hw-f' }, h('span', null, 'לכיתה'), cls),
    h('label', { class: 'hw-f' }, h('span', null, 'להגשה עד'), due),
    h('label', { class: 'hw-f' }, h('span', null, 'כותרת'), title)));
  // הנושאים
  const tp = (topics.some(t => t.id === ed.topic) && ed.topic) || (topics.some(t => t.id === remember('topic')) && remember('topic')) || (topics[0] && topics[0].id) || '';
  box.append(h('div', { class: 'hw-topics', role: 'tablist', 'aria-label': 'נושא' }, topics.map(t => {
    const n = ed.items.filter(id => bank[id] && bank[id].topic === t.id).length;
    return h('button', { type: 'button', role: 'tab', class: 'hw-topic', 'aria-selected': String(t.id === tp), 'data-topic': t.id,
      onclick: () => { ed.topic = t.id; remember('topic', t.id); redraw(); } }, t.title, n ? h('span', { class: 'hw-tn' }, String(n)) : null);
  })));
  // השאלות של הנושא: לפי רמה ואז לפי השם
  const full = ed.items.length >= HW_MAX_ITEMS;
  const qs = Object.keys(bank).filter(id => bank[id].topic === tp)
    .sort((a, b) => ((LEVELS[bank[a].level] || [])[2] - (LEVELS[bank[b].level] || [])[2]) || collator.compare(bank[a].title, bank[b].title));
  const toggle = id => {
    if (ed.items.includes(id)) ed.items = ed.items.filter(x => x !== id);
    else if (ed.items.length >= HW_MAX_ITEMS) { toast(`עד ${HW_MAX_ITEMS} שאלות בשיעורי בית אחד`, true); return; }
    else ed.items = ed.items.concat([id]);
    const n = redraw(), again = n.querySelector(`.hw-q[data-id="${CSS.escape(id)}"]`);
    if (again) again.focus();
  };
  box.append(h('div', { class: 'hw-qs', id: 'hw-qs' }, qs.map(id => {
    const on = ed.items.includes(id);
    const t = h('span', { class: 'hw-qt' }, bank[id].title);
    const row = h('button', { type: 'button', class: 'hw-q', 'aria-pressed': String(on), 'aria-disabled': String(!on && full), 'data-id': id, onclick: () => toggle(id) },
      h('span', { class: 'hw-box', 'aria-hidden': 'true' }, on ? '✓' : ''), t, levelPill(bank[id].level));
    attachPreview(t, { source: 'bank', bankId: id }, 'java', true);
    return row;
  })));
  // הנבחרות
  box.append(h('div', { class: 'hw-picked', id: 'hw-picked' }, ed.items.length
    ? [h('span', { class: 'small muted' }, `נבחרו (${ed.items.length} מתוך ${HW_MAX_ITEMS} לכל היותר):`), ed.items.map(id => h('span', { class: 'hw-chip', 'data-id': id }, qTitle(id),
      h('button', { type: 'button', class: 'hw-x', 'aria-label': 'הסרה: ' + qTitle(id), onclick: () => { ed.items = ed.items.filter(x => x !== id); redraw(); } }, '×')))]
    : h('span', { class: 'small muted' }, 'עוד לא נבחרו שאלות. בוחרים נושא ולוחצים על שאלה כדי להוסיף אותה (עד 10).')));
  // שליחה
  const n = ed.items.length, word = n === 1 ? 'שאלה אחת' : n + ' שאלות';
  const send = h('button', { type: 'button', class: 'btn sm primary', id: 'hw-send', disabled: !n }, ed.id ? `שמירת השינויים (${word})` : `שליחה לתלמידים (${word})`);
  send.onclick = () => busy(send, async () => {
    const r = await api('POST', '/v1/me/homework', { op: 'save', id: ed.id || undefined, classId: ed.classId, items: ed.items, title: ed.title.trim(), due: ed.due });
    remember('class', ed.classId);
    const edited = !!ed.id, to = ed.classId ? 'לכיתה ' + className(ed.classId) : 'לכל התלמידים';
    // תיקייה שלא מציגה את שיעורי הבית האלה — עוברים ל"כל התלמידים", כדי לראות אותם
    if (!(S.folder === 'all' || (S.folder === '' ? !ed.classId : !ed.classId || ed.classId === S.folder))) S.folder = 'all';
    S.hwEdit = null; S.hwOpen = r.id; S.hwView = 'list'; S.open = null; S.cur = null; S.curData = null;
    await reload(true);
    toast(edited ? 'השינויים נשמרו' : `שיעורי הבית נשלחו ${to} (${word})`);
  });
  box.append(h('div', { class: 'hw-send' }, send,
    h('button', { type: 'button', class: 'btn sm ghost', id: 'hw-cancel', onclick: () => { S.hwEdit = null; render(); } }, 'ביטול'),
    ed.classId === '' && data.classes.length ? h('span', { class: 'small muted' }, 'לכל התלמידים — גם למי שבלי כיתה') : null));
  return box;
}

/** "לפי מטלה": שורה דקה לכל שיעורי בית; לחיצה פותחת מתחתיה את הטבלה של מי הגיש מה */
function hwListView() {
  if (!hwList().length) return h('div', { class: 'tablewrap' }, h('div', { class: 'empty', id: 'hw-empty' }, hwEmpty()));
  const list = hwInFolder();
  if (!list.length) return h('div', { class: 'tablewrap' }, h('div', { class: 'empty' }, S.folder === '' ? 'אין שיעורי בית לכל התלמידים — רק לכיתות.' : 'אין עדיין שיעורי בית לכיתה הזו.'));
  const out = [h('div', { class: 'hw-head hw-grid', 'aria-hidden': 'true' }, ['שיעורי בית', 'לכיתה', 'שאלות', 'להגשה עד', 'הגישו', 'ממתינות לציון'].map(x => h('span', null, x)))];
  for (const hw of list) {
    const st = hwStats(hw), open = S.hwOpen === hw.id;
    const pct = st.n ? Math.round(100 * st.full / st.n) : 0;
    out.push(h('button', { type: 'button', class: 'hw-row hw-grid', 'aria-expanded': String(open), 'data-id': hw.id,
      onclick: () => { S.hwOpen = open ? null : hw.id; S.open = null; S.cur = null; S.curData = null; S.hwAsk = null; if (S.hwEdit && S.hwEdit.id) S.hwEdit = null; render(); } },
      h('span', { class: 'hw-t' }, h('span', { class: 'hw-chev', 'aria-hidden': 'true' }, open ? '▾' : '◂'), h('b', null, hwTitle(hw)), newDot(st.unseen)),
      h('span', { class: 'hw-c' }, hw.classId ? className(hw.classId) : 'כל התלמידים'),
      h('span', { class: 'hw-n' }, hw.items.length === 1 ? 'שאלה אחת' : hw.items.length + ' שאלות'),
      h('span', { class: 'hw-d' }, hw.due ? [h('span', null, dueText(hw.due)), pastDue(hw) ? pill('orange', 'עבר המועד') : null] : h('span', { class: 'muted' }, '—')),
      h('span', { class: 'hw-p', title: st.n ? `${st.full} הגישו את כל השאלות` + (st.part ? `, ${st.part} רק חלק` : '') + `, מתוך ${st.n} תלמידים` : 'אין תלמידים' },
        h('span', { class: 'bar' + (st.n && st.full === st.n ? ' ok' : '') }, h('span', { style: { width: pct + '%' } })),
        h('span', { class: 'hw-pt' }, `הגישו ${st.full} מתוך ${st.n}`), st.part ? h('span', { class: 'hw-part' }, `· ${st.part} בחלקן`) : null),
      h('span', { class: 'hw-w' }, st.waiting ? pill('purple', `ממתינות לציון ${st.waiting}`) : h('span', { class: 'muted' }, '—'))));
    if (open) out.push(hwPanel(hw));
  }
  return h('div', { class: 'hw-list', id: 'hw-list' }, out);
}

/** שיעורי הבית הפתוחים: עריכה / מחיקה (אישור בשורה), הטבלה, והקוד של התא שנבחר */
function hwPanel(hw) {
  const panel = h('div', { class: 'hw-panel', id: 'hw-panel' });
  if (S.hwEdit && S.hwEdit.id === hw.id) { panel.append(composer()); return panel; }
  panel.append(h('div', { class: 'hw-acts' },
    h('span', { class: 'small muted' }, (hw.classId ? 'לכיתה ' + className(hw.classId) : 'לכל התלמידים') + ` · ניתנו ${ago(hw.createdAt)}` + (hw.due ? ` · להגשה עד ${dueText(hw.due)}` : ' · בלי מועד הגשה')),
    h('span', { class: 'grow' }),
    h('button', { type: 'button', class: 'btn sm', id: 'hw-edit', onclick: () => openComposer(hw) }, 'עריכה'),
    h('button', { type: 'button', class: 'btn sm danger', id: 'hw-delete', onclick: () => { S.hwAsk = hw.id; render(); } }, 'מחיקה')));
  if (S.hwAsk === hw.id) {
    const go = h('button', { type: 'button', class: 'btn sm danger', id: 'hw-delete-yes' }, 'מחיקה');
    go.onclick = () => busy(go, async () => {
      await api('POST', '/v1/me/homework', { op: 'delete', id: hw.id });
      S.hwAsk = null; S.hwOpen = null; S.open = null; S.cur = null; S.curData = null;
      await reload(true);
      toast('שיעורי הבית נמחקו (ההגשות והציונים נשארו)');
    });
    panel.append(h('div', { class: 'confirm' }, h('span', { class: 'grow' }, `למחוק את ״${hwTitle(hw)}״? נמחקים רק שיעורי הבית — ההגשות והציונים של התלמידים נשארים.`),
      go, h('button', { type: 'button', class: 'btn sm ghost', onclick: () => { S.hwAsk = null; render(); } }, 'ביטול')));
  }
  panel.append(hwGrid(hw));
  if (S.open) panel.append(detailPane());
  return panel;
}

/** הטבלה של שיעורי בית: שורה לכל תלמיד (לפי הא"ב), עמודה לכל שאלה — כמו "לפי תרגיל" — ושתי עמודות סיכום */
function hwGrid(hw) {
  const act = hwStudents(hw);
  // תלמיד שעזב — רק אם הגיש משהו מהשאלות האלה
  const who = act.concat(inFolder(S.folder).filter(s => s.left && (!hw.classId || s.classId === hw.classId) && hw.items.some(id => byBank(s).has(id))))
    .sort((a, b) => collator.compare(a.name || a.email, b.name || b.email));
  if (!who.length) {
    return h('div', { class: 'tablewrap' }, h('div', { class: 'empty' }, hw.classId ? 'אין עדיין תלמידים בכיתה הזו. מעבירים תלמידים לכיתה בטאב "תלמידים".' : hwEmpty()));
  }
  const head = h('tr', null, h('th', { class: 'gname', scope: 'col' }, 'תלמיד'),
    h('th', { class: 'gsum', scope: 'col', title: 'כמה מהשאלות התלמיד הגיש' }, 'הגיש'),
    h('th', { class: 'gsum', scope: 'col', title: 'ממוצע הציונים שנתת על השאלות האלה' }, 'ממוצע'),
    hw.items.map(id => {
      const got = act.filter(s => byBank(s).has(id)).length;
      const fresh = act.filter(s => { const w = byBank(s).get(id); return w && !w.seen; }).length;
      return h('th', { class: 'gcol', scope: 'col' }, attachPreview(h('span', { class: 'gt' }, qTitle(id)), { source: 'bank', bankId: id }, 'java'),
        h('span', { class: 'gc' }, `${got}/${act.length}`, newDot(fresh)));
    }));
  const rows = who.map(s => {
    const m = byBank(s);
    const mine = hw.items.filter(id => m.has(id));
    const grades = mine.map(id => m.get(id).grade).filter(g => g != null);
    return h('tr', null,
      h('th', { class: 'gname', scope: 'row' }, h('span', { class: 'gn' }, s.name || s.email), s.left ? pill('gray', 'עזב') : null),
      h('td', { class: 'gsum' + (mine.length === hw.items.length ? ' gfull' : '') }, `${mine.length}/${hw.items.length}`),
      h('td', { class: 'gsum gavg' }, grades.length ? String(Math.round(grades.reduce((a, b) => a + b, 0) / grades.length)) : '—'),
      hw.items.map(id => gridCell(s, qTitle(id), m.get(id), isLate(hw, m.get(id)))));
  });
  return h('div', { class: 'stack', style: { gap: '8px' } }, gridLegend(!!hw.due),
    h('div', { class: 'tablewrap gwrap' }, h('table', { class: 'gridt', id: 'hw-grid' }, h('thead', null, head), h('tbody', null, rows))));
}

/** "סיכום תלמידים": לכל תלמיד — כמה שאלות הגיש מכל מה שקיבל, באיחור, ממוצע, ופס קטן — ריבוע לכל שיעורי בית (החדשים מימין) */
function hwSummary() {
  if (!hwList().length) return h('div', { class: 'tablewrap' }, h('div', { class: 'empty', id: 'hw-empty' }, hwEmpty()));
  const list = inFolder(S.folder).filter(s => !s.left);
  if (!list.length) return h('div', { class: 'tablewrap' }, h('div', { class: 'empty' }, S.folder === 'all' ? hwEmpty() : 'אין כאן תלמידים עדיין.'));
  const hws = hwInFolder();
  const withClass = S.folder === 'all' && data.classes.length > 0;
  const rows = list.map(s => {
    const t = hwTotals(s);
    const strip = h('span', { class: 'hw-strip' }, hws.map(hw => {
      if (hw.classId && hw.classId !== s.classId) return h('span', { class: 'hw-sq na', title: `${hwTitle(hw)} — לא ניתנו לתלמיד הזה` });
      const k = hw.items.filter(id => byBank(s).has(id)).length;
      const late = hw.items.some(id => isLate(hw, byBank(s).get(id)));
      return h('span', { class: 'hw-sq ' + (k === hw.items.length ? 'full' : k ? 'part' : 'none'), title: `${hwTitle(hw)} · הגיש ${k} מתוך ${hw.items.length}` + (late ? ' · באיחור' : '') });
    }));
    const sel = S.open && S.open.kind === 'su' && S.open.id === s.email;
    return h('tr', { class: sel ? 'sel' : null },
      h('td', null, h('b', null, s.name || s.email)),
      withClass ? h('td', { class: 'sub' }, className(s.classId)) : null,
      h('td', { class: 'num' }, t.total ? [h('span', { class: t.done === t.total ? 'hw-all' : null }, `${t.done} מתוך ${t.total}`)] : h('span', { class: 'muted' }, '—')),
      h('td', { class: 'num' }, t.late ? h('span', { class: 'hw-late' }, String(t.late)) : h('span', { class: 'muted' }, '—')),
      h('td', { class: 'num avg' }, t.avg != null ? h('b', null, String(t.avg)) : h('span', { class: 'muted' }, '—')),
      h('td', null, strip),
      h('td', null, h('div', { class: 'acts' }, h('button', { type: 'button', class: 'btn sm', onclick: () => openRow('su', s.email) }, 'תרגילים'))));
  });
  const legend = h('div', { class: 'glegend' }, h('span', { class: 'gkey' }, h('span', { class: 'hw-sq full' }), 'הגיש הכול'), h('span', { class: 'gkey' }, h('span', { class: 'hw-sq part' }), 'הגיש חלק'),
    h('span', { class: 'gkey' }, h('span', { class: 'hw-sq none' }), 'לא הגיש'), h('span', { class: 'gkey' }, h('span', { class: 'hw-sq na' }), 'לא ניתנו לו'),
    h('span', { class: 'gkey' }, `· ריבוע לכל שיעורי בית, החדשים מימין (${hws.length})`));
  return h('div', { class: 'stack', style: { gap: '8px' } }, legend, h('div', { class: 'tablewrap' }, h('table', { class: 'con-table compact hw-sum', id: 'hw-sum' },
    h('thead', null, h('tr', null, ['תלמיד', withClass ? 'כיתה' : null, 'הגיש (שאלות)', 'באיחור', 'ממוצע', 'שיעורי בית', ''].filter(x => x !== null).map(x => h('th', null, x)))),
    h('tbody', null, rows))));
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
