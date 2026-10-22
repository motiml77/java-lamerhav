#!/bin/sh
# viewer.sh <dir-with-java> <Main> <out-prefix> <steps...>  — צילומי חלון ההרצה (לוחצים "הבא" בין צילום לצילום)
# env NX/NY = מיקום הכפתור "הבא" במסך 1600x900
unset JAVA_TOOL_OPTIONS
T=$(cd "$(dirname "$0")" && pwd); ROOT=$T/..
JAR=$ROOT/release/dsviz.jar
D=$1; M=$2; P=$3; shift 3
mkdir -p $T/.shot $D/bin && javac -d $T/.shot $T/harness/Shot.java && javac -g -encoding UTF-8 -cp $JAR -d $D/bin $(find $D -name "*.java" -not -path "*/bin/*") || exit 1
pgrep -f "Xvfb :92" >/dev/null || { (Xvfb :92 -screen 0 1600x900x24 >/dev/null 2>&1 &); sleep 3; }
mkdir -p "$(dirname "$P")"
cd $D && DISPLAY=:92 timeout 300 java -Dnx=${NX:-1360} -Dny=${NY:-66} ${MAINBTN:+-Dmx=135 -Dmy=64} -cp $T/.shot Shot "$P" ${WAIT:-9000} "$@" -- java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Ddsviz.visual=true -cp bin:$JAR unit4.collectionsLib.LineByLine $M
true
