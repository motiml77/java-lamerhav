package school.model;

/** מחלקה שהתלמיד כתב — "צור מחלקה" צריכה להציע אותה ברשימת הטיפוסים (classgen-tests.sh) */
public class Student {
    private String name;

    public Student(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public String toString() {
        return "Student " + this.name;
    }

    /** מחלקה בתוך מחלקה — לא מוצעת (רק מחלקות ברמה העליונה) */
    static class Grade {
        int value;
    }
}
