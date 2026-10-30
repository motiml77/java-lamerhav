using System;
using Unit4.CollectionsLib;
public class EmptyPop {
    public static void Main(string[] args) {
        Stack<int> s = new Stack<int>();
        s.Push(4);
        s.Pop();
        int x = s.Pop();
        Console.WriteLine(x);
    }
}
