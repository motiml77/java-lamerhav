# (UTF-8 עם BOM) DSViz ב-Visual Studio אמיתי — כמו תלמיד: לחיצות עכבר והקשות מקלדת אמיתיות (SendInput), צילום מסך מלא אחרי כל צעד.
# רץ ב-.github/workflows/dsviz-vs-matrix.yml, פעם לכל גרסה: ‎-Major 17‎ (Visual Studio 2022), ‎18‎ (2026).
#   -Kit   הערכה שנבנתה מהמקור (build-kit.ps1): dsviz-cs, קובץ ההרחבה, dsviz.jar, המאגר, תבנית התרגילים, DSViz.targets
#   -Out   תיקיית התוצאות: shots\NNN-*.png, summary.txt / summary.md, logs\ (vsix-install.log, VSIXInstaller, ActivityLog, vs-trace.log, vs-loaded-*.txt)
# מה נבדק (המספרים — docs/vs-deep-dive.md, סעיף 4.4):
#   T0  התקנה (install-kit.ps1 + install-vsix.ps1 כמו ההתקנה): כל Visual Studio מתאים במחשב קיבל את ההרחבה, ומה שלא מתאים דולג ונרשם
#   T1  הסרגל של DSViz: כל הכפתורים גלויים ולחיצים, בשורה משלו — בלי פתרון, עם פתרון, אחרי סגירה רגילה, אחרי סגירה בכוח; ההרחבה כתבה "נטענתי"
#   T2  הרצה רגילה של פרויקט SDK: הכפתור הירוק (עכבר) ו-Ctrl+F5 (מקלדת) — קונסול, בלי dsviz-cs; "הרצה ויזואלית" בסרגל — החלון של DSViz
#   T3  ספריית בית הספר (Unit4): פרויקט ‎.NET Framework קלאסי (HintPath) ופרויקט עם ProjectReference — הרצה רגילה עם הספרייה שלהם,
#       הרצה ויזואלית של אותו קוד עם הספרייה של DSViz; פרויקט קלאסי בלי הפניה — הספרייה של DSViz מתווספת
#   T4  top-level statements: רגילה וויזואלית
#   T5  קובץ שלא שייך לאף פרויקט (בפתרון התרגילים): ההרצה הרגילה מריצה את ה-Startup Project כרגיל, מופיעה שורת הודעה שמסבירה,
#       והכפתור שבה / הכפתור בסרגל מריצים ויזואלית את הקובץ. שום דבר לא מוחלף בשקט
#   T6  הודעות: קובץ בלי Main, Main ריקה, שגיאת קומפילציה
#   T7  שני פרויקטי הרצה בפתרון רגיל: ההרצה הרגילה מריצה את ה-Startup Project (שלא משתנה), עם שורת הודעה; הסרגל מריץ את הקובץ הפתוח
#   T8  פרויקט שאין לו קשר ל-DSViz (Stack<int> של ‎.NET): נבנה ורץ, בלי הספרייה שלנו
#   T13 שתי ההרצות באותו זמן על אותו קובץ (ספריית בית הספר): ההרצה הרגילה מחכה לקלט, ובזמן הזה ההרצה הוויזואלית — שתיהן רצות, כל אחת עם
#       הספרייה שלה, והרגילה לא מופרעת
#   T3x חבר שיש רק בספרייה של בית הספר: ההרצה הרגילה עובדת, וההרצה הוויזואלית מסבירה בעברית מה חסר בספרייה של DSViz
#   T12 מאגר השאלות: מסך הכניסה; עם כניסה שמורה — לחיצה על נושא, על שאלה, על "צור תרגיל"; הקבצים, הפרויקט ב-Solution Explorer, הקובץ
#       בעורך, הבנייה, הרצה ויזואלית. ושוב אחרי שמוחקים את lib\Unit4.CollectionsLib.dll מתיקיית התרגילים (התיקון העצמי)
# לא נבדק כאן (ולמה): T9 עדכון כש-Visual Studio פתוח, T10 הרחבה מושבתת, T11 מדיניות שחוסמת PowerShell — דורשים את DSViz-Setup.exe עצמו
# (נבנה ב-Linux) או את חלון ניהול ההרחבות, ששונה בכל גרסה; ראו vs-upgrade.ps1 ו-dsviz-public.yml.
param([Parameter(Mandatory = $true)][string]$Kit, [Parameter(Mandatory = $true)][string]$Out,
    [Parameter(Mandatory = $true)][ValidateSet('17', '18')][string]$Major)
. (Join-Path $PSScriptRoot 'real-ui.ps1')
. (Join-Path $PSScriptRoot 'fixtures.ps1')
$Kit = (Resolve-Path $Kit).Path
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$script:vsMajor = [int]$Major
$logs = Join-Path $Out 'logs'
New-Item -ItemType Directory -Force $logs | Out-Null
$title = "Visual Studio $(@{ '17' = '2022'; '18' = '2026' }[$Major])"

# יומן ההרחבה גם לקובץ (%LOCALAPPDATA%\DSViz\vs-trace.log) — Visual Studio יורש את המשתנה
$env:DSVIZ_VS_TRACE = '1'
# בלי שרת: הכניסה השמורה (מפתח בדיקה) נבדקת במחשב בלבד, ושום דבר לא נשלח החוצה
$env:DSVIZ_WORKER_URL = 'http://127.0.0.1:9'
$dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'
$docs = [Environment]::GetFolderPath('MyDocuments')
$csDir = Join-Path $docs 'DataStructures-CS'
$exSln = Join-Path $csDir 'DataStructures-CS.sln'
$exDir = Join-Path $csDir 'Exercises'
$runner = Join-Path $dsviz 'cs\dsviz-cs.exe'
$traceLog = Join-Path $dsviz 'vs-trace.log'

# שומר: אחרי 175 דקות סוגרים את Visual Studio וה-java, כדי שהסיכום, הצילומים והיומנים יישמרו גם אם משהו נתקע
Start-Process powershell -ArgumentList '-NoProfile', '-Command', 'Start-Sleep 10500; Get-Process devenv,javaw,java,dsviz-cs -ErrorAction SilentlyContinue | Stop-Process -Force' -WindowStyle Hidden | Out-Null

