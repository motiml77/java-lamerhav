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
# תיקיית התבנית (csharp/project) צריכה את הספרייה ב-lib — כמו בהתקנה. היא לא במאגר (‎.gitignore): מעתיקים את זו שנבנתה עכשיו, כדי שלא
# תחסר (ב-GitHub) ולא תהיה ישנה (במחשב שבנה פעם)
mkdir -p "$ROOT/csharp/project/lib" && cp "$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/Unit4.CollectionsLib.dll" "$ROOT/csharp/project/lib/"
# Unit4.dll האמיתי של בתי הספר (מורד ונבדק מול sha256 — helpers/school-unit4.sh)
. ./helpers/school-unit4.sh
SCHOOL_DLL=$(school_unit4 "$ROOT/build") || { echo "אין Unit4.dll האמיתי של בתי הספר — הבדיקות מולו לא יכולות לרוץ"; exit 1; }

echo "== 6. תבניות C#: כל התרגילים וכל קטעי הקוד מתקמפלים"
node cs-combos.js "$W/gen" > "$W/.combos.out" 2>&1 || { cat "$W/.combos.out"; exit 1; }; tail -1 "$W/.combos.out"
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
        | LC_ALL=C sed 's/\xE2\x80[\xAA\xAC\x8E\x8F]//g' > "$W/loop-$c.txt"
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
# "כל שורה": השורה המסומנת מראה את מה שהיא כבר עשתה (הפעולה שלה והמשתנים שאחריה), והמרקר במעקב המשתנים (LineAfter)
javac -encoding UTF-8 -cp "$JAR" -d "$W/vp" calls/LineAfterCheck.java
java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$W/vp:$JAR" unit4.collectionsLib.LineAfterCheck "$W/calls.trace" > "$W/lineafter.txt" 2>&1 \
    && grep -q MARK-END "$W/lineafter.txt" || { echo "כל שורה — מה שהשורה עשתה (LineAfter) נכשל"; cat "$W/lineafter.txt"; exit 1; }
echo "כל שורה — השורה המסומנת מראה מה היא עשתה: $(grep -c '^  ok' "$W/lineafter.txt") בדיקות (קיפול הפעולה, המשתנים אחרי השורה, המרקר)"
# פעולה שרצה בתוך אובייקט: this הוא d1, המאפיינים שהשורה קוראת / משנה (כמו ב-Java)
viz objects/cs/Pets.cs --trace "$W/pets.trace" > "$W/pets.out" 2>&1 || { cat "$W/pets.out"; exit 1; }
java -Djava.awt.headless=true -Ddsviz.conditions=true -Dstdout.encoding=UTF-8 -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/pets.trace" > "$W/pets.txt" 2>&1
for want in 'this=d1 ב-הבנאי Dog() {name=null, age=0} this.name=שינוי]' 'נכנסים ל-d1.Birthday()]' \
    'this=d1 ב-Birthday() {name="רקס", age=3} this.age=שינוי]' 'this=d1 ב-Bark() {name="רקס", age=4} this.name=קריאה]' \
    'this=d2 ב-IsOlderThan() {name="לאסי", age=5} other←d1 this.age=קריאה other.age=קריאה]'; do
    grep -qF -- "$want" "$W/pets.txt" || { echo "אובייקט ב-C#: חסר $want"; grep 'אובייקט' "$W/pets.txt"; exit 1; }
done
echo "פעולה בתוך אובייקט ב-C#: $(grep -c 'אובייקט #' "$W/pets.txt") צעדים מסומנים"
# מחלקה של התלמיד עם ToString משלה (במשתנים, במחסנית, ברשימה, במערך, בערך מוחזר): ההרצה הוויזואלית מגיעה לסוף —
# בלי Quiet ה-ToString שנקרא בשביל התצוגה נרשם כצעדים, ומכל צעד שוב מציגים את המשתנים, וההרצה נתקעה
timeout 60 dotnet "$RUNNER" objects/cs/OwnToString.cs --trace "$W/owntostring.trace" > "$W/owntostring.out" 2>&1 || { echo "ToString של התלמיד: ההרצה הוויזואלית נתקעה / נכשלה"; cat "$W/owntostring.out"; exit 1; }
grep -q 'f=1/2 g=2/2 s=\[2/2,1/2\] o=2/2 2 1/2' "$W/owntostring.out" && grep -q '^STEP	END' "$W/owntostring.trace" \
    || { echo "ToString של התלמיד: הפלט / הסוף חסרים"; cat "$W/owntostring.out"; exit 1; }
n=$(grep -c '^STEP' "$W/owntostring.trace")
[ "$n" -lt 200 ] || { echo "ToString של התלמיד: $n צעדים — ה-ToString של התצוגה נרשם כצעדים"; exit 1; }
echo "מחלקה עם ToString משלה ב-C#: ההרצה הוויזואלית הגיעה לסוף ($n צעדים)"
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
# ImplicitUsings פעיל + הקוד משתמש בספרייה: System.Collections.Generic לא מוסתר — Stack של הספרייה לא דו-משמעי (כמו DSViz.targets)
viz "$VS/Recursion.cs" --trace "$W/vs4.trace" > "$W/vs4.out" 2>&1 && grep -q '^10 2$' "$W/vs4.out" \
    && echo "ImplicitUsings פעיל: Stack / Queue של הספרייה לא דו-משמעיים" || { echo "ImplicitUsings: Stack דו-משמעי"; cat "$W/vs4.out"; exit 1; }
# ומי שכתב בעצמו using System.Collections.Generic; — הסבר בעברית
cp "$VS/Recursion.cs" "$VS/Recursion.bak" && sed -i '1i using System.Collections.Generic;' "$VS/Recursion.cs"
viz "$VS/Recursion.cs" --trace "$W/vs4b.trace" > "$W/vs4b.out" 2>&1 || true; grep -q 'CS0104' "$W/vs4b.out" && grep -q 'מוחקים את השורה using System.Collections.Generic' "$W/vs4b.out" \
    && echo "Stack של .NET מול Stack של הספרייה (using ידני): הסבר בעברית" || { echo "CS0104: חסר הסבר"; cat "$W/vs4b.out"; exit 1; }
mv "$VS/Recursion.bak" "$VS/Recursion.cs"
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
# החלונות עצמם (Swing), אם יש מסך מדומה. כל ההודעות — RtlDialog (מימין לשמאל, בעיצוב של האתר); אין JOptionPane בקוד של הכלים
if grep -rln 'JOptionPane' --include='*.java' "$ROOT/csharp/new-exercise/src" | grep -v '/RtlDialog\.java$'; then
    echo "JOptionPane בכלים של Visual Studio (צריך RtlDialog)"; kill $MOCKW; exit 1
fi
echo "הודעות של Visual Studio: רק RtlDialog (בלי JOptionPane)"
if command -v Xvfb >/dev/null 2>&1; then
    (Xvfb :97 -screen 0 1600x900x24 >/dev/null 2>&1 &); sleep 2
    mkdir -p "$SUB/cls" && javac -d "$SUB/cls" "$ROOT/tutorials/harness/SwingScript.java"
    rm -rf "$SUB/root2" && cp -r "$ROOT/csharp/project" "$SUB/root2"
    # הטופס → תצוגת הבנייה באמצע (השלבים מההודעות של הבנייה) → ביטול (חזרה לטופס) → שליחה אמיתית → הכרטיס "התרגיל נוצר" → "הצגה"
    { printf 'pasteimg %s\ntext 0 רק שאלה 1\nshot s1\n' "$SUB/photo.jpg"
      for t in 'מכין את הקבצים…' 'שולח את התרגיל… (בדרך כלל עד דקה)' 'בונה את התרגיל…' 'בודק שהקוד מתקמפל…' 'יש שגיאות קומפילציה — מבקש תיקון…'; do echo "progress $t"; done
      printf 'wait 1300\nshot b1\nclick ביטול\nshot s1b\nclick שליחה ויצירת התרגיל\nwaitwin שליחת תרגיל — C#\nshot s2\nclick הצגה\nshot s3\n'; } > "$SUB/s1.txt"
    DISPLAY=:97 DSVIZ_WORKER_URL=http://127.0.0.1:8796 DSVIZ_CS="$RUNNER" timeout 120 java -Duser.home="$SUB/home" -Ddsviz.noOpen=true -cp "$SUB/cls:$JAR" \
        SwingScript "$SUB/s1.txt" "$SUB/shots" il.lamerhav.dsviz.cs.CsTool submit "$SUB/root2" > "$SUB/gui.txt" 2>&1 || true
    printf 'select 1\nshot l1\n' > "$SUB/s2.txt"
    DISPLAY=:97 timeout 60 java -Duser.home="$SUB/home" -cp "$SUB/cls:$JAR" SwingScript "$SUB/s2.txt" "$SUB/shots" il.lamerhav.dsviz.cs.CsTool solution "$SUB/root2" >> "$SUB/gui.txt" 2>&1 || true
    grep -q '"שליחה ויצירת התרגיל"' "$SUB/shots/s1.json" \
        && grep -q 'יש שגיאות קומפילציה · מבקש תיקון…' "$SUB/shots/b1.json" && grep -q 'בודק שהקוד מתקמפל' "$SUB/shots/b1.json" && grep -q '[1-9] שנ' "$SUB/shots/b1.json" \
        && grep -q '"ביטול"' "$SUB/shots/b1.json" && ! grep -q 'שליחה ויצירת התרגיל' "$SUB/shots/b1.json" && grep -q '"שליחה ויצירת התרגיל"' "$SUB/shots/s1b.json" \
        && grep -q 'התרגיל נוצר' "$SUB/shots/s2.json" && grep -q 'Ex01_Stacks.* · 3 סעיפים' "$SUB/shots/s2.json" && grep -q 'סעיף ד: עונים במילים' "$SUB/shots/s2.json" \
        && ! grep -q 'מהי סיבוכיות' "$SUB/shots/s2.json" && grep -q 'StackTargil.cs (שם Main)' "$SUB/shots/s2.json" \
        && grep -q 'מהי סיבוכיות זמן הריצה?' "$SUB/shots/s3.json" && grep -q '"הסתרה"' "$SUB/shots/s3.json" \
        && grep -q 'נבדק: הרצנו את main' "$SUB/shots/l1.json" && ! grep -q 'Moti Levi' "$SUB/shots/l1.json" \
        && echo "החלונות של Visual Studio: שליחת תרגיל → תצוגת הבנייה → כרטיס קצר (סעיף ד ב\"הצגה\"), והצעה לפתרון (נבדקה)" || { echo "החלונות של Visual Studio נכשלו"; cat "$SUB/gui.txt"; kill $MOCKW; exit 1; }
    # שגיאת קומפילציה שהתיקון לא תיקן: כרטיס לשגיאה, עם ההסבר בעברית
    node submit/mock-worker.js 8795 submit/cs-broken.json & MOCKB=$!
    sleep 1
    rm -rf "$SUB/root3" && cp -r "$ROOT/csharp/project" "$SUB/root3"
    printf 'pasteimg %s\nclick שליחה ויצירת התרגיל\nwaitwin שליחת תרגיל — C#\nshot e1\n' "$SUB/photo.jpg" > "$SUB/s3.txt"
    DISPLAY=:97 DSVIZ_WORKER_URL=http://127.0.0.1:8795 DSVIZ_CS="$RUNNER" timeout 120 java -Duser.home="$SUB/home" -Ddsviz.noOpen=true -cp "$SUB/cls:$JAR" \
        SwingScript "$SUB/s3.txt" "$SUB/shots" il.lamerhav.dsviz.cs.CsTool submit "$SUB/root3" >> "$SUB/gui.txt" 2>&1 || true
    kill $MOCKB
    grep -q 'יש שגיאת קומפילציה אחת' "$SUB/shots/e1.json" && grep -q 'הפירוט בחלון Error List' "$SUB/shots/e1.json" && ! grep -q 'CS0029' "$SUB/shots/e1.json" \
        && echo "שגיאות קומפילציה: רק התראה קצרה, הפירוט ב-Error List" || { echo "התראת השגיאות נכשלה"; cat "$SUB/gui.txt"; kill $MOCKW; exit 1; }
    # בלי אינטרנט: חלון אחד של RtlDialog עם "נסו שוב"
    printf 'shot o1\nclick סגירה\n' > "$SUB/s4.txt"
    DISPLAY=:97 DSVIZ_WORKER_URL=http://127.0.0.1:9 timeout 60 java -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9 -Duser.home="$SUB/home" -cp "$SUB/cls:$JAR" \
        SwingScript "$SUB/s4.txt" "$SUB/shots" il.lamerhav.dsviz.cs.CsTool submit "$SUB/root2" >> "$SUB/gui.txt" 2>&1 || true
    grep -q 'אין חיבור לאינטרנט' "$SUB/shots/o1.json" && grep -q '"נסו שוב"' "$SUB/shots/o1.json" \
        && echo "בלי אינטרנט: RtlDialog עם נסו שוב" || { echo "חלון בלי אינטרנט נכשל"; cat "$SUB/gui.txt"; kill $MOCKW; exit 1; }
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
javac -nowarn -encoding UTF-8 -d "$W/ai/cls" "$ROOT/access/src/il/lamerhav/dsviz/Json.java" "$ROOT/eclipse-plugin/src/il/lamerhav/dsviz/AiScaffold.java" AiScaffoldTest.java
java -Dstdout.encoding=UTF-8 -cp "$W/ai/cls" il.lamerhav.dsviz.AiScaffoldTest ai "$W/ai/src" > /dev/null
for d in $(cat "$W/ai/src/cs-dirs.txt"); do
    viz --check-project "$d" | tail -1 | grep -q '^errors: 0' || { viz --check-project "$d"; exit 1; }
