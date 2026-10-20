package examples;

import unit4.collectionsLib.*;

/** דוגמאות תור: ספירה בלי להרוס, והוצאת כל המופעים של ערך */
public class QueueExamples {

    /** כמה איברים בתור — התור חוזר למצבו המקורי */
    public static int size(Queue<Integer> q) {
        Queue<Integer> temp = new Queue<Integer>();
        int count = 0;
        while (!q.isEmpty()) {
            temp.insert(q.remove());
            count++;
        }
        while (!temp.isEmpty()) {
            q.insert(temp.remove());
        }
        return count;
    }

    /** מוחקת מהתור את כל המופעים של x (שומרת על הסדר) */
    public static void removeAll(Queue<Integer> q, int x) {
        q.insert(null);                 // סימן לסוף הסיבוב
        Integer cur = q.remove();
        while (cur != null) {
            if (cur != x) q.insert(cur);
            cur = q.remove();
        }
    }

    public static void main(String[] args) {
        Queue<Integer> q = new Queue<Integer>();
        q.insert(5);   // הראשון שנכנס — בראש התור
        q.insert(3);
        q.insert(5);
        q.insert(8);
        q.insert(5);
        q.insert(2);
        System.out.println("גודל: " + size(q));
        removeAll(q, 5);
        System.out.println("אחרי מחיקת 5: " + q);
    }
}
