using System;
using Unit4.CollectionsLib;

namespace GoldWater
{
    internal class Recursion
    {
        static int SumDigits(int n)
        {
            if (n < 10) return n;
            return n % 10 + SumDigits(n / 10);
        }

        static bool IsEven(int x)
        {
            return x % 2 == 0;
        }

        static int CountEven(Stack<int> s)
        {
            int count = 0;
            Stack<int> tmp = new Stack<int>();
            while (!s.IsEmpty())
            {
                int v = s.Pop();
                if (IsEven(v)) count++;
                tmp.Push(v);
            }
            while (!tmp.IsEmpty()) s.Push(tmp.Pop());
            return count;
        }

        static void Main(string[] args)
        {
            Stack<int> s = new Stack<int>();
            s.Push(4); s.Push(7); s.Push(10);
            Console.WriteLine(SumDigits(1234) + " " + CountEven(s));
        }
    }
}
