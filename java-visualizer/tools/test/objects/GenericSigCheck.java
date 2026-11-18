package unit4.collectionsLib;

/**
 * הטיפוס המוצהר של משתנה ב-Java עם הפרמטרים (JdiTracer.fromSignature): כך הכותרת במבנה היא "st : Stack<Animal>" — כמו ב-C# —
 * ולא רק "st : Stack" (ב-Java הטיפוס של המשתנה בזמן ריצה בלי הפרמטרים).
 * <pre>  java -cp .:dsviz.jar unit4.collectionsLib.GenericSigCheck</pre>
 */
public class GenericSigCheck {
    static int fails = 0;

    static void eq(String sig, String want) {
        String got = JdiTracer.fromSignature(sig);
        boolean ok = want == null ? got == null : want.equals(got);
        System.out.println((ok ? "  ok  " : "  FAIL ") + sig + " → " + got + (ok ? "" : "   (צריך: " + want + ")"));
        if (!ok) fails++;
    }

    public static void main(String[] a) {
        eq("Lunit4/collectionsLib/Stack<LAnimal;>;", "Stack<Animal>");
        eq("Lunit4/collectionsLib/Queue<Lzoo/Person1;>;", "Queue<Person1>");
        eq("Lunit4/collectionsLib/Node<Ljava/lang/Integer;>;", "Node<Integer>");
        eq("Lunit4/collectionsLib/BinNode<LAnimal;>;", "BinNode<Animal>");
        eq("Lunit4/collectionsLib/Stack<Lunit4/collectionsLib/Queue<Ljava/lang/String;>;>;", "Stack<Queue<String>>");
        eq("Ljava/util/Map<Ljava/lang/String;[I>;", "Map<String, int[]>");
        eq("Ljava/util/List<+LAnimal;>;", "List<? extends Animal>");
        eq("Ljava/util/List<*>;", "List<?>");
        eq("Lunit4/collectionsLib/Node<TT;>;", "Node<T>");
        eq("LOuter$Inner<LAnimal;>;", "Inner<Animal>");
        eq("LOuter<LA;>.Inner;", null);   // מחלקה פנימית של טיפוס גנרי — בלי (נשאר השם הרגיל)
        eq("LAnimal;", null);             // בלי פרמטרים — השם הרגיל מ-typeName
        eq(null, null);
        eq("Lbroken<LA;", null);
        System.out.println(fails == 0 ? "MARK-END" : "נכשלו " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
