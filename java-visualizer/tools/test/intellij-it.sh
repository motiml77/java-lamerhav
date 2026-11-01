#!/bin/sh
# בדיקות התוסף ל-IntelliJ IDEA (צריך Gradle, JDK 21, ובפעם הראשונה אינטרנט — מוריד את IntelliJ 2024.2 שמולו בודקים):
#   1. gradle test — בתוך IntelliJ "חי" בלי מסך: ספריית הקורס נוספת לבד למודול, "הוסף מבנה נתונים", הכפתור ליד main,
#      הפעולות בתפריטים (ו"הצעה לפתרון" רק ליד פעולות עם הצעה), החלונות, והודעות javac בעברית;
#      ומקצה לקצה: הפקודה שהתוסף בונה מריצה את LineByLine על Main.java ומפיקה צעדים, ו"שליחת תרגיל" בלי שרת — הודעה בעברית.
#   2. gradle buildPlugin verifyPlugin — הזיפ של התוסף, ו-Plugin Verifier של JetBrains מול IntelliJ 2024.2.
#   3. intellij-installer-test.sh — ההתקנה האוטומטית (installer/mac/intellij.sh) בתיקיות מדומות.
# IDEA_DIR=<IntelliJ מחולץ> — בודקים מולו בלי להוריד. VERIFY_IDES=dir1,dir2 — עוד גרסאות ל-Plugin Verifier.
set -e
cd "$(dirname "$0")/../../intellij-plugin"
[ -f ../release/dsviz.jar ] || sh ../tools/build-lib.sh
gradle --no-daemon -q ${IDEA_DIR:+-PideDir=$IDEA_DIR} ${VERIFY_IDES:+-PverifyIdeDirs=$VERIFY_IDES} test buildPlugin verifyPlugin
echo "IntelliJ: הבדיקות עברו, והתוסף עבר את Plugin Verifier"
bash ../tools/test/intellij-installer-test.sh build/distributions/dsviz-intellij-*.zip
