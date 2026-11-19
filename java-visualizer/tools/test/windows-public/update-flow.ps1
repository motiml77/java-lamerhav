# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# העדכון האוטומטי כשהתלמיד סוגר ופותח את Eclipse — על Windows אמיתי, עם ההתקנה החדשה ו-update.ps1 האמיתי שלה, מול שרת מקומי.
# Eclipse מדומה: תיקייה עם eclipse.exe (עותק של ping — תהליך בשם eclipse) ו-plugins\org.eclipse.jdt.core_*.jar, וכשהוא "פתוח" —
# תהליך שמחזיק את קובצי התוסף והמאגר ב-dropins נעולים, כמו Eclipse אמיתי.
#   א. רגיל: Eclipse פתוח → ההורדה נשמרת ב-pending ולא מתקינים; סוגרים → מותקן תוך שניות, build.txt = הגרסה, pending נמחק
#      "הפעלה מחדש ועדכון" (-InstallNow -Relaunch) → אחרי ההתקנה Eclipse נפתח מחדש
#   ב. המקרה שקרה בפועל: סגרו, ההתקנה התחילה, ופתחו את Eclipse באמצע (אחרי הבדיקה הראשונה) → הגרסה החדשה נכתבת ליד הישנה
#      (בלי לגעת בקבצים הנעולים), והעדכון מושלם: build.txt = הגרסה החדשה
#   ג. ההתקנה הבאה כש-Eclipse סגור מנקה את הכפילות
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
$script:runNo = 0
function RunUpdater([string[]]$extra) {
    # מי עוד מריץ את update.ps1 עכשיו (מחזיק את המנעול "עדכון אחד בכל פעם")?
    $others = @(Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*update.ps1*' })
    foreach ($o in $others) { Info ("   כבר רץ: [{0}] {1}" -f $o.ProcessId, $o.CommandLine) }
    $script:runNo++
    $env:DSVIZ_VERSION_URL = "http://127.0.0.1:$port/version.json"
    $a = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$upd`"") + $extra
    $o1 = Join-Path $Out "updater-$($script:runNo).out.txt"; $o2 = Join-Path $Out "updater-$($script:runNo).err.txt"
    $p = Start-Process powershell.exe -ArgumentList $a -PassThru -RedirectStandardOutput $o1 -RedirectStandardError $o2
    Remove-Item Env:\DSVIZ_VERSION_URL -ErrorAction SilentlyContinue
    Info ("update.ps1 {0} — תהליך {1}" -f ($extra -join ' '), $p.Id)
    return $p
}
function ShowRun([System.Diagnostics.Process]$p) {
    $n = $script:runNo
    Info ("   הסתיים: {0}, קוד: {1}" -f $p.HasExited, $(if ($p.HasExited) { $p.ExitCode } else { '-' }))
    foreach ($f in @("updater-$n.out.txt", "updater-$n.err.txt")) {
        $path = Join-Path $Out $f
        if ((Test-Path $path) -and (Get-Item $path).Length -gt 0) { Get-Content $path -Encoding UTF8 | Select-Object -First 30 | ForEach-Object { Info "   $f`: $_" } }
    }
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
# update.ps1 מהקוד (לא מההתקנה): כך שינוי ב-update.ps1 נבדק מיד, בלי לחכות לבנייה של התקנה חדשה (ההתקנה שבענף נבנית ממנו)
$srcUpd = Join-Path $PSScriptRoot '..\..\..\installer\update.ps1'
if ((Get-FileHash $srcUpd).Hash -ne (Get-FileHash $upd).Hash) { Copy-Item $srcUpd $upd -Force; Info 'update.ps1 מהקוד (שונה מזה שבהתקנה)' }
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
                $c.Response.ContentType = 'text/plain; charset=utf-8'   # כמו raw.githubusercontent.com; בלי סוג תוכן PowerShell מחזיר בתים, לא טקסט
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

Log "== א. Eclipse פתוח: מורידים ושומרים; `"הפעלה מחדש ועדכון`" — נסגר, מותקן מיד, ונפתח מחדש"
SetOld
Remove-Item (Join-Path $dsviz 'pending') -Recurse -Force -ErrorAction SilentlyContinue
StartEclipse
$n0 = LogCount
$u = RunUpdater @('-FromEclipse')
$saved = WaitFor { (Test-Path (Join-Path $dsviz 'pending\pending.json')) -or $u.HasExited } 600
Start-Sleep 5
Check 'a-pending-saved' ((Test-Path (Join-Path $dsviz 'pending\pending.json')) -and -not $u.HasExited) 'ההורדה נשמרה ב-pending, והמעדכן מחכה ש-Eclipse ייסגר'
Check 'a-not-installed-yet' ((BuildTxt) -lt $real -and -not (Installing)) 'כש-Eclipse פתוח — לא מתקינים'
# הכפתור "הפעלה מחדש ועדכון" ב-Eclipse (UpdatePrompt): מעדכן שני רק רושם את הבקשה לפתוח מחדש (הראשון מחזיק את המנעול), ו-Eclipse נסגר
$u2 = RunUpdater @('-InstallNow', '-Relaunch', "`"$fe\eclipse.exe`"", '-Workspace', "`"C:\dsviz-ws`"")
$null = WaitFor { $u2.HasExited } 30
StopEclipse
$t = Get-Date
$started = WaitFor { Installing } 30
Check 'a-installs-fast' $started ("אחרי הסגירה ההתקנה התחילה תוך {0:N0} שניות" -f ((Get-Date) - $t).TotalSeconds)
$null = WaitFor { $u.HasExited } 900
(LogSince $n0) | ForEach-Object { Info "   update.log: $_" }
$l0 = (LogSince $n0) -join "`n"
Check 'a-build-updated' ((BuildTxt) -eq $real) "build.txt = $real"
Check 'a-pending-cleared' (-not (Test-Path (Join-Path $dsviz 'pending\pending.json'))) 'אחרי ההתקנה pending נמחק'
Check 'a-log' ($l0 -match 'ההתקנה הסתיימה: גרסה') 'ביומן: "ההתקנה הסתיימה: גרסה ..."'
Check 'a-relaunched' ($l0 -match 'Eclipse נפתח מחדש') 'Eclipse נפתח מחדש אחרי ההתקנה'
StopEclipse

