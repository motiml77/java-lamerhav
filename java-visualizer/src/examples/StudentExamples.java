package examples;

import unit4.collectionsLib.*;

/** דוגמאות עם תלמידים: מצטיינים בתור, והתלמיד הטוב ביותר במערך */
public class StudentExamples {

    /** כמה תלמידים בתור עם ממוצע לפחות x — התור חוזר למצבו */
    public static int countAbove(Queue<Student> q1, int x) {
        Queue<Student> tmp = new Queue<Student>();   // עזר: שומר את התלמידים כדי להחזיר אותם לתור
        int count = 0;                               // כמה תלמידים עברו את x
        while (!q1.isEmpty()) {
            Student s1 = q1.remove();                // התלמיד שהוצאנו עכשיו
            if (s1.getAverage() >= x) count++;
            tmp.insert(s1);
        }
        while (!tmp.isEmpty()) q1.insert(tmp.remove());
        return count;
    }

    /** התלמיד עם הממוצע הגבוה ביותר */
    public static Student best(Student[] arr) {
        Student best = arr[0];           // הטוב ביותר שמצאנו עד עכשיו (מתחילים מהראשון)
        for (int i = 1; i < arr.length; i++) {
            if (arr[i].getAverage() > best.getAverage()) best = arr[i];
        }
        return best;
    }

    public static void main(String[] args) {
        Queue<Student> q1 = new Queue<Student>();
        for (int i = 0; i < 5; i++) {
            q1.insert(Student.random());   // תלמיד עם נתוני דמה
        }
        System.out.println("ממוצע 80 ומעלה: " + countAbove(q1, 80));

        Student[] arr = new Student[4];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = Student.random();
        }
        Student s1 = best(arr);
        System.out.println("הכי טוב: " + s1);

        Student s2 = new Student("נועה לוי", "יא2", 90, 85, 77);
        s2.addGrade(100);
        System.out.println(s2.getName() + " — ממוצע " + s2.getAverage() + ", מצטיינת: " + s2.isExcellent());
    }
}
