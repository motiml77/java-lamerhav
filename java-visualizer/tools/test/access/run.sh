#!/bin/sh
# בדיקות הכניסה של הלקוח (Account / AccessGate — Eclipse, IntelliJ, Visual Studio) מהמקור, בלי release/:
#   SignInEdge     — מקרי קצה מול שרת מדומה (בלי אמולטור)
#   OsTrustTest    — סינון אינטרנט שבודק תעבורה מוצפנת: תעודת שורש רק ב"מאגר מערכת ההפעלה" (בלי אמולטור)
#   AccountIt      — עמדה / מורה / תלמיד מול השרת המקומי והאמולטור
#   GateUi         — מסך הכניסה (Swing, ב-xvfb)
#   TeacherPanelIt — עמדת הניהול של המורה והתנתקות (Swing, ב-xvfb)
#   ShareIt        — כניסה משותפת בין שני תהליכים (Eclipse ↔ Visual Studio / "מודלים חישוביים"): שינוי מיידי, נעילה
# מריצים מתוך worker:  npm run test:access-java   (מפעיל את האמולטור ואת השרת המקומי)
# צילומי מסך: $ACC_SHOTS (ברירת מחדל: תיקייה זמנית)
set -e
cd "$(dirname "$0")/../../.."
R="$(pwd)"
W="${ACC_WORK:-$(mktemp -d)}"
SHOTS="${ACC_SHOTS:-$W/shots}"
LIB="$W/lib"
CLS="$W/cls"
mkdir -p "$LIB" "$CLS" "$SHOTS"
export JAVA_TOOL_OPTIONS=
E=eclipse-plugin/src/il/lamerhav/dsviz
# הכניסה המשותפת — אותו dsviz-access.jar שנכנס לכל התוכנות (tools/build-access.sh)
FORCE=1 sh tools/build-access.sh > /dev/null
ACC="$R/build/access/dsviz-access.jar"
javac --release 8 -Xlint:-options -encoding UTF-8 -nowarn -d "$LIB" $(find src/unit4 -name '*.java' ! -name JdiTracer.java)
(cd "$LIB" && unzip -qo "$ACC" -x 'META-INF/*')
javac --release 8 -Xlint:-options -encoding UTF-8 -nowarn -cp "$LIB" -d "$LIB" $E/Scaffold.java $E/NameCheck.java $E/CsSolution.java \
    $E/WorkerClient.java $E/ExerciseFiles.java $E/AiScaffold.java $E/SolutionCheck.java \
    $E/SolutionStore.java $E/ExercisePipeline.java $E/SubmitCli.java $(find csharp/new-exercise/src -name '*.java')
javac -encoding UTF-8 -nowarn -cp "$LIB" -d "$CLS" tools/test/access/*.java
# המאגר של Visual Studio (BankVs teacher — הכפתור "עמדת ניהול" בסרגל), כמו tools/build-bank-vs.sh
VSB="$W/vsb"
mkdir -p "$VSB"
javac --release 8 -Xlint:-options -encoding UTF-8 -nowarn -cp "$LIB" -d "$VSB" $(find vs-bank/src -name '*.java') \
    eclipse-bank/src/il/lamerhav/dsviz/BankStore.java eclipse-bank/src/il/lamerhav/dsviz/BankProgress.java eclipse-bank/src/il/lamerhav/dsviz/BankSections.java \
    $E/CsSolution.java
(cd "$VSB" && unzip -qo "$ACC" -x 'META-INF/*')
sh tools/check-jre-modules.sh "$VSB" "$LIB"
CP="$CLS:$VSB:$LIB"
J="java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp $CP"
rc=0
echo "=== SignInEdge"
$J il.lamerhav.dsviz.SignInEdge "$W/edge" || rc=1
xrun() { if [ -n "$DISPLAY" ]; then "$@"; else xvfb-run -a -s "-screen 0 1600x1000x24" "$@"; fi; }
echo "=== OsTrustTest"
xrun $J il.lamerhav.dsviz.OsTrustTest "$W/ostrust" "$SHOTS/OsTrustTest" || rc=1
SERVER=http://127.0.0.1:8787
PORT=8787 ADMIN_EMAILS=motiml77@gmail.com node "$R/worker/tools/dev-server.mjs" > "$W/dev-server.log" 2>&1 &
DEV=$!
trap 'kill $DEV 2>/dev/null' EXIT
for i in $(seq 100); do curl -s "$SERVER/v1/access/config" > /dev/null && break; sleep 0.2; done
for t in AccountIt GateUi TeacherPanelIt ShareIt; do
    echo "=== $t"
    case $t in
        AccountIt|ShareIt) $J -Djava.awt.headless=true il.lamerhav.dsviz.$t $SERVER 127.0.0.1:9099 "$W/$t" || rc=1 ;;
        *) xrun $J il.lamerhav.dsviz.$t $SERVER 127.0.0.1:9099 "$W/$t" "$SHOTS/$t" || rc=1 ;;
    esac
done
echo "צילומים: $SHOTS"
[ -n "$ACC_WORK" ] || rm -rf "$LIB" "$CLS" "$VSB"
exit $rc
