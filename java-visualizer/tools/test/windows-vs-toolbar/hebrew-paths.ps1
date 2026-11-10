# שם משתמש / פרויקט בעברית (ועם רווח וגרש) ב-Windows שהקידוד שלו לא עברי: ההתקנה ב-C:\בדיקה\משתמש\AppData\Local\DSViz,
# ‏TEMP ו-LOCALAPPDATA בעברית, ופרויקט בתיקייה "מבני נתונים של משה's". מריצים את מה שהסרגל של Visual Studio מריץ:
# הרצה ויזואלית (dsviz-cs.exe → חלון ההרצה), מאגר השאלות (dsviz-cs.exe --tool bank), עמדת הניהול (javaw כמו ה-VSIX).
# ומראים שהדרך הקודמת (נתיב מלא בשורת הפקודה של java) נכשלת כאן.
#   hebrew-paths.ps1 -Root <java-visualizer> -Lib <dsviz.jar> -BankJar <dsviz-bank-vs.jar> -Runner <dotnet publish> -Out <תיקייה>
param([string]$Root, [string]$Lib, [string]$BankJar, [string]$Runner, [string]$Out)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
New-Item -ItemType Directory -Force $Out | Out-Null
$script:fails = 0; $script:lines = New-Object System.Collections.Generic.List[string]
function Log([string]$s) { Write-Host $s; $script:lines.Add($s) }
function Check([string]$id, [bool]$ok, [string]$s, [string]$why = '') { if ($ok) { Log "  ok   $id  $s" } else { $script:fails++; Log "  FAIL $id  $s — $why" } }
function Shot([string]$n) {
    $b = [Windows.Forms.SystemInformation]::VirtualScreen
    $bmp = New-Object Drawing.Bitmap $b.Width, $b.Height
    $g = [Drawing.Graphics]::FromImage($bmp); $g.CopyFromScreen($b.Left, $b.Top, 0, 0, $bmp.Size)
    $bmp.Save((Join-Path $Out "$n.png")); $g.Dispose(); $bmp.Dispose()
}
function KillJava() { Get-Process javaw, java, dsviz-cs -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue; Start-Sleep 1 }
function WaitWindow([string]$like, [int]$sec) {
    for ($i = 0; $i -lt $sec; $i++) {
        $p = Get-Process javaw, java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -and $_.MainWindowTitle -like $like -and $_.MainWindowTitle -notlike '*Virtual Machine Launcher*' }   # חלון השגיאה של המשגר — לא נחשב | Select-Object -First 1
        if ($p) { return $p.MainWindowTitle }
        Start-Sleep 1
    }
    return ''
}

$jdk = $env:JAVA_HOME
$base = 'C:\בדיקה\משתמש'
$inst = "$base\AppData\Local\DSViz"
Remove-Item $base -Recurse -Force -ErrorAction SilentlyContinue
foreach ($d in @("$inst\lab", "$inst\cs", "$base\Temp")) { New-Item -ItemType Directory -Force $d | Out-Null }
Copy-Item $Lib "$inst\dsviz.jar"
Copy-Item $BankJar "$inst\lab\dsviz-bank-vs.jar"
Copy-Item "$Runner\*" "$inst\cs\" -Recurse
$mods = ((Get-Content (Join-Path $Root 'tools\build-jre.sh') -Raw) | Select-String -Pattern '--add-modules (\S+)').Matches[0].Groups[1].Value
# jlink עצמו מקבל את תיקיית הפלט בשורת הפקודה (קידוד ANSI) — בונים בתיקייה באנגלית ומעתיקים
$rtTmp = Join-Path $env:RUNNER_TEMP 'jre-he'
Remove-Item $rtTmp -Recurse -Force -ErrorAction SilentlyContinue
& "$jdk\bin\jlink.exe" --add-modules $mods --strip-debug --no-header-files --no-man-pages --output $rtTmp 2>&1 | ForEach-Object { Log "jlink: $_" }
Copy-Item $rtTmp "$inst\jdk" -Recurse
$csRoot = "$base\Documents\מבני נתונים של משה's\DataStructures-CS"
$proj = "$csRoot\התרגיל הראשון"
New-Item -ItemType Directory -Force $proj | Out-Null
Set-Content "$csRoot\DataStructures-CS.sln" '' -Encoding ascii
[IO.File]::WriteAllText("$proj\Program.cs", "using System;`r`nclass Program {`r`n    static void Main() {`r`n        int[] a = { 3, 1, 2 };`r`n        int s = 0;`r`n        foreach (int x in a) s += x;`r`n        Console.WriteLine(s);`r`n    }`r`n}`r`n", (New-Object Text.UTF8Encoding($false)))
$env:LOCALAPPDATA = "$base\AppData\Local"; $env:TEMP = "$base\Temp"; $env:TMP = "$base\Temp"; $env:USERPROFILE = $base
Log "קידוד ANSI של המערכת: $([Text.Encoding]::Default.CodePage) ($([Text.Encoding]::Default.EncodingName)) · ההתקנה: $inst · הפרויקט: $proj"
$java = "$jdk\bin\java.exe"   # Java בנתיב באנגלית — לבדיקות של הארגומנטים עצמם
Log "8.3: $((fsutil 8dot3name query C: 2>&1) -join ' ')"

