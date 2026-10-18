#!/bin/sh
# בונה את release/dsviz.jar — הספרייה (מחלקות + קוד מקור).
# הכול תואם Java 8, חוץ מ-JdiTracer (הרצה שורה-שורה) שדורש Java 11 ונטען רק כשמבקשים אותו.
set -e
cd "$(dirname "$0")/.."
OUT=build/lib
rm -rf "$OUT" && mkdir -p "$OUT" release
javac --release 8 -Xlint:-options -encoding UTF-8 -d "$OUT" $(find src/unit4 -name '*.java' ! -name JdiTracer.java)
javac --release 11 -encoding UTF-8 -cp "$OUT" -d "$OUT" src/unit4/collectionsLib/JdiTracer.java
# קוד המקור בתוך ה-jar תחת src/ (ולא ליד המחלקות) — כדי ש-javac לא יקמפל אותו מחדש בטעות.
# התוסף של Eclipse מגדיר את src כשורש קוד המקור (Ctrl+לחיצה על Stack פותח את הקוד)
mkdir -p "$OUT/src/unit4/collectionsLib"
cp src/unit4/collectionsLib/*.java "$OUT/src/unit4/collectionsLib/"
printf 'Premain-Class: unit4.collectionsLib.LineAgent\n' > build/lib-manifest.txt
jar --create --file release/dsviz.jar --manifest build/lib-manifest.txt -C "$OUT" .
echo "release/dsviz.jar"