function KillJava() { Get-Process javaw, java, dsviz-cs -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue }
function Viewers() { @(Get-Process javaw, java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'הרצה ויזואלית' }) }
function JavaWindow([string]$pattern) { Get-Process javaw, java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match $pattern } | Select-Object -First 1 }
function RunnerProcs() { @(Get-CimInstance Win32_Process -Filter "Name='dsviz-cs.exe'" -ErrorAction SilentlyContinue | ForEach-Object { [string]$_.CommandLine }) }
# סוגר את מה שנשאר מהרצה רגילה: חלון הקונסול ("Press any key"), התוכנית עצמה
function KillRuns() {
    $names = @('VsDebugConsole', 'SdkApp', 'TopApp', 'SchoolRef', 'SchoolClassic', 'ClassicDsviz', 'PlainStack', 'NoMain', 'EmptyMain', 'WaitApp', 'SchoolExt', 'Exercise')
    Get-Process -Name $names -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Get-CimInstance Win32_Process -Filter "Name='cmd.exe' or Name='dotnet.exe' or Name='conhost.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'CiApps|DataStructures-CS|DSViz-loose' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}
function TraceTail([string]$pattern, [int]$n = 6) {
    if (-not (Test-Path $traceLog)) { Info 'יומן ההרחבה (vs-trace.log) עוד לא קיים'; return }
    @(Get-Content $traceLog -Encoding UTF8 | Where-Object { $_ -match $pattern } | Select-Object -Last $n) | ForEach-Object { Info "   יומן ההרחבה: $_" }
}
# חלונות קופצים של Visual Studio (לא החלון הראשי) והטקסטים שבהם — בשביל הודעות ("אין פעולת Main") ושאלות ("build errors")
function Dialogs() {
    $list = @()
    $main = 0; try { $main = [int]$script:vsMain } catch { }
    foreach ($w in (OtherWindows $script:vsP.Id $main)) {
        try {
            if ($w.Current.BoundingRectangle.IsEmpty) { continue }
            $all = @($w.FindAll([Windows.Automation.TreeScope]::Subtree, [Windows.Automation.Condition]::TrueCondition))
            if ($all.Count -gt 400) { continue }   # לא תיבת הודעה (חלון כלי צף וכו')
            $text = (@($all | ForEach-Object { ElName $_ } | Where-Object { $_ }) | Select-Object -Unique) -join ' | '
            $list += @{ Window = $w; Text = $text; Elements = $all }
        } catch { }
    }
    return @($list)
}
# שאלות של Visual Studio שעוצרות את הבדיקה: עונים כמו תלמיד זהיר, ורושמים
function DismissDialogs() {
    if (CloseSignIn) { return 'sign-in' }
    foreach ($d in (Dialogs)) {
        if ($d.Text -match 'There were build errors|build errors\. Would you like') {
            Step 'dialog-build-errors'
            $no = @($d.Elements | Where-Object { (ElName $_) -eq 'No' -and (ElType $_) -match 'Button' })
            if ($no.Count) { RealClick $no[0] 'build-errors-no' | Out-Null }
            return 'build-errors'
        }
        if ($d.Text -match 'File Modification Detected|has been modified outside|Reload All|\| Reload \|') {
            Step 'dialog-reload'
            $b = @($d.Elements | Where-Object { (ElName $_) -match '^(Reload|Reload All|Yes to All|Yes)$' -and (ElType $_) -match 'Button' })
            if ($b.Count) { RealClick $b[0] 'reload-yes' | Out-Null }
            return 'reload'
        }
        # "This project is out of date — would you like to build it?" (ההגדרה "Prompt to build" — ברירת המחדל בחלק מהגרסאות): כן
        if ($d.Text -match 'out of date|Would you like to build') {
            Step 'dialog-out-of-date'
            $b = @($d.Elements | Where-Object { (ElName $_) -eq 'Yes' -and (ElType $_) -match 'Button' })
            if ($b.Count) { RealClick $b[0] 'out-of-date-yes' | Out-Null }
            return 'out-of-date'
        }
        # "Inconsistent Line Endings — Do you want to normalize the line endings?": כן (Windows CR LF)
        if ($d.Text -match 'Inconsistent Line Endings|normalize the line endings') {
            Step 'dialog-line-endings'
            $b = @($d.Elements | Where-Object { (ElName $_) -eq 'Yes' -and (ElType $_) -match 'Button' })
            if ($b.Count) { RealClick $b[0] 'line-endings-yes' | Out-Null }
            return 'line-endings'
        }
        # אחרי סגירה בכוח: "Visual Studio has recovered the following files" — לא משחזרים (הכול נשמר לפני)
        if ($d.Text -match 'recovered the following files|Recovered Files') {
            Step 'dialog-recovered-files'
            $b = @($d.Elements | Where-Object { (ElName $_) -match '^(Do Not Recover.*|Don.t Recover.*|Close)$' -and (ElType $_) -match 'Button' })
            if ($b.Count) { RealClick $b[0] 'recovered-files-no' | Out-Null }
            return 'recovered-files'
        }
    }
    return ''
}
# הודעה של Visual Studio שהטקסט שלה מתאים לביטוי: @{ Text; Ok (כפתור OK, או $null) }, או $null כשאין. קודם בחלונות הקופצים;
# ואם לא נמצאה שם — בכל הרכיבים של Visual Studio (איטי יותר; בשביל גרסה שבה תיבת ההודעה נמצאת במקום אחר בעץ של UI Automation)
function FindMessage([string]$pattern) {
    foreach ($d in (Dialogs)) {
        if ($d.Text -match $pattern) {
            $ok = @($d.Elements | Where-Object { (ElName $_) -eq 'OK' -and (ElType $_) -match 'Button' }) | Select-Object -First 1
            return @{ Text = $d.Text; Ok = $ok }
        }
    }
    $e = @(ByPattern $script:vsP.Id $pattern) | Select-Object -First 1
    if ($e) {
        $ok = @(ByName $script:vsP.Id 'OK' 'Button') | Select-Object -First 1
        return @{ Text = (ElName $e); Ok = $ok }
    }
    return $null
}
# יומן ההרחבה (vs-trace.log): כמה שורות יש עכשיו, והשורות שנוספו מאז ומתאימות לביטוי — "מה ההרחבה עשתה בצעד הזה"
function TraceCount() { if (Test-Path $traceLog) { return @(Get-Content $traceLog -Encoding UTF8).Count }; return 0 }
function TraceSince([int]$from, [string]$pattern) {
    if (-not (Test-Path $traceLog)) { return @() }
    return @(Get-Content $traceLog -Encoding UTF8 | Select-Object -Skip $from | Where-Object { $_ -match $pattern })
}
# מחכה לסימון של תוכנית (עד $sec שניות). בדרך: עונה לשאלות של Visual Studio. מחזיר את הסימון או $null
function WaitMarker([string]$prog, [int]$sec = 180) {
    $t0 = Get-Date; $n = 0
    while (((Get-Date) - $t0).TotalSeconds -lt $sec) {
        $m = ReadMarker $prog
        if ($m) { return $m }
        Start-Sleep -Milliseconds 700
        $n++
        if ($n % 8 -eq 0) { $q = DismissDialogs; if ($q) { Info "Visual Studio שאל: $q"; $script:lastQuestion = $q } }
    }
    return $null
}

# ------------------------------------------------------------------ הכנה (DTE): איזה פרויקט הוא Startup, איזה קובץ פתוח
function Startup() { $s = Dte { param($d) (@($d.Solution.SolutionBuild.StartupProjects) -join ',') }; return [string]$s }
function SetStartup([string]$unique) { Dte { param($d) $d.Solution.SolutionBuild.StartupProjects = $unique; 'ok' } | Out-Null }
function OpenDoc([string]$file) {
    $r = Dte { param($d) $null = $d.ItemOperations.OpenFile($file); $d.ActiveDocument.Activate(); [string]$d.ActiveDocument.FullName }
    Start-Sleep -Milliseconds 1200
    return [string]$r
}
function ActiveDoc() { $r = Dte { param($d) if ($d.ActiveDocument) { [string]$d.ActiveDocument.FullName } else { '-' } } 6; return [string]$r }
function CloseDocs() { Dte { param($d) while ($d.Documents.Count -gt 0) { $d.Documents.Item(1).Close(2) }; 'ok' } 6 | Out-Null }
function DesignMode() { $r = Dte { param($d) [int]$d.Debugger.CurrentMode } 2; return ($r -eq 1) }

# ------------------------------------------------------------------ הפעולות שנבדקות: עכבר ומקלדת אמיתיים
# ההרצה הרגילה של Visual Studio. how: green — הכפתור הירוק בסרגל (עם דיבאגר); ctrlf5 — מקלדת; menu — Debug ← Start Without Debugging (שתי לחיצות)
# $target: מה שכתוב על הכפתור הירוק (שם ה-Startup Project / הפרופיל) — אם הכפתור נחשף ב-UI Automation בשם הזה
function NormalRun([string]$how, [string]$tag, [string]$target = '') {
    VsFront | Out-Null
    if ($how -eq 'green') {
        $b = @(ByName $script:vsP.Id 'Debug Target' 'SplitButton|Button|MenuItem')
        if ($b.Count -gt 0) {
            # הצד השמאלי של הכפתור (לא החץ שפותח את רשימת הפרופילים)
            $r = $b[0].Current.BoundingRectangle
            if (RealClickAt ([int]($r.X + [Math]::Min(26, $r.Width / 3))) ([int]($r.Y + $r.Height / 2)) "$tag-green-button" $b[0]) { return 'green (Debug Target)' }
        }
        foreach ($name in @($target, 'Start', 'Start Debugging', 'Continue') | Where-Object { $_ }) {
            $b = @(ByName $script:vsP.Id $name 'Button|SplitButton' | Where-Object { $_.Current.BoundingRectangle.Y -lt 200 })
            if ($b.Count -gt 0 -and (RealClick $b[0] "$tag-green-button")) { return "green ($name)" }
        }
        Info 'הכפתור הירוק לא נמצא ב-UI Automation — עוברים לתפריט Debug'
        DumpUia $script:vsP.Id "$tag-no-green"
        $how = 'menu'
    }
    if ($how -eq 'menu') {
        $m = @(ByName $script:vsP.Id 'Debug' 'MenuItem' | Where-Object { $_.Current.BoundingRectangle.Y -lt 80 })
        if ($m.Count -gt 0 -and (RealClick $m[0] "$tag-menu-debug")) {
            $i = WaitFor { $x = @(ByName $script:vsP.Id 'Start Without Debugging' 'MenuItem'); if ($x.Count) { $x[0] } } 8
            if ($i -and (RealClick $i "$tag-menu-start-without-debugging")) { return 'menu (Debug > Start Without Debugging)' }
            [DsReal]::Chord([uint16[]]@(0x1B)); Start-Sleep -Milliseconds 300; [DsReal]::Chord([uint16[]]@(0x1B))
        }
        Info 'התפריט Debug > Start Without Debugging לא נמצא — עוברים ל-Ctrl+F5'
        DumpUia $script:vsP.Id "$tag-no-debug-menu"
    }
    [DsReal]::Chord([uint16[]]@(0x11, 0x74))   # Ctrl+F5
    Start-Sleep -Milliseconds 700
    Step "$tag-ctrl-f5"
    return 'keyboard (Ctrl+F5)'
}
# לחיצה אמיתית על כפתור של DSViz בסרגל
function ClickToolbar([string]$name, [string]$tag) {
    VsFront | Out-Null
    $b = WaitFor { ToolbarButton $name } 20 1000
    if (-not $b) { Info "הכפתור '$name' לא נמצא בסרגל"; Step "$tag-no-button"; DumpUia $script:vsP.Id "$tag-no-button"; return $false }
    return (RealClick $b $tag)
}
# Build Solution: לחיצות אמיתיות בתפריט Build. מחכה שהבנייה תיגמר; מחזיר כמה פרויקטים נכשלו (-1 = לא ידוע)
function BuildSolution([string]$tag, [int]$sec = 420) {
    VsFront | Out-Null
    $how = ''
    $m = @(ByName $script:vsP.Id 'Build' 'MenuItem' | Where-Object { $_.Current.BoundingRectangle.Y -lt 80 })
    if ($m.Count -gt 0 -and (RealClick $m[0] "$tag-menu-build")) {
        $i = WaitFor { $x = @(ByName $script:vsP.Id 'Build Solution' 'MenuItem'); if ($x.Count) { $x[0] } } 8
        if ($i -and (RealClick $i "$tag-menu-build-solution")) { $how = 'menu (Build > Build Solution)' }
        else { [DsReal]::Chord([uint16[]]@(0x1B)); Start-Sleep -Milliseconds 300; [DsReal]::Chord([uint16[]]@(0x1B)) }
    }
    if (-not $how) { [DsReal]::Chord([uint16[]]@(0x11, 0x10, 0x42)); $how = 'keyboard (Ctrl+Shift+B)'; Step "$tag-ctrl-shift-b" }
    Info "Build Solution: $how"
    # קודם מחכים שהבנייה תתחיל (אחרת "הסתיימה" הוא של בנייה קודמת), ואז שתיגמר. vsBuildState: 2 = בתהליך, 3 = הסתיימה
    $t0 = Get-Date; $state = 0; $started = $false
    while (((Get-Date) - $t0).TotalSeconds -lt 20) {
        $state = Dte { param($d) [int]$d.Solution.SolutionBuild.BuildState } 3
        if ($state -eq 2) { $started = $true; break }
        Start-Sleep -Milliseconds 400
    }
    while (((Get-Date) - $t0).TotalSeconds -lt $sec) {
        $state = Dte { param($d) [int]$d.Solution.SolutionBuild.BuildState } 4
        if ($state -eq 3) { break }
        Start-Sleep 2
    }
    if (-not $started) { Info 'הבנייה לא נראתה "בתהליך" (מהירה מאוד, או שהלחיצה לא התחילה בנייה) — ראו בצילום את חלון Output' }
    $failed = Dte { param($d) [int]$d.Solution.SolutionBuild.LastBuildInfo } 6
    Info ("הבנייה: מצב {0}, {1:N0} שניות, פרויקטים שנכשלו: {2}" -f $state, ((Get-Date) - $t0).TotalSeconds, $failed)
    Step "$tag-built"
    if ($state -ne 3 -or $null -eq $failed) { return -1 }
    return [int]$failed
}
# הרצה רגילה ועוד ניסיון אחד: אם אחרי 90 שניות התוכנית לא רצה, Visual Studio לא בונה ולא מריץ (עדיין במצב עריכה) — הלחיצה לא נקלטה
# (בריצות 5–6: חלון הכניסה של Microsoft ש-Visual Studio 2022 Enterprise פותח לבד בלחיצה הראשונה אחרי הפתיחה). לוחצים שוב, כמו תלמיד, ורושמים
function NormalRunRetry([string]$how, [string]$tag, [string]$target, [string]$prog) {
    $used = NormalRun $how $tag $target
    $m = WaitMarker $prog 90
    if (-not $m) {
        $building = (Dte { param($d) [int]$d.Solution.SolutionBuild.BuildState } 3) -eq 2
        if ((DesignMode) -and -not $building -and @(RunnerProcs).Count -eq 0) {
            DumpTopWindows "$tag-no-start"; CloseSignIn | Out-Null
            Info "ההרצה הרגילה ($used) לא התחילה אחרי 90 שניות (Visual Studio במצב עריכה, לא בונה) — הלחיצה לא נקלטה; לוחצים שוב"
            $used = (NormalRun $how "$tag-again" $target) + ' — בלחיצה השנייה'
        }
        $m = WaitMarker $prog 180
    }
    return @{ M = $m; Used = $used }
}
# הרצה רגילה של פרויקט ובדיקה שהיא רגילה: קונסול (הסימון נכתב מהתוכנית עצמה, לא מ-dsviz-cs), הספרייה הצפויה, בלי dsviz-cs
function CheckNormal([string]$id, [string]$project, [string]$file, [string]$prog, [string]$how, [string]$lib, [string]$what) {
    KillRuns; KillJava
    SetStartup "$project\$project.csproj"
    $act = OpenDoc $file
    ClearMarkers
    $r = NormalRunRetry $how $id $project $prog
    $used = $r.Used; $m = $r.M
    Start-Sleep 2
    Step "$id-after"
    $dsvizRan = @(RunnerProcs).Count
    $why = "הפעלה: $used · פעיל: $act · Startup: $(Startup) · סימונים: $(Markers)"
    if (-not $m) { Fail $id "$what — הרצה רגילה ($used)" "התוכנית לא רצה תוך 4 דקות · $why"; DumpUia $script:vsP.Id "$id-no-run" }
    else {
        Check $id (($m.Host -ne 'dsviz-cs') -and ($dsvizRan -eq 0)) "$what — הרצה רגילה ($used): רצה התוכנית עצמה בקונסול (תהליך $($m.Host)), בלי ההרצה הוויזואלית" $why
        if ($lib) { Check "$id-lib" ($m.Lib -eq $lib) "$what — בהרצה הרגילה הספרייה היא $lib" "בפועל: '$($m.Lib)'" }
    }
    for ($i = 0; $i -lt 30 -and -not (DesignMode); $i++) { Start-Sleep 1 }
    KillRuns
    Start-Sleep 1
    return $m
}
# "הרצה ויזואלית" בסרגל על הקובץ, ובדיקה שנפתח החלון של DSViz ושהקוד רץ בתוך dsviz-cs עם הספרייה של DSViz
function CheckVisual([string]$id, [string]$file, [string]$prog, [string]$lib, [string]$what) {
    KillRuns; KillJava
    $act = OpenDoc $file
    ClearMarkers
    $tcClick = TraceCount
    $clicked = ClickToolbar 'הרצה ויזואלית' "$id-toolbar-visual-run"
    $m = $null
    if ($clicked) {
        # ההרחבה רושמת כל לחיצה שהגיעה אליה ('visual run (toolbar)'). אין רישום אחרי 20 שניות — הלחיצה לא נקלטה (חלון הכניסה של Microsoft); לוחצים שוב
        if (-not (WaitFor { @(TraceSince $tcClick 'visual run \(toolbar\)').Count -gt 0 } 20)) {
            DumpTopWindows "$id-click-lost"; CloseSignIn | Out-Null
            Info "הלחיצה על 'הרצה ויזואלית' לא הגיעה להרחבה (אין רישום ביומן) — לוחצים שוב"
            $clicked = ClickToolbar 'הרצה ויזואלית' "$id-toolbar-visual-run-again"
        }
        $m = WaitMarker $prog 120
    }
    $v = WaitFor { $x = @(Viewers); if ($x.Count) { $x } } 90
    Start-Sleep 3
    Step "$id-viewer"
    $why = "פעיל: $act · סימונים: $(Markers) · חלונות: $((Windows) -join ' ; ')"
    Check $id ($clicked -and $m -and $m.Host -eq 'dsviz-cs' -and $v) "$what — 'הרצה ויזואלית' בסרגל: הקוד רץ בהרצה הוויזואלית ונפתח החלון של DSViz" $why
    if ($lib -and $m) { Check "$id-lib" ($m.Lib -eq $lib) "$what — בהרצה הוויזואלית הספרייה היא $lib (של DSViz)" "בפועל: '$($m.Lib)'" }
    if (-not ($m -and $v)) { TraceTail 'visual run|info bar' }
    KillJava
    Start-Sleep 1
}
# "הרצה ויזואלית" בסרגל שאמורה להסביר ולא להריץ: מחכים לתיבת ההודעה, בודקים את הטקסט, ולוחצים OK
function CheckMessage([string]$id, [string]$file, [string]$pattern, [string]$what) {
    KillRuns; KillJava
    $act = OpenDoc $file
    ClearMarkers
    $clicked = ClickToolbar 'הרצה ויזואלית' "$id-toolbar-visual-run"
    $dlg = $null
    if ($clicked) { $dlg = WaitFor { FindMessage $pattern } 90 1000 }
    Step "$id-message"
    $all = (@(Dialogs | ForEach-Object { $_.Text }) -join ' || ')
    Check $id ($null -ne $dlg) "$what — הודעה ברורה במקום 'שום דבר לא קורה'" "פעיל: $act · חלונות קופצים: $all · סימונים: $(Markers)"
    Check "$id-no-run" (@(Viewers).Count -eq 0 -and -not (Markers)) "$what — לא רץ שום קוד אחר (אין חלון הרצה, אין סימון)" "סימונים: $(Markers)"
    if ($dlg) {
        Info ("ההודעה: " + $dlg.Text.Substring(0, [Math]::Min(400, $dlg.Text.Length)))
        if ($dlg.Ok) { RealClick $dlg.Ok "$id-ok" | Out-Null } else { Info 'כפתור OK לא נמצא — Enter'; [DsReal]::Chord([uint16[]]@(0x0D)); Step "$id-enter" }
        Start-Sleep 1
        # ההודעה נסגרה? אם עדיין יש כפתור OK על המסך וההודעה שם — Enter (אחרת היא חוסמת את כל מה שבא אחריה)
        if (@(ByName $script:vsP.Id 'OK' 'Button').Count -gt 0 -and (FindMessage $pattern)) { [DsReal]::Chord([uint16[]]@(0x0D)); Start-Sleep 1; Step "$id-enter-again" }
    } else { TraceTail 'visual run'; DumpUia $script:vsP.Id "$id-no-message" }
    KillJava
}

# ------------------------------------------------------------------ הסרגל
function DteToolbar() {
    $r = Dte {
        param($d)
        $bars = $d.CommandBars; $ds = $bars.Item('DSViz'); $shared = @()
        foreach ($nm in 'Standard', 'Build', 'Debug', 'Text Editor', 'Debug Location', 'Layout', 'Source Control', 'Web Browser', 'Workflow', 'XML Editor') {
            try { $b = $bars.Item($nm); if ($b.Visible -and $b.Position -eq $ds.Position -and $b.RowIndex -eq $ds.RowIndex) { $shared += $nm } } catch { }
        }
        "visible=$($ds.Visible) row=$($ds.RowIndex) shared=$($shared -join ',')"
    } 3
    return [string]$r
}
function CheckToolbar([string]$id, [string]$when) {
    $ready = WaitToolbar 240
    $row = ''
    for ($i = 0; $i -lt 30; $i++) { $row = DteToolbar; if ($row -match '^visible=True row=-?\d+ shared=$') { break }; Start-Sleep 4 }
    Step "$id-toolbar"
    $main = $null; try { $main = [Windows.Automation.AutomationElement]::FromHandle($script:vsMain).Current.BoundingRectangle } catch { }
    $bad = @(); $ys = @(); $rects = @()
    foreach ($n in $script:dsvizNames) {
        $b = ToolbarButton $n
        if (-not $b) { $bad += "$n (לא נמצא)"; continue }
        $c = $b.Current; $r = $c.BoundingRectangle
        $inside = (-not $main) -or ($r.Left -ge $main.Left - 2 -and $r.Right -le $main.Right + 2)
        if (-not $c.IsEnabled -or -not $inside -or $r.Width -lt 8) { $bad += "$n ($(DescEl $b))" }
        $ys += [int]($r.Y + $r.Height / 2); $rects += $r
    }
    Check "$id-buttons" ($ready -and $bad.Count -eq 0 -and $ys.Count -eq $script:dsvizNames.Count) "$when — כל $($script:dsvizNames.Count) הכפתורים של DSViz בסרגל, גלויים ולחיצים (לא אפורים, לא מאחורי החץ 'עוד')" ($bad -join ' ; ')
    if ($bad.Count -gt 0) { DumpUia $script:vsP.Id "$id-toolbar" }
    $oneLine = ($ys.Count -gt 0) -and ((($ys | Measure-Object -Maximum).Maximum - ($ys | Measure-Object -Minimum).Minimum) -le 8)
    # הסרגל Standard (שמירה, ביטול...) בשורה אחרת? לפי המלבן של הסרגל ב-UI Automation, אם הוא נחשף בשם הזה
    $std = ''
    try {
        $tb = New-Object Windows.Automation.AndCondition((New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ControlTypeProperty, [Windows.Automation.ControlType]::ToolBar)),
            (New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::NameProperty, 'Standard')))
        $s = @([Windows.Automation.AutomationElement]::FromHandle($script:vsMain).FindAll([Windows.Automation.TreeScope]::Descendants, $tb) | Where-Object { Shown $_ })
        if ($s.Count -gt 0 -and $ys.Count -gt 0) { $sr = $s[0].Current.BoundingRectangle; $std = "Standard y=$([int]$sr.Y)..$([int]$sr.Bottom) · DSViz y=$($ys[0])"; if ($ys[0] -ge $sr.Y -and $ys[0] -le $sr.Bottom) { $std += ' · אותה שורה!' } }
    } catch { }
    Check "$id-own-row" (($row -match '^visible=True row=-?\d+ shared=$') -and $oneLine -and ($std -notmatch 'אותה שורה')) "$when — הסרגל של DSViz גלוי ובשורה משלו" "DTE: $row · הכפתורים בשורה אחת: $oneLine (y: $($ys -join ',')) · $std"
    TraceTail 'toolbar row' 3
}
function CheckLoaded([string]$id) {
    $mark = Join-Path $dsviz ("vs-loaded-{0}.0_{1}.txt" -f $script:vsMajor, $script:vsInst.instanceId)
    $text = ''
    $ok = WaitFor { Test-Path $mark } 60
    if ($ok) { $text = (Get-Content $mark -Raw -Encoding UTF8) }
    Check $id ($ok -and $text -match "build=$([regex]::Escape($script:kitVersion))") "ההרחבה כתבה שהיא נטענה ב-$title (vs-loaded): הגרסה שהותקנה ($script:kitVersion)" "$(Split-Path -Leaf $mark): $($text -replace "`r?`n", ' · ') · קבצים: $((Get-ChildItem $dsviz -Filter 'vs-loaded-*' -ErrorAction SilentlyContinue | ForEach-Object Name) -join ', ')"
}

