# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# Visual Studio כמו אצל משתמש אמיתי: מתקינים את DSViz, פותחים פרויקט Console App (.NET Framework) — ובודקים אם סרגל הכלים של DSViz מופיע,
# גלוי ולחיץ. בלי devenv /updateconfiguration ובלי התקנה ידנית של ההרחבה (משתמש לא עושה את זה) — שלוש פתיחות:
#   1. הפתיחה הראשונה אחרי ההתקנה   2. פתיחה שנייה   3. אחרי devenv /updateconfiguration (רק לאבחון: אם רק כאן הסרגל מופיע — זו הסיבה)
# אוספים: VSIXInstaller, ActivityLog.xml, יומן ההתקנה של ההרחבה (vsix-install.log), vs-trace.log, ההרחבות שמופעלות ב-Visual Studio.
#   vs-real-user.ps1 -Setup DSViz-Setup.exe -Out <תיקייה> [-Product Microsoft.VisualStudio.Product.Community] [-SetupArgs '/S']
param([Parameter(Mandatory = $true)][string]$Setup, [Parameter(Mandatory = $true)][string]$Out, [string]$Product = 'Microsoft.VisualStudio.Product.Community', [string]$SetupArgs = '/S')
. (Join-Path $PSScriptRoot 'vs-helpers.ps1')
$dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'

Log "== 0. Visual Studio במחשב (עם יותר ממופע אחד — כמו אצל רוב המורים: 2022 + 2026, Build Tools, SSMS)"
PickVs $Product | Out-Null
Check 'env-vs' (Test-Path $script:devenv) "נבדק: $script:vsLabel" $script:devenv

# ------------------------------------------------------------------ 1. התקנה (Visual Studio סגור, כמו שההתקנה דורשת)
Log "== 1. התקנה: $Setup $SetupArgs"
$c = InstallDsviz $Setup $SetupArgs 'התקנה'
foreach ($f in 'build.txt', 'lang.txt') { $pf = Join-Path $dsviz $f; Info ("{0} = {1}" -f $f, $(if (Test-Path $pf) { (Get-Content $pf -Raw).Trim() } else { '(אין)' })) }
Check 'install' ($c -eq 0 -and (Test-Path (Join-Path $dsviz 'dsviz.jar'))) 'DSViz הותקן'
foreach ($f in 'lab\dsviz-bank-vs.jar', 'bank\dsviz-bank-vs.jar', 'cs\DsViz.VsExtension.vsix', 'vsix-install.log') { Info ("{0}: {1}" -f $f, (Test-Path (Join-Path $dsviz $f))) }
if (Test-Path (Join-Path $dsviz 'vsix-install.log')) {
    Copy-Item (Join-Path $dsviz 'vsix-install.log') $Out
    Get-Content (Join-Path $dsviz 'vsix-install.log') -Encoding UTF8 | ForEach-Object { Info "   vsix-install.log: $_" }
}
foreach ($i in (VsInstances)) {
    $dir = Join-Path $env:LOCALAPPDATA ('Microsoft\VisualStudio\{0}.0_{1}\Extensions' -f ([version]$i.installationVersion).Major, $i.instanceId)
    $m = @(Get-ChildItem $dir -Recurse -Filter extension.vsixmanifest -ErrorAction SilentlyContinue | Where-Object { (Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue) -match 'DsViz.VisualStudio.Toolbar' })
    Info ("   סרגל הכלים ב-{0} {1}: {2}" -f $i.displayName, $i.catalog.productDisplayVersion, $(if ($m.Count) { $m[0].Directory.FullName } else { 'לא מותקן' }))
}
$ext = ExtensionIn
Check 'vsix-in-instance' ($ext -ne '') "סרגל הכלים (ההרחבה) הותקן ב-$script:vsLabel" $ext
$vl = Join-Path $Out 'vsixinstaller'
New-Item -ItemType Directory -Force $vl | Out-Null
Get-ChildItem $env:TEMP -Filter 'dd_VSIXInstaller*.log' -ErrorAction SilentlyContinue | ForEach-Object { Copy-Item $_.FullName $vl -ErrorAction SilentlyContinue }
Info "יומני VSIXInstaller: $(@(Get-ChildItem $vl).Count)"
foreach ($l in (Get-ChildItem $vl -ErrorAction SilentlyContinue)) {
    Select-String -Path $l.FullName -Pattern 'error|exception|not compatible|blocking|already installed|Install to|Installed' -ErrorAction SilentlyContinue | Select-Object -First 8 | ForEach-Object { Info ("   {0}: {1}" -f $l.Name, $_.Line.Trim()) }
}

