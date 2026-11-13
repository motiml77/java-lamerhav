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
    [string]$Mock = '', [string]$Exercise = '', [string]$BankJar = '', [string]$Vsix = '')
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
# KeepStart: בלי 'Continue without code' — כדי שמסך הפתיחה (Create a new project) יישאר לסרטון ההדרכה
function SkipFirstRun([int]$vsPid, [switch]$KeepStart) {
    $els = VsElements $vsPid
    foreach ($el in $els) {
        if ((ElName $el) -match 'Are you sure you want to exit') {
            foreach ($b in $els) { if ((ElName $b) -eq 'No' -and (ElType $b) -match 'Button') { ClickEl $b | Out-Null; return $true } }
        }
    }
    $pats = @('Skip and add accounts later', 'Not now, maybe later', 'Start Visual Studio')
    if (-not $KeepStart) { $pats += 'Continue without code' }
    foreach ($pat in $pats) {
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
            if ($script:tutOn) {
                # לסרטון ההדרכה: לאן העכבר הולך (target <טקסט> ב-agent, לפני ה-mark) — בלי target זה צעד של "מסתכלים"
                $tf = Join-Path $ctl 'target.txt'; $rect = $null
                if (Test-Path $tf) {
                    $v = @((Get-Content $tf -Raw -Encoding utf8).Trim() -split '\s+' | Where-Object { $_ -match '^-?\d+$' } | ForEach-Object { [int]$_ })
                    if ($v.Count -eq 4) { $rect = [ordered]@{ x = $v[0]; y = $v[1]; w = $v[2]; h = $v[3] } }
                    Remove-Item $tf -Force -ErrorAction SilentlyContinue
                }
                TutAdd "$prefix-$nm" $(if ($rect) { 'click' } else { 'look' }) $rect
            }
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
# ------------------------------------------------------------------ 2ב. מהמקור: המאגר וסרגל הכלים (DSViz-Lab-Setup.exe לא נבנה מחדש ב-CI)
# -BankJar: dsviz-bank-vs.jar שנבנה מהמקור (tools/build-bank-vs.sh) במקום זה שבהתקנה; -Vsix: ההרחבה שנבנתה מהמקור (prebuilt/ — dsviz-vsix.yml)
$teacherSeed = $false
if ($BankJar -and (Test-Path $BankJar)) {
    Copy-Item $BankJar (Join-Path $dsviz 'lab\dsviz-bank-vs.jar') -Force
    Info ("dsviz-bank-vs.jar מהמקור: {0:N0} בייטים" -f (Get-Item $BankJar).Length)
    # "כניסה" מוכנה בלי Google (מפתח בדיקה): המפתח הציבורי ב-DSVIZ_PASS_KEY לפני ש-Visual Studio עולה (עובר ל-javaw שהסרגל מפעיל)
    $seedCls = Join-Path $ctl 'seed'
    New-Item -ItemType Directory -Force $seedCls | Out-Null
    $seedCp = "$(Join-Path $dsviz 'lab\dsviz-bank-vs.jar');$(Join-Path $dsviz 'dsviz.jar')"
    $o = & javac --release 11 -encoding UTF-8 -nowarn -cp $seedCp -d $seedCls (Join-Path $Here 'SeedSession.java') 2>&1
    Info "javac SeedSession: $($o -join ' ')"
    $script:seedKey = Join-Path $ctl 'seed-key.txt'
    function Seed([string]$role, [string]$email) {
        $r = & (Join-Path $dsviz 'jdk\bin\java.exe') '-Dfile.encoding=UTF-8' -cp "$seedCls;$seedCp" il.lamerhav.dsviz.SeedSession $role $email $script:seedKey 2>&1 |
            Where-Object { $_ -notmatch 'JAVA_TOOL_OPTIONS' }
        return ($r -join ' ')
    }
    $pk = Seed 'key' '-'
    if ($pk -match 'PASSKEY (\S+)') {
        $env:DSVIZ_PASS_KEY = $Matches[1]
        $teacherSeed = $true
        Info "מפתח בדיקה לכניסה מוכנה: DSVIZ_PASS_KEY ($($Matches[1].Length) תווים)"
        Info "בלי כניסה: $(Seed 'none' '-')"
    } else { Info "SeedSession לא עבד: $pk" }
}
if ($Vsix -and (Test-Path $Vsix)) {
    $iv = Join-Path $Here '..\..\..\installer\install-vsix.ps1'
    Info "VSIX מהמקור: $Vsix ($((Get-Item $Vsix).Length) בייטים) — $iv"
    & $iv -Vsix (Resolve-Path $Vsix).Path 2>&1 | ForEach-Object { Info "install-vsix: $_" }
}

# ------------------------------------------------------------------ 2ב. שדרוג מגרסה בלי מאגר
# כמו מי שהייתה אצלו הגרסה הציבורית הישנה (בלי מאגר השאלות): Visual Studio נפתח פעם אחת כשהמאגר לא מותקן — הכפתור "מאגר שאלות"
# מוסתר, ו-Visual Studio זוכר את זה. אחר כך ההתקנה החדשה מוסיפה את המאגר — והכפתור חייב להופיע (בסעיף 4)
Log "== 2ב. Visual Studio פעם ראשונה בלי המאגר (כמו הגרסה הציבורית הישנה), ואז המאגר מותקן"
$labJar = Join-Path $dsviz 'lab\dsviz-bank-vs.jar'
$bankCopy = Join-Path $dsviz 'bank\dsviz-bank-vs.jar'
$aside = Join-Path $env:RUNNER_TEMP 'dsviz-bank-vs.jar.aside'
$asideBank = Join-Path $env:RUNNER_TEMP 'dsviz-bank-vs.jar.bank-aside'
if (Test-Path $labJar) { Move-Item $labJar $aside -Force }
if (Test-Path $bankCopy) { Move-Item $bankCopy $asideBank -Force }
Start-Process $devenv -ArgumentList '/updateconfiguration' -Wait -WindowStyle Hidden
$t0 = Get-Date
$p0 = Start-Process $devenv -ArgumentList "`"$sln`"" -PassThru
$up = $false
while (((Get-Date) - $t0).TotalMinutes -lt 12) {
    Start-Sleep 5
    $p0.Refresh()
    if ($p0.HasExited) { break }
    if (SkipFirstRun $p0.Id) { Start-Sleep 3 }
    $p0.Refresh()
    if ($p0.MainWindowTitle -like '*- Microsoft Visual Studio*') { $up = $true; break }
}
$seen = $null
if ($up) {
    for ($i = 0; $i -lt 40 -and -not $seen; $i++) { SkipFirstRun $p0.Id | Out-Null; $seen = ToolbarButton $p0.Id @('תרגיל חדש'); if (-not $seen) { Start-Sleep 4 } }
    Start-Sleep 15   # ההרחבה מסנכרנת את הכפתורים — ו-Visual Studio שומר את הסרגל כשהוא נסגר
    # סרגל "מקופל": DSViz באותה שורה עם סרגל אחר (כאן Build, לא Standard) — כך Visual Studio שומר אותו, והכפתורים נדחסים מאחורי החץ.
    # אחרי הבדיקות החוזרות של ההרחבה בדקה הראשונה (עד ~105 שניות אחרי הטעינה), אחרת היא מחזירה אותו לשורה משלו כבר כאן
    Start-Sleep 100
    $script:crowded = ''
    try {
        $bars0 = (GetDte).CommandBars
        $std0 = $bars0.Item('Standard'); $bld0 = $bars0.Item('Build'); $ds0 = $bars0.Item('DSViz')
        $bld0.Visible = $true
        $bld0.Position = $std0.Position
        $bld0.RowIndex = $std0.RowIndex + 1
        $ds0.Position = $std0.Position
        $ds0.RowIndex = $bld0.RowIndex
        $ds0.Left = $bld0.Left + $bld0.Width + 4
        Start-Sleep 2
        $script:crowded = "Standard row=$($std0.RowIndex) · Build row=$($bld0.RowIndex) · DSViz row=$($ds0.RowIndex)"
    } catch { $script:crowded = "DTE: $_" }
    Info "סרגל מקופל (DSViz באותה שורה עם Build): $script:crowded"
}
# "מאגר שאלות" תמיד גלוי ולחיץ — גם כשהמאגר לא במקום (הלחיצה מתקנת או מסבירה), לא כפתור אפור / מוסתר
$bb = ToolbarButton $p0.Id @('מאגר שאלות')
$noBank = ($null -ne $bb) -and $bb.Current.IsEnabled -and -not $bb.Current.IsOffscreen
Info ("בלי המאגר: Visual Studio עלה={0}, הסרגל={1}, 'מאגר שאלות' גלוי ולחיץ={2} · {3}" -f $up, ($null -ne $seen), $noBank, (DescEl $bb))
Shot '00-vs-without-bank'
$p0.Refresh()
if (-not $p0.HasExited) { $p0.CloseMainWindow() | Out-Null; if (-not $p0.WaitForExit(90000)) { Info 'Visual Studio לא נסגר — סוגרים בכוח'; Stop-Process -Id $p0.Id -Force -ErrorAction SilentlyContinue } }
Start-Sleep 5
Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
if (Test-Path $aside) { Move-Item $aside $labJar -Force }
if (Test-Path $asideBank) { Move-Item $asideBank $bankCopy -Force }
Check 'upgrade-prep' ($up -and $noBank) "Visual Studio בלי המאגר במקום: 'מאגר שאלות' בכל זאת גלוי ולחיץ (תמיד) — עכשיו המאגר מותקן" ''

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
# הסרגל כולו גלוי: DSViz בשורה משלו (לא עם Build / Standard — שם הכפתורים נדחסים מאחורי החץ "עוד"), וכל כפתור גלוי ולחיץ
function ToolbarRow() {
    try {
        $bars1 = (GetDte).CommandBars
        $ds1 = $bars1.Item('DSViz')
        $shared = @()
        foreach ($nm in 'Standard', 'Build', 'Debug', 'Text Editor', 'Debug Location', 'Layout') {
            try { $b1 = $bars1.Item($nm); if ($b1.Visible -and $b1.Position -eq $ds1.Position -and $b1.RowIndex -eq $ds1.RowIndex) { $shared += $nm } } catch { }
        }
        return "visible=$($ds1.Visible) row=$($ds1.RowIndex) shared=$($shared -join ',')"
    } catch { return "DTE: $_" }
}
$row = ''
for ($i = 0; $i -lt 30; $i++) { $row = ToolbarRow; if ($row -match '^visible=True row=\d+ shared=$') { break }; Start-Sleep 4 }
Check 'toolbar-own-row' ($row -match '^visible=True row=\d+ shared=$') "הסרגל של DSViz גלוי ובשורה משלו — גם אחרי שנשמר באותה שורה עם Build (2ב), כך שאף כפתור לא מקופל מאחורי החץ" "$row · לפני: $script:crowded"
Start-Sleep 3
$btn = @{}
foreach ($n in ($want + $bankNames)) { $e = ToolbarButton $vsP.Id @($n); if ($e) { $btn[$n] = $e } }
Shot '02-vs-toolbar'
$vsRect = $null; try { $vsP.Refresh(); $vsRect = [Windows.Automation.AutomationElement]::FromHandle($vsP.MainWindowHandle).Current.BoundingRectangle } catch { }
function ShownAndEnabled($el) {
    if (-not $el) { return $false }
    try {
        $c = $el.Current; $r = $c.BoundingRectangle
        $inside = (-not $vsRect) -or ($r.Right -le $vsRect.Right + 2 -and $r.Left -ge $vsRect.Left - 2)
        return ($c.IsEnabled -and -not $c.IsOffscreen -and $r.Width -gt 0 -and $r.Height -gt 0 -and $inside)
    } catch { return $false }
}
foreach ($n in @('תרגיל חדש', 'הוספת מבנה נתונים', 'מאגר שאלות', 'השאלה', 'שליחת תרגיל', 'הרצה ויזואלית', 'הדרכה')) {
    $e = ToolbarButton $vsP.Id @($n)
    Check 'toolbar-shown-enabled' (ShownAndEnabled $e) "הכפתור '$n' גלוי בסרגל ולחיץ (לא אפור ולא מאחורי החץ 'עוד')" (DescEl $e)
}
foreach ($n in $want) { Check 'toolbar-button' ($null -ne $btn[$n]) "בסרגל יש כפתור '$n'" ''; if ($btn[$n]) { Info "   $(DescEl $btn[$n])" } }
$bankName = $bankNames | Where-Object { $btn[$_] } | Select-Object -First 1
Check 'toolbar-bank' ($null -ne $bankName) "בסרגל יש כפתור מאגר ('$bankName') — המאגר מותקן, גם אחרי ש-Visual Studio כבר נפתח פעם בלי המאגר (2ב)" ''
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
            # אותו סוף שורה כמו בקובץ (LF או CRLF) — אחרת Visual Studio קופץ עם 'Inconsistent Line Endings' (חלון שחוסם את האוטומציה)
            $nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
            $code = ($e.code -split "`n" | ForEach-Object { $indent + $_.TrimEnd("`r") }) -join $nl
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

# ------------------------------------------------------------------ 6ב. מסך הכניסה מקדימה — גם כש-Visual Studio מפעיל את עצמו שוב
Log "== 6ב. כפתור המאגר בסרגל -> מסך הכניסה, ו-Visual Studio מפעיל את עצמו שוב: מסך הכניסה עדיין מעליו (צילום מסך מלא)"
Add-Type -Namespace Win -Name Z -MemberDefinition @'
[DllImport("user32.dll")] public static extern IntPtr WindowFromPoint(System.Drawing.Point p);
[DllImport("user32.dll")] public static extern IntPtr GetAncestor(IntPtr h, uint flags);
[DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
[DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
public struct RECT { public int Left, Top, Right, Bottom; }
'@ -ReferencedAssemblies System.Drawing
KillJava; Start-Sleep 2
[Screen2]::ShowWindow($vsP.MainWindowHandle, 3) | Out-Null
[Screen2]::SetForegroundWindow($vsP.MainWindowHandle) | Out-Null
Start-Sleep 2
Info "לחיצה על כפתור המאגר: $(ClickBank)"
$gw = $null
for ($i = 0; $i -lt 90 -and -not $gw; $i++) {
    Start-Sleep 1
    $gw = Get-Process javaw, java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like '*כניסה למאגר*' } | Select-Object -First 1
}
Check 'gate-front-open' ($null -ne $gw) "מסך הכניסה נפתח אחרי הלחיצה על כפתור המאגר" ((Windows) -join ' ; ')
if ($gw) {
    # Visual Studio מפעיל את עצמו שוב (כמו שקורה באמת אחרי הלחיצה) — ומסך הכניסה צריך להישאר מעליו
    Start-Sleep 1
    [Win.Z]::SetForegroundWindow($vsP.MainWindowHandle) | Out-Null
    Start-Sleep 3
    $r = New-Object Win.Z+RECT
    [void][Win.Z]::GetWindowRect($gw.MainWindowHandle, [ref]$r)
    $pt = New-Object Drawing.Point ([int](($r.Left + $r.Right) / 2)), ([int](($r.Top + $r.Bottom) / 2))
    $top = [Win.Z]::GetAncestor([Win.Z]::WindowFromPoint($pt), 2)
    [uint32]$topPid = 0; [void][Win.Z]::GetWindowThreadProcessId($top, [ref]$topPid)
    $who = (Get-Process -Id $topPid -ErrorAction SilentlyContinue).ProcessName
    Info "באמצע מסך הכניסה ($($pt.X),$($pt.Y)) רואים את החלון של: $who (pid $topPid; מסך הכניסה: $($gw.Id), Visual Studio: $($vsP.Id))"
    Shot '19-gate-in-front-of-vs'
    Check 'gate-front-of-vs' ($topPid -eq $gw.Id) "מסך הכניסה מעל Visual Studio — גם אחרי ש-Visual Studio הפעיל את עצמו שוב (רואים בצילום 19-gate-in-front-of-vs.png)" "באמצע המסך: $who"
}
KillJava
Start-Sleep 3

# ------------------------------------------------------------------ 7. 'מאגר תרגילים' (off) -> שאלה -> צור תרגיל -> 'השאלה'
Log "== 7. כפתור המאגר בסרגל (dsviz.access=off) -> בחירת שאלה -> 'צור תרגיל' -> החלון הצף 'השאלה'"
$before2 = @(Get-ChildItem $exDir -Directory -ErrorAction SilentlyContinue | ForEach-Object Name)
$st = RunStage 'bank' '30-bank' @(
    '#access=off', 'waitwin מאגר', 'wait 2500', 'shot window', 'has עמדת ניהול', 'mark window',
    'bankselect stack-sum-size', 'wait 1000', 'shot selected', 'mark selected',
    'click צור תרגיל', 'waitwin השאלה', 'wait 4000', 'shot float', 'mark float',
    'click הכן סעיף ב''', 'wait 9000', 'shot prepared', 'mark prepared'
) { ClickBank } 480
Check 'bank-window' (Test-Path (Join-Path $ctl 'shots-bank\window.json')) 'הלחיצה על כפתור המאגר (עם off) פתחה את חלון המאגר' ((Windows) -join ' ; ')
$bw = ShotJson 'bank' 'window'
Info ("חלון המאגר: " + ((@(JsonTexts $bw) | Select-Object -First 30) -join ' | '))
Check 'bank-topics' (($bw -match 'מחסנית') -or ($bw -match 'רשימה')) 'בחלון המאגר מופיע עץ הנושאים'
$res = if (Test-Path (Join-Path $ctl 'results.txt')) { Get-Content (Join-Path $ctl 'results.txt') -Encoding utf8 -Raw } else { '' }
Info "כפתור 'עמדת ניהול' בחלון המאגר (בלי כניסת מורה): $($res.Trim())"
Check 'bank-manage-hidden' ($res -match 'עמדת ניהול = False') "'עמדת ניהול' לא בחלון המאגר (הכפתור בסרגל הכלים של Visual Studio, כמו ב-Eclipse)" $res
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
    # "הכן סעיף ב" בחלון הצף: הפעולה של סעיף ב לפני Main, הבדיקות שלו ב-Main — בקובץ, ובעורך של Visual Studio (בלי 'הקובץ השתנה מבחוץ')
    $bankCs = @(Get-ChildItem $dir2 -Filter '*.cs' | Where-Object { (Get-Content $_.FullName -Raw -Encoding utf8) -match 'static (void|int) Main' }) | Select-Object -First 1
    if ($bankCs) {
        $t2 = Get-Content $bankCs.FullName -Raw -Encoding utf8
        $mi = $t2.IndexOf('static void Main')
        $si = $t2.IndexOf('SizeOf(Stack')
        Check 'bank-prepare-method' ($si -ge 0 -and $si -lt $mi) "אחרי 'הכן סעיף ב': הפעולה SizeOf נוספה לפני Main" "SizeOf@$si Main@$mi"
        Check 'bank-prepare-main' ($mi -ge 0 -and $t2.IndexOf('סעיף ב', $mi) -gt 0) "אחרי 'הכן סעיף ב': הבדיקות של סעיף ב נוספו בסוף Main" ''
        Check 'bank-prepare-eol' (-not ($t2 -match "[^`r]`n" -and $t2 -match "`r`n")) 'סוף שורה אחיד בקובץ (בלי Inconsistent Line Endings)' ''
        $d = GetDte; $docText = ''
        try { foreach ($x in $d.Documents) { if ($x.FullName -ieq $bankCs.FullName) { $td = $x.Object('TextDocument'); $docText = $td.StartPoint.CreateEditPoint().GetText($td.EndPoint) } } } catch { Info "DTE: $_" }
        Info "   העורך: $($docText.Length) תווים · SizeOf בעורך: $($docText.Contains('SizeOf'))"
        Check 'bank-prepare-editor' ($docText.Contains('SizeOf(Stack')) 'בעורך של Visual Studio רואים את סעיף ב (ההוספה נעשתה בתוך העורך)' ''
        $d = $null
        $b2 = & $msbuild (Join-Path $dir2 "$name2.csproj") /restore /nologo /v:m /p:Configuration=Debug 2>&1
        $codes2 = @([regex]::Matches(($b2 -join "`n"), 'error (CS\d+)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
        Check 'bank-prepare-build' (@($codes2 | Where-Object { $_ -ne 'CS0161' }).Count -eq 0) "אחרי 'הכן סעיף ב': אין שגיאות חוץ מ-CS0161: $($codes2 -join ',')" (($b2 | Select-String 'error' | Select-Object -First 4) -join ' | ')
    }
    # הכפתור הירוק ("הרצה ויזואלית") מריץ את ה-Startup Project: אחרי 'צור תרגיל' הוא התרגיל מהמאגר — ולא Exercise (תרגיל המחסנית של התבנית).
    # ומעבר בין קבצים: הפרויקט של הקובץ הפעיל נהיה ה-Startup Project (StartupFollower בהרחבה) — כמו ב-Eclipse, מריצים את מה שפתוח
    $d = GetDte
    $s1 = ''
    for ($w = 0; $w -lt 15; $w++) { try { $s1 = (@($d.Solution.SolutionBuild.StartupProjects) -join ',') } catch { }; if ($s1 -like "*$name2*") { break }; Start-Sleep 2 }
    Check 'bank-startup' ($s1 -like "*$name2*") "אחרי 'צור תרגיל': התרגיל מהמאגר הוא ה-Startup Project (הכפתור הירוק מריץ אותו): $s1"
    $exFile = Join-Path (Split-Path $sln) 'Exercise\Program.cs'
    $bankFile = @(Get-ChildItem $dir2 -Filter '*.cs' | Sort-Object Name)[0].FullName
    function StartupAfterOpen($file, $want) {
        try { $null = $d.ItemOperations.OpenFile($file); $d.ActiveDocument.Activate() } catch { Info "DTE פתיחה: $_" }
        $s = ''
        for ($w = 0; $w -lt 12; $w++) { Start-Sleep 1; try { $s = (@($d.Solution.SolutionBuild.StartupProjects) -join ',') } catch { }; if ($s -like "*$want*") { break } }
        $act = ''; try { $act = $d.ActiveDocument.FullName } catch { }
        Info "   פעיל: $act · Startup: $s"
        return $s
    }
    $s2 = StartupAfterOpen $exFile 'Exercise\Exercise.csproj'
    Check 'follow-exercise' ($s2 -like '*Exercise\Exercise.csproj*') "מעבר ל-Exercise\Program.cs -> ה-Startup Project הוא Exercise: $s2"
    $s3 = StartupAfterOpen $bankFile $name2
    Check 'follow-bank' ($s3 -like "*$name2*") "חזרה לקובץ התרגיל מהמאגר ($([IO.Path]::GetFileName($bankFile))) -> ה-Startup Project הוא התרגיל מהמאגר (ולא תרגיל המחסנית): $s3"
    Shot '36-bank-startup'
    $d = $null
}
Check 'single-vs2' (@(Get-Process devenv -ErrorAction SilentlyContinue).Count -eq 1) 'עדיין Visual Studio אחד' ''
KillJava