# ------------------------------------------------------------------ מאגר השאלות (חלון Swing): ה-agent רק אומר איפה הרכיב — הלחיצה אמיתית
$ctl = 'C:\dsagent'
function WriteScript([string]$mode, [string[]]$lines) { [IO.File]::WriteAllLines((Join-Path $ctl "$mode.script.txt"), [string[]]$lines, (New-Object Text.UTF8Encoding($false))) }
function AgentTail() { $f = Join-Path $ctl 'agent.log'; if (Test-Path $f) { @(Get-Content $f -Encoding UTF8 -Tail 12) | ForEach-Object { Info "   agent: $_" } } }
# מריץ תסריט של ה-agent בחלון שהכפתור בסרגל פותח. בכל mark: צילום, ואם ה-agent כתב מיקום (target.txt) — לחיצת עכבר אמיתית שם.
# מחזיר done / error / timeout / gate (נפתח מסך הכניסה במקום המאגר)
function RunBank([string]$tag, [string[]]$lines, [int]$timeoutSec = 420) {
    Get-ChildItem $ctl -Filter 'mark-*' -ErrorAction SilentlyContinue | Remove-Item -Force
    Get-ChildItem $ctl -Filter 'ack-*' -ErrorAction SilentlyContinue | Remove-Item -Force
    Remove-Item (Join-Path $ctl 'bank.done.txt'), (Join-Path $ctl 'bank.error.txt'), (Join-Path $ctl 'target.txt'), (Join-Path $ctl 'bank.script.txt'), (Join-Path $ctl 'bank.script.txt.claimed') -Force -ErrorAction SilentlyContinue
    KillJava; Start-Sleep 1
    WriteScript 'bank' $lines
    if (-not (ClickToolbar 'מאגר שאלות' "$tag-toolbar-bank")) { return 'no-button' }
    $t0 = Get-Date; $seen = @{}; $status = 'timeout'; $again = $false; $picked = $false
    while (((Get-Date) - $t0).TotalSeconds -lt $timeoutSec) {
        foreach ($m in @(Get-ChildItem $ctl -Filter 'mark-*.txt' -ErrorAction SilentlyContinue)) {
            if ($seen[$m.Name]) { continue }
            $seen[$m.Name] = $true
            $nm = $m.BaseName.Substring(5)
            Start-Sleep -Milliseconds 500
            $tf = Join-Path $ctl 'target.txt'
            $clickedAt = ''
            if (Test-Path $tf) {
                $v = @(([IO.File]::ReadAllText($tf)).Trim() -split '\s+' | Where-Object { $_ -match '^-?\d+$' } | ForEach-Object { [int]$_ })
                Remove-Item $tf -Force -ErrorAction SilentlyContinue
                $found = ($v.Count -eq 4 -and $v[2] -gt 0)
                # השאלה כבר נראית (הנושא פתוח)? אז לא לוחצים על הנושא (לחיצה נוספת הייתה סוגרת אותו), ולא לוחצים על השאלה פעמיים
                $skip = ($nm -in 'topic', 'question') -and $picked
                if ($found -and -not $skip) {
                    # שורות המאגר רחבות: לוחצים בצד ימין שלהן (שם הטקסט), כפתור — באמצע
                    $x = if ($nm -match 'topic|question') { [int]($v[0] + $v[2] * 0.7) } else { [int]($v[0] + $v[2] / 2) }
                    $y = [int]($v[1] + $v[3] / 2)
                    # מה באמת נמצא בנקודה: חלון המאגר (java), או חלון אחר שמכסה אותו? אם אחר — מביאים את חלון המאגר לקדמה לפני הלחיצה
                    $at = ''
                    try {
                        $hp = [Windows.Automation.AutomationElement]::FromPoint((New-Object Windows.Point $x, $y)).Current.ProcessId
                        $at = (Get-Process -Id $hp -ErrorAction SilentlyContinue).ProcessName
                    } catch { }
                    if ($at -notmatch '^javaw?$') {
                        Info "בנקודה ($x,$y) נמצא חלון של '$at' ולא חלון המאגר — מביאים את חלון המאגר לקדמה"
                        $jw = JavaWindow 'מאגר תרגילים'
                        if ($jw) { [DsReal]::Front($jw.MainWindowHandle) | Out-Null; Start-Sleep -Milliseconds 500 }
                    }
                    RealClickAt $x $y "$tag-$nm" | Out-Null
                    $clickedAt = "$x,$y"
                    if ($nm -match 'question') { $picked = $true }
                } elseif ($skip) { Step "$tag-$nm-skipped" }
                elseif ($nm -eq 'question-if-open') { Step "$tag-$nm-topic-closed" }
                else { Info "ה-agent לא מצא את הרכיב של '$nm' (target.txt: '$($v -join ' ')')"; Step "$tag-$nm-not-found" }
            } else { Step "$tag-$nm" }
            Info ("mark {0}{1} · חלונות: {2}" -f $nm, $(if ($clickedAt) { " — לחיצה ב-($clickedAt)" } else { '' }), ((Windows) -join ' ; '))
            Set-Content (Join-Path $ctl "ack-$nm.txt") 'ok'
        }
        if (Test-Path (Join-Path $ctl 'bank.done.txt')) { $status = 'done'; break }
        if (Test-Path (Join-Path $ctl 'bank.error.txt')) { $status = 'error'; break }
        if ($seen.Count -eq 0 -and (JavaWindow 'כניסה למאגר')) { Start-Sleep 2; Step "$tag-gate-instead"; $status = 'gate'; break }
        if (-not $again -and $seen.Count -eq 0 -and ((Get-Date) - $t0).TotalSeconds -gt 90 -and (Test-Path (Join-Path $ctl 'bank.script.txt'))) {
            $again = $true
            Info 'הכלי לא עלה אחרי 90 שניות — לוחצים שוב על הכפתור'
            ClickToolbar 'מאגר שאלות' "$tag-toolbar-bank-again" | Out-Null
        }
        Start-Sleep -Milliseconds 500
    }
    if ($status -eq 'error') { Info ("שגיאת agent: " + (Get-Content (Join-Path $ctl 'bank.error.txt') -Raw -Encoding UTF8)) }
    Info "המאגר ($tag): $status ($([int]((Get-Date) - $t0).TotalSeconds) שניות)"
    if ($status -ne 'done') { AgentTail }
    return $status
}
# פותח את המאגר מהסרגל, בוחר נושא ושאלה ולוחץ "צור תרגיל" — הכול בלחיצות אמיתיות. מחזיר את שם הפרויקט שנוצר, או ''
function CreateFromBank([string]$tag, [string]$topic, [string]$question) {
    $before = @(Get-ChildItem $exDir -Directory -ErrorAction SilentlyContinue | ForEach-Object Name)
    $steps = @(
        'waitwin מאגר תרגילים', 'wait 2500', 'mark window',
        "targetrow question $question", 'mark question-if-open', 'wait 1200',   # הנושא כבר פתוח: השאלה נראית — לוחצים עליה ישר
        "targetrow topic $topic", 'mark topic', 'wait 1500',                      # אחרת: לחיצה על הנושא פותחת אותו
        "targetrow question $question", 'mark question', 'wait 1500',
        'ptarget צור תרגיל', 'mark create',
        'waitwin השאלה', 'wait 4000', 'mark float'
    )
    $st = RunBank $tag $steps
    if ($st -eq 'gate') {
        Note "$tag-seed" 'הכניסה השמורה (מפתח בדיקה) לא התקבלה ונפתח מסך הכניסה — ממשיכים עם dsviz.access=off (בלי מסך כניסה)'
        $st = RunBank "$tag-off" (@('#access=off') + $steps)
    } else { $script:seedWorked = ($st -eq 'done') }
    Start-Sleep 6
    $q = DismissDialogs
    if ($q -eq 'reload') { Note "$tag-reload" 'Visual Studio שאל אם לטעון מחדש את הפתרון אחרי יצירת התרגיל (הפרויקט נרשם בקובץ ולא דרך Visual Studio)' }
    Step "$tag-after-create"
    $new = @(Get-ChildItem $exDir -Directory -ErrorAction SilentlyContinue | ForEach-Object Name | Where-Object { $before -notcontains $_ })
    Check "$tag-create" (($st -eq 'done') -and $new.Count -ge 1) "המאגר נפתח מהסרגל, ובלחיצות עכבר: הנושא '$topic', השאלה '$question', 'צור תרגיל' — נוצר תרגיל, ונפתח חלון 'השאלה'" "מצב: $st · תיקיות חדשות: $($new -join ', ') · חלונות: $((Windows) -join ' ; ')"
    if ($new.Count -ge 1) { return [string]$new[0] }
    return ''
}
# אחרי "צור תרגיל": הקבצים בדיסק, הפרויקט בפתרון וב-Solution Explorer, הקובץ פתוח בעורך
function CheckCreated([string]$tag, [string]$name) {
    $dir = Join-Path $exDir $name
    $cs = @(Get-ChildItem $dir -Filter '*.cs' -ErrorAction SilentlyContinue)
    Check "$tag-files" ((Test-Path (Join-Path $dir "$name.csproj")) -and $cs.Count -ge 1 -and (Test-Path (Join-Path $dir 'Properties\launchSettings.json'))) "הקבצים בדיסק: Exercises\$name\$name.csproj, קובצי ‎.cs‎ ו-launchSettings.json" "$(($cs | ForEach-Object Name) -join ', ')"
    $first = ''
    try { $first = (Get-Content (Join-Path $dir 'Properties\launchSettings.json') -Raw -Encoding UTF8 | ConvertFrom-Json).profiles.PSObject.Properties.Name | Select-Object -First 1 } catch { }
    Check "$tag-normal-first" ($first -eq 'הרצה רגילה') "בתרגיל החדש הפרופיל הראשון (ברירת המחדל של הכפתור הירוק) הוא 'הרצה רגילה'" "ראשון: '$first'"
    $slnText = Get-Content $exSln -Raw -Encoding UTF8
    Check "$tag-sln" ($slnText -match [regex]::Escape("$name.csproj")) "הפרויקט $name רשום בפתרון DataStructures-CS.sln"
    KillJava   # החלון הצף "השאלה" תמיד מעל Visual Studio — סוגרים אותו לפני שמסתכלים ולוחצים
    Start-Sleep 2
    VsFront | Out-Null
    $node = WaitFor { $x = @(ByName $script:vsP.Id $name 'TreeItem' -Any); if ($x.Count) { $x[0] } } 40
    # כל הרמות: פרויקט בתוך תיקיית פתרון (Exercises) ואולי בתוך תיקייה נוספת. אם לא נמצא — השמות שכן נראו, בשביל ההסבר
    $inDte = Dte { param($d)
        $todo = New-Object System.Collections.Stack; $seen = @(); $n = 0
        foreach ($p in $d.Solution.Projects) { $todo.Push($p) }
        while ($todo.Count -gt 0 -and $n -lt 400) {
            $n++; $p = $todo.Pop()
            $pn = [string](ComGet $p 'Name')
            if ($pn -eq $name -or ([string](ComGet $p 'UniqueName')) -like "*\$name.csproj") { return 'yes' }
            $seen += $pn
            if ([string](ComGet $p 'Kind') -ne '{66A26720-8FB5-11D2-AA7E-00C04F688DDE}') { continue }   # רק תיקיית פתרון מכילה פרויקטים
            $items = ComGet $p 'ProjectItems'
            $cnt = [int](ComGet $items 'Count')
            for ($k = 1; $k -le $cnt -and $k -le 200; $k++) {
                $it = $null
                try { $it = [System.__ComObject].InvokeMember('Item', [Reflection.BindingFlags]::InvokeMethod, $null, $items, @([object]$k)) } catch { try { $it = $items.Item($k) } catch { } }
                $sub = ComGet $it 'SubProject'
                if ($sub) { $todo.Push($sub) }
            }
        }
        'no (' + ($seen -join ', ') + ')' } 8
    Step "$tag-solution-explorer"
    # הפתרון ש-Solution Explorer מציג (DTE) הוא הקובע; הצומת עצמו ב-UI Automation קיים רק כשהתיקייה שמעליו פתוחה (העץ וירטואלי)
    Check "$tag-solution-explorer" ([string]$inDte -eq 'yes') "הפרויקט $name נמצא בפתרון שפתוח ב-Visual Studio — Solution Explorer מציג אותו (בלי טעינה מחדש)" "ב-DTE: $inDte · הצומת ב-UI Automation: $(DescEl $node)"
    if ($node) { Pass "$tag-solution-explorer-node" "הצומת של $name נראה ב-Solution Explorer ($(DescEl $node))" }
    else { Note "$tag-solution-explorer-node" "הצומת של $name לא נמצא ב-UI Automation של Solution Explorer (תיקייה סגורה / עץ וירטואלי) — ראו בצילום"; DumpUia $script:vsP.Id "$tag-solution-explorer" }
    $act = ActiveDoc
    $tab = @()
    if ($cs.Count) { $tab = @($cs | ForEach-Object { ByName $script:vsP.Id $_.Name 'TabItem' -Any }) }
    Check "$tag-editor" (($act -like "$dir\*") -and ($act -like '*.cs')) "קובץ ה-C# של התרגיל פתוח בעורך (הקובץ הפעיל)" "פעיל: $act · לשוניות: $(($tab | ForEach-Object { ElName $_ }) -join ', ')"
    return $dir
}
# תרגיל מהמאגר נוצר בכוונה בלי return בפעולות שמחזירות ערך (התלמיד כותב בעצמו — AiScaffold.stub, כמו ב-Eclipse), ולכן עד שהתלמיד
# כותב את הפעולות הוא לא מתקמפל. קודם בודקים מה התלמיד רואה אז (הרצה ויזואלית מסבירה את שגיאת הקומפילציה), ואז "התלמיד פותר":
# ההצעה לפתרון מהמאגר נכתבת במקום הפעולה הריקה (רק הפעולות שכבר בקובץ — הסעיפים נוספים אחד אחד). מחזיר כמה פעולות נכתבו
function SolveFromBank([string]$dir, [string]$topic, [string]$question) {
    $j = Get-Content (Join-Path $root "bank\$topic\$question.json") -Raw -Encoding UTF8 | ConvertFrom-Json
    $n = 0
    foreach ($t in @($j.csharp.exercise.tasks)) {
        if (-not $t.solution) { continue }
        $sol = @($t.solution -split "`r?`n")
        $sig = $sol[0].Trim()
        foreach ($f in @(Get-ChildItem $dir -Filter '*.cs' -ErrorAction SilentlyContinue)) {
            $lines = New-Object 'System.Collections.Generic.List[string]'
            $lines.AddRange([string[]]@(Get-Content $f.FullName -Encoding UTF8))
            $i = -1; for ($k = 0; $k -lt $lines.Count; $k++) { if ($lines[$k].Trim() -eq $sig) { $i = $k; break } }
            if ($i -lt 0) { continue }
            $e = -1; for ($k = $i + 1; $k -lt $lines.Count; $k++) { if ($lines[$k] -eq '    }') { $e = $k; break } }
            if ($e -lt 0) { continue }
            $lines.RemoveRange($i, $e - $i + 1)
            $lines.InsertRange($i, [string[]]@($sol | ForEach-Object { if ($_) { '    ' + $_ } else { '' } }))
            [IO.File]::WriteAllLines($f.FullName, $lines, (New-Object Text.UTF8Encoding($true)))
            $n++; break
        }
    }
    return $n
}
function BankUnsolvedThenSolve([string]$tag, [string]$dir, [string]$topic, [string]$question) {
    $main = @(Get-ChildItem $dir -Filter '*.cs' -ErrorAction SilentlyContinue | Where-Object { (Get-Content $_.FullName -Raw -Encoding UTF8) -match 'static (void|int) Main' } | Select-Object -First 1)
    if ($main.Count) { CheckMessage "$tag-unsolved-message" $main[0].FullName 'error CS\d+' "תרגיל חדש שעוד לא נפתר (פעולה בלי return)" }
    CloseDocs
    $n = SolveFromBank $dir $topic $question
    Check "$tag-solved" ($n -ge 1) "התלמיד כתב את הפתרון בפעולות של התרגיל ($question, ההצעה מהמאגר)" "פעולות שנכתבו: $n"
}
# התרגיל שנוצר: כשהקובץ שלו פתוח הוא ה-Startup Project (פתרון התרגילים), ו"הרצה ויזואלית" בסרגל (לחיצה אמיתית) פותחת את החלון של DSViz
function CheckBankRun([string]$tag, [string]$name, [string]$dir) {
    $main = @(Get-ChildItem $dir -Filter '*.cs' -ErrorAction SilentlyContinue | Where-Object { (Get-Content $_.FullName -Raw -Encoding UTF8) -match 'static (void|int) Main' } | Select-Object -First 1)
    if ($main.Count -eq 0) { Fail "$tag-visual-run" 'בתרגיל מהמאגר יש קובץ עם Main' $dir; return }
    KillRuns; KillJava
    OpenDoc $main[0].FullName | Out-Null
    WaitFor { (Startup) -like "*$name*" } 12 | Out-Null
    Info "Startup אחרי פתיחת הקובץ של התרגיל: $(Startup)"
    Check "$tag-startup-follows" ((Startup) -like "*$name*") 'בפתרון התרגילים: התרגיל שפתוח הוא ה-Startup Project (הכפתור הירוק יריץ אותו, לא את תרגיל המחסנית)' (Startup)
    $clicked = ClickToolbar 'הרצה ויזואלית' "$tag-toolbar-visual-run"
    $v = WaitFor { $x = @(Viewers); if ($x.Count) { $x } } 150
    Start-Sleep 4; Step "$tag-viewer"
    $msg = (@(Dialogs | ForEach-Object { $_.Text }) -join ' || ')
    Check "$tag-visual-run" ($clicked -and $v) "התרגיל מהמאגר: 'הרצה ויזואלית' בסרגל פותחת את החלון של DSViz" "חלונות: $((Windows) -join ' ; ') · הודעות: $msg"
    if (-not $v) { TraceTail 'visual run'; foreach ($d in (Dialogs)) { $ok = @($d.Elements | Where-Object { (ElName $_) -eq 'OK' -and (ElType $_) -match 'Button' }); if ($ok.Count) { RealClick $ok[0] "$tag-message-ok" | Out-Null } } }
    KillJava
}

