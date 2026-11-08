// Firestore דרך REST (ב-Worker אין Admin SDK). רק השרת ניגש לנתונים — כללי האבטחה של Firestore חוסמים כל מחשב אחר.
//
// מסמך = אובייקט JS פשוט: מחרוזות, מספרים, true/false, null, מערכים ואובייקטים. תאריכים נשמרים כמחרוזות ISO
// (ממוינות נכון גם כמחרוזות). שדות שמתחילים ב-_ (_id) לא נשמרים — הם רק בקריאה.

import { firestoreEmulator, projectId, serviceToken } from './google.js';

export class DbError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

function encodeValue(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === 'string') return { stringValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(encodeValue) } };
  if (typeof v === 'object') return { mapValue: { fields: encodeFields(v) } };
  return { stringValue: String(v) };
}

function encodeFields(obj) {
  const out = {};
  for (const [k, v] of Object.entries(obj || {})) if (v !== undefined && !k.startsWith('_')) out[k] = encodeValue(v);
  return out;
}

function decodeValue(v) {
  if (!v || 'nullValue' in v) return null;
  if ('booleanValue' in v) return v.booleanValue;
  if ('integerValue' in v) return Number(v.integerValue);
  if ('doubleValue' in v) return Number(v.doubleValue);
  if ('stringValue' in v) return v.stringValue;
  if ('timestampValue' in v) return v.timestampValue;
  if ('arrayValue' in v) return (v.arrayValue.values || []).map(decodeValue);
  if ('mapValue' in v) return decodeFields(v.mapValue.fields);
  return null;
}

function decodeFields(f) {
  const out = {};
  for (const [k, v] of Object.entries(f || {})) out[k] = decodeValue(v);
  return out;
}

function decodeDoc(d) {
  if (!d || !d.name) return null;
  const o = decodeFields(d.fields);
  Object.defineProperty(o, '_id', { value: decodeURIComponent(d.name.split('/').pop()), enumerable: false });
  return o;
}

const OPS = { '==': 'EQUAL', '!=': 'NOT_EQUAL', '<': 'LESS_THAN', '<=': 'LESS_THAN_OR_EQUAL', '>': 'GREATER_THAN', '>=': 'GREATER_THAN_OR_EQUAL', 'in': 'IN', 'array-contains': 'ARRAY_CONTAINS' };

