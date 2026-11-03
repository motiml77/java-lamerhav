package il.lamerhav.dsviz.intellij;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import il.lamerhav.dsviz.ClassInfo;
import il.lamerhav.dsviz.NameCheck;

/** "הוסף מבנה נתונים": מכניס במקום הסמן מבנה עם נתוני דמה (ומוסיף import unit4.collectionsLib.*; אם חסר) — כמו InsertHandler ב-Eclipse */
public final class StructureInserter {
    private StructureInserter() {}

    static final String TITLE = "הוסף מבנה נתונים";
    private static final Pattern IMPORT = Pattern.compile("import\\s+unit4\\.collectionsLib\\.\\*\\s*;");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+[\\w.]+\\s*;");

    /** איפה ייכנס הקוד ובאיזו הזחה: {מיקום, שורה ריקה (1/0), הזחה} */
    static final class Spot {
        final int insertAt, lineStart, lineEnd;
        final boolean blank;
        final String indent;

        Spot(Document doc, int caret) {
            int line = doc.getLineNumber(Math.min(caret, doc.getTextLength()));
            lineStart = doc.getLineStartOffset(line);
            lineEnd = doc.getLineEndOffset(line);
            String text = doc.getText().substring(lineStart, lineEnd);
            blank = text.trim().isEmpty();
            Matcher m = Pattern.compile("^\\s*").matcher(text);
            indent = blank ? (text.isEmpty() ? "        " : text) : (m.find() ? m.group() : "");
            insertAt = blank ? lineStart : lineEnd;
        }
    }

    /**
     * המקום שבו הקוד ייכנס, בלי לשבור את הקוד של התלמיד: אם סוף השורה של הסמן (או השורה הריקה) נמצא באמצע פקודה שנמשכת
     * לשורה הבאה (‎foo(1,‎), באמצע הערה ‎/* ... *&#47;‎ או באמצע מחרוזת """ — הקוד נכנס מיד אחריהן.
     */
    static Spot spot(PsiFile file, Document doc, int caret) {
        Spot s = new Spot(doc, caret);
        for (int i = 0; i < 20 && file != null; i++) {
            int end = continuesPast(file, s.insertAt);
            if (end < 0 || end > doc.getTextLength()) break;
            s = new Spot(doc, end);
        }
        return s;
    }

    /** הפקודה / ההערה שבתוך גוף הפעולה שהמיקום באמצע שלה — הסוף שלה; אחרת -1 */
    static int continuesPast(PsiFile file, int offset) {
        com.intellij.psi.PsiElement e = file.findElementAt(Math.min(offset, Math.max(0, file.getTextLength() - 1)));
        for (; e != null && !(e instanceof PsiFile); e = e.getParent()) {
            if (e instanceof com.intellij.psi.PsiMember) return -1;   // מחוץ לגוף של פעולה
            if (e.getParent() instanceof com.intellij.psi.PsiCodeBlock) {
                if (e instanceof com.intellij.psi.PsiWhiteSpace) return -1;   // בין פקודות — בדיוק המקום
                com.intellij.openapi.util.TextRange r = e.getTextRange();
                return r.getStartOffset() < offset && offset < r.getEndOffset() ? r.getEndOffset() : -1;
            }
        }
        return -1;
    }

    /** הפעולה מהתפריט: בודק שהסמן בתוך פעולה, פותח את החלון ומכניס */
    public static void run(Project project, Editor editor, PsiFile file) {
        if (editor == null || file == null || !file.getName().endsWith(".java")) {
            Say.info(project, "פתחו קובץ Java ושימו את הסמן במקום שבו רוצים את המבנה (בתוך main או בתוך פעולה).", TITLE);
            return;
        }
        Document doc = editor.getDocument();
        PsiDocumentManager.getInstance(project).commitDocument(doc);
        Spot spot = spot(file, doc, editor.getCaretModel().getOffset());
        if (!NameCheck.insideFunction(doc.getText(), spot.insertAt)) {
            Say.warn(project,
                    "הסמן נמצא מחוץ לפעולה (בגוף המחלקה, או לפני / אחרי המחלקה), ושם אי אפשר לכתוב פקודות.\n\n"
                    + "לחצו בתוך הפעולה main (או בכל פעולה אחרת), בשורה שבה רוצים את המבנה — ואז לחצו שוב על \"הוסף מבנה נתונים\".",
                    TITLE + " — הסמן לא במקום הנכון");
            return;
        }
        InsertDialog d = new InsertDialog(project, null, doc.getText(), spot.insertAt, spot.indent);
        if (!d.showAndGet() || d.code() == null) return;
        int caret = insert(project, doc, file, spot, d.code());
        editor.getCaretModel().moveToOffset(caret);
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        showClassInfo(project, d.chosenClass());
    }

