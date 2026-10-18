#!/bin/sh
# כל הבדיקות. להריץ אחרי tools/build-all.sh
set -e
cd "$(dirname "$0")"
W=../../build/test
rm -rf "$W" && mkdir -p "$W"
echo "== 1. התבניות של VS Code ושל Eclipse מפיקות קוד זהה"
node combos.js "$W/js" | tail -1
javac -encoding UTF-8 -d "$W/cls" ../../eclipse-plugin/src/il/lamerhav/dsviz/Scaffold.java ScaffoldAll.java
java -cp "$W/cls" ScaffoldAll "$W/combos.txt" "$W/java" >/dev/null
diff -r "$W/js" "$W/java" && echo "זהה"
echo "== 2. כל התרגילים שנוצרים מתקמפלים מול הספרייה"
javac -encoding UTF-8 -nowarn -cp ../../release/dsviz.jar -d "$W/run" "$W"/js/*.java
echo "$(ls "$W/run" | wc -l) מחלקות"
echo "== 3. התוסף של VS Code (מול חיקוי של ה-API)"
node vscode-test.js | tail -2
echo "== 4. הרצה שורה אחר שורה (JDI) — כל שורה מתועדת עם המשתנים"
L="$W/lines"
mkdir -p "$L"
cp ../../src/Main.java "$L/"
javac -g -encoding UTF-8 -cp ../../release/dsviz.jar -d "$L" "$L/Main.java"
JAR="$(cd ../../release && pwd)/dsviz.jar"
(cd "$L" && java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine Main > out.txt 2>&1)
grep -q 'שורה 20:.*total += x;.*total=0, x=9' "$L/out.txt" && grep -q 'הסכום: 21' "$L/out.txt" && grep -q 'התוכנית הסתיימה' "$L/out.txt" \
    && echo "עובד: $(grep -c 'שורה ' "$L/out.txt") שורות" || { echo "נכשל"; cat "$L/out.txt"; exit 1; }
