#!/bin/sh
# בדיקה של "המורה בעברית" בתוך Eclipse אמיתי: מתקינים את התוסף, בונים פרויקט עם קוד של תלמידים,
# ובודקים את הסימונים (Problems), את ההסברים בזמן הכתיבה (reconcile) ואת הכיבוי מהתפריט; ואת "הצעה לפתרון" (Sol.java).
# לא חלק מ-run-tests.sh (צריך Eclipse — כ-380MB). להריץ אחרי tools/build-all.sh:
#   sh tools/test/eclipse-it/run.sh [תיקיית eclipse]
# צילומי המסך (SHOT=1) ו"הצעה לפתרון" רצים ב-xvfb-run; עם XD=:99 — על מסך X קיים (כמו ב-tutorials/viewer.sh)
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
xrun() { if [ -n "$XD" ]; then DISPLAY="$XD" "$@"; else xvfb-run -a -s "-screen 0 ${XSCREEN:-1700x1050x24}" "$@"; fi; }
check "BUILD Mistakes.java:5 mentor error ל-Queue אין פעולה size \[לתור אין פעולה שסופרת" "בנייה: הסבר בעברית לשגיאה (size בתור)"
check "BUILD Mistakes.java:6 mentor error חסרה נקודה-פסיק בסוף שורה 6 \[כל פקודה" "בנייה: חסרה ; — בשורה הנכונה"
check "BUILD Warnings.java:5 mentor warning שני הצדדים הם Integer" "בנייה: אזהרה על == בין Integer"
check "BUILD Warnings.java:9 mentor warning p לא מתקדם" "בנייה: אזהרה על לולאה שלא מתקדמת"
check "BUILD Warnings.java:15 mentor warning מחרוזת לא משתנה" "בנייה: אזהרה על toUpperCase שהולך לאיבוד"
check "BUILD Warnings.java:16 mentor warning השוואת מחרוזות" "בנייה: אזהרה על == במחרוזות"
check "BUILD Mixed.java:6 mentor warning התנאי מגיע עד arr.length, אבל האינדקס האחרון הוא אחד פחות \[בסיבוב האחרון תהיה שגיאה (ArrayIndexOutOfBoundsException). כותבים: i < arr.length\]" \
    "בנייה: הסימון ב-Problems — אותו טקסט בדיוק (תווי הכיווניות לא משנים אותו)"
check "RECONCILE Targil.java:6 mentor חסרה נקודה-פסיק בסוף שורה 6" "בזמן הכתיבה (reconcile): ההסבר מופיע מיד"
check "RUNCHECK Mistakes.java errors=[1-9]" "הרצה ויזואלית: יש שגיאות — נחסמת (Mistakes.java)"
check "RUNCHECK Targil.java errors=0" "הרצה ויזואלית: קוד תקין — מותר להריץ (Targil.java)"
check "LIBRARY-BEFORE false" "פרויקט Java רגיל: בהתחלה אין בו את ספריית הקורס"
check "LIBRARY-AFTER true stack=true node=true jar=true" "פרויקט Java רגיל: הספרייה נוספה — Queue / Stack / Node מזוהים"
check "LAUNCH-CP first-is-dsviz=true entries=[2-9]" "הרצה ויזואלית בכל פרויקט: הספרייה מהתוסף ראשונה, ואחריה ה-classpath של הפרויקט"
check "OFF-MENTOR-COUNT 0" "כיבוי מהתפריט: כל הסימונים נמחקים"
check "AUTOIMPORT bare=true member=false" "פעולות העזר: size(s) — ה-import נוסף לבד; s.size() — לא"
check "POWER-OFF on=false mentor=0 autoimport=false" "לחצן הכיבוי: בלי הסברים בעברית ובלי import אוטומטי"
check "POWER-ON on=true mentor=[1-9][0-9]* autoimport=true" "לחצן ההפעלה: הכול חוזר"
grep -q "BUILD Targil.java" "$OUT" && { echo "  FAIL קוד תקין קיבל סימונים"; FAILED=1; } || echo "  ok  קוד תקין (Targil.java) — בלי אף סימון"
[ "$(grep -c '^BUILD-MENTOR-COUNT 0' "$OUT")" = 0 ] && echo "  ok  יש סימונים בבנייה" || { echo "  FAIL אין סימונים בבנייה"; FAILED=1; }
[ "$(sed -n 's/^ON-AGAIN-MENTOR-COUNT //p' "$OUT")" = "$(sed -n 's/^BUILD-MENTOR-COUNT //p' "$OUT")" ] && echo "  ok  הפעלה מחדש: אותם סימונים חוזרים" \
    || { echo "  FAIL הפעלה מחדש"; FAILED=1; }
