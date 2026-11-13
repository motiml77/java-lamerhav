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
param([switch]$AfterEclipse, [switch]$FromEclipse, [switch]$FromIde, [switch]$Videos)
$ErrorActionPreference = 'Stop'
$dir = $PSScriptRoot
$log = Join-Path $dir 'update.log'
$versionUrl = 'https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/release/version.json'
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

# רק עדכון אחד בכל פעם
$lock = Join-Path $env:TEMP 'dsviz-update.lock'
try { $fs = [IO.File]::Open($lock, 'OpenOrCreate', 'ReadWrite', 'None') } catch { exit 0 }
try {
    if ($AfterEclipse) { Start-Sleep -Seconds 5 } elseif ($FromEclipse -or $FromIde) { Start-Sleep -Seconds 60 } else { Start-Sleep -Seconds 120 }   # לא להאט את הכניסה ל-Windows / את פתיחת Eclipse
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
    # לא יותר מבדיקה אחת בשעה בכניסה ל-Windows; מ-Eclipse (בפתיחה ובסגירה) — עד פעם ב-5 דקות: אלה הרגעים שבהם התלמיד עובד,
    # וקודם בדיקה בכניסה ל-Windows חסמה לשעה את הבדיקה בסגירת Eclipse
    $stamp = Join-Path $dir 'update-check.txt'
    $every = if ($AfterEclipse -or $FromEclipse -or $FromIde) { 5 } else { 60 }
    if ((Test-Path -LiteralPath $stamp) -and ((Get-Date) - (Get-Item -LiteralPath $stamp).LastWriteTime).TotalMinutes -lt $every) { exit 0 }
    Set-Content -LiteralPath $stamp -Value (Get-Date).ToString('s')

    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    try { SyncVideos } catch { Log "סרטונים: $($_.Exception.Message)" }
    $local = 0
    $bf = Join-Path $dir 'build.txt'
    if (Test-Path -LiteralPath $bf) { $local = [int64]((Get-Content -LiteralPath $bf -Raw).Trim()) }
    # GitHub מחזיר את הקובץ כטקסט רגיל — ממירים ל-JSON בעצמנו
    $v = (Invoke-WebRequest -Uri $versionUrl -UseBasicParsing -TimeoutSec 30).Content | ConvertFrom-Json
    $remote = [int64]$v.build
    if ($remote -le $local) { exit 0 }
    Log "נמצאה גרסה חדשה: $($v.version) ($remote, מותקנת: $local)"
    Toast 'found'

    $exe = Join-Path $env:TEMP 'DSViz-Update.exe'
    Invoke-WebRequest -Uri $v.url -OutFile $exe -UseBasicParsing -TimeoutSec 900
    $hash = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLower()
    if ($hash -ne ([string]$v.sha256).ToLower()) { Log "הקובץ שהורד לא תקין (SHA-256) — מדלגים"; Remove-Item -LiteralPath $exe -Force; exit 0 }

    # מחכים ש-Eclipse / Visual Studio (והרצה ויזואלית של C#) ייסגרו (עד 12 שעות), כדי לא להפריע לעבודה — והעדכון נכנס לתוקף בפתיחה הבאה
    # בודקים כל 5 שניות (קודם: פעם בדקה — מי שסגר את Eclipse ופתח אותו שוב תוך דקה "פספס" את העדכון), ומודיעים מה עוד פתוח
    # בכל פעם שזה משתנה (סגרו את Eclipse אבל Visual Studio עוד פתוח — רואים את זה, ולא חושבים שהעדכון נתקע)
    $waited = 0
    $shown = ''
    while ((EclipseRunning) -and $waited -lt 8640) {
        $open = OpenApps
        if ($open -and $open -ne $shown) { Toast 'waiting' 0 $open; Log "מחכים שייסגרו: $open"; $shown = $open }
        Start-Sleep -Seconds 5; $waited++
    }
    if (EclipseRunning) { Log "Eclipse / Visual Studio / הרצה ויזואלית / מודלים חישוביים עדיין פתוחים — ננסה בפעם הבאה"; exit 0 }

    Log "מתקין את הגרסה החדשה..."
    $p = Start-Process -FilePath $exe -ArgumentList '/S', '/UPDATE' -PassThru -WindowStyle Hidden
    # חלון קטן בפינה: "DSViz מתעדכן לגרסה חדשה…" — נסגר לבד כשההתקנה נגמרת; בסופה ההתקנה מראה "DSViz עודכן"
    Toast 'updating' $p.Id
    $p.WaitForExit()
    Log "ההתקנה הסתיימה (קוד $($p.ExitCode))"
    Remove-Item -LiteralPath $exe -Force -ErrorAction SilentlyContinue
} catch {
    Log "שגיאה: $($_.Exception.Message)"
} finally {
    $fs.Close()
}
