public class Pets {
    public static void main(String[] args) {
        Dog d1 = new Dog("רקס", 3);
        Dog d2 = new Dog("לאסי", 5);
        d1.birthday();
        boolean older = d2.isOlderThan(d1);
        System.out.println(d1.getName() + " " + older);
    }
}

class Dog {
    private String name;
    private int age;

    public Dog(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public void birthday() {
        age = age + 1;
        bark();
    }

    public void bark() {
        System.out.println(name + " נובח");
    }

    public boolean isOlderThan(Dog other) {
        return age > other.age;
    }

    public String getName() {
        return name;
    }
}
