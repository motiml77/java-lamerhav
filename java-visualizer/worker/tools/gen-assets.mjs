// הדפים של הכניסה ומסוף ההנהלה (public/) → src/access/assets.gen.js — כדי שיעבדו בכל דרך פריסה
// (wrangler, הקובץ worker-bundle.js, והבדיקות ב-Node). מריצים אחרי כל שינוי ב-public/:  node tools/gen-assets.mjs
import { readdirSync, readFileSync, writeFileSync, statSync } from 'node:fs';
import { join, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const pub = join(root, 'public');
const out = {};
(function walk(d) {
  for (const f of readdirSync(d).sort()) {
    const p = join(d, f);
    if (statSync(p).isDirectory()) walk(p);
    else out['/' + relative(pub, p).split('\\').join('/')] = readFileSync(p, 'utf8');
  }
})(pub);
const body = '// נוצר אוטומטית מ-public/ על ידי tools/gen-assets.mjs — לא לערוך ידנית\nexport const ASSETS = ' + JSON.stringify(out, null, 0) + ';\n';
writeFileSync(join(root, 'src/access/assets.gen.js'), body);
console.log('assets:', Object.keys(out).join(' '));
