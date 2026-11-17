using System;

// "האם קטע הקוד תקין?" (דף העבודה, עמ' 6): השורות שמתקמפלות — ההמרה האחרונה נכשלת בזמן ריצה
public class Program
{
    public static void Main()
    {
        Mammal goofy = new Mammal("Goofy", "male", 250);
        object obj = goofy;
        Animal ann = (Animal)obj;
        Console.WriteLine(ann.GetGender());
        Console.WriteLine(((Bird)obj).GetName());
    }
}

public class Animal
{
    private string name;
    private string gender;

    public Animal(string name, string gender)
    {
        this.name = name;
        this.gender = gender;
    }

    public string GetName() { return name; }

    public string GetGender() { return gender; }
}

public class Mammal : Animal
{
    private int milk;

    public Mammal(string name, string gender, int milk) : base(name, gender)
    {
        this.milk = milk;
    }
}

public class Bird : Animal
{
    public Bird(string name, string gender) : base(name, gender) { }
}