# ====================================================================================================================
Section "0. הסביבה — $title"
$all = VsInstances
foreach ($i in $all) { Info ("מופע: {0} {1} [{2}] {3} · id={4}" -f $i.displayName, $i.catalog.productDisplayVersion, $i.productId, $i.installationPath, $i.instanceId) }
$fits = @($all | Where-Object { ([string]$_.productId -match 'Product\.(Community|Professional|Enterprise)$') -and ([version]$_.installationVersion).Major -ge 17 })
$script:vsInst = $fits | Where-Object { ([version]$_.installationVersion).Major -eq $script:vsMajor } | Sort-Object { [version]$_.installationVersion } | Select-Object -Last 1
if (-not $script:vsInst) { Fail 'env-vs' "$title מותקן במחשב" "אין מופע בגרסה $Major"; Save-Summary $title; exit 1 }
$script:devenv = Join-Path $script:vsInst.installationPath 'Common7\IDE\devenv.exe'
$msbuild = @((Join-Path $script:vsInst.installationPath 'MSBuild\Current\Bin\amd64\MSBuild.exe'), (Join-Path $script:vsInst.installationPath 'MSBuild\Current\Bin\MSBuild.exe')) | Where-Object { Test-Path $_ } | Select-Object -First 1
$hive = "{0}.0_{1}" -f $script:vsMajor, $script:vsInst.instanceId
$title = "{0} ({1} {2})" -f $title, $script:vsInst.displayName, $script:vsInst.catalog.productDisplayVersion
Info ("Windows: {0} · מסך: {1} · DPI: {2} · PowerShell {3}" -f [Environment]::OSVersion.VersionString, [Windows.Forms.SystemInformation]::VirtualScreen.Size, [DsReal]::Dpi(), $PSVersionTable.PSVersion)
Info "נבדק: $title · devenv: $script:devenv · msbuild: $msbuild"
Check 'env-vs' ((Test-Path $script:devenv) -and $msbuild) "${title} — יש devenv ו-MSBuild" "$script:devenv"
Info ("‎.NET SDKs: " + ((& dotnet --list-sdks 2>&1) -join ' ; '))
Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
KillJava
Step 'desktop'

# ------------------------------------------------------------------ T0
Section 'T0. התקנה — כמו DSViz-Setup.exe (חבילת C#), ואז install-vsix.ps1'
$t = Get-Date
$o = @(); $code = -1
try { $o = & (Join-Path $PSScriptRoot 'install-kit.ps1') -Kit $Kit 2>&1; $code = $LASTEXITCODE } catch { Fail 'install-kit' 'הערכה הותקנה (install-kit.ps1)' "$_" }
$o | ForEach-Object { Info "$_" }
Info ("ההתקנה: קוד {0}, {1:N0} שניות" -f $code, ((Get-Date) - $t).TotalSeconds)
foreach ($f in @($runner, (Join-Path $dsviz 'dsviz.jar'), (Join-Path $dsviz 'jdk\bin\javaw.exe'), (Join-Path $dsviz 'bank\dsviz-bank-vs.jar'), $exSln, (Join-Path $csDir 'lib\Unit4.CollectionsLib.dll'),
        (Join-Path $env:LOCALAPPDATA 'Microsoft\MSBuild\Current\Microsoft.CSharp.targets\ImportAfter\DSViz.targets'))) {
    if (-not (Test-Path $f)) { Fail 'install-file' "קיים אחרי ההתקנה: $f" }
}
$vlog = Join-Path $dsviz 'vsix-install.log'
$vtext = if (Test-Path $vlog) { Get-Content $vlog -Raw -Encoding UTF8 } else { '' }
Copy-Item $vlog (Join-Path $logs 'vsix-install.log') -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $dsviz 'logs\*') $logs -Force -ErrorAction SilentlyContinue
Get-ChildItem $env:TEMP -Filter 'dd_VSIXInstaller*.log' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 6 | ForEach-Object { Copy-Item $_.FullName $logs -Force -ErrorAction SilentlyContinue }
Add-Type -AssemblyName System.IO.Compression.FileSystem
function VsixFacts([string]$file) {
    $r = @{ Hash = ''; Version = '' }
    try {
        $z = [IO.Compression.ZipFile]::OpenRead($file)
        try {
            $e = $z.Entries | Where-Object { $_.FullName -eq 'DsViz.VsExtension.dll' } | Select-Object -First 1
            if ($e) { $st = $e.Open(); $r.Hash = [BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash($st)) -replace '-', ''; $st.Close() }
            $m = $z.Entries | Where-Object { $_.FullName -eq 'extension.vsixmanifest' } | Select-Object -First 1
            if ($m) { $rd = New-Object IO.StreamReader($m.Open(), [Text.Encoding]::UTF8); if ($rd.ReadToEnd() -match '<Identity\b[^>]*\bVersion="([^"]+)"') { $r.Version = $Matches[1] }; $rd.Close() }
        } finally { $z.Dispose() }
    } catch { }
    return $r
}
function ExtDirs($inst) {
    $d = Join-Path $env:LOCALAPPDATA ("Microsoft\VisualStudio\{0}.0_{1}\Extensions" -f ([version]$inst.installationVersion).Major, $inst.instanceId)
    if (-not (Test-Path $d)) { return @() }
    return @(Get-ChildItem $d -Recurse -Filter extension.vsixmanifest -ErrorAction SilentlyContinue | Where-Object { (Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue) -match 'DsViz\.VisualStudio\.Toolbar' } | ForEach-Object { $_.Directory.FullName })
}
$facts = VsixFacts (Join-Path $dsviz 'cs\DsViz.VsExtension.vsix')
foreach ($i in $all) {
    $label = "{0} {1}" -f $i.displayName, $i.catalog.productDisplayVersion
    $short = ([string]$i.productId -replace '^Microsoft\.VisualStudio\.Product\.', '')
    # Contains ולא -like: בשם הגרסה יכולים להיות סוגריים מרובעים ("18.0.0 Insiders [11205.157]"), שב--like הם תווים מיוחדים
    $line = @($vtext -split "`r?`n" | Where-Object { $_.Contains("$label [$short]") })
    if ($fits -contains $i) {
        $m = ([version]$i.installationVersion).Major
        $dirs = @(ExtDirs $i)
        $same = $false
        foreach ($d in $dirs) { try { if ((Get-FileHash (Join-Path $d 'DsViz.VsExtension.dll') -Algorithm SHA256).Hash -eq $facts.Hash) { $same = $true } } catch { } }
        $ok = @($line | Where-Object { $_ -match ('התקנה \(ניסיון \d\): קוד \S+ · הותקן ב-.* · גרסה ' + [regex]::Escape($facts.Version)) }).Count -gt 0
        Check "install-vsix-$m-$short" ($ok -and $same -and $facts.Hash) "$label [$short]: הותקנה בו ההרחבה (גרסה $($facts.Version)), וה-DLL המותקן הוא זה שבקובץ" "ביומן: $(($line | Select-Object -Last 2) -join ' // ') · תיקיות: $($dirs -join ', ')"
        if ($i.instanceId -eq $script:vsInst.instanceId) { $script:kitVersion = $facts.Version }
    } else {
        Check "install-skip-$short" (@($line | Where-Object { $_ -match 'לא מתאים' }).Count -gt 0 -and @(ExtDirs $i).Count -eq 0) "$label [$short]: לא מתאים לסרגל — דולג, ונרשם ביומן" "ביומן: $(($line | Select-Object -Last 1) -join '')"
    }
}
Check 'install-exit' ($code -eq 0) 'install-vsix.ps1 הסתיים בקוד 0' "קוד $code"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $dsviz 'cs\install-vsix.ps1') (Join-Path $dsviz 'cs\DsViz.VsExtension.vsix') -Check | Out-Null
Check 'install-check' ($LASTEXITCODE -eq 0) 'install-vsix.ps1 -Check אחרי ההתקנה: הכול מעודכן (קוד 0)' "קוד $LASTEXITCODE"
if (-not $script:kitVersion) { $script:kitVersion = '?' }
Info "גרסת ההרחבה שהותקנה ב-${title}: $script:kitVersion"

# ------------------------------------------------------------------ הכנה: פרויקטי הבדיקה, ה-agent של חלונות ה-Swing, כניסה שמורה
Section 'הכנה: פרויקטי הבדיקה (CiApps.sln), קובץ בלי פרויקט, ה-agent של המאגר, כניסה שמורה'
$realSchool = Join-Path $env:USERPROFILE 'school-unit4\Unit4.dll'
$haveReal = Get-SchoolUnit4 $realSchool
Check 'prep-school-real' $haveReal 'הורד Unit4.dll האמיתי של בתי הספר (sha256 תואם) — הפרויקט הקלאסי מפנה אליו' $(if ($haveReal) { $realSchool } else { 'לא הורד — הפרויקט הקלאסי מפנה לספרייה המדומה' })
$apps = New-CiApps (Join-Path $env:USERPROFILE 'source\repos\CiApps') (Join-Path $root 'tools\test\helpers\SchoolUnit4.cs') $(if ($haveReal) { $realSchool } else { '' })
Check 'prep-school-dll' (Test-Path (Join-Path $apps.Dir 'school-bin\Unit4.dll')) 'ספריית בית הספר במקום (school-bin\Unit4.dll)'
$r = & $msbuild $apps.Sln '/t:Restore' '/v:m' '/nologo' 2>&1
$r | Set-Content (Join-Path $logs 'ciapps-restore.txt') -Encoding utf8
Info "restore ל-CiApps.sln: קוד $LASTEXITCODE · $(($r | Select-String 'error' | Select-Object -First 3) -join ' | ')"
$looseDir = Join-Path $env:USERPROFILE 'DSViz-loose-test'
$looseFile = Join-Path $looseDir 'Loops4.cs'
WriteCode $looseFile ("using System;`r`n`r`nclass Loops4`r`n{`r`n    static void Main(string[] args)`r`n    {`r`n        int sum = 0;`r`n        for (int i = 1; i <= 3; i++)`r`n        {`r`n            sum += i;`r`n        }`r`n        Ci.Mark(`"loose`", `"`");`r`n        Console.WriteLine(sum);`r`n    }`r`n}`r`n" + $script:ciHelper)
# תרגיל המחסנית של התבנית (Exercise) מסמן שהוא רץ — כדי לראות אם הוא רץ כשלא ביקשו אותו
$exProg = Join-Path $csDir 'Exercise\Program.cs'
$exText = [IO.File]::ReadAllText($exProg)
if ($exText -notmatch 'exercise-template') {
    $exText = $exText -replace '(static void Main\(string\[\] args\)\s*\{)', ('$1' + "`r`n        " + 'System.IO.Directory.CreateDirectory(System.IO.Path.Combine(System.IO.Path.GetTempPath(), "dsviz-ci")); System.IO.File.WriteAllText(System.IO.Path.Combine(System.IO.Path.GetTempPath(), "dsviz-ci", "run-exercise-template.txt"), "exercise-template||" + System.Diagnostics.Process.GetCurrentProcess().ProcessName + "|" + DateTime.Now.ToString("HH:mm:ss.fff"));')
    [IO.File]::WriteAllText($exProg, $exText, (New-Object Text.UTF8Encoding($true)))
}
Check 'prep-exercise-marker' (([IO.File]::ReadAllText($exProg)) -match 'exercise-template') 'תרגיל המחסנית של התבנית מסמן כשהוא רץ'
$r = & $msbuild $exSln '/t:Restore' '/v:m' '/nologo' 2>&1
$r | Set-Content (Join-Path $logs 'exercises-restore.txt') -Encoding utf8
Info "restore ל-DataStructures-CS.sln: קוד $LASTEXITCODE"

