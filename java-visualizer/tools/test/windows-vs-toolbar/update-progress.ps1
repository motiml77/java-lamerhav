# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# אחוזי ההורדה וההתקנה בעדכון האוטומטי: מריצים את update.ps1 האמיתי של ההתקנה, מול שרת מקומי איטי (מוריד את ה-DSViz-Setup.exe החדש
# בכ-15 שניות, כמו חיבור אמיתי), ובודקים שהחלון הקטן בפינה (notify.ps1) מראה אחוזים אמיתיים — קודם בהורדה ואחר כך בהתקנה — ולא נעלם באמצע.
# כל מה שהחלון מציג מגיע מקובץ ההתקדמות ‎%TEMP%\dsviz-progress.txt‎ ("download 37" / "install 55"), ולכן בודקים גם את הרצף שנכתב בו.
#   update-progress.ps1 -Setup <DSViz-Setup.exe החדש> -Out <תיקייה>
param([Parameter(Mandatory = $true)][string]$Setup, [Parameter(Mandatory = $true)][string]$Out)
. (Join-Path $PSScriptRoot 'vs-helpers.ps1')
$dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'
$progress = Join-Path $env:TEMP 'dsviz-progress.txt'
$upd = Join-Path $dsviz 'update.ps1'
$port = 18765

Log "== 0. ההתקנה החדשה במחשב"
$has = (Test-Path $upd) -and ((Get-Content $upd -Raw -Encoding UTF8) -match 'dsviz-progress')
if (-not $has) {
    $c = InstallDsviz $Setup '/S' 'התקנה'
    $has = (Test-Path $upd) -and ((Get-Content $upd -Raw -Encoding UTF8) -match 'dsviz-progress')
}
Check 'update-has-progress' $has 'update.ps1 המותקן מדווח התקדמות (dsviz-progress.txt)'
if (-not $has) { Save-Summary; exit 1 }
$notify = Join-Path $dsviz 'notify.ps1'
Check 'notify-has-percent' ((Get-Content $notify -Raw -Encoding UTF8) -match 'ProgressFile') 'notify.ps1 המותקן קורא את קובץ ההתקדמות'

Log "== 1. שרת מקומי איטי: version.json (גרסה חדשה יותר) והקובץ — כ-6 MB בשנייה"
$sha = (Get-FileHash $Setup -Algorithm SHA256).Hash.ToLower()
$ver = (@{ version = '1.0.1'; build = [int64]99990101000000; url = "http://127.0.0.1:$port/DSViz-Setup.exe"; sha256 = $sha } | ConvertTo-Json)
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
                $c.Response.ContentType = 'application/json'
                $c.Response.ContentLength64 = $b.Length
                $c.Response.OutputStream.Write($b, 0, $b.Length)
            } elseif ($c.Request.Url.AbsolutePath -eq '/DSViz-Setup.exe') {
                $fs = [IO.File]::OpenRead($exe)
                $c.Response.ContentLength64 = $fs.Length
                $buf = New-Object byte[] 262144
                while (($n = $fs.Read($buf, 0, $buf.Length)) -gt 0) {
                    $c.Response.OutputStream.Write($buf, 0, $n)
                    Start-Sleep -Milliseconds 40
                }
                $fs.Close()
            }
        } catch { }
        try { $c.Response.Close() } catch { }
    }
}
$up = $false
for ($i = 0; $i -lt 30 -and -not $up; $i++) {
    try { $null = Invoke-WebRequest "http://127.0.0.1:$port/version.json" -UseBasicParsing -TimeoutSec 3; $up = $true } catch { Start-Sleep 1 }
}
Check 'server-up' $up 'השרת המקומי עלה'
if (-not $up) { Stop-Job $job; Remove-Job $job -Force; Save-Summary; exit 1 }