done
echo "  ok  מעטפות C# מתקמפלות (dsviz-cs --check-project)"

echo "== 9ב. הרצה ויזואלית של קובץ בלי Main בתוך פרויקט: הסבר ברור (קוד 2), ולא Main של קובץ אחר (המחסנית של התבנית)"
NM="$W/nomain"; mkdir -p "$NM"
cat > "$NM/NoMain.csproj" <<'XML'
<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>enable</ImplicitUsings></PropertyGroup></Project>
XML
printf 'using Unit4.CollectionsLib;\npublic class Program { public static void Main(string[] args){ Stack<int> s = new Stack<int>(); s.Push(1); Console.WriteLine("stack-sum"); } }\n' > "$NM/Program.cs"
printf 'public class Loops { public static int Sum(int n){ int t=0; for(int i=1;i<=n;i++) t+=i; return t; } }\n' > "$NM/Loops.cs"
set +e; viz "$NM/Loops.cs" --trace "$NM/l.trace" > "$NM/l.out" 2>&1; NMC=$?; set -e
[ "$NMC" = 2 ] && grep -q 'אין פעולת Main' "$NM/l.out" && grep -q 'Program.cs' "$NM/l.out" && [ ! -s "$NM/l.trace" ] \
    || { echo "נכשל: קובץ בלי Main (קוד $NMC)"; cat "$NM/l.out"; exit 1; }
viz "$NM/Program.cs" --trace "$NM/p.trace" > "$NM/p.out" 2>&1 && grep -q 'stack-sum' "$NM/p.out" || { echo "נכשל: Program.cs עם Main"; cat "$NM/p.out"; exit 1; }
mkdir -p "$NM/empty"; cp "$NM/NoMain.csproj" "$NM/empty/Empty.csproj"
printf 'using System;\nnamespace list { class Program {\n public static int Count(int n) { return n; }\n static void Main(string[] args)\n {\n }\n} }\n' > "$NM/empty/Program.cs"
set +e; viz "$NM/empty/Program.cs" --trace "$NM/e.trace" > "$NM/e.out" 2>&1; EMC=$?; set -e
[ "$EMC" = 2 ] && grep -q 'Main ריקה' "$NM/e.out" || { echo "נכשל: Main ריקה (קוד $EMC)"; cat "$NM/e.out"; exit 1; }
echo "קובץ בלי Main: הודעה ברורה, בלי להריץ Main אחר · Main ריקה: הודעה · קובץ עם Main: רץ"

echo "== 10. Visual Studio בלי שום פעולה: DSViz.targets (MSBuild ImportAfter) — הספרייה ופרופיל ההרצה בפרויקט שמשתמש בספרייה; פרויקט רגיל לא משתנה"
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
# הפרופיל הראשון בקובץ הוא ברירת המחדל של הכפתור הירוק / F5 ב-Visual Studio: "הרצה רגילה" לפני "הרצה ויזואלית"
first_profile() { python3 -c "import json,sys; print(list(json.load(open(sys.argv[1], encoding='utf-8-sig'))['profiles'])[0])" "$1"; }
[ "$(first_profile "$W/new1/Properties/launchSettings.json")" = 'הרצה רגילה' ] \
    && echo "הפרופיל הראשון (ברירת המחדל של הכפתור הירוק): הרצה רגילה" || { echo "הפרופיל הראשון אינו 'הרצה רגילה'"; cat "$W/new1/Properties/launchSettings.json"; exit 1; }

# ב. בנייה שנייה: מהירה — dsviz-cs לא רץ שוב (ה-Exec מדלג כש-הפרופיל כבר קיים)
rm -f "$FAKE/DSViz/cs/calls.log"
msb "$W/new1" | grep -q 'Build succeeded'
[ ! -f "$FAKE/DSViz/cs/calls.log" ] && echo "בנייה שנייה: לא רץ dsviz-cs.exe שוב (מהיר)" || { echo "בנייה שנייה הריצה את ה-Exec שלא לצורך"; cat "$FAKE/DSViz/cs/calls.log"; exit 1; }

# ג. launchSettings.json קיים עם פרופיל אחר של התלמיד — נשאר, והפרופיל שלנו מתווסף לידו
rm -rf "$W/new2" && mkdir -p "$W/new2/Properties"
printf 'using System;\nusing Unit4.CollectionsLib;\nStack<int> st = new Stack<int>(); st.Push(3);\nConsole.WriteLine("hi");\n' > "$W/new2/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/new2/New2.csproj"
printf '{"profiles":{"שלי":{"commandName":"Project","environmentVariables":{"X":"1"}}}}\n' > "$W/new2/Properties/launchSettings.json"
msb "$W/new2" | grep -q 'Build succeeded'
grep -q '"שלי"' "$W/new2/Properties/launchSettings.json" && grep -q '"X": *"1"' "$W/new2/Properties/launchSettings.json" && grep -q 'הרצה ויזואלית' "$W/new2/Properties/launchSettings.json" \
    && ! grep -q 'הרצה רגילה' "$W/new2/Properties/launchSettings.json" && [ "$(first_profile "$W/new2/Properties/launchSettings.json")" = 'שלי' ] \
    && echo "launchSettings.json קיים: הפרופיל של התלמיד נשאר ראשון, והפרופיל שלנו נוסף אחריו (בלי 'הרצה רגילה' כפולה)" || { echo "פרופיל קיים לא נשמר"; cat "$W/new2/Properties/launchSettings.json"; exit 1; }

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
#    סופרים קבצים שונים (sort -u): ב-SDK חדש יותר שורת המהדר מודפסת פעמיים ב-verbosity normal — אותה הפניה, לא הפניה כפולה
rm -rf "$W/gw" && mkdir -p "$W/gw"
cp calls/vs/GoldWater.csproj calls/vs/Recursion.cs "$W/gw/"
cp "$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/Unit4.CollectionsLib.dll" "$W/Unit4.CollectionsLib.dll"   # ה-HintPath בפרויקט: ..\Unit4.CollectionsLib.dll
V=normal msb "$W/gw" > "$W/gw.out" 2>&1; grep -q 'Build succeeded' "$W/gw.out" \
    && [ "$(grep -o '/reference:[^ ]*Unit4\.CollectionsLib\.dll' "$W/gw.out" | sort -u | wc -l)" = 1 ] \
    && ! grep -o '/reference:[^ ]*Unit4\.CollectionsLib\.dll' "$W/gw.out" | grep -q msbuild-home \
    && echo "פרויקט עם הפניה משלו לספרייה: בלי הפניה כפולה (נשארת ההפניה של הפרויקט)" || { echo "הפניה כפולה לספרייה"; grep reference "$W/gw.out" | tr ' ' '\n' | grep -i unit4; exit 1; }

# ז. ה-DLL חסר (למשל התקנה חלקית) — הבנייה מצליחה בכל זאת, ופרופיל ההרצה עדיין מתווסף
rm -rf "$W/new5" && mkdir -p "$W/new5"
printf 'using System;\n// using Unit4.CollectionsLib;\nConsole.WriteLine("hi");\n' > "$W/new5/Program.cs"
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
        && [ "$(grep -o '/reference:[^ ]*Unit4\.CollectionsLib\.dll' "$W/$d.out" | sort -u | wc -l)" = 1 ] \
        && ! grep -o '/reference:[^ ]*Unit4\.CollectionsLib\.dll' "$W/$d.out" | grep -q msbuild-home \
        || { echo "$d: הפניה כפולה / בנייה נכשלה"; grep -E 'error|warning' "$W/$d.out" | head; exit 1; }
