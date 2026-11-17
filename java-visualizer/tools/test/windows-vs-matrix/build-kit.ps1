# (UTF-8 עם BOM) מרכיב "ערכת התקנה" של חבילת ה-C# של DSViz מהמקור, על מחשב ה-CI של Windows — בלי קבצים בנויים מראש שנשמרו ב-repo:
#   <Kit>\cs\           dsviz-cs.exe (dotnet publish, כולל ‎.NET‎), קובץ ההרחבה (‎-Vsix‎: התיקייה שבה build-vsix.ps1 שם אותו) ו-install-vsix.ps1
#   <Kit>\cs-project\   תבנית תיקיית התרגילים (csharp/project) עם הספרייה שנבנתה עכשיו
#   <Kit>\dsviz.jar, <Kit>\bank\dsviz-bank-vs.jar   (נבנו לפני כן ב-Git Bash: tools/build-lib.sh, tools/build-bank-vs.sh)
#   <Kit>\DSViz.targets
# זה מה ש-DSViz-Setup.exe שם במחשב בחלק של C# (SEC_CS ב-installer/setup.nsi) — install-kit.ps1 מניח את הקבצים באותם מקומות.
# את DSViz-Setup.exe עצמו אי אפשר לבנות על Windows (tools/build-all.sh — Linux), ולכן הוא נבדק ב-workflows האחרים (dsviz-public, dsviz-vs2026-real).
param([Parameter(Mandatory = $true)][string]$Vsix, [Parameter(Mandatory = $true)][string]$Kit)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
if (Test-Path $Kit) { Remove-Item $Kit -Recurse -Force }
New-Item -ItemType Directory -Force $Kit | Out-Null
$Kit = (Resolve-Path $Kit).Path
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'; $env:DOTNET_NOLOGO = '1'
function Run([string]$what, [scriptblock]$cmd) {
    Write-Host "== $what"
    & $cmd
    if ($LASTEXITCODE -ne 0) { throw "$what נכשל (קוד $LASTEXITCODE)" }
}
Run 'הספרייה Unit4.CollectionsLib.dll' { dotnet build "$root\csharp\Unit4.CollectionsLib\Unit4.CollectionsLib.csproj" -c Release -o "$Kit\cs-lib" -v q --nologo }
Run 'dsviz-cs.exe (self-contained, win-x64)' { dotnet publish "$root\csharp\DsViz.Runner\DsViz.Runner.csproj" -c Release -r win-x64 --self-contained -p:DebugType=none -o "$Kit\cs" -v q --nologo }
Copy-Item (Join-Path $Vsix 'DsViz.VsExtension.vsix') (Join-Path $Kit 'cs\') -Force
Copy-Item "$root\installer\install-vsix.ps1" "$Kit\cs\" -Force
# תבנית תיקיית התרגילים: כמו build-all.sh (בלי bin / obj, והספרייה מהבנייה)
$proj = Join-Path $Kit 'cs-project'
New-Item -ItemType Directory -Force $proj | Out-Null
& robocopy "$root\csharp\project" $proj /E /XD bin obj lib /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy נכשל (קוד $LASTEXITCODE)" }
$global:LASTEXITCODE = 0
New-Item -ItemType Directory -Force (Join-Path $proj 'lib') | Out-Null
Copy-Item "$Kit\cs-lib\Unit4.CollectionsLib.dll" (Join-Path $proj 'lib\') -Force
Copy-Item "$root\installer\DSViz.targets" $Kit -Force
foreach ($pair in @(@("$root\release\dsviz.jar", "$Kit\dsviz.jar"), @("$root\build\lab-vs\dsviz-bank-vs.jar", "$Kit\bank\dsviz-bank-vs.jar"))) {
    if (-not (Test-Path $pair[0])) { throw "חסר $($pair[0]) — קודם בונים: sh tools/build-lib.sh; SKIP_BANK_CHECK=1 sh tools/build-bank-vs.sh" }
    New-Item -ItemType Directory -Force (Split-Path $pair[1]) | Out-Null
    Copy-Item $pair[0] $pair[1] -Force
}
$build = [DateTime]::UtcNow.ToString('yyyyMMddHHmm')
[IO.File]::WriteAllText((Join-Path $Kit 'build.txt'), $build)
Write-Host "הערכה מוכנה: $Kit (build $build)"
Get-ChildItem $Kit | ForEach-Object { Write-Host ("  {0}" -f $_.Name) }
Get-ChildItem (Join-Path $Kit 'cs') -Filter '*.vsix' | ForEach-Object { Write-Host ("  cs\{0}: {1:N0} בתים" -f $_.Name, $_.Length) }
foreach ($need in 'cs\dsviz-cs.exe', 'cs\Unit4.CollectionsLib.dll', 'cs\DsViz.VsExtension.vsix', 'cs\install-vsix.ps1', 'cs-project\DataStructures-CS.sln', 'cs-project\lib\Unit4.CollectionsLib.dll', 'cs-project\Directory.Build.props', 'dsviz.jar', 'bank\dsviz-bank-vs.jar', 'DSViz.targets') {
    if (-not (Test-Path (Join-Path $Kit $need))) { throw "חסר בערכה: $need" }
}
exit 0
