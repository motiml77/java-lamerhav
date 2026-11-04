# "מאגר תרגילים" ל-Visual Studio (C#) — גרסת בדיקה: מעתיק את dsviz-bank-vs.jar ליד ההתקנה של DSViz (%LOCALAPPDATA%\DSViz\lab)
# ויוצר שני קיצורים בשולחן העבודה: "מאגר תרגילים C#" ו"השאלה — מאגר תרגילים C#". עם -Remove — מוחק את שניהם ואת ה-jar.
# לא נוגע בהתקנה של DSViz; העדכון האוטומטי לא מוחק את הקבצים האלה.
param([switch]$Remove)
$ErrorActionPreference = 'Stop'
$dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'
$lab = Join-Path $dsviz 'lab'
$jar = Join-Path $lab 'dsviz-bank-vs.jar'
$desk = [Environment]::GetFolderPath('Desktop')
$docs = [Environment]::GetFolderPath('MyDocuments')
$csDir = Join-Path $docs 'DataStructures-CS'
$links = @{ 'מאגר תרגילים C#' = 'bank'; 'השאלה — מאגר תרגילים C#' = 'question' }
if ($Remove) {
    foreach ($n in $links.Keys) { Remove-Item (Join-Path $desk "$n.lnk") -Force -ErrorAction SilentlyContinue }
    Remove-Item $lab -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host 'הוסר.'
    exit 0
}
$javaw = Join-Path $dsviz 'jdk\bin\javaw.exe'
if (-not (Test-Path $javaw) -or -not (Test-Path (Join-Path $dsviz 'dsviz.jar'))) { Write-Host "DSViz לא מותקן ($dsviz) — מתקינים קודם את DSViz-Setup.exe עם חבילת C#."; exit 1 }
if (-not (Test-Path $csDir)) { Write-Host "אין תיקיית תרגילים של C# ($csDir) — מתקינים את DSViz-Setup.exe עם חבילת C#."; exit 1 }
New-Item -ItemType Directory -Force $lab | Out-Null
Copy-Item (Join-Path $PSScriptRoot 'dsviz-bank-vs.jar') $jar -Force
$ws = New-Object -ComObject WScript.Shell
foreach ($n in $links.Keys) {
    # WScript.Shell לא שומר קיצור עם שם בעברית — שומרים בשם זמני באנגלית ומשנים את השם
    $tmp = Join-Path $desk "dsviz-bank-$($links[$n]).lnk"
    $final = Join-Path $desk "$n.lnk"
    $s = $ws.CreateShortcut($tmp)
    $s.TargetPath = $javaw
    # התוספת קודם: יש בה גרסה חדשה של חלקים משותפים (CsSolution — Visual Studio פתוח מוסיף את הפרויקט בעצמו), גם על התקנה ישנה
    $s.Arguments = "-cp `"$jar;$(Join-Path $dsviz 'dsviz.jar')`" il.lamerhav.dsviz.BankVs $($links[$n]) `"$csDir`""
    $s.WorkingDirectory = $dsviz
    $ico = Join-Path $dsviz 'icon.ico'
    if (Test-Path $ico) { $s.IconLocation = $ico }
    $s.Save()
    Remove-Item $final -Force -ErrorAction SilentlyContinue
    Move-Item $tmp $final -Force
}
Write-Host 'הותקן: בשולחן העבודה יש "מאגר תרגילים C#" ו"השאלה — מאגר תרגילים C#".'
