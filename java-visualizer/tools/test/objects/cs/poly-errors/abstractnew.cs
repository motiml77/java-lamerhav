using System;

public class Program
{
    public static void Main()
    {
        Animal a = new Animal();
    }
}

public abstract class Animal { public abstract string Say(); }
public class Dog : Animal { public override string Say() { return "Woof"; } }
