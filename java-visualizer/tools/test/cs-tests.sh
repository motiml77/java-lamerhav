#!/bin/sh
# בדיקות C#: התבניות (כל תרגיל וכל קטע קוד מתקמפלים מול הספרייה), ההרצה הוויזואלית (dsviz-cs) והחלון (TraceViewer).
# דורש ‎.NET SDK 8. מריצים אחרי tools/build-all.sh (או לבד: sh tools/test/cs-tests.sh)
set -e
cd "$(dirname "$0")"
ROOT=$(cd ../.. && pwd)
W="$ROOT/build/test-cs"
JAR="$ROOT/release/dsviz.jar"
export DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
if ! command -v dotnet >/dev/null 2>&1; then echo "dotnet לא מותקן — מדלגים על בדיקות C#"; exit 0; fi
rm -rf "$W" && mkdir -p "$W"
dotnet build "$ROOT/csharp/DsViz.Runner/DsViz.Runner.csproj" -c Release -v q --nologo 2>&1 | grep -E "error|Build succeeded" | head -3
RUNNER="$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/dsviz-cs.dll"
viz() { dotnet "$RUNNER" "$@"; }

echo "== 6. תבניות C#: כל התרגילים וכל קטעי הקוד מתקמפלים"
node cs-combos.js "$W/gen" | tail -1
mkdir -p "$W/chk"
for f in "$W"/gen/C*.cs; do cp "$f" "$W/chk/"; done
n=0
for f in "$W"/gen/S*.snippet; do
    n=$((n+1))
    { echo "using System;"; echo "using Unit4.CollectionsLib;"; echo "public class Snip$n { public static void Main(string[] args) {"; cat "$f"; echo "} }"; } > "$W/chk/Snip$n.cs"
