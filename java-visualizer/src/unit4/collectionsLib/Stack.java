package unit4.collectionsLib;

import java.util.ArrayList;

import unit4.collectionsLib.Model.Mark;

/**
 * מחסנית — בדיוק כמו בספריית הבגרות (unit4.collectionsLib).
 * כל פעולה מתועדת אוטומטית ומוצגת בחלון הוויזואליזציה.
 */
public class Stack<T> {
    final ArrayList<T> items = new ArrayList<T>(); // התחתית ראשונה, הראש אחרון

    public Stack() {
        Recorder.register(this);
        Recorder.create(this, "נוצרה מחסנית ריקה: " + Recorder.nameOf(this));
    }

    /** הכנסת x לראש המחסנית */
    public void push(T x) {
        items.add(x);
        Recorder.change(this, Mark.NEW, null, Recorder.nameOf(this) + ".push(" + Recorder.str(x) + ")");
    }

    /** הוצאת האיבר שבראש המחסנית והחזרתו */
    public T pop() {
        if (items.isEmpty())
            throw new RuntimeException("pop() על מחסנית ריקה (" + Recorder.nameOf(this) + ")");
        T x = items.remove(items.size() - 1);
        Recorder.change(this, Mark.NONE, Recorder.str(x),
                Recorder.nameOf(this) + ".pop()  →  " + Recorder.str(x));
        return x;
    }

    /** החזרת האיבר שבראש המחסנית בלי להוציא אותו */
    public T top() {
        if (items.isEmpty())
            throw new RuntimeException("top() על מחסנית ריקה (" + Recorder.nameOf(this) + ")");
        T x = items.get(items.size() - 1);
        Recorder.read(this, Recorder.nameOf(this) + ".top()  →  " + Recorder.str(x));
        return x;
    }

    public boolean isEmpty() {
        boolean e = items.isEmpty();
        Recorder.check(this, Recorder.nameOf(this) + ".isEmpty()  →  " + e);
        return e;
    }

    /** מחזיר את המחסנית כמחרוזת, הראש ראשון: [5,4,3] */
    public String toString() {
        StringBuilder b = new StringBuilder("[");
        for (int i = items.size() - 1; i >= 0; i--) {
            b.append(items.get(i));
            if (i > 0) b.append(',');
        }
        return b.append(']').toString();
    }
}
