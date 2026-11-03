import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Features {
    record Point(int x, int y) {}

    enum Color { RED, GREEN }

    interface Shape {
        int area();
    }

    class Inner {
        int v = 3;
    }

    public static void main(String[] args) {
        var list = new ArrayList<Map<String, List<Integer>>>();
        list.add(new HashMap<>());
        Point p = new Point(1, 2);
        Color c = Color.GREEN;
        String name = switch (c) {
            case RED -> "אדום";
            case GREEN -> "ירוק";
        };
        Shape sq = new Shape() {
            public int area() {
                return 4;
            }
        };
        Function<Integer, Integer> dbl = x -> x * 2;
        Features m = new Features();
        Features.Inner in = m.new Inner();
        System.out.println(p + " " + name + " " + sq.area() + " " + dbl.apply(5) + " " + in.v);
    }
}
