@echo off
REM Usage:  run.bat   or   run.bat examples.ListExamples
chcp 65001 >nul
cd /d "%~dp0"
set CLS=%1
if "%CLS%"=="" set CLS=Main
set FILE=src\%CLS:.=\%.java
if not exist bin mkdir bin
javac -encoding UTF-8 -sourcepath src -d bin "%FILE%" src\unit4\collectionsLib\JdiTracer.java src\unit4\collectionsLib\LineAgent.java || (pause & exit /b 1)
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp bin %CLS%
