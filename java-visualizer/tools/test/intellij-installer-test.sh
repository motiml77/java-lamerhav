#!/bin/bash
# בדיקה של ההתקנה האוטומטית ל-IntelliJ IDEA (installer/mac/intellij.sh — אותה לוגיקה כמו installer/intellij.ps1 של Windows),
# בתיקיות מדומות — בלי Mac ובלי IntelliJ: מוצא כל IntelliJ IDEA (Community / Ultimate, תיקיית הגדרות קיימת או IntelliJ מותקן
# שעוד לא נפתח), מדלג על גרסה ישנה ועל מוצרים אחרים של JetBrains, לא מעתיק שוב גרסה זהה, ומסיר מכולם.
# שימוש: intellij-installer-test.sh <dsviz-intellij-<גרסה>.zip>
set -e
ZIP=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
S="$(cd "$(dirname "$0")/../../installer/mac" && pwd)/intellij.sh"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
mkdir -p "$T/cfg/IdeaIC2024.3" "$T/cfg/IntelliJIdea2023.1" "$T/cfg/PyCharm2024.2" "$T/apps/IntelliJ IDEA CE.app/Contents/Resources" "$T/src"
echo '{"name": "IntelliJ IDEA", "dataDirectoryName": "IntelliJIdea2025.2"}' > "$T/apps/IntelliJ IDEA CE.app/Contents/Resources/product-info.json"
(cd "$T/src" && unzip -q "$ZIP")
export DSVIZ_JB_CONFIG="$T/cfg" DSVIZ_JB_APPS="$T/apps"
fail() { echo "נכשל: $1"; bash "$S" list; find "$T/cfg" -maxdepth 3 | sort; exit 1; }

L=$(bash "$S" list)
grep -q '^IdeaIC2024.3|.*|ok$' <<<"$L" || fail "IdeaIC2024.3 לא נמצא"
grep -q '^IntelliJIdea2025.2|.*|ok$' <<<"$L" || fail "IntelliJ מותקן שעוד לא נפתח (product-info.json) לא נמצא"
grep -q '^IntelliJIdea2023.1|.*|old$' <<<"$L" || fail "גרסה ישנה לא סומנה"
grep -q PyCharm <<<"$L" && fail "PyCharm נכלל"
bash "$S" install "$T/src/dsviz-intellij" >/dev/null || fail "ההתקנה נכשלה"
for v in IdeaIC2024.3 IntelliJIdea2025.2; do
    [ -f "$T/cfg/$v/plugins/dsviz-intellij/runtime/dsviz.jar" ] && ls "$T/cfg/$v/plugins/dsviz-intellij/lib/"*.jar >/dev/null 2>&1 || fail "לא הותקן ב-$v"
done
[ -d "$T/cfg/IntelliJIdea2023.1/plugins/dsviz-intellij" ] && fail "הותקן בגרסה ישנה"
[ -d "$T/cfg/PyCharm2024.2/plugins" ] && fail "הותקן ב-PyCharm"
bash "$S" install "$T/src/dsviz-intellij" | grep -q "כבר מותקנת" || fail "התקנה שנייה העתיקה שוב"
# IntelliJ שהותקן אחרי DSViz — ההרצה הבאה (העדכון היומי) מוסיפה גם אותו
mkdir -p "$T/cfg/IdeaIC2026.2"
bash "$S" install "$T/src/dsviz-intellij" >/dev/null && [ -d "$T/cfg/IdeaIC2026.2/plugins/dsviz-intellij/lib" ] || fail "IntelliJ חדש לא קיבל את התוסף"
bash "$S" uninstall >/dev/null
[ -z "$(find "$T/cfg" -name dsviz-intellij)" ] || fail "ההסרה לא מחקה הכול"
echo "IntelliJ: ההתקנה מוצאת את כל הגרסאות (2024.2+, גם שעוד לא נפתחו), מדלגת על ישנות ועל מוצרים אחרים, ומסירה מכולן"