done
echo "הפניה בשם מלא / ProjectReference לספרייה: בלי הפניה כפולה"

# ט. אידמפוטנטי: --ensure-profile שוב לא משנה את הקובץ; launchSettings.json פגום (לא JSON) — הבנייה מצליחה והקובץ לא נוגע
before=$(md5sum < "$W/new2/Properties/launchSettings.json")
viz --ensure-profile "$W/new2" && [ "$(md5sum < "$W/new2/Properties/launchSettings.json")" = "$before" ] \
    || { echo "--ensure-profile שינה קובץ שכבר יש בו את הפרופיל"; exit 1; }
rm -rf "$W/new8" && mkdir -p "$W/new8/Properties"
printf 'using System;\nusing Unit4.CollectionsLib;\nConsole.WriteLine("hi");\n' > "$W/new8/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/new8/New8.csproj"
printf '{ "profiles": { oops\n' > "$W/new8/Properties/launchSettings.json"
msb "$W/new8" | grep -q 'Build succeeded' && [ "$(cat "$W/new8/Properties/launchSettings.json")" = '{ "profiles": { oops' ] \
    && echo "אידמפוטנטי, ו-launchSettings.json פגום לא שובר את הבנייה (והקובץ לא נדרס)" || { echo "launchSettings.json פגום: בעיה"; exit 1; }

# ט2. פרופיל מגרסה ישנה ("$(ProjectDir)" — ב-Windows הנתיב מגיע עם מרכאה בסוף): --ensure-profile מתקן ל-"$(ProjectDir).",
#     ו-dsviz-cs מקבל גם נתיב עם מרכאה בסוף (C:\...\Targil2") — בלי חריגה שעוצרת את Visual Studio
sed -i 's|"$(ProjectDir)\.\\"|"$(ProjectDir)\\"|' "$W/new2/Properties/launchSettings.json"
grep -q '"\\"$(ProjectDir)\\""' "$W/new2/Properties/launchSettings.json" || { echo "הכנת פרופיל ישן נכשלה"; cat "$W/new2/Properties/launchSettings.json"; exit 1; }
viz --ensure-profile "$W/new2" && grep -q '"\\"$(ProjectDir).\\""' "$W/new2/Properties/launchSettings.json" && grep -q '"שלי"' "$W/new2/Properties/launchSettings.json" \
    || { echo "פרופיל ישן לא תוקן"; cat "$W/new2/Properties/launchSettings.json"; exit 1; }
viz "$W/new8/\"" --trace "$W/q.trace" > "$W/q.out" 2>&1; rc=$?
[ $rc -ne 3 ] && ! grep -q 'Unhandled exception' "$W/q.out" && grep -q 'hi' "$W/q.out" \
    && echo "פרופיל ישן תוקן ל-\"\$(ProjectDir).\", ונתיב עם מרכאה בסוף עובד" || { echo "נתיב עם מרכאה בסוף: נכשל ($rc)"; cat "$W/q.out"; exit 1; }
# ט3. מה ש-Windows נותן ל-dsviz-cs מפרופיל ישן (כללי CommandLineToArgvW: ‎"C:\dir\"‎ → ‎C:\dir"‎, וכל מה שאחריו נבלע לאותו ארגומנט):
#     ‎"C:\dir\" --trace t‎ → ארגומנט אחד ‎C:\dir" --trace t‎; נתיב בעברית עם רווחים; מרכאה רק בהתחלה; קובץ .csproj; נתיב שלא קיים —
#     הודעה בעברית (קוד 2), בלי חריגה שלא נתפסה (שב-Visual Studio עוצרת את הדיבאגר בתוך .NET)
rm -f "$W/q2.trace" && viz "$W/new8\" --trace $W/q2.trace" > "$W/q2.out" 2>&1 && [ -s "$W/q2.trace" ] && grep -q 'hi' "$W/q2.out" \
    || { echo "ארגומנטים שנבלעו אחרי נתיב עם מרכאה: נכשל"; cat "$W/q2.out"; exit 1; }
rm -rf "$W/heb q" && mkdir -p "$W/heb q/מבני נתונים/תרגיל 3" && cp "$W/new8/Program.cs" "$W/new8/New8.csproj" "$W/heb q/מבני נתונים/תרגיל 3/"
viz "$W/heb q/מבני נתונים/תרגיל 3\"" --trace "$W/q3.trace" > "$W/q3.out" 2>&1 && grep -q 'hi' "$W/q3.out" \
    && viz "\"$W/new8" --trace "$W/q4.trace" > "$W/q4.out" 2>&1 && grep -q 'hi' "$W/q4.out" \
    && viz " $W/new8/New8.csproj\" " --trace "$W/q5.trace" > "$W/q5.out" 2>&1 && grep -q 'hi' "$W/q5.out" \
    && viz --check-project "$W/new8\"" | grep -q '^errors: 0' \
    || { echo "נתיב בעברית / מרכאה בהתחלה / csproj עם מרכאה: נכשל"; cat "$W/q3.out" "$W/q4.out" "$W/q5.out"; exit 1; }
rc=0; viz "$W/no-such-dir\"" --trace "$W/q6.trace" > "$W/q6.out" 2>&1 || rc=$?
[ $rc -eq 2 ] && grep -q 'לא נמצאו תיקייה או קובץ' "$W/q6.out" && grep -qF '"\"$(ProjectDir).\""' "$W/q6.out" && ! grep -q 'Exception' "$W/q6.out" \
    || { echo "נתיב שלא קיים: צריך הודעה בעברית וקוד 2 ($rc)"; cat "$W/q6.out"; exit 1; }
rc=0; viz --check-project "$W/no-such-dir" > "$W/q7.out" 2>&1 || rc=$?
[ $rc -eq 2 ] && grep -q 'לא נמצאו תיקייה או קובץ' "$W/q7.out" || { echo "--check-project לנתיב שלא קיים: $rc"; cat "$W/q7.out"; exit 1; }
viz --ensure-profile "$W/no-such-dir\"" && [ ! -e "$W/no-such-dir" ] || { echo "--ensure-profile לנתיב שלא קיים נכשל"; exit 1; }
echo "ארגומנטים כמו ש-Windows מעביר מפרופיל ישן (נבלעו / עברית ורווחים / מרכאה בהתחלה / csproj), ונתיב שלא קיים — הודעה בעברית"
# ט4. פרויקט של התלמיד שכבר יש בו פרופיל ישן ("$(ProjectDir)", גם עם ארגומנטים אחריו): הבנייה (DSViz.targets) מתקנת אותו לבד —
#     גם כשהפרופיל כבר קיים — ואחרי התיקון בנייה נוספת לא מריצה את dsviz-cs שוב
sed -i 's|"\\"$(ProjectDir)\.\\""|"\\"$(ProjectDir)\\" --input in.txt"|' "$W/new2/Properties/launchSettings.json"
grep -qF '"\"$(ProjectDir)\" --input in.txt"' "$W/new2/Properties/launchSettings.json" || { echo "הכנת פרופיל ישן (ט4) נכשלה"; cat "$W/new2/Properties/launchSettings.json"; exit 1; }
rm -f "$FAKE/DSViz/cs/calls.log"
msb "$W/new2" | grep -q 'Build succeeded' && grep -qF '"\"$(ProjectDir).\" --input in.txt"' "$W/new2/Properties/launchSettings.json" \
    && grep -q '"שלי"' "$W/new2/Properties/launchSettings.json" && grep -q 'ensure-profile' "$FAKE/DSViz/cs/calls.log" \
    || { echo "DSViz.targets לא תיקן פרופיל ישן בפרויקט קיים"; cat "$W/new2/Properties/launchSettings.json"; exit 1; }
rm -f "$FAKE/DSViz/cs/calls.log"
msb "$W/new2" | grep -q 'Build succeeded' && [ ! -f "$FAKE/DSViz/cs/calls.log" ] \
    || { echo "אחרי התיקון: הבנייה עדיין מריצה את dsviz-cs"; cat "$FAKE/DSViz/cs/calls.log"; exit 1; }
sed -i 's|"\\"$(ProjectDir)\.\\" --input in.txt"|"\\"$(ProjectDir).\\""|' "$W/new2/Properties/launchSettings.json"
echo "פרופיל ישן בפרויקט קיים (גם עם ארגומנטים אחריו): הבנייה מתקנת אותו לבד, ובנייה נוספת לא מריצה שוב את dsviz-cs"

# י. כמה מסגרות (TargetFrameworks) — הבנייה החיצונית לא נשברת, והבנייה הפנימית מקבלת הכול
rm -rf "$W/new9" && mkdir -p "$W/new9"
printf 'using System;\nusing Unit4.CollectionsLib;\nConsole.WriteLine(new Stack<int>().IsEmpty());\n' > "$W/new9/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFrameworks>net8.0</TargetFrameworks><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/new9/New9.csproj"
msb "$W/new9" | grep -q 'Build succeeded' && grep -q 'הרצה ויזואלית' "$W/new9/Properties/launchSettings.json" \
    && echo "TargetFrameworks (כמה מסגרות): תקין" || { echo "TargetFrameworks: נכשל"; exit 1; }

# יא. פרויקט קונסול חדש של Visual Studio (ImplicitUsings פעיל, top-level) + using Unit4.CollectionsLib + Stack: נבנה ורץ (בלי CS0104)
rm -rf "$W/new11" && mkdir -p "$W/new11"
printf 'using Unit4.CollectionsLib;\n\nStack<int> s = new Stack<int>(); s.Push(1); s.Push(2);\nQueue<int> q = new Queue<int>(); q.Insert(7);\nConsole.WriteLine(s.Pop() + q.Remove());\n' > "$W/new11/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>enable</ImplicitUsings><Nullable>enable</Nullable></PropertyGroup></Project>\n' > "$W/new11/New11.csproj"
msb "$W/new11" | grep -q 'Build succeeded' && [ "$(dotnet "$W/new11/bin/Debug/net8.0/New11.dll")" = 9 ] \
    && echo "ImplicitUsings פעיל + Stack / Queue של הספרייה: נבנה ורץ בלי CS0104 (כמו בפרויקט חדש של Visual Studio)" || { echo "ImplicitUsings + Stack: נכשל"; msb "$W/new11" 2>&1 | head -5; exit 1; }
