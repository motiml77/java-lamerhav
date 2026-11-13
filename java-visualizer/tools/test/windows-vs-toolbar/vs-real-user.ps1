# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# Visual Studio כמו אצל משתמש אמיתי: מתקינים את DSViz, פותחים פרויקט Console App (.NET Framework) — ובודקים אם סרגל הכלים של DSViz מופיע,
# גלוי ולחיץ. בלי devenv /updateconfiguration ובלי התקנה ידנית של ההרחבה (משתמש לא עושה את זה) — שלוש פתיחות:
#   1. הפתיחה הראשונה אחרי ההתקנה   2. פתיחה שנייה   3. אחרי devenv /updateconfiguration (רק לאבחון: אם רק כאן הסרגל מופיע — זו הסיבה)
# אוספים: VSIXInstaller, ActivityLog.xml, יומן ההתקנה של ההרחבה (vsix-install.log), vs-trace.log, ההרחבות שמופעלות ב-Visual Studio.
#   vs-real-user.ps1 -Setup DSViz-Setup.exe -Out <תיקייה> [-Product Microsoft.VisualStudio.Product.Community] [-SetupArgs '/S']
param([string]$Setup, [string]$Out, [string]$Product = 'Microsoft.VisualStudio.Product.Community', [string]$SetupArgs = '/S')
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
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class RealScreen {
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
}
'@
foreach ($res in @(@(1920, 1080), @(1680, 1050), @(1600, 900))) { if ([RealScreen]::Set($res[0], $res[1]) -eq 0) { break } }
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

# ------------------------------------------------------------------ 0. Visual Studio במחשב
Log "== 0. Visual Studio במחשב"
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$all = @(& $vswhere -all -prerelease -products * -format json | Out-String | ConvertFrom-Json | ForEach-Object { $_ })   # PowerShell 5.1: המערך מגיע כאובייקט אחד
Info "מופעים של Visual Studio: $($all.Count) (עם יותר מאחד — כמו אצל רוב המורים: 2022 + 2026, Build Tools, SSMS)"
foreach ($i in $all) { Info ("{0} {1} [{2}] {3} · id={4}" -f $i.displayName, $i.catalog.productDisplayVersion, $i.productId, $i.installationPath, $i.instanceId) }
$inst = $all | Where-Object { $_.productId -eq $Product } | Sort-Object { [version]$_.installationVersion } | Select-Object -Last 1
if (-not $inst) { $inst = $all | Sort-Object { [version]$_.installationVersion } | Select-Object -Last 1 }
$devenv = Join-Path $inst.installationPath 'Common7\IDE\devenv.exe'
$verMajor = ([version]$inst.installationVersion).Major
$hiveDir = Join-Path $env:LOCALAPPDATA ("Microsoft\VisualStudio\{0}.0_{1}" -f $verMajor, $inst.instanceId)
Check 'env-vs' (Test-Path $devenv) ("נבדק: {0} {1}" -f $inst.displayName, $inst.catalog.productDisplayVersion) $devenv
Info "תיקיית המשתמש של Visual Studio: $hiveDir"