# ------------------------------------------------------------------ 7ב. פרויקט רגיל, בלי התבנית של DSViz
# ------------------------------------------------------------------ 7ג. "עמדת ניהול" בסרגל: רק למורה מחובר, ופותחת את חלון המורה
Log "== 7ג. 'עמדת ניהול' בסרגל: לא מחובר / תלמיד -> אין; מורה -> יש, והלחיצה פותחת את עמדת הניהול; התנתקות -> נעלם"
function TeacherBtnState() {
    $els = @(VsByName $vsP.Id 'עמדת ניהול')
    if ($els.Count -eq 0) { return 'absent' }
    $c = $els[0].Current
    return ("present enabled={0} offscreen={1} rect={2}" -f $c.IsEnabled, $c.IsOffscreen, $c.BoundingRectangle)
}
function TeacherBtnShown([string]$st) { return ($st -match 'enabled=True offscreen=False') }
function WaitTeacherBtn([bool]$want, [int]$sec = 30) {
    $t0 = Get-Date; $st = ''
    while (((Get-Date) - $t0).TotalSeconds -lt $sec) {
        $st = TeacherBtnState
        if ((TeacherBtnShown $st) -eq $want) { break }
        Start-Sleep 2
    }
    return $st
}
if (-not $teacherSeed) {
    Info "דילוג: בלי -BankJar מהמקור (ה-dsviz-bank-vs.jar שבהתקנה ישן — בלי הסימון למורה ובלי מצב teacher)"
} else {
    KillJava
    Info "לא מחובר: $(Seed 'none' '-')"
    $st = WaitTeacherBtn $false 20
    Check 'teacher-btn-signedout' (-not (TeacherBtnShown $st)) "לא מחובר — אין 'עמדת ניהול' בסרגל" $st
    Info "תלמיד: $(Seed 'student' 'pupil@test.dev')"
    $st = WaitTeacherBtn $false 20
    Check 'teacher-btn-student' (-not (TeacherBtnShown $st)) "תלמיד מחובר — אין 'עמדת ניהול' בסרגל" $st
    Shot '35-toolbar-student'
    Info "מורה: $(Seed 'teacher' 'teacher@test.dev')"
    $st = WaitTeacherBtn $true 40
    Check 'teacher-btn-teacher' (TeacherBtnShown $st) "מורה מחובר — 'עמדת ניהול' מופיעה בסרגל (בלי לחזור ל-Visual Studio)" $st
    Shot '36-toolbar-teacher'
    # הלחיצה מריצה את BankVs teacher — ה-agent (מצב teacher) בודק את החלון מבפנים: רכיבי Swing לא נראים ל-UI Automation
    function ClickTeacher() { $e = ToolbarButton $vsP.Id @('עמדת ניהול'); if ($e) { return ClickEl $e } else { return 'הכפתור לא נמצא' } }
    $st = RunStage 'teacher' '37-teacher' @(
        'waitwin עמדת ניהול', 'wait 2500', 'shot panel', 'has K7QM', 'has התנתקות', 'has פתח עמדת ניהול בדפדפן', 'has teacher@test.dev', 'mark panel'
    ) { ClickTeacher } 150
    $res = if (Test-Path (Join-Path $ctl 'results.txt')) { Get-Content (Join-Path $ctl 'results.txt') -Encoding utf8 -Raw } else { '' }
    Info "חלון המורה: $($res.Trim() -replace "`r?`n", ' ; ')"
    Check 'teacher-panel-opens' (($st -eq 'done') -and (Test-Path (Join-Path $ctl 'shots-teacher\panel.json'))) "הלחיצה על 'עמדת ניהול' פתחה את חלון המורה" "סטטוס $st ; $((Windows) -join ' ; ')"
    Check 'teacher-panel-code' ($res -match 'has K7QM = True') "בחלון המורה: הקוד של המורה (K7QM)" $res
    Check 'teacher-panel-buttons' (($res -match 'has התנתקות = True') -and ($res -match 'has פתח עמדת ניהול בדפדפן = True')) "בחלון המורה: 'פתח עמדת ניהול בדפדפן' ו'התנתקות'" $res
    Check 'teacher-panel-email' ($res -match 'has teacher@test.dev = True') "בחלון המורה: המייל של המורה" $res
    KillJava
    Info "התנתקות: $(Seed 'none' '-')"
    $st = WaitTeacherBtn $false 40
    Check 'teacher-btn-after-signout' (-not (TeacherBtnShown $st)) "אחרי התנתקות — 'עמדת ניהול' נעלמת מהסרגל" $st
    Shot '38-toolbar-after-signout'
}

