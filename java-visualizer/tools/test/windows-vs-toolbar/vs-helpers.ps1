# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# עזרים משותפים ל-vs-real-user.ps1 ול-vs-upgrade.ps1 (נטען ב-dot-source, אחרי ש-$Out נקבע): יומן ובדיקות, צילומי מסך, UI Automation,
# בחירת Visual Studio, פתיחה / סגירה, מצב הכפתורים של DSViz בסרגל, לחיצה על "מאגר שאלות" וזיהוי החלון שנפתח.
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
New-Item -ItemType Directory -Force $Out | Out-Null
$Out = (Resolve-Path $Out).Path
$script:fails = 0
$script:lines = New-Object System.Collections.Generic.List[string]
function Log([string]$s) { Write-Host $s; $script:lines.Add($s) }
function Info([string]$s) { Log "  ..   $s" }
function Check([string]$id, [bool]$cond, [string]$s, [string]$why = '') {
    if ($cond) { Log "  ok   $id  $s" } else { $script:fails++; Log ("  FAIL $id  $s" + $(if ($why) { " — $why" } else { '' })) }
}
function Save-Summary() {
    $script:lines | Set-Content (Join-Path $Out 'summary.txt') -Encoding utf8
    if ($env:GITHUB_STEP_SUMMARY) { ("``````" + "`n" + ($script:lines -join "`n") + "`n" + "``````") | Add-Content $env:GITHUB_STEP_SUMMARY -Encoding utf8 }
}
Add-Type -AssemblyName System.Windows.Forms, System.Drawing, UIAutomationClient, UIAutomationTypes
if (-not ('VsHelpScreen' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class VsHelpScreen {
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
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
}
'@
}
foreach ($res in @(@(1920, 1080), @(1680, 1050), @(1600, 900))) { if ([VsHelpScreen]::Set($res[0], $res[1]) -eq 0) { break } }
Start-Sleep 3

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
function ClickEl($el) {
    try { $el.GetCurrentPattern([Windows.Automation.InvokePattern]::Pattern).Invoke(); return 'invoke' } catch { return $null }
}
function VsElements([int]$vsPid) {
    $cond = New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ProcessIdProperty, $vsPid)
    try { return @([Windows.Automation.AutomationElement]::RootElement.FindAll([Windows.Automation.TreeScope]::Descendants, $cond)) } catch { return @() }
}
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
function GetDte() {
    foreach ($progId in 'VisualStudio.DTE.18.0', 'VisualStudio.DTE.17.0', 'VisualStudio.DTE') { try { return [Runtime.InteropServices.Marshal]::GetActiveObject($progId) } catch { } }
    return $null
}
function Windows() { Get-Process | Where-Object { $_.MainWindowTitle } | ForEach-Object { "$($_.ProcessName)[$($_.Id)]: $($_.MainWindowTitle)" } }

