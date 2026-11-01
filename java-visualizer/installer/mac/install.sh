#!/bin/bash
# ============================================================================================
#  DSViz — מבני נתונים, הרצה ויזואלית: התקנה ל-Mac (Apple Silicon ו-Intel), בלי הרשאות מנהל.
#
#  מדביקים בטרמינל (Cmd+רווח ← Terminal) ולוחצים Enter:
#    curl -fsSL https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/installer/mac/install.sh | bash
#
#  מה מותקן (הכול לתיקייה ~/Library/Application Support/DSViz — לא צריך סיסמה):
#    Java 21 מקוצרת (java, javac, חלון, הרצה שורה-שורה), dsviz.jar, dsviz-cs (ההרצה הוויזואלית של C#),
#    תיקיות התרגילים במסמכים (DataStructures ו/או DataStructures-CS — עבודה קיימת לא נדרסת),
#    התוסף ל-VS Code (אם יש), התוסף לכל Eclipse שנמצא (Eclipse עצמו מתקין אותו), לכל IntelliJ IDEA (2024.2+), ול-C#: DSViz.targets
#    (ההפניה לספרייה בכל פרויקט) ואם צריך — ‎.NET SDK ב-~/.dotnet.
#  הרצה חוזרת = עדכון (מה שכבר עדכני — לא מורידים שוב). ההסרה: ~/Library/Application Support/DSViz/uninstall.sh
#
#  למה ככה: קבצים ש-curl מוריד לא מסומנים "הורד מהאינטרנט" (com.apple.quarantine), ולכן macOS לא חוסם
#  ולא שואל — בלי Apple Developer ID. כל קובץ שמורד נבדק מול SHA-256 מקובץ הגרסה (dsviz-mac.json).
#
#  אפשרויות (משתני סביבה — שמים לפני bash: ‎... | DSVIZ_LANG=both bash‎ — או ארגומנטים: ‎... | bash -s -- --lang=both‎):
#    DSVIZ_LANG=java|cs|both    --lang=...      באיזו שפה לומדים (ברירת מחדל: שואלים; בלי מקלדת — java, או both אם יש dotnet)
#    DSVIZ_DOTNET=1|0           --with-dotnet / --no-dotnet   להתקין ‎.NET SDK 8 ל-C# אם חסר (בלי מקלדת: רק עם 1)
#    DSVIZ_CHANNEL=mac-latest   --channel=...   ממאיזה GitHub Release להוריד (mac-test — גרסת הבדיקה)
#    DSVIZ_PAYLOAD_DIR=<dir>                    התקנה מתיקייה מקומית (dsviz-mac.json + הקבצים) — לבדיקות / בלי אינטרנט
#    DSVIZ_ECLIPSE_METHOD=auto|p2|dropins|skip  איך מתקינים ב-Eclipse (auto: Eclipse מתקין בעצמו, ואם לא הצליח — dropins)
#    DSVIZ_ECLIPSE=<Eclipse.app>                Eclipse נוסף שלא נמצא לבד
#    --update    עדכון אוטומטי (שקט, בלי שאלות, ליומן ~/Library/Logs/DSViz/update.log) — update.sh מפעיל
#    --force     להוריד ולהתקין מחדש גם אם הגרסה עדכנית
# ============================================================================================

main() {
REPO="motiml77/java-lamerhav"
APP="$HOME/Library/Application Support/DSViz"
PARENT="$HOME/Library/Application Support"
DSV="$HOME/.dsviz"
LOGDIR="$HOME/Library/Logs/DSViz"
IMPORTAFTER_MAC="$HOME/Library/Application Support/Microsoft/MSBuild/Current/Microsoft.CSharp.targets/ImportAfter"
IMPORTAFTER_OLD="$HOME/.local/share/Microsoft/MSBuild/Current/Microsoft.CSharp.targets/ImportAfter"
BUNDLE_ID="il.lamerhav.dsviz"

MODE=install
LANG_CHOICE="${DSVIZ_LANG:-}"
WANT_DOTNET="${DSVIZ_DOTNET:-}"
CHANNEL="${DSVIZ_CHANNEL:-}"
LOCAL="${DSVIZ_PAYLOAD_DIR:-}"
ECL_METHOD="${DSVIZ_ECLIPSE_METHOD:-auto}"
FORCE="${DSVIZ_FORCE:-}"
NO_DOWNLOAD=""
AFTER_ECLIPSE=""
for a in "$@"; do
    case "$a" in
        --update) MODE=update ;;
        --no-download) NO_DOWNLOAD=1 ;;
        --after-eclipse) AFTER_ECLIPSE=1 ;;
        --force) FORCE=1 ;;
        --lang=*) LANG_CHOICE="${a#*=}" ;;
        --with-dotnet) WANT_DOTNET=1 ;;
        --no-dotnet) WANT_DOTNET=0 ;;
        --channel=*) CHANNEL="${a#*=}" ;;
        --payload=*) LOCAL="${a#*=}" ;;
        -h|--help) sed -n '2,32p' "$0" 2>/dev/null || echo "https://github.com/$REPO"; return 0 ;;
        *) echo "Unknown option: $a" >&2; return 2 ;;
    esac
done

mkdir -p "$LOGDIR" 2>/dev/null
if [ "$MODE" = update ]; then
    LOG="$LOGDIR/update.log"
    exec </dev/null >>"$LOG" 2>&1
    echo "==== $(date '+%Y-%m-%d %H:%M:%S') update ===="
else
    LOG="$LOGDIR/install.log"
    : > "$LOG" 2>/dev/null || LOG=/dev/null
fi

