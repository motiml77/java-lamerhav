# "מאגר תרגילים" ב-VS Code האמיתי ב-Windows — כמו תלמיד/ה: VS Code מותקן, ואחריו DSViz-Setup.exe רגיל (/S), שמתקין את התוסף.
#   vscode-win.ps1 -NewSetup <DSViz-Setup.exe> -Out <תיקייה> -Harness <tutorials\harness> [-OldSetup <DSViz-Setup.exe קודם>]
# -OldSetup: קודם ההתקנה הקודמת — רק מתעדים מה קורה בלחיצה (לשחזר תקלה), ואז ההתקנה החדשה מעליה (עדכון) והבדיקה עצמה (vscode-win.js).
param([Parameter(Mandatory = $true)][string]$NewSetup, [Parameter(Mandatory = $true)][string]$Out, [Parameter(Mandatory = $true)][string]$Harness,
      [string]$OldSetup = '')
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$Here = $PSScriptRoot
New-Item -ItemType Directory -Force $Out | Out-Null
function Info([string]$s) { Write-Host "  ..   $s" }

# Edge בלי מסך "ברוכים הבאים" (כדי שרואים את דף הכניסה של Google)
New-Item -Path 'HKLM:\SOFTWARE\Policies\Microsoft\Edge' -Force -ErrorAction SilentlyContinue | Out-Null
Set-ItemProperty -Path 'HKLM:\SOFTWARE\Policies\Microsoft\Edge' -Name 'HideFirstRunExperience' -Value 1 -Type DWord -ErrorAction SilentlyContinue

# VS Code — ההתקנה למשתמש (כמו שמורידים מהאתר)
$vs = Join-Path $env:RUNNER_TEMP 'VSCodeUserSetup.exe'
Invoke-WebRequest 'https://update.code.visualstudio.com/latest/win32-x64-user/stable' -OutFile $vs -UseBasicParsing
Start-Process $vs -ArgumentList '/VERYSILENT', '/NORESTART', '/MERGETASKS=!runcode' -Wait
$code = Join-Path $env:LOCALAPPDATA 'Programs\Microsoft VS Code\bin\code.cmd'
Info "VS Code: $(& $code --version | Select-Object -First 1)"
$ud = Join-Path $env:APPDATA 'Code\User'
New-Item -ItemType Directory -Force $ud | Out-Null
[IO.File]::WriteAllText((Join-Path $ud 'settings.json'), '{ "security.workspace.trust.enabled": false, "workbench.startupEditor": "none", "update.mode": "none", "extensions.autoUpdate": false, "telemetry.telemetryLevel": "off" }', (New-Object Text.UTF8Encoding($false)))

# playwright-core (רק כדי להפעיל את VS Code ולמצוא את הכפתור — הלחיצה עצמה אמיתית, דרך Windows)
$nodeDir = Join-Path $env:RUNNER_TEMP 'pw'
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
& npm install --prefix $nodeDir --no-audit --no-fund playwright-core@1.63.0 2>&1 | Select-Object -Last 2 | ForEach-Object { Info "npm: $_" }
$pw = Join-Path $nodeDir 'node_modules\playwright-core'

# ה-agent שפועל בתוך הכלי (לחיצה על "כניסה עם Google", צילום בכל mark)
$ctl = 'C:\dsagent'
Remove-Item $ctl -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force (Join-Path $ctl 'cls'), (Join-Path $ctl 'seed') | Out-Null
& javac --release 11 -encoding UTF-8 -nowarn -d (Join-Path $ctl 'cls') (Join-Path $Harness 'SwingScript.java') (Join-Path $Here '..\windows-vs-toolbar\ToolbarAgent.java') 2>&1 | ForEach-Object { Info "javac: $_" }
[IO.File]::WriteAllText((Join-Path $ctl 'manifest.txt'), "Premain-Class: ToolbarAgent`r`n", (New-Object Text.UTF8Encoding($false)))
& jar --create --file (Join-Path $ctl 'agent.jar') --manifest (Join-Path $ctl 'manifest.txt') -C (Join-Path $ctl 'cls') . 2>&1 | ForEach-Object { Info "jar: $_" }

function Install([string]$setup, [string]$what) {
    $t0 = Get-Date
    Start-Process $setup -ArgumentList '/S' -Wait
    Info "$what — DSViz-Setup.exe /S: $([int]((Get-Date) - $t0).TotalSeconds) שניות"
    Info "התוספים ב-VS Code: $((& $code --list-extensions --show-versions) -join ', ')"
    $dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'
    Info "מאגר התרגילים: $(Test-Path (Join-Path $dsviz 'bank\dsviz-bank-vs.jar')) · Java: $(Test-Path (Join-Path $dsviz 'jdk\bin\javaw.exe'))"
    & javac --release 11 -encoding UTF-8 -nowarn -cp "$dsviz\bank\dsviz-bank-vs.jar;$dsviz\dsviz.jar" -d (Join-Path $ctl 'seed') (Join-Path $Here '..\windows-vs-toolbar\SeedSession.java') 2>&1 | ForEach-Object { Info "javac SeedSession: $_" }
}

$rc = 0
if ($OldSetup -and (Test-Path $OldSetup)) {
    Write-Host "== ההתקנה הקודמת: מה קורה בלחיצה על 'מאגר תרגילים'"
    Install $OldSetup 'ההתקנה הקודמת'
    & node (Join-Path $Here 'vscode-win.js') before $Out $pw
}
Write-Host "== ההתקנה החדשה (עדכון מעל): הבדיקה"
Install $NewSetup 'ההתקנה החדשה'
& node (Join-Path $Here 'vscode-win.js') after $Out $pw
$rc = $LASTEXITCODE
Copy-Item (Join-Path $ctl 'agent.log') $Out -ErrorAction SilentlyContinue
Copy-Item (Join-Path $env:TEMP 'dsviz-bank-vscode.log') $Out -ErrorAction SilentlyContinue
if ($env:GITHUB_STEP_SUMMARY) {
    foreach ($f in 'summary-before.txt', 'summary-after.txt') {
        $p = Join-Path $Out $f
        if (Test-Path $p) { ("``````" + "`n" + (Get-Content $p -Raw -Encoding UTF8) + "`n" + "``````") | Add-Content $env:GITHUB_STEP_SUMMARY -Encoding utf8 }
    }
}
exit $rc
