# הרחבת DSViz ל-Visual Studio (csharp/vs-extension) ב-Visual Studio האמיתי של המחשב: מתקין את ה-VSIX, פותח פרויקט (מדלג על חלון
# ההפעלה הראשונה כמו תלמיד), מחכה שההרחבה תיטען, ומחפש בסרגל את הכפתורים של DSViz (UI Automation). צילומי מסך + summary.txt.
param([string]$Vsix, [string]$Out, [string]$Installer)
$ErrorActionPreference = 'Continue'
New-Item -ItemType Directory -Force $Out | Out-Null
$summary = Join-Path $Out 'summary.txt'
function Info($m) { Write-Host $m; Add-Content $summary $m -Encoding utf8 }
$script:fail = 0
function Check($id, [bool]$ok, $what, $detail = '') { if ($ok) { Info "  ok   $id  $what" } else { $script:fail++; Info "  FAIL $id  $what  ($detail)" } }
Add-Type -AssemblyName System.Windows.Forms, System.Drawing, UIAutomationClient, UIAutomationTypes
Add-Type -Namespace Win -Name Mouse -MemberDefinition '[DllImport("user32.dll")] public static extern void mouse_event(int f, int dx, int dy, int d, int e); [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y); [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd); [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);'
function Shot([string]$name) {
    try {
        $b = [Windows.Forms.SystemInformation]::VirtualScreen
        $bmp = New-Object Drawing.Bitmap $b.Width, $b.Height
        $g = [Drawing.Graphics]::FromImage($bmp); $g.CopyFromScreen($b.Left, $b.Top, 0, 0, $bmp.Size)
        $bmp.Save((Join-Path $Out "$name.png")); $g.Dispose(); $bmp.Dispose(); Info "צילום: $name.png"
    } catch { Info "צילום $name נכשל: $_" }
}
function ClickEl($el) {
    try { $el.GetCurrentPattern([Windows.Automation.InvokePattern]::Pattern).Invoke(); return 'invoke' } catch { }
    try {
        $r = $el.Current.BoundingRectangle
        [Win.Mouse]::SetCursorPos([int]($r.X + $r.Width / 2), [int]($r.Y + $r.Height / 2)) | Out-Null
        Start-Sleep -Milliseconds 300
        [Win.Mouse]::mouse_event(2, 0, 0, 0, 0); [Win.Mouse]::mouse_event(4, 0, 0, 0, 0); return 'mouse'
    } catch { return $null }
}
function VsElements([int]$vsPid) {
    $cond = New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ProcessIdProperty, $vsPid)
    try { return @([Windows.Automation.AutomationElement]::RootElement.FindAll([Windows.Automation.TreeScope]::Descendants, $cond)) } catch { return @() }
}
function ElName($el) { try { return $el.Current.Name } catch { return '' } }
function SkipFirstRun([int]$vsPid) {
    $els = VsElements $vsPid
    foreach ($el in $els) {
        if ((ElName $el) -match 'Are you sure you want to exit') {
            foreach ($b in $els) { if ((ElName $b) -eq 'No' -and $b.Current.ControlType.ProgrammaticName -match 'Button') { ClickEl $b | Out-Null; return $true } }
        }
    }
    foreach ($pat in @('Skip and add accounts later', 'Not now, maybe later', 'Start Visual Studio', 'Continue without code')) {
        foreach ($el in $els) {
            $n = ElName $el
            if ($n -and $n -match $pat -and $el.Current.ControlType.ProgrammaticName -match 'Button|Hyperlink|Text') { if (ClickEl $el) { Info "חלון הפעלה ראשונה: '$n'"; return $true } }
        }
    }
    return $false
}

