#!/bin/sh
# module-info.java "ריק" + מחלקה בלי package בתוך Eclipse אמיתי (ModuleFix.java): הבאג של תלמידה — פרויקט מהאשף עם module-info,
# Targil1 בלי package, וה-Run הרגיל נופל ב-"FindException ... unnamed package not allowed in module". שתי הפעלות של Eclipse על אותה
# סביבת עבודה: before (DSViz כבוי — רואים את הנפילה), after (DSViz דולק — בהפעלה module-info נמחק, וה-Run מדפיס).
# התוסף נבנה כאן מהמקור (כמו tools/build-all.sh, בלי לגעת ב-release/), ו-Eclipse רץ עם תיקיית configuration משלו (build/eclipse-it/module)
# שבה רשומים התוסף ותוסף הבדיקה — בלי לגעת ב-dropins או ב-configuration של ההתקנה (בדיקות אחרות יכולות לרוץ עליה במקביל).
#   sh tools/test/eclipse-it/module.sh [תיקיית eclipse]          (צריך xvfb-run, או XD=:99)
set -e
cd "$(dirname "$0")"
ROOT="$(cd ../../.. && pwd)"
B="$ROOT/build/eclipse-it"
ECL="${1:-$B/eclipse}"
[ -d "$ECL/plugins" ] || { echo "  FAIL אין Eclipse ב-$ECL (run.sh מוריד אותו)"; exit 1; }
M="$B/module"
rm -rf "$M" && mkdir -p "$M/plugin/resources/examples" "$M/it/cls" "$M/configuration/org.eclipse.equinox.simpleconfigurator"
# התוסף — מהמקור, כמו ב-tools/build-all.sh (החלק "== Eclipse")
DEPS="$ROOT/build/eclipse-deps"
[ -f "$DEPS/org.eclipse.jdt.core.jar" ] && [ -f "$DEPS/org.eclipse.ui.workbench.jar" ] || python3 "$ROOT/tools/eclipse-deps.py" "$DEPS"
[ -f "$ROOT/build/access/dsviz-access.jar" ] || sh "$ROOT/tools/build-access.sh" > /dev/null
P="$M/plugin"
javac ${JAVAC_FLAGS:---release 8} -encoding UTF-8 -nowarn -cp "$(ls "$DEPS"/*.jar | tr '\n' ':')$ROOT/build/access/dsviz-access.jar" -d "$P" $(find "$ROOT/eclipse-plugin/src" -name '*.java')
(cd "$P" && unzip -qo "$ROOT/build/access/dsviz-access.jar" -x 'META-INF/*')
cp "$ROOT/eclipse-plugin/src/il/lamerhav/dsviz/problems_he.properties" "$P/il/lamerhav/dsviz/"
cp "$ROOT/eclipse-plugin/plugin.xml" "$P/"
cp -r "$ROOT/eclipse-plugin/icons" "$P/"
cp "$ROOT/release/dsviz.jar" "$ROOT/eclipse-plugin/ai.properties" "$P/resources/"
cp "$ROOT"/src/examples/*.java "$P/resources/examples/" 2>/dev/null || true
cp "$ROOT/src/Main.java" "$P/resources/" 2>/dev/null || true
V="1.0.1.v$(date -u +%Y%m%d%H%M)"
sed "s/^Bundle-Version: .*/Bundle-Version: $V/" "$ROOT/eclipse-plugin/META-INF/MANIFEST.MF" > "$M/plugin-MANIFEST.MF"
HOSTJAR="$M/il.lamerhav.dsviz_1.0.1.jar"
jar --create --file "$HOSTJAR" --manifest "$M/plugin-MANIFEST.MF" -C "$P" .
# תוסף הבדיקה
javac ${JAVAC_FLAGS:---release 11} -encoding UTF-8 -nowarn -cp "$(ls "$ECL"/plugins/*.jar | tr '\n' ':')$HOSTJAR" -d "$M/it/cls" src/il/lamerhav/dsviz/it/*.java
cp plugin.xml "$M/it/cls/"
ITJAR="$M/il.lamerhav.dsviz.it_1.0.0.jar"
jar --create --file "$ITJAR" --manifest META-INF/MANIFEST.MF -C "$M/it/cls" .
# configuration משלנו: config.ini של ההתקנה, ורשימת התוספים שלה (בלי תוספי DSViz שבדיקות אחרות התקינו, ובלי ה-reconciler של dropins)
# + התוסף ותוסף הבדיקה מהתיקייה הזו
cp "$ECL/configuration/config.ini" "$M/configuration/"
grep -v '^il\.lamerhav\.' "$ECL/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info" \
    | sed 's/^\(org\.eclipse\.equinox\.p2\.reconciler\.dropins,.*\),true$/\1,false/' > "$M/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
echo "il.lamerhav.dsviz,$V,file:$HOSTJAR,4,false" >> "$M/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
echo "il.lamerhav.dsviz.it,1.0.0,file:$ITJAR,4,false" >> "$M/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
JAVA=$(ls "$ECL"/plugins/org.eclipse.justj.openjdk.hotspot.jre.full.*/jre/bin/java 2>/dev/null | head -1)
[ -n "$JAVA" ] || JAVA=java
LAUNCH=$(ls "$ECL"/plugins/org.eclipse.equinox.launcher_*.jar)
xrun() { if [ -n "$XD" ]; then DISPLAY="$XD" "$@"; else xvfb-run -a -s "-screen 0 1600x1000x24" "$@"; fi; }
for phase in before after; do
    xrun timeout "${MODULE_TIMEOUT:-420}" "$JAVA" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "$LAUNCH" -nosplash -clean -consoleLog \
        -configuration "$M/configuration" -data "$M/ws" -application il.lamerhav.dsviz.it.module $phase "$M/$phase.txt" "$M" > "$M/$phase.log" 2>&1 || true
    [ -f "$M/$phase.txt" ] || { echo "  FAIL Eclipse לא הריץ את הבדיקה ($phase)"; tail -40 "$M/$phase.log"; exit 1; }