# יב. ImplicitUsings פעיל, בלי הספרייה בקוד: System.Collections.Generic נשאר מוסתר (List בלי using) — לא נוגעים בפרויקטים אחרים של התלמיד
rm -rf "$W/new12" && mkdir -p "$W/new12"
printf 'var l = new List<int> { 3, 4 };\nConsole.WriteLine(l.Count);\n' > "$W/new12/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>enable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/new12/New12.csproj"
msb "$W/new12" | grep -q 'Build succeeded' && [ "$(dotnet "$W/new12/bin/Debug/net8.0/New12.dll")" = 2 ] \
    && echo "ImplicitUsings פעיל, בלי הספרייה בקוד: List בלי using ממשיך לעבוד" || { echo "ImplicitUsings בפרויקט בלי הספרייה: נשבר"; exit 1; }
# יג. נתיב בעברית ורווחים (ה-target קורא את קובצי הקוד)
rm -rf "$W/new13" && mkdir -p "$W/new13/מבני נתונים/תרגיל 1"
cp "$W/new11/Program.cs" "$W/new11/New11.csproj" "$W/new13/מבני נתונים/תרגיל 1/"
msb "$W/new13/מבני נתונים/תרגיל 1" | grep -q 'Build succeeded' \
    && echo "נתיב בעברית ורווחים: נבנה" || { echo "נתיב בעברית: נכשל"; exit 1; }
# יד. פרופיל שנוצר ע"י "שליחת תרגיל" (CsSolution.LAUNCH, vscode cs.js): עם נקודה — כמו הפרופיל של dsviz-cs
grep -q 'ProjectDir).\\\\' "$ROOT/eclipse-plugin/src/il/lamerhav/dsviz/CsSolution.java" && grep -q "ProjectDir).\"'" "$ROOT/vscode-extension/cs.js" \
    && echo "פרופיל הרצה בפרויקטים שנוצרים משליחת תרגיל: עם נקודה בסוף הנתיב" || { echo "פרופיל ישן בקוד שיוצר פרויקטים"; exit 1; }

# טו. הפרויקט כבר מפנה לספרייה של בית הספר (assembly בשם Unit4, אותו namespace Unit4.CollectionsLib): DSViz.targets לא מוסיף את הספרייה שלנו —
#     אחרת CS0433 ("The type 'Node<T>' exists in both 'Unit4.CollectionsLib' and 'Unit4'") בכל שורה שמשתמשת ב-Node / Queue / Stack
rm -rf "$W/school" && mkdir -p "$W/school/lib" "$W/school/app"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><TargetFramework>net8.0</TargetFramework><AssemblyName>Unit4</AssemblyName></PropertyGroup></Project>\n' > "$W/school/lib/Unit4.csproj"
printf 'namespace Unit4.CollectionsLib { public class Node<T> { } public class Queue<T> { } }\n' > "$W/school/lib/Lib.cs"
msb "$W/school/lib" | grep -q 'Build succeeded' || { echo "ספריית בית הספר המדומה לא נבנתה"; exit 1; }
printf 'using System;\nusing Unit4.CollectionsLib;\nclass P { static void Main() { Node<int> n = new Node<int>(); Queue<int> q = new Queue<int>(); Console.WriteLine("ok"); } }\n' > "$W/school/app/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>disable</ImplicitUsings></PropertyGroup><ItemGroup><Reference Include="Unit4"><HintPath>../lib/bin/Debug/net8.0/Unit4.dll</HintPath></Reference></ItemGroup></Project>\n' > "$W/school/app/List.csproj"
msb "$W/school/app" > "$W/school.out" 2>&1
grep -q 'Build succeeded' "$W/school.out" && ! grep -q CS0433 "$W/school.out" && [ ! -f "$W/school/app/bin/Debug/net8.0/Unit4.CollectionsLib.dll" ] && [ -f "$W/school/app/bin/Debug/net8.0/Unit4.dll" ] \
    && echo "הפרויקט מפנה לספרייה של בית הספר (Unit4): בלי הפניה לספרייה של DSViz — בלי CS0433" || { echo "התנגשות עם Unit4 של בית הספר"; cat "$W/school.out" | head; exit 1; }

# טז. פרויקט קונסול רגיל שאין לו קשר ל-DSViz (ImplicitUsings, Stack<int> של ‎.NET עם Count): נבנה ורץ בדיוק כמו בלי DSViz —
#     בלי global using של הספרייה (קודם: CS1503), בלי ה-DLL שלנו ב-bin, ובלי launchSettings.json שנכתב לפרויקט
rm -rf "$W/plain" && mkdir -p "$W/plain"
printf 'var s = new Stack<int>();\ns.Push(1); s.Push(2);\nConsole.WriteLine(s.Count);\n' > "$W/plain/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>enable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/plain/Plain.csproj"
rm -f "$FAKE/DSViz/cs/calls.log"
msb "$W/plain" > "$W/plain.out" 2>&1
grep -q 'Build succeeded' "$W/plain.out" && [ "$(dotnet "$W/plain/bin/Debug/net8.0/Plain.dll")" = 2 ] \
    && [ ! -f "$W/plain/bin/Debug/net8.0/Unit4.CollectionsLib.dll" ] && [ ! -e "$W/plain/Properties" ] && [ ! -f "$FAKE/DSViz/cs/calls.log" ] \
    && echo "פרויקט רגיל עם Stack<int> של ‎.NET: נבנה ורץ כמו בלי DSViz (בלי הספרייה, בלי using מוסתר, בלי launchSettings)" || { echo "DSViz.targets שינה פרויקט רגיל"; grep -E 'error|warn' "$W/plain.out" | head -5; ls -a "$W/plain"; exit 1; }
# ההרצה הוויזואלית של אותו פרויקט: רצה (הספרייה "לבד" לא מתאימה ל-Count — מקמפלים כמו Visual Studio), ולא "שגיאת קומפילציה"
viz "$W/plain" --trace "$W/plain.trace" > "$W/plain.viz.out" 2>&1 && grep -q '^2$' "$W/plain.viz.out" \
    && echo "הרצה ויזואלית של פרויקט רגיל עם Stack של ‎.NET: רצה (2)" || { echo "הרצה ויזואלית של פרויקט רגיל נכשלה"; cat "$W/plain.viz.out"; exit 1; }
rm -rf "$W/plain/Properties"

# יז. הספרייה של בית הספר ב-ProjectReference, כשקובץ הפרויקט שלה נקרא אחרת (CollectionsLib.csproj, AssemblyName=Unit4): ההחלטה אחרי
#     שההפניות נפתרו — לפי שם ה-assembly, לא לפי שם הקובץ. בלי CS0433, ב-bin הספרייה שלהם ולא שלנו
rm -rf "$W/school2" && mkdir -p "$W/school2/CollectionsLib" "$W/school2/app"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><TargetFramework>netstandard2.0</TargetFramework><AssemblyName>Unit4</AssemblyName></PropertyGroup></Project>\n' > "$W/school2/CollectionsLib/CollectionsLib.csproj"
printf 'namespace Unit4.CollectionsLib { public class Node<T> { T v; public Node(T x) { v = x; } public T GetValue() { return v; } } }\n' > "$W/school2/CollectionsLib/Node.cs"
printf 'using Unit4.CollectionsLib;\nvar n = new Node<int>(3);\nConsole.WriteLine(typeof(Node<int>).Assembly.GetName().Name + " " + n.GetValue());\n' > "$W/school2/app/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>enable</ImplicitUsings></PropertyGroup><ItemGroup><ProjectReference Include="../CollectionsLib/CollectionsLib.csproj" /></ItemGroup></Project>\n' > "$W/school2/app/App.csproj"
msb "$W/school2/app" > "$W/school2.out" 2>&1
grep -q 'Build succeeded' "$W/school2.out" && ! grep -q CS0433 "$W/school2.out" && [ ! -f "$W/school2/app/bin/Debug/net8.0/Unit4.CollectionsLib.dll" ] \
    && [ "$(dotnet "$W/school2/app/bin/Debug/net8.0/App.dll")" = 'Unit4 3' ] \
    && echo "ספריית בית הספר ב-ProjectReference (CollectionsLib.csproj, AssemblyName=Unit4): בלי CS0433, ההרצה הרגילה עם הספרייה שלהם" || { echo "ProjectReference לספריית בית הספר: נכשל"; grep -E 'error' "$W/school2.out" | head -5; exit 1; }
# וההרצה הוויזואלית של אותו קוד — עם הספרייה של DSViz (אותו קוד, בלי שינוי)
viz "$W/school2/app" --trace "$W/school2.trace" > "$W/school2.viz.out" 2>&1 && grep -q '^Unit4.CollectionsLib 3$' "$W/school2.viz.out" \
    && echo "אותו פרויקט בהרצה ויזואלית: הספרייה של DSViz (Unit4.CollectionsLib)" || { echo "הרצה ויזואלית של פרויקט בית הספר נכשלה"; cat "$W/school2.viz.out"; exit 1; }

# יח. פרויקט של DSViz (תיקיית התרגילים: Directory.Build.props שם את הסימון DSVizProject) — עדיין מקבל את ה-using המוסתרים:
#     Stack / Queue בלי using Unit4.CollectionsLib (DSViz.targets), ו-Size בלי Helpers. (Directory.Build.props)
rm -rf "$W/marked" && mkdir -p "$W/marked/lib" "$W/marked/Targil"
cp "$ROOT/csharp/project/Directory.Build.props" "$W/marked/"
cp "$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/Unit4.CollectionsLib.dll" "$W/marked/lib/"
cp "$ROOT/csharp/project/Exercise/Exercise.csproj" "$W/marked/Targil/Targil.csproj"
printf 'using System;\nclass P { static void Main() { Stack<int> s = new Stack<int>(); s.Push(4); s.Push(5); Queue<int> q = new Queue<int>(); q.Insert(1); Console.WriteLine(Size(s) + Size(q)); } }\n' > "$W/marked/Targil/Program.cs"
msb "$W/marked/Targil" > "$W/marked.out" 2>&1
grep -q 'Build succeeded' "$W/marked.out" && [ "$(dotnet "$W/marked/Targil/bin/Debug/net8.0/Targil.dll")" = 3 ] \
    && echo "פרויקט של DSViz (מסומן): Stack / Queue / Size בלי שום using — נבנה ורץ" || { echo "פרויקט מסומן: ה-using המוסתרים לא נוספו"; grep -E 'error' "$W/marked.out" | head -5; exit 1; }
