#!/bin/sh
# Data Structures Visualizer - installer for macOS / Linux
# Installs the extension into VS Code and the plugin into every Eclipse found.
# Optional: ./install.sh /path/to/eclipse
HERE=$(cd "$(dirname "$0")" && pwd)
VSIX="$HERE/dsviz-1.0.0.vsix"
PLUGIN="$HERE/il.lamerhav.dsviz_1.0.0.jar"
echo "Data Structures Visualizer - installing..."
command -v java >/dev/null 2>&1 || echo "[!] Java (JDK) not found. Install a JDK (e.g. Adoptium Temurin 17 or 21)."

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
