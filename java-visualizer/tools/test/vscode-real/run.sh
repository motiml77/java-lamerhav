#!/bin/bash
# בדיקה ב-VS Code האמיתי (Linux + Xvfb): הכפתורים של DSViz בסרגל, "מאגר תרגילים" → מסך הכניסה, ואחרי כניסה → חלון המאגר.
#   VSC_DIR=<תיקיית עבודה> bash tools/test/vscode-real/run.sh   (אחרי tools/build-all.sh — צריך release/dsviz-1.0.1.vsix ואת המאגר)
# דורש: Xvfb, python3-xlib (pip install python-xlib), ImageMagick (import), playwright, ורשת להורדת VS Code בפעם הראשונה.
set -e
R=$(cd "$(dirname "$0")/../../.." && pwd)
export VSC_DIR=${VSC_DIR:-$R/build/test/vscode-real} VSC_UD=${VSC_UD:-/tmp/vcud}   # נתיב קצר: לשקע של VS Code יש מגבלת אורך
mkdir -p "$VSC_DIR/dl"
[ -x "$VSC_DIR/dl/VSCode-linux-x64/code" ] || (cd "$VSC_DIR/dl" && curl -sSL -o code.tar.gz https://update.code.visualstudio.com/latest/linux-x64/stable && tar xzf code.tar.gz)
BANK=$R/build/bank-parts/dsviz-bank-vs.jar
rm -rf "$VSC_DIR/ext" "$VSC_UD" "$VSC_DIR/ws" "$VSC_DIR/seed" && mkdir -p "$VSC_DIR/ws/src" "$VSC_DIR/seed" "$VSC_UD/User"
printf 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("hi");\n    }\n}\n' > "$VSC_DIR/ws/src/Main.java"
printf '{ "dsviz.bankJar": "%s", "security.workspace.trust.enabled": false, "workbench.startupEditor": "none", "telemetry.telemetryLevel": "off", "extensions.autoUpdate": false, "update.mode": "none", "window.titleBarStyle": "custom" }\n' "$BANK" > "$VSC_UD/User/settings.json"
unset JAVA_TOOL_OPTIONS
javac -encoding UTF-8 -nowarn -cp "$BANK:$R/release/dsviz.jar" -d "$VSC_DIR/seed" "$R/tools/test/windows-vs-toolbar/SeedSession.java"
"$VSC_DIR/dl/VSCode-linux-x64/bin/code" --no-sandbox --extensions-dir "$VSC_DIR/ext" --user-data-dir "$VSC_UD" --install-extension "$R/release/dsviz-1.0.1.vsix" >/dev/null 2>&1
if [ -z "$DISPLAY" ]; then Xvfb :55 -screen 0 1600x1000x24 >/dev/null 2>&1 & XP=$!; export DISPLAY=:55; sleep 2; fi
node "$R/tools/test/vscode-real/real-vscode.js"; rc=$?
if [ -n "$XP" ]; then kill $XP; fi
exit $rc
