$instances = @(& $vswhere -all -prerelease -products * -format json | ConvertFrom-Json)
foreach ($i in $instances) {
    $inst = Join-Path $i.installationPath 'Common7\IDE\VSIXInstaller.exe'
    if (-not (Test-Path $inst)) { continue }
    $argList = if ($Uninstall) { @('/quiet', "/instanceIds:$($i.instanceId)", "/uninstall:$id") } else { @('/quiet', "/instanceIds:$($i.instanceId)", "`\"$Vsix`\"") }
    Write-Host "Visual Studio $($i.catalog.productDisplayVersion) ב-$($i.installationPath)"
    $proc = Start-Process $inst -ArgumentList $argList -PassThru -WindowStyle Hidden
    if (-not $proc.WaitForExit(180000)) { try { $proc.Kill() } catch {} ; Write-Host '  לא הסתיים בזמן (ייתכן ש-Visual Studio פתוח) — מדלגים' }
    else { Write-Host "  קוד יציאה $($proc.ExitCode)" }
}
# מתקין / מסיר את ההרחבה של DSViz (סרגל הכלים) בכל Visual Studio שנמצא במחשב.
#   install-vsix.ps1 <DsViz.VsExtension.vsix>           — התקנה (למשתמש הנוכחי, בלי הרשאות מנהל)
#   install-vsix.ps1 -Uninstall                          — הסרה
# כל תקלה כאן לא עוצרת את ההתקנה של DSViz (Visual Studio ישן מ-17.14 לא מקבל את ההרחבה — והכפתורים נשארים בתפריט ההפעלה).
param([string]$Vsix = '', [switch]$Uninstall)
$ErrorActionPreference = 'Continue'
$id = 'DsViz.VisualStudio.Toolbar.6f3c1c0e-4a52-4e7e-9d1b-5b2f0c8e7a11'
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path $vswhere)) { Write-Host 'אין Visual Studio — מדלגים'; exit 0 }
$paths = & $vswhere -all -prerelease -products * -property installationPath
foreach ($p in $paths) {
    $inst = Join-Path $p 'Common7\IDE\VSIXInstaller.exe'
    if (-not (Test-Path $inst)) { continue }
    $argList = if ($Uninstall) { @('/quiet', "/uninstall:$id") } else { @('/quiet', "`"$Vsix`"") }
    Write-Host "Visual Studio ב-$p"
    $proc = Start-Process $inst -ArgumentList $argList -PassThru -WindowStyle Hidden
    if (-not $proc.WaitForExit(180000)) { try { $proc.Kill() } catch {} ; Write-Host '  לא הסתיים בזמן (ייתכן ש-Visual Studio פתוח) — מדלגים' }
    else { Write-Host "  קוד יציאה $($proc.ExitCode)" }
}
exit 0
