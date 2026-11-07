// Firebase / Google: בדיקת כניסה עם Google (Firebase ID token) ומפתח גישה ל-Firestore בשם חשבון השירות.
//
// הגדרות ב-Worker:
//   FIREBASE_PROJECT_ID          — מזהה הפרויקט (פרויקט חדש ונפרד, לא האתר הקיים)
//   FIREBASE_SERVICE_ACCOUNT     — סוד: קובץ ה-JSON של חשבון השירות (client_email, private_key)
//   FIRESTORE_EMULATOR_HOST / FIREBASE_AUTH_EMULATOR_HOST — רק לבדיקות במחשב (127.0.0.1:port)

import { b64url, b64urlJson, jsonFromB64url, b64urlToBytes, importRsaPrivate, signRs256, verifyRs256Jwk } from './crypto.js';

export class AuthError extends Error {}

const JWKS_URL = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
let jwksCache = { keys: null, until: 0 };
let tokenCache = { key: '', token: '', until: 0 };

export function projectId(env) {
  return String(env.FIREBASE_PROJECT_ID || '').trim();
}

/** אמולטור רק בכתובת מקומית — כך הגדרה שגויה בשרת האמיתי לא יכולה לכבות את הבדיקות */
function localHost(h) {
  return /^(127\.0\.0\.1|localhost|\[::1\]):\d+$/.test(String(h || ''));
}
export const firestoreEmulator = env => (localHost(env.FIRESTORE_EMULATOR_HOST) ? env.FIRESTORE_EMULATOR_HOST : '');
export const authEmulator = env => (localHost(env.FIREBASE_AUTH_EMULATOR_HOST) ? env.FIREBASE_AUTH_EMULATOR_HOST : '');

/** מפתח גישה (OAuth) ל-Firestore בשם חשבון השירות — נשמר בזיכרון עד 5 דקות לפני שפג */
export async function serviceToken(env) {
  if (firestoreEmulator(env)) return 'owner';
  let sa;
  try { sa = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT || ''); } catch (e) { sa = null; }
  if (!sa || !sa.client_email || !sa.private_key) throw new Error('FIREBASE_SERVICE_ACCOUNT missing');
  const now = Math.floor(Date.now() / 1000);
  if (tokenCache.key === sa.client_email && tokenCache.until > now + 300) return tokenCache.token;
  const head = b64urlJson({ alg: 'RS256', typ: 'JWT', kid: sa.private_key_id || undefined });
  const body = b64urlJson({
    iss: sa.client_email, sub: sa.client_email, aud: 'https://oauth2.googleapis.com/token',
    scope: 'https://www.googleapis.com/auth/datastore', iat: now, exp: now + 3600,
  });
  const key = await importRsaPrivate(sa.private_key);
  const jwt = head + '.' + body + '.' + b64url(await signRs256(key, head + '.' + body));
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: 'grant_type=' + encodeURIComponent('urn:ietf:params:oauth:grant-type:jwt-bearer') + '&assertion=' + jwt,
  });
  const j = await res.json().catch(() => ({}));
  if (!res.ok || !j.access_token) throw new Error('service token: ' + res.status + ' ' + (j.error || ''));
  tokenCache = { key: sa.client_email, token: j.access_token, until: now + (+j.expires_in || 3600) };
  return j.access_token;
}

async function jwks() {
  const now = Date.now();
  if (jwksCache.keys && jwksCache.until > now) return jwksCache.keys;
  const res = await fetch(JWKS_URL);
  if (!res.ok) throw new AuthError('google keys ' + res.status);
  const j = await res.json();
  const age = /max-age=(\d+)/.exec(res.headers.get('cache-control') || '');
  jwksCache = { keys: j.keys || [], until: now + 1000 * Math.min(+(age && age[1]) || 3600, 6 * 3600) };
  return jwksCache.keys;
}

/**
 * בודק Firebase ID token (כניסה עם Google): חתימה של Google, הפרויקט שלנו, לא פג, מייל מאומת, ספק google.com.
 * מחזיר {uid, email, name, picture}. באמולטור (רק בבדיקות) הטוקן לא חתום — בודקים את כל השאר.
 */
export async function verifyIdToken(env, token) {
  const parts = String(token || '').split('.');
  if (parts.length !== 3) throw new AuthError('format');
  let head, claims;
  try { head = jsonFromB64url(parts[0]); claims = jsonFromB64url(parts[1]); } catch (e) { throw new AuthError('format'); }
  const pid = projectId(env);
  if (!pid) throw new AuthError('no project');
  if (authEmulator(env) && head.alg === 'none') {
    // אמולטור: בלי חתימה
  } else {
    if (head.alg !== 'RS256' || !head.kid) throw new AuthError('alg');
    const jwk = (await jwks()).find(k => k.kid === head.kid);
    if (!jwk) throw new AuthError('kid');
    const ok = await verifyRs256Jwk(jwk, parts[0] + '.' + parts[1], b64urlToBytes(parts[2]));
    if (!ok) throw new AuthError('signature');
  }
  const now = Math.floor(Date.now() / 1000);
  if (claims.aud !== pid || claims.iss !== 'https://securetoken.google.com/' + pid) throw new AuthError('audience');
  if (!(claims.exp > now) || !(claims.iat <= now + 300) || !claims.sub) throw new AuthError('expired');
  const provider = claims.firebase && claims.firebase.sign_in_provider;
  if (provider !== 'google.com') throw new AuthError('provider');
  if (!claims.email || claims.email_verified !== true) throw new AuthError('email');
  return { uid: String(claims.sub), email: String(claims.email).toLowerCase(), name: String(claims.name || ''), picture: String(claims.picture || '') };
}

export const _test = { reset() { jwksCache = { keys: null, until: 0 }; tokenCache = { key: '', token: '', until: 0 }; } };
