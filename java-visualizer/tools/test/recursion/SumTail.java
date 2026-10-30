// רקורסיית זנב: הקריאה היא הדבר האחרון
public class SumTail {
    static int sum(int n, int acc) {
        if (n == 0) return acc;
        return sum(n - 1, acc + n);
    }

    public static void main(String[] args) {
        System.out.println(sum(4, 0));
    }
}
