#!/bin/bash
# בונה את חבילת ה-Mac לארכיטקטורה אחת: dsviz-mac-<arm64|x64>-<בנייה>.tar.gz — מה ש-install.sh מוריד ופורס
# לתוך ~/Library/Application Support/DSViz. רץ על Mac (GitHub Actions: macos-15 ל-arm64, macos-15-intel ל-x64), כי:
#   - jlink "מקומי" מ-Temurin 21 של אותה ארכיטקטורה: הקבצים עם החתימה המקורית (Developer ID של Eclipse Foundation)
#   - dotnet publish על Mac חותם את dsviz-cs (חתימה ad-hoc — מספיקה כשהקובץ לא הורד בדפדפן); בלי חתימה Apple Silicon הורג אותו
# שימוש: build-payload.sh <arm64|x64> <מספר בנייה> <תיקיית הקבצים המשותפים> <תיקיית הפלט>
#   הקבצים המשותפים (נבנים ב-Linux ע"י tools/build-all.sh — ראו .github/workflows/dsviz-mac.yml):
#   dsviz.jar, il.lamerhav.dsviz_<v>.jar ו-dsviz-<v>.vsix (בלי הסרטונים), project/, project-cs/, help/ (בלי mp4),
#   intellij/dsviz-intellij/lib (התוסף ל-IntelliJ — runtime/dsviz.jar מועתק מכאן)
# דורש: JAVA_HOME = JDK 21 עם jmods (Temurin) לארכיטקטורה של המחשב, ‎.NET SDK 8, python3
set -euo pipefail
ARCH=$1; BUILD=$2
COMMON=$(cd "$3" && pwd); mkdir -p "$4"; OUT=$(cd "$4" && pwd)
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../.." && pwd)
VERSION=$(sed -n 's/^VERSION=//p' "$ROOT/tools/build-all.sh" | head -1)
case "$ARCH" in arm64) RID=osx-arm64; MACH=arm64; JARCH=aarch64 ;; x64) RID=osx-x64; MACH=x86_64; JARCH=x86_64 ;; *) echo "arch: arm64|x64"; exit 2 ;; esac
[ "$(uname -s)" = Darwin ] && [ "$(uname -m)" = "$MACH" ] || { echo "צריך לבנות את $ARCH על Mac $MACH (כאן: $(uname -sm))"; exit 1; }
: "${JAVA_HOME:?JAVA_HOME = Temurin 21}"
[ -d "$JAVA_HOME/jmods" ] || { echo "אין jmods ב-$JAVA_HOME"; exit 1; }
W=$(mktemp -d); P="$W/payload"; mkdir -p "$P"
trap 'rm -rf "$W"' EXIT

# כל קובץ Mach-O חייב חתימה תקינה (ב-Apple Silicon גם ad-hoc מספיקה). קובץ בלי חתימה — חותמים ad-hoc; עם חתימה — לא נוגעים
check_signatures() {   # check_signatures <dir> <identifier לקבצים שצריך לחתום>
    local f n=0 signed=0
    while IFS= read -r f; do
        case "$(file -b "$f")" in *Mach-O*) ;; *) continue ;; esac
        n=$((n + 1))
        if ! codesign --verify --strict "$f" 2>/dev/null; then
            echo "  חתימה חסרה/לא תקינה → ad-hoc: ${f#$P/}"
            codesign --force -s - --identifier "$2.$(basename "$f")" "$f"
            codesign --verify --strict -v "$f"
            signed=$((signed + 1))
        fi
    done < <(find "$1" -type f \( -perm -u+x -o -name '*.dylib' \))
    echo "  $n קובצי Mach-O, כולם חתומים ($signed נחתמו עכשיו ad-hoc)"
}

echo "== Java 21 (jlink, $JARCH) מ-$JAVA_HOME"
"$JAVA_HOME/bin/java" -version 2>&1 | sed 's/^/  /'
# אותם מודולים כמו ב-Windows (tools/build-jre.sh): java, javac, חלון, הרצה שורה-שורה (jdk.jdi + jdwp), קידודים
MODULES=$(sed -n 's/.*--add-modules \([^ ]*\).*/\1/p' "$ROOT/tools/build-jre.sh" | head -1)
[ -n "$MODULES" ] || { echo "לא נמצאה רשימת המודולים ב-tools/build-jre.sh"; exit 1; }
# --strip-java-debug-attributes (ולא --strip-debug): לא נוגעים בקבצים המקוריים — החתימות נשארות
"$JAVA_HOME/bin/jlink" --module-path "$JAVA_HOME/jmods" --add-modules "$MODULES" --include-locales=en,he \
    --strip-java-debug-attributes --no-header-files --no-man-pages --compress=zip-6 --output "$P/jdk"
