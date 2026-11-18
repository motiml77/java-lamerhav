using System;

public class Program
{
    public static void Main()
    {
        Dog d = new Dog();
        Cat c = (Cat)d;
    }
}

public class Animal { }
public class Dog : Animal { }
public class Cat : Animal { }
