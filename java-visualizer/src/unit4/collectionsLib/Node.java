package unit4.collectionsLib;

import unit4.collectionsLib.Model.Mark;

/**
 * חוליה ברשימה מקושרת — בדיוק כמו בספריית הבגרות (unit4.collectionsLib).
 * כל פעולה מתועדת אוטומטית ומוצגת בחלון הוויזואליזציה.
 */
public class Node<T> {
    T value;
    Node<T> next;

    public Node(T value) {
        this.value = value;
        Recorder.register(this);
        Recorder.create(this, "new Node(" + Recorder.str(value) + ")");
    }

    public Node(T value, Node<T> next) {
        this.value = value;
        this.next = next;
        Recorder.register(this);
        Recorder.create(this, "new Node(" + Recorder.str(value) + ", " + Recorder.nodeRef(next) + ")");
    }

    public T getValue() {
        Recorder.read(this, ref() + ".getValue()  →  " + Recorder.str(value));
        return value;
    }

    public Node<T> getNext() {
        Recorder.read(this, ref() + ".getNext()  →  " + Recorder.nodeRef(next));
        return next;
    }

    public boolean hasNext() {
        Recorder.check(this, ref() + ".hasNext()  →  " + (next != null));
        return next != null;
    }

    public void setValue(T value) {
        String old = Recorder.str(this.value);
        this.value = value;
        Recorder.change(this, Mark.CHANGED, null, "[" + old + "].setValue(" + Recorder.str(value) + ")");
    }

    public void setNext(Node<T> next) {
        this.next = next;
        Recorder.change(this, Mark.CHANGED, null, ref() + ".setNext(" + Recorder.nodeRef(next) + ")");
    }

    private String ref() {
        return Recorder.nodeRef(this);
    }

    public String toString() {
        return String.valueOf(value);
    }
}
