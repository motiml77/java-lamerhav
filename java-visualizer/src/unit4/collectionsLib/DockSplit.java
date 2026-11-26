package unit4.collectionsLib;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * צומת אחד של סידור החלקים (Dock) על המסך: שני צדדים וביניהם קו גרירה — first משמאל / למעלה, second מימין / למטה
 * (תמיד כך, גם בחלון מימין לשמאל). כל צד הוא חלק (כרטיס) או DockSplit נוסף.
 *
 * הגודל נקבע רק מ-node.ratio (החלק של הצד הראשון), כמו ב-Splitter של IntelliJ וב-grid של VS Code:
 * - גוררים קו: שני הצדדים שלו משתנים, וכל מה שבתוך כל צד מתכווץ / מתרחב לפי היחסים שלו (החלקים שומרים על היחס ביניהם);
 * - לכל חלק יש גודל קטן ביותר (getMinimumSize שלו; לפחות MIN_W × MIN_H) ולצד שיש בו כמה חלקים — הסכום שלהם, כך שאף חלק לא נעלם,
 *   והקו נעצר רק כשכל החלקים בצד שמתכווץ כבר בגודל הקטן ביותר;
 * - שינוי גודל החלון שומר על אותם יחסים; לחיצה כפולה על קו מחזירה אותו לגודל הרגיל.
 *
 * בכוונה לא JSplitPane: מ-Java 24 ‏JSplitPane.setComponentOrientation מחליף בין שני הצדדים, וכשקובעים לחלון כיוון
 * מימין לשמאל (applyComponentOrientation) הצדדים התהפכו ו-getLeftComponent() נהיה null — ואז Swing מסרב לגרור את הקו
 * (כך זה היה ב-Java 25, למשל ה-Java שמגיע עם Eclipse). כאן אין תלות בזה: הסידור פיזי, והכיוון של החלון לא משנה אותו.
 */
final class DockSplit extends JComponent {
    private static final long serialVersionUID = 1L;

    /** עובי קו הגרירה (הרווח בין שני חלקים) */
    static final int SASH = 9;
    /** הגודל הקטן ביותר של חלק: הכותרת שלו ועוד שורה-שתיים של תוכן (הרוחב — לפחות; חלק עם כותרת ארוכה צריך יותר — Viewer.Card) */
    static final int MIN_W = 130, MIN_H = 72;

    final Dock node;
    final Component first, second;
    final Sash sash;
    /** אחרי שהמשתמש שינה גודל (סוף גרירה / לחיצה כפולה) — שומרים את הסידור */
    private final Runnable changed;

    DockSplit(Dock node, Component first, Component second, Runnable changed) {
        this.node = node;
        this.first = first;
        this.second = second;
        this.changed = changed;
        sash = new Sash();
        setOpaque(false);
        setFocusable(false);
        add(first);
        add(sash);
        add(second);
    }

    /** הגודל הקטן ביותר של צד, בכיוון של הקו */
    private int min(Component c) {
        Dimension m = c.getMinimumSize();
        return node.across ? m.width : m.height;
    }

    /** המקום של שני הצדדים יחד (בלי הקו) */
    private int total() {
        return Math.max(0, (node.across ? getWidth() : getHeight()) - SASH);
    }

    /** הגודל של הצד הראשון עכשיו (לפי ratio, בתוך המינימומים) */
    int firstSize() {
        return Dock.firstSize(total(), node.ratio, min(first), min(second));
    }

    @Override
    public void doLayout() {
        int w = getWidth(), h = getHeight(), total = total(), a = firstSize();
        if (node.across) {
            place(first, 0, 0, a, h);
            sash.setBounds(a, 0, SASH, h);
            place(second, a + SASH, 0, total - a, h);
        } else {
            place(first, 0, 0, w, a);
            sash.setBounds(0, a, w, SASH);
            place(second, 0, a + SASH, w, total - a);
        }
    }

