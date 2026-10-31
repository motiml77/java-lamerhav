package il.lamerhav.dsviz.it;

import il.lamerhav.dsviz.Layout;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * צילום מסך של Eclipse עם קובץ של תלמיד: העורך (קווים אדומים / צהובים) וחלון Problems עם ההסברים בעברית;
 * וחלון "הסברים בעברית" לבד (out-mentor.png), עם תיאור הכרטיסים שבו (PANEL-...) — לבדיקה ב-run.sh.
 *   eclipse -application il.lamerhav.dsviz.it.shot <project/src/File.java> <out.png> [line-to-select]
 */
public class Shot implements IApplication {
    @Override
    public Object start(IApplicationContext context) throws Exception {
        final String[] a = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        IEclipsePreferences p = InstanceScope.INSTANCE.getNode("il.lamerhav.dsviz");
        p.putBoolean("setupDone", true); // בלי חלון "ברוכים הבאים"
        p.flush();
        final Display display = PlatformUI.createDisplay();
        PlatformUI.createAndRunWorkbench(display, new WorkbenchAdvisor() {
            @Override
            public String getInitialWindowPerspectiveId() {
                return "org.eclipse.jdt.ui.JavaPerspective";
            }

            @Override
            public void postStartup() {
                display.timerExec(3000, () -> {
                    try {
                        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                        page.getWorkbenchWindow().getShell().setBounds(0, 0, 1700, 1050);
                        page.getWorkbenchWindow().getShell().setMaximized(true);
                        // בלי דף ה-Welcome (בהפעלה הראשונה של סביבת עבודה הוא מכסה הכול)
                        org.eclipse.ui.intro.IIntroManager intro = PlatformUI.getWorkbench().getIntroManager();
                        if (intro.getIntro() != null) intro.closeIntro(intro.getIntro());
                        // הצילום הקודם נגמר עם Problems מוגדל (וכל השאר ממוזערים, גם חלון ההסברים) — חוזרים לגודל הרגיל
                        for (org.eclipse.ui.IViewReference v : page.getViewReferences()) {
                            if (page.getPartState(v) != IWorkbenchPage.STATE_RESTORED) page.setPartState(v, IWorkbenchPage.STATE_RESTORED);
                        }
                        // בלי מה שנשאר מהצילום הקודם (חלון מוגדל); את חלון ההסברים לא פותחים כאן — הוא צריך להיפתח לבד (AutoSetup)
                        if (page.findView("il.lamerhav.dsviz.mentorView") == null) page.resetPerspective();
                        layoutReport(page.getWorkbenchWindow(), "AUTO");
                        // כמו אצל תלמיד שהחלון נפתח לו למטה: מעבירים אותו למטה (לערימה של Problems), ובודקים שהסידור מחזיר אותו לימין
                        try {
                            org.eclipse.e4.ui.workbench.modeling.EModelService ms = page.getWorkbenchWindow().getService(org.eclipse.e4.ui.workbench.modeling.EModelService.class);
                            org.eclipse.e4.ui.model.application.ui.basic.MWindow mw = page.getWorkbenchWindow().getService(org.eclipse.e4.ui.model.application.ui.basic.MWindow.class);
                            org.eclipse.e4.ui.model.application.ui.MUIElement mine = ms.find(MENTOR, mw);
                            if (mine instanceof org.eclipse.e4.ui.model.application.ui.basic.MPart && ((org.eclipse.e4.ui.model.application.ui.basic.MPart) mine).getCurSharedRef() != null) mine = ((org.eclipse.e4.ui.model.application.ui.basic.MPart) mine).getCurSharedRef();
                            org.eclipse.e4.ui.model.application.ui.MUIElement prob = ms.find("org.eclipse.ui.views.ProblemView", mw);
                            if (prob instanceof org.eclipse.e4.ui.model.application.ui.basic.MPart && ((org.eclipse.e4.ui.model.application.ui.basic.MPart) prob).getCurSharedRef() != null) prob = ((org.eclipse.e4.ui.model.application.ui.basic.MPart) prob).getCurSharedRef();
                            ms.move(mine, (org.eclipse.e4.ui.model.application.ui.MElementContainer<org.eclipse.e4.ui.model.application.ui.MUIElement>) (Object) prob.getParent());
                            layoutReport(page.getWorkbenchWindow(), "BOTTOM");
                            Layout.dockMentorRight(page.getWorkbenchWindow());
                            layoutReport(page.getWorkbenchWindow(), "REDOCKED");
                        } catch (Throwable t) {
                            t.printStackTrace(System.out);
                        }
                        // תרגיל שנוצר: main ופעולות עזר מכווצים, הפעולה של התלמיד פתוחה (Folding)
                        IFile ex = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path("/Students/src/Exercise.java"));
                        final ITextEditor exEd = (ITextEditor) IDE.openEditor(page, ex);
                        il.lamerhav.dsviz.Folding.collapseGiven(exEd, ex);
                        display.timerExec(5000, () -> foldReport(exEd));
                        IFile f = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(a[0]));
                        ITextEditor ed = (ITextEditor) IDE.openEditor(page, f);
                        page.showView("org.eclipse.ui.views.ProblemView");
                        page.activate(ed);
                        if (a.length > 2) {
                            // שורה:עמודה — הסמן על השגיאה, ופותחים את חלון ההסבר שלה (כמו ריחוף עם העכבר / F2)
                            org.eclipse.jface.text.IDocument doc = ed.getDocumentProvider().getDocument(ed.getEditorInput());
                            String[] lc = a[2].split(":");
                            int line = Integer.parseInt(lc[0]) - 1, col = lc.length > 1 ? Integer.parseInt(lc[1]) : 0;
                            ed.selectAndReveal(doc.getLineOffset(line) + col, 0);
                            display.timerExec(4000, () -> {
                                try {
                                    org.eclipse.ui.handlers.IHandlerService hs = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getService(org.eclipse.ui.handlers.IHandlerService.class);
                                    hs.executeCommand("org.eclipse.ui.edit.text.showInformation", null);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    display.timerExec(6500, () -> {
                        try {
                            IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                            for (org.eclipse.ui.IViewReference v : page.getViewReferences()) {
                                if (v.getId().contains("oomph") || v.getId().toLowerCase().contains("notification")) page.hideView(v);
                            }
                            org.eclipse.ui.IViewPart pv = page.showView("org.eclipse.ui.views.ProblemView", null, IWorkbenchPage.VIEW_VISIBLE);
                            expandAll(pv);
                            widen(page.getWorkbenchWindow().getShell());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    display.timerExec(11000, () -> {
                        IWorkbenchPage pg = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                        System.out.println("MENTOR-VIEW-OPEN " + (pg.findView("il.lamerhav.dsviz.mentorView") != null && pg.isPartVisible(pg.findView("il.lamerhav.dsviz.mentorView"))));
                        capture(display, a[1]);
                        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                        panelReport(display, page, a[1].replace(".png", "-mentor.png"), new Path(a[0]).lastSegment());
                        // ועוד צילום: חלון Problems מוגדל — ההודעות המלאות
                        org.eclipse.ui.IViewReference ref = page.findViewReference("org.eclipse.ui.views.ProblemView");
                        if (ref != null) page.setPartState(ref, IWorkbenchPage.STATE_MAXIMIZED);
                        display.timerExec(2500, () -> {
                            widen(page.getWorkbenchWindow().getShell());
                            display.timerExec(800, () -> {
                                capture(display, a[1].replace(".png", "-problems.png"));
                                PlatformUI.getWorkbench().close();
                            });
                        });
                    });
                });
            }
        });
        display.dispose();
        return IApplication.EXIT_OK;
    }

    /** איפה עומדים החלון והסרגל: לבדיקה שהסידור האוטומטי (Layout) באמת עבד */
    private static void layoutReport(org.eclipse.ui.IWorkbenchWindow w, String tag) {
        org.eclipse.e4.ui.model.application.ui.basic.MWindow mw = w.getService(org.eclipse.e4.ui.model.application.ui.basic.MWindow.class);
        org.eclipse.e4.ui.workbench.modeling.EModelService ms = w.getService(org.eclipse.e4.ui.workbench.modeling.EModelService.class);
        org.eclipse.e4.ui.model.application.ui.MUIElement o = ms.find("org.eclipse.ui.views.ContentOutline", mw);
        org.eclipse.e4.ui.model.application.ui.MUIElement m = ms.find(MENTOR, mw);
        if (o instanceof org.eclipse.e4.ui.model.application.ui.basic.MPart && ((org.eclipse.e4.ui.model.application.ui.basic.MPart) o).getCurSharedRef() != null) o = ((org.eclipse.e4.ui.model.application.ui.basic.MPart) o).getCurSharedRef();
        if (m instanceof org.eclipse.e4.ui.model.application.ui.basic.MPart && ((org.eclipse.e4.ui.model.application.ui.basic.MPart) m).getCurSharedRef() != null) m = ((org.eclipse.e4.ui.model.application.ui.basic.MPart) m).getCurSharedRef();
        System.out.println("LAYOUT-" + tag + " mentor-in-outline-stack=" + (m != null && o != null && m.getParent() == o.getParent()));
        org.eclipse.e4.ui.model.application.ui.MUIElement tb = ms.find(Layout.TOOLBAR_ID, mw);
        org.eclipse.e4.ui.model.application.ui.MUIElement main = ms.find("org.eclipse.ui.main.toolbar", mw);
                if (tb != null && tb.getWidget() instanceof org.eclipse.swt.widgets.Control && main != null && main.getWidget() instanceof org.eclipse.swt.widgets.Control) {
            org.eclipse.swt.widgets.Control ca = (org.eclipse.swt.widgets.Control) tb.getWidget(), cb = (org.eclipse.swt.widgets.Control) main.getWidget();
            org.eclipse.swt.graphics.Point a = ca.toDisplay(0, 0), b = cb.toDisplay(0, 0);
            for (org.eclipse.swt.widgets.Control c : new org.eclipse.swt.widgets.Control[] { ca.getParent(), cb.getParent() }) {
                org.eclipse.swt.graphics.Point d = c.toDisplay(0, 0);
                System.out.println("LAYOUT-" + tag + " parent " + c.getClass().getSimpleName() + " at " + d.x + "," + d.y + " size " + c.getSize().x + "x" + c.getSize().y + " data=" + c.getData("modelElement"));
            }
            for (org.eclipse.swt.widgets.Control c : ca.getParent().getParent().getChildren()) {
                org.eclipse.swt.graphics.Point d = c.toDisplay(0, 0);
                System.out.println("LAYOUT-" + tag + "   sibling " + c.getClass().getSimpleName() + " at " + d.x + "," + d.y + " size " + c.getSize().x + "x" + c.getSize().y);
            }
            org.eclipse.swt.widgets.ToolBar t = Layout.findToolBar(ca);
            System.out.println("LAYOUT-" + tag + " widget=" + ca.getClass().getSimpleName());
            for (org.eclipse.swt.widgets.Control c = t; c != null && !(c instanceof org.eclipse.swt.widgets.Shell); c = c.getParent())
                System.out.println("LAYOUT-" + tag + "   chain " + c.getClass().getSimpleName() + " size " + c.getSize().x + "x" + c.getSize().y + " pref " + c.computeSize(-1, -1).x + " layout=" + (c instanceof org.eclipse.swt.widgets.Composite ? String.valueOf(((org.eclipse.swt.widgets.Composite) c).getLayout()) : "-"));
            if (t != null) {
                int sp = 0;
                for (org.eclipse.swt.widgets.ToolItem it : t.getItems()) if (it.getData("il.lamerhav.dsviz.rowSpacer") != null) sp++;
                System.out.println("LAYOUT-" + tag + " toolbar items=" + t.getItemCount() + " spacers=" + sp + " size=" + t.getSize().x + "x" + t.getSize().y + " pref=" + t.computeSize(-1, -1).x);
            }
            System.out.println("LAYOUT-" + tag + " toolbar-y=" + a.y + " main-toolbar-y=" + b.y + " separate-row=" + (a.y > b.y + 5));
        }
    }

    /** אילו פעולות מכווצות בעורך של התרגיל */
    private static void foldReport(ITextEditor ed) {
        try {
            org.eclipse.jface.text.source.projection.ProjectionAnnotationModel m = ed.getAdapter(org.eclipse.jface.text.source.projection.ProjectionAnnotationModel.class);
            org.eclipse.jface.text.IDocument doc = ed.getDocumentProvider().getDocument(ed.getEditorInput());
            java.util.Iterator<?> it = m.getAnnotationIterator();
            while (it.hasNext()) {
                Object o = it.next();
                if (!(o instanceof org.eclipse.jface.text.source.projection.ProjectionAnnotation)) continue;
                org.eclipse.jface.text.Position pos = m.getPosition((org.eclipse.jface.text.source.Annotation) o);
                int line = doc.getLineOfOffset(pos.offset);
                String text = doc.get(doc.getLineOffset(line), doc.getLineLength(line)).trim();
                System.out.println("FOLD " + text + " collapsed=" + ((org.eclipse.jface.text.source.projection.ProjectionAnnotation) o).isCollapsed());
            }
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }

    private static final String MENTOR = "il.lamerhav.dsviz.mentorView";

    /**
     * חלון "הסברים בעברית": צילום של החלון לבד, ולכל כרטיס — מה יש בו לפי הסדר (title = שורה · שגיאה, main = ההסבר,
     * gap = שורת הרווח, fix = ההצעה לתיקון, code = הקוד המתוקן) והטקסטים עם תווי הכיווניות (<RLM> <RLE> ...).
     */
    private static void panelReport(Display display, IWorkbenchPage page, String file, String name) {
        try {
            org.eclipse.ui.IViewPart v = page.findView(MENTOR);
            org.eclipse.e4.ui.model.application.ui.basic.MPart mp = v == null ? null : v.getSite().getService(org.eclipse.e4.ui.model.application.ui.basic.MPart.class);
            if (mp == null || !(mp.getWidget() instanceof org.eclipse.swt.widgets.Control)) {
                System.out.println("PANEL none");
                return;
            }
            org.eclipse.swt.widgets.Control root = (org.eclipse.swt.widgets.Control) mp.getWidget();
            org.eclipse.swt.graphics.Point at = root.toDisplay(0, 0), size = root.getSize();
            System.out.println("PANEL " + name + " size=" + size.x + "x" + size.y);
            panelWalk(root, name);
            capture(display, file, new Rectangle(at.x, at.y, size.x, size.y));
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }

    private static void panelWalk(org.eclipse.swt.widgets.Control c, String name) {
        if (c instanceof org.eclipse.swt.widgets.Composite && c.getData() instanceof il.lamerhav.dsviz.Mentor.Finding) {
            il.lamerhav.dsviz.Mentor.Finding f = (il.lamerhav.dsviz.Mentor.Finding) c.getData();
            StringBuilder kinds = new StringBuilder();
            java.util.List<String> texts = new java.util.ArrayList<String>();
            for (org.eclipse.swt.widgets.Control k : ((org.eclipse.swt.widgets.Composite) c).getChildren()) {
                if (!(k instanceof org.eclipse.swt.widgets.Label)) continue;
                String t = ((org.eclipse.swt.widgets.Label) k).getText(), plain = il.lamerhav.dsviz.RtlText.strip(t);
                boolean bold = k.getFont().getFontData()[0].getStyle() == SWT.BOLD;
                String kind = bold ? "title" : plain.trim().isEmpty() ? "gap" : (k.getStyle() & SWT.LEFT_TO_RIGHT) != 0 ? "code"
                        : plain.startsWith("הצעה לתיקון:") ? "fix" : "main";
                kinds.append(' ').append(kind).append(kind.equals("gap") ? "(" + k.getSize().y + "px)" : "");
                texts.add(kind + " " + visible(t));
            }
            System.out.println("PANEL-CARD " + name + ":" + f.line + " " + (f.isError() ? "error" : "warning") + ":" + kinds);
            for (String t : texts) System.out.println("PANEL-TEXT " + name + ":" + f.line + " " + t);
            return;
        }
        if (c instanceof org.eclipse.swt.widgets.Label) System.out.println("PANEL-HEAD " + name + " " + visible(((org.eclipse.swt.widgets.Label) c).getText()));
        if (c instanceof org.eclipse.swt.widgets.Composite) for (org.eclipse.swt.widgets.Control k : ((org.eclipse.swt.widgets.Composite) c).getChildren()) panelWalk(k, name);
    }

    /** תווי הכיווניות כשמות (<RLM> ...) — כדי לראות אותם בלוג */
    private static String visible(String s) {
        return s.replace("\u200F", "<RLM>").replace("\u200E", "<LRM>").replace("\u202B", "<RLE>").replace("\u202A", "<LRE>").replace("\u202C", "<PDF>")
                .replace("\n", "<NL>");
    }

    private static void capture(Display display, String file) {
        capture(display, file, display.getBounds());
    }

    private static void capture(Display display, String file, Rectangle r) {
        Image img = new Image(display, r.width, r.height);
        GC gc = new GC(display);
        gc.copyArea(img, r.x, r.y);
        gc.dispose();
        ImageLoader l = new ImageLoader();
        l.data = new org.eclipse.swt.graphics.ImageData[] { img.getImageData() };
        l.save(file, SWT.IMAGE_PNG);
        img.dispose();
    }

    /** פותח את כל הקבוצות בחלון Problems (דרך ה-viewer שלו) */
    private static void expandAll(Object view) {
        for (Class<?> c = view.getClass(); c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod("getViewer");
                m.setAccessible(true);
                Object v = m.invoke(view);
                if (v instanceof org.eclipse.jface.viewers.AbstractTreeViewer) ((org.eclipse.jface.viewers.AbstractTreeViewer) v).expandAll();
                return;
            } catch (NoSuchMethodException e) {
                // במחלקת-העל
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }

    /** בחלון Problems: פותחים את הקבוצות (שגיאות / אזהרות) ומרחיבים את עמודת התיאור, כדי שיראו את ההודעות המלאות */
    private static void widen(org.eclipse.swt.widgets.Control c) {
        if (c instanceof org.eclipse.swt.widgets.Tree) {
            org.eclipse.swt.widgets.Tree t = (org.eclipse.swt.widgets.Tree) c;
            for (org.eclipse.swt.widgets.TreeColumn col : t.getColumns()) {
                if (col.getText().startsWith("Description")) col.setWidth(1150);
                else if (col.getText().startsWith("Resource")) col.setWidth(110);
                else if (col.getText().startsWith("Location")) col.setWidth(70);
                else col.setWidth(0);
            }
            for (org.eclipse.swt.widgets.TreeItem i : t.getItems()) i.setExpanded(true);
        }
        if (c instanceof org.eclipse.swt.widgets.Composite) {
            for (org.eclipse.swt.widgets.Control k : ((org.eclipse.swt.widgets.Composite) c).getChildren()) widen(k);
        }
    }

    @Override
    public void stop() {
    }
}
