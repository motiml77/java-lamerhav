package loops;

import unit4.collectionsLib.*;

// לולאות שעוברות על מבנים: מערך (אינדקס ו-for-each), תור, מחסנית, רשימה מקושרת, ומקוננת על מערך
public class Data {
    public static void main(String[] args) {
        int[] arr = { 7, 3, 9 };
        int max = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > max) max = arr[i];
        }
        for (int x : arr) {
            max += x;
        }
        Queue<Integer> q = new Queue<Integer>();
        q.insert(4);
        q.insert(5);
        while (!q.isEmpty()) {
            int v = q.remove();
            max += v;
        }
        Stack<String> st = new Stack<String>();
        st.push("a");
        st.push("b");
        while (!st.isEmpty()) {
            st.pop();
        }
        Node<Integer> lst = new Node<Integer>(1, new Node<Integer>(2));
        Node<Integer> p = lst;
        while (p != null) {
            max += p.getValue();
            p = p.getNext();
        }
        int count = 0;
        for (int i = 0; i < arr.length; i++) {
            for (int j = i + 1; j < arr.length; j++) {
                if (arr[i] > arr[j]) count++;
            }
        }
        System.out.println(max + " " + count);
    }
}
