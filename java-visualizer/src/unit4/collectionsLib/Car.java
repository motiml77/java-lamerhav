package unit4.collectionsLib;

import java.util.Calendar;
import java.util.Random;

import unit4.collectionsLib.Model.Obj;

/**
 * מכונית — מחלקה מוכנה לתרגילים: יצרן, דגם, שנה, צבע, מספר רישוי, קילומטראז', דלק ומהירות.
 *
 * <pre>
 *   Car c = new Car("Mazda", "3", 2019);                  // יצרן, דגם, שנת ייצור
 *   Car d = new Car("Kia", "Picanto", 2015, "לבן", "12-345-67");
 *   Car r = Car.random();                                  // מכונית אקראית (נתוני דמה)
 *
 *   c.drive(120);   c.refuel(20);   c.accelerate(30);   c.brake(10);
 *   c.getKm()   c.getFuel()   c.getSpeed()   c.getAge()   c.isOld()   c.needsFuel() ...
 * </pre>
 *
 * מכוניות אפשר לשים בכל מבנה: Queue&lt;Car&gt;, Stack&lt;Car&gt;, Node&lt;Car&gt;, Car[] ...
 * ובתצוגה רואים את היצרן והשנה (למשל "Mazda 2019").
 */
public class Car implements Comparable<Car> {
    /** צריכת דלק: ליטרים ל-100 ק"מ */
    public static final double LITERS_PER_100KM = 8;

    private String manufacturer;
    private String model;
    private int year;
    private String color;
    private String plate;
    private int km;
    private double fuel;
    private double tankSize;
    private int speed;
    int vizId;   // זהות בהרצה הוויזואלית (נקבע ב-Recorder.register)

    /** מכונית עם יצרן, דגם ושנת ייצור (מיכל חצי מלא, בלי נסיעות) */
    public Car(String manufacturer, String model, int year) {
        this(manufacturer, model, year, "לבן", makePlate(manufacturer + model + year));
    }

    /** מכונית עם כל הפרטים */
    public Car(String manufacturer, String model, int year, String color, String plate) {
        this.manufacturer = manufacturer;
        this.model = model;
        this.year = year;
        this.color = color;
        this.plate = plate;
        this.tankSize = 50;
        this.fuel = 25;
        this.km = 0;
        this.speed = 0;
        Recorder.register(this);
    }

    // ------------------------------------------------------------ פרטים

    // כל קריאה / שינוי של מאפיין מתועדים בהרצה הוויזואלית (Recorder.got / changed): הכרטיס של המכונית
    // מוצג עם המאפיין שנקרא מודגש. בתוך המחלקה עצמה משתמשים בשדות ישירות — כדי שלא יתועדו קריאות פנימיות

