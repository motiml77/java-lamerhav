#!/bin/bash
# עדכון אוטומטי של DSViz ב-Mac.
# מי מפעיל: LaunchAgent של המשתמש (~/Library/LaunchAgents/il.lamerhav.dsviz.update.plist — בכניסה למחשב ופעם ביום, בלי
# הרשאות מנהל; עם --agent), התוסף של VS Code (כשהוא נטען, פעם ביום) והתוסף של Eclipse (כש-Eclipse נסגר) — ברקע, בלי חלון.
# מה-LaunchAgent (--agent) לא נוגעים בתיקיות התרגילים במסמכים: macOS היה שואל "bash would like to access Documents"
# (הספרייה שבתיקיות מתעדכנת ממילא מהתוספים עצמם, וגם בעדכון הבא שמופעל מסביבת פיתוח).
#   1. לא יותר מבדיקה אחת ב-20 שעות (~/.dsviz/update-check.txt), ורק עדכון אחד בכל פעם.
#   2. מוריד את dsviz-mac.json מה-GitHub Release של הערוץ (channel.txt), ואם יש בנייה חדשה יותר —
#      מוריד את install.sh של אותה בנייה, בודק SHA-256, ומריץ אותו עם --update (שקט, בלי שאלות).
#   3. אם אין בנייה חדשה אבל Eclipse היה פתוח בעדכון הקודם (eclipse-pending) — משלים את התוסף ל-Eclipse.
#   4. IntelliJ IDEA: בכל בדיקה — התוסף נכנס לכל IntelliJ שעוד אין בו (intellij.sh), גם IntelliJ שהותקן אחרי DSViz.
#      (מי עוד מפעיל: התוסף של IntelliJ — פעם ביום, כש-IntelliJ נפתח)
#   5. VS Code / Insiders / VSCodium / Cursor / Eclipse שהותקנו אחרי DSViz: רשימת האפליקציות (--ide-signature) לא כמו
#      בהתקנה האחרונה (ides.txt) — משלימים את התוספים מההתקנה הקיימת (install.sh --update --no-download), בלי להוריד שוב.
# יומן: ~/Library/Logs/DSViz/update.log. בלי אינטרנט — לא עושים כלום (ננסה בפעם הבאה).
# שימוש: update.sh [--force] [--after-eclipse] [--agent] [--ide-signature]
main() {
APP="$(cd "$(dirname "$0")" && pwd)"
DSV="$HOME/.dsviz"
LOGDIR="$HOME/Library/Logs/DSViz"
REPO="motiml77/java-lamerhav"
FORCE=""; AFTER=""; SIG=""
for a in "$@"; do case "$a" in --force) FORCE=1 ;; --after-eclipse) AFTER=--after-eclipse ;; --agent) export DSVIZ_AGENT=1 ;; --ide-signature) SIG=1 ;; esac; done
# האפליקציות של סביבות הפיתוח שבמחשב (שורה לכל אחת) — כדי לזהות סביבה שהותקנה אחרי DSViz
ide_signature() {
    ls -d "/Applications/Visual Studio Code.app" "$HOME/Applications/Visual Studio Code.app" \
          "/Applications/Visual Studio Code - Insiders.app" "$HOME/Applications/Visual Studio Code - Insiders.app" \
          /Applications/VSCodium.app "$HOME/Applications/VSCodium.app" /Applications/Cursor.app "$HOME/Applications/Cursor.app" \
          /Applications/Eclipse*.app /Applications/*/Eclipse*.app "$HOME"/Applications/Eclipse*.app "$HOME"/eclipse/*/Eclipse*.app \
          "$HOME"/eclipse*/Eclipse*.app 2>/dev/null | LC_ALL=C sort -u
}
if [ -n "$SIG" ]; then ide_signature; return 0; fi
mkdir -p "$LOGDIR" "$DSV" 2>/dev/null
LOG="$LOGDIR/update.log"
# היומן לא גדל בלי סוף
[ -f "$LOG" ] && [ "$(wc -c < "$LOG")" -gt 1000000 ] && mv -f "$LOG" "$LOG.old"
exec </dev/null >>"$LOG" 2>&1