Remove-Item $ctl -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force (Join-Path $ctl 'cls'), (Join-Path $ctl 'seed') | Out-Null
$o = & javac --release 11 -encoding UTF-8 -nowarn -d (Join-Path $ctl 'cls') (Join-Path $root 'tutorials\harness\SwingScript.java') (Join-Path $root 'tools\test\windows-vs-toolbar\ToolbarAgent.java') 2>&1
Info "javac (agent): $($o -join ' ')"
[IO.File]::WriteAllText((Join-Path $ctl 'manifest.txt'), "Premain-Class: ToolbarAgent`r`n", (New-Object Text.UTF8Encoding($false)))
& jar --create --file (Join-Path $ctl 'agent.jar') --manifest (Join-Path $ctl 'manifest.txt') -C (Join-Path $ctl 'cls') . 2>&1 | ForEach-Object { Info "jar: $_" }
Check 'prep-agent' (Test-Path (Join-Path $ctl 'agent.jar')) 'נבנה ה-agent שמאתר את הרכיבים בחלון המאגר'
$seedCp = "$(Join-Path $dsviz 'bank\dsviz-bank-vs.jar');$(Join-Path $dsviz 'dsviz.jar')"
$o = & javac --release 11 -encoding UTF-8 -nowarn -cp $seedCp -d (Join-Path $ctl 'seed') (Join-Path $root 'tools\test\windows-vs-toolbar\SeedSession.java') 2>&1
Info "javac (SeedSession): $($o -join ' ')"
$seedKey = Join-Path $ctl 'seed-key.txt'
function Seed([string]$role, [string]$email) {
    $r = & (Join-Path $dsviz 'jdk\bin\java.exe') '-Dfile.encoding=UTF-8' -cp "$(Join-Path $ctl 'seed');$seedCp" il.lamerhav.dsviz.SeedSession $role $email $seedKey 2>&1 | Where-Object { "$_" -notmatch 'JAVA_TOOL_OPTIONS' }
    return ($r -join ' ')
}
$pk = Seed 'key' '-'
if ($pk -match 'PASSKEY (\S+)') { $env:DSVIZ_PASS_KEY = $Matches[1]; Info "מפתח בדיקה לכניסה שמורה: DSVIZ_PASS_KEY ($($Matches[1].Length) תווים)" } else { Info "SeedSession לא עבד: $pk" }
Info "בלי כניסה: $(Seed 'none' '-')"
# מכאן כל Java שהסרגל מפעיל טוען את ה-agent (Visual Studio יורש את המשתנה)
$env:JAVA_TOOL_OPTIONS = "-javaagent:$ctl\agent.jar=$ctl"
Remove-Item $traceLog -Force -ErrorAction SilentlyContinue

# ------------------------------------------------------------------ T1א: בלי פתרון
Section 'T1. הסרגל של DSViz — Visual Studio בלי פתרון (הפתיחה הראשונה אחרי ההתקנה)'
$p = StartVs ''
if (-not $p) { Fail 't1-open' "$title נפתח (בלי פתרון)" ((Windows) -join ' ; ') }
else {
    Step 't1-vs-open-no-solution'
    CheckToolbar 't1-nosln' 'בלי פתרון, בפתיחה הראשונה'
    CheckLoaded 't1-loaded'
    $closed = CloseVsByX
    Check 't1-close' $closed "$title נסגר בסגירה רגילה (לחיצה על X)" ''
}

# ------------------------------------------------------------------ T1ב + T5 + T12: פתרון התרגילים
Section 'T1. הסרגל אחרי סגירה רגילה ופתיחה עם פתרון התרגילים (DataStructures-CS)'
$p = StartVs $exSln
if (-not $p) { Fail 'ex-open' "$title נפתח עם DataStructures-CS.sln" ((Windows) -join ' ; ') }
else {
    Start-Sleep 20
    SkipFirstRun $p.Id | Out-Null
    Step 'ex-vs-open'
    CheckToolbar 't1-exsln' 'אחרי סגירה רגילה, עם פתרון התרגילים'

    Section 'T5. קובץ C# שלא שייך לאף פרויקט (Loops4.cs), כשה-Startup Project הוא תרגיל המחסנית של התבנית'
    try {
        AssertTime
        SetStartup 'Exercise\Exercise.csproj'
        # פותחים את הקובץ כמו תלמיד: Ctrl+O, מקלידים את הנתיב, Enter
        VsFront | Out-Null
        [DsReal]::Chord([uint16[]]@(0x11, 0x4F))
        # חלון "Open File" של Windows: חלון עליון של devenv (לא החלון הראשי) ששמו מתחיל ב-Open. לא סורקים את כל התוכן שלו (רשימת קבצים ארוכה)
        $dlg = WaitFor { foreach ($w in (OtherWindows $script:vsP.Id ([int]$script:vsMain))) { if ((ElName $w) -match '^Open') { return $w } } } 20
        Step 't5-open-file-dialog'
        if ($dlg) {
            Start-Sleep -Milliseconds 800   # הסמן כבר בשדה "File name"
            [DsReal]::Type($looseFile); Start-Sleep -Milliseconds 800; Step 't5-open-file-typed'; [DsReal]::Chord([uint16[]]@(0x0D))
        } else { Info "חלון הפתיחה לא נמצא · חלונות אחרים של Visual Studio: $((OtherWindows $script:vsP.Id ([int]$script:vsMain) | ForEach-Object { "'" + (ElName $_) + "'" }) -join ', ')"; DumpTopWindows 't5-no-open-dialog' }
        $opened = WaitFor { (ActiveDoc) -ieq $looseFile } 25
        Check 't5-open-real' ([bool]$opened) 'Loops4.cs נפתח כמו תלמיד: Ctrl+O, הקלדת הנתיב, Enter' "חלון הפתיחה נמצא: $($null -ne $dlg) · פעיל: $(ActiveDoc)"
        if (-not $opened) { [DsReal]::Chord([uint16[]]@(0x1B)); Start-Sleep 1; OpenDoc $looseFile | Out-Null }
        $proj = Dte { param($d) $pi = $d.ActiveDocument.ProjectItem; if ($pi -and $pi.ContainingProject) { [string]$pi.ContainingProject.Name } else { '(אין פרויקט)' } } 6
        Info "הקובץ הפעיל: $(ActiveDoc) · פרויקט ב-Visual Studio: $proj · Startup: $(Startup)"
        ClearMarkers; KillRuns; KillJava
        $tc = TraceCount
        $used = NormalRun 'ctrlf5' 't5'
        $m = WaitMarker 'exercise-template' 300
        Start-Sleep 3
        Step 't5-after-normal-run'
        $loose = ReadMarker 'loose'
        Check 't5-normal-runs-startup' ($m -and $m.Host -ne 'dsviz-cs') "ההרצה הרגילה ($used) עם Loops4.cs פתוח: Visual Studio מריץ את ה-Startup Project (Exercise) כרגיל — כמו בלי DSViz" "סימונים: $(Markers) · Startup: $(Startup)"
        Check 't5-not-hijacked' ((-not $loose) -and @(RunnerProcs).Count -eq 0 -and @(Viewers).Count -eq 0) 'ההרצה הרגילה לא הוחלפה בהרצה ויזואלית (אין dsviz-cs, אין חלון של DSViz)' "סימונים: $(Markers) · dsviz-cs: $((RunnerProcs) -join ' | ')"
        $btnName = 'הרצה ויזואלית של Loops4.cs'
        $bar = WaitFor { $x = @(ByName $script:vsP.Id $btnName ''); if ($x.Count) { $x[0] } } 30
        $txt = @(ByPattern $script:vsP.Id 'לא שייך לאף פרויקט' '')
        # הטקסט של שורת ההודעה: ב-UI Automation, ואם הוא לא נחשף שם כשם של רכיב — מה שההרחבה רשמה ביומן שהיא הציגה (והצילום)
        $said = @(TraceSince $tc 'info bar shown: .*לא שייך לאף פרויקט.*Exercise')
        VsFront | Out-Null
        Step 't5-info-bar'
        Check 't5-info-bar' (($null -ne $bar) -and ($txt.Count -gt 0 -or $said.Count -gt 0)) "שורת ההודעה מסבירה: הקובץ לא שייך לאף פרויקט ולכן רץ Exercise — עם הכפתור '$btnName'" "כפתור: $(DescEl $bar) · טקסט ב-UI Automation: $(($txt | Select-Object -First 1 | ForEach-Object { ElName $_ }) -join '') · ביומן ההרחבה: $(($said | Select-Object -Last 1) -join '')"
        if (-not $bar) { DumpUia $script:vsP.Id 't5-info-bar'; TraceTail 'info bar|run:' }
        Check 't5-keeps-startup' ((Startup) -like '*Exercise\Exercise.csproj*') 'ה-Startup Project לא השתנה (נשאר Exercise)' (Startup)
        for ($i = 0; $i -lt 20 -and -not (DesignMode); $i++) { Start-Sleep 1 }
        KillRuns
        if ($bar) {
            ClearMarkers
            VsFront | Out-Null
            $bar = @(ByName $script:vsP.Id $btnName '') | Select-Object -First 1
            $sc = $script:signInClosed; $tcClick = TraceCount
            RealClick $bar 't5-info-bar-visual-run' | Out-Null
            # ההרחבה רושמת ביומן כל לחיצה שהגיעה אליה ('visual run (info bar)') וסוגרת את שורת ההודעה. אם אחרי 20 שניות אין רישום והשורה
            # עדיין פתוחה — הלחיצה לא הגיעה (בריצה 4: חלון כניסה לחשבון Microsoft ש-Visual Studio פתח לבד, מודאלי). סוגרים אותו ולוחצים שוב, כמו תלמיד
            $got = WaitFor { @(TraceSince $tcClick 'visual run \(info bar\)').Count -gt 0 } 20
            if (-not $got) {
                DumpTopWindows 't5-info-bar-click-lost'; CloseSignIn | Out-Null
                $again = @(ByName $script:vsP.Id $btnName '') | Select-Object -First 1
                if ($again) {
                    Info ("הלחיצה על שורת ההודעה לא הגיעה להרחבה (אין רישום ביומן, השורה עדיין פתוחה)" + $(if ($script:signInClosed -gt $sc) { ' — חלון כניסה לחשבון Microsoft חסם אותה ונסגר' } else { '' }) + ' — לוחצים שוב')
                    VsFront | Out-Null
                    RealClick $again 't5-info-bar-visual-run-again' | Out-Null
                }
            }
            $m2 = WaitMarker 'loose' 120
            $v = WaitFor { $x = @(Viewers); if ($x.Count) { $x } } 90
            Start-Sleep 3; Step 't5-info-bar-viewer'
            Check 't5-info-bar-visual' ($m2 -and $m2.Host -eq 'dsviz-cs' -and $v) "לחיצה על '$btnName' בשורת ההודעה: Loops4.cs רץ בהרצה הוויזואלית ונפתח החלון של DSViz" "סימונים: $(Markers) · חלונות: $((Windows) -join ' ; ')"
            KillJava
        }
        CheckVisual 't5-toolbar-visual' $looseFile 'loose' '' 'קובץ בלי פרויקט'
        Dte { param($d) foreach ($x in @($d.Documents)) { if ($x.FullName -ieq $looseFile) { $x.Close(2) } }; 'ok' } 6 | Out-Null
    } catch { Fail 't5-exception' 'T5 רץ עד הסוף' "$_" }

    Section 'T12. מאגר השאלות מהסרגל'
    try {
        AssertTime
        Info "בלי כניסה: $(Seed 'none' '-')"
        KillJava
        Remove-Item (Join-Path $ctl 'bank.script.txt') -Force -ErrorAction SilentlyContinue
        $clicked = ClickToolbar 'מאגר שאלות' 't12-toolbar-bank-gate'
        $gate = WaitFor { JavaWindow 'כניסה למאגר' } 120
        Start-Sleep 2; Step 't12-gate'
        Check 't12-gate' ($clicked -and $gate) "בלי כניסה: לחיצה על 'מאגר שאלות' בסרגל פותחת את מסך הכניסה" ((Windows) -join ' ; ')
        KillJava
        Info "כניסה שמורה (תלמיד, מפתח בדיקה): $(Seed 'student' 'pupil@test.dev')"
        $name = CreateFromBank 't12' 'queue' 'queue-sum-even'
        if ($script:seedWorked) { Pass 't12-seeded-open' 'עם כניסה שמורה המאגר נפתח ישר, בלי מסך הכניסה' }
        if ($name) {
            $dir = CheckCreated 't12' $name
            BankUnsolvedThenSolve 't12' $dir 'queue' 'queue-sum-even'
            $b = $null
            $failed = BuildSolution 't12'
            if ($failed -ne 0) { $b = & $msbuild $exSln '/restore' '/nologo' '/v:m' 2>&1; $b | Set-Content (Join-Path $logs 't12-build.txt') -Encoding utf8 }
            Check 't12-build' ($failed -eq 0) "Build Solution ב-${title}: כל הפתרון (התבנית, הדוגמאות והתרגיל החדש) נבנה" "פרויקטים שנכשלו: $failed · $(if ($b) { ($b | Select-String 'error' | Select-Object -First 3) -join ' | ' })"
            CheckBankRun 't12' $name $dir
        }
    } catch { Fail 't12-exception' 'T12 רץ עד הסוף' "$_" }

    # ------------------------------------------------------------------ T1ג + T12ב: סגירה בכוח, הספרייה נמחקת, פתיחה מחדש
    Section 'T1 / T12ב. Visual Studio נסגר בכוח; lib\Unit4.CollectionsLib.dll נמחק מתיקיית התרגילים; פתיחה מחדש'
    try {
        AssertTime
        KillJava; KillVs
        $libFile = Join-Path $csDir 'lib\Unit4.CollectionsLib.dll'
        Remove-Item $libFile -Force -ErrorAction SilentlyContinue
        Check 't12b-lib-deleted' (-not (Test-Path $libFile)) 'הספרייה נמחקה מתיקיית התרגילים (lib\Unit4.CollectionsLib.dll)'
        $p = StartVs $exSln
        if (-not $p) { Fail 't12b-open' "$title נפתח שוב אחרי סגירה בכוח" ((Windows) -join ' ; ') }
        else {
            Start-Sleep 15
            $q = DismissDialogs
            if ($q) { Info "Visual Studio שאל אחרי הסגירה בכוח: $q" }
            Step 't12b-vs-open'
            CheckToolbar 't1-after-kill' 'אחרי סגירה בכוח ופתיחה מחדש'
            $name2 = CreateFromBank 't12b' 'list' 'list-evens-remove'
            $want = ''; try { $want = (Get-FileHash (Join-Path $dsviz 'cs-project\lib\Unit4.CollectionsLib.dll') -Algorithm SHA256).Hash } catch { }
            $got = ''; try { $got = (Get-FileHash $libFile -Algorithm SHA256).Hash } catch { }
            Check 't12b-repaired' ($got -and $got -eq $want) "'צור תרגיל' השלים לבד את הספרייה שנמחקה (מהתבנית שבהתקנה)" "קיים: $(Test-Path $libFile) · זהה לתבנית: $($got -eq $want)"
            if ($name2) {
                $dir2 = CheckCreated 't12b' $name2
                BankUnsolvedThenSolve 't12b' $dir2 'list' 'list-evens-remove'
                $b = $null
                $failed = BuildSolution 't12b'
                if ($failed -ne 0) { $b = & $msbuild $exSln '/restore' '/nologo' '/v:m' 2>&1; $b | Set-Content (Join-Path $logs 't12b-build.txt') -Encoding utf8 }
                Check 't12b-build' ($failed -eq 0) 'אחרי התיקון העצמי: Build Solution עובר' "פרויקטים שנכשלו: $failed · $(if ($b) { ($b | Select-String 'error' | Select-Object -First 3) -join ' | ' })"
                CheckBankRun 't12b' $name2 (Join-Path $exDir $name2)
            }
            KillJava
            $closed = CloseVsByX
            Check 't12b-close' $closed "$title נסגר בסגירה רגילה" ''
        }
    } catch { Fail 't12b-exception' 'T12ב רץ עד הסוף' "$_" }
}

