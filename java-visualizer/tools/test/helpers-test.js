// פעולות העזר size / duplicate: בונה מחלקה עם כל הפעולות (מחסנית, תור, רשימה, עץ) ובדיקה שהן נכונות ולא פוגעות במבנה.
// run-tests.sh מקמפל ומריץ אותה — גם רגיל וגם בהרצה ויזואלית (LineByLine)
const fs = require('fs'), path = require('path');
const t = require('../../vscode-extension/templates');
const L = ['import unit4.collectionsLib.*;', '', 'public class HelpersCheck {'];
for (const st of ['queue_stack', 'list', 'tree']) L.push(...t.helperLines(st, true, true));
L.push(`
    static void check(boolean ok, String what) {
        if (!ok) throw new RuntimeException("FAIL " + what);
        System.out.println("ok " + what);
    }

    public static void main(String[] args) {
        Stack<Integer> s = new Stack<Integer>();
        for (int i = 1; i <= 5; i++) s.push(i * 10);
        String before = s.toString();
        check(size(s) == 5 && s.toString().equals(before), "size(Stack)");
        Stack<Integer> sc = duplicate(s);
        check(sc != s && sc.toString().equals(before) && s.toString().equals(before), "duplicate(Stack)");
        check(size(new Stack<Integer>()) == 0 && duplicate(new Stack<Integer>()).isEmpty(), "Stack ריקה");

        Queue<String> q = new Queue<String>();
        for (String x : "a b c d".split(" ")) q.insert(x);
        before = q.toString();
        check(size(q) == 4 && q.toString().equals(before), "size(Queue)");
        Queue<String> qc = duplicate(q);
        check(qc != q && qc.toString().equals(before) && q.toString().equals(before), "duplicate(Queue)");

        Queue<Stack<Integer>> qs = new Queue<Stack<Integer>>();
        qs.insert(s);
        qs.insert(new Stack<Integer>());
        check(size(qs) == 2 && size(qs.head()) == 5, "תור של מחסניות");

        Node<Integer> lst = new Node<Integer>(1, new Node<Integer>(2, new Node<Integer>(3)));
        check(size(lst) == 3 && size((Node<Integer>) null) == 0, "size(Node)");
        Node<Integer> lc = duplicate(lst);
        check(lc != lst && lc.getNext() != lst.getNext() && lc.getValue() == 1 && lc.getNext().getValue() == 2
                && lc.getNext().getNext().getValue() == 3 && !lc.getNext().getNext().hasNext(), "duplicate(Node)");

        BinNode<Integer> tr = new BinNode<Integer>(new BinNode<Integer>(1), 2, new BinNode<Integer>(null, 3, new BinNode<Integer>(4)));
        check(size(tr) == 4 && size((BinNode<Integer>) null) == 0, "size(BinNode)");
        BinNode<Integer> tc = duplicate(tr);
        check(tc != tr && tc.getValue() == 2 && tc.getLeft().getValue() == 1 && tc.getRight().getRight().getValue() == 4
                && tc.getRight() != tr.getRight() && !tc.getRight().hasLeft(), "duplicate(BinNode)");
        System.out.println("ALL OK");
    }
}`);
const out = process.argv[2];
fs.mkdirSync(out, { recursive: true });
fs.writeFileSync(path.join(out, 'HelpersCheck.java'), L.join('\n') + '\n');
