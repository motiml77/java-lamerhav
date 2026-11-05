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
if (@($found).Count -eq 0) { Info 'ההרחבה לא הותקנה — לא פותחים את Visual Studio (נכשלים מהר)'; Info "כישלונות: $script:fail"; exit 1 }
Start-Sleep 2
Start-Process $devenv -ArgumentList '/updateconfiguration' -Wait -WindowStyle Hidden

# תרגיל ש-DSViz "יצר": קובץ עם שתי פעולות שיש להן הצעה בחנות (~/.dsviz/solutions.json — אותו פורמט כמו של VsSubmit), וקובץ רגיל בלי הצעות
$exDir = Join-Path $env:RUNNER_TEMP 'Exercises\Ex01_Hints'
New-Item -ItemType Directory -Force $exDir | Out-Null
$body = @'
using System;
using System.Collections.Generic;

public class Targil
{
    public static int Sum(Stack<int> s, int k)
    {
        // כאן כותבים את התשובה
        return 0;
    }

    public static int Count(Dictionary<string, List<int>> d)
    {
        // כאן כותבים את התשובה
        return 0;
    }

    public static void Main(string[] args)
    {
        Console.WriteLine(Count(null));
    }
}
'@
$hinted = Join-Path $exDir 'Targil.cs'
$plain = Join-Path $exDir 'Plain.cs'
Set-Content $hinted $body -Encoding UTF8
Set-Content $plain ($body -replace 'class Targil', 'class Plain') -Encoding UTF8
$storeDir = Join-Path $env:USERPROFILE '.dsviz'
New-Item -ItemType Directory -Force $storeDir | Out-Null
$store = @{ solutions = @(
    @{ path = $hinted.Replace('\', '/'); class = 'Targil'; method = 'Sum'; params = @('Stack<int>', 'int'); label = 'א'; status = 'ok'; time = '2'; code = 'public static int Sum(Stack<int> s, int k) { return 1; }' },
    @{ path = $hinted.Replace('\', '/'); class = 'Targil'; method = 'Count'; params = @('Dictionary<string, List<int>>'); label = 'ב'; status = 'ok'; time = '1'; code = 'public static int Count(Dictionary<string, List<int>> d) { return 2; }' }
) }
[IO.File]::WriteAllText((Join-Path $storeDir 'solutions.json'), ($store | ConvertTo-Json -Depth 6), (New-Object Text.UTF8Encoding($false)))
Info "חנות ההצעות: $storeDir\solutions.json ; קובץ עם הצעות: $hinted"
$env:DSVIZ_VS_TRACE = '1'   # עקבות של הרכיב (MEF) גם ל-%LOCALAPPDATA%\DSViz\vs-trace.log
Remove-Item "$env:LOCALAPPDATA\DSViz\vs-trace.log" -Force -ErrorAction SilentlyContinue
Remove-Item "$env:LOCALAPPDATA\DSViz\lab" -Recurse -Force -ErrorAction SilentlyContinue   # בלי מאגר שאלות מותקן: הכפתור "מאגר שאלות" מוסתר

$proj = Join-Path $env:RUNNER_TEMP 'Targil1'
dotnet new console -n Targil1 -o $proj --force | Out-Null
$t0 = Get-Date
$p = Start-Process $devenv -ArgumentList "`"$proj\Targil1.csproj`" /log" -PassThru
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
    Start-Sleep 5; SkipFirstRun $p.Id | Out-Null
    $names = @()
    for ($i = 0; $i -lt 30; $i++) {   # עד 90 שניות: ההרחבה נטענת אחרי שהחלון עולה — יוצאים ברגע שהכפתורים נמצאו
        Start-Sleep 3
        $names = @(VsElements $p.Id | ForEach-Object { ElName $_ } | Where-Object { $_ -match 'תרגיל חדש|מאגר שאלות|הוספת מבנה נתונים|שליחת תרגיל|הרצה ויזואלית|DSViz' })
        if (@($names | Select-Object -Unique).Count -ge 4) { break }
    }
    Shot 'vs-toolbar'
    ($names | Select-Object -Unique) | Set-Content (Join-Path $Out 'toolbar-uia.txt') -Encoding utf8
    Info "רכיבים של DSViz ב-Visual Studio: $((($names | Select-Object -Unique)) -join ' ; ')"
    Check 'toolbar-buttons' (@($names | Select-Object -Unique).Count -ge 3) 'בסרגל הכלים של DSViz נמצאו הכפתורים (תרגיל חדש / הוספת מבנה נתונים ...)' (($names | Select-Object -Unique) -join ' ; ')
    # הכפתור "מאגר שאלות": האם הוא באמת נראה (קיים, מופעל ולא מחוץ למסך) — לא רק שהשם קיים בעץ
    function BankState() {
        $els = @(VsElements $p.Id | Where-Object { (ElName $_) -eq 'מאגר שאלות' })
        if ($els.Count -eq 0) { return 'absent' }
        $c = $els[0].Current
        return ("present enabled={0} offscreen={1} rect={2}" -f $c.IsEnabled, $c.IsOffscreen, $c.BoundingRectangle)
    }
    $bankBefore = BankState
    Info "מאגר שאלות (בלי jar): $bankBefore"
    Check 'bank-hidden' ($bankBefore -eq 'absent' -or $bankBefore -match 'offscreen=True' -or $bankBefore -match 'rect=Empty') 'בלי מאגר שאלות מותקן — הכפתור "מאגר שאלות" מוסתר' $bankBefore
    Shot 'vs-toolbar-nobank'
    # מאגר השאלות מותקן (קובץ ה-jar של המעבדה) אחרי ש-Visual Studio כבר פתוח: הכפתור מופיע בפעם הבאה ש-VS בודק את הפקודות
    # (למשל כשעוברים לקובץ אחר — בהמשך הבדיקה); אצל תלמיד ההתקנה קודמת להפעלה, והכפתור מוצג כבר בטעינה.
    New-Item -ItemType Directory -Force "$env:LOCALAPPDATA\DSViz\lab" | Out-Null
    Set-Content "$env:LOCALAPPDATA\DSViz\lab\dsviz-bank-vs.jar" 'dummy'

    # ---- "הצעה לפתרון" בתוך הקוד: פותחים את קובץ התרגיל (מופע ה-Visual Studio שפתוח) ומחפשים את המנורות בשולי העורך
    Start-Process $devenv -ArgumentList "/Edit `"$plain`"" -WindowStyle Hidden
    Start-Sleep 12
    Shot 'vs-plain'
    $plainLamps = @(VsElements $p.Id | Where-Object { (ElName $_) -eq 'הצעה לפתרון' }).Count
    Start-Process $devenv -ArgumentList "/Edit `"$hinted`"" -WindowStyle Hidden
    $lamps = @()
    for ($i = 0; $i -lt 30; $i++) {   # עד 90 שניות: שירות השפה של C# נטען לאט
        Start-Sleep 3
        $lamps = @(VsElements $p.Id | Where-Object { (ElName $_) -eq 'הצעה לפתרון' })
        if ($lamps.Count -ge 2) { break }
    }
    Start-Sleep 2
    Shot 'vs-glyph'
    Info "מנורות 'הצעה לפתרון' בעורך: $($lamps.Count) (בקובץ רגיל: $plainLamps)"
    Check 'glyph-lamps' ($lamps.Count -ge 2) 'ליד כל פעולה עם הצעה (Sum, Count) יש מנורה בשולי העורך' "$($lamps.Count)"
    Check 'plain-no-lamps' ($plainLamps -eq 0) 'בקובץ C# רגיל אין מנורות' "$plainLamps"
    $bankShown = $false; $bankAfter = ''
    for ($i = 0; $i -lt 10 -and -not $bankShown; $i++) {
        $bankAfter = BankState
        $bankShown = $bankAfter -match 'enabled=True offscreen=False'
        if (-not $bankShown) { Start-Sleep 3 }
    }
    Info "מאגר שאלות (עם jar): $bankAfter"
    Check 'bank-visible' $bankShown 'אחרי התקנת dsviz-bank-vs.jar — הכפתור "מאגר שאלות" מופיע בסרגל (מופעל)' $bankAfter
    Shot 'vs-toolbar-bank'
    if ($lamps.Count -gt 0) {
        $how = ClickEl $lamps[0]
        Info "לחיצה על המנורה: $how"
        Start-Sleep 4
        Shot 'vs-glyph-click'
    }
    if ($names.Count -eq 0) {
        # הסרגל לא מוצג כברירת מחדל? פותחים View ← Toolbars בצילום
        $wsh = New-Object -ComObject WScript.Shell; [void]$wsh.AppActivate($p.Id)
        $wsh.SendKeys('%v'); Start-Sleep 2; Shot 'vs-view-menu'
        $wsh.SendKeys('{ESC}')
    }
}
Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep 3
# יומן הפעילות של Visual Studio: האם ההרחבה נטענה, שגיאות בטעינה
Get-ChildItem "$env:APPDATA\Microsoft\VisualStudio" -Recurse -Filter ActivityLog.xml -ErrorAction SilentlyContinue | ForEach-Object {
    Copy-Item $_.FullName (Join-Path $Out ('ActivityLog-' + $_.Directory.Name + '.xml')) -ErrorAction SilentlyContinue
    Select-String -Path $_.FullName -Pattern 'DsViz|7b2f4d1e|vsixmanifest|ctmenu|Menus' -ErrorAction SilentlyContinue | Select-Object -First 25 | ForEach-Object { Info "  log| $($_.Line.Trim())" }
}
# עקבות הרכיב (MEF) ויומן ההרכבה של Visual Studio
$trace = "$env:LOCALAPPDATA\DSViz\vs-trace.log"
$traceText = ''
if (Test-Path $trace) { Copy-Item $trace (Join-Path $Out 'vs-trace.log'); $traceText = Get-Content $trace -Raw; Get-Content $trace | Select-Object -First 30 | ForEach-Object { Info "  trace| $_" } }
else { Info 'אין vs-trace.log — הרכיב לא כתב כלום' }
$logText = ''
Get-ChildItem "$env:APPDATA\Microsoft\VisualStudio" -Recurse -Filter ActivityLog.xml -ErrorAction SilentlyContinue | ForEach-Object { $logText += (Get-Content $_.FullName -Raw) }
Get-ChildItem "$env:LOCALAPPDATA\Microsoft\VisualStudio" -Recurse -Filter *.err -ErrorAction SilentlyContinue | ForEach-Object {
    Copy-Item $_.FullName (Join-Path $Out ('mef-' + $_.Name)) -ErrorAction SilentlyContinue
    Info "MEF err: $($_.FullName)"
    Get-Content $_.FullName -TotalCount 20 | ForEach-Object { Info "  err| $_" }
}
$inLog = { param($rx) ($logText -match $rx) -or ($traceText -match $rx) }
Check 'mef-loaded' (& $inLog 'MEF tagger provider active') 'רכיב ה-MEF (tagger) נטען (שורה ביומן: MEF tagger provider active)'
Check 'mef-glyph-factory' (& $inLog 'MEF glyph factory created') 'ה-glyph factory נוצר'
Check 'mef-tags' (& $inLog 'solution tags: 2 in .*Targil\.cs') 'נוצרו 2 תגים בקובץ התרגיל (ביומן: solution tags: 2 in ...Targil.cs)'
Check 'mef-tags-activitylog' ($logText -match 'solution tags: 2') 'השורה נמצאת גם ביומן הפעילות (ActivityLog.xml)'
Check 'mef-plain-none' (-not ($traceText -match 'solution tags: [1-9]\d* in .*Plain\.cs')) 'בקובץ הרגיל לא נוצרו תגים'
Check 'mef-glyph-click' (& $inLog 'solution glyph clicked') 'לחיצה על המנורה הגיעה לרכיב (solution glyph clicked)'
Info "כישלונות: $script:fail"
exit $(if ($script:fail -gt 0) { 1 } else { 0 })
