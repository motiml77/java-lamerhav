#!/bin/bash
# בדיקה מקצה לקצה של ההתקנה ל-Mac, על Mac אמיתי (GitHub Actions: macos-26 = Apple Silicon, macos-15-intel = Intel).
# מתקינים בדיוק כמו תלמיד (curl ... | bash מה-GitHub Release שהרגע פורסם), ובודקים: Java ו-javac שבהתקנה, הרצה שורה-שורה
# (טקסט), C# (dsviz-cs + TraceViewer --check), MSBuild (DSViz.targets), החלון האמיתי (צילומי מסך: Java, C#, קלט מהמקלדת,
# לולאה שלא נגמרת), VS Code (code --list-extensions), Eclipse (p2 + הפעלה אמיתית — AutoSetup יוצר את הפרויקט), IntelliJ IDEA (תיקיות הגדרות),
# הרצה חוזרת (עדכון — קבצי התלמיד נשמרים), עדכון אוטומטי (update.sh, מ-VS Code ומ-Eclipse), Gatekeeper (קבצים "מהדפדפן"), הסרה.
# שימוש (ב-workflow): CHANNEL=mac-test SHA=<commit> BUILD=<מספר בנייה> bash java-visualizer/installer/mac/ci-test.sh
# קודם: brew install --cask visual-studio-code eclipse-java
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
T="$ROOT/tools/test"
OUT=${OUT:-$PWD/mac-ci}
rm -rf "$OUT"; mkdir -p "$OUT/shots" "$OUT/logs"
APP="$HOME/Library/Application Support/DSViz"
CHANNEL=${CHANNEL:-mac-test}
SHA=${SHA:-$(git -C "$ROOT" rev-parse HEAD)}
URL="https://raw.githubusercontent.com/motiml77/java-lamerhav/$SHA/java-visualizer/installer/mac/install.sh"
ECL_APP="/Applications/Eclipse Java.app"
CODE="/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code"
ELECTRON="/Applications/Visual Studio Code.app/Contents/MacOS/Electron"
[ -x "$ELECTRON" ] || ELECTRON=$(ls "/Applications/Visual Studio Code.app/Contents/MacOS/"* 2>/dev/null | head -1)
export DSVIZ_NONINTERACTIVE=1 HOMEBREW_NO_AUTO_UPDATE=1
R="$OUT/results.txt"
PASS=0; FAIL=0
ok()   { echo "  ok    $*" | tee -a "$R"; PASS=$((PASS + 1)); }
bad()  { echo "  FAIL  $*" | tee -a "$R"; FAIL=$((FAIL + 1)); }
info() { echo "  info  $*" | tee -a "$R"; }
sec()  { echo | tee -a "$R"; echo "== $*" | tee -a "$R"; }
check() { local d=$1; shift; if "$@" >>"$OUT/logs/checks.log" 2>&1; then ok "$d"; else bad "$d"; fi; }
tmo()  { local s=$1; shift; perl -e 'alarm shift; exec @ARGV' "$s" "$@"; }   # ב-macOS אין timeout
shot() { sleep 1; if screencapture -x "$OUT/shots/$1.png" 2>>"$OUT/logs/checks.log"; then ok "צילום מסך $1.png"; else bad "צילום מסך $1"; fi; }
# חלונות של תהליך (שם התהליך ב-System Events — DSViz / java) — לתיעוד שהחלון באמת נפתח
windows() { osascript -e 'tell application "System Events" to get name of every process whose background only is false' \
                       -e 'tell application "System Events" to get name of every window of every process whose background only is false' 2>&1; }
front() { osascript -e "tell application \"System Events\" to set frontmost of (first process whose unix id is $1) to true" >/dev/null 2>&1; }
killviz() { pkill -f 'unit4.collectionsLib' 2>/dev/null; pkill -f 'dsviz-cs' 2>/dev/null; sleep 2; pkill -9 -f 'unit4.collectionsLib' 2>/dev/null; }
J="$APP/jdk/bin"; JAR="$APP/dsviz.jar"; CS="$APP/cs/dsviz-cs"
W=$(mktemp -d); export W

sec "0. המחשב"
{ sw_vers; uname -a; echo "hw.optional.arm64=$(sysctl -n hw.optional.arm64 2>/dev/null)"; spctl --status; csrutil status
  system_profiler SPDisplaysDataType 2>/dev/null | grep -i resolution; echo "dotnet: $(dotnet --version 2>&1)"; dotnet --list-sdks
  for a in "$ECL_APP" "/Applications/Visual Studio Code.app"; do echo "== $a"; xattr -l "$a" 2>&1 | head -5; codesign -dv "$a" 2>&1 | grep -E 'TeamIdentifier|Identifier='; done
  open -h 2>&1 | grep -E 'stdout|stderr'; } > "$OUT/logs/env.txt" 2>&1
