# מזהה המחשב ותיקיית העמדה ב-Windows אמיתי (access/README.md, "עמדת מחשב שלא משתחררת לבד"):
# המזהה נשמר ב-ProgramData עם מזהה Windows האמיתי, נשאר אותו דבר כשאי אפשר לקרוא את הרישום (reg.exe חסום לתלמידים),
# גם דרך PowerShell, גם אחרי קובץ בפורמט הישן; התיקייה המשותפת פתוחה לכתיבה לכל המשתמשים; והמזהה הקבוע לא תלוי בקבצים.
#   station-id.ps1 -Root <java-visualizer> -Lib <dsviz.jar> -Out <תיקייה>
param([Parameter(Mandatory = $true)][string]$Root, [Parameter(Mandatory = $true)][string]$Lib, [Parameter(Mandatory = $true)][string]$Out)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
New-Item -ItemType Directory -Force $Out | Out-Null
$script:fails = 0; $script:lines = New-Object System.Collections.Generic.List[string]
function Log([string]$s) { Write-Host $s; $script:lines.Add($s) }
function Check([string]$id, [bool]$ok, [string]$s, [string]$why = '') { if ($ok) { Log "  ok   $id  $s" } else { $script:fails++; Log "  FAIL $id  $s — $why" } }
$jdk = $env:JAVA_HOME
$w = Join-Path $env:RUNNER_TEMP 'stationid'
Remove-Item $w -Recurse -Force -ErrorAction SilentlyContinue
$cls = Join-Path $w 'cls'
New-Item -ItemType Directory -Force $cls | Out-Null
& "$jdk\bin\javac.exe" -encoding UTF-8 -nowarn -cp $Lib -d $cls (Join-Path $Root 'tools\test\windows-vs-toolbar\StationId.java') 2>&1 | ForEach-Object { Log "javac: $_" }
$dirs = @((Join-Path $env:ProgramData 'Lamerhav'), (Join-Path $env:LOCALAPPDATA 'Lamerhav'), (Join-Path $env:APPDATA 'Lamerhav'))
function Wipe { foreach ($d in $dirs) { if (Test-Path $d) { Get-ChildItem $d -Recurse -Force | ForEach-Object { $_.Attributes = 'Normal' }; Remove-Item $d -Recurse -Force -ErrorAction SilentlyContinue } } }
function Run([string[]]$opts) {
    $o = & "$jdk\bin\java.exe" @opts '-Dstdout.encoding=UTF-8' -cp "$cls;$Lib" il.lamerhav.dsviz.StationId 2>&1 | Where-Object { $_ -notmatch 'JAVA_TOOL_OPTIONS' }
    $t = ($o -join "`n"); Log ('  ' + ($t -replace "`n", ' | '))
    return @{ machine = ([regex]::Match($t, 'MACHINE (\S+)').Groups[1].Value); stable = ([regex]::Match($t, 'STABLE (\S*)').Groups[1].Value) }
}
function IdLines { return @(Get-Content (Join-Path $env:ProgramData 'Lamerhav\.cache\i.dat') -Force -Encoding UTF8) }
$guid = (Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Cryptography' -Name MachineGuid).MachineGuid
Log "MachineGuid של המחשב: $guid ; שם המחשב: $env:COMPUTERNAME"

Wipe
$a = Run @()
$l = IdLines
Check 'id-new' ($a.machine -match '^[0-9a-f]{32}$') 'מחשב חדש: מזהה של 32 תווים' $a.machine
Check 'id-file' (($l.Count -ge 3) -and ($l[1] -eq $env:COMPUTERNAME) -and ($l[2] -eq "g:$guid")) 'i.dat ב-ProgramData: שם המחשב, ומזהה Windows האמיתי (g:…)' ($l -join ' / ')
$b = Run @()
Check 'id-again' ($b.machine -eq $a.machine) 'הפעלה שנייה — אותו מזהה'
$c = Run @('-Ddsviz.osid.off=1')
Check 'id-noread' ($c.machine -eq $a.machine) 'אי אפשר לקרוא את מזהה Windows (כמו reg.exe חסום ו-PowerShell חסום) — אותו מזהה, מהקובץ השמור'

# קובץ בפורמט הישן (שתי שורות), כמו אחרי גרסה קודמת: המזהה נשאר, והקובץ משודרג
foreach ($f in @((Join-Path $env:ProgramData 'Lamerhav\.cache\i.dat'), (Join-Path $env:LOCALAPPDATA 'Lamerhav\.cache\i.dat'), (Join-Path $env:APPDATA 'Lamerhav\.cache\i.dat'))) {
    if (Test-Path $f) { (Get-Item $f -Force).Attributes = 'Normal'; [IO.File]::WriteAllText($f, ($l[0] + "`n" + $l[1] + "`n"), (New-Object Text.UTF8Encoding $false)) }
}
$d = Run @()
Check 'id-old-format' ($d.machine -eq $a.machine) 'קובץ מזהה בפורמט הישן (שתי שורות) — אותו מזהה' $d.machine
Check 'id-upgraded' ((IdLines).Count -ge 3 -and (IdLines)[2] -eq "g:$guid") 'והקובץ שודרג לשלוש שורות' ((IdLines) -join ' / ')

# reg.exe חסום (מדיניות לתלמידים): PowerShell קורא את אותו MachineGuid
foreach ($f in @((Join-Path $env:ProgramData 'Lamerhav\.cache\i.dat'), (Join-Path $env:LOCALAPPDATA 'Lamerhav\.cache\i.dat'), (Join-Path $env:APPDATA 'Lamerhav\.cache\i.dat'))) {
    if (Test-Path $f) { (Get-Item $f -Force).Attributes = 'Normal'; [IO.File]::WriteAllText($f, ($l[0] + "`n" + $l[1] + "`n"), (New-Object Text.UTF8Encoding $false)) }
}
$e = Run @('-Ddsviz.osid.noreg=1')
Check 'id-powershell' ($e.machine -eq $a.machine) 'בלי reg.exe: PowerShell קורא את אותו MachineGuid — אותו מזהה' $e.machine
Check 'id-powershell-file' ((IdLines).Count -ge 3 -and (IdLines)[2] -eq "g:$guid") 'ונשמר אותו מזהה Windows' ((IdLines) -join ' / ')

# התיקייה המשותפת: כל משתמשי המחשב יכולים להחליף בה קובץ
$acl = (& icacls (Join-Path $env:ProgramData 'Lamerhav\.cache') 2>&1) -join "`n"
Log $acl
Check 'dir-shared' ($acl -match '\(OI\)\(CI\)\(M\)') 'ProgramData\Lamerhav\.cache — לקבוצה "משתמשים" יש הרשאת שינוי (לקבצים ולתיקיות)' $acl
Check 'dir-mark' (Test-Path (Join-Path $env:ProgramData 'Lamerhav\.cache\p.dat')) 'נרשם שההרשאה ניתנה (p.dat) — לא מנסים שוב בכל הפעלה'

# המזהה הקבוע: אותו דבר גם אחרי שכל הקבצים נמחקו (מחשב ששוחזר), והמזהה הרגיל חדש
Check 'stable' ($a.stable -match '^[0-9a-f]{32}$') 'המזהה הקבוע — 32 תווים' $a.stable
Wipe
$g = Run @()
Check 'stable-same' (($g.stable -eq $a.stable) -and ($g.machine -ne $a.machine)) 'אחרי מחיקת כל הקבצים: מזהה רגיל חדש, אותו מזהה קבוע (השרת יזהה שזה אותו מחשב)' "$($g.machine) / $($g.stable)"
Wipe

Log ''
Log "SUMMARY station-id: $($script:fails) נכשלו"
$script:lines | Out-File -FilePath (Join-Path $Out 'station-id.txt') -Encoding UTF8
exit $(if ($script:fails -gt 0) { 1 } else { 0 })
