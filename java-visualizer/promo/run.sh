#!/bin/sh
# run.sh <prefix> <steps...>   env MAIN, SCREEN, NX, NY
unset JAVA_TOOL_OPTIONS
S=$(cd "$(dirname "$0")" && pwd)
JAR=$S/../release/dsviz.jar
cd $S && javac -d h h/Shot.java && javac -g -encoding UTF-8 -cp $JAR -d bin src/*.java || exit 1
P=$1; shift
pgrep -f "Xvfb :92" >/dev/null || (Xvfb :92 -screen 0 ${SCREEN:-1600x900x24} >/dev/null 2>&1 &); sleep 1
cd src && DISPLAY=:92 timeout 300 java -Dnx=${NX:-1150} -Dny=${NY:-59} -cp ../h Shot ../cap/$P 9000 "$@" -- java -Dfile.encoding=UTF-8 -Ddsviz.visual=true -cp ../bin:$JAR unit4.collectionsLib.LineByLine ${MAIN}