info "macOS $(sw_vers -productVersion) $(uname -m), $(spctl --status 2>&1)"
# תלמיד כבר פתח את Eclipse ואת VS Code לפחות פעם אחת (אישר "הורד מהאינטרנט") — Homebrew משאיר אותם עם quarantine
xattr -dr com.apple.quarantine "$ECL_APP" "/Applications/Visual Studio Code.app" 2>/dev/null
shot 00-desktop
# VSCodium מדומה ב-~/Applications (סקריפט שרושם את הפקודות): ההתקנה מתקינה את התוסף גם בגרסאות אחרות של VS Code
FAKE="$HOME/Applications/VSCodium.app/Contents/Resources/app/bin"; mkdir -p "$FAKE"
printf '#!/bin/sh\necho "$@" >> "%s"\n' "$OUT/logs/fake-codium.txt" > "$FAKE/codium"; chmod +x "$FAKE/codium"

# IntelliJ IDEA: תיקיות הגדרות כמו של תלמיד שכבר פתח IntelliJ (בלי להוריד את IntelliJ עצמו, ‎~1GB) — 2025.1 (נתמך) ו-2023.2 (ישן)
JB="$HOME/Library/Application Support/JetBrains"
mkdir -p "$JB/IdeaIC2025.1" "$JB/IntelliJIdea2023.2"

sec "1. התקנה כמו תלמיד: curl -fsSL .../install.sh | bash  (ערוץ $CHANNEL)"
curl -fsSL "$URL" | DSVIZ_CHANNEL="$CHANNEL" DSVIZ_LANG=both bash > "$OUT/logs/install-1.log" 2>&1
rc=$?
[ $rc = 0 ] && ok "ההתקנה הסתיימה (קוד 0)" || { bad "ההתקנה נכשלה (קוד $rc)"; tail -40 "$OUT/logs/install-1.log"; }
cp "$HOME/Library/Logs/DSViz/install.log" "$OUT/logs/install-1.detail.log" 2>/dev/null
[ -z "$BUILD" ] || check "מספר הבנייה שהותקנה = $BUILD" test "$(cat "$APP/build.txt" 2>/dev/null)" = "$BUILD"
check "אין quarantine על קבצי ההתקנה (curl לא מסמן)" sh -c "! xattr -lr \"$APP\" | grep -q com.apple.quarantine"
check "~/.dsviz/jdk-path.txt → Java שבהתקנה" test "$(cat ~/.dsviz/jdk-path.txt)" = "$APP/jdk"
check "~/.dsviz/cs-path.txt → dsviz-cs" test "$(cat ~/.dsviz/cs-path.txt)" = "$APP/cs/dsviz-cs"
check "~/.dsviz/project-path.txt → ~/Documents/DataStructures" test "$(cat ~/.dsviz/project-path.txt)" = "$HOME/Documents/DataStructures"
check "~/.dsviz/project-cs-path.txt → ~/Documents/DataStructures-CS" test "$(cat ~/.dsviz/project-cs-path.txt)" = "$HOME/Documents/DataStructures-CS"
check "תיקיית התרגילים Java (Main.java, lib/dsviz.jar, .project)" test -f "$HOME/Documents/DataStructures/src/Main.java" -a -f "$HOME/Documents/DataStructures/lib/dsviz.jar" -a -f "$HOME/Documents/DataStructures/.project"
check "תיקיית התרגילים C# (sln, lib/Unit4.CollectionsLib.dll)" test -f "$HOME/Documents/DataStructures-CS/DataStructures-CS.sln" -a -f "$HOME/Documents/DataStructures-CS/lib/Unit4.CollectionsLib.dll"
check "DSViz.targets ב-ImportAfter של MSBuild (Mac)" test -f "$HOME/Library/Application Support/Microsoft/MSBuild/Current/Microsoft.CSharp.targets/ImportAfter/DSViz.targets"
check "update.sh / uninstall.sh בהתקנה" test -x "$APP/update.sh" -a -x "$APP/uninstall.sh"
check "VSCodium (מדומה): התוסף הותקן גם בו (--install-extension dsviz-*.vsix)" grep -q -- '--install-extension .*dsviz-.*\.vsix --force' "$OUT/logs/fake-codium.txt"

