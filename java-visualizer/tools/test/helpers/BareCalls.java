import unit4.collectionsLib.*;
import static unit4.collectionsLib.Helpers.*;

// פעולות העזר בלי "Helpers." — שורת import static אחת (התוסף מוסיף אותה כשיוצרים מבנה)
public class BareCalls {
    public static void main(String[] args) {
        Queue<Integer> q = new Queue<Integer>();
        q.insert(4);
        q.insert(9);
        Queue<Integer> copy = duplicate(q);
        copy.remove();
        System.out.println("size " + size(q) + " " + size(copy));
        System.out.println("MARK-END");
    }
}
