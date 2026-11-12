package il.lamerhav.dsviz;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * פרויקט עם module-info.java (האשף של Eclipse יוצר אותו כברירת מחדל — "Create module-info.java") ומחלקה בלי package:
 * ההרצה הרגילה (Run As ← Java Application) נופלת ב-"FindException: Error reading module ... unnamed package not allowed in module",
 * כי תיקיית הפלט רצה כמודול, ומודול לא יכול להכיל מחלקות בלי package. (ההרצה הוויזואלית רצה ב-classpath, ולכן עובדת.)
 * תלמידים כמעט אף פעם לא צריכים module-info: אם הוא "ריק" (רק module NAME { } ואולי requires של dsviz / java.* / jdk.*) — הוא נמחק
 * (עם Local History), הספרייה חוזרת ל-classpath הרגיל, ותצורות ההרצה של הפרויקט מאבדות את שם המודול. אחרת — הסבר בעברית, פעם אחת.
 * נקרא מ-LibraryEverywhere (בהפעלה, בפרויקט חדש, וכשנוסף קובץ בלי package או module-info.java).
 */
final class ModuleFix {
    enum Result { NONE, REMOVED, BLOCKED }

    static final String FILE = "module-info.java";
    private static final String NODE = "il.lamerhav.dsviz";
    /** הפרויקטים שכבר קיבלו את ההסבר (module-info עם הגדרות — לא נמחק), מופרדים ב-"/" (אסור בשם של פרויקט) */
    private static final String WARNED = "moduleInfoWarned";

    /**
     * מאפיינים של תצורת הרצה ש-JDT מוסיף בפרויקט מודולרי (IJavaLaunchConfigurationConstants.ATTR_MODULE_NAME וכו').
     * כמחרוזות — הקבועים לא קיימים ב-Eclipse ישן (לפני Java 9), והערכים שלהם מחושבים בזמן ריצה (javac לא מעתיק אותם)
     */
    private static final String[] LAUNCH_MODULE_ATTRS = { "org.eclipse.jdt.launching.MODULE_NAME",
            "org.eclipse.jdt.launching.MODULE_CLI_OPTIONS", "org.eclipse.jdt.launching.DEFAULT_MODULE_CLI_OPTIONS",
            "org.eclipse.jdt.launching.ATTR_SPECIAL_ADD_MODULES" };
    /** ה-module path של התצורה — נמחק רק כשהתצורה משתמשת ב-classpath של הפרויקט (אחרת יש בו רשימה שהתלמיד בנה בעצמו) */
    private static final String LAUNCH_MODULEPATH = "org.eclipse.jdt.launching.MODULEPATH";

    private ModuleFix() {
    }

    /**
     * בודק פרויקט אחד: REMOVED — module-info.java נמחק (הספרייה ב-classpath, תצורות ההרצה תוקנו); BLOCKED — יש בו הגדרות אמיתיות
     * ולכן לא נמחק (מחזיר את זה רק בפעם הראשונה לכל פרויקט — כדי להסביר פעם אחת); NONE — אין מה לעשות
     */
    static Result check(IJavaProject jp) {
        try {
            IFile info = moduleInfo(jp);
            if (info == null || !hasDefaultPackageClass(jp)) return Result.NONE;
            if (!trivial(read(info))) return warnOnce(jp.getElementName()) ? Result.BLOCKED : Result.NONE;
            info.delete(IResource.FORCE | IResource.KEEP_HISTORY, null);   // אפשר לשחזר: קליק ימני על src ← Restore from Local History
        } catch (Throwable e) {
            return Result.NONE;   // Eclipse ישן בלי מודולים / קובץ לקריאה בלבד — לא נוגעים
        }
        try {
            ProjectSetup.libraryOnClasspath(jp);   // lib/dsviz.jar מה-module path חזרה ל-classpath (אחרת "is not accessible")
        } catch (Throwable ignored) {
            // LibraryEverywhere יתקן בסיבוב הבא (prepare מחזיר את הספרייה ל-classpath בפרויקט בלי module-info)
        }
        try {
            fixLaunchConfigs(jp.getElementName());
        } catch (Throwable ignored) {
            // תצורה שלא נשמרה — JDT מתעלם משם המודול בפרויקט שאינו מודולרי
        }
        return Result.REMOVED;
    }

