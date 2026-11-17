# (UTF-8 עם BOM) עזרים ל-e2e.ps1 (נטען ב-dot-source אחרי ש-$Out נקבע): בדיקות כמו תלמיד אמיתי —
#   - לחיצות עכבר והקשות מקלדת אמיתיות (SendInput) בנקודה על המסך של רכיב שנמצא ב-UI Automation. בלי InvokePattern ובלי DTE.ExecuteCommand
#     לפעולות שנבדקות. (DTE משמש רק להכנה — איזה קובץ פתוח, מי ה-Startup Project — ולאימות.)
#   - צילום מסך מלא ממוספר אחרי כל צעד, עם צלב אדום בנקודת הלחיצה.
#   - כל בדיקה מדפיסה שורת PASS / FAIL (ו-NOTE למה שרק נרשם), ובסוף טבלה ל-$GITHUB_STEP_SUMMARY ו-summary.txt.
# Windows PowerShell 5.1 (‎shell: powershell‎): ה-C# שב-Add-Type הוא C# 5.
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$OutputEncoding = [Text.Encoding]::UTF8
New-Item -ItemType Directory -Force $Out | Out-Null
$Out = (Resolve-Path $Out).Path
$script:shots = Join-Path $Out 'shots'
New-Item -ItemType Directory -Force $script:shots | Out-Null
$script:fails = 0
$script:step = 0
$script:lines = New-Object System.Collections.Generic.List[string]
$script:rows = New-Object System.Collections.Generic.List[object]

# ------------------------------------------------------------------ יומן ובדיקות
function Log([string]$s) { Write-Host $s; $script:lines.Add($s) }
function Info([string]$s) { Log "  ..   $s" }
function Row([string]$res, [string]$id, [string]$text, [string]$why) { $script:rows.Add([pscustomobject]@{ Result = $res; Id = $id; Text = $text; Why = $why; Shot = $script:step }) }
function Pass([string]$id, [string]$text) { Log "PASS $id — $text"; Row 'PASS' $id $text '' }
function Fail([string]$id, [string]$text, [string]$why = '') { $script:fails++; Log ("FAIL $id — $text" + $(if ($why) { " — $why" } else { '' })); Row 'FAIL' $id $text $why }
function Note([string]$id, [string]$text) { Log "NOTE $id — $text"; Row 'NOTE' $id $text '' }
function Check([string]$id, [bool]$cond, [string]$text, [string]$why = '') { if ($cond) { Pass $id $text } else { Fail $id $text $why } }
# כותרת של חלק בבדיקה. בכל כותרת נשמר מה שנאסף עד עכשיו (summary.txt) — גם אם הצעד ייעצר באמצע, רואים עד איפה הגיע
function Section([string]$s) {
    Log ''; Log "== $s"
    try { $script:lines | Set-Content (Join-Path $Out 'summary.txt') -Encoding utf8 } catch { }
}
# מגבלת זמן לכל הבדיקה: אחרי 150 דקות לא מתחילים חלק חדש (נרשם ככישלון), כדי שהסיכום והיומנים ייכתבו לפני שהצעד נעצר
$script:t00 = Get-Date
function AssertTime() { if (((Get-Date) - $script:t00).TotalMinutes -gt 150) { throw 'נגמר הזמן של הבדיקה (150 דקות) — החלק הזה לא נבדק' } }
function Save-Summary([string]$title) {
    $pass = @($script:rows | Where-Object { $_.Result -eq 'PASS' }).Count
    $note = @($script:rows | Where-Object { $_.Result -eq 'NOTE' }).Count
    Log ''
    Log ("SUMMARY {0}: {1} PASS / {2} FAIL / {3} NOTE · {4} צילומים" -f $title, $pass, $script:fails, $note, $script:step)
    $script:lines | Set-Content (Join-Path $Out 'summary.txt') -Encoding utf8
    $md = New-Object System.Collections.Generic.List[string]
    $md.Add("### $title — $pass PASS / $($script:fails) FAIL / $note NOTE")
    $md.Add('')
    $md.Add('| | check | what | details | shot |')
    $md.Add('|---|---|---|---|---|')
    foreach ($r in $script:rows) {
        $icon = if ($r.Result -eq 'PASS') { 'PASS' } elseif ($r.Result -eq 'FAIL') { '**FAIL**' } else { 'note' }
        $t = ([string]$r.Text) -replace '\|', '/' -replace "[`r`n]+", ' '
        $w = ([string]$r.Why) -replace '\|', '/' -replace "[`r`n]+", ' '
        if ($w.Length -gt 300) { $w = $w.Substring(0, 300) + '…' }
        if ($t.Length -gt 300) { $t = $t.Substring(0, 300) + '…' }
        $md.Add(("| {0} | {1} | {2} | {3} | {4:D3} |" -f $icon, $r.Id, $t, $w, [int]$r.Shot))
    }
    $md | Set-Content (Join-Path $Out 'summary.md') -Encoding utf8
    if ($env:GITHUB_STEP_SUMMARY) { try { ($md -join "`n") + "`n" | Add-Content $env:GITHUB_STEP_SUMMARY -Encoding utf8 } catch { } }
}

