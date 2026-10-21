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
