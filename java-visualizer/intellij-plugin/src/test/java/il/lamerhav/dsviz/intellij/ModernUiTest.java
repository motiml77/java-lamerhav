package il.lamerhav.dsviz.intellij;

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;

import com.intellij.notification.NotificationType;
import com.intellij.ui.JBColor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

/**
 * המראה החדש: ההודעה הקצרה אחרי שהתרגיל נוצר, השלבים של הבנייה, המקום של התשובה, קטעי קוד בתוך עברית,
 * והחלון האחד של ההודעות — מימין לשמאל, והכפתורים בסדר של עברית. עם ‎-Ddsviz.shots=<תיקייה> — גם צילומי מסך (בלי מסך).
 */
public class ModernUiTest extends BasePlatformTestCase {

    // ------------------------------------------------------------------ ההודעה אחרי שהתרגיל נוצר

    public void testWordsLineLabelsOnly() {
        assertEquals("סעיף ד: עונים במילים", Created.wordsLine(Collections.singletonList(new String[] { "ד", "מהי הסיבוכיות? public static int f(int x) { return x; }" })));
        assertEquals("סעיפים ג, ד: עונים במילים", Created.wordsLine(Arrays.asList(new String[] { "ג'", "a" }, new String[] { "סעיף ד", "b" })));
        assertEquals("", Created.wordsLine(Collections.<String[]>emptyList()));
        assertEquals("סעיף אחד: עונים במילים", Created.wordsLine(Collections.singletonList(new String[] { "", "x" })));
    }

    static Submitter.Outcome outcome(int tasks, String solutions, String... others) {
        Submitter.Outcome o = new Submitter.Outcome();
        o.tasks = tasks;
        o.folder = "ex01_stacks";
        o.solutionStatus = solutions;
        for (String l : others) o.others.add(new String[] { l, "כתבו את הפעולה f(x); ונמקו.\npublic static int f(int x) {\n    return x;\n}" });
        o.files.add("/tmp/ex01_stacks/StackTargil.java");
        return o;
    }

    public void testCreatedNotificationIsShortAndRtl() {
        Created.Text t = Created.text(outcome(3, "ok", "ג", "ד"), "א", "#646C7A");
        assertEquals("\u200F✓ התרגיל נוצר", t.title);
        assertEquals(NotificationType.INFORMATION, t.type);
        assertTrue(t.html, t.html.startsWith("<div dir='rtl' style='text-align:right'>"));
        assertTrue(t.html, t.html.contains("3 סעיפים · הסמן בסעיף א"));
        assertTrue(t.html, t.html.contains("<small>בתיקייה ex01_stacks</small>"));
        assertTrue(t.html, t.html.contains("סעיפים ג, ד: עונים במילים"));
        assertTrue(t.html, t.html.contains("💡 ליד כל סעיף — הצעה לפתרון"));
        assertFalse("בלי הטקסט של הסעיפים (ובלי הקוד שבו) בהודעה", t.html.contains("public static") || t.html.contains("ונמקו"));
        assertTrue("\"הצגה\" — רק כשיש סעיפים במילים", t.show);
        assertFalse(t.jump);
        assertTrue("קצר: עד 4 שורות", t.html.split("<div>").length - 1 <= 4);

        Created.Text none = Created.text(outcome(2, ""), "", "#646C7A");
        assertFalse(none.show);
        assertFalse("בלי הצעות — בלי 💡", none.html.contains("💡"));
        assertTrue(none.html.contains("2 סעיפים · הסמן בסעיף הראשון"));
        assertTrue(Created.text(outcome(1, "ok"), "", "#000").html.contains("סעיף אחד · הסמן במקום התשובה"));

        Submitter.Outcome bad = outcome(2, "ok", "ג");
        bad.errors.add("Program.java:12: cannot find symbol   [x]");
        bad.errors.add("Program.java:14: ';' expected");
        Created.Text w = Created.text(bad, "א", "#646C7A");
        assertEquals(NotificationType.WARNING, w.type);
        assertEquals("\u200Fהתרגיל נוצר · יש 2 שגיאות קומפילציה", w.title);
        assertTrue(w.jump);
        assertTrue(w.show);
        assertEquals(Arrays.asList("Program.java", "12"), Arrays.asList(Created.firstError(bad.errors)));
        assertNull(Created.firstError(Collections.singletonList("משהו אחר")));

        Created.Text zero = Created.text(outcome(0, "", "א", "ב"), "", "#646C7A");
        assertEquals("\u200F✓ התרגיל נוצר", zero.title);
        assertTrue(zero.html.contains("אין סעיפים בקוד"));
        assertTrue(zero.html.contains("סעיפים א, ב: עונים במילים"));
    }

