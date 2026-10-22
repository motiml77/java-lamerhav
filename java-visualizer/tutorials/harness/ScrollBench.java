import il.lamerhav.dsviz.*;
import java.util.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.widgets.*;

/** מודד כמה זמן לוקחת גלילה בחלון "תרגיל חדש"/"הוסף מבנה נתונים" (צעדים קטנים של הגלגלת, עם ציור מלא אחרי כל צעד) */
public class ScrollBench {
    static ScrolledComposite find(Control c) {
        if (c instanceof ScrolledComposite) return (ScrolledComposite) c;
        if (c instanceof Composite) for (Control k : ((Composite) c).getChildren()) { ScrolledComposite r = find(k); if (r != null) return r; }
        return null;
    }
    public static void main(String[] a) throws Exception {
        Display display = new Display();
        Shell parent = new Shell(display);
        String kind = a[0];
        display.timerExec(1500, () -> {
            Shell dlg = null;
            for (Shell s : display.getShells()) if (s != parent && s.isVisible()) dlg = s;
            dlg.setSize(900, 430);
            for (int i = 0; i < 20; i++) { display.readAndDispatch(); }
            ScrolledComposite sc = find(dlg);
            System.out.println("sc=" + sc.getBounds() + " vis=" + sc.isVisible() + " content=" + sc.getContent().getSize() + " client=" + sc.getClientArea() + " shell=" + dlg.getBounds());
            long[] best = { Long.MAX_VALUE };
            final int[] paints = { 0 }; final long[] area = { 0 }; final Map<String, Integer> by = new TreeMap<>();
            display.addFilter(SWT.Paint, e -> { paints[0]++; area[0] += (long) e.width * e.height; by.merge(e.widget.getClass().getSimpleName(), 1, Integer::sum); });
            int range = sc.getContent().getSize().y - sc.getClientArea().height;
            for (int round = 0; round < 5; round++) {
                sc.setOrigin(0, 0); dlg.update();
                paints[0] = 0; area[0] = 0; by.clear();
                long t0 = System.nanoTime();
                int steps = 0;
                for (int y = 0; y < range; y += 24, steps++) { sc.setOrigin(0, y); while (display.readAndDispatch()) { } dlg.update(); }
                for (int y = range; y > 0; y -= 24, steps++) { sc.setOrigin(0, y); while (display.readAndDispatch()) { } dlg.update(); }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                best[0] = Math.min(best[0], ms);
                if (round == 4) System.out.println(kind + ": range=" + range + "px, " + steps + " steps, best " + best[0] + " ms (" + (best[0] / Math.max(1, steps)) + " ms/step), paint events=" + paints[0] + ", painted px=" + area[0] / 1000 + "k " + by);
            }
            dlg.close();
        });
        if (kind.equals("ex")) new ExerciseDialog(parent, new ArrayList<org.eclipse.jdt.core.IJavaProject>(), "DataStructures", null, "stack_queue").open();
        else new InsertDialog(parent, "stack_queue", "class A {\n  void f() {\n    \n  }\n}\n", 26, "    ").open();
        display.dispose();
    }
}
