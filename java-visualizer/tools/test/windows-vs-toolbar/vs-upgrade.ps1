# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# המקרה שטיפלנו בו: סרגל הכלים של DSViz מופיע ב-Visual Studio, אבל "מאגר שאלות" / "השאלה" / "עמדת ניהול" לא לחיצים — כי המאגר
# (lab\dsviz-bank-vs.jar) לא הגיע למחשב.
#   1. לפני: הגרסה הציבורית הקודמת (master) + המצב של המשתמש (המאגר לא ב-lab\, מחובר כמורה) — צילום
#   2. עדכון לגרסה החדשה בשקט, כמו העדכון האוטומטי (/S /UPDATE) — כל הכפתורים גלויים ולחיצים, ו"מאגר שאלות" פותח את המאגר
#      העדכון מתחיל כש-Visual Studio עוד פתוח (כמו אצל משתמש: העדכון יורד בזמן העבודה) — מחכה שייסגר, ובפתיחה הראשונה אחרי זה הכל שם
#   3. גם כשההעתקה ל-lab\ לא קורית בכלל (PowerShell חסום, תיקיית המסמכים חסומה) — הכפתורים עובדים מהעותק ב-bank\
#   4. Visual Studio פתוח וחסר בו סרגל הכלים: העדכון האוטומטי (update.ps1) מחכה ברקע שייסגר, מתקין, ובפתיחה הבאה הסרגל שם
#   vs-upgrade.ps1 -Old <DSViz-Setup הקודם> -New <DSViz-Setup החדש> -Out <תיקייה> [-Product ...] [-OldInstalled]
param([string]$Old = '', [Parameter(Mandatory = $true)][string]$New, [Parameter(Mandatory = $true)][string]$Out, [string]$Product = '', [switch]$OldInstalled)
. (Join-Path $PSScriptRoot 'vs-helpers.ps1')
$dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'
$labJar = Join-Path $dsviz 'lab\dsviz-bank-vs.jar'
$bankJar = Join-Path $dsviz 'bank\dsviz-bank-vs.jar'
$flagDir = Join-Path $env:LOCALAPPDATA 'Lamerhav\.cache'
$flag = Join-Path $flagDir 'teacher.flag'
$bankBtns = @('מאגר שאלות', 'השאלה')
$allBtns = $script:dsvizNames + @('עמדת ניהול')

Log "== 0. Visual Studio"
PickVs $Product | Out-Null
Check 'env-vs' (Test-Path $script:devenv) "נבדק: $script:vsLabel"
$sln = NewFrameworkProject 'OOP'
$env:DSVIZ_VS_TRACE = '1'

# ------------------------------------------------------------------ 1. לפני
Log "== 1. לפני: הגרסה הקודמת, והמצב של המשתמש (המאגר לא ב-lab\, מחובר כמורה)"
if (-not $OldInstalled) {
    $c = InstallDsviz $Old '/S' 'הגרסה הקודמת'
    Check 'old-install' ($c -eq 0) 'הגרסה הקודמת הותקנה'
}
Remove-Item $labJar -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $flagDir | Out-Null
Set-Content $flag "teacher@test.dev`r`nמורה לבדיקה" -Encoding utf8
Info ("lab\dsviz-bank-vs.jar={0} · bank\dsviz-bank-vs.jar={1} · teacher.flag={2} · סרגל הכלים ב-Visual Studio: {3}" -f (Test-Path $labJar), (Test-Path $bankJar), (Test-Path $flag), (ExtensionIn))
$vsP = StartVs $sln
$st = WaitButtons $vsP @('תרגיל חדש') 180
Start-Sleep 20   # ההרחבה מסנכרנת את הכפתורים אחרי שהסרגל עולה
$st = ToolbarState $vsP.Id $allBtns
Shot '1-before-old-version'
ShowState $st
Info (DteToolbar)
# הגרסה הקודמת (master) כבר כוללת את התיקון ("מאגר שאלות" תמיד לחיץ) — אז אין יותר מה לשחזר; רק מתעדים את המצב לפני העדכון
Info ("לפני העדכון (הגרסה הקודמת שבקישור הקבוע): 'מאגר שאלות' = {0}" -f $st['מאגר שאלות'])

