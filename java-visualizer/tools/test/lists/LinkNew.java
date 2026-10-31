import unit4.collectionsLib.*;

public class LinkNew {
    public static void main(String[] args) {
        Node<Integer> list = new Node<Integer>(3, new Node<Integer>(7, new Node<Integer>(1)));
        Node<Integer> n = new Node<Integer>(5);
        n.setNext(list);
        list = n;
        Node<Integer> p = list;
        while (p.getNext() != null)
            p = p.getNext();
        Node<Integer> t = new Node<Integer>(9);
        p.setNext(t);
        p = list.getNext();
        Node<Integer> q = new Node<Integer>(4);
        q.setNext(p.getNext());
        p.setNext(q);
        System.out.println(list);
    }
}
