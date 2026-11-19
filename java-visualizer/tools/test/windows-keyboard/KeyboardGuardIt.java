package il.lamerhav.dsviz;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.internal.BidiUtil;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;

/**
 * KeyboardGuard על Windows אמיתי עם מקלדת עברית (.github/workflows/dsviz-eclipse-keyboard.yml): רק המשתמש מחליף את שפת ההקלדה.
 * <ol>
 *   <li>בלי ההגנה: StyledText באמת מחליף לעברית כשהסמן ליד אות עברית (אחרת הבדיקה לא בודקת כלום);</li>
 *   <li>עם ההגנה: סמן ליד עברית בעורך, Console שלא קיבל פוקוס שהסמן שלו זז לסוף פלט בעברית, חלון ריחוף עם הסבר בעברית —
 *       השפה נשארת אנגלית;</li>
 *   <li>החלפה אוטומטית שחמקה ואחריה Ctrl+S / Shift+אות — מוחזרת, ולא נלמדת כ"שפת המשתמש";</li>
 *   <li>Alt+Shift אמיתי (SendInput) — עברית, והיא נשארת גם כשהסמן זז לקוד; Alt+Shift שוב — אנגלית.</li>
 * </ol>
 *   javac -cp swt.jar -d out KeyboardGuard.java KeyboardGuardIt.java && java -cp "out;swt.jar" il.lamerhav.dsviz.KeyboardGuardIt
 */
public final class KeyboardGuardIt {
    private static int ok, fail;
    private static Display d;

    public static void main(String[] args) {
        d = new Display();
        if (!BidiUtil.isBidiPlatform()) {
            System.out.println("FAIL אין מקלדת עברית במחשב (BidiUtil.isBidiPlatform = false) — הבדיקה לא יכולה לרוץ");
            System.exit(1);
        }
        BidiUtil.setKeyboardLanguage(BidiUtil.KEYBOARD_NON_BIDI);
        Shell shell = new Shell(d);
        shell.setText("KeyboardGuardIt");
        shell.setLayout(new FillLayout(SWT.VERTICAL));
        StyledText editor = new StyledText(shell, SWT.MULTI | SWT.BORDER);
        String code = "int x = 5; // שלום עולם\nSystem.out.println(\"סעיף א, בדיקה 1: נכון\");\nint y = 7;\n";
        editor.setText(code);
        StyledText console = new StyledText(shell, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER);   // לא מקבל פוקוס — כמו ה-Console
        console.setText("start\n");
        shell.setSize(700, 400);
        shell.open();
        shell.forceActive();
        editor.setFocus();
        editor.setCaretOffset(code.indexOf("int y"));
        pump(400);
        check("בהתחלה: אנגלית", lang() == BidiUtil.KEYBOARD_NON_BIDI);

        // 1. בלי ההגנה — SWT באמת מחליף (אחרת אין כאן מה לבדוק). SWT מחשב את הכיוון מחדש רק בלחיצת עכבר / מקשי ניווט
        //    (doMouseLocationChange / invokeAction) ובחלון טקסט חדש — לכן לוחצים באמת (SendInput), כמו תלמיד
        click(editor, code.indexOf("שלום") + 2);
        check("בלי ההגנה: לחיצה בעכבר על הערה בעברית מחליפה לעברית (ההתנהגות של SWT שגורמת לבעיה)", lang() == BidiUtil.KEYBOARD_BIDI);
        click(editor, code.indexOf("int y") + 1);
        BidiUtil.setKeyboardLanguage(BidiUtil.KEYBOARD_NON_BIDI);
        pump(200);
        Shell hover0 = hover(shell);
        check("בלי ההגנה: חלון ריחוף עם הסבר בעברית מחליף לעברית", lang() == BidiUtil.KEYBOARD_BIDI);
        hover0.dispose();
        BidiUtil.setKeyboardLanguage(BidiUtil.KEYBOARD_NON_BIDI);
        editor.setFocus();
        pump(300);

        // 2. עם ההגנה
        KeyboardGuard.install(d);
        pump(300);
        check("ההגנה פעילה, ושפת המשתמש: אנגלית", KeyboardGuard.typing() == BidiUtil.KEYBOARD_NON_BIDI);
        click(editor, code.indexOf("שלום") + 2);
        check("עורך: לחיצה בעכבר על הערה בעברית — נשאר באנגלית", lang() == BidiUtil.KEYBOARD_NON_BIDI);
        click(editor, code.indexOf("System") + 2);
        for (int i = 0; i < 3; i++) {
            click(editor, code.indexOf("נכון") + 1);
            click(editor, code.indexOf("System") + 3);
        }
        check("עורך: לחיצות הלוך ושוב בין מחרוזת בעברית לקוד — אנגלית", lang() == BidiUtil.KEYBOARD_NON_BIDI);
        editor.setCaretOffset(code.indexOf("// שלום"));
        pump(150);
        for (int i = 0; i < 6; i++) { key(SWT.ARROW_RIGHT, true); key(SWT.ARROW_RIGHT, false); }   // חצים לתוך ההערה בעברית
        pump(300);
        check("עורך: חצים לתוך הערה בעברית — אנגלית", lang() == BidiUtil.KEYBOARD_NON_BIDI);
        console.append("סעיף א, בדיקה 1: נכון");
        console.setCaretOffset(console.getCharCount());   // כמו IOConsoleViewer.repositionCaretAfterOutput אחרי הדפסה
        pump(400);
        check("Console בלי פוקוס: הסמן זז לסוף פלט בעברית — אנגלית", lang() == BidiUtil.KEYBOARD_NON_BIDI);
        Shell hover = hover(shell);
        check("חלון ריחוף עם הסבר בעברית — אנגלית", lang() == BidiUtil.KEYBOARD_NON_BIDI);
        hover.dispose();
        editor.setFocus();
        pump(200);

        // 3. החלפה שחמקה, ואחריה Ctrl+S ו-Shift+אות — מוחזרת ולא נלמדת
        BidiUtil.setKeyboardLanguage(BidiUtil.KEYBOARD_BIDI);
        key(SWT.CTRL, true);
        key('s', true);
        key('s', false);
        key(SWT.CTRL, false);
        key(SWT.SHIFT, true);
        key(SWT.ARROW_RIGHT, true);
        key(SWT.ARROW_RIGHT, false);
        key(SWT.SHIFT, false);
        pump(600);
        check("החלפה אוטומטית ואחריה Ctrl+S / Shift+חץ — חזרה לאנגלית", lang() == BidiUtil.KEYBOARD_NON_BIDI);
        check("...ולא נלמדה כשפת המשתמש", KeyboardGuard.typing() == BidiUtil.KEYBOARD_NON_BIDI);

        // 4. המשתמש מחליף בעצמו: Alt+Shift (ואם הקיצור הזה כבוי במחשב — Win+רווח)
        editor.setFocus();
        pump(200);
        boolean switched = userSwitch();
        check("Alt+Shift של המשתמש — עברית", switched && lang() == BidiUtil.KEYBOARD_BIDI);
        check("...ושפת המשתמש: עברית", KeyboardGuard.typing() == BidiUtil.KEYBOARD_BIDI);
        editor.setCaretOffset(code.indexOf("int y"));
        pump(400);
        editor.setCaretOffset(code.indexOf("System") + 2);
        pump(400);
        check("עברית שהמשתמש בחר נשארת גם כשהסמן זז לקוד", lang() == BidiUtil.KEYBOARD_BIDI);
        boolean back = userSwitch();
        check("Alt+Shift שוב — אנגלית, ונשארת", back && lang() == BidiUtil.KEYBOARD_NON_BIDI && KeyboardGuard.typing() == BidiUtil.KEYBOARD_NON_BIDI);

        System.out.println();
        System.out.println("KeyboardGuard: " + ok + " עברו, " + fail + " נכשלו");
        d.dispose();
        System.exit(fail == 0 ? 0 : 1);
    }

