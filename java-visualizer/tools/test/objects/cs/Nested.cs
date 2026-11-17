using System;

// אובייקט בתוך אובייקט ומערך בתוך אובייקט: מה מצויר בתוך הכרטיס של האובייקט (cs-tests.sh; אותה תוכנית ב-Java: ../Nested.java)
public class Program
{
    public static void Main()
    {
        Student s = new Student("נועה", 90);
        Team t = new Team("אלפא", s);
        t.SetScore(1, 7);
        t.Boost();
        s.Join(t);
        Link a = new Link(1, null);
        Link b = new Link(2, a);
        a.next = b;
        Shelf sh = new Shelf();
        sh.Fill();
        sh.Mark(1, 2);
        L1 deep = new L1();
        Console.WriteLine(t.Total() + " " + b.val + " " + sh.many.Length + " " + deep.inner.inner.inner.x);
    }
}

public class Student
{
    public string name;
    public int grade;
    public Team team;

    public Student(string name, int grade)
    {
        this.name = name;
        this.grade = grade;
    }

    public void Join(Team t)
    {
        team = t;
    }
}

public class Team
{
    private string name;
    private Student leader;
    private int[] scores;

    public Team(string name, Student leader)
    {
        this.name = name;
        this.leader = leader;
        this.scores = new int[3];
    }

    public void SetScore(int i, int v)
    {
        scores[i] = v;
    }

    public void Boost()
    {
        leader.grade = leader.grade + 5;
    }

    public int Total()
    {
        int sum = 0;
        for (int i = 0; i < scores.Length; i++)
        {
            sum += this.scores[i];
        }
        return sum;
    }
}

// רשימה מקושרת שהתלמיד כתב: מאפיין מאותו סוג — הפניה, לא כרטיס בתוך כרטיס
public class Link
{
    public int val;
    public Link next;

    public Link(int val, Link next)
    {
        this.val = val;
        this.next = next;
    }
}

public class Shelf
{
    public int[] many = new int[20];
    public Student[] people = new Student[2];
    public int[,] grid = new int[2, 3];
    public int[,] big = new int[7, 2];
    public string[] words = { "אב", "גד" };

    public void Fill()
    {
        many[3] = 4;
        people[0] = new Student("דן", 70);
    }

    public void Mark(int r, int c)
    {
        grid[r, c] = 9;
    }
}

// ארבע רמות: L1 ובתוכו L2, L3, L4 — ו-L5 כבר רק טקסט
public class L1
{
    public L2 inner = new L2();
}

public class L2
{
    public L3 inner = new L3();
}

public class L3
{
    public L4 inner = new L4();
}

public class L4
{
    public int x = 4;
    public L5 inner = new L5();
}

public class L5
{
    public int y = 5;
}