Log "== 7ב. פרויקט Console App רגיל (כמו 'Create a new project' -> Console App): התלמיד כותב קוד עם תור, לא שומר, ולוחץ 'הרצה ויזואלית'"
$plainDir = Join-Path $env:USERPROFILE 'source\repos\PlainQueue'
Remove-Item -Recurse -Force $plainDir -ErrorAction SilentlyContinue
& dotnet new console -n PlainQueue -o $plainDir --force | Out-Null          # אותה תבנית Console App ש-Visual Studio משתמש בה
& dotnet new sln -n PlainQueue -o $plainDir --force | Out-Null
& dotnet sln (Join-Path $plainDir 'PlainQueue.sln') add (Join-Path $plainDir 'PlainQueue.csproj') | Out-Null
$plainSln = Join-Path $plainDir 'PlainQueue.sln'
$plainProg = Join-Path $plainDir 'Program.cs'
$studentCode = @'
using Unit4.CollectionsLib;

Queue<int> q = new Queue<int>();
q.Insert(5);
q.Insert(8);
q.Insert(3);
int sum = 0;
while (!q.IsEmpty())
{
    sum += q.Remove();
}
Console.WriteLine("sum = " + sum);
'@
$d = GetDte
$plainOpen = $false; $typed = $false
try { $d.Solution.Close($false); Start-Sleep 3; $d.Solution.Open($plainSln); $plainOpen = $true } catch { Info "DTE (פתיחת הפתרון): $_" }
Start-Sleep 15
try { $null = $d.ItemOperations.OpenFile($plainProg) } catch { Info "DTE (פתיחת Program.cs): $_" }
Start-Sleep 3
# "כותבים" בעורך במקום ה-Hello, World של התבנית — בלי לשמור (כמו תלמיד שכתב ולחץ מיד על הכפתור)
try {
    $td = $d.ActiveDocument.Object('TextDocument')
    $td.StartPoint.CreateEditPoint().ReplaceText($td.EndPoint, ($studentCode -replace "`r?`n", "`r`n"), 0)
    $typed = -not $d.ActiveDocument.Saved
} catch { Info "DTE (כתיבה בעורך): $_" }
$sln2 = ''; try { $sln2 = $d.Solution.FullName } catch { }
Info "פתרון פתוח: $sln2"
$d = $null
Check 'plain-open' ($plainOpen -and $typed -and $sln2 -like '*PlainQueue.sln') "פרויקט Console App רגיל נפתח ב-Visual Studio, והקוד עם התור נכתב בעורך (עדיין לא נשמר)" "פתרון: $sln2 · נכתב: $typed"
Check 'plain-unsaved' (-not ((Get-Content $plainProg -Raw -Encoding utf8) -match 'Insert')) "לפני הלחיצה: בדיסק עדיין ה-Hello, World של התבנית" ''
Shot '50-plain-typed'
# ה-trace של ההרצה (%TEMP%\dsviz-*.trace) קיים כל עוד החלון של DSViz פתוח — ממנו רואים שהתור באמת נרשם
Get-ChildItem $env:TEMP -Filter 'dsviz-*.trace' -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
$t5 = ClickRun '51-plain-run'
$trace = Get-ChildItem $env:TEMP -Filter 'dsviz-*.trace' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1
$tt = if ($trace) { Get-Content $trace.FullName -Raw -Encoding utf8 } else { '' }
if ($trace) { Copy-Item $trace.FullName (Join-Path $Out 'plain-queue.trace') -ErrorAction SilentlyContinue }
Check 'plain-viewer' ($t5.Count -gt 0) "פרויקט רגיל: 'הרצה ויזואלית' בסרגל פתחה את החלון של DSViz — $($t5 -join ' | ')" ((Windows) -join ' ; ')
Check 'plain-saved' ((Get-Content $plainProg -Raw -Encoding utf8) -match 'q\.Insert\(8\)') "הכפתור שמר את הקוד לפני ההרצה (כמו הכפתור הירוק) — רץ מה שהתלמיד כתב, לא ה-Hello, World" ''
Check 'plain-queue' (($tt -match 'נוצר תור') -and ($tt -match 'q\.Insert\(8\)') -and ($tt -match 'q\.Remove\(\)')) "ההרצה הוויזואלית רשמה את התור: 'נוצר תור ריק: q', q.Insert(8), q.Remove()" $(if ($tt) { $tt.Substring(0, [Math]::Min(300, $tt.Length)) } else { 'אין trace' })
KillDsviz; Start-Sleep 2