export function db(env) {
  const pid = projectId(env);
  const emu = firestoreEmulator(env);
  const root = `projects/${pid}/databases/(default)/documents`;
  const base = (emu ? `http://${emu}` : 'https://firestore.googleapis.com') + '/v1/';
  const name = path => `${root}/${path}`;
  const urlOf = path => base + root + '/' + String(path).split('/').map(encodeURIComponent).join('/');

  async function call(method, url, body) {
    const token = await serviceToken(env);
    const res = await fetch(url, {
      method,
      headers: { authorization: 'Bearer ' + token, 'content-type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    let j = null;
    try { j = text ? JSON.parse(text) : null; } catch (e) { j = null; }
    if (!res.ok) {
      const msg = (j && j.error && (j.error.status + ' ' + j.error.message)) || text.slice(0, 200);
      throw new DbError(res.status, msg);
    }
    return j;
  }

  function updateWrite(path, data, opts = {}) {
    const w = { update: { name: name(path), fields: encodeFields(data) } };
    if (opts.merge) w.updateMask = { fieldPaths: Object.keys(data).filter(k => data[k] !== undefined && !k.startsWith('_')).map(quoteField) };
    if (opts.exists === true) w.currentDocument = { exists: true };
    if (opts.exists === false) w.currentDocument = { exists: false };
    if (opts.increment) {
      w.updateTransforms = Object.entries(opts.increment).map(([f, n]) => ({ fieldPath: quoteField(f), increment: encodeValue(n) }));
      if (!w.updateMask) w.updateMask = { fieldPaths: [] };
    }
    return w;
  }

  /** כתיבות שמצטברות בתוך טרנזקציה / batch */
  function writer() {
    const writes = [];
    return {
      writes,
      set(path, data) { writes.push(updateWrite(path, data)); },
      create(path, data) { writes.push(updateWrite(path, data, { exists: false })); },
      update(path, data, increment) { writes.push(updateWrite(path, data, { merge: true, exists: true, increment })); },
      merge(path, data, increment) { writes.push(updateWrite(path, data, { merge: true, increment })); },
      remove(path) { writes.push({ delete: name(path) }); },
    };
  }

  const api = {
    async get(path) {
      try { return decodeDoc(await call('GET', urlOf(path))); } catch (e) {
        if (e.status === 404) return null;
        throw e;
      }
    },
    async getMany(paths) {
      if (!paths.length) return [];
      const j = await call('POST', base + root + ':batchGet', { documents: paths.map(name) });
      const by = new Map();
      for (const r of j || []) if (r.found) by.set(r.found.name, decodeDoc(r.found));
      return paths.map(p => by.get(name(p)) || null);
    },
    /** מחליף את כל המסמך */
    async set(path, data) { await api.commit(w => w.set(path, data)); },
    /** משנה רק את השדות שנשלחו (יוצר אם אין) */
    async merge(path, data, increment) { await api.commit(w => w.merge(path, data, increment)); },
    /** משנה רק את השדות שנשלחו — רק אם המסמך קיים */
    async update(path, data, increment) { await api.commit(w => w.update(path, data, increment)); },
    /** יוצר רק אם אין עדיין — false אם כבר קיים */
    async create(path, data) {
      try { await api.commit(w => w.create(path, data)); return true; } catch (e) {
        if (e.status === 409 || /ALREADY_EXISTS/.test(e.message)) return false;
        throw e;
      }
    },
    async remove(path) { await api.commit(w => w.remove(path)); },
    async commit(fill, transaction) {
      const w = writer();
      await fill(w);
      if (!w.writes.length && !transaction) return;
      await call('POST', base + root + ':commit', transaction ? { writes: w.writes, transaction } : { writes: w.writes });
    },
    /**
     * שאילתה על אוסף. where: [[שדה, אופרטור, ערך], ...]; orderBy: [שדה, 'asc'|'desc']; limit.
     * (כדי לא להזדקק לאינדקסים מורכבים: סינון על שדה אחד, או מיון בלי סינון — את השאר ממיינים בקוד)
     */
    async query(collection, q = {}) {
      const sq = { from: [{ collectionId: collection }] };
      const filters = (q.where || []).map(([f, op, v]) => ({ fieldFilter: { field: { fieldPath: quoteField(f) }, op: OPS[op], value: Array.isArray(v) && op === 'in' ? { arrayValue: { values: v.map(encodeValue) } } : encodeValue(v) } }));
      if (filters.length === 1) sq.where = filters[0];
      else if (filters.length > 1) sq.where = { compositeFilter: { op: 'AND', filters } };
      if (q.orderBy) sq.orderBy = [{ field: { fieldPath: quoteField(q.orderBy[0]) }, direction: q.orderBy[1] === 'desc' ? 'DESCENDING' : 'ASCENDING' }];
      if (q.limit) sq.limit = q.limit;
      const j = await call('POST', base + root + ':runQuery', { structuredQuery: sq });
      return (j || []).filter(r => r.document).map(r => decodeDoc(r.document));
    },
    /**
     * טרנזקציה: fn(t) קורא עם t.get / t.getMany וכותב עם t.set / t.create / t.update / t.merge / t.remove.
     * אם מישהו אחר שינה את אותם מסמכים באמצע — Firestore דוחה, ומנסים שוב (עד 5 פעמים).
     */
    async tx(fn) {
      let last;
      for (let attempt = 0; attempt < 5; attempt++) {
        const begin = await call('POST', base + root + ':beginTransaction', { options: { readWrite: {} } });
        const transaction = begin.transaction;
        const w = writer();
        const t = Object.assign(w, {
          async get(path) { return (await t.getMany([path]))[0]; },
          async getMany(paths) {
            const j = await call('POST', base + root + ':batchGet', { documents: paths.map(name), transaction });
            const by = new Map();
            for (const r of j || []) if (r.found) by.set(r.found.name, decodeDoc(r.found));
            return paths.map(p => by.get(name(p)) || null);
          },
        });
        let result;
        try {
          result = await fn(t);
        } catch (e) {
          await call('POST', base + root + ':rollback', { transaction }).catch(() => {});
          throw e;
        }
        try {
          await call('POST', base + root + ':commit', { writes: w.writes, transaction });
          return result;
        } catch (e) {
          last = e;
          if (!(e.status === 409 || /ABORTED/.test(e.message))) throw e;
          await new Promise(r => setTimeout(r, 30 + Math.random() * 120 * (attempt + 1)));
        }
      }
      throw last;
    },
  };
  return api;
}

function quoteField(f) {
  return String(f).split('.').map(p => (/^[A-Za-z_][A-Za-z0-9_]*$/.test(p) ? p : '`' + p.replace(/[`\\]/g, '\\$&') + '`')).join('.');
}

/** מזהה ממוין לפי זמן (ליומן ולבקשות) */
export function timeId() {
  const t = Date.now().toString(36).padStart(9, '0');
  const r = Array.from(crypto.getRandomValues(new Uint8Array(5)), b => (b % 36).toString(36)).join('');
  return t + r;
}
