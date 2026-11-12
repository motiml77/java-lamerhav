; ============================================================
;  מבני נתונים — הרצה ויזואלית : תוכנית התקנה ל-Windows
;  מתקינה את התוסף ל-VS Code, את התוסף לכל Eclipse ולכל IntelliJ IDEA שנמצאים במחשב.
;  נבנית ע"י tools/build-all.sh (makensis). לא דורשת הרשאות מנהל ולא שואלת מה להתקין — הכול אוטומטי.
;  התקנה שקטה (למשל לכל מחשבי המעבדה): DSViz-Setup.exe /S   הסרה שקטה: uninstall.exe /S
; ============================================================
SetCompressor /SOLID lzma
Target amd64-unicode   ; Windows 10/11 של 64 ביט (כל המחשבים היום)
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"
!include "TextFunc.nsh"
!include "Sections.nsh"
!insertmacro GetParameters
!insertmacro GetOptions

!ifndef VERSION
  !define VERSION "1.0.1"
!endif
!ifndef BUILDNO
  !define BUILDNO "0"   ; מספר הבנייה (build-all.sh שם תאריך ושעה) — בשביל העדכון האוטומטי
!endif
!ifndef RELEASE
  !define RELEASE "..\release"
!endif
!ifndef BUILD
  !define BUILD "..\build"
!endif
!ifndef BANKDIR
  !define BANKDIR "..\build\bank-parts"   ; "מאגר התרגילים" (tools/build-bank-all.sh): תוסף Eclipse, תוסף IntelliJ, והתוספת ל-Visual Studio
!endif
!define PROJECT_DIR "$DOCUMENTS\DataStructures"
!define PROJECT_CS_DIR "$DOCUMENTS\DataStructures-CS"
; נקודת ההרחבה של MSBuild (ברמת המשתמש) שכל פרויקט C# מסוג SDK מייבא אוטומטית — ראו installer/DSViz.targets
!define MSBUILD_IMPORTAFTER "$LOCALAPPDATA\Microsoft\MSBuild\Current\Microsoft.CSharp.targets\ImportAfter"
!define VSIX "dsviz-${VERSION}.vsix"
!define PLUGIN "il.lamerhav.dsviz_${VERSION}.jar"
!define UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz"

VIProductVersion "${VERSION}.0"
VIAddVersionKey /LANG=1037 "ProductName" "DSViz — Data Structures Visualizer"
VIAddVersionKey /LANG=1037 "FileDescription" "הרצה ויזואלית למבני נתונים — תוכנית התקנה"
VIAddVersionKey /LANG=1037 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=1037 "ProductVersion" "${VERSION}"
VIAddVersionKey /LANG=1037 "CompanyName" "Moti Levi"
VIAddVersionKey /LANG=1037 "LegalCopyright" "© 2026 Moti Levi (motiml77@gmail.com)"

Name "מבני נתונים — הרצה ויזואלית"
OutFile "${RELEASE}\DSViz-Setup.exe"
InstallDir "$LOCALAPPDATA\DSViz"
RequestExecutionLevel user
BrandingText "מבני נתונים — הרצה ויזואלית ${VERSION}  ·  © Moti Levi"
ShowInstDetails show
ShowUninstDetails show

!define MUI_ICON "icon.ico"
!define MUI_UNICON "icon.ico"
!define MUI_ABORTWARNING
!define MUI_COMPONENTSPAGE_TEXT_TOP "באיזו שפה אתם לומדים? אפשר לסמן את Java, את C#, או את שתיהן. כל השאר (VS Code, Java להרצה, תיקיית התרגילים) מותקן אוטומטית."
!define MUI_COMPONENTSPAGE_NODESC
!define MUI_PAGE_HEADER_TEXT "בחירת שפה"
!define MUI_PAGE_HEADER_SUBTEXT "באיזו שפה כותבים את התרגילים?"
!define MUI_WELCOMEPAGE_TITLE "מבני נתונים — הרצה ויזואלית"
!define MUI_WELCOMEPAGE_TEXT "התוכנה מוסיפה ל-VS Code, ל-Eclipse, ל-IntelliJ IDEA ול-Visual Studio (בחירה בעמוד הבא: חבילת Java, חבילת C#, או שתיהן):$\r$\n$\r$\n• תרגיל חדש בלחיצה: מחסנית, תור, רשימה מקושרת או עץ — עם כל המעטפת מוכנה.$\r$\n• הרצה ויזואלית: כל שורה בקוד, עם המשתנים והמבנים — צעד אחר צעד.$\r$\n• מאגר התרגילים (עם כניסה): תרגילים מוכנים לפי נושא וקושי, ב-Eclipse, ב-IntelliJ וב-Visual Studio.$\r$\n$\r$\nההתקנה מוצאת לבד את הסביבות שבמחשב, ומורידה לבד (ברקע) את התוספים של Microsoft ל-VS Code (Java / C#) — צריך אינטרנט רק בשביל זה.$\r$\nאם Eclipse, Visual Studio או IntelliJ פתוחים — ההתקנה תבקש לסגור אותם.$\r$\n$\r$\n© 2026 Moti Levi · motiml77@gmail.com"
!define MUI_FINISHPAGE_TITLE "ההתקנה הסתיימה"
!define MUI_FINISHPAGE_TEXT "יש לפתוח מחדש את VS Code / Eclipse / IntelliJ / Visual Studio.$\r$\n$\r$\nVS Code: בסרגל הצד מופיע הסמל 'מבני נתונים'.$\r$\nEclipse: בתפריט העליון מופיע 'מבני נתונים', וכפתורים בסרגל הכלים.$\r$\nIntelliJ IDEA: התפריט 'מבני נתונים', החלון DSViz בצד ימין, ו'הרצה ויזואלית' ליד main.$\r$\nמאגר תרגילים: כפתור בסרגל הכלים של Eclipse, של IntelliJ ושל Visual Studio.$\r$\nVisual Studio (C#): בכל פרויקט — גם הקיצור 'תרגילי C#' וגם כל פרויקט אחר שיוצרים או שכבר יש — מופיעה לבד האפשרות 'הרצה ויזואלית' ליד כפתור ההפעלה. לא צריך להריץ שום דבר משורת הפקודה.$\r$\n$\r$\nמודלים חישוביים (סימולטור אוטומטים): הקיצור בשולחן העבודה. הכניסה (מורה / תלמיד / עמדת מחשב) משותפת לכל התוכנות."
!define MUI_FINISHPAGE_SHOWREADME "$INSTDIR\README-he.html"
!define MUI_FINISHPAGE_SHOWREADME_TEXT "פתיחת המדריך"
!define MUI_FINISHPAGE_NOREBOOTSUPPORT

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "Hebrew"

Var CODE          ; הנתיב ל-code.cmd של VS Code (ריק אם לא נמצא)
Var ECLIPSES      ; כמה התקנות Eclipse קיבלו את התוסף
Var EXTS          ; תוספים של Microsoft ל-VS Code שמורידים ברקע (מופרדים ברווח)
Var UPDATING      ; 1 — עדכון אוטומטי (/UPDATE): בסוף מופיע חלון קטן "DSViz עודכן"
Var FORKS         ; VS Code Insiders / VSCodium / Cursor שקיבלו את התוסף (שמות, מופרדים ברווח; ריק אם אין)


; ------------------------------------------------------------ איתור VS Code
!macro FIND_CODE UN
Function ${UN}FindCode
  StrCpy $CODE ""
  ${If} ${FileExists} "$LOCALAPPDATA\Programs\Microsoft VS Code\bin\code.cmd"
    StrCpy $CODE "$LOCALAPPDATA\Programs\Microsoft VS Code\bin\code.cmd"
  ${ElseIf} ${FileExists} "$PROGRAMFILES64\Microsoft VS Code\bin\code.cmd"
    StrCpy $CODE "$PROGRAMFILES64\Microsoft VS Code\bin\code.cmd"
  ${ElseIf} ${FileExists} "$PROGRAMFILES32\Microsoft VS Code\bin\code.cmd"
    StrCpy $CODE "$PROGRAMFILES32\Microsoft VS Code\bin\code.cmd"
  ${Else}
    SearchPath $0 "code.cmd"
    ${If} $0 != ""
      StrCpy $CODE $0
    ${EndIf}
  ${EndIf}
FunctionEnd
!macroend
!insertmacro FIND_CODE ""
!insertmacro FIND_CODE "un."

