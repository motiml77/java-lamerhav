package unit4.collectionsLib;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

import unit4.collectionsLib.Model.Access;
import unit4.collectionsLib.Model.ClassInfo;
import unit4.collectionsLib.Model.Meth;
import unit4.collectionsLib.Model.Obj;

/**
 * הציור של הורשה ופולימורפיזם (הנתונים — Poly): העצם בקופסאות זו בתוך זו עם התגיות של ההפניות והפסים של "לא נראה דרך",
 * הכרטיס הקטן בתוך כרטיס, התגית הצבעונית בתא של מבנה, החלונית "מותר? / מה רץ?", הבדיקה instanceof, הציור הקטן בכרטיס של המרה
 * שנכשלה, ותרשים המחלקות — הקטן (תגית לכל מחלקה) והמלא (UML, בבועה כשהעכבר מעליו).
 * כמו ObjCard: אותה פעולה מודדת (g == null) ומציירת, כך שהגודל תמיד מתאים לציור.
 */
final class PolyPaint {
    private PolyPaint() {}

    static final Color INK = new Color(0x1D2433), MUTED = new Color(0x6B7385), ACCENT = new Color(0x2457C5);
    static final Color RUN_BG = new Color(0xFFF0C2), RUN_LINE = new Color(0xE0A800), OK = new Color(0x1F7A4D), BAD = new Color(0xDC2626);
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    static Font bold(float size) {
        return Theme.font(Theme.boldFamily, Theme.boldStyle, Math.round(size));
    }

    static Font ui(float size) {
        return Theme.font(Theme.family, Font.PLAIN, Math.round(size));
    }

    static Font mono(float size) {
        return Theme.mono.deriveFont(size);
    }

    static Font monoB(float size) {
        return Theme.mono.deriveFont(Font.BOLD, size);
    }

    static int w(JComponent c, String s, Font f) {
        return c.getFontMetrics(f).stringWidth(s);
    }

    private static FontRenderContext frc(Graphics2D g) {
        return g == null ? FRC : g.getFontRenderContext();
    }

    static float rtlW(Graphics2D g, String s, Font f) {
        return s == null || s.isEmpty() ? 0 : CondPaint.rtl(s, f, frc(g)).getAdvance();
    }

    /** טקסט בעברית, מיושר משמאל ל-x (התחלה ויזואלית) */
    static void rtlAt(Graphics2D g, String s, Font f, Color c, float x, float base) {
        if (s == null || s.isEmpty()) return;
        g.setColor(c);
        CondPaint.rtl(s, f, g.getFontRenderContext()).draw(g, x, base);
    }

