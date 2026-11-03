public class StudentStack {
    public static void main(String[] args) {
        Stack s = new Stack();
        s.push(3);
        System.out.println(s.top());
        java.util.Stack<Integer> js = new java.util.Stack<>();
        js.push(4);
        System.out.println(js.peek());
    }
}

class Stack {
    private int[] a = new int[10];
    private int n;

    void push(int x) {
        a[n++] = x;
    }

    int top() {
        return a[n - 1];
    }
}
