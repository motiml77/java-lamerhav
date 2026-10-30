# מחפש התקנות Eclipse (עם JDT — Eclipse ל-Java) במחשב: תהליכי Eclipse שרצים, ואחר כך סריקה של התיקיות הרגילות ושל כל הכוננים.
# כותב את התיקיות (שבהן eclipse.exe) לקובץ שמקבל כפרמטר, בקידוד UTF-16 (נתיבים בעברית). לא מתקין כלום.
param([string]$Out)
$ErrorActionPreference = 'SilentlyContinue'
$found = New-Object 'System.Collections.Generic.List[string]'
$clock = [Diagnostics.Stopwatch]::StartNew()

function Add-Eclipse([string]$dir) {
    if (-not $dir) { return }
    if (-not (Test-Path -LiteralPath (Join-Path $dir 'eclipse.exe'))) { return }
    $plugins = Join-Path $dir 'plugins'
    if (-not (Get-ChildItem -LiteralPath $plugins -Filter 'org.eclipse.jdt.core_*' -ErrorAction SilentlyContinue)) { return }   # לא Eclipse ל-Java
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
