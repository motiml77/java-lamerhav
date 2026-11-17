# מתקין / מסיר את ההרחבה של DSViz (סרגל הכלים) בכל Visual Studio שנמצא במחשב (2022 ומעלה).
#   install-vsix.ps1 <DsViz.VsExtension.vsix>            — התקנה (למשתמש הנוכחי, בלי הרשאות מנהל) בכל Visual Studio
#   install-vsix.ps1 <DsViz.VsExtension.vsix> -Missing   — רק ב-Visual Studio שעוד אין בו את ההרחבה (למשל Visual Studio שהותקן
#                                                          אחרי DSViz) — מ-update.ps1, בלי לגעת ב-Visual Studio שכבר יש בו
#   install-vsix.ps1 -Uninstall                           — הסרה
#   install-vsix.ps1 <vsix> -Check                        — רק בודק: קוד 10 אם יש Visual Studio שחסר בו סרגל הכלים, או שיש בו גרסה
#                                                          ישנה (ה-DLL שונה מזה שב-vsix), אחרת 0. בלי לכתוב ביומן
#   install-vsix.ps1 <vsix> -Missing -WhenClosed          — מחכה (עד 12 שעות) ש-Visual Studio ייסגר ואז כמו -Missing, ובסוף הודעה קטנה
#                                                          "סרגל הכלים מוכן". update.ps1 מפעיל את זה ברקע כש-Visual Studio פתוח: VSIXInstaller
#                                                          לא מתקין כשהוא פתוח, ו-Visual Studio טוען הרחבות רק כשהוא נפתח — אז זה נכנס לתוקף בפתיחה הבאה
# מספר הגרסה של ההרחבה עולה בכל בנייה, ולכן עדכון הוא התקנה מעל הקיימת (VSIXInstaller מחליף במקום); רק אם מותקנת בדיוק אותה גרסה
# עם תוכן אחר, או שההתקנה מעל הקיימת לא הצליחה — מסירים ואז מתקינים.
# כל תקלה כאן לא עוצרת את ההתקנה של DSViz. יומן: vsix-install.log בתיקיית ההתקנה (%LOCALAPPDATA%\DSViz) — שורה לכל מופע (גם "לא מתאים");
# היומן המלא של VSIXInstaller מההתקנה האחרונה בכל מופע: logs\vsix-<מזהה המופע>.log.
# Windows PowerShell 5.1: ConvertFrom-Json מחזיר מערך JSON כאובייקט אחד — בלי הפירוק (ForEach-Object) הלולאה רצה פעם אחת על כל
# הרשימה, וכשיש יותר מ-Visual Studio אחד (2022 + 2026, Build Tools, SSMS) ההרחבה לא הותקנה באף אחד מהם
param([string]$Vsix = '', [switch]$Uninstall, [switch]$Missing, [switch]$Check, [switch]$WhenClosed)
$ErrorActionPreference = 'Continue'
$id = 'DsViz.VisualStudio.Toolbar.6f3c1c0e-4a52-4e7e-9d1b-5b2f0c8e7a11'
$dsvizDir = Join-Path $env:LOCALAPPDATA 'DSViz'
$logFile = Join-Path $dsvizDir 'vsix-install.log'
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
if (-not (Test-Path -LiteralPath $vswhere)) { if (-not $Check) { Say 'אין Visual Studio — מדלגים' }; exit 0 }
$instances = @()
try { $instances = @(& $vswhere -all -prerelease -products * -format json | Out-String | ConvertFrom-Json | ForEach-Object { $_ }) } catch { Say "vswhere: $($_.Exception.Message)" }
function Major($i) {
    try { return ([version]$i.installationVersion).Major } catch { return 0 }
}
# רק Visual Studio עצמו (Community / Professional / Enterprise) מ-2022 ואילך — כמו ב-source.extension.vsixmanifest. Build Tools, SSMS
# ודומיהם רשומים גם הם ב-vswhere, אבל ההרחבה לא מתאימה להם (וב-Missing היינו מנסים שוב ושוב בכל בדיקה)
function Fits($i) {
    return ([string]$i.productId -match '^Microsoft\.VisualStudio\.Product\.(Community|Professional|Enterprise)$') -and (Major $i) -ge 17
}
function Label($i) {
    return ("{0} {1} [{2}]" -f $i.displayName, $i.catalog.productDisplayVersion, ([string]$i.productId -replace '^Microsoft\.VisualStudio\.Product\.', ''))
}
foreach ($i in $instances) {
    if ($Check -or $WhenClosed -or $Missing) { break }
    if (-not (Fits $i)) { Say ("{0} — לא מתאים לסרגל הכלים (רק Visual Studio Community / Professional / Enterprise, מ-2022) — מדלגים" -f (Label $i)) }
}
$instances = @($instances | Where-Object { Fits $_ })
if (-not $Missing -and -not $Check) { Say ("{0}: {1} מופעים של Visual Studio{2}" -f $(if ($Uninstall) { 'הסרה' } else { 'התקנה' }), $instances.Count, $(if ($Vsix) { " · $Vsix" } else { '' })) }