# ------------------------------------------------------------------ Visual Studio
# כל המופעים (PowerShell 5.1: ConvertFrom-Json מחזיר את המערך כאובייקט אחד — ForEach-Object מפרק אותו)
function VsInstances() {
    $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
    return @(& $vswhere -all -prerelease -products * -format json | Out-String | ConvertFrom-Json | ForEach-Object { $_ })
}
# המופע לבדיקה: לפי מוצר (Community וכו'), ואם אין — החדש ביותר. מחזיר גם את devenv ואת תיקיית המשתמש שלו
function PickVs([string]$product) {
    $all = VsInstances
    Info "מופעים של Visual Studio: $($all.Count)"
    foreach ($i in $all) { Info ("   {0} {1} [{2}] {3} · id={4}" -f $i.displayName, $i.catalog.productDisplayVersion, $i.productId, $i.installationPath, $i.instanceId) }
    $inst = $null
    if ($product) { $inst = $all | Where-Object { $_.productId -eq $product } | Sort-Object { [version]$_.installationVersion } | Select-Object -Last 1 }
    if (-not $inst) { $inst = $all | Where-Object { $_.productId -match 'Community|Professional|Enterprise' } | Sort-Object { [version]$_.installationVersion } | Select-Object -Last 1 }
    $script:vsInst = $inst
    $script:devenv = Join-Path $inst.installationPath 'Common7\IDE\devenv.exe'
    $script:vsMajor = ([version]$inst.installationVersion).Major
    $script:vsHiveDir = Join-Path $env:LOCALAPPDATA ("Microsoft\VisualStudio\{0}.0_{1}" -f $script:vsMajor, $inst.instanceId)
    $script:vsLabel = "{0} {1}" -f $inst.displayName, $inst.catalog.productDisplayVersion
    return $inst
}
# ההרחבה של DSViz במופע שנבדק? (למשתמש או לכל המשתמשים)
function ExtensionIn() {
    $dirs = @((Join-Path $script:vsHiveDir 'Extensions'), (Join-Path $script:vsInst.installationPath 'Common7\IDE\Extensions'))
    foreach ($d in $dirs) {
        if (-not (Test-Path $d)) { continue }
        $m = Get-ChildItem $d -Recurse -Filter extension.vsixmanifest -ErrorAction SilentlyContinue |
            Where-Object { (Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue) -match 'DsViz.VisualStudio.Toolbar' } | Select-Object -First 1
        if ($m) { return $m.Directory.FullName }
    }
    return ''
}
function InstallDsviz([string]$setup, [string]$argsLine, [string]$what) {
    Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    $t = Get-Date
    $p = Start-Process $setup -ArgumentList $argsLine -PassThru -Wait
    $d = Join-Path $env:LOCALAPPDATA 'DSViz'
    $b = if (Test-Path "$d\build.txt") { (Get-Content "$d\build.txt" -Raw).Trim() } else { '' }
    Info ("{0}: {1} {2} — קוד {3}, {4:N0} שניות, build {5}" -f $what, (Split-Path $setup -Leaf), $argsLine, $p.ExitCode, ((Get-Date) - $t).TotalSeconds, $b)
    return $p.ExitCode
}

