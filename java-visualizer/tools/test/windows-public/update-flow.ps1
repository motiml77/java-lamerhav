# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# העדכון האוטומטי כשהתלמיד סוגר ופותח את Eclipse — על Windows אמיתי, עם ההתקנה החדשה ו-update.ps1 האמיתי שלה, מול שרת מקומי.
# Eclipse מדומה: תיקייה עם eclipse.exe (עותק של ping — תהליך בשם eclipse) ו-plugins\org.eclipse.jdt.core_*.jar, וכשהוא "פתוח" —
# תהליך שמחזיק את קובצי התוסף והמאגר ב-dropins נעולים, כמו Eclipse אמיתי.
#   א. רגיל: Eclipse פתוח → ההורדה נשמרת ב-pending ולא מתקינים; סוגרים → מותקן תוך שניות, build.txt = הגרסה, pending נמחק
#   ב. המקרה שקרה בפועל: סגרו, ההתקנה התחילה, ופתחו את Eclipse באמצע (אחרי הבדיקה הראשונה) → ההתקנה "לא הושלמה": build.txt
#      נשאר הישן, הקובץ שהורד נשמר; סוגרים ו"הפעלה מחדש ועדכון" (-InstallNow -Relaunch) → מותקן בלי להוריד שוב, ו-Eclipse נפתח מחדש
#   update-flow.ps1 -Setup <DSViz-Setup.exe החדש> -Out <תיקייה>
param([Parameter(Mandatory = $true)][string]$Setup, [Parameter(Mandatory = $true)][string]$Out)
. (Join-Path $PSScriptRoot '..\windows-vs-toolbar\vs-helpers.ps1')
$dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'
$upd = Join-Path $dsviz 'update.ps1'
$progress = Join-Path $env:TEMP 'dsviz-progress.txt'
$port = 18766
$fe = 'C:\eclipse-dsviz-test'
$updLog = Join-Path $dsviz 'update.log'