    private static Stroke stroke(float w, boolean dashed) {
        return dashed ? new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] { 6f, 4f }, 0f) : new BasicStroke(w);
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    // ------------------------------------------------------------ תגית של מחלקה (תא של מבנה, חלונית, תרשים)

    static int chipW(JComponent c, String cls, Font f) {
        return w(c, cls, f) + 12;
    }

    /** שם המחלקה בצבע שלה, על רקע בהיר ומסגרת בצבע (strong — מסגרת עבה: המחלקה שנמצאה / העצם) */
    static void chip(Graphics2D g, String cls, int x, int y, int h, Font f, boolean strong) {
        FontMetrics fm = g.getFontMetrics(f);
        int cw = fm.stringWidth(cls) + 12;
        g.setColor(Poly.bg(cls));
        g.fillRoundRect(x, y, cw, h, 7, 7);
        g.setColor(Poly.ink(cls));
        g.setStroke(stroke(strong ? 2.6f : 1.4f, Poly.dashed(cls)));
        g.drawRoundRect(x, y, cw, h, 7, 7);
        g.setFont(f);
        g.drawString(cls, x + 6, y + (h + fm.getAscent() - fm.getDescent()) / 2);
    }

    // ------------------------------------------------------------ תגית של הפניה: "pet : Animal ▼  רואה רק את החלק של Animal"

    static final int TAG_H = 26;
    private static final Font TAG_F = Theme.mono.deriveFont(Font.BOLD, 14f);

    static int tagW(JComponent c, Poly.Tag t) {
        String txt = t.type == null ? t.name : t.name + " : " + t.type;
        int w = w(c, txt, TAG_F) + 16 + 6 + 12;
        if (t.hint != null) w += 8 + Math.round(rtlW(null, t.hint, ui(12)));
        return w;
    }

    static void tag(Graphics2D g, Poly.Tag t, int x, int y) {
        Color c = t.type == null || Poly.get(t.type) == null ? Poly.GRAY : Poly.ink(t.type);
        FontMetrics fm = g.getFontMetrics(TAG_F);
        String head = t.type == null ? t.name : t.name + " : ";
        int pw = fm.stringWidth(head) + (t.type == null ? 0 : fm.stringWidth(t.type)) + 16, ph = TAG_H - 6;
        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y + 1, pw, ph, 9, 9);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.6f));
        g.drawRoundRect(x, y + 1, pw, ph, 9, 9);
        int base = y + 1 + (ph + fm.getAscent() - fm.getDescent()) / 2;
        g.setFont(TAG_F);
        g.setColor(INK);
        g.drawString(head, x + 8, base);
        if (t.type != null) {
            g.setColor(c);
            g.drawString(t.type, x + 8 + fm.stringWidth(head), base);
        }
        int ax = x + pw + 6, ay = y + TAG_H / 2 - 4;   // ▼ אל הקופסה
        g.setColor(c);
        g.fillPolygon(new int[] { ax, ax + 10, ax + 5 }, new int[] { ay, ay, ay + 9 }, 3);
        if (t.hint != null) rtlAt(g, t.hint, ui(12), MUTED, ax + 18, base);
    }

    // ------------------------------------------------------------ העצם בקופסאות (כרטיס שעומד לבד)

    private static final int PADX = 11, HEAD = 26, ROW = 20;
    private static final Font HEAD_F = bold(16), METH_F = mono(14), SMALL = ui(12);

    /** כל הכרטיס: התגיות של הקופסה החיצונית (ו-Object / לא ידוע) מעליה, ואז הקופסאות. g == null: רק מודדים */
    static Dimension card(JComponent c, Graphics2D g, Poly.Card card, int x, int y, Access acc, ObjCard.Hot hot) {
        List<Poly.Tag> top = new ArrayList<Poly.Tag>(card.outer);
        top.addAll(card.boxes.get(0).tags);
        int tw = 0;
        for (Poly.Tag t : top) tw = Math.max(tw, tagW(c, t));
        Dimension b = box(c, null, card, 0, 0, 0, 0, acc, hot);
        int w = Math.max(b.width, tw), th = top.size() * TAG_H;
        if (g == null) return new Dimension(w, th + b.height);
        for (int i = 0; i < top.size(); i++) tag(g, top.get(i), x, y + i * TAG_H);
        box(c, g, card, 0, x, y + th, Math.max(b.width, Math.min(w, b.width + 40)), acc, hot);
        return new Dimension(w, th + b.height);
    }

    private static String headText(Poly.Box b) {
        return b.cls.name + (b.unbuilt ? "  · עוד לא נבנתה" : "");
    }

    /** קופסה k ומה שבתוכה; width > 0 — מציירים ברוחב הזה (הקופסה הפנימית ממלאת את רוחב החיצונית, כמו בדף) */
    private static Dimension box(JComponent c, Graphics2D g, Poly.Card card, int k, int x, int y, int width, Access acc, ObjCard.Hot hot) {
        Poly.Box b = card.boxes.get(k);
        int headW = w(c, b.cls.name, HEAD_F) + (b.unbuilt ? Math.round(rtlW(null, "  · עוד לא נבנתה", SMALL)) + 6 : 0)
                + (b.cls.isAbstract ? w(c, "abstract ", SMALL) : 0) + 28;
        int ifaceH = b.cls.ifaces.isEmpty() ? 0 : 20;
        Dimension t = b.part.fields.isEmpty() ? new Dimension(0, 0) : ObjCard.table(c, null, b.part, 0, 0, acc, 1, hot);
        int rows = b.methods.size() + (b.more ? 1 : 0), mw = 0;
        for (Meth m : b.methods) mw = Math.max(mw, w(c, m.label(), METH_F) + (m == b.running ? 24 + Math.round(rtlW(null, "רצה עכשיו", bold(13))) + 12 : 0));
        int mh = rows == 0 ? 0 : 8 + rows * ROW;
        int innerH = 0, innerW = 0;
        Dimension in = null;
        List<Poly.Tag> itags = k + 1 < card.boxes.size() ? card.boxes.get(k + 1).tags : null;
        if (itags != null) {
            in = box(c, null, card, k + 1, 0, 0, 0, acc, hot);
            int tw = 0;
            for (Poly.Tag tg : itags) tw = Math.max(tw, tagW(c, tg));
            innerW = Math.max(in.width, tw);
            innerH = 10 + itags.size() * TAG_H + in.height;
        }
        int ifW = 0;
        for (String i : b.cls.ifaces) ifW += w(c, "implements " + i, SMALL) + 16;
        int w = Math.max(Math.max(headW, Math.max(t.width + 2 * PADX, mw + 2 * PADX)), Math.max(innerW + 2 * PADX, Math.max(150, ifW + 2 * PADX)));
        int h = HEAD + ifaceH + 6 + t.height + (mh > 0 ? 4 + mh : 0) + innerH + 10;
        if (g == null) return new Dimension(w, h);
        if (width > 0) w = width;
        Color ink = Poly.ink(b.cls.name);
        Composite keep = g.getComposite();
        if (b.unbuilt) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.42f));
        Shape r = new RoundRectangle2D.Float(x, y, w, h, 14, 14);
        g.setColor(Poly.bg(b.cls.name));
        g.fill(r);
        Shape clip = g.getClip();
        g.clip(r);
        g.setColor(Poly.head(b.cls.name));
        g.fillRect(x, y, w, HEAD);
        g.setClip(clip);
        g.setColor(ink);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(x + 1, y + HEAD, x + w - 1, y + HEAD);
        // הכותרת: שם המחלקה בצבע שלה (abstract — לפניו)
        FontMetrics hm = g.getFontMetrics(HEAD_F);
        int nw = hm.stringWidth(b.cls.name), aw = b.cls.isAbstract ? g.getFontMetrics(SMALL).stringWidth("abstract ") : 0;
        int ux = b.unbuilt ? Math.round(rtlW(g, "  · עוד לא נבנתה", SMALL)) + 6 : 0;
        int hx = x + (w - nw - aw - ux) / 2, hb = y + (HEAD + hm.getAscent() - hm.getDescent()) / 2 + 1;
        if (b.cls.isAbstract) {
            g.setFont(SMALL.deriveFont(Font.ITALIC));
            g.drawString("abstract ", hx, hb);
        }
        g.setFont(HEAD_F);
        g.drawString(b.cls.name, hx + aw, hb);
        if (b.unbuilt) rtlAt(g, "  · עוד לא נבנתה", SMALL, ink, hx + aw + nw + 4, hb);
        int cy = y + HEAD + 4;
        if (ifaceH > 0) {   // ממשקים: תגית קטנה "implements X" מתחת לכותרת (לא קופסה)
            int ix = x + PADX;
            g.setFont(SMALL);
            for (String i : b.cls.ifaces) {
                String s = "implements " + i;
                int iw = g.getFontMetrics().stringWidth(s) + 12;
                g.setColor(new Color(0xEEF0F4));
                g.fillRoundRect(ix, cy, iw, 16, 16, 16);
                g.setColor(MUTED);
                g.drawString(s, ix + 6, cy + 12);
                ix += iw + 4;
            }
            cy += ifaceH;
        }
        cy += 2;
        if (t.height > 0) ObjCard.table(c, g, b.part, x + PADX, cy, acc, 1, hot);
        cy += t.height;
        if (mh > 0) {
            cy += 4;
            g.setColor(alpha(ink, 170));
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 5f, 4f }, 0f));
            g.drawLine(x + 2, cy, x + w - 2, cy);
            cy += 6;
            for (Meth m : b.methods) {
                method(g, m, b, x + PADX, cy, w - 2 * PADX, ink);
                cy += ROW;
            }
            if (b.more) {
                g.setFont(METH_F);
                g.setColor(alpha(ink, 160));
                g.drawString("…", x + PADX, cy + ROW - 6);
                cy += ROW;
            }
            cy += 2;
        }
        if (itags != null) {
            cy += 10;
            for (int i = 0; i < itags.size(); i++) tag(g, itags.get(i), x + PADX, cy + i * TAG_H);
            cy += itags.size() * TAG_H;
            box(c, g, card, k + 1, x + PADX, cy, w - 2 * PADX, acc, hot);
        }
        g.setColor(ink);
        g.setStroke(stroke(b.building ? 3.4f : 2.6f, Poly.dashed(b.cls.name)));
        g.draw(r);
        g.setComposite(keep);
        if (b.hidden && (k == 0 || !card.boxes.get(k - 1).hidden)) hatch(g, r, card.hideNote);
        return new Dimension(w, h);
    }

    /** שורת פעולה בקופסה: רגילה / נדרסת (קו חוצה) / רצה עכשיו (צהוב, "◀ רצה עכשיו") */
    private static void method(Graphics2D g, Meth m, Poly.Box b, int x, int y, int w, Color ink) {
        FontMetrics fm = g.getFontMetrics(METH_F);
        String s = m.label();
        int base = y + ROW - 6;
        if (m == b.running) {
            g.setColor(RUN_BG);
            g.fillRoundRect(x - 5, y + 1, w + 10, ROW - 1, 7, 7);
            g.setColor(RUN_LINE);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(x - 5, y + 1, w + 10, ROW - 1, 7, 7);
            g.setFont(METH_F.deriveFont(Font.BOLD));
            g.setColor(ink);
            g.drawString(s, x, base);
            int ax = x + g.getFontMetrics().stringWidth(s) + 10;
            g.setColor(new Color(0x8A6100));
            g.fillPolygon(new int[] { ax + 8, ax + 8, ax }, new int[] { base - 9, base - 1, base - 5 }, 3);
            rtlAt(g, "רצה עכשיו", bold(13), new Color(0x8A6100), ax + 14, base);
            return;
        }
        boolean struck = b.struck.contains(m);
        g.setFont(METH_F);
        g.setColor(struck ? alpha(ink, 140) : ink);
        g.drawString(s, x, base);
        if (struck) {
            g.setStroke(new BasicStroke(1.4f));
            g.drawLine(x - 1, base - 5, x + fm.stringWidth(s) + 1, base - 5);
        }
    }

    /** פסים בהירים מעל הקופסאות שלא נראות דרך ההפניה — מה שמתחת עדיין קריא — והכיתוב "לא נראה דרך pet" */
    private static void hatch(Graphics2D g, Shape r, String note) {
        Shape clip = g.getClip();
        g.clip(r);
        Rectangle b = r.getBounds();
        g.setColor(new Color(255, 255, 255, 120));
        g.fill(r);
        g.setColor(new Color(214, 219, 232, 150));
        g.setStroke(new BasicStroke(6f));
        for (int k = -b.height; k < b.width; k += 16) g.drawLine(b.x + k, b.y + b.height, b.x + k + b.height, b.y);
        g.setClip(clip);
        if (note != null) {
            Font f = bold(13);
            float tw = rtlW(g, note, f);
            int bx = (int) (b.x + (b.width - tw) / 2), by = b.y + b.height - 9;
            g.setColor(new Color(255, 255, 255, 215));
            g.fillRoundRect(bx - 6, by - 14, Math.round(tw) + 12, 19, 8, 8);
            rtlAt(g, note, f, INK, bx, by);
        }
    }

    // ------------------------------------------------------------ כרטיס בתוך כרטיס (מאפיין) — קופסאות קומפקטיות: רק מאפיינים

    private static final Font C_HEAD = bold(12), C_TAG = bold(11);
    private static final Color C_LABEL = new Color(0x7C3AED);

    static Dimension compact(JComponent c, Graphics2D g, Poly.Card card, List<String> labels, int x, int y, Access acc, ObjCard.Hot hot) {
        String tag = labels == null || labels.isEmpty() ? null : String.join(", ", labels);
        int tagH = tag == null ? 0 : 19;
        Dimension b = cbox(c, null, card, 0, 0, 0, 0, acc, hot);
        int w = Math.max(b.width, tag == null ? 0 : w(c, tag, C_TAG) + 14);
        if (g == null) return new Dimension(w, tagH + b.height);
        if (tag != null) {   // המשתנה שמצביע על העצם הפנימי (כמו בכרטיס הפנימי הרגיל)
            int tw = g.getFontMetrics(C_TAG).stringWidth(tag) + 12;
            g.setColor(Color.WHITE);
            g.fillRoundRect(x, y, tw, 15, 15, 15);
            g.setColor(C_LABEL);
            g.setStroke(new BasicStroke(1.1f));
            g.drawRoundRect(x, y, tw, 15, 15, 15);
            g.setFont(C_TAG);
            g.drawString(tag, x + 6, y + 11);
        }
        cbox(c, g, card, 0, x, y + tagH, b.width, acc, hot);
        return new Dimension(w, tagH + b.height);
    }

    private static Dimension cbox(JComponent c, Graphics2D g, Poly.Card card, int k, int x, int y, int width, Access acc, ObjCard.Hot hot) {
        Poly.Box b = card.boxes.get(k);
        int pad = 5, head = 18;
        Dimension t = b.part.fields.isEmpty() ? new Dimension(0, 0) : ObjCard.table(c, null, b.part, 0, 0, acc, 1, hot);
        Dimension in = k + 1 < card.boxes.size() ? cbox(c, null, card, k + 1, 0, 0, 0, acc, hot) : null;
        int w = Math.max(w(c, b.cls.name, C_HEAD) + 16, Math.max(t.width + 2 * pad, in == null ? 0 : in.width + 2 * pad));
        int h = head + 3 + t.height + (in == null ? 0 : 4 + in.height) + pad;
        if (g == null) return new Dimension(w, h);
        if (width > 0) w = width;
        Color ink = Poly.ink(b.cls.name);
        Shape r = new RoundRectangle2D.Float(x, y, w, h, 9, 9);
        g.setColor(Poly.bg(b.cls.name));
        g.fill(r);
        Shape clip = g.getClip();
        g.clip(r);
        g.setColor(Poly.head(b.cls.name));
        g.fillRect(x, y, w, head);
        g.setClip(clip);
        g.setColor(ink);
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(x, y + head, x + w, y + head);
        g.setFont(C_HEAD);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(b.cls.name, x + (w - fm.stringWidth(b.cls.name)) / 2, y + 13);
        int cy = y + head + 3;
        if (t.height > 0) ObjCard.table(c, g, b.part, x + pad, cy, acc, 1, hot);
        cy += t.height;
        if (in != null) cbox(c, g, card, k + 1, x + pad, cy + 4, w - 2 * pad, acc, hot);
        g.setColor(ink);
        g.setStroke(stroke(1.8f, Poly.dashed(b.cls.name)));
        g.draw(r);
        return new Dimension(w, h);
    }

    // ------------------------------------------------------------ חלונית בראש התצוגה: "מותר? / מה רץ?" ובדיקת instanceof / המרה

    private static final Font P_TEXT = ui(14), P_CODE = mono(13.5f), P_CHIP = bold(13), P_PILL = bold(13);
    private static final int P_ROW = 26;

    /** החלונית: שורה לכל רשימת חלקים (ראו Poly.lines), מימין לשמאל. availW = הרוחב שיש */
    static Dimension panel(JComponent c, Graphics2D g, List<List<String>> lines, int x, int y, int availW) {
        int need = 0;
        for (List<String> l : lines) need = Math.max(need, lineW(c, null, l));
        int w = Math.max(Math.min(availW, Math.max(need + 28, 420)), need + 28), h = 12 + lines.size() * P_ROW + 6;
        if (g == null) return new Dimension(w, h);
        g.setColor(new Color(0xF4F8FF));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(1.6f));
        g.drawRoundRect(x, y, w, h, 12, 12);
        int cy = y + 10;
        for (List<String> l : lines) {
            drawLine(c, g, l, x + w - 14, cy);
            cy += P_ROW;
        }
        return new Dimension(w, h);
    }

    private static int partW(JComponent c, Graphics2D g, String p) {
        if (p.isEmpty()) return 0;
        char k = p.charAt(0);
        if (k == '\u0001' || k == '\u0005') return chipW(c, p.substring(1), P_CHIP);
        if (k == '\u0002') return w(c, p.substring(1), P_CODE);
        if (k == '\u0003') return w(c, p.substring(1), P_PILL) + 20;
        if (k == '\u0004') return w(c, "✓", P_PILL);
        return Math.round(rtlW(g, p, P_TEXT));
    }

    private static int gap(String p) {
        return p.startsWith(",") || p.startsWith(";") || p.startsWith(".") || p.endsWith("-") ? 2 : 7;
    }

    private static int lineW(JComponent c, Graphics2D g, List<String> l) {
        int w = 0;
        for (String p : l) w += partW(c, g, p) + gap(p);
        return w;
    }

    /** חלקים מימין לשמאל, מ-right; y = החלק העליון של השורה */
    private static void drawLine(JComponent c, Graphics2D g, List<String> l, int right, int y) {
        int x = right, h = P_ROW - 6, base = y + 15;
        for (String p : l) {
            int pw = partW(c, g, p);
            x -= pw;
            char k = p.isEmpty() ? ' ' : p.charAt(0);
            if (k == '\u0001' || k == '\u0005') {
                chip(g, p.substring(1), x, y + 1, h - 2, P_CHIP, k == '\u0005');
            } else if (k == '\u0002') {
                g.setFont(P_CODE);
                g.setColor(INK);
                g.drawString(p.substring(1), x, base);
            } else if (k == '\u0003') {
                g.setColor(ACCENT);
                g.fillRoundRect(x, y + 1, pw, h - 2, h - 2, h - 2);
                g.setColor(Color.WHITE);
                g.setFont(P_PILL);
                g.drawString(p.substring(1), x + 10, base);
            } else if (k == '\u0004') {
                g.setFont(P_PILL);
                g.setColor(OK);
                g.drawString("✓", x, base);
            } else if (p.startsWith("→ false") || p.startsWith("→ true")) {
                rtlAt(g, p, bold(14), p.contains("true") ? OK : BAD, x, base);
            } else {
                rtlAt(g, p, P_TEXT, INK, x, base);
            }
            x -= gap(p);
        }
    }

    /** השורות של בדיקה בשורה (instanceof / is, או המרה שתצליח): הקוד, ואז הקופסאות של העצם עם זו שנבדקה מודגשת */
    static List<List<String>> checkLines(Poly.Check ch) {
        List<List<String>> res = new ArrayList<List<String>>();
        List<String> l1 = new ArrayList<String>();
        l1.add("\u0002" + (ch.cast ? "(" + ch.type + ") " + ch.expr : ch.expr + " " + (Syntax.csharp ? "is" : "instanceof") + " " + ch.type));
        res.add(l1);
        List<String> l2 = new ArrayList<String>();
        l2.add("\u0003" + (ch.cast ? "המרה" : "מה יש בעצם?"));
        if (ch.actual == null) {
            l2.add("הערך הוא null — אין עצם, ולכן");
            l2.add("→ false");
            res.add(l2);
            return res;
        }
        l2.add("העצם הוא");
        l2.add("\u0001" + ch.actual);
        l2.add("והקופסאות בו:");
        List<ClassInfo> chain = Poly.chain(ch.actual);
        for (ClassInfo x : chain) l2.add((x.name.equals(ch.type) ? "\u0005" : "\u0001") + x.name);
        if (ch.cast) l2.add("— בעצם יש חלק של " + ch.type + ", ההמרה חוקית");
        else if (ch.result) {
            l2.add("— יש חלק של " + ch.type);
            l2.add("→ true");
        } else {
            l2.add("— אין חלק של " + ch.type);
            l2.add("→ false");
        }
        res.add(l2);
        return res;
    }

    // ------------------------------------------------------------ המרה שנכשלה: הציור בכרטיס השגיאה

    /** עץ קטן: מהשורש עד העצם (◀ העצם), והענף של הטיפוס שביקשו (◀ ביקשת — זה ענף אחר); מחלקה שלא בעץ — שתי תגיות */
    static void castPicture(Graphics2D g, String actual, String asked, int x, int y, int w, int h) {
        Font f = bold(12), lf = ui(11);
        List<String> pa = new ArrayList<String>(), pb = new ArrayList<String>();
        for (ClassInfo c : Poly.chain(actual)) pa.add(c.name);
        for (ClassInfo c : Poly.chain(asked)) pb.add(c.name);
        if (pa.isEmpty()) pa.add(actual);
        if (pb.isEmpty()) pb.add(asked);
        int common = 0;
        while (common < pa.size() && common < pb.size() && pa.get(common).equals(pb.get(common))) common++;
        int rowH = 22, cx = x + 6;
        int levels = Math.max(pa.size(), pb.size()), step = Math.min(rowH, Math.max(16, (h - 4) / Math.max(1, levels)));
        int cy = y;
        // החלק המשותף — בעמודה אחת, ואז שני ענפים זה ליד זה
        for (int i = 0; i < common; i++) {
            chip(g, pa.get(i), cx, cy, step - 4, f, false);
            if (i > 0 || common > 0) {
                g.setColor(MUTED);
                g.setStroke(new BasicStroke(1.2f));
                if (i > 0) g.drawLine(cx + 10, cy - 4, cx + 10, cy);
            }
            cy += step;
        }
        int bx1 = cx + 10, bx2 = cx + Math.max(90, w / 2);
        drawBranch(g, pa, common, bx1, cy, step, f, lf, "◀ העצם", OK, common > 0 ? cx + 10 : -1, cy - 4);
        drawBranch(g, pb, common, bx2, cy, step, f, lf, "◀ ביקשת — זה ענף אחר", BAD, common > 0 ? cx + 10 : -1, cy - 4);
    }

    private static void drawBranch(Graphics2D g, List<String> path, int from, int x, int y, int step, Font f, Font lf, String note, Color nc,
            int fromX, int fromY) {
        int cy = y;
        for (int i = from; i < path.size(); i++) {
            if (i == from && fromX >= 0) {
                g.setColor(MUTED);
                g.setStroke(new BasicStroke(1.2f));
                g.drawLine(fromX, fromY, fromX, cy - 2);
                g.drawLine(fromX, cy - 2, x + 10, cy - 2);
                g.drawLine(x + 10, cy - 2, x + 10, cy);
            } else if (i > from) {
                g.setColor(MUTED);
                g.drawLine(x + 10, cy - 4, x + 10, cy);
            }
            boolean last = i == path.size() - 1;
            chip(g, path.get(i), x, cy, step - 4, f, last);
            if (last) {
                int nx = x + g.getFontMetrics(f).stringWidth(path.get(i)) + 18;
                rtlAt(g, note, lf, nc, nx, cy + step - 9);
            }
            cy += step;
        }
    }

    // ------------------------------------------------------------ תרשים המחלקות

    /** מקום של כל מחלקה בתרשים, והקשרים — אותו סידור לתרשים הקטן ולמלא (רק הגדלים שונים) */
    static final class Layout {
        final Map<String, Rectangle> at = new LinkedHashMap<String, Rectangle>();
        List<Poly.Rel> rels = new ArrayList<Poly.Rel>();
        int width, height;
    }

    /** גודל הקופסה של כל מחלקה (full — UML עם שלושה חלקים; אחרת תגית) */
    private static Dimension nodeSize(JComponent c, ClassInfo ci, boolean full) {
        if (!full) return new Dimension(w(c, ci.name, D_CHIP) + 26, 24);
        int w = w(c, ci.name, D_HEAD) + 28;
        for (String s : fieldLines(ci)) w = Math.max(w, w(c, s, D_MONO) + 20);
        for (String s : methodLines(ci)) w = Math.max(w, w(c, s, D_MONO) + 20 + 50);
        return new Dimension(w, 26 + Math.max(1, fieldLines(ci).size()) * D_ROW + 8 + Math.max(1, methodLines(ci).size()) * D_ROW + 8);
    }

    private static final Font D_CHIP = bold(14), D_HEAD = bold(16), D_MONO = mono(13.5f);
    private static final int D_ROW = 19;

    static List<String> fieldLines(ClassInfo ci) {
        List<String> res = new ArrayList<String>();
        for (String[] f : ci.fields) res.add((f.length > 2 ? f[2] : "-") + " " + f[0] + " : " + f[1]);
        return res;
    }

    static List<String> methodLines(ClassInfo ci) {
        List<String> res = new ArrayList<String>();
        int n = 0;
        for (Meth m : ci.methods) {
            if (m.isStatic) continue;
            if (n++ >= 8) { res.add("…"); break; }
            res.add(m.vis + " " + m.label() + (m.ret == null ? "" : " : " + m.ret));
        }
        return res;
    }

    private static List<Meth> shownMethods(ClassInfo ci) {
        List<Meth> res = new ArrayList<Meth>();
        for (Meth m : ci.methods) if (!m.isStatic && res.size() < 8) res.add(m);
        return res;
    }

    /**
     * הסידור: כל עץ הורשה — מחלקת העל למעלה והיורשות מתחתיה (לפי סדר ההגדרה); מחלקה שלא בעץ ומחוברת בהכלה — ליד המחלקה שהיא
     * מחוברת אליה (משמאל לילד הראשון, מימין לשאר); מחלקות שמחוברות רק בהכלה ביניהן — קבוצה משלהן, אחת ליד השנייה
     */
    static Layout layout(JComponent c, boolean full) {
        Layout L = new Layout();
        L.rels = Poly.relations();
        List<ClassInfo> nodes = Poly.diagramClasses();
        Map<String, Dimension> size = new HashMap<String, Dimension>();
        for (ClassInfo ci : nodes) size.put(ci.name, nodeSize(c, ci, full));
        int hgap = full ? 46 : 26, agap = full ? 70 : 36, vgap = full ? 50 : 24;
        // מי מוצמד למי (מחלקה שלא בעץ ליד מחלקה בעץ / ליד הראשונה בקבוצה)
        Map<String, List<String>> left = new HashMap<String, List<String>>(), right = new HashMap<String, List<String>>();
        List<String> placed = new ArrayList<String>();
        List<String> roots = new ArrayList<String>();
        for (ClassInfo ci : nodes) if (ci.color >= 0 && (ci.sup == null || Poly.get(ci.sup) == null)) roots.add(ci.name);
        List<String> inTrees = new ArrayList<String>();
        for (String r : roots) collect(r, nodes, inTrees);
        List<String> grays = new ArrayList<String>();
        for (ClassInfo ci : nodes) if (!inTrees.contains(ci.name)) grays.add(ci.name);
        List<List<String>> grayGroups = new ArrayList<List<String>>();
        boolean moved = true;
        List<String> anchored = new ArrayList<String>(inTrees);
        while (moved) {
            moved = false;
            for (String gname : new ArrayList<String>(grays)) {
                String host = null;
                for (Poly.Rel r : L.rels) {
                    if (r.inherit) continue;
                    if (r.owner.equals(gname) && anchored.contains(r.part)) host = r.part;
                    else if (r.part.equals(gname) && anchored.contains(r.owner)) host = r.owner;
                    if (host != null) break;
                }
                if (host == null) continue;
                boolean toLeft = inTrees.contains(host) && firstChild(host);
                (toLeft ? left : right).computeIfAbsent(host, k -> new ArrayList<String>()).add(gname);
                anchored.add(gname);
                grays.remove(gname);
                moved = true;
            }
            if (!moved && !grays.isEmpty()) {   // קבוצה חדשה של מחלקות שמחוברות רק בהכלה
                String g0 = grays.remove(0);
                anchored.add(g0);
                grayGroups.add(new ArrayList<String>(java.util.Collections.singletonList(g0)));
                moved = true;
            }
        }
        int x = 0, maxH = 0;
        for (String r : roots) {
            int[] wh = placeTree(r, nodes, size, left, right, x, 0, hgap, agap, vgap, L, placed);
            x += wh[0] + hgap * 2;
            maxH = Math.max(maxH, wh[1]);
        }
        for (List<String> grp : grayGroups) {
            int[] wh = placeBlock(grp.get(0), size, left, right, x, 0, agap, L, placed, 0);
            x += wh[0] + hgap * 2;
            maxH = Math.max(maxH, wh[1]);
        }
        // הזזה כך שאין קואורדינטות שליליות, ומידות
        int minX = Integer.MAX_VALUE, maxX = 0, maxY = 0;
        for (Rectangle r : L.at.values()) {
            minX = Math.min(minX, r.x);
            maxX = Math.max(maxX, r.x + r.width);
            maxY = Math.max(maxY, r.y + r.height);
        }
        if (minX == Integer.MAX_VALUE) minX = 0;
        for (Rectangle r : L.at.values()) r.x -= minX;
        L.width = maxX - minX;
        L.height = maxY;
        return L;
    }

    private static boolean firstChild(String cls) {
        ClassInfo ci = Poly.get(cls);
        if (ci == null || ci.sup == null) return false;
        List<ClassInfo> sib = Poly.children(ci.sup);
        return !sib.isEmpty() && sib.get(0).name.equals(cls);
    }

    private static void collect(String n, List<ClassInfo> nodes, List<String> out) {
        out.add(n);
        for (ClassInfo k : Poly.children(n)) if (contains(nodes, k.name) && !out.contains(k.name)) collect(k.name, nodes, out);
    }

    private static boolean contains(List<ClassInfo> nodes, String n) {
        for (ClassInfo c : nodes) if (c.name.equals(n)) return true;
        return false;
    }

    /** רוחב שורה של מחלקה + המוצמדות לה */
    private static int blockW(String n, Map<String, Dimension> size, Map<String, List<String>> left, Map<String, List<String>> right, int agap) {
        int w = size.get(n).width;
        for (Map<String, List<String>> side : java.util.Arrays.asList(left, right)) {
            List<String> l = side.get(n);
            if (l != null) for (String a : l) w += agap + blockW(a, size, left, right, agap);
        }
        return w;
    }

    private static int subtreeW(String n, List<ClassInfo> nodes, Map<String, Dimension> size, Map<String, List<String>> left,
            Map<String, List<String>> right, int hgap, int agap) {
        int kids = 0, k = 0;
        for (ClassInfo c : Poly.children(n)) {
            if (!contains(nodes, c.name)) continue;
            kids += (k++ > 0 ? hgap : 0) + subtreeW(c.name, nodes, size, left, right, hgap, agap);
        }
        return Math.max(blockW(n, size, left, right, agap), kids);
    }

    private static int[] placeTree(String n, List<ClassInfo> nodes, Map<String, Dimension> size, Map<String, List<String>> left,
            Map<String, List<String>> right, int x, int y, int hgap, int agap, int vgap, Layout L, List<String> placed) {
        int sw = subtreeW(n, nodes, size, left, right, hgap, agap);
        List<String> kids = new ArrayList<String>();
        for (ClassInfo c : Poly.children(n)) if (contains(nodes, c.name)) kids.add(c.name);
        int rowH = size.get(n).height;
        for (String a : attached(n, left, right)) rowH = Math.max(rowH, size.get(a).height);
        int bw = blockW(n, size, left, right, agap);
        int[] blk = placeBlock(n, size, left, right, x + (sw - bw) / 2, y, agap, L, placed, rowH);
        int h = blk[1];
        if (!kids.isEmpty()) {
            int total = 0;
            for (int i = 0; i < kids.size(); i++) total += (i > 0 ? hgap : 0) + subtreeW(kids.get(i), nodes, size, left, right, hgap, agap);
            int kx = x + (sw - total) / 2, ky = y + rowH + vgap, kh = 0;
            for (String k : kids) {
                int[] wh = placeTree(k, nodes, size, left, right, kx, ky, hgap, agap, vgap, L, placed);
                kx += wh[0] + hgap;
                kh = Math.max(kh, wh[1]);
            }
            h = rowH + vgap + kh;
        }
        return new int[] { sw, h };
    }

    private static List<String> attached(String n, Map<String, List<String>> left, Map<String, List<String>> right) {
        List<String> res = new ArrayList<String>();
        if (left.get(n) != null) res.addAll(left.get(n));
        if (right.get(n) != null) res.addAll(right.get(n));
        return res;
    }

    /** מחלקה והמוצמדות לה בשורה אחת, מ-x; rowH > 0 — הכול ממורכז אנכית בגובה הזה */
    private static int[] placeBlock(String n, Map<String, Dimension> size, Map<String, List<String>> left, Map<String, List<String>> right,
            int x, int y, int agap, Layout L, List<String> placed, int rowH) {
        int h = Math.max(rowH, size.get(n).height);
        int cx = x;
        List<String> ls = left.get(n);
        if (ls != null) for (int i = ls.size() - 1; i >= 0; i--) {
            String a = ls.get(i);
            int bw = blockW(a, size, left, right, agap);
            placeBlock(a, size, left, right, cx, y, agap, L, placed, h);
            cx += bw + agap;
        }
        Dimension d = size.get(n);
        L.at.put(n, new Rectangle(cx, y + (h - d.height) / 2, d.width, d.height));
        placed.add(n);
        cx += d.width;
        List<String> rs = right.get(n);
        if (rs != null) for (String a : rs) {
            cx += agap;
            int bw = blockW(a, size, left, right, agap);
            placeBlock(a, size, left, right, cx, y, agap, L, placed, h);
            cx += bw;
        }
        return new int[] { cx - x, h };
    }

    /** הקווים: ירושה (משולש ריק אצל מחלקת העל, אחים חולקים משולש אחד) והכלה (יהלום אצל המחלקה שמכילה, שם המאפיין, * למערך) */
    private static void edges(Graphics2D g, Layout L, int ox, int oy, boolean full) {
        Color line = new Color(0x4A5468);
        g.setStroke(new BasicStroke(full ? 1.6f : 1.4f));
        int tri = full ? 11 : 8;
        // ירושה
        Map<String, List<String>> kids = new LinkedHashMap<String, List<String>>();
        for (Poly.Rel r : L.rels) if (r.inherit && L.at.containsKey(r.owner) && L.at.containsKey(r.part)) kids.computeIfAbsent(r.part, k -> new ArrayList<String>()).add(r.owner);
        for (Map.Entry<String, List<String>> e : kids.entrySet()) {
            Rectangle p = L.at.get(e.getKey());
            int px = ox + p.x + p.width / 2, py = oy + p.y + p.height;
            int top = Integer.MAX_VALUE;
            for (String k : e.getValue()) top = Math.min(top, oy + L.at.get(k).y);
            int bus = py + tri + Math.max(4, (top - py - tri) / 2);
            g.setColor(line);
            g.drawLine(px, py + tri, px, bus);
            int minX = px, maxX = px;
            for (String k : e.getValue()) {
                Rectangle r = L.at.get(k);
                int kx = ox + r.x + r.width / 2;
                minX = Math.min(minX, kx);
                maxX = Math.max(maxX, kx);
                g.drawLine(kx, bus, kx, oy + r.y);
            }
            g.drawLine(minX, bus, maxX, bus);
            Polygon t = new Polygon(new int[] { px, px - tri * 3 / 4, px + tri * 3 / 4 }, new int[] { py, py + tri, py + tri }, 3);
            g.setColor(Color.WHITE);
            g.fillPolygon(t);
            g.setColor(line);
            g.drawPolygon(t);
        }
        // הכלה
        Map<String, Integer> pairs = new HashMap<String, Integer>();
        Font lf = full ? mono(12.5f) : mono(10.5f);
        for (Poly.Rel r : L.rels) {
            if (r.inherit || !L.at.containsKey(r.owner) || !L.at.containsKey(r.part)) continue;
            String key = r.owner.compareTo(r.part) < 0 ? r.owner + "|" + r.part : r.part + "|" + r.owner;
            int n = pairs.containsKey(key) ? pairs.get(key) + 1 : 0;
            pairs.put(key, n);
            Rectangle a = L.at.get(r.owner), b = L.at.get(r.part);
            int ay1 = oy + a.y, ay2 = ay1 + a.height, by1 = oy + b.y, by2 = by1 + b.height;
            int x1, y1, x2, y2;
            int lo = Math.max(ay1, by1), hi = Math.min(ay2, by2);
            if (hi - lo > 8) {   // זו ליד זו — קו אופקי באמצע החפיפה
                int yy = (lo + hi) / 2 + n * 9 - (full ? 6 : 0);
                if (a.x + a.width <= b.x) { x1 = ox + a.x + a.width; x2 = ox + b.x; }
                else { x1 = ox + a.x; x2 = ox + b.x + b.width; }
                y1 = yy;
                y2 = yy;
            } else {   // בשורות שונות — קו מהקצה התחתון / העליון
                boolean down = a.y < b.y;
                x1 = ox + a.x + a.width / 2 + n * 9;
                x2 = ox + b.x + b.width / 2 + n * 9;
                y1 = down ? ay2 : ay1;
                y2 = down ? by1 : by2;
            }
            g.setColor(line);
            g.setStroke(new BasicStroke(full ? 1.6f : 1.4f));
            double ang = Math.atan2(y2 - y1, x2 - x1);
            int dl = full ? 22 : 15, dw = full ? 7 : 5;
            int ex = (int) Math.round(x1 + dl * Math.cos(ang)), ey = (int) Math.round(y1 + dl * Math.sin(ang));
            g.drawLine(ex, ey, x2, y2);
            int mx = (int) Math.round(x1 + dl / 2.0 * Math.cos(ang)), my = (int) Math.round(y1 + dl / 2.0 * Math.sin(ang));
            int qx = (int) Math.round(-dw * Math.sin(ang)), qy = (int) Math.round(dw * Math.cos(ang));
            Polygon d = new Polygon(new int[] { x1, mx + qx, ex, mx - qx }, new int[] { y1, my + qy, ey, my - qy }, 4);
            g.setColor(r.composition ? line : Color.WHITE);
            g.fillPolygon(d);
            g.setColor(line);
            g.drawPolygon(d);
            if (full) {   // שם המאפיין ליד הקו (בתרשים הקטן אין מקום — רק בתרשים המלא)
                g.setFont(lf);
                FontMetrics fm = g.getFontMetrics();
                int lx = (ex + x2) / 2 - fm.stringWidth(r.field) / 2, ly = (ey + y2) / 2 + fm.getAscent() + 3;
                if (Math.abs(y2 - y1) > Math.abs(x2 - x1)) { lx = (ex + x2) / 2 + 5; ly = (ey + y2) / 2 + 4; }
                g.setColor(MUTED);
                g.drawString(r.field, lx, ly);
            }
            if (r.many) {
                g.setFont(bold(full ? 14 : 12));
                g.setColor(line);
                int sx = (int) Math.round(x2 - 12 * Math.cos(ang)) - 4, sy = (int) Math.round(y2 - 12 * Math.sin(ang)) - 4;
                g.drawString("*", sx, sy);
            }
        }
    }

    /** הקופסה המלאה של מחלקה בתרשים (שלושה חלקים); running — הפעולה שרצה עכשיו בה (צהוב, "◀ רצה") */
    private static void umlBox(Graphics2D g, ClassInfo ci, Rectangle r, int ox, int oy, String running) {
        int x = ox + r.x, y = oy + r.y, w = r.width, h = r.height;
        Color ink = Poly.ink(ci.name);
        Shape s = new RoundRectangle2D.Float(x, y, w, h, 10, 10);
        g.setColor(Poly.bg(ci.name));
        g.fill(s);
        Shape clip = g.getClip();
        g.clip(s);
        g.setColor(Poly.head(ci.name));
        g.fillRect(x, y, w, 26);
        g.setClip(clip);
        g.setColor(ink);
        g.setStroke(new BasicStroke(1.6f));
        g.drawLine(x, y + 26, x + w, y + 26);
        g.setFont(ci.isAbstract ? D_HEAD.deriveFont(Font.ITALIC) : D_HEAD);
        FontMetrics hm = g.getFontMetrics();
        g.drawString(ci.name, x + (w - hm.stringWidth(ci.name)) / 2, y + 19);
        g.setFont(D_MONO);
        int cy = y + 26 + 4;
        List<String> fl = fieldLines(ci);
        for (String f : fl) {
            g.setColor(INK);
            g.drawString(f, x + 10, cy + D_ROW - 5);
            cy += D_ROW;
        }
        if (fl.isEmpty()) cy += D_ROW;
        cy += 4;
        g.setColor(alpha(ink, 170));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 5f, 4f }, 0f));
        g.drawLine(x + 1, cy, x + w - 1, cy);
        cy += 4;
        List<String> ml = methodLines(ci);
        List<Meth> ms = shownMethods(ci);
        boolean done = false;
        for (int i = 0; i < ml.size(); i++) {
            String m = ml.get(i);
            boolean run = !done && running != null && i < ms.size()
                    && ("<init>".equals(running) || ".ctor".equals(running) ? ms.get(i).ctor : !ms.get(i).ctor && ms.get(i).name.equals(running));
            if (run) {
                done = true;
                g.setColor(RUN_BG);
                g.fillRoundRect(x + 4, cy + 1, w - 8, D_ROW, 6, 6);
                g.setColor(RUN_LINE);
                g.setStroke(new BasicStroke(1.6f));
                g.drawRoundRect(x + 4, cy + 1, w - 8, D_ROW, 6, 6);
            }
            g.setFont(D_MONO);
            g.setColor(INK);
            g.drawString(m, x + 10, cy + D_ROW - 5);
            if (run) {
                int ax = x + 10 + g.getFontMetrics().stringWidth(m) + 8;
                g.setColor(new Color(0x8A6100));
                g.fillPolygon(new int[] { ax + 7, ax + 7, ax }, new int[] { cy + D_ROW - 13, cy + D_ROW - 5, cy + D_ROW - 9 }, 3);
                rtlAt(g, "רצה", bold(12), new Color(0x8A6100), ax + 11, cy + D_ROW - 5);
            }
            cy += D_ROW;
        }
        g.setColor(ink);
        g.setStroke(stroke(2.2f, Poly.dashed(ci.name)));
        g.draw(s);
    }

    /** תגית הפניה בתרשים: "pet : Animal" (מלא) / "pet ←" (קטן), מימין לקופסה של הטיפוס המוצהר, עם חץ קטן אליה */
    private static void refPill(Graphics2D g, Poly.Live live, Rectangle r, int ox, int oy, boolean full, Layout L) {
        if (live.ref == null || live.refType == null) return;
        Color c = Poly.ink(live.refType);
        Font f = full ? monoB(13.5f) : monoB(11.5f);
        FontMetrics fm = g.getFontMetrics(f);
        String head = full ? live.ref + " : " : live.ref + " ←";
        int pw = fm.stringWidth(head) + (full ? fm.stringWidth(live.refType) : 0) + 14, ph = full ? 22 : 18;
        int x = ox + r.x + r.width + 10, y = oy + r.y + (full ? 6 : (r.height - ph) / 2);
        boolean above = false;
        Rectangle pr = new Rectangle(x - ox - 8, y - oy, pw + 8, ph);
        for (Rectangle o : L.at.values()) if (o != r && o.intersects(pr)) above = true;   // מימין יש מחלקה אחרת — מעל הקופסה
        if (above) {
            x = ox + r.x + r.width - pw / 2;
            y = oy + r.y - ph - 3;
        }
        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y, pw, ph, 9, 9);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, pw, ph, 9, 9);
        if (above) g.fillPolygon(new int[] { x + 8, x + 16, x + 12 }, new int[] { y + ph - 1, y + ph - 1, y + ph + 5 }, 3);
        else g.fillPolygon(new int[] { x - 1, x - 8, x - 1 }, new int[] { y + ph / 2 - 4, y + ph / 2, y + ph / 2 + 4 }, 3);
        g.setFont(f);
        int base = y + (ph + fm.getAscent() - fm.getDescent()) / 2;
        g.setColor(INK);
        g.drawString(head, x + 7, base);
        if (full) {
            g.setColor(c);
            g.drawString(live.refType, x + 7 + fm.stringWidth(head), base);
        }
    }

    private static int refW(JComponent c, Poly.Live live, boolean full) {
        if (live == null || live.ref == null || live.refType == null) return 0;
        Font f = full ? monoB(13.5f) : monoB(11.5f);
        return 20 + w(c, full ? live.ref + " : " + live.refType : live.ref + " ←", f) + 14;
    }

    /** התרשים הקטן: תגית לכל מחלקה, הקשרים, תגית ההפניה; ושורת מקרא קטנה (ובקריאה שנשלחה לפי העצם — "◀ say() רצה ב-Dog") */
    static Dimension smallDiagram(JComponent c, Graphics2D g, Poly.Live live, int x, int y) {
        Layout L = layout(c, false);
        int rw = refW(c, live, false);
        int legendH = 20, w = L.width + rw + 8, h = L.height + 8 + legendH;
        String run = live != null && live.runClass != null && live.runMethod != null && !live.runMethod.startsWith("<") && !live.runMethod.startsWith(".")
                ? live.runMethod + "()" : null;
        w = Math.max(w, 200 + (run == null ? 0 : 160));
        if (g == null) return new Dimension(w, h);
        int ox = x + 4, oy = y + 2;
        edges(g, L, ox, oy, false);
        for (Map.Entry<String, Rectangle> e : L.at.entrySet()) {
            Rectangle r = e.getValue();
            String n = e.getKey();
            g.setColor(Poly.bg(n));
            g.fillRoundRect(ox + r.x, oy + r.y, r.width, r.height, 8, 8);
            g.setColor(Poly.ink(n));
            boolean obj = live != null && n.equals(live.objType);
            g.setStroke(stroke(obj ? 2.6f : 1.8f, Poly.dashed(n)));
            g.drawRoundRect(ox + r.x, oy + r.y, r.width, r.height, 8, 8);
            g.setFont(D_CHIP);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(n, ox + r.x + (r.width - fm.stringWidth(n)) / 2, oy + r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2);
            if (live != null && n.equals(live.refType)) refPill(g, live, r, ox, oy, false, L);
        }
        // מקרא קטן: ▷ ירושה  ◆ ◇ הכלה  (◀ say() רצה ב-Dog)
        int ly = oy + L.height + 8 + 13;
        Font lf = ui(12);
        int right = x + w - 4;
        float tw = rtlW(g, "ירושה", lf);
        g.setColor(INK);
        drawTriangleMark(g, right - 10, ly - 5);
        rtlAt(g, "ירושה", lf, INK, right - 18 - tw, ly);
        int cx = (int) (right - 18 - tw - 22);
        drawDiamond(g, cx - 4, ly - 5, true);
        drawDiamond(g, cx - 18, ly - 5, false);
        float hw = rtlW(g, "הכלה", lf);
        rtlAt(g, "הכלה", lf, INK, cx - 26 - hw, ly);
        if (run != null) {   // "◀ say() רצה ב-Dog" — משפט אחד מימין לשמאל, ושם המחלקה בצבע שלה
            String s = "◀ " + run + " רצה ב-" + live.runClass;
            java.text.AttributedString as = new java.text.AttributedString(s);
            as.addAttribute(java.awt.font.TextAttribute.FONT, bold(12));
            as.addAttribute(java.awt.font.TextAttribute.FOREGROUND, new Color(0x8A6100));
            as.addAttribute(java.awt.font.TextAttribute.FOREGROUND, Poly.ink(live.runClass), s.length() - live.runClass.length(), s.length());
            as.addAttribute(java.awt.font.TextAttribute.RUN_DIRECTION, java.awt.font.TextAttribute.RUN_DIRECTION_RTL);
            TextLayout tl = new TextLayout(as.getIterator(), g.getFontRenderContext());
            tl.draw(g, cx - 26 - hw - 22 - tl.getAdvance(), ly);
        }
        return new Dimension(w, h);
    }

    private static void drawTriangleMark(Graphics2D g, int x, int y) {
        g.setStroke(new BasicStroke(1.3f));
        g.setColor(new Color(0x4A5468));
        Polygon t = new Polygon(new int[] { x + 6, x, x }, new int[] { y, y - 5, y + 5 }, 3);
        g.setColor(Color.WHITE);
        g.fillPolygon(t);
        g.setColor(new Color(0x4A5468));
        g.drawPolygon(t);
    }

    private static void drawDiamond(Graphics2D g, int x, int y, boolean filled) {
        Polygon d = new Polygon(new int[] { x - 6, x, x + 6, x }, new int[] { y, y - 4, y, y + 4 }, 4);
        g.setColor(filled ? new Color(0x4A5468) : Color.WHITE);
        g.fillPolygon(d);
        g.setColor(new Color(0x4A5468));
        g.setStroke(new BasicStroke(1.3f));
        g.drawPolygon(d);
    }

    /** התרשים המלא (בבועה): קופסאות UML, הקשרים עם שמות המאפיינים, תגית ההפניה, הפעולה שרצה — ומקרא מלא */
    static Dimension fullDiagram(JComponent c, Graphics2D g, Poly.Live live, int x, int y) {
        Layout L = layout(c, true);
        int rw = refW(c, live, true);
        List<String> legend = legendLines();
        int legendH = 26 * 3 + 6, w = Math.max(L.width + rw + 16, 640), h = 30 + L.height + 18 + legendH;
        if (g == null) return new Dimension(w, h);
        rtlAt(g, "תרשים המחלקות", bold(15), INK, x + w - 8 - rtlW(g, "תרשים המחלקות", bold(15)), y + 18);
        int ox = x + (w - rw - L.width) / 2, oy = y + 30;
        edges(g, L, ox, oy, true);
        for (Map.Entry<String, Rectangle> e : L.at.entrySet()) {
            ClassInfo ci = Poly.get(e.getKey());
            if (ci == null) continue;
            umlBox(g, ci, e.getValue(), ox, oy, live != null && ci.name.equals(live.runClass) ? live.runMethod : null);
            if (live != null && ci.name.equals(live.refType)) refPill(g, live, e.getValue(), ox, oy, true, L);
        }
        int ly = oy + L.height + 18 + 18;
        int right = x + w - 10;
        Font lf = ui(13), bf = bold(13);
        for (int i = 0; i < legend.size(); i++) {
            String[] parts = legend.get(i).split("\u0001");
            int sx = right - 46, cy = ly + i * 26;
            g.setColor(new Color(0x4A5468));
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(sx, cy - 5, sx + 40, cy - 5);
            if (i == 0) {
                Polygon t = new Polygon(new int[] { sx + 40, sx + 28, sx + 28 }, new int[] { cy - 5, cy - 11, cy + 1 }, 3);
                g.setColor(Color.WHITE);
                g.fillPolygon(t);
                g.setColor(new Color(0x4A5468));
                g.drawPolygon(t);
            } else {
                drawDiamond(g, sx + 8, cy - 5, i == 1);
            }
            float tx = sx - 10;
            if (!parts[0].isEmpty()) {
                float bw = rtlW(g, parts[0], bf);
                rtlAt(g, parts[0], bf, INK, tx - bw, cy);
                tx -= bw + 5;
            }
            float pw = rtlW(g, parts[1], lf);
            rtlAt(g, parts[1], lf, INK, tx - pw, cy);
        }
        return new Dimension(w, h);
    }

    /** המקרא של התרשים המלא: מודגש \u0001 ההסבר */
    static List<String> legendLines() {
        String ex = "";
        for (Poly.Rel r : Poly.relations()) if (r.inherit) { ex = " (" + r.owner + " יורשת מ-" + r.part + ")"; break; }
        List<String> res = new ArrayList<String>();
        res.add("ירושה\u0001— המשולש אצל מחלקת העל" + ex);
        res.add("הכלה\u0001— היהלום אצל המחלקה שמכילה. מלא: יוצרת את העצם בעצמה");
        res.add("\u0001ריק: מקבלת עצם שנוצר בחוץ");
        return res;
    }

    static boolean has(Obj o) {
        return o != null && Poly.inHierarchy(o.type);
    }
}