# ---------------------------------------------------------------- הדפסה
if [ -t 1 ]; then B=$'\033[1m'; G=$'\033[32m'; Y=$'\033[33m'; R=$'\033[31m'; D=$'\033[2m'; N=$'\033[0m'; else B=; G=; Y=; R=; D=; N=; fi
STEPN=0; STEPS=8
step() { STEPN=$((STEPN + 1)); printf '\n%s[%d/%d] %s%s\n      %s%s%s\n' "$B" "$STEPN" "$STEPS" "$1" "$N" "$D" "$2" "$N"; echo "== $2" >>"$LOG"; }
say()  { printf '      %s\n' "$1"; [ -n "$2" ] && printf '      %s%s%s\n' "$D" "$2" "$N"; return 0; }
ok()   { printf '      %s✓%s %s\n' "$G" "$N" "$1"; [ -n "$2" ] && printf '        %s%s%s\n' "$D" "$2" "$N"; return 0; }
warn() { printf '      %s!%s %s\n' "$Y" "$N" "$1"; [ -n "$2" ] && printf '        %s%s%s\n' "$D" "$2" "$N"; WARNINGS=$((WARNINGS + 1)); return 0; }
die()  {
    printf '\n%s✗ %s%s\n  %s\n' "$R" "$1" "$N" "$2" >&2
    [ "$LOG" != /dev/null ] && [ -s "$LOG" ] && { printf '\n  %s(%s — last lines:)%s\n' "$D" "$LOG" "$N" >&2; tail -15 "$LOG" | sed 's/^/    /' >&2; }
    exit 1
}
WARNINGS=0

# ---------------------------------------------------------------- עזרים
sha256() { if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}'; else openssl dgst -sha256 "$1" | awk '{print $NF}'; fi; }
# ערך מ-dsviz-mac.json (מפתחות ייחודיים, ערך מחרוזת או מספר) — בלי jq (לא קיים ב-macOS 12–14)
jget() { sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" "$2" | head -1; }
# קובץ טקסט ב-~/.dsviz שהתוספים קוראים (UTF-8, בלי BOM; ב-Windows ההתקנה כותבת UTF-16 — שניהם נתמכים)
wtxt() { mkdir -p "$DSV" && printf '%s' "$2" > "$DSV/$1.tmp" && mv -f "$DSV/$1.tmp" "$DSV/$1"; }
# מריץ פקודה עם הגבלת זמן (ב-macOS אין timeout). מחזיר 124 אם נעצרה
with_timeout() {
    local secs=$1 i=0 pid; shift
    "$@" </dev/null & pid=$!
    while kill -0 "$pid" 2>/dev/null; do
        if [ "$i" -ge "$secs" ]; then kill "$pid" 2>/dev/null; sleep 2; kill -9 "$pid" 2>/dev/null; wait "$pid" 2>/dev/null; return 124; fi
        sleep 1; i=$((i + 1))
    done
    wait "$pid"
}
# שאלה בטרמינל (גם כשהסקריפט עצמו מגיע מ-curl | bash: קוראים מ-/dev/tty). בלי טרמינל — ברירת המחדל
TTY=""
if [ "$MODE" != update ] && [ -z "$DSVIZ_NONINTERACTIVE" ] && (exec </dev/tty) 2>/dev/null; then TTY=1; fi
ask() {   # ask <ברירת מחדל> <זמן בשניות>  → המשתנה ANSWER
    ANSWER="$1"
    [ -n "$TTY" ] || return 0
    local r=""
    if read -r -t "${2:-120}" r </dev/tty; then [ -n "$r" ] && ANSWER="$r"; fi
    return 0
}
# מעתיק קובץ רק אם השתנה (קובץ זמני ואז שינוי שם — לא כותבים מעל קובץ קיים באמצע)
cp_new() { cmp -s "$1" "$2" 2>/dev/null && return 0; cp "$1" "$2.dsviz-tmp" && mv -f "$2.dsviz-tmp" "$2"; }

# ---------------------------------------------------------------- בדיקות מקדימות
[ "$(uname -s)" = Darwin ] || die "הסקריפט הזה מיועד ל-Mac בלבד." "This installer is for macOS. On Windows use DSViz-Setup.exe."
[ "$(id -u)" = 0 ] && die "אין להריץ עם sudo — ההתקנה היא למשתמש שלכם, בלי הרשאות מנהל." "Do not run with sudo: DSViz installs into your own user account."
OSV=$(sw_vers -productVersion 2>/dev/null); OSMAJ=${OSV%%.*}
if [ -n "${DSVIZ_ARCH:-}" ]; then ARCH="$DSVIZ_ARCH"
elif [ "$(sysctl -n hw.optional.arm64 2>/dev/null)" = 1 ]; then ARCH=arm64   # גם כשהטרמינל רץ דרך Rosetta (אז uname -m אומר x86_64)
else ARCH=x64; fi
case "$CHANNEL" in ""|*[!a-zA-Z0-9._-]*) [ -n "$CHANNEL" ] && echo "Invalid channel '$CHANNEL' — using mac-latest"; CHANNEL="" ;; esac
if [ -z "$CHANNEL" ]; then CHANNEL=$(cat "$APP/channel.txt" 2>/dev/null); case "$CHANNEL" in mac-*) ;; *) CHANNEL=mac-latest ;; esac; fi
BASE="https://github.com/$REPO/releases/download/$CHANNEL"
if [ -n "$LOCAL" ]; then LOCAL=$(cd "$LOCAL" 2>/dev/null && pwd) || die "התיקייה DSVIZ_PAYLOAD_DIR לא קיימת" "DSVIZ_PAYLOAD_DIR not found"; fi

if [ "$MODE" != update ]; then
    printf '\n%sDSViz — מבני נתונים, הרצה ויזואלית%s   %s(Data Structures Visualizer for Mac)%s\n' "$B" "$N" "$D" "$N"
    printf '%smacOS %s · %s · %s%s\n' "$D" "$OSV" "$([ "$ARCH" = arm64 ] && echo 'Apple Silicon (arm64)' || echo 'Intel (x86_64)')" "${LOCAL:-$CHANNEL}" "$N"
fi
if [ -n "$OSMAJ" ] && [ "$OSMAJ" -lt 11 ] 2>/dev/null; then
    die "נדרש macOS 12 ומעלה (במחשב: $OSV)." "macOS 12 or newer is required (found $OSV)."
elif [ -n "$OSMAJ" ] && [ "$OSMAJ" -lt 12 ] 2>/dev/null; then
    warn "macOS $OSV ישן — ייתכן שחלק מהרכיבים לא יעבדו (נתמך: 12 ומעלה)." "macOS $OSV is older than supported (12+); continuing."
fi