    public void testFirstLabelFromMarker() {
        String code = "class T {\n    static int f() {\n        // ====‫כאן עונים סעיף ב'‬====\n        \n    }\n}\n";
        assertEquals("ב", Created.firstLabel(code, 3));
        assertEquals("", Created.firstLabel("class T {\n}\n", 1));
    }

    // ------------------------------------------------------------------ המקום של התשובה

    public void testAnswerRanges() {
        String s = DsvizPluginTest.STUBS;
        List<AnswerMarks.Range> r = AnswerMarks.ranges(s);
        assertEquals(2, r.size());
        assertEquals("א", r.get(0).label);
        assertEquals("ב", r.get(1).label);
        String body = s.substring(r.get(0).start, r.get(0).end);
        assertTrue(body, body.startsWith("        // ====כאן עונים סעיף א'===="));
        assertTrue("עד סוף השורה הריקה (לפני הסוגר)", s.substring(r.get(0).end).startsWith("\n    }"));
        // תשובה בסעיף א — רק ב נשאר
        String answered = s.replaceFirst("(סעיף א'====\n)        \n", "$1        return 1;\n");
        List<AnswerMarks.Range> r2 = AnswerMarks.ranges(answered);
        assertEquals(1, r2.size());
        assertEquals("ב", r2.get(0).label);
        // עם סימני כיוון (כמו שהמחולל כותב בשביל Eclipse), וסעיף יחיד
        assertEquals(1, AnswerMarks.ranges("void f() {\n    // ====‫כאן כותבים את התשובה‬====\n}\n").size());
        // קוד לפני הסימון — כבר יש תשובה
        assertEquals(0, AnswerMarks.ranges("void f() {\n    int x = 1;\n    // ====כאן עונים סעיף א'====\n}\n").size());
        assertEquals(0, AnswerMarks.ranges("// ====כאן עונים סעיף א'====\n").size());
    }

    // ------------------------------------------------------------------ השלבים של הבנייה

    public void testStageMapping() {
        assertEquals(0, SubmitDialog.stageOf("מכין את הקבצים…"));
        assertEquals(1, SubmitDialog.stageOf("שולח את התרגיל… (בדרך כלל עד דקה)"));
        assertEquals(2, SubmitDialog.stageOf("בונה את התרגיל…"));
        assertEquals(3, SubmitDialog.stageOf("בודק שהקוד מתקמפל…"));
        assertEquals(4, SubmitDialog.stageOf("בודק שההצעות לפתרון נותנות את התוצאה הנכונה…"));
        assertEquals(-1, SubmitDialog.stageOf("יש שגיאות קומפילציה — מבקש תיקון…"));
        assertEquals(-1, SubmitDialog.stageOf("בודק שוב…"));
        assertEquals(-1, SubmitDialog.stageOf("ההצעה לפתרון לא נתנה בדיוק את הפלט הצפוי — מבקש תיקון…"));
        assertEquals("יש שגיאות קומפילציה, מבקש תיקון…", SubmitDialog.subLine("יש שגיאות קומפילציה — מבקש תיקון…"));
        assertFalse(SubmitDialog.subLine("משהו — אחר").contains("—"));
        assertEquals("השרת לא ענה.", SubmitDialog.shortError("השרת לא ענה.\nפרטים"));
        assertTrue(SubmitDialog.shortError("א ".repeat(100)).endsWith("…"));
    }

