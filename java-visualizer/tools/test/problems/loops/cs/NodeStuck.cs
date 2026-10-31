using System;
using Unit4.CollectionsLib;

// סכום הרשימה — עם טעות: שכחו להתקדם (p = p.GetNext())
public class NodeStuck {
    public static int Sum(Node<int> lst) {
        int total = 0;
        Node<int> p = lst;
        while (p != null) {
            total = total + p.GetValue();
        }
        return total;
    }

    public static void Main(string[] args) {
        Node<int> lst = new Node<int>(3, new Node<int>(8, new Node<int>(5)));
        Console.WriteLine(Sum(lst));
    }
}