$vw = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$vs = & $vw -latest -prerelease -products * -requires Microsoft.Component.MSBuild -property installationPath
$devenv = Join-Path $vs 'Common7\IDE\devenv.exe'
Info "Visual Studio: $vs"
Info ("גרסת Visual Studio: " + (& $vw -latest -prerelease -products * -property catalog_productDisplayVersion))
& $Installer $Vsix
Start-Sleep 5
# היומן של VSIXInstaller — למה ההתקנה נכשלה (אם נכשלה)
Get-ChildItem $env:TEMP -Filter 'dd_VSIXInstaller*.log' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1 | ForEach-Object {
    Info "יומן ההתקנה: $($_.FullName)"
    Copy-Item $_.FullName (Join-Path $Out 'vsixinstaller.log') -ErrorAction SilentlyContinue
    (Get-Content $_.FullName -Tail 40) | ForEach-Object { Info "  | $_" }
}
$found = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\VisualStudio", "$env:ProgramData\Microsoft\VisualStudio" -Recurse -Filter extension.vsixmanifest -ErrorAction SilentlyContinue |
    Where-Object { (Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue) -match 'DsViz.VisualStudio.Toolbar' }
Check 'vsix-installed' (@($found).Count -gt 0) 'ההרחבה הותקנה ב-Visual Studio (extension.vsixmanifest נמצא)' "$(@($found).Count)"
Start-Sleep 2
Start-Process $devenv -ArgumentList '/updateconfiguration' -Wait -WindowStyle Hidden

$proj = Join-Path $env:RUNNER_TEMP 'Targil1'
dotnet new console -n Targil1 -o $proj --force | Out-Null
$t0 = Get-Date
$p = Start-Process $devenv -ArgumentList "`"$proj\Targil1.csproj`"" -PassThru
$ready = $false
while (((Get-Date) - $t0).TotalMinutes -lt 9) {
    Start-Sleep 5
    $p.Refresh()
    if ($p.HasExited) { Info "devenv נסגר (קוד $($p.ExitCode))"; break }
    if (SkipFirstRun $p.Id) { Start-Sleep 3 }
    $p.Refresh()
    if ($p.MainWindowTitle -like '*- Microsoft Visual Studio*') { $ready = $true; break }
}
Info ("Visual Studio: {0:N0} שניות, כותרת '{1}'" -f ((Get-Date) - $t0).TotalSeconds, $p.MainWindowTitle)
Check 'vs-open' $ready 'Visual Studio נפתח' $p.MainWindowTitle
if ($ready) {
    [Win.Mouse]::ShowWindow($p.MainWindowHandle, 3) | Out-Null
    [Win.Mouse]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
    Start-Sleep 20; SkipFirstRun $p.Id | Out-Null
    $names = @()
    for ($i = 0; $i -lt 18; $i++) {   # עד 90 שניות: ההרחבה (תהליך נפרד) נטענת אחרי שהחלון עולה
        Start-Sleep 5
        $names = @(VsElements $p.Id | ForEach-Object { ElName $_ } | Where-Object { $_ -match 'תרגיל חדש|מאגר שאלות|הוספת מבנה נתונים|שליחת תרגיל|הצעה לפתרון|DSViz' })
        if ($names.Count -ge 3) { break }
    }
    Shot 'vs-toolbar'
    ($names | Select-Object -Unique) | Set-Content (Join-Path $Out 'toolbar-uia.txt') -Encoding utf8
    Info "רכיבים של DSViz ב-Visual Studio: $((($names | Select-Object -Unique)) -join ' ; ')"
    Check 'toolbar-buttons' (@($names | Select-Object -Unique).Count -ge 3) 'בסרגל הכלים של DSViz נמצאו הכפתורים (תרגיל חדש / מאגר שאלות ...)' (($names | Select-Object -Unique) -join ' ; ')
    if ($names.Count -eq 0) {
        # הסרגל לא מוצג כברירת מחדל? פותחים View ← Toolbars בצילום
        $wsh = New-Object -ComObject WScript.Shell; [void]$wsh.AppActivate($p.Id)
        $wsh.SendKeys('%v'); Start-Sleep 2; Shot 'vs-view-menu'
        $wsh.SendKeys('{ESC}')
    }
}
Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force
Info "כישלונות: $script:fail"
exit $(if ($script:fail -gt 0) { 1 } else { 0 })
