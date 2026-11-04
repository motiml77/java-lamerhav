# "מאגר תרגילים" ל-Visual Studio (גרסת בדיקה) על Windows אמיתי עם Visual Studio — כמו תלמיד, עם צילומי מסך:
#   1. מתקינים את DSViz-Setup.exe (שקט, חבילת C#), ואת התוספת (install-bank-vs.ps1 — כמו לחיצה כפולה על install.cmd): הקיצורים בשולחן העבודה
#   2. פותחים את Visual Studio עם הפתרון DataStructures-CS
#   3. BankVsIt: חלון המאגר, נושא ושאלה, סינון, חיפוש, "צור תרגיל" → החלון הצף; Visual Studio טוען מחדש (Reload) ורואים את הפרויקט החדש
#   4. "לפעולה של סעיף א" → הקובץ נפתח ב-Visual Studio
#   5. התלמיד כותב את הפתרון (ההצעות השמורות), MSBuild של Visual Studio בונה, והתוכנית מדפיסה בדיוק את הפלט הרצוי; "הרצה ויזואלית" נפתחת
#   6. "סיימתי את התרגיל" → הפרויקט נמחק מהדיסק ומהפתרון, והפתרון עדיין נבנה
param([Parameter(Mandatory = $true)][string]$Setup, [Parameter(Mandatory = $true)][string]$Lab, [Parameter(Mandatory = $true)][string]$Out,
    [Parameter(Mandatory = $true)][string]$Bank, [Parameter(Mandatory = $true)][string]$TestCp)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$OutputEncoding = [Text.Encoding]::UTF8
New-Item -ItemType Directory -Force $Out | Out-Null
$Out = (Resolve-Path $Out).Path
$script:fails = 0
$script:lines = New-Object System.Collections.Generic.List[string]
function Log([string]$s) { Write-Host $s; $script:lines.Add($s) }
function Ok([string]$id, [string]$s) { Log "  ok   $id  $s" }
function Fail([string]$id, [string]$s) { $script:fails++; Log "  FAIL $id  $s" }
function Info([string]$s) { Log "  ..   $s" }
function Check([string]$id, [bool]$cond, [string]$s, [string]$why = '') { if ($cond) { Ok $id $s } else { Fail $id ($s + $(if ($why) { " — $why" } else { '' })) } }
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
function ElName($el) { try { return $el.Current.Name } catch { return '' } }
function ElType($el) { try { return $el.Current.ControlType.ProgrammaticName } catch { return '' } }
# הפעלה ראשונה של Visual Studio: מדלגים על ההתחברות / ערכת הנושא, כמו תלמיד
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
# הפתרון השתנה מחוץ ל-Visual Studio (פרויקט נוסף / נמחק): Visual Studio שואל — לוחצים "Reload" (כמו תלמיד)
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
function HasElement([int]$vsPid, [string]$pattern) {
    foreach ($el in (VsElements $vsPid)) { if ((ElName $el) -match $pattern) { return $true } }
    return $false
}

$docs = [Environment]::GetFolderPath('MyDocuments')
$desk = [Environment]::GetFolderPath('Desktop')
$dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'
$runner = Join-Path $dsviz 'cs\dsviz-cs.exe'
$csDir = Join-Path $docs 'DataStructures-CS'
$sln = Join-Path $csDir 'DataStructures-CS.sln'
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$vs = & $vswhere -latest -products * -requires Microsoft.Component.MSBuild -property installationPath
$vsName = & $vswhere -latest -products * -property displayName
$msbuild = Join-Path $vs 'MSBuild\Current\Bin\amd64\MSBuild.exe'
$devenv = Join-Path $vs 'Common7\IDE\devenv.exe'
Info "Visual Studio: $vsName — $vs · מסך: $([Windows.Forms.SystemInformation]::VirtualScreen.Size)"

