; ============================================================
;  מבני נתונים — הרצה ויזואלית : תוכנית התקנה ל-Windows
;  מתקינה את התוסף ל-VS Code ואת התוסף לכל Eclipse שנמצא במחשב.
;  נבנית ע"י tools/build-all.sh (makensis). לא דורשת הרשאות מנהל ולא שואלת מה להתקין — הכול אוטומטי.
;  התקנה שקטה (למשל לכל מחשבי המעבדה): DSViz-Setup.exe /S   הסרה שקטה: uninstall.exe /S
; ============================================================
SetCompressor /SOLID lzma
Target amd64-unicode   ; Windows 10/11 של 64 ביט (כל המחשבים היום)
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"
!include "Sections.nsh"
!insertmacro GetParameters
!insertmacro GetOptions

!ifndef VERSION
  !define VERSION "1.0.1"
!endif
!ifndef RELEASE
  !define RELEASE "..\release"
!endif
!ifndef BUILD
  !define BUILD "..\build"
!endif
!define PROJECT_DIR "$DOCUMENTS\DataStructures"
!define PROJECT_CS_DIR "$DOCUMENTS\DataStructures-CS"
!define VSIX "dsviz-${VERSION}.vsix"
!define PLUGIN "il.lamerhav.dsviz_${VERSION}.jar"
!define UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz"

VIProductVersion "${VERSION}.0"
VIAddVersionKey /LANG=1037 "ProductName" "DSViz — Data Structures Visualizer"
VIAddVersionKey /LANG=1037 "FileDescription" "הרצה ויזואלית למבני נתונים — תוכנית התקנה"
VIAddVersionKey /LANG=1037 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=1037 "ProductVersion" "${VERSION}"
VIAddVersionKey /LANG=1037 "CompanyName" "lamerhav"
VIAddVersionKey /LANG=1037 "LegalCopyright" "lamerhav"

Name "מבני נתונים — הרצה ויזואלית"
OutFile "${RELEASE}\DSViz-Setup.exe"
InstallDir "$LOCALAPPDATA\DSViz"
RequestExecutionLevel user
BrandingText "מבני נתונים — הרצה ויזואלית ${VERSION}"
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
!define MUI_WELCOMEPAGE_TEXT "התוכנה מוסיפה ל-VS Code, ל-Eclipse ול-Visual Studio (בחירה בעמוד הבא: חבילת Java, חבילת C#, או שתיהן):$\r$\n$\r$\n• תרגיל חדש בלחיצה: מחסנית, תור, רשימה מקושרת או עץ — עם כל המעטפת מוכנה.$\r$\n• הרצה ויזואלית: כל שורה בקוד, עם המשתנים והמבנים — צעד אחר צעד.$\r$\n$\r$\nההתקנה מוצאת לבד את הסביבות שבמחשב, ומורידה לבד (ברקע) את התוספים של Microsoft ל-VS Code (Java / C#) — צריך אינטרנט רק בשביל זה.$\r$\nמומלץ לסגור את VS Code ואת Eclipse לפני שממשיכים."
!define MUI_FINISHPAGE_TITLE "ההתקנה הסתיימה"
!define MUI_FINISHPAGE_TEXT "יש לפתוח מחדש את VS Code / Eclipse / Visual Studio.$\r$\n$\r$\nVS Code: בסרגל הצד מופיע הסמל 'מבני נתונים'.$\r$\nEclipse: בתפריט העליון מופיע 'מבני נתונים', וכפתורים בסרגל הכלים.$\r$\nVisual Studio (C#): פותחים את הקיצור 'תרגילי C#' בשולחן העבודה, ובוחרים ליד כפתור ההפעלה 'הרצה ויזואלית'."
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
    Delete "$0\dropins\il.lamerhav.dsviz_*.jar"
    ClearErrors
    CopyFiles /SILENT "$INSTDIR\${PLUGIN}" "$0\dropins"
    ${If} ${Errors}
      DetailPrint "Eclipse: אין הרשאה לכתוב ל-$0\dropins — יש להריץ את ההתקנה כמנהל (לחיצה ימנית ← הפעל כמנהל)"
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

