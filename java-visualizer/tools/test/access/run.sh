#!/bin/sh
# בדיקות הכניסה של הלקוח (Account / AccessGate — Eclipse, IntelliJ, Visual Studio) מהמקור, בלי release/:
#   SignInEdge     — מקרי קצה מול שרת מדומה (בלי אמולטור)
#   StationKeep    — "עמדה שמשתחררת לבד": מזהה המחשב, בחירת קובץ העמדה, תשובות השרת (בלי אמולטור)
#   Golden         — "קובץ הזהב": עדכון / התקנה מחדש / קובץ פגום / תקלות / התנתקות / שחרור עמדה / העברה מהמקום הישן,
#                    בסידור התיקיות של Windows ושל Mac, וסריקה של installer/ (בלי אמולטור)
#   OsTrustTest    — סינון אינטרנט שבודק תעבורה מוצפנת: תעודת שורש רק ב"מאגר מערכת ההפעלה" (בלי אמולטור)
#   AccountIt      — עמדה / מורה / תלמיד מול השרת המקומי והאמולטור
#   GateUi         — מסך הכניסה (Swing, ב-xvfb)
#   TeacherPanelIt — עמדת הניהול של המורה והתנתקות (Swing, ב-xvfb)
#   SubmitIt       — "הגשה למורה" מחלון השאלה (BankVsFloat): בדיקת ההרצה, השליחה, מה שהמורה רואה, "סיימתי", עמדה, בלי אינטרנט
#   ShareIt        — כניסה משותפת בין שני תהליכים (Eclipse ↔ Visual Studio / "מודלים חישוביים"): שינוי מיידי, נעילה
#   AutomataIt     — "מודלים חישוביים" (AutomataLauncher, תהליך נפרד): אבטחת השרת המקומי, הממשק לדף, כניסה / התנתקות משותפות
# מריצים מתוך worker:  npm run test:access-java   (מפעיל את האמולטור ואת השרת המקומי)
# צילומי מסך: $ACC_SHOTS (ברירת מחדל: תיקייה זמנית)
# ACC_SERVER_ROOT — מאיפה מריצים את השרת המקומי (ברירת מחדל: העץ הזה). השרת שרץ בפועל נבנה מהענף של מרכז הבקרה, ולכן
#   את הרשימות בעמדת הניהול (מחשבי המעבדה) בודקים גם מולו: ACC_SERVER_ROOT=<העץ של מרכז הבקרה>/java-visualizer ACC_ONLY=TeacherPanelIt
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
javac --release 8 -Xlint:-options -encoding UTF-8 -nowarn -cp "$LIB" -d "$LIB" $E/Scaffold.java $E/NameCheck.java $E/CsSolution.java $E/ClassGen.java $E/PolyPanels.java \
    $E/WorkerClient.java $E/ExerciseFiles.java $E/AiScaffold.java $E/SolutionCheck.java $E/RunCheck.java \
    $E/SolutionStore.java $E/ExercisePipeline.java $E/SubmitCli.java $(find csharp/new-exercise/src -name '*.java')
# המאגר של Visual Studio (BankVs teacher — הכפתור "עמדת ניהול" בסרגל), כמו tools/build-bank-vs.sh
VSB="$W/vsb"
mkdir -p "$VSB"
javac --release 8 -Xlint:-options -encoding UTF-8 -nowarn -cp "$LIB" -d "$VSB" $(find vs-bank/src -name '*.java') \
    eclipse-bank/src/il/lamerhav/dsviz/BankStore.java eclipse-bank/src/il/lamerhav/dsviz/BankClasses.java eclipse-bank/src/il/lamerhav/dsviz/BankSubmit.java eclipse-bank/src/il/lamerhav/dsviz/BankProgress.java eclipse-bank/src/il/lamerhav/dsviz/BankSections.java \
    $E/CsSolution.java
