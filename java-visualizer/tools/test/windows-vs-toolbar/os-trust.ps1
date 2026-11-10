# סינון אינטרנט שבודק תעבורה מוצפנת (נטפרי, רימון...) — ב-Windows אמיתי: תעודת שורש "של הסינון" במאגר של Windows,
# שרת HTTPS מקומי עם תעודה שהיא חתמה, וה-Java של ההתקנה (jlink עם הרשימה של tools/build-jre.sh) מתחבר עם OsTrust ונכשל בלעדיו.
#   os-trust.ps1 -Root <java-visualizer> -Lib <dsviz.jar> -Out <תיקייה>
param([Parameter(Mandatory = $true)][string]$Root, [Parameter(Mandatory = $true)][string]$Lib, [Parameter(Mandatory = $true)][string]$Out)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
New-Item -ItemType Directory -Force $Out | Out-Null
$script:fails = 0; $script:lines = New-Object System.Collections.Generic.List[string]
function Log([string]$s) { Write-Host $s; $script:lines.Add($s) }
function Check([string]$id, [bool]$ok, [string]$s, [string]$why = '') { if ($ok) { Log "  ok   $id  $s" } else { $script:fails++; Log "  FAIL $id  $s — $why" } }
$jdk = $env:JAVA_HOME
$w = Join-Path $env:RUNNER_TEMP 'ostrust'
Remove-Item $w -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $w | Out-Null

# 1. ה-Java של ההתקנה: אותם מודולים כמו tools/build-jre.sh (jlink של ה-JDK של Windows), ובלי jdk.crypto.mscapi (כמו קודם)
$mods = ((Get-Content (Join-Path $Root 'tools\build-jre.sh') -Raw) | Select-String -Pattern '--add-modules (\S+)').Matches[0].Groups[1].Value
Log "מודולים: $mods"
$rt = Join-Path $w 'jre'; $rtOld = Join-Path $w 'jre-old'
& "$jdk\bin\jlink.exe" --add-modules $mods --strip-debug --no-header-files --no-man-pages --output $rt 2>&1 | ForEach-Object { Log "jlink: $_" }
& "$jdk\bin\jlink.exe" --add-modules ($mods -replace ',jdk\.crypto\.mscapi', '') --strip-debug --no-header-files --no-man-pages --output $rtOld 2>&1 | ForEach-Object { Log "jlink: $_" }
Check 'jre-built' (Test-Path "$rt\bin\java.exe") 'נבנה ה-Java של ההתקנה (jlink, הרשימה של build-jre.sh)'
$hasMs = (& "$rt\bin\java.exe" --list-modules 2>&1 | Select-String 'jdk.crypto.mscapi').Count -gt 0
Check 'jre-mscapi' $hasMs 'ב-Java של ההתקנה יש jdk.crypto.mscapi (Windows-ROOT)'

# 2. תעודת שורש "של הסינון" במאגר של Windows, ותעודה לשרת (127.0.0.1) שהיא חתמה
$root = New-SelfSignedCertificate -Type Custom -Subject 'CN=DSViz Test Filter Root' -KeyUsage CertSign, CRLSign, DigitalSignature `
    -TextExtension @('2.5.29.19={critical}{text}ca=1&pathlength=1') -CertStoreLocation Cert:\CurrentUser\My -KeyExportPolicy Exportable -NotAfter (Get-Date).AddDays(2)
Export-Certificate -Cert $root -FilePath (Join-Path $w 'root.cer') | Out-Null
Import-Certificate -FilePath (Join-Path $w 'root.cer') -CertStoreLocation Cert:\LocalMachine\Root | Out-Null
$leaf = New-SelfSignedCertificate -Type Custom -Subject 'CN=127.0.0.1' -Signer $root -KeyUsage DigitalSignature, KeyEncipherment `
    -TextExtension @('2.5.29.17={text}IPAddress=127.0.0.1&DNS=localhost', '2.5.29.37={text}1.3.6.1.5.5.7.3.1') -CertStoreLocation Cert:\CurrentUser\My -KeyExportPolicy Exportable -NotAfter (Get-Date).AddDays(2)
