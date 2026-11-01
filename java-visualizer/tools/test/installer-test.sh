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
rm -rf "$OUT" && mkdir -p "$OUT" && OUT=$(cd "$OUT" && pwd)   # נתיב מלא — makensis רץ מתוך installer/
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

# ---------------------------------------------------------------- 2ב. שדרוג: גרסה חדשה תמיד מחליפה את הישנה (בכל סביבה)
# Eclipse זוכר תוסף לפי השם והגרסה: כש-Bundle-Version היה תמיד 1.0.1, מטמון ישן (או עותק ישן במקום אחר) השאיר את הקוד הישן טעון.
BA="$ROOT/tools/build-all.sh"
grep -q 's/^Bundle-Version: .*/Bundle-Version: $VERSION.v$BUILDNO/' "$BA" && grep -q '^BUILDNO=${BUILDNO:-$(date -u +%Y%m%d%H%M)}' "$BA" \
    && [ "$(grep -c -- '--manifest "$MF"' "$BA")" = 2 ] && ! grep -q -- '--manifest eclipse-plugin/META-INF/MANIFEST.MF' "$BA" \
    || fail "build-all.sh: לתוסף ל-Eclipse חסר Bundle-Version ייחודי לכל בנייה (1.0.1.v<מספר>)"
grep -q "p.version='\${VERSION%.\*}.\$BUILDNO'" "$BA" && grep -q 'cp ../build/vscode-package.json package.json' "$BA" \
    || fail "build-all.sh: לתוסף ל-VS Code חסרה גרסה ייחודית לכל בנייה"
ok "build-all.sh: Eclipse 1.0.1.v<בנייה> ו-VS Code 1.0.<בנייה> — כל בנייה גבוהה מהקודמת"
# Mac: make-p2.py auto משאיר את ה-qualifier של הבנייה (v… > c<hash> של התקנות קודמות)
MP=$(mktemp -d "$OUT/p2.XXXX")
mkdir -p "$MP/j/META-INF" && printf 'Manifest-Version: 1.0\r\nBundle-ManifestVersion: 2\r\nBundle-SymbolicName: il.lamerhav.dsviz;singleton:=true\r\nBundle-Version: 1.0.1.v202611011200\r\n\r\n' > "$MP/j/META-INF/MANIFEST.MF"
(cd "$MP/j" && python3 -c "import zipfile; z=zipfile.ZipFile('../p.jar','w'); z.write('META-INF/MANIFEST.MF')")
python3 "$ROOT/installer/mac/make-p2.py" "$MP/p.jar" "$MP/repo" auto > /dev/null && [ -f "$MP/repo/plugins/il.lamerhav.dsviz_1.0.1.v202611011200.jar" ] \
    || fail "Mac: make-p2.py auto לא שמר את גרסת הבנייה ($(ls "$MP/repo/plugins" 2>/dev/null))"
ok "Mac: מאגר ה-p2 עם גרסת הבנייה (1.0.1.v202611011200) — p2 מחליף את הישנה (-uninstallIU)"
grep -q 'eclipse_clean_old "$app"' "$ROOT/installer/mac/install.sh" && grep -q "find \"\$home/dropins\" -name \"\${BUNDLE_ID}_\*\" -prune" "$ROOT/installer/mac/install.sh" \
    || fail "Mac: install.sh לא מנקה עותקים ישנים ב-dropins ואת המטמון"
# Linux: install.sh — עותק ישן בתת-תיקייה של dropins ומטמון נמחקים
LE="$OUT/lin-eclipse"; mkdir -p "$LE/dropins/old/plugins" "$LE/configuration/org.eclipse.osgi"
: > "$LE/dropins/old/plugins/il.lamerhav.dsviz_1.0.1.jar"; : > "$LE/dropins/il.lamerhav.dsviz.it_1.0.0.jar"
sed -n '/^install_eclipse() {/,/^}/p' "$ROOT/installer/install.sh" > "$OUT/lin.sh"
( PLUGIN="$OUT/new.jar"; : > "$PLUGIN"; found=0; HOME="$OUT/lin-home"; . "$OUT/lin.sh"; install_eclipse "$LE" ) > /dev/null
[ ! -e "$LE/dropins/old/plugins/il.lamerhav.dsviz_1.0.1.jar" ] && [ -e "$LE/dropins/new.jar" ] && [ -e "$LE/dropins/il.lamerhav.dsviz.it_1.0.0.jar" ] \
    && [ ! -d "$LE/configuration/org.eclipse.osgi" ] && ok "Linux: install.sh מוחק עותק ישן (גם בתת-תיקייה של dropins) ואת המטמון" \
    || fail "Linux: install.sh השאיר עותק ישן / מטמון"
