using System;

public class Program
{
    public static void Main()
    {
        Animal pet = new Dog();
        pet.Bark();
    }
}

public class Animal { }
public class Dog : Animal { public string Bark() { return "Woof"; } }
