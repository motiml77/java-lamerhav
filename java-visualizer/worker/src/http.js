// שגיאה שמוצגת למשתמש כמו שהיא, ותשובת JSON — משותפים לכל השרת

export class Fail extends Error {
  constructor(status, code, message, detail, extra) { super(message); this.status = status; this.code = code; this.detail = detail; this.extra = extra; }
}

export function reply(status, obj, extra) {
  return new Response(JSON.stringify(obj), { status, headers: Object.assign({ 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' }, extra || {}) });
}