function BuildTxt { try { return [int64]((Get-Content (Join-Path $dsviz 'build.txt') -Raw).Trim()) } catch { return [int64]0 } }
function SetOld { Set-Content -LiteralPath (Join-Path $dsviz 'build.txt') -Value '202001010000' -NoNewline }
function LogTail([int]$n = 12) { if (Test-Path $updLog) { return @(Get-Content $updLog -Encoding UTF8 | Select-Object -Last $n) } else { return @() } }
function LogSince([int]$from) { if (Test-Path $updLog) { return @(Get-Content $updLog -Encoding UTF8 | Select-Object -Skip $from) } else { return @() } }
function LogCount { if (Test-Path $updLog) { return @(Get-Content $updLog -Encoding UTF8).Count } else { return 0 } }
$script:lockers = @()
function StartEclipse {
    $e = Start-Process -FilePath "$fe\eclipse.exe" -ArgumentList @('-n', '3600', '127.0.0.1') -WindowStyle Hidden -PassThru
    # Eclipse אמיתי מחזיק את קובצי ה-jar ב-dropins פתוחים — אי אפשר למחוק או לדרוס אותם
    $jars = @(Get-ChildItem "$fe\dropins" -Filter '*.jar' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    $cmd = ($jars | ForEach-Object { "`$null = [IO.File]::Open('$_', 'Open', 'Read', 'Read');" }) -join ' '
    $script:lockers = @($e, (Start-Process powershell.exe -ArgumentList @('-NoProfile', '-Command', "$cmd Start-Sleep 3600") -WindowStyle Hidden -PassThru))
    Start-Sleep 2
    Info ("Eclipse המדומה נפתח (תהליך {0}, {1} קבצים נעולים)" -f $e.Id, $jars.Count)
}
function StopEclipse {
    foreach ($p in $script:lockers) { try { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } catch { } }
    Get-Process -Name eclipse -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    $script:lockers = @()
    Start-Sleep 1
    Info 'Eclipse המדומה נסגר'
}
function RunUpdater([string[]]$extra) {
    $env:DSVIZ_VERSION_URL = "http://127.0.0.1:$port/version.json"
    $a = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$upd`"") + $extra
    $p = Start-Process powershell.exe -ArgumentList $a -PassThru -WindowStyle Hidden
    Remove-Item Env:\DSVIZ_VERSION_URL -ErrorAction SilentlyContinue
    return $p
}
function WaitFor([scriptblock]$cond, [int]$sec) { $t = Get-Date; while (-not (& $cond) -and ((Get-Date) - $t).TotalSeconds -lt $sec) { Start-Sleep -Milliseconds 300 }; return (& $cond) }
function Installing { return [bool](Get-Process | Where-Object { $_.ProcessName -like 'DSViz-Setup-*' }) }

Log "== 0. ההתקנה החדשה, ו-Eclipse מדומה שמקבל את התוסף"
New-Item -ItemType Directory -Force "$fe\plugins", "$fe\dropins" | Out-Null
Copy-Item "$env:SystemRoot\System32\PING.EXE" "$fe\eclipse.exe" -Force
Set-Content "$fe\plugins\org.eclipse.jdt.core_3.40.0.jar" -Value 'x'
Set-Content "$fe\eclipse.ini" -Value "-vmargs`r`n-Xmx1024m"
$null = InstallDsviz $Setup '/S' 'התקנה'
$real = BuildTxt
$plugin = Get-ChildItem "$fe\dropins" -Filter 'il.lamerhav.dsviz_*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
Check 'eclipse-got-plugin' ($null -ne $plugin) "Eclipse המדומה קיבל את התוסף ב-dropins ($real)"
if (-not $plugin -or $real -le 0) { Save-Summary; exit 1 }
$pluginHash = (Get-FileHash $plugin.FullName).Hash

Log "== שרת מקומי: version.json (הבנייה האמיתית של ההתקנה) והקובץ"
$sha = (Get-FileHash $Setup -Algorithm SHA256).Hash.ToLower()
$ver = (@{ version = '1.0.9'; build = $real; url = "http://127.0.0.1:$port/DSViz-Setup.exe"; sha256 = $sha } | ConvertTo-Json)
$job = Start-Job -ArgumentList $Setup, $port, $ver -ScriptBlock {
    param($exe, $port, $verJson)
    $l = New-Object Net.HttpListener
    $l.Prefixes.Add("http://127.0.0.1:$port/")
    $l.Start()
    while ($true) {
        $c = $l.GetContext()
        try {
            if ($c.Request.Url.AbsolutePath -eq '/version.json') {
                $b = [Text.Encoding]::UTF8.GetBytes($verJson)
                $c.Response.ContentLength64 = $b.Length
                $c.Response.OutputStream.Write($b, 0, $b.Length)
            } elseif ($c.Request.Url.AbsolutePath -eq '/DSViz-Setup.exe') {
                $fs = [IO.File]::OpenRead($exe)
                $c.Response.ContentLength64 = $fs.Length
                $fs.CopyTo($c.Response.OutputStream)
                $fs.Close()
            }
        } catch { }
        try { $c.Response.Close() } catch { }
    }
}
$up = WaitFor { try { $null = Invoke-WebRequest "http://127.0.0.1:$port/version.json" -UseBasicParsing -TimeoutSec 3; $true } catch { $false } } 30
Check 'server-up' $up 'השרת המקומי עלה'
if (-not $up) { Stop-Job $job; Save-Summary; exit 1 }

Log "== א. Eclipse פתוח: מורידים ושומרים, ומתקינים רק כשסוגרים"
SetOld
Remove-Item (Join-Path $dsviz 'pending') -Recurse -Force -ErrorAction SilentlyContinue
StartEclipse
$n0 = LogCount
$u = RunUpdater @('-FromEclipse')
$saved = WaitFor { Test-Path (Join-Path $dsviz 'pending\pending.json') } 240
Start-Sleep 8
Check 'a-pending-saved' $saved 'ההורדה נשמרה ב-pending (לא ב-TEMP)'
Check 'a-not-installed-yet' ((BuildTxt) -lt $real -and -not (Installing)) 'כש-Eclipse פתוח — לא מתקינים'
StopEclipse
$t = Get-Date
$started = WaitFor { Installing } 30
Check 'a-installs-fast' $started ("אחרי הסגירה ההתקנה התחילה תוך {0:N0} שניות" -f ((Get-Date) - $t).TotalSeconds)
$null = WaitFor { $u.HasExited } 600
(LogSince $n0) | ForEach-Object { Info "   update.log: $_" }
Check 'a-build-updated' ((BuildTxt) -eq $real) "build.txt = $real"
Check 'a-pending-cleared' (-not (Test-Path (Join-Path $dsviz 'pending\pending.json'))) 'אחרי ההתקנה pending נמחק'
Check 'a-log' (((LogSince $n0) -join "`n") -match 'ההתקנה הסתיימה: גרסה') 'ביומן: "ההתקנה הסתיימה: גרסה ..."'

Log "== ב. סגרו, ההתקנה התחילה, ופתחו את Eclipse באמצע — ההתקנה לא מסמנת את עצמה כמותקנת"
SetOld
Remove-Item (Join-Path $dsviz 'pending') -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $dsviz 'update-incomplete.txt') -Force -ErrorAction SilentlyContinue
Remove-Item $progress -Force -ErrorAction SilentlyContinue
$n1 = LogCount
$u = RunUpdater @('-AfterEclipse')
# פותחים את Eclipse אחרי הבדיקה הראשונה של ההתקנה (42%) — בזמן חבילת C# (44–82%), לפני הקבצים של Eclipse (86%), כמו שקרה לתלמיד
$mid = WaitFor { $t = ''; try { $t = (Get-Content $progress -Raw -ErrorAction Stop).Trim() } catch { }; $m = [regex]::Match($t, '^install (\d+)$'); $m.Success -and [int]$m.Groups[1].Value -ge 44 } 600
Check 'b-install-midway' $mid 'ההתקנה עברה את הבדיקה הראשונה של Eclipse (42%) — עכשיו חבילת C#'
StartEclipse
$null = WaitFor { $u.HasExited } 900
(LogSince $n1) | ForEach-Object { Info "   update.log: $_" }
Check 'b-build-not-claimed' ((BuildTxt) -lt $real) 'build.txt נשאר הישן — העדכון לא סומן כמותקן'
Check 'b-incomplete-file' (Test-Path (Join-Path $dsviz 'update-incomplete.txt')) 'update-incomplete.txt נכתב'
Check 'b-pending-kept' (Test-Path (Join-Path $dsviz 'pending\pending.json')) 'הקובץ שהורד נשמר להתקנה הבאה'
Check 'b-plugin-untouched' ((Get-FileHash (Get-ChildItem "$fe\dropins" -Filter 'il.lamerhav.dsviz_*.jar' | Select-Object -First 1).FullName).Hash -eq $pluginHash) 'בקבצים של Eclipse הפתוח לא נגעו'
Check 'b-log' (((LogSince $n1) -join "`n") -match 'לא הושלמה') 'ביומן: "ההתקנה לא הושלמה ... תותקן שוב"'

Log "== ב2. \"הפעלה מחדש ועדכון\" מ-Eclipse: נסגר, מותקן בלי להוריד שוב, ו-Eclipse נפתח מחדש"
$n2 = LogCount
$u = RunUpdater @('-InstallNow', '-Relaunch', "`"$fe\eclipse.exe`"", '-Workspace', "`"C:\dsviz-ws`"")
Start-Sleep 3
StopEclipse   # Eclipse נסגר (PlatformUI.getWorkbench().close())
$null = WaitFor { $u.HasExited } 900
(LogSince $n2) | ForEach-Object { Info "   update.log: $_" }
$l2 = (LogSince $n2) -join "`n"
Check 'b2-build-updated' ((BuildTxt) -eq $real) "build.txt = $real"
Check 'b2-no-redownload' ($l2 -notmatch 'נמצאה גרסה חדשה') 'בלי להוריד שוב (הקובץ מ-pending)'
Check 'b2-relaunched' ($l2 -match 'Eclipse נפתח מחדש') 'Eclipse נפתח מחדש אחרי ההתקנה'
Check 'b2-incomplete-cleared' (-not (Test-Path (Join-Path $dsviz 'update-incomplete.txt'))) 'update-incomplete.txt נמחק'

StopEclipse
Stop-Job $job -ErrorAction SilentlyContinue; Remove-Job $job -Force -ErrorAction SilentlyContinue
Copy-Item $updLog $Out -ErrorAction SilentlyContinue
Log ("SUMMARY {0} FAIL — עדכון אוטומטי: סגירה ופתיחה של Eclipse" -f $script:fails)
Save-Summary
exit ([int]($script:fails -gt 0))
