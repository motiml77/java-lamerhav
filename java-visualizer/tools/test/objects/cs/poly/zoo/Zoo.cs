using System;

public class Program
{
    public static void Main()
    {
        Animal[] zoo = new Animal[4];
        zoo[0] = new Animal("animal", "male");
        zoo[1] = new Dog("Dasty", "female", 0.5, 3);
        zoo[2] = new Boxer("Rexy", "male", 3.2, 3, "house");
        zoo[3] = new Labrador("Toto", "male", 2.5, 4, new Person("Hila"));
        for (int i = 0; i < zoo.Length; i++)
        {
            Console.WriteLine(zoo[i].GetName() + ": " + zoo[i].Say() + " / " + zoo[i].Kind());
        }
        Mammal mom = new Mammal("Numa", "female", 2);
        Animal myAnimal = mom;
        Mammal m1 = (Mammal)myAnimal;
        double milk = m1.GetMilk();
        int count = 0;
        for (int i = 0; i < zoo.Length; i++)
        {
            if (zoo[i] is Mammal)
            {
                count++;
            }
        }
        Console.WriteLine("num of Mammal is: " + count);
        Animal a2 = zoo[3];
        Boxer b1 = (Boxer)a2;
        Console.WriteLine(b1.Say());
    }
}

public class Animal
{
    private string name;
    private string gender;

    public Animal(string name, string gender)
    {
        this.name = name;
        this.gender = gender;
    }

    public string GetName() { return name; }

    public virtual string Say() { return "I am an Animal"; }

    public string Kind() { return "animal"; }
}

public class Mammal : Animal
{
    private double milk;

    public Mammal(string name, string gender, double milk) : base(name, gender)
    {
        this.milk = milk;
    }

    public double GetMilk() { return milk; }

    public override string Say() { return "I am a Mammal"; }

    public new string Kind() { return "mammal"; }
}

public class Dog : Mammal
{
    private int bonesToEat;

    public Dog(string name, string gender, double milk, int bones) : base(name, gender, milk)
    {
        this.bonesToEat = bones;
    }

    public override string Say() { return "I am a Dog"; }
}

public class Boxer : Dog
{
    private object guard;

    public Boxer(string name, string gender, double milk, int bones, object somethingToGuard) : base(name, gender, milk, bones)
    {
        this.guard = somethingToGuard;
    }

    public override string Say() { return "I am a Boxer"; }
}

public class Labrador : Dog
{
    private Person person;

    public Labrador(string name, string gender, double milk, int bones, Person person) : base(name, gender, milk, bones)
    {
        this.person = person;
    }
}

public class Person
{
    private string name;

    public Person(string name) { this.name = name; }
}