# ------------------------------------------------------------------ הכפתורים של DSViz בסרגל
$script:dsvizNames = @('תרגיל חדש', 'הוספת מבנה נתונים', 'מאגר שאלות', 'השאלה', 'שליחת תרגיל', 'הרצה ויזואלית', 'הדרכה')
function BtnState([int]$vsPid, [string]$n) {
    $els = @(VsByName $vsPid $n | Where-Object { (ElType $_) -match 'Button' })
    if ($els.Count -eq 0) { return 'absent' }
    $vis = @($els | Where-Object { -not $_.Current.IsOffscreen })
    $e = if ($vis.Count) { $vis[0] } else { $els[0] }
    $c = $e.Current
    return ("enabled={0} offscreen={1} w={2}" -f $c.IsEnabled, $c.IsOffscreen, [int]$c.BoundingRectangle.Width)
}
function Usable([string]$state) { return $state -match '^enabled=True offscreen=False' }
function ToolbarState([int]$vsPid, [string[]]$names) {
    $r = [ordered]@{}
    foreach ($n in $names) { $r[$n] = BtnState $vsPid $n }
    return $r
}
function DteToolbar() {
    try {
        $ds = (GetDte).CommandBars.Item('DSViz')
        return ("DTE: DSViz visible={0} row={1} controls={2}" -f $ds.Visible, $ds.RowIndex, $ds.Controls.Count)
    } catch { return "DTE: $($_.Exception.Message)" }
}
# פותח את Visual Studio עם הפתרון ומחכה שהחלון יעלה (מדלג על מסכי ההפעלה הראשונה). מחזיר את התהליך
function StartVs([string]$sln) {
    $vsP = Start-Process $script:devenv -ArgumentList "`"$sln`" /log" -PassThru
    $t0 = Get-Date; $up = $false
    while (((Get-Date) - $t0).TotalMinutes -lt 12) {
        Start-Sleep 5
        $vsP.Refresh()
        if ($vsP.HasExited) { break }
        if (SkipFirstRun $vsP.Id) { Start-Sleep 3 }
        $vsP.Refresh()
        if ($vsP.MainWindowTitle -like '*- Microsoft Visual Studio*') { $up = $true; break }
    }
    Info "Visual Studio עלה=$up · כותרת: $($vsP.MainWindowTitle)"
    return $vsP
}
# מחכה (עד $sec) שכל הכפתורים ב-$want יהיו גלויים ולחיצים; מחזיר את המצב של כל הכפתורים
function WaitButtons($vsP, [string[]]$want, [int]$sec = 180) {
    $t0 = Get-Date; $state = $null
    while (((Get-Date) - $t0).TotalSeconds -lt $sec) {
        SkipFirstRun $vsP.Id | Out-Null
        $state = ToolbarState $vsP.Id @(@($script:dsvizNames) + @($want) | Select-Object -Unique)   # גם כפתורים שאינם ב-dsvizNames (עמדת ניהול)
        if (@($want | Where-Object { Usable $state[$_] }).Count -eq $want.Count) { break }
        Start-Sleep 4
    }
    return $state
}
function ShowState($state) { foreach ($k in $state.Keys) { Info ("   {0}: {1}" -f $k, $state[$k]) } }
function CloseVs($vsP) {
    $vsP.Refresh()
    if (-not $vsP.HasExited) { $vsP.CloseMainWindow() | Out-Null; if (-not $vsP.WaitForExit(90000)) { Info 'Visual Studio לא נסגר — סוגרים בכוח'; Stop-Process -Id $vsP.Id -Force -ErrorAction SilentlyContinue } }
    Start-Sleep 5
    Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
}
# לוחץ על "מאגר שאלות" בסרגל ומחכה לחלון של המאגר (מסך הכניסה, או חלון המאגר למי שכבר נכנס). מחזיר את הכותרת או ''
function ClickBank($vsP, [string]$shot) {
    Get-Process javaw, java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    $els = @(VsByName $vsP.Id 'מאגר שאלות' | Where-Object { (ElType $_) -match 'Button' -and -not $_.Current.IsOffscreen })
    if ($els.Count -eq 0) { Info 'הכפתור "מאגר שאלות" לא נמצא בסרגל'; return '' }
    [VsHelpScreen]::SetForegroundWindow($vsP.MainWindowHandle) | Out-Null
    $how = ClickEl $els[0]
    Info "לחיצה על 'מאגר שאלות' ($how)"
    $title = ''
    for ($i = 0; $i -lt 45 -and -not $title; $i++) {
        Start-Sleep 2
        $w = Get-Process javaw, java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'מאגר' } | Select-Object -First 1
        if ($w) { $title = $w.MainWindowTitle }
    }
    Start-Sleep 3
    Shot $shot
    Info "חלונות: $((Windows) -join ' ; ')"
    Get-Process javaw, java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    return $title
}

# פרויקט Console App (.NET Framework) — כמו הפרויקט "OOP" של המשתמש. מחזיר את קובץ הפתרון
function NewFrameworkProject([string]$name) {
    $root = Join-Path $env:USERPROFILE "source\repos\$name"
    Remove-Item -Recurse -Force $root -ErrorAction SilentlyContinue
    $pd = Join-Path $root $name
    New-Item -ItemType Directory -Force (Join-Path $pd 'Properties') | Out-Null
    $guid = '{5B1E6F0A-3C2D-4E8F-9A7B-1C2D3E4F5A6B}'
    $csproj = @"
<?xml version="1.0" encoding="utf-8"?>
<Project ToolsVersion="15.0" xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
  <Import Project="`$(MSBuildExtensionsPath)\`$(MSBuildToolsVersion)\Microsoft.Common.props" Condition="Exists('`$(MSBuildExtensionsPath)\`$(MSBuildToolsVersion)\Microsoft.Common.props')" />
  <PropertyGroup>
    <Configuration Condition=" '`$(Configuration)' == '' ">Debug</Configuration>
    <Platform Condition=" '`$(Platform)' == '' ">AnyCPU</Platform>
    <ProjectGuid>$guid</ProjectGuid>
    <OutputType>Exe</OutputType>
    <RootNamespace>$name</RootNamespace>
    <AssemblyName>$name</AssemblyName>
    <TargetFrameworkVersion>v4.7.2</TargetFrameworkVersion>
    <FileAlignment>512</FileAlignment>
    <AutoGenerateBindingRedirects>true</AutoGenerateBindingRedirects>
    <Deterministic>true</Deterministic>
  </PropertyGroup>
  <PropertyGroup Condition=" '`$(Configuration)|`$(Platform)' == 'Debug|AnyCPU' ">
    <PlatformTarget>AnyCPU</PlatformTarget>
    <DebugSymbols>true</DebugSymbols>
    <DebugType>full</DebugType>
    <Optimize>false</Optimize>
    <OutputPath>bin\Debug\</OutputPath>
    <DefineConstants>DEBUG;TRACE</DefineConstants>
  </PropertyGroup>
  <PropertyGroup Condition=" '`$(Configuration)|`$(Platform)' == 'Release|AnyCPU' ">
    <PlatformTarget>AnyCPU</PlatformTarget>
    <DebugType>pdbonly</DebugType>
    <Optimize>true</Optimize>
    <OutputPath>bin\Release\</OutputPath>
    <DefineConstants>TRACE</DefineConstants>
  </PropertyGroup>
  <ItemGroup>
    <Reference Include="System" />
    <Reference Include="System.Core" />
  </ItemGroup>
  <ItemGroup>
    <Compile Include="Program.cs" />
    <Compile Include="Properties\AssemblyInfo.cs" />
  </ItemGroup>
  <ItemGroup>
    <None Include="App.config" />
  </ItemGroup>
  <Import Project="`$(MSBuildToolsPath)\Microsoft.CSharp.targets" />
</Project>
"@
    Set-Content (Join-Path $pd "$name.csproj") $csproj -Encoding utf8
    Set-Content (Join-Path $pd 'App.config') @'
<?xml version="1.0" encoding="utf-8" ?>
<configuration>
    <startup>
        <supportedRuntime version="v4.0" sku=".NETFramework,Version=v4.7.2" />
    </startup>
</configuration>
'@ -Encoding utf8
    Set-Content (Join-Path $pd 'Properties\AssemblyInfo.cs') "using System.Reflection;`r`n[assembly: AssemblyTitle(`"$name`")]`r`n[assembly: AssemblyVersion(`"1.0.0.0`")]`r`n" -Encoding utf8
    $prog = @'
