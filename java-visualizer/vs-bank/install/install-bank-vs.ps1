# "מאגר תרגילים" ל-Visual Studio (C#) — גרסת בדיקה: מעתיק את dsviz-bank-vs.jar ליד ההתקנה של DSViz (%LOCALAPPDATA%\DSViz\lab).
# בלי קיצורים בשולחן העבודה (הקיצור היחיד של DSViz שם הוא "מודלים חישוביים"): המאגר והשאלה נפתחים מהכפתורים "מאגר שאלות"
# ו"השאלה" בסרגל של DSViz ב-Visual Studio, ומתפריט התחלה (Rider). הקיצורים מגרסאות קודמות ("מאגר תרגילים C#",
# "השאלה — מאגר תרגילים C#") נמחקים בהתקנה / בעדכון. עם -Remove — מוחק גם את ה-jar.
# לא נוגע בהתקנה של DSViz; העדכון האוטומטי לא מוחק את הקבצים האלה.
param([switch]$Remove)
$ErrorActionPreference = 'Stop'
$dsviz = Join-Path $env:LOCALAPPDATA 'DSViz'
$lab = Join-Path $dsviz 'lab'
$jar = Join-Path $lab 'dsviz-bank-vs.jar'
$desk = [Environment]::GetFolderPath('Desktop')
$docs = [Environment]::GetFolderPath('MyDocuments')
$csDir = Join-Path $docs 'DataStructures-CS'
$links = @{ 'מאגר תרגילים C#' = 'bank'; 'השאלה — מאגר תרגילים C#' = 'question' }
foreach ($n in $links.Keys) { Remove-Item -LiteralPath (Join-Path $desk "$n.lnk") -Force -ErrorAction SilentlyContinue }   # מגרסה קודמת
if ($Remove) {
    Remove-Item $lab -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host 'הוסר.'
    exit 0
}
$javaw = Join-Path $dsviz 'jdk\bin\javaw.exe'
if (-not (Test-Path $javaw) -or -not (Test-Path (Join-Path $dsviz 'dsviz.jar'))) { Write-Host "DSViz לא מותקן ($dsviz) — מתקינים קודם את DSViz-Setup.exe עם חבילת C#."; exit 1 }
# המאגר קודם, לפני כל בדיקה אחרת: בלעדיו הכפתור "מאגר שאלות" לא מופיע בסרגל של Visual Studio
New-Item -ItemType Directory -Force $lab | Out-Null
Copy-Item (Join-Path $PSScriptRoot 'dsviz-bank-vs.jar') $jar -Force
# תיקיית התרגילים חסרה (Documents חסום / הועבר): המאגר מותקן בכל זאת, והוא יוצר את התיקייה כשפותחים תרגיל
if (-not (Test-Path $csDir)) { Write-Host "הערה: אין עדיין תיקיית תרגילים של C# ($csDir)." }
# Rider (בלי סרגל של DSViz): המאגר והשאלה מתפריט התחלה, בתיקייה "מבני נתונים" של ההתקנה (כמו "תרגיל חדש ב-C#")
$menu = Join-Path ([Environment]::GetFolderPath('Programs')) 'מבני נתונים'
if (Test-Path -LiteralPath $menu) {
    $ws = New-Object -ComObject WScript.Shell
    foreach ($n in $links.Keys) {
        # WScript.Shell לא שומר קיצור עם שם בעברית — שומרים בשם זמני באנגלית ומשנים את השם
        $tmp = Join-Path $menu "dsviz-bank-$($links[$n]).lnk"
        $final = Join-Path $menu "$n.lnk"
        $s = $ws.CreateShortcut($tmp)
        $s.TargetPath = $javaw
        # התוספת קודם: יש בה גרסה חדשה של חלקים משותפים (CsSolution, הכניסה), גם על התקנה ישנה. יחסי לתיקיית העבודה ובלי נתיב
        # התרגילים: שם משתמש בעברית לא עובר בשורת הפקודה של java (BankVs קורא את project-cs-path.txt)
        $s.Arguments = "-cp `"lab\dsviz-bank-vs.jar;dsviz.jar`" il.lamerhav.dsviz.BankVs $($links[$n])"
        $s.WorkingDirectory = $dsviz
        $ico = Join-Path $dsviz 'icon.ico'
        if (Test-Path $ico) { $s.IconLocation = $ico }
        $s.Save()
        Remove-Item -LiteralPath $final -Force -ErrorAction SilentlyContinue
        Move-Item -LiteralPath $tmp $final -Force
    }
}
Write-Host 'הותקן: ב-Visual Studio — הכפתורים "מאגר שאלות" ו"השאלה" בסרגל של DSViz; ב-Rider — מתפריט התחלה ("מבני נתונים").'
