import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

// קלט בלי Scanner: תו אחד ב-System.in.read(), ושורה ב-BufferedReader.readLine (גם בעברית)
public class ReadInput {
    public static void main(String[] args) throws IOException {
        System.out.print("אות באנגלית: ");
        int c = System.in.read();
        while (System.in.read() != '\n') {
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("עיר: ");
        String city = br.readLine();
        System.out.println("קיבלתי " + (char) c + " ו-" + city + " (" + city.length() + " תווים)");
    }
}
