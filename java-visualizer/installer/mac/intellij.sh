#!/bin/bash
# DSViz ל-IntelliJ IDEA ב-Mac: מתקין / מעדכן / מסיר את התוסף בכל IntelliJ IDEA שבמחשב — Community ו-Ultimate,
# התקנה רגילה (/Applications) ו-JetBrains Toolbox, בלי שום שאלה. מופעל מ-install.sh, מ-update.sh (העדכון היומי — כך גם IntelliJ
# שהותקן אחרי DSViz מקבל את התוסף) ומ-uninstall.sh.
#
#   intellij.sh install <תיקיית התוסף dsviz-intellij>     התקנה / עדכון. קוד יציאה 3 = IntelliJ פתוח והעדכון ממתין
#   intellij.sh uninstall                                  הסרה מכל IntelliJ
#   intellij.sh running                                    קוד יציאה 0 אם IntelliJ פתוח
#   intellij.sh list                                       הגרסאות שנמצאו: <שם>|<תיקייה>|ok/old
#
# איפה: ~/Library/Application Support/JetBrains/<IdeaIC2024.2 / IntelliJIdea2025.1 ...>/plugins/dsviz-intellij — התיקייה שבה IntelliJ
# שומר תוספים (גם ב-Toolbox). הגרסאות: לפי תיקיות ההגדרות שכבר קיימות, וגם לפי IntelliJ מותקן שעוד לא נפתח
# (Contents/Resources/product-info.json → dataDirectoryName). התוסף דורש IntelliJ 2024.2 ומעלה — גרסאות ישנות מדלגים.
# משתני סביבה (לבדיקות): DSVIZ_JB_CONFIG (במקום ~/Library/Application Support/JetBrains), DSVIZ_JB_APPS (תיקיות עם ‎.app)
PLUGIN_DIR=dsviz-intellij
CONFIG_ROOT="${DSVIZ_JB_CONFIG:-$HOME/Library/Application Support/JetBrains}"

ij_version() {   # IdeaIC2024.2 → "2024 2" ; לא IntelliJ IDEA → כלום
    case "$1" in IdeaIC20[0-9][0-9].*|IntelliJIdea20[0-9][0-9].*|IdeaIE20[0-9][0-9].*) ;; *) return 1 ;; esac
    local v=${1#IdeaIC}; v=${v#IntelliJIdea}; v=${v#IdeaIE}
    case "$v" in [0-9][0-9][0-9][0-9].[0-9]|[0-9][0-9][0-9][0-9].[0-9][0-9]) echo "${v%%.*} ${v#*.}" ;; *) return 1 ;; esac
}

ij_supported() { local y=$1 m=$2; [ "$y" -gt 2024 ] || { [ "$y" -eq 2024 ] && [ "$m" -ge 2 ]; }; }

ij_names() {   # כל שמות תיקיות ההגדרות: קיימות + של IntelliJ מותקן
    {
        [ -d "$CONFIG_ROOT" ] && ls -1 "$CONFIG_ROOT" 2>/dev/null
        local apps="${DSVIZ_JB_APPS:-/Applications:$HOME/Applications:$HOME/Library/Application Support/JetBrains/Toolbox/apps}" d f
        local IFS=:
        for d in $apps; do
            [ -d "$d" ] || continue
            find "$d" -maxdepth 6 -path '*IntelliJ*IDEA*.app/Contents/Resources/product-info.json' 2>/dev/null | while IFS= read -r f; do
                sed -n 's/.*"dataDirectoryName"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$f" | head -1
            done
        done
    } | sort -u
}

ij_list() {
    local n v
    while IFS= read -r n; do
        [ -n "$n" ] || continue
        v=$(ij_version "$n") || continue
        if ij_supported $v; then echo "$n|$CONFIG_ROOT/$n|ok"; else echo "$n|$CONFIG_ROOT/$n|old"; fi
    done <<EOF
$(ij_names)
EOF
}

ij_running() { pgrep -f 'IntelliJ IDEA.*\.app/Contents/MacOS/idea' >/dev/null 2>&1 || pgrep -x idea >/dev/null 2>&1; }

ij_install() {   # ij_install <מקור>
    local src=$1 found=0 pending=0 n dir ok dest tmp
    [ -d "$src/lib" ] || { echo "IntelliJ: התוסף חסר בהתקנה ($src)"; return 2; }
    while IFS='|' read -r n dir ok; do
        [ -n "$n" ] || continue
        found=$((found + 1))
        if [ "$ok" != ok ]; then echo "IntelliJ IDEA $n: גרסה ישנה (התוסף דורש 2024.2 ומעלה) — דילוג"; continue; fi
        dest="$dir/plugins/$PLUGIN_DIR"
        if [ -d "$dest" ] && diff -rq "$src" "$dest" >/dev/null 2>&1; then echo "IntelliJ IDEA $n: כבר מותקנת הגרסה הזאת"; continue; fi
        # עדכון של תוסף קיים כש-IntelliJ פתוח: IntelliJ דורס/נועל — מחכים לפעם הבאה. תוסף חדש — אפשר (נטען בפתיחה הבאה)
        if [ -d "$dest" ] && ij_running; then echo "IntelliJ IDEA $n: פתוח — העדכון יותקן בפעם הבאה"; pending=1; continue; fi
        mkdir -p "$dir/plugins" || { pending=1; continue; }
        tmp="$dir/plugins/.$PLUGIN_DIR.new"
        rm -rf "$tmp" && cp -R "$src" "$tmp" && rm -rf "$dest" && mv "$tmp" "$dest" \
            && echo "IntelliJ IDEA $n: התוסף הותקן ($dest)" || { echo "IntelliJ IDEA $n: ההתקנה נכשלה"; pending=1; }
    done <<EOF
$(ij_list)
EOF
    [ "$found" -eq 0 ] && echo "IntelliJ IDEA: לא נמצא — דילוג (אחרי שמתקינים אותו — העדכון היומי מוסיף את התוסף, או מריצים שוב את פקודת ההתקנה)"
    [ "$pending" -eq 0 ] || return 3
    return 0
}

ij_uninstall() {
    local d n=0
    for d in "$CONFIG_ROOT"/IdeaIC* "$CONFIG_ROOT"/IntelliJIdea* "$CONFIG_ROOT"/IdeaIE*; do
        [ -d "$d/plugins/$PLUGIN_DIR" ] || continue
        rm -rf "$d/plugins/$PLUGIN_DIR" && echo "IntelliJ IDEA $(basename "$d"): התוסף הוסר" && n=$((n + 1))
    done
    echo "IntelliJ IDEA: הוסר מ-$n גרסאות"
}

# כשמריצים את הקובץ (ולא טוענים אותו עם source)
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
    case "$1" in
        install) ij_install "$2"; exit $? ;;
        uninstall) ij_uninstall ;;
        running) ij_running; exit $? ;;
        list) ij_list ;;
        *) echo "usage: intellij.sh install <dir> | uninstall | running | list"; exit 2 ;;
    esac
fi