Log "== 2. העדכון האוטומטי (update.ps1 -FromIde): הורדה, ואחר כך התקנה — דוגמים את קובץ ההתקדמות וצילומי מסך"
Remove-Item $progress -Force -ErrorAction SilentlyContinue
$env:DSVIZ_VERSION_URL = "http://127.0.0.1:$port/version.json"
$u = Start-Process powershell.exe -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$upd`"", '-FromIde') -PassThru -WindowStyle Hidden
Remove-Item Env:\DSVIZ_VERSION_URL -ErrorAction SilentlyContinue
function ToastRunning([string]$state) {
    return @(Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*notify.ps1*' -and $_.CommandLine -like "*-State $state*" }).Count -gt 0
}
$seq = New-Object System.Collections.Generic.List[string]
$shots = @{}
$toast = @{}
$t0 = Get-Date
while (-not $u.HasExited -and ((Get-Date) - $t0).TotalMinutes -lt 25) {
    $txt = ''
    try { $txt = (Get-Content $progress -Raw -ErrorAction Stop).Trim() } catch { }
    if ($txt -and ($seq.Count -eq 0 -or $seq[$seq.Count - 1] -ne $txt)) { $seq.Add($txt) }
    $m = [regex]::Match($txt, '^(\w+)\s+(-?\d+)$')
    if ($m.Success) {
        $k = $m.Groups[1].Value; $pc = [int]$m.Groups[2].Value
        foreach ($s in @(@('download', 25, 'u1-download-25'), @('download', 70, 'u2-download-70'), @('install', 12, 'u3-install-12'), @('install', 44, 'u4-install-44'), @('install', 68, 'u5-install-68'), @('install', 86, 'u6-install-86'))) {
            if ($k -eq $s[0] -and $pc -ge $s[1] -and -not $shots[$s[2]]) {
                $shots[$s[2]] = $true
                $toast[$s[2]] = ToastRunning $(if ($k -eq 'download') { 'download' } else { 'updating' })
                Shot $s[2]
                Info ("{0}: קובץ ההתקדמות = '{1}' · החלון פתוח: {2}" -f $s[2], $txt, $toast[$s[2]])
            }
        }
    }
    Start-Sleep -Milliseconds 300
}
if (-not $u.HasExited) { Info 'העדכון לא הסתיים תוך 25 דקות'; try { $u.Kill() } catch { } }
Stop-Job $job -ErrorAction SilentlyContinue; Remove-Job $job -Force -ErrorAction SilentlyContinue
Info ("הרצף שנכתב בקובץ ההתקדמות ({0} ערכים): {1}" -f $seq.Count, (($seq | Select-Object -First 400) -join ' | '))

Log "== 3. בדיקות"
$dl = @($seq | Where-Object { $_ -match '^download (\d+)$' } | ForEach-Object { [int]($_ -split ' ')[1] })
$ins = @($seq | Where-Object { $_ -match '^install (\d+)$' } | ForEach-Object { [int]($_ -split ' ')[1] })
$mono = { param($a) $ok = $true; for ($i = 1; $i -lt $a.Count; $i++) { if ($a[$i] -lt $a[$i - 1]) { $ok = $false } }; $ok }
# הדגימה כל 300 מילישניות — הערך האחרון בהורדה (100) מוחלף מיד ב-"ready", ולכן מספיק שהגענו קרוב לסוף
Check 'download-percent' ($dl.Count -ge 5 -and $dl[0] -le 5 -and $dl[$dl.Count - 1] -ge 95) ("ההורדה דיווחה {0} אחוזים, מ-{1} עד {2}" -f $dl.Count, $(if ($dl.Count) { $dl[0] } else { '?' }), $(if ($dl.Count) { $dl[$dl.Count - 1] } else { '?' }))
Check 'download-monotonic' (& $mono $dl) 'אחוזי ההורדה רק עולים'
Check 'install-percent' ($ins.Count -ge 5 -and $ins[0] -le 10 -and $ins[$ins.Count - 1] -ge 90) ("ההתקנה דיווחה {0} שלבים, מ-{1} עד {2}: {3}" -f $ins.Count, $(if ($ins.Count) { $ins[0] } else { '?' }), $(if ($ins.Count) { $ins[$ins.Count - 1] } else { '?' }), ($ins -join ','))
Check 'install-monotonic' (& $mono $ins) 'שלבי ההתקנה רק עולים'
Check 'toast-download-open' ([bool]$toast['u1-download-25'] -and [bool]$toast['u2-download-70']) 'החלון עם אחוזי ההורדה פתוח לאורך ההורדה'
Check 'toast-install-open' ([bool]$toast['u3-install-12'] -and [bool]$toast['u4-install-44'] -and [bool]$toast['u5-install-68']) 'החלון עם אחוזי ההתקנה פתוח לאורך ההתקנה'
$log = if (Test-Path (Join-Path $dsviz 'update.log')) { @(Get-Content (Join-Path $dsviz 'update.log') -Encoding UTF8 | Select-Object -Last 8) } else { @() }
$log | ForEach-Object { Info "   update.log: $_" }
Check 'update-finished' (($log -join "`n") -match 'ההתקנה הסתיימה \(קוד 0\)') 'העדכון הסתיים בהצלחה (קוד 0)'
Start-Sleep 12
Check 'toast-closed' (-not (ToastRunning 'download') -and -not (ToastRunning 'updating')) 'אחרי העדכון חלון ההתקדמות נסגר (לא נשאר תקוע)'
Copy-Item (Join-Path $dsviz 'update.log') $Out -ErrorAction SilentlyContinue
Log ("SUMMARY {0} FAIL — אחוזי עדכון" -f $script:fails)
Save-Summary
exit ([int]($script:fails -gt 0))
