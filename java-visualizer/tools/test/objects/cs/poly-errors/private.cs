using System;

public class Program
{
    public static void Main()
    {
        
    }
}

public class Animal { private string name; public Animal(string name) { this.name = name; } public string GetName() { return name; } }
public class Dog : Animal { public Dog(string name) : base(name) { } public string Hello() { return "I am " + name; } }
