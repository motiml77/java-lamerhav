#!/bin/sh
# מאגר התרגילים (bank/): כל שאלה נבדקת לפני שהיא נכנסת לתוסף — הקובץ תקין, המעטפת של התלמיד מתקמפלת, ההצעות לפתרון מדפיסות
# בדיוק את הפלט הרצוי, וההרצה הוויזואלית מגיעה לסוף; וגם ה-V (התקדמות) נשמר ונקרא כמו שצריך.
#   sh tools/test/bank-check.sh [תיקיית מאגר — ברירת מחדל: bank/]      (צריך release/dsviz.jar — tools/build-lib.sh)
set -e
cd "$(dirname "$0")/../.."
ROOT=$(pwd)
BANK=${1:-$ROOT/bank}
W=$ROOT/build/bank-check
[ -f release/dsviz.jar ] || sh tools/build-lib.sh
rm -rf "$W" && mkdir -p "$W/cls"
javac -encoding UTF-8 -nowarn -d "$W/cls" eclipse-plugin/src/il/lamerhav/dsviz/AiScaffold.java eclipse-plugin/src/il/lamerhav/dsviz/Json.java \
    eclipse-plugin/src/il/lamerhav/dsviz/SolutionCheck.java eclipse-plugin/src/il/lamerhav/dsviz/ExercisePipeline.java -sourcepath eclipse-plugin/src -cp release/dsviz.jar \
    eclipse-bank/src/il/lamerhav/dsviz/BankStore.java eclipse-bank/src/il/lamerhav/dsviz/BankProgress.java \
    tools/test/bank/BankCheck.java tools/test/bank/BankCheckCs.java tools/test/bank/BankProgressTest.java 2>&1 | grep -v "Picked up" || true
# פלט בלי "Picked up", וקוד היציאה של הבדיקה עצמה (לא של grep) — כשל עוצר את הסקריפט
run() { s=0; java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp "$W/cls:release/dsviz.jar" "$@" > "$W/out.txt" 2>&1 || s=$?; grep -v "Picked up" "$W/out.txt"; return $s; }
run il.lamerhav.dsviz.BankProgressTest
run il.lamerhav.dsviz.BankCheck "$BANK" release/dsviz.jar
# גרסת C# (Visual Studio): כל שאלה רצה ב-dsviz-cs ומדפיסה את הפלט הרצוי
if command -v dotnet >/dev/null 2>&1; then
    export DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
    dotnet build csharp/DsViz.Runner/DsViz.Runner.csproj -c Release -v q --nologo 2>&1 | grep -E " error " | head -3 || true
    run il.lamerhav.dsviz.BankCheckCs "$BANK" dotnet "$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/dsviz-cs.dll"
else
    echo "(אין dotnet — מדלגים על גרסת C#)"
fi