# ------------------------------------------------------------------ קלט אמיתי, מסך
Add-Type -AssemblyName System.Windows.Forms, System.Drawing, UIAutomationClient, UIAutomationTypes, WindowsBase
if (-not ('DsReal' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Threading;
public static class DsReal {
    [StructLayout(LayoutKind.Sequential)] public struct MOUSEINPUT { public int dx; public int dy; public uint mouseData; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Explicit)] public struct InputUnion { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] public struct INPUT { public uint type; public InputUnion U; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X; public int Y; }
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
    [DllImport("user32.dll", SetLastError = true)] static extern uint SendInput(uint n, INPUT[] inputs, int size);
    [DllImport("user32.dll")] static extern int GetSystemMetrics(int index);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] static extern bool AttachThreadInput(uint from, uint to, bool attach);
    [DllImport("kernel32.dll")] static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] public static extern bool GetCursorPos(out POINT p);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll", CharSet = CharSet.Ansi)] static extern bool EnumDisplaySettings(string dev, int mode, ref DEVMODE dm);
    [DllImport("user32.dll", CharSet = CharSet.Ansi)] static extern int ChangeDisplaySettings(ref DEVMODE dm, int flags);
    [DllImport("user32.dll")] static extern IntPtr GetDC(IntPtr h);
    [DllImport("user32.dll")] static extern int ReleaseDC(IntPtr h, IntPtr dc);
    [DllImport("gdi32.dll")] static extern int GetDeviceCaps(IntPtr dc, int index);

    public static int SetResolution(int w, int h) {
        DEVMODE dm = new DEVMODE();
        dm.dmSize = (ushort)Marshal.SizeOf(typeof(DEVMODE));
        if (!EnumDisplaySettings(null, -1, ref dm)) return -100;
        dm.dmPelsWidth = (uint)w; dm.dmPelsHeight = (uint)h;
        dm.dmFields = 0x80000 | 0x100000;
        return ChangeDisplaySettings(ref dm, 1);
    }
    /// DPI של המסך (96 = 100%). UI Automation ו-SendInput עובדים בפיקסלים של המסך אחרי SetProcessDPIAware
    public static int Dpi() {
        IntPtr dc = GetDC(IntPtr.Zero);
        try { return GetDeviceCaps(dc, 88); } finally { ReleaseDC(IntPtr.Zero, dc); }
    }
    static void Send(INPUT[] a) {
        uint n = SendInput((uint)a.Length, a, Marshal.SizeOf(typeof(INPUT)));
        if (n != a.Length) throw new InvalidOperationException("SendInput sent " + n + " of " + a.Length + " (error " + Marshal.GetLastWin32Error() + ")");
    }
    static INPUT Mouse(int dx, int dy, uint flags) {
        INPUT i = new INPUT(); i.type = 0; i.U.mi.dx = dx; i.U.mi.dy = dy; i.U.mi.dwFlags = flags; return i;
    }
    static INPUT Key(ushort vk, ushort scan, uint flags) {
        INPUT i = new INPUT(); i.type = 1; i.U.ki.wVk = vk; i.U.ki.wScan = scan; i.U.ki.dwFlags = flags; return i;
    }
    /// מזיזים את העכבר לנקודה (קואורדינטות מוחלטות על כל שולחן העבודה הווירטואלי). מחזיר איפה הסמן באמת
    public static string MoveTo(int x, int y) {
        int vx = GetSystemMetrics(76), vy = GetSystemMetrics(77), vw = GetSystemMetrics(78), vh = GetSystemMetrics(79);
        int ax = (int)Math.Round((x - vx) * 65535.0 / Math.Max(1, vw - 1)), ay = (int)Math.Round((y - vy) * 65535.0 / Math.Max(1, vh - 1));
        Send(new INPUT[] { Mouse(ax, ay, 0x0001 | 0x8000 | 0x4000) });
        Thread.Sleep(120);
        POINT p; GetCursorPos(out p);
        if (Math.Abs(p.X - x) > 2 || Math.Abs(p.Y - y) > 2) { SetCursorPos(x, y); Thread.Sleep(80); GetCursorPos(out p); }
        return p.X + "," + p.Y;
    }
    /// לחיצת עכבר שמאלית אמיתית בנקודה: תזוזה, המתנה קצרה (hover), לחיצה ושחרור
    public static string Click(int x, int y) {
        string at = MoveTo(x, y);
        Thread.Sleep(250);
        Send(new INPUT[] { Mouse(0, 0, 0x0002) });
        Thread.Sleep(70);
        Send(new INPUT[] { Mouse(0, 0, 0x0004) });
        return at;
    }
    /// צירוף מקשים אמיתי: Chord(0x11, 0x74) = Ctrl+F5
    public static void Chord(params ushort[] vks) {
        foreach (ushort k in vks) { Send(new INPUT[] { Key(k, 0, 0) }); Thread.Sleep(40); }
        Thread.Sleep(60);
        for (int i = vks.Length - 1; i >= 0; i--) { Send(new INPUT[] { Key(vks[i], 0, 0x0002) }); Thread.Sleep(40); }
    }
    /// הקלדת טקסט (תווי Unicode), תו-תו — כמו מקלדת
    public static void Type(string text) {
        foreach (char c in text) {
            Send(new INPUT[] { Key(0, (ushort)c, 0x0004), Key(0, (ushort)c, 0x0004 | 0x0002) });
            Thread.Sleep(12);
        }
    }
    /// מביא חלון לקדמה (בלי Alt — ב-Visual Studio הוא מפעיל את התפריט): SetForegroundWindow, ואם Windows חוסם — דרך החוט של החלון הקדמי
    public static bool Front(IntPtr h) {
        if (h == IntPtr.Zero) return false;
        if (IsIconic(h)) ShowWindow(h, 9);
        if (GetForegroundWindow() == h) return true;
        SetForegroundWindow(h);
        Thread.Sleep(150);
        if (GetForegroundWindow() == h) return true;
        uint pid; uint fg = GetWindowThreadProcessId(GetForegroundWindow(), out pid), me = GetCurrentThreadId();
        try {
            AttachThreadInput(me, fg, true);
            BringWindowToTop(h);
            SetForegroundWindow(h);
        } finally { AttachThreadInput(me, fg, false); }
        Thread.Sleep(200);
        return GetForegroundWindow() == h;
    }
}
'@
}
[DsReal]::SetProcessDPIAware() | Out-Null
foreach ($res in @(@(1920, 1080), @(1680, 1050), @(1600, 900), @(1440, 900), @(1280, 1024))) { if ([DsReal]::SetResolution($res[0], $res[1]) -eq 0) { break } }
Start-Sleep 3

