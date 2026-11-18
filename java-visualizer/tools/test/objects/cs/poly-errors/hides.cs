using System;

public class Program
{
    public static void Main()
    {
        int x = 1 / 1;
        Undefined();
    }
}

public class Animal { public virtual string Say() { return "..."; } }
public class Dog : Animal { public string Say() { return "Woof"; } }
