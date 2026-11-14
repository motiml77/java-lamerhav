#!/bin/sh
# חלון ההרצה הוויזואלית כפי שהתוסף של IntelliJ מפעיל אותו: unit4.collectionsLib.LineByLine עם ה-dsviz.jar שבתוך התוסף (runtime/dsviz.jar),
# על מסך וירטואלי (xvfb) — מתקדמים ב"הבא" ומצלמים. מריצים בתוך xvfb-run:
#   xvfb-run -a -s "-screen 0 1700x1000x24" sh tools/test/ij-view-shots.sh <תיקיית עבודה עם Calls.class> <dsviz.jar> "<מספרי צעדים>" <קידומת לצילומים>
# תוצאה: <קידומת>-<צעד>.png, והלוג run.log בתיקיית העבודה. נכשל אם החלון לא נפתח.
set -e
W="$1"; JAR="$2"; STEPS="$3"; PREFIX="$4"
cd "$W"
java -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine Calls > run.log 2>&1 &
PID=$!
WID=""
for i in $(seq 1 40); do
  WID=$(xdotool search --name "הרצה ויזואלית" 2>/dev/null | head -1 || true)
  [ -n "$WID" ] && break
  sleep 1
done
[ -n "$WID" ] || { echo "חלון ההרצה הוויזואלית לא נפתח"; cat run.log; kill $PID 2>/dev/null || true; exit 1; }
sleep 3
xdotool windowsize "$WID" 1600 900; xdotool windowmove "$WID" 0 0; sleep 1
i=0
for n in $STEPS; do
  while [ "$i" -lt "$n" ]; do xdotool mousemove 1444 25 click 1; i=$((i+1)); sleep 0.4; done
  sleep 1.5
  import -window root "$PREFIX-$n.png"
  echo "צילום: $PREFIX-$n.png"
done
kill $PID 2>/dev/null || true
! grep -q "Exception in thread" run.log || { echo "חריגה בחלון ההרצה"; cat run.log; exit 1; }
