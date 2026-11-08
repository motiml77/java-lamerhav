# (UTF-8 עם BOM)
# בדיקה מקצה לקצה של סרגל הכלים של DSViz ב-Visual Studio האמיתי (מחשב Windows של GitHub Actions):
#   1. מתקינים בשקט את DSViz-Lab-Setup.exe (הגרסה החדשה: DSViz + מאגר התרגילים ל-Visual Studio + סרגל הכלים VSIX)
#   2. פותחים Visual Studio אמיתי עם הפתרון DataStructures-CS ומחפשים את הסרגל (UI Automation)
#   3. לוחצים על הכפתור 'שליחת תרגיל' בסרגל -> נפתח חלון השליחה (Swing) -> הדבקת תמונת דף מבחן -> שליחה ל-AI האמיתי -> בדיקת הקבצים
#   4. לוחצים על 'מאגר תרגילים' בסרגל: (א) בלי -Ddsviz.access=off -> מסך הכניסה, (ב) עם off -> חלון המאגר, בחירת שאלה, 'צור תרגיל' -> החלון הצף
# חלונות ה-Swing שהסרגל מפעיל (תהליך javaw נפרד) לא נחשפים ל-UIA, ולכן נטען אליהם java agent (ToolbarAgent, דרך JAVA_TOOL_OPTIONS
# ש-Visual Studio יורש) שמריץ בחלון האמיתי את אותן פקודות של tutorials/harness/SwingScript. הלחיצה על הכפתור בסרגל — אמיתית (UIA Invoke / עכבר).
#   5. אחרי 'שליחת תרגיל': הפרויקט נוסף לפתרון שפתוח (DTE) — בלי חלון 'Select an app', בלי Reload, בלי Visual Studio שני; הקובץ נפתח ב-Visual Studio
#   6. 'הרצה ויזואלית' בסרגל של DSViz על הפרויקט שנוצר -> נפתח החלון של DSViz (גם כשאין קובץ C# פתוח: Startup Project) — צילומים
# -Setup: DSViz-Setup.exe (הציבורית) או DSViz-Lab-Setup.exe. -Mock/-Exercise: שרת 'שליחת תרגיל' מדומה (בלי Gemini) — תרגיל קבוע, שאפשר לפתור ולהריץ.
param([Parameter(Mandatory = $true)][string]$Lab, [Parameter(Mandatory = $true)][string]$Out, [Parameter(Mandatory = $true)][string]$Here,
    [Parameter(Mandatory = $true)][string]$Harness, [Parameter(Mandatory = $true)][string]$Page,
    [string]$Mock = '', [string]$Exercise = '')
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$OutputEncoding = [Text.Encoding]::UTF8
New-Item -ItemType Directory -Force $Out | Out-Null
$Out = (Resolve-Path $Out).Path
$Lab = (Resolve-Path $Lab).Path
$script:fails = 0
$script:lines = New-Object System.Collections.Generic.List[string]
function Log([string]$s) { Write-Host $s; $script:lines.Add($s) }
function Ok([string]$id, [string]$s) { Log "  ok   $id  $s" }
function Fail([string]$id, [string]$s) { $script:fails++; Log "  FAIL $id  $s" }
function Info([string]$s) { Log "  ..   $s" }
function Check([string]$id, [bool]$cond, [string]$s, [string]$why = '') { if ($cond) { Ok $id $s } else { Fail $id ($s + $(if ($why) { " — $why" } else { '' })) } }
function Save-Summary() {
    $script:lines | Set-Content (Join-Path $Out 'summary.txt') -Encoding utf8
    if ($env:GITHUB_STEP_SUMMARY) { ("``````" + "`n" + ($script:lines -join "`n") + "`n" + "``````") | Add-Content $env:GITHUB_STEP_SUMMARY -Encoding utf8 }
}
Add-Type -AssemblyName System.Windows.Forms, System.Drawing, UIAutomationClient, UIAutomationTypes
function Shot([string]$name) {
    try {
        $b = [Windows.Forms.SystemInformation]::VirtualScreen
        $bmp = New-Object Drawing.Bitmap $b.Width, $b.Height
        $g = [Drawing.Graphics]::FromImage($bmp)
        $g.CopyFromScreen($b.Left, $b.Top, 0, 0, $bmp.Size)
        $bmp.Save((Join-Path $Out "$name.png"), [Drawing.Imaging.ImageFormat]::Png)
        $g.Dispose(); $bmp.Dispose()
        Info "צילום מסך: $name.png"
    } catch { Info "צילום $name נכשל: $_" }
}
function Windows() { Get-Process | Where-Object { $_.MainWindowTitle } | ForEach-Object { "$($_.ProcessName)[$($_.Id)]: $($_.MainWindowTitle)" } }
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class Screen2 {
    [StructLayout(LayoutKind.Sequential)]
    public struct DEVMODE {
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string dmDeviceName;
        public ushort dmSpecVersion; public ushort dmDriverVersion; public ushort dmSize; public ushort dmDriverExtra; public uint dmFields;
        public int dmPositionX; public int dmPositionY; public uint dmDisplayOrientation; public uint dmDisplayFixedOutput;
        public short dmColor; public short dmDuplex; public short dmYResolution; public short dmTTOption; public short dmCollate;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string dmFormName;
        public ushort dmLogPixels; public uint dmBitsPerPel; public uint dmPelsWidth; public uint dmPelsHeight; public uint dmDisplayFlags; public uint dmDisplayFrequency;
        public uint dmICMMethod; public uint dmICMIntent; public uint dmMediaType; public uint dmDitherType; public uint dmReserved1; public uint dmReserved2; public uint dmPanningWidth; public uint dmPanningHeight;
    }
    [DllImport("user32.dll", CharSet = CharSet.Ansi)] static extern bool EnumDisplaySettings(string dev, int mode, ref DEVMODE dm);
    [DllImport("user32.dll", CharSet = CharSet.Ansi)] static extern int ChangeDisplaySettings(ref DEVMODE dm, int flags);
    public static int Set(int w, int h) {
        DEVMODE dm = new DEVMODE();
        dm.dmSize = (ushort)Marshal.SizeOf(typeof(DEVMODE));
        if (!EnumDisplaySettings(null, -1, ref dm)) return -100;
        dm.dmPelsWidth = (uint)w; dm.dmPelsHeight = (uint)h;
        dm.dmFields = 0x80000 | 0x100000;
        return ChangeDisplaySettings(ref dm, 1);
    }
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
}
'@
Add-Type -Namespace Win -Name Mouse -MemberDefinition '[DllImport("user32.dll")] public static extern void mouse_event(int f, int dx, int dy, int d, int e); [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);'
foreach ($res in @(@(1920, 1080), @(1680, 1050), @(1600, 900), @(1440, 900), @(1280, 1024))) { if ([Screen2]::Set($res[0], $res[1]) -eq 0) { break } }
Start-Sleep 3

