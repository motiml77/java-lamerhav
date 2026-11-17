# (UTF-8 עם BOM) מתקין Visual Studio 2026 Community על מחשב ה-CI, ליד ה-Visual Studio 2022 שכבר בו — מה-bootstrapper הרשמי של Microsoft
# (כמו dsviz-vs2026-real.yml), עם עומס העבודה ".NET desktop development" (C#, ‎.NET Framework, ‎.NET SDK). אם כבר יש במחשב Visual Studio
# 2026 (Community / Professional / Enterprise) — לא מתקינים שוב. קוד 3010 = הותקן, נדרשת הפעלה מחדש (לא נדרשת בפועל) — נחשב הצלחה.
# ל-<Out>: יומני ההתקנה (dd_*.log), vswhere.json, והמופעים שנמצאו. קוד יציאה 1 — אין Visual Studio 2026 אחרי ההתקנה.
param([Parameter(Mandatory = $true)][string]$Out)
$Major = 18
$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
New-Item -ItemType Directory -Force $Out | Out-Null
$Out = (Resolve-Path $Out).Path
$vw = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
function Instances() {
    if (-not (Test-Path $vw)) { return @() }
    return @(& $vw -all -prerelease -products * -format json | Out-String | ConvertFrom-Json | ForEach-Object { $_ })
}
function Have() {
    return @(Instances | Where-Object { ([string]$_.productId -match 'Product\.(Community|Professional|Enterprise)$') -and ([version]$_.installationVersion).Major -eq [int]$Major })
}
function Show() {
    foreach ($i in (Instances)) { Write-Host ("  {0} {1} [{2}] {3} · id={4}" -f $i.displayName, $i.catalog.productDisplayVersion, $i.productId, $i.installationPath, $i.instanceId) }
}
Write-Host 'לפני:'; Show
$d = Get-PSDrive C
Write-Host ("דיסק C: פנוי {0:N1} GB" -f ($d.Free / 1GB))
if ((Have).Count -gt 0) {
    Write-Host "Visual Studio $Major כבר במחשב — לא מתקינים"
} else {
    $urls = @('https://aka.ms/vs/18/stable/vs_community.exe', 'https://aka.ms/vs/stable/vs_community.exe', 'https://aka.ms/vs/18/release/vs_community.exe')
    $boot = Join-Path $env:RUNNER_TEMP "vs_community_$Major.exe"
    $got = $false
    foreach ($u in $urls) {
        for ($try = 1; $try -le 3 -and -not $got; $try++) {
            try {
                Invoke-WebRequest $u -OutFile $boot -UseBasicParsing
                if ((Get-Item $boot).Length -gt 500000) { $got = $true; Write-Host ("bootstrapper: {0} ({1:N0} בתים)" -f $u, (Get-Item $boot).Length) }
            } catch { Write-Host "$u (ניסיון $try): $($_.Exception.Message)"; Start-Sleep 5 }
        }
        if ($got) { break }
    }
    if (-not $got) { Write-Host 'ה-bootstrapper לא ירד'; exit 1 }
    try { Write-Host ("גרסת ה-bootstrapper: " + (Get-Item $boot).VersionInfo.ProductVersion) } catch { }
    # ‎.NET desktop development: C#, ‎.NET Framework 4.7.2 / 4.8, ‎.NET SDK
    $a = @('--add', 'Microsoft.VisualStudio.Workload.ManagedDesktop', '--includeRecommended', '--quiet', '--wait', '--norestart', '--nocache')
    $t0 = Get-Date
    $p = Start-Process $boot -ArgumentList $a -PassThru -Wait
    Write-Host ("vs_community {0}: קוד {1} אחרי {2:N0} דקות" -f $Major, $p.ExitCode, ((Get-Date) - $t0).TotalMinutes)
    if ($p.ExitCode -notin 0, 3010) { Write-Host "ההתקנה החזירה קוד $($p.ExitCode) — בודקים אם Visual Studio בכל זאת הותקן" }
    Get-ChildItem $env:TEMP -Filter 'dd_*.log' -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -ge $t0 } | Sort-Object Length -Descending | Select-Object -First 12 |
        ForEach-Object { Copy-Item $_.FullName -Destination $Out -ErrorAction SilentlyContinue }
}
Write-Host 'אחרי:'; Show
try { & $vw -all -prerelease -products * -format json | Set-Content (Join-Path $Out 'vswhere.json') -Encoding UTF8 } catch { }
$d = Get-PSDrive C
Write-Host ("דיסק C: פנוי {0:N1} GB" -f ($d.Free / 1GB))
$mine = @(Have)
if ($mine.Count -eq 0) { Write-Host "אין Visual Studio $Major אחרי ההתקנה"; exit 1 }
foreach ($i in $mine) {
    $devenv = Join-Path $i.installationPath 'Common7\IDE\devenv.exe'
    Write-Host ("Visual Studio {0}: {1} · devenv {2} · VSIXInstaller {3}" -f $Major, $i.catalog.productDisplayVersion, (Test-Path $devenv), (Test-Path (Join-Path $i.installationPath 'Common7\IDE\VSIXInstaller.exe')))
}
exit 0
