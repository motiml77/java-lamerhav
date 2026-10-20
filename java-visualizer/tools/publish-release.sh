#!/bin/sh
# מעלה את קובצי ההתקנה ל-GitHub Release, כך שהקישור הקבוע
#   https://github.com/motiml77/java-lamerhav/releases/latest/download/DSViz-Setup.exe
# תמיד מוביל לגרסה האחרונה (הכתובת לא משתנה, אפשר לקצר אותה פעם אחת ולהשאיר).
# שימוש: tools/publish-release.sh [תג]   (ברירת מחדל: v<גרסה>)   — דורש gh מחובר (gh auth login)
# קודם בונים: tools/build-all.sh
set -e
cd "$(dirname "$0")/.."
REPO=motiml77/java-lamerhav
VERSION=$(sed -n 's/^VERSION=//p' tools/build-all.sh | head -1)
TAG=${1:-v$VERSION}
FILES="release/DSViz-Setup.exe release/dsviz-install.zip release/dsviz-$VERSION.vsix release/il.lamerhav.dsviz_$VERSION.jar release/dsviz.jar"
for f in $FILES; do [ -f "$f" ] || { echo "חסר $f — להריץ קודם tools/build-all.sh"; exit 1; }; done

if gh release view "$TAG" -R "$REPO" >/dev/null 2>&1; then
    echo "משדרג את ההוצאה הקיימת $TAG"
    gh release upload "$TAG" $FILES -R "$REPO" --clobber
else
    gh release create "$TAG" $FILES -R "$REPO" --latest --title "מבני נתונים — הרצה ויזואלית $VERSION" \
        --notes "תוכנית ההתקנה ל-Windows: DSViz-Setup.exe (לחיצה כפולה → הבא → סיום). ל-Mac/Linux: dsviz-install.zip."
fi
echo "הקישור הקבוע: https://github.com/$REPO/releases/latest/download/DSViz-Setup.exe"
