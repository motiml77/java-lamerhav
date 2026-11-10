#!/bin/sh
# בונה את release/dsviz.jar — הספרייה (מחלקות + קוד מקור).
# הכול תואם Java 8, חוץ מ-JdiTracer (הרצה שורה-שורה) שדורש Java 11 ונטען רק כשמבקשים אותו.
set -e
cd "$(dirname "$0")/.."
# ה-Java שבהתקנה (tools/build-jre.sh — jlink) בלי jdk.httpserver, והכלים של Visual Studio / Rider רצים בו:
# קוד שמשתמש ב-com.sun.net.httpserver נכשל שם (כך נכשלה הכניסה עם Google ב-Visual Studio) — עוצרים כבר בבנייה
if grep -l 'com\.sun\.net\.httpserver' $(find src/unit4 csharp/new-exercise/src vs-bank/src eclipse-plugin/src -name '*.java'); then
    echo "שגיאה: הקבצים למעלה משתמשים ב-com.sun.net.httpserver — הוא לא קיים ב-Java של ההתקנה (משתמשים ב-ServerSocket)" >&2
    exit 1
fi
OUT=build/lib
rm -rf "$OUT" && mkdir -p "$OUT" release
javac --release 8 -Xlint:-options -encoding UTF-8 -d "$OUT" $(find src/unit4 -name '*.java' ! -name JdiTracer.java)
javac --release 11 -encoding UTF-8 -cp "$OUT" -d "$OUT" src/unit4/collectionsLib/JdiTracer.java
# החלונות של C# ל-Visual Studio / Rider ("תרגיל חדש", "הוסף מבנה נתונים") — אותו מחולל כמו ב-Eclipse (Scaffold) + המרה ל-C#
# וגם "שליחת תרגיל" + "הצעה לפתרון" — אותו מנוע כמו ב-Eclipse (ExercisePipeline): לכלי של Visual Studio ול-VS Code (SubmitCli)
E=eclipse-plugin/src/il/lamerhav/dsviz
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "$OUT" -d "$OUT" $E/Scaffold.java $E/NameCheck.java $E/CsSolution.java \
    $E/Json.java $E/OsTrust.java $E/Account.java $E/AccessGate.java $E/WorkerClient.java $E/ExerciseFiles.java $E/AiScaffold.java $E/SolutionCheck.java $E/SolutionStore.java $E/ExercisePipeline.java $E/SubmitCli.java \
    $(find csharp/new-exercise/src -name '*.java')
mkdir -p "$OUT/resources" && cp eclipse-plugin/ai.properties "$OUT/resources/"   # כתובת השרת של "שליחת תרגיל" (worker/README.md)
# קוד המקור בתוך ה-jar תחת src/ (ולא ליד המחלקות) — כדי ש-javac לא יקמפל אותו מחדש בטעות.
# התוסף של Eclipse מגדיר את src כשורש קוד המקור (Ctrl+לחיצה על Stack פותח את הקוד)
mkdir -p "$OUT/src/unit4/collectionsLib"
cp src/unit4/collectionsLib/*.java "$OUT/src/unit4/collectionsLib/"
# המראה של החלון: FlatLaf (Apache 2.0) והגופן Alef (OFL) — בתוך ה-jar, כדי שהספרייה תישאר קובץ אחד
mkdir -p "$OUT/unit4/collectionsLib/fonts" "$OUT/META-INF"
cp third-party/fonts/*.ttf "$OUT/unit4/collectionsLib/fonts/"
cp third-party/fonts/OFL-Alef.txt "$OUT/META-INF/LICENSE-Alef-OFL.txt"
# Rubik (OFL) — לחלונות של Visual Studio (Theme.useRounded). מוקטן לעברית, לטינית וסימני פיסוק (fontTools subset), ‎~64KB‎ לכל משקל
cp third-party/fonts/OFL-Rubik.txt "$OUT/META-INF/LICENSE-Rubik-OFL.txt"
# התגים "תמכו בפרויקט" (Java / C#) בסרגל של חלון ההרצה — Donate.java
mkdir -p "$OUT/unit4/collectionsLib/images" && cp third-party/images/donate-*.png "$OUT/unit4/collectionsLib/images/"
cp third-party/LICENSE-flatlaf.txt "$OUT/META-INF/LICENSE-flatlaf.txt"
(cd "$OUT" && unzip -qo ../../third-party/flatlaf-3.5.4.jar -x 'META-INF/MANIFEST.MF' 'META-INF/LICENSE' 'module-info.class')
# כל מה שבספרייה רץ גם ב-Java של ההתקנה (jlink) — אף מחלקה לא צריכה מודול שלא נכנס אליה (jdeps, לא רק grep)
sh tools/check-jre-modules.sh "$OUT"
printf 'Premain-Class: unit4.collectionsLib.LineAgent\nMulti-Release: true\nImplementation-Title: DSViz - Data Structures Visualizer\nImplementation-Vendor: Moti Levi (motiml77@gmail.com)\nCopyright: (c) 2026 Moti Levi, motiml77@gmail.com\n' > build/lib-manifest.txt
jar --create --file release/dsviz.jar --manifest build/lib-manifest.txt -C "$OUT" .
echo "release/dsviz.jar"