    /**
     * שינוי בתוך חלק (או גרירת הקו) מסדר מחדש רק את מה שכאן ומטה — הגודל של DockSplit עצמו לא תלוי בתוכן שלו
     * (כמו JSplitPane ו-JScrollPane)
     */
    @Override
    public boolean isValidateRoot() {
        return true;
    }

    /** רק צד שעדיין כאן: חלק שהוגדל לכל החלון עבר למקום אחר, והסידור הישן נשאר (מוסתר) עד שבונים אותו מחדש */
    private void place(Component c, int x, int y, int w, int h) {
        if (c.getParent() == this) c.setBounds(x, y, w, h);
    }

    @Override
    public Dimension getMinimumSize() {
        return sum(first.getMinimumSize(), second.getMinimumSize());
    }

    @Override
    public Dimension getPreferredSize() {
        return sum(first.getPreferredSize(), second.getPreferredSize());
    }

    private Dimension sum(Dimension a, Dimension b) {
        return node.across ? new Dimension(a.width + SASH + b.width, Math.max(a.height, b.height))
                : new Dimension(Math.max(a.width, b.width), a.height + SASH + b.height);
    }

    /** המשתמש גורר את הקו: הצד הראשון יהיה pos פיקסלים (נעצר במינימום של כל צד) — מיד, תוך כדי הגרירה */
    void dragTo(int pos) {
        double r = Dock.dragRatio(pos, total(), node.ratio, min(first), min(second));
        if (r == node.ratio) return;
        node.ratio = r;
        revalidate();
        repaint();
    }

    /** לחיצה כפולה על הקו: חזרה לגודל הרגיל שלו */
    void reset() {
        node.ratio = node.def;
        revalidate();
        repaint();
        changed.run();
    }

    /** קו הגרירה עצמו: רקע החלון, שלוש נקודות באמצע; במעבר עכבר / גרירה — קו כחול לכל האורך */
    final class Sash extends JComponent {
        private static final long serialVersionUID = 1L;
        private boolean hover, dragging;
        /** איפה בתוך הקו המשתמש תפס אותו — כדי שהקו יישאר מתחת לעכבר */
        private int grab;
        /** המקום וה-ratio בתחילת הגרירה: מי שגרר וחזר בדיוק לאותו מקום — נשאר עם אותו ratio בדיוק (ולא עם העיגול לפיקסל) */
        private int startSize;
        private double startRatio;

        Sash() {
            setFocusable(false);
            setCursor(Cursor.getPredefinedCursor(node.across ? Cursor.E_RESIZE_CURSOR : Cursor.S_RESIZE_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    dragging = true;
                    grab = node.across ? e.getX() : e.getY();
                    startSize = firstSize();
                    startRatio = node.ratio;
                    repaint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!dragging || !DockSplit.this.isShowing()) return;
                    // לפי המקום על המסך (ולא בתוך הקו): הקו עצמו זז תוך כדי הגרירה
                    Point o = DockSplit.this.getLocationOnScreen();
                    dragTo((node.across ? e.getXOnScreen() - o.x : e.getYOnScreen() - o.y) - grab);
                    if (firstSize() == startSize) node.ratio = startRatio;
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!dragging) return;
                    dragging = false;
                    repaint();
                    changed.run();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) reset();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.BG);
            g.fillRect(0, 0, w, h);
            if (hover || dragging) {
                g.setColor(Theme.ACCENT);
                if (node.across) g.fillRoundRect(w / 2 - 1, 6, 3, Math.max(0, h - 12), 3, 3);
                else g.fillRoundRect(6, h / 2 - 1, Math.max(0, w - 12), 3, 3, 3);
            } else {
                g.setColor(new Color(0xB4BCC8));
                for (int i = -1; i <= 1; i++) {
                    if (node.across) g.fillOval(w / 2 - 1, h / 2 + i * 6 - 1, 3, 3);
                    else g.fillOval(w / 2 + i * 6 - 1, h / 2 - 1, 3, 3);
                }
            }
            g.dispose();
        }
    }
}
