package il.lamerhav.dsviz;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * בדיקה על Visual Studio אמיתי (toolbar-e2e.ps1): אותן קריאות כמו של "הוספת מבנה נתונים" — VsDte.editor() מחזיר את הקובץ הפעיל,
 * שורת הסמן והטקסט; VsDte.insert(...) מכניס טקסט בשורה (מחליף שורה ריקה / מתחת לשורה) כפעולה אחת של Undo.
 *   VsDteCheck editor                       — מדפיס FILE / LINE / LEN
 *   VsDteCheck insert <קובץ> <שורה> <0|1> <קובץ-טקסט>
 */
public class VsDteCheck {
    public static void main(String[] a) throws Exception {
        if (a[0].equals("editor")) {
            VsDte.Editor e = VsDte.editor();
            if (e == null) {
                System.out.println("NULL");
                System.exit(2);
            }
            System.out.println("FILE " + e.file);
            System.out.println("LINE " + e.line);
            System.out.println("LEN " + e.text.length());
        } else {
            String text = new String(Files.readAllBytes(Paths.get(a[4])), StandardCharsets.UTF_8);
            boolean ok = VsDte.insert(a[1], Integer.parseInt(a[2]), a[3].equals("1"), text);
            System.out.println(ok ? "OK" : "FAIL");
            System.exit(ok ? 0 : 3);
        }
    }
}