    /** module-info.java של הפרויקט (בתיקיית קוד), או null — גם ב-Eclipse ישן בלי מודולים */
    private static IFile moduleInfo(IJavaProject jp) {
        try {
            org.eclipse.jdt.core.IModuleDescription md = jp.getModuleDescription();
            IResource r = md == null ? null : md.getResource();
            return r instanceof IFile && r.exists() && FILE.equals(r.getName()) ? (IFile) r : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /** יש בפרויקט קובץ Java בחבילת ברירת המחדל (ישר בתיקיית קוד, בלי package) */
    static boolean hasDefaultPackageClass(IJavaProject jp) throws CoreException {
        for (IClasspathEntry e : jp.getRawClasspath()) {
            if (e.getEntryKind() != IClasspathEntry.CPE_SOURCE) continue;
            IResource r = ResourcesPlugin.getWorkspace().getRoot().findMember(e.getPath());
            if (!(r instanceof IContainer) || !r.isAccessible()) continue;
            for (IResource m : ((IContainer) r).members()) {
                if (!(m instanceof IFile) || !"java".equals(m.getFileExtension()) || FILE.equals(m.getName())) continue;
                IJavaElement je = JavaCore.create((IFile) m);   // null כשהקובץ מוחרג מתיקיית הקוד (exclusion)
                if (je instanceof ICompilationUnit && je.getParent().getElementName().isEmpty() && jp.isOnClasspath(m)) return true;
            }
        }
        return false;
    }

    private static final String ID = "[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*";
    private static final String QUALIFIED = ID + "(?:\\s*\\.\\s*" + ID + ")*";
    /** מודולים שלא צריך module-info כדי להשתמש בהם: הספרייה שלנו (dsviz — ProjectSetup מוסיף אותה), ו-java.* / jdk.* של ה-JDK */
    private static final String HARMLESS = "(?:dsviz|(?:java|jdk)(?:\\s*\\.\\s*" + ID + ")+)";
    private static final Pattern TRIVIAL = Pattern.compile("\\s*module\\s+" + QUALIFIED + "\\s*\\{(?:\\s*requires\\s+(?:(?:transitive|static)\\s+)*"
            + HARMLESS + "\\s*;)*\\s*\\}\\s*");

    /**
     * module-info "ריק": בלי ההערות והרווחים הוא רק module NAME { }, אולי עם requires של dsviz / java.* / jdk.* — בלי exports, opens,
     * uses, provides, requires אחר, annotation או open. (את מה שנכתב בו אין צורך לשמור: בלי module-info הכול ב-classpath ונגיש)
     */
    static boolean trivial(String text) {
        return text != null && TRIVIAL.matcher(stripComments(text.replace("﻿", ""))).matches();
    }

    /** בלי הערות (הערת בלוק, והערת שורה עד סוף השורה). הערת בלוק שלא נסגרה — הפתיחה שלה נשארת, כך שהקובץ לא נחשב ריק */
    static String stripComments(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i), n = i + 1 < s.length() ? s.charAt(i + 1) : 0;
            if (c == '/' && n == '*') {
                int end = s.indexOf("*/", i + 2);
                if (end < 0) return b.append(" /*").toString();
                b.append(' ');
                i = end + 1;
            } else if (c == '/' && n == '/') {
                int nl = s.indexOf('\n', i);
                if (nl < 0) break;
                b.append('\n');
                i = nl;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static String read(IFile f) throws Exception {
        String cs;
        try {
            cs = f.getCharset();
        } catch (CoreException e) {
            cs = "UTF-8";
        }
        try (InputStream in = f.getContents(true)) {
            return new String(ProjectSetup.readAll(in), java.nio.charset.Charset.isSupported(cs) ? cs : "UTF-8");
        }
    }

    /** ההסבר לפרויקט הזה עוד לא הוצג (ונרשם שהוצג) */
    private static synchronized boolean warnOnce(String project) {
        IEclipsePreferences p = InstanceScope.INSTANCE.getNode(NODE);
        List<String> done = new ArrayList<String>(Arrays.asList(p.get(WARNED, "").split("/")));
        if (done.contains(project)) return false;
        done.add(project);
        StringBuilder b = new StringBuilder();
        for (String d : done) if (!d.isEmpty()) b.append(b.length() > 0 ? "/" : "").append(d);
        p.put(WARNED, b.toString());
        try {
            p.flush();
        } catch (Exception ignored) {
            // נשמר עד סגירת Eclipse
        }
        return true;
    }

    /**
     * תצורות ההרצה הרגילות (Java Application) של הפרויקט: בלי שם המודול ובלי שאר ההגדרות של מודולים — כך "Run" של התצורה הקיימת
     * (למשל Targil1 שהתלמיד כבר הריץ) רץ ב-classpath. מחזיר כמה תצורות תוקנו
     */
    static int fixLaunchConfigs(String project) throws CoreException {
        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType type = mgr.getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION);
        if (type == null) return 0;
        int fixed = 0;
        for (ILaunchConfiguration c : mgr.getLaunchConfigurations(type)) {
            if (!project.equals(c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, ""))) continue;
            boolean defaultPath = c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, true);
            List<String> drop = new ArrayList<String>();
            for (String a : LAUNCH_MODULE_ATTRS) if (c.hasAttribute(a)) drop.add(a);
            if (defaultPath && c.hasAttribute(LAUNCH_MODULEPATH)) drop.add(LAUNCH_MODULEPATH);
            if (drop.isEmpty()) continue;
            ILaunchConfigurationWorkingCopy wc = c.getWorkingCopy();
            for (String a : drop) wc.removeAttribute(a);
            wc.doSave();
            fixed++;
        }
        return fixed;
    }