# ------------------------------------------------------------------ CiApps: פרויקטים רגילים של תלמיד / מורה
Section 'T2–T8. פתרון רגיל (CiApps.sln): הרצה רגילה מול הרצה ויזואלית'
KillJava; KillVs
$p = StartVs $apps.Sln
if (-not $p) { Fail 'apps-open' "$title נפתח עם CiApps.sln" ((Windows) -join ' ; ') }
else {
    Start-Sleep 25
    SkipFirstRun $p.Id | Out-Null
    Step 'apps-vs-open'
    $ready = WaitToolbar 240
    Check 'apps-toolbar' $ready 'הסרגל של DSViz גם בפתרון רגיל' (DteToolbar)
    $d = $apps.Dir
    try {
        AssertTime
        Section 'T2. פרויקט SDK רגיל'
        CheckNormal 't2-green' 'SdkApp' (Join-Path $d 'SdkApp\Program.cs') 'sdk' 'green' '' 'פרויקט SDK' | Out-Null
        CheckNormal 't2-ctrlf5' 'SdkApp' (Join-Path $d 'SdkApp\Program.cs') 'sdk' 'ctrlf5' '' 'פרויקט SDK' | Out-Null
        Check 't2-no-profile-written' (-not (Test-Path (Join-Path $d 'SdkApp\Properties\launchSettings.json'))) 'לפרויקט שלא משתמש בספרייה לא נכתב launchSettings.json בהרצה רגילה' ''
        CheckVisual 't2-visual' (Join-Path $d 'SdkApp\Program.cs') 'sdk' '' 'פרויקט SDK'
    } catch { Fail 't2-exception' 'T2 רץ עד הסוף' "$_" }
    try {
        AssertTime
        Section 'T4. top-level statements'
        CheckNormal 't4-normal' 'TopApp' (Join-Path $d 'TopApp\Program.cs') 'top' 'menu' '' 'top-level statements' | Out-Null
        CheckVisual 't4-visual' (Join-Path $d 'TopApp\Program.cs') 'top' '' 'top-level statements'
    } catch { Fail 't4-exception' 'T4 רץ עד הסוף' "$_" }
    try {
        AssertTime
        Section 'T3. הספרייה של בית הספר (Unit4) מול הספרייה של DSViz'
        CheckNormal 't3-classic-normal' 'SchoolClassic' (Join-Path $d 'SchoolClassic\Program.cs') 'school' 'menu' 'Unit4' 'פרויקט ‎.NET Framework קלאסי עם Unit4.dll של בית הספר' | Out-Null
        $outNormal = ReadMarker 'school-out'
        Check 't3-classic-bin' ((Test-Path (Join-Path $d 'SchoolClassic\bin\Debug\Unit4.dll')) -and -not (Test-Path (Join-Path $d 'SchoolClassic\bin\Debug\Unit4.CollectionsLib.dll'))) 'ב-bin של הפרויקט הקלאסי: Unit4.dll של בית הספר, בלי הספרייה של DSViz' "$((Get-ChildItem (Join-Path $d 'SchoolClassic\bin\Debug') -ErrorAction SilentlyContinue | ForEach-Object Name) -join ', ')"
        CheckVisual 't3-classic-visual' (Join-Path $d 'SchoolClassic\Program.cs') 'school' 'Unit4.CollectionsLib' 'אותו פרויקט קלאסי'
        if ($haveReal) {
            # אותו קוד (StackInt, BinTreeUtils, הדפסת חוליה — מה שיש רק ב-Unit4.dll של בתי הספר): ההרצה הרגילה עם Unit4.dll וההרצה הוויזואלית
            # עם הספרייה של DSViz הדפיסו בדיוק אותו דבר
            $outVisual = ReadMarker 'school-out'
            $want = '[4,3] [5,1] 4 h1 c3 full=True'
            Check 't3-classic-same-output' ($outNormal -and $outVisual -and $outNormal.Lib -eq $want -and $outVisual.Lib -eq $want) "Unit4.dll האמיתי: אותו קוד (StackInt, BinTreeUtils, הדפסת חוליה) הדפיס אותו דבר בהרצה הרגילה ובהרצה הוויזואלית — '$want'" "רגילה: '$($outNormal.Lib)' · ויזואלית: '$($outVisual.Lib)'"
        }
        CheckNormal 't3-ref-normal' 'SchoolRef' (Join-Path $d 'SchoolRef\Program.cs') 'schoolref' 'ctrlf5' 'Unit4' 'ספריית בית הספר ב-ProjectReference (CollectionsLib.csproj, AssemblyName=Unit4)' | Out-Null
        CheckVisual 't3-ref-visual' (Join-Path $d 'SchoolRef\Program.cs') 'schoolref' 'Unit4.CollectionsLib' 'אותו פרויקט עם ProjectReference'
        CheckNormal 't3-noref-normal' 'ClassicDsviz' (Join-Path $d 'ClassicDsviz\Program.cs') 'classicdsviz' 'ctrlf5' 'Unit4.CollectionsLib' 'פרויקט ‎.NET Framework קלאסי שמשתמש בספרייה בלי שום הפניה' | Out-Null
        OpenDoc (Join-Path $d 'ClassicDsviz\Program.cs') | Out-Null
        Start-Sleep 8
        $errs = Dte { param($x) try { [int]$x.ToolWindows.ErrorList.ErrorItems.Count } catch { -1 } } 6
        Step 't3-noref-intellisense'
        Note 't3-noref-intellisense' "פרויקט קלאסי בלי הפניה, הקובץ פתוח בעורך: $errs שורות ב-Error List (0 = גם IntelliSense מכיר את הספרייה שנוספה אחרי פתרון ההפניות)"
    } catch { Fail 't3-exception' 'T3 רץ עד הסוף' "$_" }
    try {
        AssertTime
        Section 'T13. שתי ההרצות באותו זמן על אותו קובץ: הרגילה (ספריית בית הספר) מחכה לקלט, ובזמן הזה ההרצה הוויזואלית'
        KillRuns; KillJava
        SetStartup 'WaitApp\WaitApp.csproj'
        $waitFile = Join-Path $d 'WaitApp\Program.cs'
        $waitMd5 = (Get-FileHash $waitFile -Algorithm MD5).Hash
        $act = OpenDoc $waitFile
        ClearMarkers
        $used = NormalRun 'ctrlf5' 't13'
        $mN = WaitMarker 'wait-WaitApp' 240
        Start-Sleep 2
        Step 't13-normal-waiting'
        $waiting = @(Get-Process WaitApp -ErrorAction SilentlyContinue).Count -gt 0
        Check 't13-normal' ($mN -and $mN.Lib -eq 'Unit4' -and $waiting) "ההרצה הרגילה ($used) רצה עם Unit4 של בית הספר$(if ($haveReal) { ' (Unit4.dll האמיתי)' }) ומחכה לקלט (התהליך WaitApp פתוח)" "סימונים: $(Markers) · פעיל: $act"
        $clicked = ClickToolbar 'הרצה ויזואלית' 't13-toolbar-visual-run'
        $mV = $null
        if ($clicked) { $mV = WaitMarker 'wait-dsviz-cs' 150 }
        $v = WaitFor { $x = @(Viewers); if ($x.Count) { $x } } 90
        Start-Sleep 3
        Step 't13-both-running'
        $stillWaiting = @(Get-Process WaitApp -ErrorAction SilentlyContinue).Count -gt 0
        $msg = (@(Dialogs | ForEach-Object { $_.Text }) -join ' || ')
        Check 't13-visual' ($clicked -and $mV -and $mV.Lib -eq 'Unit4.CollectionsLib' -and $v) "בזמן שההרצה הרגילה פתוחה: ההרצה הוויזואלית של אותו קובץ רצה עם Unit4.CollectionsLib (של DSViz) ונפתח החלון" "סימונים: $(Markers) · הודעות: $msg · חלונות: $((Windows) -join ' ; ')"
        Check 't13-normal-undisturbed' ($stillWaiting -and -not $msg) 'ההרצה הרגילה לא הופרעה (WaitApp עדיין רץ ומחכה), ושום הודעה לא נפתחה' "WaitApp: $stillWaiting · הודעות: $msg"
        Check 't13-file-unchanged' ((Get-FileHash $waitFile -Algorithm MD5).Hash -eq $waitMd5) 'הקובץ לא השתנה — אותו קוד בשתי ההרצות' ''
        KillJava; KillRuns
        for ($i = 0; $i -lt 30 -and -not (DesignMode); $i++) { Start-Sleep 1 }
    } catch { Fail 't13-exception' 'T13 רץ עד הסוף' "$_" }
    try {
        AssertTime
        Section 'T3x. חבר שיש רק בספרייה של בית הספר (Stack<int>.Count()): ההרצה הרגילה עובדת, ההרצה הוויזואלית מסבירה בעברית'
        $null = CheckNormal 't3x-normal' 'SchoolExt' (Join-Path $d 'SchoolExt\Program.cs') 'schoolext' 'ctrlf5' 'Unit4' 'קוד שמשתמש ב-Count() של ספריית בית הספר'
        CheckMessage 't3x-visual-message' (Join-Path $d 'SchoolExt\Program.cs') "אין 'Count'" 'אותו קוד בהרצה הוויזואלית (הספרייה של DSViz — בלי Count())'
        $last = @(Dialogs | ForEach-Object { $_.Text }) -join ' || '
        Info "ההודעה (אם עוד פתוחה): $last"
    } catch { Fail 't3x-exception' 'T3x רץ עד הסוף' "$_" }
    try {
        AssertTime
        Section 'T7. שני פרויקטי הרצה: ה-Startup Project הוא SdkApp, והקובץ הפתוח שייך ל-TopApp'
        KillRuns; KillJava
        SetStartup 'SdkApp\SdkApp.csproj'
        OpenDoc (Join-Path $d 'TopApp\Program.cs') | Out-Null
        ClearMarkers
        $tc = TraceCount
        $used = NormalRun 'ctrlf5' 't7'
        $m = WaitMarker 'sdk' 240
        Start-Sleep 3
        $top = ReadMarker 'top'
        Step 't7-after-normal-run'
        Check 't7-runs-startup' ($m -and $m.Host -ne 'dsviz-cs' -and -not $top) "ההרצה הרגילה ($used): רץ ה-Startup Project (SdkApp), כמו בלי DSViz" "סימונים: $(Markers)"
        Check 't7-startup-untouched' ((Startup) -like '*SdkApp*') 'בפתרון שאינו של DSViz ההרחבה לא מחליפה את ה-Startup Project' (Startup)
        $bn = 'הרצה ויזואלית של Program.cs'
        $bar = WaitFor { $x = @(ByName $script:vsP.Id $bn ''); if ($x.Count) { $x[0] } } 30
        $txt = @(ByPattern $script:vsP.Id 'ה-Startup Project' '' | Where-Object { (ElName $_) -match 'SdkApp' -and (ElName $_) -match 'TopApp' })
        $said = @(TraceSince $tc 'info bar shown: .*SdkApp.*TopApp')
        VsFront | Out-Null
        Step 't7-info-bar'
        Check 't7-info-bar' (($null -ne $bar) -and ($txt.Count -gt 0 -or $said.Count -gt 0)) 'שורת ההודעה מסבירה ש-Visual Studio מריץ את SdkApp ולא את TopApp שהקובץ הפתוח שייך אליו' "כפתור: $(DescEl $bar) · טקסט ב-UI Automation: $(($txt | Select-Object -First 1 | ForEach-Object { ElName $_ }) -join '') · ביומן ההרחבה: $(($said | Select-Object -Last 1) -join '')"
        if (-not $bar) { TraceTail 'info bar|run:' }
        for ($i = 0; $i -lt 20 -and -not (DesignMode); $i++) { Start-Sleep 1 }
        KillRuns
        CheckVisual 't7-visual' (Join-Path $d 'TopApp\Program.cs') 'top' '' 'הקובץ הפתוח (TopApp) כשה-Startup Project אחר'
    } catch { Fail 't7-exception' 'T7 רץ עד הסוף' "$_" }
    try {
        AssertTime
        Section 'T6. הודעות של ההרצה הוויזואלית'
        CheckMessage 't6-no-main' (Join-Path $d 'NoMain\Helper.cs') 'אין פעולת Main' 'קובץ בלי Main (Helper.cs)'
        CheckMessage 't6-empty-main' (Join-Path $d 'EmptyMain\Program.cs') 'Main ריקה' 'Main ריקה'
        CheckMessage 't6-compile-error' (Join-Path $d 'loose\Bad.cs') 'error CS\d+' 'שגיאת קומפילציה'
        Dte { param($x) foreach ($doc in @($x.Documents)) { if ($doc.FullName -like '*\loose\Bad.cs') { $doc.Close(2) } }; 'ok' } 6 | Out-Null
    } catch { Fail 't6-exception' 'T6 רץ עד הסוף' "$_" }
    try {
        AssertTime
        Section 'T8. פרויקט שאין לו קשר ל-DSViz, ו-Build Solution'
        CloseDocs
        $b = $null
        $failed = BuildSolution 't8'
        if ($failed -ne 0) { $b = & $msbuild $apps.Sln '/nologo' '/v:m' 2>&1; $b | Set-Content (Join-Path $logs 't8-build.txt') -Encoding utf8 }
        Check 't8-build-solution' ($failed -eq 0) "Build Solution ב-${title}: כל פרויקטי הבדיקה נבנים (גם אלה שאין להם קשר ל-DSViz)" "פרויקטים שנכשלו: $failed · $(if ($b) { ($b | Select-String 'error' | Select-Object -First 4) -join ' | ' })"
        $bin = Get-ChildItem (Join-Path $d 'PlainStack\bin') -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object Name
        Check 't8-plain-untouched' ((@($bin | Where-Object { $_ -match '^Unit4' }).Count -eq 0) -and -not (Test-Path (Join-Path $d 'PlainStack\Properties\launchSettings.json'))) 'בפרויקט הרגיל (Stack<int> של ‎.NET): בלי הספרייה של DSViz ב-bin, ובלי launchSettings.json שנכתב לו' "$($bin -join ', ')"
        $m = CheckNormal 't8-plain-run' 'PlainStack' (Join-Path $d 'PlainStack\Program.cs') 'plain' 'menu' '' 'פרויקט רגיל עם Stack<int> של ‎.NET'
        if ($m) { Check 't8-plain-lib' ($m.Lib -notmatch '^Unit4') 'Stack<int> בפרויקט הרגיל הוא של ‎.NET' "מגיע מ: $($m.Lib)" }
        $ref = Get-ChildItem (Join-Path $d 'SchoolRef\bin') -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object Name
        Check 't8-ref-bin' ((@($ref | Where-Object { $_ -eq 'Unit4.dll' }).Count -gt 0) -and (@($ref | Where-Object { $_ -eq 'Unit4.CollectionsLib.dll' }).Count -eq 0)) 'בפרויקט עם ProjectReference לספריית בית הספר: Unit4.dll ב-bin, בלי הספרייה של DSViz (בלי CS0433)' "$($ref -join ', ')"
    } catch { Fail 't8-exception' 'T8 רץ עד הסוף' "$_" }
    KillRuns; KillJava
    $closed = CloseVsByX
    Check 'apps-close' $closed "$title נסגר בסגירה רגילה" ''
}

