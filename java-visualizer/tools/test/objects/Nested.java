// אובייקט בתוך אובייקט ומערך בתוך אובייקט: מה מצויר בתוך הכרטיס של האובייקט (run-tests.sh, בדיקה 5ו2; אותה תוכנית ב-C#: cs/Nested.cs)
public class Nested {
    public static void main(String[] args) {
        Student s = new Student("נועה", 90);
        Team t = new Team("אלפא", s);
        t.setScore(1, 7);
        t.boost();
        s.join(t);
        Link a = new Link(1, null);
        Link b = new Link(2, a);
        a.next = b;
        Shelf sh = new Shelf();
        sh.fill();
        sh.mark(1, 2);
        L1 deep = new L1();
        System.out.println(t.total() + " " + b.val + " " + sh.many.length + " " + deep.in.in.in.x);
    }
}

class Student {
    String name;
    int grade;
    Team team;

    Student(String name, int grade) {
        this.name = name;
        this.grade = grade;
    }

    void join(Team t) {
        team = t;
    }
}

class Team {
    private String name;
    private Student leader;
    private int[] scores;

    Team(String name, Student leader) {
        this.name = name;
        this.leader = leader;
        this.scores = new int[3];
    }

    void setScore(int i, int v) {
        scores[i] = v;
    }

    void boost() {
        leader.grade = leader.grade + 5;
    }

    int total() {
        int sum = 0;
        for (int i = 0; i < scores.length; i++) {
            sum += this.scores[i];
        }
        return sum;
    }
}

// רשימה מקושרת שהתלמיד כתב: מאפיין מאותו סוג — הפניה, לא כרטיס בתוך כרטיס
class Link {
    int val;
    Link next;

    Link(int val, Link next) {
        this.val = val;
        this.next = next;
    }
}

class Shelf {
    int[] many = new int[20];
    Student[] people = new Student[2];
    int[][] grid = new int[2][3];
    int[][] big = new int[7][2];
    String[] words = { "אב", "גד" };

    void fill() {
        many[3] = 4;
        people[0] = new Student("דן", 70);
    }

    void mark(int r, int c) {
        grid[r][c] = 9;
    }
}

// ארבע רמות: L1 ובתוכו L2, L3, L4 — ו-L5 כבר רק טקסט
class L1 {
    L2 in = new L2();
}

class L2 {
    L3 in = new L3();
}

class L3 {
    L4 in = new L4();
}

class L4 {
    int x = 4;
    L5 in = new L5();
}

class L5 {
    int y = 5;
}
