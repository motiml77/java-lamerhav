// מחלקות של תרגיל אחר באותה תיקייה — main של Mixed / Single לא משתמש בהן
public class BusRoute {
    private Station[] stations;
    private int count;

    public BusRoute(int size) {
        stations = new Station[size];
    }

    public void add(Station s) {
        stations[count++] = s;
    }
}
