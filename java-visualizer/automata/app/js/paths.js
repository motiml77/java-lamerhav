/*
 * paths.js - "הדרכים" של ריצה לא דטרמיניסטית (אסל"ד, אוטומט מחסנית), מתוך ה-trace של sim.js. טהור (בלי DOM).
 * לכל צעד, הקונפיגורציות שבו הן קצות הדרכים הפעילות. דרך שנתקעה (אין מעבר שמתאים) נשארת ברשימה עם המקום שבו נתקעה.
 * כל דרך: { n: מספר, id, state, pos, stack, status: 'active' | 'stuck' | 'accepted' | 'end', letter, edges: [{t, l}] }
 *   active: עוד ממשיכה · stuck: נתקעה על האות letter · accepted: הגיעה למצב מקבל בסוף המילה · end: המילה נגמרה במצב לא מקבל
 * קונפיגורציות כפולות (אותו מצב, אותו מקום, אותה מחסנית) מופיעות פעם אחת. המספור לפי סדר ההתפצלות, ונשמר בין צעדים.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.AutomataPaths = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const ORDER = { accepted: 0, active: 1, end: 2, stuck: 3 };

  /** כל הדרכים בצעד index. m: המכונה (כדי לדעת מי מקבל). */
  function paths(trace, index, m) {
    if (!trace || !trace.steps[index] || !trace.steps[index].configs) return [];
    const word = trace.word, last = trace.steps.length - 1;
    const acc = new Set(m.states.filter((s) => s.accepting).map((s) => s.id));
    const byId = new Map(), hasChild = new Set(), firstStep = new Map();
    for (let i = 0; i <= index; i++) for (const c of trace.steps[i].configs) {
      if (!byId.has(c.id)) { byId.set(c.id, c); firstStep.set(c.id, i); }
      if (c.parent >= 0) hasChild.add(c.parent);
    }
    const now = trace.steps[index].configs;
    const leaves = now.slice();
    for (let i = 0; i < index; i++) for (const c of trace.steps[i].configs) if (c.stuck && !hasChild.has(c.id)) leaves.push(c);
    const chain = (c) => { const out = []; for (let x = c; x; x = byId.get(x.parent)) out.unshift(x); return out; };
    const seen = new Set(), list = [];
    for (const c of leaves) {
      const key = c.state + '|' + c.pos + '|' + (c.stack == null ? '' : c.stack);
      if (seen.has(key)) continue;
      seen.add(key);
      const live = now.includes(c), done = c.pos >= word.length, end = index === last;
      const isAcc = m.type === 'pda' && m.acceptBy === 'empty' ? c.stack === '' : acc.has(c.state);
      let status = 'active';
      if (!live) status = done ? 'end' : 'stuck';
      else if (done && isAcc && (end || trace.type === 'nfa')) status = 'accepted';
      else if (end && trace.result !== 'accept') status = done ? 'end' : 'stuck';
      else if (end && done) status = 'end';
      const ch = chain(c);
      list.push({ id: c.id, state: c.state, pos: c.pos, stack: c.stack, status, letter: status === 'stuck' ? word[c.pos] : null,
        edges: ch.filter((x) => x.via).map((x) => x.via), key: ch.map((x) => x.id), since: firstStep.get(c.id) });
    }
    // מספור לפי סדר ההתפצלות (השרשרת מהשורש), ואז מיון: מקבלות, פעילות, נגמרו, נתקעו
    list.sort((a, b) => { for (let k = 0; k < Math.min(a.key.length, b.key.length); k++) if (a.key[k] !== b.key[k]) return a.key[k] - b.key[k]; return a.key.length - b.key.length; });
    list.forEach((p, k) => { p.n = k + 1; delete p.key; });
    return list.sort((a, b) => ORDER[a.status] - ORDER[b.status] || a.n - b.n);
  }

  return { paths };
});
