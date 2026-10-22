#!/bin/sh
# dlg.sh <ex|ins|examples> <preset> <script> <out-dir> [doc] [marker]  — צילומי חלונות התוסף לפי תסריט (צריך build-all קודם)
unset JAVA_TOOL_OPTIONS
T=$(cd "$(dirname "$0")" && pwd); ROOT=$T/..
CP=$(ls $ROOT/build/eclipse-deps/*.jar | tr '\n' ':')
mkdir -p $T/.cls
if [ ! -f $T/.cls/DlgScript.class ] || [ $T/harness/DlgScript.java -nt $T/.cls/DlgScript.class ] || [ "$REBUILD" = 1 ]; then
  rm -rf $T/.cls && mkdir -p $T/.cls
  javac --release 11 -encoding UTF-8 -nowarn -cp "$CP" -d $T/.cls $(find $ROOT/eclipse-plugin/src -name '*.java') $T/harness/DlgScript.java 2>&1 | grep -v "^Note" 
fi
pgrep -f "Xvfb :93" >/dev/null || (Xvfb :93 -screen 0 1600x900x24 >/dev/null 2>&1 &); sleep 1
DISPLAY=:93 timeout 120 java -cp "$T/.cls:$CP" DlgScript "$@" 2>&1 | grep -v "SessionManager\|pixbuf\|mime database\|^$"
true
