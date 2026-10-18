package unit4.collectionsLib;

import unit4.collectionsLib.Model.Mark;

/**
 * צומת בעץ בינארי — בדיוק כמו בספריית הבגרות (unit4.collectionsLib).
 * כל פעולה מתועדת אוטומטית ומוצגת בחלון הוויזואליזציה.
 */
public class BinNode<T> {
    T value;
    BinNode<T> left, right;

    public BinNode(T value) {
        this.value = value;
        Recorder.register(this);
        Recorder.create(this, "new BinNode(" + Recorder.str(value) + ")");
    }

    public BinNode(BinNode<T> left, T value, BinNode<T> right) {
        this.left = left;
        this.value = value;
        this.right = right;
        Recorder.register(this);
        Recorder.create(this, "new BinNode(" + Recorder.nodeRef(left) + ", " + Recorder.str(value) + ", "
                + Recorder.nodeRef(right) + ")");
    }

    public T getValue() {
        Recorder.read(this, ref() + ".getValue()  →  " + Recorder.str(value));
        return value;
    }

    public BinNode<T> getLeft() {
        Recorder.read(this, ref() + ".getLeft()  →  " + Recorder.nodeRef(left));
        return left;
    }

    public BinNode<T> getRight() {
        Recorder.read(this, ref() + ".getRight()  →  " + Recorder.nodeRef(right));
        return right;
    }

    public boolean hasLeft() {
        Recorder.check(this, ref() + ".hasLeft()  →  " + (left != null));
        return left != null;
    }

    public boolean hasRight() {
        Recorder.check(this, ref() + ".hasRight()  →  " + (right != null));
        return right != null;
    }

    public void setValue(T value) {
        String old = Recorder.str(this.value);
        this.value = value;
        Recorder.change(this, Mark.CHANGED, null, "[" + old + "].setValue(" + Recorder.str(value) + ")");
    }

    public void setLeft(BinNode<T> left) {
        this.left = left;
        Recorder.change(this, Mark.CHANGED, null, ref() + ".setLeft(" + Recorder.nodeRef(left) + ")");
    }

    public void setRight(BinNode<T> right) {
        this.right = right;
        Recorder.change(this, Mark.CHANGED, null, ref() + ".setRight(" + Recorder.nodeRef(right) + ")");
    }

    private String ref() {
        return Recorder.nodeRef(this);
    }

    public String toString() {
        return String.valueOf(value);
    }
}
