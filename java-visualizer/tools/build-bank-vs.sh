#!/bin/sh
# "מאגר תרגילים" ל-Visual Studio (C#) — גרסת בדיקה (לא חלק מההתקנה לתלמידים):
#   build/lab-vs/dsviz-bank-vs.jar   — החלונות (Swing) + המאגר (גרסת C# של כל שאלה) + הסמלים; רץ ליד dsviz.jar של ההתקנה
#   build/lab-vs/install.cmd         — מעתיק ל-%LOCALAPPDATA%\DSViz\lab ויוצר קיצורים בשולחן העבודה (uninstall.cmd — מסיר)
# לפני הבנייה כל שאלה נבדקת (tools/test/bank-check.sh — גם גרסת C#, אם יש dotnet).
set -e
cd "$(dirname "$0")/.."
[ -f release/dsviz.jar ] || sh tools/build-lib.sh
[ "$SKIP_BANK_CHECK" = 1 ] || sh tools/test/bank-check.sh
OUT=build/bank-vs
rm -rf "$OUT" && mkdir -p "$OUT/bank" "$OUT/icons/topics"
# נגד dsviz.jar של ההתקנה (VsSubmit, ExercisePipeline, RtlDialog, NestedDiagram...); BankStore ו-BankProgress — מ-eclipse-bank (בלי Eclipse)
javac ${JAVAC_FLAGS:---release 8} -encoding UTF-8 -nowarn -cp release/dsviz.jar -d "$OUT" \
    $(find vs-bank/src -name '*.java') eclipse-bank/src/il/lamerhav/dsviz/BankStore.java eclipse-bank/src/il/lamerhav/dsviz/BankProgress.java \
    eclipse-plugin/src/il/lamerhav/dsviz/CsSolution.java eclipse-plugin/src/il/lamerhav/dsviz/Account.java eclipse-plugin/src/il/lamerhav/dsviz/AccessGate.java
# CsSolution בתוך התוספת (והיא קודם ב-classpath): Visual Studio פתוח מוסיף / מוציא את הפרויקט בעצמו — גם כשה-dsviz.jar של ההתקנה ישן
cp eclipse-bank/icons/bank.png eclipse-bank/icons/question.png "$OUT/icons/"
cp eclipse-bank/icons/topics/*.png "$OUT/icons/topics/"
cp bank/topics.json "$OUT/bank/"
LIST=""
for f in $(cd bank && find . -mindepth 2 -name '*.json' | sed 's|^\./||' | sort); do
    mkdir -p "$OUT/bank/$(dirname "$f")" && cp "bank/$f" "$OUT/bank/$f"
    LIST="$LIST${LIST:+, }\"$f\""
done
printf '{ "questions": [%s] }\n' "$LIST" > "$OUT/bank/index.json"
mkdir -p build/lab-vs
rm -f build/lab-vs/*
jar --create --file build/lab-vs/dsviz-bank-vs.jar -C "$OUT" .
cp vs-bank/install/install-bank-vs.ps1 vs-bank/install/install.cmd vs-bank/install/uninstall.cmd build/lab-vs/
cat > build/lab-vs/התקנה.txt <<'TXT'
מאגר תרגילים — גרסת בדיקה ל-Visual Studio / C# (רק לך, לא לתלמידים)

צריך: DSViz מותקן עם חבילת C# (DSViz-Setup.exe), ותיקיית התרגילים מסמכים\DataStructures-CS.

התקנה: לחיצה כפולה על install.cmd.
  בשולחן העבודה מופיעים שני קיצורים:
    "מאגר תרגילים C#"            — בוחרים נושא ושאלה (קל / בינוני / קשה) ולוחצים "צור תרגיל":
                                    נוצר פרויקט בפתרון DataStructures-CS, עם פעולה לכל סעיף ו-Main שמריץ ומדפיס.
    "השאלה — מאגר תרגילים C#"    — החלון הצף של השאלה האחרונה (נוסח, ציורים, "לפעולה של סעיף א", הצעה לפתרון, הפלט הרצוי).
  ב-Visual Studio: מאשרים Reload, קליק ימני על הפרויקט החדש ← Set as Startup Project, ומריצים ב"הרצה ויזואלית".
  "סיימתי את התרגיל" (בחלון הצף): סימון ירוק, והפרויקט נמחק מהפתרון עם כל הקבצים שלו.

הסרה: לחיצה כפולה על uninstall.cmd.
ההתקדמות נשמרת בקובץ bank-progress-cs.json בתיקייה .dsviz שבתיקיית המשתמש.
TXT
echo "build/lab-vs/dsviz-bank-vs.jar ($(echo "$LIST" | tr ',' '\n' | grep -c json) שאלות)"
