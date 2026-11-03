package il.lamerhav.dsviz.intellij;

import java.awt.RenderingHints;
import java.io.File;
import java.util.Arrays;

import javax.swing.JComponent;

import com.intellij.notification.NotificationType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * צילומי מסך (בלי מסך) של ההודעות החדשות למקרים המוזרים — רק עם ‎-Pshots=<תיקייה>. בלי זה הבדיקה רק בונה את ההודעות
 * ובודקת שהן בעברית ומימין לשמאל.
 */
public class EdgeShotsTest extends BasePlatformTestCase {

    private static Notice.Spec[] specs() {
        Notice.Spec noJdk = new Notice.Spec(Modern.Kind.WARN, VisualRunner.TITLE, "לפרויקט לא נבחרה Java (JDK)")
                .text("בלי JDK אי אפשר לקמפל את הקוד.\nבוחרים JDK בתפריט File, בחלון Project Structure, ומריצים שוב.")
                .buttons("פתיחת ההגדרות", "סגירה");
        Notice.Spec hebrewClass = VisualRunner.checkEncoding("ex01.שלום", Arrays.asList("/lib/dsviz.jar"), null, java.nio.charset.Charset.forName("windows-1252"));
        Notice.Spec hebrewDir = VisualRunner.checkEncoding("Main", Arrays.asList("/lib/dsviz.jar", "C:\\Users\\דני\\IdeaProjects\\targil\\out"), null,
                java.nio.charset.Charset.forName("windows-1252"));
        Notice.Spec indexing = Say.spec(Modern.Kind.INFO, VisualRunner.TITLE, "IntelliJ עוד סורק את הפרויקט.\nבקובץ Tasks.java אין main — נסו שוב בעוד כמה שניות.");
        return new Notice.Spec[] { noJdk, hebrewClass, hebrewDir, indexing };
    }

    public void testEdgeNoticesAreHebrewRtl() {
        for (Notice.Spec s : specs()) {
            assertNotNull(s);
            EdgeSupport.assertStudentNotice(s);
        }
    }

    public void testScreenshots() throws Exception {
        File dir = EdgeSupport.shots();
        if (dir == null) return;
        Object aa = javax.swing.UIManager.get(RenderingHints.KEY_TEXT_ANTIALIASING);
        javax.swing.UIManager.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        float scale = com.intellij.ui.scale.JBUIScale.scale(1f);
        com.intellij.ui.scale.JBUIScale.setUserScaleFactor(2f);
        try {
            String[] names = { "edge-no-jdk", "edge-hebrew-class-name", "edge-hebrew-folder", "edge-still-indexing" };
            Notice.Spec[] specs = specs();
            for (int i = 0; i < specs.length; i++) save(specs[i], new File(dir, names[i] + ".png"));
            // שליחת תרגיל לתיקייה שאי אפשר לכתוב בה
            SubmitDialog.Build b = new SubmitDialog.Build();
            File img = com.intellij.openapi.util.io.FileUtil.createTempFile("dsviz-edge-page", ".png", true);
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(60, 40, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", img);
            b.reset(Arrays.asList(new SubmitDialog.Item(img, "דף התרגיל.png")));
            b.progress("שולח את התרגיל… (בדרך כלל עד דקה)");
            b.progress("בונה את התרגיל…");
            b.fail("אי אפשר לשמור בתיקייה src — אין הרשאה, או שהיא נמחקה.");
            b.stopTimer();
            ModernUiTest.save(ModernUiTest.window("שליחת תרגיל", b, ModernUiTest.footer(new Modern.Button("נסו שוב", true, () -> { }),
                    new Modern.Button("ביטול", false, () -> { })), 540), new File(dir, "edge-submit-readonly.png"));
            // לחיצה כפולה על "הרצה ויזואלית" — בלון קטן במקום הרצה שנייה
            Created.Text t = new Created.Text();
            t.title = VisualRunner.TITLE;
            t.html = Dsviz.rtlHtml("ההרצה הוויזואלית כבר מתחילה — רגע…");
            t.type = NotificationType.INFORMATION;
            ModernUiTest.save(ModernUiTest.balloon(t, ""), new File(dir, "edge-double-run.png"));
        } finally {
            com.intellij.ui.scale.JBUIScale.setUserScaleFactor(scale);
            javax.swing.UIManager.put(RenderingHints.KEY_TEXT_ANTIALIASING, aa);
        }
    }

    private static void save(Notice.Spec s, File f) throws Exception {
        JComponent body = Notice.content(s, r -> { });
        JComponent footer = s.secondary == null ? ModernUiTest.footer(new Modern.Button(s.primary, true, () -> { }))
                : ModernUiTest.footer(new Modern.Button(s.primary, true, () -> { }), new Modern.Button(s.secondary, false, () -> { }));
        ModernUiTest.save(ModernUiTest.window(s.window, body, footer, 0), f);
    }
}