# ------------------------------------------------------------------ 2. עדכון
Log "== 2. עדכון לגרסה החדשה — בשקט, כמו העדכון האוטומטי (/S /UPDATE), כש-Visual Studio עוד פתוח"
$t = Get-Date
$up = Start-Process $New -ArgumentList '/S /UPDATE' -PassThru
Start-Sleep 60
$up.Refresh()
Shot '1b-update-started-vs-open'
Check 'update-waits-for-vs' (-not $up.HasExited) 'העדכון לא נוגע ב-Visual Studio הפתוח — מחכה שייסגר'
CloseVs $vsP
Info 'Visual Studio נסגר'
if (-not $up.WaitForExit(1800000)) { Info 'העדכון לא הסתיים תוך 30 דקות'; Stop-Process -Id $up.Id -Force -ErrorAction SilentlyContinue }
$c = $up.ExitCode
$b = if (Test-Path "$dsviz\build.txt") { (Get-Content "$dsviz\build.txt" -Raw).Trim() } else { '' }
Info ("עדכון: קוד {0}, {1:N0} שניות מההתחלה, build {2}" -f $c, ((Get-Date) - $t).TotalSeconds, $b)
Check 'update' ($c -eq 0) 'העדכון הסתיים אחרי שסגרו את Visual Studio'
Check 'update-lab-jar' (Test-Path $labJar) 'אחרי העדכון: lab\dsviz-bank-vs.jar במקום'
$ext = ExtensionIn
Check 'update-vsix' ($ext -ne '') "סרגל הכלים מותקן ב-$script:vsLabel" $ext
if (Test-Path (Join-Path $dsviz 'vsix-install.log')) {
    Copy-Item (Join-Path $dsviz 'vsix-install.log') $Out
    Get-Content (Join-Path $dsviz 'vsix-install.log') -Encoding UTF8 | Select-Object -Last 12 | ForEach-Object { Info "   vsix-install.log: $_" }
}
Log "== הפתיחה הראשונה של Visual Studio אחרי העדכון (בלי עוד סגירה ופתיחה)"
$vsP = StartVs $sln
$st = WaitButtons $vsP $allBtns 180
Shot '2-after-update'
ShowState $st
Info (DteToolbar)
foreach ($n in $allBtns) { Check 'after-button' (Usable $st[$n]) "אחרי העדכון, בפתיחה הראשונה: '$n' גלוי ולחיץ" $st[$n] }
$title = ClickBank $vsP '3-after-update-bank-click'
Check 'after-bank-opens' ($title -ne '') "אחרי העדכון: לחיצה על 'מאגר שאלות' בסרגל פותחת את המאגר — '$title'" ((Windows) -join ' ; ')
CloseVs $vsP

# ------------------------------------------------------------------ 3. בלי lab\
Log "== 3. גם בלי lab\ (ההעתקה לא קרתה — PowerShell חסום, תיקיית המסמכים חסומה): העותק ב-bank\ מספיק"
Remove-Item $labJar -Force -ErrorAction SilentlyContinue
Info ("lab\dsviz-bank-vs.jar={0} · bank\dsviz-bank-vs.jar={1}" -f (Test-Path $labJar), (Test-Path $bankJar))
$vsP = StartVs $sln
$st = WaitButtons $vsP $bankBtns 180
Shot '4-no-lab-fallback'
ShowState $st
foreach ($n in $bankBtns) { Check 'fallback-button' (Usable $st[$n]) "בלי lab\: '$n' גלוי ולחיץ (המאגר מ-bank\)" $st[$n] }
$title = ClickBank $vsP '5-no-lab-bank-click'
Check 'fallback-bank-opens' ($title -ne '') "בלי lab\: 'מאגר שאלות' פותח את המאגר — '$title'" ((Windows) -join ' ; ')
Check 'fallback-repaired' (Test-Path $labJar) "הלחיצה תיקנה: lab\dsviz-bank-vs.jar הועתק מ-bank\"
CloseVs $vsP

