using System;
using System.Globalization;

namespace Unit4.CollectionsLib
{
    /// <summary>
    /// מכונית — מחלקה מוכנה לתרגילים: יצרן, דגם, שנה, צבע, מספר רישוי, קילומטראז', דלק ומהירות.
    /// <code>
    ///   Car c = new Car("Mazda", "3", 2019);                  // יצרן, דגם, שנת ייצור
    ///   Car d = new Car("Kia", "Picanto", 2015, "לבן", "12-345-67");
    ///   Car r = Car.Random();                                  // מכונית אקראית (נתוני דמה)
    ///   c.Drive(120);   c.Refuel(20);   c.Accelerate(30);   c.Brake(10);
    ///   c.GetKm()   c.GetFuel()   c.GetSpeed()   c.GetAge()   c.IsOld()   c.NeedsFuel() ...
    /// </code>
    /// מכוניות אפשר לשים בכל מבנה: Queue&lt;Car&gt;, Stack&lt;Car&gt;, Node&lt;Car&gt;, Car[] ...
    /// ובתצוגה רואים את היצרן והשנה (למשל "Mazda 2019").
    /// </summary>
    public class Car : IComparable<Car>
    {
        /// <summary>צריכת דלק: ליטרים ל-100 ק"מ</summary>
        public const double LitersPer100Km = 8;

        string manufacturer, model, color, plate;
        int year, km, speed;
        double fuel, tankSize;

        /// <summary>מכונית עם יצרן, דגם ושנת ייצור (מיכל חצי מלא, בלי נסיעות)</summary>
        public Car(string manufacturer, string model, int year)
            : this(manufacturer, model, year, "לבן", MakePlate(manufacturer + model + year)) { }

        /// <summary>מכונית עם כל הפרטים</summary>
        public Car(string manufacturer, string model, int year, string color, string plate)
        {
            this.manufacturer = manufacturer;
            this.model = model;
            this.year = year;
            this.color = color;
            this.plate = plate;
            this.tankSize = 50;
            this.fuel = 25;
        }

        // ------------------------------------------------------------ פרטים

        public string GetManufacturer() { return manufacturer; }
        public void SetManufacturer(string manufacturer) { this.manufacturer = manufacturer; }

        public string GetModel() { return model; }
        public void SetModel(string model) { this.model = model; }

        /// <summary>שנת ייצור</summary>
        public int GetYear() { return year; }
        public void SetYear(int year) { this.year = year; }

        public string GetColor() { return color; }
        public void SetColor(string color) { this.color = color; }

        /// <summary>מספר רישוי</summary>
        public string GetPlate() { return plate; }
        public void SetPlate(string plate) { this.plate = plate; }

        /// <summary>קילומטראז' (כמה ק"מ נסעה)</summary>
        public int GetKm() { return km; }
        public void SetKm(int km) { this.km = km; }

        /// <summary>כמה ליטרים יש במיכל עכשיו</summary>
        public double GetFuel() { return fuel; }

        /// <summary>גודל המיכל בליטרים</summary>
        public double GetTankSize() { return tankSize; }

        /// <summary>המהירות עכשיו (קמ"ש)</summary>
        public int GetSpeed() { return speed; }

        // ------------------------------------------------------------ פעולות

        /// <summary>נוסעת עד km ק"מ. אם נגמר הדלק — נעצרת. מחזירה כמה ק"מ נסעה בפועל.</summary>
        public int Drive(int km)
        {
            int possible = (int)(fuel / LitersPer100Km * 100);
            int driven = Math.Max(0, Math.Min(km, possible));
            this.km += driven;
            fuel -= driven * LitersPer100Km / 100;
            Recorder.Note(Label() + ".Drive(" + km + ")  →  נסעה " + driven + " ק\"מ");
            return driven;
        }

        /// <summary>מתדלקת (עד שהמיכל מלא). מחזירה כמה ליטרים נכנסו</summary>
        public double Refuel(double liters)
        {
            double added = Math.Max(0, Math.Min(liters, tankSize - fuel));
            fuel += added;
            Recorder.Note(Label() + ".Refuel(" + liters.ToString(CultureInfo.InvariantCulture) + ")  →  נכנסו "
                          + added.ToString(CultureInfo.InvariantCulture) + " ליטר");
            return added;
        }