Log "== ב. המקרה שקרה: סגרו, ההתקנה התחילה, ופתחו את Eclipse באמצע — הגרסה החדשה נכתבת ליד הישנה, והעדכון מושלם"
SetOld
Remove-Item (Join-Path $dsviz 'pending') -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $progress -Force -ErrorAction SilentlyContinue
$oldJars = @(Get-ChildItem "$fe\dropins" -Filter '*.jar' | ForEach-Object { "$($_.Name)=$((Get-FileHash $_.FullName).Hash)" })
$n1 = LogCount
$u = RunUpdater @('-AfterEclipse')
# פותחים את Eclipse אחרי הבדיקה הראשונה של ההתקנה (42%) — בזמן חבילת C# (44–82%), לפני הקבצים של Eclipse (86%), כמו שקרה לתלמיד
$mid = WaitFor { if ($u.HasExited) { return $true }; $t = ''; try { $t = (Get-Content $progress -Raw -ErrorAction Stop).Trim() } catch { }; $m = [regex]::Match($t, '^install (\d+)$'); $m.Success -and [int]$m.Groups[1].Value -ge 44 } 900
Check 'b-install-midway' ($mid -and -not $u.HasExited) 'ההתקנה עברה את הבדיקה הראשונה של Eclipse (42%) — עכשיו חבילת C#'
StartEclipse
$null = WaitFor { $u.HasExited } 900
ShowRun $u
(LogSince $n1) | ForEach-Object { Info "   update.log: $_" }
$names = @(Get-ChildItem "$fe\dropins" -Filter '*.jar' | ForEach-Object { $_.Name })
Info ("dropins: " + ($names -join ', '))
Check 'b-new-beside-plugin' (@($names | Where-Object { $_ -match "^il\.lamerhav\.dsviz_.*\.v$real\.jar$" }).Count -eq 1) "התוסף החדש נכתב ליד הישן (…v$real.jar)"
Check 'b-new-beside-bank' (@($names | Where-Object { $_ -match "^il\.lamerhav\.dsviz\.bank_.*\.v$real\.jar$" }).Count -le 1) 'המאגר: לכל היותר עותק חדש אחד ליד הישן (אותו קובץ — לא משוכפל)'
$still = @(Get-ChildItem "$fe\dropins" -Filter '*.jar' | ForEach-Object { "$($_.Name)=$((Get-FileHash $_.FullName).Hash)" })
Check 'b-old-untouched' (@($oldJars | Where-Object { $still -notcontains $_ }).Count -eq 0) 'בקבצים של Eclipse הפתוח (הנעולים) לא נגעו'
Check 'b-build-updated' ((BuildTxt) -eq $real) "build.txt = $real — העדכון הושלם גם כש-Eclipse נפתח באמצע"
Check 'b-pending-cleared' (-not (Test-Path (Join-Path $dsviz 'pending\pending.json'))) 'pending נמחק'
Check 'b-log' (((LogSince $n1) -join "`n") -match 'ההתקנה הסתיימה: גרסה') 'ביומן: "ההתקנה הסתיימה: גרסה ..."'

Log "== ג. ההתקנה הבאה, כש-Eclipse סגור, מנקה את הכפילות: נשאר תוסף אחד ומאגר אחד"
StopEclipse
$null = InstallDsviz $Setup '/S /UPDATE' 'עדכון כש-Eclipse סגור'
$names = @(Get-ChildItem "$fe\dropins" -Filter '*.jar' | ForEach-Object { $_.Name })
Info ("dropins: " + ($names -join ', '))
Check 'c-one-plugin' (@($names | Where-Object { $_ -like 'il.lamerhav.dsviz_*' }).Count -eq 1) 'תוסף אחד ב-dropins'
Check 'c-one-bank' (@($names | Where-Object { $_ -like 'il.lamerhav.dsviz.bank_*' }).Count -eq 1) 'מאגר אחד ב-dropins'

StopEclipse
Stop-Job $job -ErrorAction SilentlyContinue; Remove-Job $job -Force -ErrorAction SilentlyContinue
Copy-Item $updLog $Out -ErrorAction SilentlyContinue
Log ("SUMMARY {0} FAIL — עדכון אוטומטי: סגירה ופתיחה של Eclipse" -f $script:fails)
Save-Summary
exit ([int]($script:fails -gt 0))
