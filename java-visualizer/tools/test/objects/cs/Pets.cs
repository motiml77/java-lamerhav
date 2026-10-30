using System;

public class Dog
{
    private string name;
    private int age;

    public Dog(string name, int age)
    {
        this.name = name;
        this.age = age;
    }

    public void Birthday()
    {
        age = age + 1;
        Bark();
    }

    public void Bark()
    {
        Console.WriteLine(name + " נובח");
    }

    public bool IsOlderThan(Dog other)
    {
        return age > other.age;
    }

    public string GetName()
    {
        return name;
    }
}

public class Program
{
    public static void Main(string[] args)
    {
        Dog d1 = new Dog("רקס", 3);
        Dog d2 = new Dog("לאסי", 5);
        d1.Birthday();
        bool older = d2.IsOlderThan(d1);
        Console.WriteLine(d1.GetName() + " " + older);
    }
}
