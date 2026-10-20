package examples;

import unit4.collectionsLib.*;

/** דוגמאות רקורסיה: עצרת, סכום מערך, והדפסת מחסנית מהתחתית */
public class RecursionExamples {

    /** עצרת: n! = n * (n-1)!   (למשל 4! = 4 * 3 * 2 * 1 = 24) */
    public static int fact(int n) {
        if (n <= 1) {                    // מקרה בסיס: כאן עוצרים — 1! = 1
            return 1;
        }
        int rest = fact(n - 1);          // rest = התשובה של הקריאה הקטנה יותר: (n-1)!
        return n * rest;                 // מכפילים את n בתשובה שחזרה
    }

    /** סכום האיברים במערך מהמקום i עד הסוף */
    public static int sum(int[] arr, int i) {
        if (i == arr.length) {           // מקרה בסיס: עברנו את כל המערך — אין מה לסכום
            return 0;
        }
        int rest = sum(arr, i + 1);      // rest = סכום האיברים שאחרי arr[i]
        return arr[i] + rest;            // האיבר הנוכחי + הסכום של כל השאר
    }

    /** מדפיסה את המחסנית מהתחתית לראש (ואחר כך המחסנית חוזרת למצבה) */
    public static void printBottomUp(Stack<Integer> stk) {
        if (stk.isEmpty()) {             // מקרה בסיס: אין מה להדפיס
            return;
        }
        int x = stk.pop();               // x = הראש. מוציאים אותו כדי שהקריאה הבאה תתעסק במה שמתחתיו
        printBottomUp(stk);              // קודם מדפיסים את כל מה שנשאר (שהוא מתחת ל-x)
        System.out.println(x);           // ורק אחר כך את x — לכן התחתית יוצאת ראשונה
        stk.push(x);                     // בדרך חזרה: מחזירים את x למקומו
    }

    public static void main(String[] args) {
        System.out.println("4! = " + fact(4));

        int[] arr = {4, 7, 1};
        System.out.println("סכום: " + sum(arr, 0));

        Stack<Integer> stk = new Stack<Integer>();
        stk.push(1);
        stk.push(2);
        stk.push(3);
        printBottomUp(stk);
    }
}
