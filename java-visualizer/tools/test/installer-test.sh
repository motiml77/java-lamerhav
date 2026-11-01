#!/bin/bash
# ההתקנות (בלי Windows ובלי Mac אמיתיים):
#  1. Mac (installer/mac/install.sh): הקטע vscode-forks — VS Code Insiders / VSCodium / Cursor מקבלים את התוסף, עם אפליקציות מדומות
#  2. Windows (installer/setup.nsi): מתקמפל ב-makensis (עם קבצים מדומים במקום מה ש-build-all.sh בונה), והגרסאות האחרות של
#     VS Code (Insiders / VSCodium / Cursor) מותקנות ומוסרות
#   bash installer-test.sh <out-dir>
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
OUT=${1:-$ROOT/build/test}/installer
rm -rf "$OUT" && mkdir -p "$OUT"
PASS=0
ok()   { echo "  ok  $*"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL $*"; exit 1; }

# ---------------------------------------------------------------- 1. Mac: vscode-forks
H="$OUT/home"; mkdir -p "$H/Applications"
fake() {   # fake <App.app> <cli>
    local d="$H/Applications/$1/Contents/Resources/app/bin"
    mkdir -p "$d"
    printf '#!/bin/sh\necho "%s $*" >> "%s"\n' "$2" "$OUT/calls.txt" > "$d/$2"
    chmod +x "$d/$2"
}
fake "Visual Studio Code - Insiders.app" code-insiders
fake "VSCodium.app" codium
fake "Cursor.app" cursor
mkdir -p "$H/Applications/Other.app/Contents/Resources/app/bin"   # בלי CLI — לא נחשב
sed -n '/^# >>> vscode-forks/,/^# <<< vscode-forks/p' "$ROOT/installer/mac/install.sh" > "$OUT/forks.sh"
[ "$(grep -c . "$OUT/forks.sh")" -gt 10 ] || fail "הקטע vscode-forks לא נמצא ב-install.sh"
(
    HOME="$H"; APP="$OUT/app"; LOG="$OUT/log.txt"; VSIX="$OUT/app/dsviz-1.0.1.vsix"; mkdir -p "$APP"; : > "$VSIX"
    with_timeout() { shift; "$@"; }
    ok() { echo "ok: $1"; }; warn() { echo "warn: $1"; }
    # shellcheck disable=SC1090
    . "$OUT/forks.sh"
    install_vscode_forks
) > "$OUT/run.txt" 2>&1
grep -q "code-insiders --install-extension $OUT/app/dsviz-1.0.1.vsix --force" "$OUT/calls.txt" \
    && grep -q "codium --install-extension" "$OUT/calls.txt" && grep -q "cursor --install-extension" "$OUT/calls.txt" \
    || { cat "$OUT/run.txt" "$OUT/calls.txt"; fail "Mac: לא כל הגרסאות של VS Code קיבלו את התוסף"; }
[ "$(wc -l < "$OUT/app/vscode-forks.txt")" = 3 ] || fail "Mac: vscode-forks.txt (בשביל ההסרה) — $(cat "$OUT/app/vscode-forks.txt")"
grep -q "ok: VSCodium: התוסף הותקן" "$OUT/run.txt" || fail "Mac: ההודעה לתלמיד"
grep -q 'vscode-forks.txt' "$ROOT/installer/mac/uninstall.sh" || fail "Mac: uninstall.sh לא מסיר מהגרסאות האחרות"
ok "Mac: VS Code Insiders, VSCodium ו-Cursor (מדומים) קיבלו את התוסף; נרשמו בשביל ההסרה"
bash -n "$ROOT/installer/mac/install.sh" && bash -n "$ROOT/installer/mac/uninstall.sh" && bash -n "$ROOT/installer/mac/update.sh" \
    && ok "Mac: install.sh / uninstall.sh / update.sh — תחביר תקין" || fail "Mac: שגיאת תחביר"

# ---------------------------------------------------------------- 1ב. Mac: העדכון האוטומטי (LaunchAgent) ו-IDE שהותקן אחרי DSViz
sed -n '/^# >>> launchagent/,/^# <<< launchagent/p' "$ROOT/installer/mac/install.sh" > "$OUT/la.sh"
[ "$(grep -c . "$OUT/la.sh")" -gt 10 ] || fail "הקטע launchagent לא נמצא ב-install.sh"
(
    HOME="$H"; APP="$H/Library/Application Support/DSViz"; LOG="$OUT/la-log.txt"; mkdir -p "$APP"
    DSVIZ_NO_LAUNCHCTL=1
    # shellcheck disable=SC1090
    . "$OUT/la.sh"
    install_launchagent && install_launchagent   # פעמיים: הפעם השנייה לא משנה כלום
) || fail "Mac: install_launchagent נכשל"
PL="$H/Library/LaunchAgents/il.lamerhav.dsviz.update.plist"
python3 - "$PL" "$H/Library/Application Support/DSViz/update.sh" <<'PY' || fail "Mac: ה-plist של ה-LaunchAgent"
import plistlib, sys
d = plistlib.load(open(sys.argv[1], 'rb'))
assert d['Label'] == 'il.lamerhav.dsviz.update', d
assert d['ProgramArguments'] == ['/bin/bash', sys.argv[2], '--agent'], d['ProgramArguments']
assert d['RunAtLoad'] is True and d['StartInterval'] == 86400 and d['ProcessType'] == 'Background', d
PY
grep -q 'launchctl bootout "gui/$(id -u)/il.lamerhav.dsviz.update"' "$ROOT/installer/mac/uninstall.sh" && grep -q 'rm -f "$LA"' "$ROOT/installer/mac/uninstall.sh" \
    || fail "Mac: uninstall.sh לא מסיר את ה-LaunchAgent"
ok "Mac: LaunchAgent של המשתמש (RunAtLoad + פעם ביום, update.sh --agent, plist תקין); uninstall.sh מסיר אותו"
# update.sh --ide-signature: רואה אפליקציה חדשה (כאן: VSCodium / Cursor / Insiders המדומים, ו-Eclipse שנוסף)
SIG1=$(HOME="$H" bash "$ROOT/installer/mac/update.sh" --ide-signature)
mkdir -p "$H/Applications/Eclipse.app"
SIG2=$(HOME="$H" bash "$ROOT/installer/mac/update.sh" --ide-signature)
echo "$SIG1" | grep -q "VSCodium.app" && [ "$SIG1" != "$SIG2" ] && echo "$SIG2" | grep -q "Applications/Eclipse.app" \
    || fail "Mac: update.sh --ide-signature ($SIG1 / $SIG2)"
grep -q 'ide_signature)" != "$(cat "$APP/ides.txt"' "$ROOT/installer/mac/update.sh" && grep -q 'update.sh" --ide-signature > "$APP/ides.txt"' "$ROOT/installer/mac/install.sh" \
    || fail "Mac: ההשוואה ל-ides.txt"
ok "Mac: update.sh מזהה סביבת פיתוח שנוספה אחרי ההתקנה (--ide-signature מול ides.txt) ומשלים את התוספים"
# כשנבדק ב-20 השעות האחרונות — update.sh (גם מה-LaunchAgent) יוצא מיד, בלי רשת ובלי לכתוב "check" ליומן
mkdir -p "$H/.dsviz" && touch "$H/.dsviz/update-check.txt"
t0=$(date +%s%N); HOME="$H" bash "$ROOT/installer/mac/update.sh" --agent; t1=$(date +%s%N)
! grep -q ' check' "$H/Library/Logs/DSViz/update.log" 2>/dev/null && [ $(( (t1 - t0) / 1000000 )) -lt 2000 ] \
    && ok "Mac: update.sh --agent כשאין מה לבדוק: יוצא מיד ($(( (t1 - t0) / 1000000 ))ms), בלי רשת" || fail "Mac: update.sh לא יצא מיד"
# --agent: בלי לגעת במסמכים
grep -q 'export DSVIZ_AGENT=1' "$ROOT/installer/mac/update.sh" && grep -q 'if \[ -n "${DSVIZ_AGENT:-}" \]; then' "$ROOT/installer/mac/install.sh" \
    && ok "Mac: עדכון מה-LaunchAgent לא נוגע ב-~/Documents (בלי שאלת הפרטיות של macOS על bash)" || fail "Mac: DSVIZ_AGENT"

# ---------------------------------------------------------------- 2. Windows: setup.nsi
NSI="$ROOT/installer/setup.nsi"
for p in 'Microsoft VS Code Insiders\bin\code-insiders.cmd' 'VSCodium\bin\codium.cmd' 'cursor\resources\app\bin\cursor.cmd' \
         'FileOpen $3 "$INSTDIR\vscode-forks.txt" r' '--uninstall-extension lamerhav.dsviz'; do
    grep -qF -- "$p" "$NSI" || fail "setup.nsi: חסר $p"
done
ok "Windows: setup.nsi מתקין ב-VS Code Insiders / VSCodium / Cursor ומסיר מהם"
if command -v makensis >/dev/null 2>&1; then
    B="$OUT/nsis/build"; R="$OUT/nsis/release"
    mkdir -p "$B/light" "$B/help" "$B/project" "$B/jre-windows/bin" "$B/dsviz-cs-win" "$B/project-cs" "$B/vs-template" "$B/intellij/dsviz-intellij/lib" "$R"
    for f in light/dsviz-1.0.1.vsix light/il.lamerhav.dsviz_1.0.1.jar help/index.html project/README.txt jre-windows/bin/java.exe \
             dsviz-cs-win/dsviz-cs.exe project-cs/README.txt vs-template/DSViz-Exercise.zip intellij/dsviz-intellij/lib/dsviz-intellij.jar; do echo x > "$B/$f"; done
    echo x > "$R/dsviz.jar"
    (cd "$ROOT/installer" && makensis -V2 -WX -DBUILD="$B" -DRELEASE="$R" setup.nsi) > "$OUT/makensis.txt" 2>&1 \
        && [ -s "$R/DSViz-Setup.exe" ] && ok "Windows: setup.nsi מתקמפל ב-makensis בלי אזהרות ($(du -k "$R/DSViz-Setup.exe" | cut -f1)K עם קבצים מדומים)" \
        || { tail -30 "$OUT/makensis.txt"; fail "Windows: makensis נכשל"; }
else
    echo "  (אין makensis — מדלגים על הקימפול של setup.nsi)"
fi
echo "$PASS בדיקות של ההתקנות עברו"
