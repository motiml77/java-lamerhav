# מעטפת ויזואלית למבני נתונים — מחסנית, תור, רשימה מקושרת ועץ

כותבים פונקציה רגילה ב-Java, לוחצים **Run**, ונפתח חלון שמראה את ההרצה **צעד אחר צעד**:

- **המבנים עצמם** — מחסניות, תורים, רשימות מקושרות ועצים, מצוירים ומתעדכנים בכל צעד
- **שורת הקוד** שרצה עכשיו (מודגשת בצהוב)
- **מחסנית הקריאות** — רואים כל קריאה רקורסיבית
- **הפלט** (System.out) עד אותו צעד

אפשר לנוע קדימה ואחורה, להריץ אוטומטית, ולקפוץ לכל צעד עם פס הגלילה.

![מחסנית: סכום עם מחסנית עזר](docs/main.png)

| היפוך רשימה מקושרת | הכנסה לעץ חיפוש (רקורסיה) |
|---|---|
| ![](docs/list.png) | ![](docs/tree.png) |

---

## למה זה נוח

- **אותו API בדיוק כמו בספריית הבגרות** (`unit4.collectionsLib`):
  `Stack`, `Queue`, `Node`, `BinNode` עם `push/pop/top/isEmpty`, `insert/remove/head`,
  `getValue/getNext/setNext/hasNext`, `getLeft/getRight/setLeft/setRight/hasLeft/hasRight`.
  קוד קיים רץ כמו שהוא — רק `import unit4.collectionsLib.*;`
- **אפס מעטפת בקוד של התלמידים.** אין צורך לקרוא לשום פונקציית ציור — כל פעולה מתועדת אוטומטית.
- **בניה בשורה אחת** של מבנים לבדיקה: `Build.stack(1, 2, 3)`, `Build.list(4, 7, 1)`, `Build.tree(...)`.
- **שמות המשתנים מזוהים אוטומטית** מהקוד (`Stack<Integer> temp = new Stack<>()` יוצג בשם temp).
- Java טהורה (8 ומעלה), בלי ספריות חיצוניות. עובד ב-**Eclipse**, ב-**VS Code** ומשורת הפקודה.

---

## התקנה

### Eclipse
1. `File → Import → General → Existing Projects into Workspace`
2. בוחרים את התיקייה `java-visualizer` → `Finish`
3. פותחים את `src/Main.java` → לחיצה ימנית → `Run As → Java Application`

### VS Code
1. מתקינים את התוסף **Extension Pack for Java** (VS Code יציע אותו אוטומטית)
2. `File → Open Folder` → בוחרים את התיקייה `java-visualizer` עצמה
3. פותחים את `src/Main.java` ולוחצים **Run** שמופיע מעל `main`

### שורת פקודה
```
run.bat                          (Windows)
./run.sh                         (Mac / Linux)
./run.sh examples.ListExamples   ← הרצת קובץ אחר
```

### הוספה לפרויקט קיים
מעתיקים את התיקייה `src/unit4` לתוך תיקיית `src` של הפרויקט.
**חשוב:** אם בפרויקט כבר מחוברת ספריית הבגרות המקורית (קובץ jar של unit4) — יש להסיר אותה מה-Build Path,
אחרת יהיו שתי מחלקות `Stack` באותו שם.

---

## איך נראה תרגיל

```java
import unit4.collectionsLib.*;

public class Main {

    // כאן כותבים את הפונקציה של התרגיל
    public static int sum(Stack<Integer> s) {
        Stack<Integer> temp = new Stack<Integer>();
        int total = 0;
        while (!s.isEmpty()) {
            int x = s.pop();
            total += x;
            temp.push(x);
        }
        while (!temp.isEmpty()) {
            s.push(temp.pop());
        }
        return total;
    }

    public static void main(String[] args) {
        Stack<Integer> s = Build.stack(4, 7, 1, 9);   // 9 בראש המחסנית
        System.out.println("הסכום: " + sum(s));
    }
}
```

בתיקייה `src/examples` יש דוגמאות מוכנות להרצה:

| קובץ | מה מודגם |
|---|---|
| `StackExamples` | היפוך מחסנית עם מחסנית עזר, הכנסה לתחתית ברקורסיה |
| `QueueExamples` | ספירת איברים בלי להרוס את התור, מחיקת כל המופעים של ערך |
| `ListExamples`  | סכום, מחיקת חוליה, היפוך רשימה במקום (עם תוויות `p`, `prev`, `cur`) |
| `TreeExamples`  | מספר צמתים, גובה, הכנסה לעץ חיפוש בינארי |

---

## החלון

