#!/bin/sh
# bench.sh <ex|ins> — מדידת גלילה בחלונות התוסף (צריך build-all קודם)
unset JAVA_TOOL_OPTIONS
T=$(cd "$(dirname "$0")" && pwd); ROOT=$T/..
CP=$(ls $ROOT/build/eclipse-deps/*.jar | tr '\n' ':')
rm -rf $T/.clsb && mkdir -p $T/.clsb
javac --release 11 -encoding UTF-8 -nowarn -cp "$CP" -d $T/.clsb $(find $ROOT/eclipse-plugin/src -name '*.java') $T/harness/ScrollBench.java 2>&1 | grep -v "^Note"
true
DISPLAY=:93 timeout 120 java -cp "$T/.clsb:$CP" ScrollBench "$@" 2>&1 | grep -v "SessionManager\|pixbuf\|mime database\|^$"
