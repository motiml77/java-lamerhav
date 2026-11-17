#!/bin/sh
# "צור מחלקה" (בתוך "הוסף מבנה נתונים") — המחולל של Java (ClassGen: Eclipse, IntelliJ, Visual Studio) ושל VS Code (classgen.js):
#   1. הבדיקות של כל אחד (Student, טיפוסים, הודעות על קלט שגוי, החלון של VS Code)
#   2. אותם מקרים (classgen-cases.txt) — אותו קוד ואותן הודעות בשתי הגרסאות, תו-תו
#   3. כל מחלקה תקינה מתקמפלת ורצה עם Main שקורא לבנאי, לכל get / set ול-toString: Java מול dsviz.jar, C# מול Unit4.CollectionsLib.dll
# שימוש: sh classgen-tests.sh [תיקיית עבודה]   (C# — רק אם יש dotnet)
set -e
cd "$(dirname "$0")"
ROOT=$(cd ../.. && pwd)
W=${1:-$ROOT/build/test/classgen}
JAR="$ROOT/release/dsviz.jar"
rm -rf "$W" && mkdir -p "$W/cls"
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -d "$W/cls" "$ROOT/eclipse-plugin/src/il/lamerhav/dsviz/ClassGen.java" ClassGenTest.java
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "$W/cls" ClassGenTest classgen-cases.txt "$W/java" classgen-project
node classgen-test.js classgen-cases.txt "$W" classgen-project
diff -r "$W/java" "$W/js" > "$W/diff.txt" || { echo "ההפקה של Java ושל JS שונה:"; head -40 "$W/diff.txt"; exit 1; }
echo "  ok  Java ו-JS: אותו קוד ואותן הודעות ב-$(ls "$W/js" | wc -l) מקרים"

