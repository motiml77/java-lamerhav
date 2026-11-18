// תרשים המחלקות — רק המחלקות ש-main משתמש בהן: Dog יורשת מ-Animal. BusRoute ו-Station (בקובץ אחר, של תרגיל אחר) לא בתרשים
public class Mixed {
    public static void main(String[] args) {
        Animal a = new Dog("Rex");
        System.out.println(a.say());
    }
}