TMP=$(mktemp -d "${TMPDIR:-/tmp}/dsviz.XXXXXX") || die "אין אפשרות ליצור תיקייה זמנית" "mktemp failed"
trap 'rm -rf "$TMP" "$PARENT"/.DSViz-new-$$ 2>/dev/null' EXIT

# ================================================================ 1. הורדה
getfile() {   # getfile <שם הקובץ בגרסה> <יעד>
    if [ -n "$LOCAL" ]; then
        cp "$LOCAL/$1" "$2" 2>>"$LOG"
    elif [ "$MODE" = update ] || [ ! -t 2 ] || [ "${1%.json}" != "$1" ] || [ "${1%.sh}" != "$1" ]; then
        curl -fsSL --retry 3 --retry-delay 3 --connect-timeout 20 -o "$2" "$BASE/$1" 2>>"$LOG"
    else
        curl -fL --retry 3 --retry-delay 3 --connect-timeout 20 --progress-bar -o "$2" "$BASE/$1"
    fi
}
payload_ok() {   # התקנה קיימת ותקינה?
    [ -x "$1/jdk/bin/java" ] && [ -x "$1/jdk/bin/javac" ] && [ -f "$1/dsviz.jar" ] && [ -x "$1/cs/dsviz-cs" ] && [ -f "$1/build.txt" ] \
        && "$1/jdk/bin/java" -version >/dev/null 2>&1
}

step "מוריד את DSViz (Java 21, ההרצה הוויזואלית ל-Java ול-C#, התוספים)" "Downloading DSViz (Java 21 runtime, visual runners, IDE plugins)"
L_BUILD=$(cat "$APP/build.txt" 2>/dev/null | tr -cd '0-9')
if [ -n "$NO_DOWNLOAD" ]; then
    payload_ok "$APP" || die "אין התקנה קיימת להשלים" "--no-download: no existing installation"
    ok "משתמשים בהתקנה הקיימת (גרסה $L_BUILD)" "Using the installed payload (build $L_BUILD)"
