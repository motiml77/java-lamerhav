#!/bin/sh
# בדיקה מקצה לקצה של "שליחת תרגיל" עם השרת האמיתי (worker/) שרץ מקומית ב-wrangler dev, ו-Gemini מדומה:
#   החלון של Eclipse (Ctrl+V אמיתי, קבצים) → multipart → Worker → "Gemini" → JSON → בניית המעטפת; וסבב תיקון.
# צריך: npm i wrangler@4 (בתיקייה כלשהי) ו-WRANGLER=<הנתיב ל-wrangler>; וצריך build-all קודם.
set -e
cd "$(dirname "$0")"
T=$(pwd); ROOT=$(cd ../.. && pwd); W=$ROOT/build/test/e2e
rm -rf "$W" && mkdir -p "$W"
WRANGLER=${WRANGLER:-wrangler}
# Gemini מדומה: מחזיר את התרגיל מהקובץ MOCK_FILE
cat > "$W/mock.mjs" <<'JS'
import http from 'node:http'; import fs from 'node:fs';
http.createServer((req, res) => { const c = []; req.on('data', d => c.push(d)); req.on('end', () => {
  const j = JSON.parse(Buffer.concat(c).toString('utf8'));
  fs.appendFileSync(process.env.LOG, j.contents[0].parts.map(p => p.inlineData ? p.inlineData.mimeType : 'text').join(',') + '\n');
  const ex = Object.assign({ isExercise: true, problem: '' }, JSON.parse(fs.readFileSync(process.env.MOCK_FILE, 'utf8')));
  res.writeHead(200, { 'content-type': 'application/json' });
  res.end(JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify(ex) }] } }] }));
}); }).listen(+process.env.PORT);
JS
mkdir -p "$W/worker" && cp -r "$ROOT/worker/src" "$W/worker/" && sed 's|^MODELS = .*|&\nGEMINI_BASE = "http://127.0.0.1:8798"|' "$ROOT/worker/wrangler.toml" > "$W/worker/wrangler.toml"
printf 'GEMINI_API_KEY=test\n' > "$W/worker/.dev.vars"
LOG=$W/gemini.log PORT=8798 MOCK_FILE=$T/ai/2-classes.json node "$W/mock.mjs" & MOCK=$!
(cd "$W/worker" && $WRANGLER dev --port 8797 --ip 127.0.0.1 > "$W/wrangler.log" 2>&1) & WR=$!
trap 'kill $MOCK $WR 2>/dev/null; true' EXIT
i=0; until grep -q "Ready on" "$W/wrangler.log" 2>/dev/null; do i=$((i+1)); [ $i -gt 60 ] && { cat "$W/wrangler.log"; exit 1; }; sleep 1; done
python3 -c "
from PIL import Image, ImageDraw
im = Image.new('RGB', (900, 500), 'white'); ImageDraw.Draw(im).rectangle([30, 30, 870, 470], outline='black', width=4); im.save('$W/page.png')"
cat > "$W/script.txt" <<S
addfile $T/ai/files/targil.docx
pasteimg $W/page.png
pasteimg $W/page.png
text 0 רק שאלה 2
click שליחה ויצירת התרגיל
wait 4000
S
DSVIZ_WORKER_URL=http://127.0.0.1:8797 sh "$ROOT/tutorials/dlg.sh" submit x "$W/script.txt" "$W/out" | grep -v Gtk-CRITICAL
test -f "$W/out/exercise.json" || { echo "FAIL: לא התקבל תרגיל"; exit 1; }
grep -q "text,text,image/png,text,image/png,text,image/png" "$W/gemini.log" && echo "  ok  נשלחו ל-Gemini: טקסט ה-Word + התמונה שבו + 2 תמונות מודבקות (Ctrl+V)" || { cat "$W/gemini.log"; exit 1; }
# סבב תיקון: Gemini מדומה מחזיר גרסה מתוקנת
kill $MOCK; sleep 1
LOG=$W/gemini.log PORT=8798 MOCK_FILE=$T/ai/repair/fixed.json node "$W/mock.mjs" & MOCK=$!
sleep 1
mkdir -p "$W/rp/cls"
javac -encoding UTF-8 -d "$W/rp/cls" $ROOT/eclipse-plugin/src/il/lamerhav/dsviz/Json.java $ROOT/eclipse-plugin/src/il/lamerhav/dsviz/AiScaffold.java \
    $ROOT/eclipse-plugin/src/il/lamerhav/dsviz/WorkerClient.java $ROOT/eclipse-plugin/src/il/lamerhav/dsviz/ExerciseFiles.java AiRepairTest.java
java -Dstdout.encoding=UTF-8 -cp "$W/rp/cls" il.lamerhav.dsviz.AiRepairTest ai/repair/broken.json http://127.0.0.1:8797 "$W/rp" "$ROOT/release/dsviz.jar"
echo "e2e: עבר"