# ------------------------------------------------------------------ 1. התקנה (Visual Studio סגור, כמו שההתקנה דורשת)
Log "== 1. התקנה: $Setup $SetupArgs"
Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
$t = Get-Date
$p = Start-Process $Setup -ArgumentList $SetupArgs -PassThru -Wait
Info ("ההתקנה: קוד {0}, {1:N0} שניות" -f $p.ExitCode, ((Get-Date) - $t).TotalSeconds)
$dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'
foreach ($f in 'build.txt', 'lang.txt') { $pf = Join-Path $dsviz $f; Info ("{0} = {1}" -f $f, $(if (Test-Path $pf) { (Get-Content $pf -Raw).Trim() } else { '(אין)' })) }
Check 'install' ($p.ExitCode -eq 0 -and (Test-Path (Join-Path $dsviz 'dsviz.jar'))) 'DSViz הותקן'
foreach ($f in 'lab\dsviz-bank-vs.jar', 'bank\dsviz-bank-vs.jar', 'cs\DsViz.VsExtension.vsix', 'vsix-install.log') { Info ("{0}: {1}" -f $f, (Test-Path (Join-Path $dsviz $f))) }
if (Test-Path (Join-Path $dsviz 'vsix-install.log')) { Copy-Item (Join-Path $dsviz 'vsix-install.log') $Out }
function ExtManifests() {
    return @(Get-ChildItem "$env:LOCALAPPDATA\Microsoft\VisualStudio", "$env:ProgramData\Microsoft\VisualStudio", (Join-Path $inst.installationPath 'Common7\IDE\Extensions') -Recurse -Filter extension.vsixmanifest -ErrorAction SilentlyContinue |
        Where-Object { (Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue) -match 'DsViz.VisualStudio.Toolbar' })
}
$man = ExtManifests
foreach ($m in $man) { Info "ההרחבה: $($m.FullName)" }
Check 'vsix-in-instance' (@($man | Where-Object { $_.FullName -like "$hiveDir*" -or $_.FullName -like "$($inst.installationPath)*" }).Count -gt 0) "ההרחבה הותקנה ב-$($inst.displayName)" "$(@($man).Count) עותקים: $(($man | ForEach-Object { $_.Directory.FullName }) -join ' ; ')"
$vl = Join-Path $Out 'vsixinstaller'
New-Item -ItemType Directory -Force $vl | Out-Null
Get-ChildItem $env:TEMP -Filter 'dd_VSIXInstaller*.log' -ErrorAction SilentlyContinue | ForEach-Object { Copy-Item $_.FullName $vl -ErrorAction SilentlyContinue }
Info "יומני VSIXInstaller: $(@(Get-ChildItem $vl).Count)"
foreach ($l in (Get-ChildItem $vl -ErrorAction SilentlyContinue)) {
    Select-String -Path $l.FullName -Pattern 'error|fail|exception|not compatible|blocking|already installed|Install to|Installed' -ErrorAction SilentlyContinue | Select-Object -First 12 | ForEach-Object { Info ("   {0}: {1}" -f $l.Name, $_.Line.Trim()) }
}

# ------------------------------------------------------------------ 2. פרויקט Console App (.NET Framework) — כמו "OOP" של המשתמש
$root = Join-Path $env:USERPROFILE 'source\repos\OOP'
Remove-Item -Recurse -Force $root -ErrorAction SilentlyContinue
$pd = Join-Path $root 'OOP'
New-Item -ItemType Directory -Force (Join-Path $pd 'Properties') | Out-Null
$guid = '{5B1E6F0A-3C2D-4E8F-9A7B-1C2D3E4F5A6B}'
@"
<?xml version="1.0" encoding="utf-8"?>
<Project ToolsVersion="15.0" xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
  <Import Project="`$(MSBuildExtensionsPath)\`$(MSBuildToolsVersion)\Microsoft.Common.props" Condition="Exists('`$(MSBuildExtensionsPath)\`$(MSBuildToolsVersion)\Microsoft.Common.props')" />
  <PropertyGroup>
    <Configuration Condition=" '`$(Configuration)' == '' ">Debug</Configuration>
    <Platform Condition=" '`$(Platform)' == '' ">AnyCPU</Platform>
    <ProjectGuid>$guid</ProjectGuid>
    <OutputType>Exe</OutputType>
    <RootNamespace>OOP</RootNamespace>
    <AssemblyName>OOP</AssemblyName>
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
"@ | Set-Content (Join-Path $pd 'OOP.csproj') -Encoding utf8
@'
<?xml version="1.0" encoding="utf-8" ?>
<configuration>
    <startup>
        <supportedRuntime version="v4.0" sku=".NETFramework,Version=v4.7.2" />
    </startup>
