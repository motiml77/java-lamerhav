import unit4.collectionsLib.*;

public class Warnings {
    public static boolean same(Stack<Integer> s, Queue<Integer> q) {
        return s.top() == q.head();
    }

    public static void print(Node<Integer> p) {
        while (p != null) {
            System.out.println(p.getValue());
        }
    }

    public static String upper(String s) {
        s.toUpperCase();
        if (s == "ABC") return s;
        return "";
    }
}