using System;

namespace NAME
{
    internal class Program
    {
        static void Main(string[] args)
        {
            int[] arr = new int[] { 2, 4, 2, 1, 7, 4, 6, 5, 3 };
            int sum = 0;
            for (int i = 0; i < arr.Length; i++)
            {
                sum += arr[i];
            }
            Console.WriteLine("sum = " + sum);
        }
    }
}
'@
    Set-Content (Join-Path $pd 'Program.cs') ($prog -replace 'NAME', $name) -Encoding utf8
    $sln = Join-Path $root "$name.sln"
    $t = "`t"
    $slnText = @(
        '', 'Microsoft Visual Studio Solution File, Format Version 12.00', '# Visual Studio Version 17', 'VisualStudioVersion = 17.0.0.0', 'MinimumVisualStudioVersion = 10.0.40219.1',
        "Project(`"{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}`") = `"$name`", `"$name\$name.csproj`", `"$guid`"", 'EndProject', 'Global',
        "${t}GlobalSection(SolutionConfigurationPlatforms) = preSolution", "$t${t}Debug|Any CPU = Debug|Any CPU", "$t${t}Release|Any CPU = Release|Any CPU", "${t}EndGlobalSection",
        "${t}GlobalSection(ProjectConfigurationPlatforms) = postSolution",
        "$t$t$guid.Debug|Any CPU.ActiveCfg = Debug|Any CPU", "$t$t$guid.Debug|Any CPU.Build.0 = Debug|Any CPU",
        "$t$t$guid.Release|Any CPU.ActiveCfg = Release|Any CPU", "$t$t$guid.Release|Any CPU.Build.0 = Release|Any CPU",
        "${t}EndGlobalSection", 'EndGlobal'
    ) -join "`r`n"
    Set-Content $sln $slnText -Encoding utf8
    Info "פרויקט Console App (.NET Framework): $sln"
    return $sln
}
