package unit4.collectionsLib;

import java.util.ArrayList;

import unit4.collectionsLib.Model.Mark;

/**
 * תור — בדיוק כמו בספריית הבגרות (unit4.collectionsLib).
 * כל פעולה מתועדת אוטומטית ומוצגת בחלון הוויזואליזציה.
 */
public class Queue<T> {
    final ArrayList<T> items = new ArrayList<T>(); // ראש התור ראשון

    public Queue() {
        Recorder.register(this);
        Recorder.create(this, "נוצר תור ריק: " + Recorder.nameOf(this));
    }

    /** הכנסת x לסוף התור */
    public void insert(T x) {
        items.add(x);
        Recorder.change(this, Mark.NEW, null, Recorder.nameOf(this) + ".insert(" + Recorder.str(x) + ")");
    }

    /** הוצאת האיבר שבראש התור והחזרתו */
    public T remove() {
        if (items.isEmpty())
            throw new RuntimeException("remove() על תור ריק (" + Recorder.nameOf(this) + ")");
        T x = items.remove(0);
        Recorder.change(this, Mark.NONE, Recorder.str(x),
                Recorder.nameOf(this) + ".remove()  →  " + Recorder.str(x));
        return x;
    }

    /** החזרת האיבר שבראש התור בלי להוציא אותו */
    public T head() {
        if (items.isEmpty())
            throw new RuntimeException("head() על תור ריק (" + Recorder.nameOf(this) + ")");
        T x = items.get(0);
        Recorder.read(this, Recorder.nameOf(this) + ".head()  →  " + Recorder.str(x));
        return x;
    }

    public boolean isEmpty() {
        boolean e = items.isEmpty();
        Recorder.check(this, Recorder.nameOf(this) + ".isEmpty()  →  " + e);
        return e;
    }

    /** מחזיר את התור כמחרוזת, ראש התור ראשון: [1,2,3] */
    public String toString() {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) b.append(',');
            b.append(items.get(i));
        }
        return b.append(']').toString();
    }
}
