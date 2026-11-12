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
# שאלה בתיקייה המקומית (כמו ~/.dsviz/bank) — נוספת למאגר בלי בנייה; עם ציור ארוך (רואים את כולו, בלי גלילה הצידה)
mkdir -p "$B/bank-local/list"
python3 - "$ROOT/bank/list/list-length-sum.json" "$B/bank-local/list/test-long-list.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1], encoding='utf-8'))
d['id'] = 'test-long-list'
d['title'] = 'שאלה מהתיקייה המקומית'
d['question']['sections'][0].setdefault('figures', []).append({"type": "list", "values": [5, 12, 7, 20, 3, 18, 9, 14, 2, 30, 11, 6], "caption": "שרשרת ארוכה"})
open(sys.argv[2], 'w', encoding='utf-8').write(json.dumps(d, ensure_ascii=False))
PY
OUT="$B/bank/out.txt"
xrun "$JAVA" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Ddsviz.bankProgress="$B/bank/progress.json" -Ddsviz.bank="$B/bank-local" -Ddsviz.debugLayout=true -jar "$LAUNCH" \
    -nosplash -clean -consoleLog -data "$B/ws-bank" -application il.lamerhav.dsviz.it.bank "$OUT" "$B/bank" > "$B/bank/eclipse.log" 2>&1 || true
