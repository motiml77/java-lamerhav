// הורשה ופולימורפיזם — טעויות נפוצות (ההסברים של "המורה בעברית": run.sh, וצילום עם SHOT=1)
public class Poly {
    public static void main(String[] args) {
        Animal pet = new Dog("Rexy", "Boxer");
        pet.bark();
        Dog d = pet;
        System.out.println(d);
    }
}

class Animal {
    private String name;

    public Animal(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

class Dog extends Animal {
    private String breed;

    public Dog(String name, String breed) {
        this.breed = breed;
    }

    public String bark() {
        return "Woof";
    }
}
