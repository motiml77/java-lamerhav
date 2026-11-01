# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# DSViz ל-IntelliJ IDEA ב-Windows: מתקין / מעדכן / מסיר את התוסף בכל IntelliJ IDEA שבמחשב — Community ו-Ultimate,
# התקנה רגילה ו-JetBrains Toolbox, בלי שום שאלה. מופעל מההתקנה (setup.nsi), מהעדכון האוטומטי (update.ps1, בכל כניסה ל-Windows —
# כך גם IntelliJ שהותקן אחרי DSViz מקבל את התוסף) ומההסרה.
#
#   intellij.ps1 -Source <תיקיית התוסף dsviz-intellij> [-Quiet]     התקנה / עדכון
#   intellij.ps1 -Running                                            קוד יציאה 0 אם IntelliJ פתוח עכשיו
#   intellij.ps1 -Uninstall                                          הסרה מכל IntelliJ
#
# איפה: התוסף נכנס ל-‎%APPDATA%\JetBrains\<IdeaIC2024.2 / IntelliJIdea2025.1 ...>\plugins\dsviz-intellij — התיקייה שבה IntelliJ
# שומר תוספים (גם ב-Toolbox). מוצאים את הגרסאות לפי תיקיות ההגדרות שכבר קיימות, וגם לפי IntelliJ מותקן שעוד לא נפתח
# (product-info.json → dataDirectoryName). התוסף דורש IntelliJ ‏2024.2 ומעלה — בגרסאות ישנות יותר מדלגים.
# IntelliJ פתוח: תוסף חדש אפשר להעתיק (נטען בפתיחה הבאה); עדכון של תוסף קיים — רק כש-IntelliJ סגור (הקבצים נעולים).
param([string]$Source, [switch]$Quiet, [switch]$Running, [switch]$Uninstall, [string]$ConfigRoot, [string[]]$InstallRoots)
$ErrorActionPreference = 'Continue'
$PluginDir = 'dsviz-intellij'
$MinYear = 2024; $MinMinor = 2
if (-not $ConfigRoot) { $ConfigRoot = Join-Path $env:APPDATA 'JetBrains' }

function Say([string]$m) { if (-not $Quiet) { Write-Output $m } }

function IdeaRunning { return [bool](Get-Process -Name idea64, idea -ErrorAction SilentlyContinue) }
if ($Running) { if (IdeaRunning) { exit 0 } else { exit 1 } }

# IdeaIC2024.2 / IntelliJIdea2025.1 / IntelliJIdea2025.3 → @(2024, 2) — או $null אם זה לא IntelliJ IDEA
function IdeaVersion([string]$name) {
    if ($name -match '^(IdeaIC|IntelliJIdea|IdeaIE|IntelliJIdeaCE)(\d{4})\.(\d+)$') { return @([int]$Matches[2], [int]$Matches[3]) }
    return $null
}
function Supported($v) { return $v -and ($v[0] -gt $MinYear -or ($v[0] -eq $MinYear -and $v[1] -ge $MinMinor)) }

# כל תיקיות ההגדרות של IntelliJ IDEA: קיימות, ושל IntelliJ מותקן שעוד לא נפתח
function ConfigDirs {
    $names = @{}
    if (Test-Path -LiteralPath $ConfigRoot) {
        Get-ChildItem -LiteralPath $ConfigRoot -Directory -ErrorAction SilentlyContinue | ForEach-Object { $names[$_.Name] = 1 }
    }
    $roots = if ($InstallRoots) { $InstallRoots } else {
        @("$env:ProgramFiles\JetBrains", "${env:ProgramFiles(x86)}\JetBrains", "$env:LOCALAPPDATA\Programs", "$env:LOCALAPPDATA\JetBrains\Toolbox\apps")
    }
    foreach ($r in $roots) {
        if (-not $r -or -not (Test-Path -LiteralPath $r)) { continue }
        Get-ChildItem -LiteralPath $r -Recurse -Depth 5 -Filter product-info.json -ErrorAction SilentlyContinue | ForEach-Object {
            try {
                $j = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
                if ($j.dataDirectoryName) { $names[[string]$j.dataDirectoryName] = 1 }
            } catch { }
        }
    }
    $out = @()
    foreach ($n in $names.Keys) {
        $v = IdeaVersion $n
        if (-not $v) { continue }
        $out += [pscustomobject]@{ Name = $n; Dir = (Join-Path $ConfigRoot $n); Ok = (Supported $v) }
    }
    return $out | Sort-Object Name
}