; ------------------------------------------------------------ התקנה ל-Eclipse אחד
; קלט: תיקייה במחסנית. אם יש בה eclipse.exe — מעתיקים את התוסף ל-dropins
Function TryEclipse
  Exch $0
  Push $1
  ${If} ${FileExists} "$0\eclipse.exe"
    Push $0
    Call EclipseUtf8
    CreateDirectory "$0\dropins"
    ; קודם מוחקים כל עותק קודם של התוסף (dropins בכל עומק ובכל שם, plugins, bundles.info) ומנקים את המטמון של Eclipse
    ; בכל מקום שבו הוא יכול להיות — אחרת Eclipse יכול להמשיך לטעון את הגרסה הישנה (clean-eclipse.ps1)
    ${If} ${FileExists} "$INSTDIR\clean-eclipse.ps1"
      nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\clean-eclipse.ps1" "$0"'
      Pop $1
    ${EndIf}
    Delete "$0\dropins\il.lamerhav.dsviz_*.jar"   ; גם בלי PowerShell
    ClearErrors
    CopyFiles /SILENT "$INSTDIR\${PLUGIN}" "$0\dropins"
    ${If} ${Errors}
      DetailPrint "Eclipse: אין הרשאה לכתוב ל-$0\dropins — יש להריץ את ההתקנה כמנהל (לחיצה ימנית ← הפעל כמנהל)"
      MessageBox MB_OK|MB_ICONEXCLAMATION "ה-Eclipse שנמצא ב:$\r$\n$0$\r$\n$\r$\nנמצא בתיקייה מוגנת, ולכן אי אפשר להתקין בו בלי הרשאות מנהל.$\r$\n$\r$\nסגרו את ההתקנה, ולחצו על הקובץ DSViz-Setup.exe לחיצה ימנית ← 'הפעל כמנהל'." /SD IDOK
    ${Else}
      ; כמו eclipse -clean: מוחקים את המטמון של Eclipse, כדי שיטען את התוסף החדש ולא את הישן
      RMDir /r "$0\configuration\org.eclipse.osgi"
      DetailPrint "Eclipse: הותקן ב-$0"
      ; רשימת המקומות (UTF-16, כדי שגם נתיבים בעברית יעבדו) — בשביל ההסרה
      FileOpen $1 "$INSTDIR\eclipse-dirs.txt" a
      FileSeek $1 0 END
      FileWriteUTF16LE $1 "$0$\r$\n"
      FileClose $1
      IntOp $ECLIPSES $ECLIPSES + 1
    ${EndIf}
  ${EndIf}
  Pop $1
  Pop $0
FunctionEnd

; eclipse.ini: מוסיף -Dfile.encoding=UTF-8 (עברית תקינה בקוד ובפלט) אם עוד אין
Function EclipseUtf8
  Exch $0
  Push $2
  Push $3
  Push $4
  StrCpy $3 0
  ClearErrors
  FileOpen $2 "$0\eclipse.ini" r
  ${IfNot} ${Errors}
    ${Do}
      ClearErrors
      FileRead $2 $4
      ${If} ${Errors}
        ${Break}
      ${EndIf}
      StrCpy $4 $4 15
      ${If} $4 == "-Dfile.encoding"
        StrCpy $3 1
      ${EndIf}
    ${Loop}
    FileClose $2
    ${If} $3 == 0
      ClearErrors
      FileOpen $2 "$0\eclipse.ini" a
      ${IfNot} ${Errors}
        FileSeek $2 0 END
        FileWrite $2 "$\r$\n-Dfile.encoding=UTF-8$\r$\n"
        FileClose $2
        DetailPrint "Eclipse: הוגדר קידוד UTF-8 ב-eclipse.ini"
      ${EndIf}
    ${EndIf}
  ${EndIf}
  Pop $4
  Pop $3
  Pop $2
  Pop $0
FunctionEnd

; מעבר על כל התיקיות שמתאימות לתבנית (למשל C:\eclipse*) — בודקים גם את התיקייה וגם את eclipse שבתוכה
!macro SCAN BASE PATTERN
  FindFirst $R0 $R1 "${BASE}\${PATTERN}"
  ${DoWhile} $R1 != ""
    ${If} $R1 != "."
    ${AndIf} $R1 != ".."
      Push "${BASE}\$R1"
      Call TryEclipse
      Push "${BASE}\$R1\eclipse"
      Call TryEclipse
    ${EndIf}
    FindNext $R0 $R1
  ${Loop}
  FindClose $R0
!macroend

; ------------------------------------------------------------ Eclipse חייב להיות סגור בזמן ההתקנה
; (התוסף מועתק ל-dropins, והמטמון של Eclipse נמחק — כש-Eclipse פתוח הוא דורס את זה ביציאה, והתוסף לא נטען כמו שצריך)
; $0 = 0 אם Eclipse רץ
Function IsEclipseRunning
  nsExec::Exec '"$SYSDIR\cmd.exe" /c ""$SYSDIR\tasklist.exe" /NH /FI "IMAGENAME eq eclipse.exe" | "$SYSDIR\find.exe" /I "eclipse.exe""'
FunctionEnd

Function EnsureEclipseClosed
  Push $0
  Push $1
  StrCpy $1 0
  check:
    Call IsEclipseRunning
    Pop $0
    StrCmp $0 "0" running done
  running:
    ${If} ${Silent}
      ; התקנה שקטה (עדכון אוטומטי / מעבדה): מחכים ש-Eclipse ייסגר — עד שעתיים
      IntOp $1 $1 + 1
      IntCmp $1 240 done 0 done
      Sleep 30000
      Goto check
    ${EndIf}
    MessageBox MB_YESNOCANCEL|MB_ICONEXCLAMATION "Eclipse פתוח כרגע.$\r$\n$\r$\nכדי שהתוסף יותקן כמו שצריך, צריך לסגור את Eclipse בזמן ההתקנה.$\r$\n$\r$\nכן — לסגור את Eclipse עכשיו (אם יש קבצים שלא נשמרו, Eclipse ישאל אם לשמור)$\r$\nלא — סגרתי בעצמי, להמשיך$\r$\nביטול — לצאת מההתקנה" IDYES close IDNO check
    Quit
  close:
    DetailPrint "Eclipse: סוגר..."
    nsExec::Exec '"$SYSDIR\taskkill.exe" /IM eclipse.exe'
    Pop $0
    ; מחכים עד 60 שניות שייסגר (אולי הוא שואל אם לשמור)
    StrCpy $1 0
  wait:
    Sleep 2000
    Call IsEclipseRunning
    Pop $0
    StrCmp $0 "0" 0 done
    IntOp $1 $1 + 1
    IntCmp $1 30 check wait check
  done:
  Pop $1
  Pop $0
FunctionEnd

; ------------------------------------------------------------ Visual Studio חייב להיות סגור בזמן ההתקנה של חבילת C#
; (את סרגל הכלים של DSViz — ההרחבה — VSIXInstaller לא מתקין כש-Visual Studio פתוח; וגם dsviz-cs / הספרייה נעולים בזמן הרצה)
; $0 = 0 אם Visual Studio רץ
; javaw.exe לקיצורים: בתיקייה עם עברית (שם משתמש) — דרך צומת באנגלית ב-ProgramData (automata.ps1 -JavaPath). התוצאה נקראת מקובץ
; UTF-16 (‏-Out) ולא מהפלט של nsExec — שם הקידוד שונה, ו-"-" הגיע כתו זבל והקיצור נשבר. כל מה שאינו קובץ קיים → javaw הרגיל
Function DSVizJavaw
  Push $0
  StrCpy $R9 ""
  ${If} ${FileExists} "$INSTDIR\automata.ps1"
    Delete "$PLUGINSDIR\javaw-path.txt"
    InitPluginsDir
    nsExec::Exec '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "$INSTDIR\automata.ps1" -JavaPath "$INSTDIR\jdk" -Out "$PLUGINSDIR\javaw-path.txt"'
    Pop $0
    ClearErrors
    FileOpen $0 "$PLUGINSDIR\javaw-path.txt" r
    ${IfNot} ${Errors}
      FileReadUTF16LE $0 $R9
      FileClose $0
      ${TrimNewLines} "$R9" $R9
    ${EndIf}
  ${EndIf}
  ${If} $R9 == ""
  ${OrIf} $R9 == "-"
    StrCpy $R9 "$INSTDIR\jdk\bin\javaw.exe"
  ${ElseIfNot} ${FileExists} "$R9"
    StrCpy $R9 "$INSTDIR\jdk\bin\javaw.exe"
  ${EndIf}
  Pop $0
FunctionEnd

