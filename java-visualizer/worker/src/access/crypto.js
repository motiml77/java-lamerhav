// כלים קטנים להצפנה ולקידוד — רק WebCrypto (עובד ב-Cloudflare Workers וב-Node 20+)

export const enc = new TextEncoder();
const dec = new TextDecoder();

export function b64url(bytes) {
  const u8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let s = '';
  for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000));
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function b64urlToBytes(s) {
  const t = String(s || '').replace(/-/g, '+').replace(/_/g, '/');
  const bin = atob(t + '='.repeat((4 - (t.length % 4)) % 4));
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export const b64urlJson = obj => b64url(enc.encode(JSON.stringify(obj)));
export const jsonFromB64url = s => JSON.parse(dec.decode(b64urlToBytes(s)));

export async function sha256(s) {
  return new Uint8Array(await crypto.subtle.digest('SHA-256', typeof s === 'string' ? enc.encode(s) : s));
}

export async function sha256Hex(s) {
  return Array.from(await sha256(s), b => b.toString(16).padStart(2, '0')).join('');
}

/** מחרוזת אקראית (base64url) — n בתים */
export function randomToken(n = 32) {
  return b64url(crypto.getRandomValues(new Uint8Array(n)));
}

/** קוד מורה: 8 תווים בלי תווים שמתבלבלים (0/O, 1/I/L) */
const CODE_ABC = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
export function teacherCode() {
  const r = crypto.getRandomValues(new Uint8Array(8));
  return Array.from(r, b => CODE_ABC[b % CODE_ABC.length]).join('');
}
export function normalizeCode(s) {
  const c = String(s || '').toUpperCase().replace(/[\s\-_]/g, '');
  return /^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}$/.test(c) ? c : '';
}
export const showCode = c => (c && c.length === 8 ? c.slice(0, 4) + '-' + c.slice(4) : c || '');

/** השוואה בזמן קבוע (לטביעות) */
export function sameText(a, b) {
  a = String(a || ''); b = String(b || '');
  if (a.length !== b.length) return false;
  let x = 0;
  for (let i = 0; i < a.length; i++) x |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return x === 0;
}

function pemBody(pem) {
  return String(pem || '').replace(/\\n/g, '\n').replace(/-----[A-Z ]+-----/g, '').replace(/\s+/g, '');
}

const RS256 = { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' };

export async function importRsaPrivate(pem) {
  const der = b64urlToBytes(pemBody(pem).replace(/\+/g, '-').replace(/\//g, '_'));
  return crypto.subtle.importKey('pkcs8', der, RS256, true, ['sign']);
}

export async function signRs256(key, text) {
  return new Uint8Array(await crypto.subtle.sign(RS256, key, enc.encode(text)));
}

export async function verifyRs256Jwk(jwk, text, sig) {
  const key = await crypto.subtle.importKey('jwk', jwk, RS256, false, ['verify']);
  return crypto.subtle.verify(RS256, key, sig, enc.encode(text));
}

/** המפתח הציבורי (SPKI, base64) מתוך המפתח הפרטי — כדי להציג אותו ב-/v1/access/key ולבדוק שהתוספים מכירים אותו */
export async function publicSpki(privateKey) {
  const jwk = await crypto.subtle.exportKey('jwk', privateKey);
  const pub = await crypto.subtle.importKey('jwk', { kty: 'RSA', n: jwk.n, e: jwk.e, alg: 'RS256', ext: true }, RS256, true, ['verify']);
  const spki = new Uint8Array(await crypto.subtle.exportKey('spki', pub));
  return btoa(String.fromCharCode.apply(null, spki));
}
