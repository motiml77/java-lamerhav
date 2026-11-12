package il.lamerhav.dsviz.cs;

import java.awt.Dimension;
import java.util.List;

import javax.swing.JComponent;

/**
 * "מאגר תרגילים" ל-Visual Studio: ציור קטן של מבנה בנוסח השאלה — אותו ציור כמו בחלונות של C# (NestedDiagram), בגודל הטבעי שלו
 * (בלי הקטנה: מספרים קריאים; ציור שרחב מהמקום שיש בחלון — מוקטן רק כמה שצריך כדי שייכנס, ב-BankVsPanel / BankZoom.scaled).
 */
public final class BankFigure {
    private BankFigure() {}

    public static JComponent of(String type, String inner, List<List<String>> groups) {
        NestedDiagram d = new NestedDiagram();
        d.setMaxWidth(100000);
        d.set(type, inner, groups);
        d.setOpaque(false);
        Dimension p = d.getPreferredSize();
        d.setMinimumSize(p);
        d.setMaximumSize(p);
        return d;
    }
}
