package loops;

import unit4.collectionsLib.*;

public class FineLoops {
    // לולאות תקינות שנראות "חשודות" — אף אחת מהן לא נעצרת
    private int count;

    private void tick() {
        count++;
    }

    public static void main(String[] args) {
        // while (true) עם break
        int i = 0;
        while (true) {
            i++;
            if (i == 100) break;
        }
        // מאפיין שמשתנה דרך פעולה (לא בתוך הגוף עצמו)
        FineLoops f = new FineLoops();
        while (f.count < 50) {
            f.tick();
        }
        // המשתנה שבתנאי משתנה רק לפעמים
        int found = 0, k = 0;
        while (found < 3) {
            k++;
            if (k % 50 == 0) found++;
        }
        // לולאות מקוננות (הפנימית מתחילה מחדש)
        int pairs = 0;
        for (int a = 0; a < 8; a++) {
            for (int b = 0; b < 8; b++) {
                pairs++;
            }
        }
        // רשימה: p מתקדם
        Node<Integer> lst = new Node<Integer>(1, new Node<Integer>(2, new Node<Integer>(3)));
        int sum = 0;
        for (Node<Integer> p = lst; p != null; p = p.getNext()) sum += p.getValue();
        System.out.println(i + " " + f.count + " " + k + " " + pairs + " " + sum);
    }
}