</configuration>
'@ | Set-Content (Join-Path $pd 'App.config') -Encoding utf8
@'
using System.Reflection;
[assembly: AssemblyTitle("OOP")]
[assembly: AssemblyVersion("1.0.0.0")]
'@ | Set-Content (Join-Path $pd 'Properties\AssemblyInfo.cs') -Encoding utf8
@'
using System;

namespace OOP
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
'@ | Set-Content (Join-Path $pd 'Program.cs') -Encoding utf8
$sln = Join-Path $root 'OOP.sln'
@"
Microsoft Visual Studio Solution File, Format Version 12.00
# Visual Studio Version 18
VisualStudioVersion = 18.0.0.0
MinimumVisualStudioVersion = 10.0.40219.1
Project("{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}") = "OOP", "OOP\OOP.csproj", "$guid"
EndProject
Global
	GlobalSection(SolutionConfigurationPlatforms) = preSolution
		Debug|Any CPU = Debug|Any CPU
		Release|Any CPU = Release|Any CPU
	EndGlobalSection
	GlobalSection(ProjectConfigurationPlatforms) = postSolution
		$guid.Debug|Any CPU.ActiveCfg = Debug|Any CPU
		$guid.Debug|Any CPU.Build.0 = Debug|Any CPU
		$guid.Release|Any CPU.ActiveCfg = Release|Any CPU
		$guid.Release|Any CPU.Build.0 = Release|Any CPU
	EndGlobalSection
EndGlobal
"@ | Set-Content $sln -Encoding utf8
Info "פרויקט .NET Framework: $sln"