# ------------------------------------------------------------------ 1. התקנה
Log "== 1. ההתקנה: DSViz-Setup.exe (חבילת C#) והתוספת 'מאגר תרגילים'"
$p = Start-Process $Setup -ArgumentList '/S', '/LANG=cs' -PassThru -Wait
Check 'install' ($p.ExitCode -eq 0 -and (Test-Path $sln) -and (Test-Path $runner)) "DSViz הותקן: הפתרון DataStructures-CS ו-dsviz-cs" "קוד $($p.ExitCode)"
$o = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Lab 'install-bank-vs.ps1') 2>&1
Info "install-bank-vs.ps1: $($o -join ' ')"
$ws = New-Object -ComObject WScript.Shell
Info "בשולחן העבודה: $((Get-ChildItem $desk -Filter '*.lnk' | ForEach-Object Name) -join ', ')"
foreach ($n in @('מאגר תרגילים C#', 'השאלה — מאגר תרגילים C#')) {
    $l = Join-Path $desk "$n.lnk"
    $ok = Test-Path -LiteralPath $l
    $tgt = ''; $arg = ''
    if ($ok) {
        # WScript לא קורא קובץ עם שם בעברית — קוראים עותק בשם באנגלית
        $tmp = Join-Path $env:TEMP 'dsviz-check.lnk'
        Copy-Item -LiteralPath $l $tmp -Force
        $s = $ws.CreateShortcut($tmp)
        $tgt = [string]$s.TargetPath; $arg = [string]$s.Arguments
        Remove-Item $tmp -Force
    }
    $good = $ok -and $tgt -and (Test-Path -LiteralPath $tgt) -and $arg -match 'dsviz-bank-vs.jar' -and $arg -match 'DataStructures-CS'
    Check 'shortcut' $good "בשולחן העבודה: הקיצור '$n'" "קיים: $ok · $tgt $arg"
}
# לחיצה כפולה על הקיצור "מאגר תרגילים C#" — כמו תלמיד: החלון נפתח
$bankLnk = Join-Path $desk 'מאגר תרגילים C#.lnk'
if (Test-Path -LiteralPath $bankLnk) {
    Start-Process -FilePath $bankLnk
    $seen = $false
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep 1
        if (@(Get-Process javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'מאגר תרגילים' }).Count -gt 0) { $seen = $true; break }
    }
    Start-Sleep 4
    Shot '00-shortcut-bank-window'
    Check 'shortcut-open' $seen "לחיצה כפולה על הקיצור 'מאגר תרגילים C#' פותחת את חלון המאגר" ((Windows) -join ' ; ')
    Get-Process javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'מאגר תרגילים' } | Stop-Process -Force
}
$jar = Join-Path $dsviz 'lab\dsviz-bank-vs.jar'
Check 'lab-jar' (Test-Path $jar) "התוספת הועתקה: $jar"

# ------------------------------------------------------------------ 2. Visual Studio עם הפתרון
Log "== 2. Visual Studio נפתח עם הפתרון DataStructures-CS"
$t0 = Get-Date
$vsP = Start-Process $devenv -ArgumentList "`"$sln`"" -PassThru
$ready = $false
while (((Get-Date) - $t0).TotalMinutes -lt 9) {
    Start-Sleep 5
    $vsP.Refresh()
    if ($vsP.HasExited) { break }
    if (SkipFirstRun $vsP.Id) { Start-Sleep 3 }
    $vsP.Refresh()
    if ($vsP.MainWindowTitle -like '*- Microsoft Visual Studio*') { $ready = $true; break }
}
Check 'vs-open' $ready "Visual Studio נפתח ($([int]((Get-Date) - $t0).TotalSeconds) שניות): $($vsP.MainWindowTitle)" "חלונות: $((Windows) -join ' ; ')"
if ($ready) {
    [Screen2]::ShowWindow($vsP.MainWindowHandle, 3) | Out-Null
    [Screen2]::SetForegroundWindow($vsP.MainWindowHandle) | Out-Null
    Start-Sleep 40
    SkipFirstRun $vsP.Id | Out-Null
    Shot '01-vs-open'
}

# ------------------------------------------------------------------ 3. המאגר: בחירה ויצירה (BankVsIt — כמו תלמיד)
Log "== 3. מאגר תרגילים: חלון, נושא, שאלה, 'צור תרגיל'"
$it = Join-Path $Out 'it'
$go = Join-Path $Out 'go'
$java = Join-Path $dsviz 'jdk\bin\java.exe'
$cp = "$(Join-Path $dsviz 'dsviz.jar');$jar;$TestCp"
$itP = Start-Process $java -ArgumentList "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp `"$cp`" il.lamerhav.dsviz.BankVsIt `"$csDir`" `"$it`" --go `"$go`"" `
    -PassThru -NoNewWindow -RedirectStandardOutput (Join-Path $Out 'it.out.txt') -RedirectStandardError (Join-Path $Out 'it.err.txt')