# --- Java: כל מחלקה + CheckMain, מול הספרייה
nj=0
for d in "$W"/compile/*/; do
    [ "$(cat "$d/lang.txt")" = java ] || continue
    javac -nowarn -encoding UTF-8 -cp "$JAR" -d "$d/out" $(find "$d/src" -name '*.java') > "$d/javac.txt" 2>&1 \
        || { echo "לא מתקמפל: $d"; cat "$d/javac.txt"; find "$d/src" -name '*.java' -exec cat {} \; ; exit 1; }
    java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$d/out:$JAR" "$(cat "$d/main.txt")" > "$d/run.txt" 2>&1 \
        || { echo "נכשל בהרצה: $d"; cat "$d/run.txt"; exit 1; }
    grep -q '^OK ' "$d/run.txt" || { echo "חסר OK: $d"; cat "$d/run.txt"; exit 1; }
    nj=$((nj+1))
done
grep -qx 'Student: name=xyz, grade=8, active=false' "$W/compile/1/run.txt" || { echo "toString של Student (Java):"; cat "$W/compile/1/run.txt"; exit 1; }
grep -qx 'Library: waiting=\[\], returned=\[\], first=null, tree=null, shelves=\[0, 0\], grid=\[null, null\]' "$W/compile/6/run.txt" || { echo "toString עם מערכים (Java):"; cat "$W/compile/6/run.txt"; exit 1; }
grep -qx 'Team: best=Student Dan, line=\[Student Noa\], cars=\[Car Mazda\]' "$W/compile/scan-java/run.txt" || { echo "Team עם מחלקות מהפרויקט (Java):"; cat "$W/compile/scan-java/run.txt"; exit 1; }
echo "  ok  Java: $nj מחלקות התקמפלו ורצו (בנאי, get / set לכל שדה, toString; גם עם Student / Queue<Student> / Car[] מהפרויקט ו-import)"

# --- הכלי של Visual Studio: הקבצים (פרויקט ישן / SDK, BOM ו-CRLF, namespace), ואז החלון עצמו מקצה לקצה (Swing, אם יש Xvfb)
T="$W/vstool"
mkdir -p "$T/cls"
E="$ROOT/eclipse-plugin/src/il/lamerhav/dsviz"
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -cp "$JAR" -d "$T/cls" "$E/Scaffold.java" "$E/NameCheck.java" "$E/CsSolution.java" "$E/ClassGen.java" \
    "$E/WorkerClient.java" "$E/ExerciseFiles.java" "$E/AiScaffold.java" "$E/SolutionCheck.java" "$E/SolutionStore.java" "$E/ExercisePipeline.java" "$E/SubmitCli.java" \
    $(find "$ROOT/csharp/new-exercise/src" -name '*.java') "$ROOT/tutorials/harness/SwingScript.java" cstool/il/lamerhav/dsviz/cs/CsClassFileTest.java
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "$T/cls" il.lamerhav.dsviz.cs.CsClassFileTest "$T/work" classgen-project | sed 's/^/  ok  /'
if command -v Xvfb >/dev/null 2>&1; then
    # פרויקט ‎.NET Framework‎ (csproj בלי Sdk): ממלאים את החלון, "יצירת המחלקה" — Student.cs נוצר ליד Program.cs, באותו namespace, ונרשם ב-csproj
    P="$T/e2e/OldApp"
    mkdir -p "$P"
    printf '<?xml version="1.0" encoding="utf-8"?>\r\n<Project ToolsVersion="15.0">\r\n  <ItemGroup>\r\n    <Compile Include="Program.cs" />\r\n  </ItemGroup>\r\n</Project>\r\n' > "$P/OldApp.csproj"
    printf 'using System;\r\n\r\nnamespace OldApp\r\n{\r\n    class Program\r\n    {\r\n        static void Main() { }\r\n    }\r\n}\r\n' > "$P/Program.cs"
    printf 'wait 700\ncall demo Student|string name;int grade;bool active\ncall withToString 1\nwait 400\nshotpop form\nclick יצירת המחלקה\nwaitwin צור מחלקה\nshot done\nclose\n' > "$T/e2e.txt"
    (Xvfb :96 -screen 0 1600x1000x24 >/dev/null 2>&1 &)
    sleep 2
    DISPLAY=:96 timeout 90 java -Ddsviz.noOpen=true -Dstdout.encoding=UTF-8 -cp "$T/cls:$JAR" SwingScript "$T/e2e.txt" "$T/shots" il.lamerhav.dsviz.cs.CsTool class "$T/e2e" --file "$P/Program.cs" > "$T/e2e.out" 2>&1 || true
    grep -q 'המחלקה Student נוצרה' "$T/shots/done.json" && grep -q 'נרשם בקובץ הפרויקט' "$T/shots/done.json" && grep -q 'OldApp.csproj' "$T/shots/done.json" \
        && grep -q '<Compile Include="Student.cs" />' "$P/OldApp.csproj" && grep -q 'public override string ToString()' "$P/Student.cs" \
        && head -c 40 "$P/Student.cs" | grep -q 'namespace OldApp' \
        && echo "  ok  Visual Studio (Swing): החלון → Student.cs ליד Program.cs (namespace OldApp), נרשם ב-OldApp.csproj, והודעה בעברית" \
        || { echo "הכלי של Visual Studio — צור מחלקה נכשל"; cat "$T/e2e.out"; cat "$T/shots/done.json" 2>/dev/null | head -c 600; cat "$P/OldApp.csproj"; exit 1; }
else
    echo "  (אין Xvfb — מדלגים על החלון של Visual Studio)"
fi

# --- C#: csc של ה-SDK, מול Unit4.CollectionsLib.dll
if ! command -v dotnet >/dev/null 2>&1; then echo "  (אין dotnet — מדלגים על הקומפילציה של C#)"; exit 0; fi
export DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
LIB="$W/cs-lib"
dotnet build "$ROOT/csharp/Unit4.CollectionsLib/Unit4.CollectionsLib.csproj" -c Release -o "$LIB" -v q --nologo > "$W/lib-build.txt" 2>&1 || { cat "$W/lib-build.txt"; exit 1; }
SDKLINE=$(dotnet --list-sdks | grep '^8\.' | tail -1)
SDK="$(echo "$SDKLINE" | sed 's/.*\[\(.*\)\]/\1/')/$(echo "$SDKLINE" | cut -d' ' -f1)"
DOTROOT=$(dirname "$(dirname "$SDK")")
REFDIR=$(ls -d "$DOTROOT"/packs/Microsoft.NETCore.App.Ref/8.*/ref/net8.0 | tail -1)
RT=$(dotnet --list-runtimes | grep '^Microsoft.NETCore.App 8\.' | tail -1 | cut -d' ' -f2)
REFS=$(for f in "$REFDIR"/*.dll; do printf ' -r:%s' "$f"; done)
nc=0
for d in "$W"/compile/*/; do
    [ "$(cat "$d/lang.txt")" = cs ] || continue
    # shellcheck disable=SC2086
    dotnet "$SDK/Roslyn/bincore/csc.dll" -nologo -nowarn:8632 -target:exe -out:"$d/check.dll" $REFS -r:"$LIB/Unit4.CollectionsLib.dll" "$d"/*.cs > "$d/csc.txt" 2>&1 \
        || { echo "לא מתקמפל (C#): $d"; cat "$d/csc.txt"; for f in "$d"/*.cs; do echo "--- $f"; cat "$f"; done; exit 1; }
    cp "$LIB/Unit4.CollectionsLib.dll" "$d/"
    printf '{"runtimeOptions":{"tfm":"net8.0","framework":{"name":"Microsoft.NETCore.App","version":"%s"}}}\n' "$RT" > "$d/check.runtimeconfig.json"
    dotnet "$d/check.dll" > "$d/run.txt" 2>&1 || { echo "נכשל בהרצה (C#): $d"; cat "$d/run.txt"; exit 1; }
    grep -q '^OK ' "$d/run.txt" || { echo "חסר OK (C#): $d"; cat "$d/run.txt"; exit 1; }
    nc=$((nc+1))
done
grep -qx 'Student: name=xyz, grade=8, active=False' "$W/compile/2/run.txt" || { echo "ToString של Student (C#):"; cat "$W/compile/2/run.txt"; exit 1; }
grep -q ', shelves=\[0, 0\], grid=System.Int32\[,\]' "$W/compile/7/run.txt" || { echo "ToString עם מערך (C#):"; cat "$W/compile/7/run.txt"; exit 1; }
grep -qx 'Team: best=Student Dan, line=\[Student Noa\], cars=\[Car Mazda\]' "$W/compile/scan-cs/run.txt" || { echo "Team עם מחלקות מהפרויקט (C#):"; cat "$W/compile/scan-cs/run.txt"; exit 1; }
echo "  ok  C#: $nc מחלקות התקמפלו ורצו (בנאי, Get / Set לכל שדה, ToString; גם עם Student / Queue<Student> / Car[] מהפרויקט ו-using)"
