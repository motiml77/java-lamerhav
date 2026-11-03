import java.io.File;
import java.util.Scanner;

public class MissingFile {
    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(new File("no-such-file.txt"));
        System.out.println(in.nextLine());
    }
}
