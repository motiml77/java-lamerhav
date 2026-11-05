# (UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# גרסת בדיקה (רק למנהל — לא בהורדה של התלמידים): מתקין את "מאגר התרגילים" עם מסך הכניסה, אחרי ש-DSViz-Setup.exe הותקן.
#   Eclipse        — il.lamerhav.dsviz.bank_*.jar לתיקיית dropins של כל Eclipse שקיבל את DSViz (eclipse-dirs.txt)
#   Visual Studio  — dsviz-bank-vs.jar + שני קיצורים בשולחן העבודה (install-bank-vs.ps1)
#   IntelliJ IDEA  — התוסף dsviz-bank-intellij לכל IntelliJ שיש בו את DSViz
#   lab-install.ps1 -Remove  — מסיר את כל זה (DSViz עצמו נשאר)
param([switch]$Remove)
$ErrorActionPreference = 'Continue'
$here = $PSScriptRoot
$dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'
$ijRoot = Join-Path $env:APPDATA 'JetBrains'

function EclipseDirs {
    $f = Join-Path $dsviz 'eclipse-dirs.txt'
    if (-not (Test-Path $f)) { return @() }
    return @(Get-Content $f -Encoding Unicode | Where-Object { $_ -and (Test-Path (Join-Path $_ 'eclipse.exe')) } | Select-Object -Unique)
}
function IdeaPluginDirs {
    if (-not (Test-Path $ijRoot)) { return @() }
    return @(Get-ChildItem $ijRoot -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'plugins' } |
        Where-Object { Test-Path (Join-Path $_ 'dsviz-intellij') })
}

foreach ($e in EclipseDirs) {
    Get-ChildItem (Join-Path $e 'dropins') -Filter 'il.lamerhav.dsviz.bank_*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
    if (-not $Remove) {
        try {
            Copy-Item (Get-ChildItem $here -Filter 'il.lamerhav.dsviz.bank_*.jar' | Select-Object -First 1).FullName (Join-Path $e 'dropins') -Force -ErrorAction Stop
            Write-Output "Eclipse: $e"
        } catch { Write-Output "Eclipse: אין הרשאה לכתוב ל-$e\dropins — מריצים את ההתקנה כמנהל" }
    }
}
foreach ($p in IdeaPluginDirs) {
    $t = Join-Path $p 'dsviz-bank-intellij'
    if (Test-Path $t) { Remove-Item $t -Recurse -Force -ErrorAction SilentlyContinue }
    if (-not $Remove) {
        Expand-Archive (Get-ChildItem $here -Filter 'dsviz-bank-intellij-*.zip' | Select-Object -First 1).FullName -DestinationPath $p -Force
        Write-Output "IntelliJ: $p"
    }
}
$vs = Join-Path $here 'install-bank-vs.ps1'
if ($Remove) { & $vs -Remove } else { & $vs }
exit 0
