# (UTF-8 עם BOM)
# "ניהול DSViz" — קיצור בשולחן העבודה ובתפריט התחל שפותח את מסוף ההנהלה כחלון נפרד (Edge או Chrome במצב חלון, בלי שורת כתובת).
#   admin-shortcut.ps1 -Remove  — מוחק את הקיצורים
param([switch]$Remove, [string]$Url = 'https://dsviz-exercise.motiml77.workers.dev/admin', [string]$Icon)
$name = 'ניהול DSViz'
$desk = [Environment]::GetFolderPath('Desktop')
$menu = Join-Path ([Environment]::GetFolderPath('Programs')) 'מבני נתונים'
$targets = @((Join-Path $desk "$name.lnk"), (Join-Path $menu "$name.lnk"))
foreach ($t in $targets) { Remove-Item $t -Force -ErrorAction SilentlyContinue }
if ($Remove) { exit 0 }
$browser = @("${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe", "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe", "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
    "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe") | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
New-Item -ItemType Directory -Force $menu | Out-Null
$ws = New-Object -ComObject WScript.Shell
foreach ($final in $targets) {
    # WScript.Shell לא שומר קיצור עם שם בעברית — שומרים בשם זמני באנגלית ומשנים את השם
    $tmp = Join-Path (Split-Path $final) 'dsviz-admin-tmp.lnk'
    $s = $ws.CreateShortcut($tmp)
    if ($browser) { $s.TargetPath = $browser; $s.Arguments = "--app=$Url --window-size=1280,860" }
    else { $s.TargetPath = 'explorer.exe'; $s.Arguments = $Url }
    if ($Icon -and (Test-Path $Icon)) { $s.IconLocation = $Icon }
    $s.Description = 'מסוף ההנהלה של DSViz'
    $s.Save()
    Move-Item $tmp $final -Force
}
Write-Output "הקיצור נוצר: $name ($browser)"
