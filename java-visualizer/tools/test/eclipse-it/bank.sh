#!/bin/sh
# "מאגר תרגילים" (ההרחבה il.lamerhav.dsviz.bank) בתוך Eclipse אמיתי — מתקינים את התוסף + ההרחבה ב-dropins כמו המורה,
# ובודקים: ההרחבה נטענת, הכפתור בסרגל, החלון (נושאים, נקודות קושי, V, נושא ריק), יצירת תרגיל משאלה (קבצים, "כאן עונים",
# הפלט הרצוי מעל main, הצעות לפתרון), "פתח את התרגיל", וחלון הצד "השאלה" (+ "סיימתי"). צילומי מסך: build/eclipse-it/bank/
#   sh tools/test/eclipse-it/bank.sh [תיקיית eclipse]     (אחרי tools/build-all.sh; צריך xvfb-run או XD=:99)
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
sh "$ROOT/tools/build-bank.sh" > "$B/bank-build.log" 2>&1 || { cat "$B/bank-build.log"; exit 1; }
FRAG=$(ls "$ROOT"/build/lab/il.lamerhav.dsviz.bank_*.jar)
rm -rf "$B/it" "$B/ws-bank" "$B/bank" && mkdir -p "$B/it/cls" "$B/bank"
javac ${JAVAC_FLAGS:---release 11} -encoding UTF-8 -nowarn -cp "$(ls "$ECL"/plugins/*.jar | tr '\n' ':')$(ls "$ROOT"/release/il.lamerhav.dsviz_*.jar)" -d "$B/it/cls" src/il/lamerhav/dsviz/it/*.java
cp plugin.xml "$B/it/cls/"
# תוסף הבדיקה — דרך p2 (מאגר מקומי + director), עם גרסה חדשה בכל הרצה: מ-dropins ה-reconciler לא תמיד מתקין אותו.
# התוסף וההרחבה עצמם — ב-dropins, בדיוק כמו אצל המורה
ITV="1.0.0.v$(date +%s)"
sed "s/^Bundle-Version: .*/Bundle-Version: $ITV/" META-INF/MANIFEST.MF > "$B/it/MANIFEST.MF"
REPO="$B/it/repo-$ITV"   # מאגר חדש בכל הרצה — p2 זוכר מאגרים לפי הכתובת
mkdir -p "$REPO/plugins"
jar --create --file "$REPO/plugins/il.lamerhav.dsviz.it_$ITV.jar" --manifest "$B/it/MANIFEST.MF" -C "$B/it/cls" .
# ה-Java שבתוך Eclipse (JustJ) — גרסאות חדשות של Eclipse דורשות Java חדש מזה שמותקן במחשב
JAVA=$(ls "$ECL"/plugins/org.eclipse.justj.openjdk.hotspot.jre.full.*/jre/bin/java 2>/dev/null | head -1)
[ -n "$JAVA" ] || JAVA=java
LAUNCH=$(ls "$ECL"/plugins/org.eclipse.equinox.launcher_*.jar)
rm -f "$ECL"/dropins/il.lamerhav.dsviz*.jar
cp "$ROOT"/release/il.lamerhav.dsviz_*.jar "$FRAG" "$ECL/dropins/"
"$JAVA" -jar "$LAUNCH" -nosplash -consoleLog -application org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher \
    -metadataRepository "file:$REPO" -artifactRepository "file:$REPO" -source "$REPO" -publishArtifacts > "$B/bank-p2.log" 2>&1
"$JAVA" -jar "$LAUNCH" -nosplash -consoleLog -application org.eclipse.equinox.p2.director -uninstallIU il.lamerhav.dsviz.it >> "$B/bank-p2.log" 2>&1 || true
"$JAVA" -jar "$LAUNCH" -nosplash -consoleLog -application org.eclipse.equinox.p2.director -repository "file:$REPO" -installIU il.lamerhav.dsviz.it >> "$B/bank-p2.log" 2>&1 \
    || { echo "  FAIL התקנת תוסף הבדיקה"; tail -20 "$B/bank-p2.log"; exit 1; }
xrun() { if [ -n "$XD" ]; then DISPLAY="$XD" "$@"; else xvfb-run -a -s "-screen 0 ${XSCREEN:-1600x1000x24}" "$@"; fi; }
OUT="$B/bank/out.txt"
xrun "$JAVA" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Ddsviz.bankProgress="$B/bank/progress.json" -Ddsviz.debugLayout=true -jar "$LAUNCH" \
    -nosplash -clean -consoleLog -data "$B/ws-bank" -application il.lamerhav.dsviz.it.bank "$OUT" "$B/bank" > "$B/bank/eclipse.log" 2>&1 || true
