#!/bin/sh
# הלוגיקה של installer/install-vsix.ps1 בלי Windows ובלי Visual Studio: PowerShell (pwsh) על Linux, עם vswhere ו-VSIXInstaller מדומים
# (tools/test/helpers/install-vsix-sim.ps1). בודק את מה שאי אפשר לנסות שוב ושוב ב-CI של Windows: מי לא מתאים (Build Tools, SSMS, 2017),
# עדכון במקום כשמספר הגרסה עלה, הסרה-ואז-התקנה כשהגרסה זהה או כשהעדכון במקום נדחה, -Check / -Missing, והשורות ביומן. בלי pwsh — מדלגים.
set -e
cd "$(dirname "$0")"
PWSH=$(command -v pwsh || true)
[ -n "$PWSH" ] || [ ! -x "$HOME/.dotnet/tools/pwsh" ] || PWSH="$HOME/.dotnet/tools/pwsh"
if [ -z "$PWSH" ]; then echo "pwsh לא מותקן — מדלגים על בדיקת install-vsix.ps1"; exit 0; fi
"$PWSH" -NoLogo -NoProfile -File helpers/install-vsix-sim.ps1 -Script ../../installer/install-vsix.ps1 -Work ../../build/test-install-vsix
