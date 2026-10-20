package examples;

import unit4.collectionsLib.*;

/** דוגמאות תור: ספירה בלי להרוס, והוצאת כל המופעים של ערך */
public class QueueExamples {

    /** כמה איברים בתור — התור חוזר למצבו המקורי */
    public static int size(Queue<Integer> q1) {
        Queue<Integer> tmp = new Queue<Integer>();   // עזר: שומר את האיברים כדי להחזיר אותם
        int count = 0;                               // כאן סופרים
        while (!q1.isEmpty()) {
            tmp.insert(q1.remove());
            count++;
        }
        while (!tmp.isEmpty()) {
            q1.insert(tmp.remove());
        }
        return count;
    }

    /** מוחקת מהתור את כל המופעים של x (שומרת על הסדר) */
    public static void removeAll(Queue<Integer> q1, int x) {
        q1.insert(null);                 // סימן לסוף הסיבוב
        Integer curr = q1.remove();      // האיבר הנוכחי
        while (curr != null) {
            if (curr != x) q1.insert(curr);   // כל מה שלא x חוזר לסוף התור
            curr = q1.remove();
        }
    }

    public static void main(String[] args) {
        Queue<Integer> q1 = new Queue<Integer>();
        q1.insert(5);   // הראשון שנכנס — בראש התור
        q1.insert(3);
        q1.insert(5);
        q1.insert(8);
        q1.insert(5);
        q1.insert(2);
        System.out.println("גודל: " + size(q1));
        removeAll(q1, 5);
        System.out.println("אחרי מחיקת 5: " + q1);
    }
}
