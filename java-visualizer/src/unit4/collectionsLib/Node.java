package unit4.collectionsLib;

import unit4.collectionsLib.Model.Mark;

/**
 * חוליה ברשימה מקושרת — בדיוק כמו בספריית הבגרות (unit4.collectionsLib).
 * כל פעולה מתועדת אוטומטית ומוצגת בחלון הוויזואליזציה.
 */
public class Node<T> {
    T value;
    Node<T> next;
    int vizId;   // מזהה בהרצה שורה-שורה (נקבע ב-Recorder.register)

    public Node(T value) {
        Recorder.sync();
        this.value = value;
        Recorder.register(this);
        Recorder.create(this, "new Node(" + Recorder.str(value) + ")");
    }

    public Node(T value, Node<T> next) {
        Recorder.sync();
        this.value = value;
        this.next = next;
        Recorder.register(this);
        Recorder.create(this, "new Node(" + Recorder.str(value) + ", " + Recorder.nodeRef(next) + ")");
    }

    public T getValue() {
        Recorder.sync();
        Recorder.read(this, ref() + ".getValue()  →  " + Recorder.str(value));
        return value;
    }

    public Node<T> getNext() {
        Recorder.sync();
        Recorder.read(this, ref() + ".getNext()  →  " + Recorder.nodeRef(next));
        return next;
    }

    public boolean hasNext() {
        Recorder.sync();
        Recorder.check(this, ref() + ".hasNext()  →  " + (next != null));
        return next != null;
    }

    public void setValue(T value) {
        Recorder.sync();
        String old = Recorder.str(this.value);
        this.value = value;
        Recorder.change(this, Mark.CHANGED, null, "[" + old + "].setValue(" + Recorder.str(value) + ")");
    }

    public void setNext(Node<T> next) {
        Recorder.sync();
        this.next = next;
        Recorder.change(this, Mark.CHANGED, null, ref() + ".setNext(" + Recorder.nodeRef(next) + ")");
    }

    // ---- תאימות לגרסה הישנה של Unit4 (getInfo / setInfo)

    /** כמו getValue — מהגרסה הישנה של Unit4 */
    public T getInfo() {
        return getValue();
    }

    /** כמו setValue — מהגרסה הישנה של Unit4 */
    public void setInfo(T value) {
        setValue(value);
    }

    private String ref() {
        return Recorder.nodeRef(this);
    }

    /** כל הרשימה מהחוליה הזו והלאה, למשל: 5 -> 2 -> 8 -> null */
    public String toString() {
        return Viz.text(this);
    }
}