    /** מכניס את הקוד (ואת ה-import אם חסר). מחזיר את מיקום הסמן אחרי הקוד */
    static int insert(Project project, Document doc, PsiFile file, Spot spot, String code) {
        int[] caret = { 0 };
        WriteCommandAction.runWriteCommandAction(project, TITLE, null, () -> {
            if (spot.blank) {
                doc.replaceString(spot.lineStart, spot.lineEnd, code);
                caret[0] = spot.lineStart + code.length();
            } else {
                doc.insertString(spot.lineEnd, "\n" + code);
                caret[0] = spot.lineEnd + 1 + code.length();
            }
            if (!IMPORT.matcher(doc.getText()).find()) {
                Matcher pkg = PACKAGE.matcher(doc.getText());
                String imp = "import unit4.collectionsLib.*;";
                if (pkg.find()) doc.insertString(pkg.end(), "\n\n" + imp);
                else doc.insertString(0, imp + "\n\n");
                caret[0] += imp.length() + 2;
            }
            caret[0] += importAmbiguous(doc, code);
            PsiDocumentManager.getInstance(project).commitDocument(doc);
        }, file);
        return caret[0];
    }

    /** מחלקות של הקורס שיש להן אחות באותו שם ב-java.util */
    private static final String[] CLASH = { "Stack", "Queue" };
    private static final Pattern JAVA_UTIL_ALL = Pattern.compile("(?m)^\\s*import\\s+java\\.util\\.\\*\\s*;");

    /**
     * בקובץ יש גם ‎import java.util.*;‎ (בשביל Scanner) — אז Stack ו-Queue "דו-משמעיים" (java.util.Stack או של הקורס?) והקוד לא מתקמפל.
     * import מפורש של המחלקה של הקורס מכריע (הוא חזק מ-*). לא נוגעים אם התלמיד כבר כתב import מפורש לשם הזה (למשל java.util.Stack).
     * מחזיר כמה תווים נוספו לפני הקוד.
     */
    static int importAmbiguous(Document doc, String code) {
        String text = doc.getText();
        Matcher ours = IMPORT.matcher(text);
        if (!JAVA_UTIL_ALL.matcher(text).find() || !ours.find()) return 0;
        StringBuilder add = new StringBuilder();
        for (String name : CLASH) {
            if (!Pattern.compile("\\b" + name + "\\s*<|\\bnew\\s+" + name + "\\b").matcher(code).find()) continue;
            if (Pattern.compile("(?m)^\\s*import\\s+[\\w.]+\\." + name + "\\s*;").matcher(text).find()) continue;
            add.append("\nimport unit4.collectionsLib.").append(name).append(";");
        }
        if (add.length() == 0) return 0;
        doc.insertString(ours.end(), add.toString());
        return add.length();
    }

    /** תלמיד / מכונית: הודעה קטנה עם מה שיש במחלקה (המאפיינים והפעולות) */
    static void showClassInfo(Project project, String cls) {
        ClassInfo.Info info = cls == null ? null : ClassInfo.ALL.get(cls);
        if (info == null) return;
        StringBuilder b = new StringBuilder("<div dir='rtl' style='text-align:right'>\u200F").append(esc(info.intro)).append("<br><b>יצירה:</b><br>");
        for (String[] c : info.creators) b.append("<code>").append(esc(c[0])).append("</code> — ").append(esc(c[1])).append("<br>");
        b.append("<b>פעולות:</b><br>");
        for (String[] m : info.methods) b.append("<code>").append(esc(m[0])).append("</code> — ").append(esc(m[1])).append("<br>");
        b.append("</div>");
        Dsviz.notify(project, "מה יש במחלקה " + info.title, b.toString(), com.intellij.notification.NotificationType.INFORMATION);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
