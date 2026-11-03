#!/bin/sh
# מקרי קצה של תלמידים בתוך Eclipse אמיתי (Edge.java): הרצה ויזואלית על כל מה שתלמיד יכול לכתוב, "שליחת תרגיל" מול שרת מדומה
# עם תשובות שבורות, "הוסף מבנה נתונים" במקומות מוזרים, תרגיל חדש / דוגמאות / הדרכה / כיבוי, "הצעה לפתרון" והמורה בעברית.
# כל מקרה: בלי חלון שגיאה של Eclipse, בלי שגיאה ב-Error Log, בלי תקיעה, הודעה קצרה בעברית מימין לשמאל, ותוצאה נכונה.
# להריץ אחרי בניית הספרייה והתוסף (tools/build-all.sh, או רק החלקים של הספרייה ו-Eclipse). צריך xvfb-run, xdotool ו-node.
#   sh tools/test/eclipse-it/edge.sh [תיקיית eclipse]          — הכול (כ-25 דקות)
#   EDGE_ONLY=AC sh tools/test/eclipse-it/edge.sh              — רק חלק מהקבוצות (A הרצה, B שליחה, C הוספה, D חלונות וכיבוי,
#                                                                E הצעה לפתרון, F המורה בעברית, G הרצה עם חלון)
# צילומי המסך: EDGE_SHOTS=<תיקייה> (ברירת מחדל: build/eclipse-it/edge). run.sh מריץ את זה בסוף (EDGE=0 — מדלגים)
set -e
cd "$(dirname "$0")"
ROOT="$(cd ../../.. && pwd)"
B="$ROOT/build/eclipse-it"
ECL="${1:-$B/eclipse}"
[ -d "$ECL/plugins" ] || { echo "  FAIL אין Eclipse ב-$ECL (run.sh מוריד אותו)"; exit 1; }
E="$B/edge"
rm -rf "$E" && mkdir -p "$E/cls" "$E/dropins"
SHOTS="${EDGE_SHOTS:-$E}"
mkdir -p "$SHOTS"
javac ${JAVAC_FLAGS:---release 11} -encoding UTF-8 -nowarn -cp "$(ls "$ECL"/plugins/*.jar | tr '\n' ':')$(ls "$ROOT"/release/il.lamerhav.dsviz_*.jar)" -d "$E/cls" src/il/lamerhav/dsviz/it/*.java
cp plugin.xml "$E/cls/"
jar --create --file "$E/dropins/il.lamerhav.dsviz.it_1.0.0.jar" --manifest META-INF/MANIFEST.MF -C "$E/cls" .
cp "$ROOT"/release/il.lamerhav.dsviz_*.jar "$E/dropins/"
# כמו run.sh: התוספים בתיקיית dropins של Eclipse (הגרסה הקודמת שלהם נמחקת)
rm -f "$ECL"/dropins/il.lamerhav.dsviz*.jar
cp "$E"/dropins/*.jar "$ECL/dropins/"
PORT="${EDGE_PORT:-8823}"
MOCK_DELAY_MS=1500 node "$ROOT/tools/test/submit/mock-worker.js" "$PORT" "$(pwd)/submit" "$E/mock.log" & MOCKP=$!
LC_ALL=C.UTF-8 xvfb-run -a -s "-screen 0 1600x1000x24" timeout "${EDGE_TIMEOUT:-2400}" java -Dfile.encoding=UTF-8 -Ddsviz.worker="http://127.0.0.1:$PORT" \
    -Dedge.mocklog="$E/mock.log" -Dedge.only="$EDGE_ONLY" \
    -jar "$ECL"/plugins/org.eclipse.equinox.launcher_*.jar -nosplash -clean -consoleLog -data "$E/ws" \
    -application il.lamerhav.dsviz.it.edge "$E/out.txt" "$SHOTS" "$ROOT/tools/test/ai/files" > "$E/eclipse.log" 2>&1 || true
kill $MOCKP 2>/dev/null || true
pkill -f "dsviz.child=true" 2>/dev/null || true
rm -rf "$E/ws" "$E/cls"
OUT="$E/out.txt"
[ -f "$OUT" ] || { echo "  FAIL Eclipse לא הריץ את בדיקות הקצה"; tail -40 "$E/eclipse.log"; exit 1; }
grep '^ok \|^FAIL\|^SUMMARY' "$OUT" | sed 's/^/  /'
grep -q '^DONE' "$OUT" || { echo "  FAIL בדיקות הקצה לא הגיעו לסוף (תקיעה?)"; tail -20 "$E/eclipse.log"; exit 1; }
echo "  צילומים: $SHOTS"
! grep -q '^FAIL' "$OUT"
