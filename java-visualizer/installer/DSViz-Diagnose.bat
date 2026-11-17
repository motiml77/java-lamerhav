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
        $fits = ([string]$i.productId -match '^Microsoft\.VisualStudio\.Product\.(Community|Professional|Enterprise)$') -and $major -ge 17
        if (-not $fits) { Put '  סרגל הכלים של DSViz: לא מתאים למוצר הזה (רק Visual Studio Community / Professional / Enterprise, מ-2022)'; continue }
        $ver = ''
        if ($found) { try { if ((Get-Content (Join-Path $found 'extension.vsixmanifest') -Raw) -match '<Identity\b[^>]*\bVersion="([^"]+)"') { $ver = $Matches[1] } } catch { } }
        Put ('  סרגל הכלים של DSViz: ' + $(if ($found) { 'מותקן · גרסה ' + $ver + ' · ' + $found } else { 'לא מותקן' }))
        # הסימן שההרחבה כותבת בכל פעם שהיא נטענת ב-Visual Studio הזה (vs-loaded-<גרסה>_<מופע>.txt): מותקנת זה עוד לא אומר שהיא נטענת
        $mark = Join-Path $dsviz ('vs-loaded-{0}.0_{1}.txt' -f $major, $i.instanceId)
        if (Test-Path $mark) {
            $kv = @{}; foreach ($ln in (Get-Content $mark -Encoding UTF8)) { $k = $ln.IndexOf('='); if ($k -gt 0) { $kv[$ln.Substring(0, $k)] = $ln.Substring($k + 1) } }
            Put ('  ההרחבה נטענה לאחרונה: ' + $kv['time'] + ' · גרסה ' + $kv['build'] + ' · Visual Studio ' + $kv['vs'])
            if ($found -and $ver -and $kv['build'] -and $ver -ne $kv['build']) { Put ('  *** הגרסה שנטענה (' + $kv['build'] + ') אינה הגרסה שמותקנת עכשיו (' + $ver + ') — סוגרים את Visual Studio לגמרי ופותחים מחדש ***') }
        } elseif ($found) {
            Put '  *** ההרחבה מותקנת, אבל אף פעם לא נטענה ב-Visual Studio הזה (אין vs-loaded). אם Visual Studio נפתח מאז ההתקנה: היא מושבתת או נכשלה בטעינה —'
            Put '      Extensions ← Manage Extensions ← Installed ← "DSViz - Data Structures" ← Enable, ואז לסגור ולפתוח את Visual Studio (גרסה ישנה של DSViz לא כתבה את הסימן) ***'
        }
    }
    $loaded = @(Get-ChildItem $dsviz -Filter 'vs-loaded-*.txt' -ErrorAction SilentlyContinue | ForEach-Object { $_.Name })
    if ($loaded.Count) { Put ('קובצי "ההרחבה נטענה": ' + ($loaded -join ', ')) }
    # הלחצן האדום "כיבוי DSViz" בסרגל (Power.cs בהרחבה): כבוי — נשארים רק הלחצן הירוק "הפעלת DSViz" ו-Visual Studio עצמו
    if (Test-Path (Join-Path $dsviz 'vs-power-off.flag')) { Put '*** DSViz כבוי ב-Visual Studio (נלחץ "כיבוי DSViz"): בסרגל נשאר רק הלחצן הירוק "הפעלת DSViz" — לוחצים עליו כדי להחזיר הכול ***' }
} else {
    Put 'לא נמצא Visual Studio (אין vswhere.exe)'
}

# האם Visual Studio נפתח לפני שהתוסף הותקן? תוסף חדש נטען רק אחרי שסוגרים ופותחים מחדש — עד אז Visual Studio משתמש בגרסה הישנה שבזיכרון
$vl = Join-Path $dsviz 'vsix-install.log'
$lastInstall = $null
if (Test-Path $vl) {
    foreach ($line in (Get-Content $vl -Encoding UTF8)) {
        if ($line -match '^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s+.*התקנה \(ניסיון \d+\): קוד (0|timeout)\b') { $lastInstall = [datetime]::ParseExact($Matches[1], 'yyyy-MM-dd HH:mm:ss', $null) }
    }
}
foreach ($p in @(Get-Process devenv -ErrorAction SilentlyContinue)) {
    $st = $null; try { $st = $p.StartTime } catch { }
    if ($st) {
        Put ('Visual Studio (תהליך ' + $p.Id + ') נפתח ב-' + $st.ToString('yyyy-MM-dd HH:mm:ss') + $(if ($lastInstall) { ' · התוסף הותקן לאחרונה ב-' + $lastInstall.ToString('yyyy-MM-dd HH:mm:ss') } else { '' }))
        if ($lastInstall -and $st -lt $lastInstall) { Put '*** אזהרה: Visual Studio נפתח לפני שהתוסף הותקן — הוא עדיין משתמש בגרסה הישנה. צריך לסגור את Visual Studio לגמרי ולפתוח מחדש ***' }
    }
}

