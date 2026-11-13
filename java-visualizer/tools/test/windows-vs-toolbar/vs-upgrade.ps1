# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# המקרה שטיפלנו בו: סרגל הכלים של DSViz מופיע ב-Visual Studio, אבל "מאגר שאלות" / "השאלה" / "עמדת ניהול" לא לחיצים — כי המאגר
# (lab\dsviz-bank-vs.jar) לא הגיע למחשב.
#   1. לפני: הגרסה הציבורית הקודמת (master) + המצב של המשתמש (המאגר לא ב-lab\, מחובר כמורה) — צילום
#   2. עדכון לגרסה החדשה בשקט, כמו העדכון האוטומטי (/S /UPDATE) — כל הכפתורים גלויים ולחיצים, ו"מאגר שאלות" פותח את המאגר
#   3. גם כשההעתקה ל-lab\ לא קורית בכלל (PowerShell חסום, תיקיית המסמכים חסומה) — הכפתורים עובדים מהעותק ב-bank\
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
Check 'before-repro' (-not (Usable $st['מאגר שאלות'])) "לפני (הגרסה הקודמת): 'מאגר שאלות' לא לחיץ — המצב שהמשתמש ראה" $st['מאגר שאלות']
CloseVs $vsP

# ------------------------------------------------------------------ 2. עדכון
Log "== 2. עדכון לגרסה החדשה — בשקט, כמו העדכון האוטומטי (/S /UPDATE)"
$c = InstallDsviz $New '/S /UPDATE' 'עדכון'
Check 'update' ($c -eq 0) 'העדכון הסתיים'
Check 'update-lab-jar' (Test-Path $labJar) 'אחרי העדכון: lab\dsviz-bank-vs.jar במקום'
$ext = ExtensionIn
Check 'update-vsix' ($ext -ne '') "סרגל הכלים מותקן ב-$script:vsLabel" $ext
if (Test-Path (Join-Path $dsviz 'vsix-install.log')) {
    Copy-Item (Join-Path $dsviz 'vsix-install.log') $Out
    Get-Content (Join-Path $dsviz 'vsix-install.log') -Encoding UTF8 | Select-Object -Last 12 | ForEach-Object { Info "   vsix-install.log: $_" }
}
$vsP = StartVs $sln
$st = WaitButtons $vsP $allBtns 180
Shot '2-after-update'
ShowState $st
Info (DteToolbar)
foreach ($n in $allBtns) { Check 'after-button' (Usable $st[$n]) "אחרי העדכון: '$n' גלוי ולחיץ" $st[$n] }
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

Get-ChildItem "$env:APPDATA\Microsoft\VisualStudio" -Recurse -Filter ActivityLog.xml -ErrorAction SilentlyContinue | ForEach-Object { Copy-Item $_.FullName (Join-Path $Out ('ActivityLog-' + $_.Directory.Name + '.xml')) -ErrorAction SilentlyContinue }
if (Test-Path (Join-Path $dsviz 'vs-trace.log')) { Copy-Item (Join-Path $dsviz 'vs-trace.log') $Out }
Log ("SUMMARY {0} FAIL — שדרוג ב-{1}" -f $script:fails, $script:vsLabel)
Save-Summary
exit ([int]($script:fails -gt 0))
