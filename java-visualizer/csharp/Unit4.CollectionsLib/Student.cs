using System;
using System.Globalization;
using System.Linq;

namespace Unit4.CollectionsLib
{
    /// <summary>
    /// תלמיד — מחלקה מוכנה לתרגילים: שם, כיתה, תעודת זהות, גיל וציונים.
    /// <code>
    ///   Student s = new Student("נועה לוי", "יא2", 90, 85, 77);   // שם, כיתה, ציונים
    ///   Student t = new Student("דני כהן", 88);                     // שם וציון אחד
    ///   Student r = Student.Random();                               // תלמיד אקראי (נתוני דמה)
    ///   s.GetName()  s.GetAverage()  s.GetGrade(0)  s.GetNumGrades()  s.IsExcellent() ...
    /// </code>
    /// תלמידים אפשר לשים בכל מבנה: Queue&lt;Student&gt;, Stack&lt;Student&gt;, Node&lt;Student&gt;, Student[] ...
    /// ובתצוגה רואים את השם והממוצע.
    /// </summary>
    public class Student : IComparable<Student>
    {
        string name, classRoom, id;
        int age;
        int[] grades;
        internal int VizId;   // זהות בהרצה הוויזואלית (Recorder.NextObjId)

        /// <summary>תלמיד עם שם, כיתה וציונים (אפשר כמה ציונים שרוצים)</summary>
        public Student(string name, string classRoom, params int[] grades)
        {
            this.name = name;
            this.classRoom = classRoom;
            this.grades = grades == null ? new int[0] : (int[])grades.Clone();
            this.id = MakeId(name);
            this.age = 15 + Math.Abs(JavaHash(name) % 4);
            VizId = Recorder.NextObjId();
        }

        /// <summary>תלמיד עם שם וציון אחד</summary>
        public Student(string name, int grade) : this(name, "", grade) { }

        /// <summary>תלמיד עם כל הפרטים</summary>
        public Student(string name, string id, string classRoom, int age, params int[] grades) : this(name, classRoom, grades)
        {
            this.id = id;
            this.age = age;
        }

        // ------------------------------------------------------------ פרטים

        // כל קריאה / שינוי של מאפיין מתועדים בהרצה הוויזואלית (Recorder.Got / Changed): הכרטיס של התלמיד
        // מוצג עם המאפיין שנקרא מודגש. בתוך המחלקה עצמה משתמשים בשדות ישירות — כדי שלא יתועדו קריאות פנימיות

        public string GetName() { return Recorder.Got(this, "name", "GetName()", name); }
        public void SetName(string name) { this.name = name; Recorder.Changed(this, "name", "SetName(" + Recorder.Lit(name) + ")"); }

        public string GetClassRoom() { return Recorder.Got(this, "classRoom", "GetClassRoom()", classRoom); }
        public void SetClassRoom(string classRoom) { this.classRoom = classRoom; Recorder.Changed(this, "classRoom", "SetClassRoom(" + Recorder.Lit(classRoom) + ")"); }

        /// <summary>תעודת זהות (9 ספרות)</summary>
        public string GetId() { return Recorder.Got(this, "id", "GetId()", id); }
        public void SetId(string id) { this.id = id; Recorder.Changed(this, "id", "SetId(" + Recorder.Lit(id) + ")"); }

        public int GetAge() { return Recorder.Got(this, "age", "GetAge()", age); }
        public void SetAge(int age) { this.age = age; Recorder.Changed(this, "age", "SetAge(" + age + ")"); }

        // ------------------------------------------------------------ ציונים

        public int GetNumGrades() { return Recorder.Got(this, "grades", "GetNumGrades()", grades.Length); }

        /// <summary>הציון במקום i (מ-0)</summary>
        public int GetGrade(int i) { return Recorder.Got(this, "grades", "GetGrade(" + i + ")", grades[i]); }

        /// <summary>משנה את הציון במקום i (מ-0)</summary>
        public void SetGrade(int i, int grade)
        {
            grades[i] = grade;
            Recorder.Changed(this, "grades", "SetGrade(" + i + ", " + grade + ")");
        }

        /// <summary>מוסיף ציון בסוף</summary>
        public void AddGrade(int grade)
        {
            Array.Resize(ref grades, grades.Length + 1);
            grades[grades.Length - 1] = grade;
            Recorder.Changed(this, "grades", "AddGrade(" + grade + ")");
        }

        /// <summary>עותק של מערך הציונים</summary>
        public int[] GetGrades() { return Recorder.Got(this, "grades", "GetGrades()", (int[])grades.Clone()); }

        /// <summary>ממוצע הציונים (0 אם אין ציונים)</summary>
        public double GetAverage() { return Recorder.Got(this, "average", "GetAverage()", Average()); }

        double Average()
        {
            if (grades.Length == 0) return 0;
            int sum = 0;
            foreach (int g in grades) sum += g;
            return (double)sum / grades.Length;
        }

        public int GetMaxGrade() { return Recorder.Got(this, "grades", "GetMaxGrade()", grades.Length == 0 ? 0 : grades.Max()); }
        public int GetMinGrade() { return Recorder.Got(this, "grades", "GetMinGrade()", grades.Length == 0 ? 0 : grades.Min()); }

