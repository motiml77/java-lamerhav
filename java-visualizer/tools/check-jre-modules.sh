#!/bin/sh
# בודק שקוד שרץ ב-Java של ההתקנה (tools/build-jre.sh — jlink, רק חלק מהמודולים) לא צריך מודול שלא נכנס אליה.
# כך נכשלה פעם הכניסה עם Google ב-Visual Studio: com.sun.net.httpserver (jdk.httpserver) — NoClassDefFoundError רק אצל המשתמש.
# grep על שם אחד לא מספיק (jdk.net, jdk.jsobject, java.smartcardio, jdk.crypto.mscapi...) — jdeps מוצא כל מודול שהמחלקות צריכות.
#   sh tools/check-jre-modules.sh <תיקיית מחלקות או jar> [classpath]
# בלי jdeps (JRE בלי JDK) — אזהרה בלבד.
set -e
cd "$(dirname "$0")/.."
TARGET="$1"
CP="$2"
[ -n "$TARGET" ] || { echo "שימוש: check-jre-modules.sh <classes> [classpath]" >&2; exit 2; }
JDEPS="${JDEPS:-jdeps}"
command -v "$JDEPS" > /dev/null 2>&1 || { echo "אזהרה: אין jdeps — לא נבדקו המודולים של $TARGET" >&2; exit 0; }
JAVA="${JAVA:-java}"
# המודולים שבהתקנה: מה ש-build-jre.sh מעביר ל-jlink, ו-java.se פרוש לכל המודולים שהוא כולל
LIST=$(sed -n 's/.*--add-modules \([^ ]*\).*/\1/p' tools/build-jre.sh | tr ',' ' ')
[ -n "$LIST" ] || { echo "שגיאה: לא נמצאה רשימת המודולים ב-tools/build-jre.sh" >&2; exit 1; }
ALLOWED="java.base"
for m in $LIST; do
    ALLOWED="$ALLOWED $m"
    if [ "$m" = java.se ]; then
        ALLOWED="$ALLOWED $("$JAVA" --describe-module java.se 2>/dev/null | awk '$1 == "requires" { print $2 }' | tr '\n' ' ')"
    fi
done
NEED=$("$JDEPS" --multi-release 21 --ignore-missing-deps --print-module-deps ${CP:+-cp "$CP"} "$TARGET" 2>/dev/null | tail -1 | tr ',' ' ')
[ -n "$NEED" ] || { echo "שגיאה: jdeps לא החזיר רשימת מודולים עבור $TARGET" >&2; exit 1; }
BAD=""
for m in $NEED; do
    case " $ALLOWED " in *" $m "*) ;; *) BAD="$BAD $m" ;; esac
done
if [ -n "$BAD" ]; then
    echo "שגיאה: $TARGET צריך מודולים שלא נמצאים ב-Java של ההתקנה (tools/build-jre.sh):$BAD" >&2
    "$JDEPS" --multi-release 21 --ignore-missing-deps -verbose:class ${CP:+-cp "$CP"} "$TARGET" 2>/dev/null | grep -E "$(echo $BAD | tr ' ' '|')" | head -20 >&2
    exit 1
fi
echo "מודולים ($TARGET): $(echo $NEED | tr ' ' ',') — כולם ב-Java של ההתקנה"