# ------------------------------------------------------------------ 7ג. שני פרויקטים באותו פתרון: 'הרצה ויזואלית' מריצה את הפרויקט של הקובץ הפעיל
# A (מחסנית, שני קבצים: Program.cs משתמש ב-Helper.cs) ו-B (תור). בכל פעם כותבים בלי לשמור, עוברים קובץ, לוחצים — ובודקים ב-trace מה באמת רץ
Log "== 7ג. שני פרויקטים: 'הרצה ויזואלית' מריצה את הפרויקט שהתלמיד כותב בו עכשיו (גם כשעוברים בין פרויקטים, וגם מקובץ עזר)"
$twoDir = Join-Path $env:USERPROFILE 'source\repos\TwoProjects'
Remove-Item -Recurse -Force $twoDir -ErrorAction SilentlyContinue
$dirA = Join-Path $twoDir 'StackA'; $dirB = Join-Path $twoDir 'QueueB'
& dotnet new console -n StackA -o $dirA --force | Out-Null
& dotnet new console -n QueueB -o $dirB --force | Out-Null
& dotnet new sln -n TwoProjects -o $twoDir --force | Out-Null
& dotnet sln (Join-Path $twoDir 'TwoProjects.sln') add (Join-Path $dirA 'StackA.csproj') (Join-Path $dirB 'QueueB.csproj') | Out-Null
$helperA = Join-Path $dirA 'Helper.cs'
[IO.File]::WriteAllText($helperA, "static class Helper`r`n{`r`n    public static int Twice(int x) { return x * 2; }`r`n}`r`n", (New-Object Text.UTF8Encoding($true)))
$progA = Join-Path $dirA 'Program.cs'; $progB = Join-Path $dirB 'Program.cs'
$codeA1 = "using Unit4.CollectionsLib;`r`n`r`nStack<int> s = new Stack<int>();`r`ns.Push(7);`r`ns.Push(Helper.Twice(4));`r`nConsole.WriteLine(s.Pop());`r`n"
$codeB1 = "using Unit4.CollectionsLib;`r`n`r`nQueue<int> q = new Queue<int>();`r`nq.Insert(42);`r`nConsole.WriteLine(q.Remove());`r`n"
$codeA2 = "using Unit4.CollectionsLib;`r`n`r`nStack<int> s = new Stack<int>();`r`ns.Push(99);`r`ns.Push(Helper.Twice(5));`r`nConsole.WriteLine(s.Top());`r`n"
function EditDoc([string]$path, [string]$code) {
    $dd = GetDte
    try {
        $null = $dd.ItemOperations.OpenFile($path); Start-Sleep 2
        $td = $dd.ActiveDocument.Object('TextDocument')
        $td.StartPoint.CreateEditPoint().ReplaceText($td.EndPoint, $code, 0)
        return (-not $dd.ActiveDocument.Saved)
    } catch { Info "DTE (עריכה של $path): $_"; return $false } finally { $dd = $null }
}
function ActivateDoc([string]$path) {
    $dd = GetDte
    try { $null = $dd.ItemOperations.OpenFile($path); Start-Sleep 2; return $dd.ActiveDocument.FullName } catch { Info "DTE (פתיחה של $path): $_"; return '' } finally { $dd = $null }
}
function RunAndTrace([string]$shot) {
    Get-ChildItem $env:TEMP -Filter 'dsviz-*.trace' -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
    $tw = ClickRun $shot
    Start-Sleep 2
    $tr = Get-ChildItem $env:TEMP -Filter 'dsviz-*.trace' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1
    $txt = if ($tr) { Get-Content $tr.FullName -Raw -Encoding utf8 } else { '' }
    if ($tr) { Copy-Item $tr.FullName (Join-Path $Out "$shot.trace") -ErrorAction SilentlyContinue }
    KillDsviz; Start-Sleep 2
    return @{ win = ($tw.Count -gt 0); text = $txt }
}
function Snip([string]$t) { if ($t) { $t.Substring(0, [Math]::Min(240, $t.Length)) -replace "`r?`n", ' ' } else { 'אין trace' } }
$d = GetDte
try { $d.Solution.Close($false); Start-Sleep 3; $d.Solution.Open((Join-Path $twoDir 'TwoProjects.sln')) } catch { Info "DTE (פתיחת TwoProjects): $_" }
$d = $null
Start-Sleep 20
$eA = EditDoc $progA $codeA1
$eB = EditDoc $progB $codeB1
Info "נכתב בלי לשמור: A=$eA B=$eB"
# 1. הקובץ הפעיל: Program.cs של A
$act = ActivateDoc $progA; Info "פעיל: $act"
$r1 = RunAndTrace '52-two-A'
Check 'two-run-A' ($r1.win -and $r1.text -match 's\.Push\(7\)' -and $r1.text -notmatch 'q\.Insert\(42\)') "שני פרויקטים: הקובץ הפעיל ב-StackA — רץ StackA (s.Push(7)), לא QueueB" (Snip $r1.text)
Check 'two-saved-A' ((Get-Content $progA -Raw -Encoding utf8) -match 's\.Push\(7\)') "הקוד של StackA נשמר לפני ההרצה" ''
# 2. עוברים ל-Program.cs של B
$act = ActivateDoc $progB; Info "פעיל: $act"
$r2 = RunAndTrace '53-two-B'
Check 'two-run-B' ($r2.win -and $r2.text -match 'q\.Insert\(42\)' -and $r2.text -notmatch 's\.Push') "מעבר ל-QueueB — רץ QueueB (q.Insert(42)), לא StackA" (Snip $r2.text)
Check 'two-saved-B' ((Get-Content $progB -Raw -Encoding utf8) -match 'q\.Insert\(42\)') "הקוד של QueueB נשמר לפני ההרצה" ''
# 3. משנים שוב את A (בלי לשמור) ועוברים לקובץ העזר של A (בלי Main) — רץ A עם הקוד החדש
$eA2 = EditDoc $progA $codeA2
$act = ActivateDoc $helperA; Info "פעיל: $act (A2 נכתב: $eA2)"
$r3 = RunAndTrace '54-two-A-helper'
Check 'two-run-helper' ($r3.win -and $r3.text -match 's\.Push\(99\)' -and $r3.text -notmatch 's\.Push\(7\)' -and $r3.text -notmatch 'q\.Insert') "מקובץ העזר Helper.cs של StackA — רץ StackA עם הקוד העדכני (s.Push(99)), גם בלי לשמור לפני" (Snip $r3.text)
# 4. וחזרה ל-B — שוב B
$act = ActivateDoc $progB; Info "פעיל: $act"
$r4 = RunAndTrace '55-two-B-again'
Check 'two-run-B-again' ($r4.win -and $r4.text -match 'q\.Insert\(42\)' -and $r4.text -notmatch 's\.Push') "חזרה ל-QueueB — רץ QueueB" (Snip $r4.text)

