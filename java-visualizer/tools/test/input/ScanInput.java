import java.util.Scanner;

import unit4.collectionsLib.Stack;

// קלט מהמקלדת: nextInt, לולאה (גם שני מספרים בשורה אחת), ו-nextLine בעברית
public class ScanInput {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        System.out.print("כמה מספרים? ");
        int n = in.nextInt();
        Stack<Integer> s = new Stack<Integer>();
        for (int i = 0; i < n; i++) {
            System.out.print("מספר " + (i + 1) + ": ");
            s.push(in.nextInt());
        }
        in.nextLine();
        System.out.print("מה השם שלך? ");
        String name = in.nextLine();
        int sum = 0;
        while (!s.isEmpty()) {
            sum += s.pop();
        }
        System.out.println("שלום " + name + ", הסכום: " + sum);
    }
}