Function IsVisualStudioRunning
  nsExec::Exec '"$SYSDIR\cmd.exe" /c ""$SYSDIR\tasklist.exe" /NH /FI "IMAGENAME eq devenv.exe" | "$SYSDIR\find.exe" /I "devenv.exe""'
FunctionEnd

Function EnsureVisualStudioClosed
  Push $0
  Push $1
  StrCpy $1 0
  check:
    Call IsVisualStudioRunning
    Pop $0
    StrCmp $0 "0" running done
  running:
    ${If} ${Silent}
      ; התקנה שקטה (עדכון אוטומטי): update.ps1 כבר חיכה ש-Visual Studio ייסגר; אם בכל זאת פתוח — מחכים עד שעתיים
      IntOp $1 $1 + 1
      IntCmp $1 240 done 0 done
      Sleep 30000
      Goto check
    ${EndIf}
    MessageBox MB_YESNOCANCEL|MB_ICONEXCLAMATION "Visual Studio פתוח כרגע.$\r$\n$\r$\nכדי שסרגל הכלים של DSViz וההרצה הוויזואלית יותקנו כמו שצריך, צריך לסגור את Visual Studio בזמן ההתקנה.$\r$\n$\r$\nכן — לסגור את Visual Studio עכשיו (אם יש קבצים שלא נשמרו, Visual Studio ישאל אם לשמור)$\r$\nלא — סגרתי בעצמי, להמשיך$\r$\nביטול — לצאת מההתקנה" IDYES close IDNO check
    Quit
  close:
    DetailPrint "Visual Studio: סוגר..."
    nsExec::Exec '"$SYSDIR\taskkill.exe" /IM devenv.exe'
    Pop $0
    ; מחכים עד 60 שניות שייסגר (אולי הוא שואל אם לשמור)
    StrCpy $1 0
  wait:
    Sleep 2000
    Call IsVisualStudioRunning
    Pop $0
    StrCmp $0 "0" 0 done
    IntOp $1 $1 + 1
    IntCmp $1 30 check wait check
  done:
  Pop $1
  Pop $0
FunctionEnd

; ------------------------------------------------------------ יש IntelliJ IDEA במחשב? (תיקיית הגדרות ב-%APPDATA%\JetBrains) → $HASIDEA = 1 / 0
Var HASIDEA
Function HasIntelliJ
  Push $0
  Push $1
  StrCpy $HASIDEA 0
  FindFirst $0 $1 "$APPDATA\JetBrains\IdeaIC*"
  StrCmp $1 "" +2
  StrCpy $HASIDEA 1
  FindClose $0
  FindFirst $0 $1 "$APPDATA\JetBrains\IntelliJIdea*"
  StrCmp $1 "" +2
  StrCpy $HASIDEA 1
  FindClose $0
  Pop $1
  Pop $0
FunctionEnd

; ------------------------------------------------------------ IntelliJ IDEA חייב להיות סגור כשמעדכנים את התוסף
; (הקבצים של תוסף קיים נעולים כש-IntelliJ פתוח; התקנה ראשונה אפשר גם כשהוא פתוח — נטען בפתיחה הבאה)
Function EnsureIntelliJClosed
  Push $0
  Push $1
  StrCpy $1 0
  check:
    nsExec::Exec '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\intellij.ps1" -Running'
    Pop $0
    StrCmp $0 "0" running done
  running:
    ${If} ${Silent}
      ; עדכון אוטומטי: לא מחכים — intellij.ps1 מדלג על גרסה פתוחה, והתוסף מתעדכן בכניסה הבאה ל-Windows (update.ps1)
      Goto done
    ${EndIf}
    MessageBox MB_YESNOCANCEL|MB_ICONEXCLAMATION "IntelliJ IDEA פתוח כרגע.$\r$\n$\r$\nכדי שהתוסף יותקן כמו שצריך, צריך לסגור את IntelliJ בזמן ההתקנה.$\r$\n$\r$\nכן — לסגור את IntelliJ עכשיו (הוא שומר את הקבצים לבד)$\r$\nלא — סגרתי בעצמי, להמשיך$\r$\nביטול — להמשיך בלי לעדכן את IntelliJ (יעודכן בכניסה הבאה ל-Windows)" IDYES close IDNO check
    Goto done
  close:
    DetailPrint "IntelliJ IDEA: סוגר..."
    nsExec::Exec '"$SYSDIR\taskkill.exe" /IM idea64.exe'
    Pop $0
    StrCpy $1 0
  wait:
    Sleep 2000
    nsExec::Exec '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\intellij.ps1" -Running'
    Pop $0
    StrCmp $0 "0" 0 done
    IntOp $1 $1 + 1
    IntCmp $1 30 check wait check
  done:
  Pop $1
  Pop $0
FunctionEnd

; ------------------------------------------------------------ רכיבים
Section "-core"
  ; ניקוי ההתקנה הקודמת (כשמתקינים על קיימת): קיצורים בשולחן העבודה (נוצר רק "מודלים חישוביים" — בסעיף -automata), קיצורי תפריט התחלה, קבצים ותיקיות מגרסה קודמת —
  ; כך כל התיקונים נכנסים, בלי שיישאר משהו ישן. לא נוגעים בעבודה של התלמיד (תיקיות התרגילים במסמכים), ב-JDK ובסרטונים שכבר ירדו.
  Delete "$DESKTOP\תרגילי C# (מבני נתונים).lnk"
  Delete "$DESKTOP\תרגיל חדש ב-C#.lnk"
  Delete "$DESKTOP\הוסף מבנה נתונים ל-C#.lnk"
  Delete "$DESKTOP\שליחת תרגיל ב-C#.lnk"
  Delete "$DESKTOP\הצעה לפתרון ב-C#.lnk"
  Delete "$DESKTOP\תרגילי מבני נתונים (VS Code).lnk"
  ; הקיצורים של מאגר התרגילים ל-C# (גרסת המעבדה, install-bank-vs.ps1) — במקומם הכפתורים "מאגר שאלות" ו"השאלה" בסרגל של Visual Studio
  Delete "$DESKTOP\מאגר תרגילים C#.lnk"
  Delete "$DESKTOP\השאלה — מאגר תרגילים C#.lnk"
  RMDir /r "$SMPROGRAMS\מבני נתונים"
  Delete "$INSTDIR\*.vsix"
  Delete "$INSTDIR\il.lamerhav.dsviz_*.jar"
  Delete "$INSTDIR\dsviz.jar"
  RMDir /r "$INSTDIR\project"
  RMDir /r "$INSTDIR\cs"
  RMDir /r "$INSTDIR\cs-project"
  RMDir /r "$INSTDIR\vs-template"
  RMDir /r "$INSTDIR\intellij"
  SetOutPath "$INSTDIR"
  ; התוספים בלי הסרטונים (הם נשמרים פעם אחת, בתיקייה help שלמטה) — כדי שההתקנה לא תכפיל אותם
  File "${BUILD}\light\${VSIX}"
  File "${BUILD}\light\${PLUGIN}"
  File "${RELEASE}\dsviz.jar"
  File "README-he.html"
  File "icon.ico"
  File "update.ps1"
  File "intellij.ps1"
  File "notify.ps1"
  File "clean-eclipse.ps1"
  File "automata.ps1"
  ; התוסף ל-IntelliJ IDEA (התיקייה dsviz-intellij כמו שהיא בתוך plugins). הספרייה (runtime\dsviz.jar) — אותו קובץ כמו $INSTDIR\dsviz.jar,
  ; מועתק במחשב ולא נכלל פעמיים בהתקנה
  RMDir /r "$INSTDIR\intellij"   ; בלי jar-ים מבנייה קודמת (intellij.ps1 מעתיק את כל התיקייה כמו שהיא)
  SetOutPath "$INSTDIR\intellij\dsviz-intellij\lib"
  File /nonfatal "${BUILD}\intellij\dsviz-intellij\lib\*.jar"   ; בלי Gradle בבנייה — בלי התוסף (intellij.ps1 מדלג)
  CreateDirectory "$INSTDIR\intellij\dsviz-intellij\runtime"
  CopyFiles /SILENT "$INSTDIR\dsviz.jar" "$INSTDIR\intellij\dsviz-intellij\runtime"
  SetOutPath "$INSTDIR"
  ; עדכון אוטומטי ברקע: מספר הבנייה שמותקן, ובדיקה בכל כניסה ל-Windows (וגם כש-Eclipse נסגר — התוסף מפעיל)
  FileOpen $0 "$INSTDIR\build.txt" w
  FileWrite $0 "${BUILDNO}"
  FileClose $0
  ; עדכון אוטומטי — רק כשמשתמשים ב-DSViz (Eclipse / IntelliJ / VS Code נפתחים, כלי מ-Visual Studio, מודלים חישוביים): בלי משימה
  ; נסתרת בכניסה ל-Windows. מוחקים את זו שהתקנות קודמות רשמו
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "DSVizUpdate"
  CreateDirectory "$PROFILE\.dsviz"
  FileOpen $0 "$PROFILE\.dsviz\install-dir.txt" w
  FileWriteUTF16LE /BOM $0 "$INSTDIR"
  FileClose $0
  SetOutPath "$INSTDIR\help"
  ; דפי ההדרכה (הכפתור ?) בלי הסרטונים עצמם — כדי שההתקנה תישאר מתחת ל-100MB (המגבלה של GitHub).
  ; הנגן מנגן מהאתר כשאין עותק במחשב, ו-update.ps1 -Videos מוריד אותם ברקע (מיד אחרי ההתקנה ובכל כניסה ל-Windows)
  File /r /x *.mp4 "${BUILD}\help\*.*"
  DetailPrint "מוריד את סרטוני ההדרכה (מאחורי הקלעים)..."
  nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "$INSTDIR\update.ps1" -Videos'   ; בלי חלון — וההתקנה מחכה שיסתיים
  Pop $0
  SetOutPath "$INSTDIR\project"
  File /r "${BUILD}\project\*.*"
  SetOutPath "$INSTDIR"
  WriteUninstaller "$INSTDIR\uninstall.exe"

  WriteRegStr HKCU "${UNINST_KEY}" "DisplayName" "מבני נתונים — הרצה ויזואלית"
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINST_KEY}" "Publisher" "Moti Levi"
  WriteRegStr HKCU "${UNINST_KEY}" "Contact" "motiml77@gmail.com"
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayIcon" "$INSTDIR\icon.ico"
  WriteRegStr HKCU "${UNINST_KEY}" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegDWORD HKCU "${UNINST_KEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINST_KEY}" "NoRepair" 1

  CreateDirectory "$SMPROGRAMS\מבני נתונים"
  CreateShortcut "$SMPROGRAMS\מבני נתונים\מדריך.lnk" "$INSTDIR\README-he.html"
  CreateShortcut "$SMPROGRAMS\מבני נתונים\הסרה.lnk" "$INSTDIR\uninstall.exe"

  Call FindCode
