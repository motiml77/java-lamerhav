// מחלקות של תרגיל אחר באותו פרויקט — Main לא משתמש בהן
public class BusRoute
{
    private Station[] stations;
    private int count;
    public BusRoute(int size) { stations = new Station[size]; }
    public void Add(Station s) { stations[count++] = s; }
}

public class Station
{
    private string name;
    public Station(string name) { this.name = name; }
}