# התיקיות שבהן ההרחבה מותקנת במופע הזה (התקנה למשתמש: ‎%LOCALAPPDATA%\Microsoft\VisualStudio\<גרסה>.0_<מזהה>\Extensions; לכל המשתמשים:
# Common7\IDE\Extensions). אחרי עדכון במקום יכולות להיות לרגע שתיים (הישנה נמחקת בפתיחה הבאה של Visual Studio)
function InstalledDirs($i) {
    $dirs = @()
    try { $dirs += Join-Path $env:LOCALAPPDATA ('Microsoft\VisualStudio\{0}.0_{1}\Extensions' -f (Major $i), $i.instanceId) } catch { }
    $dirs += Join-Path ([string]$i.installationPath) 'Common7\IDE\Extensions'
    $found = @()
    foreach ($d in $dirs) {
        if (-not (Test-Path -LiteralPath $d)) { continue }
        $found += @(Get-ChildItem -LiteralPath $d -Recurse -Filter extension.vsixmanifest -ErrorAction SilentlyContinue |
            Where-Object { (Get-Content -LiteralPath $_.FullName -Raw -ErrorAction SilentlyContinue) -match [regex]::Escape($id) } | ForEach-Object { $_.Directory.FullName })
    }
    return @($found)
}
function ManifestVersion([string]$text) {
    if ($text -match '<Identity\b[^>]*\bVersion="([^"]+)"') { return $Matches[1] }
    return ''
}
function DirVersion([string]$dir) {
    try { return (ManifestVersion (Get-Content -LiteralPath (Join-Path $dir 'extension.vsixmanifest') -Raw -ErrorAction Stop)) } catch { return '' }
}
# מה יש בקובץ ה-vsix: ה-SHA-256 של ה-DLL (בו גם הכפתורים של הסרגל) ומספר הגרסה שבמניפסט. נקרא פעם אחת
$script:vsixInfo = $null
function VsixInfo {
    if ($null -ne $script:vsixInfo) { return $script:vsixInfo }
    $info = @{ Hash = ''; Version = '' }
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $z = [IO.Compression.ZipFile]::OpenRead($Vsix)
        try {
            $e = $z.Entries | Where-Object { $_.FullName -eq 'DsViz.VsExtension.dll' } | Select-Object -First 1
            if ($e) {
                $st = $e.Open()
                try { $info.Hash = [BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash($st)) -replace '-', '' } finally { $st.Close() }
            }
            $m = $z.Entries | Where-Object { $_.FullName -eq 'extension.vsixmanifest' } | Select-Object -First 1
            if ($m) {
                $rd = New-Object IO.StreamReader($m.Open(), [Text.Encoding]::UTF8)
                try { $info.Version = ManifestVersion ($rd.ReadToEnd()) } finally { $rd.Close() }
            }
        } finally { $z.Dispose() }
    } catch { }
    $script:vsixInfo = $info
    return $info
}
# באחת התיקיות מותקנת בדיוק ההרחבה שבקובץ? (משווים את ה-DLL)
function AnyCurrent($dirs) {
    $want = (VsixInfo).Hash
    if (-not $want) { return $true }   # אי אפשר להשוות — לא מתקינים שוב ושוב
    foreach ($d in @($dirs)) {
        $dll = Join-Path $d 'DsViz.VsExtension.dll'
        if (-not (Test-Path -LiteralPath $dll)) { continue }
        try { if ((Get-FileHash -LiteralPath $dll -Algorithm SHA256).Hash -eq $want) { return $true } } catch { return $true }
    }
    return $false
}
function Needs($i) {
    if (-not (Test-Path -LiteralPath (Join-Path ([string]$i.installationPath) 'Common7\IDE\VSIXInstaller.exe'))) { return $false }
    $dirs = @(InstalledDirs $i)
    return ($dirs.Count -eq 0) -or (-not (AnyCurrent $dirs))
}
if ($Check) {
    if (-not $Vsix -or -not (Test-Path -LiteralPath $Vsix)) { exit 0 }
    foreach ($i in $instances) { if (Needs $i) { exit 10 } }
    exit 0
}
$wait = $null
if ($WhenClosed) {
    # רק אחד מחכה בכל פעם (כל פתיחה של כלי מ-Visual Studio מפעילה את update.ps1)
    try { $wait = [IO.File]::Open((Join-Path $env:TEMP 'dsviz-vsix-wait.lock'), 'OpenOrCreate', 'ReadWrite', 'None') } catch { exit 0 }
    $Missing = $true
    $n = 0
    while ((Get-Process -Name devenv -ErrorAction SilentlyContinue) -and $n -lt 8640) { Start-Sleep -Seconds 5; $n++ }
    if (Get-Process -Name devenv -ErrorAction SilentlyContinue) { exit 0 }
    Start-Sleep -Seconds 3   # ש-Visual Studio ישחרר את הקבצים שלו
}
function RunInstaller([string]$exe, [string[]]$argList, [int]$ms) {
    $p = Start-Process -FilePath $exe -ArgumentList $argList -PassThru -WindowStyle Hidden
    if (-not $p.WaitForExit($ms)) { try { $p.Kill() } catch { }; return 'timeout' }
    return [string]$p.ExitCode
}
# היומן המלא של VSIXInstaller מהריצה האחרונה (‎%TEMP%\dd_VSIXInstaller_*.log‎) — עותק לכל מופע בתיקיית ההתקנה, בשביל DSViz-Diagnose
function KeepLog($i, [datetime]$since) {
    try {
        $last = Get-ChildItem -LiteralPath $env:TEMP -Filter 'dd_VSIXInstaller*.log' -ErrorAction SilentlyContinue |
            Where-Object { $_.LastWriteTime -ge $since.AddSeconds(-2) } | Sort-Object LastWriteTime | Select-Object -Last 1
        if (-not $last) { return $null }
        $dir = Join-Path $dsvizDir 'logs'
        New-Item -ItemType Directory -Force $dir | Out-Null
        Copy-Item -LiteralPath $last.FullName -Destination (Join-Path $dir ("vsix-{0}.log" -f $i.instanceId)) -Force
        return $last
    } catch { return $null }
}

