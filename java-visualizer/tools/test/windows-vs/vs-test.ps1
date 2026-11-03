# (UTF-8 עם BOM)
# בדיקה של DSViz ב-Visual Studio אמיתי (מחשב Windows של GitHub Actions — יש בו Visual Studio 2022 / 2026 מותקן).
# מתקינים את DSViz-Setup.exe כמו תלמיד (שקט, חבילת C#), ואז עוברים על מה שתלמיד עושה:
#   1. ההתקנה: הקבצים, DSViz.targets, תיקיית התרגילים, הקיצורים, תבנית הפרויקט
#   2. הפתרון המוכן (DataStructures-CS.sln) נבנה ב-MSBuild של Visual Studio, ובכל פרויקט "הרצה ויזואלית"
#   3. פרויקט חדש מהתבנית הרגילה (Console App, top-level / Main רגיל), בתיקייה עם עברית ורווחים; .NET Framework
#   4. "הרצה ויזואלית" בדיוק כמו ש-Visual Studio מריץ את הפרופיל: התוכנית רצה, החלון נפתח (צילום)
#   5. Visual Studio עצמו: פותחים את הפתרון, לוחצים על כפתור ההפעלה (F5) — נפתח החלון של DSViz (צילומים)
#   6. החלונות של C# (תרגיל חדש / הוסף מבנה / שליחת תרגיל / הצעה לפתרון) — נפתחים (צילומים)
# כל שלב נרשם ב-summary.txt (וב-GITHUB_STEP_SUMMARY). בסוף: קוד יציאה 1 אם משהו נכשל.
#   -Fixtures: java-visualizer\tools\test (קבצי התרגיל לשליחה, השרת המדומה) · -Harness: java-visualizer\tutorials\harness (SwingScript)
#   -Live: גם שליחה לשרת האמיתי (Gemini) · -NoGui: בלי השלב שפותח את Visual Studio עצמו
param([Parameter(Mandatory = $true)][string]$Setup, [string]$Out = 'vs-ci', [Parameter(Mandatory = $true)][string]$Fixtures,
    [Parameter(Mandatory = $true)][string]$Harness, [bool]$Live = $true, [switch]$NoGui)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$OutputEncoding = [Text.Encoding]::UTF8
New-Item -ItemType Directory -Force $Out | Out-Null
$Out = (Resolve-Path $Out).Path
$Setup = (Resolve-Path $Setup).Path
$script:fails = 0
$script:lines = New-Object System.Collections.Generic.List[string]

function Log([string]$s) { Write-Host $s; $script:lines.Add($s) }
function Ok([string]$id, [string]$s) { Log "  ok   $id  $s" }
function Fail([string]$id, [string]$s) { $script:fails++; Log "  FAIL $id  $s" }
function Info([string]$s) { Log "  ..   $s" }
function Check([string]$id, [bool]$cond, [string]$s, [string]$why = '') { if ($cond) { Ok $id $s } else { Fail $id ($s + $(if ($why) { " — $why" } else { '' })) } }

