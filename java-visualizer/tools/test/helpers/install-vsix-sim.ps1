# (UTF-8 עם BOM) הדמיה של installer/install-vsix.ps1 בלי Windows: רץ ב-pwsh (גם על Linux) מ-tools/test/install-vsix-test.sh.
# vswhere מדומה (קובץ JSON של מופעים), ו-VSIXInstaller מדומה (Start-Process מוחלף בפונקציה שמתנהגת כמוהו: בודק שהגרסה של המופע בטווח
# של המניפסט, מסרב לאותה גרסה, מעדכן במקום גרסה נמוכה יותר — או מסרב, כדי לבדוק את המסלול החלופי). אין כאן Visual Studio אמיתי:
# נבדקת הלוגיקה של הסקריפט (לאיזה מופע מתקינים, מתי מסירים, מה נכתב ביומן). ההתקנה האמיתית נבדקת ב-CI של Windows (dsviz-vs-matrix.yml).
param([Parameter(Mandatory = $true)][string]$Script, [Parameter(Mandatory = $true)][string]$Work)
$ErrorActionPreference = 'Stop'
$Script = (Resolve-Path $Script).Path
if (Test-Path $Work) { Remove-Item $Work -Recurse -Force }
New-Item -ItemType Directory -Force $Work | Out-Null
$Work = (Resolve-Path $Work).Path
Add-Type -AssemblyName System.IO.Compression.FileSystem
$script:fails = 0
function Check([string]$what, [bool]$ok, [string]$why = '') {
    if ($ok) { Write-Host "  ok   $what" } else { $script:fails++; Write-Host ("  FAIL $what" + $(if ($why) { " — $why" } else { '' })) }
}
$sep = [IO.Path]::DirectorySeparatorChar
$id = 'DsViz.VisualStudio.Toolbar.6f3c1c0e-4a52-4e7e-9d1b-5b2f0c8e7a11'
$env:LOCALAPPDATA = Join-Path $Work 'local'
$env:TEMP = Join-Path $Work 'temp'
$pf = Join-Path $Work 'pf86'
[Environment]::SetEnvironmentVariable('ProgramFiles(x86)', $pf)
New-Item -ItemType Directory -Force (Join-Path $env:LOCALAPPDATA 'DSViz'), $env:TEMP, (Join-Path $pf 'Microsoft Visual Studio/Installer') | Out-Null

