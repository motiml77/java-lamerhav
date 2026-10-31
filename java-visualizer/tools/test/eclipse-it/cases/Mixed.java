import unit4.collectionsLib.*;

public class Mixed {
    public static int sum(int[] arr) {
        int s = 0;
        for (int i = 0; i <= arr.length; i++) {
            s += arr[i];
        }
        return s;
    }

    public static void drain(Stack<Integer> st) {
        while (!st.isEmpty()) {
            int a = st.pop();
            int b = st.pop();
            System.out.println(a + b);
        }
        st.isEmpty();
    }

    public static double avg(int total, int n) {
        double r = total / n;
        return r;
    }

    public static void main(String[] args) {
        Stack<Integer> s = new Stack<String>();
        drain(s);
    }
}
