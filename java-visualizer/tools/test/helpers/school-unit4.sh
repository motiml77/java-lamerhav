#!/usr/bin/env bash
# מוריד את Unit4.dll האמיתי — הספרייה שבתי הספר מחלקים (תיקיית Unit4CSharp ב-Google Drive של משרד החינוך / המפקחים) — ובודק אותו מול sha256.
# הקובץ לא נשמר במאגר (הוא לא שלנו); נשמר ב-build/school-unit4 ומשמש את הבדיקות. אפשר לתת נתיב מוכן ב-DSVIZ_SCHOOL_UNIT4.
# מדפיס את הנתיב; קוד יציאה 1 אם אי אפשר להשיג את הקובץ הנכון.
#   source school-unit4.sh; SCHOOL_DLL=$(school_unit4 "$ROOT/build")
SCHOOL_UNIT4_SHA256=9f47124fc1f7f7a1204ec083636933023ceda142d23734e76e146a33c96cf4b9
SCHOOL_UNIT4_URL='https://drive.usercontent.google.com/download?id=1r24UUvcESdtZ14be8ud3sEu4IEYsqnQF&export=download&confirm=t'
school_unit4() {
    local f="${DSVIZ_SCHOOL_UNIT4:-$1/school-unit4/Unit4.dll}"
    if [ ! -f "$f" ] || ! echo "$SCHOOL_UNIT4_SHA256  $f" | sha256sum -c --status; then
        [ -n "$DSVIZ_SCHOOL_UNIT4" ] && { echo "DSVIZ_SCHOOL_UNIT4=$f — לא הקובץ הנכון (sha256)" >&2; return 1; }
        mkdir -p "$(dirname "$f")"
        curl -fsSL --retry 4 --retry-delay 5 -o "$f.tmp" "$SCHOOL_UNIT4_URL" || { echo "ההורדה של Unit4.dll נכשלה" >&2; return 1; }
        echo "$SCHOOL_UNIT4_SHA256  $f.tmp" | sha256sum -c --status || { echo "Unit4.dll שהורד שונה מהצפוי (sha256 $(sha256sum < "$f.tmp" | cut -c1-16)…)" >&2; rm -f "$f.tmp"; return 1; }
        mv "$f.tmp" "$f"
    fi
    echo "$f"
}

# הגרסה שהמורים משתמשים בה בפועל (קיבלנו מהמשתמש, 17.11.2026): קטנה יותר — בלי StackInt, CardLib, HanoiLib, OrganLib; ב-Grid
# ‏FlipX / FlipY / FlipCellX / FlipCellY ובלי Delay. האוספים, BinTreeUtils והפלט של TreeCanvas — זהים לגרסה שלמעלה (נבדק בפירוק הקוד).
# אין לה עדיין קישור להורדה: נותנים נתיב ב-DSVIZ_SCHOOL_UNIT4_TEACHERS (או קישור ב-SCHOOL_UNIT4_TEACHERS_URL). בלי — מודפס "לא נבדק".
SCHOOL_UNIT4_TEACHERS_SHA256=1dcf50f0d25fa8ab7fbe0ea168840c2904f10b27c00c7226652f0b6b1dcad57a
SCHOOL_UNIT4_TEACHERS_URL="${SCHOOL_UNIT4_TEACHERS_URL:-}"
school_unit4_teachers() {
    local f="${DSVIZ_SCHOOL_UNIT4_TEACHERS:-$1/school-unit4-teachers/Unit4.dll}"
    if [ ! -f "$f" ] || ! echo "$SCHOOL_UNIT4_TEACHERS_SHA256  $f" | sha256sum -c --status; then
        [ -n "$DSVIZ_SCHOOL_UNIT4_TEACHERS" ] && { echo "DSVIZ_SCHOOL_UNIT4_TEACHERS=$f — לא הקובץ הנכון (sha256)" >&2; return 1; }
        [ -n "$SCHOOL_UNIT4_TEACHERS_URL" ] || return 1
        mkdir -p "$(dirname "$f")"
        curl -fsSL --retry 4 --retry-delay 5 -o "$f.tmp" "$SCHOOL_UNIT4_TEACHERS_URL" || { echo "ההורדה של Unit4.dll (גרסת המורים) נכשלה" >&2; return 1; }
        echo "$SCHOOL_UNIT4_TEACHERS_SHA256  $f.tmp" | sha256sum -c --status || { echo "Unit4.dll (גרסת המורים) שהורד שונה מהצפוי" >&2; rm -f "$f.tmp"; return 1; }
        mv "$f.tmp" "$f"
    fi
    echo "$f"
}
