#!/bin/sh
# Data Structures Visualizer - installer for Linux
# Installs the extension into VS Code and the plugin into every Eclipse found.
# Optional: ./install.sh /path/to/eclipse
# macOS: use the one-line Terminal installer instead (bundled Java 21, C#, auto-update):
#   curl -fsSL https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/installer/mac/install.sh | bash
MAC_URL="https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/installer/mac/install.sh"
if [ "$(uname -s)" = Darwin ]; then
    echo "ב-Mac מתקינים בפקודה אחת בטרמינל (Java כלולה, בלי סיסמה) — מריצים אותה עכשיו:"
    echo "On macOS, running the Mac installer: curl -fsSL $MAC_URL | bash"
    curl -fsSL "$MAC_URL" | bash
    exit $?
fi
HERE=$(cd "$(dirname "$0")" && pwd)
VSIX="$HERE/dsviz-1.0.1.vsix"
PLUGIN="$HERE/il.lamerhav.dsviz_1.0.1.jar"
echo "Data Structures Visualizer - installing..."
command -v java >/dev/null 2>&1 || echo "[!] Java (JDK) not found. Install a JDK (e.g. Adoptium Temurin 17 or 21)."

# תיקיית תרגילים מוכנה במסמכים (משותפת ל-VS Code ול-Eclipse). אם כבר קיימת — רק מעדכנים את הספרייה
DOCS="$HOME/Documents"; [ -d "$DOCS" ] || DOCS="$HOME"
PROJECT="$DOCS/DataStructures"
if [ -f "$PROJECT/.project" ]; then
    mkdir -p "$PROJECT/lib" && cp "$HERE/dsviz.jar" "$PROJECT/lib/" && echo "[project] updated library in $PROJECT"
elif [ -d "$HERE/project" ]; then
    mkdir -p "$PROJECT" && cp -R "$HERE/project/." "$PROJECT/" && echo "[project] created $PROJECT"
fi
mkdir -p "$HOME/.dsviz" && printf '%s' "$PROJECT" > "$HOME/.dsviz/project-path.txt"

CODE=$(command -v code)
[ -z "$CODE" ] && [ -x "/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code" ] && \
    CODE="/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code"
if [ -n "$CODE" ]; then
    echo "[VS Code] installing extension..."
    "$CODE" --install-extension "$VSIX" --force
    "$CODE" --install-extension vscjava.vscode-java-pack
else
    echo "[VS Code] not found - skipped"
fi

found=0
install_eclipse() {
    d="$1"
    mkdir -p "$d/dropins" 2>/dev/null
    rm -f "$d"/dropins/il.lamerhav.dsviz_*.jar
    rm -rf "$d/configuration/org.eclipse.osgi"   # כמו eclipse -clean — כדי שיטען את התוסף החדש
    if cp "$PLUGIN" "$d/dropins/" 2>/dev/null; then
        echo "[Eclipse] installed into $d"; found=$((found + 1))
    else
        echo "[Eclipse] could not write to $d/dropins (try: sudo $0 \"$d\")"
    fi
}
if [ -n "$1" ]; then install_eclipse "$1"; fi
for d in "$HOME"/eclipse/*/eclipse "$HOME"/eclipse "$HOME"/eclipse-* /opt/eclipse* /usr/lib/eclipse \
         /Applications/Eclipse*.app/Contents/Eclipse "$HOME"/Applications/Eclipse*.app/Contents/Eclipse; do
    if [ -f "$d/eclipse" ] || [ -f "$d/eclipse.ini" ]; then install_eclipse "$d"; fi
done
[ "$found" = 0 ] && echo "[Eclipse] not found automatically. Run: ./install.sh /path/to/eclipse"
echo "Done. Restart VS Code / Eclipse."