    /** לחיצת עכבר אמיתית (SendInput) על התו ב-offset */
    private static void click(StyledText st, int offset) {
        org.eclipse.swt.graphics.Point p = st.getLocationAtOffset(offset);
        org.eclipse.swt.graphics.Point at = st.toDisplay(p.x + 2, p.y + st.getLineHeight(offset) / 2);
        Event m = new Event();
        m.type = SWT.MouseMove; m.x = at.x; m.y = at.y;
        d.post(m);
        pump(40);
        Event dn = new Event();
        dn.type = SWT.MouseDown; dn.button = 1;
        d.post(dn);
        pump(30);
        Event up = new Event();
        up.type = SWT.MouseUp; up.button = 1;
        d.post(up);
        pump(350);
    }

    /** חלון ריחוף (כמו של Eclipse מעל שגיאה): StyledText חדש שמתחיל באות עברית */
    private static Shell hover(Shell owner) {
        Shell h = new Shell(owner, SWT.ON_TOP | SWT.NO_FOCUS | SWT.TOOL);
        h.setLayout(new FillLayout());
        StyledText tip = new StyledText(h, SWT.MULTI | SWT.WRAP | SWT.READ_ONLY);
        tip.setText("חסר נקודה-פסיק בסוף השורה");
        h.setBounds(owner.getBounds().x + 40, owner.getBounds().y + 80, 300, 80);
        h.setVisible(true);
        pump(400);
        return h;
    }

    /** החלפת שפה כמו שמשתמש עושה (SendInput): Alt+Shift; אם השפה לא זזה — Win+רווח */
    private static boolean userSwitch() {
        int before = lang();
        key(SWT.ALT, true);
        key(SWT.SHIFT, true);
        key(SWT.SHIFT, false);
        key(SWT.ALT, false);
        pump(700);
        if (lang() != before) return true;
        System.out.println("  (Alt+Shift לא החליף שפה במחשב הזה — מנסים Win+רווח)");
        key(SWT.COMMAND, true);
        key(' ', true);
        key(' ', false);
        key(SWT.COMMAND, false);
        pump(1200);
        return lang() != before;
    }

    private static void key(int code, boolean down) {
        Event e = new Event();
        e.type = down ? SWT.KeyDown : SWT.KeyUp;
        if (code < 0x10000 && code != ' ') e.character = (char) code;
        else if (code == ' ') e.character = ' ';
        else e.keyCode = code;
        d.post(e);
        pump(40);
    }

    private static int lang() {
        return BidiUtil.getKeyboardLanguage();
    }

    private static void pump(int ms) {
        final boolean[] done = { false };
        d.timerExec(ms, () -> done[0] = true);
        while (!done[0]) {
            if (!d.readAndDispatch()) d.sleep();
        }
    }

    private static void check(String name, boolean c) {
        if (c) ok++; else fail++;
        System.out.println((c ? "  ok   " : "  FAIL ") + name + "   [שפה עכשיו: " + (lang() == BidiUtil.KEYBOARD_BIDI ? "עברית" : "אנגלית") + "]");
    }
}
