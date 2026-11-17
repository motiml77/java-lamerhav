namespace School.Model;

// class InComment — לא מחלקה
public class Car
{
    private string model;

    public Car(string model)
    {
        this.model = model;
    }

    public override string ToString()
    {
        return "Car " + this.model;
    }
}