function ClickEl($el) {
    try { $el.GetCurrentPattern([Windows.Automation.InvokePattern]::Pattern).Invoke(); return 'invoke' } catch { }
    try {
        $r = $el.Current.BoundingRectangle
        [Win.Mouse]::SetCursorPos([int]($r.X + $r.Width / 2), [int]($r.Y + $r.Height / 2)) | Out-Null
        Start-Sleep -Milliseconds 300
        [Win.Mouse]::mouse_event(2, 0, 0, 0, 0); [Win.Mouse]::mouse_event(4, 0, 0, 0, 0)
        return 'mouse'
    } catch { return $null }
}
function VsElements([int]$vsPid) {
    $cond = New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ProcessIdProperty, $vsPid)
    try { return @([Windows.Automation.AutomationElement]::RootElement.FindAll([Windows.Automation.TreeScope]::Descendants, $cond)) } catch { return @() }
}
# אלמנטים של Visual Studio לפי שם מדויק (מהיר יותר מסריקת כל העץ)
function VsByName([int]$vsPid, [string]$name) {
    $c1 = New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ProcessIdProperty, $vsPid)
    $c2 = New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::NameProperty, $name)
    $c = New-Object Windows.Automation.AndCondition($c1, $c2)
    try { return @([Windows.Automation.AutomationElement]::RootElement.FindAll([Windows.Automation.TreeScope]::Descendants, $c)) } catch { return @() }
}
function ElName($el) { try { return $el.Current.Name } catch { return '' } }
function ElType($el) { try { return $el.Current.ControlType.ProgrammaticName } catch { return '' } }
function SkipFirstRun([int]$vsPid) {
    $els = VsElements $vsPid
    foreach ($el in $els) {
        if ((ElName $el) -match 'Are you sure you want to exit') {
            foreach ($b in $els) { if ((ElName $b) -eq 'No' -and (ElType $b) -match 'Button') { ClickEl $b | Out-Null; return $true } }
        }
    }
    foreach ($pat in @('Skip and add accounts later', 'Not now, maybe later', 'Start Visual Studio', 'Continue without code')) {
        foreach ($el in $els) {
            $n = ElName $el
            if ($n -and $n -match $pat -and (ElType $el) -match 'Button|Hyperlink|Text') { if (ClickEl $el) { Info "הפעלה ראשונה: '$n'"; return $true } }
        }
    }
    return $false
}
# אלמנט הכפתור של DSViz בסרגל (עדיפות: Button שנראה על המסך)
function ToolbarButton([int]$vsPid, [string[]]$names) {
    foreach ($n in $names) {
        $els = @(VsByName $vsPid $n)
        if ($els.Count -eq 0) { continue }
        $btn = @($els | Where-Object { (ElType $_) -match 'Button' -and -not $_.Current.IsOffscreen })
        if ($btn.Count -eq 0) { $btn = @($els | Where-Object { (ElType $_) -match 'Button|MenuItem' }) }
        if ($btn.Count -eq 0) { $btn = $els }
        return $btn[0]
    }
    return $null
}
function DescEl($el) {
    if (-not $el) { return 'absent' }
    $c = $el.Current
    return ("{0} '{1}' enabled={2} offscreen={3} rect={4}" -f (ElType $el), $c.Name, $c.IsEnabled, $c.IsOffscreen, $c.BoundingRectangle)
}
# אחרי השליחה הכלי פותח את קובץ הפתרון (Desktop.open) — אם ב-Windows אין שיוך ברירת מחדל ל-.sln נפתח חלון "Select an app": בוחרים Visual Studio, "Just once"
function AnyByName([string]$name) {
    $c = New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::NameProperty, $name)
    try { return @([Windows.Automation.AutomationElement]::RootElement.FindAll([Windows.Automation.TreeScope]::Descendants, $c)) } catch { return @() }
}
function HandleOpenWith([int]$sec = 30) {
    $logged = $false
    for ($i = 0; $i -lt $sec; $i += 2) {
        $jo = @(AnyByName 'Just once')
        if ($jo.Count -eq 0) {
            $hdr = @(AnyByName 'Select an app to open this .sln file')
            if ($hdr.Count -gt 0 -and -not $logged) { $logged = $true; Info "נמצאה הכותרת של חלון הבחירה, אבל לא הכפתור 'Just once' — סוגי רכיבים: $((AnyByName 'Always' | ForEach-Object { ElType $_ }) -join ',')" }
        }
        if ($jo.Count -gt 0) {
            Shot "$script:shotPrefix-open-with-chooser"
            Info "Just once: $(($jo | ForEach-Object { ElType $_ }) -join ',')"
            $how = ClickEl $jo[0]
            Info "חלון 'Select an app to open this .sln file' (אין שיוך ברירת מחדל ל-.sln במחשב הבדיקה) — Visual Studio הוא ברירת המחדל; 'Just once' ($how)"
            return $true
        }
        Start-Sleep 2
    }
    # גיבוי: חלון הבחירה של Windows (Shell) לא נחשף ל-UIA מהתהליך הזה — לוחצים בעכבר במקום הכפתור 'Just once'
    # (החלון במרכז המסך, 'Visual Studio' כבר מסומן כברירת מחדל; המיקום יחסי לגודל המסך — כך נראה בצילום 1920x1080)
    $b = [Windows.Forms.SystemInformation]::VirtualScreen
    Shot "$script:shotPrefix-before-blind-click"
    # Windows Server 2022: "How do you want to open this file?" -> OK (Visual Studio 2022 מסומן) · Windows 2025: "Select an app" -> Just once
    if ([Environment]::OSVersion.Version.Build -ge 26000) { $bx = 1062; $by = 822 } else { $bx = 1027; $by = 673 }
    [Win.Mouse]::SetCursorPos([int]($b.Width * $bx / 1920), [int]($b.Height * $by / 1080)) | Out-Null
    Start-Sleep -Milliseconds 400
    [Win.Mouse]::mouse_event(2, 0, 0, 0, 0); [Win.Mouse]::mouse_event(4, 0, 0, 0, 0)
    Info "חלון 'Select an app to open this .sln file' לא נחשף ל-UIA — לחיצת עכבר ב-'Just once' לפי מיקום (Visual Studio = ברירת המחדל)"
    Start-Sleep 3
    Shot "$script:shotPrefix-after-blind-click"
    return $true
}
# הפתרון שונה מבחוץ (פרויקט נוסף): Visual Studio שואל — לוחצים Reload (כמו תלמיד)
function ClickReload([int]$vsPid, [int]$sec = 60) {
    for ($i = 0; $i -lt $sec; $i += 2) {
        foreach ($el in (VsElements $vsPid)) {
            $n = ElName $el
            if ($n -match '^(Reload|Reload All|Reload Solution|_Reload)$' -and (ElType $el) -match 'Button') {
                Shot "$script:shotPrefix-reload-ask"
                $how = ClickEl $el
                Info "Visual Studio שאל אם לטעון מחדש — לחצנו '$n' ($how)"
                return $true
            }
        }
        Start-Sleep 2
    }
    return $false
}
function GetDte() {
    foreach ($pid2 in 'VisualStudio.DTE.18.0', 'VisualStudio.DTE.17.0', 'VisualStudio.DTE') { try { return [Runtime.InteropServices.Marshal]::GetActiveObject($pid2) } catch { } }
    return $null
}
function KillJava() { Get-Process javaw, java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue }

