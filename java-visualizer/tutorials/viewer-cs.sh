#!/bin/sh
# viewer-cs.sh <dir-with-cs> <out-prefix> <steps...> — צילומי חלון ההרצה של C# (dsviz-cs → TraceViewer), כמו viewer.sh של Java
unset JAVA_TOOL_OPTIONS
T=$(cd "$(dirname "$0")" && pwd); ROOT=$T/..
JAR=$ROOT/release/dsviz.jar
RUNNER=${DSVIZ_CS_DLL:-$ROOT/csharp/DsViz.Runner/bin/Release/net8.0/dsviz-cs.dll}
D=$1; P=$2; shift 2
mkdir -p $T/.shot && javac -d $T/.shot $T/harness/Shot.java || exit 1
dotnet "$RUNNER" "$D" --trace "$D/run.trace" > "$D/run.out" 2>&1 || { cat "$D/run.out"; exit 1; }
XD=${XD:-:92}
pgrep -f "Xvfb $XD " >/dev/null || { (Xvfb $XD -screen 0 1600x900x24 >/dev/null 2>&1 &); sleep 3; }
mkdir -p "$(dirname "$P")"
cd $D && DISPLAY=$XD timeout 300 java -Dnx=${NX:-1360} -Dny=${NY:-66} ${MAINBTN:+-Dmx=135 -Dmy=64} -cp $T/.shot Shot "$P" ${WAIT:-9000} "$@" -- java -Dfile.encoding=UTF-8 -cp $JAR unit4.collectionsLib.TraceViewer "$D/run.trace"
true
