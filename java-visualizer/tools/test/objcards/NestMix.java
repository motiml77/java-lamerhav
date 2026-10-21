import unit4.collectionsLib.*;

public class NestMix {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Stack<Integer>[] arr = new Stack[3];
        for (int i = 0; i < arr.length; i++) arr[i] = new Stack<Integer>();
        arr[0].push(4);
        arr[1].push(8);
        arr[1].push(9);
        Stack<Stack<Stack<Integer>>> deep = new Stack<Stack<Stack<Integer>>>();
        Stack<Stack<Integer>> mid = new Stack<Stack<Integer>>();
        mid.push(arr[1]);
        deep.push(mid);
        int x = arr[1].pop();
        Queue<Node<Integer>> qn = new Queue<Node<Integer>>();
        qn.insert(new Node<Integer>(1, new Node<Integer>(2, new Node<Integer>(3))));
        qn.insert(new Node<Integer>(7));
        BinNode<Stack<Integer>> t = new BinNode<Stack<Integer>>(arr[0]);
        t.setLeft(new BinNode<Stack<Integer>>(arr[1]));
        t.setRight(new BinNode<Stack<Integer>>(new Stack<Integer>()));
        t.getRight().getValue().push(5);
        Stack<Integer> loop = new Stack<Integer>();
        System.out.println("done");
    }
}