done
FAILED=0
check() { grep -q -- "$2" "$M/$1.txt" && echo "  ok  $3" || { echo "  FAIL $3  ($2)"; FAILED=1; }; }
echo "== לפני (DSViz כבוי) — הבאג"
grep -E '^(BEFORE|POWER)' "$M/before.txt" | grep -v CMDLINE | sed 's/^/    /'
check before "^POWER false" "DSViz כבוי בשלב הראשון"
check before "^BEFORE-ERRORS ayala=\[Targil1.java:1 Must declare a named package" "ב-Eclipse הזה Targil1 בלי package במודול היא גם שגיאת קומפילציה (Proceed — והקובץ המקומפל נוצר)"
check before "^BEFORE-MODULAR ayala=true" "הפרויקט מודולרי (module-info.java)"
check before "^BEFORE-LAUNCH ayala .*exit=1 findException=true .*unnamed package not allowed in module" "Run רגיל של Targil1: FindException — unnamed package not allowed in module"
check before "^BEFORE-OFF-UNTOUCHED module-info=true toasts=0" "DSViz כבוי: module-info לא נמחק, בלי הודעה"
echo "== אחרי (Eclipse עולה עם DSViz)"
grep -vE 'CMDLINE|^DONE' "$M/after.txt" | sed 's/^/    /'
check after "^STARTUP-REMOVED ayala module-info=false" "בהפעלה: module-info.java נמחק מ-ayala"
check after "^AYALA module-info=false history=[1-9][0-9]* modular=false lib=plain" "נשמר ב-Local History; הפרויקט לא מודולרי; ספריית הקורס ב-classpath הרגיל (לא module path)"
check after "^AYALA-HISTORY module ayala { }$" "ב-Local History: התוכן המקורי של module-info.java (אפשר לשחזר)"
check after "^AYALA-CONFIG module-name=none module-attrs=\[\]" "תצורת ההרצה Targil1: בלי שם המודול ובלי שום מאפיין של מודולים"
check after "^AYALA-ERRORS \[\]" "ayala נבנה בלי שגיאות"
check after "^AFTER-LAUNCH ayala terminated=true exit=0 findException=false module-path=false out=Targil1 runs: 42$" "Run רגיל של אותה תצורה: מדפיס Targil1 runs: 42 (בלי FindException)"
check after "^EXPORTER module-info=true exports=true modular=true" "module-info עם exports — לא נמחק"
check after "^PKGONLY module-info=true modular=true" "פרויקט מודולרי שכל המחלקות שלו בחבילות — module-info לא נמחק"
check after "^PKGONLY-CONFIG module-name=pkgonly" "ותצורת ההרצה שלו לא השתנתה"
check after "^AFTER-LAUNCH pkgonly terminated=true exit=0 findException=false module-path=true out=pkgonly runs$" "ו-Run שלו עדיין רץ כמודול"
check after "^TOAST-STARTUP warn | rtl=true | ההרצה הרגילה תוקנה / הקובץ module-info.java הוסר מהפרויקט ayala: מחלקה שלא נמצאת בתוך package לא יכולה לרוץ בפרויקט עם module-info. עכשיו גם ההרצה הרגילה עובדת. (אפשר לשחזר אותו מ-Local History.)" \
    "הודעה בפינה, מימין לשמאל: module-info הוסר מ-ayala"
