#!/bin/sh
# הרצה משורת הפקודה (בלי Eclipse / VS Code):   ./run.sh            → מריץ את Main
#                                              ./run.sh examples.ListExamples
cd "$(dirname "$0")"
CLS="${1:-Main}"
FILE="src/$(echo "$CLS" | tr . /).java"
mkdir -p bin
javac -encoding UTF-8 -d bin $(find src -name "*.java") && java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp bin "$CLS"
