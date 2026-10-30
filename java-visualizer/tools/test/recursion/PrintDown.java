// רקורסיית זנב בפעולה void
public class PrintDown {
    static void down(int n) {
        if (n == 0) return;
        System.out.println(n);
        down(n - 1);
    }

    public static void main(String[] args) {
        down(3);
    }
}
