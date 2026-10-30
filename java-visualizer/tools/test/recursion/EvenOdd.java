// רקורסיה הדדית
public class EvenOdd {
    static boolean isEven(int n) {
        if (n == 0) return true;
        return isOdd(n - 1);
    }

    static boolean isOdd(int n) {
        if (n == 0) return false;
        return isEven(n - 1);
    }

    public static void main(String[] args) {
        System.out.println(isEven(3));
    }
}
