package school.model;

// class InComment — לא מחלקה (הערה)
public class Car {
    private String model;

    public Car(String model) {
        this.model = model;
    }

    public String toString() {
        String s = "class InString";   // גם לא מחלקה (מחרוזת)
        return "Car " + this.model + (s.isEmpty() ? "" : "");
    }
}

interface Drivable {
    void drive();
}
