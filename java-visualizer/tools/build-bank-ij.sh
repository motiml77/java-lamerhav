#!/bin/sh
# "מאגר תרגילים" ל-IntelliJ — גרסת בדיקה (לא חלק מההתקנה לתלמידים):
#   build/lab-ij/dsviz-bank-intellij-1.0.0.zip  — תוסף נפרד (תלוי בתוסף DSViz הראשי); IntelliJ: Settings → Plugins → ⚙ → Install Plugin from Disk
# לפני הבנייה: הכלי (tools/build-bank-vs.sh — אותם חלונות כמו ב-Visual Studio, במצב --ij), הבדיקה הרחבה במצב IntelliJ (BankIjAll —
# כל 27 השאלות, הגשר, "סיימתי", מקרי קצה), והבדיקות של התוסף בתוך IntelliJ (gradle test). צריך Gradle ו-JDK 21.
set -e
cd "$(dirname "$0")/.."
sh tools/build-bank-vs.sh
[ -f intellij-plugin/build/distributions/dsviz-intellij-1.0.1.zip ] || (cd intellij-plugin && gradle --no-daemon -q buildPlugin)
W=build/bank-ij-all
rm -rf "$W" && mkdir -p "$W/cls"
javac -encoding UTF-8 -nowarn -cp release/dsviz.jar:build/lab-vs/dsviz-bank-vs.jar -d "$W/cls" \
    tools/test/intellij-bank/BankIjAll.java tools/test/intellij-bank/BankIjIt.java tools/test/windows-vs/BankVsIt.java
run() { if [ -n "$DISPLAY" ]; then "$@"; else xvfb-run -a "$@"; fi; }
LC_ALL=C.UTF-8 run java -Duser.home="$PWD/$W/home" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 \
    -cp release/dsviz.jar:build/lab-vs/dsviz-bank-vs.jar:$W/cls il.lamerhav.dsviz.BankIjAll "$PWD/$W/work" "$PWD/release/dsviz.jar" | grep -v "^  ok   create-\|^  ok   run-"
(cd intellij-bank && gradle --no-daemon -q test buildPlugin)
mkdir -p build/lab-ij && rm -f build/lab-ij/*
cp intellij-bank/build/distributions/dsviz-bank-intellij-*.zip build/lab-ij/
cat > build/lab-ij/התקנה.txt <<'TXT'
מאגר תרגילים — גרסת בדיקה ל-IntelliJ IDEA (רק לך, לא לתלמידים)

צריך: DSViz מותקן ב-IntelliJ (התוסף הראשי).

התקנה:
  1. ב-IntelliJ: File → Settings → Plugins → גלגל השיניים (⚙) → Install Plugin from Disk...
  2. בוחרים את הקובץ dsviz-bank-intellij-1.0.0.zip, ומאשרים הפעלה מחדש של IntelliJ.
  3. בסרגל העליון (ליד "הרצה ויזואלית") ובתפריט Tools מופיעים "מאגר תרגילים" ו"השאלה".

בוחרים נושא ושאלה ולוחצים "צור תרגיל": נוצרת חבילה ex01_... בתיקיית הקוד (src) של הפרויקט, עם פעולה לכל סעיף ו-main שמריץ ומדפיס,
והקובץ נפתח בסעיף א. החלון הצף "השאלה" נשאר מקדימה; "לפעולה של סעיף ..." קופץ לפעולה; מריצים בכפתור "הרצה ויזואלית" של DSViz בסרגל העליון של IntelliJ.
"סיימתי את התרגיל": סימון ירוק, ונמחקים רק קבצי התרגיל (וה-class שלהם) — קבצים אחרים שלך נשארים.

הסרה: Settings → Plugins → "DSViz — Exercise Bank (lab)" → Uninstall. העדכון האוטומטי של DSViz לא נוגע בתוסף הזה.
ההתקדמות נשמרת בקובץ bank-progress-ij.json בתיקייה .dsviz שבתיקיית המשתמש.
TXT
echo "build/lab-ij/$(ls build/lab-ij | grep zip)"
