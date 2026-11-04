using System;
using Unit4.CollectionsLib;

// מחלקה של התלמיד עם ToString משלה: ההרצה הוויזואלית מציגה את המשתנים (ושם קוראת ל-ToString) — היא לא נתקעת,
// וה-ToString עצמו לא נרשם כצעדים כשהוא נקרא רק בשביל התצוגה (רק כשהתלמיד קורא לו בקוד)
public class Fraction
{
    private int numerator;
    private int denominator;

    public Fraction(int numerator, int denominator)
    {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public Fraction Twice()
    {
        return new Fraction(numerator * 2, denominator);
    }

    public override string ToString()
    {
        string s = "";
        for (int i = 0; i < 1; i++)
        {
            s = numerator + "/" + denominator;
        }
        return s;
    }
}

public class OwnToString
{
    public static object Same(object o)
    {
        return o;
    }

    public static void Main(string[] args)
    {
        Fraction f = new Fraction(1, 2);
        Fraction g = f.Twice();
        Stack<Fraction> s = new Stack<Fraction>();
        s.Push(f);
        s.Push(g);
        Node<Fraction> list = new Node<Fraction>(f, new Node<Fraction>(g));
        Fraction[] arr = { f, g };
        object o = Same(g);
        Console.WriteLine("f=" + f + " g=" + g + " s=" + s + " o=" + o + " " + arr.Length + " " + list.GetValue());
    }
}
