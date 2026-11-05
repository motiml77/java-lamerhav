# גרסת הבדיקה על Windows אמיתי: DSViz-Lab-Setup.exe ו-DSViz-Admin-Setup.exe בשקט, ואז:
# הקבצים במקום, הקיצורים, מסך הכניסה שנפתח לפני המאגר (צילום), ועמדת מחשב אמיתית מול השרת עם קוד המורה.
param([string]$Dir, [string]$Out, [string]$Code)
$ErrorActionPreference = 'Continue'
New-Item -ItemType Directory -Force $Out | Out-Null
$script:fails = 0
function Check([string]$s, [bool]$c) { if ($c) { Write-Host "  ok   $s" } else { $script:fails++; Write-Host "  FAIL $s" } }
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
function Shot([string]$name) {
    $b = [Windows.Forms.SystemInformation]::VirtualScreen
    $bmp = New-Object Drawing.Bitmap $b.Width, $b.Height
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($b.Left, $b.Top, 0, 0, $bmp.Size)
    $bmp.Save((Join-Path $Out "$name.png"), [Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $bmp.Dispose()
}
$t = Measure-Command { Start-Process (Join-Path $Dir 'DSViz-Lab-Setup.exe') -ArgumentList '/S' -Wait }
Write-Host "התקנה: $([int]$t.TotalSeconds) שניות"
$d = Join-Path $env:LOCALAPPDATA 'DSViz'
$desk = [Environment]::GetFolderPath('Desktop')
Check 'DSViz הותקן (dsviz.jar, jdk)' ((Test-Path "$d\dsviz.jar") -and (Test-Path "$d\jdk\bin\javaw.exe"))
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [IO.Compression.ZipFile]::OpenRead("$d\dsviz.jar")
Check 'dsviz.jar כולל את מסך הכניסה (AccessGate, Account)' (($z.Entries | Where-Object { $_.FullName -eq 'il/lamerhav/dsviz/AccessGate.class' }) -and ($z.Entries | Where-Object { $_.FullName -eq 'il/lamerhav/dsviz/Account.class' }))
$z.Dispose()
Check 'מאגר ל-Visual Studio (lab\dsviz-bank-vs.jar)' (Test-Path "$d\lab\dsviz-bank-vs.jar")
Check 'קיצור "מאגר תרגילים C#" בשולחן העבודה' (Test-Path (Join-Path $desk 'מאגר תרגילים C#.lnk'))
Check 'רשום בהסרת תוכניות (DSViz-Lab)' (Test-Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Lab')
Start-Process (Join-Path $Dir 'DSViz-Admin-Setup.exe') -ArgumentList '/S' -Wait
$adm = Join-Path $desk 'ניהול DSViz.lnk'
Check 'קיצור "ניהול DSViz" בשולחן העבודה' (Test-Path $adm)
if (Test-Path $adm) {
    $s = (New-Object -ComObject WScript.Shell).CreateShortcut($adm)
    Write-Host "     $($s.TargetPath) $($s.Arguments)"
    Check 'הקיצור פותח את /admin בחלון נפרד' ($s.Arguments -like '*--app=https://dsviz-exercise.motiml77.workers.dev/admin*')
}
# המאגר (Visual Studio): בלי כניסה — נפתח מסך הכניסה ולא המאגר
$csDir = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'DataStructures-CS'
$p = Start-Process "$d\jdk\bin\javaw.exe" -ArgumentList "-cp `"$d\lab\dsviz-bank-vs.jar;$d\dsviz.jar`" il.lamerhav.dsviz.BankVs bank `"$csDir`"" -PassThru
Start-Sleep 20
$titles = Get-Process | Where-Object { $_.MainWindowTitle } | ForEach-Object { $_.MainWindowTitle }
Write-Host "     חלונות: $($titles -join ' | ')"
Shot '1-bank-gate'
Check 'בלי כניסה — נפתח "כניסה למאגר התרגילים"' ($titles -contains 'כניסה למאגר התרגילים')
Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
# עמדת מחשב אמיתית: הקוד של המורה מול השרת, ואחר כך בלי אינטרנט
if ($Code) {
    $w = Join-Path $env:RUNNER_TEMP 'live'
    & "$d\jdk\bin\java.exe" -Dfile.encoding=UTF-8 -cp "$env:RUNNER_TEMP\cls;$d\dsviz.jar" il.lamerhav.dsviz.LiveClaim $w $Code "בדיקת התקנה (GitHub) - למחוק"
    Check 'עמדה: הקוד התקבל בשרת, והמחשב מזוהה גם בלי אינטרנט' ($LASTEXITCODE -eq 0)
    # עכשיו המאגר נפתח מיד (בלי מסך כניסה)
    $p = Start-Process "$d\jdk\bin\javaw.exe" -ArgumentList "-Ddsviz.machineDir=`"$w\machine`" -Ddsviz.home=`"$w\user`" -cp `"$d\lab\dsviz-bank-vs.jar;$d\dsviz.jar`" il.lamerhav.dsviz.BankVs bank `"$csDir`"" -PassThru
    Start-Sleep 20
    $titles = Get-Process | Where-Object { $_.MainWindowTitle } | ForEach-Object { $_.MainWindowTitle }
    Write-Host "     חלונות: $($titles -join ' | ')"
    Shot '2-bank-open'
    Check 'אחרי הכניסה — המאגר נפתח בלי מסך הכניסה' (-not ($titles -contains 'כניסה למאגר התרגילים') -and ($titles | Where-Object { $_ -like '*מאגר*' }))
    Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
}
Write-Host ""
Write-Host "$(if ($script:fails) { "$($script:fails) נכשלו" } else { 'הכול עבר' })"
exit $script:fails
