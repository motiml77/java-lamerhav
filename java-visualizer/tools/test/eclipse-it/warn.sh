#!/bin/sh
# אזהרות בהרצה הוויזואלית — בתוך Eclipse אמיתי, כמו תלמיד: פרויקט חדש עם Pets.java (לולאה שהתנאי שלה לא מתקיים — "הלולאה לא רצה
# אף לא פעם אחת"; רקורסיה עם תנאי עצירה שגוי sum(n - 2) — "הרקורסיה לא נעצרת"), "הרצה ויזואלית" (אותה פקודה כמו הכפתור בסרגל),
# לחיצות עכבר אמיתיות על "הבא" וצילומי מסך של חלון ההרצה (התהליך הנפרד שנבנה מ-release/dsviz.jar). אחרי tools/build-all.sh:
#   sh tools/test/eclipse-it/warn.sh [תיקיית eclipse]
set -e
cd "$(dirname "$0")"
ROOT="$(cd ../../.. && pwd)"
B="$ROOT/build/eclipse-it"
ECL="${1:-$B/eclipse}"
[ -d "$ECL/plugins" ] || { echo "אין Eclipse ב-$ECL (run.sh מוריד אותו)"; exit 1; }
P="$B/warn"
rm -rf "$P" "$B/ws-warn" && mkdir -p "$P/cls" "$P/src" "$P/shots"
unset JAVA_TOOL_OPTIONS
cat > "$P/src/Pets.java" <<'JAVA'
public class Pets {
    static int sum(int n) {
        if (n == 0)
            return 0;
        return n + sum(n - 2);
    }

    public static void main(String[] args) {
        int i = 10;
        while (i < 5) {
            i++;
        }
        System.out.println("אחרי הלולאה");
        System.out.println(sum(5));
    }
}
JAVA
javac ${JAVAC_FLAGS:---release 11} -encoding UTF-8 -nowarn -cp "$(ls "$ECL"/plugins/*.jar | tr '\n' ':')$(ls "$ROOT"/release/il.lamerhav.dsviz_*.jar)" -d "$P/cls" src/il/lamerhav/dsviz/it/*.java
cp plugin.xml "$P/cls/"
jar --create --file "$P/il.lamerhav.dsviz.it_1.0.0.jar" --manifest META-INF/MANIFEST.MF -C "$P/cls" .
rm -f "$ECL"/dropins/il.lamerhav.dsviz*.jar
cp "$P/il.lamerhav.dsviz.it_1.0.0.jar" "$ROOT"/release/il.lamerhav.dsviz_*.jar "$ECL/dropins/"
XD=${XD:-:73}
pgrep -f "Xvfb $XD " >/dev/null || { (Xvfb $XD -screen 0 1600x1000x24 >/dev/null 2>&1 &); sleep 2; }
export DISPLAY=$XD
java -Doomph.setup.skip=true -Dfile.encoding=UTF-8 -jar "$ECL"/plugins/org.eclipse.equinox.launcher_*.jar -nosplash -clean -consoleLog -data "$B/ws-warn" \
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
# "הבא" (לחיצות עכבר אמיתיות): צילום אחרי כל אחד מהצעדים הראשונים (int i, while, println, println(sum)), ואז עמוק בתוך הרקורסיה
n=0
for c in 1 1 1 1 1; do
    $K 'הרצה ויזואלית — מבני נתונים' CLICK:-135,26 $c 200 >/dev/null || true; sleep 2; n=$((n+1))
    import -display $XD -window root "$P/shots/step-$n.png" 2>/dev/null || true
done
for c in 12 8 8 8; do
    $K 'הרצה ויזואלית — מבני נתונים' CLICK:-135,26 $c 200 >/dev/null || true; sleep 3; n=$((n+1))
    import -display $XD -window root "$P/shots/step-$n.png" 2>/dev/null || true
done
touch "$P/shots/done.flag"
i=0; while kill -0 $EP 2>/dev/null && [ $i -lt 60 ]; do sleep 1; i=$((i+1)); done
pkill -f "LineByLine Pets" 2>/dev/null || true
cat "$P/out.txt" 2>/dev/null
grep -q '^FILES 1$' "$P/out.txt" && ok "פרויקט עם Pets.java" || bad "הקבצים לא נוצרו"
grep -q '^BUILD errors=0$' "$P/out.txt" && ok "Eclipse בנה את הפרויקט בלי שגיאות" || bad "שגיאות בנייה"
grep -q '^RUN-HANDLED true$' "$P/out.txt" && grep -q '^RUN-SENT$' "$P/out.txt" && ok "הפקודה \"הרצה ויזואלית\" (כמו הכפתור בסרגל)" || bad "הפקודה לא רצה"
for s in 1-eclipse step-1 step-5 step-9; do [ -s "$P/shots/$s.png" ] || bad "אין צילום $s"; done
echo "צילומים: $P/shots"
exit $FAILED
