package examples;

import unit4.collectionsLib.*;

/** דוגמאות עם תלמידים: מצטיינים בתור, התלמיד הטוב ביותר במערך, מחסנית לפי ממוצע */
public class StudentExamples {

    /** כמה תלמידים בתור עם ממוצע לפחות x — התור חוזר למצבו */
    public static int countAbove(Queue<Student> q, int x) {
        Queue<Student> temp = new Queue<Student>();
        int count = 0;
        while (!q.isEmpty()) {
            Student s = q.remove();
            if (s.getAverage() >= x) count++;
            temp.insert(s);
        }
        while (!temp.isEmpty()) q.insert(temp.remove());
        return count;
    }

    /** התלמיד עם הממוצע הגבוה ביותר */
    public static Student best(Student[] arr) {
        Student best = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i].getAverage() > best.getAverage()) best = arr[i];
        }
        return best;
    }

    public static void main(String[] args) {
        Queue<Student> q = Build.queue(Build.students(5));
        System.out.println("ממוצע 80 ומעלה: " + countAbove(q, 80));

        Student[] cls = Build.array(Build.students(4));
        Student b = best(cls);
        System.out.println("הכי טוב: " + b);

        Student s = new Student("נועה לוי", "יא2", 90, 85, 77);
        s.addGrade(100);
        System.out.println(s.getName() + " — ממוצע " + s.getAverage() + ", מצטיינת: " + s.isExcellent());
    }
}
