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
W=$(cd "$W" && pwd)   # נתיב מלא — יש שלבים שעובדים מתוך תיקייה אחרת (cd)
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

# --- היררכיה מוכנה ועץ משלכם (Java): כל הקבצים + ה-main לדוגמה מתקמפלים ורצים — בפרויקט רגיל (בלי הספרייה ובלי import),
#     בפרויקט של DSViz (עם import unit4.collectionsLib.*; בכל קובץ, בלי package ועם package) — וההרצה הוויזואלית מראה את אותם
#     צבעים שהחלונות מציגים (ClassGen.colors) ואת הקופסאות זו בתוך זו
tree_fail() { echo "היררכיה ($1): $2"; for f in "$1"/*.txt; do echo "--- $f"; head -c 3000 "$f"; done; exit 1; }
nh=0; nv=0
for d in "$W"/trees/java-*/; do
    d=${d%/}; mode=$(cat "$d/mode.txt"); main=$(cat "$d/main.txt")
    if [ "$mode" = plain ]; then
        javac -nowarn -encoding UTF-8 -d "$d/out" $(find "$d/src" -name '*.java') > "$d/javac.txt" 2>&1 || tree_fail "$d" "לא מתקמפל בלי הספרייה"
        java -Dstdout.encoding=UTF-8 -cp "$d/out" "$main" > "$d/run.txt" 2>&1 || tree_fail "$d" "נכשל בהרצה"
    else
        javac -nowarn -encoding UTF-8 -cp "$JAR" -d "$d/out" $(find "$d/src" -name '*.java') > "$d/javac.txt" 2>&1 || tree_fail "$d" "לא מתקמפל מול dsviz.jar"
        java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$d/out:$JAR" "$main" > "$d/run.txt" 2>&1 || tree_fail "$d" "נכשל בהרצה"
    fi
    while IFS= read -r want; do [ -z "$want" ] || grep -qF -- "$want" "$d/run.txt" || tree_fail "$d" "חסר בפלט: $want"; done < "$d/expect.txt"
    nh=$((nh+1))
    if [ -f "$d/visual.txt" ]; then
        (cd "$d/src" && javac -g -nowarn -encoding UTF-8 -cp "$JAR" -d . *.java && timeout 120 java -Djava.awt.headless=true -Ddsviz.conditions=true \
            -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine "$main" > "$d/visual-out.txt" 2>&1) || true
        grep -F '[מחלקות: ' "$d/visual-out.txt" | head -1 > "$d/classes.txt"
        grep -F '[קופסאות' "$d/visual-out.txt" > "$d/boxes.txt" || true
        while IFS= read -r tok; do
            case "$tok" in
                C\ *) grep -qF -- "${tok#C }" "$d/classes.txt" || tree_fail "$d" "הצבע בהרצה שונה מהחלון: ${tok#C }" ;;
                B\ *) grep -qF -- "${tok#B }" "$d/boxes.txt" || tree_fail "$d" "חסרה קופסה בתוך קופסה: ${tok#B }" ;;
            esac
        done < "$d/visual.txt"
        nv=$((nv+1))
    fi
done
echo "  ok  היררכיות (Java): $nh פרויקטים התקמפלו ורצו (בלי הספרייה / עם import, בלי package / עם); $nv הרצות ויזואליות — אותם צבעים כמו בחלון, קופסאות זו בתוך זו"

