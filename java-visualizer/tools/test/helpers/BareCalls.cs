using System;
using Unit4.CollectionsLib;

// פעולות העזר בלי "Helpers." ובלי שורת using — global using (Directory.Build.props / dsviz-cs)
public class BareCalls
{
    public static void Main(string[] args)
    {
        Queue<int> q = new Queue<int>();
        q.Insert(4); q.Insert(9);
        Queue<int> copy = Duplicate(q);
        copy.Remove();
        Console.WriteLine("size " + Size(q) + " " + Size(copy));
        Console.WriteLine("MARK-END");
    }
}
