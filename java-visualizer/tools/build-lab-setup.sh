#!/bin/sh
# קיצורי דרך למנהל (לא נוגעים ב-release/ ולא בקישור הקבוע):
#   build/lab-setup/DSViz-Lab-Setup.exe    — עטיפה דקה: מריצה את DSViz-Setup.exe (ההתקנה הציבורית — כוללת היום את מאגר התרגילים והכניסה)
#   build/lab-setup/DSViz-Admin-Setup.exe  — "ניהול DSViz": קיצור למסוף ההנהלה בחלון נפרד
# קודם: tools/build-all.sh (שבונה גם את מאגר התרגילים). צריך makensis.
set -e
cd "$(dirname "$0")/.."
P=build/lab-setup/parts
rm -rf build/lab-setup && mkdir -p "$P"
cp release/DSViz-Setup.exe "$P/"
BUILDNO=$(date -u +%Y%m%d%H%M)
(cd installer/lab && makensis -V2 -DBUILDNO=$BUILDNO lab.nsi && makensis -V2 admin.nsi)
ls -la build/lab-setup/*.exe
