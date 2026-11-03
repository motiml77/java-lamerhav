public class Rec {
    static int f(int n) {
        return f(n + 1) + 1;
    }

    public static void main(String[] args) {
        System.out.println(f(0));
    }
}
