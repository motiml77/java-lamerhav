// רקורסיה עמוקה אבל תקינה: 60 קריאות מקוננות עד תנאי העצירה, והן חוזרות — בלי אזהרה
public class DeepOk {
    static int sum(int n) {
        if (n == 0) return 0;
        return n + sum(n - 1);
    }

    public static void main(String[] args) {
        System.out.println(sum(60));
    }
}
