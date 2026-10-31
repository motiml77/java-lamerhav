import java.util.Scanner;

// קלט עד שנגמר: while (in.hasNextInt()) — בחלון "סיום הקלט", בבדיקה סוף הקלט
public class SumUntil {
    static int readSum(Scanner in) {
        int sum = 0;
        System.out.println("הקלידו מספרים:");
        while (in.hasNextInt()) {
            sum += in.nextInt();
        }
        return sum;
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int s = readSum(in);
        System.out.println("סכום: " + s);
    }
}