rm -f "$ECL"/dropins/il.lamerhav.dsviz.bank_*.jar   # שאר הבדיקות (run.sh) — בלי ההרחבה
"$JAVA" -jar "$LAUNCH" -nosplash -consoleLog -application org.eclipse.equinox.p2.director -uninstallIU il.lamerhav.dsviz.it >> "$B/bank-p2.log" 2>&1 || true
[ -f "$OUT" ] || { echo "  FAIL Eclipse לא הריץ את הבדיקה"; tail -40 "$B/bank/eclipse.log"; exit 1; }
FAILED=0
check() { grep -q -- "$1" "$OUT" && echo "  ok  $2" || { echo "  FAIL $2  ($1)"; FAILED=1; }; }
check "^FRAGMENT found=true resolved=true" "ההרחבה נטענה ליד התוסף (dropins)"
check "^COMMAND defined=true handled=true" "הפקודה \"מאגר תרגילים\" קיימת ויש לה מטפל"
check "^TOOLBAR .*מאגר תרגילים" "הכפתור \"מאגר תרגילים\" בסרגל של DSViz"
check "^QUESTION loops-sum-factorial|loops|easy" "השאלות נטענו (לולאות — קל)"
check "^QUESTION stack-sum-size|stack|easy" "השאלות נטענו (מחסנית — קל)"
check "^QUESTION stack-split-sorted|stack|medium" "השאלות הבינוניות נטענו (מחסנית — בינוני)"
check "^QUESTION tree-path-sum|tree|medium" "השאלות הבינוניות נטענו (עץ בינארי — בינוני)"
check "^MEDIUM stack-split-sorted files=1 marks=1 checks=2 helper=true expected=true" "שאלה בינונית ב-Eclipse (מחסנית): קובץ, מקום תשובה לסעיף א, check נתון, ו-main עם 2 הבדיקות של סעיף א"
check "^MEDIUM classes-fraction files=2 marks=1 checks=3 helper=true expected=true" "שאלה בינונית ב-Eclipse (מחלקה Fraction עם toString): שני קבצים, ו-main עם 3 הבדיקות של סעיף א"
check "^MEDIUM tree-equal-mirror files=1 marks=1 checks=4 helper=true expected=true" "שאלה בינונית ב-Eclipse (עץ בינארי): קובץ, ו-main עם 4 הבדיקות של סעיף א"
[ "$(grep -c '^QUESTION ' "$OUT")" = "$(( $(find "$ROOT/bank" -mindepth 2 -name '*.json' | wc -l) + 1 ))" ] && echo "  ok  כל השאלות במאגר נטענו ($(grep -c '^QUESTION ' "$OUT"))" || { echo "  FAIL לא כל השאלות נטענו"; FAILED=1; }
check "^TOPICS לולאות | מערכים | מחרוזות | מחלקות | רקורסיה | מחסנית | תור | שרשרת חוליות | עץ בינארי | $" "הנושאים: לולאות, מערכים, מחרוזות, מחלקות, רקורסיה, מחסנית, תור, שרשרת חוליות, עץ בינארי"
check "^DIALOG title=מאגר תרגילים rtl=true" "החלון: מימין לשמאל"
check "^TREE TOPIC loops closed 0/6 | TOPIC arrays closed 0/6 | TOPIC strings closed 0/6 | TOPIC classes closed 0/6 | TOPIC recursion closed 0/6 | TOPIC stack open 0/6 | Q stack-copy-greater easy | Q stack-max-contains easy | Q stack-sum-size easy | Q stack-brackets medium | Q stack-remove-bottom medium | Q stack-split-sorted medium | TOPIC queue closed 0/6 | TOPIC list closed 0/7 | TOPIC tree closed 0/6" "סרגל הצד: עץ נושאים — הכול סגור חוץ מהנושא של השאלה (פתוח), עם כמה V מתוך כמה; בכל נושא מהקל לבינוני"
check "^SEARCH-QUEUE TOPIC queue open 0/6 | Q queue-copy-contains easy | Q queue-min-last easy | Q queue-sum-even easy | Q queue-last-min medium | Q queue-merge-sorted medium | Q queue-remove-reverse medium$" "חיפוש: \"תור\" — רק הנושא והשאלות שמתאימים"
check "^FILTER-HARD $" "סינון לפי קושי \"קשה\": עוד אין שאלות קשות — רשימה ריקה"
check "^DIALOG-BUTTONS צור תרגיל | שאלה אקראית | סגירה" "החלון: צור תרגיל / שאלה אקראית / סגירה (בלי עותק — עוד לא נוצר)"
check "^DIALOG-PREVIEW .*TEXT סעיף א'.*TEXT סעיף ב'.*TEXT כשהכול פתור נכון, התוכנית מדפיסה" "החלון: נוסח השאלה — כרטיס לכל סעיף, והפלט הרצוי"
check "^DIALOG-PREVIEW .*FIGURE stack \[\[9\], \[2\], \[7\], \[4\]\] \"המחסנית s (4 בראש)" "ציור: מחסנית (4 בראש — נכנס אחרון)"
check "^PREVIEW-TREE .*FIGURE tree \[\[8\], \[3\], \[10\], \[1\], \[6\], null, \[14\]\] \"העץ t\"" "ציור: עץ בינארי (לפי רמות, עם מקום ריק)"
check "^PREVIEW-QUEUE .*FIGURE queue \[\[3\], \[8\], \[1\], \[6\]\]" "ציור: תור"
check "^PREVIEW-LIST .*FIGURE list \[\[6\], \[2\], \[9\], \[3\]\]" "ציור: שרשרת חוליות"
check "^DONE-LOOPS true" "V ירוק נשמר"
check "^EMPTY-TOPIC true" "חיפוש בלי תוצאות — הודעה \"לא נמצאו שאלות\""
check "^CREATED \[/Students/src/ex01_stacks/StackTargil1.java\]" "התרגיל נוצר בחבילה משלו (ex01_stacks) בלי שגיאות קומפילציה"
check "^FILE .*StackTargil1.java answer-marks=1 expected-comment=true todo-free=true" "בקובץ אחרי היצירה: רק סעיף א (\"כאן עונים סעיף א'\"), הפלט הרצוי שלו מעל main, ובלי הפתרון"
check "^SOLUTIONS .*StackTargil1.sumStack א ok\]" "💡 הצעה לפתרון לסעיף א — נשמרה ונבדקה (סעיף ב עוד לא בקוד)"
check "^ACTIVE-EDITOR StackTargil1.java" "\"פתח את התרגיל\" פותח את אותו קובץ"
check "^FLOAT open=true question=stack-sum-size rtl=true title=השאלה — סכום וגודל של מחסנית" "החלון הצף \"השאלה\" נפתח עם השאלה של התרגיל"
check "^FLOAT-MIN true" "בחלון הצף יש כפתור מזעור"
check "^FLOAT-SHOWN SECTION-BUTTONS א sumStack" "בחלון הצף: לסעיף א — \"לפעולה\" ו-💡"
check "^FLOAT-SHOWN FIGURE stack" "בחלון הצף: הציור של המחסנית"
check "^FLOAT-SHOWN EXPECTED סעיף א: 22$" "בחלון הצף: הפלט הרצוי של הסעיפים שכבר בקוד (סעיף א)"
check "^FLOAT-SHOWN PREPARE-BUTTON ב sizeOf" "בחלון הצף: לסעיף ב — \"הכן סעיף ב'\""
check "^PREPARED-BEFORE 1" "לפני: סעיף אחד בקוד"
check "^PREPARE ok" "\"הכן סעיף ב\" — בלי שגיאות (הסמן היה בתחילת הקובץ)"
check "^PREPARED-AFTER 2" "אחרי: שני הסעיפים בקוד"
check "^FILE2 .*StackTargil1.java answer-marks=2 second-method=true method-before-main=true main-has-b=true expected-b=true" "בקובץ: הפעולה של סעיף ב לפני main, הבדיקות שלו ב-main והשורה שלו בפלט הרצוי"
check "^SOLUTIONS2 .*StackTargil1.sumStack א ok, StackTargil1.sizeOf ב ok" "💡 הצעה לפתרון גם לסעיף ב"
check "^ACTIVE-LINE dirty=false prev=// ====.*כאן עונים סעיף ב" "העורך נשמר, והסמן בשורה שמתחת ל\"כאן עונים סעיף ב'\""
check "^PROBLEMS2 0" "אחרי ההוספה: אין שגיאות קומפילציה (חוץ מ-return בפעולות הריקות)"
check "^FLOAT2-SHOWN EXPECTED סעיף א: 22 | סעיף ב: 4 | המחסנית אחרי: \[4,7,2,9\]" "בחלון הצף אחרי ההוספה: כל הפלט הרצוי"
check "^FLOAT2-SHOWN SECTION-BUTTONS ב sizeOf" "בחלון הצף אחרי ההוספה: לסעיף ב — \"לפעולה\" ו-💡"
check "^FLOAT-SHOWN DONE false" "בחלון הצף: \"סיימתי\""
check "^FLOAT-REOPEN question=stack-sum-size" "סוגרים — ופותחים שוב בכפתור \"השאלה\" (לפי הקובץ שבעורך)"
check "^FLOAT-REVEAL minimized=false visible=true onscreen=true right=true" "\"השאלה\" על חלון ממוזער / מחוץ למסך: החלון חוזר פתוח, בתוך המסך, בצד ימין"
check "^BEFORE-DONE src-files=1 folder=true bin=true tabs=1 solutions=2 owner=stack-sum-size" "לפני \"סיימתי\": יש קובץ, חבילה, קבצים מקומפלים, לשונית פתוחה והצעות לפתרון"
check "^TYPING typed=true editor-focus=true float-visible=true float-owned=true" "כותבים קוד בעורך בזמן שהחלון הצף פתוח: ההקלדה נכנסת לעורך, והחלון נשאר מקדימה (שייך לחלון של Eclipse)"
check "^DONE-BUTTON true" "בחלון הצף יש \"סיימתי את התרגיל\""
check "^CANCEL confirm=false src-files=1 folder=true bin=true tabs=1 solutions=2" "\"סיימתי\" ואז \"ביטול\" — חוזרים לשאלה, ושום דבר לא נמחק"
check "^ASK true inline=true text=.*השאלה תסומן בסימן ירוק. מה לעשות עם קבצי התרגיל.*קבצי התרגיל יימחקו מהפרויקט.*הקובץ שיימחק: StackTargil1.java.*רק הקבצים שנוצרו מהתרגיל" "לחיצה על \"סיימתי\" — שואלים בתוך החלון הצף עצמו (בלי חלון נוסף שמכסה), בעברית: למחוק (ברירת המחדל) או לשמור"
check "^AFTER-DONE done=true float-closed=true" "אחרי האישור: V ירוק, והחלון הצף נסגר"
check "^AFTER-DONE src-files=0 folder=false bin=false tabs=0 solutions=0 owner=null" "התרגיל נמחק עם כל המעטפת: קבצים, חבילה, bin, לשוניות, הצעות לפתרון, והשיוך לשאלה"
check "^KEEP-DONE true" "תרגיל נוסף, והתלמיד הוסיף קובץ משלו לחבילה: \"סיימתי\""
check "^KEEP created-left=0 helper=true folder=true mine=true" "רק קבצי התרגיל נמחקו — הקובץ של התלמיד בחבילה, החבילה, וקובץ אחר בפרויקט נשארו"
check "^DIALOG2-BUTTONS צור תרגיל | שאלה אקראית | סגירה" "אחרי המחיקה: בחלון המאגר שוב \"צור תרגיל\""
check "^DIALOG2-TREE .*Q stack-sum-size easy done" "אחרי המחיקה: ה-V נשאר בעץ"
check "^PREVIEW-LONG .*FIGURE-FIT [4-9][0-9][0-9]" "ציור ארוך (שרשרת של 12 חוליות, משאלה בתיקייה המקומית) — כולו, בלי גלילה הצידה (מוקטן לרוחב השורה רק כשהיא צרה ממנו)"
# חריגות ביומן שיש בהן קוד של המאגר — חוץ מאחת ידועה של Eclipse עצמו (סגירת עורך שיש לו היסטוריית undo: AbstractOperation.hasContext)
awk 'BEGIN{RS="!ENTRY"} /Exception/ && /il\.lamerhav\.dsviz\.Bank/ && !/AbstractOperation\.hasContext/ {print "!ENTRY" $0}' "$B/bank/eclipse.log" > "$B/bank/bank-errors.log"
[ -s "$B/bank/bank-errors.log" ] && { echo "  FAIL חריגה בקוד של המאגר:"; head -30 "$B/bank/bank-errors.log"; FAILED=1; } || echo "  ok  בלי חריגות של המאגר ביומן"
echo "  צילומים: $(ls "$B"/bank/*.png | tr '\n' ' ')"
[ $FAILED = 0 ]