$docs = [Environment]::GetFolderPath('MyDocuments')
$desk = [Environment]::GetFolderPath('Desktop')
$dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'
$runner = Join-Path $dsviz 'cs\dsviz-cs.exe'
$csDir = Join-Path $docs 'DataStructures-CS'
$sln = Join-Path $csDir 'DataStructures-CS.sln'
$exDir = Join-Path $csDir 'Exercises'
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$vs = & $vswhere -latest -prerelease -products * -requires Microsoft.Component.MSBuild -property installationPath
$vsName = & $vswhere -latest -prerelease -products * -property displayName
$vsVer = & $vswhere -latest -prerelease -products * -property catalog_productDisplayVersion
$msbuild = Join-Path $vs 'MSBuild\Current\Bin\amd64\MSBuild.exe'
$devenv = Join-Path $vs 'Common7\IDE\devenv.exe'

# שומר: אחרי 70 דקות סוגרים את Visual Studio וה-java כדי שהצילומים והיומנים יישמרו
Start-Process powershell -ArgumentList '-NoProfile', '-Command', 'Start-Sleep 4200; Get-Process devenv,javaw -ErrorAction SilentlyContinue | Stop-Process -Force' -WindowStyle Hidden | Out-Null

Log "== 0. הסביבה"
Info "Windows: $([Environment]::OSVersion.VersionString) · $vsName $vsVer — $vs · מסך: $([Windows.Forms.SystemInformation]::VirtualScreen.Size)"
Check 'env-vs' ((Test-Path $msbuild) -and (Test-Path $devenv)) 'Visual Studio עם MSBuild ו-devenv'
Info ("DSViz-Lab-Setup.exe: {0:N0} בייטים, {1}" -f (Get-Item $Lab).Length, (Get-Item $Lab).LastWriteTime)

# ------------------------------------------------------------------ 1. התקנה
Log "== 1. התקנה שקטה של DSViz-Lab-Setup.exe (DSViz + מאגר תרגילים + סרגל הכלים)"
$t = Get-Date
$p = Start-Process $Lab -ArgumentList '/S' -PassThru -Wait
Info ("ההתקנה: קוד {0}, {1:N0} שניות" -f $p.ExitCode, ((Get-Date) - $t).TotalSeconds)
Check 'install-exit' ($p.ExitCode -eq 0) 'ההתקנה הסתיימה בהצלחה' "קוד $($p.ExitCode)"
foreach ($f in @($runner, (Join-Path $dsviz 'dsviz.jar'), (Join-Path $dsviz 'jdk\bin\javaw.exe'), (Join-Path $dsviz 'lab\dsviz-bank-vs.jar'), $sln)) {
    Check 'install-file' (Test-Path $f) "קיים: $f"
}
Start-Sleep 3
$found = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\VisualStudio", "$env:ProgramData\Microsoft\VisualStudio" -Recurse -Filter extension.vsixmanifest -ErrorAction SilentlyContinue |
    Where-Object { (Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue) -match 'DsViz.VisualStudio.Toolbar' }
Check 'vsix-installed' (@($found).Count -gt 0) 'ההרחבה (סרגל הכלים) הותקנה ב-Visual Studio' "$(@($found).Count)"
Get-ChildItem $env:TEMP -Filter 'dd_VSIXInstaller*.log' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1 | ForEach-Object { Copy-Item $_.FullName (Join-Path $Out 'vsixinstaller.log') -ErrorAction SilentlyContinue }
if (-not (Test-Path $runner) -or -not (Test-Path $sln)) { Log 'DSViz לא הותקן — עוצרים'; Save-Summary; exit 1 }
$javaw = Join-Path $dsviz 'jdk\bin\javaw.exe'
$javaVer = & (Join-Path $dsviz 'jdk\bin\java.exe') -version 2>&1 | Select-Object -First 1
Info "Java של DSViz: $javaVer"
$before = @(Get-ChildItem $exDir -Directory -ErrorAction SilentlyContinue | ForEach-Object Name)
Info "תרגילים קיימים לפני: $($before.Count)"

