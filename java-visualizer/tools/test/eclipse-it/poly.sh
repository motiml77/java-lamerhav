#!/bin/sh
# הורשה ופולימורפיזם בהרצה הוויזואלית — בתוך Eclipse אמיתי, כמו תלמיד (PolyIt): פרויקט חדש, כל מחלקה בקובץ משלה
# (tools/test/objects/poly/pets/Pets.java מפוצל ל-Animal / Dog / Cat / Person / Collar / Pets), "הרצה ויזואלית" על Pets.java.
# בחלון ההרצה: לחיצות על "הבא" (XTest) עד pet = d (פסים: "לא נראה דרך pet") ועד pet.say() (החלונית "מותר? / מה רץ?"),
# וצילומי מסך. אחרי tools/build-all.sh:   sh tools/test/eclipse-it/poly.sh [תיקיית eclipse]
set -e
cd "$(dirname "$0")"
ROOT="$(cd ../../.. && pwd)"
B="$ROOT/build/eclipse-it"
ECL="${1:-$B/eclipse}"
[ -d "$ECL/plugins" ] || { echo "אין Eclipse ב-$ECL (run.sh מוריד אותו)"; exit 1; }
P="$B/poly"
rm -rf "$P" "$B/ws-poly" && mkdir -p "$P/cls" "$P/src" "$P/shots"
unset JAVA_TOOL_OPTIONS
python3 "$ROOT/tools/test/helpers/split-classes.py" "$ROOT/tools/test/objects/poly/pets/Pets.java" "$P/src"
javac ${JAVAC_FLAGS:---release 11} -encoding UTF-8 -nowarn -cp "$(ls "$ECL"/plugins/*.jar | tr '\n' ':')$(ls "$ROOT"/release/il.lamerhav.dsviz_*.jar)" -d "$P/cls" src/il/lamerhav/dsviz/it/*.java
cp plugin.xml "$P/cls/"
jar --create --file "$P/il.lamerhav.dsviz.it_1.0.0.jar" --manifest META-INF/MANIFEST.MF -C "$P/cls" .
rm -f "$ECL"/dropins/il.lamerhav.dsviz*.jar
cp "$P/il.lamerhav.dsviz.it_1.0.0.jar" "$ROOT"/release/il.lamerhav.dsviz_*.jar "$ECL/dropins/"
XD=${XD:-:73}
pgrep -f "Xvfb $XD " >/dev/null || { (Xvfb $XD -screen 0 1600x1000x24 >/dev/null 2>&1 &); sleep 2; }
export DISPLAY=$XD
java -Doomph.setup.skip=true -Dfile.encoding=UTF-8 -jar "$ECL"/plugins/org.eclipse.equinox.launcher_*.jar -nosplash -clean -consoleLog -data "$B/ws-poly" \
    -application il.lamerhav.dsviz.it.poly "$P/out.txt" "$P/src" "$P/shots" > "$P/eclipse.log" 2>&1 &
EP=$!
K="python3 $ROOT/tools/test/vscode-real/xkeys.py $XD"
FAILED=0
ok() { echo "  ok  $1"; }
bad() { echo "  FAIL $1"; FAILED=1; }
i=0; while [ ! -f "$P/shots/editor.flag" ] && [ $i -lt 240 ]; do sleep 1; i=$((i+1)); done
sleep 1; import -display $XD -window root "$P/shots/1-eclipse.png" 2>/dev/null || true
# החלון של ההרצה הוויזואלית (תהליך Java נפרד, ש-Eclipse מפעיל)
i=0; while ! $K 'הרצה ויזואלית — מבני נתונים' Shift_L 0 >/dev/null 2>&1 && [ $i -lt 120 ]; do sleep 1; i=$((i+1)); done
if $K 'הרצה ויזואלית — מבני נתונים' Shift_L 0 >/dev/null 2>&1; then ok "Eclipse: \"הרצה ויזואלית\" פתחה את החלון"; else bad "Eclipse: חלון ההרצה הוויזואלית לא נפתח"; fi
sleep 6
# "הבא" (לחיצות עכבר אמיתיות) — צעד 32: Animal pet = d; צעד 33: Dog.say() דרך pet
$K 'הרצה ויזואלית — מבני נתונים' CLICK:-135,26 31 200 >/dev/null || true; sleep 2
import -display $XD -window root "$P/shots/2-pet-equals-d.png" 2>/dev/null || true
$K 'הרצה ויזואלית — מבני נתונים' CLICK:-135,26 1 200 >/dev/null || true; sleep 2
import -display $XD -window root "$P/shots/3-pet-say.png" 2>/dev/null || true
touch "$P/shots/done.flag"
i=0; while kill -0 $EP 2>/dev/null && [ $i -lt 60 ]; do sleep 1; i=$((i+1)); done
pkill -f "LineByLine Pets" 2>/dev/null || true
cat "$P/out.txt" 2>/dev/null
grep -q '^FILES 6$' "$P/out.txt" && ok "פרויקט עם 6 קבצים (מחלקה בכל קובץ)" || bad "הקבצים לא נוצרו"
grep -q '^BUILD errors=0$' "$P/out.txt" && ok "Eclipse בנה את הפרויקט בלי שגיאות" || bad "שגיאות בנייה"
grep -q '^RUN-HANDLED true$' "$P/out.txt" && grep -q '^RUN-SENT$' "$P/out.txt" && ok "הפקודה \"הרצה ויזואלית\" (כמו הכפתור בסרגל)" || bad "הפקודה לא רצה"
for s in 1-eclipse 2-pet-equals-d 3-pet-say; do [ -s "$P/shots/$s.png" ] || bad "אין צילום $s"; done
echo "צילומים: $P/shots"
exit $FAILED
