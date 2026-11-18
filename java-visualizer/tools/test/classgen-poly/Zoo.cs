// "צור מחלקה" עם הורשה (C#): virtual / override / בלי virtual (המחולל מוסיף virtual), sealed, property, פעולה עם =>
using System;
using System.Collections.Generic;

public class Animal
{
    private string name;
    private string gender;
    public int Legs { get; set; }
    private static int count;

    public Animal(string name, string gender)
    {
        this.name = name;
        this.gender = gender;
        count++;
    }

    public string GetName() => name;

    public virtual string Say()
    {
        return "I am an Animal";
    }

    public string Kind()
    {
        return "animal";
    }

    protected virtual void Grow(int by)
    {
        Legs += by;
    }

    public override string ToString()
    {
        return "Animal " + name;
    }
}

public class Mammal : Animal
{
    private double milk;

    public Mammal(string name, string gender, double milk) : base(name, gender)
    {
        this.milk = milk;
    }

    public double GetMilk()
    {
        return milk;
    }

    public override string Say()
    {
        return "I am a Mammal";
    }
}

public class Dog : Mammal, IComparable<Dog>
{
    private int bonesToEat;
    private List<string> tricks = new List<string>();

    public Dog(string name, string gender, double milk, int bones)
        : base(name, gender, milk)
    {
        this.bonesToEat = bones;
    }

    public sealed override string Say()
    {
        return "I am a Dog";
    }

    public int CompareTo(Dog other) => bonesToEat - other.bonesToEat;
}

public sealed class Locked
{
    private int x;
}

public class Pet : IDisposable
{
    public void Dispose() { }
}
