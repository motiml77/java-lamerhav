# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# עדכון אוטומטי של "מבני נתונים — הרצה ויזואלית" (רץ ברקע, בחלון נסתר):
#   - בכניסה ל-Windows (רשום ב-HKCU\...\Run), ופעם בסגירת Eclipse (התוסף מפעיל אותו עם -AfterEclipse)
#   - בודק אם יש גרסה חדשה (version.json באתר), מוריד את ההתקנה, בודק שהקובץ שלם (SHA-256),
#     מחכה ש-Eclipse ייסגר, ומתקין בשקט (/S /UPDATE) — עם אותן שפות שנבחרו בהתקנה. עבודה של התלמיד לא נוגעים בה.
#   - בלי אינטרנט / שגיאה — לא עושים כלום (ננסה בפעם הבאה). יומן: update.log ליד הקובץ הזה.
param([switch]$AfterEclipse)
$ErrorActionPreference = 'Stop'
$dir = $PSScriptRoot
$log = Join-Path $dir 'update.log'
$versionUrl = 'https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/release/version.json'
function Log([string]$m) { try { Add-Content -LiteralPath $log -Value ("{0:yyyy-MM-dd HH:mm}  {1}" -f (Get-Date), $m) -Encoding UTF8 } catch { } }
function EclipseRunning { return [bool](Get-Process -Name eclipse -ErrorAction SilentlyContinue) }

# רק עדכון אחד בכל פעם
$lock = Join-Path $env:TEMP 'dsviz-update.lock'
try { $fs = [IO.File]::Open($lock, 'OpenOrCreate', 'ReadWrite', 'None') } catch { exit 0 }
try {
    if ($AfterEclipse) { Start-Sleep -Seconds 5 } else { Start-Sleep -Seconds 120 }   # לא להאט את הכניסה ל-Windows
    # לא יותר מבדיקה אחת בשעה
    $stamp = Join-Path $dir 'update-check.txt'
    if ((Test-Path -LiteralPath $stamp) -and ((Get-Date) - (Get-Item -LiteralPath $stamp).LastWriteTime).TotalMinutes -lt 60) { exit 0 }
    Set-Content -LiteralPath $stamp -Value (Get-Date).ToString('s')

    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $local = 0
    $bf = Join-Path $dir 'build.txt'
    if (Test-Path -LiteralPath $bf) { $local = [int64]((Get-Content -LiteralPath $bf -Raw).Trim()) }
    # GitHub מחזיר את הקובץ כטקסט רגיל — ממירים ל-JSON בעצמנו
    $v = (Invoke-WebRequest -Uri $versionUrl -UseBasicParsing -TimeoutSec 30).Content | ConvertFrom-Json
    $remote = [int64]$v.build
    if ($remote -le $local) { exit 0 }
    Log "נמצאה גרסה חדשה: $($v.version) ($remote, מותקנת: $local)"

    $exe = Join-Path $env:TEMP 'DSViz-Update.exe'
    Invoke-WebRequest -Uri $v.url -OutFile $exe -UseBasicParsing -TimeoutSec 900
    $hash = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLower()
    if ($hash -ne ([string]$v.sha256).ToLower()) { Log "הקובץ שהורד לא תקין (SHA-256) — מדלגים"; Remove-Item -LiteralPath $exe -Force; exit 0 }

    # מחכים ש-Eclipse ייסגר (עד 12 שעות), כדי לא להפריע לעבודה
    $waited = 0
    while ((EclipseRunning) -and $waited -lt 720) { Start-Sleep -Seconds 60; $waited++ }
    if (EclipseRunning) { Log "Eclipse עדיין פתוח — ננסה בפעם הבאה"; exit 0 }

    Log "מתקין את הגרסה החדשה..."
    $p = Start-Process -FilePath $exe -ArgumentList '/S', '/UPDATE' -PassThru -Wait -WindowStyle Hidden
    Log "ההתקנה הסתיימה (קוד $($p.ExitCode))"
    Remove-Item -LiteralPath $exe -Force -ErrorAction SilentlyContinue
} catch {
    Log "שגיאה: $($_.Exception.Message)"
} finally {
    $fs.Close()
}