$pw = ConvertTo-SecureString 'changeit' -AsPlainText -Force
Export-PfxCertificate -Cert $leaf -FilePath (Join-Path $w 'leaf.pfx') -Password $pw -ChainOption BuildChain | Out-Null
Check 'root-installed' ((Get-ChildItem Cert:\LocalMachine\Root | Where-Object { $_.Thumbprint -eq $root.Thumbprint }).Count -eq 1) 'תעודת השורש במאגר של Windows (LocalMachine\Root)'

# 3. הלקוח והשרת
$cls = Join-Path $w 'cls'
New-Item -ItemType Directory -Force $cls | Out-Null
& "$jdk\bin\javac.exe" -encoding UTF-8 -nowarn -cp $Lib -d $cls (Join-Path $Root 'tools\test\windows-vs-toolbar\TlsCheck.java') 2>&1 | ForEach-Object { Log "javac: $_" }
$portFile = Join-Path $w 'port.txt'
$srv = Start-Process "$jdk\bin\java.exe" -ArgumentList "-cp `"$cls;$Lib`" il.lamerhav.dsviz.TlsCheck serve `"$(Join-Path $w 'leaf.pfx')`" changeit `"$portFile`"" -PassThru -WindowStyle Hidden
for ($i = 0; $i -lt 40 -and -not (Test-Path $portFile); $i++) { Start-Sleep -Milliseconds 500 }
$url = "https://127.0.0.1:$((Get-Content $portFile -Raw).Trim())"
Log "שרת HTTPS: $url"
function Probe([string]$java, [string[]]$opts) {
    $o = & $java @opts '-Dstdout.encoding=UTF-8' -cp "$cls;$Lib" il.lamerhav.dsviz.TlsCheck check $url 2>&1 | Where-Object { $_ -notmatch 'JAVA_TOOL_OPTIONS' }
    $t = ($o -join "`n"); Log ($t -replace "`n", ' | '); return $t
}
$with = Probe "$rt\bin\java.exe" @()
Check 'with-fix' ($with -match 'STATUS ok') 'עם OsTrust: ה-Java של ההתקנה מתחבר לשרת שהתעודה שלו מהשורש שבמאגר של Windows' $with
Check 'with-fix-store' ($with -match 'STORES .*Windows-ROOT') 'נטען המאגר Windows-ROOT' $with
$without = Probe "$rt\bin\java.exe" @('-Ddsviz.osTrust=off')
Check 'without-fix' (($without -match 'STATUS offline') -and ($without -notmatch 'STATUS ok')) 'בלי OsTrust (כמו הקוד הקודם): החיבור נכשל' $without
Check 'without-fix-message' ($without -match 'נטפרי') 'ההודעה מסבירה על סינון שבודק תעבורה מוצפנת' $without
$old = Probe "$rtOld\bin\java.exe" @()
Check 'no-mscapi' ($old -match 'STATUS offline') 'Java בלי jdk.crypto.mscapi (ההתקנה הקודמת) — אין Windows-ROOT, נכשל' $old
$jdkProbe = Probe "$jdk\bin\java.exe" @()
Check 'full-jdk' ($jdkProbe -match 'STATUS ok') 'גם ב-JDK מלא (כמו ה-Java של Eclipse / IntelliJ) — מתחבר' $jdkProbe
$url = $url -replace '127\.0\.0\.1', ([Net.Dns]::GetHostName())
$wrongName = Probe "$rt\bin\java.exe" @()
Check 'hostname-checked' ($wrongName -notmatch 'STATUS ok') 'שם שרת שלא בתעודה — נכשל (בדיקת השם נשמרת, לא "סומכים על הכול")' $wrongName

Stop-Process -Id $srv.Id -Force -ErrorAction SilentlyContinue
Get-ChildItem Cert:\LocalMachine\Root | Where-Object { $_.Thumbprint -eq $root.Thumbprint } | Remove-Item -ErrorAction SilentlyContinue
Log "SUMMARY $(@($script:lines | Where-Object { $_ -match '^  ok ' }).Count) ok / $script:fails FAIL"
[IO.File]::WriteAllLines((Join-Path $Out 'os-trust-summary.txt'), [string[]]$script:lines, (New-Object Text.UTF8Encoding($false)))
exit ([int]($script:fails -gt 0))