$null = $itP.Handle   # בלי זה ExitCode ריק בסוף
Start-Sleep 12
Shot '02-bank-window-over-vs'
$created = Join-Path $it 'created.txt'
for ($i = 0; $i -lt 300 -and -not (Test-Path $created) -and -not $itP.HasExited; $i++) { Start-Sleep 1 }
Check 'created' (Test-Path $created) "נוצר תרגיל (BankVsIt)" (Get-Content (Join-Path $Out 'it.err.txt') -Raw -Encoding utf8)
$dir = if (Test-Path $created) { (Get-Content $created -Raw -Encoding utf8).Trim() } else { '' }
$name = if ($dir) { Split-Path $dir -Leaf } else { '' }
Shot '03-created-float'
if ($ready -and $dir) {
    $script:shotPrefix = '04'
    $rl = ClickReload $vsP.Id 60
    Info "Reload: $rl"
    Start-Sleep 25
    Shot '05-vs-reloaded-with-float'
    # Solution Explorer (Ctrl+Alt+L) — רואים בו את הפרויקט החדש
    $wsh = New-Object -ComObject WScript.Shell
    [void]$wsh.AppActivate($vsP.Id); Start-Sleep 1; $wsh.SendKeys('^%l'); Start-Sleep 4
    Shot '05b-solution-explorer'
    Check 'vs-project' (HasElement $vsP.Id ([regex]::Escape($name))) "ב-Visual Studio (Solution Explorer) מופיע הפרויקט החדש $name"
}
New-Item -ItemType File -Force "$go.1" | Out-Null

# ------------------------------------------------------------------ 4. "לפעולה של סעיף א"
Log "== 4. 'לפעולה של סעיף א' פותח את הקובץ ב-Visual Studio"
$jumped = Join-Path $it 'jumped.txt'
for ($i = 0; $i -lt 120 -and -not (Test-Path $jumped) -and -not $itP.HasExited; $i++) { Start-Sleep 1 }
Info "נפתח: $(if (Test-Path $jumped) { Get-Content $jumped -Raw -Encoding utf8 } else { '-' })"
Start-Sleep 8
Shot '06-jump-file-in-vs'
if ($ready -and $dir) {
    $cs = Get-ChildItem $dir -Filter '*.cs' | Select-Object -First 1
    Check 'vs-file' (HasElement $vsP.Id ([regex]::Escape($cs.Name))) "הקובץ $($cs.Name) פתוח ב-Visual Studio (לשונית)" ((Windows) -join ' ; ')
}

