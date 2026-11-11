/*
 * model.js - מבנה הנתונים של מכונה (אס"ד, אסל"ד, אוטומט מחסנית, מכונת טיורינג).
 * לוגיקה טהורה בלבד: בלי DOM. רץ גם בדפדפן (window.AutomataModel) וגם ב-Node (require).
 * למה לא ES module: דפדפנים חוסמים import מ-file://, והכלי חייב להיפתח בלחיצה כפולה על index.html.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) { module.exports = api; require('./validate.js'); }
  else root.AutomataModel = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const FORMAT = 'dsviz-automaton';
  const VERSION = 1;
  const TYPES = ['dfa', 'nfa', 'pda', 'tm'];
  const EPS = '';            // ε נשמר כמחרוזת ריקה
  const EPS_CHAR = 'ε';
  const EPS_ALIASES = new Set(['', 'ε', 'eps', 'epsilon', 'λ', 'lambda', 'אפסילון']);
  const MOVES = ['L', 'R', 'S'];

  const TYPE_INFO = {
    dfa: { short: 'אס"ד', long: 'אוטומט סופי דטרמיניסטי', en: 'DFA' },
    nfa: { short: 'אסל"ד', long: 'אוטומט סופי לא דטרמיניסטי', en: 'NFA' },
    pda: { short: 'אוטומט מחסנית', long: 'אוטומט מחסנית', en: 'PDA' },
    tm: { short: 'מכונת טיורינג', long: 'מכונת טיורינג', en: 'TM' },
  };

  /* ---------- סמלים ומילים ---------- */

  /** מפרק מחרוזת לסמלים לפי נקודות קוד (כך ש-ε או אותיות עבריות נספרות כסמל אחד). */
  function symbols(word) { return Array.from(word || ''); }

  function isEps(s) { return EPS_ALIASES.has(String(s).trim().toLowerCase()); }
  function showSym(s) { return s === EPS ? EPS_CHAR : s; }

  /* ---------- יצירה ---------- */

  function createMachine(type, opts) {
    if (!TYPES.includes(type)) throw new Error('סוג מכונה לא מוכר: ' + type);
    opts = opts || {};
    const m = {
      format: FORMAT,
      version: VERSION,
      type,
      name: opts.name || 'מכונה חדשה',
      alphabet: opts.alphabet ? opts.alphabet.slice() : [],
      states: [],
      start: null,
      transitions: [],
      tests: [],
      view: { x: 0, y: 0, zoom: 1 },
    };
    if (type === 'pda') {
      m.stackAlphabet = opts.stackAlphabet ? opts.stackAlphabet.slice() : ['Z'];
      m.stackStart = opts.stackStart || 'Z';
      m.acceptBy = opts.acceptBy || 'final';
    }
    if (type === 'tm') {
      m.blank = opts.blank || '_';
      m.tapeAlphabet = opts.tapeAlphabet ? opts.tapeAlphabet.slice() : [m.blank];
    }
    return m;
  }

  function clone(m) { return JSON.parse(JSON.stringify(m)); }

  let idCounter = 0;
  function uid(prefix) {
    idCounter = (idCounter + 1) % 1e6;
    return prefix + Date.now().toString(36).slice(-4) + idCounter.toString(36) + Math.random().toString(36).slice(2, 5);
  }

  /* ---------- מצבים ---------- */

  function getState(m, id) { return m.states.find((s) => s.id === id) || null; }
  function stateName(m, id) { const s = getState(m, id); return s ? s.name : id; }

  /** השם הפנוי הקטן ביותר מהצורה q0, q1, ... */
  function nextStateName(m) {
    const used = new Set(m.states.map((s) => s.name));
    for (let i = 0; ; i++) if (!used.has('q' + i)) return 'q' + i;
  }

  function addState(m, props) {
    props = props || {};
    const s = {
      id: props.id || uid('s'),
      name: props.name || nextStateName(m),
      x: Math.round(props.x || 0),
      y: Math.round(props.y || 0),
      accepting: !!props.accepting,
    };
    if (props.loopAngle != null) s.loopAngle = props.loopAngle;
    m.states.push(s);
    if (m.start == null) m.start = s.id;
    return s;
  }

  function removeStates(m, ids) {
    const set = new Set(ids);
    m.states = m.states.filter((s) => !set.has(s.id));
    m.transitions = m.transitions.filter((t) => !set.has(t.from) && !set.has(t.to));
    if (set.has(m.start)) m.start = m.states.length ? m.states[0].id : null;
  }

  /** מחזיר null אם הצליח, או הודעת שגיאה בעברית. */
  function renameState(m, id, name) {
    name = String(name || '').trim();
    if (!name) return 'לשם מצב צריך לפחות תו אחד';
    if (name.length > 12) return 'שם מצב יכול להיות עד 12 תווים';
    if (m.states.some((s) => s.id !== id && s.name === name)) return 'כבר יש מצב בשם ' + name;
    const s = getState(m, id);
    if (s) s.name = name;
    return null;
  }

  function setStart(m, id) { if (getState(m, id)) m.start = id; }
  function setAccepting(m, id, value) { const s = getState(m, id); if (s) s.accepting = value == null ? !s.accepting : !!value; }
  function moveState(m, id, x, y) { const s = getState(m, id); if (s) { s.x = Math.round(x); s.y = Math.round(y); } }

  /* ---------- מעברים ---------- */

  function getTransition(m, id) { return m.transitions.find((t) => t.id === id) || null; }
  function findTransition(m, from, to) { return m.transitions.find((t) => t.from === from && t.to === to) || null; }

  function labelKey(type, l) {
    if (type === 'pda') return l.read + '\u0001' + l.pop + '\u0001' + l.push;
    if (type === 'tm') return l.read + '\u0001' + l.write + '\u0001' + l.move;
    return l.read;
  }

  /** מוסיף מעבר. אם כבר יש קשת מ-from ל-to, התוויות מתמזגות לתוכה (קשת אחת לכל זוג). */
  function addTransition(m, from, to, labels) {
    labels = labels || [];
    let t = findTransition(m, from, to);
    if (!t) {
      t = { id: uid('t'), from, to, labels: [] };
      m.transitions.push(t);
    }
    const keys = new Set(t.labels.map((l) => labelKey(m.type, l)));
    for (const l of labels) {
      const k = labelKey(m.type, l);
      if (!keys.has(k)) { keys.add(k); t.labels.push(Object.assign({}, l)); }
    }
    return t;
  }

  function setTransitionLabels(m, id, labels) {
    const t = getTransition(m, id);
    if (!t) return;
    const seen = new Set();
    t.labels = labels.filter((l) => { const k = labelKey(m.type, l); if (seen.has(k)) return false; seen.add(k); return true; });
  }

  function removeTransitions(m, ids) {
    const set = new Set(ids);
    m.transitions = m.transitions.filter((t) => !set.has(t.id));
  }

  /* ---------- תוויות: טקסט <-> מבנה ---------- */

  function formatLabel(type, l) {
    if (type === 'pda') return showSym(l.read) + ',' + showSym(l.pop) + '/' + (l.push === EPS ? EPS_CHAR : l.push);
    if (type === 'tm') return l.read + '/' + l.write + ',' + l.move;
    return showSym(l.read);
  }

  /** טקסט של תווית שלמה לתצוגה. ב-FA: "a, b"; ב-PDA/TM: כלל בכל שורה. */
  function formatLabels(type, labels) {
    if (type === 'pda' || type === 'tm') return labels.map((l) => formatLabel(type, l)).join('\n');
    return labels.map((l) => formatLabel(type, l)).join(', ');
  }

  function oneSymbol(raw, what, errors, allowEps) {
    const s = String(raw).trim();
    if (allowEps && isEps(s)) return EPS;
    const cs = symbols(s);
    if (cs.length !== 1) { errors.push(what + ' "' + s + '" צריך להיות תו אחד' + (allowEps ? ' (או ε)' : '')); return null; }
    if (cs[0] === ',' || cs[0] === '/' || cs[0] === '|') { errors.push('התו "' + cs[0] + '" שמור ואי אפשר להשתמש בו כסמל'); return null; }
    return cs[0];
  }

  /**
   * מפענח טקסט שהמשתמש הקליד בעורך התווית.
   * FA:  "a, b"  /  "a b"  /  "ε"
   * PDA: "a,Z/AZ"  או  "a,Z→AZ" ; כמה כללים: שורה לכל כלל או מפריד "|"
   * TM:  "1/0,R"  או  "1→0,R"  או  "1/0R" ; כמה כללים: שורה לכל כלל או "|"
   * מחזיר { labels, errors }.
   */
  function parseLabels(type, text) {
    const errors = [];
    const labels = [];
    text = String(text == null ? '' : text);
    if (type === 'dfa' || type === 'nfa') {
      const parts = text.split(/[,\s]+/).filter((p) => p !== '');
      if (!parts.length) errors.push('צריך לפחות סמל אחד');
      for (const p of parts) {
        if (type === 'dfa' && isEps(p)) { if (!errors.includes('באס"ד אין מעברי ε')) errors.push('באס"ד אין מעברי ε'); continue; }
        const s = oneSymbol(p, 'הסמל', errors, type === 'nfa');
        if (s != null) labels.push({ read: s });
      }
      return { labels, errors };
    }
    const rules = text.split(/[\n|]+/).map((r) => r.trim()).filter(Boolean);
    if (!rules.length) errors.push('צריך לפחות כלל אחד');
    for (const r of rules) {
      if (type === 'pda') {
        const mm = r.match(/^(.*?),(.*?)(?:\/|→|->)(.*)$/);
        if (!mm) { errors.push('הכלל "' + r + '" צריך להיראות כך: a,Z/AZ (קוראים a, שולפים Z, דוחפים AZ)'); continue; }
        const read = oneSymbol(mm[1], 'סמל הקלט', errors, true);
        const pop = oneSymbol(mm[2], 'הסמל שנשלף', errors, true);
        const pushRaw = mm[3].replace(/\s+/g, '');
        const push = isEps(pushRaw) ? EPS : pushRaw.replace(/ε/g, '');
        if (read == null || pop == null) continue;
        labels.push({ read, pop, push });
      } else {
        const mm = r.match(/^(.*?)(?:\/|→|->)\s*(.+?)\s*,?\s*([LRSlrs])$/);
        if (!mm) { errors.push('הכלל "' + r + '" צריך להיראות כך: 1/0,R (קוראים 1, כותבים 0, זזים ימינה)'); continue; }
        const read = oneSymbol(mm[1], 'הסמל שנקרא', errors, false);
        const write = oneSymbol(mm[2], 'הסמל שנכתב', errors, false);
        if (read == null || write == null) continue;
        labels.push({ read, write, move: mm[3].toUpperCase() });
      }
    }
    return { labels, errors };
  }

  /* ---------- אלפבית ---------- */

  /** הסמלים שמופיעים בתוויות (בלי ε), לפי סדר הופעה. */
  function usedInputSymbols(m) {
    const out = [];
    const seen = new Set();
    const add = (s) => { if (s !== EPS && s != null && !seen.has(s)) { seen.add(s); out.push(s); } };
    for (const t of m.transitions) for (const l of t.labels) {
      if (m.type === 'tm') { add(l.read); add(l.write); } else add(l.read);
    }
    return out;
  }

  function usedStackSymbols(m) {
    const out = new Set();
    if (m.type !== 'pda') return [];
    if (m.stackStart) out.add(m.stackStart);
    for (const t of m.transitions) for (const l of t.labels) {
      if (l.pop !== EPS) out.add(l.pop);
      for (const c of symbols(l.push)) out.add(c);
    }
    return Array.from(out);
  }

  /** Σ האפקטיבי: המוצהר אם יש, אחרת הנגזר מהתוויות. ב-TM בלי הצהרה: כל מה שנקרא חוץ מהריק. */
  function inputAlphabet(m) {
    if (m.alphabet && m.alphabet.length) return m.alphabet.slice();
    if (m.type === 'tm') {
      const out = [];
      for (const t of m.transitions) for (const l of t.labels) if (l.read !== m.blank && !out.includes(l.read)) out.push(l.read);
      return out;
    }
    return usedInputSymbols(m);
  }

  /* ---------- שמירה וטעינה ---------- */

  function serialize(m) { return JSON.stringify(m, null, 2); }

  /** טוען JSON (מחרוזת או אובייקט), משלים ברירות מחדל ובודק מבנה. מחזיר { machine, errors }. */
  function deserialize(input) {
    let o;
    try { o = typeof input === 'string' ? JSON.parse(input) : input; } catch (e) { return { machine: null, errors: ['הקובץ אינו JSON תקין'] }; }
    if (!o || typeof o !== 'object') return { machine: null, errors: ['הקובץ ריק'] };
    if (o.format && o.format !== FORMAT) return { machine: null, errors: ['זה לא קובץ מכונה של DSViz'] };
    if (!TYPES.includes(o.type)) return { machine: null, errors: ['סוג המכונה בקובץ לא מוכר: ' + o.type] };
    if (o.version && o.version > VERSION) return { machine: null, errors: ['הקובץ נשמר בגרסה חדשה יותר של הכלי'] };
    const m = createMachine(o.type, o);
    m.name = String(o.name || m.name);
    const errors = [];
    const ids = new Set();
    for (const s of o.states || []) {
      if (!s || !s.id || ids.has(s.id)) { errors.push('מצב לא תקין בקובץ דולג'); continue; }
      ids.add(s.id);
      m.states.push({ id: String(s.id), name: String(s.name || s.id), x: +s.x || 0, y: +s.y || 0, accepting: !!s.accepting, ...(s.loopAngle != null ? { loopAngle: +s.loopAngle } : {}) });
    }
    m.start = ids.has(o.start) ? o.start : (m.states[0] ? m.states[0].id : null);
    for (const t of o.transitions || []) {
      if (!t || !ids.has(t.from) || !ids.has(t.to)) { errors.push('מעבר שמצביע על מצב חסר דולג'); continue; }
      const labels = (t.labels || []).map((l) => {
        if (m.type === 'pda') return { read: String(l.read ?? ''), pop: String(l.pop ?? ''), push: String(l.push ?? '') };
        if (m.type === 'tm') return { read: String(l.read), write: String(l.write), move: String(l.move || 'S').toUpperCase() };
        return { read: String(l.read ?? '') };
      });
      const nt = addTransition(m, t.from, t.to, labels);
      if (t.id && !m.transitions.some((x) => x.id === t.id)) nt.id = String(t.id);
    }
    m.tests = Array.isArray(o.tests) ? o.tests.filter((x) => x && typeof x.word === 'string').map((x) => ({ word: x.word, expect: !!x.expect })) : [];
    if (o.view) m.view = { x: +o.view.x || 0, y: +o.view.y || 0, zoom: +o.view.zoom || 1 };
    // השפה שהמכונה אמורה לקבל (לשונית "שפה"): הנוסחה כטקסט, והאם זה קובץ תרגיל של מורה
    if (o.language && typeof o.language.text === 'string') {
      m.language = { text: o.language.text, allowBuild: o.language.allowBuild !== false, locked: !!o.language.locked, ...(o.language.desc ? { desc: String(o.language.desc) } : {}) };
      // המשימה (סבב 14, js/task.js): מי בונה, והבדיקה האחרונה
      if (['auto', 'practice', 'solution'].includes(o.language.mode)) m.language.mode = o.language.mode;
      if (o.language.check && typeof o.language.check === 'object' && typeof o.language.check.status === 'string') m.language.check = JSON.parse(JSON.stringify(o.language.check));
    }
    return { machine: m, errors };
  }

  /* ---------- סידור אוטומטי ---------- */

  /**
   * שכבות לפי BFS מהמצב ההתחלתי, משמאל לימין (הדיאגרמה היא שרטוט מתמטי ונשארת LTR גם בממשק RTL).
   * בכל שכבה המצבים מסודרים אנכית סביב המרכז. מצבים שאי אפשר להגיע אליהם בשכבה האחרונה.
   */
  function autoLayout(m, opts) {
    opts = opts || {};
    const gapX = opts.gapX || 180, gapY = opts.gapY || 130;
    const cx = opts.cx || 0, cy = opts.cy || 0;
    if (!m.states.length) return;
    const layer = new Map();
    const order = [];
    const startId = m.start && getState(m, m.start) ? m.start : m.states[0].id;
    layer.set(startId, 0); order.push(startId);
    for (let i = 0; i < order.length; i++) {
      const cur = order[i];
      for (const t of m.transitions) if (t.from === cur && !layer.has(t.to)) { layer.set(t.to, layer.get(cur) + 1); order.push(t.to); }
    }
    const maxL = order.length ? Math.max(...order.map((id) => layer.get(id))) : 0;
    for (const s of m.states) if (!layer.has(s.id)) { layer.set(s.id, maxL + 1); order.push(s.id); }
    const layers = [];
    for (const id of order) { const l = layer.get(id); (layers[l] = layers[l] || []).push(id); }
    const total = layers.length;
    layers.forEach((ids, li) => {
      ids.forEach((id, k) => {
        const s = getState(m, id);
        s.x = Math.round(cx + (li - (total - 1) / 2) * gapX);
        s.y = Math.round(cy + (k - (ids.length - 1) / 2) * gapY);
      });
    });
  }

  return {
    FORMAT, VERSION, TYPES, TYPE_INFO, EPS, EPS_CHAR, MOVES,
    symbols, isEps, showSym,
    createMachine, clone, uid,
    getState, stateName, nextStateName, addState, removeStates, renameState, setStart, setAccepting, moveState,
    getTransition, findTransition, addTransition, setTransitionLabels, removeTransitions,
    formatLabel, formatLabels, parseLabels,
    usedInputSymbols, usedStackSymbols, inputAlphabet,
    serialize, deserialize,
    autoLayout,
  };
});
