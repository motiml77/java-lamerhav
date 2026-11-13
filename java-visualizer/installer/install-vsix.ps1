# מתקין / מסיר את ההרחבה של DSViz (סרגל הכלים) בכל Visual Studio שנמצא במחשב.
#   install-vsix.ps1 <DsViz.VsExtension.vsix>            — התקנה (למשתמש הנוכחי, בלי הרשאות מנהל) בכל Visual Studio
#   install-vsix.ps1 <DsViz.VsExtension.vsix> -Missing   — רק ב-Visual Studio שעוד אין בו את ההרחבה (למשל Visual Studio שהותקן
#                                                          אחרי DSViz) — מ-update.ps1, בלי לגעת ב-Visual Studio שכבר יש בו
#   install-vsix.ps1 -Uninstall                           — הסרה
# כל תקלה כאן לא עוצרת את ההתקנה של DSViz. יומן: vsix-install.log בתיקיית ההתקנה (%LOCALAPPDATA%\DSViz).
# Windows PowerShell 5.1: ConvertFrom-Json מחזיר מערך JSON כאובייקט אחד — בלי הפירוק (ForEach-Object) הלולאה רצה פעם אחת על כל
# הרשימה, וכשיש יותר מ-Visual Studio אחד (2022 + 2026, Build Tools, SSMS) ההרחבה לא הותקנה באף אחד מהם
param([string]$Vsix = '', [switch]$Uninstall, [switch]$Missing)
$ErrorActionPreference = 'Continue'
$id = 'DsViz.VisualStudio.Toolbar.6f3c1c0e-4a52-4e7e-9d1b-5b2f0c8e7a11'
$logFile = Join-Path (Join-Path $env:LOCALAPPDATA 'DSViz') 'vsix-install.log'
function Say([string]$m) {
    Write-Host $m
    try {
        $lines = @()
        if (Test-Path -LiteralPath $logFile) { $lines = @(Get-Content -LiteralPath $logFile -Encoding UTF8 -ErrorAction SilentlyContinue | Select-Object -Last 300) }
        $lines += ('{0:yyyy-MM-dd HH:mm:ss}  {1}' -f (Get-Date), $m)
        Set-Content -LiteralPath $logFile -Value $lines -Encoding UTF8
    } catch { }
}
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path -LiteralPath $vswhere)) { Say 'אין Visual Studio — מדלגים'; exit 0 }
$instances = @()
try { $instances = @(& $vswhere -all -prerelease -products * -format json | Out-String | ConvertFrom-Json | ForEach-Object { $_ }) } catch { Say "vswhere: $($_.Exception.Message)" }
# רק Visual Studio עצמו (Community / Professional / Enterprise) מ-2022 ואילך — כמו ב-source.extension.vsixmanifest. Build Tools, SSMS
# ודומיהם רשומים גם הם ב-vswhere, אבל ההרחבה לא מתאימה להם (וב-Missing היינו מנסים שוב ושוב בכל בדיקה)
function Fits($i) {
    $major = 0
    try { $major = ([version]$i.installationVersion).Major } catch { }
    return ([string]$i.productId -match '^Microsoft\.VisualStudio\.Product\.(Community|Professional|Enterprise)$') -and $major -ge 17
}
foreach ($i in $instances) {
    if (-not (Fits $i)) { Write-Host ("לא מתאים לסרגל הכלים: {0} {1} [{2}]" -f $i.displayName, $i.catalog.productDisplayVersion, $i.productId) }
}
$instances = @($instances | Where-Object { Fits $_ })
if (-not $Missing) { Say ("{0}: {1} מופעים של Visual Studio{2}" -f $(if ($Uninstall) { 'הסרה' } else { 'התקנה' }), $instances.Count, $(if ($Vsix) { " · $Vsix" } else { '' })) }

# ההרחבה כבר במופע הזה? (התקנה למשתמש: ‎%LOCALAPPDATA%\Microsoft\VisualStudio\<גרסה>.0_<מזהה>\Extensions; לכל המשתמשים: Common7\IDE\Extensions)
function Installed($i) {
    $dirs = @()
    try { $dirs += Join-Path $env:LOCALAPPDATA ('Microsoft\VisualStudio\{0}.0_{1}\Extensions' -f ([version]$i.installationVersion).Major, $i.instanceId) } catch { }
    $dirs += Join-Path $i.installationPath 'Common7\IDE\Extensions'
    foreach ($d in $dirs) {
        if (-not (Test-Path -LiteralPath $d)) { continue }
        $m = Get-ChildItem -LiteralPath $d -Recurse -Filter extension.vsixmanifest -ErrorAction SilentlyContinue |
            Where-Object { (Get-Content -LiteralPath $_.FullName -Raw -ErrorAction SilentlyContinue) -match [regex]::Escape($id) } | Select-Object -First 1
        if ($m) { return $m.Directory.FullName }
    }
    return ''
}
function RunInstaller([string]$exe, [string[]]$argList, [int]$ms) {
    $p = Start-Process -FilePath $exe -ArgumentList $argList -PassThru -WindowStyle Hidden
    if (-not $p.WaitForExit($ms)) { try { $p.Kill() } catch { }; return 'timeout' }
    return [string]$p.ExitCode
}

$q = [string][char]34
foreach ($i in $instances) {
    $path = [string]$i.installationPath
    $name = "{0} {1}" -f $i.displayName, $i.catalog.productDisplayVersion
    $inst = Join-Path $path 'Common7\IDE\VSIXInstaller.exe'
    if (-not (Test-Path -LiteralPath $inst)) { if (-not $Missing) { Say "$name — אין VSIXInstaller — מדלגים ($path)" }; continue }
    $target = "/instanceIds:$($i.instanceId)"
    if ($Uninstall) {
        Say "$name — הסרה: קוד $(RunInstaller $inst @('/quiet', $target, "/uninstall:$id") 120000)"
        continue
    }
    $before = Installed $i
    if ($Missing -and $before) { continue }
    if ($Missing) { Say "$name — אין בו את סרגל הכלים של DSViz (למשל Visual Studio שהותקן אחרי DSViz) — מתקינים" }
    if ($before) {   # אותה גרסה לא מוחלפת לבד — מסירים את הקודמת ואז מתקינים
        Say "$name — הסרת הגרסה הקודמת: קוד $(RunInstaller $inst @('/quiet', $target, "/uninstall:$id") 120000)"
    }
    $ok = ''
    foreach ($try in 1, 2) {
        $code = RunInstaller $inst @('/quiet', $target, ($q + $Vsix + $q)) 180000
        $ok = Installed $i
        Say ("{0} — התקנה (ניסיון {1}): קוד {2}{3}" -f $name, $try, $code, $(if ($ok) { " · הותקן ב-$ok" } else { ' · לא נמצא אחרי ההתקנה' }))
        if ($ok -or $code -eq 'timeout') { break }
        Start-Sleep -Seconds 5
    }
    if (-not $ok) {
        $last = Get-ChildItem -LiteralPath $env:TEMP -Filter 'dd_VSIXInstaller*.log' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1
        if ($last) {
            Select-String -LiteralPath $last.FullName -Pattern 'error|exception|not compatible|blocking|denied' -ErrorAction SilentlyContinue | Select-Object -First 6 |
                ForEach-Object { Say ("   VSIXInstaller: " + $_.Line.Trim()) }
        }
    }
}
exit 0
