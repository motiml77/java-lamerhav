using System;
using Unit4.CollectionsLib;

// דוגמאות תור: ספירה בלי להרוס, והוצאת כל המופעים של ערך
class Program
{
    // כמה איברים בתור — התור חוזר למצבו המקורי
    static int Size(Queue<int> q1)
    {
        Queue<int> tmp = new Queue<int>();   // עזר: שומר את האיברים כדי להחזיר אותם
        int count = 0;                       // כאן סופרים
        while (!q1.IsEmpty())
        {
            tmp.Insert(q1.Remove());
            count++;
        }
        while (!tmp.IsEmpty())
        {
            q1.Insert(tmp.Remove());
        }
        return count;
    }

    // מוחקת מהתור את כל המופעים של x (שומרת על הסדר)
    static void RemoveAll(Queue<int> q1, int x)
    {
        int n = Size(q1);                    // כמה איברים יש — עוברים על כולם פעם אחת
        for (int i = 0; i < n; i++)
        {
            int curr = q1.Remove();          // האיבר הנוכחי
            if (curr != x)
            {
                q1.Insert(curr);             // כל מה שלא x חוזר לסוף התור
            }
        }
    }

    static void Main(string[] args)
    {
        Queue<int> q1 = new Queue<int>();
        q1.Insert(5);   // הראשון שנכנס — בראש התור
        q1.Insert(3);
        q1.Insert(5);
        q1.Insert(8);
        q1.Insert(5);
        q1.Insert(2);

        Console.WriteLine("גודל: " + Size(q1));
        RemoveAll(q1, 5);
        Console.WriteLine("אחרי מחיקת 5: " + q1);
    }
}
