#!/bin/sh
# בונה את כל קובצי ההתקנה לתיקייה release/:
#   dsviz.jar                    — הספרייה (גם לשימוש ידני בכל פרויקט)
#   il.lamerhav.dsviz_1.0.1.jar  — התוסף ל-Eclipse (מעתיקים לתיקיית dropins)
#   dsviz-1.0.1.vsix             — התוסף ל-VS Code
#   dsviz-intellij-1.0.1.zip     — התוסף ל-IntelliJ IDEA (Settings ← Plugins ← Install Plugin from Disk; ההתקנות מתקינות לבד)
#   DSViz-Setup.exe              — תוכנית התקנה ל-Windows (מוצאת לבד את VS Code ואת Eclipse; Java 21 כלולה)
#   dsviz-install.zip            — התקנה ל-Linux, התוספים והמדריך (בלי ה-exe של Windows)
# ל-Mac: פקודה אחת בטרמינל (installer/mac/install.sh) — החבילות נבנות ב-GitHub Actions (.github/workflows/dsviz-mac.yml)
# דרישות לבנייה: JDK 11+ (ל-IntelliJ: JDK 21 + Gradle 8.5+ ואינטרנט בפעם הראשונה), Python 3, Node.js, NSIS (makensis) — רק למי שבונה
set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
VERSION=1.0.1
# מספר הבנייה (תאריך ושעה, UTC) — עולה בכל בנייה. נכנס ל-Bundle-Version של התוסף ל-Eclipse (1.0.1.v<מספר>), להתקנה
# ול-version.json: Eclipse מעדיף תמיד את הגרסה הגבוהה יותר, כך שעותק ישן של התוסף (שנשאר במקום אחר) לא יכול "לנצח"
BUILDNO=${BUILDNO:-$(date -u +%Y%m%d%H%M)}

tools/build-lib.sh

echo "== C# (Unit4.CollectionsLib.dll + dsviz-cs.exe — ההרצה הוויזואלית של C#)"
# דורש ‎.NET SDK 8 (רק למי שבונה). dsviz-cs מתפרסם self-contained ל-Windows — לתלמיד לא צריך להתקין כלום
export DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
dotnet build csharp/Unit4.CollectionsLib/Unit4.CollectionsLib.csproj -c Release -o build/cs-lib -v q --nologo
rm -rf build/dsviz-cs-win
dotnet publish csharp/DsViz.Runner/DsViz.Runner.csproj -c Release -r win-x64 --self-contained -p:PublishReadyToRun=true -p:DebugType=none \
    -o build/dsviz-cs-win -v q --nologo