$q = [string][char]34
foreach ($i in $instances) {
    $path = [string]$i.installationPath
    $name = Label $i
    $inst = Join-Path $path 'Common7\IDE\VSIXInstaller.exe'
    if (-not (Test-Path -LiteralPath $inst)) { if (-not $Missing) { Say "$name — אין VSIXInstaller — מדלגים ($path)" }; continue }
    $target = "/instanceIds:$($i.instanceId)"
    if ($Uninstall) {
        Say "$name — הסרה: קוד $(RunInstaller $inst @('/quiet', $target, "/uninstall:$id") 120000)"
        continue
    }
    $info = VsixInfo
    $before = @(InstalledDirs $i)
    if ($Missing -and $before.Count -gt 0 -and (AnyCurrent $before)) { continue }
    if ($Missing) {
        if ($before.Count -gt 0) { Say "$name — יש בו גרסה ישנה של סרגל הכלים של DSViz (העדכון רץ כש-Visual Studio היה פתוח) — מעדכנים" }
        else { Say "$name — אין בו את סרגל הכלים של DSViz (למשל Visual Studio שהותקן אחרי DSViz) — מתקינים" }
    }
    if ($WhenClosed -and (Get-Process -Name devenv -ErrorAction SilentlyContinue)) { Say "$name — Visual Studio נפתח שוב — ננסה בפעם הבאה"; break }
    $oldVersion = if ($before.Count -gt 0) { DirVersion $before[0] } else { '' }
    $removed = $false
    if ($before.Count -gt 0 -and (-not $info.Version -or $oldVersion -eq $info.Version)) {
        # בדיוק אותו מספר גרסה (או שאי אפשר לקרוא אותו) עם תוכן אחר: VSIXInstaller לא מחליף ("already installed") — מסירים את הקודמת
        Say "$name — הסרת הגרסה הקודמת ($oldVersion): קוד $(RunInstaller $inst @('/quiet', $target, "/uninstall:$id") 120000)"
        $removed = $true
    }
    $ok = $false
    foreach ($try in 1, 2) {
        $t0 = Get-Date
        $code = RunInstaller $inst @('/quiet', $target, ($q + $Vsix + $q)) 180000
        $kept = KeepLog $i $t0
        $after = @(InstalledDirs $i)
        $ok = ($after.Count -gt 0) -and (AnyCurrent $after)
        $what = if ($ok) { " · הותקן ב-$($after[-1])" } elseif ($after.Count -gt 0) { ' · נשארה הגרסה הקודמת' } else { ' · לא נמצא אחרי ההתקנה' }
        $ver = if ($info.Version) { " · גרסה $($info.Version)" + $(if ($oldVersion -and $oldVersion -ne $info.Version -and -not $removed) { " (במקום $oldVersion)" } else { '' }) } else { '' }
        Say ("{0} — התקנה (ניסיון {1}): קוד {2}{3}{4}" -f $name, $try, $code, $what, $ver)
        if ($ok) { $script:installedAny = $true }
        if ($ok -or $code -eq 'timeout') { break }
        if (-not $kept) { $kept = Get-ChildItem -LiteralPath $env:TEMP -Filter 'dd_VSIXInstaller*.log' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1 }
        if ($kept) {
            Select-String -LiteralPath $kept.FullName -Pattern 'error|exception|not compatible|blocking|denied|already installed|newer version' -ErrorAction SilentlyContinue | Select-Object -First 6 |
                ForEach-Object { Say ("   VSIXInstaller: " + $_.Line.Trim()) }
        }
        if ($try -eq 1 -and $after.Count -gt 0 -and -not $removed) {
            # ההתקנה מעל הקיימת לא החליפה אותה (למשל מותקנת גרסה חדשה יותר, או שהקודמת מושבתת): מסירים ומנסים שוב
            Say "$name — העדכון במקום לא הצליח — הסרת הגרסה הקודמת: קוד $(RunInstaller $inst @('/quiet', $target, "/uninstall:$id") 120000)"
            $removed = $true
        }
        Start-Sleep -Seconds 5
    }
}
# אחרי המתנה לסגירת Visual Studio: הודעה קטנה בפינה — סרגל הכלים יופיע בפתיחה הבאה
if ($WhenClosed -and $script:installedAny) {
    $notify = Join-Path (Split-Path -Parent $PSScriptRoot) 'notify.ps1'
    if (Test-Path -LiteralPath $notify) {
        try { Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$notify`"", '-State', 'vsready') } catch { }
    }
}
if ($wait) { $wait.Close() }
exit 0