# 0. java.exe מתיקיית ההתקנה בעברית (כמו שהיה): המשגר לא מוצא את java.dll
$o = & "$inst\jdk\bin\java.exe" -version 2>&1 | Out-String
Check 'old-java-path-fails' ($o -match 'java\.dll|Error') 'הדרך הקודמת: java.exe מתיקייה בעברית לא עולה כאן' ($o.Trim())

# אבחון: השם הקצר של java.exe — האם המשגר עולה ממנו (אם לא, צריך את הצומת)
$short = (cmd /c "for %I in (`"$inst\jdk\bin\java.exe`") do @echo %~sI" 2>&1 | Out-String).Trim()
$o = & $short -version 2>&1 | Out-String
Log "שם קצר: $short → $(($o -split "`n")[0].Trim())"

# 1. הדרך הקודמת: classpath מלא בשורת הפקודה — java לא מוצאת את המחלקה (הנתיב הופך ל-"?")
$o = & $java -cp "$inst\dsviz.jar" unit4.collectionsLib.TraceViewer --check x 2>&1 | Out-String
Check 'old-classpath-fails' ($LASTEXITCODE -ne 0 -and $o -match 'ClassNotFound|Could not find|find or load') 'הדרך הקודמת: classpath עם עברית בשורת הפקודה של java — נכשל כאן' ($o.Trim())

# 2. קובץ הרצה (dsviz-cs.exe — .NET, Unicode) ל-TEMP בעברית
$trace = "$env:TEMP\ריצה של משה.trace"
$o = & "$inst\cs\dsviz-cs.exe" "$proj\Program.cs" --trace $trace 2>&1 | Out-String
Check 'trace-written' (Test-Path -LiteralPath $trace) 'dsviz-cs.exe הריץ את הפרויקט מתיקייה בעברית וכתב את קובץ ההרצה ל-TEMP בעברית' ($o.Trim())

# 3. נתיב הקובץ בשורת הפקודה (הדרך הקודמת) מול DSVIZ_ARGS (SafeJava / envArgs)
Push-Location $inst
$o = & $java -cp dsviz.jar unit4.collectionsLib.TraceViewer --check $trace 2>&1 | Out-String
Check 'old-args-fail' ($o -notmatch '^ok \d+ steps') 'הדרך הקודמת: נתיב בעברית כארגומנט ל-main — לא נקרא' ($o.Trim())
$env:DSVIZ_ARGS = "--check`n$trace"
$o = & $java -cp dsviz.jar unit4.collectionsLib.TraceViewer --args-env 2>&1 | Out-String
Remove-Item Env:\DSVIZ_ARGS
Check 'env-args' ($o -match 'ok \d+ steps') 'הדרך החדשה: classpath יחסי + DSVIZ_ARGS — קובץ ההרצה נקרא' ($o.Trim())
Pop-Location

# 4. הרצה ויזואלית כמו מהסרגל: dsviz-cs.exe ← חלון ההרצה (javaw, דרך SafeJava)
KillJava
$vErr = Join-Path $env:RUNNER_TEMP 'visual-err.txt'
$p = Start-Process "$inst\cs\dsviz-cs.exe" -ArgumentList "`"$proj\Program.cs`"" -PassThru -WindowStyle Hidden -RedirectStandardError $vErr -RedirectStandardOutput (Join-Path $env:RUNNER_TEMP 'visual-out.txt')
$t = WaitWindow '*' 120
Start-Sleep 3; Shot 'he-1-visual-run'
Log "dsviz-cs.exe (הרצה ויזואלית): $((Get-Content $vErr -Raw -ErrorAction SilentlyContinue) -replace "`r?`n", ' | ')"
Check 'visual-run' ($t -ne '') "הרצה ויזואלית מפרויקט בתיקייה בעברית: נפתח החלון ('$t')" ((Get-Process javaw, java -ErrorAction SilentlyContinue | ForEach-Object { "$($_.ProcessName):$($_.MainWindowTitle)" }) -join ' ; ')
KillJava

# 5. מאגר השאלות: dsviz-cs.exe --tool bank <פרויקט בעברית> — BankVs מקבל את תיקיית התרגילים (נתיב עם "?" היה נכשל ב-Paths.get)
$env:JAVA_TOOL_OPTIONS = '-Ddsviz.access=off'
Start-Process "$inst\cs\dsviz-cs.exe" -ArgumentList '--tool', 'bank', "`"$proj`"" -WindowStyle Hidden
$t = WaitWindow '*מאגר*' 90
Start-Sleep 3; Shot 'he-2-bank'
Check 'bank' ($t -ne '') "מאגר השאלות עם תיקיית תרגילים בעברית: נפתח החלון ('$t')" ((Get-Process javaw, java -ErrorAction SilentlyContinue | ForEach-Object { "$($_.ProcessName):$($_.MainWindowTitle)" }) -join ' ; ')
KillJava
Remove-Item Env:\JAVA_TOOL_OPTIONS

# 6. עמדת הניהול כמו מה-VSIX (javaw, classpath יחסי מתיקיית ההתקנה בעברית), עם כניסת מורה מוכנה (מפתח בדיקה) ב-LOCALAPPDATA בעברית
$seed = Join-Path $env:RUNNER_TEMP 'seed'
New-Item -ItemType Directory -Force $seed | Out-Null
& "$jdk\bin\javac.exe" --release 11 -encoding UTF-8 -nowarn -cp "$BankJar;$Lib" -d $seed (Join-Path $Root 'tools\test\windows-vs-toolbar\SeedSession.java') 2>&1 | ForEach-Object { Log "javac: $_" }
$key = Join-Path $env:RUNNER_TEMP 'seed-key.txt'
$pk = & $java -cp "$seed;$BankJar;$Lib" il.lamerhav.dsviz.SeedSession key - $key 2>&1 | Out-String
if ($pk -match 'PASSKEY (\S+)') { $env:DSVIZ_PASS_KEY = $Matches[1] }
$s = & $java '-Dfile.encoding=UTF-8' -cp "$seed;$BankJar;$Lib" il.lamerhav.dsviz.SeedSession teacher teacher@test.dev $key 2>&1 | Out-String
Log "כניסה מוכנה: $($s.Trim())"
$flag = "$env:LOCALAPPDATA\Lamerhav\.cache\teacher.flag"
Check 'teacher-flag' ((Test-Path -LiteralPath $flag) -and -not (Test-Path -LiteralPath "$inst\teacher.flag")) 'הסימון לסרגל (teacher.flag) נכתב בתיקייה הנסתרת של הזיהוי (LOCALAPPDATA בעברית), לא בתיקיית ההתקנה' $s
# Start-Process (לא &): javaw יורש את הפלט של dsviz-cs.exe, ו-& היה מחכה עד שהחלון נסגר
$tOut = Join-Path $env:RUNNER_TEMP 'teacher-out.txt'; $tErr = Join-Path $env:RUNNER_TEMP 'teacher-err.txt'
Start-Process "$inst\cs\dsviz-cs.exe" -ArgumentList '--tool', 'teacher' -WindowStyle Hidden -RedirectStandardOutput $tOut -RedirectStandardError $tErr
$t = WaitWindow '*עמדת ניהול*' 60
Start-Sleep 3; Shot 'he-3-teacher-panel'
$e = (Get-Content $tErr -Raw -ErrorAction SilentlyContinue) + (Get-Content $tOut -Raw -ErrorAction SilentlyContinue)
if ($e) { Log "dsviz-cs.exe --tool teacher: $($e.Trim())" }
Check 'teacher-panel' ($t -ne '') "עמדת הניהול מהתקנה בתיקייה בעברית: נפתח החלון ('$t')" ''
KillJava

Log "צמתים ל-Java (ProgramData): $((Get-ChildItem $env:ProgramData -Filter 'DSViz-jdk-*' -ErrorAction SilentlyContinue | ForEach-Object Name) -join ', ')"
Log "SUMMARY $(@($script:lines | Where-Object { $_ -match '^  ok ' }).Count) ok / $script:fails FAIL"
[IO.File]::WriteAllLines((Join-Path $Out 'hebrew-paths-summary.txt'), [string[]]$script:lines, (New-Object Text.UTF8Encoding($false)))
exit ([int]($script:fails -gt 0))