# ------------------------------------------------------------------ 2. ה-agent שמפעיל את חלונות ה-Swing שהסרגל פותח
Log "== 2. הכנת ה-agent (מריץ את SwingScript בחלון האמיתי שהסרגל פתח)"
$ctl = 'C:\dsagent'
Remove-Item $ctl -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $ctl | Out-Null
$cls = Join-Path $ctl 'cls'
New-Item -ItemType Directory -Force $cls | Out-Null
$o = & javac --release 11 -encoding UTF-8 -nowarn -d $cls (Join-Path $Harness 'SwingScript.java') (Join-Path $Here 'ToolbarAgent.java') 2>&1
Info "javac: $($o -join ' ')"
[IO.File]::WriteAllText((Join-Path $ctl 'manifest.txt'), "Premain-Class: ToolbarAgent`r`n", (New-Object Text.UTF8Encoding($false)))
& jar --create --file (Join-Path $ctl 'agent.jar') --manifest (Join-Path $ctl 'manifest.txt') -C $cls . 2>&1 | ForEach-Object { Info "jar: $_" }
Check 'agent-jar' (Test-Path (Join-Path $ctl 'agent.jar')) 'נבנה agent.jar'
$env:JAVA_TOOL_OPTIONS = "-javaagent:$ctl\agent.jar=$ctl"
function WriteScript([string]$mode, [string[]]$lines) {
    [IO.File]::WriteAllLines((Join-Path $ctl "$mode.script.txt"), [string[]]$lines, (New-Object Text.UTF8Encoding($false)))
}
# מריץ שלב: כותב תסריט, לוחץ על הכפתור בסרגל (scriptblock), ומצלם את המסך האמיתי בכל mark של ה-agent
function RunStage([string]$mode, [string]$prefix, [string[]]$lines, [scriptblock]$click, [int]$timeoutSec) {
    Get-ChildItem $ctl -Filter 'mark-*' | Remove-Item -Force
    Get-ChildItem $ctl -Filter 'ack-*' | Remove-Item -Force
    Remove-Item (Join-Path $ctl "$mode.done.txt"), (Join-Path $ctl "$mode.error.txt"), (Join-Path $ctl 'results.txt') -Force -ErrorAction SilentlyContinue
    WriteScript $mode $lines
    $how = & $click
    Info "לחיצה על הכפתור בסרגל: $how"
    $t0 = Get-Date; $seen = @{}; $retried = $false; $status = 'timeout'
    while (((Get-Date) - $t0).TotalSeconds -lt $timeoutSec) {
        foreach ($m in @(Get-ChildItem $ctl -Filter 'mark-*.txt' -ErrorAction SilentlyContinue)) {
            if ($seen[$m.Name]) { continue }
            $seen[$m.Name] = $true
            $nm = $m.BaseName.Substring(5)
            Start-Sleep -Milliseconds 800
            Shot "$prefix-$nm"
            Info ("חלונות: " + ((Windows) -join ' ; '))
            Set-Content (Join-Path $ctl "ack-$nm.txt") 'ok'
        }
        if (Test-Path (Join-Path $ctl "$mode.done.txt")) { $status = 'done'; break }
        if (Test-Path (Join-Path $ctl "$mode.error.txt")) { $status = 'error'; break }
        if (-not $retried -and ((Get-Date) - $t0).TotalSeconds -gt 75 -and (Test-Path (Join-Path $ctl "$mode.script.txt"))) {
            $retried = $true
            Info 'הכלי לא עלה אחרי 75 שניות — לוחצים שוב על הכפתור'
            Info "לחיצה חוזרת: $(& $click)"
        }
        Start-Sleep -Milliseconds 700
    }
    if ($status -eq 'error') { Info ("שגיאת agent: " + (Get-Content (Join-Path $ctl "$mode.error.txt") -Raw -Encoding utf8)) }
    Info "שלב $mode : $status ($([int]((Get-Date) - $t0).TotalSeconds) שניות)"
    return $status
}
function ShotJson([string]$mode, [string]$n) { $f = Join-Path $ctl "shots-$mode\$n.json"; if (Test-Path $f) { Get-Content $f -Raw -Encoding utf8 } else { '' } }
function JsonTexts([string]$j) { @([regex]::Matches($j, '"text":"([^"]*)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -and $_.Trim() }) }

# שרת מדומה לשליחת תרגיל (ירושה: Visual Studio -> javaw של 'שליחת תרגיל'): תרגיל קבוע, בלי רשת
$mockProc = $null
if ($Mock -and $Exercise) {
    $mockProc = Start-Process node -ArgumentList "`"$((Resolve-Path $Mock).Path)`" 8796 `"$((Resolve-Path $Exercise).Path)`" `"$(Join-Path $Out 'mock-worker.log')`"" -PassThru -WindowStyle Hidden
    Start-Sleep 2
    $env:DSVIZ_WORKER_URL = 'http://127.0.0.1:8796'
    Info 'שרת מדומה לשליחת תרגיל על 127.0.0.1:8796 (DSVIZ_WORKER_URL)'
}
# ------------------------------------------------------------------ 3. Visual Studio עם הפתרון
Log "== 3. Visual Studio נפתח עם הפתרון DataStructures-CS"
Start-Process $devenv -ArgumentList '/updateconfiguration' -Wait -WindowStyle Hidden
$t0 = Get-Date
$vsP = Start-Process $devenv -ArgumentList "`"$sln`" /log" -PassThru
$ready = $false
while (((Get-Date) - $t0).TotalMinutes -lt 12) {
    Start-Sleep 5
    $vsP.Refresh()
    if ($vsP.HasExited) { Info "devenv נסגר (קוד $($vsP.ExitCode))"; break }
    if (SkipFirstRun $vsP.Id) { Start-Sleep 3 }
    $vsP.Refresh()
    if ($vsP.MainWindowTitle -like '*- Microsoft Visual Studio*') { $ready = $true; break }
}
Info ("Visual Studio: {0:N0} שניות, כותרת '{1}'" -f ((Get-Date) - $t0).TotalSeconds, $vsP.MainWindowTitle)
Check 'vs-open' $ready 'Visual Studio נפתח עם הפתרון' $vsP.MainWindowTitle
if (-not $ready) { Shot 'vs-not-ready'; Save-Summary; Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force; exit 1 }
[Screen2]::ShowWindow($vsP.MainWindowHandle, 3) | Out-Null
[Screen2]::SetForegroundWindow($vsP.MainWindowHandle) | Out-Null
Start-Sleep 20; SkipFirstRun $vsP.Id | Out-Null
Shot '01-vs-open'

# ------------------------------------------------------------------ 4. הסרגל
Log "== 4. סרגל הכלים של DSViz ב-Visual Studio"
$want = @('תרגיל חדש', 'הוספת מבנה נתונים', 'שליחת תרגיל', 'הרצה ויזואלית', 'הדרכה')
$bankNames = @('מאגר תרגילים', 'מאגר שאלות')
$btn = @{}
for ($i = 0; $i -lt 40; $i++) {   # עד ~2.5 דקות: ההרחבה נטענת אחרי שהחלון עולה
    foreach ($n in ($want + $bankNames[0] + $bankNames[1])) { if (-not $btn[$n]) { $e = ToolbarButton $vsP.Id @($n); if ($e) { $btn[$n] = $e } } }
    $bk = $btn[$bankNames[0]] -or $btn[$bankNames[1]]
    if ((@($want | Where-Object { $btn[$_] }).Count -ge 5) -and $bk) { break }
    SkipFirstRun $vsP.Id | Out-Null
    Start-Sleep 4
}
Shot '02-vs-toolbar'
foreach ($n in $want) { Check 'toolbar-button' ($null -ne $btn[$n]) "בסרגל יש כפתור '$n'" ''; if ($btn[$n]) { Info "   $(DescEl $btn[$n])" } }
$bankName = $bankNames | Where-Object { $btn[$_] } | Select-Object -First 1
Check 'toolbar-bank' ($null -ne $bankName) "בסרגל יש כפתור מאגר ('$bankName') — המאגר מותקן" ''
if ($bankName) { Info "   $(DescEl $btn[$bankName])" }
$all = @(VsElements $vsP.Id | ForEach-Object { ElName $_ } | Where-Object { $_ -match 'DSViz|מאגר|שליחת|תרגיל|הרצה ויזואלית|הדרכה|מבנה נתונים' } | Select-Object -Unique)
Info "כל הרכיבים הקשורים ל-DSViz ב-UIA: $($all -join ' ; ')"
$all | Set-Content (Join-Path $Out 'toolbar-uia.txt') -Encoding utf8

# ------------------------------------------------------------------ 5. 'שליחת תרגיל' מהסרגל
Log "== 5. לחיצה על 'שליחת תרגיל' בסרגל -> חלון השליחה -> דף מבחן -> AI אמיתי"
function ClickSubmit() { $e = ToolbarButton $vsP.Id @('שליחת תרגיל'); if ($e) { return ClickEl $e } else { return 'הכפתור לא נמצא' } }
$page = (Resolve-Path $Page).Path
$st = RunStage 'submit' '10-submit' @(
    'waitwin שליחת תרגיל', 'wait 1500', 'mark window',
    "pasteimg $page", 'wait 800', 'shot form', 'mark form',
    'click שליחה ויצירת התרגיל', 'wait 5000', 'mark building',
    'waittext התרגיל נוצר|420000', 'wait 2000', 'shot card', 'mark card'
) { ClickSubmit } 540
Check 'submit-window' (Test-Path (Join-Path $ctl 'shots-submit\form.json')) "הלחיצה על 'שליחת תרגיל' בסרגל פתחה את חלון השליחה (Swing)" ((Windows) -join ' ; ')
Check 'submit-form' ((ShotJson 'submit' 'form') -match 'שליחה ויצירת התרגיל') "בטופס יש הכפתור 'שליחה ויצירת התרגיל' והתמונה הודבקה"
$card = ShotJson 'submit' 'card'
Check 'submit-card' (($st -eq 'done') -and ($card -match 'התרגיל נוצר') -and ($card -match 'Ex\d\d_')) "תשובה מה-AI האמיתי: הכרטיס 'התרגיל נוצר' עם שם הפרויקט" "סטטוס $st"
Info ("תוכן הכרטיס: " + ((JsonTexts $card) -join ' | '))
Info ("תוכן הטופס: " + ((JsonTexts (ShotJson 'submit' 'form')) -join ' | '))
# אחרי השליחה: הפרויקט נוסף ל-Visual Studio הפתוח דרך DTE. אסור: חלון 'Select an app' של Windows (פתיחת ה-.sln), שאלת Reload, Visual Studio שני
Start-Sleep 6
$chooser = @(AnyByName 'Just once').Count + @(AnyByName 'Select an app to open this .sln file').Count + @(AnyByName 'How do you want to open this file?').Count
Check 'submit-no-chooser' ($chooser -eq 0) "אחרי השליחה לא נפתח חלון 'Select an app' של Windows (ה-.sln לא נפתח שוב)" "$chooser"
Check 'submit-single-vs' (@(Get-Process devenv -ErrorAction SilentlyContinue).Count -eq 1) 'אחרי השליחה אין Visual Studio שני (חלון אחד)' "$(@(Get-Process devenv -ErrorAction SilentlyContinue).Count)"
Get-Process devenv -ErrorAction SilentlyContinue | Where-Object { $_.Id -ne $vsP.Id } | ForEach-Object { Info "נפתח Visual Studio נוסף (pid $($_.Id)) — סוגרים אותו"; Stop-Process -Id $_.Id -Force }
[Screen2]::ShowWindow($vsP.MainWindowHandle, 3) | Out-Null
[Screen2]::SetForegroundWindow($vsP.MainWindowHandle) | Out-Null
$script:shotPrefix = '12-submit'
$reload = ClickReload $vsP.Id 8
Check 'submit-no-reload' (-not $reload) 'Visual Studio לא שאל Reload / File Modification Detected (הפרויקט נוסף דרכו)' "$reload"
Check 'submit-card-no-reload' ($card -notmatch 'Reload') "בכרטיס 'התרגיל נוצר' אין 'Reload' (הפרויקט נוסף ל-Visual Studio הפתוח)"
Check 'submit-card-added' ($card -match 'נוסף לפתרון שפתוח') "הכרטיס אומר שהפרויקט נוסף לפתרון שפתוח ב-Visual Studio" ((JsonTexts $card) -join ' | ')
Start-Sleep 5
Shot '13-submit-after-vs'
$after = @(Get-ChildItem $exDir -Directory -ErrorAction SilentlyContinue | ForEach-Object Name)
$new = @($after | Where-Object { $before -notcontains $_ })
Info "תיקיות חדשות ב-Exercises: $($new -join ', ')"
Check 'submit-folder' ($new.Count -ge 1) 'נוצרה תיקיית פרויקט חדשה ב-Exercises\Ex##_...' ''
$subDir = $null; $subName = $null
if ($new.Count -ge 1) {
    $name = $new[0]; $dir = Join-Path $exDir $name
    $subDir = $dir; $subName = $name
    Check 'submit-name' ($name -match '^Ex\d\d_') "שם הפרויקט לפי המוסכמה: $name"
    $slnText = Get-Content $sln -Raw -Encoding utf8
    Check 'submit-sln' ($slnText -match [regex]::Escape("$name.csproj")) 'ה-.csproj רשום בפתרון DataStructures-CS.sln (נשמר מ-Visual Studio)'
    Check 'submit-sln-once' (([regex]::Matches($slnText, [regex]::Escape("$name.csproj"))).Count -eq 1) 'הפרויקט רשום בפתרון פעם אחת (לא נרשם גם בקובץ וגם ב-Visual Studio)'
    Check 'submit-csproj' (Test-Path (Join-Path $dir "$name.csproj")) 'קיים קובץ .csproj'
    $cs = @(Get-ChildItem $dir -Filter '*.cs' -ErrorAction SilentlyContinue)
    Check 'submit-classes' ($cs.Count -gt 0) "נוצרו מחלקות: $(($cs | ForEach-Object Name) -join ', ')"
    $here = @($cs | Select-String -Pattern 'כאן עונים' -SimpleMatch)
    Check 'submit-stubs' ($here.Count -gt 0) "יש 'כאן עונים' בשלדים (סעיפים): $($here.Count)"
    Check 'submit-main' (($cs | Select-String -Pattern 'static void Main|static int Main' | Select-Object -First 1) -ne $null) 'יש Main'
    New-Item -ItemType Directory -Force (Join-Path $Out 'created-submit') | Out-Null
    foreach ($kf in @(Get-ChildItem $dir -Recurse -File | Where-Object { $_.FullName -notmatch '\\(bin|obj)\\' -and $_.Extension -in '.cs', '.csproj', '.json' })) { Copy-Item $kf.FullName (Join-Path $Out "created-submit\$($kf.Name)") -Force }
    if ($cs.Count -gt 0) { Info ("התחלת $($cs[0].Name):`r`n" + ((Get-Content $cs[0].FullName -TotalCount 40 -Encoding utf8) -join "`r`n")) }
    $b = & $msbuild (Join-Path $dir "$name.csproj") /restore /nologo /v:m /p:Configuration=Debug 2>&1
    $b | Set-Content (Join-Path $Out 'build-submit.txt') -Encoding utf8
    $codes = @([regex]::Matches(($b -join "`n"), 'error (CS\d+)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    $other = @($codes | Where-Object { $_ -ne 'CS0161' })
    Check 'submit-skeleton' ($other.Count -eq 0) "אין שגיאות קומפילציה חוץ מ-CS0161 (פעולה ריקה בלי return): $($codes -join ',')" (($b | Select-String 'error' | Select-Object -First 4) -join ' | ')
    # הפרויקט החדש ב-Visual Studio הפתוח (DTE) — בלי טעינה מחדש של הפתרון
    $d = GetDte
    $pi = $null; $cnt = ''; $docName = ''; $startup = ''
    for ($w = 0; $w -lt 20 -and -not $pi; $w++) {
        try { $pi = $d.Solution.FindProjectItem($cs[0].FullName); $cnt = $d.Solution.Projects.Count } catch { Info "DTE: $_" }
        if (-not $pi) { Start-Sleep 3 }
    }
    try { $docName = $d.ActiveDocument.FullName } catch { }
    try { $startup = (@($d.Solution.SolutionBuild.StartupProjects) -join ',') } catch { }
    Info "DTE: הקובץ $($cs[0].Name) שייך לפרויקט ב-Visual Studio: $($null -ne $pi) · קובץ פעיל: $docName · פרויקטים (שורש): $cnt · Startup: $startup"
    Check 'submit-in-vs' ($null -ne $pi) 'אחרי הכפתור: הפרויקט החדש מופיע בפתרון שפתוח ב-Visual Studio (DTE), בלי טעינה מחדש' "פעיל: $docName"
    Check 'submit-opened-file' ($docName -like "$dir*" -and $docName -like '*.cs') 'ב-Visual Studio נפתח קובץ ה-C# של התרגיל (הקובץ הפעיל)' "פעיל: $docName"
    Check 'submit-startup' ($startup -like "*$name*") "הפרויקט החדש הוא Startup Project (הכפתור הירוק מריץ אותו): $startup"
    $d = $null
}
Check 'single-vs' (@(Get-Process devenv -ErrorAction SilentlyContinue).Count -eq 1) 'לא נפתח עוד Visual Studio (חלון אחד)' "$(@(Get-Process devenv -ErrorAction SilentlyContinue).Count)"
KillJava
Start-Sleep 3

# ------------------------------------------------------------------ 5ב. 'הרצה ויזואלית' בסרגל על הפרויקט שנוצר מהשליחה
Log "== 5ב. לחיצה על 'הרצה ויזואלית' בסרגל על הפרויקט שנוצר -> נפתח החלון של DSViz"
function SolveProject([string]$projDir) {
    # התלמיד כותב את הפתרון: כל פעולה-שלד מוחלפת בהצעה לפתרון ששמורה בצד (solutions.json) — כמו ב-vs-test.ps1
    $store = Join-Path $env:USERPROFILE '.dsviz\solutions.json'
    if (-not (Test-Path $store)) { return -1 }
    $sol = (Get-Content $store -Raw -Encoding utf8 | ConvertFrom-Json).solutions
    $applied = 0
    foreach ($f in Get-ChildItem $projDir -Filter '*.cs') {
        $text = [IO.File]::ReadAllText($f.FullName)
        foreach ($e in $sol) {
            if (([IO.Path]::GetFullPath($e.path)) -ne $f.FullName) { continue }
            $k = $text.IndexOf(' ' + $e.method + '(')
            if ($k -lt 0) { continue }
            $start = $text.LastIndexOf("`n", $k) + 1
            $open = $text.IndexOf('{', $k)
            $depth = 0; $end = -1
            for ($c = $open; $c -lt $text.Length; $c++) {
                if ($text[$c] -eq '{') { $depth++ } elseif ($text[$c] -eq '}') { $depth--; if ($depth -eq 0) { $end = $c; break } }
            }
            if ($end -lt 0) { continue }
            $indent = [regex]::Match($text.Substring($start), '^[ \t]*').Value
            $code = ($e.code -split "`n" | ForEach-Object { $indent + $_.TrimEnd("`r") }) -join "`r`n"
            $text = $text.Substring(0, $start) + $code + $text.Substring($end + 1)
            $applied++
        }
        [IO.File]::WriteAllText($f.FullName, $text, (New-Object Text.UTF8Encoding($true)))
    }
    return $applied
}
function DsvizWindows() { @(Get-Process javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle }) }
function KillDsviz() { Get-CimInstance Win32_Process -Filter "Name='javaw.exe' or Name='java.exe' or Name='dsviz-cs.exe'" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } }
# לוחצים על כפתור 'הרצה ויזואלית' של DSViz בסרגל, ומחכים לחלון של DSViz (javaw עם כותרת). מחזיר את הכותרות
function ClickRun([string]$shot) {
    KillDsviz; Start-Sleep 2
    [Screen2]::ShowWindow($vsP.MainWindowHandle, 3) | Out-Null
    [Screen2]::SetForegroundWindow($vsP.MainWindowHandle) | Out-Null
    Start-Sleep 2
    $dd = GetDte; $dc = '?'; $da = ''; try { $dc = $dd.Documents.Count; $da = $dd.ActiveDocument.FullName } catch { }
    Info "מסמכים פתוחים ב-Visual Studio לפני הלחיצה: $dc · פעיל: '$da'"
    $dd = $null
    $e = ToolbarButton $vsP.Id @('הרצה ויזואלית')
    if (-not $e) { Info 'הכפתור "הרצה ויזואלית" לא נמצא בסרגל'; Shot "$shot-nobutton"; return @() }
    Info "   $(DescEl $e)"
    $how = ClickEl $e
    Info "לחיצה על 'הרצה ויזואלית' בסרגל: $how"
    Start-Sleep 2; Shot "$shot-clicked"
    $titles = @()
    for ($i = 0; $i -lt 120; $i++) {
        Start-Sleep 1
        $titles = @(DsvizWindows | ForEach-Object { $_.MainWindowTitle })
        if ($titles.Count -gt 0) { break }
    }
    Start-Sleep 5
    Shot "$shot-run"
    Info ("חלונות: " + ((Windows) -join ' ; '))
    return $titles
}
if ($subDir) {
    # Visual Studio פתוח עם הקובץ: סוגרים אותו לפני שכותבים את הפתרון, ופותחים שוב אחרי (בלי שאלת 'File Modification Detected')
    $d = GetDte
    try { while ($d.Documents.Count -gt 0) { $d.Documents.Item(1).Close(2) } } catch { Info "DTE (סגירת מסמכים): $_" }
    $nSolved = SolveProject $subDir
    Info "הוכנסו $nSolved הצעות לפתרון (כמו תלמיד שכתב את הפעולות)"
    Check 'run-solved' ($nSolved -gt 0) 'הפעולות של התרגיל נכתבו (הצעות לפתרון)'
    $mainCs = @(Get-ChildItem $subDir -Filter '*.cs' | Where-Object { (Get-Content $_.FullName -Raw -Encoding utf8) -match 'static (void|int) Main' }) | Select-Object -First 1
    try { $null = $d.ItemOperations.OpenFile($mainCs.FullName) } catch { Info "DTE (פתיחת הקובץ): $_" }
    Start-Sleep 5
    $act = ''; try { $act = $d.ActiveDocument.FullName } catch { }
    Info "קובץ פעיל לפני הלחיצה: $act"
    $d = $null
    # א. עם קובץ ה-C# של התרגיל פתוח
    $t1 = ClickRun '40-run-open-file'
    Check 'run-toolbar-viewer' ($t1.Count -gt 0) "לחיצה על 'הרצה ויזואלית' בסרגל (קובץ C# פתוח): נפתח החלון של DSViz — $($t1 -join ' | ')" ((Windows) -join ' ; ')
    Check 'run-toolbar-title' (($t1 -join ' ') -match 'הרצה ויזואלית') "כותרת החלון: 'הרצה ויזואלית — מבני נתונים'" ($t1 -join ' | ')
    KillDsviz; Start-Sleep 3
    # ב. בלי אף מסמך פתוח (רק Solution Explorer): הכפתור מריץ את ה-Startup Project
    $d = GetDte
    try { while ($d.Documents.Count -gt 0) { $d.Documents.Item(1).Close(2) } } catch { Info "DTE (סגירת מסמכים): $_" }
    $d = $null
    Start-Sleep 3
    $t2 = ClickRun '41-run-no-doc'
    Check 'run-toolbar-startup' ($t2.Count -gt 0) "לחיצה על 'הרצה ויזואלית' בסרגל בלי קובץ פתוח: מריץ את ה-Startup Project — נפתח החלון של DSViz: $($t2 -join ' | ')" ((Windows) -join ' ; ')
    KillDsviz; Start-Sleep 3
    Check 'run-single-vs' (@(Get-Process devenv -ErrorAction SilentlyContinue).Count -eq 1) 'עדיין Visual Studio אחד' ''
}
KillJava
Start-Sleep 3

