import java.awt.*; import java.awt.event.KeyEvent; import java.awt.image.BufferedImage; import java.io.File; import javax.imageio.ImageIO; import java.util.*;
// Shot <out-prefix> <wait-ms> <stepsAt...> -- <command...>   (presses Left between captures)
public class Shot {
  public static void main(String[] a) throws Exception {
    int sep = Arrays.asList(a).indexOf("--");
    String out = a[0]; long wait = Long.parseLong(a[1]);
    Process p = new ProcessBuilder(Arrays.asList(a).subList(sep + 1, a.length)).redirectErrorStream(true).redirectOutput(new File(out + ".log")).start();
    Thread.sleep(wait);
    Robot r = new Robot(); r.setAutoDelay(30);
    Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
    
    int at = 1;
    for (int i = 2; i < sep; i++) {
      int target = Integer.parseInt(a[i]);
      while (at < target) { r.mouseMove(Integer.getInteger("nx",1150), Integer.getInteger("ny",59)); r.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK); r.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK); at++; }
      Thread.sleep(700);
      ImageIO.write(r.createScreenCapture(new Rectangle(scr)), "png", new File(out + "-" + target + ".png"));
    }
    p.destroyForcibly();
    ProcessHandle.allProcesses().filter(h -> h.info().commandLine().orElse("").contains("dsviz.child")).forEach(ProcessHandle::destroyForcibly);
  }
}
