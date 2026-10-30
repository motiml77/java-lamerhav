package bugs;

public class Index {
    public static void main(String[] args) {
        int[] a = { 4, 7, 1 };
        int s = 0;
        for (int i = 0; i <= a.length; i++) s += a[i];
        System.out.println(s);
    }
}