else
    JSON="$TMP/dsviz-mac.json"
    getfile dsviz-mac.json "$JSON" || die "לא הצלחתי להוריד את קובץ הגרסה. יש חיבור לאינטרנט?" "Could not download $BASE/dsviz-mac.json — check the internet connection."
    R_BUILD=$(jget build "$JSON" | tr -cd '0-9'); R_VER=$(jget version "$JSON")
    R_FILE=$(jget "${ARCH}_file" "$JSON"); R_SHA=$(jget "${ARCH}_sha256" "$JSON"); R_SIZE=$(jget "${ARCH}_size" "$JSON")
    [ -n "$R_BUILD" ] && [ -n "$R_FILE" ] && [ ${#R_SHA} = 64 ] || die "קובץ הגרסה לא תקין" "Invalid dsviz-mac.json (no entry for $ARCH)"
    case "$R_FILE" in */*|..*) die "קובץ הגרסה לא תקין" "Invalid file name in dsviz-mac.json" ;; esac
    if [ -z "$FORCE" ] && [ "$L_BUILD" = "$R_BUILD" ] && payload_ok "$APP"; then
        ok "כבר מותקנת הגרסה האחרונה ($R_VER, בנייה $R_BUILD)" "Already up to date (build $R_BUILD) — checking the rest"
    elif [ "$MODE" = update ] && [ -n "$L_BUILD" ] && [ "$L_BUILD" -ge "$R_BUILD" ] 2>/dev/null && payload_ok "$APP"; then
        ok "אין גרסה חדשה יותר" "No newer build (installed $L_BUILD, published $R_BUILD)"
    else
        [ -n "$R_SIZE" ] && say "$R_FILE ($((R_SIZE / 1048576)) MB)"
        avail=$(df -k "$HOME" 2>/dev/null | awk 'NR==2 {print $4}')
        [ -n "$avail" ] && [ "$avail" -lt 700000 ] 2>/dev/null && die "אין מספיק מקום פנוי בדיסק (צריך כ-700MB)" "Not enough free disk space (~700 MB needed)"
        getfile "$R_FILE" "$TMP/payload.tgz" || die "ההורדה נכשלה. אפשר להריץ שוב את אותה פקודה." "Download failed ($BASE/$R_FILE). Run the same command again."
        GOT=$(sha256 "$TMP/payload.tgz")
        [ "$GOT" = "$R_SHA" ] || die "הקובץ שהורד לא שלם (SHA-256 לא תואם). מריצים שוב את הפקודה." "Checksum mismatch for $R_FILE (expected $R_SHA, got $GOT). Please run again."
        ok "הורד ונבדק (SHA-256)" "Downloaded and verified"

        # פורסים לתיקייה חדשה ליד הקיימת, בודקים שהיא עובדת, ורק אז מחליפים בשינוי שם — לא כותבים מעל קבצים חתומים
        mkdir -p "$PARENT" || die "אין הרשאה לכתוב ל-$PARENT" "Cannot create $PARENT"
        NEW="$PARENT/.DSViz-new-$$"
        rm -rf "$NEW" && mkdir "$NEW" && tar -xzf "$TMP/payload.tgz" -C "$NEW" 2>>"$LOG" || die "פריסת הקובץ נכשלה" "Extracting the payload failed"
        rm -f "$TMP/payload.tgz"
        xattr -dr com.apple.quarantine "$NEW" 2>/dev/null || true
        "$NEW/jdk/bin/java" -version >>"$LOG" 2>&1 || die "ה-Java שהורדה לא רצה במחשב הזה" "The bundled Java does not start on this Mac (see $LOG)"
        [ "$("$NEW/jdk/bin/java" -XshowSettings:properties -version 2>&1 | sed -n 's/.*os.arch = //p')" = "$([ "$ARCH" = arm64 ] && echo aarch64 || echo x86_64)" ] \
            || warn "הארכיטקטורה של Java לא תואמת למחשב" "Java architecture does not match this Mac ($ARCH)"
        "$NEW/cs/dsviz-cs" --version >>"$LOG" 2>&1 || warn "ההרצה הוויזואלית של C# (dsviz-cs) לא רצה" "dsviz-cs does not start (C# visual run unavailable); see $LOG"
        for f in lang.txt eclipse.txt intellij-pending; do [ -f "$APP/$f" ] && cp "$APP/$f" "$NEW/$f"; done
        OLD="$PARENT/.DSViz-old-$$"
        if [ -d "$APP" ]; then mv "$APP" "$OLD" || die "אי אפשר להחליף את ההתקנה הקיימת" "Cannot move the old installation aside"; fi
        if ! mv "$NEW" "$APP"; then [ -d "$OLD" ] && mv "$OLD" "$APP"; die "ההחלפה נכשלה — ההתקנה הקודמת הוחזרה" "Activating the new version failed (old version restored)"; fi
        rm -rf "$OLD"
        ok "הותקן ב-$APP" "Installed build $R_BUILD into $APP"
    fi
    printf '%s' "$CHANNEL" > "$APP/channel.txt"
fi
chmod +x "$APP"/*.sh 2>/dev/null
VER=$(cat "$APP/version.txt" 2>/dev/null); BUILD=$(cat "$APP/build.txt" 2>/dev/null)
PLUGIN_JAR=$(ls "$APP"/eclipse/p2/plugins/${BUNDLE_ID}_*.jar 2>/dev/null | head -1)
PLUGIN_VER=$(basename "$PLUGIN_JAR" .jar); PLUGIN_VER=${PLUGIN_VER#${BUNDLE_ID}_}
VSIX=$(ls "$APP"/dsviz-*.vsix 2>/dev/null | head -1)

# הנתיבים שהתוספים (VS Code, Eclipse) ו-dsviz-cs קוראים
wtxt jdk-path.txt "$APP/jdk"
wtxt cs-path.txt "$APP/cs/dsviz-cs"
wtxt install-dir.txt "$APP"

# ================================================================ 2. שפה
step "באיזו שפה לומדים?" "Which language are you learning? (Java / C#)"
case "$LANG_CHOICE" in java|cs|both) ;; *) LANG_CHOICE="" ;; esac
[ -z "$LANG_CHOICE" ] && LANG_CHOICE=$(cat "$APP/lang.txt" 2>/dev/null)
case "$LANG_CHOICE" in java|cs|both) ;; *) LANG_CHOICE="" ;; esac
HAVE_DOTNET=""
for d in "$(command -v dotnet 2>/dev/null)" "$HOME/.dotnet/dotnet" /usr/local/share/dotnet/dotnet /opt/homebrew/bin/dotnet /usr/local/bin/dotnet; do
    [ -n "$d" ] && [ -x "$d" ] && { HAVE_DOTNET="$d"; break; }
done
if [ -z "$LANG_CHOICE" ]; then
    DEF=1; { [ -n "$HAVE_DOTNET" ] || ls -d /Applications/Rider*.app "$HOME"/Applications/Rider*.app >/dev/null 2>&1; } && DEF=3
    if [ -n "$TTY" ]; then
        printf '      1) Java   2) C#   3) שתיהן / both   [%s]: ' "$DEF"
        ask "$DEF" 120
    else ANSWER=$DEF; fi
    case "$ANSWER" in 2|c|C|cs|"c#"|"C#") LANG_CHOICE=cs ;; 3|b|both) LANG_CHOICE=both ;; *) LANG_CHOICE=java ;; esac
fi
printf '%s' "$LANG_CHOICE" > "$APP/lang.txt"
JAVA_ON=""; CS_ON=""
case "$LANG_CHOICE" in java) JAVA_ON=1 ;; cs) CS_ON=1 ;; both) JAVA_ON=1; CS_ON=1 ;; esac
ok "$( [ "$LANG_CHOICE" = both ] && echo 'Java ו-C#' || { [ "$LANG_CHOICE" = cs ] && echo 'C#' || echo 'Java'; } )" \
   "Language: $LANG_CHOICE  (the visual run works for both; this chooses the exercise folders and IDE extras)"

# ================================================================ 3. תיקיות התרגילים
step "תיקיות התרגילים במסמכים" "Exercise folders in Documents"
DOCS="$HOME/Documents"
# יוצר תיקייה מהתבנית, או (אם כבר יש בה עבודה) רק מעדכן את הספרייה. ב-macOS הכתיבה הראשונה למסמכים מהטרמינל
# מקפיצה "Terminal would like to access files in your Documents folder" — אם סירבו, עוברים לתיקיית הבית
project_dir() {   # project_dir <שם> <קובץ שמסמן שהתיקייה כבר קיימת> <תבנית>  → PDIR, PSTATE (existing / created / "")
    local name=$1 mark=$2 tpl=$3 base
    PSTATE=""
    for base in "$DOCS" "$HOME"; do
        PDIR="$base/$name"
        if [ -f "$PDIR/$mark" ]; then PSTATE=existing; return 0; fi
        if mkdir -p "$PDIR" 2>>"$LOG" && [ -w "$PDIR" ]; then
            cp -Rn "$tpl/." "$PDIR/" 2>>"$LOG"   # -n: קובץ שכבר קיים בתיקייה לא נדרס
            [ -f "$PDIR/$mark" ] && { PSTATE=created; return 0; }
        fi
        [ "$base" = "$DOCS" ] && warn "אין גישה לתיקיית המסמכים — התיקייה תיווצר בתיקיית הבית" \
            "No access to ~/Documents (macOS privacy prompt declined?) — using ~/$name instead"
    done
    return 1
}
if [ -n "${DSVIZ_AGENT:-}" ]; then
    say "עדכון ברקע — התיקיות במסמכים לא משתנות" "Background update (LaunchAgent): exercise folders in ~/Documents are left as they are"
    JAVA_DOCS=""; CS_DOCS=""
else
    JAVA_DOCS=$JAVA_ON; CS_DOCS=$CS_ON
fi
if [ -n "$JAVA_DOCS" ]; then
    project_dir DataStructures .project "$APP/project"
    if [ -n "$PSTATE" ]; then
        if [ "$PSTATE" = existing ]; then mkdir -p "$PDIR/lib" && cp_new "$APP/dsviz.jar" "$PDIR/lib/dsviz.jar"; ok "Java: $PDIR (קיימת — הספרייה עודכנה, העבודה שלכם לא נגעה)" "Java folder exists — library updated, your files untouched"
        else ok "Java: $PDIR" "Created the Java exercise folder"; fi
        wtxt project-path.txt "$PDIR"
    else warn "לא הצלחתי ליצור את תיקיית התרגילים של Java" "Could not create the Java exercise folder"; fi
fi
if [ -n "$CS_DOCS" ]; then
    project_dir DataStructures-CS DataStructures-CS.sln "$APP/project-cs"
    if [ -n "$PSTATE" ]; then
        if [ "$PSTATE" = existing ]; then
            mkdir -p "$PDIR/lib" && cp_new "$APP/project-cs/lib/Unit4.CollectionsLib.dll" "$PDIR/lib/Unit4.CollectionsLib.dll"
            cp_new "$APP/project-cs/Directory.Build.props" "$PDIR/Directory.Build.props"   # קובץ שלנו: Size(x) / Duplicate(x) בלי Helpers.
            ok "C#: $PDIR (קיימת — הספרייה עודכנה)" "C# folder exists — library updated, your files untouched"
        else ok "C#: $PDIR" "Created the C# exercise folder"; fi
        wtxt project-cs-path.txt "$PDIR"
    else warn "לא הצלחתי ליצור את תיקיית התרגילים של C#" "Could not create the C# exercise folder"; fi
fi

# ================================================================ 4. VS Code
step "VS Code" "VS Code extension"
CODE=""
for c in "$(command -v code 2>/dev/null)" "/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code" \
         "$HOME/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code" \
         "$HOME/Downloads/Visual Studio Code.app/Contents/Resources/app/bin/code" "$HOME/Desktop/Visual Studio Code.app/Contents/Resources/app/bin/code"; do
    [ -n "$c" ] && [ -x "$c" ] && { CODE="$c"; break; }
done
if [ -z "$CODE" ]; then
    say "VS Code לא נמצא — מדלגים. אחרי שמתקינים אותו (code.visualstudio.com) מריצים שוב את אותה פקודה." \
        "VS Code not found — skipped. Install it from https://code.visualstudio.com and run this command again."
elif [ -z "$VSIX" ]; then
    warn "התוסף ל-VS Code חסר בהתקנה" "The VS Code extension is missing from the payload"
else
    if with_timeout 180 "$CODE" --install-extension "$VSIX" --force >>"$LOG" 2>&1; then
        ok "התוסף הותקן (בסרגל הצד של VS Code: הסמל 'מבני נתונים')" "Extension installed — reopen VS Code and look for the DSViz icon in the sidebar"
    else
        warn "התקנת התוסף ל-VS Code נכשלה" "code --install-extension failed (see $LOG)"
    fi
    # התוספים של Microsoft (השלמה וצבעים ל-Java / C#) — ברקע, אחד אחרי השני; ההתקנה לא מחכה. בלי אינטרנט — מדלגים בשקט
    if [ "$MODE" != update ]; then
        EXTS=""; [ -n "$JAVA_ON" ] && EXTS="vscjava.vscode-java-pack"; [ -n "$CS_ON" ] && EXTS="$EXTS ms-dotnettools.csharp"
        if [ -n "$EXTS" ]; then
            nohup /bin/bash -c 'c=$1; shift; have=$("$c" --list-extensions 2>/dev/null | tr A-Z a-z)
                for e in "$@"; do echo "$have" | grep -qx "$e" || "$c" --install-extension "$e"; done' _ "$CODE" $EXTS \
                >>"$LOGDIR/vscode-extensions.log" 2>&1 </dev/null &
            say "התוספים של Microsoft ל-VS Code ($EXTS) יורדים ברקע" "Microsoft extensions ($EXTS) are being installed in the background"
        fi
    fi
fi
# >>> vscode-forks (נבדק גם ב-Linux: tools/test/run-tests.sh מריץ את הקטע הזה עם אפליקציות מדומות)
# VS Code Insiders / VSCodium / Cursor: אותו תוסף (vsix). רשימה ב-vscode-forks.txt — בשביל ההסרה
vscode_forks() {   # שורה לכל CLI שנמצא
    local app b
    for app in "/Applications/Visual Studio Code - Insiders.app" "$HOME/Applications/Visual Studio Code - Insiders.app" \
               /Applications/VSCodium.app "$HOME/Applications/VSCodium.app" /Applications/Cursor.app "$HOME/Applications/Cursor.app"; do
        for b in code-insiders codium cursor code; do
            [ -x "$app/Contents/Resources/app/bin/$b" ] && { echo "$app/Contents/Resources/app/bin/$b"; break; }
        done
    done
}
install_vscode_forks() {
    local c name
    : > "$APP/vscode-forks.txt"
    [ -n "$VSIX" ] || return 0
    while IFS= read -r c; do
        [ -n "$c" ] || continue
        name=${c%.app/*}; name=${name##*/}
        if with_timeout 180 "$c" --install-extension "$VSIX" --force >>"$LOG" 2>&1; then
            echo "$c" >> "$APP/vscode-forks.txt"
            ok "$name: התוסף הותקן" "Extension installed in $name"
        else
            warn "$name: התקנת התוסף נכשלה" "$c --install-extension failed (see $LOG)"
        fi
    done <<EOF
$(vscode_forks)
EOF
}
# <<< vscode-forks
install_vscode_forks

