#!/bin/sh
# קומפילציה של ההרחבה ל-Visual Studio (csharp/vs-extension) מול ה-SDK של Visual Studio 17.0 — בלי Windows: רק הקוד (בלי VSCT, pkgdef
# והרכבת ה-VSIX — אלה נבנים ב-CI של Windows: tools/test/windows-vs/build-vsix.ps1). תופס שימוש בממשק שלא קיים ב-SDK לפני שמחכים ל-CI.
# בנוסף: מספר הגרסה של הבנייה (ברירת המחדל מהשעון, וגם -p:VsixVersion) נכנס ל-DLL ול-Identity Version של המניפסט (DsVizStampManifest).
# דורש ‎.NET SDK 8 ורשת (NuGet) בפעם הראשונה.
set -e
cd "$(dirname "$0")/../.."
export DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
P=csharp/vs-extension/DsViz.VsExtension.csproj
VER=1.0.26001.7
mkdir -p build
rm -rf csharp/vs-extension/bin csharp/vs-extension/obj
dotnet build "$P" -c Release -p:CreateVsixContainer=false -p:GeneratePkgDefFile=false -p:VsixVersion=$VER -v q > build/vs-extension.out 2>&1 \
    || { echo "ההרחבה לא מתקמפלת מול ה-SDK של 17.0:"; grep -E 'error' build/vs-extension.out | sort -u | head -20; exit 1; }
DLL=csharp/vs-extension/bin/Release/net472/DsViz.VsExtension.dll
[ -f "$DLL" ] || { echo "אין $DLL"; exit 1; }
grep -q "AssemblyVersionAttribute(\"$VER\")" csharp/vs-extension/obj/Release/net472/DsViz.VsExtension.AssemblyInfo.cs || { echo "גרסת ה-DLL אינה $VER"; exit 1; }
echo "SDK 17.0: ההרחבה מתקמפלת ($(grep -c 'warning' build/vs-extension.out || true) שורות אזהרה), גרסת ה-DLL $VER"
# ברירת המחדל: 1.0.<שנה ויום בשנה>.<דקה ביום> — כל חלק עד 65534 (גרסת assembly)
DEF=$(dotnet msbuild "$P" -getProperty:VsixVersion 2>/dev/null | tr -d '\r')
echo "$DEF" | grep -qE '^1\.0\.[0-9]{5}\.[0-9]{1,4}$' && [ "$(echo "$DEF" | cut -d. -f3)" -le 65534 ] && [ "$(echo "$DEF" | cut -d. -f4)" -lt 1440 ] \
    || { echo "מספר הגרסה של ברירת המחדל לא תקין: '$DEF'"; exit 1; }
# המניפסט שנארז (‎obj\...\extension.vsixmanifest‎, נכתב ע"י VSSDK ב-Windows): כאן מריצים רק את DsVizStampManifest על עותק של המקור
M=build/vs-extension-manifest.xml
cp csharp/vs-extension/source.extension.vsixmanifest "$M"
dotnet msbuild "$P" -t:DsVizStampManifest -p:IntermediateVsixManifest="$(pwd)/$M" -p:VsixVersion=$VER -v:q > /dev/null
grep -q "<Identity Id=\"DsViz.VisualStudio.Toolbar.6f3c1c0e-4a52-4e7e-9d1b-5b2f0c8e7a11\" Version=\"$VER\"" "$M" \
    && grep -q 'Version="\[17.0,)"' "$M" && grep -q '<ProductArchitecture>amd64</ProductArchitecture>' "$M" && grep -q 'Version="\[17.4,)"' "$M" \
    && grep -q '<ProductArchitecture>arm64</ProductArchitecture>' "$M" \
    || { echo "המניפסט לא קיבל את הגרסה $VER (או שהטווחים השתנו)"; cat "$M"; exit 1; }
echo "המניפסט: Identity Version = $VER (ברירת המחדל בבנייה: $DEF) · [17.0,) amd64 + [17.4,) arm64"
