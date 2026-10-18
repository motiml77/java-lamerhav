@echo off
REM ============================================================
REM  Data Structures Visualizer - installer for Windows
REM  Installs the extension into VS Code and the plugin into every
REM  Eclipse that is found.  Optional: install.bat "C:\path\to\eclipse"
REM ============================================================
setlocal
cd /d "%~dp0"
set "HERE=%~dp0"
set "VSIX=%HERE%dsviz-1.0.0.vsix"
set "PLUGIN=%HERE%il.lamerhav.dsviz_1.0.0.jar"
set ECLIPSES=0
echo.
echo  Data Structures Visualizer - installing...
echo  ------------------------------------------

where java >nul 2>nul
if errorlevel 1 echo  [!] Java (JDK) was not found in PATH. Install a JDK (e.g. Adoptium Temurin 17 or 21).

REM ---------------- VS Code
set "CODE="
where code >nul 2>nul
if not errorlevel 1 set "CODE=code"
if not defined CODE if exist "%LOCALAPPDATA%\Programs\Microsoft VS Code\bin\code.cmd" set "CODE=%LOCALAPPDATA%\Programs\Microsoft VS Code\bin\code.cmd"
if not defined CODE if exist "%ProgramFiles%\Microsoft VS Code\bin\code.cmd" set "CODE=%ProgramFiles%\Microsoft VS Code\bin\code.cmd"
if not defined CODE goto novscode
echo  [VS Code] installing extension...
call "%CODE%" --install-extension "%VSIX%" --force
echo  [VS Code] installing Extension Pack for Java (needs internet)...
call "%CODE%" --install-extension vscjava.vscode-java-pack
goto eclipse
:novscode
echo  [VS Code] not found - skipped

REM ---------------- Eclipse
:eclipse
if not "%~1"=="" call :install_eclipse "%~1"
for /d %%D in ("%USERPROFILE%\eclipse\*") do if exist "%%~D\eclipse\eclipse.exe" call :install_eclipse "%%~D\eclipse"
for /d %%D in ("%USERPROFILE%\eclipse" "C:\eclipse*" "%ProgramFiles%\eclipse*" "%USERPROFILE%\Desktop\eclipse*" "%USERPROFILE%\Downloads\eclipse*" "D:\eclipse*") do call :check_dir "%%~D"
if %ECLIPSES%==0 echo  [Eclipse] not found automatically. Run:  install.bat "C:\path\to\eclipse"   (the folder with eclipse.exe)

echo.
echo  Done. Restart VS Code / Eclipse.
echo  VS Code: click the "Data Structures" icon in the side bar.
echo  Eclipse: use the new menu / toolbar buttons.
echo.
pause
exit /b 0

:check_dir
if exist "%~1\eclipse.exe" call :install_eclipse "%~1"
if exist "%~1\eclipse\eclipse.exe" call :install_eclipse "%~1\eclipse"
goto :eof

:install_eclipse
if not exist "%~1\eclipse.exe" if not exist "%~1\eclipse" (
  echo  [Eclipse] "%~1" does not look like an Eclipse folder
  goto :eof
)
if not exist "%~1\dropins" mkdir "%~1\dropins" 2>nul
del /q "%~1\dropins\il.lamerhav.dsviz_*.jar" 2>nul
copy /y "%PLUGIN%" "%~1\dropins\" >nul 2>nul
if errorlevel 1 goto copy_failed
echo  [Eclipse] installed into %~1
set /a ECLIPSES+=1
goto :eof
:copy_failed
echo  [Eclipse] could not write to %~1\dropins  -  right-click install.bat and choose "Run as administrator"
goto :eof