# צילום מסך מלא אחרי כל צעד: ‎NNN-שם.png‎, עם צלב אדום בנקודת הלחיצה (אם הייתה)
function Step([string]$name, [int]$x = -1, [int]$y = -1) {
    $script:step++
    $file = '{0:D3}-{1}.png' -f $script:step, (($name -replace '[^A-Za-z0-9\-]+', '_').Trim('_'))   # שמות קבצים ב-ASCII בלבד (Artifacts)
    if ($file.Length -gt 90) { $file = $file.Substring(0, 86) + '.png' }
    try {
        $b = [Windows.Forms.SystemInformation]::VirtualScreen
        $bmp = New-Object Drawing.Bitmap $b.Width, $b.Height
        $g = [Drawing.Graphics]::FromImage($bmp)
        $g.CopyFromScreen($b.Left, $b.Top, 0, 0, $bmp.Size)
        if ($x -ge 0) {
            $pen = New-Object Drawing.Pen ([Drawing.Color]::Red), 3
            $cx = $x - $b.Left; $cy = $y - $b.Top
            $g.DrawEllipse($pen, $cx - 14, $cy - 14, 28, 28)
            $g.DrawLine($pen, $cx - 24, $cy, $cx + 24, $cy)
            $g.DrawLine($pen, $cx, $cy - 24, $cx, $cy + 24)
            $pen.Dispose()
        }
        $bmp.Save((Join-Path $script:shots $file), [Drawing.Imaging.ImageFormat]::Png)
        $g.Dispose(); $bmp.Dispose()
    } catch { Info "צילום $file נכשל: $_" }
    Info "צעד $($script:step): $name -> shots\$file"
}
function Windows() { Get-Process | Where-Object { $_.MainWindowTitle } | ForEach-Object { "$($_.ProcessName)[$($_.Id)]: $($_.MainWindowTitle)" } }

