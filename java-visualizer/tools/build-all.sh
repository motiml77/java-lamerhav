#!/bin/sh
# בונה את כל קובצי ההתקנה לתיקייה release/:
#   dsviz.jar                    — הספרייה (גם לשימוש ידני בכל פרויקט)
#   il.lamerhav.dsviz_1.0.1.jar  — התוסף ל-Eclipse (מעתיקים לתיקיית dropins)
#   dsviz-1.0.1.vsix             — התוסף ל-VS Code
#   DSViz-Setup.exe              — תוכנית התקנה ל-Windows (מוצאת לבד את VS Code ואת Eclipse; Java 21 כלולה)
#   dsviz-install.zip            — התקנה ל-Mac/Linux, התוספים והמדריך (בלי ה-exe של Windows)
# דרישות לבנייה: JDK 11+, Python 3, Node.js, NSIS (makensis) — רק למי שבונה, לא למי שמתקין
set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
VERSION=1.0.1

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
python3 tools/make-vs-template.py build/cs-lib/Unit4.CollectionsLib.dll csharp/project/Exercise build/vs-template/DSViz-Exercise.zip

echo "== סרטוני הדרכה (הכפתור ?): help/ → תוסף Eclipse, תוסף VS Code, ההתקנה"
node tools/build-help.js
rm -rf build/help && mkdir -p build/help && cp help/index.html help/player.html help/tutorials.json help/*.mp4 help/*.jpg build/help/ 2>/dev/null || true

echo "== Eclipse"
DEPS=build/eclipse-deps
[ -f "$DEPS/org.eclipse.jdt.core.jar" ] || python3 tools/eclipse-deps.py "$DEPS"
OUT=build/eclipse
rm -rf "$OUT" && mkdir -p "$OUT/resources/examples"
CP=$(ls $DEPS/*.jar | tr '\n' ':')
javac --release 11 -encoding UTF-8 -nowarn -cp "$CP" -d "$OUT" $(find eclipse-plugin/src -name '*.java')
cp eclipse-plugin/plugin.xml "$OUT/"
cp -r eclipse-plugin/icons "$OUT/"
cp release/dsviz.jar "$OUT/resources/"
cp eclipse-plugin/ai.properties "$OUT/resources/"   # "שליחת תרגיל": כתובת השרת (worker/README.md)
mkdir -p "$OUT/resources/help" && cp build/help/* "$OUT/resources/help/" 2>/dev/null || true
cp src/examples/*.java "$OUT/resources/examples/"
cp src/Main.java "$OUT/resources/"
jar --create --file "release/il.lamerhav.dsviz_$VERSION.jar" --manifest eclipse-plugin/META-INF/MANIFEST.MF -C "$OUT" .
echo "release/il.lamerhav.dsviz_$VERSION.jar"
# גרסה בלי סרטונים — זו שנכנסת להתקנה (הסרטונים שם פעם אחת בתיקייה help; התוסף מוצא אותם שם)
mkdir -p build/light && rm -rf "$OUT/resources/help"
jar --create --file "build/light/il.lamerhav.dsviz_$VERSION.jar" --manifest eclipse-plugin/META-INF/MANIFEST.MF -C "$OUT" .

echo "== VS Code"
cd vscode-extension
rm -rf resources && mkdir -p resources/examples
cp ../release/dsviz.jar resources/
cp ../src/examples/*.java resources/examples/
cp -R ../build/cs-resources resources/cs
rm -rf resources/help && mkdir -p resources/help && cp ../build/help/* resources/help/ 2>/dev/null || true
npx --yes @vscode/vsce@2.32.0 package --skip-license --allow-missing-repository --out "$ROOT/release/dsviz-$VERSION.vsix"
rm -rf resources/help
npx --yes @vscode/vsce@2.32.0 package --skip-license --allow-missing-repository --out "$ROOT/build/light/dsviz-$VERSION.vsix"
mkdir -p resources/help && cp ../build/help/* resources/help/ 2>/dev/null || true   # חזרה לצורה המלאה (לבדיקות)
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
    (cd installer && makensis -V2 -DVERSION=$VERSION setup.nsi)
    echo "release/DSViz-Setup.exe"
else
    echo "makensis לא מותקן (apt install nsis) — DSViz-Setup.exe לא נבנה מחדש"
fi

echo "== installer zip"
PKG=build/dsviz-install
rm -rf "$PKG" && mkdir -p "$PKG"
cp installer/install.sh installer/README-he.html "$PKG/"
cp installer/install.sh "$PKG/install-mac.command"   # ב-Mac: לחיצה כפולה
# בלי DSViz-Setup.exe: הוא רק ל-Windows, והוא כבר קובץ נפרד בתיקיית release
cp -R build/project "$PKG/project"
cp "release/dsviz-$VERSION.vsix" "release/il.lamerhav.dsviz_$VERSION.jar" release/dsviz.jar "$PKG/"
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
