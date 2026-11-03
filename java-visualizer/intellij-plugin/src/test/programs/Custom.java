public class Custom {
    static class MyException extends RuntimeException {
        MyException(String m) {
            super(m);
        }
    }

    public static void main(String[] args) {
        int x = 1;
        throw new MyException("משהו השתבש");
    }
}
