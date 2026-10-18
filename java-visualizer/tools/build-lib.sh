#!/bin/sh
# בונה את release/dsviz.jar — הספרייה (מחלקות + קוד מקור), תואמת Java 8 ומעלה
set -e
cd "$(dirname "$0")/.."
OUT=build/lib
rm -rf "$OUT" && mkdir -p "$OUT" release
javac --release 8 -Xlint:-options -encoding UTF-8 -d "$OUT" $(find src/unit4 -name '*.java')
cp -r src/unit4/collectionsLib/*.java "$OUT/unit4/collectionsLib/"
jar --create --file release/dsviz.jar -C "$OUT" .
echo "release/dsviz.jar"
