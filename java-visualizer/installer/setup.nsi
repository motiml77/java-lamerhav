; ============================================================
;  מבני נתונים — הרצה ויזואלית : תוכנית התקנה ל-Windows
;  מתקינה את התוסף ל-VS Code ואת התוסף לכל Eclipse שנמצא במחשב.
;  נבנית ע"י tools/build-all.sh (makensis). לא דורשת הרשאות מנהל.
;  התקנה שקטה (למשל לכל מחשבי המעבדה): DSViz-Setup.exe /S   הסרה שקטה: uninstall.exe /S
; ============================================================
SetCompressor /SOLID lzma
Target amd64-unicode   ; Windows 10/11 של 64 ביט (כל המחשבים היום)
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"

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
!define VSIX "dsviz-${VERSION}.vsix"
!define PLUGIN "il.lamerhav.dsviz_${VERSION}.jar"
!define UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\DSViz"

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
!define MUI_WELCOMEPAGE_TITLE "מבני נתונים — הרצה ויזואלית"
!define MUI_WELCOMEPAGE_TEXT "התוכנה מוסיפה ל-VS Code ול-Eclipse:$\r$\n$\r$\n• תרגיל חדש בלחיצה: מחסנית, תור, רשימה מקושרת או עץ — עם כל המעטפת מוכנה.$\r$\n• הרצה ויזואלית: כל שורה בקוד, עם המשתנים והמבנים — צעד אחר צעד.$\r$\n$\r$\nההתקנה מוצאת לבד את VS Code ואת Eclipse שבמחשב. מומלץ לסגור אותם לפני שממשיכים."
!define MUI_FINISHPAGE_TITLE "ההתקנה הסתיימה"
!define MUI_FINISHPAGE_TEXT "יש לפתוח מחדש את VS Code / Eclipse.$\r$\n$\r$\nVS Code: בסרגל הצד מופיע הסמל 'מבני נתונים'.$\r$\nEclipse: בתפריט העליון מופיע 'מבני נתונים', וכפתורים בסרגל הכלים."
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

Section "Java 21 (כלולה בהתקנה — בלי הורדה)" SEC_JDK
  SectionIn RO
  ; Java מקוצרת (java, javac, חלון, הרצה שורה-שורה) — התוסף של VS Code מריץ איתה את התוכניות
  SetOutPath "$INSTDIR\jdk"
  File /r "${BUILD}\jre-windows\*.*"
  SetOutPath "$INSTDIR"
  CreateDirectory "$PROFILE\.dsviz"
  FileOpen $0 "$PROFILE\.dsviz\jdk-path.txt" w
  FileWriteUTF16LE /BOM $0 "$INSTDIR\jdk"
  FileClose $0
  DetailPrint "Java 21: הותקנה ב-$INSTDIR\jdk"
SectionEnd

Section "תיקיית תרגילים מוכנה (במסמכים)" SEC_PROJECT
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
SectionEnd

Section "תוסף ל-VS Code" SEC_VSCODE
  ${If} $CODE == ""
    DetailPrint "VS Code: לא נמצא — דילוג"
  ${Else}
    DetailPrint "VS Code: מתקין את התוסף..."
    nsExec::ExecToLog '"$SYSDIR\cmd.exe" /c ""$CODE" --install-extension "$INSTDIR\${VSIX}" --force"'
    Pop $0
    ${If} $0 == "0"
      DetailPrint "VS Code: התוסף הותקן"
      ; קיצור דרך בשולחן העבודה שפותח את תיקיית התרגילים ב-VS Code
      ${GetParent} $CODE $1
      ${GetParent} $1 $1
      ${If} ${FileExists} "$1\Code.exe"
      ${AndIf} ${FileExists} "${PROJECT_DIR}\.project"
        CreateShortcut "$DESKTOP\תרגילי מבני נתונים (VS Code).lnk" "$1\Code.exe" '"${PROJECT_DIR}"' "$INSTDIR\icon.ico"
        DetailPrint "VS Code: נוצר קיצור דרך בשולחן העבודה"
      ${EndIf}
    ${Else}
      DetailPrint "VS Code: ההתקנה נכשלה (קוד $0)"
    ${EndIf}
  ${EndIf}
SectionEnd

Section /o "השלמה אוטומטית ל-Java ב-VS Code (רשות, מהאינטרנט)" SEC_JAVAPACK
  ; לא נחוץ להרצה (התוסף שלנו מריץ בעצמו עם ה-Java שבהתקנה). רץ ברקע — ההתקנה לא מחכה לו
  ${If} $CODE != ""
    DetailPrint "VS Code: Extension Pack for Java יותקן ברקע (אם יש אינטרנט)"
    Exec '"$SYSDIR\cmd.exe" /c start "" /min "$SYSDIR\cmd.exe" /c ""$CODE" --install-extension vscjava.vscode-java-pack""'
  ${EndIf}
SectionEnd

Section "תוסף ל-Eclipse" SEC_ECLIPSE
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
    MessageBox MB_YESNO|MB_ICONQUESTION "לא נמצא Eclipse באופן אוטומטי.$\r$\n$\r$\nלבחור את התיקייה של Eclipse (התיקייה שבה נמצא eclipse.exe)?" /SD IDNO IDNO eclipse_done
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

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_PROJECT} "תיקייה DataStructures במסמכים עם תרגיל לדוגמה, דוגמאות פתורות וכל ההגדרות — נפתחת גם ב-VS Code וגם ב-Eclipse."
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_VSCODE} "סרגל 'מבני נתונים' ב-VS Code: תרגיל חדש, הוספה לקוד והרצה ויזואלית."
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_JDK} "Java 21 מובנית: מקמפלת ומריצה את התרגילים ב-VS Code, כולל ההרצה הוויזואלית. לא צריך להוריד כלום."
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_JAVAPACK} "רשות: התוסף של Microsoft להשלמה אוטומטית ובדיקת שגיאות בזמן ההקלדה. לא נחוץ להרצה. מותקן ברקע מהאינטרנט."
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_ECLIPSE} "תפריט 'מבני נתונים' וכפתורים ב-Eclipse. מותקן לכל Eclipse שנמצא במחשב."
!insertmacro MUI_FUNCTION_DESCRIPTION_END

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
