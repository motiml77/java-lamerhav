# (UTF-8 עם BOM) בונה את ההרחבה של DSViz ל-Visual Studio (csharp/vs-extension) ב-MSBuild של Visual Studio, ובודק את הקובץ שנוצר:
# המניפסט (מזהה, מספר הגרסה של הבנייה, טווחי הגרסאות [17.0,) amd64 ו-[17.4,) arm64), שה-DLL מפנה ל-Shell.15.0 17.0.0.0 (ה-SDK של 17.0 —
# עובד גם ב-17.x ישן וב-2026), שהסרגל (Menus.ctmenu) בתוך ה-DLL וב-pkgdef, ושגרסת ה-DLL היא גרסת ה-VSIX.
# אם MSBuild לא יצר VSIX — אורזים ידנית (pack-vsix.ps1) מה-DLL וה-pkgdef שנבנו, עם אותו מספר גרסה.
# פלט: <Out>\DsViz.VsExtension.vsix, <Out>\vsix-build.txt, יומן MSBuild. קוד יציאה 0 — תקין; 1 — לא.
param([Parameter(Mandatory = $true)][string]$Out, [string]$Version = '')
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
New-Item -ItemType Directory -Force $Out | Out-Null
$Out = (Resolve-Path $Out).Path
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$projDir = Join-Path $root 'csharp\vs-extension'
$proj = Join-Path $projDir 'DsViz.VsExtension.csproj'
$report = New-Object System.Collections.Generic.List[string]
function Say([string]$s) { Write-Host $s; $report.Add($s) }
function Done([int]$code) { $report | Set-Content (Join-Path $Out 'vsix-build.txt') -Encoding utf8; exit $code }
if (-not $Version) { $u = [DateTime]::UtcNow; $Version = '1.0.{0}{1:000}.{2}' -f $u.ToString('yy'), $u.DayOfYear, ($u.Hour * 60 + $u.Minute) }
Say "גרסת ההרחבה: $Version"