done
viz --check "$W"/chk/*.cs | tail -1 | grep -q ' 0 עם שגיאות' && echo "$(ls "$W/chk" | wc -l) קבצי C# התקמפלו" || { viz --check "$W"/chk/*.cs | tail -20; exit 1; }
for c in C1 C500 C1000 C1900 Snip1 Snip150; do
    viz "$W/chk/$c.cs" --trace "$W/$c.trace" > "$W/$c.out" 2>&1
    ! grep -q '^STEP	ERROR' "$W/$c.trace" || { echo "נכשל: $c"; cat "$W/$c.out"; exit 1; }
done
echo "הרצת דוגמאות C#: תקין"

echo "== 7. הרצה ויזואלית של C# (dsviz-cs): כל הדוגמאות, החזרות, שגיאות, והחלון קורא את הקובץ"
for p in "$ROOT"/csharp/project/Exercise "$ROOT"/csharp/project/Examples/*; do
    n=$(basename "$p")
    viz "$p" --trace "$W/$n.trace" > "$W/$n.out" 2>&1 || { echo "נכשל: $n"; cat "$W/$n.out"; exit 1; }
    java -Djava.awt.headless=true -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/$n.trace" | grep -q '^ok [1-9]' || { echo "החלון לא קרא את $n"; exit 1; }
done
# שגיאות: Pop על מחסנית ריקה, ו-null (ב-C# ההודעה לא אומרת מי null — מנחשים מהשורה)
for n in EmptyPop:'[כרטיס empty-stack s Pop()]' NullNode:'[כרטיס null p GetValue()]'; do
    c=${n%%:*}; want=${n#*:}
    viz "problems/cs/$c.cs" --trace "$W/$c.trace" > "$W/$c.out" 2>&1 || true
    java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/$c.trace" | grep -qF "$want" \
        && echo "כרטיס שגיאה ב-C#: $want" || { echo "חסר כרטיס ב-$c"; cat "$W/$c.out"; exit 1; }
done
# תנאים: אותן תוצאות כמו ב-Java (ב-C# הערכים נרשמים בשורת ה-for אחרי ה-i++ — עדיין מוצג מה שנבדק)
viz conds/cs/Conds.cs --trace "$W/conds.trace" > "$W/conds.out" 2>&1 || { cat "$W/conds.out"; exit 1; }
java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/conds.trace" > "$W/conds.txt" 2>&1
for want in 'שורה 24 if: x % 2 == 0 | 7 % 2 == 0 | x=7 | false עוברים ל-else]' 'שורה 28: else של שורה 24]' \
    'while: !s.IsEmpty() | !true | s=Stack [] | false יוצאים מהלולאה]' 'for: i < 2 | 0 < 2 | i=0 | true ממשיכים לסיבוב]' \
    'for: i < 2 | 2 < 2 | i=2 | false יוצאים מהלולאה]' 'do: k > 1 | 1 > 1 | k=1 | false יוצאים מהלולאה]' \
    'שורה 7 if: x < 0 | 0 < 0 | x=0 | false עוברים ל-else if]' 'שורה 12: else של שורה 9]' \
    'רץ ה-else if — התנאים בשורות 56, 58 לא התקיימו, והתנאי בשורה 60 התקיים]' 'רץ ה-else — אף תנאי לא התקיים (שורות 56, 58, 60)]'; do
    grep -qF -- "$want" "$W/conds.txt" || { echo "תנאים ב-C#: חסר $want"; grep '^  \[' "$W/conds.txt"; exit 1; }
done
echo "תנאים ב-C#: $(grep -c '^  \[תנאי' "$W/conds.txt") בדיקות תנאי מסומנות"
# פעולת עזר: יציאה וחזרה (ב-C# אחרי החזרה ממשיכים ישר לשורה הבאה)
viz calls/cs/Calls.cs --trace "$W/calls.trace" > "$W/calls.out" 2>&1 || { cat "$W/calls.out"; exit 1; }
java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/calls.trace" > "$W/calls.txt" 2>&1
for want in 'ENTERED שורה 7: SumEven() > IsEven() > Rem() | יצאנו מ-IsEven() (שורה 11) לפעולת העזר Rem() עם x = 8, d = 2' \
    'RETURNING שורה 11: SumEven() > IsEven() | IsEven() מחזירה true — חוזרים ל-SumEven() בשורה 30' \
    'RETURNED שורה 31: SumEven() | חזרנו ל-SumEven() מהקריאה בשורה 30 — IsEven() החזירה true, ממשיכים בשורה 31' \
    'RETURNING שורה 22: MaxOf() > Report() | Report() הסתיימה — חוזרים ל-MaxOf() בשורה 46'; do
    grep -qF -- "$want" "$W/calls.txt" || { echo "קריאות ב-C#: חסר $want"; grep 'קריאה' "$W/calls.txt"; exit 1; }
done
echo "פעולות עזר ב-C#: $(grep -c 'קריאה #' "$W/calls.txt") צעדים מסומנים"
# פעולה שרצה בתוך אובייקט: this הוא d1, המאפיינים שהשורה קוראת / משנה (כמו ב-Java)
viz objects/cs/Pets.cs --trace "$W/pets.trace" > "$W/pets.out" 2>&1 || { cat "$W/pets.out"; exit 1; }
java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/pets.trace" > "$W/pets.txt" 2>&1
for want in 'this=d1 ב-הבנאי Dog() {name=null, age=0} this.name=שינוי]' 'נכנסים ל-d1.Birthday()]' \
    'this=d1 ב-Birthday() {name="רקס", age=3} this.age=שינוי]' 'this=d1 ב-Bark() {name="רקס", age=4} this.name=קריאה]' \
    'this=d2 ב-IsOlderThan() {name="לאסי", age=5} other←d1 this.age=קריאה other.age=קריאה]'; do
    grep -qF -- "$want" "$W/pets.txt" || { echo "אובייקט ב-C#: חסר $want"; grep 'אובייקט' "$W/pets.txt"; exit 1; }
done
echo "פעולה בתוך אובייקט ב-C#: $(grep -c 'אובייקט #' "$W/pets.txt") צעדים מסומנים"
# סוגי רקורסיה: אותו סיווג כמו ב-Java
for spec in 'Fib:רקורסיה כפולה | פעולות Fib | עומק 4 | קריאות 9' 'EvenOdd:רקורסיה הדדית | פעולות IsEven, IsOdd' \
    'SumTail:רקורסיית זנב | פעולות Sum | עומק 5' 'ListLen:רקורסיה פשוטה | פעולות Length | עומק 4 | קריאות 4 | על שרשרת חוליות'; do
    c=${spec%%:*}; want=${spec#*:}
    viz recursion/cs/$c.cs --trace "$W/rec-$c.trace" > "$W/rec-$c.out" 2>&1 || { cat "$W/rec-$c.out"; exit 1; }
    java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/rec-$c.trace" > "$W/rec-$c.txt" 2>&1
    grep -qF -- "[רקורסיה: $want" "$W/rec-$c.txt" && echo "רקורסיה ב-C#: $c — $want" || { echo "רקורסיה ב-C#: $c חסר $want"; grep 'רקורסיה:' "$W/rec-$c.txt"; exit 1; }
done
# פרויקט רגיל של Visual Studio: קובץ אחד מתוך פרויקט (עם Program.cs של תבנית חדשה), כמה Main, ImplicitUsings, ותוכנית top-level
VS="$W/vs/GoldWater"
mkdir -p "$VS" && cp calls/vs/GoldWater.csproj calls/vs/Recursion.cs "$VS/" && printf '// See https://aka.ms/new-console-template\nSystem.Console.WriteLine("Hello, World!");\n' > "$VS/Program.cs"
viz "$VS/Recursion.cs" --trace "$W/vs1.trace" > "$W/vs1.out" 2>&1 && grep -q '^10 2$' "$W/vs1.out" \
    && echo "קובץ מתוך פרויקט של Visual Studio: רץ ה-Main שבקובץ (עם כל הפרויקט)" || { echo "קובץ מתוך פרויקט נכשל"; cat "$W/vs1.out"; exit 1; }
viz "$VS" --trace "$W/vs2.trace" > "$W/vs2.out" 2>&1 && grep -q 'Hello, World!' "$W/vs2.out" \
    && echo "תיקיית הפרויקט: כמו ב-Visual Studio (ה-top-level הוא ה-Main)" || { echo "תיקיית פרויקט נכשלה"; cat "$W/vs2.out"; exit 1; }
printf 'using System;\nnamespace GoldWater { class Program { static void Main(string[] args) { Console.WriteLine("x"); } } }\n' > "$VS/Program.cs"
viz "$VS" --trace "$W/vs3.trace" > "$W/vs3.out" 2>&1 || true; grep -q 'CS0017' "$W/vs3.out" && grep -q 'יותר מפעולת Main אחת' "$W/vs3.out" \
    && echo "כמה Main: הסבר בעברית" || { echo "כמה Main: חסר הסבר"; cat "$W/vs3.out"; exit 1; }
sed -i 's/<ImplicitUsings>disable/<ImplicitUsings>enable/' "$VS/GoldWater.csproj"
viz "$VS/Recursion.cs" --trace "$W/vs4.trace" > "$W/vs4.out" 2>&1 || true; grep -q 'ImplicitUsings>disable' "$W/vs4.out" \
    && echo "Stack של ‎.NET מול Stack של הספרייה: הסבר בעברית" || { echo "ImplicitUsings: חסר הסבר"; cat "$W/vs4.out"; exit 1; }
mkdir -p "$W/vs/Top" && cp calls/vs/Top.cs "$W/vs/Top/Program.cs" && printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework></PropertyGroup></Project>\n' > "$W/vs/Top/Top.csproj"
viz "$W/vs/Top" --trace "$W/top.trace" > "$W/top.out" 2>&1 && grep -q '^8$' "$W/top.out" \
    && grep -qP '^STEP\tLINE\t.*שורה 4:' "$W/top.trace" && grep -qP '^STEP\tCREATE\t.*מחסנית ריקה: s$' "$W/top.trace" \
    && grep -qP '^STEP\tRETURN\t.*Sum' "$W/top.trace" \
    && echo "תוכנית top-level (תבנית חדשה): שורה-שורה, שמות המשתנים, והפעולות שאחרי הקוד" || { echo "top-level נכשל"; cat "$W/top.out"; grep '^STEP' "$W/top.trace" | head; exit 1; }
# "שליחת תרגיל" + "הצעה לפתרון" ל-Visual Studio: אותו מנוע כמו ב-Eclipse (SubmitCli / VsSubmit), מול שרת מדומה
SUB="$W/submit"; mkdir -p "$SUB/home" && cp -r "$ROOT/csharp/project" "$SUB/root" && cp ai/files/photo.jpg "$SUB/"
node submit/mock-worker.js 8796 submit/cs-stack.json & MOCKW=$!
sleep 1
printf '{"lang":"csharp","out":"%s","store":"%s","cs":["dotnet","%s"],"note":"","items":[{"path":"%s","name":"דף התרגיל.jpg"}]}' \
    "$SUB/root" "$SUB/solutions.json" "$RUNNER" "$SUB/photo.jpg" > "$SUB/m.json"
DSVIZ_WORKER_URL=http://127.0.0.1:8796 java -cp "$JAR" il.lamerhav.dsviz.SubmitCli submit --manifest "$SUB/m.json" > "$SUB/out.txt" 2>&1 || true
grep -q '"done":true' "$SUB/out.txt" && grep -q '"solutionStatus":"ok"' "$SUB/out.txt" && grep -q 'Ex01_Stacks.csproj' "$SUB/root/DataStructures-CS.sln" \
    && [ "$(grep -c 'כאן עונים' "$SUB/root/Exercises/Ex01_Stacks/StackTargil.cs")" = 3 ] && ! grep -q 'count++' "$SUB/root/Exercises/Ex01_Stacks/StackTargil.cs" \
    && grep -q '"status":"ok"' "$SUB/solutions.json" \
    && echo "שליחת תרגיל ב-C#: פרויקט Ex01_Stacks בפתרון, בלי פתרון בקוד, וההצעות נבדקו (dsviz-cs)" || { echo "שליחת תרגיל ב-C# נכשלה"; cat "$SUB/out.txt"; kill $MOCKW; exit 1; }
# החלונות עצמם (Swing), אם יש מסך מדומה
if command -v Xvfb >/dev/null 2>&1; then
    (Xvfb :97 -screen 0 1600x900x24 >/dev/null 2>&1 &); sleep 2
    mkdir -p "$SUB/cls" && javac -d "$SUB/cls" "$ROOT/tutorials/harness/SwingScript.java"
    rm -rf "$SUB/root2" && cp -r "$ROOT/csharp/project" "$SUB/root2"
    printf 'pasteimg %s\ntext 0 רק שאלה 1\nshot s1\nclick שליחה ויצירת התרגיל\nwaitwin שליחת תרגיל — C#\nshot s2\n' "$SUB/photo.jpg" > "$SUB/s1.txt"
    DISPLAY=:97 DSVIZ_WORKER_URL=http://127.0.0.1:8796 DSVIZ_CS="$RUNNER" timeout 120 java -Duser.home="$SUB/home" -Ddsviz.noOpen=true -cp "$SUB/cls:$JAR" \
        SwingScript "$SUB/s1.txt" "$SUB/shots" il.lamerhav.dsviz.cs.CsTool submit "$SUB/root2" > "$SUB/gui.txt" 2>&1 || true
    printf 'select 1\nshot l1\n' > "$SUB/s2.txt"
    DISPLAY=:97 timeout 60 java -Duser.home="$SUB/home" -cp "$SUB/cls:$JAR" SwingScript "$SUB/s2.txt" "$SUB/shots" il.lamerhav.dsviz.cs.CsTool solution "$SUB/root2" >> "$SUB/gui.txt" 2>&1 || true
    grep -q '"שליחה ויצירת התרגיל"' "$SUB/shots/s1.json" && grep -q 'נוצר התרגיל Ex01_Stacks' "$SUB/shots/s2.json" \
        && grep -q 'נבדק: הרצנו את main' "$SUB/shots/l1.json" && ! grep -q 'Moti Levi' "$SUB/shots/l1.json" \
        && echo "החלונות של Visual Studio: שליחת תרגיל → פרויקט, והצעה לפתרון (נבדקה)" || { echo "החלונות של Visual Studio נכשלו"; cat "$SUB/gui.txt"; kill $MOCKW; exit 1; }
fi
kill $MOCKW
# Helpers.Size / Helpers.Duplicate מהספרייה: בלי לשנות את המבנה, עותק עמוק, וצעד אחד לכל קריאה
viz helpers/HelpersCheck.cs --trace "$W/helpers.trace" > "$W/helpers.out" 2>&1 && grep -q MARK-END "$W/helpers.out" \
    && grep -qP '^STEP\tCREATE\t.*Duplicate\(s\)' "$W/helpers.trace" && echo "Helpers ב-C#: $(grep -c '^  ok' "$W/helpers.out") בדיקות" \
    || { echo "Helpers ב-C# נכשל"; cat "$W/helpers.out"; exit 1; }
# בלי "Helpers." ובלי שורת using: Size(q) / Duplicate(q) — global using (כמו Directory.Build.props)
viz helpers/BareCalls.cs --trace "$W/bare.trace" > "$W/bare.out" 2>&1 && grep -q 'size 2 1' "$W/bare.out" \
    && viz --check helpers/BareCalls.cs > /dev/null 2>&1 && echo "Size(q) / Duplicate(q) ב-C# בלי Helpers." || { echo "קריאה בלי Helpers. ב-C# נכשלה"; cat "$W/bare.out"; exit 1; }
grep -qP '^STEP\tRETURN\t\d+\t24\t' "$W/RecursionExamples.trace" && echo "ערך ההחזרה 24 נרשם" || { echo "חסר RETURN"; exit 1; }
grep -qP '^VAR\ttotal\t' "$W/StackExamples.trace" && grep -q '^V	LIN	1' "$W/StackExamples.trace" && echo "משתנים ומחסנית נרשמו" || { echo "חסרים משתנים"; exit 1; }
grep -q '^V	TREE' "$W/TreeExamples.trace" && grep -q '^V	ARR' "$W/ArrayExamples.trace" && grep -q '^V	CHAIN' "$W/ListExamples.trace" && echo "עץ, מערך ורשימה נרשמו"
# כרטיסי אובייקטים: שורות O/F (תוכן המחלקה), ACC (גישה למאפיין), והחלון קורא אותם
grep -q '^SUB' "$W/NestedExamples.trace" && grep -q '^V	LIN	1' "$W/NestedExamples.trace" && grep -qP '^STEP\tLINE' "$W/NestedExamples.trace" && echo "NestedExamples: מבנים בתוך מבנים נרשמו" || { echo "חסר SUB ב-NestedExamples"; exit 1; }
grep -q '^O	' "$W/StudentExamples.trace" && grep -q '^F	average	' "$W/StudentExamples.trace" && grep -qP '^ACC\t' "$W/StudentExamples.trace" \
    && echo "כרטיסי אובייקטים וגישה למאפיין נרשמו (Student)" || { echo "חסרים O/F/ACC ב-StudentExamples"; exit 1; }
grep -qP '^ACC\t' "$W/CarExamples.trace" && echo "גישה למאפיין נרשמה (Car)" || { echo "חסר ACC ב-CarExamples"; exit 1; }
# מבנה בתוך מבנה (מחסנית של מחסניות, רשימה / עץ של תורים): שורות SUB ... ENDSUB, והחלון קורא אותן
mkdir -p "$W/nest" && cat > "$W/nest/Program.cs" <<'CS'
using Unit4.CollectionsLib;
class Program
{
    static void Main(string[] args)
    {
        Stack<int> a = new Stack<int>(); a.Push(1); a.Push(2);
        Stack<Stack<int>> ss = new Stack<Stack<int>>(); ss.Push(a); ss.Push(new Stack<int>());
        ss.Top().Push(9);
        Queue<int> q = new Queue<int>(); q.Insert(5);
        Node<Queue<int>> lst = new Node<Queue<int>>(q);
        BinNode<Queue<int>> t = new BinNode<Queue<int>>(q);
        Stack<int>[] arr = new Stack<int>[1]; arr[0] = a;
    }
}
CS
viz "$W/nest" --trace "$W/nest.trace" > "$W/nest.out" 2>&1 || { cat "$W/nest.out"; exit 1; }
[ "$(grep -c '^SUB' "$W/nest.trace")" -ge 10 ] && [ "$(grep -c '^SUB' "$W/nest.trace")" = "$(grep -c '^ENDSUB' "$W/nest.trace")" ] \
    && java -Djava.awt.headless=true -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/nest.trace" | grep -q '^ok [1-9]' \
    && echo "מבנים בתוך מבנים נרשמו (SUB / ENDSUB) והחלון קרא אותם" || { echo "חסרים SUB ב-nest.trace"; exit 1; }
# שגיאת זמן ריצה: נרשמת כצעד ERROR, והתוכנית מסתיימת עם קוד 1
mkdir -p "$W/boom" && cat > "$W/boom/Program.cs" <<'CS'
using System;
class Program
{
    static int Crash(int[] a) { return a[5]; }
    static void Main(string[] args) { int[] a = new int[2]; Console.WriteLine(Crash(a)); }
}
CS
if viz "$W/boom" --trace "$W/boom.trace" > "$W/boom.out" 2>&1; then echo "היה אמור להיכשל"; exit 1; fi
grep -q '^STEP	ERROR' "$W/boom.trace" && echo "שגיאת זמן ריצה נרשמה כצעד" || { echo "חסר ERROR"; exit 1; }
# שגיאת קומפילציה: מוצגת בפורמט של Visual Studio ולא מריצים
mkdir -p "$W/bad" && printf 'class P { static void Main() { int x = ; } }\n' > "$W/bad/Program.cs"
if viz "$W/bad" --trace "$W/bad.trace" > "$W/bad.out" 2>&1; then echo "היה אמור להיכשל"; exit 1; fi
grep -q 'Program.cs(1,.*error CS' "$W/bad.out" && [ ! -f "$W/bad.trace" ] && echo "שגיאת קומפילציה: מוצגת, בלי הרצה" || { cat "$W/bad.out"; exit 1; }
# הרצה רגילה (בלי dsviz-cs): הספרייה היא סתם מבני נתונים, בלי תיעוד
dotnet build "$ROOT/csharp/project/Examples/StackExamples" -v q --nologo 2>&1 | grep -E " error |Build succeeded" | head -2
dotnet "$ROOT/csharp/project/Examples/StackExamples/bin/Debug/net8.0/StackExamples.dll" | grep -q 'הסכום: 21' && echo "הרצה רגילה: תקינה"

echo "== 8. Visual Studio: חלון \"תרגיל חדש ב-C#\" — אותו קוד כמו VS Code, יצירת פרויקט ורישום בפתרון"
mkdir -p "$W/cstool"
javac --release 11 -nowarn -encoding UTF-8 -cp "$JAR" -d "$W/cstool" "$ROOT/eclipse-plugin/src/il/lamerhav/dsviz/Scaffold.java" "$ROOT/eclipse-plugin/src/il/lamerhav/dsviz/NameCheck.java" \
    "$ROOT/eclipse-plugin/src/il/lamerhav/dsviz/CsSolution.java" $(find "$ROOT/csharp/new-exercise/src" cstool ScaffoldCsAll.java -name '*.java')
# א. המחולל של Visual Studio (Java) מפיק בדיוק אותם 3,426 קבצי C# כמו המחולל של VS Code (JS), בתים-בתים
node combos.js "$W/jsx" > /dev/null
java -cp "$W/cstool:$JAR" ScaffoldCsAll "$W/combos.txt" "$W/javacs"
diff -r "$W/gen" "$W/javacs" > /dev/null && echo "זהה ל-VS Code: $(ls "$W/javacs" | wc -l) קבצים" || { diff -rq "$W/gen" "$W/javacs" | head; exit 1; }
# ב. יצירת פרויקטים בתיקיית התרגילים, רישום ב-.sln, בנייה והרצה ויזואלית
cp -r "$ROOT/csharp/project" "$W/csroot"
rm -rf "$W"/csroot/*/bin "$W"/csroot/*/obj "$W"/csroot/*/*/bin "$W"/csroot/*/*/obj
java -Djava.awt.headless=true -cp "$W/cstool:$JAR" il.lamerhav.dsviz.cs.CsProjectTest "$W/csroot" > /dev/null
(cd "$W/csroot" && dotnet sln DataStructures-CS.sln list | grep -c 'Exercises/Targil' | grep -q 3) && echo "3 תרגילים נרשמו בפתרון" || { echo "הפתרון לא עודכן"; exit 1; }
for n in Targil1 Targil2 Targil3; do
    dotnet build "$W/csroot/Exercises/$n" -v q --nologo 2>&1 | grep -q 'Build succeeded' || { echo "בנייה נכשלה: $n"; exit 1; }
done
viz "$W/csroot/Exercises/Targil2" --trace "$W/t2.trace" > /dev/null 2>&1; grep -q '^SUB' "$W/t2.trace" && echo "התרגיל מבנה-בתוך-מבנה נבנה ורץ ויזואלית"

echo "== 9. שליחת תרגיל — המחולל יודע לבנות גם C# (לכלי של Visual Studio, בהמשך): המעטפות מתקמפלות יחד עם הספרייה"
mkdir -p "$W/ai/cls"
javac -nowarn -encoding UTF-8 -d "$W/ai/cls" "$ROOT/eclipse-plugin/src/il/lamerhav/dsviz/Json.java" "$ROOT/eclipse-plugin/src/il/lamerhav/dsviz/AiScaffold.java" AiScaffoldTest.java
java -Dstdout.encoding=UTF-8 -cp "$W/ai/cls" il.lamerhav.dsviz.AiScaffoldTest ai "$W/ai/src" > /dev/null
for d in $(cat "$W/ai/src/cs-dirs.txt"); do
    viz --check-project "$d" | tail -1 | grep -q '^errors: 0' || { viz --check-project "$d"; exit 1; }
done
echo "  ok  מעטפות C# מתקמפלות (dsviz-cs --check-project)"
