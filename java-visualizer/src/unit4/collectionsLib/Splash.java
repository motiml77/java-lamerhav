package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Arc2D;
import java.io.File;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * חלון "טוען..." עם ספינר: נפתח מיד כשלוחצים "הרצה ויזואלית" (בתהליך שמפעיל), ונסגר כשחלון ההרצה האמיתי מוכן.
 * החלון האמיתי מסמן שהוא מוכן ביצירת קובץ ({@link #READY_PROPERTY}).
 */
final class Splash {
    private Splash() {}

    static final String READY_PROPERTY = "dsviz.ready";

    /** פותח את החלון (אם יש מסך) ומחזיר את קובץ הסימון שהילד צריך ליצור; null אם לא פותחים */
    static File show() {
        if (GraphicsEnvironment.isHeadless() || Boolean.getBoolean("dsviz.nosplash")) return null;
        try {
            final File ready = File.createTempFile("dsviz-ready", ".flag");
            ready.delete();
            ready.deleteOnExit();
            SwingUtilities.invokeLater(new Runnable() {
                public void run() { open(ready); }
            });
            return ready;
        } catch (Exception e) {
            return null;
        }
    }

    private static void open(final File ready) {
        try {
            final JWindow w = new JWindow();
            final Spinner spinner = new Spinner();
            w.setContentPane(spinner);
            w.setAlwaysOnTop(true);
            w.setSize(new Dimension(380, 130));
            w.setLocationRelativeTo(null);
            w.setVisible(true);
            final long started = System.currentTimeMillis();
            Timer t = new Timer(40, null);
            t.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    spinner.tick();
                    // נסגר כשהחלון האמיתי מוכן (או אחרי 60 שניות — כדי לא להישאר תקוע לעולם)
                    if (ready.exists() || System.currentTimeMillis() - started > 60000) {
                        ((Timer) e.getSource()).stop();
                        w.dispose();
                    }
                }
            });
            t.start();
        } catch (Throwable ignored) {
            // אין מסך / בעיית גרפיקה — ממשיכים בלי חלון טעינה
        }
    }

    /** כרטיס לבן מעוגל עם ספינר מסתובב וטקסט */
    private static final class Spinner extends JComponent {
        private int angle;

        void tick() {
            angle = (angle + 12) % 360;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(new Color(0xF1F3F7));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(0xDFE3EA));
            g.drawRect(0, 0, w - 1, h - 1);
            // ספינר: מסלול אפור וקשת כחולה מסתובבת
            int d = 44, x = w - 24 - d, y = (h - d) / 2;
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0xDCE3F0));
            g.drawOval(x, y, d, d);
            g.setColor(new Color(0x2F6FED));
            g.draw(new Arc2D.Double(x, y, d, d, -angle, 100, Arc2D.OPEN));
            // טקסט (מיושר לימין, ליד הספינר)
            Font f = new Font("Dialog", Font.BOLD, 17);
            g.setFont(f);
            g.setColor(new Color(0x1D2433));
            String title = "טוען את ההרצה הוויזואלית...";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(title, x - 18 - fm.stringWidth(title), h / 2 - 2);
            g.setFont(new Font("Dialog", Font.PLAIN, 13));
            g.setColor(new Color(0x667085));
            String sub = "רגע, זה לוקח כמה שניות";
            g.drawString(sub, x - 18 - g.getFontMetrics().stringWidth(sub), h / 2 + 22);
            g.dispose();
        }
    }
}