# ================================================================ 5. Eclipse
step "Eclipse" "Eclipse plugin"
ECLIPSE_PENDING=""
# כל ה-Eclipse במחשב (‎.app עם eclipse.ini), רק כאלה עם JDT (ל-Java). Homebrew: "Eclipse Java.app"; Oomph: ~/eclipse/<שם>/Eclipse.app
find_eclipses() {
    local a seen="|"
    for a in ${DSVIZ_ECLIPSE:+"$DSVIZ_ECLIPSE"} /Applications/Eclipse*.app /Applications/*/Eclipse*.app "$HOME"/Applications/Eclipse*.app \
             "$HOME"/Applications/*/Eclipse*.app "$HOME"/eclipse/*/Eclipse*.app "$HOME"/eclipse/Eclipse*.app "$HOME"/eclipse*/Eclipse*.app \
             "$HOME"/Downloads/Eclipse*.app "$HOME"/Desktop/Eclipse*.app; do
        [ -f "$a/Contents/Eclipse/eclipse.ini" ] || continue
        a=$(cd "$a" && pwd -P)
        case "$seen" in *"|$a|"*) continue ;; esac
        seen="$seen$a|"
        echo "$a"
    done
}
eclipse_running() { pgrep -f "$1/Contents/MacOS/eclipse" >/dev/null 2>&1; }
bundles_info() { echo "$1/Contents/Eclipse/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"; }
# Eclipse מתקין בעצמו (p2 director) מהמאגר הקטן שבהתקנה. מפעילים דרך open (LaunchServices), כדי ש-macOS יראה
# את Eclipse — ולא את הטרמינל — ככותב לתוך Eclipse.app (App Management, macOS 13+, חוסם כתיבה של הטרמינל לאפליקציות)
eclipse_p2() {
    local app=$1 home="$1/Contents/Eclipse" repo="$TMP/p2-repo" out="$TMP/p2-$$.log" args
    rm -rf "$repo" && cp -R "$APP/eclipse/p2" "$repo" || return 1
    args=(-nosplash -consoleLog -application org.eclipse.equinox.p2.director -repository "file:$repo" -installIU "$BUNDLE_ID/$PLUGIN_VER")
    grep -q "^$BUNDLE_ID,[^,]*,plugins/" "$(bundles_info "$app")" 2>/dev/null && args+=(-uninstallIU "$BUNDLE_ID")   # עדכון: מחליפים את הגרסה הקודמת
    : > "$out"
    if [ "${DSVIZ_ECLIPSE_DIRECT:-}" != 1 ] && open -h 2>&1 | grep -q -- '--stdout'; then
        with_timeout 300 open -W -n -g -a "$app" --stdout "$out" --stderr "$out" --args "${args[@]}"
    else
        with_timeout 300 "$app/Contents/MacOS/eclipse" "${args[@]}" >"$out" 2>&1
    fi
    cat "$out" >>"$LOG" 2>/dev/null
    grep -q "^$BUNDLE_ID,$PLUGIN_VER," "$(bundles_info "$app")" 2>/dev/null
}
eclipse_dropins() {
    local home="$1/Contents/Eclipse"
    mkdir -p "$home/dropins" 2>>"$LOG" && rm -f "$home"/dropins/${BUNDLE_ID}_*.jar 2>>"$LOG" \
        && cp "$PLUGIN_JAR" "$home/dropins/" 2>>"$LOG" || return 1
    rm -rf "$home/configuration/org.eclipse.osgi" 2>>"$LOG"   # כמו eclipse -clean — כדי שיטען את התוסף החדש
    return 0
}
record_eclipse() {   # eclipse.txt: <Eclipse.app>|<p2/dropins>|<גרסה> — בשביל ההסרה
    { grep -v "^$1|" "$APP/eclipse.txt" 2>/dev/null; echo "$1|$2|$PLUGIN_VER"; } > "$APP/eclipse.txt.tmp" && mv -f "$APP/eclipse.txt.tmp" "$APP/eclipse.txt"
}
ECL_FOUND=0
if [ -z "$JAVA_ON" ]; then
    say "דילוג (רק C#)" "Skipped (C# only)"