        /// <summary>ממוצע 90 ומעלה</summary>
        public bool IsExcellent() { return Recorder.Got(this, "average", "IsExcellent()", Average() >= 90); }

        /// <summary>ממוצע 55 ומעלה</summary>
        public bool IsPassing() { return Recorder.Got(this, "average", "IsPassing()", Average() >= 55); }

        // ------------------------------------------------------------ השוואה והצגה

        /// <summary>השוואה לפי ממוצע (מאפשר עץ חיפוש של תלמידים)</summary>
        public int CompareTo(Student other) { return Average().CompareTo(other.Average()); }

        /// <summary>שני תלמידים שווים אם יש להם אותה תעודת זהות</summary>
        public override bool Equals(object o)
        {
            if (ReferenceEquals(this, o)) return true;
            var s = o as Student;
            return s != null && id != null && id == s.id;
        }

        public override int GetHashCode() { return id == null ? 0 : id.GetHashCode(); }

        /// <summary>"נועה לוי (יא2) ממוצע 84"</summary>
        public override string ToString()
        {
            return name + (string.IsNullOrEmpty(classRoom) ? "" : " (" + classRoom + ")")
                 + " ממוצע " + (Math.Round(Average() * 10) / 10.0).ToString(CultureInfo.InvariantCulture);
        }

        /// <summary>תווית קצרה לתצוגה: "נועה 84"</summary>
        internal string Label()
        {
            string first = name == null ? "?" : name.Trim().Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries).FirstOrDefault() ?? "?";
            return first + " " + Math.Round(Average()).ToString(CultureInfo.InvariantCulture);
        }

        /// <summary>הכרטיס של התלמיד בחלון: שורה ראשונה — שם פרטי; שנייה — כיתה וממוצע; ומתחת — כל המאפיינים</summary>
        internal Obj VizObj()
        {
            var o = new Obj { Id = VizId, Type = "Student" };
            o.Title = name == null ? "?" : name.Trim().Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries).FirstOrDefault() ?? "?";
            o.Sub = (string.IsNullOrEmpty(classRoom) ? "" : classRoom + " · ") + Math.Round(Average()).ToString(CultureInfo.InvariantCulture);
            o.Fields.Add(new[] { "name", Recorder.Lit(name) });
            o.Fields.Add(new[] { "classRoom", Recorder.Lit(classRoom) });
            o.Fields.Add(new[] { "id", Recorder.Lit(id) });
            o.Fields.Add(new[] { "age", age.ToString(CultureInfo.InvariantCulture) });
            o.Fields.Add(new[] { "grades", Recorder.ArrayText(grades) });
            o.Fields.Add(new[] { "average", (Math.Round(Average() * 10) / 10.0).ToString("0.0", CultureInfo.InvariantCulture) });
            return o;
        }

        // ------------------------------------------------------------ נתוני דמה

        static readonly string[] First = { "נועה", "דני", "מאיה", "יונתן", "שירה", "איתי", "תמר", "עומר", "רוני", "אורי",
            "הילה", "נדב", "יעל", "אלון", "מיכל", "גיל", "ליאור", "עדי", "שחר", "טל" };
        static readonly string[] Last = { "כהן", "לוי", "מזרחי", "פרץ", "ביטון", "אברהם", "פרידמן", "דהן", "אזולאי", "גבאי",
            "שפירא", "חדד", "קליין", "אוחיון", "רוזן" };
        static readonly string[] Classes = { "י1", "י2", "יא1", "יא2", "יא3", "יב1", "יב2" };
        static readonly Random Rnd = new Random();

        /// <summary>תלמיד אקראי — שם, כיתה ו-3 עד 4 ציונים</summary>
        public static Student Random()
        {
            var g = new int[3 + Rnd.Next(2)];
            for (int i = 0; i < g.Length; i++) g[i] = 55 + Rnd.Next(46);
            return new Student(First[Rnd.Next(First.Length)] + " " + Last[Rnd.Next(Last.Length)], Classes[Rnd.Next(Classes.Length)], g);
        }

        /// <summary>מערך של n תלמידים אקראיים</summary>
        public static Student[] Random(int n)
        {
            var a = new Student[n];
            for (int i = 0; i < n; i++) a[i] = Random();
            return a;
        }

        /// <summary>כמו String.hashCode של Java — כדי שאותו שם ייתן תמיד אותה תעודת זהות</summary>
        internal static int JavaHash(string s)
        {
            int h = 0;
            if (s != null) foreach (char c in s) h = unchecked(31 * h + c);
            return h;
        }

        static string MakeId(string name)
        {
            long h = Math.Abs((long)JavaHash(name) * 2654435761L) % 100000000L;
            string baseId = h.ToString("D8", CultureInfo.InvariantCulture);
            int sum = 0;   // ספרת ביקורת כמו בתעודת זהות ישראלית
            for (int i = 0; i < 8; i++)
            {
                int d = (baseId[i] - '0') * (i % 2 == 0 ? 1 : 2);
                sum += d > 9 ? d - 9 : d;
            }
            return baseId + (10 - sum % 10) % 10;
        }
    }
}
