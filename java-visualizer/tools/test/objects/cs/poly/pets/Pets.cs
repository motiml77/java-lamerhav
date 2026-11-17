using System;

// הורשה ופולימורפיזם — הדוגמה של העיצוב שאושר: Animal / Dog / Cat, ו-Person / Collar בהכלה
public class Program
{
    public static void Main()
    {
        Animal a = new Animal("Bob", 3);
        Dog d = new Dog("Rexy", 5, "Boxer", new Person("Hila"));
        Cat c = new Cat("Mitzi", 2, true);
        Animal pet = d;
        Console.WriteLine(pet.Say());
        Console.WriteLine(d.Bark());
    }
}

public class Animal
{
    private string name;
    private int age;

    public Animal(string name, int age)
    {
        this.name = name;
        this.age = age;
    }

    public string GetName() { return name; }

    public virtual string Say() { return "..."; }
}

public class Dog : Animal
{
    private string breed;
    private Person owner;      // מקבל מבחוץ

    public Dog(string name, int age, string breed, Person owner) : base(name, age)
    {
        this.breed = breed;
        this.owner = owner;
    }

    public override string Say() { return "Woof"; }

    public string Bark() { return base.Say() + "!"; }

    public void Fetch() { }
}

public class Cat : Animal
{
    private bool indoor;
    private Collar collar;

    public Cat(string n, int a, bool inside) : base(n, a)
    {
        this.indoor = inside;
        this.collar = new Collar("red"); // יוצר בעצמו
    }

    public override string Say() { return "Meow"; }
}

public class Person
{
    private string name;

    public Person(string name) { this.name = name; }
}

public class Collar
{
    private string color;

    public Collar(string color) { this.color = color; }
}