# אותו קוד בפרויקט לא מסומן (בלי Directory.Build.props): לא נבנה — Stack שם הוא של ‎.NET, כמו בלי DSViz (אין using אוטומטי)
rm -rf "$W/unmarked" && mkdir -p "$W/unmarked"
printf 'Stack<int> s = new Stack<int>(); s.Push(4);\nQueue<int> q = new Queue<int>(); q.Insert(1);\nConsole.WriteLine(s.Count);\n' > "$W/unmarked/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>enable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/unmarked/Unmarked.csproj"
msb "$W/unmarked" > "$W/unmarked.out" 2>&1 || true
grep -q "error CS1061.*'Insert'" "$W/unmarked.out" && ! grep -q CS1503 "$W/unmarked.out" \
    && echo "פרויקט לא מסומן: אין using אוטומטי של הספרייה (Queue הוא של ‎.NET — אותה שגיאה כמו בלי DSViz)" || { echo "פרויקט לא מסומן קיבל using אוטומטי"; grep -E 'error' "$W/unmarked.out" | head -5; exit 1; }
# יח2. פרויקט מסומן שה-lib\ של תיקיית התרגילים חסר בו: נבנה עם הספרייה שבהתקנה
rm -f "$W/marked/lib/Unit4.CollectionsLib.dll" && rm -rf "$W/marked/Targil/bin" "$W/marked/Targil/obj"
msb "$W/marked/Targil" > "$W/marked2.out" 2>&1
grep -q 'Build succeeded' "$W/marked2.out" && [ "$(dotnet "$W/marked/Targil/bin/Debug/net8.0/Targil.dll")" = 3 ] \
    && echo "פרויקט מסומן בלי lib\\ (נמחק): נבנה עם הספרייה שבהתקנה" || { echo "פרויקט מסומן בלי lib: נכשל"; grep -E 'error' "$W/marked2.out" | head -5; exit 1; }

# יט. הספרייה (Node) בקובץ אחד, ואוספים של ‎.NET (List, Stack עם Count) מה-using המוסתר בקובץ אחר: System.Collections.Generic נשאר —
#     פרויקט שנבנה בלי DSViz נבנה גם איתו
rm -rf "$W/mixed" && mkdir -p "$W/mixed"
printf 'using Unit4.CollectionsLib;\nNode<int> n = new Node<int>(7);\nConsole.WriteLine(n.GetValue() + Other.Total());\n' > "$W/mixed/Program.cs"
printf 'static class Other { public static int Total() { var l = new List<int> { 1, 2 }; var s = new Stack<int>(); s.Push(5); return l.Count + s.Count; } }\n' > "$W/mixed/Other.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>enable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/mixed/Mixed.csproj"
msb "$W/mixed" > "$W/mixed.out" 2>&1
grep -q 'Build succeeded' "$W/mixed.out" && [ "$(dotnet "$W/mixed/bin/Debug/net8.0/Mixed.dll")" = 10 ] \
    && echo "הספרייה + אוספים של ‎.NET באותו פרויקט: ה-using המוסתר נשאר, נבנה ורץ" || { echo "פרויקט מעורב: נכשל"; grep -E 'error' "$W/mixed.out" | head -5; exit 1; }

# כ. קובץ מגרסה ישנה: "הרצה ויזואלית" ראשונה (הכפתור הירוק הריץ ויזואלית). הבנייה מעבירה את "הרצה רגילה" לראש — פעם אחת
rm -rf "$W/oldorder" && mkdir -p "$W/oldorder/Properties"
cp "$W/new1/Program.cs" "$W/oldorder/Program.cs"; cp "$W/new1/New1.csproj" "$W/oldorder/OldOrder.csproj"
printf '{\n  "profiles": {\n    "הרצה ויזואלית": {\n      "commandName": "Executable",\n      "executablePath": "%%LOCALAPPDATA%%\\\\DSViz\\\\cs\\\\dsviz-cs.exe",\n      "commandLineArgs": "\\"$(ProjectDir).\\"",\n      "workingDirectory": "$(ProjectDir)"\n    },\n    "הרצה רגילה": {\n      "commandName": "Project"\n    }\n  }\n}\n' > "$W/oldorder/Properties/launchSettings.json"
[ "$(first_profile "$W/oldorder/Properties/launchSettings.json")" = 'הרצה ויזואלית' ] || { echo "הכנת קובץ ישן (כ) נכשלה"; exit 1; }
rm -f "$FAKE/DSViz/cs/calls.log"
msb "$W/oldorder" | grep -q 'Build succeeded' && [ "$(first_profile "$W/oldorder/Properties/launchSettings.json")" = 'הרצה רגילה' ] \
    && grep -q 'הרצה ויזואלית' "$W/oldorder/Properties/launchSettings.json" && grep -q 'ensure-profile' "$FAKE/DSViz/cs/calls.log" \
    || { echo "קובץ ישן: 'הרצה רגילה' לא עברה לראש"; cat "$W/oldorder/Properties/launchSettings.json"; exit 1; }
rm -f "$FAKE/DSViz/cs/calls.log"
msb "$W/oldorder" | grep -q 'Build succeeded' && [ ! -f "$FAKE/DSViz/cs/calls.log" ] \
    || { echo "אחרי העברת 'הרצה רגילה' לראש: הבנייה עדיין מריצה את dsviz-cs"; cat "$FAKE/DSViz/cs/calls.log"; exit 1; }
# בלי הרצה רגילה בכלל (רק "הרצה ויזואלית"): נוספת "הרצה רגילה" בראש
printf '{ "profiles": { "הרצה ויזואלית": { "commandName": "Executable", "executablePath": "x", "commandLineArgs": "\\"$(ProjectDir).\\"" } } }\n' > "$W/oldorder/Properties/launchSettings.json"
msb "$W/oldorder" | grep -q 'Build succeeded' && [ "$(first_profile "$W/oldorder/Properties/launchSettings.json")" = 'הרצה רגילה' ] \
    && echo "קובץ מגרסה ישנה ('הרצה ויזואלית' ראשונה, או בלי הרצה רגילה): 'הרצה רגילה' עוברת לראש — פעם אחת" || { echo "קובץ בלי הרצה רגילה: נכשל"; cat "$W/oldorder/Properties/launchSettings.json"; exit 1; }
# הפרופיל הראשון של המשתמש אינו "Project" (ואין הרצה רגילה בכלל), ו"הרצה ויזואלית" אחריו: הסדר שלו נשאר, והבנייה לא מריצה את dsviz-cs בכל פעם
printf '{ "profiles": { "WSL": { "commandName": "WSL2" }, "הרצה ויזואלית": { "commandName": "Executable", "executablePath": "x", "commandLineArgs": "\\"$(ProjectDir).\\"" }, "שליחת תרגיל": { "commandName": "Executable" } } }\n' > "$W/oldorder/Properties/launchSettings.json"
rm -f "$FAKE/DSViz/cs/calls.log"
msb "$W/oldorder" | grep -q 'Build succeeded' && [ "$(first_profile "$W/oldorder/Properties/launchSettings.json")" = 'WSL' ] && [ ! -f "$FAKE/DSViz/cs/calls.log" ] \
    && echo "הפרופיל הראשון של המשתמש (לא Project): נשאר ראשון, בלי להריץ את dsviz-cs בכל בנייה" || { echo "פרופיל ראשון של המשתמש: נכשל"; cat "$W/oldorder/Properties/launchSettings.json"; cat "$FAKE/DSViz/cs/calls.log" 2>/dev/null; exit 1; }