check "IntelliJ IDEA 2025.1: התוסף הותקן אוטומטית (plugins/dsviz-intellij עם lib ו-runtime/dsviz.jar)" sh -c "ls \"$JB/IdeaIC2025.1/plugins/dsviz-intellij/lib/\"*.jar && test -f \"$JB/IdeaIC2025.1/plugins/dsviz-intellij/runtime/dsviz.jar\""
check "IntelliJ IDEA 2023.2 (ישן מדי): דילוג" test ! -e "$JB/IntelliJIdea2023.2/plugins/dsviz-intellij"
# IntelliJ שהותקן אחרי DSViz: העדכון האוטומטי (update.sh) מוסיף לו את התוסף — גם בלי בנייה חדשה
mkdir -p "$JB/IntelliJIdea2026.1"
bash "$APP/update.sh" --force >/dev/null 2>&1
check "IntelliJ שהותקן אחרי DSViz: update.sh הוסיף לו את התוסף" sh -c "ls \"$JB/IntelliJIdea2026.1/plugins/dsviz-intellij/lib/\"*.jar"

sec "2. Java 21 שבהתקנה"
"$J/java" -version > "$OUT/logs/java-version.txt" 2>&1; "$J/javac" -version >> "$OUT/logs/java-version.txt" 2>&1
check "java -version" "$J/java" -version
check "javac -version" "$J/javac" -version
MODS=$("$J/java" --list-modules 2>&1)
check "מודולים: jdk.jdi, jdk.jdwp.agent, jdk.compiler, java.desktop" sh -c 'for m in jdk.jdi jdk.jdwp.agent jdk.compiler java.desktop jdk.localedata; do echo "$0" | grep -q "^$m@" || exit 1; done' "$MODS"
ARCHJ=$("$J/java" -XshowSettings:properties -version 2>&1 | sed -n 's/.*os.arch = //p')
check "ארכיטקטורה של Java ($ARCHJ) תואמת למחשב" test "$ARCHJ" = "$([ "$(sysctl -n hw.optional.arm64 2>/dev/null)" = 1 ] && echo aarch64 || echo x86_64)"
codesign -dv "$J/java" > "$OUT/logs/codesign.txt" 2>&1; codesign -dv "$CS" >> "$OUT/logs/codesign.txt" 2>&1
check "java חתום (Developer ID של Eclipse Foundation, JCDTMS22B4) ותקין" sh -c "codesign --verify --strict \"$J/java\" && codesign -dv \"$J/java\" 2>&1 | grep -q 'TeamIdentifier=JCDTMS22B4'"
check "libjvm.dylib חתום ותקין" codesign --verify --strict "$APP/jdk/lib/server/libjvm.dylib"
check "dsviz-cs חתום (ad-hoc) ותקין" codesign --verify --strict -v "$CS"
check "dsviz-cs --version" "$CS" --version

sec "3. Java — הרצה שורה-שורה (טקסט, כמו בבדיקות)"
mkdir -p "$W/java" && cp "$ROOT/src/Main.java" "$W/java/"
"$J/javac" -g -encoding UTF-8 -cp "$JAR" -d "$W/java" "$W/java/Main.java" 2>>"$OUT/logs/checks.log"
okl=0
for try in 1 2 3; do
    (cd "$W/java" && tmo 120 "$J/java" -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine Main > "$OUT/logs/java-lines.txt" 2>&1)
    grep -q 'שורה [0-9]*:.*total += x;.*total=0, x=9' "$OUT/logs/java-lines.txt" && grep -q 'הסכום: 21' "$OUT/logs/java-lines.txt" \
        && grep -q 'התוכנית הסתיימה' "$OUT/logs/java-lines.txt" && { okl=1; break; }
done
[ $okl = 1 ] && ok "LineByLine Main: $(grep -c 'שורה ' "$OUT/logs/java-lines.txt") שורות, משתנים, 'הסכום: 21'" || bad "LineByLine Main"
mkdir -p "$W/loops/loops" && cp "$T"/problems/loops/CounterStuck.java "$W/loops/loops/"
"$J/javac" -g -encoding UTF-8 -cp "$JAR" -d "$W/loops" "$W/loops/loops/CounterStuck.java" 2>>"$OUT/logs/checks.log"
(cd "$W/loops" && tmo 150 "$J/java" -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp ".:$JAR" unit4.collectionsLib.LineByLine loops.CounterStuck 2>&1 \
    | perl -pe 's/\xE2\x80[\xAA\xAC\x8E\x8F]//g' > "$OUT/logs/java-loop.txt")