STAMP="$DSV/update-check.txt"
if [ -z "$FORCE" ] && [ -f "$STAMP" ] && [ -z "$(find "$STAMP" -mmin +1200 2>/dev/null)" ] && [ ! -f "$APP/eclipse-pending" ] && [ ! -f "$APP/intellij-pending" ]; then
    return 0   # נבדק ב-20 השעות האחרונות
fi
LOCK="$DSV/update.lock"
if ! mkdir "$LOCK" 2>/dev/null; then
    [ -n "$(find "$LOCK" -maxdepth 0 -mmin +120 2>/dev/null)" ] || return 0   # עדכון אחר רץ עכשיו
    rm -rf "$LOCK"; mkdir "$LOCK" 2>/dev/null || return 0
fi
trap 'rm -rf "$LOCK" "$TMP" 2>/dev/null' EXIT
echo "---- $(date '+%Y-%m-%d %H:%M:%S') check"
[ -n "$AFTER" ] && sleep 5
touch "$STAMP"

# IntelliJ IDEA: גם IntelliJ שהותקן אחרי DSViz (או שהיה פתוח בעדכון הקודם) מקבל את התוסף — בכל בדיקה, גם בלי אינטרנט
if [ -f "$APP/intellij.sh" ] && [ -d "$APP/intellij/dsviz-intellij/lib" ] && [ "$(cat "$APP/lang.txt" 2>/dev/null)" != cs ]; then
    bash "$APP/intellij.sh" install "$APP/intellij/dsviz-intellij"; IJ_RC=$?
    if [ "$IJ_RC" = 3 ]; then : > "$APP/intellij-pending"; else rm -f "$APP/intellij-pending"; fi
fi

CHANNEL=$(cat "$APP/channel.txt" 2>/dev/null); case "$CHANNEL" in mac-*) ;; *) CHANNEL=mac-latest ;; esac
BASE="https://github.com/$REPO/releases/download/$CHANNEL"
TMP=$(mktemp -d "${TMPDIR:-/tmp}/dsviz-update.XXXXXX") || return 0
if ! curl -fsSL --retry 2 --connect-timeout 20 --max-time 60 -o "$TMP/v.json" "$BASE/dsviz-mac.json"; then
    echo "offline / no version file ($BASE)"; return 0
fi
jget() { sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" "$2" | head -1; }
REMOTE=$(jget build "$TMP/v.json" | tr -cd '0-9')
LOCAL=$(cat "$APP/build.txt" 2>/dev/null | tr -cd '0-9')
echo "installed: ${LOCAL:-none}  published: ${REMOTE:-?}  channel: $CHANNEL"
if [ -n "$REMOTE" ] && { [ -z "$LOCAL" ] || [ "$REMOTE" -gt "$LOCAL" ]; }; then
    INST=$(jget installer_file "$TMP/v.json"); SHA=$(jget installer_sha256 "$TMP/v.json")
    case "$INST" in ""|*/*) echo "bad installer name"; return 0 ;; esac
    curl -fsSL --retry 2 --connect-timeout 20 --max-time 120 -o "$TMP/install.sh" "$BASE/$INST" || { echo "installer download failed"; return 0; }
    GOT=$(shasum -a 256 "$TMP/install.sh" 2>/dev/null | awk '{print $1}')
    [ "$GOT" = "$SHA" ] || { echo "installer checksum mismatch ($GOT != $SHA)"; return 0; }
    echo "updating to build $REMOTE"
    /bin/bash "$TMP/install.sh" --update $AFTER
elif [ -f "$APP/eclipse-pending" ] && [ -x "$APP/install.sh" ]; then
    echo "completing the Eclipse plugin"
    /bin/bash "$APP/install.sh" --update --no-download $AFTER
elif [ -f "$APP/install.sh" ] && [ "$(ide_signature)" != "$(cat "$APP/ides.txt" 2>/dev/null)" ]; then
    echo "a development environment was added or removed — completing the plugins"
    /bin/bash "$APP/install.sh" --update --no-download $AFTER
fi
return 0
}
main "$@"
