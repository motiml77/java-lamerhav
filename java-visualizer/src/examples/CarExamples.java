package examples;

import unit4.collectionsLib.*;

/** דוגמאות עם מכוניות: כמה ותיקות בתור, המכונית הכי חדשה במערך, נסיעה ותדלוק */
public class CarExamples {

    /** כמה מכוניות בתור הן ותיקות (10 שנים ומעלה) — התור חוזר למצבו */
    public static int countOld(Queue<Car> q) {
        Queue<Car> temp = new Queue<Car>();
        int count = 0;
        while (!q.isEmpty()) {
            Car c = q.remove();
            if (c.isOld()) count++;
            temp.insert(c);
        }
        while (!temp.isEmpty()) q.insert(temp.remove());
        return count;
    }

    /** המכונית עם שנת הייצור הגבוהה ביותר */
    public static Car newest(Car[] arr) {
        Car best = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i].getYear() > best.getYear()) best = arr[i];
        }
        return best;
    }

    /** סכום הקילומטראז' של כל המכוניות במחסנית — המחסנית חוזרת למצבה */
    public static int totalKm(Stack<Car> s) {
        Stack<Car> temp = new Stack<Car>();
        int total = 0;
        while (!s.isEmpty()) {
            Car c = s.pop();
            total = total + c.getKm();
            temp.push(c);
        }
        while (!temp.isEmpty()) s.push(temp.pop());
        return total;
    }

    public static void main(String[] args) {
        Queue<Car> q = new Queue<Car>();
        for (int i = 0; i < 5; i++) {
            q.insert(Car.random());   // מכונית עם נתוני דמה
        }
        System.out.println("ותיקות: " + countOld(q));

        Car[] cars = new Car[4];
        for (int i = 0; i < cars.length; i++) {
            cars[i] = Car.random();
        }
        System.out.println("הכי חדשה: " + newest(cars));

        Stack<Car> s = new Stack<Car>();
        s.push(cars[0]);
        s.push(cars[1]);
        System.out.println("סה\"כ ק\"מ: " + totalKm(s));

        Car c = new Car("Mazda", "3", 2019);
        c.refuel(20);
        c.accelerate(50);
        int driven = c.drive(120);
        c.brake(50);
        System.out.println("נסעה " + driven + " ק\"מ, נשאר דלק: " + c.getFuel());
    }
}
