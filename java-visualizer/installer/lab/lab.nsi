; גרסת בדיקה (רק למנהל — לא בהורדה של התלמידים, לא בקישור הקבוע):
; DSViz המלא (DSViz-Setup.exe בשקט) + "מאגר התרגילים" עם מסך הכניסה — Eclipse, IntelliJ IDEA, Visual Studio (C#).
; בנייה: tools/build-lab-setup.sh
SetCompressor /SOLID lzma
Target amd64-unicode
!include "MUI2.nsh"
!include "LogicLib.nsh"
!ifndef OUTDIR
  !define OUTDIR "..\..\build\lab-setup"
!endif
!ifndef PARTS
  !define PARTS "..\..\build\lab-setup\parts"
!endif
!ifndef BUILDNO
  !define BUILDNO "0"
!endif

Name "DSViz — גרסת בדיקה (מורים ותלמידים)"
OutFile "${OUTDIR}\DSViz-Lab-Setup.exe"
InstallDir "$LOCALAPPDATA\DSViz-Lab"
RequestExecutionLevel user
BrandingText "DSViz — גרסת בדיקה ${BUILDNO}  ·  © Moti Levi"
ShowInstDetails show
ShowUninstDetails show
VIProductVersion "1.0.1.0"
VIAddVersionKey /LANG=1037 "ProductName" "DSViz Lab"
VIAddVersionKey /LANG=1037 "FileDescription" "DSViz — גרסת בדיקה: מאגר התרגילים עם כניסה"
VIAddVersionKey /LANG=1037 "FileVersion" "1.0.1"
VIAddVersionKey /LANG=1037 "LegalCopyright" "© 2026 Moti Levi (motiml77@gmail.com)"

!define MUI_ICON "..\icon.ico"
!define MUI_UNICON "..\icon.ico"
!define MUI_WELCOMEPAGE_TITLE "DSViz — גרסת בדיקה"
!define MUI_WELCOMEPAGE_TEXT "מתקין את DSViz המלא (הרצה ויזואלית, תרגיל חדש, שליחת תרגיל) ואת מאגר התרגילים — ב-Eclipse, ב-IntelliJ IDEA וב-Visual Studio.$\r$\n$\r$\nבפתיחה הראשונה של מאגר התרגילים נפתח מסך כניסה: מורה (חשבון Google), עמדת מחשב בכיתה (קוד המורה) או תלמיד. צריך אינטרנט רק בכניסה הראשונה.$\r$\n$\r$\nלפני שממשיכים — סוגרים את Eclipse ו-IntelliJ."
!define MUI_FINISHPAGE_TITLE "ההתקנה הסתיימה"
!define MUI_FINISHPAGE_TEXT "פותחים מחדש את Eclipse / IntelliJ / Visual Studio.$\r$\n$\r$\nEclipse ו-IntelliJ: הכפתור 'מאגר תרגילים' בסרגל הכלים.$\r$\nVisual Studio (C#): הקיצור 'מאגר תרגילים C#' בשולחן העבודה."
!define MUI_FINISHPAGE_NOREBOOTSUPPORT
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "Hebrew"

Section "install"
  SetOutPath "$PLUGINSDIR"
  File "${PARTS}\DSViz-Setup.exe"
  DetailPrint "מתקין את DSViz (זה לוקח דקה-שתיים)..."
  ExecWait '"$PLUGINSDIR\DSViz-Setup.exe" /S' $0
  DetailPrint "DSViz: הסתיים ($0)"
  Delete "$PLUGINSDIR\DSViz-Setup.exe"

  RMDir /r "$INSTDIR"
  SetOutPath "$INSTDIR"
  File "${PARTS}\il.lamerhav.dsviz.bank_*.jar"
  File "${PARTS}\dsviz-bank-vs.jar"
  File "${PARTS}\dsviz-bank-intellij-*.zip"
  File "install-bank-vs.ps1"
  File "lab-install.ps1"
  File "..\icon.ico"
  DetailPrint "מתקין את מאגר התרגילים..."
  nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\lab-install.ps1"'
  WriteUninstaller "$INSTDIR\uninstall.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Lab" "DisplayName" "DSViz — גרסת בדיקה (מאגר תרגילים)"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Lab" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Lab" "DisplayIcon" "$INSTDIR\icon.ico"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Lab" "Publisher" "Moti Levi"
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Lab" "NoModify" 1
SectionEnd

Section "Uninstall"
  nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\lab-install.ps1" -Remove'
  RMDir /r "$INSTDIR"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Lab"
SectionEnd
