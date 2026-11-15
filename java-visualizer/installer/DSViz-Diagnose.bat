@echo off
rem DSViz - diagnose: collects what is needed to understand why the Visual Studio toolbar does not show,
rem writes DSViz-diagnose.txt on the Desktop, opens it in Notepad and copies it to the clipboard.
rem The PowerShell part is at the end of this file, after the exit line (cmd never reads past it).
title DSViz diagnose
set "DSVIZ_SELF=%~f0"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=[IO.File]::ReadAllText($env:DSVIZ_SELF,[Text.Encoding]::UTF8); iex ($s.Substring($s.IndexOf('#PS'+'START')))"
if errorlevel 1 pause
exit /b
#PSSTART
# DSViz — דוח אבחון (נשמר ב-UTF-8; cmd לא קורא את החלק הזה)
$ErrorActionPreference = 'SilentlyContinue'
$quiet = [bool]$env:DSVIZ_DIAG_QUIET   # לבדיקות: בלי פנקס רשימות, בלי לוח, בלי חלון — רק כותב את הקובץ ומדפיס את הנתיב
$out = New-Object System.Collections.Generic.List[string]
function Put([string]$s) { $out.Add($s) }
function Section([string]$t) { $out.Add(''); $out.Add('==== ' + $t + ' ====') }
$dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'

Put 'DSViz — דוח אבחון'
Put 'את כל התוכן הזה מדביקים בהודעה ושולחים (הוא כבר הועתק — Ctrl+V).'
Put ('תאריך: ' + (Get-Date -Format 'yyyy-MM-dd HH:mm'))
$os = Get-CimInstance Win32_OperatingSystem
Put ('Windows: ' + $os.Caption + ' ' + $os.Version + ' · PowerShell ' + $PSVersionTable.PSVersion)

Section 'DSViz'
if (Test-Path $dsviz) {
    foreach ($f in 'build.txt', 'lang.txt') {
        $p = Join-Path $dsviz $f
        Put ($f + ': ' + $(if (Test-Path $p) { (Get-Content $p -Raw).Trim() } else { '(אין)' }))
    }
    foreach ($f in 'dsviz.jar', 'update.ps1', 'cs\dsviz-cs.exe', 'cs\DsViz.VsExtension.vsix', 'cs\install-vsix.ps1', 'lab\dsviz-bank-vs.jar', 'bank\dsviz-bank-vs.jar') {
        Put ($f + ': ' + $(if (Test-Path (Join-Path $dsviz $f)) { 'יש' } else { 'אין' }))
    }
} else {
    Put ('DSViz לא מותקן במשתמש הזה (אין ' + $dsviz + ')')
}

Section 'Visual Studio'
$running = @(Get-Process devenv -ErrorAction SilentlyContinue).Count
Put ('Visual Studio פתוח עכשיו: ' + $(if ($running) { 'כן (' + $running + ')' } else { 'לא' }))
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
if (Test-Path $vswhere) {
    $list = @(& $vswhere -all -prerelease -products * -format json | Out-String | ConvertFrom-Json | ForEach-Object { $_ })
    Put ('מופעים של Visual Studio: ' + $list.Count)
    foreach ($i in $list) {
        $major = 0
        try { $major = ([version]$i.installationVersion).Major } catch { }
        $dirs = @((Join-Path $env:LOCALAPPDATA ('Microsoft\VisualStudio\{0}.0_{1}\Extensions' -f $major, $i.instanceId)), (Join-Path ([string]$i.installationPath) 'Common7\IDE\Extensions'))
        $found = ''
        foreach ($d in $dirs) {
            if ($found -or -not (Test-Path $d)) { continue }
            $m = Get-ChildItem $d -Recurse -Filter extension.vsixmanifest -ErrorAction SilentlyContinue |
                Where-Object { (Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue) -match 'DsViz\.VisualStudio\.Toolbar' } | Select-Object -First 1
            if ($m) { $found = $m.Directory.FullName }
        }
        Put ('- {0} {1} [{2}] · {3}' -f $i.displayName, $i.catalog.productDisplayVersion, $i.productId, $i.installationPath)
        Put ('  סרגל הכלים של DSViz: ' + $(if ($found) { 'מותקן · ' + $found } else { 'לא מותקן' }))
    }
} else {
    Put 'לא נמצא Visual Studio (אין vswhere.exe)'
}

Section 'vsix-install.log — 60 השורות האחרונות'
$l = Join-Path $dsviz 'vsix-install.log'
if (Test-Path $l) { Get-Content $l -Encoding UTF8 -Tail 60 | ForEach-Object { Put $_ } } else { Put '(אין קובץ — ההתקנה של הסרגל לא רצה במחשב הזה, או גרסה ישנה)' }

Section 'update.log — 30 השורות האחרונות'
$u = Join-Path $dsviz 'update.log'
if (Test-Path $u) { Get-Content $u -Encoding UTF8 -Tail 30 | ForEach-Object { Put $_ } } else { Put '(אין קובץ)' }

Section 'VSIXInstaller — שורות שגיאה מהיומן האחרון'
$v = Get-ChildItem $env:TEMP -Filter 'dd_VSIXInstaller*.log' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1
if ($v) {
    Put ($v.Name + ' · ' + $v.LastWriteTime)
    Select-String -Path $v.FullName -Pattern 'error|exception|not compatible|blocked|denied|fail' -ErrorAction SilentlyContinue | Select-Object -First 25 | ForEach-Object { Put $_.Line.Trim() }
} else {
    Put '(אין)'
}

# בלי שם המשתמש בנתיבים
$text = $out -join "`r`n"
if ($env:USERPROFILE) { $text = $text.Replace($env:USERPROFILE, '%USERPROFILE%') }
$desk = [Environment]::GetFolderPath('Desktop')
if (-not $desk -or -not (Test-Path $desk)) { $desk = $env:TEMP }
$file = Join-Path $desk 'DSViz-diagnose.txt'
[IO.File]::WriteAllText($file, $text, (New-Object Text.UTF8Encoding($true)))
if ($quiet) { Write-Output $file; return }
try { Set-Clipboard -Value $text } catch { }
Start-Process notepad.exe -ArgumentList ('"' + $file + '"')
try {
    Add-Type -AssemblyName System.Windows.Forms
    $msg = 'הדוח מוכן ונפתח בפנקס הרשימות.' + "`n" + 'הוא כבר הועתק — אפשר להדביק (Ctrl+V) בהודעה ולשלוח.' + "`n`n" + 'הקובץ נשמר גם בשולחן העבודה: DSViz-diagnose.txt'
    [Windows.Forms.MessageBox]::Show($msg, 'DSViz — אבחון', 'OK', 'Information', 'Button1', 'RtlReading, RightAlign') | Out-Null
} catch { }