elif [ "$ECL_METHOD" = skip ]; then
    say "דילוג (DSVIZ_ECLIPSE_METHOD=skip)" "Skipped"
elif [ -z "$PLUGIN_JAR" ]; then
    warn "התוסף ל-Eclipse חסר בהתקנה" "The Eclipse plugin is missing from the payload"
else
    ECLS=$(find_eclipses)
    [ -z "$ECLS" ] && say "Eclipse לא נמצא — מדלגים. אחרי שמתקינים אותו מריצים שוב את אותה פקודה." \
        "No Eclipse found (looked in /Applications, ~/Applications, ~/eclipse). Install Eclipse IDE for Java Developers and run this command again."
    while IFS= read -r app; do
        [ -n "$app" ] || continue
        ECL_FOUND=$((ECL_FOUND + 1))
        name=$(basename "$app" .app)
        if ! ls "$app"/Contents/Eclipse/plugins/org.eclipse.jdt.core_* >/dev/null 2>&1; then
            say "$name: בלי Java (JDT) — מדלגים" "$app has no Java development tools — skipped"; continue
        fi
        if grep -q "^$BUNDLE_ID,$PLUGIN_VER," "$(bundles_info "$app")" 2>/dev/null; then
            ok "$name: כבר מותקנת הגרסה הזאת" "$app: plugin $PLUGIN_VER already installed"; continue
        fi
        if eclipse_running "$app"; then
            if [ "$MODE" = update ]; then
                # עדכון אוטומטי: מחכים ש-Eclipse ייסגר (אחרי סגירה — התוסף עצמו הפעיל אותנו), אחרת בפעם הבאה
                w=0; while eclipse_running "$app" && [ $w -lt "$([ -n "$AFTER_ECLIPSE" ] && echo 120 || echo 2)" ]; do sleep 5; w=$((w + 1)); done
            elif [ -n "$TTY" ]; then
                printf '      %s פתוח. סגרו אותו (Cmd+Q) ולחצו Enter — או s ואז Enter כדי לדלג: ' "$name"
                ask "" 600
                while [ "$ANSWER" != s ] && eclipse_running "$app"; do
                    printf '      עדיין פתוח (still running). Enter / s: '; ask "" 600
                done
            fi
            if eclipse_running "$app"; then
                warn "$name פתוח — התוסף יותקן בפעם הבאה (או: סגרו את Eclipse והריצו שוב את הפקודה)" "$app is running — skipped; close Eclipse and run the command again"
                ECLIPSE_PENDING=1; continue
            fi
        fi
        done_how=""
        if [ "$ECL_METHOD" != dropins ] && eclipse_p2 "$app"; then
            done_how=p2
            rm -f "$app"/Contents/Eclipse/dropins/${BUNDLE_ID}_*.jar 2>/dev/null   # עותק ישן מהתקנה קודמת (dropins) — כדי שלא יהיו שניים
        elif [ "$ECL_METHOD" != p2 ] && eclipse_dropins "$app"; then
            done_how=dropins
        fi
        if [ -n "$done_how" ]; then
            record_eclipse "$app" "$done_how"
            ok "$name: התוסף הותקן (בתפריט העליון: 'מבני נתונים')" "$app: plugin $PLUGIN_VER installed ($done_how)"
        else
            warn "$name: macOS לא נתן להתקין את התוסף" "Could not install into $app (macOS App Management / permissions)."
            say "פותרים כך: Eclipse ← Help ← Install New Software ← Add ← Archive ← בוחרים את הקובץ:" \
                "Fix: in Eclipse: Help > Install New Software > Add > Archive… and choose:"
            say "$APP/eclipse/dsviz-eclipse-site.zip"
            say "או: הגדרות המערכת ← פרטיות ואבטחה ← ניהול אפליקציות ← להפעיל את Terminal, ולהריץ שוב." \
                "Or: System Settings > Privacy & Security > App Management > enable Terminal, then run this command again."
            ECLIPSE_PENDING=1
        fi
    done <<EOF
