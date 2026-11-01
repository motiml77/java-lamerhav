# מחפש התקנות Eclipse (עם JDT — Eclipse ל-Java) במחשב: תהליכי Eclipse שרצים, ואחר כך סריקה של התיקיות הרגילות ושל כל הכוננים.
# כותב את התיקיות (שבהן eclipse.exe) לקובץ שמקבל כפרמטר, בקידוד UTF-16 (נתיבים בעברית). לא מתקין כלום.
# Prev (לא חובה): רשימת ה-Eclipse שקיבלו את התוסף בהתקנה הקודמת (eclipse-dirs.txt, UTF-16) — נכללים תמיד, כדי שאף אחד מהם
# לא יישאר עם התוסף הישן גם אם החיפוש לא מוצא אותו הפעם
param([string]$Out, [string]$Prev)
$ErrorActionPreference = 'SilentlyContinue'
$found = New-Object 'System.Collections.Generic.List[string]'
$clock = [Diagnostics.Stopwatch]::StartNew()

function Add-Eclipse([string]$dir, [switch]$Known) {
    if (-not $dir) { return }
    if (-not (Test-Path -LiteralPath (Join-Path $dir 'eclipse.exe'))) { return }
    $plugins = Join-Path $dir 'plugins'
    # Eclipse ל-Java: JDT בתיקייה plugins — או ב-bundles.info (התקנה של Eclipse Installer עם "bundle pool": הקבצים ב-‎%USERPROFILE%\.p2\pool)
    $java = $Known -or (Get-ChildItem -LiteralPath $plugins -Filter 'org.eclipse.jdt.core_*' -ErrorAction SilentlyContinue)
    if (-not $java) {
        $bi = Join-Path $dir 'configuration\org.eclipse.equinox.simpleconfigurator\bundles.info'
        $java = (Test-Path -LiteralPath $bi) -and (Select-String -LiteralPath $bi -Pattern '^org\.eclipse\.jdt\.core,' -Quiet)
    }
    if (-not $java) { return }   # לא Eclipse ל-Java
    foreach ($f in $found) { if ($f -ieq $dir) { return } }
    $found.Add($dir)
}

# תיקיות שלא שווה להיכנס אליהן
$skip = @('windows', 'node_modules', '.git', '$recycle.bin', 'system volume information', 'appdata', 'programdata', 'plugins', 'features', 'dropins', 'configuration', 'jre', 'jdk', 'temp')

function Walk([string]$dir, [int]$depth) {
    if ($clock.Elapsed.TotalSeconds -gt 60) { return }
    Add-Eclipse $dir
    if ($depth -le 0) { return }
    $subs = $null
    try { $subs = [IO.Directory]::GetDirectories($dir) } catch { return }
    foreach ($s in $subs) {
        $name = (Split-Path $s -Leaf).ToLower()
        if ($skip -contains $name) { continue }
        Walk $s ($depth - 1)
    }
}

# 0. מה שקיבל את התוסף בפעם הקודמת
if ($Prev -and (Test-Path -LiteralPath $Prev)) {
    foreach ($l in [IO.File]::ReadAllLines($Prev, [Text.Encoding]::Unicode)) { Add-Eclipse $l.Trim() -Known }
}

# 1. Eclipse שרץ עכשיו — הכי מדויק
Get-Process | Where-Object { $_.Name -like 'eclipse*' } | ForEach-Object {
    try { Add-Eclipse (Split-Path $_.Path -Parent) } catch { }
}

# 2. מקומות רגילים — עמוק יותר
foreach ($r in @($env:USERPROFILE, (Join-Path $env:USERPROFILE 'eclipse'), (Join-Path $env:USERPROFILE 'Downloads'), (Join-Path $env:USERPROFILE 'Desktop'),
                 (Join-Path $env:USERPROFILE 'Documents'), $env:ProgramFiles, ${env:ProgramFiles(x86)}, ($env:LOCALAPPDATA + '\Programs'))) {
    if ($r -and (Test-Path -LiteralPath $r)) { Walk $r 4 }
}

# 3. כל הכוננים
foreach ($d in [IO.DriveInfo]::GetDrives()) {
    if ($d.DriveType -eq 'Fixed' -or $d.DriveType -eq 'Removable') { if ($d.IsReady) { Walk $d.RootDirectory.FullName 3 } }
}

[IO.File]::WriteAllText($Out, (($found -join "`r`n") + "`r`n"), (New-Object System.Text.UnicodeEncoding($false, $false)))   # UTF-16 בלי BOM
