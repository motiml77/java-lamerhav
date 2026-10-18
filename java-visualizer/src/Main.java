import unit4.collectionsLib.*;

/**
 * ===== תבנית לתרגיל =====
 * 1. כותבים את הפונקציה למטה.
 * 2. ב-main בונים מבנה לבדיקה בשורה אחת (Build) וקוראים לפונקציה.
 * 3. מריצים (Run) — נפתח חלון שמראה כל צעד: המבנים, שורת הקוד, מחסנית הקריאות והפלט.
 *
 * ניווט בחלון: חץ שמאלה / רווח = צעד הבא,  חץ ימינה = צעד קודם,  P = הפעלה אוטומטית.
 */
public class Main {

    // >>> כאן כותבים את הפונקציה של התרגיל
    // דוגמה: סכום האיברים במחסנית — בלי לשנות את המחסנית המקורית
    public static int sum(Stack<Integer> s) {
        Stack<Integer> temp = new Stack<Integer>();
        int total = 0;
        while (!s.isEmpty()) {
            int x = s.pop();
            total += x;
            temp.push(x);
        }
        while (!temp.isEmpty()) {
            s.push(temp.pop());
        }
        return total;
    }

    public static void main(String[] args) {
        Stack<Integer> s = Build.stack(4, 7, 1, 9);   // 9 בראש המחסנית
        System.out.println("הסכום: " + sum(s));
        System.out.println("המחסנית אחרי: " + s);
    }
}
