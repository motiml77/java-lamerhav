# (UTF-8 עם BOM)
# בדיקת ההתקנה הציבורית (DSViz-Setup.exe — כולל את מאגר התרגילים והכניסה) על Windows אמיתי של GitHub.
#   -Scenario fresh    התקנה חדשה (/S) ואז עדכון שני (/S /UPDATE) — בלי כפילות; ואז הסרה
#   -Scenario upgrade  קודם ההתקנה הציבורית הנוכחית מ-master (בלי מאגר), ואז החדשה כמו שהעדכון האוטומטי מריץ: /S /UPDATE
#   -Scenario lab      קודם DSViz-Lab-Setup.exe הישן (הוצאה lab-test — מתקין את המאגר בדרך הישנה), ואז החדשה: /S /UPDATE
#   -Scenario labwrap  DSViz-Lab-Setup.exe החדש (עטיפה דקה, נבנית כאן עם NSIS) על מחשב נקי
# מה אמיתי: ההתקנות עצמן, Visual Studio (הקיצורים, ה-jar, חלון הכניסה של המאגר), ההסרה. מה מדומה: Eclipse (תיקייה עם eclipse.exe ו-JDT מדומים
# בתיקיית המשתמש) ו-IntelliJ (תיקיית הגדרות IdeaIC2024.2 ב-APPDATA) — אין אותם על המחשב של GitHub, ולכן נבדק רק שהקבצים הגיעו למקום הנכון.
param([string]$Scenario = 'fresh', [string]$Setup, [string]$Out, [int64]$Build = 0,
      [string]$OldUrl = 'https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/release/DSViz-Setup.exe',
      [string]$LabUrl = 'https://github.com/motiml77/java-lamerhav/releases/download/lab-test/DSViz-Lab-Setup.exe',
      [string]$LabNsi)
