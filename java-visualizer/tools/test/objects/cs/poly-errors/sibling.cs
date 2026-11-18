using System;

public class Program
{
    public static void Main()
    {
        Cat c = new Dog();
    }
}

public class Animal { }
public class Dog : Animal { }
public class Cat : Animal { }
