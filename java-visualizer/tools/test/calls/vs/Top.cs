using System;
using Unit4.CollectionsLib;

Stack<int> s = new Stack<int>();
s.Push(3);
s.Push(5);
Console.WriteLine(Sum(s));

static int Sum(Stack<int> st)
{
    int t = 0;
    while (!st.IsEmpty()) t += st.Pop();
    return t;
}
