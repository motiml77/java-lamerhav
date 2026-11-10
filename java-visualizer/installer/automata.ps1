# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# "מודלים חישוביים" (AutomataLauncher, javaw ברקע) — להתקנה, להסרה ולעדכון האוטומטי:
#   -Running  קוד יציאה 0 אם התוכנה פתוחה (חלון / שרת), 1 אם לא
#   -Stop     סוגר אותה (לפני החלפת הקבצים בהתקנה / בהסרה). העבודה של הדף נשמרת בדפדפן (localStorage), לא בתוכנה.
param([switch]$Running, [switch]$Stop)
$ErrorActionPreference = 'SilentlyContinue'
function Launchers {
    @(Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" | Where-Object { $_.CommandLine -like '*il.lamerhav.dsviz.AutomataLauncher*' })
}
if ($Running) { if ((Launchers).Count -gt 0) { exit 0 } else { exit 1 } }
if ($Stop) {
    foreach ($p in Launchers) { Stop-Process -Id $p.ProcessId -Force }
    # החלון של Edge (פרופיל משלו, automata-data\edge) — בלי השרת הוא כבר לא עובד
    @(Get-CimInstance Win32_Process -Filter "Name='msedge.exe'" | Where-Object { $_.CommandLine -like '*DSViz\automata-data\edge*' }) | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    Start-Sleep -Milliseconds 500
    exit 0
}
