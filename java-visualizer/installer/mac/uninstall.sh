#!/bin/bash
# הסרת DSViz מה-Mac. מריצים בטרמינל:
#   "$HOME/Library/Application Support/DSViz/uninstall.sh"
# או: curl -fsSL https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/installer/mac/uninstall.sh | bash
# מסיר: התוסף מ-VS Code ומכל Eclipse, DSViz.targets (C#), התיקייה ~/Library/Application Support/DSViz, הנתיבים ב-~/.dsviz.
# לא נוגע: בתיקיות התרגילים במסמכים (DataStructures, DataStructures-CS — העבודה שלכם), וב-‎.NET SDK שב-~/.dotnet.
main() {
APP="$HOME/Library/Application Support/DSViz"
DSV="$HOME/.dsviz"
BUNDLE_ID="il.lamerhav.dsviz"
if [ -t 1 ]; then G=$'\033[32m'; Y=$'\033[33m'; D=$'\033[2m'; B=$'\033[1m'; N=$'\033[0m'; else G=; Y=; D=; B=; N=; fi
ok()   { printf '  %s✓%s %s  %s%s%s\n' "$G" "$N" "$1" "$D" "$2" "$N"; }
warn() { printf '  %s!%s %s  %s%s%s\n' "$Y" "$N" "$1" "$D" "$2" "$N"; }
[ "$(uname -s)" = Darwin ] || { echo "macOS only"; return 1; }
TTY=""; [ -z "$DSVIZ_NONINTERACTIVE" ] && (exec </dev/tty) 2>/dev/null && TTY=1
TMP=$(mktemp -d "${TMPDIR:-/tmp}/dsviz-un.XXXXXX"); trap 'rm -rf "$TMP"' EXIT
with_timeout() {
    local secs=$1 i=0 pid; shift
    "$@" </dev/null & pid=$!
    while kill -0 "$pid" 2>/dev/null; do
        if [ "$i" -ge "$secs" ]; then kill "$pid" 2>/dev/null; sleep 2; kill -9 "$pid" 2>/dev/null; wait "$pid" 2>/dev/null; return 124; fi
        sleep 1; i=$((i + 1))
    done
    wait "$pid"
}
printf '\n%sהסרת DSViz%s  %s(Uninstalling DSViz)%s\n' "$B" "$N" "$D" "$N"

# ---- VS Code
for c in "$(command -v code 2>/dev/null)" "/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code" \
         "$HOME/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code" "$HOME/Downloads/Visual Studio Code.app/Contents/Resources/app/bin/code"; do
    [ -n "$c" ] && [ -x "$c" ] || continue
    if "$c" --list-extensions 2>/dev/null </dev/null | grep -qix lamerhav.dsviz; then
        with_timeout 120 "$c" --uninstall-extension lamerhav.dsviz >/dev/null 2>&1 && ok "VS Code: התוסף הוסר" "VS Code extension removed"
    fi
    break
done

# ---- Eclipse: כל מקום שבו התוסף רשום (eclipse.txt), וגם כל Eclipse שנמצא בו התוסף (מהתקנה ישנה / ידנית)
ECLS=$( { cut -d'|' -f1 "$APP/eclipse.txt" 2>/dev/null
          for a in /Applications/Eclipse*.app /Applications/*/Eclipse*.app "$HOME"/Applications/Eclipse*.app "$HOME"/Applications/*/Eclipse*.app \
                   "$HOME"/eclipse/*/Eclipse*.app "$HOME"/eclipse*/Eclipse*.app "$HOME"/Downloads/Eclipse*.app "$HOME"/Desktop/Eclipse*.app; do
              [ -f "$a/Contents/Eclipse/eclipse.ini" ] && (cd "$a" && pwd -P)
          done; } | sort -u)
while IFS= read -r app; do
    [ -n "$app" ] && [ -d "$app" ] || continue
    home="$app/Contents/Eclipse"
    info="$home/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
    grep -q "^$BUNDLE_ID," "$info" 2>/dev/null || ls "$home"/dropins/${BUNDLE_ID}_*.jar >/dev/null 2>&1 || continue
    name=$(basename "$app" .app)
    while pgrep -f "$app/Contents/MacOS/eclipse" >/dev/null 2>&1; do
        if [ -n "$TTY" ]; then
            printf '  %s פתוח — סגרו אותו (Cmd+Q) ולחצו Enter, או s ו-Enter לדלג: ' "$name"; r=""; read -r r </dev/tty
            [ "$r" = s ] && break
        else break; fi
    done
    if pgrep -f "$app/Contents/MacOS/eclipse" >/dev/null 2>&1; then warn "$name פתוח — דילוג" "$app is running — skipped"; continue; fi
    if ls "$home"/dropins/${BUNDLE_ID}_*.jar >/dev/null 2>&1; then
        rm -f "$home"/dropins/${BUNDLE_ID}_*.jar && rm -rf "$home/configuration/org.eclipse.osgi"
    fi
    if grep -q "^$BUNDLE_ID,[^,]*,plugins/" "$info" 2>/dev/null; then
        # הותקן דרך p2 — Eclipse עצמו מסיר (דרך open, כמו בהתקנה — App Management)
        out="$TMP/p2.log"; : > "$out"
        args=(-nosplash -consoleLog -application org.eclipse.equinox.p2.director -uninstallIU "$BUNDLE_ID")
        if [ "${DSVIZ_ECLIPSE_DIRECT:-}" != 1 ] && open -h 2>&1 | grep -q -- '--stdout'; then
            with_timeout 300 open -W -n -g -a "$app" --stdout "$out" --stderr "$out" --args "${args[@]}"
        else
            with_timeout 300 "$app/Contents/MacOS/eclipse" "${args[@]}" >"$out" 2>&1
        fi
    fi
    if grep -q "^$BUNDLE_ID,[^,]*,plugins/" "$info" 2>/dev/null || ls "$home"/dropins/${BUNDLE_ID}_*.jar >/dev/null 2>&1; then
        warn "$name: לא הצלחתי להסיר — ב-Eclipse: Help ← About ← Installation Details ← Data Structures Visualizer ← Uninstall" "Could not remove from $app"
    else
        ok "$name: התוסף הוסר" "Eclipse plugin removed from $app"
    fi
done <<EOF
$ECLS
EOF

# ---- C# (MSBuild) — משאירים את התיקייה ImportAfter (יכולים להיות בה קבצים של תוכנות אחרות)
for d in "$HOME/Library/Application Support/Microsoft/MSBuild/Current/Microsoft.CSharp.targets/ImportAfter" \
         "$HOME/.local/share/Microsoft/MSBuild/Current/Microsoft.CSharp.targets/ImportAfter"; do
    [ -f "$d/DSViz.targets" ] && rm -f "$d/DSViz.targets" && ok "C#: DSViz.targets הוסר" "$d/DSViz.targets removed"
done

# ---- הקבצים עצמם
rm -rf "$APP" "$HOME/Library/Logs/DSViz"
for f in jdk-path.txt cs-path.txt install-dir.txt project-path.txt project-cs-path.txt update-check.txt update.lock; do rm -rf "$DSV/$f"; done
rmdir "$DSV" 2>/dev/null
ok "הוסר: $APP" "Removed the DSViz files"
printf '\n%sDSViz הוסר.%s התיקיות במסמכים (DataStructures, DataStructures-CS) נשארו — יש בהן את העבודה שלכם.\n' "$G" "$N"
printf '%sDSViz removed. Your exercise folders in ~/Documents were kept%s.%s\n' "$D" "$([ -x "$HOME/.dotnet/dotnet" ] && echo ', and so was the .NET SDK in ~/.dotnet')" "$N"
return 0
}
main "$@"
