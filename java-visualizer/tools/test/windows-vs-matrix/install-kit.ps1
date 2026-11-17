# (UTF-8 עם BOM) מתקין את הערכה של build-kit.ps1 כמו ש-DSViz-Setup.exe מתקין את חבילת ה-C# (SEC_CS ב-installer/setup.nsi), למשתמש הנוכחי:
#   %LOCALAPPDATA%\DSViz\cs, cs-project, dsviz.jar, bank + lab (המאגר), jdk (ה-Java של מחשב הבדיקה — במקום ה-Java המקוצרת שבהתקנה),
#   ‎~\.dsviz\cs-path.txt‎ ו-project-cs-path.txt, DSViz.targets בתיקיית ImportAfter של MSBuild, תיקיית התרגילים Documents\DataStructures-CS,
#   ובסוף install-vsix.ps1 — באותה שורת פקודה בדיוק שההתקנה מריצה (powershell.exe -NoProfile -ExecutionPolicy Bypass -File ...).
# בלי update.ps1: העדכון האוטומטי לא חלק מהבדיקה (הוא היה מוריד את ההתקנה הציבורית ומחליף את מה שנבדק).
# מדפיס את הפלט של install-vsix.ps1; קוד היציאה שלו הוא קוד היציאה כאן.
param([Parameter(Mandatory = $true)][string]$Kit, [switch]$SkipVsix)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$Kit = (Resolve-Path $Kit).Path
$inst = Join-Path $env:LOCALAPPDATA 'DSViz'
$docs = [Environment]::GetFolderPath('MyDocuments')
$csDir = Join-Path $docs 'DataStructures-CS'
New-Item -ItemType Directory -Force $inst | Out-Null
foreach ($d in 'cs', 'cs-project') {
    $t = Join-Path $inst $d
    if (Test-Path $t) { Remove-Item $t -Recurse -Force }
    & robocopy (Join-Path $Kit $d) $t /E /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy $d נכשל (קוד $LASTEXITCODE)" }
}
$global:LASTEXITCODE = 0
Copy-Item (Join-Path $Kit 'dsviz.jar') $inst -Force
Copy-Item (Join-Path $Kit 'build.txt') $inst -Force
Copy-Item (Join-Path $Kit 'DSViz-Diagnose.bat') $inst -Force   # כמו setup.nsi
[IO.File]::WriteAllText((Join-Path $inst 'lang.txt'), 'both')
foreach ($d in 'bank', 'lab') {
    New-Item -ItemType Directory -Force (Join-Path $inst $d) | Out-Null
    Copy-Item (Join-Path $Kit 'bank\dsviz-bank-vs.jar') (Join-Path $inst "$d\") -Force
}
# Java: ההתקנה שמה Java 21 מקוצרת ב-jdk\; כאן — צומת (junction) ל-Java של מחשב הבדיקה
$jdk = Join-Path $inst 'jdk'
if (-not (Test-Path (Join-Path $jdk 'bin\javaw.exe'))) {
    if (Test-Path $jdk) { & cmd.exe /c rmdir "$jdk" | Out-Null }
    if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\javaw.exe'))) { throw 'אין JAVA_HOME עם javaw.exe (actions/setup-java)' }
    & cmd.exe /c mklink /J "$jdk" "$env:JAVA_HOME" | Out-Null
}
$global:LASTEXITCODE = 0
$dot = Join-Path $env:USERPROFILE '.dsviz'
New-Item -ItemType Directory -Force $dot | Out-Null
[IO.File]::WriteAllText((Join-Path $dot 'cs-path.txt'), (Join-Path $inst 'cs\dsviz-cs.exe'), [Text.Encoding]::Unicode)
[IO.File]::WriteAllText((Join-Path $dot 'project-cs-path.txt'), $csDir, [Text.Encoding]::Unicode)
$after = Join-Path $env:LOCALAPPDATA 'Microsoft\MSBuild\Current\Microsoft.CSharp.targets\ImportAfter'
New-Item -ItemType Directory -Force $after | Out-Null
Copy-Item (Join-Path $Kit 'DSViz.targets') $after -Force
if (-not (Test-Path (Join-Path $csDir 'DataStructures-CS.sln'))) {
    & robocopy (Join-Path $inst 'cs-project') $csDir /E /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy DataStructures-CS נכשל (קוד $LASTEXITCODE)" }
    Write-Host "נוצרה תיקיית התרגילים: $csDir"
} else {
    New-Item -ItemType Directory -Force (Join-Path $csDir 'lib') | Out-Null
    Copy-Item (Join-Path $inst 'cs-project\lib\Unit4.CollectionsLib.dll') (Join-Path $csDir 'lib\') -Force
    Copy-Item (Join-Path $inst 'cs-project\Directory.Build.props') $csDir -Force
    Write-Host 'תיקיית התרגילים קיימת — הספרייה ו-Directory.Build.props עודכנו'
}
$global:LASTEXITCODE = 0
Write-Host "DSViz (חבילת C#) הותקן ב-$inst · build $((Get-Content (Join-Path $inst 'build.txt') -Raw).Trim())"
if ($SkipVsix) { exit 0 }
# בדיוק כמו setup.nsi: nsExec של powershell.exe עם install-vsix.ps1 והקובץ הראשי
$ps = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$ErrorActionPreference = 'Continue'
$o = & $ps -NoProfile -ExecutionPolicy Bypass -File (Join-Path $inst 'cs\install-vsix.ps1') (Join-Path $inst 'cs\DsViz.VsExtension.vsix') 2>&1
$code = $LASTEXITCODE
$o | ForEach-Object { Write-Host "install-vsix: $_" }
exit $code