# ------------------------------------------------------------------ המופעים (כמו vswhere -all -products * -format json)
function Inst([string]$name, [string]$ver, [string]$product, [string]$iid, [bool]$withInstaller = $true) {
    $path = Join-Path $Work "vs/$iid"
    New-Item -ItemType Directory -Force (Join-Path $path 'Common7/IDE') | Out-Null
    if ($withInstaller) { Set-Content (Join-Path $path 'Common7/IDE/VSIXInstaller.exe') 'fake' }
    return [ordered]@{ instanceId = $iid; installationPath = $path; installationVersion = $ver; displayName = $name; productId = "Microsoft.VisualStudio.Product.$product"; catalog = @{ productDisplayVersion = $ver } }
}
$all = @(
    (Inst 'Visual Studio Professional 2022' '17.9.2' 'Professional' 'aaaa17'),
    (Inst 'Visual Studio Enterprise 2022' '17.14.5' 'Enterprise' 'bbbb17'),
    (Inst 'Visual Studio Community 2026' '18.0.1' 'Community' 'cccc18'),
    (Inst 'Visual Studio Build Tools 2022' '17.14.5' 'BuildTools' 'dddd17'),
    (Inst 'SQL Server Management Studio 21' '21.0.1' 'Ssms' 'eeee21'),
    (Inst 'Visual Studio Community 2017' '15.9.60' 'Community' 'ffff15')
)
$json = Join-Path $Work 'instances.json'
($all | ConvertTo-Json -Depth 5) | Set-Content $json -Encoding utf8
$vswhere = Join-Path $pf 'Microsoft Visual Studio/Installer/vswhere.exe'
if ($IsWindows) { Set-Content ($vswhere -replace '\.exe$', '.cmd') "@type `"$json`""; throw 'ההדמיה נכתבה ל-Linux / Mac (vswhere מדומה כסקריפט)' }
Set-Content $vswhere "#!/bin/sh`ncat '$json'`n"
& chmod +x $vswhere

# ------------------------------------------------------------------ קובצי VSIX מדומים
function MakeVsix([string]$file, [string]$version, [string]$range, [string]$dll) {
    $stage = Join-Path $Work ('stage-' + [guid]::NewGuid().ToString('N').Substring(0, 6))
    New-Item -ItemType Directory -Force $stage | Out-Null
    $m = "<PackageManifest><Metadata><Identity Id=`"$id`" Version=`"$version`" Language=`"en-US`" Publisher=`"x`" /></Metadata><Installation><InstallationTarget Id=`"Microsoft.VisualStudio.Community`" Version=`"$range`" /></Installation></PackageManifest>"
    [IO.File]::WriteAllText((Join-Path $stage 'extension.vsixmanifest'), $m)
    [IO.File]::WriteAllText((Join-Path $stage 'DsViz.VsExtension.dll'), $dll)
    if (Test-Path $file) { Remove-Item $file -Force }
    [IO.Compression.ZipFile]::CreateFromDirectory($stage, $file)
    Remove-Item $stage -Recurse -Force
}
$cs = Join-Path $env:LOCALAPPDATA 'DSViz/cs'
New-Item -ItemType Directory -Force $cs | Out-Null
$main = Join-Path $cs 'DsViz.VsExtension.vsix'

# ------------------------------------------------------------------ VSIXInstaller מדומה
$global:simCalls = New-Object System.Collections.Generic.List[string]
$global:simNoUpgrade = $false
function ExtRoot($inst) { return (Join-Path $env:LOCALAPPDATA ("Microsoft/VisualStudio/{0}.0_{1}/Extensions" -f ([version]$inst.installationVersion).Major, $inst.instanceId)) }
function ExtDirs($inst) {
    $r = ExtRoot $inst
    if (-not (Test-Path $r)) { return @() }
    return @(Get-ChildItem $r -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'extension.vsixmanifest') } | ForEach-Object { $_.FullName })
}
function DllOf($inst) { $d = @(ExtDirs $inst); if ($d.Count -eq 0) { return '' }; return (($d | ForEach-Object { [IO.File]::ReadAllText((Join-Path $_ 'DsViz.VsExtension.dll')) }) -join '+') }
function InRange([int]$major, [string]$range) {
    if ($range -notmatch '^\[(\d+)\.\d+,\s*(?:(\d+)\.\d+)?\)$') { return $false }
    if ($major -lt [int]$Matches[1]) { return $false }
    if ($Matches[2] -and $major -ge [int]$Matches[2]) { return $false }
    return $true
}
function FakeInstaller([string]$exe, [string[]]$a) {
    $iid = ($a | Where-Object { $_ -like '/instanceIds:*' }) -replace '^/instanceIds:', ''
    $inst = $all | Where-Object { $_.instanceId -eq $iid } | Select-Object -First 1
    $log = Join-Path $env:TEMP ("dd_VSIXInstaller_{0:yyyyMMddHHmmssfff}.log" -f (Get-Date))
    $un = $a | Where-Object { $_ -like '/uninstall:*' }
    if ($un) {
        $global:simCalls.Add("uninstall $iid")
        foreach ($d in (ExtDirs $inst)) { Remove-Item $d -Recurse -Force }
        Set-Content $log "uninstall $iid"
        return 0
    }
    $file = ($a | Where-Object { $_ -notlike '/*' } | Select-Object -First 1).Trim('"')
    $global:simCalls.Add("install $iid $(Split-Path -Leaf $file)")
    $z = [IO.Compression.ZipFile]::OpenRead($file)
    try {
        $rd = New-Object IO.StreamReader(($z.Entries | Where-Object { $_.FullName -eq 'extension.vsixmanifest' }).Open())
        $m = $rd.ReadToEnd(); $rd.Close()
    } finally { $z.Dispose() }
    $ver = [regex]::Match($m, 'Identity[^>]*Version="([^"]+)"').Groups[1].Value
    $range = [regex]::Match($m, 'InstallationTarget[^>]*Version="([^"]+)"').Groups[1].Value
    if (-not (InRange ([version]$inst.installationVersion).Major $range)) { Set-Content $log "error: This extension is not compatible with $($inst.displayName) ($range)"; return 2003 }
    foreach ($d in (ExtDirs $inst)) {
        $old = [regex]::Match([IO.File]::ReadAllText((Join-Path $d 'extension.vsixmanifest')), 'Identity[^>]*Version="([^"]+)"').Groups[1].Value
        if ($old -eq $ver) { Set-Content $log 'error: This extension is already installed to all applicable products.'; return 1001 }
        if ($global:simNoUpgrade -or ([version]$old -gt [version]$ver)) { Set-Content $log "error: a newer version is already installed ($old)"; return 1002 }
        Remove-Item $d -Recurse -Force   # עדכון במקום
    }
    $dest = Join-Path (ExtRoot $inst) ([guid]::NewGuid().ToString('N').Substring(0, 8))
    New-Item -ItemType Directory -Force $dest | Out-Null
    [IO.Compression.ZipFile]::ExtractToDirectory($file, $dest)
    Set-Content $log "installed $ver to $dest"
    return 0
}
function global:Start-Process {
    param([string]$FilePath, $ArgumentList, [switch]$PassThru, $WindowStyle, [switch]$Wait)
    $code = 0
    if ($FilePath -like '*VSIXInstaller.exe') { $code = FakeInstaller $FilePath @($ArgumentList) }
    $p = New-Object psobject -Property @{ ExitCode = $code }
    $p | Add-Member -MemberType ScriptMethod -Name WaitForExit -Value { param($ms) return $true }
    $p | Add-Member -MemberType ScriptMethod -Name Kill -Value { }
    return $p
}
function Run([hashtable]$named) {
    $global:simCalls.Clear()
    $global:LASTEXITCODE = 0
    $out = & $Script @named 6>$null
    $script:lastExit = $global:LASTEXITCODE
    return ($out | Out-String)
}
function LogText() { $f = Join-Path $env:LOCALAPPDATA 'DSViz/vsix-install.log'; if (Test-Path $f) { return (Get-Content $f -Raw -Encoding utf8) } else { return '' } }
$vs179 = $all[0]; $vs22 = $all[1]; $vs26 = $all[2]; $bt = $all[3]; $ssms = $all[4]; $vs2017 = $all[5]

Write-Host '== 1. התקנה ראשונה: בכל Visual Studio 2022 / 2026 (גם 17.9), לא ב-Build Tools / SSMS / 2017'
MakeVsix $main '1.0.26320.10' '[17.0,)' 'DLL-a'
$o = Run @{ Vsix = $main }
Check 'Visual Studio 2022 (17.9 ו-17.14) ו-2026 קיבלו את ההרחבה' (((DllOf $vs179) -eq 'DLL-a') -and ((DllOf $vs22) -eq 'DLL-a') -and ((DllOf $vs26) -eq 'DLL-a')) ((DllOf $vs179) + ' ' + (DllOf $vs22) + ' ' + (DllOf $vs26))
Check 'Build Tools / SSMS / Visual Studio 2017: לא נגעו' (((DllOf $bt) + (DllOf $ssms) + (DllOf $vs2017)) -eq '') ''
Check 'בלי הסרה לפני התקנה ראשונה, התקנה אחת לכל מופע' (($global:simCalls -join ';') -eq 'install aaaa17 DsViz.VsExtension.vsix;install bbbb17 DsViz.VsExtension.vsix;install cccc18 DsViz.VsExtension.vsix') ($global:simCalls -join ';')
$log = LogText
Check 'ביומן: שורה לכל מופע מתאים, בפורמט ש-DSViz-Diagnose קורא' (@([regex]::Matches($log, '(?m)^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\s+.*התקנה \(ניסיון 1\): קוד 0 · הותקן ב-')).Count -eq 3) $log
Check 'ביומן: "לא מתאים" ל-Build Tools, SSMS ו-2017, עם סוג המוצר' (($log -match 'Build Tools 2022 17\.14\.5 \[BuildTools\] — לא מתאים') -and ($log -match '\[Ssms\] — לא מתאים') -and ($log -match 'Community 2017 15\.9\.60 \[Community\] — לא מתאים')) $log
Check 'ביומן: שם המופע, סוג המוצר ומספר הגרסה' ($log -match 'Professional 2022 17\.9\.2 \[Professional\] — התקנה \(ניסיון 1\): קוד 0 · הותקן ב-.* · גרסה 1\.0\.26320\.10') $log
Check 'היומן המלא של VSIXInstaller נשמר לכל מופע (logs\vsix-<מופע>.log)' ((Test-Path (Join-Path $env:LOCALAPPDATA 'DSViz/logs/vsix-aaaa17.log')) -and (Test-Path (Join-Path $env:LOCALAPPDATA 'DSViz/logs/vsix-cccc18.log'))) ''
Check 'קוד יציאה 0' ($script:lastExit -eq 0) "$script:lastExit"

Write-Host '== 2. -Check ו--Missing'
$null = Run @{ Vsix = $main; Check = $true }
Check '-Check אחרי התקנה: 0 (הכול מעודכן)' ($script:lastExit -eq 0) "$script:lastExit"
Check '-Check לא קורא ל-VSIXInstaller' ($global:simCalls.Count -eq 0) ($global:simCalls -join ';')
foreach ($d in (ExtDirs $vs26)) { Remove-Item $d -Recurse -Force }
$null = Run @{ Vsix = $main; Check = $true }
Check '-Check כשחסר במופע אחד: 10' ($script:lastExit -eq 10) "$script:lastExit"
$null = Run @{ Vsix = $main; Missing = $true }
Check '-Missing: מתקין רק במופע שחסר בו' ((($global:simCalls -join ';') -eq 'install cccc18 DsViz.VsExtension.vsix') -and ((DllOf $vs26) -eq 'DLL-a')) ($global:simCalls -join ';')
$null = Run @{ Vsix = $main; Missing = $true }
Check '-Missing שוב: שום קריאה' ($global:simCalls.Count -eq 0) ($global:simCalls -join ';')

Write-Host '== 3. עדכון: מספר הגרסה עלה — התקנה מעל הקיימת, בלי הסרה'
MakeVsix $main '1.0.26321.5' '[17.0,)' 'DLL-b'
$null = Run @{ Vsix = $main; Check = $true }
Check '-Check כשבהתקנה גרסה חדשה: 10' ($script:lastExit -eq 10) "$script:lastExit"
$null = Run @{ Vsix = $main }
Check 'עדכון במקום: רק install, בלי uninstall' (-not (($global:simCalls -join ';') -match 'uninstall')) ($global:simCalls -join ';')
Check 'כל המופעים עם הגרסה החדשה' (((DllOf $vs179) -eq 'DLL-b') -and ((DllOf $vs22) -eq 'DLL-b') -and ((DllOf $vs26) -eq 'DLL-b')) ((DllOf $vs179) + ' ' + (DllOf $vs22) + ' ' + (DllOf $vs26))
Check 'ביומן: "במקום" הגרסה הקודמת' ((LogText) -match 'גרסה 1\.0\.26321\.5 \(במקום 1\.0\.26320\.10\)') ''

Write-Host '== 4. אותו מספר גרסה, תוכן אחר: הסרה ואז התקנה'
MakeVsix $main '1.0.26321.5' '[17.0,)' 'DLL-c'
$null = Run @{ Vsix = $main }
Check '2022: uninstall ואז install' (($global:simCalls -join ';') -match 'uninstall bbbb17;install bbbb17 DsViz\.VsExtension\.vsix') ($global:simCalls -join ';')
Check 'כל המופעים עם התוכן החדש' (((DllOf $vs179) -eq 'DLL-c') -and ((DllOf $vs22) -eq 'DLL-c') -and ((DllOf $vs26) -eq 'DLL-c')) ''
Check 'ביומן: הסרת הגרסה הקודמת (אותו מספר)' ((LogText) -match 'הסרת הגרסה הקודמת \(1\.0\.26321\.5\)') ''

Write-Host '== 5. VSIXInstaller מסרב לעדכן במקום: הסרה וניסיון שני'
MakeVsix $main '1.0.26322.1' '[17.0,)' 'DLL-d'
$global:simNoUpgrade = $true
$null = Run @{ Vsix = $main }
$global:simNoUpgrade = $false
Check '2022: install (נכשל) ← uninstall ← install' (($global:simCalls -join ';') -match 'install bbbb17 DsViz\.VsExtension\.vsix;uninstall bbbb17;install bbbb17 DsViz\.VsExtension\.vsix') ($global:simCalls -join ';')
Check 'בסוף כל המופעים עם הגרסה החדשה' (((DllOf $vs179) -eq 'DLL-d') -and ((DllOf $vs22) -eq 'DLL-d') -and ((DllOf $vs26) -eq 'DLL-d')) ((DllOf $vs179) + ' ' + (DllOf $vs22) + ' ' + (DllOf $vs26))
Check 'ביומן: "העדכון במקום לא הצליח" ושורת VSIXInstaller' (((LogText) -match 'העדכון במקום לא הצליח') -and ((LogText) -match 'VSIXInstaller: error: a newer version')) ''

Write-Host '== 6. הסרה'
$null = Run @{ Uninstall = $true }
Check 'הסרה מכל המופעים המתאימים' ((($global:simCalls -join ';') -eq 'uninstall aaaa17;uninstall bbbb17;uninstall cccc18') -and (((DllOf $vs179) + (DllOf $vs22) + (DllOf $vs26)) -eq '')) ($global:simCalls -join ';')

Write-Host ''
Write-Host ("install-vsix.ps1 (הדמיה): {0}" -f $(if ($script:fails) { "$script:fails בדיקות נכשלו" } else { 'כל הבדיקות עברו' }))
exit $script:fails
