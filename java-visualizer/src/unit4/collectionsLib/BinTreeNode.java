package unit4.collectionsLib;

/**
 * צומת בעץ בינארי — בשם ובפעולות של הגרסה הישנה של Unit4 (getInfo / setInfo).
 * זהה ל-BinNode (ומוצג באותה צורה), כדי שתרגילים ישנים ירוצו בלי שינוי.
 */
public class BinTreeNode<T> extends BinNode<T> {

    public BinTreeNode(T info) {
        super(info);
    }

    public BinTreeNode(BinTreeNode<T> left, T info, BinTreeNode<T> right) {
        super(left, info, right);
    }

    public T getInfo() {
        return getValue();
    }

    public void setInfo(T info) {
        setValue(info);
    }

    @Override
    public BinTreeNode<T> getLeft() {
        return (BinTreeNode<T>) super.getLeft();
    }

    @Override
    public BinTreeNode<T> getRight() {
        return (BinTreeNode<T>) super.getRight();
    }

    public void setLeft(BinTreeNode<T> left) {
        super.setLeft(left);
    }

    public void setRight(BinTreeNode<T> right) {
        super.setRight(right);
    }
}
