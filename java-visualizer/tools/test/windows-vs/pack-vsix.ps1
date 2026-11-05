# אורז את ההרחבה של DSViz (csharp/vs-extension) ל-VSIX ידנית: msbuild בונה את ה-dll ואת ה-pkgdef, כאן מרכיבים מהם את הקובץ
# (VSIX = zip עם [Content_Types].xml, extension.vsixmanifest, ה-dll וה-pkgdef). נפעל גם כש-msbuild לא יוצר את הקובץ לבד (פרויקט SDK-style).
param([string]$Bin, [string]$SourceManifest, [string]$OutFile)
$ErrorActionPreference = 'Stop'
$stage = Join-Path $env:RUNNER_TEMP 'vsix-stage'
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $stage | Out-Null
Copy-Item (Join-Path $Bin 'DsViz.VsExtension.dll') $stage
$pkgdef = Get-ChildItem $Bin -Filter '*.pkgdef' | Select-Object -First 1
if (-not $pkgdef) { throw "לא נמצא pkgdef ב-$Bin" }
Copy-Item $pkgdef.FullName (Join-Path $stage 'DsViz.VsExtension.pkgdef')
$m = [IO.File]::ReadAllText($SourceManifest)
$m = [regex]::Replace($m, '<Asset Type="Microsoft.VisualStudio.VsPackage"[^>]*/>', '<Asset Type="Microsoft.VisualStudio.VsPackage" Path="DsViz.VsExtension.pkgdef" />')
$m = [regex]::Replace($m, '<Asset Type="Microsoft.VisualStudio.MefComponent"[^>]*/>', '<Asset Type="Microsoft.VisualStudio.MefComponent" Path="DsViz.VsExtension.dll" />')
[IO.File]::WriteAllText((Join-Path $stage 'extension.vsixmanifest'), $m, (New-Object Text.UTF8Encoding($false)))
$ct = '<?xml version="1.0" encoding="utf-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="vsixmanifest" ContentType="text/xml" /><Default Extension="dll" ContentType="application/octet-stream" /><Default Extension="pkgdef" ContentType="text/plain" /></Types>'
[IO.File]::WriteAllText((Join-Path $stage '[Content_Types].xml'), $ct, (New-Object Text.UTF8Encoding($false)))
Add-Type -AssemblyName System.IO.Compression.FileSystem
Remove-Item $OutFile -Force -ErrorAction SilentlyContinue
[IO.Compression.ZipFile]::CreateFromDirectory($stage, $OutFile)
Get-Item $OutFile | Select-Object Name, Length
Get-ChildItem $stage | Select-Object Name, Length
