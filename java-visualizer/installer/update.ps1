# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# עדכון אוטומטי של "מבני נתונים — הרצה ויזואלית" (רץ ברקע, בחלון נסתר):
#   - רק כשמשתמשים ב-DSViz: כש-Eclipse נפתח ונסגר (-FromEclipse / -AfterEclipse), כש-IntelliJ / VS Code נפתחים וכשמפעילים כלי של DSViz
#     מ-Visual Studio או את מודלים חישוביים (-FromIde). אין משימה נסתרת בכניסה ל-Windows
#   - בודק אם יש גרסה חדשה (version.json באתר), מוריד את ההתקנה (הודעה קטנה בפינה: "נמצאה גרסה חדשה"), בודק שהקובץ שלם (SHA-256),
#     מחכה ש-Eclipse ייסגר, ומתקין בשקט (/S /UPDATE) — עם אותן שפות שנבחרו בהתקנה. עבודה של התלמיד לא נוגעים בה.
#   - בלי אינטרנט / שגיאה — לא עושים כלום (ננסה בפעם הבאה). יומן: update.log ליד הקובץ הזה.
#   - IntelliJ IDEA: בכל בדיקה — התוסף נכנס לכל IntelliJ שעוד אין בו (intellij.ps1), גם IntelliJ שהותקן אחרי DSViz
#   - Visual Studio: בכל בדיקה — סרגל הכלים נכנס לכל Visual Studio שעוד אין בו, או שיש בו גרסה ישנה (install-vsix.ps1 -Missing), גם Visual
#     Studio שהותקן אחרי DSViz. Visual Studio פתוח (זה המצב הרגיל — הבדיקה מתחילה מכלי של DSViz בתוכו): VSIXInstaller לא מתקין כשהוא
#     פתוח, אז תהליך קטן ברקע (install-vsix.ps1 -WhenClosed) מחכה שייסגר, מתקין, ומודיע "סרגל הכלים מוכן" — ובפתיחה הבאה הוא שם
#   - -Videos: רק מוריד את סרטוני ההדרכה שחסרים (ההתקנה לא כוללת אותם — מגבלת הגודל של GitHub). רץ מיד אחרי ההתקנה, וגם בכל בדיקה.
#   - הכניסה השמורה ("קובץ הזהב" — עמדת מחשב / מורה / תלמיד, ב-%ProgramData% / %LOCALAPPDATA% / %APPDATA%\Lamerhav\.cache) מחוץ
#     לתיקיית ההתקנה: העדכון (/S /UPDATE) לא נוגע בה, ואסור להוסיף כאן מחיקה שלה (tools/test/access/Golden.java בודק)
#   - העדכון שהורד נשמר ב-pending\ (ליד הקובץ הזה, לא ב-TEMP) עד שהוא באמת מותקן: build.txt של ההתקנה = הגרסה שהורדה.
#     התקנה שלא הושלמה (Eclipse / Visual Studio נפתחו באמצע) — אותו קובץ מותקן שוב בסגירה הבאה, בלי להוריד שוב.
#   - -InstallNow (מ-Eclipse: "הפעלה מחדש ועדכון"): מחכים ש-Eclipse ייסגר (עד 2 דקות), מתקינים את העדכון שהורד,
#     ופותחים את Eclipse מחדש (-Relaunch <eclipse.exe> -Workspace <תיקייה>) — גם אם ההתקנה לא הצליחה, כדי שלא יישאר סגור
param([switch]$AfterEclipse, [switch]$FromEclipse, [switch]$FromIde, [switch]$Videos, [switch]$InstallNow, [string]$Relaunch = '', [string]$Workspace = '')
$ErrorActionPreference = 'Stop'
$dir = $PSScriptRoot
$log = Join-Path $dir 'update.log'
$versionUrl = 'https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/release/version.json'
# רק לבדיקות (tools/test/windows-vs-toolbar/update-progress.ps1): שרת מקומי במקום GitHub, בלי ההמתנות והבדיקה "לא יותר מפעם ב-5 דקות".
# משתמש רגיל לא מגדיר את המשתנה הזה
$testUrl = $env:DSVIZ_VERSION_URL
if ($testUrl) { $versionUrl = $testUrl }
# קובץ ההתקדמות של החלון הקטן בפינה (notify.ps1): "download 37" בזמן ההורדה, "install 55" בהתקנה (setup.nsi מעדכן בכל שלב),
# "ready" אחרי שההורדה נגמרה, "done" / "failed" בסוף. אותו קובץ ש-setup.nsi כותב אליו: ‎%TEMP%\dsviz-progress.txt
$progressFile = Join-Path $env:TEMP 'dsviz-progress.txt'
function Prog([string]$kind, [int]$pct) {
    try { [IO.File]::WriteAllText($progressFile, "$kind $pct", [Text.Encoding]::ASCII) } catch { }
}
# הורדה עם אחוזים (במקום Invoke-WebRequest שלא מדווח): קוראים בחתיכות ומעדכנים את קובץ ההתקדמות בכל אחוז שמשתנה
function DownloadWithProgress([string]$url, [string]$dest) {
    $req = [Net.HttpWebRequest]::Create($url)
    $req.UserAgent = 'DSViz-Update'; $req.Timeout = 60000; $req.ReadWriteTimeout = 60000
    $resp = $req.GetResponse()
    try {
        $total = $resp.ContentLength   # -1 — השרת לא אמר כמה (הפס נע, בלי אחוזים)
        $in = $resp.GetResponseStream()
        $out = [IO.File]::Create($dest)
        try {
            $buf = New-Object byte[] 65536
            $got = 0L; $last = -2
            while (($n = $in.Read($buf, 0, $buf.Length)) -gt 0) {
                $out.Write($buf, 0, $n)
                $got += $n
                $pct = if ($total -gt 0) { [int][Math]::Floor(100.0 * $got / $total) } else { -1 }
                if ($pct -ne $last) { $last = $pct; Prog 'download' $pct }
            }
        } finally { $out.Close(); $in.Close() }
    } finally { $resp.Close() }
}
function Log([string]$m) { try { Add-Content -LiteralPath $log -Value ("{0:yyyy-MM-dd HH:mm}  {1}" -f (Get-Date), $m) -Encoding UTF8 } catch { } }
# Eclipse (קובצי התוסף נעולים), או הרצה ויזואלית של C# (dsviz-cs.exe והקבצים שלו נעולים בזמן ריצה, וההתקנה מוחקת ומחליפה את התיקייה cs —
# עלולים להישאר בה קבצים ישנים, בזמן ש-build.txt כבר אומר שהגרסה החדשה מותקנת, כך שהעדכון לא היה מנסה שוב)
# Eclipse / Visual Studio / הרצה ויזואלית של C# פתוחים? (Visual Studio — כי את סרגל הכלים שלו אי אפשר להתקין כשהוא פתוח)
# וגם "מודלים חישוביים" (javaw עם AutomataLauncher — הקבצים שלו בתיקייה automata נעולים בזמן שהוא פתוח)
function AutomataRunning {
    try { return [bool](@(Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" | Where-Object { $_.CommandLine -like '*il.lamerhav.dsviz.AutomataLauncher*' }).Count) } catch { return $false }
}
function EclipseRunning { return [bool](Get-Process -Name eclipse, devenv, dsviz-cs -ErrorAction SilentlyContinue) -or (AutomataRunning) }
# מה בדיוק פתוח ומעכב את העדכון — לשמות בהודעה ("Eclipse, Visual Studio")
function OpenApps {
    $names = @()
    if (Get-Process -Name eclipse -ErrorAction SilentlyContinue) { $names += 'Eclipse' }
    if (Get-Process -Name devenv -ErrorAction SilentlyContinue) { $names += 'Visual Studio' }
    if (Get-Process -Name dsviz-cs -ErrorAction SilentlyContinue) { $names += 'הרצה ויזואלית של C#' }
    if (AutomataRunning) { $names += 'מודלים חישוביים' }
    return ($names -join ', ')
}

# הודעה קטנה בפינה (notify.ps1), ברקע — לא מחכים לה ולא לוקחת פוקוס: found / waiting / updating
function Toast([string]$state, [int]$watch = 0, [string]$apps = '') {
    try {
        $notify = Join-Path $dir 'notify.ps1'
        if (-not (Test-Path -LiteralPath $notify)) { return }
        $a = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$notify`"", '-State', $state)
        if ($state -eq 'download' -or $state -eq 'updating') { $a += @('-ProgressFile', "`"$progressFile`"") }
        if ($watch -gt 0) { $a += @('-WatchPid', $watch) }
        if ($apps) { $a += @('-Apps', "`"$apps`"") }
        Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -ArgumentList $a
    } catch { }
}

# סרטוני ההדרכה (help\, help\vscode\, help\vs\ — a.mp4 ... e.mp4) שעוד לא במחשב: מורידים מהאתר לקובץ זמני ואז משנים את השם
function SyncVideos {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $base = 'https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/help/'
    foreach ($sub in @('', 'vscode', 'vs')) {
        $d = if ($sub) { Join-Path (Join-Path $dir 'help') $sub } else { Join-Path $dir 'help' }
        if (-not (Test-Path -LiteralPath (Join-Path $d 'index.html'))) { continue }
        foreach ($id in @('a', 'b', 'c', 'd', 'e')) {
            $f = Join-Path $d "$id.mp4"
            if (Test-Path -LiteralPath $f) { continue }
            $url = $base + $(if ($sub) { "$sub/" } else { '' }) + "$id.mp4"
            try {
                Invoke-WebRequest -Uri $url -OutFile "$f.part" -UseBasicParsing -TimeoutSec 600
                Move-Item -LiteralPath "$f.part" -Destination $f -Force
            } catch {
                Remove-Item -LiteralPath "$f.part" -Force -ErrorAction SilentlyContinue
                Log "סרטון $sub/$id לא ירד: $($_.Exception.Message)"
                return   # בלי אינטרנט — ננסה בפעם הבאה
            }
        }
    }
}

if ($Videos) {
    $vlock = Join-Path $env:TEMP 'dsviz-videos.lock'
    try { $vfs = [IO.File]::Open($vlock, 'OpenOrCreate', 'ReadWrite', 'None') } catch { exit 0 }
    try { SyncVideos } catch { Log "סרטונים: $($_.Exception.Message)" } finally { $vfs.Close() }
    exit 0
}

# העדכון שהורד ומחכה להתקנה: pending\DSViz-Setup-<build>.exe + pending.json ({build, version, sha256, file})
$pendDir = Join-Path $dir 'pending'
$pendJson = Join-Path $pendDir 'pending.json'
$relaunchFile = Join-Path $pendDir 'relaunch.txt'   # "הפעלה מחדש ועדכון": מי שמתקין פותח את Eclipse מחדש בסוף
function LocalBuild {
    $bf = Join-Path $dir 'build.txt'
    try { if (Test-Path -LiteralPath $bf) { return [int64]((Get-Content -LiteralPath $bf -Raw).Trim()) } } catch { }
    return [int64]0
}
function ReadPending {
    try {
        if (-not (Test-Path -LiteralPath $pendJson)) { return $null }
        $p = Get-Content -LiteralPath $pendJson -Raw -Encoding UTF8 | ConvertFrom-Json
        $f = Join-Path $pendDir ([string]$p.file)
        if ((Test-Path -LiteralPath $f) -and ((Get-FileHash -LiteralPath $f -Algorithm SHA256).Hash.ToLower() -eq ([string]$p.sha256).ToLower())) { return $p }
    } catch { }
    return $null
}
function ClearPending {
    Get-ChildItem -LiteralPath $pendDir -Filter 'DSViz-Setup-*' -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $pendJson -Force -ErrorAction SilentlyContinue
}
function RelaunchEclipse {
    if (-not (Test-Path -LiteralPath $relaunchFile)) { return }
    # בקשה ישנה (Eclipse לא נסגר אז, והתלמיד המשיך לעבוד) — לא פותחים את Eclipse פתאום שעות אחר כך
    if (((Get-Date) - (Get-Item -LiteralPath $relaunchFile).LastWriteTime).TotalMinutes -gt 15) { Remove-Item -LiteralPath $relaunchFile -Force -ErrorAction SilentlyContinue; return }
    try {
        $l = @(Get-Content -LiteralPath $relaunchFile -Encoding UTF8)
        Remove-Item -LiteralPath $relaunchFile -Force -ErrorAction SilentlyContinue
        $exePath = if ($l.Count -gt 0) { $l[0].Trim() } else { '' }
        $ws = if ($l.Count -gt 1) { $l[1].Trim() } else { '' }
        if ($exePath -and (Test-Path -LiteralPath $exePath) -and -not (Get-Process -Name eclipse -ErrorAction SilentlyContinue)) {
            if ($ws) { Start-Process -FilePath $exePath -ArgumentList @('-data', "`"$ws`"") } else { Start-Process -FilePath $exePath }
            Log "Eclipse נפתח מחדש"
        }
    } catch { Log "פתיחה מחדש של Eclipse: $($_.Exception.Message)" }
}
# פתוחים עכשיו? בודקים כל שנייה (מי שסגר את Eclipse ופתח אותו מיד "פספס" את העדכון כשבדקנו כל 5 שניות);
# "מודלים חישוביים" (שאילתת WMI — איטית יותר) — כל 5 שניות
$script:tick = 0; $script:auto = $false; $script:installed = $false
function Busy {
    $script:tick++
    if (($script:tick % 5) -eq 1) { $script:auto = AutomataRunning }
    return [bool](Get-Process -Name eclipse, devenv, dsviz-cs -ErrorAction SilentlyContinue) -or $script:auto
}

if ($InstallNow) {
    New-Item -ItemType Directory -Path $pendDir -Force | Out-Null
    if ($Relaunch) { Set-Content -LiteralPath $relaunchFile -Value @($Relaunch, $Workspace) -Encoding UTF8 }
}

# תחזוקה של IntelliJ / Visual Studio (התוסף לכל IntelliJ, סרגל הכלים לכל Visual Studio — גם כאלה שהותקנו אחרי DSViz).
# רצה בסוף, כשאין עדכון להתקין (בעדכון — ההתקנה עושה את זה בעצמה): קודם היא רצה לפני בדיקת הגרסה, וההתקנה של סרגל הכלים
# ל-Visual Studio (דקות) עיכבה את העדכון
function IdeMaintenance {
    # IntelliJ IDEA: גם IntelliJ שהותקן אחרי DSViz (או שהיה פתוח בזמן ההתקנה) מקבל את התוסף — בכל בדיקה, גם בלי אינטרנט
    try {
        $langFile = Join-Path $dir 'lang.txt'
        $lang = if (Test-Path -LiteralPath $langFile) { (Get-Content -LiteralPath $langFile -Raw).Trim() } else { 'java' }
        $ij = Join-Path $dir 'intellij\dsviz-intellij'
        if ($lang -ne 'cs' -and (Test-Path -LiteralPath (Join-Path $ij 'lib'))) {
            $r = & (Join-Path $dir 'intellij.ps1') -Source $ij -Quiet
            if ($LASTEXITCODE -eq 3) { Log 'IntelliJ פתוח — התוסף יעודכן בפעם הבאה' }
        }
        # מאגר התרגילים ב-IntelliJ (אחרי שהתוסף הראשי נכנס) — אותה בדיקה, גם בלי אינטרנט
        $bank = Join-Path $dir 'bank\bank-install.ps1'
        if ($lang -ne 'cs' -and (Test-Path -LiteralPath $bank)) { & $bank -Only intellij -Quiet | Out-Null }
    } catch { Log "IntelliJ: $($_.Exception.Message)" }
    # Visual Studio: גם Visual Studio שהותקן אחרי DSViz (למשל 2026 ליד 2022), או שההתקנה של סרגל הכלים לא הצליחה בו, או שנשארה בו
    # גרסה ישנה (העדכון רץ כשהוא היה פתוח), מקבל את סרגל הכלים — בכל בדיקה, גם בלי אינטרנט. Visual Studio שכבר יש בו את הגרסה הנוכחית — לא נוגעים.
    # כש-Visual Studio פתוח: מחכים ברקע שייסגר (תהליך נפרד — לא מעכב את בדיקת הגרסה)
    try {
        $langFile = Join-Path $dir 'lang.txt'
        $lang = if (Test-Path -LiteralPath $langFile) { (Get-Content -LiteralPath $langFile -Raw).Trim() } else { 'java' }
        $vsixPs = Join-Path $dir 'cs\install-vsix.ps1'
        $vsixFile = Join-Path $dir 'cs\DsViz.VsExtension.vsix'
        if ($lang -ne 'java' -and (Test-Path -LiteralPath $vsixPs) -and (Test-Path -LiteralPath $vsixFile)) {
            if (-not (Get-Process -Name devenv -ErrorAction SilentlyContinue)) {
                & $vsixPs -Vsix $vsixFile -Missing | Out-Null
            } else {
                & $vsixPs -Vsix $vsixFile -Check | Out-Null
                if ($LASTEXITCODE -eq 10) {
                    Log 'Visual Studio פתוח וחסר בו סרגל הכלים (או שיש בו גרסה ישנה) — יותקן ברקע כשייסגר'
                    Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$vsixPs`"", '-Vsix', "`"$vsixFile`"", '-Missing', '-WhenClosed')
                }
            }
        }
    } catch { Log "Visual Studio: $($_.Exception.Message)" }
}

# רק עדכון אחד בכל פעם (מי שכבר מחכה לסגירה — יתקין, ויפתח את Eclipse מחדש אם ביקשו: relaunch.txt)
$lock = Join-Path $env:TEMP 'dsviz-update.lock'
try { $fs = [IO.File]::Open($lock, 'OpenOrCreate', 'ReadWrite', 'None') } catch { exit 0 }
try {
    if ($testUrl -or $InstallNow) { } elseif ($AfterEclipse) { Start-Sleep -Seconds 5 } elseif ($FromEclipse -or $FromIde) { Start-Sleep -Seconds 60 } else { Start-Sleep -Seconds 120 }   # לא להאט את הכניסה ל-Windows / את פתיחת Eclipse
    $local = LocalBuild
    $pending = ReadPending
    if ($pending -and [int64]$pending.build -le $local) { ClearPending; $pending = $null }   # כבר מותקן
    if (-not $pending) {
        if ($InstallNow) { Log 'אין עדכון שמחכה להתקנה'; RelaunchEclipse; exit 0 }
        # לא יותר מבדיקה אחת בשעה בכניסה ל-Windows; מ-Eclipse (בפתיחה ובסגירה) — עד פעם ב-5 דקות. עדכון שכבר הורד לא מחכה לזה
        $stamp = Join-Path $dir 'update-check.txt'
        $every = if ($AfterEclipse -or $FromEclipse -or $FromIde) { 5 } else { 60 }
        if (-not $testUrl -and (Test-Path -LiteralPath $stamp) -and ((Get-Date) - (Get-Item -LiteralPath $stamp).LastWriteTime).TotalMinutes -lt $every) { exit 0 }
        Set-Content -LiteralPath $stamp -Value (Get-Date).ToString('s')

        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        # קודם הגרסה — ורק אחר כך סרטוני ההדרכה: הורדת הסרטונים יכולה לקחת דקות, והעדכון חיכה לה
        # GitHub מחזיר את הקובץ כטקסט רגיל — ממירים ל-JSON בעצמנו
        $v = (Invoke-WebRequest -Uri $versionUrl -UseBasicParsing -TimeoutSec 30).Content | ConvertFrom-Json
        $remote = [int64]$v.build
        if ($remote -le $local) {
            # אין גרסה חדשה — הזדמנות להוריד סרטונים שחסרים (בעדכון: ההתקנה מורידה אותם בעצמה, update.ps1 -Videos)
            if (-not $testUrl) { try { SyncVideos } catch { Log "סרטונים: $($_.Exception.Message)" } }
            exit 0
        }
        Log "נמצאה גרסה חדשה: $($v.version) ($remote, מותקנת: $local)"
        # חלון בפינה עם אחוזי הורדה — נשאר על המסך עד שההורדה נגמרת (בלי כפתור סגירה)
        Prog 'download' 0
        Toast 'download'
        New-Item -ItemType Directory -Path $pendDir -Force | Out-Null
        ClearPending
        $name = "DSViz-Setup-$remote.exe"
        $part = Join-Path $pendDir "$name.part"
        DownloadWithProgress $v.url $part
        $hash = (Get-FileHash -LiteralPath $part -Algorithm SHA256).Hash.ToLower()
        if ($hash -ne ([string]$v.sha256).ToLower()) { Log "הקובץ שהורד לא תקין (SHA-256) — מדלגים"; Remove-Item -LiteralPath $part -Force; Prog 'failed' 0; exit 0 }
        Move-Item -LiteralPath $part -Destination (Join-Path $pendDir $name) -Force
        ([ordered]@{ build = $remote; version = [string]$v.version; sha256 = $hash; file = $name } | ConvertTo-Json) | Set-Content -LiteralPath $pendJson -Encoding UTF8
        $pending = ReadPending
        if (-not $pending) { Log "העדכון שהורד לא נשמר — ננסה בפעם הבאה"; Prog 'failed' 0; exit 0 }
    } else {
        Log "עדכון שהורד קודם מחכה להתקנה: $($pending.version) ($($pending.build), מותקנת: $local)"
    }
    Prog 'ready' 100   # ההורדה הושלמה ונבדקה — חלון ההורדה נסגר
    $exe = Join-Path $pendDir ([string]$pending.file)

    # מחכים ש-Eclipse / Visual Studio (והרצה ויזואלית של C#, מודלים חישוביים) ייסגרו — עד 12 שעות (ב-InstallNow: עד 2 דקות),
    # ומודיעים מה עוד פתוח בכל פעם שזה משתנה. בודקים כל שנייה
    $limit = if ($InstallNow) { 120 } else { 43200 }
    $waited = 0
    $shown = ''
    while ((Busy) -and $waited -lt $limit) {
        if (($waited % 5) -eq 0) {
            $open = OpenApps
            if ($open -and $open -ne $shown) { Toast 'waiting' 0 $open; Log "מחכים שייסגרו: $open"; $shown = $open }
        }
        Start-Sleep -Seconds 1; $waited++
    }
    if (Busy) {
        Log "Eclipse / Visual Studio / הרצה ויזואלית / מודלים חישוביים עדיין פתוחים — העדכון שהורד יותקן בסגירה הבאה"
        if ($InstallNow) { Remove-Item -LiteralPath $relaunchFile -Force -ErrorAction SilentlyContinue }   # Eclipse לא נסגר (ביטלו) — לא פותחים אותו אחר כך
        exit 0
    }

    Log "מתקין את הגרסה החדשה ($($pending.version))..."
    Prog 'install' 0
    $script:installed = $true
    $p = Start-Process -FilePath $exe -ArgumentList '/S', '/UPDATE' -PassThru -WindowStyle Hidden
    # חלון קטן בפינה: "DSViz מתעדכן לגרסה חדשה" עם אחוזים (המתקין מדווח בכל שלב) — נסגר לבד כשההתקנה נגמרת; בסופה ההתקנה מראה "DSViz עודכן"
    Toast 'updating' $p.Id
    $p.WaitForExit()
    $after = LocalBuild
    if ($after -ge [int64]$pending.build) {
        Log "ההתקנה הסתיימה: גרסה $($pending.version) מותקנת (קוד $($p.ExitCode))"
        Prog 'done' 100
        ClearPending
    } else {
        # build.txt לא התעדכן — Eclipse / Visual Studio נפתחו באמצע (update-incomplete.txt), או שההתקנה נכשלה: הקובץ נשאר, וננסה שוב
        Log "ההתקנה לא הושלמה (קוד $($p.ExitCode), מותקנת: $after) — העדכון שהורד יותקן שוב בסגירה הבאה"
        Prog 'failed' 0
    }
    RelaunchEclipse
} catch {
    Log "שגיאה: $($_.Exception.Message)"
    Prog 'failed' 0   # חלון ההתקדמות (אם פתוח) נסגר — לא נשאר תקוע על המסך
    RelaunchEclipse
} finally {
    if (-not $InstallNow -and -not $script:installed) { try { IdeMaintenance } catch { } }
    $fs.Close()
}