check "לולאה שלא נגמרת (Java): נעצרת עם כרטיס" sh -c "grep -qF '[כרטיס loop x x > 0]' '$OUT/logs/java-loop.txt' && grep -q 'הלולאה לא נגמרת — התוכנית נעצרה' '$OUT/logs/java-loop.txt'"
mkdir -p "$W/input" && cp "$T"/input/ScanInput.java "$W/input/"
"$J/javac" -g -encoding UTF-8 -cp "$JAR" -d "$W/input" "$W/input/ScanInput.java" 2>>"$OUT/logs/checks.log"
printf '3\n4 5\n6\nדני כהן\n' > "$W/input/scan.in"
(cd "$W/input" && tmo 120 "$J/java" -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -Ddsviz.input=@scan.in -cp ".:$JAR" unit4.collectionsLib.LineByLine ScanInput </dev/null 2>&1 \
    | perl -pe 's/\xE2\x80\x8E//g' > "$OUT/logs/java-input.txt")
check "קלט מהמקלדת (Java): צעדי קלט ו'שלום דני כהן, הסכום: 15'" sh -c "grep -q '(ScanInput.java:10)  קלט מהמקלדת: 3' '$OUT/logs/java-input.txt' && grep -q 'שלום דני כהן, הסכום: 15' '$OUT/logs/java-input.txt'"