; ------------------------------------------------------------ רכיבים
Section "-core"
  SetOutPath "$INSTDIR"
  File "${RELEASE}\${VSIX}"
  File "${RELEASE}\${PLUGIN}"
  File "${RELEASE}\dsviz.jar"
  File "README-he.html"
  File "icon.ico"
  SetOutPath "$INSTDIR\project"
  File /r "${BUILD}\project\*.*"
  SetOutPath "$INSTDIR"
  WriteUninstaller "$INSTDIR\uninstall.exe"

  WriteRegStr HKCU "${UNINST_KEY}" "DisplayName" "מבני נתונים — הרצה ויזואלית"
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINST_KEY}" "Publisher" "lamerhav"
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

Section "Java — Eclipse ו-VS Code (תרגילים ב-Java)" SEC_JAVA
  DetailPrint "חבילת Java"
SectionEnd

Section /o "C# — Visual Studio, VS Code ו-Rider (תרגילים ב-C#)" SEC_CS
  DetailPrint "חבילת C#"
  ; ההרצה הוויזואלית (dsviz-cs.exe — כוללת את סביבת ההרצה של .NET, לא צריך להתקין כלום)
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

  ; תיקיית תרגילים מוכנה (Documents\DataStructures-CS): פתרון של Visual Studio עם תרגיל ודוגמאות
  ${IfNot} ${FileExists} "${PROJECT_CS_DIR}\DataStructures-CS.sln"
    CreateDirectory "${PROJECT_CS_DIR}"
    CopyFiles /SILENT "$INSTDIR\cs-project\*.*" "${PROJECT_CS_DIR}"
    DetailPrint "נוצרה תיקיית התרגילים ל-C#: ${PROJECT_CS_DIR}"
  ${Else}
    ; כבר קיימת — לא נוגעים בעבודה של התלמיד, רק מעדכנים את הספרייה
    CreateDirectory "${PROJECT_CS_DIR}\lib"
    CopyFiles /SILENT "$INSTDIR\cs-project\lib\Unit4.CollectionsLib.dll" "${PROJECT_CS_DIR}\lib"
    DetailPrint "תיקיית התרגילים ל-C# קיימת — הספרייה עודכנה"
  ${EndIf}
  FileOpen $0 "$PROFILE\.dsviz\project-cs-path.txt" w
  FileWriteUTF16LE /BOM $0 "${PROJECT_CS_DIR}"
  FileClose $0
  CreateShortcut "$DESKTOP\תרגילי C# (מבני נתונים).lnk" "${PROJECT_CS_DIR}\DataStructures-CS.sln" "" "$INSTDIR\icon.ico"
  ; החלונות "תרגיל חדש" ו"הוסף מבנה נתונים" ל-C# (אותם חלונות כמו ב-Eclipse וב-VS Code) — מקיצור, בשביל Visual Studio / Rider
  CreateShortcut "$DESKTOP\תרגיל חדש ב-C#.lnk" "$INSTDIR\jdk\bin\javaw.exe" '-cp "$INSTDIR\dsviz.jar" il.lamerhav.dsviz.cs.CsTool new "${PROJECT_CS_DIR}"' "$INSTDIR\icon.ico"
  CreateDirectory "$SMPROGRAMS\מבני נתונים"
  CreateShortcut "$SMPROGRAMS\מבני נתונים\תרגיל חדש ב-C#.lnk" "$INSTDIR\jdk\bin\javaw.exe" '-cp "$INSTDIR\dsviz.jar" il.lamerhav.dsviz.cs.CsTool new "${PROJECT_CS_DIR}"' "$INSTDIR\icon.ico"
  Delete "$SMPROGRAMS\מבני נתונים\הוספת משתנה ל-C#.lnk"   ; השם מגרסה קודמת
  CreateShortcut "$DESKTOP\הוסף מבנה נתונים ל-C#.lnk" "$INSTDIR\jdk\bin\javaw.exe" '-cp "$INSTDIR\dsviz.jar" il.lamerhav.dsviz.cs.CsTool insert "${PROJECT_CS_DIR}"' "$INSTDIR\icon.ico"
  CreateShortcut "$SMPROGRAMS\מבני נתונים\הוסף מבנה נתונים ל-C#.lnk" "$INSTDIR\jdk\bin\javaw.exe" '-cp "$INSTDIR\dsviz.jar" il.lamerhav.dsviz.cs.CsTool insert "${PROJECT_CS_DIR}"' "$INSTDIR\icon.ico"

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
  ${If} $R1 == "cs"
    !insertmacro UnselectSection ${SEC_JAVA}
    !insertmacro SelectSection ${SEC_CS}
  ${ElseIf} $R1 == "both"
    !insertmacro SelectSection ${SEC_CS}
  ${ElseIf} $R1 == ""
    Call DetectCsTools
    Pop $R2
    ${If} $R2 == "1"
      !insertmacro SelectSection ${SEC_CS}
    ${EndIf}
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
  ; קיצור דרך בשולחן העבודה שפותח את תיקיית התרגילים ב-VS Code
  ${If} $CODE != ""
    ${GetParent} $CODE $1
    ${GetParent} $1 $1
    ${If} ${FileExists} "$1\Code.exe"
    ${AndIf} ${FileExists} "${PROJECT_DIR}\.project"
      CreateShortcut "$DESKTOP\תרגילי מבני נתונים (VS Code).lnk" "$1\Code.exe" '"${PROJECT_DIR}"' "$INSTDIR\icon.ico"
      DetailPrint "VS Code: נוצר קיצור דרך בשולחן העבודה"
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
  Delete "$INSTDIR\eclipse-dirs.txt"
  !insertmacro SCAN "$PROFILE\eclipse" "*"
  !insertmacro SCAN "$PROFILE" "eclipse*"
  !insertmacro SCAN "$DESKTOP" "eclipse*"
  !insertmacro SCAN "$PROFILE\Downloads" "eclipse*"
  !insertmacro SCAN "$PROGRAMFILES64" "eclipse*"
  !insertmacro SCAN "C:" "eclipse*"
  !insertmacro SCAN "D:" "eclipse*"
  ${If} $ECLIPSES == 0
  ${AndIf} $CODE != ""
    DetailPrint "Eclipse: לא נמצא — דילוג (VS Code הותקן)"
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

