// יוצר קוד רישיון לתצוגה הוויזואלית (לתלמיד ששילם):
//   LICENSE_SECRET=<אותו סוד כמו ב-Worker> node tools/license.mjs <מזהה>
// המזהה: מספר קבלה / כתובת מייל / כל מחרוזת (אותיות, ספרות, . _ @ + -). לביטול — מוסיפים אותו ל-REVOKED ב-Worker.
import { licenseSignature } from '../src/index.js';
const id = process.argv[2];
const secret = process.env.LICENSE_SECRET;
if (!id || !secret || !/^[A-Za-z0-9._@+-]{1,80}$/.test(id)) {
  console.error('שימוש: LICENSE_SECRET=... node tools/license.mjs <מזהה>');
  process.exit(1);
}
console.log('DSV-' + id + '-' + await licenseSignature(secret, id));
