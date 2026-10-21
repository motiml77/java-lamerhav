package il.lamerhav.dsviz;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * תיאור קצר של המחלקות המוכנות (Student, Car): המאפיינים והפעולות — כדי שהתלמיד יידע מה יש לו.
 * שימו לב: יש עותק זהה ב-JavaScript בתוסף של VS Code (templates.js, CLASS_INFO).
 */
public final class ClassInfo {
    private ClassInfo() {}

    public static final class Info {
        public final String name, title, intro;
        public final String[][] fields;    // { שם, טיפוס, הסבר }
        public final String[][] methods;   // { חתימה, הסבר }
        public final String[][] creators;  // { קוד, הסבר }

        Info(String name, String title, String intro, String[][] creators, String[][] fields, String[][] methods) {
            this.name = name; this.title = title; this.intro = intro;
            this.creators = creators; this.fields = fields; this.methods = methods;
        }
    }

    public static final Map<String, Info> ALL = new LinkedHashMap<String, Info>();

    static {
        ALL.put("Student", new Info("Student", "תלמיד (Student)",
                "המאפיינים פרטיים — ניגשים אליהם דרך הפעולות (למשל s1.getName()).",
                new String[][] {
                    { "new Student(\"נועה\", \"יא2\", 90, 85, 77)", "שם, כיתה וציונים (כמה שרוצים)" },
                    { "Student.random()", "תלמיד אקראי עם נתוני דמה" } },
                new String[][] {
                    { "name", "String", "שם התלמיד" },
                    { "classRoom", "String", "הכיתה, למשל \"יא2\"" },
                    { "id", "String", "תעודת זהות (9 ספרות)" },
                    { "age", "int", "גיל" },
                    { "grades", "int[]", "הציונים" } },
                new String[][] {
                    { "getName()  getClassRoom()  getId()  getAge()", "קריאת הפרטים" },
                    { "getNumGrades()", "כמה ציונים יש" },
                    { "getGrade(i)", "הציון במקום i (מ-0)" },
                    { "setGrade(i, g)  addGrade(g)", "שינוי ציון / הוספת ציון בסוף" },
                    { "getAverage()", "ממוצע הציונים (double)" },
                    { "getMaxGrade()  getMinGrade()", "הציון הגבוה / הנמוך" },
                    { "isExcellent()  isPassing()", "ממוצע 90 ומעלה / 55 ומעלה" },
                    { "compareTo(other)  equals(other)", "השוואה לפי ממוצע / שווים אם אותה ת\"ז" } }));
        ALL.put("Car", new Info("Car", "מכונית (Car)",
                "המאפיינים פרטיים — ניגשים אליהם דרך הפעולות (למשל c1.getKm()).",
                new String[][] {
                    { "new Car(\"Mazda\", \"3\", 2019)", "יצרן, דגם ושנת ייצור" },
                    { "Car.random()", "מכונית אקראית עם נתוני דמה" } },
                new String[][] {
                    { "manufacturer", "String", "יצרן, למשל \"Mazda\"" },
                    { "model", "String", "דגם" },
                    { "year", "int", "שנת ייצור" },
                    { "color", "String", "צבע" },
                    { "plate", "String", "מספר רישוי" },
                    { "km", "int", "קילומטראז' (כמה ק\"מ נסעה)" },
                    { "fuel", "double", "ליטרים במיכל עכשיו" },
                    { "tankSize", "double", "גודל המיכל בליטרים" },
                    { "speed", "int", "מהירות עכשיו (קמ\"ש)" } },
                new String[][] {
                    { "getManufacturer()  getModel()  getYear()  getColor()  getPlate()", "קריאת הפרטים" },
                    { "getKm()  getFuel()  getTankSize()  getSpeed()", "קריאת מצב המכונית" },
                    { "drive(km)", "נוסעת עד km ק\"מ (עד שנגמר הדלק) — מחזירה כמה נסעה" },
                    { "refuel(l)", "מתדלקת l ליטרים (עד שהמיכל מלא)" },
                    { "accelerate(d)  brake(d)", "מוסיפה / מורידה מהירות" },
                    { "getAge()  isOld()", "גיל המכונית / 10 שנים ומעלה" },
                    { "needsFuel()  isMoving()", "פחות מעשירית מיכל / המהירות גדולה מ-0" },
                    { "compareTo(other)  equals(other)", "השוואה לפי שנה / שוות אם אותו מספר רישוי" } }));
    }
}