# --- הכלי של Visual Studio: הקבצים (פרויקט ישן / SDK, BOM ו-CRLF, namespace), ואז החלון עצמו מקצה לקצה (Swing, אם יש Xvfb)
T="$W/vstool"
mkdir -p "$T/cls"
E="$ROOT/eclipse-plugin/src/il/lamerhav/dsviz"
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -cp "$JAR" -d "$T/cls" "$E/Scaffold.java" "$E/NameCheck.java" "$E/CsSolution.java" "$E/ClassGen.java" "$E/PolyPanels.java" \
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
    # הורשה בכלי של Visual Studio: "היררכיה מוכנה" בפרויקט SDK שכבר יש בו Main (ה-main לדוגמה — Run()), "צור מחלקה" שיורשת מ-Person
    # ודורסת את GetName (נוספת virtual בקובץ של Person), "הוסף עצם" (הקוד בלוח) — ובסוף הפרויקט כולו נבנה ורץ
    S="$T/e2e/SdkApp"
    mkdir -p "$S" "$ROOT/build/shots"
    printf '<Project Sdk="Microsoft.NET.Sdk">\n  <PropertyGroup>\n    <OutputType>Exe</OutputType>\n    <TargetFramework>net8.0</TargetFramework>\n  </PropertyGroup>\n</Project>\n' > "$S/SdkApp.csproj"
    printf 'namespace SdkApp;\n\npublic static class Program\n{\n    public static void Main()\n    {\n        ZooDemo.Run();\n        System.Console.WriteLine(new Guide("Hila", 3).GetName());\n    }\n}\n' > "$S/Program.cs"
    printf 'wait 900\ncall choose 0\nwait 500\nshot tree\nclick יצירת המחלקות\nwaitwin היררכיה\nshot treedone\nclose\n' > "$T/tree.txt"
    printf 'wait 900\ncall demo Guide|int years\nwait 300\ncall superOf Person\nwait 400\ncall pick GetName()\nwait 300\ncall method string Greet(string who)\nwait 400\nshot inherit\nclick יצירת המחלקה\nwaitwin צור מחלקה\nshot inheritdone\nclose\n' > "$T/inherit.txt"
    printf 'wait 900\ncall demo Animal|a1|Boxer|Rexy, male, 3.2, 3, house\nwait 500\nshot object\ncall demoArray Animal|zoo|Dog:Dasty, female, 0.5, 3/Boxer:Rexy, male, 3.2, 3, house/:/Labrador:Toto, male, 2.5, 4, new Person("Hila")\nwait 500\nshot objectarr\nclick העתקת הקוד\nwaitwin הוסף עצם\nshot objectdone\nclose\n' > "$T/object.txt"
    for m in tree inherit object; do
        mode=$m; [ $m = inherit ] && mode=class
        DISPLAY=:96 timeout 90 java -Ddsviz.noOpen=true -Ddsviz.printCode=1 -Dstdout.encoding=UTF-8 -cp "$T/cls:$JAR" SwingScript "$T/$m.txt" "$T/shots" il.lamerhav.dsviz.cs.CsTool $mode "$T/e2e" --file "$S/Program.cs" > "$T/$m.out" 2>&1 || true
    done
    grep -q 'נוצרו 7 קבצים' "$T/shots/treedone.json" && grep -q 'ZooDemo.Run();' "$T/shots/treedone.json" && grep -q 'public static void Run()' "$S/ZooDemo.cs" \
        && grep -q 'public class Boxer : Dog' "$S/Boxer.cs" && grep -q 'namespace SdkApp;' "$S/Boxer.cs" \
        && grep -q 'המחלקה Guide נוצרה' "$T/shots/inheritdone.json" && grep -q 'נוספה המילה virtual בקובץ' "$T/shots/inheritdone.json" \
        && grep -q 'public virtual string GetName()' "$S/Person.cs" && grep -q 'public override string GetName()' "$S/Guide.cs" && grep -q ' : base(name)' "$S/Guide.cs" \
        && grep -q 'הקוד הועתק' "$T/shots/objectdone.json" && grep -qxF 'Animal[] zoo = new Animal[4];' "$T/object.out" \
        && grep -qxF 'zoo[3] = new Labrador("Toto", "male", 2.5, 4, new Person("Hila"));' "$T/object.out" && ! grep -q 'zoo\[2\]' "$T/object.out" \
        || { echo "הכלי של Visual Studio — הורשה נכשלה"; for o in tree inherit object; do tail -n 20 "$T/$o.out"; done; ls "$S"; exit 1; }
    for s in tree inherit object; do cp "$T/shots/$s.png" "$ROOT/build/shots/vs-$s.png"; done
    if command -v dotnet >/dev/null 2>&1; then
        (cd "$S" && DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1 dotnet build -c Release -v q --nologo > "$T/sdkapp-build.txt" 2>&1 && dotnet bin/Release/net8.0/SdkApp.dll > "$T/sdkapp-run.txt" 2>&1) \
            && grep -qx 'I am a Boxer' "$T/sdkapp-run.txt" && grep -qx 'Hila' "$T/sdkapp-run.txt" \
            || { echo "SdkApp (אחרי היררכיה מוכנה + צור מחלקה) לא נבנה / לא רץ"; cat "$T/sdkapp-build.txt" "$T/sdkapp-run.txt" | tail -20; exit 1; }
    fi
    echo "  ok  Visual Studio (Swing): היררכיה מוכנה (7 קבצים, Run() כשיש Main), צור מחלקה שיורשת (base, override, virtual בקובץ של מחלקת העל), הוסף עצם (גם מערך) — והפרויקט נבנה ורץ"
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