$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
New-Item -ItemType Directory -Force $Out | Out-Null
$script:fails = 0
function Check([string]$s, [bool]$c) { if ($c) { Write-Host "  ok   $s" } else { $script:fails++; Write-Host "  FAIL $s" } }
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
function WaitTitle([scriptblock]$ok, [int]$sec) {
    $end = (Get-Date).AddSeconds($sec)
    do {
        $script:titles = @(Get-Process | Where-Object { $_.MainWindowTitle } | ForEach-Object { $_.MainWindowTitle })
        if (& $ok) { Start-Sleep 2; return $true }
        Start-Sleep 1
    } while ((Get-Date) -lt $end)
    return $false
}
function Shot([string]$name) {
    $b = [Windows.Forms.SystemInformation]::VirtualScreen
    $bmp = New-Object Drawing.Bitmap $b.Width, $b.Height
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($b.Left, $b.Top, 0, 0, $bmp.Size)
    $bmp.Save((Join-Path $Out "$name.png"), [Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $bmp.Dispose()
}
function Download([string]$url, [string]$to) {
    for ($i = 1; $i -le 3; $i++) {
        try { Invoke-WebRequest -Uri $url -OutFile $to -UseBasicParsing -TimeoutSec 900; if ((Get-Item $to).Length -gt 50MB) { return } } catch { Write-Host "  הורדה נכשלה ($i): $($_.Exception.Message)" }
    }
    throw "לא ירד: $url"
}
function Run([string]$exe, [string]$a) {
    $t = Measure-Command { $script:p = Start-Process $exe -ArgumentList $a -PassThru -Wait }
    Write-Host "  $(Split-Path $exe -Leaf) $a — $([int]$t.TotalSeconds) שניות, קוד $($script:p.ExitCode)"
    return $script:p.ExitCode
}
function TreeHash([string]$d) {
    if (-not (Test-Path -LiteralPath $d)) { return '' }
    $d = (Resolve-Path -LiteralPath $d).Path.TrimEnd('\')
    return ((Get-ChildItem -LiteralPath $d -Recurse -File | ForEach-Object { $_.FullName.Substring($d.Length) + '|' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash } | Sort-Object) -join "`n")
}

$d = Join-Path $env:LOCALAPPDATA 'DSViz'
$desk = [Environment]::GetFolderPath('Desktop')
$ecl = Join-Path $env:USERPROFILE 'eclipse-sim'
$ijCfg = Join-Path $env:APPDATA 'JetBrains\IdeaIC2024.2'
$lnk1 = Join-Path $desk 'מאגר תרגילים C#.lnk'
$lnk2 = Join-Path $desk 'השאלה — מאגר תרגילים C#.lnk'
$labKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Lab'

# --- מדמים Eclipse (ל-Java, עם JDT) ו-IntelliJ IDEA 2024.2
New-Item -ItemType Directory -Force "$ecl\plugins", "$ecl\dropins", "$ecl\configuration" | Out-Null
Copy-Item "$env:WINDIR\System32\find.exe" "$ecl\eclipse.exe" -Force
Set-Content "$ecl\plugins\org.eclipse.jdt.core_3.38.0.jar" 'sim'
Set-Content "$ecl\eclipse.ini" "-vmargs`r`n-Xmx512m"
New-Item -ItemType Directory -Force "$ijCfg\plugins" | Out-Null
Write-Host "מדומים: Eclipse ב-$ecl, IntelliJ ב-$ijCfg"

function BankState {
    $ecb = @(Get-ChildItem "$ecl\dropins" -Filter 'il.lamerhav.dsviz.bank_*.jar' -ErrorAction SilentlyContinue)
    return [pscustomobject]@{
        EclBank = $ecb.Count
        EclBankHash = $(if ($ecb.Count) { (Get-FileHash $ecb[0].FullName).Hash } else { '' })
        EclMain = @(Get-ChildItem "$ecl\dropins" -Filter 'il.lamerhav.dsviz_*.jar' -ErrorAction SilentlyContinue).Count
        IjBank = TreeHash "$ijCfg\plugins\dsviz-bank-intellij"
        IjMain = Test-Path "$ijCfg\plugins\dsviz-intellij\lib"
        VsJar = $(if (Test-Path "$d\lab\dsviz-bank-vs.jar") { (Get-FileHash "$d\lab\dsviz-bank-vs.jar").Hash } else { '' })
    }
}
function CheckBank([string]$when, $st) {
    Check "$when — Eclipse: מאגר אחד ב-dropins (בלי כפילות)" ($st.EclBank -eq 1)
    Check "$when — Eclipse: גם התוסף הראשי (אחד)" ($st.EclMain -eq 1)
    Check "$when — IntelliJ: התוסף הראשי והמאגר (dsviz-bank-intellij\lib\*.jar)" ($st.IjMain -and ((Get-ChildItem "$ijCfg\plugins\dsviz-bank-intellij\lib" -Filter *.jar -ErrorAction SilentlyContinue).Count -ge 1))
    Check "$when — Visual Studio: lab\dsviz-bank-vs.jar" ($st.VsJar -ne '')
    Check "$when — Visual Studio: שני הקיצורים בשולחן העבודה" ((Test-Path $lnk1) -and (Test-Path $lnk2))
    $pj = Get-ChildItem "$d\bank" -Filter 'il.lamerhav.dsviz.bank_*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
    Check "$when — מה שב-dropins הוא המאגר של ההתקנה הנוכחית" ($pj -and $st.EclBankHash -eq (Get-FileHash $pj.FullName).Hash)
}
function CheckBuild([string]$when) {
    $b = if (Test-Path "$d\build.txt") { (Get-Content "$d\build.txt" -Raw).Trim() } else { '' }
    Write-Host "     build.txt = $b (צפוי $Build)"
    Check "$when — build.txt = הבנייה החדשה" ($Build -gt 0 -and $b -eq "$Build")
}
function OpenBankGate([string]$shot) {
    $csDir = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'DataStructures-CS'
    $p = Start-Process "$d\jdk\bin\javaw.exe" -ArgumentList "-cp `"$d\lab\dsviz-bank-vs.jar;$d\dsviz.jar`" il.lamerhav.dsviz.BankVs bank `"$csDir`"" -PassThru
    $ok = WaitTitle { $script:titles -contains 'כניסה למאגר התרגילים' } 90
    Write-Host "     חלונות: $($script:titles -join ' | ')"
    Shot $shot
    Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
    Check 'Visual Studio: המאגר מהתקנה הציבורית נפתח ובלי כניסה מציג "כניסה למאגר התרגילים"' $ok
}

$tmpOld = Join-Path $env:RUNNER_TEMP 'old-setup.exe'
switch ($Scenario) {
    'fresh' {
        $c = Run $Setup '/S /LANG=both'
        Check 'התקנה חדשה הסתיימה (קוד 0)' ($c -eq 0)
        Check 'DSViz הותקן (dsviz.jar, jdk)' ((Test-Path "$d\dsviz.jar") -and (Test-Path "$d\jdk\bin\javaw.exe"))
        CheckBuild 'חדשה'
        $s1 = BankState; CheckBank 'חדשה' $s1
        OpenBankGate 'fresh-bank-gate'
        $c = Run $Setup '/S /UPDATE'
        Check 'עדכון שני (/S /UPDATE) הסתיים (קוד 0)' ($c -eq 0)
        $s2 = BankState; CheckBank 'עדכון חוזר' $s2
        Check 'אידמפוטנטי: אותם קבצים אחרי עדכון חוזר' (($s1.EclBankHash -eq $s2.EclBankHash) -and ($s1.IjBank -eq $s2.IjBank) -and ($s1.VsJar -eq $s2.VsJar))
        $c = Run "$d\uninstall.exe" "/S _?=$d"
        $s3 = BankState
        Check 'הסרה: המאגר הוסר מ-Eclipse' ($s3.EclBank -eq 0 -and $s3.EclMain -eq 0)
        Check 'הסרה: המאגר הוסר מ-IntelliJ' ($s3.IjBank -eq '' -and -not $s3.IjMain)
        Check 'הסרה: הקיצורים וה-jar של Visual Studio הוסרו' (-not (Test-Path $lnk1) -and -not (Test-Path $lnk2) -and -not (Test-Path "$d\lab"))
    }
    'upgrade' {
        Download $OldUrl $tmpOld
        $c = Run $tmpOld '/S /LANG=both'
        Check 'ההתקנה הציבורית הנוכחית (master) הותקנה' ($c -eq 0 -and (Test-Path "$d\dsviz.jar"))
        $before = BankState
        # ההתקנה שב-master כבר כוללת את המאגר (מאז build 202611080427) — רק רושמים מה היה; הבדיקות שאחרי העדכון הן העיקר (מאגר אחד, בלי כפילות)
        Write-Host "     לפני: מאגר ב-Eclipse=$($before.EclBank), IntelliJ='$($before.IjBank)', Visual Studio='$($before.VsJar)'"
        Check 'לפני: התוסף הראשי ב-Eclipse וב-IntelliJ' ($before.EclMain -eq 1 -and $before.IjMain)
        $oldBuild = (Get-Content "$d\build.txt" -Raw).Trim(); Write-Host "     build לפני: $oldBuild"
        $c = Run $Setup '/S /UPDATE'
        Check 'עדכון (/S /UPDATE) הסתיים (קוד 0)' ($c -eq 0)
        CheckBuild 'אחרי עדכון'
        Check 'ה-build עלה' ([int64]$oldBuild -lt $Build)
        $s1 = BankState; CheckBank 'אחרי עדכון' $s1
        Check 'lang.txt נשאר both (השפות לא השתנו)' ((Get-Content "$d\lang.txt" -Raw).Trim() -eq 'both')
        OpenBankGate 'upgrade-bank-gate'
        $c = Run $Setup '/S /UPDATE'
        $s2 = BankState; CheckBank 'עדכון חוזר' $s2
        Check 'אידמפוטנטי: אותם קבצים אחרי עדכון חוזר' (($s1.EclBankHash -eq $s2.EclBankHash) -and ($s1.IjBank -eq $s2.IjBank) -and ($s1.VsJar -eq $s2.VsJar))
    }
    'lab' {
        Download $LabUrl $tmpOld
        $c = Run $tmpOld '/S'
        Check 'DSViz-Lab-Setup.exe הישן הותקן' ($c -eq 0 -and (Test-Path "$d\dsviz.jar"))
        $labDir = Join-Path $env:LOCALAPPDATA 'DSViz-Lab'
        $before = BankState
        Write-Host "     לפני: Eclipse bank=$($before.EclBank) main=$($before.EclMain), IJ main=$($before.IjMain), VS jar=$([bool]$before.VsJar)"
        Check 'לפני (מעבדה): המאגר מותקן (Eclipse, IntelliJ, Visual Studio)' ($before.EclBank -eq 1 -and $before.IjBank -ne '' -and $before.VsJar -ne '' -and (Test-Path $lnk1))
        Check 'לפני (מעבדה): DSViz-Lab רשום ויש לו תיקייה' ((Test-Path $labKey) -and (Test-Path $labDir))
        $c = Run $Setup '/S /UPDATE'
        Check 'עדכון (/S /UPDATE) מעל מעבדה הסתיים (קוד 0)' ($c -eq 0)
        CheckBuild 'אחרי עדכון מעל מעבדה'
        $s1 = BankState; CheckBank 'אחרי עדכון מעל מעבדה' $s1
        Check 'המאגר נשאר (לא נמחק) ועודכן לגרסה החדשה' (($s1.EclBank -eq 1) -and ($s1.VsJar -ne ''))
        Check 'DSViz-Lab הוחלף: אין רישום ואין תיקייה (ההסרה שלו לא תמחק את המאגר)' (-not (Test-Path $labKey) -and -not (Test-Path $labDir))
        OpenBankGate 'lab-bank-gate'
        $c = Run $Setup '/S /UPDATE'
        $s2 = BankState; CheckBank 'עדכון חוזר' $s2
        Check 'אידמפוטנטי: אותם קבצים אחרי עדכון חוזר' (($s1.EclBankHash -eq $s2.EclBankHash) -and ($s1.IjBank -eq $s2.IjBank) -and ($s1.VsJar -eq $s2.VsJar))
    }
    'labwrap' {
        $mk = "${env:ProgramFiles(x86)}\NSIS\makensis.exe"
        if (-not (Test-Path $mk)) { $mk = "$env:ProgramFiles\NSIS\makensis.exe" }
        $parts = Join-Path $env:RUNNER_TEMP 'lab-parts'; $outd = Join-Path $env:RUNNER_TEMP 'lab-out'
        New-Item -ItemType Directory -Force $parts, $outd | Out-Null
        Copy-Item $Setup "$parts\DSViz-Setup.exe" -Force
        Push-Location (Split-Path $LabNsi -Parent)
        & $mk /V2 "/DBUILDNO=1" "/DNOAMD64=1" "/DOUTDIR=$outd" "/DPARTS=$parts" (Split-Path $LabNsi -Leaf)
        Pop-Location
        Check 'העטיפה (DSViz-Lab-Setup.exe) נבנתה' (Test-Path "$outd\DSViz-Lab-Setup.exe")
        Write-Host "     גודל העטיפה: $([int]((Get-Item "$outd\DSViz-Lab-Setup.exe").Length / 1MB)) MB"
        $c = Run "$outd\DSViz-Lab-Setup.exe" '/S'
        Check 'העטיפה הותקנה בשקט (קוד 0)' ($c -eq 0)
        CheckBuild 'עטיפה'
        $s1 = BankState; CheckBank 'עטיפה' $s1
        Check 'אין תיקיית DSViz-Lab חדשה' (-not (Test-Path (Join-Path $env:LOCALAPPDATA 'DSViz-Lab')))
        OpenBankGate 'labwrap-bank-gate'
    }
}
Write-Host ""
Write-Host "$(if ($script:fails) { "$($script:fails) נכשלו" } else { 'הכול עבר' })"
exit $script:fails