(cd "$VSB" && unzip -qo "$ACC" -x 'META-INF/*')
sh tools/check-jre-modules.sh "$VSB" "$LIB"
# הבדיקות עצמן — אחרי המאגר: SubmitIt בודקת את "הגשה למורה" בחלון השאלה (BankVsFloat)
javac -encoding UTF-8 -nowarn -cp "$LIB:$VSB" -d "$CLS" tools/test/access/*.java
# "מודלים חישוביים": המשגר (כמו tools/build-automata.sh), והאפליקציה — מהענף אם יש (automata/app), אחרת דף קטן עם access-client.js
AUT="$W/automata"
mkdir -p "$AUT/cls"
javac --release 11 -encoding UTF-8 -nowarn -cp "$LIB" -d "$AUT/cls" $(find automata-launcher/src -name '*.java')
cp automata/app/js/access-client.js "$AUT/cls/il/lamerhav/dsviz/"
APP="${AUTOMATA_APP:-automata/app}"
if [ ! -f "$APP/index.html" ]; then
    APP="$AUT/app" && mkdir -p "$APP/js" && cp automata/app/js/access-client.js "$APP/js/"
    printf '<!doctype html><html lang="he" dir="rtl"><meta charset="utf-8"><title>מודלים חישוביים</title><body><h1>מודלים חישוביים</h1></body></html>\n' > "$APP/index.html"
fi
CP="$CLS:$VSB:$LIB"
J="java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp $CP"
rc=0
echo "=== SignInEdge"
$J il.lamerhav.dsviz.SignInEdge "$W/edge" || rc=1
echo "=== StationKeep"
$J il.lamerhav.dsviz.StationKeep "$W/keep" || rc=1
echo "=== Golden"
$J -Djava.awt.headless=true il.lamerhav.dsviz.Golden "$W/golden" "$R" || rc=1
xrun() { if [ -n "$DISPLAY" ]; then "$@"; else xvfb-run -a -s "-screen 0 1600x1000x24" "$@"; fi; }
echo "=== OsTrustTest"
xrun $J il.lamerhav.dsviz.OsTrustTest "$W/ostrust" "$SHOTS/OsTrustTest" || rc=1
SERVER=http://127.0.0.1:8787
PORT=8787 ADMIN_EMAILS=motiml77@gmail.com node "${ACC_SERVER_ROOT:-$R}/worker/tools/dev-server.mjs" > "$W/dev-server.log" 2>&1 &
DEV=$!
trap 'kill $DEV 2>/dev/null' EXIT
for i in $(seq 100); do curl -s "$SERVER/v1/access/config" > /dev/null && break; sleep 0.2; done
for t in AccountIt GateUi TeacherPanelIt SubmitIt ShareIt AutomataIt; do
    case " ${ACC_ONLY:-$t} " in *" $t "*) ;; *) continue ;; esac   # ACC_ONLY="ShareIt AutomataIt" — רק אלה
    echo "=== $t"
    case $t in
        AutomataIt) $J -Djava.awt.headless=true -Dautomata.cp="$AUT/cls:$CP" il.lamerhav.dsviz.$t $SERVER 127.0.0.1:9099 "$W/$t" "$APP" || rc=1 ;;
        AccountIt|ShareIt) $J -Djava.awt.headless=true il.lamerhav.dsviz.$t $SERVER 127.0.0.1:9099 "$W/$t" || rc=1 ;;
        SubmitIt) xrun $J il.lamerhav.dsviz.$t $SERVER 127.0.0.1:9099 "$W/$t" "$SHOTS/$t" "$LIB" "$R/bank" || rc=1 ;;
        *) xrun $J il.lamerhav.dsviz.$t $SERVER 127.0.0.1:9099 "$W/$t" "$SHOTS/$t" || rc=1 ;;
    esac
done
echo "צילומים: $SHOTS"
[ -n "$ACC_WORK" ] || rm -rf "$LIB" "$CLS" "$VSB" "$AUT"
exit $rc