Add-Type -AssemblyName System.Windows.Forms, System.Drawing
function Shot([string]$name) {
    try {
        $b = [Windows.Forms.SystemInformation]::VirtualScreen
        $bmp = New-Object Drawing.Bitmap $b.Width, $b.Height
        $g = [Drawing.Graphics]::FromImage($bmp)
        $g.CopyFromScreen($b.Left, $b.Top, 0, 0, $bmp.Size)
        $bmp.Save((Join-Path $Out "$name.png"), [Drawing.Imaging.ImageFormat]::Png)
        $g.Dispose(); $bmp.Dispose()
        Info "צילום: $name.png"
    } catch { Info "צילום $name נכשל: $_" }
}
function Windows() {
    Get-Process | Where-Object { $_.MainWindowTitle } | ForEach-Object { "$($_.ProcessName)[$($_.Id)]: $($_.MainWindowTitle)" }
}
# חלון של DSViz = תהליך javaw עם חלון (כותרת): "הרצה ויזואלית — מבני נתונים", "שליחת תרגיל — C#" וכו'
function Viewers() {
    @(Get-Process javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle })
}
function KillDsviz() {
    Get-CimInstance Win32_Process -Filter "Name='javaw.exe' or Name='java.exe' or Name='dsviz-cs.exe'" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

# מסך גדול: ברירת המחדל של המחשב ב-GitHub היא 1024x768 — מגדילים (עד 1920x1080), כדי שבצילומי המסך Visual Studio יהיה חלון גדול וברור
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
foreach ($res in @(@(1920, 1080), @(1680, 1050), @(1600, 1200), @(1600, 900), @(1440, 900), @(1280, 1024))) {
    $rc = [Screen2]::Set($res[0], $res[1])
    if ($rc -eq 0) { break }
}
Start-Sleep 3
Info "מסך: $([Windows.Forms.SystemInformation]::VirtualScreen.Size)"

$docs = [Environment]::GetFolderPath('MyDocuments')
$dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'
$runner = Join-Path $dsviz 'cs\dsviz-cs.exe'
$csDir = Join-Path $docs 'DataStructures-CS'
$importAfter = Join-Path $env:LOCALAPPDATA 'Microsoft\MSBuild\Current\Microsoft.CSharp.targets\ImportAfter\DSViz.targets'

# ------------------------------------------------------------------ 0. הסביבה
Log "== 0. הסביבה"
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$vs = & $vswhere -latest -products * -requires Microsoft.Component.MSBuild -property installationPath
$vsName = & $vswhere -latest -products * -property displayName
$vsVer = & $vswhere -latest -products * -property catalog_productDisplayVersion
$msbuild = Join-Path $vs 'MSBuild\Current\Bin\amd64\MSBuild.exe'
$devenv = Join-Path $vs 'Common7\IDE\devenv.exe'
Info "Visual Studio: $vsName $vsVer — $vs"
Info "Windows: $([Environment]::OSVersion.VersionString) · מסך: $([Windows.Forms.SystemInformation]::VirtualScreen.Size)"
Info ".NET SDK: $((& dotnet --list-sdks) -join ' | ')"
Check 'env-vs' ((Test-Path $msbuild) -and (Test-Path $devenv)) "Visual Studio עם MSBuild ו-devenv"

# ------------------------------------------------------------------ 1. ההתקנה
Log "== 1. ההתקנה (DSViz-Setup.exe /S /LANG=cs — כמו תלמיד, בלי חלונות)"
$t = Get-Date
$p = Start-Process $Setup -ArgumentList '/S', '/LANG=cs' -PassThru -Wait
Info ("ההתקנה: קוד {0}, {1:N0} שניות" -f $p.ExitCode, ((Get-Date) - $t).TotalSeconds)
Check 'install-exit' ($p.ExitCode -eq 0) "ההתקנה הסתיימה בהצלחה" "קוד $($p.ExitCode)"
foreach ($f in @($runner, (Join-Path $dsviz 'cs\Unit4.CollectionsLib.dll'), (Join-Path $dsviz 'dsviz.jar'), (Join-Path $dsviz 'jdk\bin\javaw.exe'), $importAfter,
        (Join-Path $csDir 'DataStructures-CS.sln'), (Join-Path $csDir 'lib\Unit4.CollectionsLib.dll'))) {
    Check 'install-file' (Test-Path $f) "קיים: $f"
}
$tpl = @(Get-ChildItem (Join-Path $docs 'Visual Studio *\Templates\ProjectTemplates\DSViz-Exercise.zip') -ErrorAction SilentlyContinue)
Check 'install-template' ($tpl.Count -gt 0) "תבנית הפרויקט של Visual Studio: $(($tpl | ForEach-Object FullName) -join ', ')"
$ws = New-Object -ComObject WScript.Shell
$desk = [Environment]::GetFolderPath('Desktop')
$lnks = @(Get-ChildItem $desk -Filter '*.lnk' -ErrorAction SilentlyContinue)
Info "קיצורים בשולחן העבודה: $(($lnks | ForEach-Object Name) -join ', ')"
foreach ($l in $lnks) {
    $s = $ws.CreateShortcut($l.FullName)
    Check 'install-lnk' (Test-Path $s.TargetPath) "הקיצור '$($l.BaseName)' מצביע על קובץ קיים" $s.TargetPath
}
if (-not (Test-Path $runner)) { Log "אין dsviz-cs — אין טעם להמשיך"; $script:lines | Set-Content (Join-Path $Out 'summary.txt') -Encoding utf8; exit 1 }
& $runner --version | ForEach-Object { Info $_ }

# ------------------------------------------------------------------ עזרים
function Build([string]$proj, [string]$log) {
    $o = & $msbuild $proj /restore /m /nologo /v:m /p:Configuration=Debug 2>&1
    $o | Set-Content (Join-Path $Out "$log.build.txt") -Encoding utf8
    return @{ Code = $LASTEXITCODE; Text = ($o -join "`n") }
}
function Profile([string]$projDir) {
    $f = Join-Path $projDir 'Properties\launchSettings.json'
    if (-not (Test-Path $f)) { return $null }
    $j = Get-Content $f -Raw -Encoding utf8 | ConvertFrom-Json
    return $j.profiles.'הרצה ויזואלית'
}
# מריצים את הפרופיל בדיוק כמו ש-Visual Studio מריץ אותו (%משתני סביבה% ו-$(ProjectDir) שנגמר ב-\ מוחלפים, בלי ארגומנטים נוספים),
# ובודקים שנפתח החלון של DSViz. לפני כן — הרצה ישירה עם קובץ הקלטה: התוכנית רצה עד הסוף
function RunLikeVs([string]$id, [string]$projDir, [string]$name, [int]$wait = 40) {
    $pr = Profile $projDir
    if (-not $pr) { Fail $id "אין פרופיל 'הרצה ויזואלית' ב-$projDir"; return }
    $trace = Join-Path $Out "$name.trace.txt"
    $o = Join-Path $Out "$name.out.txt"; $e = Join-Path $Out "$name.err.txt"
    $p = Start-Process $runner -ArgumentList "`"$($projDir.TrimEnd('\'))`" --trace `"$trace`"" -PassThru -Wait -NoNewWindow -RedirectStandardOutput $o -RedirectStandardError $e
    $err = (Get-Content $e -Raw -Encoding utf8)
    $lines = if (Test-Path $trace) { @(Get-Content $trace -Encoding utf8).Count } else { 0 }
    Check "$id-trace" ($p.ExitCode -eq 0 -and $lines -gt 5) "הרצה ויזואלית של $name רצה עד הסוף ($lines שורות בהקלטה)" "קוד $($p.ExitCode); $err"
    $pd = $projDir.TrimEnd('\') + '\'
    $exe = [Environment]::ExpandEnvironmentVariables($pr.executablePath)
    $args1 = $pr.commandLineArgs.Replace('$(ProjectDir)', $pd)
    $wd = $pr.workingDirectory.Replace('$(ProjectDir)', $pd)
    Info "כמו Visual Studio: $exe $args1 (בתיקייה $wd)"
    KillDsviz
    $p2 = Start-Process $exe -ArgumentList $args1 -WorkingDirectory $wd -PassThru
    $seen = $false
    for ($i = 0; $i -lt $wait; $i++) { Start-Sleep 1; if ((Viewers).Count -gt 0) { $seen = $true; break } }
    Start-Sleep 5
    Shot "$name-viewer"
    Check "$id-window" $seen "החלון של DSViz נפתח כשמריצים את הפרופיל כמו Visual Studio ($name)" "חלונות: $((Windows) -join ' ; ')"
    KillDsviz; Stop-Process -Id $p2.Id -Force -ErrorAction SilentlyContinue
}

# ------------------------------------------------------------------ 2. הפתרון המוכן
Log "== 2. תיקיית התרגילים (DataStructures-CS.sln) — בנייה ב-MSBuild של Visual Studio"
$b = Build (Join-Path $csDir 'DataStructures-CS.sln') 'sln'
Check 'sln-build' ($b.Code -eq 0) "הפתרון נבנה" (($b.Text -split "`n" | Select-String 'error' | Select-Object -First 5) -join ' | ')
foreach ($cp in Get-ChildItem $csDir -Recurse -Filter '*.csproj') {
    Check 'sln-profile' ($null -ne (Profile $cp.DirectoryName)) "פרופיל 'הרצה ויזואלית' ב-$($cp.BaseName)"
}
RunLikeVs 'sln-run' (Join-Path $csDir 'Exercise') 'Exercise'
RunLikeVs 'sln-run' (Join-Path $csDir 'Examples\StackExamples') 'StackExamples'

# ------------------------------------------------------------------ 3. פרויקט חדש מהתבנית של Visual Studio
Log "== 3. פרויקט חדש (Console App) — כמו 'Create a new project' ב-Visual Studio"
$repos = Join-Path $env:USERPROFILE 'source\repos'
$code = @'
using Unit4.CollectionsLib;

Stack<int> s = new Stack<int>();
for (int i = 1; i <= 5; i++)
    s.Push(i * 10);
int sum = 0;
while (!s.IsEmpty())
    sum += s.Pop();
Console.WriteLine("sum = " + sum);
'@
$codeMain = @'
using System;
using Unit4.CollectionsLib;

namespace ConsoleApp2
{
    internal class Program
    {
        static int Count(Queue<int> q)
        {
            int n = 0;
            Queue<int> t = new Queue<int>();
            while (!q.IsEmpty()) { t.Insert(q.Remove()); n++; }
            while (!t.IsEmpty()) q.Insert(t.Remove());
            return n;
        }

        static void Main(string[] args)
        {
            Queue<int> q = new Queue<int>();
            q.Insert(4); q.Insert(7); q.Insert(1);
            Console.WriteLine(Count(q));
        }
    }
}
'@
$cases = @(
    @{ Id = 'new-toplevel'; Dir = (Join-Path $repos 'ConsoleApp1\ConsoleApp1'); Name = 'ConsoleApp1'; Code = $code; Tfm = 'net8.0'; Extra = @() },
    @{ Id = 'new-main'; Dir = (Join-Path $repos 'ConsoleApp2\ConsoleApp2'); Name = 'ConsoleApp2'; Code = $codeMain; Tfm = 'net8.0'; Extra = @('--use-program-main') },
    @{ Id = 'new-hebrew'; Dir = (Join-Path $repos 'מבני נתונים\תרגיל 1'); Name = 'Targil1'; Code = $code; Tfm = 'net8.0'; Extra = @() },
    @{ Id = 'new-onedrive'; Dir = (Join-Path $env:USERPROFILE "OneDrive - בית ספר\מסמכים\Visual Studio\Targil2"); Name = 'Targil2'; Code = $codeMain; Tfm = 'net8.0'; Extra = @() }
)
$sdks = (& dotnet --list-sdks) -join ' '
if ($sdks -match '(^|\s)9\.') { $cases += @{ Id = 'new-net9'; Dir = (Join-Path $repos 'ConsoleApp9\ConsoleApp9'); Name = 'ConsoleApp9'; Code = $code; Tfm = 'net9.0'; Extra = @() } }
if ($sdks -match '(^|\s)10\.') { $cases += @{ Id = 'new-net10'; Dir = (Join-Path $repos 'ConsoleApp10\ConsoleApp10'); Name = 'ConsoleApp10'; Code = $code; Tfm = 'net10.0'; Extra = @() } }
foreach ($c in $cases) {
    New-Item -ItemType Directory -Force $c.Dir | Out-Null
    $a = @('new', 'console', '-n', $c.Name, '-o', $c.Dir, '--framework', $c.Tfm, '--force') + $c.Extra
    & dotnet @a | Out-Null
    $prog = Join-Path $c.Dir 'Program.cs'
    $txt = $c.Code
    if ($c.Code -eq $codeMain) { $txt = $txt.Replace('namespace ConsoleApp2', "namespace $($c.Name)") }
    [IO.File]::WriteAllText($prog, $txt, (New-Object Text.UTF8Encoding($true)))   # Visual Studio שומר UTF-8 עם BOM
    $b = Build (Join-Path $c.Dir "$($c.Name).csproj") $c.Id
    Check "$($c.Id)-build" ($b.Code -eq 0) "$($c.Name) ($($c.Tfm)) נבנה — הספרייה מתווספת לבד" (($b.Text -split "`n" | Select-String 'error' | Select-Object -First 3) -join ' | ')
    $pr = Profile $c.Dir
    Check "$($c.Id)-profile" ($null -ne $pr) "פרופיל 'הרצה ויזואלית' נוסף לבד ($($c.Dir))"
    if ($pr) { RunLikeVs $c.Id $c.Dir $c.Id }
}

# .NET Framework (תבנית "Console App (.NET Framework)") — csproj בסגנון הישן
Log "== 3ב. Console App (.NET Framework 4.8)"
$fw = Join-Path $repos 'ConsoleAppFw\ConsoleAppFw'
New-Item -ItemType Directory -Force (Join-Path $fw 'Properties') | Out-Null
@'
<?xml version="1.0" encoding="utf-8"?>
<Project ToolsVersion="15.0" xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
  <Import Project="$(MSBuildExtensionsPath)\$(MSBuildToolsVersion)\Microsoft.Common.props" Condition="Exists('$(MSBuildExtensionsPath)\$(MSBuildToolsVersion)\Microsoft.Common.props')" />
  <PropertyGroup>
    <Configuration Condition=" '$(Configuration)' == '' ">Debug</Configuration>
    <Platform Condition=" '$(Platform)' == '' ">AnyCPU</Platform>
    <ProjectGuid>{5D0B4C1E-1A2B-4C3D-9E8F-0123456789AB}</ProjectGuid>
    <OutputType>Exe</OutputType>
    <RootNamespace>ConsoleAppFw</RootNamespace>
    <AssemblyName>ConsoleAppFw</AssemblyName>
    <TargetFrameworkVersion>v4.8</TargetFrameworkVersion>
    <FileAlignment>512</FileAlignment>
  </PropertyGroup>
  <PropertyGroup Condition=" '$(Configuration)|$(Platform)' == 'Debug|AnyCPU' ">
    <DebugSymbols>true</DebugSymbols>
    <DebugType>full</DebugType>
    <Optimize>false</Optimize>
    <OutputPath>bin\Debug\</OutputPath>
  </PropertyGroup>
  <ItemGroup>
    <Reference Include="System" />
    <Reference Include="System.Core" />
  </ItemGroup>
  <ItemGroup>
    <Compile Include="Program.cs" />
  </ItemGroup>
  <Import Project="$(MSBuildToolsPath)\Microsoft.CSharp.targets" />
</Project>
'@ | Set-Content (Join-Path $fw 'ConsoleAppFw.csproj') -Encoding utf8
[IO.File]::WriteAllText((Join-Path $fw 'Program.cs'), $codeMain.Replace('namespace ConsoleApp2', 'namespace ConsoleAppFw'), (New-Object Text.UTF8Encoding($true)))
$b = Build (Join-Path $fw 'ConsoleAppFw.csproj') 'fw'
Check 'fw-build' ($b.Code -eq 0) ".NET Framework 4.8 נבנה עם הספרייה" (($b.Text -split "`n" | Select-String 'error' | Select-Object -First 3) -join ' | ')
$pr = Profile $fw
if ($pr) { Ok 'fw-profile' "יש פרופיל 'הרצה ויזואלית'" } else { Info ".NET Framework: אין launchSettings — אין 'הרצה ויזואלית' ליד כפתור ההפעלה (רק מהקיצור / dsviz-cs)" }
$o = & $runner $fw --trace (Join-Path $Out 'fw.trace.txt') 2>&1
Check 'fw-run' ($LASTEXITCODE -eq 0) "dsviz-cs מריץ גם פרויקט .NET Framework" ($o -join ' | ')

# ------------------------------------------------------------------ 3ג. תבנית DSViz של Visual Studio
Log "== 3ג. תבנית הפרויקט 'תרגיל מבני נתונים (DSViz)'"
if ($tpl.Count -gt 0) {
    $td = Join-Path $env:TEMP 'dsviz-tpl'
    Remove-Item $td -Recurse -Force -ErrorAction SilentlyContinue
    Expand-Archive $tpl[0].FullName $td
    $vt = Get-ChildItem $td -Filter '*.vstemplate' -Recurse | Select-Object -First 1
    Check 'tpl-vstemplate' ($null -ne $vt) "יש .vstemplate בתבנית"
    $tp = Join-Path $repos 'TargilTpl\TargilTpl'
    New-Item -ItemType Directory -Force $tp | Out-Null
    Copy-Item (Join-Path $td '*') $tp -Recurse -Force
    Remove-Item (Join-Path $tp '*.vstemplate'), (Join-Path $tp '__*') -Force -ErrorAction SilentlyContinue
    foreach ($f in Get-ChildItem $tp -Recurse -File) {
        $s = [IO.File]::ReadAllText($f.FullName)
        $s2 = $s.Replace('$safeprojectname$', 'TargilTpl').Replace('$projectname$', 'TargilTpl').Replace('$guid1$', [guid]::NewGuid().ToString()).Replace('$targetframeworkversion$', '4.8')
        if ($s2 -ne $s) { [IO.File]::WriteAllText($f.FullName, $s2, (New-Object Text.UTF8Encoding($true))) }
    }
    $cp = Get-ChildItem $tp -Filter '*.csproj' | Select-Object -First 1
    if ($cp) { Rename-Item $cp.FullName 'TargilTpl.csproj' -ErrorAction SilentlyContinue }
    $b = Build (Join-Path $tp 'TargilTpl.csproj') 'tpl'
    Check 'tpl-build' ($b.Code -eq 0) "פרויקט מהתבנית נבנה" (($b.Text -split "`n" | Select-String 'error' | Select-Object -First 3) -join ' | ')
    if (Profile $tp) { RunLikeVs 'tpl-run' $tp 'TargilTpl' } else { Fail 'tpl-profile' "אין פרופיל 'הרצה ויזואלית' בפרויקט מהתבנית" }
}

# ------------------------------------------------------------------ 4. החלונות של C#
Log "== 4. החלונות 'תרגיל חדש' / 'הוסף מבנה נתונים' / 'שליחת תרגיל' / 'הצעה לפתרון' (מהקיצורים)"
foreach ($l in $lnks) {
    $s = $ws.CreateShortcut($l.FullName)
    if ($s.Arguments -notmatch 'CsTool (\w+)') { continue }
    $mode = $Matches[1]
    KillDsviz
    $p = Start-Process $s.TargetPath -ArgumentList $s.Arguments -PassThru
    $seen = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep 1
        $w = @(Get-Process -Id $p.Id -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle })
        if ($w.Count -gt 0) { $seen = $true; break }
        if ($p.HasExited) { break }
    }
    Start-Sleep 3
    Shot "cstool-$mode"
    Check "cstool-$mode" $seen "החלון '$($l.BaseName)' נפתח" "יצא: $($p.HasExited) קוד: $(if ($p.HasExited) { $p.ExitCode })"
    KillDsviz
}

# ------------------------------------------------------------------ 5. שליחת תרגיל (יצירת המחלקות + Gemini)
Log "== 5. שליחת תרגיל ב-C# — הפרויקט נוצר בפתרון, נבנה ב-Visual Studio ורץ ויזואלית"
$jar = Join-Path $dsviz 'dsviz.jar'
$java = Join-Path $dsviz 'jdk\bin\java.exe'
$home2 = Join-Path $Out 'submit-home'
New-Item -ItemType Directory -Force (Join-Path $home2 '.dsviz') | Out-Null
$aiFiles = Join-Path $Fixtures 'ai\files'

# מריץ את SubmitCli (אותו מנוע של החלון "שליחת תרגיל ב-C#") ומחזיר את השורות שהוא כותב (progress / error / done)
function SubmitCli([string]$id, [string[]]$files, [string]$url, [string]$note = '') {
    $items = @($files | ForEach-Object { @{ path = $_; name = [IO.Path]::GetFileName($_) } })
    $m = @{ lang = 'csharp'; out = $csDir; store = (Join-Path $home2 '.dsviz\solutions.json'); cs = @($runner); note = $note; items = $items; taken = @() }
    $mf = Join-Path $Out "$id.manifest.json"
    [IO.File]::WriteAllText($mf, ($m | ConvertTo-Json -Depth 6), (New-Object Text.UTF8Encoding($false)))
    if ($url) { $env:DSVIZ_WORKER_URL = $url } else { Remove-Item Env:\DSVIZ_WORKER_URL -ErrorAction SilentlyContinue }
    $o = Join-Path $Out "$id.out.txt"; $e = Join-Path $Out "$id.err.txt"
    $t0 = Get-Date
    $p = Start-Process $java -ArgumentList "-Duser.home=`"$home2`" -Dfile.encoding=UTF-8 -cp `"$jar`" il.lamerhav.dsviz.SubmitCli submit --manifest `"$mf`"" -PassThru -Wait -NoNewWindow -RedirectStandardOutput $o -RedirectStandardError $e
    Info ("שליחה '{0}': קוד {1}, {2:N0} שניות" -f $id, $p.ExitCode, ((Get-Date) - $t0).TotalSeconds)
    $lines = @(Get-Content $o -Encoding utf8 | Where-Object { $_.Trim().StartsWith('{') } | ForEach-Object { try { $_ | ConvertFrom-Json } catch { $null } } | Where-Object { $_ })
    foreach ($l in $lines) {
        if ($l.progress) { Info "  [$id] $($l.progress)" }
        if ($l.error) { Info "  [$id] שגיאה: $($l.error)" }
    }
    $script:lastErr = (Get-Content $e -Raw -Encoding utf8)
    return $lines
}
# התלמיד כותב את הפתרון: מחליף כל פעולה-שלד (ריקה) בהצעה לפתרון ששמורה בצד (solutions.json) — כך בודקים שהפרויקט שנוצר באמת עובד
function ApplySolutions([string]$folderDir) {
    $sol = (Get-Content (Join-Path $home2 '.dsviz\solutions.json') -Raw -Encoding utf8 | ConvertFrom-Json).solutions
    $applied = 0
    foreach ($f in Get-ChildItem $folderDir -Filter '*.cs') {
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
# בודק פרויקט שנוצר מהשליחה: קבצים, רישום בפתרון, בנייה ב-MSBuild של Visual Studio, הרצה ויזואלית
function VerifyCreated([string]$id, $done, [bool]$expectSolutionCheck) {
    $folder = $done.folder
    $dir = Join-Path $csDir "Exercises\$folder"
    Check "$id-folder" (Test-Path $dir) "נוצרה התיקייה Exercises\$folder" "קבצים: $($done.files -join ', ')"
    $sln = Get-Content (Join-Path $csDir 'DataStructures-CS.sln') -Raw -Encoding utf8
    Check "$id-sln" ($sln -match [regex]::Escape("$folder.csproj")) "הפרויקט $folder נרשם בפתרון (Visual Studio מציג אותו)"
    $cs = @(Get-ChildItem $dir -Filter '*.cs' -ErrorAction SilentlyContinue)
    Check "$id-classes" ($cs.Count -gt 0) "נוצרו מחלקות: $(($cs | ForEach-Object Name) -join ', ')"
    $here = @($cs | Select-String -Pattern 'כאן עונים' -SimpleMatch)
    Check "$id-stubs" ($here.Count -gt 0 -or $done.tasks -le 1) "יש מקום מסומן לתלמיד ('כאן עונים'): $($here.Count) · סעיפים: $($done.tasks)"
    # הקוד שנוצר — לארטיפקט (לבדיקה ידנית) ולתחילת הלוג
    $keep = Join-Path $Out "created\$id"
    New-Item -ItemType Directory -Force $keep | Out-Null
    foreach ($kf in @(Get-ChildItem $dir -Recurse -File | Where-Object { $_.FullName -notmatch '\\(bin|obj)\\' -and $_.Extension -in '.cs', '.csproj', '.json' })) { Copy-Item $kf.FullName (Join-Path $keep $kf.Name) -Force }
    if ($cs.Count -gt 0) { Info ("[$id] התחלת $($cs[0].Name):`r`n" + ((Get-Content $cs[0].FullName -TotalCount 30 -Encoding utf8) -join "`r`n")) }
    Check "$id-main" (($cs | Select-String -Pattern 'static void Main|static int Main|Main\(' | Select-Object -First 1) -ne $null) "יש Main"
    Check "$id-focus" ($done.focus -and (Test-Path $done.focus)) "הקובץ שנפתח לתלמיד (הסמן בסעיף א) קיים: $($done.focus) שורה $($done.line)"
    $errs = @($done.errors)
    Check "$id-noerrors" ($errs.Count -eq 0) "אין שגיאות קומפילציה בתרגיל שנוצר" ($errs -join ' | ')
    # תרגיל חדש = פעולות ריקות: השגיאה היחידה שמותרת היא "לא כל הנתיבים מחזירים ערך" (CS0161) — התלמיד עוד לא כתב את הפתרון
    $b = Build (Join-Path $dir "$folder.csproj") "$id-skeleton"
    $codes = @([regex]::Matches($b.Text, 'error (CS\d+)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    $other = @($codes | Where-Object { $_ -ne 'CS0161' })
    Check "$id-skeleton" ($other.Count -eq 0) "התרגיל החדש (לפני שהתלמיד כותב): אין שגיאות חוץ מ-CS0161 (פעולה בלי return): $($codes -join ',')" (($b.Text -split "`n" | Select-String 'error' | Select-Object -First 4) -join ' | ')
    $n = ApplySolutions $dir
    Check "$id-solve" ($n -gt 0 -or $done.tasks -eq 0) "הוכנסו $n הצעות לפתרון (כמו תלמיד שכתב את הפעולות)"
    $b = Build (Join-Path $dir "$folder.csproj") $id
    Check "$id-build" ($b.Code -eq 0) "$folder נבנה ב-MSBuild של Visual Studio אחרי שהפעולות נכתבו" (($b.Text -split "`n" | Select-String 'error' | Select-Object -First 4) -join ' | ')
    Check "$id-profile" ($null -ne (Profile $dir)) "פרופיל 'הרצה ויזואלית' בפרויקט החדש"
    if (Profile $dir) { RunLikeVs $id $dir $id }
    Check "$id-solutions" (Test-Path (Join-Path $home2 '.dsviz\solutions.json')) "ההצעות לפתרון נשמרו בצד (לא בקוד של התלמיד): $($done.solutionStatus)"
}

# א. שרת מדומה — בלי רשת: בודק את כל מה שלנו (יצירת הפרויקט, השמות, הרישום בפתרון, הבנייה) בלי תלות ב-Gemini
$mockLog = Join-Path $Out 'mock-worker.log'
$mock = Start-Process node -ArgumentList "`"$(Join-Path $Fixtures 'submit\mock-worker.js')`" 8796 `"$(Join-Path $Fixtures 'submit\cs-stack.json')`" `"$mockLog`"" -PassThru -WindowStyle Hidden
Start-Sleep 2
$photo = Join-Path $aiFiles 'photo.jpg'
$r = SubmitCli 'mock1' @($photo) 'http://127.0.0.1:8796'
$done = $r | Where-Object { $_.done } | Select-Object -Last 1
Check 'mock1-done' ($null -ne $done) "שליחה לשרת מדומה: התרגיל נוצר" ((($r | Where-Object { $_.error } | ForEach-Object error) -join ' | ') + ' ' + $script:lastErr)
if ($done) {
    Check 'mock1-name' ($done.folder -match '^Ex\d\d_') "שם הפרויקט לפי הנושא: $($done.folder)"
    Check 'mock1-status' ($done.solutionStatus -eq 'ok') "ההצעות לפתרון נבדקו והצליחו (dsviz-cs מריץ את Main): $($done.solutionStatus)"
    Check 'mock1-others' (@($done.others).Count -eq 1) "סעיף שעונים עליו במילים — בהודעה ולא בקוד"
    VerifyCreated 'mock1' $done $true
    # שליחה שנייה: שם אחר (לא מתנגש בראשונה)
    $r2 = SubmitCli 'mock2' @($photo) 'http://127.0.0.1:8796'
    $d2 = $r2 | Where-Object { $_.done } | Select-Object -Last 1
    Check 'mock2-unique' ($d2 -and $d2.folder -ne $done.folder) "שליחה שנייה מקבלת שם אחר: $($done.folder) ← $($d2.folder)"
}
Stop-Process -Id $mock.Id -Force -ErrorAction SilentlyContinue

# ב. החלון "שליחת תרגיל ב-C#" עצמו (Swing) מול שרת מדומה: הטופס → הדבקת תמונה → שליחה → הכרטיס "התרגיל נוצר"
Log "== 5ב. החלון 'שליחת תרגיל ב-C#' (מהקיצור) — הטופס, הדבקת תמונה, שליחה, הכרטיס"
$mock = Start-Process node -ArgumentList "`"$(Join-Path $Fixtures 'submit\mock-worker.js')`" 8797 `"$(Join-Path $Fixtures 'submit\cs-stack.json')`" `"$mockLog`"" -PassThru -WindowStyle Hidden
Start-Sleep 2
$cls = Join-Path $Out 'swing-cls'
New-Item -ItemType Directory -Force $cls | Out-Null
& javac -encoding UTF-8 -d $cls (Join-Path $Harness 'SwingScript.java') 2>&1 | Out-Null
Check 'gui-javac' (Test-Path (Join-Path $cls 'SwingScript.class')) "הוכן הכלי שמפעיל את החלון (SwingScript)"
$guiRoot = Join-Path $env:TEMP 'dsviz-gui-root'
Remove-Item $guiRoot -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item $csDir $guiRoot -Recurse
$script1 = Join-Path $Out 'gui1.txt'
[IO.File]::WriteAllLines($script1, [string[]]@("pasteimg $photo", 'text 0 רק שאלה 1', 'shot form', 'click שליחה ויצירת התרגיל', 'waitwin שליחת תרגיל — C#', 'wait 2500', 'shot card', 'click הצגה', 'shot show'), (New-Object Text.UTF8Encoding($false)))
$env:DSVIZ_WORKER_URL = 'http://127.0.0.1:8797'
$env:DSVIZ_CS = $runner
$o = Join-Path $Out 'gui1.out.txt'; $e = Join-Path $Out 'gui1.err.txt'
$p = Start-Process $java -ArgumentList "-Duser.home=`"$home2`" -Dfile.encoding=UTF-8 -Ddsviz.noOpen=true -cp `"$cls;$jar`" SwingScript `"$script1`" `"$(Join-Path $Out 'gui-shots')`" il.lamerhav.dsviz.cs.CsTool submit `"$guiRoot`"" -PassThru -NoNewWindow -RedirectStandardOutput $o -RedirectStandardError $e
if (-not $p.WaitForExit(240000)) { Stop-Process -Id $p.Id -Force; Info "החלון לא הסתיים בזמן" }
Remove-Item Env:\DSVIZ_WORKER_URL, Env:\DSVIZ_CS -ErrorAction SilentlyContinue
Stop-Process -Id $mock.Id -Force -ErrorAction SilentlyContinue
$sd = Join-Path $Out 'gui-shots'
function ShotJson([string]$n) { $f = Join-Path $sd "$n.json"; if (Test-Path $f) { Get-Content $f -Raw -Encoding utf8 } else { '' } }
Check 'gui-form' ((ShotJson 'form') -match 'שליחה ויצירת התרגיל') "הטופס נפתח עם הכפתור 'שליחה ויצירת התרגיל'" (Get-Content $e -Raw -Encoding utf8)
Check 'gui-card' (((ShotJson 'card') -match 'התרגיל נוצר') -and ((ShotJson 'card') -match 'Ex\d\d_')) "אחרי השליחה: הכרטיס 'התרגיל נוצר' עם שם הפרויקט" (Get-Content $e -Raw -Encoding utf8)
Check 'gui-others' (((ShotJson 'card') -match 'עונים במילים') -and -not ((ShotJson 'card') -match 'מהי סיבוכיות')) "הסעיף שעונים עליו במילים — בהודעה הקצרה, והטקסט שלו לא בכרטיס"
Check 'gui-show' ((ShotJson 'show') -match 'מהי סיבוכיות זמן הריצה') "'הצגה' פותח את הסעיף במילים"
Check 'gui-created' (@(Get-ChildItem (Join-Path $guiRoot 'Exercises') -Directory | Where-Object { $_.Name -like 'Ex*_Stacks*' }).Count -ge 1) "הפרויקט נוצר בתיקיית התרגילים"

# ב2. החלון "שליחת תרגיל ב-C#" מול Gemini האמיתי: הדבקת תמונה של דף תרגיל → שליחה → תצוגת הבנייה → הכרטיס "התרגיל נוצר"; ואז החלון "הצעה לפתרון"
function SwingRun([string]$id, [string[]]$lines, [string]$mainArgs, [int]$timeoutSec = 400) {
    $sf = Join-Path $Out "$id.script.txt"
    [IO.File]::WriteAllLines($sf, [string[]]$lines, (New-Object Text.UTF8Encoding($false)))
    $o = Join-Path $Out "$id.out.txt"; $e = Join-Path $Out "$id.err.txt"
    $env:DSVIZ_CS = $runner
    $p = Start-Process $java -ArgumentList "-Duser.home=`"$home2`" -Dfile.encoding=UTF-8 -Ddsviz.noOpen=true -cp `"$cls;$jar`" SwingScript `"$sf`" `"$(Join-Path $Out 'gui-shots')`" $mainArgs" -PassThru -NoNewWindow -RedirectStandardOutput $o -RedirectStandardError $e
    if (-not $p.WaitForExit($timeoutSec * 1000)) { Stop-Process -Id $p.Id -Force; Info "החלון $id לא הסתיים בזמן" }
    Remove-Item Env:\DSVIZ_CS -ErrorAction SilentlyContinue
    return (Get-Content $e -Raw -Encoding utf8)
}
if ($Live) {
    Log "== 5ב2. החלון 'שליחת תרגיל ב-C#' מול Gemini האמיתי"
    $guiRoot2 = Join-Path $env:TEMP 'dsviz-gui-root2'
    Remove-Item $guiRoot2 -Recurse -Force -ErrorAction SilentlyContinue
    Copy-Item $csDir $guiRoot2 -Recurse
    Remove-Item Env:\DSVIZ_WORKER_URL -ErrorAction SilentlyContinue
    $page = Join-Path $aiFiles 'exam-page-1.png'
    $errG = SwingRun 'gui-live' @("pasteimg $page", 'shot live-form', 'click שליחה ויצירת התרגיל', 'wait 4000', 'shot live-building', 'waittext התרגיל נוצר|300000', 'shot live-card', 'click הצגה', 'shot live-show') "il.lamerhav.dsviz.cs.CsTool submit `"$guiRoot2`""
    $cardG = ShotJson 'live-card'
    Check 'gui-live-card' (($cardG -match 'התרגיל נוצר') -and ($cardG -match 'Ex\d\d_')) "Gemini אמיתי דרך החלון: הכרטיס 'התרגיל נוצר' עם שם הפרויקט" $errG
    Check 'gui-live-project' (@(Get-ChildItem (Join-Path $guiRoot2 'Exercises') -Directory -ErrorAction SilentlyContinue).Count -ge 1) "הפרויקט שביקש Gemini נוצר בתיקיית התרגילים (דרך החלון)"
    Log "== 5ב3. החלון 'הצעה לפתרון ב-C#' — ההצעות ש-Gemini נתן, נבדקו והשמורות בצד"
    $errS = SwingRun 'gui-solution' @('shot sol-list', 'select 1', 'wait 800', 'shot sol-1', 'select 0', 'wait 800', 'shot sol-0') "il.lamerhav.dsviz.cs.CsTool solution `"$guiRoot2`"" 120
    $sol1 = ShotJson 'sol-1'; $sol0 = ShotJson 'sol-0'
    Check 'gui-solution-open' ((ShotJson 'sol-list') -match 'הצעה לפתרון|פתרון') "החלון 'הצעה לפתרון ב-C#' נפתח עם רשימת התרגילים והסעיפים" $errS
    Check 'gui-solution-text' (($sol1 -match 'נבדק') -or ($sol0 -match 'נבדק')) "ההצעה לפתרון מוצגת ומסומנת 'נבדק: הרצנו את main' (ההצעה של Gemini עברה בדיקה)" "$sol0 | $sol1"
}

# ג. Gemini אמיתי (השרת של Cloudflare): אותו מסלול כמו תלמיד. דף תרגיל → תרגיל בפרויקט → בנייה → הרצה ויזואלית
if ($Live) {
    Log "== 5ג. Gemini אמיתי (שרת השליחה האמיתי): דף תרגיל ← תרגיל ב-C# שנבנה ורץ"
    foreach ($case in @(
            @{ Id = 'live1'; Files = @((Join-Path $aiFiles 'exam-page-1.png')); Note = '' },   # תמונה של דף מבחן (רשימה מקושרת + תור)
            @{ Id = 'live2'; Files = @((Join-Path $aiFiles 'targil.docx')); Note = 'סעיף א בלבד' })) {
        $r = SubmitCli $case.Id $case.Files '' $case.Note
        $done = $r | Where-Object { $_.done } | Select-Object -Last 1
        $errTxt = (($r | Where-Object { $_.error } | ForEach-Object error) -join ' | ') + ' ' + $script:lastErr
        Check "$($case.Id)-done" ($null -ne $done) "Gemini החזיר תרגיל והוא נוצר מ-$(($case.Files | ForEach-Object { Split-Path $_ -Leaf }) -join ', ')" $errTxt
        if ($done) {
            Info ("תרגיל: {0} · {1} סעיפים · הצעות לפתרון: {2} · שגיאות: {3}" -f $done.folder, $done.tasks, $done.solutionStatus, @($done.errors).Count)
            VerifyCreated $case.Id $done $false
        }
    }
} else { Info "Gemini אמיתי: דילגנו (-Live:`$false)" }

# ------------------------------------------------------------------ 6. Visual Studio עצמו
if (-not $NoGui) {
    Log "== 6. Visual Studio: פותחים פרויקט ולוחצים על כפתור ההפעלה"
    Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes
    Add-Type -Namespace Win -Name Mouse -MemberDefinition '[DllImport("user32.dll")] public static extern void mouse_event(int f, int dx, int dy, int d, int e); [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);'
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
    # כל הרכיבים של Visual Studio (כל החלונות של התהליך). שם → רכיב
    function VsElements([int]$vsPid) {
        $cond = New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ProcessIdProperty, $vsPid)
        try { return @([Windows.Automation.AutomationElement]::RootElement.FindAll([Windows.Automation.TreeScope]::Descendants, $cond)) } catch { return @() }
    }
    # בהפעלה ראשונה של Visual Studio במחשב חדש: חלון התחברות, בחירת ערכת נושא וכו'. מדלגים עליהם כמו תלמיד ("Skip and add accounts later").
    # בלי ללחוץ על ה-X של חלון ההתחברות — הוא שואל "Are you sure you want to exit?" וסוגר את Visual Studio
    function ElName($el) { try { return $el.Current.Name } catch { return '' } }
    function SkipFirstRun([int]$vsPid) {
        $els = VsElements $vsPid
        foreach ($el in $els) {   # אם יצא חלון "לצאת מ-Visual Studio?" — עונים "No"
            if ((ElName $el) -match 'Are you sure you want to exit') {
                foreach ($b in $els) { if ((ElName $b) -eq 'No' -and $b.Current.ControlType.ProgrammaticName -match 'Button') { $how = ClickEl $b; Info "ענינו 'No' לשאלת היציאה ($how)"; return $true } }
            }
        }
        foreach ($pat in @('Skip and add accounts later', 'Not now, maybe later', 'Start Visual Studio', 'Continue without code')) {
            foreach ($el in $els) {
                $n = ElName $el
                if ($n -and $n -match $pat -and $el.Current.ControlType.ProgrammaticName -match 'Button|Hyperlink|Text') {
                    $how = ClickEl $el
                    if ($how) { Info "חלון הפעלה ראשונה: לחצנו על '$n' ($how)"; return $true }
                }
            }
        }
        return $false
    }
    function OpenVs([string]$id, [string]$target, [string]$title, [string]$projDir, [string]$profileName, [int]$readyMin = 9) {
        KillDsviz
        Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force
        Start-Sleep 3
        if ($profileName) {
            # הפרופיל שנבחר בתפריט שליד כפתור ההפעלה נשמר ב-.csproj.user — כמו שתלמיד בוחר "הרצה ויזואלית" פעם אחת
            $cp = Get-ChildItem $projDir -Filter '*.csproj' | Select-Object -First 1
            $u = "<?xml version=`"1.0`" encoding=`"utf-8`"?>`r`n<Project ToolsVersion=`"Current`" xmlns=`"http://schemas.microsoft.com/developer/msbuild/2003`">`r`n  <PropertyGroup>`r`n    <ActiveDebugProfile>$profileName</ActiveDebugProfile>`r`n  </PropertyGroup>`r`n</Project>`r`n"
            [IO.File]::WriteAllText($cp.FullName + '.user', $u, (New-Object Text.UTF8Encoding($false)))
        }
        $t0 = Get-Date
        $vsP = Start-Process $devenv -ArgumentList "`"$target`"" -PassThru
        $ready = $false; $k = 0
        while (((Get-Date) - $t0).TotalMinutes -lt $readyMin) {
            Start-Sleep 5; $k++
            $vsP.Refresh()
            if ($vsP.HasExited) { Info "devenv נסגר (קוד $($vsP.ExitCode))"; break }
            if (SkipFirstRun $vsP.Id) { Start-Sleep 3 }
            $vsP.Refresh()
            # Visual Studio נפתח: הכותרת היא "<פתרון או פרויקט> - Microsoft Visual Studio" (פרויקט בתוך תיקיית פתרון — שם הפתרון)
            if ($vsP.MainWindowTitle -like '*- Microsoft Visual Studio*') { $ready = $true; break }
            if ($k % 12 -eq 0) { Info ("ממתינים ל-Visual Studio ({0:N0} שניות): '{1}'" -f ((Get-Date) - $t0).TotalSeconds, $vsP.MainWindowTitle); Shot "$id-wait$k" }
        }
        Info ("Visual Studio: {0:N0} שניות, כותרת '{1}'" -f ((Get-Date) - $t0).TotalSeconds, $vsP.MainWindowTitle)
        Check "$id-open" $ready "Visual Studio פתח את $title" "כותרת: $($vsP.MainWindowTitle) · חלונות: $((Windows) -join ' ; ')"
        if (-not $ready) { Shot "$id-opened"; Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force; return }
        [Screen2]::ShowWindow($vsP.MainWindowHandle, 3) | Out-Null   # SW_MAXIMIZE — חלון גדול, שרואים בו את Visual Studio
        [Screen2]::SetForegroundWindow($vsP.MainWindowHandle) | Out-Null
        Start-Sleep 45   # טעינת הפרויקט, הבנייה של עיצוב-זמן ו-IntelliSense
        SkipFirstRun $vsP.Id | Out-Null
        [Screen2]::ShowWindow($vsP.MainWindowHandle, 3) | Out-Null
        Shot "$id-opened"
        # הכפתור הירוק: שמו הוא שם הפרופיל שנבחר. מחפשים אותו (ואת הפרופילים) בסרגל הכלים
        $names = @(); $start = $null
        foreach ($el in (VsElements $vsP.Id)) {
            $n = ''
            try { $n = $el.Current.Name } catch { }
            if ($n -and $n -match 'ויזואלית|רגילה|Start|Debug Target|Continue|ConsoleApp|Ex\d\d') {
                $names += "$($el.Current.ControlType.ProgrammaticName.Replace('ControlType.', '')):$n"
                if (-not $start -and $profileName -and $n -like "*$profileName*" -and $el.Current.ControlType.ProgrammaticName -match 'Button|SplitButton') { $start = $el }
            }
        }
        $names | Select-Object -Unique | Set-Content (Join-Path $Out "$id-uia.txt") -Encoding utf8
        Info "בסרגל: $((($names | Select-Object -Unique) | Select-Object -First 12) -join ' ; ')"
        # שם הפרופיל לא נחשף ל-UI Automation (הכפתור מצויר בעצמו) — רואים אותו בצילום; כאן בודקים שיש כפתור הפעלה, והפרופיל נבחר ב-.csproj.user
        if ($profileName) { Check "$id-button" (($names -join ' ') -match 'Debug Target') "ב-Visual Studio יש תפריט הפעלה (Debug Target); הפרופיל '$profileName' נבחר בו (נראה בצילום)" ($names -join ' ; ') }
        # מפעילים: לחיצה על הכפתור (ואם לא נמצא — Ctrl+F5 = Start Without Debugging)
        $how = $null
        if ($start) { $how = ClickEl $start; if ($how) { Info "לחצנו על '$($start.Current.Name)' ($how)" } }
        if (-not $how) {
            $wsh = New-Object -ComObject WScript.Shell
            [void]$wsh.AppActivate($vsP.Id); Start-Sleep 1; $wsh.SendKeys('^{F5}'); Info "Ctrl+F5"
        }
        $seen = $false
        Start-Sleep 2; Shot "$id-clicked"
        for ($i = 0; $i -lt 150; $i++) { Start-Sleep 1; if ((Viewers).Count -gt 0) { $seen = $true; break } }
        Start-Sleep 6
        Shot "$id-run"
        Info "חלונות: $((Windows) -join ' ; ')"
        Check "$id-viewer" $seen "Visual Studio: אחרי לחיצה על כפתור ההפעלה נפתח החלון של DSViz" "חלונות: $((Windows) -join ' ; ')"
        KillDsviz
        Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force
        Start-Sleep 3
    }
    # א. תרגיל שנוצר משליחה (ואחרי שהתלמיד כתב את הפתרון): פותחים את הפרויקט ולוחצים על "הרצה ויזואלית"
    $exCsproj = Get-ChildItem (Join-Path $csDir 'Exercises') -Recurse -Filter 'Ex01_*.csproj' | Select-Object -First 1
    if ($exCsproj) { OpenVs 'vs-exercise' $exCsproj.FullName 'Ex01_' $exCsproj.DirectoryName 'הרצה ויזואלית' 9 }
    else { Fail 'vs-exercise-missing' "אין פרויקט תרגיל שנוצר" }
    # ב. פרויקט קונסול חדש שהתלמיד יצר (top-level, ImplicitUsings) — פותחים ולוחצים על "הרצה ויזואלית"
    OpenVs 'vs-new' (Join-Path $repos 'ConsoleApp1\ConsoleApp1\ConsoleApp1.csproj') 'ConsoleApp1' (Join-Path $repos 'ConsoleApp1\ConsoleApp1') 'הרצה ויזואלית' 5
}

# ------------------------------------------------------------------ סיכום
$ok = @($script:lines | Where-Object { $_ -like '  ok *' }).Count
Log ""
Log "SUMMARY $ok ok / $($script:fails) FAIL — $vsName $vsVer"
$script:lines | Set-Content (Join-Path $Out 'summary.txt') -Encoding utf8
if ($env:GITHUB_STEP_SUMMARY) { ("``````" + "`n" + ($script:lines -join "`n") + "`n" + "``````") | Add-Content $env:GITHUB_STEP_SUMMARY -Encoding utf8 }
exit $(if ($script:fails -gt 0) { 1 } else { 0 })
