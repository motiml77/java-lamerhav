package examples;

import unit4.collectionsLib.*;

/** דוגמאות עם מכוניות: כמה ותיקות בתור, המכונית הכי חדשה במערך, נסיעה ותדלוק */
public class CarExamples {

    /** כמה מכוניות בתור הן ותיקות (10 שנים ומעלה) — התור חוזר למצבו */
    public static int countOld(Queue<Car> q1) {
        Queue<Car> tmp = new Queue<Car>();   // עזר: שומר את המכוניות כדי להחזיר אותן לתור
        int count = 0;                       // כמה ותיקות מצאנו
        while (!q1.isEmpty()) {
            Car c1 = q1.remove();            // המכונית שהוצאנו עכשיו
            if (c1.isOld()) count++;
            tmp.insert(c1);
        }
        while (!tmp.isEmpty()) q1.insert(tmp.remove());
        return count;
    }

    /** המכונית עם שנת הייצור הגבוהה ביותר */
    public static Car newest(Car[] arr) {
        Car best = arr[0];               // הכי חדשה שמצאנו עד עכשיו (מתחילים מהראשונה)
        for (int i = 1; i < arr.length; i++) {
            if (arr[i].getYear() > best.getYear()) best = arr[i];
        }
        return best;
    }

    /** סכום הקילומטראז' של כל המכוניות במחסנית — המחסנית חוזרת למצבה */
    public static int totalKm(Stack<Car> stk) {
        Stack<Car> tmp = new Stack<Car>();   // עזר: שומר את המכוניות כדי להחזיר אותן
        int total = 0;                       // סך כל הק"מ
        while (!stk.isEmpty()) {
            Car c1 = stk.pop();
            total = total + c1.getKm();
            tmp.push(c1);
        }
        while (!tmp.isEmpty()) stk.push(tmp.pop());
        return total;
    }

    public static void main(String[] args) {
        Queue<Car> q1 = new Queue<Car>();
        for (int i = 0; i < 5; i++) {
            q1.insert(Car.random());   // מכונית עם נתוני דמה
        }
        System.out.println("ותיקות: " + countOld(q1));

        Car[] arr = new Car[4];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = Car.random();
        }
        System.out.println("הכי חדשה: " + newest(arr));

        Stack<Car> stk = new Stack<Car>();
        stk.push(arr[0]);
        stk.push(arr[1]);
        System.out.println("סה\"כ ק\"מ: " + totalKm(stk));

        Car c1 = new Car("Mazda", "3", 2019);
        c1.refuel(20);
        c1.accelerate(50);
        int driven = c1.drive(120);
        c1.brake(50);
        System.out.println("נסעה " + driven + " ק\"מ, נשאר דלק: " + c1.getFuel());
    }
}