# התוסף של VS Code: הספרייה ודוגמאות C# (נכנסים ל-resources/cs בשלב של VS Code)
rm -rf build/cs-resources && mkdir -p build/cs-resources/lib build/cs-resources/Examples
cp build/cs-lib/Unit4.CollectionsLib.dll build/cs-resources/lib/
for d in csharp/project/Examples/*/; do
    n=$(basename "$d"); mkdir -p "build/cs-resources/Examples/$n"; cp "$d/Program.cs" "build/cs-resources/Examples/$n/"
done
# תיקיית התרגילים של C# שההתקנה יוצרת במסמכים (DataStructures-CS) + תבנית פרויקט ל-Visual Studio
rm -rf build/project-cs && mkdir -p build/project-cs/lib
(cd csharp/project && tar c --exclude=bin --exclude=obj --exclude=lib .) | tar x -C build/project-cs
cp build/cs-lib/Unit4.CollectionsLib.dll build/project-cs/lib/
mkdir -p csharp/project/lib && cp build/cs-lib/Unit4.CollectionsLib.dll csharp/project/lib/   # לבדיקות שבונות את התיקייה עצמה
python3 tools/make-vs-template.py build/cs-lib/Unit4.CollectionsLib.dll csharp/project/Exercise build/vs-template/DSViz-Exercise.zip

echo "== סרטוני הדרכה (הכפתור ?): help/ → תוסף Eclipse, תוסף VS Code, ההתקנה"
# משכי הסרטונים נמדדים ב-ffprobe; בלי ffmpeg (למשל בבנייה של ה-Mac ב-GitHub Actions) — הדפים שכבר בתיקייה help/
if command -v ffprobe >/dev/null 2>&1; then node tools/build-help.js; else echo "  (אין ffprobe — משתמשים בדפי ההדרכה שבתיקייה help/)"; fi
rm -rf build/help && mkdir -p build/help && cp help/index.html help/player.html help/tutorials.json help/*.mp4 help/*.jpg build/help/ 2>/dev/null || true
# לכל סביבה הסרטונים שלה: help/ (Eclipse), help/vscode/, help/vs/ (Visual Studio) — בהתקנה כולם, וכל כלי פותח את התיקייה שלו
for d in vscode vs; do [ -d help/$d ] && mkdir -p build/help/$d && cp help/$d/index.html help/$d/player.html help/$d/tutorials.json help/$d/*.mp4 help/$d/*.jpg build/help/$d/ 2>/dev/null || true; done

echo "== Eclipse"
DEPS=build/eclipse-deps
[ -f "$DEPS/org.eclipse.jdt.core.jar" ] && [ -f "$DEPS/ecj.jar" ] && [ -f "$DEPS/org.eclipse.e4.ui.workbench.jar" ] && [ -f "$DEPS/org.eclipse.core.contenttype.jar" ] || python3 tools/eclipse-deps.py "$DEPS"
OUT=build/eclipse
rm -rf "$OUT" && mkdir -p "$OUT/resources/examples"
CP=$(ls $DEPS/*.jar | tr '\n' ':')
javac ${JAVAC_FLAGS:---release 8} -encoding UTF-8 -nowarn -cp "$CP" -d "$OUT" $(find eclipse-plugin/src -name '*.java')
cp eclipse-plugin/src/il/lamerhav/dsviz/problems_he.properties "$OUT/il/lamerhav/dsviz/"   # המורה בעברית: תרגום כל הודעות הקומפיילר
cp eclipse-plugin/plugin.xml "$OUT/"
# "שליחת תרגיל" מופיעה בתפריטים ובסרגל הכלים רק כשיש כתובת שרת (eclipse-plugin/ai.properties, worker/README.md)
if ! grep -qE '^url=https?://' eclipse-plugin/ai.properties; then
    python3 -c "
import re, sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
s = re.sub(r'[ \t]*<!-- submit:start -->.*?<!-- submit:end -->\n', '', s, flags=re.S)
open(p, 'w', encoding='utf-8').write(s)" "$OUT/plugin.xml"
    echo "  (שליחת תרגיל: אין כתובת שרת — הכפתור לא מוצג)"
fi
cp -r eclipse-plugin/icons "$OUT/"
cp release/dsviz.jar "$OUT/resources/"
cp eclipse-plugin/ai.properties "$OUT/resources/"   # "שליחת תרגיל": כתובת השרת (worker/README.md)
mkdir -p "$OUT/resources/help" && cp build/help/* "$OUT/resources/help/" 2>/dev/null || true
cp src/examples/*.java "$OUT/resources/examples/"
cp src/Main.java "$OUT/resources/"
# Bundle-Version עם qualifier של הבנייה (1.0.1.v202611011200). שם הקובץ נשאר il.lamerhav.dsviz_1.0.1.jar (קישורים קבועים);
# ל-Eclipse משנה רק הגרסה שבתוך MANIFEST.MF. בלי זה כל הבניות היו "1.0.1", ועותק ישן היה נטען במקום החדש
MF=build/eclipse-MANIFEST.MF
sed "s/^Bundle-Version: .*/Bundle-Version: $VERSION.v$BUILDNO/" eclipse-plugin/META-INF/MANIFEST.MF > "$MF"
grep -q "^Bundle-Version: $VERSION.v$BUILDNO" "$MF" || { echo "Bundle-Version לא עודכן"; exit 1; }
jar --create --file "release/il.lamerhav.dsviz_$VERSION.jar" --manifest "$MF" -C "$OUT" .
echo "release/il.lamerhav.dsviz_$VERSION.jar (Bundle-Version $VERSION.v$BUILDNO)"
# גרסה בלי סרטונים — זו שנכנסת להתקנה (הסרטונים שם פעם אחת בתיקייה help; התוסף מוצא אותם שם)
mkdir -p build/light && rm -rf "$OUT/resources/help"
jar --create --file "build/light/il.lamerhav.dsviz_$VERSION.jar" --manifest "$MF" -C "$OUT" .

echo "== IntelliJ IDEA"
# Gradle + IntelliJ Platform Gradle Plugin (intellij-plugin/build.gradle.kts). בפעם הראשונה מוריד את IntelliJ 2024.2 שמולו בונים (~700MB).
# IDEA_DIR=<IntelliJ מחולץ> — בונים מולו בלי להוריד. בלי Gradle — מדלגים (שאר ההתקנה נבנית; ההתקנה מדלגת על IntelliJ)
rm -rf build/intellij
if command -v gradle >/dev/null 2>&1 && (cd intellij-plugin && gradle --no-daemon -q ${IDEA_DIR:+-PideDir=$IDEA_DIR} buildPlugin); then
    cp "intellij-plugin/build/distributions/dsviz-intellij-$VERSION.zip" release/
    # להתקנות: התיקייה dsviz-intellij בלי runtime/dsviz.jar — ההתקנה מעתיקה לשם את dsviz.jar שכבר בה (לא פעמיים באותה חבילה)
    mkdir -p build/intellij && (cd build/intellij && unzip -q "$ROOT/release/dsviz-intellij-$VERSION.zip") && rm -rf build/intellij/dsviz-intellij/runtime
    echo "release/dsviz-intellij-$VERSION.zip"
else
    echo "  (אין Gradle או שהבנייה נכשלה — התוסף ל-IntelliJ לא נבנה)"
fi

