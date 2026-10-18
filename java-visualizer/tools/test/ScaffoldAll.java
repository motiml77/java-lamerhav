import il.lamerhav.dsviz.Scaffold;
import java.nio.file.*; import java.util.*;
public class ScaffoldAll {
  public static void main(String[] a) throws Exception {
    Path out = Paths.get(a[1]); Files.createDirectories(out);
    for (String line : Files.readAllLines(Paths.get(a[0]))) {
      if (line.isEmpty()) continue;
      String[] p = line.split("\\|", -1);
      Scaffold.Options o = new Scaffold.Options();
      o.className = p[0]; o.structure = p[1]; o.type = p[2]; o.returns = p[3];
      o.second = Boolean.parseBoolean(p[4]); o.extra = Boolean.parseBoolean(p[5]); o.random = Boolean.parseBoolean(p[6]);
      o.values = p[7]; o.values2 = p[8]; o.functionName = "solve";
      Scaffold.Result r = Scaffold.generate(o);
      Files.write(out.resolve(o.className + ".java"), (r.code + "//cursor=" + r.cursorLine + "\n").getBytes("UTF-8"));
    }
    System.out.println(String.join(" ", Scaffold.literals("a\\b, \"q\"", "String", false)) + " " + String.join(" ", Scaffold.literals("', \\", "Character", false)));
  }
}
