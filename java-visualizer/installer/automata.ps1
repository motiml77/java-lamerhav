# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# "מודלים חישוביים" (AutomataLauncher, javaw ברקע) — להתקנה, להסרה ולעדכון האוטומטי:
#   -Running  קוד יציאה 0 אם התוכנה פתוחה (חלון / שרת), 1 אם לא
#   -Stop     סוגר אותה (לפני החלפת הקבצים בהתקנה / בהסרה). העבודה של הדף נשמרת בדפדפן (localStorage), לא בתוכנה.
#   -JavaPath <תיקיית jdk>  מדפיס את javaw.exe שהקיצור צריך: בתיקייה עם עברית (שם משתמש) — דרך צומת (junction) באנגלית
#             ב-%ProgramData%\DSViz-jdk-<hash> (אותו שם כמו SafeJava ב-dsviz-cs.exe), כי javaw לא עולה מנתיב שלא בקידוד של
#             Windows — גם לא מהשם הקצר (8.3: ה-JVM טוען את net.dll מהנתיב הארוך). בתיקייה באנגלית / אם אי אפשר — "-".
param([switch]$Running, [switch]$Stop, [string]$JavaPath)
$ErrorActionPreference = 'SilentlyContinue'
function Launchers {
    @(Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" | Where-Object { $_.CommandLine -like '*il.lamerhav.dsviz.AutomataLauncher*' })
}
if ($JavaPath) {
    $jdk = $JavaPath.TrimEnd('\')
    if ($jdk -notmatch '[^\x00-\x7F]') { Write-Output '-'; exit 0 }
    $sha = [Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($jdk.ToLowerInvariant()))
    $hash = (($sha | ForEach-Object { $_.ToString('X2') }) -join '').Substring(0, 10)
    $link = Join-Path $env:ProgramData "DSViz-jdk-$hash"
    $exe = Join-Path $link 'bin\javaw.exe'
    if (-not (Test-Path -LiteralPath $exe)) {
        if (Test-Path -LiteralPath $link) { cmd /c rmdir "$link" 2>&1 | Out-Null }   # צומת ישן ששבור (rmdir — בלי למחוק את מה שבפנים)
        cmd /c mklink /J "$link" "$jdk" 2>&1 | Out-Null
    }
    if ((Test-Path -LiteralPath $exe) -and $exe -notmatch '[^\x00-\x7F]') { Write-Output $exe } else { Write-Output '-' }
    exit 0
}
if ($Running) { if ((Launchers).Count -gt 0) { exit 0 } else { exit 1 } }
if ($Stop) {
    foreach ($p in Launchers) { Stop-Process -Id $p.ProcessId -Force }
    # החלון של Edge (פרופיל משלו, automata-data\edge) — בלי השרת הוא כבר לא עובד
    @(Get-CimInstance Win32_Process -Filter "Name='msedge.exe'" | Where-Object { $_.CommandLine -like '*DSViz\automata-data\edge*' }) | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    Start-Sleep -Milliseconds 500
    exit 0
}
