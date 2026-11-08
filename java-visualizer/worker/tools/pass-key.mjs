// מפתח החתימה של "אישור הכניסה" (pass) — פעם אחת, בהתקנה:
//   node tools/pass-key.mjs
// מדפיס:
//   1. המפתח הפרטי — מכניסים כסוד לשרת:  npx wrangler@4 secret put PASS_KEY   (ומדביקים את כל הבלוק)
//   2. המפתח הציבורי (base64) — נכנס לתוספים: eclipse-plugin/resources/access.properties → passKey=...
// לא שומרים את המפתח הפרטי בשום קובץ במאגר.
import { generateKeyPairSync } from 'node:crypto';
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
console.log('----- PASS_KEY (סוד — רק לשרת) -----');
console.log(privateKey.export({ type: 'pkcs8', format: 'pem' }).trim());
console.log('\n----- passKey (ציבורי — לתוספים) -----');
console.log(publicKey.export({ type: 'spki', format: 'der' }).toString('base64'));
