// מערך של Object עם עצמים של Person1 (כמו בצילום מהמשתמש): Person1 בלי הורשה / הכלה — אין תרשים. BusRoute ו-Station לא בשימוש
public class Single {
    public static void main(String[] args) {
        Object[] arr = new Person1[3];
        arr[0] = new Person1("naor", "123");
        arr[1] = new Person1("aa", "12");
        System.out.println(arr[0]);
    }
}
