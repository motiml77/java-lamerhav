using System;
using Unit4.CollectionsLib;
public class NullNode {
    public static void Main(string[] args) {
        Node<int> lst = new Node<int>(3, new Node<int>(5));
        Node<int> p = lst;
        int sum = 0;
        while (true) {
            sum += p.GetValue();
            p = p.GetNext();
        }
    }
}
