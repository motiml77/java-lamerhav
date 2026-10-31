#!/bin/sh
# כל הבדיקות. להריץ אחרי tools/build-all.sh
set -e
cd "$(dirname "$0")"
W=../../build/test
JAR="$(cd ../../release && pwd)/dsviz.jar"
rm -rf "$W" && mkdir -p "$W"
echo "== 1. התבניות של VS Code ושל Eclipse מפיקות קוד זהה (תרגילים + הוספה לקוד)"
node combos.js "$W/js" | tail -1
javac -encoding UTF-8 -d "$W/cls" ../../eclipse-plugin/src/il/lamerhav/dsviz/Scaffold.java ScaffoldAll.java
java -cp "$W/cls" ScaffoldAll "$W/combos.txt" "$W/java" >/dev/null
diff -r "$W/js" "$W/java" && echo "זהה"
javac -encoding UTF-8 -cp "$W/cls" -d "$W/cls" RandomCheck.java
echo "== 1ב. בדיקת שם למשתנה חדש (לא מתנגש בקוד) — אותן תשובות ב-JS וב-Java"
node names-test.js
javac -encoding UTF-8 -cp "$W/cls" -d "$W/cls" ../../eclipse-plugin/src/il/lamerhav/dsviz/NameCheck.java ../../csharp/new-exercise/src/il/lamerhav/dsviz/cs/ScaffoldCs.java NameCheckTest.java
java -Dstdout.encoding=UTF-8 -cp "$W/cls" NameCheckTest names-cases.txt
java -cp "$W/cls" RandomCheck "$W/randoms.txt"
echo "== 2. כל התרגילים וכל קטעי הקוד מתקמפלים מול הספרייה"
mkdir -p "$W/compile"
for f in "$W"/js/C*.java; do grep -q '^ERROR' "$f" || cp "$f" "$W/compile/"; done
n=0
for f in "$W"/js/S*.snippet; do
    grep -q '^ERROR' "$f" && continue
    n=$((n+1)); c="Snip$n"
    { echo "import unit4.collectionsLib.*;"; echo "public class $c { public static void main(String[] args) {"; cat "$f"; echo "} }"; } > "$W/compile/$c.java"
