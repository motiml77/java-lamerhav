// הורשה ופולימורפיזם — הדוגמה של העיצוב שאושר: Animal / Dog / Cat, ו-Person / Collar בהכלה
public class Pets {
    public static void main(String[] args) {
        Animal a = new Animal("Bob", 3);
        Dog d = new Dog("Rexy", 5, "Boxer", new Person("Hila"));
        Cat c = new Cat("Mitzi", 2, true);
        Animal pet = d;
        System.out.println(pet.say());
        System.out.println(d.bark());
    }
}

class Animal {
    private String name;
    private int age;

    public Animal(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }

    public String say() { return "..."; }
}

class Dog extends Animal {
    private String breed;
    private Person owner;      // מקבל מבחוץ

    public Dog(String name, int age, String breed, Person owner) {
        super(name, age);
        this.breed = breed;
        this.owner = owner;
    }

    public String say() { return "Woof"; }

    public String bark() { return super.say() + "!"; }

    public void fetch() { }
}

class Cat extends Animal {
    private boolean indoor;
    private Collar collar;

    public Cat(String n, int a, boolean in) {
        super(n, a);
        this.indoor = in;
        this.collar = new Collar("red"); // יוצר בעצמו
    }

    public String say() { return "Meow"; }
}

class Person {
    private String name;

    public Person(String name) { this.name = name; }
}

class Collar {
    private String color;

    public Collar(String color) { this.color = color; }
}
