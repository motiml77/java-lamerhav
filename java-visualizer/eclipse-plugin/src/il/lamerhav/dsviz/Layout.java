package il.lamerhav.dsviz;

import org.eclipse.e4.ui.model.application.ui.MElementContainer;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.SideValue;
import org.eclipse.e4.ui.model.application.ui.advanced.MPlaceholder;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.basic.MPartSashContainerElement;
import org.eclipse.e4.ui.model.application.ui.basic.MPartStack;
import org.eclipse.e4.ui.model.application.ui.basic.MTrimBar;
import org.eclipse.e4.ui.model.application.ui.basic.MTrimElement;
import org.eclipse.e4.ui.model.application.ui.basic.MTrimmedWindow;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;
import org.eclipse.e4.ui.model.application.ui.menu.MToolBar;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IWorkbenchWindow;

/**
 * סידור החלון של Eclipse אחרי ההתקנה, בלי שהתלמיד יצטרך לגרור כלום:
 * - חלון "הסברים בעברית" קבוע בצד ימין של העורך (ולא למטה, שם Eclipse פותח חלונות חדשים כברירת מחדל);
 * - סרגל הכלים של התוכנה בשורה נפרדת מכל שאר הסרגלים.
 * עובד על המודל של החלון (e4) — לכן גם בסביבת עבודה קיימת, שבה הסידור כבר שמור ותוספים חדשים לא משנים אותו.
 */
public final class Layout {
    public static final String TOOLBAR_ID = "il.lamerhav.dsviz.toolbar";
    private static final String OUTLINE = "org.eclipse.ui.views.ContentOutline";
    private static final String EDITORS = "org.eclipse.ui.editorss";

    private Layout() {
    }

    /**
     * סרגל הכלים של התוכנה — בשורה משלו. Eclipse מחלק סרגלים לשורות רק לפי רוחב (סרגל שלא נכנס בשורה יורד לשורה הבאה),
     * ולכן מרחיבים את הסרגל שלנו לרוחב המסך בעזרת מפריד ריק ובלתי נראה בסופו — והוא תמיד יורד לשורה משלו.
     * בטוח להריץ כמה פעמים (מפריד אחד בלבד). מחזיר true אם הסרגל נמצא ומוכן.
     */
    public static boolean toolbarOwnRow(IWorkbenchWindow w) {
        MWindow mw = w.getService(MWindow.class);
        EModelService ms = w.getService(EModelService.class);
        if (mw == null || ms == null) return false;
        MUIElement tb = ms.find(TOOLBAR_ID, mw);
        if (!(tb instanceof MToolBar)) return false;
        ToolBar bar = findToolBar(tb.getWidget());
        if (bar == null || bar.isDisposed()) return false;
        // Eclipse מחלק סרגלים לשורות רק לפי רוחב: מרחיבים את הסרגל שלנו לרוחב המסך בעזרת מפריד ריק ובלתי נראה בסופו,
        // ולכן הוא תמיד יורד לשורה משלו. (הכפתורים נשארים בצד, והשאר ריק.)
        int width = bar.getDisplay().getBounds().width;
        for (ToolItem item : bar.getItems()) {
            if (SPACER.equals(item.getData(SPACER))) {
                if (item.getWidth() != width) item.setWidth(width);
                return true;
            }
        }
        ToolItem spacer = new ToolItem(bar, SWT.SEPARATOR);
        spacer.setData(SPACER, SPACER);
        spacer.setWidth(width);
        // הסרגל (ועטיפת התמונה שלו) שומרים גודל — מחשבים אותו מחדש, ואז מסדרים את שורות הסרגלים מחדש
        bar.pack(true);
        bar.getParent().pack(true);
        for (Composite c = bar.getParent().getParent(); c != null; c = c.getParent()) c.layout(true, true);
        return true;
    }

    /** ב-Eclipse חדש ה-ToolBar עטוף ב-Composite — מחפשים אותו */
    public static ToolBar findToolBar(Object widget) {
        if (widget instanceof ToolBar) return (ToolBar) widget;
        if (widget instanceof Composite) {
            for (Control c : ((Composite) widget).getChildren()) {
                ToolBar t = findToolBar(c);
                if (t != null) return t;
            }
        }
        return null;
    }

    private static final String SPACER = "il.lamerhav.dsviz.rowSpacer";

    /**
     * חלון "הסברים בעברית" בצד ימין: ליד העורך, באותה ערימה של Outline (שם הוא עומד גם ב-perspective של Java).
     * אם אין Outline (או שהוא ממוזער) — ערימה חדשה מימין לעורך.
     */
    public static boolean dockMentorRight(IWorkbenchWindow w) {
        MWindow mw = w.getService(MWindow.class);
        EModelService ms = w.getService(EModelService.class);
        EPartService ps = w.getService(EPartService.class);
        if (mw == null || ms == null || ps == null) return false;
        MPart part = ps.findPart(MentorView.ID);
        if (part == null) return false;
        MUIElement el = part.getCurSharedRef() != null ? part.getCurSharedRef() : part;
        MPartStack target = rightStack(ms, mw, el);
        if (target != null) {
            if ((Object) el.getParent() != target) ms.move(el, (MElementContainer<MUIElement>) (MElementContainer<?>) target);
        } else {
            MUIElement editors = ms.find(EDITORS, mw);
            if (!(editors instanceof MPartSashContainerElement)) return false;
            MPartStack stack = ms.createModelElement(MPartStack.class);
            ms.move(el, (MElementContainer<MUIElement>) (MElementContainer<?>) stack);
            ms.insert(stack, (MPartSashContainerElement) editors, EModelService.RIGHT_OF, 0.25f);
        }
        el.setToBeRendered(true);
        el.setVisible(true);
        ps.activate(part);
        return true;
    }

    /** הערימה שבה נמצא Outline, אם היא חלק מהחלון (לא ממוזערת בצד) */
    private static MPartStack rightStack(EModelService ms, MWindow mw, MUIElement mine) {
        MUIElement o = ms.find(OUTLINE, mw);
        if (o == null) return null;
        if (o instanceof MPart && ((MPart) o).getCurSharedRef() != null) o = ((MPart) o).getCurSharedRef();
        MElementContainer<?> p = o.getParent();
        if (!(p instanceof MPartStack)) return null;
        for (MUIElement a = (MUIElement) p; a != null; a = (MUIElement) a.getParent()) {
            if (a instanceof MTrimBar) return null;   // ממוזער לסרגל בצד
            if (a == mine) return null;
        }
        return (MPartStack) p;
    }

    static boolean isPlaceholder(MUIElement e) {
        return e instanceof MPlaceholder;
    }
}
