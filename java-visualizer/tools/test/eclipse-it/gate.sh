#!/bin/sh
# מסך הכניסה האמיתי (Swing, AccessGate.Dialog) בתוך Eclipse אמיתי — מכין את Eclipse כמו bank.sh (התוסף + ההרחבה ב-dropins,
# תוסף הבדיקה דרך p2) ומריץ את GateIt. את הצד של הדפדפן/ההנהלה עושה worker/test/eclipse-gate-e2e.mjs (מפעיל את הסקריפט הזה
# עם GATE_WORKER / GATE_PASSKEY / GATE_SHOTS; אל תריצו אותו ישירות).
#   cd worker && npm run test:eclipse-gate
set -e
cd "$(dirname "$0")"
ROOT="$(cd ../../.. && pwd)"
B="$ROOT/build/eclipse-it"
ECL="${ECLIPSE_DIR:-$B/eclipse}"
[ -d "$ECL/plugins" ] || { echo "אין Eclipse ב-$ECL (bank.sh מוריד אותו)"; exit 1; }
: "${GATE_WORKER:?}" "${GATE_PASSKEY:?}" "${GATE_SHOTS:?}"
G="$B/gate"
rm -rf "$G" && mkdir -p "$G"
# התוסף הראשי — נבנה כאן מהמקור (כמו tools/build-all.sh, בלי לגעת ב-release/), כי ה-jar שב-release/ עלול להיות ישן מהקוד של הכניסה
DEPS="$ROOT/build/eclipse-deps"
[ -f "$DEPS/org.eclipse.jdt.core.jar" ] && [ -f "$DEPS/org.eclipse.ui.workbench.jar" ] || python3 "$ROOT/tools/eclipse-deps.py" "$DEPS"
P="$G/plugin"
mkdir -p "$P/resources/examples"
javac ${JAVAC_FLAGS:---release 8} -encoding UTF-8 -nowarn -cp "$(ls "$DEPS"/*.jar | tr '\n' ':')" -d "$P" $(find "$ROOT/eclipse-plugin/src" -name '*.java')
cp "$ROOT/eclipse-plugin/src/il/lamerhav/dsviz/problems_he.properties" "$P/il/lamerhav/dsviz/"
cp "$ROOT/eclipse-plugin/plugin.xml" "$P/"
cp -r "$ROOT/eclipse-plugin/icons" "$P/"
cp "$ROOT/release/dsviz.jar" "$ROOT/eclipse-plugin/ai.properties" "$P/resources/"
cp "$ROOT"/src/examples/*.java "$ROOT/src/Main.java" "$P/resources/examples/" 2>/dev/null || true
sed "s/^Bundle-Version: .*/Bundle-Version: 1.0.1.v$(date -u +%Y%m%d%H%M)/" "$ROOT/eclipse-plugin/META-INF/MANIFEST.MF" > "$G/plugin-MANIFEST.MF"
HOSTJAR="$G/il.lamerhav.dsviz_1.0.1.jar"
jar --create --file "$HOSTJAR" --manifest "$G/plugin-MANIFEST.MF" -C "$P" .
# ההרחבה "מאגר תרגילים" (fragment) — נבנית מול התוסף הזה
HOST_JAR="$HOSTJAR" sh "$ROOT/tools/build-bank.sh" > "$G/bank-build.log" 2>&1 || { cat "$G/bank-build.log"; exit 1; }
FRAG=$(ls "$ROOT"/build/lab/il.lamerhav.dsviz.bank_*.jar)
mkdir -p "$G/it/cls"
javac ${JAVAC_FLAGS:---release 11} -encoding UTF-8 -nowarn -cp "$(ls "$ECL"/plugins/*.jar | tr '\n' ':')$HOSTJAR" -d "$G/it/cls" src/il/lamerhav/dsviz/it/*.java
cp plugin.xml "$G/it/cls/"
ITV="1.0.0.v$(date +%s)"
sed "s/^Bundle-Version: .*/Bundle-Version: $ITV/" META-INF/MANIFEST.MF > "$G/it/MANIFEST.MF"
REPO="$G/it/repo-$ITV"
mkdir -p "$REPO/plugins"
jar --create --file "$REPO/plugins/il.lamerhav.dsviz.it_$ITV.jar" --manifest "$G/it/MANIFEST.MF" -C "$G/it/cls" .
JAVA=$(ls "$ECL"/plugins/org.eclipse.justj.openjdk.hotspot.jre.full.*/jre/bin/java 2>/dev/null | head -1)
[ -n "$JAVA" ] || JAVA=java
LAUNCH=$(ls "$ECL"/plugins/org.eclipse.equinox.launcher_*.jar)
rm -f "$ECL"/dropins/il.lamerhav.dsviz*.jar
cp "$HOSTJAR" "$FRAG" "$ECL/dropins/"
"$JAVA" -jar "$LAUNCH" -nosplash -consoleLog -application org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher \
    -metadataRepository "file:$REPO" -artifactRepository "file:$REPO" -source "$REPO" -publishArtifacts > "$G/p2.log" 2>&1
"$JAVA" -jar "$LAUNCH" -nosplash -consoleLog -application org.eclipse.equinox.p2.director -uninstallIU il.lamerhav.dsviz.it >> "$G/p2.log" 2>&1 || true
"$JAVA" -jar "$LAUNCH" -nosplash -consoleLog -application org.eclipse.equinox.p2.director -repository "file:$REPO" -installIU il.lamerhav.dsviz.it >> "$G/p2.log" 2>&1 \
    || { echo "FAIL התקנת תוסף הבדיקה"; tail -20 "$G/p2.log"; exit 1; }
xrun() { if [ -n "$XD" ]; then DISPLAY="$XD" "$@"; else xvfb-run -a -s "-screen 0 ${XSCREEN:-1600x1000x24}" "$@"; fi; }
# מחשב "נקי": אין כניסה שמורה (תיקיות זיהוי חדשות), מזהה מחשב חדש
mkdir -p "$G/machine" "$G/home"
mkdir -p "$GATE_SHOTS"
OUT="$GATE_SHOTS/out.txt"
rm -f "$OUT"
echo "Eclipse מתחיל (xvfb)"
xrun "$JAVA" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 \
    -Ddsviz.worker="$GATE_WORKER" -Ddsviz.passKey="$GATE_PASSKEY" -Ddsviz.machineDir="$G/machine" -Ddsviz.home="$G/home" \
    -Ddsviz.machine="gateit$(date +%s)0000000000000000" \
    -jar "$LAUNCH" -nosplash -clean -consoleLog -data "$G/ws" -application il.lamerhav.dsviz.it.gate "$OUT" "$GATE_SHOTS" > "$GATE_SHOTS/eclipse.log" 2>&1 || true
rm -f "$ECL"/dropins/il.lamerhav.dsviz.bank_*.jar
"$JAVA" -jar "$LAUNCH" -nosplash -consoleLog -application org.eclipse.equinox.p2.director -uninstallIU il.lamerhav.dsviz.it >> "$G/p2.log" 2>&1 || true
echo "Eclipse הסתיים"