# ------------------------------------------------------------------ 6. 'מאגר תרגילים' בלי off -> מסך כניסה
Log "== 6. לחיצה על כפתור המאגר בסרגל (בלי dsviz.access=off) -> מסך 'כניסה למאגר התרגילים'"
function ClickBank() { $e = ToolbarButton $vsP.Id $bankNames; if ($e) { return ClickEl $e } else { return 'הכפתור לא נמצא' } }
$st = RunStage 'bank' '20-bank-gate' @(
    '#access=on', 'waitwin כניסה למאגר', 'wait 2500', 'shot gate', 'mark gate'
) { ClickBank } 240
$gate = ShotJson 'bank' 'gate'
Check 'gate-opens' (($st -eq 'done') -and ($gate -ne '')) "בלי כניסה: הלחיצה על כפתור המאגר פתחה את מסך 'כניסה למאגר התרגילים'" "סטטוס $st"
Info ("תוכן מסך הכניסה: " + ((JsonTexts $gate) -join ' | '))
KillJava
Start-Sleep 3

# ------------------------------------------------------------------ 7. 'מאגר תרגילים' (off) -> שאלה -> צור תרגיל -> 'השאלה'
Log "== 7. כפתור המאגר בסרגל (dsviz.access=off) -> בחירת שאלה -> 'צור תרגיל' -> החלון הצף 'השאלה'"
$before2 = @(Get-ChildItem $exDir -Directory -ErrorAction SilentlyContinue | ForEach-Object Name)
$st = RunStage 'bank' '30-bank' @(
    '#access=off', 'waitwin מאגר', 'wait 2500', 'shot window', 'has עמדת ניהול', 'mark window',
    'bankselect stack-sum-size', 'wait 1000', 'shot selected', 'mark selected',
    'click צור תרגיל', 'waitwin השאלה', 'wait 4000', 'shot float', 'mark float'
) { ClickBank } 420
Check 'bank-window' (Test-Path (Join-Path $ctl 'shots-bank\window.json')) 'הלחיצה על כפתור המאגר (עם off) פתחה את חלון המאגר' ((Windows) -join ' ; ')
$bw = ShotJson 'bank' 'window'
Info ("חלון המאגר: " + ((@(JsonTexts $bw) | Select-Object -First 30) -join ' | '))
Check 'bank-topics' (($bw -match 'מחסנית') -or ($bw -match 'רשימה')) 'בחלון המאגר מופיע עץ הנושאים'
$res = if (Test-Path (Join-Path $ctl 'results.txt')) { Get-Content (Join-Path $ctl 'results.txt') -Encoding utf8 -Raw } else { '' }
Info "כפתור 'עמדת ניהול' בחלון המאגר (בלי כניסת מורה): $($res.Trim())"
Check 'bank-manage-hidden' ($res -match 'עמדת ניהול = False') "'עמדת ניהול' לא מוצג לתלמיד (מוצג רק למורה מחובר — BankVsWindow: AccessGate.isTeacher())" $res
Check 'bank-float' (($st -eq 'done') -and (Test-Path (Join-Path $ctl 'shots-bank\float.json'))) "'צור תרגיל' -> נפתח החלון הצף 'השאלה'" "סטטוס $st"
Info ("החלון הצף: " + ((@(JsonTexts (ShotJson 'bank' 'float')) | Select-Object -First 20) -join ' | '))
Start-Sleep 8
Shot '35-bank-after-vs'
$after2 = @(Get-ChildItem $exDir -Directory -ErrorAction SilentlyContinue | ForEach-Object Name)
$new2 = @($after2 | Where-Object { $before2 -notcontains $_ })
Info "תיקיות חדשות מהמאגר: $($new2 -join ', ')"
Check 'bank-folder' ($new2.Count -ge 1) 'נוצר פרויקט תרגיל מהמאגר' ''
if ($new2.Count -ge 1) {
    $name2 = $new2[0]; $dir2 = Join-Path $exDir $name2
    $slnText = Get-Content $sln -Raw -Encoding utf8
    Check 'bank-sln' ($slnText -match [regex]::Escape("$name2.csproj")) "הפרויקט $name2 נרשם בפתרון"
    Check 'bank-files' ((Test-Path (Join-Path $dir2 "$name2.csproj")) -and @(Get-ChildItem $dir2 -Filter '*.cs').Count -ge 1) 'בפרויקט .csproj וקבצי .cs'
    $b = & $msbuild (Join-Path $dir2 "$name2.csproj") /restore /nologo /v:m /p:Configuration=Debug 2>&1
    $b | Set-Content (Join-Path $Out 'build-bank.txt') -Encoding utf8
    $codes = @([regex]::Matches(($b -join "`n"), 'error (CS\d+)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    Check 'bank-skeleton' (@($codes | Where-Object { $_ -ne 'CS0161' }).Count -eq 0) "התרגיל מהמאגר: אין שגיאות חוץ מ-CS0161: $($codes -join ',')" (($b | Select-String 'error' | Select-Object -First 4) -join ' | ')
}
Check 'single-vs2' (@(Get-Process devenv -ErrorAction SilentlyContinue).Count -eq 1) 'עדיין Visual Studio אחד' ''
KillJava

# ------------------------------------------------------------------ סיום
Log "== 8. יומנים"
Copy-Item (Join-Path $ctl 'agent.log') $Out -ErrorAction SilentlyContinue
foreach ($m in 'submit', 'bank') { if (Test-Path (Join-Path $ctl "shots-$m")) { Copy-Item (Join-Path $ctl "shots-$m") (Join-Path $Out "swing-shots-$m") -Recurse -Force } }
Get-ChildItem "$env:APPDATA\Microsoft\VisualStudio" -Recurse -Filter ActivityLog.xml -ErrorAction SilentlyContinue | ForEach-Object {
    Copy-Item $_.FullName (Join-Path $Out ('ActivityLog-' + $_.Directory.Name + '.xml')) -ErrorAction SilentlyContinue
}
if ($mockProc) { Stop-Process -Id $mockProc.Id -Force -ErrorAction SilentlyContinue }
Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force
$ok = @($script:lines | Where-Object { $_ -like '  ok *' }).Count
Log ""
Log "SUMMARY $ok ok / $($script:fails) FAIL — $vsName $vsVer"
Save-Summary
exit $(if ($script:fails -gt 0) { 1 } else { 0 })
