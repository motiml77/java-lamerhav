public class Zoo {
    public static void main(String[] args) {
        Animal[] zoo = new Animal[4];
        zoo[0] = new Animal("animal", "male");
        zoo[1] = new Dog("Dasty", "female", 0.5, 3);
        zoo[2] = new Boxer("Rexy", "male", 3.2, 3, "house");
        zoo[3] = new Labrador("Toto", "male", 2.5, 4, new Person("Hila"));
        for (int i = 0; i < zoo.length; i++) {
            System.out.println(zoo[i].getName() + ": " + zoo[i].say());
        }
        Mammal mom = new Mammal("Numa", "female", 2);
        Animal myAnimal = mom;
        Mammal m1 = (Mammal) myAnimal;
        double milk = m1.getMilk();
        int count = 0;
        for (int i = 0; i < zoo.length; i++) {
            if (zoo[i] instanceof Mammal) {
                count++;
            }
        }
        System.out.println("num of Mammal is: " + count);
        Animal a2 = zoo[3];
        Boxer b1 = (Boxer) a2;
        System.out.println(b1.say());
    }
}

class Animal {
    private String name;
    private String gender;

    public Animal(String name, String gender) {
        this.name = name;
        this.gender = gender;
    }

    public String getName() {
        return name;
    }

    public String getGender() {
        return gender;
    }

    public String say() {
        return "I am an Animal";
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

    public String say() {
        return "I am a Mammal";
    }
}

class Dog extends Mammal {
    private int bonesToEat;

    public Dog(String name, String gender, double milk, int bones) {
        super(name, gender, milk);
        this.bonesToEat = bones;
    }

    public String say() {
        return "I am a Dog";
    }
}

class Boxer extends Dog {
    private Object guard;

    public Boxer(String name, String gender, double milk, int bones, Object somethingToGuard) {
        super(name, gender, milk, bones);
        this.guard = somethingToGuard;
    }

    public String say() {
        return "I am a Boxer";
    }
}

class Labrador extends Dog {
    private Person person;

    public Labrador(String name, String gender, double milk, int bones, Person person) {
        super(name, gender, milk, bones);
        this.person = person;
    }

    public Person getPerson() {
        return person;
    }
}

class Person {
    private String name;

    public Person(String name) {
        this.name = name;
    }
}
