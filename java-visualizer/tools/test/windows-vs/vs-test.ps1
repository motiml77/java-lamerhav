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
function Viewers() {
    @(Get-CimInstance Win32_Process -Filter "Name='javaw.exe' or Name='java.exe'" | Where-Object { $_.CommandLine -match 'TraceViewer|CsTool' })
}
function KillDsviz() {
    Get-CimInstance Win32_Process -Filter "Name='javaw.exe' or Name='java.exe' or Name='dsviz-cs.exe'" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

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
# מריצים את הפרופיל כמו Visual Studio: %משתני סביבה% ו-$(ProjectDir) (נגמר ב-\) מוחלפים, ו-workingDirectory
function RunLikeVs([string]$id, [string]$projDir, [string]$name, [int]$wait = 25, [string]$stdin = '') {
    $pr = Profile $projDir
    if (-not $pr) { Fail $id "אין פרופיל 'הרצה ויזואלית' ב-$projDir"; return }
    $pd = $projDir.TrimEnd('\') + '\'
    $exe = [Environment]::ExpandEnvironmentVariables($pr.executablePath)
    $args1 = $pr.commandLineArgs.Replace('$(ProjectDir)', $pd)
    $wd = $pr.workingDirectory.Replace('$(ProjectDir)', $pd)
    Info "מריצים: `"$exe`" $args1 (בתיקייה $wd)"
    # קודם בדיקה עם קובץ הרצה (בלי חלון): התוכנית רצה עד הסוף
    $trace = Join-Path $Out "$name.trace.txt"
    $o = Join-Path $Out "$name.out.txt"; $e = Join-Path $Out "$name.err.txt"
    $inF = $null
    if ($stdin) { $inF = Join-Path $Out "$name.in.txt"; $stdin | Set-Content $inF -Encoding utf8 }
    $extra = "--trace `"$trace`"" + $(if ($inF) { " --input `"$inF`"" } else { '' })
    $p = Start-Process $exe -ArgumentList "$args1 $extra" -WorkingDirectory $wd -PassThru -Wait -NoNewWindow -RedirectStandardOutput $o -RedirectStandardError $e
    $err = (Get-Content $e -Raw -Encoding utf8)
    $lines = if (Test-Path $trace) { @(Get-Content $trace -Encoding utf8).Count } else { 0 }
    Check "$id-trace" ($p.ExitCode -eq 0 -and $lines -gt 5) "הרצה ויזואלית של $name רצה עד הסוף ($lines שורות בהקלטה)" "קוד $($p.ExitCode); $err"
    # ואז בדיוק כמו Visual Studio (בחלון קונסול חדש): נפתח החלון של DSViz
    KillDsviz
    $p2 = Start-Process $exe -ArgumentList $args1 -WorkingDirectory $wd -PassThru
    $seen = $false
    for ($i = 0; $i -lt $wait; $i++) { Start-Sleep 1; if ((Viewers).Count -gt 0) { $seen = $true; break } }
    Start-Sleep 4
    Shot "$name-viewer"
    Check "$id-window" $seen "החלון של DSViz נפתח ($name)" "חלונות: $((Windows) -join ' ; ')"
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
New-Item -ItemType Directory -Force $home2 | Out-Null
$aiFiles = Join-Path $Fixtures 'ai\files'

# מריץ את SubmitCli (אותו מנוע של החלון "שליחת תרגיל ב-C#") ומחזיר את השורות שהוא כותב (progress / error / done)
function SubmitCli([string]$id, [string[]]$files, [string]$url, [string]$note = '') {
    $items = @($files | ForEach-Object { @{ path = $_; name = [IO.Path]::GetFileName($_) } })
    $m = @{ lang = 'csharp'; out = $csDir; store = (Join-Path $home2 'solutions.json'); cs = @($runner); note = $note; items = $items; taken = @() }
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
    Check "$id-stubs" ($here.Count -gt 0 -or $done.tasks -eq 0) "יש מקום מסומן לתלמיד ('כאן עונים'): $($here.Count) · סעיפים: $($done.tasks)"
    Check "$id-main" (($cs | Select-String -Pattern 'static void Main|static int Main|Main\(' | Select-Object -First 1) -ne $null) "יש Main"
    Check "$id-focus" ($done.focus -and (Test-Path $done.focus)) "הקובץ שנפתח לתלמיד (הסמן בסעיף א) קיים: $($done.focus) שורה $($done.line)"
    $errs = @($done.errors)
    Check "$id-noerrors" ($errs.Count -eq 0) "אין שגיאות קומפילציה בתרגיל שנוצר" ($errs -join ' | ')
    $b = Build (Join-Path $dir "$folder.csproj") $id
    Check "$id-build" ($b.Code -eq 0) "$folder נבנה ב-MSBuild של Visual Studio" (($b.Text -split "`n" | Select-String 'error' | Select-Object -First 4) -join ' | ')
    $b2 = Build (Join-Path $csDir 'DataStructures-CS.sln') "$id-sln"
    Check "$id-sln-build" ($b2.Code -eq 0) "כל הפתרון (כולל התרגיל החדש) נבנה" (($b2.Text -split "`n" | Select-String 'error' | Select-Object -First 4) -join ' | ')
    Check "$id-profile" ($null -ne (Profile $dir)) "פרופיל 'הרצה ויזואלית' בפרויקט החדש"
    if (Profile $dir) { RunLikeVs $id $dir $id }
    Check "$id-solutions" (Test-Path (Join-Path $home2 'solutions.json')) "ההצעות לפתרון נשמרו בצד (לא בקוד של התלמיד): $($done.solutionStatus)"
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
@("pasteimg $photo", 'text 0 רק שאלה 1', 'shot form', 'click שליחה ויצירת התרגיל', 'waitwin שליחת תרגיל — C#', 'wait 2500', 'shot card', 'click הצגה', 'shot show') |
    Set-Content $script1 -Encoding utf8
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

# ג. Gemini אמיתי (השרת של Cloudflare): אותו מסלול כמו תלמיד. דף תרגיל → תרגיל בפרויקט → בנייה → הרצה ויזואלית
if ($Live) {
    Log "== 5ג. Gemini אמיתי (שרת השליחה האמיתי): דף תרגיל ← תרגיל ב-C# שנבנה ורץ"
    foreach ($case in @(
            @{ Id = 'live1'; Files = @((Join-Path $aiFiles 'photo.jpg')); Note = '' },
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
    Log "== 6. Visual Studio: פותחים את הפתרון ולוחצים על כפתור ההפעלה"
    Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes
    function OpenVs([string]$id, [string]$target, [string]$title, [string]$projDir, [string]$profileName) {
        KillDsviz
        Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force
        if ($profileName) {
            # הפרופיל שנבחר בתפריט שליד כפתור ההפעלה נשמר ב-.csproj.user — כמו שתלמיד בוחר "הרצה ויזואלית" פעם אחת
            $cp = Get-ChildItem $projDir -Filter '*.csproj' | Select-Object -First 1
            @"
<?xml version="1.0" encoding="utf-8"?>
<Project ToolsVersion="Current" xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
  <PropertyGroup>
    <ActiveDebugProfile>$profileName</ActiveDebugProfile>
  </PropertyGroup>
</Project>
"@ | Set-Content ($cp.FullName + '.user') -Encoding utf8
        }
        $t0 = Get-Date
        $vsP = Start-Process $devenv -ArgumentList "`"$target`"" -PassThru
        $ready = $false
        for ($i = 0; $i -lt 300; $i++) {
            Start-Sleep 2
            $vsP.Refresh()
            if ($vsP.MainWindowTitle -like "*$title*") { $ready = $true; break }
        }
        Info ("Visual Studio נפתח תוך {0:N0} שניות: '{1}'" -f ((Get-Date) - $t0).TotalSeconds, $vsP.MainWindowTitle)
        Info "חלונות: $((Windows) -join ' ; ')"
        Start-Sleep 60   # טעינת הפרויקטים והבנייה של עיצוב-זמן
        Shot "$id-opened"
        Check "$id-open" $ready "Visual Studio פתח את $title" "כותרת: $($vsP.MainWindowTitle)"
        if (-not $ready) { return }
        # הכפתור הירוק: שמו הוא שם הפרופיל שנבחר. מחפשים אותו (ואת הפרופילים) בסרגל הכלים
        $names = @()
        $start = $null
        try {
            $root = [Windows.Automation.AutomationElement]::FromHandle($vsP.MainWindowHandle)
            $all = $root.FindAll([Windows.Automation.TreeScope]::Descendants, [Windows.Automation.Condition]::TrueCondition)
            foreach ($el in $all) {
                $n = $el.Current.Name
                if ($n -and ($n -match 'ויזואלית|רגילה|Start|הפעלה|ConsoleApp|Exercise|Debug Target')) {
                    $names += "$($el.Current.ControlType.ProgrammaticName.Replace('ControlType.', '')):$n"
                    if (-not $start -and $profileName -and $n -like "*$profileName*") { $start = $el }
                }
            }
        } catch { Info "UIAutomation: $_" }
        $names | Select-Object -Unique | Set-Content (Join-Path $Out "$id-uia.txt") -Encoding utf8
        Info "בסרגל: $((($names | Select-Object -Unique) | Select-Object -First 12) -join ' ; ')"
        if ($profileName) { Check "$id-button" ($null -ne $start) "ליד כפתור ההפעלה מופיע '$profileName'" }
        # לוחצים: Invoke על הכפתור (ואם אין — F5)
        $clicked = $false
        if ($start) {
            try { $start.GetCurrentPattern([Windows.Automation.InvokePattern]::Pattern).Invoke(); $clicked = $true; Info "לחיצה על '$($start.Current.Name)'" } catch { Info "Invoke: $_" }
        }
        if (-not $clicked) {
            $wsh = New-Object -ComObject WScript.Shell
            [void]$wsh.AppActivate($vsP.Id); Start-Sleep 1; $wsh.SendKeys('{F5}'); Info "F5"
        }
        $seen = $false
        for ($i = 0; $i -lt 120; $i++) { Start-Sleep 1; if ((Viewers).Count -gt 0) { $seen = $true; break } }
        Start-Sleep 6
        Shot "$id-run"
        Info "חלונות: $((Windows) -join ' ; ')"
        Check "$id-viewer" $seen "אחרי לחיצה על כפתור ההפעלה נפתח החלון של DSViz" "חלונות: $((Windows) -join ' ; ')"
        KillDsviz
        Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force
        Start-Sleep 3
    }
    # א. הקיצור "תרגילי C# (מבני נתונים)" — הפתרון המוכן, "הרצה ויזואלית" ראשון בתפריט → F5 מריץ ויזואלית
    OpenVs 'vs-sln' (Join-Path $csDir 'DataStructures-CS.sln') 'DataStructures-CS' (Join-Path $csDir 'Exercise') ''
    # ב. פרויקט חדש שהתלמיד יצר, ובחר "הרצה ויזואלית" בתפריט שליד הכפתור
    OpenVs 'vs-new' (Join-Path $repos 'ConsoleApp1\ConsoleApp1\ConsoleApp1.csproj') 'ConsoleApp1' (Join-Path $repos 'ConsoleApp1\ConsoleApp1') 'הרצה ויזואלית'
}

# ------------------------------------------------------------------ סיכום
$ok = @($script:lines | Where-Object { $_ -like '  ok *' }).Count
Log ""
Log "SUMMARY $ok ok / $($script:fails) FAIL — $vsName $vsVer"
$script:lines | Set-Content (Join-Path $Out 'summary.txt') -Encoding utf8
if ($env:GITHUB_STEP_SUMMARY) { ("``````" + "`n" + ($script:lines -join "`n") + "`n" + "``````") | Add-Content $env:GITHUB_STEP_SUMMARY -Encoding utf8 }
exit $(if ($script:fails -gt 0) { 1 } else { 0 })
