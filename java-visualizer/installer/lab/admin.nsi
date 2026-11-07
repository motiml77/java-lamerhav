; "ניהול DSViz" — מסוף ההנהלה (תלמידים, מורים, עמדות, תשלומים, הגדרות) כחלון נפרד במחשב של המנהל.
; הכניסה: חשבון Google של המנהל בלבד (ADMIN_EMAILS בשרת). בנייה: tools/build-lab-setup.sh
SetCompressor /SOLID lzma
Target amd64-unicode
!include "MUI2.nsh"
!ifndef OUTDIR
  !define OUTDIR "..\..\build\lab-setup"
!endif

Name "ניהול DSViz"
OutFile "${OUTDIR}\DSViz-Admin-Setup.exe"
InstallDir "$LOCALAPPDATA\DSViz-Admin"
RequestExecutionLevel user
BrandingText "ניהול DSViz  ·  © Moti Levi"
ShowInstDetails show
VIProductVersion "1.0.0.0"
VIAddVersionKey /LANG=1037 "ProductName" "DSViz Admin"
VIAddVersionKey /LANG=1037 "FileDescription" "ניהול DSViz — מסוף ההנהלה"
VIAddVersionKey /LANG=1037 "FileVersion" "1.0.0"
VIAddVersionKey /LANG=1037 "LegalCopyright" "© 2026 Moti Levi (motiml77@gmail.com)"

!define MUI_ICON "..\icon.ico"
!define MUI_UNICON "..\icon.ico"
!define MUI_WELCOMEPAGE_TITLE "ניהול DSViz"
!define MUI_WELCOMEPAGE_TEXT "מוסיף לשולחן העבודה ולתפריט התחל את 'ניהול DSViz' — מסוף ההנהלה: מורים וכמות העמדות, עמדות מחשב, תלמידים, תשלומים והגדרות.$\r$\n$\r$\nהמסוף נפתח בחלון נפרד, ונכנסים אליו עם חשבון Google של המנהל."
!define MUI_FINISHPAGE_TITLE "מוכן"
!define MUI_FINISHPAGE_TEXT "בשולחן העבודה יש עכשיו 'ניהול DSViz'."
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_TEXT "לפתוח את ניהול DSViz עכשיו"
!define MUI_FINISHPAGE_RUN_FUNCTION OpenAdmin
!define MUI_FINISHPAGE_NOREBOOTSUPPORT
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "Hebrew"

Function OpenAdmin
  ExecShell "open" "$DESKTOP\ניהול DSViz.lnk"
FunctionEnd

Section "install"
  SetOutPath "$INSTDIR"
  File "admin-shortcut.ps1"
  File "..\icon.ico"
  nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\admin-shortcut.ps1" -Icon "$INSTDIR\icon.ico"'
  WriteUninstaller "$INSTDIR\uninstall.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Admin" "DisplayName" "ניהול DSViz"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Admin" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Admin" "DisplayIcon" "$INSTDIR\icon.ico"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Admin" "Publisher" "Moti Levi"
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Admin" "NoModify" 1
SectionEnd

Section "Uninstall"
  nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\admin-shortcut.ps1" -Remove'
  RMDir /r "$INSTDIR"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Admin"
SectionEnd
