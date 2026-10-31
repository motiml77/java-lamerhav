// מעקב המשתנים ברקורסיה עמוקה: ברשימת הפעולות — הפנימיות, "…" במקום האמצעיות, ו-main למטה (עם המשתנים שלו)
public class Deep {
    static int sum(int n) {
        if (n == 0) return 0;
        return n + sum(n - 1);
    }

    public static void main(String[] args) {
        int start = 13;
        int total = sum(start);
        System.out.println(total);
    }
}