# Windows: לפני ההעתקה — clean-eclipse.ps1 (כל עותק ישן, bundles.info, המטמון), ו-Eclipse מההתקנה הקודמת תמיד מקבל את החדש
for p in 'File "clean-eclipse.ps1"' '-File "$INSTDIR\clean-eclipse.ps1" "$0"' '-File "$INSTDIR\clean-eclipse.ps1" "$4"' \
         'CopyFiles /SILENT "$INSTDIR\eclipse-dirs.txt" "$PLUGINSDIR\eclipse-prev.txt"' '"$PLUGINSDIR\eclipse-found.txt" "$PLUGINSDIR\eclipse-prev.txt"' \
         'RMDir /r "$INSTDIR\intellij"' 'RMDir /r "$INSTDIR\cs"' 'RMDir /r "$0\configuration\org.eclipse.osgi"'; do
    grep -qF -- "$p" "$NSI" || fail "setup.nsi: חסר $p"
done
awk '/clean-eclipse.ps1" "\$0"/{c=NR} /CopyFiles \/SILENT "\$INSTDIR\\\$\{PLUGIN\}"/{p=NR} END{exit !(c && p && c < p)}' "$NSI" \
    || fail "setup.nsi: clean-eclipse.ps1 צריך לרוץ לפני שמעתיקים את התוסף החדש"
ok "Windows: setup.nsi מנקה כל עותק ישן לפני ההעתקה, וכולל את ה-Eclipse מההתקנה הקודמת"
PWSH=$(command -v pwsh || true)
if [ -n "$PWSH" ]; then
    WE="$OUT/win-eclipse"; WH="$OUT/win-home"
    mkdir -p "$WE/dropins/dsviz/eclipse/plugins" "$WE/plugins" "$WE/configuration/org.eclipse.osgi/x" "$WE/configuration/org.eclipse.equinox.simpleconfigurator" \
             "$WH/.eclipse/epp_4.31_1/configuration/org.eclipse.osgi"
    mkjar() { python3 -c "import zipfile,sys; z=zipfile.ZipFile(sys.argv[1],'w'); z.writestr('META-INF/MANIFEST.MF','Bundle-SymbolicName: '+sys.argv[2]+'\r\nBundle-Version: 1.0.1\r\n')" "$1" "$2"; }
    mkjar "$WE/dropins/il.lamerhav.dsviz_1.0.1.jar" 'il.lamerhav.dsviz;singleton:=true'
    mkjar "$WE/dropins/dsviz (1).jar" 'il.lamerhav.dsviz;singleton:=true'        # הורד שוב בדפדפן, בשם אחר
    mkjar "$WE/dropins/dsviz/eclipse/plugins/il.lamerhav.dsviz_1.0.0.jar" 'il.lamerhav.dsviz'
    mkjar "$WE/plugins/il.lamerhav.dsviz_1.0.1.jar" 'il.lamerhav.dsviz;singleton:=true'
    mkjar "$WE/dropins/il.lamerhav.dsviz.it_1.0.0.jar" 'il.lamerhav.dsviz.it'   # תוסף אחר — נשאר
    mkjar "$WE/dropins/other.jar" 'org.example.other'
    printf 'il.lamerhav.dsviz,1.0.1,plugins/il.lamerhav.dsviz_1.0.1.jar,4,false\norg.example,1.0.0,plugins/org.example.jar,4,false\n' \
        > "$WE/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
    "$PWSH" -NoProfile -File "$ROOT/installer/clean-eclipse.ps1" "$WE" -UserHome "$WH" > "$OUT/clean-eclipse.txt" 2>&1
    left=$(cd "$WE" && find dropins plugins -type f | sort | tr '\n' ' ')
    [ "$left" = "dropins/il.lamerhav.dsviz.it_1.0.0.jar dropins/other.jar " ] && [ ! -d "$WE/configuration/org.eclipse.osgi" ] \
        && [ ! -d "$WH/.eclipse/epp_4.31_1/configuration/org.eclipse.osgi" ] \
        && ! grep -q '^il.lamerhav.dsviz,' "$WE/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info" \
        && grep -q '^org.example,' "$WE/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info" \
        && ok "Windows: clean-eclipse.ps1 (pwsh) — כל העותקים הישנים, השורה ב-bundles.info והמטמונים נמחקו; תוספים אחרים נשארו" \
        || { cat "$OUT/clean-eclipse.txt"; fail "Windows: clean-eclipse.ps1 — נשאר: $left"; }
else
    echo "  (אין pwsh — מדלגים על ההרצה של clean-eclipse.ps1)"
fi

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
