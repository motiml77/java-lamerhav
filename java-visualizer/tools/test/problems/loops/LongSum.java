package loops;

public class LongSum {
    // לולאה ארוכה אבל תקינה (סכום 1..2000) — אסור לעצור אותה
    public static void main(String[] args) {
        int i = 1, sum = 0;
        while (i <= 2000) {
            sum = sum + i;
            i++;
        }
        System.out.println("סכום: " + sum);
    }
}
