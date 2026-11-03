import unit4.collectionsLib.*;

public class EmptyRemove {
    public static void main(String[] args) {
        Queue<Integer> q = new Queue<Integer>();
        q.insert(5);
        q.remove();
        int y = q.remove();
        System.out.println(y);
    }
}
