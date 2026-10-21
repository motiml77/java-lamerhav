package unit4.collectionsLib;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;

/**
 * המראה של החלון: FlatLaf (מראה מודרני ושטוח), הגופן Alef שנארז בספרייה (עברית ואנגלית),
 * וגופן קוד מודרני מהמחשב (Cascadia Mono / Consolas ...). הכול עם נפילה חזרה אם משהו חסר.
 */
public final class Theme {
    private Theme() {}

    public static final Color BG = new Color(0xF1F3F7);          // רקע החלון
    public static final Color CARD = Color.WHITE;                // רקע הכרטיסים
    public static final Color BORDER = new Color(0xDFE3EA);
    public static final Color TEXT = new Color(0x1D2433);
    public static final Color MUTED = new Color(0x667085);
    public static final Color ACCENT = new Color(0x2F6FED);
    public static final Color ACCENT_DARK = new Color(0x2457C5);
    public static final Color ACCENT_SOFT = new Color(0xE8F0FE);
    public static final Color CODE_LINE = new Color(0xFFF0B3);   // השורה הנוכחית בקוד
    public static final Color CODE_BAR = new Color(0xF0A500);
    public static final Color GUTTER = new Color(0xF7F8FA);
    public static final Color CHANGED = new Color(0xFFF0B3);     // משתנה שהשתנה

    public static Font ui, uiBold, small, smallBold, title, big, mono, monoBig;
    static String family = Font.DIALOG, boldFamily = Font.DIALOG;
    static int boldStyle = Font.BOLD;
    private static boolean installed;

    public static void install() {
        if (installed) return;
        installed = true;
        String regular = register("Alef-Regular.ttf");
        register("Alef-Bold.ttf");   // אותה משפחה, משקל מודגש
        if (regular != null) family = regular;
        boldFamily = family;
        boldStyle = Font.BOLD;
        ui = font(family, Font.PLAIN, 15);
        uiBold = font(boldFamily, boldStyle, 15);
        small = font(family, Font.PLAIN, 13);
        smallBold = font(boldFamily, boldStyle, 13);
        title = font(boldFamily, boldStyle, 15);
        big = font(boldFamily, boldStyle, 19);
        mono = monoFont(14);
        monoBig = monoFont(16);

        boolean flat = false;
        try {
            Class.forName("com.formdev.flatlaf.FlatLightLaf").getMethod("setup").invoke(null);
            flat = true;
        } catch (Throwable t) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }
        UIManager.put("defaultFont", new FontUIResource(ui));
        if (flat) {
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 12);
            UIManager.put("Component.focusWidth", 0);
            UIManager.put("Component.innerFocusWidth", 0);
            UIManager.put("Button.innerFocusWidth", 0);
            UIManager.put("Button.margin", new Insets(5, 12, 5, 12));
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
            UIManager.put("ScrollBar.width", 11);
            UIManager.put("ScrollPane.border", javax.swing.BorderFactory.createEmptyBorder());
            UIManager.put("SplitPane.dividerSize", 10);
            UIManager.put("SplitPane.background", BG);
            UIManager.put("SplitPaneDivider.gripColor", new Color(0xB8C0CC));
            UIManager.put("Table.rowHeight", 28);
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
            UIManager.put("Table.gridColor", new Color(0xEEF0F4));
            UIManager.put("TableHeader.height", 28);
            UIManager.put("TableHeader.background", GUTTER);
            UIManager.put("TableHeader.separatorColor", BORDER);
            UIManager.put("TableHeader.bottomSeparatorColor", BORDER);
            UIManager.put("Slider.thumbSize", new Dimension(14, 14));
            UIManager.put("Slider.trackWidth", 4);
            UIManager.put("Slider.thumbColor", ACCENT);
            UIManager.put("Slider.trackValueColor", ACCENT);
            UIManager.put("ToolTip.font", new FontUIResource(small));
            UIManager.put("TitlePane.unifiedBackground", false);
        }
        // בלי FlatLaf (מראה המערכת) — הגופן לכל רכיב בנפרד
        for (String k : new String[] { "Label.font", "Button.font", "ToggleButton.font", "CheckBox.font", "Table.font",
                "TableHeader.font", "List.font", "TextArea.font" })
            UIManager.put(k, new FontUIResource(ui));
    }

    /** גופן עם "נפילה חזרה": תו שחסר בגופן (למשל ← או אות עברית בגופן קוד) נלקח מגופן אחר */
    static Font font(String family, int style, int size) {
        return StyleContext.getDefaultStyleContext().getFont(family, style, size);
    }

    private static Font monoFont(int size) {
        Set<String> have = new HashSet<String>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : new String[] { "Cascadia Mono", "Cascadia Code", "Consolas", "JetBrains Mono", "SF Mono",
                "Menlo", "DejaVu Sans Mono", "Liberation Mono" })
            if (have.contains(n)) return font(n, Font.PLAIN, size);
        return font(Font.MONOSPACED, Font.PLAIN, size);
    }

    /** טוען גופן שנארז בתוך הספרייה (unit4/collectionsLib/fonts) ומחזיר את שם המשפחה שלו */
    private static String register(String file) {
        if ("false".equals(System.getProperty("dsviz.fonts"))) return null;   // לבדיקות (למשל תחת Wine)
        InputStream in = Theme.class.getResourceAsStream("fonts/" + file);
        if (in == null) return null;
        try {
            Font f = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
            return f.getFamily();
        } catch (Exception e) {
            return null;
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }
    }
}
