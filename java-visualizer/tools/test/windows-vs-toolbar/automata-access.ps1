# "מודלים חישוביים" כתוכנה מקומית ב-Windows אמיתי, עם הכניסה המשותפת, בשם משתמש בעברית (Windows באנגלית):
#   ההתקנה ב-C:\בדיקה\מודלים\AppData\Local\DSViz (dsviz.jar, automata\dsviz-automata.jar, automata\app, jdk), LOCALAPPDATA / APPDATA בעברית.
#   1. כניסת מורה מוכנה (כמו אחרי כניסה ב-Eclipse; SeedSession עם מפתח בדיקה) → המשגר עולה כמו מהקיצור (javaw, classpath יחסי)
#      ופותח חלון של Edge (--app) → ‎/api/access‎ מראה את המורה.
#   2. אבטחה: בלי מפתח / מפתח שגוי → 401; Host זר → 403.
#   3. התנתקות דרך המשגר (‎/api/signout‎) → teacher.flag נמחק, ו-Account / AccessGate (הקוד של Eclipse) רואים "לא מחובר".
#   4. עמדת מחשב (s.dat של כל המחשב) — המשגר רואה אותה; התנתקות לא משחררת עמדה.
#   5. הפעלה שנייה (עוד לחיצה על הקיצור) → אותו שרת; סוגרים את החלון → המשגר יוצא.
#   automata-access.ps1 -Root <java-visualizer> -Lib <dsviz.jar> -Automata <תיקייה עם dsviz-automata.jar [ו-app]> -Out <תיקייה>
param([string]$Root, [string]$Lib, [string]$Automata, [string]$Out)
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
function Launchers { @(Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" | Where-Object { $_.CommandLine -like '*il.lamerhav.dsviz.AutomataLauncher*' }) }

$jdk = $env:JAVA_HOME
$base = 'C:\בדיקה\מודלים'
$inst = "$base\AppData\Local\DSViz"
Remove-Item $base -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$inst\automata", "$base\AppData\Roaming", "$base\Temp" | Out-Null
Copy-Item $Lib "$inst\dsviz.jar"
Copy-Item (Join-Path $Automata 'dsviz-automata.jar') "$inst\automata\"
if (Test-Path (Join-Path $Automata 'app\index.html')) {
    Copy-Item (Join-Path $Automata 'app') "$inst\automata\app" -Recurse
} else {
    # האפליקציה בענף automat — כאן דף קטן (access-client.js מגיע מתוך ה-jar)
    New-Item -ItemType Directory -Force "$inst\automata\app" | Out-Null
    [IO.File]::WriteAllText("$inst\automata\app\index.html", '<!doctype html><html lang="he" dir="rtl"><meta charset="utf-8"><title>מודלים חישוביים</title><body style="font:24px sans-serif;padding:40px"><h1>מודלים חישוביים</h1><p id="who">...</p><script type="module">import { DSVizAccess } from "./js/access-client.js"; DSVizAccess.onChange(a => { document.getElementById("who").textContent = a.state + " " + (a.email || a.name || ""); });</script></body></html>', (New-Object Text.UTF8Encoding($false)))
}
$mods = ((Get-Content (Join-Path $Root 'tools\build-jre.sh') -Raw) | Select-String -Pattern '--add-modules (\S+)').Matches[0].Groups[1].Value
$rtTmp = Join-Path $env:RUNNER_TEMP 'jre-automata'
Remove-Item $rtTmp -Recurse -Force -ErrorAction SilentlyContinue
& "$jdk\bin\jlink.exe" --add-modules $mods --strip-debug --no-header-files --no-man-pages --output $rtTmp 2>&1 | ForEach-Object { Log "jlink: $_" }
Copy-Item $rtTmp "$inst\jdk" -Recurse
$env:LOCALAPPDATA = "$base\AppData\Local"; $env:APPDATA = "$base\AppData\Roaming"; $env:TEMP = "$base\Temp"; $env:TMP = "$base\Temp"
$java = "$jdk\bin\java.exe"
$idDir = "$env:LOCALAPPDATA\Lamerhav\.cache"
$flag = "$idDir\teacher.flag"
# javaw.exe כמו הקיצור שההתקנה יוצרת: installer\automata.ps1 -JavaPath (צומת באנגלית ב-ProgramData; השם הקצר 8.3 לא מספיק —
# ה-JVM טוען את net.dll מהנתיב הארוך). מראים גם שהשם הקצר נכשל כאן
$short = (cmd /c "for %I in (`"$inst\jdk\bin\java.exe`") do @echo %~sI" 2>&1 | Out-String).Trim()
$o = & $short -cp "$inst\dsviz.jar" unit4.collectionsLib.TraceViewer --check x 2>&1 | Out-String
Log "java מהשם הקצר ($short): $(($o -split "`n" | Select-Object -First 2) -join ' | ')"
$javaw = (& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Root 'installer\automata.ps1') -JavaPath "$inst\jdk" | Out-String).Trim()
Check 'java-junction' ($javaw -ne '-' -and $javaw -notmatch '[^\x00-\x7F]' -and (Test-Path $javaw)) "הקיצור: javaw דרך צומת באנגלית ($javaw)" $javaw
if ($javaw -eq '-' -or -not (Test-Path $javaw)) { $javaw = "$inst\jdk\bin\javaw.exe" }
Log "ההתקנה: $inst · javaw: $javaw · ANSI: $([Text.Encoding]::Default.CodePage)"

# ---- כניסת מורה מוכנה (כמו אחרי כניסה ב-Eclipse), עם מפתח בדיקה (DSVIZ_PASS_KEY — גם למשגר)
$seed = Join-Path $env:RUNNER_TEMP 'seed-automata'
New-Item -ItemType Directory -Force $seed | Out-Null
& "$jdk\bin\javac.exe" --release 11 -encoding UTF-8 -nowarn -cp $Lib -d $seed (Join-Path $Root 'tools\test\windows-vs-toolbar\SeedSession.java') 2>&1 | ForEach-Object { Log "javac: $_" }
$key = Join-Path $env:RUNNER_TEMP 'seed-automata-key.txt'
function Seed([string]$what, [string]$arg = '-') { return (& $java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp "$seed;$Lib" il.lamerhav.dsviz.SeedSession $what $arg $key 2>&1 | Out-String).Trim() }
$pk = Seed 'key'
if ($pk -match 'PASSKEY (\S+)') { $env:DSVIZ_PASS_KEY = $Matches[1] }
$env:DSVIZ_WORKER_URL = 'http://127.0.0.1:9'   # בלי שרת אמיתי: הבדיקה מול השרת בהפעלה — "בלי אינטרנט" (לא מוחקת כלום)
Log "מורה: $(Seed 'teacher' 'teacher@test.dev')"
Check 'seed-teacher' ((Seed 'show') -match '^STATE teacher' -and (Test-Path -LiteralPath $flag)) "כניסת מורה מוכנה (כמו Eclipse), והסימון teacher.flag בתיקייה הנסתרת ($flag)" (Seed 'show')

# ---- המשגר, כמו מהקיצור "מודלים חישוביים"
$data = "$env:LOCALAPPDATA\DSViz\automata-data"
Get-Process msedge -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
$lErr = Join-Path $env:RUNNER_TEMP 'automata-err.txt'
Start-Process -FilePath $javaw -ArgumentList '-cp', '"automata\dsviz-automata.jar;dsviz.jar"', 'il.lamerhav.dsviz.AutomataLauncher' -WorkingDirectory $inst -RedirectStandardError $lErr -RedirectStandardOutput (Join-Path $env:RUNNER_TEMP 'automata-out.txt')
$inf = $null
for ($i = 0; $i -lt 60 -and -not $inf; $i++) {
    Start-Sleep 1
    if (Test-Path -LiteralPath "$data\instance.json") { try { $inf = Get-Content -LiteralPath "$data\instance.json" -Raw | ConvertFrom-Json } catch { } }
}
Check 'launcher-up' ($null -ne $inf) "המשגר עלה מתיקיית ההתקנה בעברית (instance.json ב-$data)" ((Get-Content $lErr -Raw -ErrorAction SilentlyContinue))
$port = [int]$inf.port; $tok = [string]$inf.token
$api = "http://127.0.0.1:$port"
$h = @{ 'X-DSViz-Token' = $tok }
$edge = $false
for ($i = 0; $i -lt 30 -and -not $edge; $i++) {
    Start-Sleep 1
    $edge = [bool](@(Get-CimInstance Win32_Process -Filter "Name='msedge.exe'" | Where-Object { $_.CommandLine -like '*--app=http://127.0.0.1:*' -and $_.CommandLine -like '*automata-data\edge*' }).Count)
}
Start-Sleep 6
Shot 'automata-1-window-teacher'
Check 'edge-app' $edge 'נפתח חלון של Edge במצב תוכנה (--app) עם פרופיל משלו (automata-data\edge)' ((Get-CimInstance Win32_Process -Filter "Name='msedge.exe'" | Select-Object -First 3 | ForEach-Object CommandLine) -join ' | ')
Check 'token-not-in-cmdline' (-not (@(Get-CimInstance Win32_Process -Filter "Name='msedge.exe'" | Where-Object { $_.CommandLine -like "*$tok*" }).Count)) 'המפתח לא בשורת הפקודה של Edge (רק קוד פתיחה חד-פעמי)'

$a = try { Invoke-RestMethod "$api/api/access" -Headers $h } catch { $null }
Check 'access-teacher' ($a.state -eq 'teacher' -and $a.email -eq 'teacher@test.dev') "‎/api/access‎: מורה (מהכניסה של 'Eclipse')" ($a | ConvertTo-Json -Compress)
Check 'no-secrets' ((($a | ConvertTo-Json -Compress) -notmatch 'seedsession|"token"|"pass"')) 'בתשובה אין כרטיס / אישור' ($a | ConvertTo-Json -Compress)
$st = try { (Invoke-WebRequest "$api/api/access" -UseBasicParsing).StatusCode } catch { [int]$_.Exception.Response.StatusCode }
Check 'no-token-401' ($st -eq 401) 'בלי מפתח → 401' "$st"
$st = try { (Invoke-WebRequest "$api/api/access" -Headers @{ 'X-DSViz-Token' = 'x' + $tok.Substring(1) } -UseBasicParsing).StatusCode } catch { [int]$_.Exception.Response.StatusCode }
Check 'bad-token-401' ($st -eq 401) 'מפתח שגוי → 401' "$st"
$st = try { (Invoke-WebRequest "$api/api/access" -Headers @{ 'X-DSViz-Token' = $tok; 'Origin' = 'https://evil.example' } -UseBasicParsing).StatusCode } catch { [int]$_.Exception.Response.StatusCode }
Check 'foreign-origin-403' ($st -eq 403) 'Origin זר → 403' "$st"

# ---- התנתקות דרך המשגר → בכל התוכנות
$so = try { Invoke-RestMethod "$api/api/signout" -Method Post -Headers $h -ContentType 'application/json' -Body '{}' } catch { $null }
Start-Sleep 2
$show = Seed 'show'
Check 'signout' ($so.ok -eq $true) '‎/api/signout‎ ok' ($so | ConvertTo-Json -Compress)
Check 'flag-gone' (-not (Test-Path -LiteralPath $flag)) 'teacher.flag נמחק (סרגל Visual Studio מסתיר את "עמדת ניהול")'
Check 'eclipse-signed-out' ($show -match '^STATE none' -and $show -match 'teacher=false') "Account / AccessGate (הקוד של Eclipse) — לא מחובר ($show)" $show
Start-Sleep 3
Shot 'automata-2-window-signed-out'

# ---- כניסה מחדש ב-'Eclipse' → המשגר רואה מיד (בלי הפעלה מחדש)
Seed 'teacher' 'teacher2@test.dev' | Out-Null
$a = try { Invoke-RestMethod "$api/api/access" -Headers $h } catch { $null }
Check 'live-teacher' ($a.state -eq 'teacher' -and $a.email -eq 'teacher2@test.dev') 'כניסה חדשה ב-Eclipse — המשגר רואה אותה' ($a | ConvertTo-Json -Compress)
Seed 'none' | Out-Null

# ---- עמדת מחשב (לכל המחשב) — משותפת
Log "עמדה: $(Seed 'station' 'מעבדה 2 · עמדה 5')"
$a = try { Invoke-RestMethod "$api/api/access" -Headers $h } catch { $null }
Check 'station-shared' ($a.state -eq 'station') "‎/api/access‎: עמדת מחשב (s.dat ב-%ProgramData%\Lamerhav\.cache)" ($a | ConvertTo-Json -Compress)
$so = try { Invoke-RestMethod "$api/api/signout" -Method Post -Headers $h -ContentType 'application/json' -Body '{}' } catch { $null }
Check 'station-stays' ($so.status -eq 'station' -and ((Seed 'show') -match '^STATE station')) 'התנתקות לא משחררת עמדה (רק המורה)' ($so | ConvertTo-Json -Compress)
Remove-Item -LiteralPath "$env:ProgramData\Lamerhav\.cache\s.dat" -Force -ErrorAction SilentlyContinue

# ---- הפעלה שנייה → אותו שרת, עוד חלון
Start-Process -FilePath $javaw -ArgumentList '-cp', '"automata\dsviz-automata.jar;dsviz.jar"', 'il.lamerhav.dsviz.AutomataLauncher' -WorkingDirectory $inst
Start-Sleep 8
Check 'single-instance' ((Launchers).Count -eq 1) "הפעלה שנייה — לא שרת שני ($((Launchers).Count) משגרים)"
Shot 'automata-3-second-launch'

# ---- סוגרים את החלונות → המשגר יוצא
Get-Process msedge -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle } | ForEach-Object { [void]$_.CloseMainWindow() }
Start-Sleep 3
@(Get-CimInstance Win32_Process -Filter "Name='msedge.exe'" | Where-Object { $_.CommandLine -like '*automata-data\edge*' }) | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
$gone = $false
for ($i = 0; $i -lt 120 -and -not $gone; $i++) { Start-Sleep 1; $gone = (Launchers).Count -eq 0 }
Check 'exit-after-window' $gone "החלון נסגר — המשגר יצא ($i שניות)"
Check 'instance-cleaned' (-not (Test-Path -LiteralPath "$data\instance.json")) 'instance.json נמחק ביציאה'
foreach ($p in Launchers) { Stop-Process -Id $p.ProcessId -Force }

Log "SUMMARY $(@($script:lines | Where-Object { $_ -match '^  ok ' }).Count) ok / $script:fails FAIL"
[IO.File]::WriteAllLines((Join-Path $Out 'automata-access-summary.txt'), [string[]]$script:lines, (New-Object Text.UTF8Encoding($false)))
exit ([int]($script:fails -gt 0))