# ------------------------------------------------------------------ UI Automation
function ElName($el) { try { return [string]$el.Current.Name } catch { return '' } }
function ElType($el) { try { return ([string]$el.Current.ControlType.ProgrammaticName) -replace '^ControlType\.', '' } catch { return '' } }
function DescEl($el) {
    if (-not $el) { return 'absent' }
    try { $c = $el.Current; return ("{0} '{1}' enabled={2} offscreen={3} rect={4}" -f (ElType $el), $c.Name, $c.IsEnabled, $c.IsOffscreen, $c.BoundingRectangle) } catch { return 'gone' }
}
function Shown($el) {
    if (-not $el) { return $false }
    try { $c = $el.Current; $r = $c.BoundingRectangle; return (-not $c.IsOffscreen) -and (-not $r.IsEmpty) -and $r.Width -gt 1 -and $r.Height -gt 1 } catch { return $false }
}
# החלונות העליונים של תהליך (החלון הראשי, תפריטים קופצים, תיבות הודעה)
function TopWindows([int]$procId) {
    $c = New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ProcessIdProperty, $procId)
    try { return @([Windows.Automation.AutomationElement]::RootElement.FindAll([Windows.Automation.TreeScope]::Children, $c)) } catch { return @() }
}
# כל החלונות של התהליך חוץ מהחלון הראשי ($mainHandle): תיבות הודעה, חלון "Open File", שאלות. בעץ של UI Automation חלון עם "בעלים"
# (דיאלוג) מופיע כילד של החלון שפתח אותו ולא ליד החלון הראשי — לכן גם החלונות שמתחת לחלון הראשי, ועוד רמה אחת מתחתיהם
function OtherWindows([int]$procId, [int]$mainHandle) {
    $isWindow = New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ControlTypeProperty, [Windows.Automation.ControlType]::Window)
    $list = @()
    $tops = @(TopWindows $procId)
    # החלון הראשי השמור לא נמצא בין החלונות של התהליך (בריצות 7–8: ה-handle התחלף, ו"Open File" שמתחת לחלון הראשי לא נמצא) —
    # החלון הראשי הוא זה שהכותרת שלו "... - Microsoft Visual Studio"; מעדכנים את השמור
    if (-not @($tops | Where-Object { try { [int]$_.Current.NativeWindowHandle -eq $mainHandle } catch { $false } }).Count) {
        $m = @($tops | Where-Object { (ElName $_) -match 'Microsoft Visual Studio( \(Administrator\))?$' }) | Select-Object -First 1
        if ($m) { $mainHandle = [int]$m.Current.NativeWindowHandle; if ($procId -eq $script:vsP.Id) { $script:vsMain = [IntPtr]$mainHandle } }
    }
    foreach ($w in $tops) {
        try {
            if ([int]$w.Current.NativeWindowHandle -ne $mainHandle) { $list += $w; continue }
            foreach ($c in @($w.FindAll([Windows.Automation.TreeScope]::Children, $isWindow))) {
                $list += $c
                try { $list += @($c.FindAll([Windows.Automation.TreeScope]::Children, $isWindow)) } catch { }
            }
        } catch { }
    }
    return @($list)
}
# חלון כניסה לחשבון Microsoft ("Microsoft Azure — Sign in") ש-Visual Studio פותח לבד ברקע (חשבון / Copilot), באמצע הבדיקה. הוא מודאלי:
# לחיצות על החלון הראשי לא מגיעות לשום מקום (כך נבלעה הלחיצה על שורת ההודעה ב-T5). סוגרים אותו כמו תלמיד — בלי להיכנס — וסופרים,
# כדי שמי שלחץ יידע ללחוץ שוב. רק חלונות כניסה: הכותרת, או השדה "Email, phone, or Skype" בדף; לא חלון ההפעלה הראשונה (SkipFirstRun)
$script:signInClosed = 0
# כל החלונות העליונים במסך (כל התהליכים): תהליך, מחלקה, שם, מיקום — לקובץ, כשלחיצה לא נקלטה או חלון לא נמצא; כדי לדעת מי פתח את
# חלון הכניסה (בריצות 5–7 הוא לא זוהה לפי התהליכים שבדקנו)
function DumpTopWindows([string]$tag) {
    try {
        $names = @{}; Get-Process | ForEach-Object { $names[[int]$_.Id] = $_.ProcessName }
        $rows = @([Windows.Automation.AutomationElement]::RootElement.FindAll([Windows.Automation.TreeScope]::Children, [Windows.Automation.Condition]::TrueCondition) | ForEach-Object {
            $c = $_.Current
            $t = ''
            if ([int]$c.NativeWindowHandle -ne [int]$script:vsMain) {
                try {
                    $all = @($_.FindAll([Windows.Automation.TreeScope]::Subtree, [Windows.Automation.Condition]::TrueCondition))
                    if ($all.Count -le 400) { $t = ((@($all | ForEach-Object { ElName $_ } | Where-Object { $_ }) | Select-Object -Unique) -join ' | ') } else { $t = "($($all.Count) רכיבים)" }
                } catch { }
            }
            if ($t.Length -gt 300) { $t = $t.Substring(0, 300) }
            "{0}[{1}]`t{2}`t'{3}'`t{4}`t{5}" -f $names[[int]$c.ProcessId], $c.ProcessId, $c.ClassName, $c.Name, $c.BoundingRectangle, $t
        })
        $f = Join-Path $logs "top-windows-$tag.txt"
        $rows | Set-Content $f -Encoding UTF8
        Info "החלונות העליונים במסך נשמרו: $(Split-Path $f -Leaf) ($($rows.Count))"
        @($rows | Where-Object { $_ -notmatch '^(devenv|explorer|hosted-compute-agent|TextInputHost)\[' }) | Select-Object -First 8 | ForEach-Object { Info "   חלון: $_" }
    } catch { Info "DumpTopWindows: $_" }
}
function CloseSignIn() {
    $closed = 0
    $owners = @{}
    if ($script:vsP) { $owners[[int]$script:vsP.Id] = 1 }
    Get-Process -Name 'msedgewebview2', 'Microsoft.AAD.BrokerPlugin', 'ServiceHub.IdentityHost', 'ApplicationFrameHost', 'backgroundTaskHost', 'Microsoft.ServiceHub.Controller', 'ServiceHub.Host.dotnet.x64', 'ServiceHub.Host.netfx.x86', 'ServiceHub.Host.netfx.x64', 'msedge' -ErrorAction SilentlyContinue | ForEach-Object { $owners[[int]$_.Id] = 1 }
    $main = 0; try { $main = [int]$script:vsMain } catch { }
    $cands = @()
    try { $cands = @([Windows.Automation.AutomationElement]::RootElement.FindAll([Windows.Automation.TreeScope]::Children, [Windows.Automation.Condition]::TrueCondition)) } catch { }
    if ($script:vsP) { $cands += @(OtherWindows $script:vsP.Id $main) }
    foreach ($w in $cands) {
        try {
            $c = $w.Current
            if ($c.BoundingRectangle.IsEmpty -or [int]$c.NativeWindowHandle -eq $main -or -not $owners.ContainsKey([int]$c.ProcessId)) { continue }
            $hit = $c.Name -match '^(Sign in to your account|Microsoft Azure|Microsoft account|Sign in|Sign in to Visual Studio)$'
            if (-not $hit) {
                $all = @($w.FindAll([Windows.Automation.TreeScope]::Subtree, [Windows.Automation.Condition]::TrueCondition))
                if ($all.Count -gt 400) { continue }
                $hit = @($all | Where-Object { (ElName $_) -match '^Email, phone, or Skype$|^Sign in to your account$|^Can.t access your account\?$|^Sign-in options$' }).Count -gt 0
            }
            if (-not $hit) { continue }
            Step 'dialog-sign-in'
            $done = $false
            try { $w.GetCurrentPattern([Windows.Automation.WindowPattern]::Pattern).Close(); $done = $true } catch { }
            if (-not $done) { try { [void][DsReal]::Front([IntPtr][int]$c.NativeWindowHandle); [DsReal]::Chord([uint16]0x1B); $done = $true } catch { } }   # Esc
            Start-Sleep -Milliseconds 800
            Info "נסגר חלון כניסה לחשבון Microsoft ש-Visual Studio פתח לבד ('$($c.Name)', תהליך $($c.ProcessId)) — הוא חוסם לחיצות בחלון הראשי"
            $closed++
        } catch { }
    }
    $script:signInClosed += $closed
    return ($closed -gt 0)
}
# רכיבים של התהליך לפי שם מדויק (ואפשר סוג: 'Button', 'MenuItem'...). רק רכיבים שנראים על המסך, אלא אם -Any
function ByName([int]$procId, [string]$name, [string]$type = '', [switch]$Any) {
    $c = New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::NameProperty, $name)
    $found = @()
    foreach ($w in (TopWindows $procId)) {
        try {
            if ((ElName $w) -eq $name) { $found += $w }
            $found += @($w.FindAll([Windows.Automation.TreeScope]::Descendants, $c))
        } catch { }
    }
    if ($type) { $found = @($found | Where-Object { (ElType $_) -match $type }) }
    if (-not $Any) { $found = @($found | Where-Object { Shown $_ }) }
    return @($found)
}
# כל הרכיבים של התהליך ששמם מתאים לביטוי (איטי — סורק את כל העץ; לחיפוש טקסט של הודעה)
function ByPattern([int]$procId, [string]$pattern, [string]$type = '') {
    $found = @()
    foreach ($w in (TopWindows $procId)) {
        try {
            foreach ($e in @($w.FindAll([Windows.Automation.TreeScope]::Subtree, [Windows.Automation.Condition]::TrueCondition))) {
                $n = ElName $e
                if ($n -and $n -match $pattern -and (-not $type -or (ElType $e) -match $type)) { $found += $e }
            }
        } catch { }
    }
    return @($found)
}
# כל השמות והסוגים של הרכיבים של התהליך — לקובץ, כשמשהו לא נמצא (כדי לראות בסבב אחד איך הרכיב נקרא בגרסה הזו)
function DumpUia([int]$procId, [string]$name) {
    try {
        $f = Join-Path $Out ("uia-{0:D3}-{1}.txt" -f $script:step, ($name -replace '[^A-Za-z0-9\-]+', '_'))
        $sb = New-Object System.Collections.Generic.List[string]
        foreach ($w in (TopWindows $procId)) {
            foreach ($e in @($w.FindAll([Windows.Automation.TreeScope]::Subtree, [Windows.Automation.Condition]::TrueCondition))) {
                try { $c = $e.Current; if ($c.Name -or $c.AutomationId) { $sb.Add(("{0}`t{1}`t{2}`toff={3}`t{4}" -f (ElType $e), $c.Name, $c.AutomationId, $c.IsOffscreen, $c.BoundingRectangle)) } } catch { }
            }
        }
        $sb | Set-Content $f -Encoding utf8
        Info "רשימת הרכיבים (UI Automation) נשמרה: $(Split-Path -Leaf $f) ($($sb.Count) שורות)"
    } catch { Info "DumpUia: $_" }
}
# לחיצת עכבר אמיתית על רכיב: הנקודה באמצע המלבן שלו על המסך. מדווח מה באמת נמצא בנקודה (חלון אחר שמכסה?), ומצלם אחרי הלחיצה
function RealClick($el, [string]$name, [double]$fx = 0.5, [double]$fy = 0.5) {
    if (-not (Shown $el)) { Info "לחיצה על '$name': הרכיב לא נראה על המסך — $(DescEl $el)"; Step "$name-not-visible"; return $false }
    $r = $el.Current.BoundingRectangle
    $x = [int]($r.X + $r.Width * $fx); $y = [int]($r.Y + $r.Height * $fy)
    return (RealClickAt $x $y $name $el)
}
function RealClickAt([int]$x, [int]$y, [string]$name, $expect = $null) {
    try {
        $hit = [Windows.Automation.AutomationElement]::FromPoint((New-Object Windows.Point $x, $y))
        if ($expect -and $hit) {
            $same = $false
            for ($h = $hit; $h -ne $null; $h = [Windows.Automation.TreeWalker]::RawViewWalker.GetParent($h)) { if ([Windows.Automation.Automation]::Compare($h, $expect)) { $same = $true; break } }
            if (-not $same) { Info "אזהרה: בנקודה ($x,$y) נמצא $(DescEl $hit) — לא '$name' (חלון אחר מכסה?)" }
        }
    } catch { }
    $at = ''
    try { $at = [DsReal]::Click($x, $y) } catch { Info "SendInput נכשל: $($_.Exception.Message)"; Step "$name-click-failed" $x $y; return $false }
    Start-Sleep -Milliseconds 700
    Info "לחיצת עכבר אמיתית על '$name' ב-($x,$y) · הסמן: $at"
    Step "$name-clicked" $x $y
    return $true
}
# מחכה עד שהתנאי מחזיר ערך (לא ריק / לא false); מחזיר אותו, או $null אחרי הזמן
function WaitFor([scriptblock]$cond, [int]$sec, [int]$everyMs = 500) {
    $t0 = Get-Date
    while (((Get-Date) - $t0).TotalSeconds -lt $sec) {
        $v = $null
        try { $v = & $cond } catch { }
        if ($v) { return $v }
        Start-Sleep -Milliseconds $everyMs
    }
    return $null
}