rm -f "$ECL"/dropins/il.lamerhav.dsviz.bank_*.jar   # שאר הבדיקות (run.sh) — בלי ההרחבה
"$JAVA" -jar "$LAUNCH" -nosplash -consoleLog -application org.eclipse.equinox.p2.director -uninstallIU il.lamerhav.dsviz.it >> "$B/bank-p2.log" 2>&1 || true
[ -f "$OUT" ] || { echo "  FAIL Eclipse לא הריץ את הבדיקה"; tail -40 "$B/bank/eclipse.log"; exit 1; }
FAILED=0
check() { grep -q -- "$1" "$OUT" && echo "  ok  $2" || { echo "  FAIL $2  ($1)"; FAILED=1; }; }
check "^FRAGMENT found=true resolved=true" "ההרחבה נטענה ליד התוסף (dropins)"
check "^COMMAND defined=true handled=true" "הפקודה \"מאגר תרגילים\" קיימת ויש לה מטפל"
check "^TOOLBAR .*מאגר תרגילים" "הכפתור \"מאגר תרגילים\" בסרגל של DSViz"
check "^QUESTION sample-loops-digits|loops|easy" "השאלות נטענו (לולאות — קל)"
check "^QUESTION sample-stack-even|stack|medium" "השאלות נטענו (מחסנית — בינוני)"
[ "$(grep -c '^QUESTION ' "$OUT")" = "$(find "$ROOT/bank" -mindepth 2 -name '*.json' | wc -l)" ] && echo "  ok  כל השאלות במאגר נטענו ($(grep -c '^QUESTION ' "$OUT"))" || { echo "  FAIL לא כל השאלות נטענו"; FAILED=1; }
check "^TOPICS לולאות | מערכים | מחרוזות | מחלקות | רקורסיה | מחסנית | תור | שרשרת חוליות | עץ בינארי | $" "הנושאים: לולאות, מערכים, מחרוזות, מחלקות, רקורסיה, מחסנית, תור, שרשרת חוליות, עץ בינארי"
check "^DIALOG title=מאגר תרגילים rtl=true" "החלון: מימין לשמאל"
check "^DIALOG-LABELS .*נושאים.*השאלות בנושא מחסנית" "החלון: נושאים ושאלות הנושא"
check "^DIALOG-BUTTONS צור תרגיל | שאלה אקראית | סגירה" "החלון: צור תרגיל / שאלה אקראית / סגירה (בלי עותק — עוד לא נוצר)"
check "^DIALOG-PREVIEW .*TEXT סעיף א'.*TEXT סעיף ב'.*TEXT כשהכול פתור נכון, main מדפיס" "החלון: נוסח השאלה — כרטיס לכל סעיף, והפלט הרצוי"
check "^DIALOG-PREVIEW .*FIGURE stack \[\[9\], \[2\], \[7\], \[4\]\] \"המחסנית s (4 בראש)" "ציור: מחסנית (4 בראש — נכנס אחרון)"
check "^DIALOG-PREVIEW .*FIGURE stack \[\[3\], \[5\], \[3\]\] \"לפני.*FIGURE stack \[\[5\]\] \"אחרי\"" "ציור: לפני ואחרי בסעיף ב"
check "^PREVIEW-TREE .*FIGURE tree \[\[8\], \[3\], \[10\], \[1\], \[6\], null, \[14\]\] \"העץ t\"" "ציור: עץ בינארי (לפי רמות, עם מקום ריק)"
check "^PREVIEW-QUEUE .*FIGURE queue \[\[3\], \[8\], \[1\], \[6\]\]" "ציור: תור"
check "^PREVIEW-LIST .*FIGURE list \[\[5\], \[12\], \[7\], \[20\]\]" "ציור: שרשרת חוליות"
check "^DONE-LOOPS true" "V ירוק נשמר"
check "^EMPTY-TOPIC true" "נושא בלי שאלות — הודעה \"עוד אין שאלות\""
check "^CREATED \[/Students/src/ex01_stacks/StackTargil.java\]" "התרגיל נוצר בחבילה משלו (ex01_stacks) בלי שגיאות קומפילציה"
check "^FILE .*StackTargil.java answer-marks=2 expected-comment=true todo-free=true" "בקובץ: \"כאן עונים\" לכל סעיף, הפלט הרצוי מעל main, ובלי הפתרון"
check "^SOLUTIONS .*StackTargil.countEven א ok, StackTargil.removeAll ב ok" "💡 הצעה לפתרון לכל סעיף — נשמרה ונבדקה"
check "^ACTIVE-EDITOR StackTargil.java" "\"פתח את התרגיל\" פותח את אותו קובץ"
check "^VIEW sample-stack-even" "חלון הצד \"השאלה\" מציג את השאלה של התרגיל"
check "^VIEW-WIDTH [3-9][0-9][0-9]$" "חלון הצד רחב מספיק לקריאה (הורחב לבד מהצד הצר של Java)"
check "^LEFT-WIDTH 2[0-4][0-9]$" "העמודה משמאל (Package Explorer) הוצרה — יותר מקום לקוד ולשאלה"
check "^VIEW-SHOWN SECTION-BUTTONS א countEven" "חלון הצד: לסעיף א — \"לפעולה\" ו-💡"
check "^VIEW-SHOWN EXPECTED מחסנית: \[4,7,2,9\] | סעיף א: 2" "חלון הצד: הפלט הרצוי"
check "^VIEW-SHOWN DONE false" "חלון הצד: \"סיימתי\" (עוד לא סומן)"
check "^VIEW-AFTER-DONE true" "סימון V — מתעדכן בחלון הצד"
check "^DIALOG2-BUTTONS פתח את התרגיל | צור עותק חדש | שאלה אקראית | סגירה" "שאלה שכבר נוצרה: \"פתח את התרגיל\" + \"צור עותק חדש\""
check "^DIALOG2-LABELS .*כבר יצרת את התרגיל הזה (ex01_stacks)" "שאלה שכבר נוצרה: הודעה איפה התרגיל"
grep -q "Exception" "$B/bank/eclipse.log" && grep -q "il.lamerhav.dsviz.Bank" "$B/bank/eclipse.log" && { echo "  FAIL חריגה בקוד של המאגר:"; grep -n -A6 "Exception" "$B/bank/eclipse.log" | grep -B2 -A6 "Bank" | head -30; FAILED=1; } || echo "  ok  בלי חריגות של המאגר ביומן"
echo "  צילומים: $(ls "$B"/bank/*.png | tr '\n' ' ')"
[ $FAILED = 0 ]