echo "== VS Code"
cd vscode-extension
rm -rf resources && mkdir -p resources/examples
cp ../release/dsviz.jar resources/
cp ../src/examples/*.java resources/examples/
cp -R ../build/cs-resources resources/cs
HELPSRC=../build/help; [ -f ../build/help/vscode/index.html ] && HELPSRC=../build/help/vscode   # הסרטונים של VS Code
rm -rf resources/help && mkdir -p resources/help && cp $HELPSRC/* resources/help/ 2>/dev/null || true
# גרסה ייחודית לכל בנייה (1.0.<מספר הבנייה>; שם הקובץ נשאר dsviz-1.0.1.vsix): VS Code מתקין אותה לתיקייה חדשה
# (lamerhav.dsviz-1.0.<מספר>), מסמן את הישנה כמיושנת ומוחק אותה, ומבקש לטעון מחדש חלון פתוח — בלי לסמוך על כך
# ש-‎--force מחליף בדיוק את אותה גרסה. package.json עצמו לא משתנה (מוחזר בסוף, גם אם האריזה נכשלה)
cp package.json ../build/vscode-package.json
node -e "const f='package.json',p=require('./'+f);p.version='${VERSION%.*}.$BUILDNO';require('fs').writeFileSync(f,JSON.stringify(p,null,4)+'\n')"
vsce_ok=1
npx --yes @vscode/vsce@2.32.0 package --skip-license --allow-missing-repository --out "$ROOT/release/dsviz-$VERSION.vsix" || vsce_ok=0
rm -rf resources/help
[ $vsce_ok = 1 ] && { npx --yes @vscode/vsce@2.32.0 package --skip-license --allow-missing-repository --out "$ROOT/build/light/dsviz-$VERSION.vsix" || vsce_ok=0; }
cp ../build/vscode-package.json package.json
[ $vsce_ok = 1 ] || { echo "vsce נכשל"; exit 1; }
echo "  (VS Code: גרסה ${VERSION%.*}.$BUILDNO)"
mkdir -p resources/help && cp $HELPSRC/* resources/help/ 2>/dev/null || true   # חזרה לצורה המלאה (לבדיקות)
cd "$ROOT"

echo "== project template (תיקיית התרגילים שההתקנה יוצרת)"
P=build/project
rm -rf "$P" && mkdir -p "$P/src/examples" "$P/lib"
cp -R installer/project/. "$P/"
cp src/Main.java "$P/src/"
cp src/examples/*.java "$P/src/examples/"
cp release/dsviz.jar "$P/lib/"

echo "== Windows installer (DSViz-Setup.exe)"
if command -v makensis >/dev/null 2>&1; then
    tools/build-jre.sh   # Java 21 מקוצרת ל-Windows שנכנסת לתוך ההתקנה
    # BUILDNO (למעלה) — העדכון האוטומטי מתקין כל בנייה חדשה יותר; אותו מספר כמו ב-Bundle-Version של התוסף ל-Eclipse
    (cd installer && makensis -V2 -DVERSION=$VERSION -DBUILDNO=$BUILDNO setup.nsi)
    echo "release/DSViz-Setup.exe"
    # מה העדכון האוטומטי (installer/update.ps1) בודק: הגרסה, מספר הבנייה, והקובץ (עם SHA-256 — כדי לוודא שהורד שלם)
    printf '{\n  "version": "%s",\n  "build": %s,\n  "url": "https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/release/DSViz-Setup.exe",\n  "sha256": "%s"\n}\n' \
        "$VERSION" "$BUILDNO" "$(sha256sum release/DSViz-Setup.exe | cut -d' ' -f1)" > release/version.json
    echo "release/version.json (build $BUILDNO)"
else
    echo "makensis לא מותקן (apt install nsis) — DSViz-Setup.exe לא נבנה מחדש"
fi

echo "== installer zip"
PKG=build/dsviz-install
rm -rf "$PKG" && mkdir -p "$PKG"
cp installer/install.sh installer/README-he.html "$PKG/"
# ב-Mac לא מפיצים יותר install-mac.command: קובץ שהורד בדפדפן נחסם ב-macOS 15 ומעלה (Gatekeeper) —
# ב-Mac מתקינים בפקודה אחת בטרמינל (installer/mac/install.sh; ראו README-he.html)
# בלי DSViz-Setup.exe: הוא רק ל-Windows, והוא כבר קובץ נפרד בתיקיית release
cp -R build/project "$PKG/project"
cp "release/dsviz-$VERSION.vsix" "release/il.lamerhav.dsviz_$VERSION.jar" release/dsviz.jar "$PKG/"
[ -f "release/dsviz-intellij-$VERSION.zip" ] && cp "release/dsviz-intellij-$VERSION.zip" "$PKG/"
rm -f release/dsviz-install.zip
(cd build && python3 -c "
import zipfile, os
with zipfile.ZipFile('../release/dsviz-install.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk('dsviz-install'):
        for f in sorted(files):
            full = os.path.join(root, f)
            info = zipfile.ZipInfo(full)
            info.external_attr = (0o755 if f.endswith(('.sh', '.command')) else 0o644) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, open(full, 'rb').read())
")
ls -la release
