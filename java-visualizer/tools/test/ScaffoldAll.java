import il.lamerhav.dsviz.Scaffold;
import java.nio.file.*;
public class ScaffoldAll {
  public static void main(String[] a) throws Exception {
    Path out = Paths.get(a[1]); Files.createDirectories(out);
    for (String line : Files.readAllLines(Paths.get(a[0]))) {
      if (line.isEmpty()) continue;
      String[] p = line.split("\\|", -1);
      String text, file;
      try {
        if (p[0].equals("T")) {
          Scaffold.Options o = new Scaffold.Options();
          o.className = p[1]; o.structure = p[2]; o.type = p[3]; o.returns = p[4];
          o.second = Boolean.parseBoolean(p[5]); o.extra = Boolean.parseBoolean(p[6]); o.random = Boolean.parseBoolean(p[7]);
          o.values = p[8]; o.values2 = p[9]; o.recursive = Boolean.parseBoolean(p[10]); o.functionName = "solve";
          file = p[1] + ".java";
          try { Scaffold.Result r = Scaffold.generate(o); text = r.code + "//cursor=" + r.cursorLine; }
          catch (IllegalArgumentException e) { text = "ERROR: " + e.getMessage(); }
        } else {
          Scaffold.SnippetOptions o = new Scaffold.SnippetOptions();
          o.structure = p[2]; o.type = p[3]; o.name = p[4]; o.size = Integer.parseInt(p[5]); o.rows = Integer.parseInt(p[6]);
          o.cols = Integer.parseInt(p[7]); o.seed = Integer.parseInt(p[8]); o.indent = "        ";
          file = p[1] + ".snippet";
          try { text = Scaffold.snippet(o); } catch (IllegalArgumentException e) { text = "ERROR: " + e.getMessage(); }
        }
      } catch (RuntimeException e) { throw new RuntimeException(line, e); }
      Files.write(out.resolve(file), (text + "\n").getBytes("UTF-8"));
    }
  }
}