$vw = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$msb = & $vw -latest -prerelease -products * -requires Microsoft.Component.MSBuild -find 'MSBuild\**\Bin\MSBuild.exe' | Select-Object -First 1
if (-not $msb) { Say 'לא נמצא MSBuild של Visual Studio'; Done 1 }
Say "MSBuild: $msb"
$log = Join-Path $Out 'msbuild-vsix.log'
& $msb $proj '/restore' '/p:Configuration=Release' "/p:VsixVersion=$Version" '/v:m' '/nologo' '/fl' "/flp:logfile=$log;verbosity=normal;encoding=UTF-8" 2>&1 | ForEach-Object { Write-Host "  $_" }
$code = $LASTEXITCODE
$bin = Join-Path $projDir 'bin\Release\net472'
$vsix = Join-Path $bin 'DsViz.VsExtension.vsix'
if (-not (Test-Path $vsix)) {
    Say "msbuild (קוד $code) לא יצר VSIX — אורזים ידנית"
    try { & (Join-Path $PSScriptRoot 'pack-vsix.ps1') -Bin $bin -SourceManifest (Join-Path $projDir 'source.extension.vsixmanifest') -OutFile $vsix -Version $Version | Out-Null }
    catch { Say "אריזה ידנית נכשלה: $($_.Exception.Message)" }
}
$bad = @()
if (-not (Test-Path $vsix)) { $bad += "אין קובץ $vsix" }
else {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $z = [IO.Compression.ZipFile]::OpenRead($vsix)
    try {
        $names = @($z.Entries | ForEach-Object { $_.FullName })
        Say ("{0}: {1:N0} בתים · {2}" -f (Split-Path -Leaf $vsix), (Get-Item $vsix).Length, ($names -join ', '))
        foreach ($need in 'extension.vsixmanifest', 'DsViz.VsExtension.dll', 'DsViz.VsExtension.pkgdef') { if ($names -notcontains $need) { $bad += "חסר בקובץ: $need" } }
        $m = ''
        $e = $z.Entries | Where-Object { $_.FullName -eq 'extension.vsixmanifest' } | Select-Object -First 1
        if ($e) { $rd = New-Object IO.StreamReader($e.Open(), [Text.Encoding]::UTF8); $m = $rd.ReadToEnd(); $rd.Close() }
        if ($m -notmatch 'Identity Id="DsViz\.VisualStudio\.Toolbar\.6f3c1c0e-4a52-4e7e-9d1b-5b2f0c8e7a11"') { $bad += 'המזהה (Identity Id) במניפסט לא כצפוי' }
        if ($m -notmatch ('<Identity\b[^>]*\bVersion="' + [regex]::Escape($Version) + '"')) { $bad += "הגרסה במניפסט אינה $Version" }
        $ranges = @([regex]::Matches($m, '<InstallationTarget [^>]*Version="([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
        Say ("  מניפסט: גרסה {0} · טווחים: {1}" -f ([regex]::Match($m, '<Identity\b[^>]*\bVersion="([^"]+)"').Groups[1].Value), ($ranges -join ' '))
        if (($ranges -join ' ') -ne '[17.0,) [17.4,)') { $bad += "טווחי הגרסאות צריכים להיות [17.0,) ו-[17.4,): $($ranges -join ' ')" }
        if ($m -match '\|%CurrentProject%') { $bad += 'במניפסט נשארו סימני |%CurrentProject%| (לא הוחלפו בנתיבים)' }
        $p = ''
        $e = $z.Entries | Where-Object { $_.FullName -eq 'DsViz.VsExtension.pkgdef' } | Select-Object -First 1
        if ($e) { $rd = New-Object IO.StreamReader($e.Open(), $true); $p = $rd.ReadToEnd(); $rd.Close() }
        if ($p -notmatch 'Menus\.ctmenu') { $bad += 'ב-pkgdef אין רישום של Menus.ctmenu (בלי זה אין סרגל)' }
        if ($p -notmatch '7b2f4d1e-9c3a-4e68-b0d5-1a8c6f3e2d97') { $bad += 'ב-pkgdef אין את החבילה DsVizPackage' }
        $e = $z.Entries | Where-Object { $_.FullName -eq 'DsViz.VsExtension.dll' } | Select-Object -First 1
        if ($e) {
            # ה-DLL נקרא בתהליך PowerShell נפרד (ReflectionOnlyLoadFrom נועל את הקובץ ונשאר בתהליך)
            $tmp = Join-Path $env:TEMP ('dsviz-vsix-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
            New-Item -ItemType Directory -Force $tmp | Out-Null
            $dll = Join-Path $tmp 'DsViz.VsExtension.dll'
            $fs = [IO.File]::Create($dll); $st = $e.Open(); $st.CopyTo($fs); $st.Close(); $fs.Close()
            $probe = Join-Path $tmp 'probe.ps1'
            [IO.File]::WriteAllText($probe, @'
param([string]$Dll)
$a = [Reflection.Assembly]::ReflectionOnlyLoadFrom($Dll)
"VERSION " + $a.GetName().Version
foreach ($r in $a.GetReferencedAssemblies()) { "REF " + $r.Name + " " + $r.Version }
foreach ($n in $a.GetManifestResourceNames()) { "RES " + $n }
'@, (New-Object Text.UTF8Encoding($false)))
            $lines = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $probe $dll 2>&1 | ForEach-Object { "$_" })
            Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
            $dllVer = (@($lines | Where-Object { $_ -like 'VERSION *' }) + @('VERSION ?'))[0].Substring(8)
            $refs = @($lines | Where-Object { $_ -like 'REF *' } | ForEach-Object { $_.Substring(4) })
            $res = @($lines | Where-Object { $_ -like 'RES *' } | ForEach-Object { $_.Substring(4) })
            if ($refs.Count -eq 0) { $bad += "אי אפשר לקרוא את ה-DLL: $($lines -join ' | ')" }
            else {
                Say ("  DLL {0} · מפנה ל: {1}" -f $dllVer, ((@($refs | Where-Object { $_ -match '^(Microsoft\.VisualStudio|EnvDTE)' })) -join ' · '))
                $shell = @($refs | Where-Object { $_ -like 'Microsoft.VisualStudio.Shell.15.0 *' })
                if ($shell.Count -eq 0 -or $shell[0] -ne 'Microsoft.VisualStudio.Shell.15.0 17.0.0.0') { $bad += "ה-DLL צריך להפנות ל-Microsoft.VisualStudio.Shell.15.0 17.0.0.0 (מפנה ל: $($shell -join ', '))" }
                if ($dllVer -ne $Version) { $bad += "גרסת ה-DLL ($dllVer) אינה $Version" }
                if ($res -notcontains 'VSPackage.resources') { $bad += 'ב-DLL אין המשאב VSPackage.resources (הסרגל — Menus.ctmenu)' }
            }
        }
    } finally { $z.Dispose() }
}
foreach ($b in $bad) { Say "FAIL $b" }
if ($bad.Count -gt 0) { Done 1 }
Copy-Item $vsix (Join-Path $Out 'DsViz.VsExtension.vsix') -Force
Say "ok   DsViz.VsExtension.vsix $Version"
Done 0