    public void testBuildViewStates() throws Exception {
        SubmitDialog.Build b = new SubmitDialog.Build();
        b.reset(items());
        assertEquals("●○○○○", b.states());
        b.progress("שולח את התרגיל… (בדרך כלל עד דקה)");
        b.progress("בונה את התרגיל…");
        b.progress("בודק שהקוד מתקמפל…");
        assertEquals("✓✓✓●○", b.states());
        b.progress("יש שגיאות קומפילציה — מבקש תיקון…");
        assertEquals("יש שגיאות קומפילציה, מבקש תיקון…", b.subText());
        b.elapsed(12);
        assertEquals("12 שנ'", b.time());
        b.progress("שולח את התרגיל…");
        assertEquals("לא חוזרים אחורה", "✓✓✓●○", b.states());
        b.finished();
        assertEquals("✓✓✓✓✓", b.states());
        b.reset(items());
        b.progress("שולח את התרגיל…");
        b.fail("אין חיבור לשרת.");
        assertEquals("✓✕○○○", b.states());
        b.stopTimer();
        assertRtl(b);
    }

    private static List<SubmitDialog.Item> items() throws Exception {
        List<SubmitDialog.Item> l = new ArrayList<SubmitDialog.Item>();
        File img = File.createTempFile("page", ".png");
        BufferedImage page = new BufferedImage(600, 420, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 600, 420);
        g.setColor(new Color(0x30, 0x30, 0x30));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        g.drawString("שאלה 1", 420, 50);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        for (int i = 0; i < 9; i++) g.fillRect(60 + (i % 3) * 30, 90 + i * 34, 480 - (i % 3) * 30, 9);
        g.dispose();
        ImageIO.write(page, "png", img);
        l.add(new SubmitDialog.Item(img, "צילום מסך 1.png"));
        File pdf = File.createTempFile("bagrut", ".pdf");
        Files.write(pdf.toPath(), new byte[2048]);
        l.add(new SubmitDialog.Item(pdf, "bagrut-2025.pdf"));
        File doc = File.createTempFile("exer", ".docx");
        Files.write(doc.toPath(), new byte[2048]);
        l.add(new SubmitDialog.Item(doc, "targil-3.docx"));
        return l;
    }

    // ------------------------------------------------------------------ עברית עם קוד

    public void testCodeSegmentsInHebrew() {
        List<String[]> s = Modern.segments("כתבו את הפעולה f(x); שמחזירה את הסכום.");
        assertEquals(3, s.size());
        assertEquals("f(x);", s.get(1)[0]);
        assertEquals("code", s.get(1)[1]);
        assertEquals("כתבו את הפעולה ‪f(x);‬ שמחזירה את הסכום.", Modern.bidi("כתבו את הפעולה f(x); שמחזירה את הסכום."));
        assertEquals("(למשל ‪isEmpty()‬)", Modern.bidi("(למשל isEmpty())"));
        assertEquals("הפעולה מקבלת ‪Queue<Integer> q‬ ומחזירה ‪int‬.", Modern.bidi("הפעולה מקבלת Queue<Integer> q ומחזירה int."));
        assertEquals("סעיף א' — 3 נקודות", Modern.bidi("סעיף א' — 3 נקודות"));
        assertEquals("שם של תוכנה — לא בגופן קוד", "name", Modern.segments("לכבות את DSViz?").get(1)[1]);
        assertEquals("לכבות את \u202ADSViz\u202C?", Modern.bidi("לכבות את DSViz?"));
        assertTrue(Modern.codeLine("    public static int f(int x) {"));
        assertTrue(Modern.codeLine("}"));
        assertFalse(Modern.codeLine("כתבו את f(x)"));
        assertFalse(Modern.codeLine("3, 4, 5"));
    }

