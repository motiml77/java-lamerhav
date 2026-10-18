#!/bin/sh
# בונה את כל קובצי ההתקנה לתיקייה release/:
#   dsviz.jar                    — הספרייה (גם לשימוש ידני בכל פרויקט)
#   il.lamerhav.dsviz_1.0.0.jar  — התוסף ל-Eclipse (מעתיקים לתיקיית dropins)
#   dsviz-1.0.0.vsix             — התוסף ל-VS Code
#   dsviz-install.zip            — כל הנ"ל + סקריפט התקנה ומדריך (את זה מחלקים למחשבים)
# דרישות לבנייה: JDK 11+, Python 3, Node.js (רק למי שבונה — לא למי שמתקין)
set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
VERSION=1.0.0

tools/build-lib.sh

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
cp src/examples/*.java "$OUT/resources/examples/"
jar --create --file "release/il.lamerhav.dsviz_$VERSION.jar" --manifest eclipse-plugin/META-INF/MANIFEST.MF -C "$OUT" .
echo "release/il.lamerhav.dsviz_$VERSION.jar"

echo "== VS Code"
cd vscode-extension
rm -rf resources && mkdir -p resources/examples
cp ../release/dsviz.jar resources/
cp ../src/examples/*.java resources/examples/
npx --yes @vscode/vsce@2.32.0 package --skip-license --allow-missing-repository --out "$ROOT/release/dsviz-$VERSION.vsix"
cd "$ROOT"

echo "== installer zip"
PKG=build/dsviz-install
rm -rf "$PKG" && mkdir -p "$PKG"
cp installer/install.bat installer/install.sh installer/README-he.html "$PKG/"
cp "release/dsviz-$VERSION.vsix" "release/il.lamerhav.dsviz_$VERSION.jar" release/dsviz.jar "$PKG/"
rm -f release/dsviz-install.zip
(cd build && python3 -c "
import zipfile, os
with zipfile.ZipFile('../release/dsviz-install.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for f in sorted(os.listdir('dsviz-install')):
        info = zipfile.ZipInfo('dsviz-install/' + f)
        info.external_attr = (0o755 if f.endswith('.sh') else 0o644) << 16
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, open('dsviz-install/' + f, 'rb').read())
")
ls -la release