$ECLS
EOF
fi
if [ -n "$ECLIPSE_PENDING" ]; then : > "$APP/eclipse-pending"; else rm -f "$APP/eclipse-pending"; fi

# ================================================================ 6. IntelliJ IDEA
# כל IntelliJ IDEA במחשב (Community / Ultimate, ‎/Applications ו-Toolbox, 2024.2 ומעלה) — בלי שאלות (intellij.sh).
# IntelliJ שיותקן אחר כך — העדכון היומי (update.sh) מוסיף לו את התוסף
step "IntelliJ IDEA" "IntelliJ IDEA plugin"
if [ -z "$JAVA_ON" ]; then
    say "דילוג (רק C#)" "Skipped (C# only)"
elif [ ! -d "$APP/intellij/dsviz-intellij/lib" ] || [ ! -f "$APP/intellij.sh" ]; then
    warn "התוסף ל-IntelliJ חסר בהתקנה" "The IntelliJ plugin is missing from the payload"
else
    [ -f "$APP/intellij/dsviz-intellij/runtime/dsviz.jar" ] || { mkdir -p "$APP/intellij/dsviz-intellij/runtime" && cp "$APP/dsviz.jar" "$APP/intellij/dsviz-intellij/runtime/"; }
    IJ_OUT=$(bash "$APP/intellij.sh" install "$APP/intellij/dsviz-intellij" 2>&1); IJ_RC=$?
    echo "$IJ_OUT" >>"$LOG"
    printf '%s\n' "$IJ_OUT" | while IFS= read -r l; do [ -n "$l" ] && say "$l"; done
    if [ "$IJ_RC" = 3 ]; then
        : > "$APP/intellij-pending"
        warn "IntelliJ פתוח — העדכון של התוסף יותקן אחרי שייסגר (בעדכון האוטומטי, או: סגרו את IntelliJ והריצו שוב)" "IntelliJ is running — the plugin update will be applied later"
    else
        rm -f "$APP/intellij-pending"
    fi
fi

# ================================================================ 7. C#
step "C#" "C# support (dsviz-cs, MSBuild targets, .NET SDK)"
if [ -z "$CS_ON" ]; then
    say "ההרצה הוויזואלית של C# מותקנת תמיד (גם ב-VS Code). התיקייה וההגדרות של C# — רק אם בוחרים C#." \
        "C# visual runner installed; exercise folder / MSBuild integration skipped (Java only). Re-run with DSVIZ_LANG=both to add them."
else
    # DSViz.targets: בכל פרויקט C# (dotnet build / VS Code / Rider) — ההפניה לספרייה Unit4.CollectionsLib ופרופיל "הרצה ויזואלית"
    t_ok=""
    for d in "$IMPORTAFTER_MAC" "$IMPORTAFTER_OLD"; do
        mkdir -p "$d" 2>>"$LOG" && cp_new "$APP/DSViz.targets" "$d/DSViz.targets" 2>>"$LOG" && t_ok=1
    done
    [ -n "$t_ok" ] && ok "כל פרויקט C# מכיר את Stack / Queue / Node (בלי להוסיף הפניה)" "MSBuild: DSViz.targets → $IMPORTAFTER_MAC" \
        || warn "התקנת DSViz.targets נכשלה" "Could not install DSViz.targets"
    if [ -n "$HAVE_DOTNET" ]; then
        ok ".NET SDK: $("$HAVE_DOTNET" --version 2>/dev/null | head -1)" "Found $HAVE_DOTNET"
    else
        if [ -z "$WANT_DOTNET" ] && [ -n "$TTY" ]; then
            say "ל-dotnet build / F5 ב-VS Code צריך ‎.NET SDK 8 (כ-200MB, ל-~/.dotnet, בלי סיסמה). ההרצה הוויזואלית עובדת גם בלעדיו." \
                "dotnet build / F5 need the .NET 8 SDK (~200 MB into ~/.dotnet, no admin). The visual run works without it."
            printf '      להתקין עכשיו? / Install now? [Y/n]: '
            ask y 120
            case "$ANSWER" in n|N|no|לא) WANT_DOTNET=0 ;; *) WANT_DOTNET=1 ;; esac
        fi
        if [ "$WANT_DOTNET" = 1 ] && [ "$MODE" != update ]; then
            say "מוריד ומתקין ‎.NET SDK 8..." "Installing the .NET 8 SDK into ~/.dotnet (dotnet-install.sh from Microsoft)..."
            if curl -fsSL --retry 3 -o "$TMP/dotnet-install.sh" https://dot.net/v1/dotnet-install.sh 2>>"$LOG" \
               && bash "$TMP/dotnet-install.sh" --channel 8.0 --install-dir "$HOME/.dotnet" >>"$LOG" 2>&1 </dev/null && [ -x "$HOME/.dotnet/dotnet" ]; then
                for prof in "$HOME/.zprofile" "$HOME/.bash_profile"; do
                    [ "$prof" = "$HOME/.bash_profile" ] && [ ! -f "$prof" ] && continue
                    grep -q 'DSViz: .NET' "$prof" 2>/dev/null || printf '\n# DSViz: .NET SDK (~/.dotnet)\nexport DOTNET_ROOT="$HOME/.dotnet"\nexport PATH="$PATH:$HOME/.dotnet:$HOME/.dotnet/tools"\n' >> "$prof"
                done
                ok ".NET SDK הותקן: $("$HOME/.dotnet/dotnet" --version 2>/dev/null)" "Installed into ~/.dotnet (PATH added to ~/.zprofile — open a new Terminal / restart VS Code)"
            else
                warn "התקנת ‎.NET SDK נכשלה" "The .NET SDK install failed — see $LOG, or install it from https://dotnet.microsoft.com/download"
            fi
        else
            say "‎.NET SDK לא מותקן (לא חובה להרצה הוויזואלית). להתקנה: להריץ שוב עם DSVIZ_DOTNET=1" \
                "No .NET SDK (optional). To add it: curl ... | DSVIZ_LANG=$LANG_CHOICE DSVIZ_DOTNET=1 bash"
        fi
    fi