cp "$JAVA_HOME/NOTICE" "$P/jdk/" 2>/dev/null || true
"$P/jdk/bin/java" -version
"$P/jdk/bin/javac" -version
MODS=$("$P/jdk/bin/java" --list-modules)
grep -q '^jdk.jdi@' <<<"$MODS" && grep -q '^jdk.compiler@' <<<"$MODS" || { echo "jdk.jdi / jdk.compiler חסרים"; exit 1; }
[ "$("$P/jdk/bin/java" -XshowSettings:properties -version 2>&1 | sed -n 's/.*os.arch = //p')" = "$JARCH" ] || { echo "ארכיטקטורה שגויה"; exit 1; }
check_signatures "$P/jdk" il.lamerhav.dsviz.jdk
codesign -dv "$P/jdk/bin/java" 2>&1 | grep -E 'Identifier|TeamIdentifier|flags' | sed 's/^/  /' || true

echo "== dsviz-cs ($RID, self-contained)"
export DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
dotnet publish "$ROOT/csharp/DsViz.Runner/DsViz.Runner.csproj" -c Release -r "$RID" --self-contained \
    -p:PublishReadyToRun=true -p:DebugType=none -o "$P/cs" -v q --nologo
[ -f "$P/cs/Unit4.CollectionsLib.dll" ] || { echo "Unit4.CollectionsLib.dll חסר ב-cs/"; exit 1; }
check_signatures "$P/cs" il.lamerhav.dsviz-cs
codesign -dv "$P/cs/dsviz-cs" 2>&1 | grep -E 'Identifier|Signature|flags' | sed 's/^/  /' || true
"$P/cs/dsviz-cs" --version
grep -q "$MACH" <<<"$(file "$P/cs/dsviz-cs")" || { echo "dsviz-cs לא $MACH"; exit 1; }

echo "== הספרייה, התוספים, תיקיות התרגילים"
cp "$COMMON/dsviz.jar" "$P/"
cp "$COMMON/dsviz-$VERSION.vsix" "$P/"
# Eclipse: מאגר p2 קטן (Eclipse מתקין ממנו בעצמו). הגרסה 1.0.1.c<hash של התוכן> — תוסף ששונה מקבל גרסה חדשה (ו-p2 מחליף),
# ובעדכון שבו התוסף לא השתנה — אותה גרסה, וההתקנה לא נוגעת ב-Eclipse.
# וגם כקובץ zip — ל-Help ← Install New Software ← Archive (אם macOS לא נתן להתקין אוטומטית)
mkdir -p "$P/eclipse"
python3 "$HERE/make-p2.py" "$COMMON/il.lamerhav.dsviz_$VERSION.jar" "$P/eclipse/p2" auto
(cd "$P/eclipse/p2" && zip -qrX ../dsviz-eclipse-site.zip .)
# IntelliJ IDEA: התיקייה dsviz-intellij (כמו שהיא בתוך plugins) + intellij.sh שמתקין אותה בכל IntelliJ שבמחשב
if [ -d "$COMMON/intellij/dsviz-intellij/lib" ]; then
    mkdir -p "$P/intellij" && cp -R "$COMMON/intellij/dsviz-intellij" "$P/intellij/"
    mkdir -p "$P/intellij/dsviz-intellij/runtime" && cp "$COMMON/dsviz.jar" "$P/intellij/dsviz-intellij/runtime/"
else
    echo "  (אין תוסף ל-IntelliJ בקבצים המשותפים — החבילה בלעדיו)"
fi
cp -R "$COMMON/project" "$P/project"
cp -R "$COMMON/project-cs" "$P/project-cs"
[ -d "$COMMON/help" ] && cp -R "$COMMON/help" "$P/help"
cp "$ROOT/installer/DSViz.targets" "$ROOT/installer/README-he.html" "$P/"
cp "$HERE/install.sh" "$HERE/update.sh" "$HERE/uninstall.sh" "$HERE/intellij.sh" "$P/"
chmod 755 "$P"/*.sh
printf '%s\n' "$BUILD" > "$P/build.txt"
printf '%s\n' "$VERSION" > "$P/version.txt"
printf '%s\n' "$ARCH" > "$P/arch.txt"
find "$P" -name .DS_Store -delete

echo "== חבילה"
NAME="dsviz-mac-$ARCH-$BUILD.tar.gz"
# COPYFILE_DISABLE / --no-xattrs: בלי קובצי ._ ובלי מאפיינים מורחבים של המחשב שבנה
(cd "$P" && COPYFILE_DISABLE=1 tar --no-xattrs -czf "$OUT/$NAME" . 2>/dev/null || COPYFILE_DISABLE=1 tar -czf "$OUT/$NAME" .)
shasum -a 256 "$OUT/$NAME" | awk '{print $1}' > "$OUT/$NAME.sha256"
du -sh "$P"/jdk "$P"/cs "$P"/eclipse "$P"/project* "$P"/help 2>/dev/null | sed 's/^/  /'
echo "$OUT/$NAME  $(du -h "$OUT/$NAME" | cut -f1)  sha256 $(cat "$OUT/$NAME.sha256")"