check after "^TOAST-STARTUP .*בפרויקט exporter ההרצה הרגילה (Run) לא תעבוד: .*מעבירים את המחלקה לתוך package" "ובאותה הודעה: הסבר על exporter (מה לעשות)"
check after "^EXPORTER-TOASTS 1 module-info=true" "ההסבר על exporter — פעם אחת בלבד (גם אחרי קובץ נוסף בלי package)"
check after "^BENI-PREPARED module-info=true requires-dsviz=true lib=module" "פרויקט מודולרי עם מחלקות בחבילות: DSViz מכין אותו כרגיל (requires dsviz, module path)"
check after "^NEWFILE-REMOVED beni module-info=false" "נוסף קובץ בלי package: module-info (עם requires dsviz) נמחק מיד"
check after "^BENI module-info=false history=[1-9][0-9]* modular=false lib=plain" "הספרייה חזרה מה-module path ל-classpath הרגיל"
check after "^BENI-HISTORY module beni { *requires dsviz; }$" "ב-Local History: module-info עם requires dsviz"
check after "^BENI-CONFIG module-name=none module-attrs=\[\]" "תצורת ההרצה Targil2: בלי שם המודול"
check after "^BENI-ERRORS \[\]" "Stack מהספרייה מתקמפל (בלי 'is not accessible')"
check after "^AFTER-LAUNCH beni terminated=true exit=0 findException=false module-path=false out=Targil2 stack: 97$" "Run רגיל: Targil2 stack: 97"
check after "^TOAST-NEWFILE done | rtl=true | ההרצה הרגילה תוקנה / הקובץ module-info.java הוסר מהפרויקט beni:" "הודעה בפינה על beni"
check after "^POWER-OFF dana module-info=true toasts=0" "DSViz כבוי (לחצן הכיבוי): לא נוגעים, בלי הודעה"
check after "^POWER-ON dana module-info=false" "מפעילים שוב: מתוקן"
check after "^TOAST-POWERON done | rtl=true | ההרצה הרגילה תוקנה / הקובץ module-info.java הוסר מהפרויקט dana:" "והודעה בפינה על dana"
grep -q '^DONE' "$M/after.txt" || { echo "  FAIL הבדיקה לא הגיעה לסוף"; FAILED=1; }
# חריגות ביומן שיש בהן קוד של התוסף
awk 'BEGIN{RS="!ENTRY"} /Exception/ && /il\.lamerhav\.dsviz\.(ModuleFix|LibraryEverywhere|ProjectSetup|Toast)/ {print "!ENTRY" $0}' "$M/before.log" "$M/after.log" > "$M/plugin-errors.log"
[ -s "$M/plugin-errors.log" ] && { echo "  FAIL חריגה בקוד של התוסף:"; head -30 "$M/plugin-errors.log"; FAILED=1; } || echo "  ok  בלי חריגות של התוסף ביומן"
echo "  צילומים: $M/module-toast-startup.png $M/module-toast-newfile.png"
exit $FAILED
