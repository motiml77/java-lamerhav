namespace School.Model
{
    /// <summary>מחלקה שהתלמיד כתב — "צור מחלקה" צריכה להציע אותה ברשימת הטיפוסים (classgen-tests.sh)</summary>
    public class Student
    {
        private string name;

        public Student(string name)
        {
            this.name = name;
        }

        public string GetName()
        {
            return this.name;
        }

        public override string ToString()
        {
            return "Student " + this.name;
        }

        // מחלקה בתוך מחלקה — לא מוצעת
        class Grade
        {
        }
    }
}
