using System;
using Unit4.CollectionsLib;

// דוגמאות רשימה מקושרת: סכום, מחיקה, היפוך
class Program
{
    static int Sum(Node<int> lst)
    {
        int total = 0;                   // כאן מצטבר הסכום
        Node<int> curr = lst;            // מצביע עזר: עובר חוליה אחרי חוליה (lst נשאר בראש הרשימה)
        while (curr != null)
        {
            total = total + curr.GetValue();
            curr = curr.GetNext();       // מתקדמים לחוליה הבאה
        }
        return total;
    }

    // מחיקת החוליה הראשונה שערכה x. מחזירה את ראש הרשימה החדש
    static Node<int> Remove(Node<int> lst, int x)
    {
        if (lst == null)
        {
            return null;
        }
        if (lst.GetValue() == x)
        {
            return lst.GetNext();        // מוחקים את הראש — הבא הופך לראש
        }
        Node<int> curr = lst;            // עומד על החוליה שלפני זו שבודקים
        while (curr.HasNext() && curr.GetNext().GetValue() != x)
        {
            curr = curr.GetNext();
        }
        if (curr.HasNext())
        {
            curr.SetNext(curr.GetNext().GetNext());      // "מדלגים" על החוליה שמוחקים
        }
        return lst;
    }

    // היפוך הרשימה במקום
    static Node<int> Reverse(Node<int> lst)
    {
        Node<int> prev = null;           // החוליה שכבר הפכנו (בהתחלה אין)
        Node<int> curr = lst;            // החוליה שהופכים עכשיו
        while (curr != null)
        {
            Node<int> next = curr.GetNext();   // שומרים את הבאה — לפני שמשנים את הקישור
            curr.SetNext(prev);                // הופכים את החץ אחורה
            prev = curr;
            curr = next;
        }
        return prev;                     // prev היא הראש החדש
    }

    static void Main(string[] args)
    {
        Node<int> lst = new Node<int>(6);   // מתחילים מהחוליה האחרונה
        lst = new Node<int>(1, lst);
        lst = new Node<int>(8, lst);
        lst = new Node<int>(3, lst);        // החוליה הראשונה

        Console.WriteLine("סכום: " + Sum(lst));
        lst = Remove(lst, 8);
        Console.WriteLine("אחרי מחיקת 8: " + lst);
        lst = Reverse(lst);
        Console.WriteLine("אחרי היפוך: " + lst);
    }
}
