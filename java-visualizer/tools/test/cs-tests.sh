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
# לולאה שלא נגמרת: התוכנית נעצרת (לא נתקעת), והחלון מראה את אותו כרטיס כמו ב-Java — ולולאות תקינות וארוכות רצות עד הסוף
for n in CounterStuck:'[לולאה x שורה 7 הלולאה בשורה 7 לא נגמרת|x נשאר 5 בכל הסיבובים|x--;' \
    WrongWay:'[כרטיס loop i i < 10]|i רק הולך וקטן|בחלק השלישי של ה-for כתוב i-- — הוא מקטין את i. צריך i++.' \
    NodeStuck:'[כרטיס loop p p != null]|p לא מתקדם|p = p.GetNext();' \
    StackStuck:'[כרטיס loop s !s.IsEmpty()]|לא מוציאים מ-s|s.Top() רק מציץ בראש המחסנית בלי להוציא — מוציאים באמת:  s.Pop()' \
    SameState:'[כרטיס loop n n >= 1]|אותו מצב חוזר שוב ושוב|n--;' \
    Semicolon:'[כרטיס loop n n > 0]|יש ; מיד אחרי while (n > 0)|מוחקים את ה-;'; do
    c=${n%%:*}; wants=${n#*:}
    timeout 120 dotnet "$RUNNER" "problems/loops/cs/$c.cs" --trace "$W/loop-$c.trace" > "$W/loop-$c.out" 2>&1 || true
    grep -q 'לא נגמרת — התוכנית נעצרה' "$W/loop-$c.out" && ! grep -q '^STEP	END' "$W/loop-$c.trace" && grep -q '^LOOP	' "$W/loop-$c.trace" \
        || { echo "לולאה שלא נגמרת ב-C#: $c לא נעצרה"; tail -5 "$W/loop-$c.out"; exit 1; }
    java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/loop-$c.trace" \
        | sed 's/\xE2\x80[\xAA\xAC\x8E\x8F]//g' > "$W/loop-$c.txt"
    echo "$wants" | tr '|' '\n' | while read -r want; do
        grep -qF -- "$want" "$W/loop-$c.txt" || { echo "לולאה שלא נגמרת ב-C# ($c): חסר $want"; cat "$W/loop-$c.txt"; exit 1; }
    done
    echo "לולאה שלא נגמרת ב-C#: $c נעצרה, עם כרטיס"
done
timeout 120 dotnet "$RUNNER" problems/loops/cs/FineLoops.cs --trace "$W/loop-fine.trace" > "$W/loop-fine.out" 2>&1 \
    && grep -q '100 50 150 64 6 200010000' "$W/loop-fine.out" && grep -q '^STEP	END' "$W/loop-fine.trace" && ! grep -q '^LOOP	' "$W/loop-fine.trace" \
    && echo "לולאות תקינות וארוכות ב-C#: רצות עד הסוף ולא נעצרות" || { echo "לולאה תקינה ב-C# נעצרה"; tail -5 "$W/loop-fine.out"; exit 1; }
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
# תנאים מורכבים (&&, ||, !, סוגריים) ב-C#: כל חלק בנפרד, קיצור (short-circuit), ו-.Length (property, לא פעולה)
for want in 'שורה 72 if: x > 5 && y > 4 | 7 > 5 && 3 > 4 | x=7 y=3 | חלקים: x > 5 ✓, y > 4 ✗ | false מדלגים על ה-if]' \
    'x > 5 מתקיים (7 > 5), אבל y > 4 לא מתקיים (3 > 4) — וב-&& צריך ששניהם יתקיימו]' \
    'שורה 73 if: x > 5 || y > 4 | 7 > 5 || y > 4 | x=7 y=3 | חלקים: x > 5 ✓, y > 4 לא נבדק | true נכנסים ל-if]' \
    'x > 5 מתקיים (7 > 5) — וזה מספיק ב-||, אז את y > 4 לא בודקים בכלל]' \
    'שורה 74 if: x % 2 == 0 && y != 0 || s.Length == 3 | 7 % 2 == 0 && y != 0 || 3 == 3 | x=7 y=3 s=abc | חלקים: x % 2 == 0 ✗, y != 0 לא נבדק, s.Length == 3 ✓ | true נכנסים ל-if]' \
    'החלק הראשון לא מתקיים, אבל s.Length == 3 מתקיים (3 == 3) — וזה מספיק ב-||]' \
    'שורה 75 if: !(x < 0) && (y == 3 || y == 9) | !(7 < 0) && (3 == 3 || y == 9) | x=7 y=3 | חלקים: !(x < 0) ✓, y == 3 ✓, y == 9 לא נבדק | true נכנסים ל-if]' \
    'שורה 76 if: y > 4 && x / (y - 3) > 1 | 3 > 4 && x / (y - 3) > 1 | y=3 x=7 | חלקים: y > 4 ✗, x / (y - 3) > 1 לא נבדק | false מדלגים על ה-if]' \
    'y > 4 לא מתקיים (3 > 4), ולכן את x / (y - 3) > 1 לא בודקים בכלל]' \
    'שורה 80 while: !st.IsEmpty() && st.Top() > 0 | !false && 5 > 0 | st=Stack [5,2] | חלקים: !st.IsEmpty() ✓, st.Top() > 0 ✓ | true ממשיכים לסיבוב]' \
    'שורה 80 while: !st.IsEmpty() && st.Top() > 0 | !true && st.Top() > 0 | st=Stack [] | חלקים: !st.IsEmpty() ✗, st.Top() > 0 לא נבדק | false יוצאים מהלולאה]' \
    'שורה 83 if: s.IndexOf("z") >= 0 || y > 1 | s.IndexOf("z") >= 0 || 3 > 1 | s=abc y=3 | חלקים: s.IndexOf("z") >= 0 ?, y > 1 ? | true נכנסים ל-if]' \
    'לא ידוע מה יצא ב-s.IndexOf("z") >= 0 (אי אפשר לחשב את זה כאן), אבל התנאי כולו התקיים]'; do
    grep -qF -- "$want" "$W/conds.txt" || { echo "תנאים מורכבים ב-C#: חסר $want"; grep '^  \[' "$W/conds.txt"; exit 1; }
done
echo "תנאים מורכבים ב-C#: $(grep -c '| חלקים:' "$W/conds.txt") בדיקות תנאי-מורכב מסומנות"
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
# מעקב המשתנים לכל פעולה פעילה (שורות FV בקובץ): אותן מסגרות כמו ב-Java, והחלון עצמו (VarsPanel) — בלי מסך
for want in '[מסגרות #17: Rem{x=8, d=2} ← IsEven{x=8} ← SumEven{s=Stack [3], sum=0, tmp=Stack [], v=8} ← Main{s=Stack [3]}]' \
    '[מסגרות #19: IsEven{x=8} ← SumEven{s=Stack [3], sum=0, tmp=Stack [], v=8} ← Main{s=Stack [3]} | IsEven() מחזירה true — חוזרים ל-SumEven() בשורה 30]' \
    '[מסגרות #21: SumEven{s=Stack [3], sum=8, tmp=Stack [], v=8} ← Main{s=Stack [3]}]' \
    '[סיום #76: Main{s=Stack [8,3], total=8, q=Queue []}]'; do
    grep -qF -- "$want" "$W/calls.txt" || { echo "מעקב משתנים ב-C#: חסר $want"; grep 'מסגרות\|סיום' "$W/calls.txt"; exit 1; }
done
viz recursion/cs/Fib.cs --trace "$W/fib.trace" > "$W/fib.out" 2>&1 || { cat "$W/fib.out"; exit 1; }
mkdir -p "$W/vp" && javac -encoding UTF-8 -cp "$JAR" -d "$W/vp" calls/VarsPanelCheck.java
for t in calls fib; do
    java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$W/vp:$JAR" unit4.collectionsLib.VarsPanelCheck $t "$W/$t.trace" > "$W/vp-$t.txt" 2>&1 \
        && grep -q MARK-END "$W/vp-$t.txt" || { echo "מעקב המשתנים (VarsPanel) נכשל: $t"; cat "$W/vp-$t.txt"; exit 1; }
done
echo "מעקב משתנים ב-C#: המשתנים של כל פעולה פעילה, ובחלון — $(cat "$W"/vp-*.txt | grep -c '^  ok') בדיקות (כותרת, רשימה, בחירה, חזרה לפעולה שרצה)"
# פעולה שרצה בתוך אובייקט: this הוא d1, המאפיינים שהשורה קוראת / משנה (כמו ב-Java)
viz objects/cs/Pets.cs --trace "$W/pets.trace" > "$W/pets.out" 2>&1 || { cat "$W/pets.out"; exit 1; }
java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/pets.trace" > "$W/pets.txt" 2>&1
for want in 'this=d1 ב-הבנאי Dog() {name=null, age=0} this.name=שינוי]' 'נכנסים ל-d1.Birthday()]' \
    'this=d1 ב-Birthday() {name="רקס", age=3} this.age=שינוי]' 'this=d1 ב-Bark() {name="רקס", age=4} this.name=קריאה]' \
    'this=d2 ב-IsOlderThan() {name="לאסי", age=5} other←d1 this.age=קריאה other.age=קריאה]'; do
    grep -qF -- "$want" "$W/pets.txt" || { echo "אובייקט ב-C#: חסר $want"; grep 'אובייקט' "$W/pets.txt"; exit 1; }
done
echo "פעולה בתוך אובייקט ב-C#: $(grep -c 'אובייקט #' "$W/pets.txt") צעדים מסומנים"
# רשימה מקושרת: לכל מצביע חץ וצבע משלו (כמה מצביעים לאותה חוליה), וחיבור חוליה חדשה לרשימה קיימת (באמצע/בסוף) — כמו ב-Java
viz lists/cs/LinkNew.cs --trace "$W/linknew.trace" > "$W/linknew.out" 2>&1 || { cat "$W/linknew.out"; exit 1; }
java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/linknew.trace" > "$W/linknew.txt" 2>&1
for want in '[שרשרת #7: list: 3(list)→7→1→null]' '[שרשרת #7: n: 5(n)→null]' \
    '[שרשרת #8: n: 5(n)→3(list)→7→1→null]' \
    '[שרשרת #10: list, n: 5(list,n)→3→7→1→null]' '[שרשרת #11: list, n, p: 5(list,n,p)→3→7→1→null]' \
    '[שרשרת #28: list, n: 5(list,n)→3→7→1(p)→9(t)→null]' \
    'ענף 4(q)↗7' \
    '[שרשרת #37: list, n: 5(list,n)→3(p)→4(q)→7→1→9(t)→null]'; do
    grep -qF -- "$want" "$W/linknew.txt" || { echo "רשימה מקושרת ב-C#: חסר $want"; grep 'שרשרת' "$W/linknew.txt"; exit 1; }
done
! grep -q 'אבד' "$W/linknew.txt" || { echo "רשימה מקושרת ב-C#: חוליה סומנה כאבודה בלי סיבה"; grep 'אבד' "$W/linknew.txt"; exit 1; }
echo "רשימה מקושרת ב-C#: $(grep -c 'שרשרת #' "$W/linknew.txt") צעדים מסומנים, בלי 'אבדה' שגויה"
# סוגי רקורסיה: אותו סיווג כמו ב-Java
for spec in 'Fib:רקורסיה כפולה | פעולות Fib | עומק 4 | קריאות 9' 'EvenOdd:רקורסיה הדדית | פעולות IsEven, IsOdd' \
    'SumTail:רקורסיית זנב | פעולות Sum | עומק 5' 'ListLen:רקורסיה פשוטה | פעולות Length | עומק 4 | קריאות 4 | על שרשרת חוליות'; do
    c=${spec%%:*}; want=${spec#*:}
    viz recursion/cs/$c.cs --trace "$W/rec-$c.trace" > "$W/rec-$c.out" 2>&1 || { cat "$W/rec-$c.out"; exit 1; }
    java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/rec-$c.trace" > "$W/rec-$c.txt" 2>&1
    grep -qF -- "[רקורסיה: $want" "$W/rec-$c.txt" && echo "רקורסיה ב-C#: $c — $want" || { echo "רקורסיה ב-C#: $c חסר $want"; grep 'רקורסיה:' "$W/rec-$c.txt"; exit 1; }
done
# חץ "חוזרים לראש הלולאה": אותן חזרות כמו ב-Java (גם foreach, while עם סוגריים בשורה נפרדת, ו-continue)
viz loops/cs/LoopBack.cs --trace "$W/loopback.trace" > "$W/loopback.out" 2>&1 || { cat "$W/loopback.out"; exit 1; }
java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/loopback.trace" > "$W/loopback.txt" 2>&1
for want in 'שורה 13 for: מ-14, החץ מ-15 | for (int i = 0; i < n; i++)]' 'שורה 17 while: מ-20, החץ מ-21 | while (k > 0)]' \
    'שורה 24 do: מ-25, החץ מ-25 | do { ... } while (d > 0)]' 'שורה 27 for: מ-28, החץ מ-29 | for (int b = 0; b < 2; b++)]' \
    'שורה 26 for: מ-27, החץ מ-30 | for (int a = 0; a < 2; a++)]' 'שורה 32 foreach: מ-33, החץ מ-36 | foreach (int x in arr)]' \
    'שורה 37 for: מ-38, החץ מ-40 | for (int c = 0; c < 3; c++)]'; do
    grep -qF -- "$want" "$W/loopback.txt" || { echo "חץ הלולאה ב-C#: חסר $want"; grep 'חזרה' "$W/loopback.txt"; exit 1; }
done
! grep -q 'שורה 27 for: מ-26\|\[חזרה #[0-9]* שורה 41 \|\[חזרה #[0-9]* שורה 8 ' "$W/loopback.txt" \
    || { echo "חץ הלולאה ב-C#: חץ במקום שאין בו חזרה"; grep 'חזרה' "$W/loopback.txt"; exit 1; }
echo "חץ \"חוזרים לראש הלולאה\" ב-C#: $(grep -c '^  \[חזרה #' "$W/loopback.txt") חזרות מסומנות"
# קלט מהמקלדת (Console.ReadLine / Console.Read / Console.In): השורה שמחכה, מה שהוקלד כצעד INPUT, והפלט — כמו ב-Java.
# בלי חלון: --input קובץ (או DSVIZ_INPUT) "מקליד" את השורות כשהתוכנית מחכה (UTF-8 — עברית בלי תלות בקידוד של שורת הפקודה)
IN="$W/input"
mkdir -p "$IN"
printf '3\n4\n5\n6\nדני כהן\n' > "$IN/sum.in"
printf 'y\nתל אביב\n' > "$IN/read.in"
printf 'abc\n' > "$IN/bad.in"
printf '1\n2\n3\n' > "$IN/until.in"
incheck() { f=$1; shift; for want in "$@"; do grep -qF -- "$want" "$f" || { echo "קלט ב-C#: חסר $want ב-$(basename "$f")"; cat "$f"; exit 1; }; done; }
for run in InputSum:sum ReadChars:read InputSum:bad SumUntil:until; do
    viz input/cs/${run%%:*}.cs --trace "$IN/${run#*:}.trace" --input "$IN/${run#*:}.in" < /dev/null > "$IN/${run#*:}.out" 2>&1 || true
    java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$IN/${run#*:}.trace" > "$IN/${run#*:}.txt" 2>&1
done
incheck "$IN/sum.txt" '⌨ מחכה לקלט מהמקלדת — שורה 10:  Console.ReadLine()  (מספר שלם)' '(InputSum.cs:10)  קלט מהמקלדת: 3' \
    '⌨ מחכה לקלט מהמקלדת — שורה 15:  Console.ReadLine()  (מספר שלם)' '(InputSum.cs:15)  קלט מהמקלדת: 6' \
    '⌨ מחכה לקלט מהמקלדת — שורה 18:  Console.ReadLine()  (שורת טקסט)' '(InputSum.cs:18)  קלט מהמקלדת: דני כהן'
[ "$(grep -c 'קלט מהמקלדת: ' "$IN/sum.txt")" = 5 ] && [ "$(grep -c 'מחכה לקלט' "$IN/sum.txt")" = 5 ] \
    || { echo "קלט ב-C#: מספר צעדי הקלט / ההמתנות"; cat "$IN/sum.txt"; exit 1; }
# מה שהוקלד: מודפס גם לקונסול אחרי השאלה, נכנס לפלט שבחלון, וצעד ה-Push שאחריו רואה אותו
incheck "$IN/sum.out" 'כמה מספרים? 3' 'מספר 3: 6' 'מה השם שלך? דני כהן' 'שלום דני כהן, הסכום: 15'
grep -qF 'OUT	כמה מספרים? 3\nמספר 1: 4\n' "$IN/sum.trace" \
    && grep -P '^STEP\t(INPUT|CHANGE)\t' "$IN/sum.trace" | grep -A1 'קלט מהמקלדת: ‎4‎' | grep -qP '^STEP\tCHANGE\t24\t.*s\.Push\(4\)$' || { echo "קלט ב-C#: הפלט / הצעדים שאחרי הקלט"; grep '^STEP' "$IN/sum.trace" | head -20; exit 1; }
incheck "$IN/read.txt" '⌨ מחכה לקלט מהמקלדת — שורה 9:  Console.Read()  (תו)' '(ReadChars.cs:9)  קלט מהמקלדת: y' \
    '⌨ מחכה לקלט מהמקלדת — שורה 12:  Console.In.ReadLine()  (שורת טקסט)' '(ReadChars.cs:12)  קלט מהמקלדת: תל אביב'
incheck "$IN/read.out" 'קיבלתי y ו-תל אביב (7 תווים)'
# הקלדה שגויה: int.Parse("abc") — צעד הקלט, ואחריו שגיאה עם כרטיס שמסביר
incheck "$IN/bad.txt" '(InputSum.cs:10)  קלט מהמקלדת: abc' '[כרטיס other]'
grep -qP '^STEP\tERROR\t.*FormatException' "$IN/bad.trace" || { echo "קלט ב-C#: חסרה השגיאה של abc"; cat "$IN/bad.out"; exit 1; }
# while ((line = Console.ReadLine()) != null) עד שהקלט נגמר — צעד אחד "סוף הקלט" (בחלון: אחרי "סיום הקלט")
incheck "$IN/until.txt" '⌨ מחכה לקלט מהמקלדת — שורה 11:  Console.ReadLine()  (שורת טקסט)' '(SumUntil.cs:11)  קלט מהמקלדת: 3' \
    '(SumUntil.cs:11)  סוף הקלט — אין עוד מה לקרוא'
incheck "$IN/until.out" 'סכום: 6'
# DSVIZ_INPUT (כמו dsviz.input ב-Java): השורות במשתנה הסביבה
DSVIZ_INPUT='2\n7\n8\nרון' viz input/cs/InputSum.cs --trace "$IN/env.trace" < /dev/null > "$IN/env.out" 2>&1 || true
incheck "$IN/env.out" 'כמה מספרים? 2' 'שלום רון, הסכום: 15'
# קלט מצינור (בלי --input): נקרא כרגיל ונרשם כצעדי קלט, בלי הד לקונסול (כמו בהרצה רגילה)
printf '2\n7\n8\nרון\n' | viz input/cs/InputSum.cs --trace "$IN/pipe.trace" > "$IN/pipe.out" 2>&1 || true
java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$IN/pipe.trace" > "$IN/pipe.txt" 2>&1
incheck "$IN/pipe.out" 'כמה מספרים? מספר 1: ' 'שלום רון, הסכום: 15'
incheck "$IN/pipe.txt" '(InputSum.cs:10)  קלט מהמקלדת: 2' '(InputSum.cs:18)  קלט מהמקלדת: רון'
# בלי קלט בכלל: ReadLine מחזירה null (כמו בהרצה רגילה) — צעד "סוף הקלט", ו-int.Parse(null) נכשל
viz input/cs/InputSum.cs --trace "$IN/eof.trace" < /dev/null > "$IN/eof.out" 2>&1 || true
grep -qP '^STEP\tINPUT\t.*סוף הקלט' "$IN/eof.trace" && grep -qP '^STEP\tERROR\t.*ArgumentNullException' "$IN/eof.trace" \
    || { echo "קלט ב-C#: בלי קלט"; cat "$IN/eof.out"; exit 1; }
# בלי --trace: כשהתוכנית מחכה נפתח החלון כבר עכשיו (LiveViewer) ומקבל את הצעדים במנות; כאן "חלון" מדומה שעונה על ASK
printf '#!/bin/sh\nexec node "%s" "$@"\n' "$(pwd)/input/cs/fake-viewer.js" > "$IN/fakejava" && chmod +x "$IN/fakejava"
DSVIZ_JAVA="$IN/fakejava" DSVIZ_JAR="$JAR" FAKE_OUT="$IN/live.stream" FAKE_ANSWERS='5|7|EOF' timeout 120 dotnet "$RUNNER" input/cs/SumUntil.cs < /dev/null > "$IN/live.out" 2>&1 \
    || { echo "קלט ב-C#: ההרצה עם החלון נכשלה"; cat "$IN/live.out"; exit 1; }
java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$IN/live.stream" > "$IN/live.txt" 2>&1
[ "$(grep -c '^ASK	' "$IN/live.stream")" = 3 ] && [ "$(grep -c '^RESUME$' "$IN/live.stream")" = 3 ] && grep -q '^DONE$' "$IN/live.stream" \
    && grep -qP '^ASK\t\d+\tSumUntil\tReadSum\tSumUntil.cs\t11\tConsole.ReadLine\(\)\tשורת טקסט$' "$IN/live.stream" \
    || { echo "קלט ב-C#: מה שנשלח לחלון"; grep -v '^SRC' "$IN/live.stream" | cut -c1-80; exit 1; }
incheck "$IN/live.txt" '(SumUntil.cs:11)  קלט מהמקלדת: 5' '(SumUntil.cs:11)  קלט מהמקלדת: 7' '(SumUntil.cs:11)  סוף הקלט — אין עוד מה לקרוא'
incheck "$IN/live.out" 'נפתח חלון ההרצה הוויזואלית' 'סכום: 12'
grep -qx '7' "$IN/live.out" || { echo "קלט ב-C#: מה שהוקלד בחלון לא הודפס לקונסול"; cat "$IN/live.out"; exit 1; }
# החלון האמיתי (Swing), אם יש מסך מדומה: מחכה — הפס עם השאלה, מקלידים, ו"סיום הקלט"
if command -v Xvfb >/dev/null 2>&1; then
    Xvfb :95 -screen 0 1600x900x24 >/dev/null 2>&1 & XVFB=$!
    sleep 2
    mkdir -p "$IN/cls" && javac -d "$IN/cls" "$ROOT/tutorials/harness/SwingScript.java"
    printf 'shot w1\ntext 0 2\nclick שליחה\nwait 800\ntext 0 4\nclick שליחה\nwait 800\nshot w2\ntext 0 5\nclick שליחה\nwait 800\nclick סיום הקלט\nwait 2500\nshot end\n' > "$IN/gui.script"
    printf '#!/bin/sh\nexec java -cp "%s:%s" SwingScript "%s" "%s" unit4.collectionsLib.TraceViewer --live\n' "$IN/cls" "$JAR" "$IN/gui.script" "$IN/shots" > "$IN/swingjava"
    chmod +x "$IN/swingjava"
    DISPLAY=:95 DSVIZ_JAVA="$IN/swingjava" DSVIZ_JAR="$JAR" timeout 120 dotnet "$RUNNER" input/cs/InputSum.cs < /dev/null > "$IN/gui.out" 2>&1 || true
    kill $XVFB 2>/dev/null || true
    grep -q '"התוכנית מחכה שתקלידו מספר שלם"' "$IN/shots/w1.json" && grep -q 'שורה 10:  .*Console.ReadLine()' "$IN/shots/w1.json" \
        && grep -q '"כמה מספרים?"' "$IN/shots/w1.json" && grep -q 'שורה 15:' "$IN/shots/w2.json" && grep -q '"מספר 2:"' "$IN/shots/w2.json" \
        && ! grep -q 'מחכה שתקלידו' "$IN/shots/end.json" && grep -q 'שלום , הסכום: 9' "$IN/gui.out" \
        && echo "קלט ב-C# בחלון האמיתי: הפס עם השאלה והשורה, שליחה, ו\"סיום הקלט\"" || { echo "קלט ב-C# בחלון נכשל"; cat "$IN/gui.out"; exit 1; }
fi
echo "קלט מהמקלדת ב-C#: --input, DSVIZ_INPUT, צינור, סוף הקלט, הקלדה שגויה, וחלון שנפתח כשהתוכנית מחכה"
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
    && grep -qF '"others":[{"label":"ד","text":"מהי סיבוכיות זמן הריצה?"}]' "$SUB/out.txt" && ! grep -q 'מהי סיבוכיות' "$SUB/root/Exercises/Ex01_Stacks/StackTargil.cs" \
    && echo "שליחת תרגיל ב-C#: פרויקט Ex01_Stacks בפתרון, בלי פתרון בקוד, וההצעות נבדקו (dsviz-cs); סעיף ד (במילים) — בהודעה ולא בקוד" || { echo "שליחת תרגיל ב-C# נכשלה"; cat "$SUB/out.txt"; kill $MOCKW; exit 1; }
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
    grep -q '"שליחה ויצירת התרגיל"' "$SUB/shots/s1.json" && grep -q 'נוצר התרגיל Ex01_Stacks, ובו 3 סעיפים בקוד' "$SUB/shots/s2.json" \
        && grep -q 'ד — מהי סיבוכיות זמן הריצה?' "$SUB/shots/s2.json" \
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

echo "== 10. Visual Studio בלי שום פעולה: DSViz.targets (MSBuild ImportAfter) — הפניה לספרייה ופרופיל ההרצה אוטומטית בכל פרויקט"
# מדמים את %LOCALAPPDATA% של Windows (MSBuildUserExtensionsPath נגזר מ-XDG_DATA_HOME גם ב-Linux) —
# עם קובץ ה-targets שההתקנה כותבת, ואת תיקיית ההתקנה $(LOCALAPPDATA)\DSViz\cs (dsviz-cs.exe + הספרייה)
FAKE="$W/msbuild-home"
rm -rf "$FAKE" && mkdir -p "$FAKE/Microsoft/MSBuild/Current/Microsoft.CSharp.targets/ImportAfter" "$FAKE/DSViz/cs"
cp "$ROOT/installer/DSViz.targets" "$FAKE/Microsoft/MSBuild/Current/Microsoft.CSharp.targets/ImportAfter/DSViz.targets"
cp "$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/Unit4.CollectionsLib.dll" "$FAKE/DSViz/cs/Unit4.CollectionsLib.dll"
printf '#!/bin/sh\necho "CALLED $*" >> "%s/calls.log"\nexec dotnet "%s" "$@"\n' "$FAKE/DSViz/cs" "$RUNNER" > "$FAKE/DSViz/cs/dsviz-cs.exe"
chmod +x "$FAKE/DSViz/cs/dsviz-cs.exe"
export XDG_DATA_HOME="$FAKE"
msb() { (cd "$1" && shift && dotnet build -v "${V:-q}" --nologo "$@"); }

# א. פרויקט חדש (בלי הפניה, בלי launchSettings): אחרי בנייה — ה-DLL נעתק (ההפניה הצליחה) ופרופיל "הרצה ויזואלית" נוסף
rm -rf "$W/new1" && mkdir -p "$W/new1"
printf 'using System;\nusing Unit4.CollectionsLib;\nStack<int> s = new Stack<int>(); s.Push(1); s.Push(2);\nConsole.WriteLine(s.Pop());\n' > "$W/new1/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/new1/New1.csproj"
msb "$W/new1" | grep -q 'Build succeeded' && [ -f "$W/new1/bin/Debug/net8.0/Unit4.CollectionsLib.dll" ] \
    && grep -q 'הרצה ויזואלית' "$W/new1/Properties/launchSettings.json" && grep -q 'הרצה רגילה' "$W/new1/Properties/launchSettings.json" \
    && [ "$(dotnet "$W/new1/bin/Debug/net8.0/New1.dll")" = 2 ] \
    && echo "פרויקט חדש: הפניה לספרייה + פרופיל הרצה נוספו לבד (והתוכנית רצה עם הספרייה)" || { echo "DSViz.targets נכשל על פרויקט חדש"; cat "$W/new1/Properties/launchSettings.json" 2>&1; exit 1; }

# ב. בנייה שנייה: מהירה — dsviz-cs לא רץ שוב (ה-Exec מדלג כש-הפרופיל כבר קיים)
rm -f "$FAKE/DSViz/cs/calls.log"
msb "$W/new1" | grep -q 'Build succeeded'
[ ! -f "$FAKE/DSViz/cs/calls.log" ] && echo "בנייה שנייה: לא רץ dsviz-cs.exe שוב (מהיר)" || { echo "בנייה שנייה הריצה את ה-Exec שלא לצורך"; cat "$FAKE/DSViz/cs/calls.log"; exit 1; }

# ג. launchSettings.json קיים עם פרופיל אחר של התלמיד — נשאר, והפרופיל שלנו מתווסף לידו
rm -rf "$W/new2" && mkdir -p "$W/new2/Properties"
printf 'using System;\nConsole.WriteLine("hi");\n' > "$W/new2/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/new2/New2.csproj"
printf '{"profiles":{"שלי":{"commandName":"Project","environmentVariables":{"X":"1"}}}}\n' > "$W/new2/Properties/launchSettings.json"
msb "$W/new2" | grep -q 'Build succeeded'
grep -q '"שלי"' "$W/new2/Properties/launchSettings.json" && grep -q '"X": *"1"' "$W/new2/Properties/launchSettings.json" && grep -q 'הרצה ויזואלית' "$W/new2/Properties/launchSettings.json" \
    && ! grep -q 'הרצה רגילה' "$W/new2/Properties/launchSettings.json" \
    && echo "launchSettings.json קיים: הפרופיל של התלמיד נשאר, והפרופיל שלנו נוסף (בלי 'הרצה רגילה' כפולה)" || { echo "פרופיל קיים לא נשמר"; cat "$W/new2/Properties/launchSettings.json"; exit 1; }

# ד. DSVizDisable=true — שום דבר לא קורה
rm -rf "$W/new3" && mkdir -p "$W/new3"
printf 'using System;\nConsole.WriteLine("hi");\n' > "$W/new3/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/new3/New3.csproj"
msb "$W/new3" -p:DSVizDisable=true | grep -q 'Build succeeded'
[ ! -e "$W/new3/Properties" ] && [ ! -f "$W/new3/bin/Debug/net8.0/Unit4.CollectionsLib.dll" ] \
    && echo "DSVizDisable=true: שום דבר לא נוסף" || { echo "DSVizDisable=true לא עבד"; exit 1; }

# ה. ספרייה (classlib, לא Exe) — לא נוגעים
rm -rf "$W/new4" && mkdir -p "$W/new4"
printf 'namespace N4 { public class C1 {} }\n' > "$W/new4/Class1.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><TargetFramework>net8.0</TargetFramework><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/new4/New4.csproj"
msb "$W/new4" | grep -q 'Build succeeded'
[ ! -e "$W/new4/Properties" ] && echo "ספרייה (OutputType != Exe): לא נוגעים" || { echo "DSViz.targets נגע בספרייה (לא Exe)"; exit 1; }

# ו. פרויקט רגיל של Visual Studio (GoldWater, עם הפניה משלו לספרייה) — לא מתווספת הפניה כפולה, והבנייה מצליחה.
#    (ResolveAssemblyReferences היה מאחד שתי הפניות לבד — אבל לטובת העותק שבהתקנה; לכן בודקים שנשארה ההפניה של הפרויקט)
rm -rf "$W/gw" && mkdir -p "$W/gw"
cp calls/vs/GoldWater.csproj calls/vs/Recursion.cs "$W/gw/"
cp "$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/Unit4.CollectionsLib.dll" "$W/Unit4.CollectionsLib.dll"   # ה-HintPath בפרויקט: ..\Unit4.CollectionsLib.dll
V=normal msb "$W/gw" > "$W/gw.out" 2>&1; grep -q 'Build succeeded' "$W/gw.out" \
    && [ "$(grep -o '/reference:[^ ]*Unit4\.CollectionsLib\.dll' "$W/gw.out" | wc -l)" = 1 ] \
    && ! grep -o '/reference:[^ ]*Unit4\.CollectionsLib\.dll' "$W/gw.out" | grep -q msbuild-home \
    && echo "פרויקט עם הפניה משלו לספרייה: בלי הפניה כפולה (נשארת ההפניה של הפרויקט)" || { echo "הפניה כפולה לספרייה"; grep reference "$W/gw.out" | tr ' ' '\n' | grep -i unit4; exit 1; }

# ז. ה-DLL חסר (למשל התקנה חלקית) — הבנייה מצליחה בכל זאת, ופרופיל ההרצה עדיין מתווסף
rm -rf "$W/new5" && mkdir -p "$W/new5"
printf 'using System;\nConsole.WriteLine("hi");\n' > "$W/new5/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/new5/New5.csproj"
msb "$W/new5" -p:DSVizLibPath="$W/no-such-file.dll" | grep -q 'Build succeeded'
grep -q 'הרצה ויזואלית' "$W/new5/Properties/launchSettings.json" \
    && echo "ה-DLL חסר: הבנייה הצליחה בכל זאת, ופרופיל ההרצה נוסף" || { echo "ה-DLL החסר שבר את הבנייה"; exit 1; }

# ח. הפניה בשם המלא ("Unit4.CollectionsLib, Version=...") או ProjectReference לפרויקט של הספרייה — בלי הפניה כפולה
rm -rf "$W/new6" "$W/new7" && mkdir -p "$W/new6" "$W/new7"
for d in new6 new7; do printf 'using System;\nusing Unit4.CollectionsLib;\nConsole.WriteLine(new Queue<int>().IsEmpty());\n' > "$W/$d/Program.cs"; done
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>disable</ImplicitUsings></PropertyGroup><ItemGroup><Reference Include="Unit4.CollectionsLib, Version=1.0.1.0, Culture=neutral, PublicKeyToken=null"><HintPath>%s</HintPath></Reference></ItemGroup></Project>\n' "$W/Unit4.CollectionsLib.dll" > "$W/new6/New6.csproj"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>disable</ImplicitUsings></PropertyGroup><ItemGroup><ProjectReference Include="%s" /></ItemGroup></Project>\n' "$ROOT/csharp/Unit4.CollectionsLib/Unit4.CollectionsLib.csproj" > "$W/new7/New7.csproj"
for d in new6 new7; do
    V=normal msb "$W/$d" > "$W/$d.out" 2>&1; grep -q 'Build succeeded' "$W/$d.out" \
        && [ "$(grep -o '/reference:[^ ]*Unit4\.CollectionsLib\.dll' "$W/$d.out" | wc -l)" = 1 ] \
        && ! grep -o '/reference:[^ ]*Unit4\.CollectionsLib\.dll' "$W/$d.out" | grep -q msbuild-home \
        || { echo "$d: הפניה כפולה / בנייה נכשלה"; grep -E 'error|warning' "$W/$d.out" | head; exit 1; }
done
echo "הפניה בשם מלא / ProjectReference לספרייה: בלי הפניה כפולה"

# ט. אידמפוטנטי: --ensure-profile שוב לא משנה את הקובץ; launchSettings.json פגום (לא JSON) — הבנייה מצליחה והקובץ לא נוגע
before=$(md5sum < "$W/new2/Properties/launchSettings.json")
viz --ensure-profile "$W/new2" && [ "$(md5sum < "$W/new2/Properties/launchSettings.json")" = "$before" ] \
    || { echo "--ensure-profile שינה קובץ שכבר יש בו את הפרופיל"; exit 1; }
rm -rf "$W/new8" && mkdir -p "$W/new8/Properties"
printf 'using System;\nConsole.WriteLine("hi");\n' > "$W/new8/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/new8/New8.csproj"
printf '{ "profiles": { oops\n' > "$W/new8/Properties/launchSettings.json"
msb "$W/new8" | grep -q 'Build succeeded' && [ "$(cat "$W/new8/Properties/launchSettings.json")" = '{ "profiles": { oops' ] \
    && echo "אידמפוטנטי, ו-launchSettings.json פגום לא שובר את הבנייה (והקובץ לא נדרס)" || { echo "launchSettings.json פגום: בעיה"; exit 1; }

# י. כמה מסגרות (TargetFrameworks) — הבנייה החיצונית לא נשברת, והבנייה הפנימית מקבלת הכול
rm -rf "$W/new9" && mkdir -p "$W/new9"
printf 'using System;\nusing Unit4.CollectionsLib;\nConsole.WriteLine(new Stack<int>().IsEmpty());\n' > "$W/new9/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFrameworks>net8.0</TargetFrameworks><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/new9/New9.csproj"
msb "$W/new9" | grep -q 'Build succeeded' && grep -q 'הרצה ויזואלית' "$W/new9/Properties/launchSettings.json" \
    && echo "TargetFrameworks (כמה מסגרות): תקין" || { echo "TargetFrameworks: נכשל"; exit 1; }

unset XDG_DATA_HOME
echo "DSViz.targets: 10 תרחישים נבדקו (פרויקט חדש, בנייה שנייה מהירה, launchSettings קיים, DSVizDisable, ספרייה, הפניה קיימת, DLL חסר, שם מלא/ProjectReference, אידמפוטנטי/קובץ פגום, כמה מסגרות)"
