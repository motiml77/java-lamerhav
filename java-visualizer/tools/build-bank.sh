#!/bin/sh
# "מאגר תרגילים" ל-Eclipse — גרסת בדיקה (fragment נפרד, לא חלק מההתקנה לתלמידים):
#   build/lab/il.lamerhav.dsviz.bank_1.0.0.jar  — מעתיקים לתיקיית dropins של Eclipse, ליד il.lamerhav.dsviz_1.0.1.jar
#   build/lab/התקנה.txt                         — איך מתקינים ומסירים
# לפני הבנייה כל שאלה במאגר נבדקת (tools/test/bank-check.sh) — שאלה שלא עוברת עוצרת את הבנייה.
# צריך את התוסף הראשי ב-release/ (tools/build-all.sh, או מה שכבר שם) ואת התלויות של Eclipse (tools/eclipse-deps.py).
set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
BUILDNO=${BUILDNO:-$(date -u +%Y%m%d%H%M)}
HOST=release/il.lamerhav.dsviz_1.0.1.jar
[ -f "$HOST" ] || { echo "חסר $HOST — מריצים קודם tools/build-all.sh"; exit 1; }
[ -f release/dsviz.jar ] || sh tools/build-lib.sh
sh tools/test/bank-check.sh

DEPS=build/eclipse-deps
[ -f "$DEPS/org.eclipse.ui.workbench.jar" ] && [ -f "$DEPS/org.eclipse.jdt.core.jar" ] || python3 tools/eclipse-deps.py "$DEPS"
OUT=build/bank-fragment
rm -rf "$OUT" && mkdir -p "$OUT/bank"
CP=$(ls $DEPS/*.jar | tr '\n' ':')$HOST
javac ${JAVAC_FLAGS:---release 8} -encoding UTF-8 -nowarn -cp "$CP" -d "$OUT" $(find eclipse-bank/src -name '*.java')
cp eclipse-bank/fragment.xml "$OUT/"
cp -r eclipse-bank/icons "$OUT/"
# המאגר: topics.json, השאלות, ו-index.json — רשימת הקבצים (בתוך jar אי אפשר "לרשום תיקייה")
cp bank/topics.json "$OUT/bank/"
LIST=""
for f in $(cd bank && find . -mindepth 2 -name '*.json' | sed 's|^\./||' | sort); do
    mkdir -p "$OUT/bank/$(dirname "$f")" && cp "bank/$f" "$OUT/bank/$f"
    LIST="$LIST${LIST:+, }\"$f\""
done
printf '{ "questions": [%s] }\n' "$LIST" > "$OUT/bank/index.json"
MF=build/bank-MANIFEST.MF
sed "s/^Bundle-Version: .*/Bundle-Version: 1.0.0.v$BUILDNO/" eclipse-bank/META-INF/MANIFEST.MF > "$MF"
mkdir -p build/lab
rm -f build/lab/il.lamerhav.dsviz.bank_*.jar
jar --create --file build/lab/il.lamerhav.dsviz.bank_1.0.0.jar --manifest "$MF" -C "$OUT" .
cat > build/lab/התקנה.txt <<'TXT'
מאגר תרגילים — גרסת בדיקה ל-Eclipse (רק לך, לא לתלמידים)

התקנה:
  1. סוגרים את Eclipse.
  2. מעתיקים את הקובץ il.lamerhav.dsviz.bank_1.0.0.jar לתיקייה dropins שבתוך תיקיית Eclipse
     (אותה תיקייה שבה כבר נמצא il.lamerhav.dsviz_1.0.1.jar של DSViz).
  3. פותחים את Eclipse. בסרגל של DSViz מופיע הכפתור "מאגר תרגילים".

הסרה: סוגרים את Eclipse, מוחקים את il.lamerhav.dsviz.bank_1.0.0.jar מ-dropins, ופותחים שוב.

העדכון האוטומטי של DSViz לא מוחק את הקובץ הזה.
ה-V ("כבר עשיתי") נשמר בקובץ bank-progress.json בתיקייה .dsviz שבתיקיית המשתמש.
שאלה חדשה או מתוקנת אפשר לנסות בלי בנייה חדשה: שמים אותה בתיקייה .dsviz/bank/<נושא>/ (למשל .dsviz/bank/stack/).
TXT
echo "build/lab/il.lamerhav.dsviz.bank_1.0.0.jar (Bundle-Version 1.0.0.v$BUILDNO, $(echo "$LIST" | tr ',' '\n' | grep -c json) שאלות)"
