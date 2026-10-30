package shapes;

// מחלקה עם toString משלה
public class Person {
    private String name;
    private Point home;

    public Person(String name, Point home) {
        this.name = name;
        this.home = home;
    }

    public String toString() {
        return "Person(" + name + ")";
    }
}
