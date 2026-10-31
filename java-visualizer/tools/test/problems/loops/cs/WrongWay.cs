using System;

// הדפסה של 0..9 — עם טעות: i-- במקום i++
public class WrongWay {
    public static void Main(string[] args) {
        int printed = 0;
        for (int i = 0; i < 10; i--) {
            printed++;
        }
        Console.WriteLine(printed);
    }
}
