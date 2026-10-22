import il.lamerhav.dsviz.NameCheck;
import il.lamerhav.dsviz.Scaffold;
import il.lamerhav.dsviz.cs.ScaffoldCs;
import java.nio.file.*;
import java.util.*;

/** אותן בדיקות שם כמו names-test.js (names-cases.txt) — מול NameCheck.java, כדי ששני העותקים יתנהגו אותו דבר */
public class NameCheckTest {
  public static void main(String[] a) throws Exception {
    StringBuilder raw = new StringBuilder();
    for (String l : Files.readAllLines(Paths.get(a[0]), java.nio.charset.StandardCharsets.UTF_8)) if (!l.startsWith("#")) raw.append(l).append('\n');
    int bad = 0, n = 0;
    for (String block : raw.toString().split("(?m)^====$")) {
      String b = block.replaceAll("^\\n+", "");
      String[] lines = b.split("\n", -1);
      String[] head = lines[0].split("\\|");
      String code = String.join("\n", Arrays.copyOfRange(lines, 1, lines.length));
      int offset = code.indexOf('§');
      String text = code.replace("§", "");
      boolean cs = text.contains("using System");
      NameCheck.Ident id = cs ? s -> ScaffoldCs.isIdentifier(s) : s -> Scaffold.isIdentifier(s);
      String err = NameCheck.checkName(head[0], text, offset, id);
      String got = err == null ? "ok" : !id.ok(head[0]) || NameCheck.RESERVED_NAMES.contains(head[0]) ? "bad" : "taken";
      n++;
      if (!got.equals(head[1])) { bad++; System.out.println("נכשל: " + head[0] + " — " + head[2] + ": ציפינו " + head[1] + ", קיבלנו " + got); }
    }
    if (bad > 0) { System.out.println(bad + " מתוך " + n + " נכשלו"); System.exit(1); }
    System.out.println(n + " name checks OK");
  }
}