Section 'תיקיית התרגילים של C# (מאגר התרגילים / תרגיל חדש)'
Put ('מסמכים (Documents): ' + [Environment]::GetFolderPath('MyDocuments'))
$pcp = Join-Path $env:USERPROFILE '.dsviz\project-cs-path.txt'
$csRoot = $null
if (Test-Path $pcp) { $csRoot = ([IO.File]::ReadAllText($pcp)).Trim([char]0xFEFF, ' ', "`r", "`n"); Put ('project-cs-path.txt: ' + $csRoot) } else { Put 'project-cs-path.txt: אין (חבילת C# לא הותקנה במשתמש הזה)' }
if (-not $csRoot) { $csRoot = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'DataStructures-CS' }
foreach ($f in 'DataStructures-CS.sln', 'lib\Unit4.CollectionsLib.dll', 'Directory.Build.props') { Put ('  ' + $f + ': ' + $(if (Test-Path (Join-Path $csRoot $f)) { 'יש' } else { '*** אין ***' })) }
Put ('התבנית בהתקנה (cs-project\lib): ' + $(if (Test-Path (Join-Path $dsviz 'cs-project\lib\Unit4.CollectionsLib.dll')) { 'יש — מאגר התרגילים משלים ממנה לבד את מה שחסר' } else { 'אין' }))

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
$kept = @(Get-ChildItem (Join-Path $dsviz 'logs') -Filter 'vsix-*.log' -ErrorAction SilentlyContinue)
foreach ($k in $kept) { Put ('היומן המלא של ההתקנה האחרונה במופע: logs\' + $k.Name + ' · ' + $k.LastWriteTime.ToString('yyyy-MM-dd HH:mm') + ' · ' + $k.Length + ' בתים') }

Section 'כניסת מורה / עמדה — למה מתבקש קוד שוב'
Put ('שם המחשב: ' + $env:COMPUTERNAME + ' · משתמש Windows: ' + $env:USERNAME)
Put ('שעון המחשב: ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' · אזור זמן: ' + (Get-TimeZone).Id + '   (שעון שגוי גורם ל"האישור השמור לא אומת")')
$places = [ordered]@{
    'משתמש (LOCALAPPDATA\Lamerhav\.cache)'  = (Join-Path $env:LOCALAPPDATA 'Lamerhav\.cache')
    'גיבוי (APPDATA\Lamerhav\.cache)'         = (Join-Path $env:APPDATA 'Lamerhav\.cache')
    'כל המחשב (ProgramData\Lamerhav\.cache)' = (Join-Path $env:ProgramData 'Lamerhav\.cache')
    'ישן (.dsviz)'                              = (Join-Path $env:USERPROFILE '.dsviz')
    'ישן (ProgramData\DSViz)'                  = (Join-Path $env:ProgramData 'DSViz')
}
foreach ($k in $places.Keys) {
    $d = $places[$k]
    if (-not (Test-Path $d)) { Put ('- ' + $k + ': אין תיקייה'); continue }
    $files = @(Get-ChildItem $d -Force -File -ErrorAction SilentlyContinue)
    Put ('- ' + $k + ': ' + $files.Count + ' קבצים')
    foreach ($f in $files | Where-Object { $_.Name -match '^(i|s|a|d)\.dat$|^station|^account|^install-id|^machine-id' }) { Put ('    ' + $f.Name + ' · ' + $f.Length + ' בתים · ' + $f.LastWriteTime.ToString('yyyy-MM-dd HH:mm')) }
}
# ProgramData: האם המשתמש הנוכחי יכול לכתוב שם (אם לא — הכניסה נשמרת רק אצל המשתמש, ונעלמת עם פרופיל זמני)
$pd = Join-Path $env:ProgramData 'Lamerhav\.cache'
if (Test-Path $pd) {
    try { $t = Join-Path $pd ('w' + [guid]::NewGuid().ToString('N').Substring(0, 6) + '.tmp'); [IO.File]::WriteAllText($t, 'x'); Remove-Item $t -Force; Put 'כתיבה ל-ProgramData: מותרת למשתמש הזה' }
    catch { Put 'כתיבה ל-ProgramData: אסורה למשתמש הזה (הכניסה נשמרת רק בתיקיות של המשתמש)' }
}
# הסיבה האחרונה שהעמדה נמחקה (נכתבת רק כשהשרת שחרר / דחה)
$dd = Join-Path $env:LOCALAPPDATA 'Lamerhav\.cache\d.dat'
if (Test-Path $dd) { $l = @(Get-Content $dd -Encoding UTF8 -TotalCount 2); if ($l.Count -ge 2 -and $l[1] -match '^\d+$') { Put ('סיבת המחיקה האחרונה (מהשרת): ' + $l[0] + ' · ' + ([DateTimeOffset]::FromUnixTimeMilliseconds([long]$l[1]).LocalDateTime.ToString('yyyy-MM-dd HH:mm'))) } }
Put ('פרופיל Windows זמני? ' + $(if ($env:USERPROFILE -match 'TEMP\.|\.bak$') { 'כן — נראה פרופיל זמני (הכניסה שנשמרה אצל המשתמש נעלמת בהתנתקות)' } else { 'לא נראה' }))

# ======================= בדיקה עמוקה: מה בדיוק ירוץ כשלוחצים "הרצה ויזואלית" =======================
# 1) האם התוסף שמותקן ב-Visual Studio הוא בדיוק זה שבהתקנה (השוואת קובץ ה-DLL)
Section 'בדיקה עמוקה 1 — התוסף המותקן מול התוסף שבהתקנה'
$shippedVsix = Join-Path $dsviz 'cs\DsViz.VsExtension.vsix'
$shippedHash = $null
if (Test-Path $shippedVsix) {
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [IO.Compression.ZipFile]::OpenRead($shippedVsix)
        $e = $zip.Entries | Where-Object { $_.Name -eq 'DsViz.VsExtension.dll' } | Select-Object -First 1
        if ($e) { $st = $e.Open(); $sha = [Security.Cryptography.SHA256]::Create(); $shippedHash = ([BitConverter]::ToString($sha.ComputeHash($st))).Replace('-', ''); $st.Close() }
        $zip.Dispose()
    } catch { }
    Put ('התוסף שבהתקנה (cs\DsViz.VsExtension.vsix): ' + (Get-Item $shippedVsix).Length + ' בתים · ' + (Get-Item $shippedVsix).LastWriteTime.ToString('yyyy-MM-dd HH:mm') + $(if ($shippedHash) { ' · DLL ' + $shippedHash.Substring(0, 12) } else { '' }))
} else { Put 'אין cs\DsViz.VsExtension.vsix בתיקיית ההתקנה' }
$instances = @()
if ($list) { $instances = @($list) }
$anyExt = $false
foreach ($i in $instances) {
    $major = 0; try { $major = ([version]$i.installationVersion).Major } catch { }
    $d = Join-Path $env:LOCALAPPDATA ('Microsoft\VisualStudio\{0}.0_{1}\Extensions' -f $major, $i.instanceId)
    if (-not (Test-Path $d)) { continue }
    foreach ($m in @(Get-ChildItem $d -Recurse -Filter extension.vsixmanifest -ErrorAction SilentlyContinue | Where-Object { (Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue) -match 'DsViz\.VisualStudio\.Toolbar' })) {
        $anyExt = $true
        $dll = Join-Path $m.Directory.FullName 'DsViz.VsExtension.dll'
        $h = $null; if (Test-Path $dll) { $h = (Get-FileHash $dll -Algorithm SHA256).Hash }
        $same = if (-not $h) { 'אין DLL בתיקייה!' } elseif (-not $shippedHash) { 'לא ניתן להשוות' } elseif ($h -eq $shippedHash) { 'זהה לזה שבהתקנה ✓' } else { '*** שונה מזה שבהתקנה — Visual Studio מחזיק גרסה אחרת ***' }
        Put ('- ' + $i.displayName + ' ' + $i.catalog.productDisplayVersion + ' · ' + $m.Directory.Name + ' · DLL ' + $(if ($h) { $h.Substring(0, 12) + ' · ' + (Get-Item $dll).LastWriteTime.ToString('yyyy-MM-dd HH:mm') } else { '-' }) + ' · ' + $same)
    }
}
if (-not $anyExt) { Put 'לא נמצאה התקנה של התוסף בתיקיות המשתמש של Visual Studio' }

# 2) כל Visual Studio שפתוח: איזה פתרון, איזה פרויקט הפעלה, איזה קובץ פעיל (דרך האוטומציה של Visual Studio)
Section 'בדיקה עמוקה 2 — Visual Studio פתוח: פתרון, פרויקט הפעלה, הקובץ הפעיל'
$activeFiles = New-Object System.Collections.Generic.List[string]
if (-not (@(Get-Process devenv -ErrorAction SilentlyContinue).Count)) {
    Put 'אין Visual Studio פתוח עכשיו. כדי שהבדיקה תראה את המצב האמיתי: פותחים Visual Studio, פותחים את הקובץ שמריצים (שיהיה הלשונית הפעילה), ומריצים את הקובץ הזה שוב.'
} else {
    try {
        Add-Type -TypeDefinition 'using System; using System.Collections.Generic; using System.Runtime.InteropServices; using System.Runtime.InteropServices.ComTypes; public static class DsRotDiag { [DllImport("ole32.dll")] static extern int GetRunningObjectTable(int r, out IRunningObjectTable t); [DllImport("ole32.dll")] static extern int CreateBindCtx(int r, out IBindCtx c); public static List<object> Dtes(List<string> names) { List<object> res = new List<object>(); IRunningObjectTable rot; if (GetRunningObjectTable(0, out rot) != 0) return res; IEnumMoniker en; rot.EnumRunning(out en); en.Reset(); IMoniker[] m = new IMoniker[1]; while (en.Next(1, m, IntPtr.Zero) == 0) { IBindCtx c; CreateBindCtx(0, out c); string n; m[0].GetDisplayName(c, null, out n); if (n != null && n.StartsWith("!VisualStudio.DTE")) { object o; if (rot.GetObject(m[0], out o) == 0 && o != null) { res.Add(o); names.Add(n); } } } return res; } }'
        $names = New-Object 'System.Collections.Generic.List[string]'
        $dtes = $null
        for ($try = 0; $try -lt 4; $try++) { $names.Clear(); $dtes = @([DsRotDiag]::Dtes($names)); if ($dtes.Count -gt 0) { break }; Start-Sleep -Milliseconds 1500 }
        if ($dtes.Count -eq 0) { Put 'Visual Studio פתוח, אבל לא הצלחתי להתחבר אליו (אולי עסוק / רץ כמנהל — מריצים את הקובץ הזה באותה הרשאה כמו Visual Studio)' }
        for ($k = 0; $k -lt $dtes.Count; $k++) {
            $o = $dtes[$k]
            Put ('Visual Studio ' + $names[$k])
            try { Put ('  גרסה: ' + $o.Version + ' · חלון: ' + $o.MainWindow.Caption) } catch { Put '  (עסוק — אי אפשר לקרוא)' }
            try { Put ('  פתרון: ' + $(if ($o.Solution.IsOpen) { $o.Solution.FullName } else { '(אין פתרון פתוח — קבצים בודדים)' })) } catch { }
            try { $sp = $o.Solution.SolutionBuild.StartupProjects; Put ('  פרויקט הפעלה (Startup): ' + $(if ($sp) { ($sp -join ' | ') } else { '(אין)' })) } catch { }
            try { foreach ($pr in $o.Solution.Projects) { Put ('  פרויקט בפתרון: ' + $pr.Name + ' · ' + $pr.FullName) } } catch { }
            try {
                $ad = $o.ActiveDocument
                if ($ad) {
                    $cp = $null; try { $cp = $ad.ProjectItem.ContainingProject.FullName } catch { }
                    Put ('  הקובץ הפעיל: ' + $ad.FullName + ' · ' + $(if ($cp) { 'שייך לפרויקט ' + $cp } else { 'לא שייך לפרויקט פתוח (Miscellaneous Files)' }))
                    if ($ad.FullName -match '\.cs$') { $activeFiles.Add($ad.FullName) }
                } else { Put '  הקובץ הפעיל: (אין קובץ פתוח)' }
            } catch { }
            try {
                $docs = @(); foreach ($dd in $o.Documents) { $docs += $dd.FullName }
                Put ('  קבצים פתוחים (' + $docs.Count + '): ' + (($docs | Select-Object -First 15) -join ' | '))
                foreach ($f in $docs) { if ($f -match '\.cs$' -and -not $activeFiles.Contains($f)) { $activeFiles.Add($f) } }
            } catch { }
        }
    } catch { Put ('שגיאה בחיבור ל-Visual Studio: ' + $_.Exception.Message) }
}

# 3) מה ההרצה הוויזואלית תעשה בפועל עם הקובץ: אותה בחירת פרויקט כמו dsviz-cs, ואחר כך הרצה אמיתית (בלי חלון) ובדיקה איזה קוד רץ
Section 'בדיקה עמוקה 3 — מה בדיוק ירוץ (הרצה אמיתית של הקובץ, בלי חלון)'
$targets = New-Object System.Collections.Generic.List[string]
if ($env:DSVIZ_DIAG_TARGET -and (Test-Path $env:DSVIZ_DIAG_TARGET)) { $targets.Add((Resolve-Path $env:DSVIZ_DIAG_TARGET).Path) }
foreach ($f in $activeFiles) { if ($targets.Count -lt 3 -and -not $targets.Contains($f) -and (Test-Path $f)) { $targets.Add($f) } }
if ($targets.Count -eq 0 -and -not $quiet) {
    $ans = Read-Host 'לא נמצא קובץ פתוח ב-Visual Studio. גוררים לכאן את קובץ ה-.cs שמריצים (או מקלידים את הנתיב) ולוחצים Enter. בלי קובץ — Enter'
    $ans = ([string]$ans).Trim().Trim('"')
    if ($ans -and (Test-Path $ans) -and $ans -match '\.cs$') { $targets.Add((Resolve-Path $ans).Path) }
}
if ($targets.Count -eq 0) { Put 'לא נבדק קובץ (לא היה קובץ פעיל ב-Visual Studio ולא נבחר קובץ).' }
$csExe = if ($env:DSVIZ_CS_EXE) { $env:DSVIZ_CS_EXE } else { Join-Path $dsviz 'cs\dsviz-cs.exe' }
$mainRx = 'static\s+(async\s+)?(void|int|Task|Task<int>|System\.Threading\.Tasks\.Task)\s+Main\s*\('
foreach ($file in $targets) {
    Put ''
    Put ('>>> קובץ: ' + $file)
    # הפרויקט שההרצה תיקח: אותו חיפוש כמו dsviz-cs (התיקייה של הקובץ ועד 4 תיקיות למעלה; csproj יחיד)
    $dir = Split-Path $file -Parent; $owner = $null; $lvl = 0
    while ($dir -and $lvl -lt 5) {
        $cp = @(Get-ChildItem -LiteralPath $dir -Filter '*.csproj' -File -ErrorAction SilentlyContinue)
        Put ('    תיקייה ' + $lvl + ': ' + $dir + ' · csproj: ' + $(if ($cp.Count) { ($cp.Name -join ', ') } else { 'אין' }))
        if ($cp.Count -eq 1) { $owner = $cp[0].FullName; break }
        if ($cp.Count -gt 1) { break }
        $dir = Split-Path $dir -Parent; $lvl++
    }
    $hasMain = $false
    try { $hasMain = [bool](Select-String -LiteralPath $file -Pattern $mainRx -Quiet) } catch { }
    $topLevel = $false
    try { $first = (Get-Content -LiteralPath $file -Raw -Encoding UTF8); $topLevel = ($first -notmatch '\b(class|struct|namespace|interface|record)\s+\w+') -and ($first -match ';') } catch { }
    Put ('    יש Main בקובץ הזה: ' + $(if ($hasMain) { 'כן' } elseif ($topLevel) { 'לא (נראה כקוד "ישיר" בלי class — top-level)' } else { 'לא' }))
    if ($owner) {
        $pdir = Split-Path $owner -Parent
        Put ('    ההרצה תקמפל את כל הפרויקט: ' + $owner)
        $csFiles = @(Get-ChildItem -LiteralPath $pdir -Filter '*.cs' -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch '\\(bin|obj)\\' })
        Put ('    קבצי .cs בפרויקט: ' + $csFiles.Count)
        $mainFiles = @($csFiles | Where-Object { Select-String -LiteralPath $_.FullName -Pattern $mainRx -Quiet })
        Put ('    קבצים עם Main בפרויקט: ' + $(if ($mainFiles.Count) { ($mainFiles.Name -join ', ') } else { 'אין' }))
        $so = $null; try { $so = ([xml](Get-Content -LiteralPath $owner -Raw -Encoding UTF8)).Project.PropertyGroup.StartupObject | Where-Object { $_ } | Select-Object -First 1 } catch { }
        if ($so) { Put ('    StartupObject בפרויקט: ' + $so) }
        if (-not $hasMain -and -not $topLevel -and $mainFiles.Count -ge 1 -and -not ($mainFiles.FullName -contains $file)) {
            Put ('    *** בקובץ שמריצים אין Main. בגרסה ישנה של DSViz ההרצה הריצה במקומו את ה-Main של ' + $mainFiles[0].Name + ' (למשל המחסנית של התבנית); בגרסה עדכנית מופיעה הודעה "אין פעולת Main" ***')
        }
    } else {
        Put '    הקובץ לא שייך לפרויקט (לא נמצא csproj יחיד) — ירוץ הקובץ לבד.'
    }
    if (-not (Test-Path $csExe)) { Put ('    אין dsviz-cs.exe (' + $csExe + ') — לא ניתן להריץ.'); continue }
    $tr = Join-Path $env:TEMP ('dsviz-diag-' + [guid]::NewGuid().ToString('N').Substring(0, 6) + '.trace')
    try {
        $psi = New-Object Diagnostics.ProcessStartInfo
        $psi.FileName = $csExe
        $psi.Arguments = '"' + $file + '" --trace "' + $tr + '"'
        $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true
        $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true; $psi.RedirectStandardInput = $true
        $psi.StandardOutputEncoding = [Text.Encoding]::UTF8; $psi.StandardErrorEncoding = [Text.Encoding]::UTF8
        $p = [Diagnostics.Process]::Start($psi)
        $p.StandardInput.Close()
        $so2 = $p.StandardOutput.ReadToEndAsync(); $se2 = $p.StandardError.ReadToEndAsync()
        if (-not $p.WaitForExit(90000)) { try { $p.Kill() } catch { }; Put '    ההרצה לא הסתיימה תוך 90 שניות — נעצרה' }
        else {
            Put ('    קוד יציאה של dsviz-cs: ' + $p.ExitCode)
            foreach ($ln in @(($so2.Result + "`n" + $se2.Result) -split "`r?`n" | Where-Object { $_.Trim() } | Select-Object -First 12)) { Put ('    | ' + $ln) }
        }
    } catch { Put ('    שגיאה בהרצת dsviz-cs: ' + $_.Exception.Message) }
    if (Test-Path $tr) {
        $lines = @(Get-Content -LiteralPath $tr -Encoding UTF8)
        $srcs = @($lines | Where-Object { $_ -like "SRC`t*" } | ForEach-Object { ($_ -split "`t")[1] })
        Put ('    קבצי קוד שהמעקב כלל: ' + ($srcs -join ', '))
        $leaf = Split-Path $file -Leaf
        $outLine = $lines | Where-Object { $_ -like "OUT`t*" } | Select-Object -Last 1
        if ($outLine) { $o3 = ($outLine -split "`t", 2)[1]; if ($o3.Length -gt 200) { $o3 = $o3.Substring(0, 200) + '…' }; Put ('    פלט התוכנית שרצה: ' + $o3) }
        Put ('    תוצאה: ' + $(if ($srcs -contains $leaf) { 'הקובץ שנבחר נכלל בהרצה.' } else { '*** הקובץ שנבחר לא היה חלק מההרצה ***' }))
        Remove-Item -LiteralPath $tr -Force -ErrorAction SilentlyContinue
    }
}

# 4) מעקב של התוסף עצמו (נכתב רק אם Visual Studio נפתח אחרי שהופעל המעקב) + הפעלת המעקב לפעם הבאה
Section 'בדיקה עמוקה 4 — מעקב התוסף (vs-trace.log)'
$vt = Join-Path $dsviz 'vs-trace.log'
if (Test-Path $vt) {
    Put ('vs-trace.log · ' + (Get-Item $vt).Length + ' בתים · עודכן ' + (Get-Item $vt).LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss') + ' — 60 השורות האחרונות:')
    Get-Content -LiteralPath $vt -Encoding UTF8 -Tail 60 | ForEach-Object { Put ('  ' + $_) }
} else { Put '(אין עדיין מעקב)' }
if (-not $quiet) {
    $cur = [Environment]::GetEnvironmentVariable('DSVIZ_VS_TRACE', 'User')
    if (-not $cur) {
        try { [Environment]::SetEnvironmentVariable('DSVIZ_VS_TRACE', '1', 'User'); Put 'הופעל מעקב של התוסף (משתנה סביבה DSVIZ_VS_TRACE של המשתמש). כדי שיתחיל לתעד: סוגרים את Visual Studio לגמרי, פותחים, מריצים "הרצה ויזואלית" פעם אחת, ואז מריצים את הקובץ הזה שוב — הדוח יכלול את מה שהתוסף החליט.' } catch { }
    } else { Put 'המעקב כבר מופעל (DSVIZ_VS_TRACE).' }
}
$al = @(Get-ChildItem (Join-Path $env:APPDATA 'Microsoft\VisualStudio') -Recurse -Filter ActivityLog.xml -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1)
foreach ($a in $al) {
    $hits = @(Select-String -LiteralPath $a.FullName -Pattern 'DsViz' -ErrorAction SilentlyContinue | Select-Object -Last 15)
    Put ('ActivityLog.xml (' + $a.LastWriteTime.ToString('yyyy-MM-dd HH:mm') + '): ' + $hits.Count + ' שורות DsViz')
    foreach ($h in $hits) { $t = $h.Line.Trim(); if ($t.Length -gt 220) { $t = $t.Substring(0, 220) + '…' }; Put ('  ' + $t) }
}

# בלי שם המשתמש בנתיבים
$text = $out -join "`r`n"
if ($env:USERPROFILE) { $text = $text.Replace($env:USERPROFILE, '%USERPROFILE%') }
# ושם המשתמש עצמו (גם מחוץ לנתיב הבית — למשל ביומנים), כמילה שלמה
if ($env:USERNAME -and $env:USERNAME.Length -ge 3) { $text = [regex]::Replace($text, '(?<![\p{L}\p{N}])' + [regex]::Escape($env:USERNAME) + '(?![\p{L}\p{N}])', '%USERNAME%', 'IgnoreCase') }
$desk = [Environment]::GetFolderPath('Desktop')
if (-not $desk -or -not (Test-Path $desk)) { $desk = $env:TEMP }
# DSVIZ_DIAG_OUT: התוסף מריץ את הבדיקה ברקע כשיש בעיה (דיווח אוטומטי) ושומר אותה בקובץ זמני משלו — לא על שולחן העבודה
$file = if ($env:DSVIZ_DIAG_OUT) { $env:DSVIZ_DIAG_OUT } else { Join-Path $desk 'DSViz-diagnose.txt' }
[IO.File]::WriteAllText($file, $text, (New-Object Text.UTF8Encoding($true)))
if ($quiet) { Write-Output $file; return }
try { Set-Clipboard -Value $text } catch { }
Start-Process notepad.exe -ArgumentList ('"' + $file + '"')
try {
    Add-Type -AssemblyName System.Windows.Forms
    $msg = 'הדוח מוכן ונפתח בפנקס הרשימות.' + "`n" + 'הוא כבר הועתק — אפשר להדביק (Ctrl+V) בהודעה ולשלוח.' + "`n`n" + 'הקובץ נשמר גם בשולחן העבודה: DSViz-diagnose.txt'
    [Windows.Forms.MessageBox]::Show($msg, 'DSViz — אבחון', 'OK', 'Information', 'Button1', 'RtlReading, RightAlign') | Out-Null
} catch { }
