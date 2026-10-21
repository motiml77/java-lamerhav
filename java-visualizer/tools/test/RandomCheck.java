import il.lamerhav.dsviz.Scaffold;
import java.nio.file.*;

/** ההגרלה של ערכים אקראיים (כולל מבנים פנימיים) זהה ב-Java וב-JS: קורא את randoms.txt שיצר combos.js */
public class RandomCheck {
  public static void main(String[] a) throws Exception {
    int n = 0;
    for (String line : Files.readAllLines(Paths.get(a[0]))) {
      if (line.isEmpty()) continue;
      String[] p = line.split("\\|", -1);
      String got = Scaffold.randomValues(p[0], p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]));
      if (!got.equals(p[4])) { System.out.println("שונה: " + line + "\n  Java: " + got); System.exit(1); }
      n++;
    }
    System.out.println(n + " הגרלות זהות");
  }
}
