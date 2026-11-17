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
javac -encoding UTF-8 -nowarn -d "$W/cls" eclipse-plugin/src/il/lamerhav/dsviz/AiScaffold.java access/src/il/lamerhav/dsviz/Json.java \
    eclipse-plugin/src/il/lamerhav/dsviz/SolutionCheck.java eclipse-plugin/src/il/lamerhav/dsviz/ExercisePipeline.java -sourcepath eclipse-plugin/src:access/src -cp release/dsviz.jar \
    eclipse-bank/src/il/lamerhav/dsviz/BankStore.java eclipse-bank/src/il/lamerhav/dsviz/BankProgress.java eclipse-bank/src/il/lamerhav/dsviz/BankSections.java \
    tools/test/bank/BankCheck.java tools/test/bank/BankCheckCs.java tools/test/bank/BankSchoolCs.java tools/test/bank/BankProgressTest.java tools/test/bank/BankSectionsCheck.java 2>&1 | grep -v "Picked up" || true
# פלט בלי "Picked up", וקוד היציאה של הבדיקה עצמה (לא של grep) — כשל עוצר את הסקריפט
run() { s=0; java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp "$W/cls:release/dsviz.jar" "$@" > "$W/out.txt" 2>&1 || s=$?; grep -v "Picked up" "$W/out.txt"; return $s; }
run il.lamerhav.dsviz.BankProgressTest
run il.lamerhav.dsviz.BankCheck "$BANK" release/dsviz.jar
# גרסת C# (Visual Studio): כל שאלה רצה ב-dsviz-cs ומדפיסה את הפלט הרצוי
if command -v dotnet >/dev/null 2>&1; then
    export DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
    dotnet build csharp/DsViz.Runner/DsViz.Runner.csproj -c Release -v q --nologo 2>&1 | grep -E " error " | head -3 || true
    run il.lamerhav.dsviz.BankCheckCs "$BANK" dotnet "$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/dsviz-cs.dll"
    # ...וגם בלי הספרייה של DSViz, מול Unit4.dll של בתי הספר (תלמיד שמעתיק תרגיל לפרויקט של בית הספר): כל שאלה נבנית ומדפיסה
    # את הפלט הרצוי — מול הגרסה מהתיקייה של המפקחים (מורדת), ומול גרסת המורים אם היא במחשב (build/school-unit4-teachers)
    . tools/test/helpers/school-unit4.sh
    # המהדר וה-reference assemblies של ‎.NET 8 — מתוך ה-SDK שמותקן ("8.0.134 [/usr/lib/dotnet/sdk]")
    SDKDIR=$(dotnet --list-sdks 2>/dev/null | grep '^8\.' | tail -1 | sed 's/^\([^ ]*\) \[\(.*\)\]$/\2\/\1/')
    CSC="$SDKDIR/Roslyn/bincore/csc.dll"; [ -f "$CSC" ] || CSC=""
    REF=$(ls -d "$(dirname "$(dirname "$SDKDIR")")"/packs/Microsoft.NETCore.App.Ref/8.*/ref/net8.0 2>/dev/null | sort -V | tail -1)
    if [ -n "$CSC" ] && [ -n "$REF" ] && SCHOOL=$(school_unit4 "$ROOT/build"); then
        run il.lamerhav.dsviz.BankSchoolCs "$BANK" "$SCHOOL" "$CSC" "$REF" "$W/school" | grep -v "^  ok "
        if TEACH=$(school_unit4_teachers "$ROOT/build"); then run il.lamerhav.dsviz.BankSchoolCs "$BANK" "$TEACH" "$CSC" "$REF" "$W/school-teachers" | grep -v "^  ok "; fi
    else
        echo "לא נבדק: המאגר מול Unit4.dll של בתי הספר (אין csc / ref של net8 / Unit4.dll)"; exit 1
    fi
    # "הכן סעיף ב": סעיף א לבד, ואחרי ההוספה — כל הפלט (Java ו-C#), גם עם CRLF / טאבים / קוד של התלמיד
    run il.lamerhav.dsviz.BankSectionsCheck "$BANK" release/dsviz.jar dotnet "$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/dsviz-cs.dll" | grep -v "^  ok "
else
    run il.lamerhav.dsviz.BankSectionsCheck "$BANK" release/dsviz.jar | grep -v "^  ok "

    echo "(אין dotnet — מדלגים על גרסת C#)"
fi