# ------------------------------------------------------------------ 5. התלמיד פותר: בנייה, פלט, הרצה ויזואלית
Log "== 5. כותבים את הפתרון, בונים ב-MSBuild של Visual Studio, ומריצים"
if ($dir) {
    $sol = (Get-Content (Join-Path $env:USERPROFILE '.dsviz\solutions.json') -Raw -Encoding utf8 | ConvertFrom-Json).solutions
    $applied = 0
    foreach ($f in Get-ChildItem $dir -Filter '*.cs') {
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
            $nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }   # אותם סופי שורות כמו בקובץ (אחרת Visual Studio שואל על Line Endings)
            $code = ($e.code -split "`n" | ForEach-Object { $indent + $_.TrimEnd("`r") }) -join $nl
            $text = $text.Substring(0, $start) + $code + $text.Substring($end + 1)
            $applied++
        }
        [IO.File]::WriteAllText($f.FullName, $text, (New-Object Text.UTF8Encoding($true)))
    }
    Check 'solve' ($applied -eq 2) "הפתרון נכתב בשני הסעיפים ($applied)"
    $proj = Join-Path $dir "$name.csproj"
    $b = & $msbuild $proj /restore /nologo /v:m /p:Configuration=Debug 2>&1
    $b | Set-Content (Join-Path $Out 'build-exercise.txt') -Encoding utf8
    Check 'build' ($LASTEXITCODE -eq 0) "MSBuild של Visual Studio בנה את $name" (($b | Select-Object -Last 8) -join ' | ')
    $exe = Get-ChildItem (Join-Path $dir 'bin\Debug') -Recurse -Filter "$name.exe" | Select-Object -First 1
    $expected = ((Get-Content (Join-Path $Bank 'stack\stack-sum-size.json') -Raw -Encoding utf8 | ConvertFrom-Json).csharp.exercise.expectedOutput).Replace("`r", '').Trim()
    if ($exe) {
        $prev = [Console]::OutputEncoding
        $actual = ((& $exe.FullName) -join "`n").Replace("`r", '').Trim()
        Info "הפלט:`n$actual"
        Check 'output' ($actual -eq $expected) "התוכנית (הרצה רגילה) מדפיסה בדיוק את הפלט הרצוי מהשאלה" "צפוי: $expected"
    } else { Fail 'output' "אין $name.exe אחרי הבנייה" }
    # הרצה ויזואלית: הפרופיל של הפרויקט, כמו ש-Visual Studio מריץ אותו
    $lf = Join-Path $dir 'Properties\launchSettings.json'
    $pr = (Get-Content $lf -Raw -Encoding utf8 | ConvertFrom-Json).profiles.'הרצה ויזואלית'
    Check 'profile' ($null -ne $pr) "לפרויקט יש פרופיל 'הרצה ויזואלית'"
    if ($pr) {
        $pd = $dir.TrimEnd('\') + '\'
        $vexe = [Environment]::ExpandEnvironmentVariables($pr.executablePath)
        $vargs = $pr.commandLineArgs.Replace('$(ProjectDir)', $pd)
        $vp = Start-Process $vexe -ArgumentList $vargs -WorkingDirectory $pr.workingDirectory.Replace('$(ProjectDir)', $pd) -PassThru
        $seen = $false
        for ($i = 0; $i -lt 60; $i++) {
            Start-Sleep 1
            if (@(Get-Process javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'הרצה ויזואלית' }).Count -gt 0) { $seen = $true; break }
        }
        Start-Sleep 6
        Shot '07-visual-run'
        Check 'visual' $seen "'הרצה ויזואלית' של התרגיל נפתחה" ((Windows) -join ' ; ')
        Get-Process javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'הרצה ויזואלית' } | Stop-Process -Force
        Stop-Process -Id $vp.Id -Force -ErrorAction SilentlyContinue
    }
}
Shot '08-before-done'
New-Item -ItemType File -Force "$go.2" | Out-Null
# "הרצה ויזואלית" מהכפתור בחלון הצף
$ran = Join-Path $it 'ran.txt'
for ($i = 0; $i -lt 120 -and -not (Test-Path $ran) -and -not $itP.HasExited; $i++) { Start-Sleep 1 }
$seen = $false
for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep 1
    if (@(Get-Process javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'הרצה ויזואלית' }).Count -gt 0) { $seen = $true; break }
}
Start-Sleep 6
Shot '08b-float-run-button'
Check 'float-run-window' $seen "הכפתור 'הרצה ויזואלית' בחלון הצף פתח את ההרצה הוויזואלית של התרגיל" ((Windows) -join ' ; ')
Get-Process javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'הרצה ויזואלית' } | Stop-Process -Force
Get-Process dsviz-cs -ErrorAction SilentlyContinue | Stop-Process -Force
New-Item -ItemType File -Force "$go.3" | Out-Null

