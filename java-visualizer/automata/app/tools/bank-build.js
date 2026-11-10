/*
 * tools/bank-build.js - אורז את מאגר השאלות (../bank: topics.json ותיקייה לכל נושא) לקובץ js/bank/data.js.
 * למה: הכלי נפתח מ-file://, ושם הדפדפן לא נותן לקרוא קובצי JSON. לכן הנתונים נכנסים לסקריפט רגיל.
 * מריצים אחרי כל שינוי בשאלה: node tools/bank-build.js   (test/bank.test.js נכשל אם data.js לא מעודכן)
 */
'use strict';
const fs = require('fs');
const path = require('path');

const BANK = path.resolve(__dirname, '..', '..', 'bank');
const OUT = path.resolve(__dirname, '..', 'js', 'bank', 'data.js');
const LEVELS = ['easy', 'medium', 'hard'];

function collect() {
  const topics = JSON.parse(fs.readFileSync(path.join(BANK, 'topics.json'), 'utf8')).topics;
  const questions = [];
  for (const t of topics) {
    const dir = path.join(BANK, t.id);
    for (const f of fs.readdirSync(dir).filter((x) => x.endsWith('.json')).sort()) {
      const q = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
      if (q.topic !== t.id) throw new Error(f + ': topic ' + q.topic + ' לא מתאים לתיקייה ' + t.id);
      if (q.id + '.json' !== f) throw new Error(f + ': id ' + q.id + ' לא מתאים לשם הקובץ');
      questions.push(q);
    }
  }
  const rank = (q) => [topics.findIndex((t) => t.id === q.topic), LEVELS.indexOf(q.level), q.order || 0];
  questions.sort((a, b) => { const x = rank(a), y = rank(b); for (let i = 0; i < 3; i++) if (x[i] !== y[i]) return x[i] - y[i]; return a.id < b.id ? -1 : 1; });
  return { topics, questions };
}

function render(data) {
  return '/* js/bank/data.js - נוצר אוטומטית מ-bank/*.json על ידי tools/bank-build.js. לא לערוך ביד. */\n' +
    '(function (root) {\n  const data = {\n    topics: ' + JSON.stringify(data.topics) + ',\n    questions: [\n' +
    data.questions.map((q) => '      ' + JSON.stringify(q)).join(',\n') + '\n    ],\n  };\n' +
    "  if (typeof module === 'object' && module.exports) module.exports = data;\n  else root.AutomataBankData = data;\n" +
    "})(typeof globalThis !== 'undefined' ? globalThis : this);\n";
}

if (require.main === module) {
  fs.writeFileSync(OUT, render(collect()));
  console.log('נכתב ' + path.relative(process.cwd(), OUT));
}
module.exports = { collect, render, OUT };
