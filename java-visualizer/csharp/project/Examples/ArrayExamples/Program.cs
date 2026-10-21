using System;
using Unit4.CollectionsLib;

// דוגמאות מערכים: מיון בועות, וסכום של מטריצה
class Program
{
    // מיון בועות: משווים זוגות שכנים ומחליפים אם הם בסדר הפוך
    static void Sort(int[] arr)
    {
        for (int i = 0; i < arr.Length - 1; i++)
        {
            for (int j = 0; j < arr.Length - 1 - i; j++)
            {
                if (arr[j] > arr[j + 1])
                {
                    int tmp = arr[j];            // החלפה בעזרת משתנה עזר
                    arr[j] = arr[j + 1];
                    arr[j + 1] = tmp;
                }
            }
        }
    }

    // סכום כל האיברים במטריצה
    static int Sum(int[,] mat)
    {
        int total = 0;
        for (int r = 0; r < mat.GetLength(0); r++)
        {
            for (int c = 0; c < mat.GetLength(1); c++)
            {
                total = total + mat[r, c];
            }
        }
        return total;
    }

    static void Main(string[] args)
    {
        int[] arr = { 5, 2, 8, 1, 9, 3 };
        Sort(arr);

        int[,] mat = { { 1, 2, 3 }, { 4, 5, 6 } };
        Console.WriteLine("סכום המטריצה: " + Sum(mat));
    }
}