SectionEnd

Section "-jdk"
  ; Java מקוצרת (java, javac, חלון, הרצה שורה-שורה). נחוצה בכל חבילה: החלון הוויזואלי כתוב ב-Java,
  ; והתוסף של VS Code מריץ איתה את התוכניות. כלולה בהתקנה — בלי הורדה
  SetOutPath "$INSTDIR\jdk"
  File /r "${BUILD}\jre-windows\*.*"
  SetOutPath "$INSTDIR"
  CreateDirectory "$PROFILE\.dsviz"
  FileOpen $0 "$PROFILE\.dsviz\jdk-path.txt" w
  FileWriteUTF16LE /BOM $0 "$INSTDIR\jdk"
  FileClose $0
  DetailPrint "Java 21: הותקנה ב-$INSTDIR\jdk"
SectionEnd

Section "-vscode"
  ; התוסף של VS Code עובד גם ב-Java וגם ב-C# — מותקן תמיד אם VS Code נמצא
  ${If} $CODE == ""
    DetailPrint "VS Code: לא נמצא — דילוג"
  ${Else}
    DetailPrint "VS Code: מתקין את התוסף..."
    nsExec::ExecToLog '"$SYSDIR\cmd.exe" /c ""$CODE" --install-extension "$INSTDIR\${VSIX}" --force"'
    Pop $0
    ${If} $0 == "0"
      DetailPrint "VS Code: התוסף הותקן"
    ${Else}
      DetailPrint "VS Code: ההתקנה נכשלה (קוד $0)"
    ${EndIf}
  ${EndIf}
SectionEnd

; ------------------------------------------------------------ VS Code Insiders / VSCodium / Cursor
; אותו תוסף (vsix) לכל גרסה אחרת של VS Code שמותקנת. נרשמות ב-vscode-forks.txt — בשביל ההסרה
!macro VSIX_FORK CMD NAME
  ${If} ${FileExists} "${CMD}"
    nsExec::ExecToLog '"$SYSDIR\cmd.exe" /c ""${CMD}" --install-extension "$INSTDIR\${VSIX}" --force"'
    Pop $0
    ${If} $0 == "0"
      DetailPrint "${NAME}: התוסף הותקן"
      StrCpy $FORKS "$FORKS ${NAME}"
      FileOpen $1 "$INSTDIR\vscode-forks.txt" a
      FileSeek $1 0 END
      FileWriteUTF16LE $1 "${CMD}$\r$\n"
      FileClose $1
    ${Else}
      DetailPrint "${NAME}: ההתקנה נכשלה (קוד $0)"
    ${EndIf}
  ${EndIf}
!macroend

Section "-vscode-forks"
  StrCpy $FORKS ""
  Delete "$INSTDIR\vscode-forks.txt"
  !insertmacro VSIX_FORK "$LOCALAPPDATA\Programs\Microsoft VS Code Insiders\bin\code-insiders.cmd" "VS Code Insiders"
  !insertmacro VSIX_FORK "$PROGRAMFILES64\Microsoft VS Code Insiders\bin\code-insiders.cmd" "VS Code Insiders"
  !insertmacro VSIX_FORK "$LOCALAPPDATA\Programs\VSCodium\bin\codium.cmd" "VSCodium"
  !insertmacro VSIX_FORK "$PROGRAMFILES64\VSCodium\bin\codium.cmd" "VSCodium"
  !insertmacro VSIX_FORK "$LOCALAPPDATA\Programs\cursor\resources\app\bin\cursor.cmd" "Cursor"
SectionEnd

; "מודלים חישוביים" (סימולטור אוטומטים) — תוכנה מקומית, לכל מי שמתקין (בלי תלות בחבילה): AutomataLauncher מגיש את
; automata\app על 127.0.0.1 ופותח חלון (Edge ‎--app‎). הכניסה משותפת עם Eclipse / Visual Studio / IntelliJ (access/README.md).
; הקיצור היחיד בשולחן העבודה. השמירה של הדף — ב-‎%LOCALAPPDATA%\DSViz\automata-data‎ (לא נמחקת בעדכון)
Section "-automata"
  ${If} ${FileExists} "$INSTDIR\automata.ps1"
    nsExec::Exec '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "$INSTDIR\automata.ps1" -Stop'   ; הקבצים נעולים כשהיא פתוחה
    Pop $0
  ${EndIf}
  RMDir /r "$INSTDIR\automata"
  SetOutPath "$INSTDIR\automata"
  File "${BUILD}\automata\dsviz-automata.jar"
  File "automata.ico"
  SetOutPath "$INSTDIR\automata\app"
  File /nonfatal /r "${BUILD}\automata\app\*.*"   ; בלי האפליקציה בבנייה — בלי קיצור (למטה)
  SetOutPath "$INSTDIR"
  Delete "$DESKTOP\מודלים חישוביים.lnk"
  ${If} ${FileExists} "$INSTDIR\automata\app\index.html"
    ; ‏javaw.exe מתיקייה עם עברית (שם משתמש) לא עולה ב-Windows שהקידוד שלו לא עברי — גם לא מהשם הקצר (8.3): ה-JVM טוען את
    ; net.dll מהנתיב הארוך. לכן צומת באנגלית ב-ProgramData (automata.ps1 -JavaPath, כמו SafeJava). ‎-cp‎ יחסי (תיקיית העבודה = $INSTDIR)
    Push $1
    Push $R9
    Call DSVizJavaw
    StrCpy $1 $R9
    Pop $R9
    CreateShortcut "$DESKTOP\מודלים חישוביים.lnk" "$1" '-cp "automata\dsviz-automata.jar;dsviz.jar" il.lamerhav.dsviz.AutomataLauncher' "$INSTDIR\automata\automata.ico"
    CreateDirectory "$SMPROGRAMS\מבני נתונים"
    CreateShortcut "$SMPROGRAMS\מבני נתונים\מודלים חישוביים.lnk" "$1" '-cp "automata\dsviz-automata.jar;dsviz.jar" il.lamerhav.dsviz.AutomataLauncher' "$INSTDIR\automata\automata.ico"
    Pop $1
    DetailPrint "מודלים חישוביים: קיצור בשולחן העבודה"
  ${EndIf}
