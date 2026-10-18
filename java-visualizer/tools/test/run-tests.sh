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
