// "צור מחלקה" עם הורשה: מחלקה מופשטת עם פעולה מופשטת — מחלקה יורשת חייבת לדרוס אותה
abstract class Shape {
    private String color;

    public Shape(String color) {
        this.color = color;
    }

    public String getColor() {
        return color;
    }

    public abstract double area();

    public abstract void draw(char pen);
}
