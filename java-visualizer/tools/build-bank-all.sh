#!/bin/sh
# "מאגר התרגילים" לכל הסביבות, בתיקייה אחת שההתקנה (installer/setup.nsi) לוקחת ממנה: build/bank-parts/
#   il.lamerhav.dsviz.bank_*.jar      — Eclipse (build-bank.sh)
#   dsviz-bank-intellij-*.zip         — IntelliJ IDEA (build-bank-ij.sh)
#   dsviz-bank-vs.jar                 — Visual Studio / C# (build-bank-vs.sh, נבנה בתוך build-bank-ij.sh)
# רץ מ-tools/build-all.sh אחרי שהתוספים הראשיים נבנו (release/). כל שאלה במאגר נבדקת בדרך (Java וגם C#).
set -e
cd "$(dirname "$0")/.."
sh tools/build-bank.sh
sh tools/build-bank-ij.sh
P=build/bank-parts
rm -rf "$P" && mkdir -p "$P"
cp build/lab/il.lamerhav.dsviz.bank_*.jar build/lab-vs/dsviz-bank-vs.jar build/lab-ij/dsviz-bank-intellij-*.zip "$P/"
ls -la "$P"
