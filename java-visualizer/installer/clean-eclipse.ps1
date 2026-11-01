# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# מנקה מ-Eclipse אחד כל עותק קודם של התוסף, לפני שההתקנה מעתיקה את החדש (וגם בהסרה):
#   - כל jar / תיקייה של il.lamerhav.dsviz ב-dropins (גם בתתי-תיקיות, וגם בשם אחר — לפי Bundle-SymbolicName שבתוך הקובץ)
#   - il.lamerhav.dsviz_* בתיקייה plugins (התקנה ידנית / Install New Software), והשורה שלו ב-bundles.info
#   - המטמון של Eclipse (configuration\org.eclipse.osgi — כמו eclipse -clean): בתיקיית Eclipse, בתיקייה של -configuration
#     ב-eclipse.ini, ובתיקיות ההגדרות של המשתמש (‎%USERPROFILE%\.eclipse\*\configuration — Eclipse בתיקייה מוגנת כמו Program Files)
# בלי זה Eclipse יכול להמשיך לטעון את הגרסה הישנה: המטמון זוכר את התוסף לפי השם והגרסה, ועותק ישן במקום אחר יכול לנצח.
#   clean-eclipse.ps1 <תיקיית Eclipse> [-UserHome <תיקיית הבית>]
param([string]$Eclipse, [string]$UserHome = $env:USERPROFILE)
$ErrorActionPreference = 'SilentlyContinue'
$bsn = 'il.lamerhav.dsviz'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Say([string]$m) { Write-Output "Eclipse: $m" }

# ה-Bundle-SymbolicName של jar או של תיקייה (בלי ;singleton:=true), או ריק
function SymbolicName([string]$path) {
    $text = $null
    if (Test-Path -LiteralPath $path -PathType Container) {
        $mf = Join-Path $path 'META-INF\MANIFEST.MF'
        if (Test-Path -LiteralPath $mf) { $text = [IO.File]::ReadAllText($mf) }
    } else {
        $z = $null
        try {
            $z = [IO.Compression.ZipFile]::OpenRead($path)
            $e = $z.GetEntry('META-INF/MANIFEST.MF')
            if ($e) { $r = New-Object IO.StreamReader($e.Open()); $text = $r.ReadToEnd(); $r.Close() }
        } catch { } finally { if ($z) { $z.Dispose() } }
    }
    if (-not $text) { return '' }
    $m = [regex]::Match($text, '(?m)^Bundle-SymbolicName:\s*([^;\r\n]+)')
    if ($m.Success) { return $m.Groups[1].Value.Trim() }
    return ''
}

function Remove([string]$path) {
    Remove-Item -LiteralPath $path -Recurse -Force
    if (Test-Path -LiteralPath $path) { Say "לא הצלחתי למחוק את $path (Eclipse פתוח? אין הרשאה?)" } else { Say "הוסרה גרסה קודמת: $path" }
}

if (-not $Eclipse -or -not (Test-Path -LiteralPath $Eclipse)) { exit 0 }

# 1. dropins — כל jar ותיקייה, בכל עומק (dropins\x.jar, dropins\dsviz\plugins\x.jar, dropins\dsviz\eclipse\plugins\...)
$dropins = Join-Path $Eclipse 'dropins'
if (Test-Path -LiteralPath $dropins) {
    Get-ChildItem -LiteralPath $dropins -Recurse -Force | Where-Object {
        ($_.PSIsContainer -and $_.Name -like "${bsn}_*") -or (-not $_.PSIsContainer -and $_.Extension -ieq '.jar')
    } | ForEach-Object {
        if ((SymbolicName $_.FullName) -eq $bsn -or ($_.Name -like "${bsn}_*" -and $_.Name -notlike "${bsn}.*")) { Remove $_.FullName }
    }
}

# 2. plugins — רק לפי השם (התיקייה גדולה)
$plugins = Join-Path $Eclipse 'plugins'
Get-ChildItem -LiteralPath $plugins -Filter "${bsn}_*" -Force | ForEach-Object {
    if ((SymbolicName $_.FullName) -eq $bsn) { Remove $_.FullName }
}

# 3. bundles.info: שורות של התוסף שהקובץ שלהן כבר לא קיים (חוץ מ-dropins — את זה Eclipse מעדכן לבד לפי מה שיש שם)
function FixBundlesInfo([string]$config) {
    $bi = Join-Path $config 'org.eclipse.equinox.simpleconfigurator\bundles.info'
    if (-not (Test-Path -LiteralPath $bi)) { return }
    $lines = [IO.File]::ReadAllLines($bi)
    $keep = New-Object 'System.Collections.Generic.List[string]'
    $changed = $false
    foreach ($l in $lines) {
        $p = $l.Split(',')
        if ($p.Length -ge 3 -and $p[0] -eq $bsn -and -not $p[2].StartsWith('dropins/')) {
            $f = $p[2]
            if (-not [IO.Path]::IsPathRooted($f) -and -not $f.StartsWith('file:')) { $f = Join-Path $Eclipse ($f -replace '/', '\') }
            if (-not (Test-Path -LiteralPath ($f -replace '^file:/*', ''))) { $changed = $true; continue }
        }
        $keep.Add($l)
    }
    if ($changed) {
        [IO.File]::WriteAllLines($bi, $keep.ToArray(), (New-Object Text.UTF8Encoding($false)))
        Say "bundles.info עודכן: $bi"
    }
}

# 4. המטמון של Eclipse — בכל מקום שבו יכולה להיות תיקיית configuration של ה-Eclipse הזה
$configs = New-Object 'System.Collections.Generic.List[string]'
$configs.Add((Join-Path $Eclipse 'configuration'))
$ini = Join-Path $Eclipse 'eclipse.ini'
if (Test-Path -LiteralPath $ini) {
    $il = [IO.File]::ReadAllLines($ini)
    for ($i = 0; $i -lt $il.Length - 1; $i++) {
        if ($il[$i].Trim() -eq '-configuration') {
            $c = $il[$i + 1].Trim() -replace '^file:/*', '' -replace '@user\.home', $UserHome
            if (-not [IO.Path]::IsPathRooted($c)) { $c = Join-Path $Eclipse $c }
            $configs.Add($c)
        }
    }
}
# Eclipse בתיקייה בלי הרשאת כתיבה (Program Files): ההגדרות והמטמון אצל המשתמש. לא יודעים איזו מהן שייכת לאיזה Eclipse —
# מנקים את המטמון בכולן (בדיוק מה ש-eclipse -clean עושה; Eclipse בונה אותו מחדש בהפעלה הבאה)
Get-ChildItem -LiteralPath (Join-Path $UserHome '.eclipse') -Directory -Force | ForEach-Object {
    $c = Join-Path $_.FullName 'configuration'
    if (Test-Path -LiteralPath $c) { $configs.Add($c) }
}
foreach ($c in $configs) {
    FixBundlesInfo $c
    $osgi = Join-Path $c 'org.eclipse.osgi'
    if (Test-Path -LiteralPath $osgi) {
        Remove-Item -LiteralPath $osgi -Recurse -Force
        if (Test-Path -LiteralPath $osgi) { Say "לא הצלחתי לנקות את המטמון $osgi" } else { Say "המטמון נוקה: $osgi" }
    }
}
exit 0