    public String getManufacturer() { return Recorder.got(this, "manufacturer", "getManufacturer()", manufacturer); }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; Recorder.changed(this, "manufacturer", "setManufacturer(" + Recorder.lit(manufacturer) + ")"); }

    public String getModel() { return Recorder.got(this, "model", "getModel()", model); }
    public void setModel(String model) { this.model = model; Recorder.changed(this, "model", "setModel(" + Recorder.lit(model) + ")"); }

    /** שנת ייצור */
    public int getYear() { return Recorder.got(this, "year", "getYear()", year); }
    public void setYear(int year) { this.year = year; Recorder.changed(this, "year", "setYear(" + year + ")"); }

    public String getColor() { return Recorder.got(this, "color", "getColor()", color); }
    public void setColor(String color) { this.color = color; Recorder.changed(this, "color", "setColor(" + Recorder.lit(color) + ")"); }

    /** מספר רישוי */
    public String getPlate() { return Recorder.got(this, "plate", "getPlate()", plate); }
    public void setPlate(String plate) { this.plate = plate; Recorder.changed(this, "plate", "setPlate(" + Recorder.lit(plate) + ")"); }

    /** קילומטראז' (כמה ק"מ נסעה) */
    public int getKm() { return Recorder.got(this, "km", "getKm()", km); }
    public void setKm(int km) { this.km = km; Recorder.changed(this, "km", "setKm(" + km + ")"); }

    /** כמה ליטרים יש במיכל עכשיו */
    public double getFuel() { return Recorder.got(this, "fuel", "getFuel()", fuel); }

    /** גודל המיכל בליטרים */
    public double getTankSize() { return Recorder.got(this, "tankSize", "getTankSize()", tankSize); }

    /** המהירות עכשיו (קמ"ש) */
    public int getSpeed() { return Recorder.got(this, "speed", "getSpeed()", speed); }

    // ------------------------------------------------------------ פעולות

    /**
     * נוסעת עד km ק"מ. אם נגמר הדלק — נעצרת. מחזירה כמה ק"מ נסעה בפועל.
     */
    public int drive(int km) {
        int possible = (int) (fuel / LITERS_PER_100KM * 100);
        int driven = Math.max(0, Math.min(km, possible));
        this.km += driven;
        fuel -= driven * LITERS_PER_100KM / 100;
        Recorder.changed(this, "km", "drive(" + km + ")  →  נסעה " + driven + " ק\"מ");
        return driven;
    }

    /** מתדלקת (עד שהמיכל מלא). מחזירה כמה ליטרים נכנסו */
    public double refuel(double liters) {
        double added = Math.max(0, Math.min(liters, tankSize - fuel));
        fuel += added;
        Recorder.changed(this, "fuel", "refuel(" + liters + ")  →  נכנסו " + added + " ליטר");
        return added;
    }

    /** מוסיפה למהירות */
    public void accelerate(int delta) {
        speed += Math.max(0, delta);
        Recorder.changed(this, "speed", "accelerate(" + delta + ")  →  " + speed + " קמ\"ש");
    }

    /** מורידה מהירות (לא פחות מ-0) */
    public void brake(int delta) {
        speed = Math.max(0, speed - Math.max(0, delta));
        Recorder.changed(this, "speed", "brake(" + delta + ")  →  " + speed + " קמ\"ש");
    }

    private int age() { return Calendar.getInstance().get(Calendar.YEAR) - year; }

    /** כמה שנים עברו מאז שנת הייצור */
    public int getAge() { return Recorder.got(this, "year", "getAge()", age()); }

    /** 10 שנים ומעלה */
    public boolean isOld() { return Recorder.got(this, "year", "isOld()", age() >= 10); }

    /** פחות מעשירית מיכל */
    public boolean needsFuel() { return Recorder.got(this, "fuel", "needsFuel()", fuel < tankSize / 10); }

    /** נוסעת (המהירות גדולה מ-0) */
    public boolean isMoving() { return Recorder.got(this, "speed", "isMoving()", speed > 0); }

    // ------------------------------------------------------------ השוואה והצגה

    /** השוואה לפי שנת ייצור (מאפשר עץ חיפוש של מכוניות) */
    public int compareTo(Car other) {
        return Integer.compare(year, other.year);
    }

    /** שתי מכוניות שוות אם יש להן אותו מספר רישוי */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Car)) return false;
        Car c = (Car) o;
        return plate != null && plate.equals(c.plate);
    }

    @Override
    public int hashCode() {
        return plate == null ? 0 : plate.hashCode();
    }

    /** "Mazda 3 (2019)" */
    @Override
    public String toString() {
        return manufacturer + " " + model + " (" + year + ")";
    }

    /** תווית קצרה לתצוגה: "Mazda 2019" */
    String label() {
        return manufacturer + " " + year;
    }

    /** הכרטיס של המכונית בחלון: יצרן ודגם; שנה וקילומטראז'; ומתחת — כל המאפיינים */
    Obj vizObj() {
        Obj o = new Obj();
        o.id = vizId;
        o.type = "Car";
        o.title = manufacturer + " " + model;
        o.sub = year + " · " + String.format("%,d", km) + " ק\"מ";
        o.fields.add(new String[] { "manufacturer", Recorder.lit(manufacturer) });
        o.fields.add(new String[] { "model", Recorder.lit(model) });
        o.fields.add(new String[] { "year", String.valueOf(year) });
        o.fields.add(new String[] { "color", Recorder.lit(color) });
        o.fields.add(new String[] { "plate", Recorder.lit(plate) });
        o.fields.add(new String[] { "km", String.valueOf(km) });
        o.fields.add(new String[] { "fuel", String.valueOf(Math.round(fuel * 10) / 10.0) });
        o.fields.add(new String[] { "tankSize", String.valueOf(Math.round(tankSize * 10) / 10.0) });
        o.fields.add(new String[] { "speed", String.valueOf(speed) });
        return o;
    }

    // ------------------------------------------------------------ נתוני דמה

    static final String[][] MODELS = {
        { "Toyota", "Corolla", "Yaris", "Camry" }, { "Mazda", "3", "6", "CX-5" }, { "Kia", "Picanto", "Rio", "Sportage" },
        { "Hyundai", "i10", "i20", "Tucson" }, { "Skoda", "Octavia", "Fabia", "Kodiaq" }, { "Honda", "Civic", "Jazz", "CR-V" },
        { "Suzuki", "Swift", "Vitara", "Ignis" }, { "Seat", "Ibiza", "Leon", "Ateca" }
    };
    static final String[] COLORS = { "לבן", "שחור", "כסוף", "אפור", "אדום", "כחול", "ירוק" };
    private static final Random RND = new Random();

    /** מכונית אקראית — יצרן, דגם, שנה, צבע, קילומטראז' ודלק */
    public static Car random() {
        String[] m = MODELS[RND.nextInt(MODELS.length)];
        int year = 2008 + RND.nextInt(17);
        Car c = new Car(m[0], m[1 + RND.nextInt(m.length - 1)], year, COLORS[RND.nextInt(COLORS.length)],
                makePlate(String.valueOf(RND.nextLong())));
        c.km = (Calendar.getInstance().get(Calendar.YEAR) - year + 1) * (6000 + RND.nextInt(14000));
        c.fuel = 5 + RND.nextInt(46);
        return c;
    }

    /** מערך של n מכוניות אקראיות */
    public static Car[] random(int n) {
        Car[] a = new Car[n];
        for (int i = 0; i < n; i++) a[i] = random();
        return a;
    }

    /** מספר רישוי בן 7 ספרות, כמו "12-345-67" */
    private static String makePlate(String seed) {
        long h = Math.abs((long) seed.hashCode() * 2654435761L) % 10000000L;
        String d = String.format("%07d", h);
        return d.substring(0, 2) + "-" + d.substring(2, 5) + "-" + d.substring(5);
    }
}
