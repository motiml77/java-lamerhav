package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;

/**
 * נורת אזהרה צהובה מהבהבת — הציור המשותף: "הלולאה לא רצה אף לא פעם אחת" (LoopSkip, בחלון הקוד) ו"הרקורסיה לא נעצרת?"
 * (בפס של הרקורסיה). ההבהוב לפי השעון (מחזור של שנייה בערך); מי שמצייר מרענן את האזור שלו כל כמה עשרות מילישניות.
 */
final class WarnLight {
    private WarnLight() {}

    static final Color AMBER = new Color(0xB45309), YELLOW = new Color(0xFACC15), PILL = new Color(0xFEF3C7), TEXT = new Color(0x78350F);

    /** 0..1: ההבהוב. ב-1 הנורה דולקת בעוצמה מלאה, ב-0 כמעט כבויה */
    static float blink(long now) {
        String freeze = System.getProperty("dsviz.blinkFreeze");   // לבדיקות: שלב קבוע של ההבהוב (0..1)
        if (freeze != null) {
            try {
                return Math.max(0f, Math.min(1f, Float.parseFloat(freeze)));
            } catch (NumberFormatException ignored) {
                // ההבהוב הרגיל
            }
        }
        return (float) (0.5 + 0.5 * Math.sin(now / 1000.0 * 2 * Math.PI));
    }

    /** הרקע של התגית (צהוב בהיר שנושם קצת עם הנורה) והמסגרת שלה */
    static void pill(Graphics2D g, int x, int y, int w, int h, float blink) {
        g.setColor(new Color(255, 255, 255, 245));
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(new Color(PILL.getRed(), PILL.getGreen(), PILL.getBlue(), 190 + (int) (65 * blink)));
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 120 + (int) (110 * blink)));
        g.setStroke(new BasicStroke(1.4f));
        g.drawRoundRect(x, y, w, h, h, h);
    }

    /** הנורה: הילה זוהרת (גזורה ל-band, כדי שלא תגלוש לשורות שמעל ומתחת) וגוף הנורה, במרכז (cx, cy) ובקוטר lamp */
    static void lamp(Graphics2D g, float cx, float cy, int lamp, float blink, Rectangle band) {
        float glow = lamp * (0.9f + 0.45f * blink);
        Shape clip = g.getClip();
        if (band != null) g.clipRect(band.x, band.y, band.width, band.height);
        g.setPaint(new RadialGradientPaint(cx, cy, glow, new float[] { 0f, 1f }, new Color[] { new Color(250, 204, 21, 40 + (int) (170 * blink)), new Color(250, 204, 21, 0) }));
        g.fill(new Ellipse2D.Float(cx - glow, cy - glow, glow * 2, glow * 2));
        g.setClip(clip);
        int a = 70 + (int) (185 * blink);
        g.setPaint(new RadialGradientPaint(cx - lamp * 0.12f, cy - lamp * 0.15f, lamp * 0.62f, new float[] { 0f, 1f },
                new Color[] { new Color(255, 238, 120, a), new Color(YELLOW.getRed(), YELLOW.getGreen(), YELLOW.getBlue(), a) }));
        g.fill(new Ellipse2D.Float(cx - lamp / 2f, cy - lamp / 2f, lamp, lamp));
        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 200));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Float(cx - lamp / 2f, cy - lamp / 2f, lamp, lamp));
    }
}