# --- היררכיה מוכנה ועץ משלכם (C#): בפרויקט רגיל (בלי Unit4 ובלי using), עם כל שורות ה-using של בית הספר (גם classes ב-namespace אחר
#     ו-Program שעושה using לשניהם), בתוך תיקיית התרגילים של DSViz (dotnet build, Directory.Build.props) ובפרויקט SDK רגיל — וההרצה
#     הוויזואלית (dsviz-cs) מראה את אותם צבעים כמו בחלון ואת הקופסאות זו בתוך זו
dotnet build "$ROOT/csharp/DsViz.Runner/DsViz.Runner.csproj" -c Release -v q --nologo > "$W/runner-build.txt" 2>&1 || { cat "$W/runner-build.txt"; exit 1; }
RUNNER="$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/dsviz-cs.dll"
nh=0; nv=0
for d in "$W"/trees/cs-*/; do
    d=${d%/}; mode=$(cat "$d/mode.txt")
    case "$mode" in
        plain|dsviz)
            lib=""; [ "$mode" = dsviz ] && lib="-r:$LIB/Unit4.CollectionsLib.dll"
            # shellcheck disable=SC2086
            dotnet "$SDK/Roslyn/bincore/csc.dll" -nologo -target:exe -out:"$d/app.dll" $REFS $lib "$d"/src/*.cs > "$d/csc.txt" 2>&1 || tree_fail "$d" "לא מתקמפל ($mode)"
            [ "$mode" = dsviz ] && cp "$LIB/Unit4.CollectionsLib.dll" "$d/"
            printf '{"runtimeOptions":{"tfm":"net8.0","framework":{"name":"Microsoft.NETCore.App","version":"%s"}}}\n' "$RT" > "$d/app.runtimeconfig.json"
            dotnet "$d/app.dll" > "$d/run.txt" 2>&1 || tree_fail "$d" "נכשל בהרצה" ;;
        project)
            # תיקיית התרגילים של DSViz: Directory.Build.props (using static של Helpers) + Exercise (Program.cs עם using Unit4.CollectionsLib;)
            P="$d/proj"
            mkdir -p "$P/lib" && cp "$ROOT/csharp/project/Directory.Build.props" "$P/" && cp -r "$ROOT/csharp/project/Exercise" "$P/" && cp "$LIB/Unit4.CollectionsLib.dll" "$P/lib/"
            rm -rf "$P/Exercise/bin" "$P/Exercise/obj" && cp "$d"/src/*.cs "$P/Exercise/"
            sed -i "s|^    static void Main(string\[\] args)\$|    static void Main(string[] args)\n    {\n        $(cat "$d/main.txt").Run();\n    }\n    static void Old(string[] args)|" "$P/Exercise/Program.cs"
            (cd "$P/Exercise" && dotnet build -c Release -v q --nologo > "$d/build.txt" 2>&1 && dotnet bin/Release/net8.0/Exercise.dll > "$d/run.txt" 2>&1) || tree_fail "$d" "תיקיית התרגילים של DSViz" ;;
        sdk)
            P="$d/sdk"
            mkdir -p "$P" && cp "$d"/src/*.cs "$P/"
            printf '<Project Sdk="Microsoft.NET.Sdk">\n  <PropertyGroup>\n    <OutputType>Exe</OutputType>\n    <TargetFramework>net8.0</TargetFramework>\n  </PropertyGroup>\n</Project>\n' > "$P/Plain.csproj"
            (cd "$P" && dotnet build -c Release -v q --nologo > "$d/build.txt" 2>&1 && dotnet bin/Release/net8.0/Plain.dll > "$d/run.txt" 2>&1) || tree_fail "$d" "פרויקט SDK רגיל" ;;
    esac
    while IFS= read -r want; do [ -z "$want" ] || grep -qF -- "$want" "$d/run.txt" || tree_fail "$d" "חסר בפלט: $want"; done < "$d/expect.txt"
    nh=$((nh+1))
    if [ -f "$d/visual.txt" ]; then
        dotnet "$RUNNER" "$d/src" --trace "$d/viz.trace" > "$d/viz.out" 2>&1 || true
        java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$d/viz.trace" > "$d/visual-out.txt" 2>&1 || true
        grep -F '[מחלקות: ' "$d/visual-out.txt" | head -1 > "$d/classes.txt"
        grep -F '[קופסאות' "$d/visual-out.txt" > "$d/boxes.txt" || true
        while IFS= read -r tok; do
            case "$tok" in
                C\ *) grep -qF -- "${tok#C }" "$d/classes.txt" || tree_fail "$d" "הצבע בהרצה שונה מהחלון: ${tok#C }" ;;
                B\ *) grep -qF -- "${tok#B }" "$d/boxes.txt" || tree_fail "$d" "חסרה קופסה בתוך קופסה: ${tok#B }" ;;
            esac
        done < "$d/visual.txt"
        nv=$((nv+1))
    fi
done
echo "  ok  היררכיות (C#): $nh פרויקטים התקמפלו ורצו (בלי Unit4, עם כל ה-using של בית הספר, namespace אחר, תיקיית התרגילים של DSViz, פרויקט SDK); $nv הרצות ויזואליות (dsviz-cs) — אותם צבעים כמו בחלון, קופסאות זו בתוך זו"
