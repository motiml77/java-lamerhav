package unit4.collectionsLib;

import java.util.Arrays;
import java.util.Random;

import unit4.collectionsLib.Model.Mark;
import unit4.collectionsLib.Model.Obj;

/**
 * תלמיד — מחלקה מוכנה לתרגילים: שם, כיתה, תעודת זהות, גיל וציונים.
 *
 * <pre>
 *   Student s = new Student("נועה לוי", "יא2", 90, 85, 77);   // שם, כיתה, ציונים
 *   Student t = new Student("דני כהן", 88);                     // שם וציון אחד
 *   Student r = Student.random();                               // תלמיד אקראי (נתוני דמה)
 *
 *   s.getName()  s.getAverage()  s.getGrade(0)  s.getNumGrades()  s.isExcellent() ...
 * </pre>
 *
 * תלמידים אפשר לשים בכל מבנה: Queue&lt;Student&gt;, Stack&lt;Student&gt;, Node&lt;Student&gt;, Student[] ...
 * ובתצוגה רואים את השם והממוצע.
 */
public class Student implements Comparable<Student> {
    private String name;
    private String classRoom;
    private String id;
    private int age;
    private int[] grades;
    int vizId;   // זהות בהרצה הוויזואלית (נקבע ב-Recorder.register)

    /** תלמיד עם שם, כיתה וציונים (אפשר כמה ציונים שרוצים) */
    public Student(String name, String classRoom, int... grades) {
        this.name = name;
        this.classRoom = classRoom;
        this.grades = grades == null ? new int[0] : Arrays.copyOf(grades, grades.length);
        this.id = makeId(name);
        this.age = 15 + Math.abs((name == null ? 0 : name.hashCode()) % 4);
        Recorder.register(this);
    }

    /** תלמיד עם שם וציון אחד */
    public Student(String name, int grade) {
        this(name, "", grade);
    }

    /** תלמיד עם כל הפרטים */
    public Student(String name, String id, String classRoom, int age, int... grades) {
        this(name, classRoom, grades);
        this.id = id;
        this.age = age;
    }

    // ------------------------------------------------------------ פרטים

    // כל קריאה / שינוי של מאפיין מתועדים בהרצה הוויזואלית (Recorder.got / changed): הכרטיס של התלמיד
    // מוצג עם המאפיין שנקרא מודגש. בתוך המחלקה עצמה משתמשים בשדות ישירות — כדי שלא יתועדו קריאות פנימיות

    public String getName() { return Recorder.got(this, "name", "getName()", name); }
    public void setName(String name) { this.name = name; Recorder.changed(this, "name", "setName(" + Recorder.lit(name) + ")"); }

    public String getClassRoom() { return Recorder.got(this, "classRoom", "getClassRoom()", classRoom); }
    public void setClassRoom(String classRoom) { this.classRoom = classRoom; Recorder.changed(this, "classRoom", "setClassRoom(" + Recorder.lit(classRoom) + ")"); }

    /** תעודת זהות (9 ספרות) */
    public String getId() { return Recorder.got(this, "id", "getId()", id); }
    public void setId(String id) { this.id = id; Recorder.changed(this, "id", "setId(" + Recorder.lit(id) + ")"); }

    public int getAge() { return Recorder.got(this, "age", "getAge()", age); }
    public void setAge(int age) { this.age = age; Recorder.changed(this, "age", "setAge(" + age + ")"); }

    // ------------------------------------------------------------ ציונים

    public int getNumGrades() { return Recorder.got(this, "grades", "getNumGrades()", grades.length); }

    /** הציון במקום i (מ-0) */
    public int getGrade(int i) { return Recorder.got(this, "grades", "getGrade(" + i + ")", grades[i]); }

    /** משנה את הציון במקום i (מ-0) */
    public void setGrade(int i, int grade) {
        grades[i] = grade;
        Recorder.changed(this, "grades", "setGrade(" + i + ", " + grade + ")");
    }

    /** מוסיף ציון בסוף */
    public void addGrade(int grade) {
        grades = Arrays.copyOf(grades, grades.length + 1);
        grades[grades.length - 1] = grade;
        Recorder.changed(this, "grades", "addGrade(" + grade + ")");
    }

    /** עותק של מערך הציונים */
    public int[] getGrades() { return Recorder.got(this, "grades", "getGrades()", Arrays.copyOf(grades, grades.length)); }

    /** ממוצע הציונים (0 אם אין ציונים) */
    public double getAverage() { return Recorder.got(this, "average", "getAverage()", average()); }

    private double average() {
        if (grades.length == 0) return 0;
        int sum = 0;
        for (int g : grades) sum += g;
        return (double) sum / grades.length;
    }

