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
        String name = arr[0].getName();
        arr[1].setGrade(0, 100);
        garage.top().drive(50);

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
        System.exit(fails == 0 ? 0 : 1);
    }
}
