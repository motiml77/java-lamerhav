package il.lamerhav.dsviz.intellij;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;

import il.lamerhav.dsviz.IjShared;

/** חלון הכלים "DSViz" (בצד ימין): כפתורים גדולים עם טקסט בעברית לכל היכולות — למי שלא מוצא את התפריט */
public final class DsvizToolWindow implements ToolWindowFactory, DumbAware {
    static final String ID = "DSViz";

    @Override
    public void createToolWindowContent(Project project, ToolWindow window) {
        Content c = ContentFactory.getInstance().createContent(build(project), "", false);
        window.getContentManager().addContent(c);
    }

    /** אחרי כיבוי / הפעלה: בונים מחדש */
    static void refresh(Project project) {
        ToolWindow w = ToolWindowManager.getInstance(project).getToolWindow(ID);
        if (w == null || w.getContentManager().getContentCount() == 0) return;
        Content c = w.getContentManager().getContent(0);
        if (c != null) c.setComponent(build(project));
    }

    static JComponent build(Project project) {
        JPanel list = new JPanel(new GridLayout(0, 1, 0, JBUI.scale(6)));
        if (Dsviz.on()) {
            list.add(button(project, "DSViz.NewExercise", "תרגיל חדש"));
            if (IjShared.workerConfigured()) list.add(button(project, "DSViz.Submit", "שליחת תרגיל (PDF / Word / תמונה)"));
            list.add(button(project, "DSViz.Insert", "הוסף מבנה נתונים"));
            list.add(button(project, "DSViz.Run", "הרצה ויזואלית"));
            list.add(button(project, "DSViz.Examples", "דוגמאות פתורות"));
            list.add(button(project, "DSViz.Help", "הדרכה לשימוש (?)"));
        }
        list.add(button(project, "DSViz.Power", null));
        JPanel top = new JPanel(new BorderLayout(0, JBUI.scale(10)));
        top.add(list, BorderLayout.NORTH);
        top.add(Forms.muted("<html><div dir='rtl'>" + (Dsviz.on()
                ? "הרצה ויזואלית: חלון שמראה כל שורה, את המשתנים ואת מבני הנתונים. גם בלחיצה ימנית בקוד, בתפריט Run, בכפתור שליד main ובקיצור Ctrl+Alt+R."
                : "DSViz כבוי — לחצו \"הפעלת DSViz\" כדי להחזיר את כל הכלים.") + "</div></html>"), BorderLayout.CENTER);
        top.setBorder(JBUI.Borders.empty(10));
        JPanel root = new WidthPanel();
        root.add(top, BorderLayout.NORTH);
        Forms.rtl(root);
        JBScrollPane sp = new JBScrollPane(root, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(JBUI.Borders.empty());
        return sp;
    }

    /** פאנל שתמיד ברוחב החלון (בלי גלילה הצידה) — הטקסט נשבר לשורות והכפתורים נמתחים */
    private static final class WidthPanel extends JPanel implements javax.swing.Scrollable {
        WidthPanel() {
            super(new BorderLayout());
        }

        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return JBUI.scale(16); }
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return r.height; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static JButton button(Project project, String id, String text) {
        AnAction a = ActionManager.getInstance().getAction(id);
        JButton b = new JButton();
        if (a != null) {
            boolean on = Dsviz.on();
            b.setText(text != null ? text : on ? "כיבוי DSViz" : "הפעלת DSViz");
            b.setIcon(id.equals("DSViz.Power") ? (on ? Dsviz.POWER_OFF : Dsviz.POWER_ON) : a.getTemplatePresentation().getIcon());
            b.setToolTipText(a.getTemplatePresentation().getDescription());
            b.addActionListener(e -> ActionUtil.invokeAction(a, b, ActionPlaces.TOOLWINDOW_CONTENT, null, null));
        }
        b.setHorizontalAlignment(SwingConstants.LEADING);
        b.setIconTextGap(JBUI.scale(8));
        b.setPreferredSize(new Dimension(JBUI.scale(160), JBUI.scale(34)));
        return b;
    }
}