fi

# ================================================================ 8. סיום
step "סיום" "Done"
"$APP/jdk/bin/javac" -version >>"$LOG" 2>&1 && ok "Java 21: $APP/jdk" "java + javac + jdk.jdi (line-by-line runs)" || warn "javac לא רץ" "javac failed"
[ -x "$APP/cs/dsviz-cs" ] && ok "C#: $APP/cs/dsviz-cs"
rm -f "$DSV/update-check.txt.tmp" 2>/dev/null
touch "$DSV/update-check.txt" 2>/dev/null   # העדכון האוטומטי (update.sh) יבדוק שוב רק מחר
# >>> launchagent (נבדק גם ב-Linux: tools/test/installer-test.sh)
# עדכון אוטומטי גם כשלא פותחים את VS Code / Eclipse / IntelliJ (ו-IDE שהותקן אחרי DSViz מקבל את התוסף): LaunchAgent של המשתמש,
# בלי הרשאות מנהל — update.sh --agent בכניסה למחשב ופעם ביום. כשאין מה לעשות update.sh יוצא מיד (לכל היותר בדיקה ב-20 שעות,
# קובץ JSON קטן). ב-macOS 13 ומעלה מופיעה פעם אחת ההודעה "Background Items Added" (פריט רקע: bash) — זה הוא.
LA_ID=il.lamerhav.dsviz.update
LA="$HOME/Library/LaunchAgents/$LA_ID.plist"
install_launchagent() {
    mkdir -p "$HOME/Library/LaunchAgents" 2>>"$LOG" || return 1
    cat > "$LA.dsviz-tmp" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LA_ID</string>
  <key>ProgramArguments</key>
  <array><string>/bin/bash</string><string>$APP/update.sh</string><string>--agent</string></array>
  <key>RunAtLoad</key><true/>
  <key>StartInterval</key><integer>86400</integer>
  <key>ProcessType</key><string>Background</string>
  <key>LowPriorityIO</key><true/>
  <key>Nice</key><integer>10</integer>
  <key>StandardOutPath</key><string>/dev/null</string>
  <key>StandardErrorPath</key><string>/dev/null</string>
</dict>
</plist>
PLIST
    if cmp -s "$LA.dsviz-tmp" "$LA"; then rm -f "$LA.dsviz-tmp"; else mv -f "$LA.dsviz-tmp" "$LA" || return 1; fi
    # טוענים רק אם עוד לא טעון (לא מורידים ומעלים: בעדכון, update.sh עצמו רץ מה-LaunchAgent הזה)
    if [ "${DSVIZ_NO_LAUNCHCTL:-}" != 1 ] && ! launchctl print "gui/$(id -u)/$LA_ID" >/dev/null 2>&1; then
        launchctl bootstrap "gui/$(id -u)" "$LA" >>"$LOG" 2>&1 || launchctl load -w "$LA" >>"$LOG" 2>&1
    fi
    return 0
}
# <<< launchagent
if [ -f "$APP/update.sh" ] && install_launchagent; then
    ok "עדכון אוטומטי: בכניסה למחשב ופעם ביום" "Auto-update: at login and once a day ($LA)"
else
    warn "העדכון האוטומטי ברקע לא הוגדר (העדכון ירוץ כשפותחים את VS Code / Eclipse / IntelliJ)" "LaunchAgent not installed"
fi
# אילו סביבות פיתוח יש עכשיו — update.sh משלים את התוספים כשנוספת סביבה
grep -q 'ide-signature' "$APP/update.sh" 2>/dev/null && /bin/bash "$APP/update.sh" --ide-signature > "$APP/ides.txt" 2>/dev/null
if [ "$MODE" != update ]; then
    printf '\n%sהכול מוכן!%s  פותחים מחדש את VS Code / Eclipse / IntelliJ IDEA.\n' "$G$B" "$N"
    printf '%sAll set! Restart VS Code / Eclipse / IntelliJ IDEA.%s\n' "$D" "$N"
    printf '  • VS Code: הסמל "מבני נתונים" בסרגל הצד ← "הרצה ויזואלית".\n'
    printf '  • Eclipse: התפריט "מבני נתונים" למעלה.\n'
    printf '  • IntelliJ IDEA: התפריט "מבני נתונים", החלון DSViz בצד ימין, ו"הרצה ויזואלית" ליד main.\n'
    printf '  • %sבפעם הראשונה macOS ישאל אם לתת ל-VS Code / Eclipse גישה לתיקיית המסמכים — לוחצים "אישור" (Allow).%s\n' "$D" "$N"
    printf '  • %sאם מופיעה ההודעה "Background Items Added" (פריט רקע: bash) — זה העדכון האוטומטי של DSViz.%s\n' "$D" "$N"
    printf '  • %sעדכונים: אוטומטיים (פעם ביום). הסרה: "%s/uninstall.sh"%s\n' "$D" "$APP" "$N"
    [ "$WARNINGS" -gt 0 ] && printf '  %s(%d אזהרות — הפרטים ב-%s)%s\n' "$Y" "$WARNINGS" "$LOG" "$N"
else
    echo "update finished: build $BUILD, warnings: $WARNINGS"
fi
return 0
}

main "$@"
