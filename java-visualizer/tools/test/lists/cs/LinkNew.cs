using System;
using Unit4.CollectionsLib;

public class Program
{
    public static void Main(string[] args)
    {
        Node<int> list = new Node<int>(3, new Node<int>(7, new Node<int>(1)));
        Node<int> n = new Node<int>(5);
        n.SetNext(list);
        list = n;
        Node<int> p = list;
        while (p.GetNext() != null)
            p = p.GetNext();
        Node<int> t = new Node<int>(9);
        p.SetNext(t);
        p = list.GetNext();
        Node<int> q = new Node<int>(4);
        q.SetNext(p.GetNext());
        p.SetNext(q);
        Console.WriteLine(list);
    }
}
