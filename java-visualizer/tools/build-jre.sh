#!/bin/sh
# בונה את build/jre-windows: Java 21 מקוצרת ל-Windows שנכנסת לתוך DSViz-Setup.exe —
# כך שאין צורך להוריד Java בהתקנה. כוללת את מה שהתלמידים צריכים: java, javac, חלון (Swing),
# הרצה שורה-שורה (jdk.jdi + jdwp) וקידודי עברית. ~67MB במקום ~300MB של JDK מלא.
# מורידים JDK ל-Linux (בשביל jlink) ול-Windows (בשביל ה-jmods) מאותה גרסה בדיוק, ובודקים checksum.
set -e
cd "$(dirname "$0")/.."
VER=21.0.5_11
TAG=jdk-21.0.5%2B11
BASE="https://github.com/adoptium/temurin21-binaries/releases/download/$TAG"
C=build/jdk-cache
OUT=build/jre-windows
[ -f "$OUT/bin/java.exe" ] && { echo "$OUT (קיים)"; exit 0; }
mkdir -p "$C"
fetch() {   # $1 = שם הקובץ ב-Adoptium, $2 = שם מקומי
    if [ ! -f "$C/$2" ]; then
        curl -fsSL -o "$C/$2.part" "$BASE/$1"
        exp=$(curl -fsSL "$BASE/$1.sha256.txt" | awk '{print $1}')
        act=$(sha256sum "$C/$2.part" | awk '{print $1}')
        [ "$exp" = "$act" ] || { echo "checksum mismatch: $1"; rm -f "$C/$2.part"; exit 1; }
        mv "$C/$2.part" "$C/$2"
    fi
}
fetch "OpenJDK21U-jdk_x64_linux_hotspot_$VER.tar.gz" linux.tar.gz
fetch "OpenJDK21U-jdk_x64_windows_hotspot_$VER.zip" windows.zip
rm -rf "$C/linux" "$C/windows" "$OUT"
mkdir -p "$C/linux" "$C/windows"
tar -xzf "$C/linux.tar.gz" -C "$C/linux"
(cd "$C/windows" && unzip -q ../windows.zip)
"$C"/linux/jdk*/bin/jlink --module-path "$(ls -d "$C"/windows/jdk*)/jmods" \
    --add-modules java.se,jdk.compiler,jdk.jdi,jdk.jdwp.agent,jdk.attach,jdk.zipfs,jdk.charsets,jdk.localedata,jdk.unsupported,jdk.crypto.ec \
    --include-locales=en,he --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "$OUT"
cp "$(ls -d "$C"/windows/jdk*)/NOTICE" "$OUT/" 2>/dev/null || true
rm -rf "$C/linux" "$C/windows"
echo "$OUT ($(du -sh "$OUT" | cut -f1))"
