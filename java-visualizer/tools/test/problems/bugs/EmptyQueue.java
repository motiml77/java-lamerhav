package bugs;

import unit4.collectionsLib.*;

public class EmptyQueue {
    public static void main(String[] args) {
        Queue<Integer> q = new Queue<Integer>();
        q.insert(5);
        q.remove();
        System.out.println(q.head());
    }
}
