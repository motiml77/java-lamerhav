public class Million {
    public static void main(String[] args) {
        long sum = 0;
        for (int i = 0; i < 1000000; i++) {
            sum += i;
        }
        System.out.println("sum=" + sum);
    }
}
