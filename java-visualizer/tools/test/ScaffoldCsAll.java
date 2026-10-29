import il.lamerhav.dsviz.Scaffold;
import il.lamerhav.dsviz.cs.ScaffoldCs;
import java.nio.file.*;
// כמו ScaffoldAll, אבל קוד C# (ScaffoldCs — הגרסה של Visual Studio); מדלג על צירופים שגויים (כמו cs-combos.js)
public class ScaffoldCsAll {
  public static void main(String[] a) throws Exception {
    Path out = Paths.get(a[1]); Files.createDirectories(out);
    for (String line : Files.readAllLines(Paths.get(a[0]))) {
      if (line.isEmpty()) continue;
      String[] p = line.split("\\|", -1);
      String text, file;
      if (p[0].equals("T")) {
        Scaffold.Options o = new Scaffold.Options();
        o.className = p[1]; o.structure = p[2]; o.type = p[3]; o.returns = p[4];
        o.second = Boolean.parseBoolean(p[5]); o.extra = Boolean.parseBoolean(p[6]); o.random = Boolean.parseBoolean(p[7]);
        o.values = p[8]; o.values2 = p[9]; o.recursive = Boolean.parseBoolean(p[10]); o.count = Integer.parseInt(p[11]); o.values3 = p[12]; o.functionName = "solve";
        if (p.length > 14) { o.helperSize = Boolean.parseBoolean(p[13]); o.helperCopy = Boolean.parseBoolean(p[14]); }
        file = p[1] + ".cs";
        try { text = ScaffoldCs.generate(o).code; } catch (IllegalArgumentException e) { continue; }
      } else {
        Scaffold.SnippetOptions o = new Scaffold.SnippetOptions();
        o.structure = p[2]; o.type = p[3]; o.name = p[4]; o.size = Integer.parseInt(p[5]); o.rows = Integer.parseInt(p[6]);
        o.cols = Integer.parseInt(p[7]); o.seed = Integer.parseInt(p[8]); o.indent = "        "; o.values = p.length > 9 ? p[9] : null;
        file = p[1] + ".snippet";
        try { text = ScaffoldCs.snippet(o); } catch (IllegalArgumentException e) { continue; }
      }
      Files.write(out.resolve(file), (text + "\n").getBytes("UTF-8"));
    }
  }
}
