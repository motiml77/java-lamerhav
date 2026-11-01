#!/bin/sh
# Data Structures Visualizer - installer for Linux
# Installs the extension into VS Code and the plugin into every Eclipse and IntelliJ IDEA (2024.2+) found.
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
    # כל עותק קודם (dropins בכל עומק) והמטמון — כמו eclipse -clean, כדי שיטען את התוסף החדש (גם ב-~/.eclipse: Eclipse בתיקייה בלי הרשאה)
    find "$d/dropins" -name 'il.lamerhav.dsviz_*' -prune -exec rm -rf {} + 2>/dev/null
    rm -rf "$d/configuration/org.eclipse.osgi" "$HOME"/.eclipse/*/configuration/org.eclipse.osgi
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

# IntelliJ IDEA (Community / Ultimate, 2024.2+): ~/.config/JetBrains/<IdeaIC2024.2 / IntelliJIdea2025.1 ...>/plugins/dsviz-intellij
IJZIP="$HERE/dsviz-intellij-1.0.1.zip"
ij=0
if [ -f "$IJZIP" ]; then
    for d in "$HOME"/.config/JetBrains/IdeaIC20* "$HOME"/.config/JetBrains/IntelliJIdea20*; do
        [ -d "$d" ] || continue
        v=$(basename "$d" | sed 's/^[A-Za-z]*//'); y=${v%%.*}; m=${v#*.}
        case "$y$m" in *[!0-9]*|"") continue ;; esac
        if [ "$y" -lt 2024 ] || { [ "$y" -eq 2024 ] && [ "$m" -lt 2 ]; }; then echo "[IntelliJ] $(basename "$d"): too old (needs 2024.2+) - skipped"; continue; fi
        mkdir -p "$d/plugins" && rm -rf "$d/plugins/dsviz-intellij"
        if (cd "$d/plugins" && { unzip -qo "$IJZIP" 2>/dev/null || python3 -m zipfile -e "$IJZIP" . ; }); then
            echo "[IntelliJ] installed into $d/plugins"; ij=$((ij + 1))
        fi
    done
    [ "$ij" = 0 ] && echo "[IntelliJ] not found - skipped (or: Settings > Plugins > gear > Install Plugin from Disk > $IJZIP)"
fi
echo "Done. Restart VS Code / Eclipse / IntelliJ IDEA."