# ------------------------------------------------------------------ 9. צילומים לסרטון הדרכה: מהפתיחה של Visual Studio עד כניסת מורה עם Google
# כל צעד — צילום מסך מלא (tut-NN-...png, וגם בתיקייה tutorial). פרויקט Console App רגיל (בלי התבנית של DSViz), קוד בלי שורת using.
Log "== 9. סרטון הדרכה: Visual Studio מההתחלה -> פרויקט חדש רגיל -> קוד עם תור -> הרצה ויזואלית -> מאגר שאלות -> כניסת מורה עם Google"
function Elements([int]$vsPid, [string]$pattern, [string]$typePattern = '') {
    @(VsElements $vsPid | Where-Object { (ElName $_) -match $pattern -and (-not $typePattern -or (ElType $_) -match $typePattern) })
}
function WaitEl([int]$vsPid, [string]$pattern, [string]$typePattern = '', [int]$sec = 60) {
    for ($i = 0; $i -lt $sec; $i++) { $e = @(Elements $vsPid $pattern $typePattern); if ($e.Count -gt 0) { return $e[0] }; Start-Sleep 1 }
    return $null
}
# כל צילום של ההדרכה נרשם ב-tutorial\steps.json: הקובץ, סוג הצעד (click / type / look) ולאן לוחצים (המלבן על המסך) —
# ממנו נבנה הסרטון (tutorials/screens.js): העכבר זז בדיוק לכפתור האמיתי, ההדגשה סביבו, והמצלמה מתקרבת אליו
$script:tut = New-Object System.Collections.Generic.List[object]
$script:tutOn = $true
function RectOf($el) {
    try { $r = $el.Current.BoundingRectangle; if ($r.Width -gt 0 -and $r.Height -gt 0 -and -not $el.Current.IsOffscreen) { return [ordered]@{ x = [int]$r.X; y = [int]$r.Y; w = [int]$r.Width; h = [int]$r.Height } } } catch { }
    return $null
}
function UnionRect($els) {
    $rs = @($els | ForEach-Object { RectOf $_ } | Where-Object { $_ })
    if ($rs.Count -eq 0) { return $null }
    $x1 = ($rs | ForEach-Object { $_.x } | Measure-Object -Minimum).Minimum; $y1 = ($rs | ForEach-Object { $_.y } | Measure-Object -Minimum).Minimum
    $x2 = ($rs | ForEach-Object { $_.x + $_.w } | Measure-Object -Maximum).Maximum; $y2 = ($rs | ForEach-Object { $_.y + $_.h } | Measure-Object -Maximum).Maximum
    return [ordered]@{ x = [int]$x1; y = [int]$y1; w = [int]($x2 - $x1); h = [int]($y2 - $y1) }
}
function TutAdd([string]$name, [string]$kind, $rect) { $script:tut.Add([ordered]@{ shot = "$name.png"; kind = $kind; target = $rect }) }
# לפני כל צילום של ההדרכה: בלי חלונות שמכסים את Visual Studio — חלון "Sign in" של Microsoft / Azure / GitHub,
# חלון Copilot Chat ולשונית What's New (במחשב של תלמיד הם לא תמיד שם, ובסרטון הם מסתירים את הכפתור שמסבירים עליו)
function TutClean() {
    $closed = @()
    # חלון המסוף של מחשב הבדיקה (hosted-compute-agent) — לא חלק מ-Visual Studio, ובסרטון הוא מכסה את שולחן העבודה
    Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -match 'HostedComputeAgent|hosted-compute-agent' } | ForEach-Object {
        [Screen2]::ShowWindow($_.MainWindowHandle, 6) | Out-Null; $closed += "מסוף: $($_.ProcessName)"
    }
    try {
        $wins = [Windows.Automation.AutomationElement]::RootElement.FindAll([Windows.Automation.TreeScope]::Children, [Windows.Automation.Condition]::TrueCondition)
        foreach ($w in $wins) {
            $n = ElName $w
            if ($n -match '^(Sign in|Sign in to your account|Pick an account|Microsoft account|Visual Studio account|Microsoft Azure|Add an account|GitHub Copilot)' -or $n -match 'Sign in to (Visual Studio|your account)') {
                try { $w.GetCurrentPattern([Windows.Automation.WindowPattern]::Pattern).Close(); $closed += $n } catch { }
            }
        }
    } catch { }
    if ($vsP) {
        # חלונות מתוך Visual Studio (WebView של הכניסה): כפתור Close / Cancel בתוך חלון שהשם שלו כניסה
        foreach ($w in @(VsElements $vsP.Id | Where-Object { (ElType $_) -match 'Window' -and (ElName $_) -match '^(Sign in|Microsoft account|Visual Studio account|Pick an account)|Sign in to' })) {
            try { $w.GetCurrentPattern([Windows.Automation.WindowPattern]::Pattern).Close(); $closed += (ElName $w) } catch { }
        }
    }
    try {
        $d = GetDte
        if ($d) {
            foreach ($tw in @($d.Windows)) { try { if ($tw.Caption -match 'Copilot|What.s New|GitHub Copilot') { $c = $tw.Caption; $tw.Close(); $closed += $c } } catch { } }
        }
        $d = $null
    } catch { }
    if ($closed.Count) { Info "לפני צילום ההדרכה נסגרו: $($closed -join ' ; ')"; Start-Sleep 2 }
}
function TutShot([string]$name, $el = $null, [string]$kind = 'look', $rect = $null) {
    TutClean
    # המלבן נלקח אחרי הסגירה (חלון שנסגר יכול להזיז את הכפתור), ורק אם הכפתור באמת על המסך
    if ($el) { $r2 = RectOf $el; if ($r2) { $rect = $r2 } }
    Shot $name
    TutAdd $name $kind $rect
}
# Edge בלי מסך "ברוכים הבאים" (כדי שבצילום יראו את דף הכניסה)
New-Item -Path 'HKLM:\SOFTWARE\Policies\Microsoft\Edge' -Force -ErrorAction SilentlyContinue | Out-Null
Set-ItemProperty -Path 'HKLM:\SOFTWARE\Policies\Microsoft\Edge' -Name 'HideFirstRunExperience' -Value 1 -Type DWord -ErrorAction SilentlyContinue
KillJava; KillDsviz
Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep 5
$tutRepo = Join-Path $env:USERPROFILE 'source\repos'
Remove-Item -Recurse -Force (Join-Path $tutRepo 'Targil1') -ErrorAction SilentlyContinue
# 1. פותחים את Visual Studio (בלי פרויקט) — מסך הפתיחה
$vsP = Start-Process $devenv -PassThru
$startEl = $null
for ($i = 0; $i -lt 120 -and -not $startEl; $i++) {
    Start-Sleep 3
    SkipFirstRun $vsP.Id -KeepStart | Out-Null
    if ($i % 3 -eq 2) { TutClean }
    $startEl = @(Elements $vsP.Id '^Create a new project' 'Button|ListItem|Text|Hyperlink|Group|Custom')[0]
}
Start-Sleep 2
TutShot 'tut-01-vs-start' $startEl 'click'
Check 'tut-start' ($null -ne $startEl) "1. Visual Studio נפתח — מסך הפתיחה עם 'Create a new project'" ((Windows) -join ' ; ')
$created = $false
if ($startEl) {
    # 2. Create a new project — רשימת התבניות; לוחצים על תיבת החיפוש
    Info "לחיצה: $(ClickEl $startEl)"
    $search = WaitEl $vsP.Id '^Search for templates' 'Edit|ComboBox|Custom|Text' 60
    Start-Sleep 4
    if (-not $search) {
        # בלי שם ב-UI Automation: Alt+S (הקיצור שכתוב בתיבת החיפוש) מעביר אליה את הפוקוס — וממנה המלבן לסרטון
        [Windows.Forms.SendKeys]::SendWait('%s'); Start-Sleep 1
        try { $f = [Windows.Automation.AutomationElement]::FocusedElement; if ($f -and $f.Current.ProcessId -eq $vsP.Id) { $search = $f; Info "תיבת החיפוש (Alt+S): $(ElType $f) '$(ElName $f)'" } } catch { }
    }
    TutShot 'tut-02-templates' $search 'click'
    Info "בתבניות מופיעה גם 'תרגיל מבני נתונים (DSViz)': $(@(Elements $vsP.Id 'DSViz').Count -gt 0) (לא צריך אותה)"
    # 3. מקלידים Console App ובוחרים את התבנית של C#
    # מקלידים בתיבת החיפוש: קודם ValuePattern; אם לא נכתב — Alt+S (הקיצור שכתוב בתיבה עצמה) והקלדה, ובודקים שהטקסט באמת שם
    $typedOk = $false
    if ($search) { try { $search.GetCurrentPattern([Windows.Automation.ValuePattern]::Pattern).SetValue('Console App'); $typedOk = ($search.GetCurrentPattern([Windows.Automation.ValuePattern]::Pattern).Current.Value -match 'Console App') } catch { } }
    if (-not $typedOk) {
        if ($search) { [void](ClickEl $search) } else { [Windows.Forms.SendKeys]::SendWait('%s'); Start-Sleep 1 }
        [Windows.Forms.SendKeys]::SendWait('^a'); [Windows.Forms.SendKeys]::SendWait('Console App')
        Start-Sleep 1
        try { $f = [Windows.Automation.AutomationElement]::FocusedElement; if (-not $search) { $search = $f }; $typedOk = ($f.GetCurrentPattern([Windows.Automation.ValuePattern]::Pattern).Current.Value -match 'Console App') } catch { }
    }
    Info "חיפוש התבניות: $(if ($typedOk) { 'נכתב Console App' } else { 'לא הצלחנו לוודא שנכתב' })"
    Start-Sleep 5
    $item = @(Elements $vsP.Id '^Console App' 'ListItem' | Where-Object { (ElName $_) -match 'C#' -and (ElName $_) -notmatch 'Framework' })[0]
    if (-not $item) { $item = @(Elements $vsP.Id '^Console App' 'ListItem' | Where-Object { (ElName $_) -notmatch 'Framework' })[0] }
    Info "התבנית: $(ElName $item)"
    Check 'tut-template' ($null -ne $item) "3. בחיפוש נמצאה התבנית Console App של C# (לא התבנית של DSViz)" "חיפוש: $typedOk"
    TutShot 'tut-03-console-app' $item 'click'
    if ($item) { try { $item.GetCurrentPattern([Windows.Automation.SelectionItemPattern]::Pattern).Select() } catch { [void](ClickEl $item) } }
    Start-Sleep 2
    $next = WaitEl $vsP.Id '^Next$' 'Button' 10
    TutShot 'tut-03b-next' $next 'click'
    if ($next) { Info "Next: $(ClickEl $next)" }
    # 4. שם הפרויקט, ושוב Next
    $nameBox = WaitEl $vsP.Id '^Project name' 'Edit' 30
    Start-Sleep 2
    TutShot 'tut-04-project-name' $nameBox 'type'
    if ($nameBox) { try { $nameBox.GetCurrentPattern([Windows.Automation.ValuePattern]::Pattern).SetValue('Targil1') } catch { } }
    Start-Sleep 2
    $next = WaitEl $vsP.Id '^Next$' 'Button' 10
    TutShot 'tut-04b-next' $next 'click'
    if ($next) { Info "Next: $(ClickEl $next)" }
    # 5. הגרסה של ‎.NET -> Create
    Start-Sleep 4
    $create = WaitEl $vsP.Id '^Create$' 'Button' 20
    TutShot 'tut-05-framework' $create 'click'
    if ($create) { Info "Create: $(ClickEl $create)" }
    for ($i = 0; $i -lt 90 -and -not $created; $i++) { Start-Sleep 2; $vsP.Refresh(); $created = $vsP.MainWindowTitle -like '*Targil1*' }
}
if (-not $created) {
    # לא הצלחנו ללחוץ דרך מסך התבניות (גרסה אחרת של Visual Studio) — אותה תבנית Console App, ופותחים אותה
    Info "יצירה דרך המסך לא הסתיימה — dotnet new console (אותה תבנית) ופתיחה ב-Visual Studio"
    $tp = Join-Path $tutRepo 'Targil1\Targil1'
    & dotnet new console -n Targil1 -o $tp --force | Out-Null
    & dotnet new sln -n Targil1 -o (Split-Path $tp) --force | Out-Null
    & dotnet sln (Join-Path (Split-Path $tp) 'Targil1.sln') add (Join-Path $tp 'Targil1.csproj') | Out-Null
    $d = GetDte; try { $d.Solution.Open((Join-Path (Split-Path $tp) 'Targil1.sln')) } catch { Info "DTE: $_" }; $d = $null
    for ($i = 0; $i -lt 60 -and -not $created; $i++) { Start-Sleep 2; $vsP.Refresh(); $created = $vsP.MainWindowTitle -like '*Targil1*' }
}
Check 'tut-created' $created "2-5. נוצר פרויקט Console App רגיל (Targil1) ונפתח ב-Visual Studio" $vsP.MainWindowTitle
[Screen2]::ShowWindow($vsP.MainWindowHandle, 3) | Out-Null
[Screen2]::SetForegroundWindow($vsP.MainWindowHandle) | Out-Null
$tbEls = @()
for ($i = 0; $i -lt 30 -and @($tbEls | ForEach-Object { ElName $_ } | Select-Object -Unique).Count -lt 4; $i++) {
    Start-Sleep 3
    $tbEls = @(VsElements $vsP.Id | Where-Object { (ElName $_) -match '^(תרגיל חדש|מאגר שאלות|הוספת מבנה נתונים|שליחת תרגיל|הדרכה|הרצה ויזואלית)$' })
}
$tb = @($tbEls | ForEach-Object { ElName $_ } | Select-Object -Unique)
Start-Sleep 5
# 6. הפרויקט פתוח — וסרגל הכלים של DSViz
TutShot 'tut-06-project-and-toolbar' $null 'look' (UnionRect $tbEls)
Check 'tut-toolbar' ($tb.Count -ge 4) "6. בפרויקט הרגיל — סרגל הכלים של DSViz מופיע" ($tb -join ' ; ')
# 7. הקוד שבקובץ (Hello, World!) -> כותבים קוד עם תור, שורה אחרי שורה — בלי שום שורה בראש הקובץ
$prog = @(Get-ChildItem $tutRepo -Recurse -Filter Program.cs -ErrorAction SilentlyContinue | Where-Object { $_.FullName -like '*Targil1*' })[0]
$tutLines = @(
    'Queue<int> q = new Queue<int>();',
    'q.Insert(5);',
    'q.Insert(8);',
    'q.Insert(3);',
    'int sum = 0;',
    'while (!q.IsEmpty())',
    '{',
    '    sum += q.Remove();',
    '}',
    'Console.WriteLine("sum = " + sum);'
)
$d = GetDte
try { if ($prog) { $null = $d.ItemOperations.OpenFile($prog.FullName) } } catch { Info "DTE (פתיחת Program.cs): $_" }
Start-Sleep 4
TutShot 'tut-07-default-code' $null 'look'
try {
    $td = $d.ActiveDocument.Object('TextDocument')
    $td.StartPoint.CreateEditPoint().ReplaceText($td.EndPoint, '', 0)
    Start-Sleep 1
    TutShot 'tut-07a-empty' $null 'type'
    for ($k = 1; $k -le $tutLines.Count; $k++) {
        $td.StartPoint.CreateEditPoint().ReplaceText($td.EndPoint, (($tutLines[0..($k - 1)]) -join "`r`n"), 0)
        Start-Sleep -Milliseconds 700
        TutShot ('tut-07-code-{0:D2}' -f $k) $null 'type'
    }
    $d.ActiveDocument.Save()
} catch { Info "DTE (כתיבת הקוד): $_" }
$d = $null
Start-Sleep 8   # IntelliSense מסיים לבדוק — רואים שאין קו אדום מתחת ל-Queue
TutShot 'tut-07-code-done' $null 'look'
# 8. הרצה ויזואלית: הכפתור בסרגל -> החלון של DSViz -> "הבא" כמה פעמים (ה-agent לוחץ בחלון האמיתי)
KillDsviz; Start-Sleep 2
[Screen2]::SetForegroundWindow($vsP.MainWindowHandle) | Out-Null
Start-Sleep 2
$runBtn = ToolbarButton $vsP.Id @('הרצה ויזואלית')
TutShot 'tut-08-run-button' $runBtn 'click'
$viewerLines = @('waitwin הרצה ויזואלית', 'wait 3500', 'target הבא', 'mark v00')
for ($k = 1; $k -le 7; $k++) { $viewerLines += @('click הבא', 'wait 1600', 'target הבא', ('mark v{0:D2}' -f $k)) }
$viewerLines += @('wait 500', 'mark v-end')
TutClean
$st = RunStage 'viewer' 'tut-08' $viewerLines { if ($runBtn) { ClickEl $runBtn } else { 'הכפתור לא נמצא' } } 240
Check 'tut-run' ($st -eq 'done') "8. 'הרצה ויזואלית' בסרגל — נפתח החלון של DSViz, ו'הבא' מתקדם צעד אחרי צעד (בלי שורת using ובלי תבנית DSViz)" "סטטוס $st · $((Windows) -join ' ; ')"
KillDsviz; Start-Sleep 3
# 9-10. מאגר שאלות -> מסך הכניסה -> מורה: כניסה עם Google -> הדפדפן נפתח בדף הכניסה של Google
Get-Process msedge, chrome -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
[Screen2]::SetForegroundWindow($vsP.MainWindowHandle) | Out-Null
Start-Sleep 2
$bankBtn = ToolbarButton $vsP.Id $bankNames
TutShot 'tut-09-bank-button' $bankBtn 'click'
TutClean
$st = RunStage 'bank' 'tut-10' @('waitwin כניסה למאגר', 'wait 2500', 'target כניסה עם Google', 'mark login-screen', 'clicktarget כניסה עם Google', 'wait 4000', 'mark teacher-waiting', 'wait 12000', 'mark google-page') { ClickBank } 240
$titles = (Windows) -join ' ; '
Info "חלונות אחרי 'כניסה עם Google': $titles"
Check 'tut-gate' ($st -eq 'done') "9. 'מאגר שאלות' -> מסך הכניסה, ולחיצה על 'כניסה עם Google' של מורה" "סטטוס $st"
Check 'tut-browser' ($titles -match '(msedge|chrome|firefox)\[\d+\]: .*(Google|Sign in|כניסה|DSViz|/signin\?role=teacher)') "10. הדפדפן נפתח בדף הכניסה (Google) — רואים בצילום tut-10-google-page.png" $titles
KillJava
Get-Process msedge, chrome -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
# 11. אחרי הכניסה (כאן בלי כניסה אמיתית: dsviz.access=off — אותו חלון שנפתח אחרי הכניסה): בוחרים שאלה -> צור תרגיל -> החלון 'השאלה' -> הכן סעיף ב'
[Screen2]::SetForegroundWindow($vsP.MainWindowHandle) | Out-Null
Start-Sleep 2
TutClean
$st = RunStage 'bank' 'tut-11' @(
    '#access=off', 'waitwin מאגר', 'wait 3000', 'mark bank-window',
    'bankselect stack-max-contains', 'wait 1500', 'target צור תרגיל', 'mark selected',
    'clicktarget צור תרגיל', 'waitwin השאלה', 'wait 6000', "target הכן סעיף ב'", 'mark float',
    "clicktarget הכן סעיף ב'", 'wait 9000', 'mark prepared'
) { ClickBank } 420
Check 'tut-bank' ($st -eq 'done') "11. אחרי הכניסה: חלון המאגר -> בחירת שאלה -> 'צור תרגיל' -> 'השאלה' -> 'הכן סעיף ב''" "סטטוס $st"
Start-Sleep 4
TutShot 'tut-12-exercise-in-vs' $null 'look'
KillJava
$script:tutOn = $false
New-Item -ItemType Directory -Force (Join-Path $Out 'tutorial') | Out-Null
Get-ChildItem $Out -Filter 'tut-*.png' | ForEach-Object { Copy-Item $_.FullName (Join-Path $Out 'tutorial') -Force }
$tutSteps = @($script:tut | Where-Object { Test-Path (Join-Path $Out $_.shot) })
$tutInfo = [ordered]@{ vs = "$vsName $vsVer"; screen = [ordered]@{ w = [Windows.Forms.SystemInformation]::VirtualScreen.Width; h = [Windows.Forms.SystemInformation]::VirtualScreen.Height }; steps = $tutSteps }
[IO.File]::WriteAllText((Join-Path $Out 'tutorial\steps.json'), ($tutInfo | ConvertTo-Json -Depth 6), (New-Object Text.UTF8Encoding($false)))
Info "צילומי ההדרכה: $($tutSteps.Count) צעדים -> tutorial\steps.json"
Check 'tut-steps' ($tutSteps.Count -ge 25) "סרטון ההדרכה: נרשמו $($tutSteps.Count) צעדים עם הצילום ולאן לוחצים" ''
KillJava

# ------------------------------------------------------------------ סיום
Log "== 8. יומנים"
Copy-Item (Join-Path $ctl 'agent.log') $Out -ErrorAction SilentlyContinue
foreach ($m in 'submit', 'bank', 'teacher') { if (Test-Path (Join-Path $ctl "shots-$m")) { Copy-Item (Join-Path $ctl "shots-$m") (Join-Path $Out "swing-shots-$m") -Recurse -Force } }
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
