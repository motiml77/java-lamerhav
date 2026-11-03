package pkg.sub;

import unit4.collectionsLib.*;

public class InPkg {
    public static void main(String[] args) {
        Stack<String> s = new Stack<String>();
        s.push("א");
        System.out.println("top=" + s.top());
    }
}
