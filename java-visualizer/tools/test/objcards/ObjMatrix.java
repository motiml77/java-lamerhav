import unit4.collectionsLib.*;

public class ObjMatrix {
    public static void main(String[] args) {
        Student[][] cls = new Student[2][3];
        cls[0][0] = new Student("נועה לוי", "יא2", 90, 85, 77);
        cls[0][1] = new Student("דני כהן", "יא2", 70, 95);
        cls[1][2] = new Student("מאיה פרץ", "יא1", 98, 91);
        double avg = cls[0][0].getAverage();
        String n = cls[1][2].getName();
        cls[0][1].setGrade(0, 100);
    }
}