SectionEnd

Section "Java — Eclipse ו-VS Code (תרגילים ב-Java)" SEC_JAVA
  Call EnsureEclipseClosed   ; לפני שמתקינים משהו של Java
  DetailPrint "חבילת Java"
SectionEnd

Section /o "C# — Visual Studio, VS Code ו-Rider (תרגילים ב-C#)" SEC_CS
  DetailPrint "חבילת C#"
  Call EnsureVisualStudioClosed   ; לפני שמחליפים את dsviz-cs ומתקינים את סרגל הכלים
  ; ההרצה הוויזואלית (dsviz-cs.exe — כוללת את סביבת ההרצה של .NET, לא צריך להתקין כלום)
  ; בלי קבצים מבנייה קודמת (DLL-ים שהשם שלהם השתנה וכו') — התיקיות נבנות מחדש בכל התקנה / עדכון
  RMDir /r "$INSTDIR\cs"
  RMDir /r "$INSTDIR\cs-project"
  SetOutPath "$INSTDIR\cs"
  File /r "${BUILD}\dsviz-cs-win\*.*"
  SetOutPath "$INSTDIR\cs-project"
  File /r "${BUILD}\project-cs\*.*"
  SetOutPath "$INSTDIR\vs-template"
  File "${BUILD}\vs-template\DSViz-Exercise.zip"
  SetOutPath "$INSTDIR"
  CreateDirectory "$PROFILE\.dsviz"
  FileOpen $0 "$PROFILE\.dsviz\cs-path.txt" w
  FileWriteUTF16LE /BOM $0 "$INSTDIR\cs\dsviz-cs.exe"
  FileClose $0
  DetailPrint "C#: ההרצה הוויזואלית הותקנה ב-$INSTDIR\cs"

  ; MSBuild (DSViz.targets): בכל פרויקט C# של Visual Studio — חדש או קיים, בכל תיקייה — מתווספת לבד
  ; ההפניה לספרייה ופרופיל ההרצה "הרצה ויזואלית", בלי להריץ שום דבר משורת הפקודה (ראו installer/DSViz.targets)
  SetOutPath "${MSBUILD_IMPORTAFTER}"
  File "DSViz.targets"
  SetOutPath "$INSTDIR"
  DetailPrint "Visual Studio: בכל פרויקט C# מופיעה לבד 'הרצה ויזואלית' ליד כפתור ההפעלה (${MSBUILD_IMPORTAFTER})"
  ; סרגל הכלים של DSViz בראש Visual Studio (הרחבה): מותקן בכל Visual Studio שנמצא (installer/install-vsix.ps1). תקלה כאן לא עוצרת את ההתקנה
  ${If} ${FileExists} "$INSTDIR\cs\DsViz.VsExtension.vsix"
    DetailPrint "Visual Studio: מתקין את סרגל הכלים של DSViz..."
    nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\cs\install-vsix.ps1" "$INSTDIR\cs\DsViz.VsExtension.vsix"'
    Pop $0
  ${EndIf}

  ; תיקיית תרגילים מוכנה (Documents\DataStructures-CS): פתרון של Visual Studio עם תרגיל ודוגמאות
  ${IfNot} ${FileExists} "${PROJECT_CS_DIR}\DataStructures-CS.sln"
    CreateDirectory "${PROJECT_CS_DIR}"
    CopyFiles /SILENT "$INSTDIR\cs-project\*.*" "${PROJECT_CS_DIR}"
    DetailPrint "נוצרה תיקיית התרגילים ל-C#: ${PROJECT_CS_DIR}"
  ${Else}
    ; כבר קיימת — לא נוגעים בעבודה של התלמיד, רק מעדכנים את הספרייה
    CreateDirectory "${PROJECT_CS_DIR}\lib"
    CopyFiles /SILENT "$INSTDIR\cs-project\lib\Unit4.CollectionsLib.dll" "${PROJECT_CS_DIR}\lib"
    CopyFiles /SILENT "$INSTDIR\cs-project\Directory.Build.props" "${PROJECT_CS_DIR}"   ; קובץ שלנו: מאפשר Size(x) / Duplicate(x) בלי Helpers.
    DetailPrint "תיקיית התרגילים ל-C# קיימת — הספרייה עודכנה"
  ${EndIf}
  FileOpen $0 "$PROFILE\.dsviz\project-cs-path.txt" w
  FileWriteUTF16LE /BOM $0 "${PROJECT_CS_DIR}"
  FileClose $0
  ; החלונות "תרגיל חדש" ו"הוסף מבנה נתונים" ל-C# (אותם חלונות כמו ב-Eclipse וב-VS Code) — מקיצור, בשביל Visual Studio / Rider
  CreateDirectory "$SMPROGRAMS\מבני נתונים"
  ; שם משתמש בעברית: java מפענחת את שורת הפקודה בקידוד של Windows — לכן ‎-cp‎ יחסי (תיקיית העבודה של הקיצור = $INSTDIR)
  ; ובלי נתיב התרגילים בשורה (CsTool קורא אותו מ-project-cs-path.txt שנכתב למעלה)
  Push $OUTDIR
  SetOutPath "$INSTDIR"
  ; ‏javaw.exe עצמו לא עולה מנתיב שלא בקידוד של Windows ("could not find java.dll") — השם הקצר (8.3) של הנתיב, אם יש
  Push $1
  ; (השם הקצר 8.3 לא מספיק: ה-JVM טוען את net.dll מהנתיב הארוך — לכן אותו צומת באנגלית כמו של "מודלים חישוביים")
  Push $R9
  Call DSVizJavaw
  StrCpy $1 $R9
  Pop $R9
  CreateShortcut "$SMPROGRAMS\מבני נתונים\תרגיל חדש ב-C#.lnk" "$1" '-cp dsviz.jar il.lamerhav.dsviz.cs.CsTool new' "$INSTDIR\icon.ico"
  Delete "$SMPROGRAMS\מבני נתונים\הוספת משתנה ל-C#.lnk"   ; השם מגרסה קודמת
  CreateShortcut "$SMPROGRAMS\מבני נתונים\הדרכה לשימוש.lnk" "$1" '-cp dsviz.jar il.lamerhav.dsviz.cs.CsTool help' "$INSTDIR\icon.ico"
  CreateShortcut "$SMPROGRAMS\מבני נתונים\הוסף מבנה נתונים ל-C#.lnk" "$1" '-cp dsviz.jar il.lamerhav.dsviz.cs.CsTool insert' "$INSTDIR\icon.ico"
  ; "שליחת תרגיל" (דף התרגיל → פרויקט מוכן בפתרון) ו"הצעה לפתרון" (חלון שנשאר פתוח ליד Visual Studio) — כמו ב-Eclipse וב-VS Code
  CreateShortcut "$SMPROGRAMS\מבני נתונים\שליחת תרגיל ב-C#.lnk" "$1" '-cp dsviz.jar il.lamerhav.dsviz.cs.CsTool submit' "$INSTDIR\icon.ico"
  CreateShortcut "$SMPROGRAMS\מבני נתונים\הצעה לפתרון ב-C#.lnk" "$1" '-cp dsviz.jar il.lamerhav.dsviz.cs.CsTool solution' "$INSTDIR\icon.ico"
  ; מאגר התרגילים ל-C# (גרסת המעבדה, lab\dsviz-bank-vs.jar): בלי קיצורים בשולחן העבודה — ב-Visual Studio הכפתורים בסרגל,
  ; ול-Rider מתפריט התחלה (התיקייה הזו נבנית מחדש בכל עדכון)
  ${If} ${FileExists} "$INSTDIR\lab\dsviz-bank-vs.jar"
    CreateShortcut "$SMPROGRAMS\מבני נתונים\מאגר תרגילים C#.lnk" "$1" '-cp "lab\dsviz-bank-vs.jar;dsviz.jar" il.lamerhav.dsviz.BankVs bank' "$INSTDIR\icon.ico"
    CreateShortcut "$SMPROGRAMS\מבני נתונים\השאלה — מאגר תרגילים C#.lnk" "$1" '-cp "lab\dsviz-bank-vs.jar;dsviz.jar" il.lamerhav.dsviz.BankVs question' "$INSTDIR\icon.ico"
  ${EndIf}
  Pop $1
  Pop $0
  SetOutPath $0

  ; תבנית פרויקט חדש ב-Visual Studio: "תרגיל מבני נתונים (DSViz)"
  Delete "$INSTDIR\vs-templates.txt"
  Call InstallVsTemplates

  ; תוסף ה-C# של Microsoft ל-VS Code (צבעים והשלמה) — יורד לבד ברקע (Section "-extensions"), אם יש אינטרנט
  StrCpy $EXTS "$EXTS ms-dotnettools.csharp"
SectionEnd

; מעתיק את תבנית הפרויקט לכל גרסת Visual Studio שנמצאה (Documents\Visual Studio <שנה>) ורושם איפה — בשביל ההסרה
Function InstallVsTemplates
  StrCpy $R8 0
  FindFirst $R0 $R1 "$DOCUMENTS\Visual Studio *"
  ${DoWhile} $R1 != ""
    ${If} ${FileExists} "$DOCUMENTS\$R1\*.*"
      Push "$DOCUMENTS\$R1"
      Call CopyVsTemplate
      IntOp $R8 $R8 + 1
    ${EndIf}
    FindNext $R0 $R1
  ${Loop}
  FindClose $R0
  ${If} $R8 == 0
  ${AndIf} ${FileExists} "$PROGRAMFILES64\Microsoft Visual Studio\2022\*.*"
    ; Visual Studio מותקן אבל עוד לא נפתח אף פעם — יוצרים את תיקיית התבניות מראש
    Push "$DOCUMENTS\Visual Studio 2022"
    Call CopyVsTemplate
  ${EndIf}
FunctionEnd

Function CopyVsTemplate
  Exch $0
  Push $1
  CreateDirectory "$0\Templates\ProjectTemplates"
  ClearErrors
  CopyFiles /SILENT "$INSTDIR\vs-template\DSViz-Exercise.zip" "$0\Templates\ProjectTemplates\DSViz-Exercise.zip"
  ${IfNot} ${Errors}
    FileOpen $1 "$INSTDIR\vs-templates.txt" a
    FileSeek $1 0 END
    FileWriteUTF16LE $1 "$0\Templates\ProjectTemplates\DSViz-Exercise.zip$\r$\n"
    FileClose $1
    DetailPrint "Visual Studio: נוספה תבנית פרויקט ב-$0"
  ${EndIf}
  Pop $1
  Pop $0
FunctionEnd

; ------------------------------------------------------------ בחירת חבילת שפה
; ברירת מחדל: Java; C# מסומן אוטומטית אם יש Visual Studio / Rider. אפשר לקבוע גם משורת הפקודה: /LANG=java|cs|both
Function .onInit
  ${GetParameters} $R0
  ${GetOptions} $R0 "/LANG=" $R1
  ; עדכון אוטומטי: אותן שפות כמו בהתקנה הקודמת
  ClearErrors
  ${GetOptions} $R0 "/UPDATE" $R3
  ${IfNot} ${Errors}
    StrCpy $UPDATING "1"
  ${EndIf}
  ${If} $UPDATING == "1"
  ${AndIf} ${FileExists} "$INSTDIR\lang.txt"
    FileOpen $R4 "$INSTDIR\lang.txt" r
    FileRead $R4 $R1
    FileClose $R4
    ${If} $R1 == "java"
      StrCpy $R1 "javaonly"
    ${EndIf}
  ${EndIf}
  ${If} $R1 == "cs"
    !insertmacro UnselectSection ${SEC_JAVA}
    !insertmacro SelectSection ${SEC_CS}
  ${ElseIf} $R1 == "both"
    !insertmacro SelectSection ${SEC_CS}
  ${ElseIf} $R1 == "javaonly"
    !insertmacro UnselectSection ${SEC_CS}
  ${ElseIf} $R1 == ""
    Call DetectCsTools
    Pop $R2
    ${If} $R2 == "1"
      !insertmacro SelectSection ${SEC_CS}
    ${EndIf}
  ${EndIf}
FunctionEnd

; עדכון אוטומטי שהסתיים: חלון קטן בפינה — "DSViz עודכן לגרסה החדשה" (נעלם לבד, לא לוקח פוקוס). לא מחכים לו
Function .onInstSuccess
  ${If} $UPDATING == "1"
  ${AndIf} ${FileExists} "$INSTDIR\notify.ps1"
    Exec '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "$INSTDIR\notify.ps1" -State done'
  ${EndIf}
FunctionEnd

; לפחות חבילה אחת חייבת להיות מסומנת
Function .onSelChange
  ${IfNot} ${SectionIsSelected} ${SEC_JAVA}
  ${AndIfNot} ${SectionIsSelected} ${SEC_CS}
    !insertmacro SelectSection ${SEC_JAVA}
  ${EndIf}
FunctionEnd

; מחזיר 1 אם נמצא Visual Studio או Rider במחשב
Function DetectCsTools
  Push "0"
  FindFirst $R3 $R4 "$DOCUMENTS\Visual Studio *"
  ${If} $R4 != ""
    Pop $R5
    Push "1"
  ${EndIf}
  FindClose $R3
  ${If} ${FileExists} "$PROGRAMFILES64\Microsoft Visual Studio\*.*"
  ${OrIf} ${FileExists} "$PROGRAMFILES32\Microsoft Visual Studio\*.*"
  ${OrIf} ${FileExists} "$PROGRAMFILES64\JetBrains\*.*"
  ${OrIf} ${FileExists} "$LOCALAPPDATA\JetBrains\Toolbox\*.*"
    Pop $R5
    Push "1"
  ${EndIf}
FunctionEnd

Section "-javaproject"
  ${IfNot} ${SectionIsSelected} ${SEC_JAVA}
    Return
  ${EndIf}
  ${IfNot} ${FileExists} "${PROJECT_DIR}\.project"
    CreateDirectory "${PROJECT_DIR}"
    CopyFiles /SILENT "$INSTDIR\project\*.*" "${PROJECT_DIR}"
    DetailPrint "נוצרה תיקיית התרגילים: ${PROJECT_DIR}"
  ${Else}
    ; התיקייה כבר קיימת — לא נוגעים בעבודה של התלמיד, רק מעדכנים את הספרייה
    CreateDirectory "${PROJECT_DIR}\lib"
    CopyFiles /SILENT "$INSTDIR\dsviz.jar" "${PROJECT_DIR}\lib"
    DetailPrint "תיקיית התרגילים קיימת — הספרייה עודכנה"
  ${EndIf}
  ; התוספים (Eclipse ו-VS Code) מוצאים את התיקייה לפי הקובץ הזה
  CreateDirectory "$PROFILE\.dsviz"
  FileOpen $0 "$PROFILE\.dsviz\project-path.txt" w
  FileWriteUTF16LE /BOM $0 "${PROJECT_DIR}"
  FileClose $0
  ${If} $CODE != ""
    ${GetParent} $CODE $1
    ${GetParent} $1 $1
    ${If} ${FileExists} "$1\Code.exe"
    ${AndIf} ${FileExists} "${PROJECT_DIR}\.project"
    ${EndIf}
  ${EndIf}
SectionEnd

Section "-javapack"
  ; לא נחוץ להרצה (התוסף שלנו מריץ בעצמו עם ה-Java שבהתקנה). רץ ברקע ובחלון נסתר — ההתקנה לא מחכה לו.
  ; אם אין אינטרנט — ההורדה פשוט נכשלת בשקט, ושאר ההתקנה לא מושפעת
  ${IfNot} ${SectionIsSelected} ${SEC_JAVA}
    Return
  ${EndIf}
  StrCpy $EXTS "$EXTS vscjava.vscode-java-pack"
SectionEnd

Section "-eclipse"
  ${IfNot} ${SectionIsSelected} ${SEC_JAVA}
    Return
  ${EndIf}
  StrCpy $ECLIPSES 0
  InitPluginsDir
  ; כל Eclipse שקיבל את התוסף בהתקנה הקודמת מקבל גם את החדש — גם אם החיפוש לא מוצא אותו הפעם (אחרת נשאר בו התוסף הישן)
  Delete "$PLUGINSDIR\eclipse-prev.txt"
  ${If} ${FileExists} "$INSTDIR\eclipse-dirs.txt"
    CopyFiles /SILENT "$INSTDIR\eclipse-dirs.txt" "$PLUGINSDIR\eclipse-prev.txt"
  ${EndIf}
  Delete "$INSTDIR\eclipse-dirs.txt"
  ; חיפוש מלא (PowerShell): Eclipse שרץ עכשיו, התיקיות הרגילות וכל הכוננים — בכל שם תיקייה, רק Eclipse ל-Java (עם JDT)
  File "/oname=$PLUGINSDIR\find-eclipse.ps1" "find-eclipse.ps1"
  Delete "$PLUGINSDIR\eclipse-found.txt"
  DetailPrint "Eclipse: מחפש התקנות במחשב..."
  nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$PLUGINSDIR\find-eclipse.ps1" "$PLUGINSDIR\eclipse-found.txt" "$PLUGINSDIR\eclipse-prev.txt"'
  Pop $0
  ${If} ${FileExists} "$PLUGINSDIR\eclipse-found.txt"
    FileOpen $R2 "$PLUGINSDIR\eclipse-found.txt" r
    FileReadUTF16LE $R2 $R3
    ${DoWhile} $R3 != ""
      ; מסירים סוף שורה
      trim_eol:
      StrCpy $R4 $R3 1 -1
      StrCmp $R4 "$\n" cut_eol
      StrCmp $R4 "$\r" cut_eol
      Goto eol_done
      cut_eol:
      StrCpy $R3 $R3 -1
      Goto trim_eol
      eol_done:
      ${If} $R3 != ""
        Push $R3
        Call TryEclipse
      ${EndIf}
      FileReadUTF16LE $R2 $R3
    ${Loop}
    FileClose $R2
  ${EndIf}
  ; גיבוי: אם החיפוש לא עבד (אין PowerShell / נחסם) — המקומות הרגילים, לפי שם התיקייה
  ${If} $ECLIPSES == 0
    !insertmacro SCAN "$PROFILE\eclipse" "*"
    !insertmacro SCAN "$PROFILE" "eclipse*"
    !insertmacro SCAN "$DESKTOP" "eclipse*"
    !insertmacro SCAN "$PROFILE\Downloads" "eclipse*"
    !insertmacro SCAN "$PROGRAMFILES64" "eclipse*"
    !insertmacro SCAN "C:" "eclipse*"
    !insertmacro SCAN "D:" "eclipse*"
  ${EndIf}
  Call HasIntelliJ
  ${If} $ECLIPSES == 0
  ${AndIf} "$CODE$FORKS" != ""
    DetailPrint "Eclipse: לא נמצא — דילוג (VS Code הותקן)"
  ${ElseIf} $ECLIPSES == 0
  ${AndIf} $HASIDEA == 1
    DetailPrint "Eclipse: לא נמצא — דילוג (IntelliJ IDEA נמצא)"
  ${ElseIf} $ECLIPSES == 0
    MessageBox MB_YESNO|MB_ICONQUESTION "לא נמצאו VS Code ו-Eclipse באופן אוטומטי.$\r$\n$\r$\nלבחור את התיקייה של Eclipse (התיקייה שבה נמצא eclipse.exe)?" /SD IDNO IDNO eclipse_done
    nsDialogs::SelectFolderDialog "התיקייה של Eclipse (שבה נמצא eclipse.exe)" "C:\"
    Pop $0
    ${If} $0 != "error"
      Push $0
      Call TryEclipse
      ${If} $ECLIPSES == 0
        DetailPrint "Eclipse: בתיקייה $0 אין eclipse.exe"
      ${EndIf}
    ${EndIf}
    eclipse_done:
  ${EndIf}
  DetailPrint "Eclipse: הותקן ב-$ECLIPSES מקומות"
SectionEnd

Section "-intellij"
  ; כל IntelliJ IDEA במחשב (Community / Ultimate, התקנה רגילה ו-Toolbox, 2024.2 ומעלה) — בלי שאלות (intellij.ps1)
  ${IfNot} ${SectionIsSelected} ${SEC_JAVA}
    Return
  ${EndIf}
  Call EnsureIntelliJClosed
  DetailPrint "IntelliJ IDEA: מחפש התקנות במחשב..."
  nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\intellij.ps1" -Source "$INSTDIR\intellij\dsviz-intellij"'
  Pop $0
SectionEnd

Section "-bank"
  ; "מאגר התרגילים" עם מסך הכניסה — לכל סביבה שקיבלה את DSViz: Eclipse (dropins), IntelliJ IDEA, Visual Studio (C#).
  ; רץ בכל התקנה ובכל עדכון אוטומטי (/UPDATE), בשקט וחוזר על עצמו בלי להכפיל (bank-install.ps1). בלי חלון (nsExec)
  RMDir /r "$INSTDIR\bank"
  SetOutPath "$INSTDIR\bank"
  File "${BANKDIR}\il.lamerhav.dsviz.bank_*.jar"
  File "${BANKDIR}\dsviz-bank-vs.jar"
  File "${BANKDIR}\dsviz-bank-intellij-*.zip"
  File "..\vs-bank\install\install-bank-vs.ps1"
  File "bank-install.ps1"
  SetOutPath "$INSTDIR"
  ; גרסת הבדיקה הישנה (DSViz-Lab) — ההתקנה הזאת מחליפה אותה: מסירים את הרישום והתיקייה שלה, כדי שההסרה שלה לא תמחק את המאגר
  ${If} ${FileExists} "$LOCALAPPDATA\DSViz-Lab\*.*"
    RMDir /r "$LOCALAPPDATA\DSViz-Lab"
    DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz-Lab"
    DetailPrint "גרסת הבדיקה (DSViz-Lab) הוחלפה בהתקנה הזאת"
  ${EndIf}
  ${If} ${SectionIsSelected} ${SEC_CS}
    DetailPrint "מאגר התרגילים: Eclipse, IntelliJ IDEA ו-Visual Studio..."
    nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\bank\bank-install.ps1"'
    Pop $0
    ; קיצורי המאגר בתפריט התחלה (בשביל Rider; ב-Visual Studio — הכפתורים בסרגל). בהתקנה חדשה ה-jar מגיע רק כאן (אחרי חלק ה-C#),
    ; לכן יוצרים אותם גם כאן. javaw דרך הצומת באנגלית (automata.ps1 -JavaPath) — שם משתמש בעברית
    ${If} ${FileExists} "$INSTDIR\lab\dsviz-bank-vs.jar"
      Push $1
      Push $R9
      Call DSVizJavaw
      StrCpy $1 $R9
      Pop $R9
      CreateDirectory "$SMPROGRAMS\מבני נתונים"
      CreateShortcut "$SMPROGRAMS\מבני נתונים\מאגר תרגילים C#.lnk" "$1" '-cp "lab\dsviz-bank-vs.jar;dsviz.jar" il.lamerhav.dsviz.BankVs bank' "$INSTDIR\icon.ico"
      CreateShortcut "$SMPROGRAMS\מבני נתונים\השאלה — מאגר תרגילים C#.lnk" "$1" '-cp "lab\dsviz-bank-vs.jar;dsviz.jar" il.lamerhav.dsviz.BankVs question' "$INSTDIR\icon.ico"
      Pop $1
    ${EndIf}
    Push $0
  ${Else}
    DetailPrint "מאגר התרגילים: Eclipse ו-IntelliJ IDEA..."
    nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\bank\bank-install.ps1" -NoVs'
  ${EndIf}
  Pop $0
SectionEnd

Section "-extensions"
  ; התוספים של Microsoft ל-VS Code (השלמה אוטומטית ל-Java / C#): יורדים ומותקנים לבד ברקע, בחלון נסתר,
  ; בזה אחר זה (כדי ש-VS Code לא יקבל כמה התקנות במקביל). ההתקנה לא מחכה להם. בלי אינטרנט — מדלגים בשקט
  ${If} $CODE != ""
  ${AndIf} $EXTS != ""
    DetailPrint "VS Code: מתקין (מאחורי הקלעים, בלי חלון):$EXTS"
    ; nsExec — בלי חלון cmd, וההתקנה מחכה שכל התוספים יסתיימו ורק אז מודיעה שנגמר
    nsExec::ExecToLog '"$SYSDIR\cmd.exe" /c for %e in ($EXTS) do "$CODE" --install-extension %e'
    Pop $0
  ${EndIf}
SectionEnd

Section "-langs"
  ; השפות שנבחרו — העדכון האוטומטי (/UPDATE) מתקין שוב את אותן
  FileOpen $0 "$INSTDIR\lang.txt" w
  ${If} ${SectionIsSelected} ${SEC_JAVA}
  ${AndIf} ${SectionIsSelected} ${SEC_CS}
    FileWrite $0 "both"
  ${ElseIf} ${SectionIsSelected} ${SEC_CS}
    FileWrite $0 "cs"
  ${Else}
    FileWrite $0 "java"
  ${EndIf}
  FileClose $0
SectionEnd

Section "-wait"
  ; נותנים לתהליכים הנסתרים של הורדת התוספים (VS Code) לעלות לפני שהמתקין נסגר
  Sleep 1500
SectionEnd

; ------------------------------------------------------------ הסרה
Section "Uninstall"
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "DSVizUpdate"
  ; "מודלים חישוביים": סוגרים, מוחקים את הקבצים ואת הקיצורים. ‎automata-data‎ (העבודה שנשמרה בדף) נשאר — כמו תיקיות התרגילים
  ${If} ${FileExists} "$INSTDIR\automata.ps1"
    nsExec::Exec '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "$INSTDIR\automata.ps1" -Stop'
    Pop $0
  ${EndIf}
  RMDir /r "$INSTDIR\automata"
  Delete "$INSTDIR\automata.ps1"
  Delete "$DESKTOP\מודלים חישוביים.lnk"
  Delete "$SMPROGRAMS\מבני נתונים\מודלים חישוביים.lnk"
  Delete "$SMPROGRAMS\מבני נתונים\מאגר תרגילים C#.lnk"
  Delete "$SMPROGRAMS\מבני נתונים\השאלה — מאגר תרגילים C#.lnk"
  Delete "$PROFILE\.dsviz\install-dir.txt"
  Call un.FindCode
  ${If} $CODE != ""
    DetailPrint "VS Code: מסיר את התוסף..."
    nsExec::ExecToLog '"$SYSDIR\cmd.exe" /c ""$CODE" --uninstall-extension lamerhav.dsviz"'
    Pop $0
  ${EndIf}

  ; VS Code Insiders / VSCodium / Cursor (נרשמו ב-vscode-forks.txt)
  ClearErrors
  FileOpen $3 "$INSTDIR\vscode-forks.txt" r
  ${IfNot} ${Errors}
    ${Do}
      ClearErrors
      FileReadUTF16LE $3 $4
      ${If} ${Errors}
        ${Break}
      ${EndIf}
      Call un.TrimLine
      ${If} $4 != ""
      ${AndIf} ${FileExists} "$4"
        nsExec::ExecToLog '"$SYSDIR\cmd.exe" /c ""$4" --uninstall-extension lamerhav.dsviz"'
        Pop $0
      ${EndIf}
    ${Loop}
    FileClose $3
  ${EndIf}
  Delete "$INSTDIR\vscode-forks.txt"
  ; IntelliJ IDEA: מכל הגרסאות (‎%APPDATA%\JetBrains\*\plugins\dsviz-intellij)
  ${If} ${FileExists} "$INSTDIR\intellij.ps1"
    nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\intellij.ps1" -Uninstall'
    Pop $0
  ${EndIf}
  RMDir /r "$INSTDIR\intellij"
  Delete "$INSTDIR\intellij.ps1"
  ; מאגר התרגילים (Eclipse, IntelliJ, Visual Studio) — לפני שמוחקים את eclipse-dirs.txt
  ${If} ${FileExists} "$INSTDIR\bank\bank-install.ps1"
    nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\bank\bank-install.ps1" -Remove'
    Pop $0
  ${EndIf}
  RMDir /r "$INSTDIR\bank"
  RMDir /r "$INSTDIR\lab"

  ; כל התקנות Eclipse שהתוסף הועתק אליהן רשומות ב-eclipse-dirs.txt (שורה לכל תיקייה)
  ClearErrors
  FileOpen $3 "$INSTDIR\eclipse-dirs.txt" r
  ${IfNot} ${Errors}
    ${Do}
      ClearErrors
      FileReadUTF16LE $3 $4
      ${If} ${Errors}
        ${Break}
      ${EndIf}
      Call un.RemoveFromEclipse
    ${Loop}
    FileClose $3
  ${EndIf}

  ; תבניות Visual Studio (נרשמו ב-vs-templates.txt)
  ClearErrors
  FileOpen $3 "$INSTDIR\vs-templates.txt" r
  ${IfNot} ${Errors}
    ${Do}
      ClearErrors
      FileReadUTF16LE $3 $4
      ${If} ${Errors}
        ${Break}
      ${EndIf}
      Call un.TrimLine
      ${If} $4 != ""
        Delete "$4"
        DetailPrint "Visual Studio: הוסרה תבנית $4"
      ${EndIf}
    ${Loop}
    FileClose $3
  ${EndIf}
  Delete "$INSTDIR\vs-templates.txt"
  ${If} ${FileExists} "$INSTDIR\cs\install-vsix.ps1"
    nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\cs\install-vsix.ps1" -Uninstall'
    Pop $0
  ${EndIf}
  RMDir /r "$INSTDIR\cs"
  RMDir /r "$INSTDIR\cs-project"
  RMDir /r "$INSTDIR\vs-template"
  Delete "$PROFILE\.dsviz\cs-path.txt"
  ; MSBuild (DSViz.targets) — משאירים את התיקייה (ImportAfter) כי יכולים להיות בה קבצים של תוכנות אחרות
  Delete "${MSBUILD_IMPORTAFTER}\DSViz.targets"
  Delete "$DESKTOP\תרגילי C# (מבני נתונים).lnk"
  Delete "$DESKTOP\תרגיל חדש ב-C#.lnk"
  Delete "$SMPROGRAMS\מבני נתונים\תרגיל חדש ב-C#.lnk"
  Delete "$SMPROGRAMS\מבני נתונים\הוספת משתנה ל-C#.lnk"
  Delete "$SMPROGRAMS\מבני נתונים\הוסף מבנה נתונים ל-C#.lnk"
  Delete "$SMPROGRAMS\מבני נתונים\הדרכה לשימוש.lnk"
  Delete "$DESKTOP\הוסף מבנה נתונים ל-C#.lnk"
  Delete "$DESKTOP\שליחת תרגיל ב-C#.lnk"
  Delete "$SMPROGRAMS\מבני נתונים\שליחת תרגיל ב-C#.lnk"
  Delete "$DESKTOP\הצעה לפתרון ב-C#.lnk"
  Delete "$SMPROGRAMS\מבני נתונים\הצעה לפתרון ב-C#.lnk"
  ; תיקיית התרגילים ב-Documents\DataStructures-CS נשארת — יש בה את העבודה של התלמיד

  Delete "$INSTDIR\${VSIX}"
  Delete "$INSTDIR\${PLUGIN}"
  Delete "$INSTDIR\dsviz.jar"
  Delete "$INSTDIR\README-he.html"
  Delete "$INSTDIR\icon.ico"
  Delete "$INSTDIR\eclipse-dirs.txt"
  Delete "$INSTDIR\clean-eclipse.ps1"
  Delete "$INSTDIR\uninstall.exe"
  RMDir /r "$INSTDIR\project"
  RMDir /r "$INSTDIR\jdk"
  Delete "$PROFILE\.dsviz\jdk-path.txt"
  Delete "$DESKTOP\תרגילי מבני נתונים (VS Code).lnk"
  ; תיקיית התרגילים במסמכים נשארת — יש בה את העבודה של התלמיד
  RMDir "$INSTDIR"
  Delete "$SMPROGRAMS\מבני נתונים\מדריך.lnk"
  Delete "$SMPROGRAMS\מבני נתונים\הסרה.lnk"
  RMDir "$SMPROGRAMS\מבני נתונים"
  DeleteRegKey HKCU "${UNINST_KEY}"
SectionEnd

; מוריד סוף שורה (CR/LF) מ-$4
Function un.TrimLine
  Push $5
  StrCpy $5 $4 1 -1
  ${If} $5 == "$\n"
    StrCpy $4 $4 -1
  ${EndIf}
  StrCpy $5 $4 1 -1
  ${If} $5 == "$\r"
    StrCpy $4 $4 -1
  ${EndIf}
  Pop $5
FunctionEnd

; $4 = שורה מהרשימה (תיקיית Eclipse) — מוחקים את התוסף מ-dropins שלה
Function un.RemoveFromEclipse
  StrCpy $5 $4 1 -1
  ${If} $5 == "$\n"
    StrCpy $4 $4 -1
  ${EndIf}
  StrCpy $5 $4 1 -1
  ${If} $5 == "$\r"
    StrCpy $4 $4 -1
  ${EndIf}
  ${If} $4 != ""
    ${If} ${FileExists} "$INSTDIR\clean-eclipse.ps1"
      nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\clean-eclipse.ps1" "$4"'
      Pop $5
    ${EndIf}
    Delete "$4\dropins\il.lamerhav.dsviz_*.jar"
    DetailPrint "Eclipse: הוסר מ-$4"
  ${EndIf}
FunctionEnd
