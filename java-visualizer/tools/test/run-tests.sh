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
echo "== 3. התוסף של VS Code (מול חיקוי של ה-API)"
node vscode-test.js | tail -2
echo "== 4. הרצה שורה אחר שורה (JDI) — כל שורה מתועדת עם המשתנים"
L="$W/lines"
mkdir -p "$L"
cp ../../src/Main.java "$L/"
javac -g -encoding UTF-8 -cp "$JAR" -d "$L" "$L/Main.java"
(cd "$L" && java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine Main > out.txt 2>&1)
grep -q 'שורה [0-9]*:.*total += x;.*total=0, x=9' "$L/out.txt" && grep -q 'הסכום: 21' "$L/out.txt" && grep -q 'התוכנית הסתיימה' "$L/out.txt" \
    && echo "עובד: $(grep -c 'שורה ' "$L/out.txt") שורות" || { echo "נכשל"; cat "$L/out.txt"; exit 1; }
echo "== 5. הרצה רגילה (Run) — רק פלט לקונסול, בלי חלון ובלי תיעוד צעדים"
(cd "$L" && java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" Main > plain.txt 2>&1)
grep -q 'הסכום: 21' "$L/plain.txt" && ! grep -q '#1 ' "$L/plain.txt" && [ "$(wc -l < "$L/plain.txt")" -le 3 ] \
    && echo "עובד: $(wc -l < "$L/plain.txt") שורות פלט בלבד" || { echo "נכשל"; cat "$L/plain.txt"; exit 1; }
(cd "$L" && java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -Ddsviz.visual=true -cp ".:$JAR" Main > visual.txt 2>&1)
grep -q '#1 ' "$L/visual.txt" && echo "הרצה ויזואלית (טקסט כשאין מסך): מתועדת" || { echo "נכשל"; cat "$L/visual.txt"; exit 1; }
