package loops;

public class WrongWay {
    // הדפסה של 0..9 — עם טעות: i-- במקום i++
    public static void main(String[] args) {
        int printed = 0;
        for (int i = 0; i < 10; i--) {
            printed++;
        }
        System.out.println(printed);
    }
}
