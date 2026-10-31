// "חלון" מדומה לבדיקת ההרצה החיה של C# בלי מסך (DSVIZ_JAVA מצביע על עטיפה שמריצה אותו במקום Java):
// שומר את כל מה ש-dsviz-cs שולח לחלון (FAKE_OUT), ועל כל ASK "מקליד" את התשובה הבאה מ-FAKE_ANSWERS (מופרדות ב-|;
// EOF = "סיום הקלט"). בסוף (DONE) — יוצא, כמו חלון שנסגר.
const fs = require('fs');
const readline = require('readline');
const out = fs.createWriteStream(process.env.FAKE_OUT);
const answers = (process.env.FAKE_ANSWERS || '').split('|');
const rl = readline.createInterface({ input: process.stdin });
rl.on('line', (line) => {
    out.write(line + '\n');
    const f = line.split('\t');
    if (f[0] === 'ASK') {
        const a = answers.shift();
        if (a === undefined) return;
        process.stdout.write(a === 'EOF' ? 'DSVIZ-EOF\n' : 'DSVIZ-IN\t' + a + '\n');
    } else if (f[0] === 'DONE') {
        out.end(() => process.exit(0));
    }
});
rl.on('close', () => out.end());