# הפרופילים שנוצרים עם התרגילים (התבנית, "תרגיל חדש" / המאגר, VS Code): "הרצה רגילה" ראשונה
for f in "$ROOT"/csharp/project/Exercise/Properties/launchSettings.json "$ROOT"/csharp/project/Examples/*/Properties/launchSettings.json "$W/csroot/Exercises/Targil1/Properties/launchSettings.json"; do
    [ "$(first_profile "$f")" = 'הרצה רגילה' ] || { echo "הפרופיל הראשון אינו 'הרצה רגילה': $f"; exit 1; }
done
node -e "const cs = require('$ROOT/vscode-extension/cs.js'); if (Object.keys(cs.LAUNCH.profiles)[0] !== 'הרצה רגילה') process.exit(1)" \
    && echo "התבנית, 'תרגיל חדש' ו-VS Code: 'הרצה רגילה' ראשונה בכל launchSettings.json" || { echo "cs.js: 'הרצה רגילה' לא ראשונה"; exit 1; }

# כא. פרויקט ‎.NET Framework (net48) שהקוד שלו משתמש בספרייה בלי הפניה: הספרייה מתווספת אחרי שההפניות נפתרו — יחד עם netstandard.dll
#     (הספרייה היא netstandard2.0; בלי זה CS0012 בכל שורה). צריך את חבילת ה-reference assemblies מ-NuGet — בלי רשת מדלגים
rm -rf "$W/fw1" && mkdir -p "$W/fw1"
printf 'using System;\nusing Unit4.CollectionsLib;\nclass P { static void Main() { Stack<int> s = new Stack<int>(); s.Push(4); Console.WriteLine(s.Pop()); } }\n' > "$W/fw1/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net48</TargetFramework></PropertyGroup></Project>\n' > "$W/fw1/Fw1.csproj"
if msb "$W/fw1" > "$W/fw1.out" 2>&1; then
    ! grep -q CS0012 "$W/fw1.out" && [ -f "$W/fw1/bin/Debug/net48/Unit4.CollectionsLib.dll" ] \
        && echo "פרויקט ‎.NET Framework 4.8 בלי הפניה: הספרייה (ו-netstandard) מתווספות לבד — נבנה" || { echo ".NET Framework בלי הפניה: נכשל"; grep -E 'error' "$W/fw1.out" | sort -u | head -5; exit 1; }
elif grep -qE 'NU1101|NU1301|MSB3644|Unable to load the service index' "$W/fw1.out"; then
    echo "(net48: אין גישה לחבילת ה-reference assemblies — הבדיקה דולגה)"
else
    echo ".NET Framework בלי הפניה: הבנייה נכשלה"; grep -E 'error' "$W/fw1.out" | sort -u | head -5; exit 1
fi

# כב. פרויקט עם גרסת שפה ישנה (LangVersion 7.3 — ברירת המחדל של ‎.NET Framework): ההרצה הוויזואלית מוסיפה קובץ global using משלה (C# 10) —
#     בגרסת השפה של הפרויקט הוא לא היה מתקמפל (CS8773) וההרצה הוויזואלית לא הייתה מתחילה
rm -rf "$W/cs73" && mkdir -p "$W/cs73"
printf 'using System;\nusing Unit4.CollectionsLib;\nclass Program\n{\n    static void Main(string[] args)\n    {\n        Stack<int> s = new Stack<int>();\n        s.Push(7);\n        Console.WriteLine("cs73 " + s.Pop());\n    }\n}\n' > "$W/cs73/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><LangVersion>7.3</LangVersion><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>\n' > "$W/cs73/Cs73.csproj"
viz "$W/cs73" --trace "$W/cs73.trace" > "$W/cs73.out" 2>&1 || true
[ -s "$W/cs73.trace" ] && grep -q 'cs73 7' "$W/cs73.out" && ! grep -qE 'CS8773|error CS' "$W/cs73.out" \
    && echo "פרויקט עם LangVersion 7.3: ההרצה הוויזואלית מקמפלת ומריצה אותו" || { echo "הרצה ויזואלית של פרויקט עם LangVersion 7.3 נכשלה"; head -5 "$W/cs73.out"; exit 1; }

# כג. ההפרדה בין ההרצה הרגילה להרצה הוויזואלית, על אותו קובץ בדיוק (using Unit4.CollectionsLib; פעם אחת, בלי שום שינוי של המשתמש), מול
#     הספרייה של בית הספר: ב-Reference — Unit4.dll האמיתי שבתי הספר מחלקים; ב-ProjectReference — ספרייה מדומה עם אותו API מקוד מקור
#     (helpers/SchoolUnit4.cs, assembly בשם Unit4 — צריך קוד מקור בשביל פרויקט). לכל סוג פרויקט: עם איזו ספרייה הבנייה הרגילה
#     מקמפלת (בדיוק הפניה אחת ל-Unit4*, בלי CS0433 / CS0104), מה ב-bin, עם איזו ספרייה התוכנית רצה — ועם איזו ההרצה הוויזואלית. הקובץ לא משתנה
SCH="$W/school-real"; rm -rf "$SCH" && mkdir -p "$SCH/CollectionsLib" "$SCH/ref" "$SCH/proj" "$SCH/none"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><TargetFramework>netstandard2.0</TargetFramework><AssemblyName>Unit4</AssemblyName><LangVersion>5</LangVersion></PropertyGroup></Project>\n' > "$SCH/CollectionsLib/CollectionsLib.csproj"
cp helpers/SchoolUnit4.cs "$SCH/CollectionsLib/SchoolUnit4.cs"
msb "$SCH/CollectionsLib" -c Release -o "$SCH/bin" > "$SCH/lib.out" 2>&1 && [ -f "$SCH/bin/Unit4.dll" ] || { echo "הספרייה של בית הספר (SchoolUnit4.cs) לא נבנתה"; grep error "$SCH/lib.out" | head -3; exit 1; }
rm -rf "$SCH/CollectionsLib/bin" "$SCH/CollectionsLib/obj"
cat > "$SCH/Program.cs" <<'CS'
using System;
using Unit4.CollectionsLib;

class Program
{
    static void Main(string[] args)
    {
        Stack<int> s = new Stack<int>();
        s.Push(1); s.Push(2);
        Queue<int> q = new Queue<int>();
        q.Insert(3);
        Node<int> n = new Node<int>(4, new Node<int>(5));
        BinNode<int> t = new BinNode<int>(new BinNode<int>(6), 7, null);
        int sum = s.Pop() + s.Top() + q.Head() + q.Remove() + n.GetNext().GetValue() + t.GetLeft().GetValue() + (t.HasRight() ? 100 : 0) + (s.IsEmpty() ? 100 : 0);
        Console.WriteLine(typeof(Stack<int>).Assembly.GetName().Name + " " + sum);
    }
}
CS
SUM=20
PROG_MD5=$(md5sum < "$SCH/Program.cs")
# אותו csproj כמו "Console App" חדש (ImplicitUsings פעיל — Stack / Queue גם ב-System.Collections.Generic), ועוד ההפניה לספרייה של בית הספר
CSPROJ_HEAD='<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>enable</ImplicitUsings></PropertyGroup>'
printf '%s<ItemGroup><Reference Include="Unit4"><HintPath>%s</HintPath></Reference></ItemGroup></Project>\n' "$CSPROJ_HEAD" "$SCHOOL_DLL" > "$SCH/ref/Ref.csproj"
printf '%s<ItemGroup><ProjectReference Include="../CollectionsLib/CollectionsLib.csproj" /></ItemGroup></Project>\n' "$CSPROJ_HEAD" > "$SCH/proj/Proj.csproj"
printf '%s</Project>\n' "$CSPROJ_HEAD" > "$SCH/none/None.csproj"
for k in ref:Ref:Unit4 proj:Proj:Unit4 none:None:Unit4.CollectionsLib; do
    d=${k%%:*}; rest=${k#*:}; n=${rest%%:*}; want=${rest#*:}
    cp "$SCH/Program.cs" "$SCH/$d/Program.cs"
    V=normal msb "$SCH/$d" > "$SCH/$d.out" 2>&1
    refs=$(grep -o '/reference:[^ ]*[\\/]Unit4[^ \\/]*\.dll' "$SCH/$d.out" | sed 's|.*[\\/]||' | sort -u | tr '\n' ' ')
    bin=$(ls "$SCH/$d/bin/Debug/net8.0" | grep '^Unit4.*\.dll$' | tr '\n' ' ')
    run=$(dotnet "$SCH/$d/bin/Debug/net8.0/$n.dll")
    grep -q 'Build succeeded' "$SCH/$d.out" && ! grep -qE 'CS0433|CS0104' "$SCH/$d.out" && [ "$refs" = "$want.dll " ] && [ "$bin" = "$want.dll " ] && [ "$run" = "$want $SUM" ] \
        || { echo "הבנייה הרגילה ($d): הפניות '$refs', ב-bin '$bin', הרצה '$run' (צפוי: $want)"; grep -E 'error [A-Z]+[0-9]+' "$SCH/$d.out" | head -5; exit 1; }
    viz "$SCH/$d" --trace "$SCH/$d.trace" > "$SCH/$d.viz" 2>&1 && grep -qx "Unit4.CollectionsLib $SUM" "$SCH/$d.viz" && [ -s "$SCH/$d.trace" ] \
        || { echo "ההרצה הוויזואלית ($d) לא רצה עם הספרייה של DSViz"; cat "$SCH/$d.viz"; exit 1; }
    [ "$(md5sum < "$SCH/$d/Program.cs")" = "$PROG_MD5" ] || { echo "הקובץ ($d) השתנה"; exit 1; }
    echo "  $d: הבנייה הרגילה — הפניה אחת: $refs· ב-bin: $bin· רצה עם $want · ההרצה הוויזואלית — Unit4.CollectionsLib · אותו קובץ, בלי שינוי"
done
echo "אותו קוד, בלי שינוי: הבנייה הרגילה עם הספרייה שהפרויקט מפנה אליה (Reference / ProjectReference בשם אחר) או עם של DSViz כשאין, ההרצה הוויזואלית תמיד עם של DSViz"

# כד. ה-API: כל חבר ציבורי של Unit4.dll האמיתי (Unit4.CollectionsLib ו-Unit4.BinTreeUtilsLib — גם StackInt ו-BinTreeUtils) קיים גם בספרייה של
#     DSViz — אותו שם, אותם פרמטרים, אותו סוג מוחזר. וגם הספרייה המדומה (SchoolUnit4.cs) — שה-ProjectReference בבדיקות האלה בונה ממנה
mkdir -p "$W/apicmp"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><EnableDefaultCompileItems>false</EnableDefaultCompileItems></PropertyGroup><ItemGroup><Compile Include="%s" /></ItemGroup></Project>\n' "$PWD/helpers/ApiCompare.cs" > "$W/apicmp/ApiCompare.csproj"
msb "$W/apicmp" -p:DSVizDisable=true -o "$W/apicmp/out" > "$W/apicmp.build" 2>&1 || { echo "ApiCompare לא נבנה"; grep error "$W/apicmp.build" | head -3; exit 1; }
dotnet "$W/apicmp/out/ApiCompare.dll" "$SCHOOL_DLL" "$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/Unit4.CollectionsLib.dll" > "$W/apicmp.txt" \
    && grep -q '^both  *type Unit4.CollectionsLib.StackInt$' "$W/apicmp.txt" && grep -q '^both  *type Unit4.BinTreeUtilsLib.BinTreeUtils$' "$W/apicmp.txt" \
    && grep -q '^both  *type Unit4.BinTreeCanvasLib.TreeCanvas$' "$W/apicmp.txt" && grep -q '^both  *static Void TreeCanvas.AddTree<T>(BinNode<T>)$' "$W/apicmp.txt" \
    && echo "ה-API מול Unit4.dll האמיתי: $(grep -c '^both' "$W/apicmp.txt") חברים משותפים (גם StackInt, BinTreeUtils ו-TreeCanvas), 0 שחסרים בספרייה של DSViz (ורק אצלנו: $(grep -c '^only-dsviz' "$W/apicmp.txt") — Helpers, Student, Car, GetInfo / SetInfo...)" \
    || { echo "חסר בספרייה של DSViz חבר שיש ב-Unit4.dll האמיתי:"; grep '^only-school' "$W/apicmp.txt"; exit 1; }
dotnet "$W/apicmp/out/ApiCompare.dll" "$SCH/bin/Unit4.dll" "$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/Unit4.CollectionsLib.dll" > "$W/apicmp-sim.txt" \
    || { echo "חסר בספרייה של DSViz חבר שיש בספרייה המדומה (SchoolUnit4.cs):"; grep '^only-school' "$W/apicmp-sim.txt"; exit 1; }

# כה. קוד שמשתמש בחבר / סוג שיש רק בספרייה של בית הספר (כאן: ספרייה שהוסיפה Stack<T>.Count() והסוג BinTreeNode<T> של גרסה ישנה): ההרצה
#     הרגילה עובדת; ההרצה הוויזואלית לא מתחילה, ומסבירה בעברית מה חסר ושההרצה הרגילה לא מושפעת (בלי חריגה, בלי שקט)
mkdir -p "$SCH/ext/lib" "$SCH/extapp"
sed 's|public bool IsEmpty() { return head == null; }|public bool IsEmpty() { return head == null; }\n        public int Count() { int c = 0; for (Node<T> p = head; p != null; p = p.GetNext()) c++; return c; }|' helpers/SchoolUnit4.cs > "$SCH/ext/lib/SchoolUnit4.cs"
printf 'namespace Unit4.CollectionsLib { public class BinTreeNode<T> : BinNode<T> { public BinTreeNode(T v) : base(v) { } } }\n' >> "$SCH/ext/lib/SchoolUnit4.cs"
grep -q 'public int Count()' "$SCH/ext/lib/SchoolUnit4.cs" || { echo "הכנת הספרייה המורחבת נכשלה"; exit 1; }
cp "$SCH/CollectionsLib/CollectionsLib.csproj" "$SCH/ext/lib/CollectionsLib.csproj"
printf 'using Unit4.CollectionsLib;\n\nStack<int> s = new Stack<int>();\ns.Push(4); s.Push(9);\nConsole.WriteLine("count " + s.Count());\nBinTreeNode<int> b = new BinTreeNode<int>(3);\nConsole.WriteLine("tree " + b.GetValue());\n' > "$SCH/extapp/Program.cs"
printf '%s<ItemGroup><ProjectReference Include="../ext/lib/CollectionsLib.csproj" /></ItemGroup></Project>\n' "$CSPROJ_HEAD" > "$SCH/extapp/ExtApp.csproj"
msb "$SCH/extapp" > "$SCH/extapp.out" 2>&1 && [ "$(dotnet "$SCH/extapp/bin/Debug/net8.0/ExtApp.dll" | tr '\n' ' ')" = 'count 2 tree 3 ' ] \
    || { echo "חבר שיש רק בספרייה של בית הספר: ההרצה הרגילה נכשלה"; grep error "$SCH/extapp.out" | head -3; exit 1; }
rc=0; viz "$SCH/extapp" --trace "$SCH/extapp.trace" > "$SCH/extapp.viz" 2>&1 || rc=$?
[ $rc -eq 1 ] && grep -q "אין 'Count'" "$SCH/extapp.viz" && grep -q "הסוג 'BinTreeNode' לא קיים בספרייה של DSViz" "$SCH/extapp.viz" \
    && grep -q 'ספרייה שהפרויקט מפנה אליה (Unit4)' "$SCH/extapp.viz" && grep -q 'ההרצה הרגילה (הכפתור הירוק) לא מושפעת' "$SCH/extapp.viz" \
    && grep -q 'Push, Pop, Top, IsEmpty' "$SCH/extapp.viz" && ! grep -qE 'Unhandled|Exception' "$SCH/extapp.viz" \
    && echo "חבר / סוג שיש רק בספרייה של בית הספר (Stack.Count(), BinTreeNode): ההרצה הרגילה עובדת, וההרצה הוויזואלית מסבירה בעברית מה חסר ושההרצה הרגילה לא מושפעת" \
    || { echo "חבר חסר בספרייה של DSViz: ההרצה הוויזואלית צריכה הסבר בעברית (קוד $rc)"; cat "$SCH/extapp.viz"; exit 1; }

# כו. שתי ההרצות על אותו פרויקט באותו זמן: התוכנית של ההרצה הרגילה רצה ומחכה לקלט, ובזמן הזה ההרצה הוויזואלית של אותו קובץ — שתיהן
#     מצליחות, וההרצה הוויזואלית לא כותבת ולא נוגעת בשום קובץ של הפרויקט (bin / obj / קוד): היא מקמפלת בזיכרון, בתהליך נפרד
CC="$W/concurrent"; rm -rf "$CC" && mkdir -p "$CC"
printf 'using System;\nusing Unit4.CollectionsLib;\n\nclass Program\n{\n    static void Main(string[] args)\n    {\n        Stack<int> s = new Stack<int>();\n        s.Push(5);\n        Console.WriteLine("started " + typeof(Stack<int>).Assembly.GetName().Name);\n        string line = Console.ReadLine();\n        Console.WriteLine("got " + line + " " + s.Top());\n    }\n}\n' > "$CC/Program.cs"
printf '%s<ItemGroup><Reference Include="Unit4"><HintPath>%s</HintPath></Reference></ItemGroup></Project>\n' "$CSPROJ_HEAD" "$SCHOOL_DLL" > "$CC/Concurrent.csproj"
msb "$CC" | grep -q 'Build succeeded' || { echo "הפרויקט של ההרצה המקבילה לא נבנה"; exit 1; }
mkfifo "$CC/stdin.fifo"
dotnet "$CC/bin/Debug/net8.0/Concurrent.dll" < "$CC/stdin.fifo" > "$CC/normal.out" 2>&1 &
NPID=$!
exec 3> "$CC/stdin.fifo"
for i in 1 2 3 4 5 6 7 8 9 10; do grep -q started "$CC/normal.out" 2>/dev/null && break; sleep 1; done
snap() { (cd "$CC" && find . -type f ! -name '*.out' ! -name '*.fifo' ! -name 'visual.*' -exec stat -c '%n %s %Y' {} + | sort); }
snap > "$W/concurrent.before"
printf 'visual\n' > "$W/concurrent.in"
vrc=0; viz "$CC/Program.cs" --input "$W/concurrent.in" --trace "$CC/visual.trace" > "$CC/visual.out" 2>&1 || vrc=$?
snap > "$W/concurrent.after"
running=no; kill -0 $NPID 2>/dev/null && running=yes
printf 'normal\n' >&3; exec 3>&-
nrc=0; wait $NPID || nrc=$?
[ $vrc -eq 0 ] && grep -q 'got visual 5' "$CC/visual.out" && grep -q 'started Unit4.CollectionsLib' "$CC/visual.out" && [ -s "$CC/visual.trace" ] \
    && [ "$running" = yes ] && [ $nrc -eq 0 ] && grep -q 'started Unit4$' "$CC/normal.out" && grep -q 'got normal 5' "$CC/normal.out" \
    && cmp -s "$W/concurrent.before" "$W/concurrent.after" \
    && echo "שתי ההרצות באותו זמן על אותו קובץ: הרגילה (Unit4 של בית הספר) חיכתה לקלט והמשיכה, הוויזואלית (Unit4.CollectionsLib) רצה עד הסוף — ושום קובץ בפרויקט לא השתנה" \
    || { echo "הרצה מקבילה: ויזואלית $vrc, רגילה $nrc (רצה בזמן הוויזואלית: $running)"; cat "$CC/visual.out" "$CC/normal.out"; diff "$W/concurrent.before" "$W/concurrent.after" | head; exit 1; }

# כז. אותו פלט בשתי ההרצות, מול Unit4.dll האמיתי: תוכנית שמשתמשת בכל ה-API שלו (Stack / Queue / Node / BinNode, StackInt, BinTreeUtils,
#     והדפסה ישירה של חוליה — שם ToString מחזיר רק את הערך) — ההרצה הרגילה (Unit4.dll) וההרצה הוויזואלית (הספרייה של DSViz) מדפיסות אותו דבר.
#     ובפרויקט שלא מפנה לספרייה של בית הספר, חוליה מודפסת כמו תמיד ב-DSViz (כל השרשרת)
SP="$W/school-print"; rm -rf "$SP" && mkdir -p "$SP/app" "$SP/own"
cp helpers/SchoolProgram.cs "$SP/app/Program.cs"
printf '%s<ItemGroup><Reference Include="Unit4"><HintPath>%s</HintPath></Reference></ItemGroup></Project>\n' "$CSPROJ_HEAD" "$SCHOOL_DLL" > "$SP/app/SchoolApp.csproj"
V=normal msb "$SP/app" > "$SP/build.out" 2>&1 && grep -q 'Build succeeded' "$SP/build.out" && ! grep -qE 'CS0433|CS0104' "$SP/build.out" \
    || { echo "התוכנית מול Unit4.dll האמיתי לא נבנתה"; grep -E 'error [A-Z]+[0-9]+' "$SP/build.out" | head -5; exit 1; }
ls "$SP/app/bin/Debug/net8.0" | grep -qx 'Unit4.dll' && ! ls "$SP/app/bin/Debug/net8.0" | grep -qx 'Unit4.CollectionsLib.dll' || { echo "ב-bin לא רק Unit4.dll"; exit 1; }
dotnet "$SP/app/bin/Debug/net8.0/SchoolApp.dll" > "$SP/normal.txt" 2>&1 || { echo "ההרצה הרגילה מול Unit4.dll נכשלה"; cat "$SP/normal.txt"; exit 1; }
viz "$SP/app" --trace "$SP/visual.trace" > "$SP/visual.out" 2>&1 || { echo "ההרצה הוויזואלית של התוכנית נכשלה"; cat "$SP/visual.out"; exit 1; }
tail -n +2 "$SP/visual.out" > "$SP/visual.txt"
[ "$(wc -l < "$SP/normal.txt")" -ge 12 ] && grep -qx 'node: 4 | next: 8 | has: True' "$SP/normal.txt" && grep -q '^stackint: \[3,5,1\] top=3' "$SP/normal.txt" \
    && grep -q '^pre=2,1,0,3,4 in=0,1,2,3,4 post=0,1,4,3,2 level=2,1,3,0,4$' "$SP/normal.txt" && cmp -s "$SP/normal.txt" "$SP/visual.txt" \
    && grep -qP '^STEP\tREAD\t.*BinTreeUtils\.Height\(\[2\]\)  →  2$' "$SP/visual.trace" && grep -qP '^STEP\tCREATE\t.*BinTreeUtils\.BuildRandomTree\(7, 3, 5\)' "$SP/visual.trace" \
    || { echo "הפלט שונה בין ההרצה הרגילה (Unit4.dll) להרצה הוויזואלית:"; diff "$SP/normal.txt" "$SP/visual.txt" | head -20; exit 1; }
printf 'using System;\nusing Unit4.CollectionsLib;\n\nclass Program\n{\n    static void Main(string[] args)\n    {\n        Node<int> lst = new Node<int>(4, new Node<int>(8));\n        Console.WriteLine(lst);\n    }\n}\n' > "$SP/own/Program.cs"
printf '%s</Project>\n' "$CSPROJ_HEAD" > "$SP/own/Own.csproj"
viz "$SP/own" --trace "$SP/own.trace" > "$SP/own.out" 2>&1 && grep -qx '4 -> 8 -> null' "$SP/own.out" \
    || { echo "בפרויקט בלי הספרייה של בית הספר חוליה צריכה להיות מודפסת כשרשרת"; cat "$SP/own.out"; exit 1; }
echo "אותו פלט בהרצה הרגילה (Unit4.dll האמיתי) ובהרצה הוויזואלית: $(wc -l < "$SP/normal.txt") שורות — Stack / Queue / Node / BinNode, StackInt, BinTreeUtils (כל 13 הפעולות), חוליה מודפסת כערך; ובפרויקט רגיל של DSViz — שרשרת"

# כח. התבנית של בתי הספר: בראש הקובץ using Unit4.BinTreeCanvasLib; using Unit4.BinTreeUtilsLib; using Unit4.GridLib; (גם כשלא משתמשים בהן).
#     ההרצה הרגילה בונה מול Unit4.dll; ההרצה הוויזואלית צריכה להתחיל — לא CS0234 על GridLib (ספרייה גרפית שאין אצלנו: ה-using יורד,
#     בלי להזיז שורות). TreeCanvas (Unit4.BinTreeCanvasLib) קיים גם אצלנו, עם אותו פלט לקונסול כמו שם (PrintOutAllElements, סדרי המעבר).
#     וקוד שבאמת משתמש ב-Grid — הסבר בעברית שזו ספרייה גרפית של בית הספר, וההרצה הרגילה לא מושפעת
ST="$W/school-template"; rm -rf "$ST" && mkdir -p "$ST/app" "$ST/canvas" "$ST/grid"
SCHOOL_REF="<ItemGroup><Reference Include=\"Unit4\"><HintPath>$SCHOOL_DLL</HintPath></Reference></ItemGroup>"
printf 'using System;\nusing Unit4.CollectionsLib;\nusing Unit4.BinTreeCanvasLib;\nusing Unit4.BinTreeUtilsLib;\nusing Unit4.GridLib;\nusing Unit4.TurtleLib;\n//using System.Collections.Generic;\n\nnamespace List\n{\n    public class Program\n    {\n        public static void Main(string[] args)\n        {\n            Queue<int> q = new Queue<int>();\n            q.Insert(5);\n            q.Insert(11);\n            Console.WriteLine(q);\n        }\n    }\n}\n' > "$ST/app/Program.cs"
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>disable</ImplicitUsings></PropertyGroup>%s</Project>\n' "$SCHOOL_REF" > "$ST/app/List.csproj"
V=normal msb "$ST/app" > "$ST/build.out" 2>&1 && grep -q 'Build succeeded' "$ST/build.out" || { echo "התבנית של בית הספר לא נבנתה מול Unit4.dll"; grep -E 'error' "$ST/build.out" | head -3; exit 1; }
[ "$(dotnet "$ST/app/bin/Debug/net8.0/List.dll")" = '[5,11]' ] || { echo "ההרצה הרגילה של התבנית נכשלה"; exit 1; }
viz "$ST/app" --trace "$ST/app.trace" > "$ST/app.out" 2>&1 && grep -qx '\[5,11\]' "$ST/app.out" && ! grep -q CS0234 "$ST/app.out" \
    && grep -qP '^STEP\tLINE\t.*שורה 16:' "$ST/app.trace" \
    || { echo "התבנית של בית הספר (using GridLib / TurtleLib): ההרצה הוויזואלית לא התחילה"; cat "$ST/app.out"; exit 1; }
cp "$ST/app/List.csproj" "$ST/canvas/"
printf 'using System;\nusing Unit4.CollectionsLib;\nusing Unit4.BinTreeCanvasLib;\nusing Unit4.BinTreeUtilsLib;\nusing Unit4.GridLib;\n\nclass Program\n{\n    static void Main()\n    {\n        BinNode<int> t = new BinNode<int>(new BinNode<int>(new BinNode<int>(1), 2, null), 4, new BinNode<int>(null, 7, new BinNode<int>(9)));\n        TreeCanvas.SetAnimastion(AnimationSpeed.StandardSpeedPlus3);\n        TreeCanvas.PrintOutAllElements(true);\n        TreeCanvas.AddTree(t);\n        TreeCanvas.TreeDrawInOrder(); Console.WriteLine();\n        TreeCanvas.TreeDrawPreOrder(); Console.WriteLine();\n        TreeCanvas.TreeDrawPostOrder(); Console.WriteLine();\n        TreeCanvas.TreeDrawLevelOrder(); Console.WriteLine();\n        TreeCanvas.PrintOutAllElements(false);\n        TreeCanvas.TreeDrawInOrder();\n        Console.WriteLine(BinTreeUtils.Height(t));\n    }\n}\n' > "$ST/canvas/Program.cs"
viz "$ST/canvas" --trace "$ST/canvas.trace" > "$ST/canvas.out" 2>&1 && tail -n +2 "$ST/canvas.out" > "$ST/canvas.txt" \
    && [ "$(cat "$ST/canvas.txt")" = "$(printf '1, 2, 4, 7, 9\n4, 2, 1, 7, 9\n1, 2, 9, 7, 4\n4, 2, 7, 1, 9\n2')" ] && grep -q 'TreeCanvas: מעבר תוכי' "$ST/canvas.trace" \
    || { echo "TreeCanvas בהרצה הוויזואלית: פלט שונה מזה של הספרייה של בית הספר"; cat "$ST/canvas.out"; exit 1; }
cp "$ST/app/List.csproj" "$ST/grid/"
sed 's/Console.WriteLine(q);/Grid g = new Grid(3, 3, 40, 40); g.Show(); Console.WriteLine(q);/' "$ST/app/Program.cs" > "$ST/grid/Program.cs"
! viz "$ST/grid" --trace "$ST/grid.trace" > "$ST/grid.out" 2>&1 && grep -q "'Grid' היא מהספרייה של בית הספר (Unit4.GridLib)" "$ST/grid.out" && grep -q 'ההרצה הרגילה (הכפתור הירוק) לא מושפעת' "$ST/grid.out" \
    && ! grep -q "Unhandled" "$ST/grid.out" \
    || { echo "קוד שמשתמש ב-Grid: חסר ההסבר בעברית"; cat "$ST/grid.out"; exit 1; }
echo "התבנית של בתי הספר (using BinTreeCanvasLib / BinTreeUtilsLib / GridLib / TurtleLib): ההרצה הוויזואלית מתחילה, TreeCanvas עם אותו פלט (4 סדרי מעבר), ושימוש ב-Grid — הסבר בעברית"

# כט. אין פער שלא ידוע לנו מול Unit4.dll האמיתי: כל סוג ציבורי שם קיים בספרייה של DSViz (באותו API — סעיף כד), או רשום כגרפי בהרצה
#     הוויזואלית (SchoolUsings.GraphicTypes, ‏dsviz-cs --school-graphics) — שם יש הסבר בעברית. סוג חדש בגרסה חדשה של הקובץ — הבדיקה נכשלת
#     ואומרת מה להוסיף. ותוכנית עם using לכל namespace שיש בקובץ רצה בהרצה הוויזואלית; using עם שגיאת הקלדה נשאר שגיאה (כמו בהרצה הרגילה)
dotnet "$W/apicmp/out/ApiCompare.dll" --types "$SCHOOL_DLL" "$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/Unit4.CollectionsLib.dll" > "$ST/school-only.txt" \
    && viz --school-graphics > "$ST/graphics.txt" || { echo "רשימת הסוגים של Unit4.dll לא נקראה"; exit 1; }
if ! cmp -s "$ST/school-only.txt" "$ST/graphics.txt"; then
    echo "יש ב-Unit4.dll האמיתי סוג שאין בספרייה של DSViz ושלא רשום כגרפי (או רשום סוג שכבר לא קיים שם):"
    diff "$ST/graphics.txt" "$ST/school-only.txt" | sed -n 's/^> /  חדש בקובץ של בית הספר (לממש בספרייה, או להוסיף ל-GraphicTypes ב-SchoolUsings.cs): /p; s/^< /  רשום ב-GraphicTypes ואין בקובץ: /p'
    exit 1
fi
mkdir -p "$ST/all" "$ST/typo"
{ echo 'using System;'; dotnet "$W/apicmp/out/ApiCompare.dll" --namespaces "$SCHOOL_DLL" | sed 's/^/using /; s/$/;/'
  printf 'class Program\n{\n    static void Main()\n    {\n        Stack<int> s = new Stack<int>();\n        s.Push(7);\n        Console.WriteLine(s);\n    }\n}\n'; } > "$ST/all/Program.cs"
NS_COUNT=$(grep -c '^using Unit4\.' "$ST/all/Program.cs")
cp "$ST/app/List.csproj" "$ST/all/"
V=normal msb "$ST/all" > "$ST/all.build" 2>&1 && grep -q 'Build succeeded' "$ST/all.build" || { echo "תוכנית עם using לכל ה-namespaces לא נבנתה מול Unit4.dll"; grep error "$ST/all.build" | head -3; exit 1; }
viz "$ST/all" --trace "$ST/all.trace" > "$ST/all.out" 2>&1 && grep -qx '\[7\]' "$ST/all.out" \
    || { echo "using לכל $NS_COUNT ה-namespaces של Unit4.dll: ההרצה הוויזואלית לא התחילה"; cat "$ST/all.out"; exit 1; }
cp "$ST/app/List.csproj" "$ST/typo/"
sed 's/using Unit4.GridLib;/using Unit4.Colections;/' "$ST/app/Program.cs" > "$ST/typo/Program.cs"
! viz "$ST/typo" --trace "$ST/typo.trace" > "$ST/typo.out" 2>&1 && grep -q "CS0234.*'Colections'" "$ST/typo.out" \
    || { echo "using עם שגיאת הקלדה (Unit4.Colections) צריך להישאר שגיאה, כמו בהרצה הרגילה"; cat "$ST/typo.out"; exit 1; }
echo "מול Unit4.dll האמיתי: כל $(grep -c . "$ST/graphics.txt") הסוגים שאין אצלנו רשומים כגרפיים (עם הסבר), using לכל $NS_COUNT ה-namespaces — ההרצה הוויזואלית מתחילה, ושגיאת הקלדה ב-using נשארת שגיאה"

unset XDG_DATA_HOME
echo "DSViz.targets: 31 תרחישים נבדקו (כל סוג ב-Unit4.dll האמיתי מסווג, using לכל ה-namespaces שלו, התבנית של בתי הספר עם using לספריות הגרפיות ו-TreeCanvas, אותו קוד בהרצה רגילה ובהרצה ויזואלית מול ספריית בית הספר, אותו פלט מול Unit4.dll האמיתי, השוואת ה-API, חבר שחסר אצלנו, שתי ההרצות באותו זמן, פרויקט רגיל לא משתנה, ספריית בית הספר ב-Reference / ProjectReference, פרויקט מסומן, קוד מעורב, סדר הפרופילים והעברת 'הרצה רגילה' לראש, ‎.NET Framework בלי הפניה, LangVersion ישן בהרצה הוויזואלית, ImplicitUsings + Stack, נתיב בעברית, פרויקט חדש, בנייה שנייה מהירה, launchSettings קיים, DSVizDisable, ספרייה, הפניה קיימת, DLL חסר, שם מלא/ProjectReference, אידמפוטנטי/קובץ פגום, פרופיל ישן בפרויקט קיים, ארגומנטים כמו מ-Windows, כמה מסגרות)"
