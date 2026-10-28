package il.lamerhav.dsviz.it;

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
 * צילום מסך של Eclipse עם קובץ של תלמיד: העורך (קווים אדומים / צהובים) וחלון Problems עם ההסברים בעברית.
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
                        // בלי מה שנשאר מהצילום הקודם (חלון מוגדל); את חלון ההסברים לא פותחים כאן — הוא צריך להיפתח לבד (AutoSetup)
                        if (page.findView("il.lamerhav.dsviz.mentorView") == null) page.resetPerspective();
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
                        // ועוד צילום: חלון Problems מוגדל — ההודעות המלאות
                        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
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

    private static void capture(Display display, String file) {
        Rectangle r = display.getBounds();
        Image img = new Image(display, r.width, r.height);
        GC gc = new GC(display);
        gc.copyArea(img, 0, 0);
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
