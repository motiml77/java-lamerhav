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
// עותק של נוסח השאלות במאגר (bank/ — קבוע לכל גרסה), כדי שעמדת הניהול תראה למורה את השאלה כמו שהתלמיד רואה אותה
// (מעבר עם העכבר על שם התרגיל), בלי שהתלמיד ישלח אותה. רק הנוסח והציורים, בלי ההצעה לפתרון.
const bankDir = join(root, '..', 'bank');
try {
  const preview = {};
  const parents = (classes, re) => Object.fromEntries((classes || []).map(c => {
    const m = re.exec(c.code || '');
    return [c.name, m ? m[1] : ''];
  }));
  for (const topic of readdirSync(bankDir).sort()) {
    const td = join(bankDir, topic);
    if (!statSync(td).isDirectory()) continue;
    for (const f of readdirSync(td).sort()) {
      if (!f.endsWith('.json')) continue;
      const q = JSON.parse(readFileSync(join(td, f), 'utf8'));
      const cs = q.csharp && q.csharp.question ? q.csharp : null;
      preview[q.id] = {
        title: q.title, topic: q.topic, level: q.level,
        java: { q: q.question, parents: parents(q.exercise && q.exercise.classes, /class\s+\w+\s+extends\s+(\w+)/) },
        cs: cs ? { q: cs.question, parents: parents((cs.exercise && cs.exercise.classes) || (q.exercise && q.exercise.classes), /class\s+\w+\s*:\s*(\w+)/) } : null,
      };
    }
  }
  out['/bank-preview.json'] = JSON.stringify(preview);
} catch (e) {
  console.log('bank-preview: ' + e.message);
}
const body = '// נוצר אוטומטית מ-public/ על ידי tools/gen-assets.mjs — לא לערוך ידנית\nexport const ASSETS = ' + JSON.stringify(out, null, 0) + ';\n';
writeFileSync(join(root, 'src/access/assets.gen.js'), body);
console.log('assets:', Object.keys(out).join(' '));