sec "4. C# — dsviz-cs (Roslyn, self-contained) + החלון קורא את קובץ ההרצה"
CSP="$HOME/Documents/DataStructures-CS"
nok=0; nall=0
for p in "$CSP/Exercise" "$CSP"/Examples/*; do
    n=$(basename "$p"); nall=$((nall + 1))
    tmo 180 "$CS" "$p" --trace "$W/$n.trace" > "$OUT/logs/cs-$n.txt" 2>&1 \
        && "$J/java" -Djava.awt.headless=true -cp "$JAR" unit4.collectionsLib.TraceViewer --check "$W/$n.trace" 2>&1 | grep -q '^ok [1-9]' && nok=$((nok + 1)) \
        || info "C# $n: $(tail -2 "$OUT/logs/cs-$n.txt" | tr '\n' ' ')"
done
[ $nok = $nall ] && ok "C#: כל $nall הפרויקטים בתיקיית התרגילים רצו, והחלון קרא את הצעדים" || bad "C#: רק $nok מתוך $nall"
tmo 150 "$CS" "$T/problems/loops/cs/CounterStuck.cs" --trace "$W/loop-cs.trace" > "$OUT/logs/cs-loop.txt" 2>&1
check "לולאה שלא נגמרת (C#): נעצרת" sh -c "grep -q 'לא נגמרת — התוכנית נעצרה' '$OUT/logs/cs-loop.txt' && grep -q '^LOOP	' '$W/loop-cs.trace'"

sec "5. C# — MSBuild: DSViz.targets (בלי שום הפניה בפרויקט) ותיקיית התרגילים נבנית"
mkdir -p "$W/new1" && cd "$W/new1"
printf 'using System;\nusing Unit4.CollectionsLib;\nStack<int> s = new Stack<int>(); s.Push(1); s.Push(2);\nConsole.WriteLine(s.Pop());\n' > Program.cs
printf '<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>\n' > New1.csproj
dotnet msbuild New1.csproj -getProperty:MSBuildUserExtensionsPath -getProperty:DSVizInstallDir -getProperty:DSVizRunner > "$OUT/logs/msbuild-props.txt" 2>&1
info "MSBuild: $(tr '\n' ' ' < "$OUT/logs/msbuild-props.txt" | head -c 400)"
dotnet build -v q --nologo > "$OUT/logs/msbuild-new1.txt" 2>&1
check "פרויקט חדש: נבנה עם Stack בלי הפניה (DSViz.targets הוסיף אותה)" sh -c "grep -q 'Build succeeded' '$OUT/logs/msbuild-new1.txt' && test -f bin/Debug/net8.0/Unit4.CollectionsLib.dll"
check "פרויקט חדש: התוכנית רצה (2)" test "$(dotnet bin/Debug/net8.0/New1.dll 2>&1)" = 2
cp Properties/launchSettings.json "$OUT/logs/launchSettings-new1.json" 2>/dev/null
check "פרופיל 'הרצה ויזואלית' נוסף, עם dsviz-cs של ה-Mac" sh -c "grep -q 'הרצה ויזואלית' Properties/launchSettings.json && grep -q 'Application Support/DSViz/cs/dsviz-cs' Properties/launchSettings.json"
cd "$CSP" && dotnet build DataStructures-CS.sln -v q --nologo > "$OUT/logs/msbuild-exercises.txt" 2>&1
check "תיקיית התרגילים של C# נבנית (dotnet build DataStructures-CS.sln)" grep -q 'Build succeeded' "$OUT/logs/msbuild-exercises.txt"
cd "$OUT"

sec "6. החלון האמיתי (לא headless) — צילומי מסך"
viz_java() {   # viz_java <תיקייה> <מחלקה> <צילום> <שניות> [stdin]
    killviz
    (cd "$1" && "$J/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Ddsviz.visual=true -Xdock:name=DSViz -cp ".:$JAR" \
        unit4.collectionsLib.LineByLine "$2" < "${5:-/dev/null}" > "$OUT/logs/gui-$3.txt" 2>&1 &)
    sleep "$4"
    windows > "$OUT/logs/windows-$3.txt"
    shot "$3"
}
viz_java "$W/java" Main 01-java-viewer 25
grep -q 'DSViz\|java' "$OUT/logs/windows-01-java-viewer.txt" && info "חלונות: $(head -c 300 "$OUT/logs/windows-01-java-viewer.txt")"
# קלט מהמקלדת: החלון נפתח עם שורת ההקלדה; מקלידים בו 3 ← Enter
viz_java "$W/input" ScanInput 02-java-keyboard 25
pid=$(pgrep -f 'dsviz.child=true' | head -1); [ -n "$pid" ] && front "$pid"; sleep 1
osascript -e 'tell application "System Events" to keystroke "3"' -e 'delay 0.5' -e 'tell application "System Events" to key code 36' >>"$OUT/logs/checks.log" 2>&1
sleep 4; shot 02b-java-keyboard-typed
grep -q 'קלט מהמקלדת: 3' "$OUT/logs/gui-02-java-keyboard.txt" && ok "הקלדה בחלון (osascript): התוכנית קיבלה 3" || info "ההקלדה בחלון לא הגיעה לתוכנית (פוקוס?)"
viz_java "$W/loops" loops.CounterStuck 03-java-infinite-loop 45
pid=$(pgrep -f 'dsviz.child=true' | head -1); [ -n "$pid" ] && front "$pid"; sleep 1
osascript -e 'tell application "System Events" to key code 119' >>"$OUT/logs/checks.log" 2>&1   # End — הצעד האחרון (הכרטיס)
sleep 3; shot 03b-java-infinite-loop-card
killviz
(cd "$CSP" && "$CS" "$CSP/Examples/StackExamples" > "$OUT/logs/gui-04-cs.txt" 2>&1 &)
sleep 30; windows > "$OUT/logs/windows-04-cs.txt"; shot 04-cs-viewer
killviz
(cd "$W" && "$CS" "$T/input/cs/InputSum.cs" < /dev/null > "$OUT/logs/gui-05-cs-keyboard.txt" 2>&1 &)
sleep 30; shot 05-cs-keyboard
killviz

sec "7. VS Code"
"$CODE" --list-extensions --show-versions > "$OUT/logs/vscode-extensions.txt" 2>&1
check "code --list-extensions: lamerhav.dsviz@1.0.1" grep -qi '^lamerhav.dsviz@1.0.1' "$OUT/logs/vscode-extensions.txt"
EXT=$(ls -d "$HOME"/.vscode/extensions/lamerhav.dsviz-* 2>/dev/null | head -1)
check "התוסף שהותקן הוא הבנייה החדשה (עם הקוד ל-Mac)" grep -q startMacUpdater "$EXT/runner.js"
# הקוד של התוסף עצמו, ב-node של VS Code על ה-Mac: מוצא את Java ואת dsviz-cs של ההתקנה
ELECTRON_RUN_AS_NODE=1 "$ELECTRON" -e "const r=require('$EXT/runner.js'), c=require('$EXT/cs.js'); console.log(JSON.stringify({java: r.findJava(), cs: c.findRunner(), mac: r.MAC_DIR}))" > "$OUT/logs/vscode-find.txt" 2>&1
check "runner.findJava() / cs.findRunner() ב-VS Code מוצאים את ההתקנה" sh -c "grep -q 'Application Support/DSViz/jdk/bin/javac' '$OUT/logs/vscode-find.txt' && grep -q 'Application Support/DSViz/cs/dsviz-cs' '$OUT/logs/vscode-find.txt'"
"$CODE" --disable-workspace-trust "$HOME/Documents/DataStructures" "$HOME/Documents/DataStructures/src/Main.java" >>"$OUT/logs/checks.log" 2>&1 &
sleep 35; shot 07-vscode
osascript -e 'quit app "Visual Studio Code"' >/dev/null 2>&1; sleep 3; pkill -f "Visual Studio Code.app" 2>/dev/null

sec "8. Eclipse — התוסף הותקן דרך p2 (Eclipse עצמו), ונטען בהפעלה אמיתית"
INFO="$ECL_APP/Contents/Eclipse/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
PV=$(basename "$(ls "$APP"/eclipse/p2/plugins/il.lamerhav.dsviz_*.jar | head -1)" .jar); PV=${PV#il.lamerhav.dsviz_}
info "גרסת התוסף ל-Eclipse: $PV"
grep dsviz "$INFO" > "$OUT/logs/eclipse-bundles.txt" 2>&1; cp "$APP/eclipse.txt" "$OUT/logs/" 2>/dev/null
check "bundles.info: il.lamerhav.dsviz,$PV,plugins/ (הותקן דרך p2)" grep -q "^il.lamerhav.dsviz,$PV,plugins/" "$INFO"
check "eclipse.txt: נרשם (בשביל ההסרה)" grep -q "|p2|" "$APP/eclipse.txt"
tmo 180 "$ECL_APP/Contents/MacOS/eclipse" -nosplash -application org.eclipse.equinox.p2.director -listInstalledRoots > "$OUT/logs/eclipse-roots.txt" 2>&1
check "p2 director -listInstalledRoots: il.lamerhav.dsviz/$PV" grep -q "^il.lamerhav.dsviz/$PV" "$OUT/logs/eclipse-roots.txt"
WS="$W/eclipse-ws"; rm -f "$HOME/.dsviz/update-check.txt"
"$ECL_APP/Contents/MacOS/eclipse" -data "$WS" > "$OUT/logs/eclipse-gui.txt" 2>&1 &
for i in $(seq 1 45); do sleep 4; [ -d "$WS/.metadata/.plugins/org.eclipse.core.resources/.projects/DataStructures" ] && break; done
check "Eclipse נפתח, והתוסף (AutoSetup) יצר את הפרויקט DataStructures מהמסמכים" test -d "$WS/.metadata/.plugins/org.eclipse.core.resources/.projects/DataStructures"
sleep 15; shot 08-eclipse
grep -i -B2 -A8 'il.lamerhav.dsviz' "$WS/.metadata/.log" > "$OUT/logs/eclipse-errors.txt" 2>/dev/null
check "ב-.metadata/.log אין שגיאות של il.lamerhav.dsviz" sh -c "! grep -q '!ENTRY il.lamerhav.dsviz 4' '$WS/.metadata/.log' 2>/dev/null"
cp "$WS/.metadata/.log" "$OUT/logs/eclipse-metadata.log" 2>/dev/null
# סגירה רגילה (Cmd+Q): התוסף מפעיל את update.sh (העדכון האוטומטי ב-Mac) — רואים את זה ביומן העדכונים
before=$(grep -c ' check' "$HOME/Library/Logs/DSViz/update.log" 2>/dev/null); before=${before:-0}
EID=$(defaults read "$ECL_APP/Contents/Info" CFBundleIdentifier 2>/dev/null)
# קודם סוגרים את חלון "ברוכים הבאים" (Enter), ואז Cmd+Q; אם נשאל "לצאת?" — Enter
osascript -e "tell application id \"$EID\" to activate" -e 'delay 2' -e 'tell application "System Events" to key code 36' >>"$OUT/logs/checks.log" 2>&1
sleep 3; osascript -e "tell application id \"$EID\" to quit" >>"$OUT/logs/checks.log" 2>&1
for i in $(seq 1 30); do sleep 2; pgrep -f "$ECL_APP/Contents/MacOS/eclipse" >/dev/null || break
    [ "$i" = 8 ] && { shot 08b-eclipse-quitting; osascript -e 'tell application "System Events" to key code 36' >/dev/null 2>&1; }; done
sleep 12
after=$(grep -c ' check' "$HOME/Library/Logs/DSViz/update.log" 2>/dev/null); after=${after:-0}
check "סגירת Eclipse הפעילה את update.sh (העדכון האוטומטי)" test "$after" -gt "$before"
pkill -f "$ECL_APP/Contents/MacOS/eclipse" 2>/dev/null

sec "9. הרצה חוזרת = עדכון: מה שעדכני לא יורד שוב, וקבצי התלמיד נשמרים"
printf 'public class MyWork { }\n' > "$HOME/Documents/DataStructures/src/MyWork.java"
echo '// שינוי של התלמיד' >> "$HOME/Documents/DataStructures/src/Main.java"
sum_main=$(shasum "$HOME/Documents/DataStructures/src/Main.java")
curl -fsSL "$URL" | DSVIZ_CHANNEL="$CHANNEL" bash > "$OUT/logs/install-2.log" 2>&1
check "הרצה שנייה: הצליחה, 'Already up to date'" sh -c "grep -q 'Already up to date' '$OUT/logs/install-2.log'"
check "הרצה שנייה: Eclipse — 'already installed'" grep -q 'already installed' "$OUT/logs/install-2.log"
curl -fsSL "$URL" | DSVIZ_CHANNEL="$CHANNEL" DSVIZ_FORCE=1 bash > "$OUT/logs/install-3-force.log" 2>&1
check "הרצה עם DSVIZ_FORCE=1: הורדה והחלפה מחדש" sh -c "grep -q 'Downloaded and verified' '$OUT/logs/install-3-force.log' && '$J/java' -version"
check "קבצי התלמיד נשמרו (MyWork.java, השינוי ב-Main.java)" sh -c "test -f '$HOME/Documents/DataStructures/src/MyWork.java' && test \"\$(shasum '$HOME/Documents/DataStructures/src/Main.java')\" = '$sum_main'"
check "לא נשארו תיקיות זמניות (.DSViz-new / .DSViz-old)" sh -c "! ls -d '$HOME/Library/Application Support'/.DSViz-* 2>/dev/null | grep -q ."

sec "10. עדכון אוטומטי (update.sh) — וגם ההפעלה מ-VS Code"
echo 202001010000 > "$APP/build.txt"; rm -f "$HOME/.dsviz/update-check.txt"
bash "$APP/update.sh"
check "update.sh: מצא בנייה חדשה והתקין אותה" test "$(cat "$APP/build.txt")" = "$BUILD"
tail -30 "$HOME/Library/Logs/DSViz/update.log" > "$OUT/logs/update-tail.txt"
check "update.sh: ביומן 'updating to build $BUILD'" grep -q "updating to build $BUILD" "$HOME/Library/Logs/DSViz/update.log"
rm -f "$HOME/.dsviz/update-check.txt"; before=$(grep -c ' check' "$HOME/Library/Logs/DSViz/update.log" 2>/dev/null); before=${before:-0}
ELECTRON_RUN_AS_NODE=1 "$ELECTRON" -e "console.log(require('$EXT/runner.js').startMacUpdater())" > "$OUT/logs/vscode-updater.txt" 2>&1
sleep 15
check "VS Code (runner.startMacUpdater) מפעיל את update.sh ברקע" sh -c "grep -q true '$OUT/logs/vscode-updater.txt' && test \$(grep -c ' check' '$HOME/Library/Logs/DSViz/update.log') -gt $before"
cp "$HOME/Library/Logs/DSViz/update.log" "$OUT/logs/update.log" 2>/dev/null

sec "11. Gatekeeper: קובץ שהורד בדפדפן (com.apple.quarantine) — מה קורה בדרך 'הידנית'"
Q="0081;$(printf %x "$(date +%s)");Safari;$(uuidgen)"
mkdir -p "$W/gk" && cd "$W/gk"
curl -fsSL -o install.sh "$URL"; xattr -w com.apple.quarantine "$Q" install.sh
bash install.sh --help > gk-script.txt 2>&1
check "install.sh שהורד בדפדפן ומורץ עם bash — רץ (הטרמינל קורא אותו כטקסט, בלי בדיקה)" grep -q DSViz gk-script.txt
printf '#!/bin/bash\necho DSViz test\n' > install-mac.command; chmod +x install-mac.command; xattr -w com.apple.quarantine "$Q" install-mac.command
spctl -a -vv -t open --context context:primary-signature install-mac.command > "$OUT/logs/gatekeeper-command.txt" 2>&1
info ".command שהורד בדפדפן: $(tr '\n' ' ' < "$OUT/logs/gatekeeper-command.txt")"
(tmo 20 open install-mac.command >>"$OUT/logs/gatekeeper-command.txt" 2>&1 &)   # open מחכה עד שסוגרים את חלון Gatekeeper — ברקע, עם timeout
sleep 8; shot 11-gatekeeper-command-doubleclick
osascript -e 'tell application "System Events" to keystroke return' >/dev/null 2>&1; pkill -x Terminal 2>/dev/null; pkill -f "open install-mac.command" 2>/dev/null
# החבילה עצמה, "מהדפדפן": tar פורס — האם ה-quarantine עובר לקבצים? והאם dsviz-cs (ad-hoc) / java (Developer ID) רצים?
TGZ=$(curl -fsSL "https://github.com/motiml77/java-lamerhav/releases/download/$CHANNEL/dsviz-mac.json" | sed -n "s/.*\"$([ "$(sysctl -n hw.optional.arm64)" = 1 ] && echo arm64 || echo x64)_file\"[^\"]*\"\([^\"]*\)\".*/\1/p")
curl -fsSL -o p.tgz "https://github.com/motiml77/java-lamerhav/releases/download/$CHANNEL/$TGZ" && xattr -w com.apple.quarantine "$Q" p.tgz
mkdir x && tar -xzf p.tgz -C x
{ echo "== xattr אחרי tar -xzf של קובץ עם quarantine:"; xattr -l x/cs/dsviz-cs; xattr -l x/jdk/bin/java
  echo "== spctl:"; spctl -a -vv -t execute x/cs/dsviz-cs 2>&1; spctl -a -vv -t execute x/jdk/bin/java 2>&1
  echo "== dsviz-cs --version (quarantined):"; tmo 30 x/cs/dsviz-cs --version; echo "exit=$?"
  echo "== java -version (quarantined):"; tmo 30 x/jdk/bin/java -version; echo "exit=$?"; } > "$OUT/logs/gatekeeper-payload.txt" 2>&1 &