# ------------------------------------------------------------------ T14: Empty Project דרך האשף של Visual Studio (File > New > Project)
# פרויקט רגיל לגמרי, כמו שתלמיד יוצר בלי DSViz: האשף (חיפוש התבנית, שם, מיקום, Create), Add Class, הדבקת קוד שמשתמש ב-Stack,
# ואז הרצה רגילה (Debug > Start Without Debugging) והרצה ויזואלית (הכפתור בסרגל) — עם צילום אחרי כל צעד
Section 'T14. פרויקט חדש דרך האשף: Empty Project (.NET Framework) → מחלקה עם Main → הרצה רגילה והרצה ויזואלית'
# חלון של Visual Studio (לא הראשי) שיש בו רכיב בשם הזה — האשף ("Create a new project" / "Configure your new project"), "Add New Item"
function VsWindowWith([string]$pattern) {
    foreach ($w in (OtherWindows $script:vsP.Id ([int]$script:vsMain))) {
        try {
            if ((ElName $w) -match $pattern) { return $w }
            foreach ($e in @($w.FindAll([Windows.Automation.TreeScope]::Descendants, (New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ControlTypeProperty, [Windows.Automation.ControlType]::Text))))) {
                if ((ElName $e) -match $pattern) { return $w }
            }
        } catch { }
    }
    return $null
}
function InWin($w, [string]$pattern, [string]$type = '') {
    if (-not $w) { return @() }
    try { return @($w.FindAll([Windows.Automation.TreeScope]::Descendants, [Windows.Automation.Condition]::TrueCondition) | Where-Object { (ElName $_) -match $pattern -and (-not $type -or (ElType $_) -match $type) -and (Shown $_) }) } catch { return @() }
}
function SubText($el) { try { return ((@($el.FindAll([Windows.Automation.TreeScope]::Subtree, [Windows.Automation.Condition]::TrueCondition) | ForEach-Object { ElName $_ } | Where-Object { $_ }) | Select-Object -Unique) -join ' | ') } catch { return '' } }
# שדה טקסט: לחיצה אמיתית, Ctrl+A, הקלדה
function TypeInto($el, [string]$text, [string]$tag) {
    if ($el -and (RealClick $el $tag)) { Start-Sleep -Milliseconds 300 }
    [DsReal]::Chord([uint16[]]@(0x11, 0x41)); Start-Sleep -Milliseconds 200
    [DsReal]::Type($text); Start-Sleep -Milliseconds 500
}
$emptyRoot = Join-Path $env:USERPROFILE 'DSViz-empty-test'
$emptyName = 'EmptyDemo'
$emptyCode = @'
using System;
using Unit4.CollectionsLib;

class Program
{
    static void Main(string[] args)
    {
        Stack<int> s = new Stack<int>();
        s.Push(3);
        s.Push(7);
        Console.WriteLine("Empty Project: top=" + s.Top() + " " + s);
        Ci.Mark("empty", typeof(Stack<int>).Assembly.GetName().Name);
    }
}

static class Ci
{
    public static void Mark(string prog, string lib)
    {
        string dir = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "dsviz-ci");
        System.IO.Directory.CreateDirectory(dir);
        string host = System.IO.Path.GetFileNameWithoutExtension(System.Environment.GetCommandLineArgs()[0]);
        System.IO.File.WriteAllText(System.IO.Path.Combine(dir, "run-" + prog + ".txt"), prog + "|" + lib + "|" + host + "|" + System.DateTime.Now.ToString("HH:mm:ss.fff"));
    }
}
'@
$emptyCode = $emptyCode -replace "`r?`n", "`r`n"   # סופי שורה של Windows, כמו הקובץ ש-Visual Studio יצר (אחרת: "Inconsistent Line Endings")
KillJava; KillVs
Remove-Item $emptyRoot -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $emptyRoot | Out-Null
$p = StartVs ''
if (-not $p) { Fail 't14-open' "$title נפתח (בלי פתרון)" ((Windows) -join ' ; ') }
else {
    try {
        AssertTime
        WaitToolbar 240 | Out-Null
        Step 't14-vs-open'
        # 1. האשף: Ctrl+Shift+N (= File > New > Project)
        VsFront | Out-Null
        [DsReal]::Chord([uint16[]]@(0x11, 0x10, 0x4E))
        $npd = WaitFor { VsWindowWith '^Create a new project$' } 60 1000
        Step 't14-wizard-open'
        Check 't14-wizard-open' ($null -ne $npd) 'Ctrl+Shift+N פתח את האשף "Create a new project"' "חלונות אחרים: $((OtherWindows $script:vsP.Id ([int]$script:vsMain) | ForEach-Object { "'" + (ElName $_) + "'" }) -join ', ')"
        if (-not $npd) { DumpUia $script:vsP.Id 't14-no-wizard'; throw 'האשף לא נפתח' }
        # 2. חיפוש התבנית ובחירת Empty Project (.NET Framework) של C# (יש גם אחת של Visual Basic באותו שם)
        $search = @(InWin $npd 'Search for templates' 'Edit') | Select-Object -First 1   # ב-2022 השם הוא '_Search for templates (Alt+S)'
        if (-not $search) { Info 'שדה החיפוש לא נמצא בשם — Alt+S (קיצור המקלדת של האשף)'; [DsReal]::Chord([uint16[]]@(0x12, 0x53)); Start-Sleep -Milliseconds 500 }
        TypeInto $search 'Empty Project (.NET Framework)' 't14-search'
        Start-Sleep 3
        $cands = @(WaitFor { $x = @(InWin $npd 'Empty Project \(\.NET Framework\)' 'ListItem|DataItem'); if ($x.Count) { $x } } 30)
        $pick = $null
        foreach ($c in $cands) { $t = (ElName $c) + ' | ' + (SubText $c); Info "תבנית: $t"; if (-not $pick -and $t -match 'C#') { $pick = $c } }
        if (-not $pick) { $pick = @($cands | Where-Object { ((ElName $_) + (SubText $_)) -notmatch 'Visual Basic|C\+\+' }) | Select-Object -First 1 }
        Step 't14-wizard-search'
        if (-not $pick) { DumpUia $script:vsP.Id 't14-no-template'; throw 'התבנית Empty Project (.NET Framework) לא נמצאה באשף' }
        RealClick $pick 't14-template' | Out-Null
        $next = @(InWin $npd '^Next$' 'Button') | Select-Object -First 1
        RealClick $next 't14-next' | Out-Null
        # 3. Configure your new project: שם, מיקום, Create
        $nameBox = WaitFor { $x = @(InWin $npd '^Project name' 'Edit'); if ($x.Count) { $x[0] } } 30
        if (-not $nameBox) { $npd = VsWindowWith 'Configure your new project'; $nameBox = @(InWin $npd '^Project name' 'Edit') | Select-Object -First 1 }
        Check 't14-configure' ($null -ne $nameBox) 'אחרי Next: המסך "Configure your new project" עם שם הפרויקט' (SubText $npd)
        if (-not $nameBox) { DumpUia $script:vsP.Id 't14-no-configure'; throw 'מסך ההגדרות של האשף לא נמצא' }
        TypeInto $nameBox $emptyName 't14-project-name'
        $loc = @(InWin $npd '^Location' 'Edit|ComboBox') | Select-Object -First 1
        if ($loc -and (ElType $loc) -match 'ComboBox') { $e = @(InWin $loc '.*' 'Edit') | Select-Object -First 1; if ($e) { $loc = $e } }
        TypeInto $loc $emptyRoot 't14-location'
        Step 't14-wizard-configure'
        $create = @(InWin $npd '^Create$' 'Button') | Select-Object -First 1
        if (-not $create) {   # בגרסה שבה יש עוד מסך (Additional information) לפני Create
            RealClick (@(InWin $npd '^Next$' 'Button') | Select-Object -First 1) 't14-next2' | Out-Null
            $create = WaitFor { $x = @(InWin $npd '^Create$' 'Button'); if ($x.Count) { $x[0] } } 20
        }
        RealClick $create 't14-create' | Out-Null
        # 4. הפרויקט נוצר ונפתח
        $csproj = WaitFor { $f = @(Get-ChildItem $emptyRoot -Recurse -Filter "$emptyName.csproj" -ErrorAction SilentlyContinue) | Select-Object -First 1; if ($f) { $f } } 120 1000
        $opened = WaitFor { $n = Dte { param($d) if ($d.Solution.IsOpen) { [int]$d.Solution.Projects.Count } else { 0 } } 4; if ($n -ge 1) { $n } } 120 1000
        Start-Sleep 5; DismissDialogs | Out-Null
        VsFront | Out-Null
        Step 't14-created'
        $projText = if ($csproj) { Get-Content $csproj.FullName -Raw -Encoding UTF8 } else { '' }
        $outType = if ($projText -match '<OutputType>([^<]+)</OutputType>') { $Matches[1] } else { '?' }
        $fw = if ($projText -match '<TargetFrameworkVersion>([^<]+)</TargetFrameworkVersion>') { $Matches[1] } else { '?' }
        Check 't14-created' ($csproj -and $opened) "האשף יצר את הפרויקט $emptyName.csproj ו-Visual Studio פתח אותו" "csproj: $(if ($csproj) { $csproj.FullName } else { 'אין' }) · פרויקטים בפתרון: $opened"
        if (-not $csproj) { throw 'הפרויקט לא נוצר' }
        Check 't14-plain-project' (($projText -notmatch 'Unit4|DsViz|DSViz') -and ($projText -notmatch '<Compile ')) "פרויקט ריק ורגיל של ‎.NET Framework (OutputType=$outType, $fw): בלי הפניה לספרייה, בלי שום דבר של DSViz, בלי קבצי קוד" $(($projText -replace '\s+', ' ') -replace '^(.{0,400}).*$', '$1')
        $projDir = $csproj.DirectoryName
        $file = Join-Path $projDir 'Program.cs'
        # 5. Add Class (Shift+Alt+C) — אחרי לחיצה על הפרויקט ב-Solution Explorer
        $node = WaitFor { $x = @(ByName $script:vsP.Id $emptyName 'TreeItem'); if ($x.Count) { $x[0] } } 30
        if ($node) { RealClick $node 't14-solution-explorer-project' | Out-Null }
        [DsReal]::Chord([uint16[]]@(0x10, 0x12, 0x43))
        $add = WaitFor { VsWindowWith '^Add New Item' } 30 1000
        $how = ''
        if ($add) {
            Start-Sleep 2
            Step 't14-add-new-item'
            $nb = @(InWin $add '^Name' 'Edit') | Select-Object -First 1
            TypeInto $nb 'Program.cs' 't14-class-name'
            [DsReal]::Chord([uint16[]]@(0x0D))
            if (WaitFor { Test-Path $file } 30) { $how = 'Add Class (Shift+Alt+C) ← Program.cs' }
        }
        if (-not $how) {
            Info "חלון Add New Item לא עבד ($(if ($add) { 'נפתח' } else { 'לא נפתח' })) — מוסיפים את Program.cs לפרויקט דרך DTE"
            if ($add) { [DsReal]::Chord([uint16[]]@(0x1B)) }
            Set-Content -Path $file -Value $emptyCode -Encoding UTF8
            Dte { param($d) $d.Solution.Projects.Item(1).ProjectItems.AddFromFile($file) | Out-Null; 'ok' } 8 | Out-Null
            $how = 'DTE AddFromFile (גיבוי)'
        }
        Check 't14-add-class' (Test-Path $file) "נוסף קובץ קוד לפרויקט: Program.cs ($how)" ''
        # 6. הקוד: הדבקה בעורך (Ctrl+A, Ctrl+V, Ctrl+S) כמו תלמיד שמדביק קוד
        OpenDoc $file | Out-Null
        VsFront | Out-Null
        Set-Clipboard -Value $emptyCode
        [DsReal]::Chord([uint16[]]@(0x11, 0x41)); Start-Sleep -Milliseconds 300
        [DsReal]::Chord([uint16[]]@(0x11, 0x56)); Start-Sleep 1
        [DsReal]::Chord([uint16[]]@(0x11, 0x53)); Start-Sleep 2
        $pasted = (Get-Content $file -Raw -Encoding UTF8) -match 'Ci\.Mark\("empty"'
        if (-not $pasted) {
            Info 'ההדבקה בעורך לא נשמרה — כותבים את הקוד לקובץ ופותחים מחדש'
            CloseDocs; Set-Content -Path $file -Value $emptyCode -Encoding UTF8; OpenDoc $file | Out-Null
        }
        DismissDialogs | Out-Null
        Step 't14-code'
        Check 't14-code' ((Get-Content $file -Raw -Encoding UTF8) -match 'Ci\.Mark\("empty"') "הקוד (Stack<int> מ-Unit4.CollectionsLib, בלי שום הפניה בפרויקט) ב-Program.cs — $(if ($pasted) { 'הודבק בעורך ונשמר' } else { 'נכתב לקובץ' })" ''
        # Visual Studio שומר את קובץ הפרויקט (הקובץ שנוסף) רק כשבונים — שומרים הכול לפני התמונה, כדי שההשוואה תבדוק רק את מה ש-DSViz עשה
        Dte { param($d) $d.ExecuteCommand('File.SaveAll'); 'ok' } 6 | Out-Null
        Start-Sleep 2
        $before = (Get-FileHash $csproj.FullName).Hash
        $beforeText = @(Get-Content $csproj.FullName -Encoding UTF8)
        # 7. הרצה רגילה: Debug > Start Without Debugging (שתי לחיצות עכבר)
        KillRuns; KillJava; ClearMarkers
        $r = NormalRunRetry 'menu' 't14-normal' $emptyName 'empty'
        $used = $r.Used; $m = $r.M
        Start-Sleep 3
        Step 't14-normal-after'
        $why = "הפעלה: $used · Startup: $(Startup) · סימונים: $(Markers)"
        if (-not $m) { Fail 't14-normal' "Empty Project מהאשף — הרצה רגילה ($used)" "התוכנית לא רצה תוך 4 דקות · $why"; DumpUia $script:vsP.Id 't14-no-run' }
        else {
            Check 't14-normal' (($m.Host -ne 'dsviz-cs') -and (@(RunnerProcs).Count -eq 0)) "Empty Project מהאשף — הרצה רגילה ($used): רצה התוכנית עצמה בקונסול (תהליך $($m.Host)), בלי ההרצה הוויזואלית" $why
            Check 't14-normal-lib' ($m.Lib -eq 'Unit4.CollectionsLib') 'Empty Project מהאשף — בהרצה הרגילה Stack מגיע מהספרייה Unit4.CollectionsLib, שנוספה לבד (בלי הפניה בפרויקט)' "בפועל: '$($m.Lib)'"
        }
        for ($i = 0; $i -lt 30 -and -not (DesignMode); $i++) { Start-Sleep 1 }
        KillRuns
        # 8. הרצה ויזואלית: הכפתור בסרגל של DSViz
        CheckVisual 't14-visual' $file 'empty' 'Unit4.CollectionsLib' 'Empty Project מהאשף'
        $afterText = @(Get-Content $csproj.FullName -Encoding UTF8)
        $diff = @(Compare-Object $beforeText $afterText | ForEach-Object { "$($_.SideIndicator) $($_.InputObject.Trim())" })
        Check 't14-project-unchanged' ((Get-FileHash $csproj.FullName).Hash -eq $before) 'שתי ההרצות לא שינו את קובץ הפרויקט של התלמיד (EmptyDemo.csproj)' "שורות ששונו: $($diff -join ' | ')"
    } catch { Fail 't14-exception' 'T14 רץ עד הסוף' "$_" }
    KillRuns; KillJava
    $closed = CloseVsByX
    Check 't14-close' $closed "$title נסגר בסגירה רגילה" ''
}

