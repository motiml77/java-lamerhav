package unit4.collectionsLib;

/**
 * בדיקת כרטיסי אובייקטים (בלי חלון): תוכנית עם Student/Car במבנים שונים, ובודקים שהמקליט
 * רושם את תוכן האובייקט בכל מבנה ואת הגישה למאפיין (getName / setGrade / drive).
 */
public class ObjCheck {
    static int fails = 0;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  FAIL ") + what);
        if (!ok) fails++;
    }

    public static void main(String[] args) {
        Student[] arr = new Student[2];
        arr[0] = new Student("נועה לוי", "יא2", 90, 85, 77);
        arr[1] = new Student("דני כהן", "יא2", 70, 95);
        Stack<Car> garage = new Stack<Car>();
        garage.push(new Car("Mazda", "3", 2019));
        Queue<Student> q = new Queue<Student>();
        q.insert(arr[0]);
        Stack<Stack<Integer>> nest = new Stack<Stack<Integer>>();
        nest.push(new Stack<Integer>());
        nest.top().push(5);
        Queue<Queue<Integer>> qq = new Queue<Queue<Integer>>();
        qq.insert(new Queue<Integer>());
        Node<Stack<Integer>> lst = new Node<Stack<Integer>>(new Stack<Integer>());
        String name = arr[0].getName();
        arr[1].setGrade(0, 100);
        garage.top().drive(50);

        boolean nestedStack = false, nestedQueue = false, nestedChain = false, hiddenInner = true;
        boolean getName = false, setGrade = false, drive = false, stackCards = false, queueCards = false;
        for (int i = 0; i < Recorder.stepCount(); i++) {
            Model.Step s = Recorder.step(i);
            if (s.access != null) {
                String f = s.access.field;
                if (s.access.obj.type.equals("Student") && f.equals("name") && s.access.mark == Model.Mark.READ) getName = true;
                if (s.access.obj.type.equals("Student") && f.equals("grades") && s.access.mark == Model.Mark.CHANGED) setGrade = true;
                if (s.access.obj.type.equals("Car") && s.access.mark == Model.Mark.CHANGED) drive = true;
            }
            for (Model.View v : s.views) {
                if (v instanceof Model.LinearView && ((Model.LinearView) v).stack && !((Model.LinearView) v).subs.isEmpty()
                        && ((Model.LinearView) v).subs.get(0) instanceof Model.LinearView) nestedStack = true;
                if (v instanceof Model.LinearView && !((Model.LinearView) v).stack && !((Model.LinearView) v).subs.isEmpty()) nestedQueue = true;
                if (v instanceof Model.ChainView && ((Model.ChainView) v).cells.get(0).sub != null) nestedChain = true;
                // מחסנית פנימית בלי משתנה: מצוירת רק בתוך התא, לא גם לבד (כשהיא כבר בתוך המבנה)
                if (v.src != null) for (Model.View w : s.views)
                    if (w instanceof Model.LinearView) for (Model.View sub : ((Model.LinearView) w).subs)
                        if (sub != null && sub.src == v.src) hiddenInner = false;
                if (v instanceof Model.LinearView) {
                    Model.LinearView l = (Model.LinearView) v;
                    if (!l.objs.isEmpty() && l.objs.get(0) != null && l.objs.get(0).type.equals("Car") && l.stack) stackCards = true;
                    if (!l.objs.isEmpty() && l.objs.get(0) != null && l.objs.get(0).type.equals("Student") && !l.stack) queueCards = true;
                }
            }
        }
        check(getName, "getName() נרשם כקריאה של name");
        check(setGrade, "setGrade() נרשם כשינוי של grades");
        check(drive, "drive() של Car נרשם כשינוי");
        check(stackCards, "מחסנית של Car: כרטיסים");
        check(queueCards, "תור של Student: כרטיסים");
        check(nestedStack, "מחסנית של מחסניות: המחסנית הפנימית מצוירת בתוך התא");
        check(nestedQueue, "תור של תורים: התור הפנימי מצויר בתוך התא");
        check(nestedChain, "רשימה של מחסניות: המחסנית בתוך החוליה");
        check(hiddenInner, "מחסנית פנימית בלי משתנה לא מוצגת גם לבד");
        System.exit(fails == 0 ? 0 : 1);
    }
}