GKP=$!
sleep 12; shot 12-gatekeeper-quarantined-binary
wait "$GKP"   # רק הבדיקה הזו (לא תהליכים אחרים שרצים ברקע)
osascript -e 'tell application "System Events" to keystroke return' >/dev/null 2>&1
info "חבילה 'מהדפדפן': $(grep -A1 'exit=\|quarantine' "$OUT/logs/gatekeeper-payload.txt" | tr '\n' ' ' | head -c 600)"
cd "$OUT"

sec "12. הסרה (uninstall.sh)"
bash "$APP/uninstall.sh" > "$OUT/logs/uninstall.log" 2>&1
check "uninstall.sh: התיקייה נמחקה" test ! -e "$APP"
check "uninstall.sh: ~/.dsviz/jdk-path.txt נמחק" test ! -e "$HOME/.dsviz/jdk-path.txt"
check "uninstall.sh: התוסף הוסר מ-VS Code" sh -c "! '$CODE' --list-extensions | grep -qi lamerhav.dsviz"
check "uninstall.sh: התוסף הוסר מ-Eclipse (p2)" sh -c "! grep -q '^il.lamerhav.dsviz,' '$INFO'"
check "uninstall.sh: התוסף הוסר גם מ-VSCodium (מדומה)" grep -q -- '--uninstall-extension lamerhav.dsviz' "$OUT/logs/fake-codium.txt"
rm -rf "$HOME/Applications/VSCodium.app"
check "uninstall.sh: התוסף הוסר מכל IntelliJ IDEA" sh -c "! ls -d \"$JB\"/*/plugins/dsviz-intellij 2>/dev/null | grep -q ."
check "uninstall.sh: DSViz.targets הוסר" test ! -e "$HOME/Library/Application Support/Microsoft/MSBuild/Current/Microsoft.CSharp.targets/ImportAfter/DSViz.targets"
check "uninstall.sh: תיקיות התרגילים נשארו (העבודה של התלמיד)" test -f "$HOME/Documents/DataStructures/src/MyWork.java" -a -d "$HOME/Documents/DataStructures-CS"

sec "13. התקנה מ-install.sh שהורד בדפדפן (bash ~/Downloads/install.sh) — ובלי Eclipse/VS Code"
DSVIZ_CHANNEL="$CHANNEL" DSVIZ_LANG=java DSVIZ_ECLIPSE_METHOD=skip bash "$W/gk/install.sh" > "$OUT/logs/install-quarantined-script.log" 2>&1
check "התקנה מקובץ עם quarantine: הצליחה, Java רצה" sh -c "'$J/java' -version && test -x '$CS'"
bash "$APP/uninstall.sh" > "$OUT/logs/uninstall-2.log" 2>&1
check "הסרה שנייה" test ! -e "$APP"

sec "סיכום"
echo "  $PASS עברו, $FAIL נכשלו" | tee -a "$R"
ls -la "$OUT/shots" >> "$R"
[ "$FAIL" = 0 ]
