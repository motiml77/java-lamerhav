using System;
using Unit4.CollectionsLib;

// דוגמה עם תלמידים: מי התלמיד עם הממוצע הגבוה ביותר בתור
class Program
{
    // התלמיד הטוב ביותר בתור — התור חוזר למצבו המקורי
    static Student Best(Queue<Student> q1)
    {
        Queue<Student> tmp = new Queue<Student>();
        Student best = q1.Head();                  // מתחילים מהראשון
        while (!q1.IsEmpty())
        {
            Student s1 = q1.Remove();
            if (s1.GetAverage() > best.GetAverage())
            {
                best = s1;
            }
            tmp.Insert(s1);
        }
        while (!tmp.IsEmpty())
        {
            q1.Insert(tmp.Remove());
        }
        return best;
    }

    static void Main(string[] args)
    {
        Queue<Student> q1 = new Queue<Student>();
        q1.Insert(new Student("נועה לוי", "יא2", 90, 85, 77));
        q1.Insert(new Student("דני כהן", "יא2", 70, 95));
        q1.Insert(new Student("מאיה פרץ", "יא1", 98, 91));

        Student best = Best(q1);
        Console.WriteLine("הטוב ביותר: " + best.GetName() + " " + best.GetAverage());
    }
}