# ------------------------------------------------------------------ 3. פתיחות של Visual Studio
$names = @('תרגיל חדש', 'הוספת מבנה נתונים', 'מאגר שאלות', 'השאלה', 'שליחת תרגיל', 'הרצה ויזואלית', 'הדרכה')
function ToolbarState([int]$vsPid) {
    $r = [ordered]@{}
    foreach ($n in $names) {
        $els = @(VsByName $vsPid $n | Where-Object { (ElType $_) -match 'Button' })
        if ($els.Count -eq 0) { $r[$n] = 'absent'; continue }
        $c = $els[0].Current
        $r[$n] = ("enabled={0} offscreen={1} w={2}" -f $c.IsEnabled, $c.IsOffscreen, [int]$c.BoundingRectangle.Width)
    }
    return $r
}
function DteToolbar() {
    try {
        $bars = (GetDte).CommandBars
        $ds = $bars.Item('DSViz')
        return ("DSViz: visible={0} row={1} position={2} controls={3}" -f $ds.Visible, $ds.RowIndex, $ds.Position, $ds.Controls.Count)
    } catch { return "DTE: $($_.Exception.Message)" }
}
function OpenVs([string]$tag) {
    Log "== פתיחה: $tag"
    $vsP = Start-Process $devenv -ArgumentList "`"$sln`" /log" -PassThru
    $t0 = Get-Date; $up = $false
    while (((Get-Date) - $t0).TotalMinutes -lt 12) {
        Start-Sleep 5
        $vsP.Refresh()
        if ($vsP.HasExited) { break }
        if (SkipFirstRun $vsP.Id) { Start-Sleep 3 }
        $vsP.Refresh()
        if ($vsP.MainWindowTitle -match 'OOP|Microsoft Visual Studio') { $up = $true; break }
    }
    Info "Visual Studio עלה=$up · כותרת: $($vsP.MainWindowTitle)"
    $state = $null; $ok = $false
    for ($i = 0; $i -lt 45; $i++) {   # עד 3 דקות: ההרחבה נטענת ברקע אחרי שהחלון עולה
        SkipFirstRun $vsP.Id | Out-Null
        $state = ToolbarState $vsP.Id
        $ok = @($names | Where-Object { $state[$_] -match '^enabled=True offscreen=False' }).Count -eq $names.Count
        if ($ok) { break }
        if ($i -eq 6) { Shot "$tag-30s" }
        Start-Sleep 4
    }
    Start-Sleep 3
    Shot "$tag-toolbar"
    foreach ($n in $names) { Info ("   {0}: {1}" -f $n, $state[$n]) }
    Info (DteToolbar)
    $rel = @(VsElements $vsP.Id | ForEach-Object { ElName $_ } | Where-Object { $_ -match 'DSViz|מאגר|תרגיל|הרצה ויזואלית|הדרכה|מבנה נתונים|עמדת ניהול' } | Select-Object -Unique)
    Info "רכיבים של DSViz (UIA): $($rel -join ' ; ')"
    Check "toolbar-$tag" $ok "$tag — סרגל הכלים של DSViz מופיע, וכל הכפתורים גלויים ולחיצים"
    $vsP.Refresh()
    if (-not $vsP.HasExited) { $vsP.CloseMainWindow() | Out-Null; if (-not $vsP.WaitForExit(90000)) { Info 'לא נסגר — סוגרים בכוח'; Stop-Process -Id $vsP.Id -Force -ErrorAction SilentlyContinue } }
    Start-Sleep 5
    Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    return $ok
}
$env:DSVIZ_VS_TRACE = '1'   # יומן ההרחבה (vs-trace.log) — רק בבדיקה
$r1 = OpenVs 'first'
$r2 = OpenVs 'second'
Log "== devenv /updateconfiguration (לאבחון בלבד)"
Start-Process $devenv -ArgumentList '/updateconfiguration' -Wait -WindowStyle Hidden
$r3 = OpenVs 'after-updateconfiguration'
Info ("סיכום: פתיחה ראשונה={0}, שנייה={1}, אחרי updateconfiguration={2}" -f $r1, $r2, $r3)

# ------------------------------------------------------------------ 4. יומנים
Get-ChildItem "$env:APPDATA\Microsoft\VisualStudio" -Recurse -Filter ActivityLog.xml -ErrorAction SilentlyContinue | ForEach-Object {
    Copy-Item $_.FullName (Join-Path $Out ('ActivityLog-' + $_.Directory.Name + '.xml')) -ErrorAction SilentlyContinue
    Select-String -Path $_.FullName -Pattern 'DsViz|DSViz|7b2f4d1e' -ErrorAction SilentlyContinue | Select-Object -First 25 | ForEach-Object { Info ("   ActivityLog: " + ($_.Line.Trim() -replace '\s+', ' ').Substring(0, [Math]::Min(300, ($_.Line.Trim() -replace '\s+', ' ').Length))) }
}
foreach ($f in 'vs-trace.log', 'vsix-install.log', 'update.log') { $pf = Join-Path $dsviz $f; if (Test-Path $pf) { Copy-Item $pf $Out } }
# ההרחבות שמופעלות (privateregistry.bin של Visual Studio, אחרי שנסגר)
$hive = Join-Path $hiveDir 'privateregistry.bin'
if (Test-Path $hive) {
    & reg.exe load 'HKU\DSVizVsHive' $hive 2>&1 | Out-Null
    $q = & reg.exe query ("HKU\DSVizVsHive\Software\Microsoft\VisualStudio\{0}.0_{1}\ExtensionManager\EnabledExtensions" -f $verMajor, $inst.instanceId) 2>&1
    Info "EnabledExtensions: $((@($q) | Where-Object { $_ -match 'DsViz' }) -join ' | ')"
    $q2 = & reg.exe query ("HKU\DSVizVsHive\Software\Microsoft\VisualStudio\{0}.0_{1}\ExtensionManager\PendingDeletions" -f $verMajor, $inst.instanceId) 2>&1
    Info "PendingDeletions: $((@($q2) | Where-Object { $_ -match 'DsViz' }) -join ' | ')"
    [gc]::Collect(); Start-Sleep 1
    & reg.exe unload 'HKU\DSVizVsHive' 2>&1 | Out-Null
} else { Info "אין privateregistry.bin ב-$hiveDir" }
Log ("SUMMARY {0} FAIL — {1} {2}" -f $script:fails, $inst.displayName, $inst.catalog.productDisplayVersion)
Save-Summary
exit 0
