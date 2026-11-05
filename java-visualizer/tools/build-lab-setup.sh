#!/bin/sh
# גרסת בדיקה (רק למנהל — לא נוגע ב-release/ ולא בקישור הקבוע):
#   build/lab-setup/DSViz-Lab-Setup.exe    — DSViz המלא + מאגר התרגילים עם מסך הכניסה (Eclipse, IntelliJ, Visual Studio)
#   build/lab-setup/DSViz-Admin-Setup.exe  — "ניהול DSViz": קיצור למסוף ההנהלה בחלון נפרד
# קודם: tools/build-all.sh, tools/build-bank.sh, tools/build-bank-ij.sh (בונה גם את build-bank-vs.sh). צריך makensis.
set -e
cd "$(dirname "$0")/.."
P=build/lab-setup/parts
rm -rf build/lab-setup && mkdir -p "$P"
cp release/DSViz-Setup.exe build/lab/il.lamerhav.dsviz.bank_*.jar build/lab-vs/dsviz-bank-vs.jar build/lab-ij/dsviz-bank-intellij-*.zip "$P/"
cp vs-bank/install/install-bank-vs.ps1 installer/lab/
BUILDNO=$(date -u +%Y%m%d%H%M)
(cd installer/lab && makensis -V2 -DBUILDNO=$BUILDNO lab.nsi && makensis -V2 admin.nsi)
rm -f installer/lab/install-bank-vs.ps1
ls -la build/lab-setup/*.exe
