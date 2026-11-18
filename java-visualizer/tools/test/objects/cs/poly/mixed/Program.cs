using System;

// תרשים המחלקות — רק המחלקות ש-Main משתמש בהן: Dog יורשת מ-Animal. BusRoute ו-Station (קובץ אחר בפרויקט) לא בתרשים
public class Animal
{
    protected string name;
    public Animal(string name) { this.name = name; }
    public virtual string Say() { return "..."; }
}

public class Dog : Animal
{
    public Dog(string name) : base(name) { }
    public override string Say() { return name + ": Woof"; }
}

public class Program
{
    public static void Main(string[] args)
    {
        Animal a = new Dog("Rex");
        Console.WriteLine(a.Say());
    }
}
