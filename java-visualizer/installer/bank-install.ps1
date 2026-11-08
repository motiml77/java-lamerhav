# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# "מאגר התרגילים" עם מסך הכניסה — חלק מההתקנה של DSViz (וגם מהעדכון האוטומטי): מופעל מ-setup.nsi אחרי שהתוספים הראשיים הותקנו.
#   Eclipse        — il.lamerhav.dsviz.bank_*.jar לתיקיית dropins של כל Eclipse שקיבל את DSViz (eclipse-dirs.txt)
#   IntelliJ IDEA  — התוסף dsviz-bank-intellij לכל IntelliJ שיש בו את DSViz (גם IntelliJ שהותקן אחר כך: update.ps1 מפעיל עם -Only intellij)
#   Visual Studio  — dsviz-bank-vs.jar (‎%LOCALAPPDATA%\DSViz\lab) + שני קיצורים בשולחן העבודה (install-bank-vs.ps1)
# אפשר להריץ שוב ושוב: מעתיק רק מה שהשתנה, ולא מכפיל (שם הקובץ קבוע; עותק ישן מוחלף). אותה התקנה מחליפה גם התקנה של גרסת הבדיקה (DSViz-Lab).
#   bank-install.ps1 [-NoVs] [-Only intellij] [-Quiet]     התקנה / עדכון
#   bank-install.ps1 -Remove                                הסרה (DSViz עצמו נשאר)
param([switch]$Remove, [switch]$NoVs, [string]$Only = 'all', [switch]$Quiet, [string]$ConfigRoot)
$ErrorActionPreference = 'Continue'
$here = $PSScriptRoot
$dsviz = Split-Path -Parent $here
if (-not $ConfigRoot) { $ConfigRoot = Join-Path $env:APPDATA 'JetBrains' }
function Say([string]$m) { if (-not $Quiet) { Write-Output $m } }
function IdeaRunning { return [bool](Get-Process -Name idea64, idea -ErrorAction SilentlyContinue) }
function TreeHash([string]$d) {
    if (-not (Test-Path -LiteralPath $d)) { return '' }
    $d = (Resolve-Path -LiteralPath $d).Path.TrimEnd('\')
    return ((Get-ChildItem -LiteralPath $d -Recurse -File | ForEach-Object { $_.FullName.Substring($d.Length) + '|' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash } | Sort-Object) -join "`n")
}
function EclipseDirs {
    $f = Join-Path $dsviz 'eclipse-dirs.txt'
    if (-not (Test-Path -LiteralPath $f)) { return @() }
    return @(Get-Content -LiteralPath $f -Encoding Unicode | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'eclipse.exe')) } | Select-Object -Unique)
}
# תיקיות plugins של IntelliJ: להתקנה — אלה שיש בהן את DSViz; להסרה — אלה שיש בהן את המאגר
function IdeaPluginDirs([string]$has) {
    if (-not (Test-Path -LiteralPath $ConfigRoot)) { return @() }
    return @(Get-ChildItem -LiteralPath $ConfigRoot -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'plugins' } |
        Where-Object { Test-Path -LiteralPath (Join-Path $_ $has) })
}

if ($Remove) {
    foreach ($e in EclipseDirs) {
        Get-ChildItem -LiteralPath (Join-Path $e 'dropins') -Filter 'il.lamerhav.dsviz.bank_*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
        Say "Eclipse: המאגר הוסר מ-$e"
    }
    foreach ($p in IdeaPluginDirs 'dsviz-bank-intellij') {
        Remove-Item -LiteralPath (Join-Path $p 'dsviz-bank-intellij') -Recurse -Force -ErrorAction SilentlyContinue
        Say "IntelliJ: המאגר הוסר מ-$p"
    }
    $vs = Join-Path $here 'install-bank-vs.ps1'
    if (Test-Path -LiteralPath $vs) { try { & $vs -Remove | Out-Null } catch { } }
    exit 0
}

if ($Only -ne 'intellij') {
    $jar = Get-ChildItem -LiteralPath $here -Filter 'il.lamerhav.dsviz.bank_*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jar) {
        foreach ($e in EclipseDirs) {
            $drop = Join-Path $e 'dropins'
            try {
                $old = @(Get-ChildItem -LiteralPath $drop -Filter 'il.lamerhav.dsviz.bank_*.jar' -ErrorAction SilentlyContinue)
                $same = ($old.Count -eq 1) -and ($old[0].Name -eq $jar.Name) -and ((Get-FileHash -LiteralPath $old[0].FullName).Hash -eq (Get-FileHash -LiteralPath $jar.FullName).Hash)
                if ($same) { Say "Eclipse: המאגר כבר מותקן ב-$e"; continue }
                $old | Remove-Item -Force -ErrorAction Stop
                Copy-Item -LiteralPath $jar.FullName -Destination $drop -Force -ErrorAction Stop
                Say "Eclipse: המאגר הותקן ב-$e"
            } catch { Say "Eclipse: אין הרשאה לכתוב ל-$drop — מריצים את ההתקנה כמנהל" }
        }
    }
}

$zip = Get-ChildItem -LiteralPath $here -Filter 'dsviz-bank-intellij-*.zip' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($zip) {
    $tmp = Join-Path $env:TEMP 'dsviz-bank-ij'
    Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
    try {
        Expand-Archive -LiteralPath $zip.FullName -DestinationPath $tmp -Force -ErrorAction Stop
        $src = Join-Path $tmp 'dsviz-bank-intellij'
        $srcHash = TreeHash $src
        foreach ($p in IdeaPluginDirs 'dsviz-intellij') {
            $t = Join-Path $p 'dsviz-bank-intellij'
            if ($srcHash -ne '' -and (TreeHash $t) -eq $srcHash) { Say "IntelliJ: המאגר כבר מותקן ב-$p"; continue }
            if ((Test-Path -LiteralPath $t) -and (IdeaRunning)) { Say "IntelliJ: פתוח — המאגר יתעדכן כשייסגר ($p)"; continue }
            try {
                if (Test-Path -LiteralPath $t) { Remove-Item -LiteralPath $t -Recurse -Force -ErrorAction Stop }
                Copy-Item -LiteralPath $src -Destination $p -Recurse -Force -ErrorAction Stop
                Say "IntelliJ: המאגר הותקן ב-$p"
            } catch { Say "IntelliJ: ההתקנה של המאגר נכשלה ב-$p — $($_.Exception.Message)" }
        }
    } catch { Say "IntelliJ: $($_.Exception.Message)" }
    Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
}

if (-not $NoVs -and $Only -ne 'intellij') {
    $vs = Join-Path $here 'install-bank-vs.ps1'
    if (Test-Path -LiteralPath $vs) { try { & $vs | ForEach-Object { Say $_ } } catch { Say "Visual Studio: $($_.Exception.Message)" } }
}
exit 0
