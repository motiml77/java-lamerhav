; עטיפה דקה: מריצה את DSViz-Setup.exe (ההתקנה הציבורית, שכוללת היום גם את מאגר התרגילים והכניסה) בשקט.
; נשארה בשביל הקישור הישן (lab-test) ובשביל מי שהתקין דרכה — אין בה שום דבר שאין בהתקנה הציבורית.
; בנייה: tools/build-lab-setup.sh
SetCompressor /SOLID lzma
!ifndef NOAMD64   ; בבדיקה על Windows (NSIS מ-choco) אין stub ל-64 ביט — לעטיפה זה לא משנה
  Target amd64-unicode
!endif
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
RequestExecutionLevel user
BrandingText "DSViz — גרסת בדיקה ${BUILDNO}  ·  © Moti Levi"
ShowInstDetails show
VIProductVersion "1.0.1.0"
VIAddVersionKey /LANG=1037 "ProductName" "DSViz Lab"
VIAddVersionKey /LANG=1037 "FileDescription" "DSViz — גרסת בדיקה: מאגר התרגילים עם כניסה"
VIAddVersionKey /LANG=1037 "FileVersion" "1.0.1"
VIAddVersionKey /LANG=1037 "LegalCopyright" "© 2026 Moti Levi (motiml77@gmail.com)"

!define MUI_ICON "..\icon.ico"
!define MUI_WELCOMEPAGE_TITLE "DSViz — גרסת בדיקה"
!define MUI_WELCOMEPAGE_TEXT "מתקין את DSViz המלא (הרצה ויזואלית, תרגיל חדש, שליחת תרגיל) ואת מאגר התרגילים — ב-Eclipse, ב-IntelliJ IDEA וב-Visual Studio.$\r$\n$\r$\nבפתיחה הראשונה של מאגר התרגילים נפתח מסך כניסה: מורה (חשבון Google), עמדת מחשב בכיתה (קוד המורה) או תלמיד. צריך אינטרנט רק בכניסה הראשונה.$\r$\n$\r$\nלפני שממשיכים — סוגרים את Eclipse ו-IntelliJ."
!define MUI_FINISHPAGE_TITLE "ההתקנה הסתיימה"
!define MUI_FINISHPAGE_TEXT "פותחים מחדש את Eclipse / IntelliJ / Visual Studio.$\r$\n$\r$\nEclipse ו-IntelliJ: הכפתור 'מאגר תרגילים' בסרגל הכלים.$\r$\nVisual Studio (C#): הקיצור 'מאגר תרגילים C#' בשולחן העבודה."
!define MUI_FINISHPAGE_NOREBOOTSUPPORT
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_LANGUAGE "Hebrew"

Section "install"
  ; ההתקנה הציבורית (DSViz-Setup.exe) כבר כוללת את מאגר התרגילים והכניסה, בכל הסביבות — כאן רק מריצים אותה בשקט.
  ; (גרסאות קודמות של הבדיקה התקינו את המאגר בנפרד, ב-DSViz-Lab: ההתקנה הציבורית מחליפה אותן ומנקה את הרישום שלהן)
  SetOutPath "$PLUGINSDIR"
  File "${PARTS}\DSViz-Setup.exe"
  DetailPrint "מתקין את DSViz ואת מאגר התרגילים (זה לוקח דקה-שתיים)..."
  ExecWait '"$PLUGINSDIR\DSViz-Setup.exe" /S' $0
  DetailPrint "DSViz: הסתיים ($0)"
  Delete "$PLUGINSDIR\DSViz-Setup.exe"
SectionEnd