# ------------------------------------------------------------------ 6. "סיימתי את התרגיל"
Log "== 6. 'סיימתי את התרגיל' — הפרויקט נמחק"
$doneF = Join-Path $it 'done.txt'
for ($i = 0; $i -lt 180 -and -not (Test-Path $doneF) -and -not $itP.HasExited; $i++) { Start-Sleep 1 }
Start-Sleep 2
if ($ready) {
    $script:shotPrefix = '09'
    $rl = ClickReload $vsP.Id 45
    Info "Reload אחרי המחיקה: $rl"
    Start-Sleep 20
    Shot '10-vs-after-done'
}
New-Item -ItemType File -Force "$go.4" | Out-Null
$itP.WaitForExit(240000) | Out-Null
# המחיקה עצמה נבדקת ב-BankVsIt (done-dir / done-sln) לפני התרגיל השני — שמקבל שוב את אותו שם (Ex01_...) ונשאר בכוונה עם קובץ של התלמיד
$keepF = Join-Path $it 'keep-project.txt'
$keep = if (Test-Path $keepF) { (Get-Content $keepF -Raw -Encoding utf8).Trim() } else { '' }
if ($keep) {
    Check 'keep-mine' (Test-Path (Join-Path $keep 'MyHelper.cs')) "התרגיל השני: הקובץ של התלמיד (MyHelper.cs) נשאר בפרויקט $(Split-Path $keep -Leaf)"
    Check 'keep-ex-files' (@(Get-ChildItem $keep -Filter 'StackTargil*.cs').Count -eq 0) "התרגיל השני: קבצי התרגיל נמחקו"
}
if ($dir) {
    $b = & $msbuild $sln /restore /nologo /v:m /p:Configuration=Debug 2>&1
    $b | Set-Content (Join-Path $Out 'build-solution-after.txt') -Encoding utf8
    Check 'sln-builds' ($LASTEXITCODE -eq 0) "אחרי 'סיימתי' הפתרון כולו (עם הפרויקט שנשאר) עדיין נבנה ב-MSBuild" (($b | Select-Object -Last 8) -join ' | ')
}
$res = Join-Path $it 'bank-vs-result.txt'
if (Test-Path $res) {
    Log "== הבדיקה של החלונות (BankVsIt)"
    foreach ($l in (Get-Content $res -Encoding utf8)) { if ($l -like '  FAIL*') { $script:fails++ }; if ($l -notlike 'SUMMARY*') { $script:lines.Add($l); Write-Host $l } }
} else { Fail 'it-result' "אין תוצאות מ-BankVsIt: $(Get-Content (Join-Path $Out 'it.err.txt') -Raw -Encoding utf8)" }
Check 'it-exit' ($itP.HasExited -and $itP.ExitCode -eq 0) "BankVsIt הסתיים בהצלחה" "קוד $(if ($itP.HasExited) { $itP.ExitCode } else { 'עדיין רץ' })"
Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force
# הפתיחה הבאה של הכלי: מה שנשאר מהתרגיל הראשון (obj ש-Visual Studio יצר מחדש) נמחק
$pd = Join-Path $it 'pending-dir.txt'
$pdir = if (Test-Path $pd) { (Get-Content $pd -Raw -Encoding utf8).Trim() } else { '' }
if ($pdir) {
    Start-Sleep 5
    $cp2 = Start-Process $java -ArgumentList "-Dfile.encoding=UTF-8 -cp `"$cp`" il.lamerhav.dsviz.BankVsIt `"$csDir`" `"$(Join-Path $Out 'clean')`" --clean-check `"$pdir`"" -PassThru -Wait -NoNewWindow
    $cr = Join-Path $Out 'clean\clean-result.txt'
    Check 'pending-clean' ((Test-Path $cr) -and ((Get-Content $cr -Raw -Encoding utf8) -match 'ok   pending-clean')) "אחרי ש-Visual Studio נסגר, בפתיחה הבאה של הכלי: מה שנשאר מהתרגיל (obj) נמחק" $(if (Test-Path $cr) { Get-Content $cr -Raw -Encoding utf8 } else { 'אין תוצאה' })
    Check 'pending-gone' (-not (Test-Path $pdir)) "התיקייה $(Split-Path $pdir -Leaf) כבר לא קיימת"
} else { Info "התיקייה של התרגיל נמחקה כבר ב'סיימתי' (שום דבר לא נשאר)" }
$ok = @($script:lines | Where-Object { $_ -like '  ok *' }).Count
Log ""
Log "SUMMARY $ok ok / $($script:fails) FAIL — $vsName"
$script:lines | Set-Content (Join-Path $Out 'summary.txt') -Encoding utf8
if ($env:GITHUB_STEP_SUMMARY) { ("``````" + "`n" + ($script:lines -join "`n") + "`n" + "``````") | Add-Content $env:GITHUB_STEP_SUMMARY -Encoding utf8 }
exit $(if ($script:fails -gt 0) { 1 } else { 0 })