# ------------------------------------------------------------------ 2. פרויקט Console App (.NET Framework) ופתיחות של Visual Studio
Log "== 2. פרויקט Console App (.NET Framework) — כמו 'OOP' של המשתמש"
$sln = NewFrameworkProject 'OOP'
$env:DSVIZ_VS_TRACE = '1'   # יומן ההרחבה (vs-trace.log) — רק בבדיקה
function OpenAndCheck([string]$tag) {
    Log "== פתיחה: $tag"
    $vsP = StartVs $sln
    $state = WaitButtons $vsP $script:dsvizNames 180
    Start-Sleep 3
    Shot "$tag-toolbar"
    ShowState $state
    Info (DteToolbar)
    $rel = @(VsElements $vsP.Id | ForEach-Object { ElName $_ } | Where-Object { $_ -match 'DSViz|מאגר|תרגיל|הרצה ויזואלית|הדרכה|מבנה נתונים|עמדת ניהול' } | Select-Object -Unique)
    Info "רכיבים של DSViz (UIA): $($rel -join ' ; ')"
    $ok = @($script:dsvizNames | Where-Object { Usable $state[$_] }).Count -eq $script:dsvizNames.Count
    Check "toolbar-$tag" $ok "$tag — סרגל הכלים של DSViz מופיע, וכל הכפתורים גלויים ולחיצים"
    CloseVs $vsP
    return $ok
}
$r1 = OpenAndCheck 'first'
$r2 = OpenAndCheck 'second'
Log "== devenv /updateconfiguration (לאבחון בלבד — משתמש לא מריץ את זה)"
Start-Process $script:devenv -ArgumentList '/updateconfiguration' -Wait -WindowStyle Hidden
$r3 = OpenAndCheck 'after-updateconfiguration'
Info ("סיכום: פתיחה ראשונה={0}, שנייה={1}, אחרי updateconfiguration={2}" -f $r1, $r2, $r3)

# ------------------------------------------------------------------ 3. יומנים
Get-ChildItem "$env:APPDATA\Microsoft\VisualStudio" -Recurse -Filter ActivityLog.xml -ErrorAction SilentlyContinue | ForEach-Object {
    Copy-Item $_.FullName (Join-Path $Out ('ActivityLog-' + $_.Directory.Name + '.xml')) -ErrorAction SilentlyContinue
    Select-String -Path $_.FullName -Pattern 'DsViz|7b2f4d1e' -ErrorAction SilentlyContinue | Select-Object -First 20 | ForEach-Object {
        $line = $_.Line.Trim() -replace '\s+', ' '
        Info ("   ActivityLog: " + $line.Substring(0, [Math]::Min(300, $line.Length)))
    }
}
foreach ($f in 'vs-trace.log', 'update.log') { $pf = Join-Path $dsviz $f; if (Test-Path $pf) { Copy-Item $pf $Out } }
# ההרחבות שמופעלות (privateregistry.bin של Visual Studio, אחרי שנסגר)
$hive = Join-Path $script:vsHiveDir 'privateregistry.bin'
if (Test-Path $hive) {
    & reg.exe load 'HKU\DSVizVsHive' $hive 2>&1 | Out-Null
    $key = "HKU\DSVizVsHive\Software\Microsoft\VisualStudio\{0}.0_{1}\ExtensionManager" -f $script:vsMajor, $script:vsInst.instanceId
    foreach ($sub in 'EnabledExtensions', 'PendingDeletions') {
        $q = & reg.exe query "$key\$sub" 2>&1
        Info ("{0}: {1}" -f $sub, ((@($q) | Where-Object { "$_" -match 'DsViz' }) -join ' | '))
    }
    [gc]::Collect(); Start-Sleep 1
    & reg.exe unload 'HKU\DSVizVsHive' 2>&1 | Out-Null
} else { Info "אין privateregistry.bin ב-$($script:vsHiveDir)" }
Log ("SUMMARY {0} FAIL — {1}" -f $script:fails, $script:vsLabel)
Save-Summary
exit ([int]($script:fails -gt 0))
