#!/bin/sh
# בדיקה של "המורה בעברית" בתוך Eclipse אמיתי: מתקינים את התוסף, בונים פרויקט עם קוד של תלמידים,
# ובודקים את הסימונים (Problems), את ההסברים בזמן הכתיבה (reconcile) ואת הכיבוי מהתפריט.
# לא חלק מ-run-tests.sh (צריך Eclipse — כ-380MB). להריץ אחרי tools/build-all.sh:
#   sh tools/test/eclipse-it/run.sh [תיקיית eclipse]
set -e
cd "$(dirname "$0")"
ROOT="$(cd ../../.. && pwd)"
B="$ROOT/build/eclipse-it"
ECL="${1:-$B/eclipse}"
URL="${ECLIPSE_URL:-https://download.eclipse.org/technology/epp/downloads/release/2026-09/R/eclipse-java-2026-09-R-linux-gtk-x86_64.tar.gz}"
mkdir -p "$B"
if [ ! -d "$ECL/plugins" ]; then
    echo "מוריד Eclipse..."
    curl -sSL -o "$B/eclipse.tar.gz" "$URL" && tar xzf "$B/eclipse.tar.gz" -C "$B" && rm "$B/eclipse.tar.gz"
fi
rm -rf "$B/it" "$B/ws" && mkdir -p "$B/it/cls"
javac ${JAVAC_FLAGS:---release 11} -encoding UTF-8 -nowarn -cp "$(ls "$ECL"/plugins/*.jar | tr '\n' ':')$(ls "$ROOT"/release/il.lamerhav.dsviz_*.jar)" -d "$B/it/cls" src/il/lamerhav/dsviz/it/*.java
cp plugin.xml "$B/it/cls/"
jar --create --file "$B/it/il.lamerhav.dsviz.it_1.0.0.jar" --manifest META-INF/MANIFEST.MF -C "$B/it/cls" .
rm -f "$ECL"/dropins/il.lamerhav.dsviz*.jar
cp "$B/it/il.lamerhav.dsviz.it_1.0.0.jar" "$ROOT"/release/il.lamerhav.dsviz_*.jar "$ECL/dropins/"
java -Dfile.encoding=UTF-8 -jar "$ECL"/plugins/org.eclipse.equinox.launcher_*.jar -nosplash -clean -consoleLog -data "$B/ws" \
    -application il.lamerhav.dsviz.it.app "$ROOT/release/dsviz.jar" "$(pwd)/cases" "$B/out.txt" > "$B/eclipse.log" 2>&1 || true
OUT="$B/out.txt"
[ -f "$OUT" ] || { echo "  FAIL Eclipse לא הריץ את הבדיקה"; tail -40 "$B/eclipse.log"; exit 1; }
cat "$OUT"
check() { grep -q -- "$1" "$OUT" && echo "  ok  $2" || { echo "  FAIL $2  ($1)"; FAILED=1; }; }
FAILED=0
check "BUILD Mistakes.java:5 mentor error ל-Queue אין פעולה size \[לתור אין פעולה שסופרת" "בנייה: הסבר בעברית לשגיאה (size בתור)"
check "BUILD Mistakes.java:6 mentor error חסרה נקודה-פסיק בסוף שורה 6 \[כל פקודה" "בנייה: חסרה ; — בשורה הנכונה"
check "BUILD Warnings.java:5 mentor warning שני הצדדים הם Integer" "בנייה: אזהרה על == בין Integer"
check "BUILD Warnings.java:9 mentor warning p לא מתקדם" "בנייה: אזהרה על לולאה שלא מתקדמת"
check "BUILD Warnings.java:15 mentor warning מחרוזת לא משתנה" "בנייה: אזהרה על toUpperCase שהולך לאיבוד"
check "BUILD Warnings.java:16 mentor warning השוואת מחרוזות" "בנייה: אזהרה על == במחרוזות"
check "RECONCILE Targil.java:6 mentor חסרה נקודה-פסיק בסוף שורה 6" "בזמן הכתיבה (reconcile): ההסבר מופיע מיד"
check "RUNCHECK Mistakes.java errors=[1-9]" "הרצה ויזואלית: יש שגיאות — נחסמת (Mistakes.java)"
check "RUNCHECK Targil.java errors=0" "הרצה ויזואלית: קוד תקין — מותר להריץ (Targil.java)"
check "LIBRARY-BEFORE false" "פרויקט Java רגיל: בהתחלה אין בו את ספריית הקורס"
check "LIBRARY-AFTER true stack=true node=true jar=true" "פרויקט Java רגיל: הספרייה נוספה — Queue / Stack / Node מזוהים"
check "OFF-MENTOR-COUNT 0" "כיבוי מהתפריט: כל הסימונים נמחקים"
grep -q "BUILD Targil.java" "$OUT" && { echo "  FAIL קוד תקין קיבל סימונים"; FAILED=1; } || echo "  ok  קוד תקין (Targil.java) — בלי אף סימון"
[ "$(grep -c '^BUILD-MENTOR-COUNT 0' "$OUT")" = 0 ] && echo "  ok  יש סימונים בבנייה" || { echo "  FAIL אין סימונים בבנייה"; FAILED=1; }
[ "$(sed -n 's/^ON-AGAIN-MENTOR-COUNT //p' "$OUT")" = "$(sed -n 's/^BUILD-MENTOR-COUNT //p' "$OUT")" ] && echo "  ok  הפעלה מחדש: אותם סימונים חוזרים" \
    || { echo "  FAIL הפעלה מחדש"; FAILED=1; }
# צילומי מסך (SHOT=1, צריך xvfb-run): העורך וחלון Problems עם ההסברים בעברית
if [ "$SHOT" = 1 ]; then
    for f in Mistakes:5:21 Warnings:16:15; do
        xvfb-run -a -s "-screen 0 1700x1050x24" java -Dfile.encoding=UTF-8 -jar "$ECL"/plugins/org.eclipse.equinox.launcher_*.jar -nosplash -consoleLog -data "$B/ws" \
            -application il.lamerhav.dsviz.it.shot "/Students/src/${f%%:*}.java" "$B/${f%%:*}.png" "${f#*:}" >> "$B/eclipse.log" 2>&1 || true
        [ "$f" = "Mistakes:5:21" ] && { grep -m1 "MENTOR-VIEW-OPEN" "$B/eclipse.log" | grep -q true && echo "  ok  בהפעלה הראשונה — חלון ההסברים בעברית נפתח לבד בצד" \
            || { echo "  FAIL חלון ההסברים לא נפתח לבד"; FAILED=1; }; }
        [ "$f" = "Mistakes:5:21" ] && { for k in "LAYOUT-AUTO mentor-in-outline-stack=true" "LAYOUT-BOTTOM mentor-in-outline-stack=false" "LAYOUT-REDOCKED mentor-in-outline-stack=true" "separate-row=true"; do
            grep -q "$k" "$B/eclipse.log" && echo "  ok  סידור אוטומטי: $k" || { echo "  FAIL סידור אוטומטי: $k"; FAILED=1; }; done; }
        [ "$f" = "Mistakes:5:21" ] && { for k in "FOLD public static int twice(int x) { collapsed=true" "FOLD public static void main(String\[\] args) { collapsed=true" "FOLD public static int countEven(int\[\] a) { collapsed=false"; do
            grep -q "$k" "$B/eclipse.log" && echo "  ok  כיווץ בתרגיל שנוצר: $k" || { echo "  FAIL כיווץ בתרגיל שנוצר: $k"; FAILED=1; }; done; }
        [ -f "$B/${f%%:*}.png" ] && echo "  צילום: $B/${f%%:*}.png  $B/${f%%:*}-problems.png"
    done
fi
exit $FAILED
