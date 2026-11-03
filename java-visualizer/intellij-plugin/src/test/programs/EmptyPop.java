import unit4.collectionsLib.*;

public class EmptyPop {
    public static void main(String[] args) {
        Stack<Integer> s = new Stack<Integer>();
        s.push(1);
        s.pop();
        int x = s.pop();
        System.out.println(x);
    }
}
