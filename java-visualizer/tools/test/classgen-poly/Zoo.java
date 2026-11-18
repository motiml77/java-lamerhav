// "צור מחלקה" עם הורשה (classgen-cases.txt): מחלקות שהתלמיד כתב בעצמו — המחולל קורא מהן את השדות, הבנאי והפעולות
import java.util.ArrayList;

class Animal {
    private String name;
    private String gender;
    protected int legs = 4, eyes = 2;
    private static int count;   // static — לא שדה של עצם
    private int[] marks = { 1, 2 };

    public Animal() {
        this("x", "y");
    }

    public Animal(String name, String gender) {
        this.name = name;
        this.gender = gender;
        count++;
    }

    private Animal(int secret) {   // private — לא נספר
    }

    public String getName() {
        return name;
    }

    public String say() {
        return "I am an Animal";   // say() { } בתוך מחרוזת — לא נספר
    }

    protected void grow(int by, String why) {
        legs += by;
    }

    public final String id() {
        return "animal";
    }

    public static int total() {
        return count;
    }

    @Override
    public String toString() {
        return "Animal " + name;
    }
}

class Mammal extends Animal {
    private double milk;

    public Mammal(String name, String gender, double milk) {
        super(name, gender);
        this.milk = milk;
    }

    public double getMilk() {
        return milk;
    }

    @Override
    public String say() {
        return "I am a Mammal";
    }
}

class Dog extends Mammal {
    private int bonesToEat;
    private ArrayList<String> tricks = new ArrayList<String>();

    public Dog(String name, String gender, double milk, int bones) {
        super(name, gender, milk);
        this.bonesToEat = bones;
    }

    public String say() {
        return "I am a Dog";
    }
}

final class Locked {
    private int x;
}

interface Walker {
    void walk();
}