# ------------------------------------------------------------------ 4. Visual Studio פתוח וחסר בו סרגל הכלים — העדכון האוטומטי
Log "== 4. Visual Studio פתוח וחסר בו סרגל הכלים: העדכון האוטומטי (update.ps1) מתקין כשהוא נסגר"
$vsixPs = Join-Path $dsviz 'cs\install-vsix.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $vsixPs -Uninstall | Out-Null
Check 'heal-prep' ((ExtensionIn) -eq '') 'הכנה: סרגל הכלים הוסר (כמו אצל המשתמש — לא הותקן)'
$vsP = StartVs $sln
Start-Sleep 30
$st = ToolbarState $vsP.Id $script:dsvizNames
Shot '6-vs-open-toolbar-missing'
Check 'heal-repro' ($st['תרגיל חדש'] -eq 'absent') 'Visual Studio פתוח בלי סרגל הכלים של DSViz' $st['תרגיל חדש']
Info 'מפעילים את העדכון האוטומטי (update.ps1 -FromIde — כמו כשפותחים כלי של DSViz), Visual Studio פתוח'
$u = Start-Process powershell.exe -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$(Join-Path $dsviz 'update.ps1')`"", '-FromIde') -PassThru -WindowStyle Hidden
if (-not $u.WaitForExit(600000)) { Info 'update.ps1 לא הסתיים תוך 10 דקות' }
Get-Content (Join-Path $dsviz 'update.log') -Encoding UTF8 -ErrorAction SilentlyContinue | Select-Object -Last 4 | ForEach-Object { Info "   update.log: $_" }
$waiter = @(Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" | Where-Object { $_.CommandLine -like '*install-vsix.ps1*WhenClosed*' })
Check 'heal-waiting' ($waiter.Count -gt 0) 'תהליך ברקע מחכה ש-Visual Studio ייסגר כדי להתקין את סרגל הכלים'
Check 'heal-not-while-open' ((ExtensionIn) -eq '') 'בזמן ש-Visual Studio פתוח לא נוגעים בו'
CloseVs $vsP
Info 'Visual Studio נסגר'
foreach ($w in $waiter) { try { Wait-Process -Id $w.ProcessId -Timeout 600 -ErrorAction SilentlyContinue } catch { } }
Start-Sleep 2
Shot '7-toolbar-ready-toast'
Get-Content (Join-Path $dsviz 'vsix-install.log') -Encoding UTF8 -ErrorAction SilentlyContinue | Select-Object -Last 4 | ForEach-Object { Info "   vsix-install.log: $_" }
$ext = ExtensionIn
Check 'heal-installed' ($ext -ne '') 'אחרי הסגירה: סרגל הכלים הותקן ברקע' $ext
Log "== פתיחה הבאה של Visual Studio"
$vsP = StartVs $sln
$st = WaitButtons $vsP $allBtns 180
Shot '8-reopen-toolbar-back'
ShowState $st
foreach ($n in $allBtns) { Check 'heal-reopen' (Usable $st[$n]) "בפתיחה הבאה: '$n' גלוי ולחיץ" $st[$n] }
CloseVs $vsP

Get-ChildItem "$env:APPDATA\Microsoft\VisualStudio" -Recurse -Filter ActivityLog.xml -ErrorAction SilentlyContinue | ForEach-Object { Copy-Item $_.FullName (Join-Path $Out ('ActivityLog-' + $_.Directory.Name + '.xml')) -ErrorAction SilentlyContinue }
if (Test-Path (Join-Path $dsviz 'vs-trace.log')) { Copy-Item (Join-Path $dsviz 'vs-trace.log') $Out }
Log ("SUMMARY {0} FAIL — שדרוג ב-{1}" -f $script:fails, $script:vsLabel)
Save-Summary
exit ([int]($script:fails -gt 0))
