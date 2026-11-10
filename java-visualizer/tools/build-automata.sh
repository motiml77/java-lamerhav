#!/bin/sh
# "מודלים חישוביים" כתוכנה מקומית (automata-launcher/, access/README.md):
#   build/automata/dsviz-automata.jar — המשגר (שרת מקומי + חלון) עם הכניסה המשותפת (dsviz-access.jar) בפנים
#   build/automata/app/               — האפליקציה (automata/app, בלי e2e/) — אם היא בענף; אחרת רק ה-jar (אזהרה)
# בהתקנה: <התקנה>\automata\dsviz-automata.jar + <התקנה>\automata\app ; מריצים ב-javaw של ההתקנה:
#   javaw -cp "automata\dsviz-automata.jar;dsviz.jar" il.lamerhav.dsviz.AutomataLauncher
set -e
cd "$(dirname "$0")/.."
sh tools/build-access.sh > /dev/null
OUT=build/automata
CLS=$OUT/cls
rm -rf "$OUT" && mkdir -p "$CLS"
(cd "$CLS" && unzip -qo ../../access/dsviz-access.jar -x 'META-INF/*')
javac --release 11 -encoding UTF-8 -nowarn -cp "$CLS" -d "$CLS" $(find automata-launcher/src -name '*.java')
cp automata/app/js/access-client.js "$CLS/il/lamerhav/dsviz/"   # גם בתוך ה-jar — לאפליקציה בלי הקובץ
sh tools/check-jre-modules.sh "$CLS"
printf 'Main-Class: il.lamerhav.dsviz.AutomataLauncher\nImplementation-Title: DSViz - computational models (local app)\nImplementation-Vendor: Moti Levi (motiml77@gmail.com)\n' > "$OUT/MANIFEST.MF"
jar --create --file "$OUT/dsviz-automata.jar" --manifest "$OUT/MANIFEST.MF" -C "$CLS" .
rm -rf "$CLS" "$OUT/MANIFEST.MF"
APP="${AUTOMATA_APP:-automata/app}"
if [ -f "$APP/index.html" ]; then
    mkdir -p "$OUT/app"
    (cd "$APP" && tar -cf - --exclude=./e2e --exclude='*.md' .) | (cd "$OUT/app" && tar -xf -)
    [ -f "$OUT/app/js/access-client.js" ] || cp automata/app/js/access-client.js "$OUT/app/js/"
    echo "$OUT/dsviz-automata.jar + $OUT/app"
else
    echo "אזהרה: אין $APP/index.html (האפליקציה בענף automat) — נבנה רק $OUT/dsviz-automata.jar" >&2
    echo "$OUT/dsviz-automata.jar"
fi
