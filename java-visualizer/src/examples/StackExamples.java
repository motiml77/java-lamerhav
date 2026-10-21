package examples;

import unit4.collectionsLib.*;

/** דוגמאות מחסנית: היפוך, והכנסה לתחתית ברקורסיה */
public class StackExamples {

    /** מחזירה מחסנית חדשה הפוכה. המחסנית המקורית נשארת כמו שהייתה */
    public static Stack<Integer> reverse(Stack<Integer> stk) {
        Stack<Integer> tmp = new Stack<Integer>();      // עזר: שומרת עותק כדי להחזיר את stk בסוף
        Stack<Integer> result = new Stack<Integer>();   // התוצאה: האיברים בסדר הפוך
        while (!stk.isEmpty()) {
            int x = stk.pop();                          // האיבר שהוצאנו עכשיו
            result.push(x);
            tmp.push(x);
        }
        while (!tmp.isEmpty()) {
            stk.push(tmp.pop());                        // מחזירים ל-stk את מה שהוצאנו ממנו
        }
        return result;
    }

    /** רקורסיה: הכנסת x לתחתית המחסנית */
    public static void pushBottom(Stack<Integer> stk, int x) {
        if (stk.isEmpty()) {            // מקרה בסיס: המחסנית ריקה — התחתית היא כאן
            stk.push(x);
            return;
        }
        int top = stk.pop();            // מוציאים את הראש ושומרים אותו (כל קריאה שומרת איבר משלה)
        pushBottom(stk, x);             // קריאה רקורסיבית: מכניסים את x לתחתית של מה שנשאר
        stk.push(top);                  // בדרך חזרה: מחזירים את האיבר שהוצאנו
    }

    public static void main(String[] args) {
        Stack<Integer> stk = new Stack<Integer>();
        stk.push(1);
        stk.push(2);
        stk.push(3);
        stk.push(4);   // האחרון שנכנס — בראש המחסנית
        Stack<Integer> rev = reverse(stk);
        System.out.println("הפוכה: " + rev);

        pushBottom(stk, 99);
        System.out.println("אחרי pushBottom: " + stk);
    }
}
