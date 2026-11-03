package il.lamerhav.dsviz.intellij;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.swing.JComponent;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;

import junit.framework.Assert;

/**
 * כלים לבדיקות של "תלמידים עושים דברים מוזרים": תופסים כל הודעה (Notice) וכל בלון (Notification) במקום שייפתחו,
 * ובודקים שהטקסט קצר, בעברית, ובלי אנגלית טכנית (stack trace, Exception, נתיבים של Java).
 */
final class EdgeSupport {
    private EdgeSupport() {}

    /** ההודעות שנפתחו (Notice) והבלונים (Notification) — מאז install */
    static final class Seen {
        final List<Notice.Spec> notices = Collections.synchronizedList(new ArrayList<Notice.Spec>());
        final List<Notification> balloons = Collections.synchronizedList(new ArrayList<Notification>());
        /** מה "לוחצים" בכל הודעה: true — הכפתור הראשי */
        volatile Function<Notice.Spec, Boolean> answer = s -> false;

        Notice.Spec last() {
            Assert.assertFalse("לא נפתחה הודעה", notices.isEmpty());
            return notices.get(notices.size() - 1);
        }

        void clear() {
            notices.clear();
            balloons.clear();
        }
    }

    static Seen install(Project project, Disposable parent) {
        Seen seen = new Seen();
        Notice.interceptor = s -> {
            seen.notices.add(s);
            return seen.answer.apply(s);
        };
        com.intellij.openapi.util.Disposer.register(parent, () -> Notice.interceptor = null);
        project.getMessageBus().connect(parent).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(Notification n) {
                if ("DSViz".equals(n.getGroupId())) seen.balloons.add(n);
            }
        });
        return seen;
    }

    private static final Pattern HEBREW = Pattern.compile("[א-ת]");
    private static final Pattern TECH = Pattern.compile("Exception|\\bat [a-z]+\\.|java\\.(lang|io|nio)|Permission denied|No such file|\\(Native Method\\)|Caused by");

    /** הודעה לתלמיד: יש בה עברית, אין בה אנגלית טכנית, והיא לא ארוכה */
    static void assertStudentText(String what, String text) {
        Assert.assertNotNull(what, text);
        Assert.assertTrue(what + " — בלי עברית: " + text, HEBREW.matcher(text).find());
        Assert.assertFalse(what + " — אנגלית טכנית: " + text, TECH.matcher(text).find());
        Assert.assertTrue(what + " — ארוכה מדי (" + text.length() + "): " + text, text.length() <= 400);
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (l.isEmpty() || HEBREW.matcher(l).find()) continue;
            // שורה בלי עברית — רק אם זה נתיב / שם קובץ / קוד קצר (למשל התיקייה שיש בשמה עברית)
            Assert.assertTrue(what + " — שורה באנגלית: " + l, l.length() <= 120 && (l.contains("/") || l.contains("\\") || l.contains(".")));
        }
    }

    /** הודעה (Notice) קצרה בעברית, מימין לשמאל */
    static void assertStudentNotice(Notice.Spec s) {
        assertStudentText("כותרת", s.heading);
        if (!s.text.isEmpty()) assertStudentText("טקסט", s.heading + "\n" + s.text);
        JComponent body = Notice.content(s, r -> { });
        ModernUiTest.assertRtl(body);
    }

    /** הבלון: עברית, ותוכן עטוף ב-div מימין לשמאל */
    static void assertStudentBalloon(Notification n) {
        Assert.assertTrue(n.getContent(), n.getContent().startsWith("<div dir='rtl'"));
        assertStudentText("בלון", n.getTitle() + "\n" + n.getContent().replaceAll("<[^>]+>", " "));
    }

    /** כל הטקסטים שברכיב (לבדיקה שאין אנגלית) */
    static List<String> texts(Component c) {
        List<String> out = new ArrayList<String>();
        collect(c, out);
        return out;
    }

    private static void collect(Component c, List<String> out) {
        if (c instanceof javax.swing.JLabel && ((javax.swing.JLabel) c).getText() != null) out.add(((javax.swing.JLabel) c).getText());
        if (c instanceof javax.swing.text.JTextComponent) out.add(((javax.swing.text.JTextComponent) c).getText());
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) collect(k, out);
    }

    /** תיקיית הצילומים (‎-Ddsviz.shots), או null */
    static File shots() {
        String s = System.getProperty("dsviz.shots");
        if (s == null || s.isEmpty()) return null;
        File d = new File(s);
        d.mkdirs();
        return d;
    }

    static File root() {
        return new File(System.getProperty("dsviz.root", ".."));
    }
}