| אזור | מה רואים |
|---|---|
| למעלה | כפתורי ניווט, מהירות, ומונה "צעד X מתוך Y" |
| פס צבעוני | מה קרה בצעד הזה (למשל `temp.push(9)`), באיזו שורה ובאיזו פעולה |
| מבנים | כל המבנים הקיימים כרגע. המבנה שהצעד נגע בו מסומן בכותרת כחולה |
| קוד | הקובץ, עם השורה הנוכחית מודגשת |
| מחסנית הקריאות | הקריאה הפעילה למעלה, `main` למטה |
| פלט | כל מה שהודפס עד הצעד הזה |

**צבעים:** ירוק = נוסף / נוצר · צהוב = נקרא · כתום = שונה · אדום מקווקו = הוצא (pop / remove) ·
מסגרת כתומה ומשולש = החוליה / הצומת האחרונים שהקוד נגע בהם.

**מקלדת:** חץ שמאלה / רווח / Enter = צעד הבא · חץ ימינה = צעד קודם · Home / End = התחלה / סוף · P = הפעלה אוטומטית.

**"להציג בדיקות"** — כשמבטלים את הסימון, הניווט מדלג על `isEmpty / hasNext / hasLeft / hasRight`
ורואים רק את הפעולות שמשנות או קוראות.

---

## פקודות עזר

### Build — בניה מהירה (נספרת כצעד אחד בלבד)
```java
Stack<Integer>   s = Build.stack(1, 2, 3);           // push לפי הסדר → 3 בראש
Queue<String>    q = Build.queue("a", "b", "c");      // insert לפי הסדר → "a" בראש התור
Node<Integer>  lst = Build.list(4, 7, 1);            // 4 -> 7 -> 1 -> null
BinNode<Integer> t = Build.tree(5, 3, 8, null, 4);    // לפי רמות, null = אין צומת
BinNode<Integer> b = Build.bst(5, 3, 8, 1, 4);        // עץ חיפוש בינארי
Stack<Integer>   r = Build.randomStack(6, 1, 20);     // וגם randomQueue / randomList / randomBst
```

### Viz — שליטה בתצוגה (הכול אופציונלי)
```java
Viz.point("p", p);          // תווית "p" מעל החוליה ש-p מצביע עליה (לקרוא שוב אחרי כל p = p.getNext())
Viz.name(s, "מקורית");      // לתת שם למבנה
Viz.note("עכשיו מתחילים");  // הערה שמופיעה כצעד
Viz.off();  ...  Viz.on();  // לא לתעד קטע קוד (למשל לולאה ארוכה)
Viz.maxSteps(20000);        // ברירת המחדל: 5000 צעדים
Viz.textMode();             // בלי חלון — הצעדים מודפסים לקונסול
```

---

## דברים שכדאי לדעת

- **חוליה שנעלמת מהתצוגה** — כשאף משתנה כבר לא מצביע על חוליה (למשל אחרי מחיקה מהרשימה),
  היא נאספת ע"י ה-Garbage Collector ונעלמת, בדיוק כמו בזיכרון האמיתי. הזדמנות טובה להסביר GC 🙂
- **חוליה ראשונה שנשארת** — אחרי `lst = lst.getNext()` החוליה הישנה עדיין מצביעה הלאה, ולכן יכולה להופיע
  עד שינוי המצביעים הבא. `Viz.point("lst", lst)` מראה לאן `lst` מצביע עכשיו.
- **מחסנית/תור עזר ריקים** שלא נגעו בהם זמן רב מוסתרים, כדי שהמסך לא יתמלא במבני עזר ישנים.
- **שגיאות** (למשל `pop()` על מחסנית ריקה, `NullPointerException`, רקורסיה אינסופית) מופיעות כצעד אדום
  בשורה שגרמה להן.
- **לולאה אינסופית** — אחרי 5000 צעדים התיעוד נעצר והחלון מציג הודעה. סוגרים את החלון כדי לעצור את התוכנית.
- **הקוד לא מוצג?** החלון מחפש את קובץ המקור בתיקיית הפרויקט. ב-Eclipse וב-VS Code זה עובד אוטומטית;
  בשורת הפקודה צריך להריץ מתוך התיקייה `java-visualizer`.
- **עברית בקוד** — הקבצים שמורים ב-UTF-8 (הפרויקט כבר מוגדר כך ב-Eclipse וב-VS Code).

---

## מבנה הקבצים

```
java-visualizer/
├── src/
│   ├── Main.java                  ← תבנית לתרגיל
│   ├── examples/                  ← דוגמאות מוכנות
│   └── unit4/collectionsLib/
│       ├── Stack, Queue, Node, BinNode   ← המבנים (API של הבגרות)
│       ├── Build, Viz                    ← פקודות עזר
│       └── Recorder, Viewer, StructPanel, Model, SourceFiles  ← המנוע (פנימי)
├── run.bat / run.sh               ← הרצה משורת הפקודה
├── .project / .classpath          ← הגדרות Eclipse
└── .vscode/                       ← הגדרות VS Code
```