    public void testOthersPopupIsCompactRtlWithLtrCode() {
        JComponent c = Created.othersPanel(outcome(2, "", "ג", "ד").others);
        assertTrue("עד 300px גובה", c.getPreferredSize().height <= JBUI.scale(300));
        assertRtl(c);
        List<Component> ltr = new ArrayList<Component>();
        collect(c, ltr);
        assertFalse("הקוד משמאל לימין", ltr.isEmpty());
        for (Component k : ltr) assertTrue(k.getComponentOrientation().isLeftToRight());
    }

    private static void collect(Component c, List<Component> ltr) {
        if (c instanceof JComponent && ((JComponent) c).getClientProperty(Modern.LTR) != null) {
            ltr.add(c);
            return;
        }
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) collect(k, ltr);
    }

    // ------------------------------------------------------------------ החלון האחד של ההודעות — מימין לשמאל

    /** כל רכיב מימין לשמאל, חוץ מקוד (מסומן LTR) */
    static void assertRtl(Component c) {
        if (c instanceof JComponent && ((JComponent) c).getClientProperty(Modern.LTR) != null) return;
        assertFalse(c.getClass().getName() + " משמאל לימין", c.getComponentOrientation().isLeftToRight());
        if (c instanceof JTextPane) {
            Object dir = ((JTextPane) c).getDocument().getProperty(java.awt.font.TextAttribute.RUN_DIRECTION);
            assertEquals("טקסט — כיוון מימין לשמאל", java.awt.font.TextAttribute.RUN_DIRECTION_RTL, dir);
        }
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) assertRtl(k);
    }

    static List<Notice.Card> cards(int n) {
        List<Notice.Card> l = new ArrayList<Notice.Card>();
        String[][] e = {
            { "Button.java · שורה 42", "השם לא מוכר — בדקו את האיות (אותיות גדולות וקטנות), שהמשתנה הוגדר, ושיש import", "int total = sum(q) + cnt;" },
            { "Button.java · שורה 57", "חסר ; בסוף הפקודה", "q.insert(x)" },
            { "Program.java · שורה 12", "אי התאמה בטיפוסים: אי אפשר להכניס String למשתנה מטיפוס int", "int n = s.getValue();" },
            { "Program.java · שורה 20", "חסרה הוראת return — הפעולה צריכה להחזיר ערך בכל מסלול (גם אחרי לולאה או if)", "" },
            { "Program.java · שורה 31", "unreachable statement", "" },
            { "Program.java · שורה 33", "something else", "" },
        };
        for (int i = 0; i < n; i++) l.add(new Notice.Card(e[i % e.length][0], e[i % e.length][1], e[i % e.length][2], () -> { }));
        return l;
    }

    public void testCompileErrorNoticeIsRtlWithCards() {
        Notice.Spec s = VisualRunner.compileErrors(cards(6));
        assertEquals("6 שגיאות — מתקנים ומריצים שוב", s.heading);
        assertEquals("ועוד 2 בחלון Build", s.more);
        assertEquals("מעבר לשגיאה הראשונה", s.primary);
        assertEquals("סגירה", s.secondary);
        assertEquals("שגיאה אחת — מתקנים ומריצים שוב", VisualRunner.compileErrors(cards(1)).heading);
        Notice n = new Notice(getProject(), s);
        try {
            assertRtl(n.body());
            List<JLabel> labels = new ArrayList<JLabel>();
            labels(n.body(), labels);
            int links = 0;
            boolean more = false;
            for (JLabel l : labels) {
                if (l.getText() != null && l.getText().contains("· שורה")) links++;
                if ("ועוד 2 בחלון Build".equals(l.getText())) more = true;
            }
            assertEquals("4 כרטיסים לכל היותר", 4, links);
            assertTrue(more);
            JPanel footer = n.footer();
            assertFalse("הכפתורים מימין לשמאל", footer.getComponentOrientation().isLeftToRight());
            JButton first = (JButton) footer.getComponent(0);
            assertEquals("הראשי ראשון (מימין)", "מעבר לשגיאה הראשונה", first.getText());
            assertTrue(((Modern.Button) first).primary);
            assertFalse(((Modern.Button) footer.getComponent(1)).primary);
            if (n.getRootPane() != null) assertSame(first, n.getRootPane().getDefaultButton());
        } finally {
            n.close(0);
        }
    }

    private static void labels(Component c, List<JLabel> out) {
        if (c instanceof JLabel) out.add((JLabel) c);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) labels(k, out);
    }

    public void testSayUsesTheOneRtlDialog() {
        Notice.Spec s = Say.spec(Modern.Kind.WARN, "שליחת תרגיל — אין חיבור לאינטרנט", "בודקים את החיבור ומנסים שוב.");
        assertEquals("שליחת תרגיל", s.window);
        assertEquals("אין חיבור לאינטרנט", s.heading);
        Notice.Spec q = Say.spec(Modern.Kind.QUESTION, "הרצה ויזואלית", "בקובץ T.java אין main.\n\nלהוסיף main קצרה?");
        assertEquals("בקובץ T.java אין main.", q.heading);
        assertEquals("להוסיף main קצרה?", q.text);
        for (Notice.Spec spec : new Notice.Spec[] { s, q.buttons("להוסיף main", "לא"), Actions.powerOff() }) {
            Notice n = new Notice(getProject(), spec);
            try {
                assertRtl(n.body());
                assertFalse(n.footer().getComponentOrientation().isLeftToRight());
                if (n.getContentPane() != null) assertFalse(n.getContentPane().getComponentOrientation().isLeftToRight());
                assertEquals(spec.primary, ((JButton) n.footer().getComponent(0)).getText());
            } finally {
                n.close(0);
            }
        }
    }

    public void testSubmitDialogFormIsRtl() throws Exception {
        File dir = Files.createTempDirectory("dsviz-sd").toFile();
        SubmitDialog d = new SubmitDialog(getProject(), Arrays.asList(new ExerciseDialog.Target(dir, "src")));
        try {
            assertRtl(d.deck());
            assertEquals("שליחה", ((JButton) d.footer().getComponent(0)).getText());
            assertNotNull("בלי קבצים — לא שולחים", d.doValidate());
        } finally {
            d.close(0);
        }
    }

    /** כל החלונות של התוסף על אותו בסיס: הכפתורים מימין לשמאל, הראשי ראשון (מימין) */
    public void testAllDialogsHaveRtlFooters() throws Exception {
        File dir = Files.createTempDirectory("dsviz-ex").toFile();
        String code = "public class T {\n    public static void main(String[] a) {\n        \n    }\n}\n";
        Modern.Dialog[] ds = {
            new ExerciseDialog(getProject(), Arrays.asList(new ExerciseDialog.Target(dir, "src")), "stack"),
            new InsertDialog(getProject(), null, code, code.indexOf("        \n"), "        "),
            new Help.ChooserDialog(getProject()),
        };
        String[] primary = { "יצירת התרגיל", "הוספה לקוד", "צפייה" };
        for (int i = 0; i < ds.length; i++) {
            try {
                JPanel f = ds[i].footer();
                assertFalse(ds[i].getClass().getSimpleName(), f.getComponentOrientation().isLeftToRight());
                assertEquals(primary[i], ((JButton) f.getComponent(0)).getText());
                assertTrue(((Modern.Button) f.getComponent(0)).primary);
            } finally {
                ds[i].close(0);
            }
        }
    }

    // ------------------------------------------------------------------ צילומי מסך (רק עם ‎-Ddsviz.shots=<תיקייה>)

    public void testScreenshots() throws Exception {
        String out = System.getProperty("dsviz.shots");
        if (out == null || out.isEmpty()) return;
        File dir = new File(out);
        dir.mkdirs();
        // החלקת טקסט כמו ב-IntelliJ: מ-UIManager, לפני שהרכיבים נוצרים — כך המדידה והציור באותו מצב
        Object aa = javax.swing.UIManager.get(RenderingHints.KEY_TEXT_ANTIALIASING);
        javax.swing.UIManager.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        float scale = com.intellij.ui.scale.JBUIScale.scale(1f);
        com.intellij.ui.scale.JBUIScale.setUserScaleFactor(2f);   // כמו מסך HiDPI — צילומים חדים
        try {
            shots(dir);
        } finally {
            com.intellij.ui.scale.JBUIScale.setUserScaleFactor(scale);
            javax.swing.UIManager.put(RenderingHints.KEY_TEXT_ANTIALIASING, aa);
        }
    }

    private void shots(File dir) throws Exception {
        for (boolean dark : new boolean[] { false, true }) {
            JBColor.setDark(dark);
            try {
                String sfx = dark ? "-dark" : "";
                // 1. הבנייה, באמצע: שלושה קבצים, שני שלבים הסתיימו, השלב השלישי עכשיו (12 שנ'), תיקון בשורה קטנה
                SubmitDialog.Build b = new SubmitDialog.Build();
                b.reset(items());
                b.progress("שולח את התרגיל… (בדרך כלל עד דקה)");
                b.progress("בונה את התרגיל…");
                b.progress("בודק שהקוד מתקמפל…");
                b.progress("יש שגיאות קומפילציה — מבקש תיקון…");
                b.elapsed(12);
                b.stopTimer();
                save(window("שליחת תרגיל", b, footer(new Modern.Button("ביטול", false, () -> { })), 540), new File(dir, "build-midway" + sfx + ".png"));
                // 2. הבנייה נכשלה: שורה אדומה ו"נסו שוב"
                SubmitDialog.Build f = new SubmitDialog.Build();
                f.reset(items());
                f.progress("שולח את התרגיל… (בדרך כלל עד דקה)");
                f.fail("השרת לא ענה בזמן. בדקו את החיבור ונסו שוב.");
                save(window("שליחת תרגיל", f, footer(new Modern.Button("נסו שוב", true, () -> { }), new Modern.Button("ביטול", false, () -> { })), 540),
                        new File(dir, "build-error" + sfx + ".png"));
                // 3. החלונית של "הצגה"
                save(frame(Created.othersPanel(outcome(2, "", "ג", "ד").others)), new File(dir, "words-popup" + sfx + ".png"));
                // 4. ההודעה (בלון) — אותו HTML שנשלח ל-IntelliJ
                Submitter.Outcome ok = outcome(3, "ok", "ד");
                save(balloon(Created.text(ok, "א", "#" + com.intellij.ui.ColorUtil.toHex(Modern.MUTED)), Created.SHOW), new File(dir, "notification" + sfx + ".png"));
                Submitter.Outcome bad = outcome(3, "ok");
                bad.errors.add("Program.java:12: cannot find symbol");
                bad.errors.add("Program.java:14: ';' expected");
                save(balloon(Created.text(bad, "א", "#" + com.intellij.ui.ColorUtil.toHex(Modern.MUTED)), Created.JUMP), new File(dir, "notification-errors" + sfx + ".png"));
                // 5. שגיאות הקומפילציה של ההרצה הוויזואלית
                Notice.Spec ce = VisualRunner.compileErrors(cards(6));
                save(window("הרצה ויזואלית", Notice.content(ce, r -> { }),
                        footer(new Modern.Button(ce.primary, true, () -> { }), new Modern.Button(ce.secondary, false, () -> { })), 0),
                        new File(dir, "compile-errors" + sfx + ".png"));
                // 6. אין אינטרנט / כיבוי
                Notice.Spec off = new Notice.Spec(Modern.Kind.WARN, "שליחת תרגיל", "אין חיבור לאינטרנט")
                        .text("כדי לשלוח תרגיל צריך אינטרנט. בודקים את החיבור ומנסים שוב.\nהרצה ויזואלית, הוספת מבנה נתונים ודוגמאות פתורות עובדים גם בלי אינטרנט.")
                        .buttons("נסו שוב", "סגירה");
                save(window("שליחת תרגיל", Notice.content(off, r -> { }),
                        footer(new Modern.Button("נסו שוב", true, () -> { }), new Modern.Button("סגירה", false, () -> { })), 0), new File(dir, "offline" + sfx + ".png"));
                Notice.Spec pw = Actions.powerOff();
                save(window("DSViz", Notice.content(pw, r -> { }),
                        footer(new Modern.Button("כיבוי", true, () -> { }), new Modern.Button("ביטול", false, () -> { })), 0), new File(dir, "power-off" + sfx + ".png"));
                // 8. העורך: המקום של התשובה בצהוב (סעיף א כבר נענה — הצהוב שלו נעלם; ב ו-ג עוד מחכים)
                if (!dark) editorShot(new File(dir, "editor-answer.png"));
                // 7. הטופס עם קבצים
                File src = Files.createTempDirectory("dsviz-sd").toFile();
                SubmitDialog d = new SubmitDialog(getProject(), Arrays.asList(new ExerciseDialog.Target(src, "src  (dsviz-demo)")));
                try {
                    for (SubmitDialog.Item it : items()) d.add(it.file, it.name);
                    save(window("שליחת תרגיל", d.deck(), footer(new Modern.Button("שליחה", true, () -> { }), new Modern.Button("ביטול", false, () -> { })), 540),
                            new File(dir, "form" + sfx + ".png"));
                } finally {
                    d.close(0);
                }
            } finally {
                JBColor.setDark(false);
            }
        }
    }

    /** עורך אמיתי של IntelliJ (בלי מסך) עם הסימון של AnswerMarks */
    private void editorShot(File f) throws Exception {
        String code = "package ex01_q;\n\nimport unit4.collectionsLib.*;\n\npublic class Tasks {\n\n"
                + "    public static int sumEven(Queue<Integer> q) {\n        // ====כאן עונים סעיף א'====\n        int sum = 0;\n        return sum;\n    }\n\n"
                + "    public static boolean has(Queue<Integer> q, int x) {\n        // ====כאן עונים סעיף ב'====\n        \n    }\n\n"
                + "    public static int count(Queue<Integer> q) {\n        // ====כאן עונים סעיף ג'====\n        \n    }\n}\n";
        com.intellij.psi.PsiFile file = myFixture.addFileToProject("ex01_q/Tasks.java", code);
        AnswerMarks marks = AnswerMarks.get(getProject());
        marks.track(new File(file.getVirtualFile().getParent().getPath()));
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        com.intellij.openapi.editor.Editor e = myFixture.getEditor();
        marks.attach(e.getDocument());
        e.getSettings().setLineNumbersShown(true);
        e.getCaretModel().moveToOffset(code.indexOf("        \n    }\n\n    public static int count") + 8);
        JComponent c = e.getComponent();
        c.setPreferredSize(new Dimension(JBUI.scale(560), e.getLineHeight() * 23));
        try {
            save(frame(c), f);
        } catch (Throwable t) {
            System.out.println("[shots] editor: " + t);
        }
    }

    private static JPanel footer(JButton... buttons) {
        JPanel p = Modern.panel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING, JBUI.scale(8), 0));
        p.setBorder(JBUI.Borders.empty(4, 20, 16, 20));
        for (JButton b : buttons) p.add(b);
        p.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        return p;
    }

    /** חלון מדומה: שורת כותרת, התוכן והכפתורים */
    private static JComponent window(String title, JComponent body, JComponent south, int width) {
        JPanel w = new JPanel(new java.awt.BorderLayout());
        w.setBackground(Modern.PANEL);
        JLabel t = new JLabel(title, javax.swing.SwingConstants.CENTER);
        t.setFont(Modern.font(12, Font.PLAIN));
        t.setForeground(Modern.INK_2);
        t.setOpaque(true);
        t.setBackground(new JBColor(0xEBECF0, 0x3C3F41));
        t.setBorder(JBUI.Borders.empty(7, 0));
        w.add(t, java.awt.BorderLayout.NORTH);
        w.add(body, java.awt.BorderLayout.CENTER);
        if (south != null) w.add(south, java.awt.BorderLayout.SOUTH);
        w.setBorder(javax.swing.BorderFactory.createLineBorder(new JBColor(0xC9CCD6, 0x1E1F22)));
        if (width > 0) w.setPreferredSize(new Dimension(JBUI.scale(width), w.getPreferredSize().height));
        return w;
    }

    private static JComponent frame(JComponent c) {
        JPanel w = new JPanel(new java.awt.BorderLayout());
        w.setBackground(Modern.PANEL);
        w.add(c);
        w.setBorder(javax.swing.BorderFactory.createLineBorder(new JBColor(0xC9CCD6, 0x1E1F22)));
        return w;
    }

    /** בלון מדומה של IntelliJ: הכותרת, התוכן (אותו HTML, ב-JEditorPane של IntelliJ) והפעולה */
    private static JComponent balloon(Created.Text t, String action) {
        JPanel w = new JPanel(new java.awt.BorderLayout(0, JBUI.scale(6)));
        w.setBackground(new JBColor(0xFFFFFF, 0x2B2D30));
        w.setBorder(javax.swing.BorderFactory.createCompoundBorder(new Modern.Round(new JBColor(0xD3D5DB, 0x4E5157), 8, 0, 0), JBUI.Borders.empty(10, 14)));
        JLabel title = new JLabel(t.title);
        title.setFont(Modern.font(13, Font.BOLD));
        title.setForeground(t.type == NotificationType.WARNING ? Modern.INK : Modern.INK);
        title.setIcon(t.type == NotificationType.WARNING ? com.intellij.icons.AllIcons.General.Warning : com.intellij.icons.AllIcons.General.Information);
        title.setIconTextGap(JBUI.scale(8));
        w.add(title, java.awt.BorderLayout.NORTH);
        JEditorPane p = new JEditorPane();
        p.setEditorKit(com.intellij.util.ui.HTMLEditorKitBuilder.simple());
        p.setText("<html><body>" + t.html + "</body></html>");
        p.setEditable(false);
        p.setOpaque(false);
        p.setFont(Modern.font(13, Font.PLAIN));
        p.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        p.setForeground(Modern.INK);
        p.setSize(new Dimension(JBUI.scale(330), Short.MAX_VALUE));
        p.setPreferredSize(new Dimension(JBUI.scale(330), p.getPreferredSize().height));
        w.add(p, java.awt.BorderLayout.CENTER);
        JLabel a = new JLabel(action);
        a.setForeground(Modern.ACCENT);
        a.setFont(Modern.font(13, Font.PLAIN));
        JPanel ar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
        ar.setOpaque(false);
        ar.add(a);
        w.add(ar, java.awt.BorderLayout.SOUTH);
        JPanel bg = new JPanel(new java.awt.BorderLayout());
        bg.setBackground(new JBColor(0xF0F1F4, 0x1E1F22));
        bg.setBorder(JBUI.Borders.empty(14));
        bg.add(w);
        return bg;
    }

    private static void layout(Component c) {
        if (c instanceof Container) {
            ((Container) c).doLayout();
            for (Component k : ((Container) c).getComponents()) layout(k);
        }
    }

    static void save(JComponent c, File f) throws Exception {
        Dimension d = c.getPreferredSize();
        c.setSize(d);
        layout(c);
        layout(c);
        int s = 1;   // בלי הגדלה: הטקסט נמדד ומצויר באותו קנה מידה (אחרת טקסט מיושר לימין נחתך)
        BufferedImage img = new BufferedImage(d.width * s, d.height * s, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(s, s);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(UIUtil.getPanelBackground());
        g.fillRect(0, 0, d.width, d.height);
        c.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", f);
    }
}