    // ------------------------------------------------------------------ ההודעה

    /** "ayala" / "ayala, beni" */
    private static String names(List<String> projects) {
        StringBuilder b = new StringBuilder();
        for (String p : projects) b.append(b.length() > 0 ? ", " : "").append(p);
        return b.toString();
    }

    static String removedText(List<String> projects) {
        return "הקובץ module-info.java הוסר " + (projects.size() == 1 ? "מהפרויקט " : "מהפרויקטים ") + names(projects)
                + ": מחלקה שלא נמצאת בתוך package לא יכולה לרוץ בפרויקט עם module-info. עכשיו גם ההרצה הרגילה עובדת."
                + " (אפשר לשחזר אותו מ-Local History.)";
    }

    static String blockedText(List<String> projects) {
        return (projects.size() == 1 ? "בפרויקט " : "בפרויקטים ") + names(projects)
                + " ההרצה הרגילה (Run) לא תעבוד: מחלקה שלא נמצאת בתוך package לא יכולה לרוץ בפרויקט עם module-info."
                + " מעבירים את המחלקה לתוך package (קליק ימני על הקובץ ← Refactor ← Move), או מוחקים את module-info.java."
                + " (יש בו הגדרות, ולכן הוא לא נמחק אוטומטית.)";
    }

    /** הודעה קטנה בפינה (Toast — לא חלון שצריך לסגור), אחת לכל סבב בדיקה */
    static void notify(List<String> removed, List<String> blocked) {
        if (removed.isEmpty() && blocked.isEmpty()) return;
        final Toast.Spec s = new Toast.Spec();
        s.warn = !blocked.isEmpty();
        s.title = !removed.isEmpty() ? "ההרצה הרגילה תוקנה" : "ההרצה הרגילה לא תעבוד";
        StringBuilder t = new StringBuilder();
        if (!removed.isEmpty()) t.append(removedText(removed));
        if (!blocked.isEmpty()) t.append(t.length() > 0 ? "\n\n" : "").append(blockedText(blocked));
        s.text = t.toString();
        s.hideMs = 20000;
        try {
            if (!PlatformUI.isWorkbenchRunning()) return;   // בלי חלון (הרצה בלי ממשק) — רק התיקון
            Display.getDefault().asyncExec(() -> show(s, 0));
        } catch (Throwable ignored) {
            // בלי הודעה — התיקון עצמו כבר נעשה
        }
    }

    /** בחלון הראשי של Eclipse; אם הוא עוד לא מוכן (מיד אחרי ההפעלה) — מנסים שוב כל 2 שניות, עד חצי דקה */
    private static void show(final Toast.Spec s, final int attempt) {
        try {
            IWorkbenchWindow w = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (w == null && PlatformUI.getWorkbench().getWorkbenchWindows().length > 0) w = PlatformUI.getWorkbench().getWorkbenchWindows()[0];
            if (w == null || w.getShell() == null || w.getShell().isDisposed() || !w.getShell().isVisible()) {
                if (attempt < 15) Display.getDefault().timerExec(2000, () -> show(s, attempt + 1));
                return;
            }
            Toast.show(w.getShell(), s);
        } catch (Throwable ignored) {
            // Eclipse נסגר באמצע
        }
    }
}
