public class Threads {
    public static void main(String[] args) throws Exception {
        Thread t = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                System.out.println("t" + i);
            }
        });
        t.start();
        t.join();
        System.out.println("main done");
    }
}
