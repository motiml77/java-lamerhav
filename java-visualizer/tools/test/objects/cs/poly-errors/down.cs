using System;

public class Program
{
    public static void Main()
    {
        Animal pet = new Dog();
        Dog d = pet;
    }
}

public class Animal { }
public class Dog : Animal { }