function SameTree([string]$a, [string]$b) {
    if (-not (Test-Path -LiteralPath $b)) { return $false }
    $fa = Get-ChildItem -LiteralPath $a -Recurse -File | ForEach-Object { $_.FullName.Substring($a.Length) + '|' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash } | Sort-Object
    $fb = Get-ChildItem -LiteralPath $b -Recurse -File | ForEach-Object { $_.FullName.Substring($b.Length) + '|' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash } | Sort-Object
    return (($fa -join "`n") -eq ($fb -join "`n"))
}

if ($Uninstall) {
    $n = 0
    foreach ($c in (ConfigDirs)) {
        $p = Join-Path (Join-Path $c.Dir 'plugins') $PluginDir
        if (Test-Path -LiteralPath $p) {
            Remove-Item -LiteralPath $p -Recurse -Force -ErrorAction SilentlyContinue
            if (Test-Path -LiteralPath $p) { Say "IntelliJ: לא הצלחתי להסיר מ-$($c.Name) (IntelliJ פתוח?)" } else { Say "IntelliJ: הוסר מ-$($c.Name)"; $n++ }
        }
    }
    Say "IntelliJ: הוסר מ-$n גרסאות"
    exit 0
}

if (-not $Source -or -not (Test-Path -LiteralPath (Join-Path $Source 'lib'))) { Say "IntelliJ: התוסף חסר בהתקנה ($Source)"; exit 2 }
$Source = (Resolve-Path -LiteralPath $Source).Path.TrimEnd('\')
$dirs = @(ConfigDirs)
if ($dirs.Count -eq 0) { Say 'IntelliJ IDEA: לא נמצא — דילוג (אחרי שמתקינים אותו, התוסף נוסף לבד בכניסה הבאה ל-Windows)'; exit 0 }
$installed = 0; $pending = 0
foreach ($c in $dirs) {
    if (-not $c.Ok) { Say "IntelliJ IDEA $($c.Name): גרסה ישנה (התוסף דורש 2024.2 ומעלה) — דילוג"; continue }
    $plugins = Join-Path $c.Dir 'plugins'
    $dest = Join-Path $plugins $PluginDir
    if (SameTree $Source $dest) { Say "IntelliJ IDEA $($c.Name): כבר מותקנת הגרסה הזאת"; $installed++; continue }
    $fresh = -not (Test-Path -LiteralPath $dest)
    if (-not $fresh -and (IdeaRunning)) { Say "IntelliJ IDEA $($c.Name): פתוח — העדכון יותקן כשייסגר"; $pending++; continue }
    try {
        New-Item -ItemType Directory -Force -Path $plugins | Out-Null
        $tmp = Join-Path $plugins ".$PluginDir.new"
        Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
        Copy-Item -LiteralPath $Source -Destination $tmp -Recurse -Force -ErrorAction Stop
        if (Test-Path -LiteralPath $dest) { Remove-Item -LiteralPath $dest -Recurse -Force -ErrorAction Stop }
        Rename-Item -LiteralPath $tmp -NewName $PluginDir -ErrorAction Stop
        Say "IntelliJ IDEA $($c.Name): התוסף הותקן ($dest)"
        $installed++
    } catch {
        Say "IntelliJ IDEA $($c.Name): ההתקנה נכשלה — $($_.Exception.Message)"
        $pending++
    }
}
Say "IntelliJ IDEA: הותקן ב-$installed גרסאות$(if ($pending) { ", $pending ממתינות" })"
if ($pending) { exit 3 } else { exit 0 }
