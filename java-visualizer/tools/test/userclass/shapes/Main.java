package shapes;

import unit4.collectionsLib.*;

public class Main {
    public static void main(String[] args) {
        Point p = new Point(3, 4);
        MyNode a = new MyNode(1, null);
        MyNode b = new MyNode(2, a);
        a.next = b;
        Person dana = new Person("Dana", p);
        Stack<Point> st = new Stack<Point>();
        st.push(p);
        Point[] arr = { p, new Point(0, 0) };
        String word = "abcdef";
        int last = word.length() - 1;
        char ch = word.charAt(last);
        String mid = word.substring(1, last);
        int pos = word.indexOf('d');
        int cmp = word.compareTo("abz");
        String up = word.toUpperCase();
        System.out.println(dana + " " + ch + mid + pos + cmp + up + arr.length);
    }
}
