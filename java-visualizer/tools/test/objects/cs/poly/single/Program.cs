using System;

// כמו בצילום מהמשתמש: מערך של object עם עצמים של Person1 — Person1 בלי הורשה / הכלה, אין תרשים; BusRoute ו-Station לא בשימוש
public class Person1
{
    private string name;
    private string id;
    public Person1(string name, string id) { this.name = name; this.id = id; }
}

public class Program
{
    public static void Main(string[] args)
    {
        object[] arr = new Person1[3];
        arr[0] = new Person1("naor", "123");
        arr[1] = new Person1("aa", "12");
        arr[2] = new object();
    }
}