# צילומי מסך (SHOT=1, צריך xvfb-run או XD): העורך, חלון Problems וחלון "הסברים בעברית" (‎-mentor.png‎)
if [ "$SHOT" = 1 ]; then
    for f in Mistakes:5:21 Warnings:16:15 Mixed:15:20; do
        xrun java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "$ECL"/plugins/org.eclipse.equinox.launcher_*.jar -nosplash -consoleLog -data "$B/ws" \
            -application il.lamerhav.dsviz.it.shot "/Students/src/${f%%:*}.java" "$B/${f%%:*}.png" "${f#*:}" >> "$B/eclipse.log" 2>&1 || true
        [ "$f" = "Mistakes:5:21" ] && { grep -m1 "MENTOR-VIEW-OPEN" "$B/eclipse.log" | grep -q true && echo "  ok  בהפעלה הראשונה — חלון ההסברים בעברית נפתח לבד בצד" \
            || { echo "  FAIL חלון ההסברים לא נפתח לבד"; FAILED=1; }; }
        [ "$f" = "Mistakes:5:21" ] && { for k in "LAYOUT-AUTO mentor-in-outline-stack=true" "LAYOUT-BOTTOM mentor-in-outline-stack=false" "LAYOUT-REDOCKED mentor-in-outline-stack=true" "separate-row=true"; do
            grep -q "$k" "$B/eclipse.log" && echo "  ok  סידור אוטומטי: $k" || { echo "  FAIL סידור אוטומטי: $k"; FAILED=1; }; done; }
        [ "$f" = "Mistakes:5:21" ] && { for k in "FOLD public static int twice(int x) { collapsed=true" "FOLD public static void main(String\[\] args) { collapsed=true" "FOLD public static int countEven(int\[\] a) { collapsed=false"; do
            grep -q "$k" "$B/eclipse.log" && echo "  ok  כיווץ בתרגיל שנוצר: $k" || { echo "  FAIL כיווץ בתרגיל שנוצר: $k"; FAILED=1; }; done; }
        [ -f "$B/${f%%:*}.png" ] && echo "  צילום: $B/${f%%:*}.png  $B/${f%%:*}-problems.png  $B/${f%%:*}-mentor.png"
    done
    # חלון "הסברים בעברית": בכל כרטיס קודם ההתראה, אחריה שורת רווח, ורק אז ההצעה לתיקון (והקוד בשורה משלו);
    # וכל טקסט עטוף בתווי הכיווניות (RtlText) — מימין לשמאל, וקטעי הקוד משמאל לימין כיחידה אחת
    for k in "PANEL-CARD Mistakes.java:5 error: title main gap([1-9][0-9]*px) fix code$" \
             "PANEL-CARD Mixed.java:6 warning: title main gap([1-9][0-9]*px) fix code$" \
             "PANEL-CARD Mixed.java:18 warning: title main gap([1-9][0-9]*px) fix code$" \
             "PANEL-TEXT Mixed.java:18 main <RLM><RLE><RLM><LRE>isEmpty<PDF><RLM> רק בודקת ומחזירה <RLM><LRE>true<PDF><RLM> או <RLM><LRE>false<PDF><RLM>" \
             "PANEL-TEXT Mixed.java:6 fix <RLM><RLE>הצעה לתיקון: בסיבוב האחרון תהיה שגיאה <RLM><LRE>(ArrayIndexOutOfBoundsException)<PDF><RLM>. כותבים:<PDF>" \
             "PANEL-TEXT Mixed.java:6 code <LRM><LRE> i < arr.length <PDF>" \
             "PANEL-TEXT Mixed.java:27 main <RLM><RLE>אי התאמה בטיפוסים: אי אפשר להכניס <RLM><LRE>Stack<String><PDF><RLM> למשתנה מטיפוס <RLM><LRE>Stack<Integer><PDF><RLM><PDF>" \
             "PANEL-HEAD Mixed.java <RLM><RLE><RLM><LRE>1<PDF><RLM> שגיאות ו-<RLM><LRE>5<PDF><RLM> אזהרות"; do
        grep -q -- "$k" "$B/eclipse.log" && echo "  ok  חלון ההסברים: ${k%%<*}" || { echo "  FAIL חלון ההסברים: $k"; FAILED=1; }
    done
fi
# "הצעה לפתרון" (צריך xvfb-run): תרגיל מתשובה לדוגמה → ההצעות נבדקו ונשמרו בצד, הכפתור מעל כל פעולה של סעיף, לחיצה → החלון
mkdir -p "$B/sol"
XSCREEN=1600x1000x24 xrun timeout 240 java -Dfile.encoding=UTF-8 -jar "$ECL"/plugins/org.eclipse.equinox.launcher_*.jar -nosplash -clean -consoleLog \
    -data "$B/ws-sol" -application il.lamerhav.dsviz.it.solution "$ROOT/tools/test/ai/1-stack.json" "$B/sol/out.txt" "$B/sol" > "$B/sol/eclipse.log" 2>&1 || true
rm -rf "$B/ws-sol"
SOL="$B/sol/out.txt"
for k in "STATUS ok" "ERRORS \[\]" "STUDENT-CODE-HAS-SOLUTION false" "STORE solutions.json entries=2 path=true" "MINING countEven( line=6 indent=[1-9]" \
         "MINING removeAll( line=11 indent=[1-9]" "MINING void main( line=24 indent=0" "CLICK-DIALOG true" "DIALOG-RTL true code-ltr=true" "MENU-DIALOG true public static void removeAll"; do
    grep -q -- "$k" "$SOL" 2>/dev/null && echo "  ok  הצעה לפתרון: $k" || { echo "  FAIL הצעה לפתרון: $k"; FAILED=1; }
done
exit $FAILED