# ------------------------------------------------------------------ T15: כיבוי DSViz — הלחצן האדום בסוף הסרגל
# לחיצה אמיתית על "כיבוי DSViz" ← "OK" בהודעה: כל שאר הכפתורים של DSViz נעלמים, ונשאר רק הלחצן הירוק "הפעלת DSViz". ההרצה הרגילה
# עובדת כרגיל — גם קוד שמשתמש בספריית הקורס בלי שום הפניה (DSViz.targets נטען מ-MSBuild ולא נכבה) — וההרחבה לא עושה כלום בזמן הזה.
# אחרי סגירה ופתיחה של Visual Studio — עדיין כבוי (vs-power-off.flag). "הפעלת DSViz" ← כל הכפתורים חוזרים, וההרצה הוויזואלית עובדת שוב
Section 'T15. כיבוי DSViz (הלחצן האדום בסוף הסרגל) והפעלה מחדש'
$powerFlag = Join-Path $dsviz 'vs-power-off.flag'
Remove-Item $powerFlag -Force -ErrorAction SilentlyContinue
# הכפתורים הרגילים של DSViz שנראים עכשיו בסרגל (כש-DSViz כבוי — אף אחד)
function PowerShownButtons() { @($script:dsvizNames | Where-Object { $b = ToolbarButton $_; $b -and $b.Current.IsEnabled }) }
function PowerOffToolbar([int]$sec) { WaitFor { if ((@(PowerShownButtons).Count -eq 0) -and (ToolbarButton 'הפעלת DSViz') -and -not (ToolbarButton 'כיבוי DSViz')) { $true } } $sec 1000 }
function PowerOnToolbar([int]$sec) { WaitFor { if ((@(PowerShownButtons).Count -eq $script:dsvizNames.Count) -and (ToolbarButton 'כיבוי DSViz') -and -not (ToolbarButton 'הפעלת DSViz')) { $true } } $sec 1000 }
KillJava; KillVs
$p = StartVs $apps.Sln
if (-not $p) { Fail 't15-open' "$title נפתח עם CiApps.sln" ((Windows) -join ' ; ') }
else {
    $d = $apps.Dir
    try {
        AssertTime
        Start-Sleep 25
        SkipFirstRun $p.Id | Out-Null
        $ready = WaitToolbar 240
        $red = WaitFor { ToolbarButton 'כיבוי DSViz' } 60 1000
        Step 't15-on'
        Check 't15-red' ($ready -and $red -and -not (ToolbarButton 'הפעלת DSViz')) 'DSViz פועל: כל הכפתורים בסרגל, ובסופו הלחצן האדום "כיבוי DSViz" (בלי הירוק)' "$(DescEl $red) · $(DteToolbar)"
        if ($red) {
            $rights = @($script:dsvizNames | ForEach-Object { $b = ToolbarButton $_; if ($b) { [int]$b.Current.BoundingRectangle.Right } })
            $rr = $red.Current.BoundingRectangle
            $last = ($rights.Count -gt 0) -and ($rr.Left -ge (($rights | Measure-Object -Maximum).Maximum - 2))
            Check 't15-red-last' $last 'הלחצן "כיבוי DSViz" הוא האחרון בסרגל של DSViz' "כיבוי: x=$([int]$rr.Left) · הקצה הימני של שאר הכפתורים: $($rights -join ',')"
        }
        # 1. כיבוי: לחיצה אמיתית, הסבר, OK
        $tc = TraceCount
        $clicked = ClickToolbar 'כיבוי DSViz' 't15-power-off'
        $dlg = $null
        if ($clicked) { $dlg = WaitFor { FindMessage 'לכבות את DSViz' } 30 1000 }
        Step 't15-confirm'
        Check 't15-confirm' ($null -ne $dlg) 'לפני הכיבוי: הודעה שמסבירה מה ייכבה (אישור / ביטול)' "חלונות קופצים: $((@(Dialogs | ForEach-Object { $_.Text }) -join ' || '))"
        if ($dlg) {
            Check 't15-confirm-text' (($dlg.Text -match 'לא משתנים') -and ($dlg.Text -match 'הפעלת DSViz')) 'ההודעה אומרת שהקוד והפרויקטים לא משתנים, ואיך מחזירים ("הפעלת DSViz")' ($dlg.Text.Substring(0, [Math]::Min(400, $dlg.Text.Length)))
            if ($dlg.Ok) { RealClick $dlg.Ok 't15-confirm-ok' | Out-Null } else { Info 'כפתור OK לא נמצא — Enter'; [DsReal]::Chord([uint16[]]@(0x0D)); Step 't15-confirm-enter' }
        } else { DumpUia $script:vsP.Id 't15-no-confirm' }
        $off = PowerOffToolbar 30
        Start-Sleep 2
        Step 't15-off'
        Check 't15-off-toolbar' ([bool]$off) 'אחרי "כיבוי": כל הכפתורים של DSViz נעלמו מהסרגל, ונשאר רק הלחצן הירוק "הפעלת DSViz"' "עדיין נראים: $((PowerShownButtons) -join ', ') · הירוק: $(DescEl (ToolbarButton 'הפעלת DSViz')) · האדום: $(DescEl (ToolbarButton 'כיבוי DSViz'))"
        if (-not $off) { DumpUia $script:vsP.Id 't15-off-toolbar' }
        Check 't15-off-saved' ((Test-Path $powerFlag) -and @(TraceSince $tc 'power: off').Count -gt 0) 'המצב "כבוי" נשמר (vs-power-off.flag) ונרשם ביומן ההרחבה ("power: off")' "flag=$(Test-Path $powerFlag) · יומן: $((TraceSince $tc 'power') -join ' | ')"
        # 2. כשכבוי: ההרצה הרגילה של Visual Studio, כמו בלי DSViz — וגם ספריית הקורס עדיין נוספת בבנייה (DSViz.targets)
        $tcRun = TraceCount
        CheckNormal 't15-off-normal' 'SdkApp' (Join-Path $d 'SdkApp\Program.cs') 'sdk' 'ctrlf5' '' 'DSViz כבוי: פרויקט SDK' | Out-Null
        CheckNormal 't15-off-lib' 'ClassicDsviz' (Join-Path $d 'ClassicDsviz\Program.cs') 'classicdsviz' 'menu' 'Unit4.CollectionsLib' 'DSViz כבוי: קוד שמשתמש בספריית הקורס בלי שום הפניה (DSViz.targets ממשיך להוסיף אותה)' | Out-Null
        $busy = @(TraceSince $tcRun 'info bar shown|startup project ->|visual run|solution glyph')
        Check 't15-off-quiet' ($busy.Count -eq 0) 'DSViz כבוי: ההרחבה לא עשתה כלום בזמן ההרצות (אין שורת הודעה, לא החליפה Startup Project, לא הריצה ויזואלית)' ($busy -join ' | ')
    } catch { Fail 't15-exception' 'T15 (כיבוי) רץ עד הסוף' "$_" }
    # 3. סגירה ופתיחה: עדיין כבוי
    KillRuns; KillJava
    $closed = CloseVsByX
    Check 't15-close' $closed "$title נסגר בסגירה רגילה (DSViz כבוי)" ''
    $p = StartVs $apps.Sln
    if (-not $p) { Fail 't15-reopen' "$title נפתח שוב עם CiApps.sln" ((Windows) -join ' ; ') }
    else {
        try {
            AssertTime
            Start-Sleep 25
            SkipFirstRun $p.Id | Out-Null
            # עד שההרחבה נטענת Visual Studio מציג את ברירת המחדל של הסרגל — מחכים שהיא תסתיר את הכפתורים
            $still = PowerOffToolbar 240
            Step 't15-reopened'
            Check 't15-persist' ([bool]$still -and (Test-Path $powerFlag)) 'אחרי סגירה ופתיחה של Visual Studio: DSViz עדיין כבוי — רק הלחצן הירוק "הפעלת DSViz" בסרגל' "עדיין נראים: $((PowerShownButtons) -join ', ') · הירוק: $(DescEl (ToolbarButton 'הפעלת DSViz')) · flag=$(Test-Path $powerFlag) · $(DteToolbar)"
            if (-not $still) { DumpUia $script:vsP.Id 't15-reopened'; TraceTail 'power' }
            # 4. הפעלה: הלחצן הירוק — הכפתורים חוזרים, וההרצה הוויזואלית עובדת שוב
            $tc = TraceCount
            $clicked = ClickToolbar 'הפעלת DSViz' 't15-power-on'
            $on = $false
            if ($clicked) { $on = PowerOnToolbar 60 }
            Start-Sleep 2
            Step 't15-on-again'
            Check 't15-on-toolbar' ([bool]$on) 'אחרי "הפעלת DSViz": כל הכפתורים של DSViz חזרו לסרגל, והלחצן האדום "כיבוי DSViz" שוב בסופו' "נראים: $((PowerShownButtons) -join ', ') · האדום: $(DescEl (ToolbarButton 'כיבוי DSViz')) · הירוק: $(DescEl (ToolbarButton 'הפעלת DSViz'))"
            if (-not $on) { DumpUia $script:vsP.Id 't15-on-toolbar' }
            Check 't15-on-saved' ((-not (Test-Path $powerFlag)) -and @(TraceSince $tc 'power: on').Count -gt 0) 'המצב "פועל" נשמר (vs-power-off.flag נמחק) ונרשם ביומן ההרחבה ("power: on")' "flag=$(Test-Path $powerFlag) · יומן: $((TraceSince $tc 'power') -join ' | ')"
            CheckVisual 't15-on-visual' (Join-Path $apps.Dir 'SdkApp\Program.cs') 'sdk' '' 'אחרי "הפעלת DSViz": פרויקט SDK'
        } catch { Fail 't15-exception-reopen' 'T15 (פתיחה מחדש והפעלה) רץ עד הסוף' "$_" }
        KillRuns; KillJava
        $closed = CloseVsByX
        Check 't15-close-again' $closed "$title נסגר בסגירה רגילה (DSViz פועל שוב)" ''
    }
}
# בכל מקרה: DSViz חוזר להיות פועל (הקובץ נמחק) — שום דבר אחרי הבדיקה הזו לא רץ כשהוא כבוי
Remove-Item $powerFlag -Force -ErrorAction SilentlyContinue

# ------------------------------------------------------------------ יומנים
Section 'יומנים'
KillJava; Get-Process devenv -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
foreach ($f in @($traceLog, (Join-Path $dsviz 'vsix-install.log'), (Join-Path $ctl 'agent.log'))) { if (Test-Path $f) { Copy-Item $f $logs -Force -ErrorAction SilentlyContinue } }
Get-ChildItem $dsviz -Filter 'vs-loaded-*' -ErrorAction SilentlyContinue | ForEach-Object { Copy-Item $_.FullName $logs -Force; Info "$($_.Name): $((Get-Content $_.FullName -Raw) -replace "`r?`n", ' · ')" }
Get-ChildItem $dsviz -Filter 'vs-toolbar-shown*' -ErrorAction SilentlyContinue | ForEach-Object { Info "$($_.Name): $((Get-Content $_.FullName -Raw).Trim())" }
foreach ($pair in @(@((Join-Path $env:APPDATA "Microsoft\VisualStudio\$hive\ActivityLog.xml"), 'ActivityLog.xml'), @((Join-Path $env:LOCALAPPDATA "Microsoft\VisualStudio\$hive\ComponentModelCache\Microsoft.VisualStudio.Default.err"), 'mef-errors.txt'))) {
    if (Test-Path $pair[0]) { Copy-Item $pair[0] (Join-Path $logs $pair[1]) -Force -ErrorAction SilentlyContinue } else { Info "אין $($pair[0])" }
}
$act = Join-Path $logs 'ActivityLog.xml'
if (Test-Path $act) { @(Select-String -Path $act -Pattern 'DsViz' -SimpleMatch -Context 0, 2 | Select-Object -Last 12) | ForEach-Object { Info ("ActivityLog: " + ($_.Line.Trim())) } }
if (Test-Path $traceLog) { @(Get-Content $traceLog -Encoding UTF8 -Tail 25) | ForEach-Object { Info "vs-trace: $_" } }
Save-Summary $title
exit $(if ($script:fails -gt 0) { 1 } else { 0 })
