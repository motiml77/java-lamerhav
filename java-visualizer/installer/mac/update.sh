#!/bin/bash
# עדכון אוטומטי של DSViz ב-Mac — בלי LaunchAgent (שמוסיף "פריט רקע" מבלבל ב-macOS ואין לו גישה למסמכים).
# מי מפעיל: התוסף של VS Code (כשהוא נטען, פעם ביום) והתוסף של Eclipse (כש-Eclipse נסגר) — ברקע, בלי חלון.
#   1. לא יותר מבדיקה אחת ב-20 שעות (~/.dsviz/update-check.txt), ורק עדכון אחד בכל פעם.
#   2. מוריד את dsviz-mac.json מה-GitHub Release של הערוץ (channel.txt), ואם יש בנייה חדשה יותר —
#      מוריד את install.sh של אותה בנייה, בודק SHA-256, ומריץ אותו עם --update (שקט, בלי שאלות).
#   3. אם אין בנייה חדשה אבל Eclipse היה פתוח בעדכון הקודם (eclipse-pending) — משלים את התוסף ל-Eclipse.
# יומן: ~/Library/Logs/DSViz/update.log. בלי אינטרנט — לא עושים כלום (ננסה בפעם הבאה).
# שימוש: update.sh [--force] [--after-eclipse]
main() {
APP="$(cd "$(dirname "$0")" && pwd)"
DSV="$HOME/.dsviz"
LOGDIR="$HOME/Library/Logs/DSViz"
REPO="motiml77/java-lamerhav"
FORCE=""; AFTER=""
for a in "$@"; do case "$a" in --force) FORCE=1 ;; --after-eclipse) AFTER=--after-eclipse ;; esac; done
mkdir -p "$LOGDIR" "$DSV" 2>/dev/null
LOG="$LOGDIR/update.log"
# היומן לא גדל בלי סוף
[ -f "$LOG" ] && [ "$(wc -c < "$LOG")" -gt 1000000 ] && mv -f "$LOG" "$LOG.old"
exec </dev/null >>"$LOG" 2>&1

STAMP="$DSV/update-check.txt"
if [ -z "$FORCE" ] && [ -f "$STAMP" ] && [ -z "$(find "$STAMP" -mmin +1200 2>/dev/null)" ] && [ ! -f "$APP/eclipse-pending" ]; then
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
fi
return 0
}
main "$@"
