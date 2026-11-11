#!/bin/sh
# בונה את build/access/dsviz-access.jar — הכניסה המשותפת לכל התוכנות (access/README.md):
#   Account (כניסה עם Google, עמדה, השמירה המוצפנת בתיקייה הנסתרת), AccessGate (מסך הכניסה), AccessWatch (שינויים
#   מתוכנה אחרת), OsTrust, Json. נבנה פעם אחת, ונכנס כמו שהוא ל-dsviz.jar, לתוסף של Eclipse, ל-IntelliJ ולמאגר של Visual Studio.
# Java 8, ורק מודולים שבתוך ה-Java של ההתקנה (check-jre-modules.sh). מדלג אם ה-jar חדש מכל קובצי המקור (FORCE=1 — תמיד).
set -e
cd "$(dirname "$0")/.."
JAR=build/access/dsviz-access.jar
SRC=$(find access/src -name '*.java')
if [ "$FORCE" != 1 ] && [ -f "$JAR" ] && [ -z "$(find access/src tools/build-access.sh -newer "$JAR" -type f)" ]; then
    echo "$JAR"
    exit 0
fi
if grep -l 'com\.sun\.net\.httpserver' $SRC; then
    echo "שגיאה: הקבצים למעלה משתמשים ב-com.sun.net.httpserver — הוא לא קיים ב-Java של ההתקנה (משתמשים ב-ServerSocket)" >&2
    exit 1
fi
OUT=build/access/cls
rm -rf "$OUT" && mkdir -p "$OUT"
javac --release 8 -Xlint:-options -encoding UTF-8 -nowarn -d "$OUT" $SRC
sh tools/check-jre-modules.sh "$OUT"
printf 'Implementation-Title: DSViz access (shared sign-in)\nImplementation-Vendor: Moti Levi (motiml77@gmail.com)\n' > build/access/MANIFEST.MF
rm -f "$JAR"
jar --create --file "$JAR" --manifest build/access/MANIFEST.MF -C "$OUT" .
rm -rf "$OUT"
echo "$JAR"
