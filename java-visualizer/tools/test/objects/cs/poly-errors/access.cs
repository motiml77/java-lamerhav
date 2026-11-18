using System;

public class Program
{
    public static void Main()
    {
        
    }
}

public class Animal { public virtual string Say() { return "..."; } }
public class Dog : Animal { protected override string Say() { return "Woof"; } }