done
javac -encoding UTF-8 -nowarn -cp "$JAR" -d "$W/run" "$W"/compile/*.java
echo "$(ls "$W/compile" | wc -l) קבצים התקמפלו"
for c in C1 C200 C500 C800 C1000 Snip1 Snip40 Snip80; do
    [ -f "$W/run/$c.class" ] && java -Djava.awt.headless=true -cp "$W/run:$JAR" $c > "$W/run/$c.out" 2>&1 \
        && ! grep -q Exception "$W/run/$c.out" || { echo "נכשל: $c"; cat "$W/run/$c.out"; exit 1; }
done
echo "הרצת דוגמאות: תקין"
# מחלקות שהתלמיד כתב בעצמו (בלי שדה שם, רשימה מקושרת משלו עם מעגל, toString משלו) ומחרוזות — בהרצה שורה-שורה
mkdir -p "$W/userclass"
javac -g -encoding UTF-8 -nowarn -cp "$JAR" -d "$W/userclass" userclass/shapes/*.java
(cd "$W/userclass" && cp -r "$OLDPWD/userclass/shapes" . && timeout 120 java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -Ddsviz.visual=true \
    -cp ".:$JAR" unit4.collectionsLib.LineByLine shapes.Main > out.txt 2>&1)
U="$W/userclass/out.txt"
! grep -q "5000 צעדים" "$U" && grep -q "p=Point {x=3 · y=4}" "$U" && grep -q "st=Stack \[{x=3 · y=4}\]" "$U" \
    && grep -q "word.charAt(last)  →  'f'" "$U" && grep -q 'word.substring(1, last)  →  "bcde"' "$U" \
    && grep -q "השוני הראשון במקום 2" "$U" && grep -q "הפעולה toString מחזירה" "$U" \
    && echo "מחלקות של התלמיד ומחרוזות: כרטיסים, ותיאור הפעולות על מחרוזות (charAt / substring / compareTo) — בלי לולאה בגלל toString" \
    || { echo "נכשל: מחלקות של התלמיד / מחרוזות"; tail -30 "$U"; exit 1; }
# לולאות: for, מקוננות (גם j < i), while, do-while, for-each, ולולאות שעוברות על מערך / תור / מחסנית / רשימה
mkdir -p "$W/loops"
javac -g -encoding UTF-8 -nowarn -cp "$JAR" -d "$W/loops" loops/loops/*.java
(cd "$W/loops" && cp -r "$OLDPWD/loops/loops" . && timeout 120 java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -Ddsviz.visual=true \
    -cp ".:$JAR" unit4.collectionsLib.LineByLine loops.Main > out.txt 2>&1 \
    && timeout 120 java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -Ddsviz.visual=true -cp ".:$JAR" unit4.collectionsLib.LineByLine loops.Data > data.txt 2>&1)
L2="$W/loops/out.txt"
L3="$W/loops/data.txt"
grep -qF "for (int i = 0; i < 4; i++) סיבוב 2, i=1 [0,(1),2,3], תנאי: 1 < 4 → true" "$L2" \
    && grep -qF "לולאה פנימית: for (int j = 0; j < i; j++) סיבוב 2, j=1 [0,(1)]" "$L2" \
    && grep -qF "הזוגות (i, j): ../x./xO" "$L2" \
    && grep -qF "while (n > 1) סיבוב 3, n: 20 → 10 → 5, תנאי: 5 > 1 → true" "$L2" \
    && grep -qF "do { ... } while (k > 0) סיבוב 3, k: 3 → 2 → 1, תנאי: 0 > 0 → false" "$L2" \
    && grep -qF "for (int x : arr) סיבוב 2 [0,(1)], על מערך arr [5, 6], x = 6" "$L2" \
    && grep -qF "for (int i = 1; i < arr.length; i++) סיבוב 2, i=2 [0,1,(2)], על מערך arr [7, 3, 9], תנאי: 2 < 3 → true" "$L3" \
    && grep -qF "while (!q.isEmpty()) סיבוב 1, על תור q [4, 5], בתור q יש עוד 2 איברים, תנאי: !q.isEmpty() → true" "$L3" \
    && grep -qF "while (!st.isEmpty()) סיבוב 2, על מחסנית st, המחסנית st ריקה, תנאי: !st.isEmpty() → false" "$L3" \
    && grep -qF "p מצביע על חוליה עם הערך 2, תנאי: p != null → true" "$L3" \
    && grep -qF "הלולאה הפנימית מתחילה מחדש — כי i התקדם ל-1" "$L3" \
    && echo "לולאות: סיבובים, תנאי עם ערכים, סרגל ערכים / מערך, תור / מחסנית / רשימה, היסטוריה ב-while, ולולאות מקוננות" \
    || { echo "נכשל: לולאות"; grep -h "לולאה\|הזוגות" "$L2" "$L3" | head -30; exit 1; }
echo "== 3. התוסף של VS Code (מול חיקוי של ה-API)"
node vscode-test.js | tail -2
echo "== 3ב. החלון \"הוסף מבנה נתונים\" של VS Code בדפדפן (Chromium): לחיצות, ערכים אקראיים, בדיקת שם, ציור"
node addstructure-test.js "$W" | tail -1
echo "== 4. הרצה שורה אחר שורה (JDI) — כל שורה מתועדת עם המשתנים"
L="$W/lines"
mkdir -p "$L"
cp ../../src/Main.java "$L/"
javac -g -encoding UTF-8 -cp "$JAR" -d "$L" "$L/Main.java"
# שורת "התוכנית הסתיימה" נכתבת מחוט רקע — בלי חלון (כמו כאן) היא לפעמים נאבדת כשהתהליך נסגר מהר; מנסים עד 3 פעמים
ok=0
for try in 1 2 3; do
    (cd "$L" && java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine Main > out.txt 2>&1)
    if grep -q 'שורה [0-9]*:.*total += x;.*total=0, x=9' "$L/out.txt" && grep -q 'הסכום: 21' "$L/out.txt" && grep -q 'התוכנית הסתיימה' "$L/out.txt"; then ok=1; break; fi
done
[ $ok = 1 ] && echo "עובד: $(grep -c 'שורה ' "$L/out.txt") שורות" || { echo "נכשל"; cat "$L/out.txt"; exit 1; }
echo "== 5. הרצה רגילה (Run) — רק פלט לקונסול, בלי חלון ובלי תיעוד צעדים"
(cd "$L" && java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" Main > plain.txt 2>&1)
grep -q 'הסכום: 21' "$L/plain.txt" && ! grep -q '#1 ' "$L/plain.txt" && [ "$(wc -l < "$L/plain.txt")" -le 3 ] \
    && echo "עובד: $(wc -l < "$L/plain.txt") שורות פלט בלבד" || { echo "נכשל"; cat "$L/plain.txt"; exit 1; }
(cd "$L" && java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -Ddsviz.visual=true -cp ".:$JAR" Main > visual.txt 2>&1)
grep -q '#1 ' "$L/visual.txt" && echo "הרצה ויזואלית (טקסט כשאין מסך): מתועדת" || { echo "נכשל"; cat "$L/visual.txt"; exit 1; }

echo "== 5א. שגיאות שהתלמיד רואה בציור: מבנה ריק, null, מערך, וחוליות שאבדו (אין מצביע)"
P="$W/problems"
mkdir -p "$P"
javac -g -encoding UTF-8 -cp "$JAR" -d "$P" $(find problems -name "*.java")
card() {  # card <class> <expected text>
    (cd "$P" && java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine "$1" > "$1.txt" 2>&1) || true
    grep -qF "$2" "$P/$1.txt" && echo "  ok  $1: $2" || { echo "  FAIL $1: חסר $2"; cat "$P/$1.txt"; exit 1; }
}
card bugs.EmptyPop '[כרטיס empty-stack s pop()]'
card bugs.EmptyQueue '[כרטיס empty-queue q head()]'
card bugs.NullNode '[כרטיס null p getValue()]'
card bugs.TreeNull '[כרטיס null t getValue()]'
card bugs.Index '[כרטיס index]'
card lost.LostCheck 'MARK-NO-LOSS-ABOVE'
sed '/MARK-NO-LOSS-ABOVE/q' "$P/lost.LostCheck.txt" | grep -q 'אבד' && { echo "  FAIL חוליה סומנה כאבודה בלי סיבה"; cat "$P/lost.LostCheck.txt"; exit 1; }
sed '1,/MARK-NO-LOSS-ABOVE/d' "$P/lost.LostCheck.txt" | grep -q 'אבד' && echo "  ok  ראש הרשימה שאבד מסומן (ורק הוא)" || { echo "  FAIL ראש שאבד לא סומן"; cat "$P/lost.LostCheck.txt"; exit 1; }

echo "== 5ג. פעולות העזר מהספרייה: Helpers.size / Helpers.duplicate — לא משנות את המבנה, ומעתיקות גם מבנים פנימיים"
H="$W/helpers"
mkdir -p "$H"
cp helpers/HelpersCheck.java "$H/"
javac -g -encoding UTF-8 -cp "$JAR" -d "$H" "$H/HelpersCheck.java"
java -Dstdout.encoding=UTF-8 -cp "$H:$JAR" HelpersCheck > "$H/out.txt" 2>&1 && grep -q MARK-END "$H/out.txt" && grep '^  ok' "$H/out.txt" || { cat "$H/out.txt"; exit 1; }
(cd "$H" && java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine HelpersCheck > lines.txt 2>&1) || true
# שורה 14 (sc = Helpers.duplicate(s)): השורה עצמה וצעד אחד של העותק — בלי ה-pop/push שבפנים, והעותק נקרא sc
grep -q '  size(s)  →  3' "$H/lines.txt" && grep -q '  duplicate(s)  →  עותק חדש' "$H/lines.txt" \
    && [ "$(grep -c '(HelpersCheck.java:14)' "$H/lines.txt")" = 2 ] && grep -q ' sc.pop()  →  3' "$H/lines.txt" \
    && echo "  ok  בהרצה הוויזואלית: כל קריאה היא צעד אחד, והעותק נקרא בשם המשתנה" || { echo "  FAIL צעדים של Helpers"; grep 'Helpers\|java:1[46])' "$H/lines.txt"; exit 1; }
# בלי "Helpers.": שורת import static אחת, וקוראים ל-size(q) / duplicate(q) ישירות
cp helpers/BareCalls.java "$H/"
javac -g -encoding UTF-8 -cp "$JAR" -d "$H" "$H/BareCalls.java"
java -Dstdout.encoding=UTF-8 -cp "$H:$JAR" BareCalls > "$H/bare.txt" 2>&1 && grep -q 'size 2 1' "$H/bare.txt" \
    && (cd "$H" && java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine BareCalls > bare-lines.txt 2>&1; true) \
    && grep -q '  duplicate(q)  →  עותק חדש      copy' "$H/bare-lines.txt" \
    && echo "  ok  size(q) / duplicate(q) בלי Helpers. (import static)" || { echo "  FAIL קריאה בלי Helpers."; cat "$H/bare.txt"; grep 'size\|duplicate' "$H/bare-lines.txt"; exit 1; }

echo "== 5ד. תנאים בהרצה הוויזואלית: המשתנים בקטן, האם התנאי מתקיים, ואיזה ענף רץ (if / else / else if, while, for, do-while)"
C="$W/conds"
mkdir -p "$C"
cp conds/Conds.java "$C/"
javac -g -encoding UTF-8 -cp "$JAR" -d "$C" "$C/Conds.java"
(cd "$C" && java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine Conds > out.txt 2>&1) || true
conds() {   # $1 = הקובץ, ואחריו השורות שחייבות להופיע
    f=$1; shift
    for want in "$@"; do
        grep -qF -- "$want" "$f" && echo "  ok  $want" || { echo "  FAIL חסר: $want"; grep '^  \[' "$f"; exit 1; }
    done
}
conds "$C/out.txt" \
    'שורה 23 if: x % 2 == 0 | 7 % 2 == 0 | x=7 | false עוברים ל-else]' 'שורה 27: else של שורה 23]' \
    'שורה 30 if: x > 5 | 7 > 5 | x=7 | true נכנסים ל-if]' 'שורה 31: if של שורה 30]' \
    'while: !s.isEmpty() | !false | s=Stack [8,3] | true ממשיכים לסיבוב]' 'while: !s.isEmpty() | !true | s=Stack [] | false יוצאים מהלולאה]' \
    'שורה 41 if: v > 5 | 3 > 5 | v=3 | false מדלגים על ה-if]' \
    'for: i < 2 | 0 < 2 | i=0 | true ממשיכים לסיבוב]' 'for: i < 2 | 2 < 2 | i=2 | false יוצאים מהלולאה]' \
    'do: k > 1 | 2 > 1 | k=2 | true חוזרים לסיבוב נוסף]' 'do: k > 1 | 1 > 1 | k=1 | false יוצאים מהלולאה]' \
    'שורה 6 if: x < 0 | 0 < 0 | x=0 | false עוברים ל-else if]' 'שורה 11: else של שורה 8]' \
    'שורה 16 if: n <= 1 | 1 <= 1 | n=1 | true נכנסים ל-if]' \
    'שורה 8 if: x == 0 | 0 == 0 | x=0 | true נכנסים ל-else if]' \
    'רץ ה-else if — התנאי בשורה 6 לא התקיים, והתנאי בשורה 8 התקיים]' 'רץ ה-else — אף תנאי לא התקיים (שורות 6, 8)]' \
    'רץ ה-else if — התנאים בשורות 55, 57 לא התקיימו, והתנאי בשורה 59 התקיים]' 'רץ ה-else — אף תנאי לא התקיים (שורות 55, 57, 59)]' \
    'רץ ה-else — כי התנאי בשורה 23 לא התקיים]' 'רץ ה-if — כי התנאי בשורה 30 התקיים]'
! grep -q 'for: i < 2 | 1 < 2 | i=1 | false' "$C/out.txt" || { echo "  FAIL ב-for מוצג הערך מלפני ה-i++"; exit 1; }

echo "== 5ה. פעולה שקוראת לפעולת עזר: יוצאים אליה, מי מחכה, מה היא מחזירה, וחוזרים (לא מ-main, ולא רקורסיה)"
K="$W/calls"
mkdir -p "$K"
cp calls/Calls.java "$K/"
javac -g -encoding UTF-8 -cp "$JAR" -d "$K" "$K/Calls.java"
(cd "$K" && java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine Calls > out.txt 2>&1) || true
conds "$K/out.txt" \
    'CALLING שורה 29: sumEven() > isEven() | בשורה 29 sumEven() קוראת לפעולת העזר isEven() — יוצאים אליה עם x = 8 | כותרת 9]' \
    'ENTERED שורה 10: sumEven() > isEven() | יצאנו מ-sumEven() (שורה 29) לפעולת העזר isEven() עם x = 8 | כותרת 9]' \
    'ENTERED שורה 6: sumEven() > isEven() > rem() | יצאנו מ-isEven() (שורה 10) לפעולת העזר rem() עם x = 8, d = 2 | כותרת 5]' \
    'RETURNING שורה 6: sumEven() > isEven() > rem() | rem() מחזירה 0 — חוזרים ל-isEven() בשורה 10' \
    'RETURNING שורה 10: sumEven() > isEven() | isEven() מחזירה false — חוזרים ל-sumEven() בשורה 29' \
    'RETURNED שורה 29: sumEven() | חזרנו ל-sumEven() בשורה 29 — isEven() החזירה true, ממשיכים מאותה נקודה' \
    'INSIDE שורה 17: maxOf() > bigger() | בתוך פעולת העזר bigger() · maxOf() מחכה בשורה 43 לתשובה' \
    'RETURNING שורה 22: maxOf() > report() | report() הסתיימה — חוזרים ל-maxOf() בשורה 45' \
    'RETURNED שורה 46: maxOf() | חזרנו ל-maxOf() מהקריאה בשורה 45 — report() הסתיימה, ממשיכים בשורה 46'
! grep -q 'קריאה #[0-9]* [A-Z]* שורה [0-9]*: main()' "$K/out.txt" || { echo "  FAIL קריאה מ-main סומנה"; exit 1; }
! grep -q 'קריאה #[0-9]* [A-Z]* שורה 5[0-9]:' "$K/out.txt" || { echo "  FAIL שורה ב-main סומנה"; exit 1; }

echo "== 5ו. פעולה שרצה בתוך אובייקט (d1.birthday()): מי this, מאפיינים שהשורה קוראת / משנה, וערכים נכונים בכל רגע"
O="$W/self"
mkdir -p "$O"
cp objects/Pets.java "$O/"
javac -g -encoding UTF-8 -cp "$JAR" -d "$O" "$O/Pets.java"
(cd "$O" && java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine Pets > out.txt 2>&1) || true
conds "$O/out.txt" \
    'שורה 16: this=d1 ב-הבנאי Dog() {name=null, age=0} this.name=שינוי]' \
    'שורה 17: this=d1 ב-הבנאי Dog() {name="רקס", age=0} this.age=שינוי]' \
    'שורה 5: נכנסים ל-d1.birthday()]' \
    'שורה 21: this=d1 ב-birthday() {name="רקס", age=3} this.age=שינוי]' \
    'שורה 26: this=d1 ב-bark() {name="רקס", age=4} this.name=קריאה]' \
    'שורה 30: this=d2 ב-isOlderThan() {name="לאסי", age=5} other←d1 this.age=קריאה other.age=קריאה]' \
    'שורה 34: this=d1 ב-getName() {name="רקס", age=4} this.name=קריאה]'

echo "== 5ז. סוגי רקורסיה: פשוטה, זנב (גם void), כפולה, הדדית, על עץ / על שרשרת — והקריאות של תנאי העצירה"
R="$W/reckinds"
mkdir -p "$R"
cp recursion/*.java "$R/"
javac -g -encoding UTF-8 -cp "$JAR" -d "$R" "$R"/*.java
for spec in 'Fact:רקורסיה פשוטה | פעולות fact | עומק 4 | קריאות 4 | עצירה [#5→1]' \
    'SumTail:רקורסיית זנב | פעולות sum | עומק 5 | קריאות 5 | עצירה [#6→10]' \
    'PrintDown:רקורסיית זנב | פעולות down' \
    'Fib:רקורסיה כפולה | פעולות fib | עומק 4 | קריאות 9 | עצירה [#5→1, #6→0, #7→1, #9→1, #10→0]' \
    'EvenOdd:רקורסיה הדדית | פעולות isEven, isOdd | עומק 4' \
    'TreeCount:רקורסיה כפולה | פעולות count | עומק 3 | קריאות 7 | על עץ בינארי' \
    'ListLen:רקורסיה פשוטה | פעולות length | עומק 4 | קריאות 4 | על שרשרת חוליות'; do
    c=${spec%%:*}; want=${spec#*:}
    (cd "$R" && java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine $c > $c.txt 2>&1) || true
    grep -qF -- "[רקורסיה: $want" "$R/$c.txt" && echo "  ok  $c: $want" || { echo "  FAIL $c: $want"; grep 'רקורסיה:' "$R/$c.txt"; exit 1; }
done

echo "== 5ח. חץ \"חוזרים לראש הלולאה\" בקוד: for, while, do-while, מקוננות (החץ של החיצונית), for-each, continue — והציור עצמו"
LB="$W/loopback"
mkdir -p "$LB"
cp loops/LoopBack.java "$LB/"
javac -g -encoding UTF-8 -cp "$JAR" -d "$LB" "$LB/LoopBack.java"
(cd "$LB" && java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine LoopBack > out.txt 2>&1) || true
conds "$LB/out.txt" \
    'שורה 10 for: מ-11, החץ מ-12 | for (int i = 0; i < n; i++)]' \
    'שורה 14 while: מ-16, החץ מ-17 | while (k > 0)]' \
    'שורה 20 do: מ-21, החץ מ-21 | do { ... } while (d > 0)]' \
    'שורה 23 for: מ-24, החץ מ-25 | for (int b = 0; b < 2; b++)]' \
    'שורה 22 for: מ-23, החץ מ-26 | for (int a = 0; a < 2; a++)]' \
    'שורה 28 foreach: מ-30, החץ מ-32 | for (int x : arr)]' \
    'שורה 33 for: מ-34, החץ מ-36 | for (int c = 0; c < 3; c++)]'
# בלי חץ: כניסה ראשונה ללולאה הפנימית, for בשורה אחת, ובתוך פעולת העזר; ובסך הכול 16 חזרות
! grep -q 'שורה 23 for: מ-22\|\[חזרה #[0-9]* שורה 37 \|\[חזרה #[0-9]* שורה 5 ' "$LB/out.txt" && [ "$(grep -c '^  \[חזרה #' "$LB/out.txt")" = 16 ] \
    && echo "  ok  בלי חץ כשלא חוזרים להתחלה (כניסה ללולאה, שורה אחת, פעולת עזר) — 16 חזרות" \
    || { echo "  FAIL חץ במקום שאין בו חזרה"; grep 'חזרה' "$LB/out.txt"; exit 1; }
javac -encoding UTF-8 -cp "$JAR" -d "$LB" loops/LoopArrowPaint.java
java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$LB:$JAR" unit4.collectionsLib.LoopArrowPaint > "$LB/paint.txt" 2>&1 \
    && grep -q MARK-END "$LB/paint.txt" && grep '^  ok' "$LB/paint.txt" || { cat "$LB/paint.txt"; exit 1; }

echo "== 5ט. קלט מהמקלדת (Scanner / BufferedReader / System.in.read): השורה שמחכה, מה שהוקלד כצעד INPUT, והפלט"
# בלי חלון: dsviz.input "מקליד" את השורות כשהתוכנית מחכה (מקובץ UTF-8 — עברית בלי תלות בקידוד של שורת הפקודה)
I="$W/input"
mkdir -p "$I"
cp input/*.java "$I/"
javac -g -encoding UTF-8 -cp "$JAR" -d "$I" "$I"/*.java
printf '3\n4 5\n6\nדני כהן\n' > "$I/scan.in"
printf 'y\nתל אביב\n' > "$I/read.in"
printf 'abc\n' > "$I/bad.in"
printf '1 2\n3\n' > "$I/sum.in"
for run in ScanInput:scan ReadInput:read ScanInput:bad SumUntil:sum; do
    (cd "$I" && timeout 120 java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -Ddsviz.input=@${run#*:}.in -cp ".:$JAR" \
        unit4.collectionsLib.LineByLine ${run%%:*} < /dev/null 2>&1 | sed 's/\xE2\x80\x8E//g' > ${run#*:}.out) || true
done
conds "$I/scan.out" \
    '⌨ מחכה לקלט מהמקלדת — שורה 10:  in.nextInt()' '(ScanInput.java:10)  קלט מהמקלדת: 3' \
    '⌨ מחכה לקלט מהמקלדת — שורה 14:  in.nextInt()' '(ScanInput.java:14)  קלט מהמקלדת: 4 5' '(ScanInput.java:14)  s.push(5)' \
    '(ScanInput.java:14)  קלט מהמקלדת: 6' '⌨ מחכה לקלט מהמקלדת — שורה 18:  in.nextLine()' '(ScanInput.java:18)  קלט מהמקלדת: דני כהן' \
    'name="דני כהן"' 'שלום דני כהן, הסכום: 15'
[ "$(grep -c 'קלט מהמקלדת: ' "$I/scan.out")" = 4 ] && echo "  ok  4 צעדי קלט (\"4 5\" לשתי קריאות בשורה אחת — צעד אחד)" \
    || { echo "  FAIL מספר צעדי הקלט"; grep 'קלט מהמקלדת' "$I/scan.out"; exit 1; }
grep -A1 'מחכה לקלט מהמקלדת — שורה 18' "$I/scan.out" | grep -qx 'דני כהן' && echo "  ok  מה שהוקלד מודפס גם לקונסול" \
    || { echo "  FAIL ההקלדה לא הודפסה לקונסול"; exit 1; }
conds "$I/read.out" '⌨ מחכה לקלט מהמקלדת — שורה 9:  System.in.read()' '⌨ מחכה לקלט מהמקלדת — שורה 14:  br.readLine()' \
    '(ReadInput.java:14)  קלט מהמקלדת: תל אביב' 'קיבלתי y ו-תל אביב (7 תווים)'
conds "$I/bad.out" '(ScanInput.java:10)  קלט מהמקלדת: abc' 'שגיאה: InputMismatchException'
# while (in.hasNextInt()) עד שהקלט נגמר — צעד אחד "סוף הקלט" (בחלון: אחרי "סיום הקלט")
conds "$I/sum.out" '⌨ מחכה לקלט מהמקלדת — שורה 8:  in.hasNextInt()' '(SumUntil.java:8)  קלט מהמקלדת: 1 2' \
    '(SumUntil.java:8)  סוף הקלט — אין עוד מה לקרוא' 'סכום: 6'
# בלי חלון ובלי dsviz.input — כמו קודם: System.in הרגיל. קלט מצינור עובד (בלי צעדי קלט), ובסוף הקלט — NoSuchElementException
(cd "$I" && printf '2\n7 8\nרון\n' | timeout 120 java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" \
    unit4.collectionsLib.LineByLine ScanInput > piped.out 2>&1) || true
grep -q 'שלום רון, הסכום: 15' "$I/piped.out" && ! grep -q 'קלט מהמקלדת\|מחכה לקלט' "$I/piped.out" \
    && echo "  ok  בלי חלון: קלט מצינור כמו קודם" || { echo "  FAIL קלט מצינור בלי חלון"; tail -5 "$I/piped.out"; exit 1; }
(cd "$I" && timeout 120 java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" \
    unit4.collectionsLib.LineByLine ScanInput < /dev/null > eof.out 2>&1) || true
grep -q 'שגיאה: NoSuchElementException' "$I/eof.out" && ! grep -q 'קלט מהמקלדת\|מחכה לקלט' "$I/eof.out" \
    && echo "  ok  בלי חלון ובלי קלט: NoSuchElementException כמו קודם" || { echo "  FAIL בלי קלט"; tail -5 "$I/eof.out"; exit 1; }

echo "== 5ב. כרטיסי אובייקטים: תוכן המחלקה בתוך כל מבנה, וגישה למאפיין (getName / setGrade / drive)"
mkdir -p "$W/obj"
javac -encoding UTF-8 -cp "$JAR" -d "$W/obj" objcards/ObjCheck.java
java -Djava.awt.headless=true -Ddsviz.visual=true -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$W/obj:$JAR" unit4.collectionsLib.ObjCheck > "$W/obj/out.txt" 2>&1 \
    && grep -E '^  (ok|FAIL)' "$W/obj/out.txt" || { grep -E '^  (ok|FAIL)' "$W/obj/out.txt"; exit 1; }
javac -g -encoding UTF-8 -cp "$JAR" -d "$W/obj" objcards/ObjMatrix.java
java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$W/obj:$JAR" unit4.collectionsLib.LineByLine ObjMatrix > "$W/obj/matrix.txt" 2>&1
grep -q 'getAverage()  →  84.0' "$W/obj/matrix.txt" && grep -q 'setGrade(0, 100)' "$W/obj/matrix.txt" && grep -q 'Student מאיה פרץ (95)' "$W/obj/matrix.txt" \
    && echo "  ok  מטריצה של Student (שורה-שורה): נרשמת, והגישות מתועדות" || { echo "  FAIL מטריצה של Student"; cat "$W/obj/matrix.txt"; exit 1; }
javac -g -encoding UTF-8 -nowarn -cp "$JAR" -d "$W/obj" objcards/NestMix.java 2>&1 | grep -v "^Note" || true
java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$W/obj:$JAR" unit4.collectionsLib.LineByLine NestMix > "$W/obj/nest.txt" 2>&1
grep -q 'arr=\[\[4\], \[9,8\], \[\]\]' "$W/obj/nest.txt" && grep -q 'deep=Stack \[\[\[9,8\]\]\]' "$W/obj/nest.txt" && grep -q 'qn=Queue \[1 -> 2 -> 3 -> null,7 -> null\]' "$W/obj/nest.txt" \
    && echo "  ok  מבנים בתוך מבנים (מערך של מחסניות, מחסנית של מחסניות של מחסניות, תור של רשימות, עץ של מחסניות): נרשמים" \
    || { echo "  FAIL מבנים בתוך מבנים"; cat "$W/obj/nest.txt"; exit 1; }
mkdir -p "$W/ne"
javac -encoding UTF-8 -cp "$JAR" -d "$W/ne" ../../src/examples/NestedExamples.java
java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$W/ne:$JAR" examples.NestedExamples > "$W/ne/out.txt" 2>&1
grep -q 'סכום: 25' "$W/ne/out.txt" && grep -q 'כמה מספרים בתורים: 3' "$W/ne/out.txt" && grep -q 'אחרי שיטוח' "$W/ne/out.txt" \
    && echo "  ok  דוגמאות מבנה בתוך מבנה (NestedExamples): רצות ומחשבות נכון" || { echo "  FAIL NestedExamples"; cat "$W/ne/out.txt"; exit 1; }

echo "== 9. שליחת תרגיל: השרת (Worker), הכנת הקבצים, ובניית המעטפת מהתשובה — מתקמפלת ורצה"
(cd ../../worker && node test/worker-test.mjs | tail -1)
mkdir -p "$W/ai/cls" "$W/ai/src" "$W/ai/bin"
javac -encoding UTF-8 -d "$W/ai/cls" ../../eclipse-plugin/src/il/lamerhav/dsviz/Json.java ../../eclipse-plugin/src/il/lamerhav/dsviz/AiScaffold.java \
    ../../eclipse-plugin/src/il/lamerhav/dsviz/ExerciseFiles.java ../../eclipse-plugin/src/il/lamerhav/dsviz/WorkerClient.java AiScaffoldTest.java ExerciseFilesTest.java
java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$W/ai/cls" il.lamerhav.dsviz.ExerciseFilesTest ai/files
java -Dstdout.encoding=UTF-8 -cp "$W/ai/cls" il.lamerhav.dsviz.AiScaffoldTest ai "$W/ai/src"
javac -encoding UTF-8 -cp "$JAR" -d "$W/ai/bin" $(find "$W/ai/src" -name '*.java')
for m in $(cat "$W/ai/src/mains.txt"); do
    java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$W/ai/bin:$JAR" "$m" > "$W/ai/run.txt" 2>&1 || { echo "  FAIL $m"; cat "$W/ai/run.txt"; exit 1; }
    grep -q 'סעיף' "$W/ai/run.txt" || { echo "  FAIL $m (אין פלט של סעיפים)"; cat "$W/ai/run.txt"; exit 1; }
done
echo "  ok  כל המעטפות מתקמפלות ורצות (main קורא לכל הסעיפים)"
# "הצעה לפתרון": ההצעות מתאימות לכותרת, לא נכנסות לקוד של התלמיד, ובאמת נותנות את הפלט הצפוי;
# פעם עם javac ופעם עם ecj (הקומפיילר של Eclipse — מה שרץ בתוך Eclipse)
mkdir -p "$W/sol/cls"
javac -encoding UTF-8 -d "$W/sol/cls" ../../eclipse-plugin/src/il/lamerhav/dsviz/Json.java ../../eclipse-plugin/src/il/lamerhav/dsviz/AiScaffold.java \
    ../../eclipse-plugin/src/il/lamerhav/dsviz/SolutionCheck.java ../../eclipse-plugin/src/il/lamerhav/dsviz/SolutionStore.java SolutionCheckTest.java
for cp in "$W/sol/cls" "$W/sol/cls:../../build/eclipse-deps/ecj.jar"; do
    rm -rf "$W/sol/work" && mkdir -p "$W/sol/work"
    java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$cp" il.lamerhav.dsviz.SolutionCheckTest ai "$JAR" "$W/sol/work" > "$W/sol/out.txt" 2>&1 \
        || { echo "  FAIL הצעה לפתרון ($cp)"; cat "$W/sol/out.txt"; exit 1; }
done
sed 's/^/  /' "$W/sol/out.txt"

echo "== 9א. האתר בונה את הקוד כמו Eclipse: site/assets/scaffold.js מול AiScaffold.java — אותם קבצים בדיוק"
node scaffold-parity.js "$W/parity" > "$W/parity.txt" 2>&1 && echo "  ok  $(grep -c '^ *ok' "$W/parity.txt") השוואות זהות" || { cat "$W/parity.txt"; exit 1; }
echo "== 9ב. האתר: שאלת בגרות לקוד (בדפדפן, מול קוד השרת) ומונה ההורדות"
node site-bagrut-test.js "$W" > "$W/site.txt" 2>&1 && grep -E '^  (ok|FAIL)|עברו' "$W/site.txt" || { cat "$W/site.txt"; exit 1; }

echo "== 10. המורה בעברית (Eclipse): תרגום כל הודעות הקומפיילר, מיקום מדויק של הבעיה, ואזהרות על טעויות נפוצות"
DEPS="$(cd ../../build/eclipse-deps && pwd)"
mkdir -p "$W/mentor/cls"
javac -encoding UTF-8 -nowarn -cp "$(ls "$DEPS"/*.jar | tr '\n' ':')" -d "$W/mentor/cls" ../../eclipse-plugin/src/il/lamerhav/dsviz/Mentor.java \
    ../../eclipse-plugin/src/il/lamerhav/dsviz/MentorRules.java ../../eclipse-plugin/src/il/lamerhav/dsviz/MentorSource.java \
    ../../eclipse-plugin/src/il/lamerhav/dsviz/HebrewErrors.java ../../eclipse-plugin/src/il/lamerhav/dsviz/RtlText.java RtlTextTest.java MentorTest.java
cp ../../eclipse-plugin/src/il/lamerhav/dsviz/problems_he.properties "$W/mentor/cls/il/lamerhav/dsviz/"
(cd "$W/mentor" && jar xf "$DEPS/ecj.jar" org/eclipse/jdt/internal/compiler/problem/messages.properties)
java -Dstdout.encoding=UTF-8 -Dclean=../../src/examples:../../src/Main.java -Dmessages="$W/mentor/org/eclipse/jdt/internal/compiler/problem/messages.properties" \
    -cp "$W/mentor/cls:$(ls "$DEPS"/*.jar | tr '\n' ':')" il.lamerhav.dsviz.MentorTest "$JAR" mentor-cases.txt
echo "== 10א. חלון \"הסברים בעברית\": עברית עם קוד מוצגת נכון מימין לשמאל (s.pop() בסוף משפט, arr[i], Stack<Integer>, !=, מספרים, רשימות)"
java -Dstdout.encoding=UTF-8 -cp "$W/mentor/cls" il.lamerhav.dsviz.RtlTextTest

echo "== 5ח. רשימה מקושרת: לכל מצביע חץ וצבע משלו (כמה מצביעים לאותה חוליה), וחיבור חוליה חדשה לרשימה קיימת (באמצע/בסוף) — בלי שכפול ובלי 'אבדה' שגויה"
LN="$W/lists"
mkdir -p "$LN"
cp lists/LinkNew.java "$LN/"
javac -g -encoding UTF-8 -cp "$JAR" -d "$LN" "$LN/LinkNew.java"
(cd "$LN" && java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine LinkNew > out.txt 2>&1) || true
conds "$LN/out.txt" \
    '[שרשרת #7: n: 5(n)→null]' '[שרשרת #7: רשימה: 3(list)→7→1→null]' \
    '[שרשרת #8: n: 5(n)→3(list)→7→1→null]' \
    '[שרשרת #10: n: 5(list,n)→3→7→1→null]' '[שרשרת #11: n: 5(list,n,p)→3→7→1→null]' \
    '[שרשרת #28: n: 5(list,n)→3→7→1(p)→9(t)→null]' \
    'ענף 4(q)↗7' \
    '[שרשרת #37: n: 5(list,n)→3(p)→4(q)→7→1→9(t)→null]'
! grep -q 'אבד' "$LN/out.txt" && echo "  ok  אין אזהרת 'אבדה' שגויה (אין חוליה בלי מצביע) בכל הצעדים" \
    || { echo "  FAIL חוליה סומנה כאבודה בלי סיבה"; grep 'אבד' "$LN/out.txt"; exit 1; }

echo "== C# (Visual Studio / VS Code / Rider)"
sh ./cs-tests.sh