Section "-extensions"
  ; התוספים של Microsoft ל-VS Code (השלמה אוטומטית ל-Java / C#): יורדים ומותקנים לבד ברקע, בחלון נסתר,
  ; בזה אחר זה (כדי ש-VS Code לא יקבל כמה התקנות במקביל). ההתקנה לא מחכה להם. בלי אינטרנט — מדלגים בשקט
  ${If} $CODE != ""
  ${AndIf} $EXTS != ""
    DetailPrint "VS Code: יורדים ומותקנים ברקע:$EXTS"
    ExecShell "open" "$SYSDIR\cmd.exe" '/c for %e in ($EXTS) do "$CODE" --install-extension %e' SW_HIDE
  ${EndIf}
SectionEnd

Section "-wait"
  ; נותנים לתהליכים הנסתרים של הורדת התוספים (VS Code) לעלות לפני שהמתקין נסגר
  Sleep 1500
SectionEnd

; ------------------------------------------------------------ הסרה
Section "Uninstall"
  Call un.FindCode
  ${If} $CODE != ""
    DetailPrint "VS Code: מסיר את התוסף..."
    nsExec::ExecToLog '"$SYSDIR\cmd.exe" /c ""$CODE" --uninstall-extension lamerhav.dsviz"'
    Pop $0
  ${EndIf}

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
  RMDir /r "$INSTDIR\cs"
  RMDir /r "$INSTDIR\cs-project"
  RMDir /r "$INSTDIR\vs-template"
  Delete "$PROFILE\.dsviz\cs-path.txt"
  Delete "$DESKTOP\תרגילי C# (מבני נתונים).lnk"
  Delete "$DESKTOP\תרגיל חדש ב-C#.lnk"
  Delete "$SMPROGRAMS\מבני נתונים\תרגיל חדש ב-C#.lnk"
  Delete "$SMPROGRAMS\מבני נתונים\הוספת משתנה ל-C#.lnk"
  Delete "$SMPROGRAMS\מבני נתונים\הוסף מבנה נתונים ל-C#.lnk"
  Delete "$DESKTOP\הוסף מבנה נתונים ל-C#.lnk"
  ; תיקיית התרגילים ב-Documents\DataStructures-CS נשארת — יש בה את העבודה של התלמיד

  Delete "$INSTDIR\${VSIX}"
  Delete "$INSTDIR\${PLUGIN}"
  Delete "$INSTDIR\dsviz.jar"
  Delete "$INSTDIR\README-he.html"
  Delete "$INSTDIR\icon.ico"
  Delete "$INSTDIR\eclipse-dirs.txt"
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
    Delete "$4\dropins\il.lamerhav.dsviz_*.jar"
    DetailPrint "Eclipse: הוסר מ-$4"
  ${EndIf}
FunctionEnd