        /// <summary>מוסיפה למהירות</summary>
        public void Accelerate(int delta)
        {
            speed += Math.Max(0, delta);
            Recorder.Note(Label() + ".Accelerate(" + delta + ")  →  " + speed + " קמ\"ש");
        }

        /// <summary>מורידה מהירות (לא פחות מ-0)</summary>
        public void Brake(int delta)
        {
            speed = Math.Max(0, speed - Math.Max(0, delta));
            Recorder.Note(Label() + ".Brake(" + delta + ")  →  " + speed + " קמ\"ש");
        }

        /// <summary>כמה שנים עברו מאז שנת הייצור</summary>
        public int GetAge() { return DateTime.Now.Year - year; }

        /// <summary>10 שנים ומעלה</summary>
        public bool IsOld() { return GetAge() >= 10; }

        /// <summary>פחות מעשירית מיכל</summary>
        public bool NeedsFuel() { return fuel < tankSize / 10; }

        /// <summary>נוסעת (המהירות גדולה מ-0)</summary>
        public bool IsMoving() { return speed > 0; }

        // ------------------------------------------------------------ השוואה והצגה

        /// <summary>השוואה לפי שנת ייצור (מאפשר עץ חיפוש של מכוניות)</summary>
        public int CompareTo(Car other) { return year.CompareTo(other.year); }

        /// <summary>שתי מכוניות שוות אם יש להן אותו מספר רישוי</summary>
        public override bool Equals(object o)
        {
            if (ReferenceEquals(this, o)) return true;
            var c = o as Car;
            return c != null && plate != null && plate == c.plate;
        }

        public override int GetHashCode() { return plate == null ? 0 : plate.GetHashCode(); }

        /// <summary>"Mazda 3 (2019)"</summary>
        public override string ToString() { return manufacturer + " " + model + " (" + year + ")"; }

        /// <summary>תווית קצרה לתצוגה: "Mazda 2019"</summary>
        internal string Label() { return manufacturer + " " + year; }

        // ------------------------------------------------------------ נתוני דמה

        static readonly string[][] Models =
        {
            new[] { "Toyota", "Corolla", "Yaris", "Camry" }, new[] { "Mazda", "3", "6", "CX-5" }, new[] { "Kia", "Picanto", "Rio", "Sportage" },
            new[] { "Hyundai", "i10", "i20", "Tucson" }, new[] { "Skoda", "Octavia", "Fabia", "Kodiaq" }, new[] { "Honda", "Civic", "Jazz", "CR-V" },
            new[] { "Suzuki", "Swift", "Vitara", "Ignis" }, new[] { "Seat", "Ibiza", "Leon", "Ateca" }
        };
        static readonly string[] Colors = { "לבן", "שחור", "כסוף", "אפור", "אדום", "כחול", "ירוק" };
        static readonly Random Rnd = new Random();

        /// <summary>מכונית אקראית — יצרן, דגם, שנה, צבע, קילומטראז' ודלק</summary>
        public static Car Random()
        {
            string[] m = Models[Rnd.Next(Models.Length)];
            int year = 2008 + Rnd.Next(17);
            var c = new Car(m[0], m[1 + Rnd.Next(m.Length - 1)], year, Colors[Rnd.Next(Colors.Length)], MakePlate(Rnd.Next().ToString()));
            c.km = (DateTime.Now.Year - year + 1) * (6000 + Rnd.Next(14000));
            c.fuel = 5 + Rnd.Next(46);
            return c;
        }

        /// <summary>מערך של n מכוניות אקראיות</summary>
        public static Car[] Random(int n)
        {
            var a = new Car[n];
            for (int i = 0; i < n; i++) a[i] = Random();
            return a;
        }

        /// <summary>מספר רישוי בן 7 ספרות, כמו "12-345-67"</summary>
        static string MakePlate(string seed)
        {
            long h = Math.Abs((long)Student.JavaHash(seed) * 2654435761L) % 10000000L;
            string d = h.ToString("D7", CultureInfo.InvariantCulture);
            return d.Substring(0, 2) + "-" + d.Substring(2, 3) + "-" + d.Substring(5);
        }
    }
}
