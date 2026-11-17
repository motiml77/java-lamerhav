// "האם קטע הקוד תקין?" (דף העבודה, עמ' 6): השורות שמתקמפלות — ההמרה האחרונה נכשלת בזמן ריצה
public class Valid {
    public static void main(String[] args) {
        Mammal goofy = new Mammal("Goofy", "male", 250);
        Object obj = goofy;
        Animal ann = (Animal) obj;
        System.out.println(ann.getGender());
        System.out.println(((Bird) obj).getName());
    }
}

class Animal {
    private String name;
    private String gender;

    public Animal(String name, String gender) {
        this.name = name;
        this.gender = gender;
    }

    public String getName() { return name; }

    public String getGender() { return gender; }
}

class Mammal extends Animal {
    private int milk;

    public Mammal(String name, String gender, int milk) {
        super(name, gender);
        this.milk = milk;
    }
}

class Bird extends Animal {
    public Bird(String name, String gender) { super(name, gender); }
}