# ------------------------------------------------------------------ Visual Studio
function VsInstances() {
    $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
    return @(& $vswhere -all -prerelease -products * -format json | Out-String | ConvertFrom-Json | ForEach-Object { $_ })
}
# DTE של ה-Visual Studio שנבדק: ב-Running Object Table לפי מזהה התהליך שפתחנו (‎!VisualStudio.DTE.<גרסה>:<pid>‎ — גם כשבמחשב כמה
# מופעים), ואם אין — לפי הגרסה. להכנה ולאימות בלבד — לא לפעולות שנבדקות
if (-not ('DsRot' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;
public static class DsRot {
    [DllImport("ole32.dll")] static extern int GetRunningObjectTable(int reserved, out IRunningObjectTable rot);
    [DllImport("ole32.dll")] static extern int CreateBindCtx(int reserved, out IBindCtx ctx);
    public static object Dte(int pid) {
        IRunningObjectTable rot;
        if (GetRunningObjectTable(0, out rot) != 0) return null;
        IEnumMoniker en;
        rot.EnumRunning(out en);
        en.Reset();
        IMoniker[] m = new IMoniker[1];
        while (en.Next(1, m, IntPtr.Zero) == 0) {
            IBindCtx c;
            CreateBindCtx(0, out c);
            string n;
            m[0].GetDisplayName(c, null, out n);
            if (n != null && n.StartsWith("!VisualStudio.DTE") && n.EndsWith(":" + pid)) {
                object o;
                if (rot.GetObject(m[0], out o) == 0 && o != null) return o;
            }
        }
        return null;
    }
}
'@
}
function GetDte() {
    if ($script:vsP) { try { $o = [DsRot]::Dte($script:vsP.Id); if ($o) { return $o } } catch { } }
    foreach ($progId in @("VisualStudio.DTE.$($script:vsMajor).0", 'VisualStudio.DTE')) { try { return [Runtime.InteropServices.Marshal]::GetActiveObject($progId) } catch { } }
    return $null
}
# קריאה ל-DTE עם ניסיונות חוזרים: Visual Studio עסוק מחזיר RPC_E_CALL_REJECTED
function Dte([scriptblock]$body, [int]$tries = 20) {
    $last = $null
    for ($i = 0; $i -lt $tries; $i++) {
        $d = GetDte
        if ($d) {
            try { return (& $body $d) } catch { $last = $_ }
        }
        Start-Sleep -Milliseconds 800
    }
    if ($last) { Info "DTE: $($last.Exception.Message)" } else { Info 'DTE: אין חיבור ל-Visual Studio' }
    return $null
}
# מאפיין של אובייקט COM של Visual Studio דרך IDispatch ישירות. בפרויקטים מסוג SDK ‏(CPS) PowerShell לא מוצא את המאפיינים
# (אין לו מידע על הטיפוס) ומחזיר ריק בלי שגיאה — כך ‎$p.Name‎ היה ריק לכל פרויקט C#. "עסוק" (RPC_E_CALL_REJECTED) נזרק הלאה כדי ש-Dte ינסה שוב
function ComGet($o, [string]$prop) {
    if ($null -eq $o) { return $null }
    try { return [System.__ComObject].InvokeMember($prop, [Reflection.BindingFlags]::GetProperty, $null, $o, $null) }
    catch {
        $e = $_.Exception; while ($e.InnerException) { $e = $e.InnerException }
        if ($e -is [Runtime.InteropServices.COMException] -and ($e.HResult -eq -2147418111 -or $e.HResult -eq -2147417846)) { throw }
        try { return $o.$prop } catch { return $null }
    }
}
# מסכי ההפעלה הראשונה של Visual Studio (כניסה לחשבון, בחירת ערכת צבעים, חלון הפתיחה): גם אותם סוגרים בלחיצת עכבר אמיתית
function SkipFirstRun([int]$procId) {
    $pats = 'Not now, maybe later|Skip and add accounts later|Skip this for now|^Start Visual Studio$|Continue without code|Are you sure you want to exit'
    foreach ($w in (TopWindows $procId)) {
        $all = @()
        try { $all = @($w.FindAll([Windows.Automation.TreeScope]::Subtree, [Windows.Automation.Condition]::TrueCondition)) } catch { }
        foreach ($el in $all) {
            $n = ElName $el
            if (-not $n -or $n -notmatch $pats -or (ElType $el) -notmatch 'Button|Hyperlink|Text|Window') { continue }
            if ($n -match 'Are you sure you want to exit') {
                $no = @($all | Where-Object { (ElName $_) -eq 'No' -and (ElType $_) -match 'Button' })
                if ($no.Count) { RealClick $no[0] 'first-run-exit-no' | Out-Null; return $true }
                continue
            }
            if (-not (Shown $el)) { continue }
            Info "הפעלה ראשונה: '$n'"
            RealClick $el 'first-run' | Out-Null
            return $true
        }
    }
    return $false
}
# פותח את Visual Studio (עם פתרון, או בלי) ומחכה לחלון הראשי. מחזיר את התהליך, או $null
# פותח את Visual Studio; אם devenv עצמו קרס בזמן העלייה (בריצה 6: 0xC0000374 — heap corruption ב-Visual Studio, מיד אחרי סגירה בכוח),
# מנסים פעם אחת נוספת, כמו תלמיד שפותח שוב — ורושמים את זה
function StartVs([string]$sln, [int]$minutes = 12) {
    $p = StartVsOnce $sln $minutes
    if (-not $p -and $script:lastVsExit -and $script:lastVsExit -ne 0) {
        Info ("devenv קרס בזמן העלייה (קוד 0x{0:X8}) — פותחים שוב" -f ([int64]$script:lastVsExit -band 0xFFFFFFFF))
        Start-Sleep 5
        $p = StartVsOnce $sln $minutes
    }
    return $p
}
function StartVsOnce([string]$sln, [int]$minutes = 12) {
    $script:lastVsExit = $null
    # ‎/log‎: רק יומן הפעילות (ActivityLog.xml) לאבחון — לא משנה את ההתנהגות. בלי ‎/updateconfiguration‎ ובלי שום מתג אחר
    $a = @()
    if ($sln) { $a += "`"$sln`"" }
    $a += '/log'
    $t0 = Get-Date
    $p = Start-Process $script:devenv -ArgumentList $a -PassThru
    $up = $false
    while (((Get-Date) - $t0).TotalMinutes -lt $minutes) {
        Start-Sleep 4
        $p.Refresh()
        if ($p.HasExited) { Info "devenv נסגר (קוד $($p.ExitCode))"; $script:lastVsExit = $p.ExitCode; break }
        if (SkipFirstRun $p.Id) { Start-Sleep 3 }
        $p.Refresh()
        $title = [string]$p.MainWindowTitle
        if ($sln) { if ($title -like '*Microsoft Visual Studio*' -and $title -notlike 'Microsoft Visual Studio') { $up = $true; break } }
        elseif ($title -like '*Microsoft Visual Studio*' -and @(ByName $p.Id 'File' 'MenuItem').Count -gt 0) { $up = $true; break }
    }
    Info ("Visual Studio: {0:N0} שניות, עלה={1}, כותרת '{2}'" -f ((Get-Date) - $t0).TotalSeconds, $up, $p.MainWindowTitle)
    if (-not $up) { Step 'vs-not-up'; return $null }
    Start-Sleep 2
    $p.Refresh()   # החלון הראשי כמו שהוא עכשיו (אחרי שחלון הפתיחה נסגר)
    [DsReal]::ShowWindow($p.MainWindowHandle, 3) | Out-Null   # SW_MAXIMIZE
    [DsReal]::Front($p.MainWindowHandle) | Out-Null
    $script:vsP = $p
    $script:vsMain = $p.MainWindowHandle   # החלון הראשי, כמו שהוא עכשיו (כשתיבת הודעה פתוחה Process.MainWindowHandle יכול להשתנות)
    return $p
}
# מביא את Visual Studio לקדמה (לפני לחיצה / הקשה). true — החלון הראשי, או חלון אחר של Visual Studio (תיבת הודעה), בקדמה.
# אם Windows לא נותן להביא חלון לקדמה מבחוץ: לחיצת עכבר אמיתית על שורת המצב שבתחתית החלון (לא משנה כלום — רק מפעילה את החלון, כמו משתמש)
# חלון של Windows שקופץ במכונת הבדיקה ולוקח את המיקוד ("System Properties — Windows created a temporary paging file"): OK
function CloseSystemNoise() {
    foreach ($pp in @(Get-Process -Name 'SystemPropertiesPerformance', 'SystemPropertiesAdvanced' -ErrorAction SilentlyContinue)) {
        foreach ($w in (TopWindows $pp.Id)) {
            try { $w.GetCurrentPattern([Windows.Automation.WindowPattern]::Pattern).Close(); Info "נסגר חלון של Windows: '$(ElName $w)' ($($pp.ProcessName))" } catch { }
        }
    }
}
function VsFront() {
    CloseSystemNoise
    CloseSignIn | Out-Null
    if ($script:vsP) { OtherWindows $script:vsP.Id ([int]$script:vsMain) | Out-Null }   # מעדכן את החלון הראשי אם ה-handle התחלף
    $h = $script:vsMain
    $ok = [DsReal]::Front($h)
    if (-not $ok) {
        try {
            [uint32]$fgPid = 0
            [void][DsReal]::GetWindowThreadProcessId([DsReal]::GetForegroundWindow(), [ref]$fgPid)
            if ($fgPid -eq $script:vsP.Id) { $ok = $true }   # חלון אחר של Visual Studio בקדמה (הודעה, תפריט)
            else {
                $r = [Windows.Automation.AutomationElement]::FromHandle($h).Current.BoundingRectangle
                if (-not $r.IsEmpty) {
                    $at = [DsReal]::Click([int]($r.X + $r.Width * 0.3), [int]($r.Bottom - 12))
                    Start-Sleep -Milliseconds 400
                    $ok = ([DsReal]::GetForegroundWindow() -eq $h)
                    Info "Visual Studio לא היה בקדמה — לחיצת עכבר על שורת המצב ($at): בקדמה=$ok"
                }
            }
        } catch { }
        if (-not $ok) { Info 'אזהרה: Visual Studio לא בקדמה' }
    }
    Start-Sleep -Milliseconds 300
    return $ok
}
# סגירה רגילה (כמו משתמש): לחיצה אמיתית על ה-X של החלון. מחכה שהתהליך ייגמר; אם לא — סוגר בכוח ומחזיר $false
function CloseVsByX() {
    $p = $script:vsP
    if (-not $p) { return $true }
    $p.Refresh()
    if ($p.HasExited) { return $true }
    VsFront | Out-Null
    $x = @(ByName $p.Id 'Close' 'Button' | Sort-Object { $_.Current.BoundingRectangle.Y } | Select-Object -First 1)
    $clicked = $false
    if ($x.Count -gt 0 -and $x[0].Current.BoundingRectangle.Y -lt 60) { $clicked = RealClick $x[0] 'vs-close-x' }
    if (-not $clicked) { Info "כפתור הסגירה לא נמצא — Alt+F4 (מקלדת אמיתית)"; [DsReal]::Chord([uint16[]]@(0x12, 0x73)); Step 'vs-alt-f4' }
    for ($i = 0; $i -lt 45 -and -not $p.HasExited; $i++) {
        Start-Sleep 2
        $p.Refresh()
        if ($p.HasExited) { break }
        # "Save changes?" — לא שומרים (Don't Save / No)
        $dont = @()
        foreach ($w in (OtherWindows $p.Id ([int]$script:vsMain))) {
            try {
                $dont += @($w.FindAll([Windows.Automation.TreeScope]::Descendants, [Windows.Automation.Condition]::TrueCondition) |
                    Where-Object { (ElType $_) -match 'Button' -and (ElName $_) -match '^(Don.t Save|No)$' -and (Shown $_) })
            } catch { }
        }
        if ($dont.Count -gt 0) { RealClick $dont[0] 'vs-close-dont-save' | Out-Null }
    }
    $p.Refresh()
    $ok = $p.HasExited
    if (-not $ok) { Info 'Visual Studio לא נסגר — סוגרים בכוח'; Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
    Start-Sleep 4
    Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    return $ok
}
function KillVs() {
    Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep 4
}

# ------------------------------------------------------------------ הכפתורים של DSViz בסרגל
$script:dsvizNames = @('תרגיל חדש', 'הוספת מבנה נתונים', 'מאגר שאלות', 'השאלה', 'שליחת תרגיל', 'הרצה ויזואלית', 'הדרכה')
function ToolbarButton([string]$name) {
    $els = @(ByName $script:vsP.Id $name 'Button')
    if ($els.Count -eq 0) { return $null }
    return $els[0]
}
function WaitToolbar([int]$sec = 180) {
    $t0 = Get-Date
    while (((Get-Date) - $t0).TotalSeconds -lt $sec) {
        SkipFirstRun $script:vsP.Id | Out-Null
        $n = @($script:dsvizNames | Where-Object { $b = ToolbarButton $_; $b -and $b.Current.IsEnabled }).Count
        if ($n -eq $script:dsvizNames.Count) { return $true }
        Start-Sleep 4
    }
    return $false
}