    public int getMaxGrade() {
        int max = grades.length == 0 ? 0 : grades[0];
        for (int g : grades) max = Math.max(max, g);
        return Recorder.got(this, "grades", "getMaxGrade()", max);
    }

    public int getMinGrade() {
        int min = grades.length == 0 ? 0 : grades[0];
        for (int g : grades) min = Math.min(min, g);
        return Recorder.got(this, "grades", "getMinGrade()", min);
    }

    /** ממוצע 90 ומעלה */
    public boolean isExcellent() { return Recorder.got(this, "average", "isExcellent()", average() >= 90); }

    /** ממוצע 55 ומעלה */
    public boolean isPassing() { return Recorder.got(this, "average", "isPassing()", average() >= 55); }

    // ------------------------------------------------------------ השוואה והצגה

    /** השוואה לפי ממוצע (מאפשר עץ חיפוש של תלמידים) */
    public int compareTo(Student other) {
        return Double.compare(average(), other.average());
    }

    /** שני תלמידים שווים אם יש להם אותה תעודת זהות */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Student)) return false;
        Student s = (Student) o;
        return id != null && id.equals(s.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    /** "נועה לוי (יא2) ממוצע 84.0" */
    @Override
    public String toString() {
        return name + (classRoom == null || classRoom.isEmpty() ? "" : " (" + classRoom + ")")
                + " ממוצע " + Math.round(average() * 10) / 10.0;
    }

    /** תווית קצרה לתצוגה: "נועה 84" */
    String label() {
        String first = name == null ? "?" : name.trim().split("\\s+")[0];
        return first + " " + Math.round(average());
    }

    /** הכרטיס של התלמיד בחלון: שורה ראשונה — שם פרטי; שנייה — כיתה וממוצע; ומתחת — כל המאפיינים */
    Obj vizObj() {
        Obj o = new Obj();
        o.id = vizId;
        o.type = "Student";
        o.title = name == null ? "?" : name.trim().split("\\s+")[0];
        o.sub = (classRoom == null || classRoom.isEmpty() ? "" : classRoom + " · ") + Math.round(average());
        o.fields.add(new String[] { "name", Recorder.lit(name) });
        o.fields.add(new String[] { "classRoom", Recorder.lit(classRoom) });
        o.fields.add(new String[] { "id", Recorder.lit(id) });
        o.fields.add(new String[] { "age", String.valueOf(age) });
        o.fields.add(new String[] { "grades", Arrays.toString(grades) });
        o.fields.add(new String[] { "average", String.valueOf(Math.round(average() * 10) / 10.0) });
        return o;
    }

    // ------------------------------------------------------------ נתוני דמה

    static final String[] FIRST = { "נועה", "דני", "מאיה", "יונתן", "שירה", "איתי", "תמר", "עומר", "רוני", "אורי",
            "הילה", "נדב", "יעל", "אלון", "מיכל", "גיל", "ליאור", "עדי", "שחר", "טל" };
    static final String[] LAST = { "כהן", "לוי", "מזרחי", "פרץ", "ביטון", "אברהם", "פרידמן", "דהן", "אזולאי", "גבאי",
            "שפירא", "חדד", "קליין", "אוחיון", "רוזן" };
    static final String[] CLASSES = { "י1", "י2", "יא1", "יא2", "יא3", "יב1", "יב2" };
    private static final Random RND = new Random();

    /** תלמיד אקראי — שם, כיתה ו-3 עד 4 ציונים */
    public static Student random() {
        int[] g = new int[3 + RND.nextInt(2)];
        for (int i = 0; i < g.length; i++) g[i] = 55 + RND.nextInt(46);
        return new Student(FIRST[RND.nextInt(FIRST.length)] + " " + LAST[RND.nextInt(LAST.length)],
                CLASSES[RND.nextInt(CLASSES.length)], g);
    }

    /** מערך של n תלמידים אקראיים */
    public static Student[] random(int n) {
        Student[] a = new Student[n];
        for (int i = 0; i < n; i++) a[i] = random();
        return a;
    }

    private static String makeId(String name) {
        long h = Math.abs((long) (name == null ? 0 : name.hashCode()) * 2654435761L) % 100000000L;
        String base = String.format("%08d", h);
        int sum = 0;   // ספרת ביקורת כמו בתעודת זהות ישראלית
        for (int i = 0; i < 8; i++) {
            int d = (base.charAt(i) - '0') * (i % 2 == 0 ? 1 : 2);
            sum += d > 9 ? d - 9 : d;
        }
        return base + (10 - sum % 10) % 10;
    }
}
