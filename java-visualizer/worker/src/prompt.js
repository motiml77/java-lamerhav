// ההנחיות למודל, תיאור הספרייה והתבנית (schema) של התשובה — ב-Java או ב-C#, לפי מה שהתלמיד בחר.
// הכול נמצא כאן, בשרת — התוסף שולח רק את דף התרגיל (ואת השפה), ולכן אי אפשר להשתמש בשרת לבקשות אחרות.

const LANG = {
  java: {
    name: 'Java',
    api: `
unit4.collectionsLib — imported automatically (import unit4.collectionsLib.*;). Exactly these classes and methods exist:

Stack<T>:   new Stack<T>()  · void push(T x) · T pop() · T top() · boolean isEmpty() · String toString()
Queue<T>:   new Queue<T>()  · void insert(T x) · T remove() · T head() · boolean isEmpty() · String toString()
Node<T>:    new Node<T>(T value) · new Node<T>(T value, Node<T> next)
            T getValue() · Node<T> getNext() · boolean hasNext() · void setValue(T value) · void setNext(Node<T> next)
            (getInfo()/setInfo() are aliases of getValue()/setValue())
BinNode<T>: new BinNode<T>(T value) · new BinNode<T>(BinNode<T> left, T value, BinNode<T> right)
            T getValue() · BinNode<T> getLeft() · BinNode<T> getRight() · boolean hasLeft() · boolean hasRight()
            void setValue(T value) · void setLeft(BinNode<T> left) · void setRight(BinNode<T> right)
BinTreeNode<T>: older name of a binary tree node (same as BinNode, with getInfo()/setInfo()) — use it only if the exercise uses it.
There is NO size(), length(), peek(), clone() or copy method in Stack/Queue. Nothing else exists in this library.
Arrays (int[], String[], int[][]) and String/Math/Integer/Character from java.lang are of course available.`,
    types: `מספר שלם → int, מספר ממשי → double, תו → char, מחרוזת → String, בוליאני/אמת-שקר → boolean,
    מחסנית של מספרים שלמים → Stack<Integer>, תור של מחרוזות → Queue<String>, שרשרת חוליות / רשימה מקושרת של שלמים → Node<Integer>,
    עץ בינארי של שלמים → BinNode<Integer>, מערך של שלמים → int[], מערך דו-ממדי → int[][]. Generics always use wrapper types (Integer, Character, Double, Boolean).`,
    names: 'camelCase method names (countEven, isSorted), PascalCase class names',
    mainHeader: 'public static void main(String[] args)',
    print: 'System.out.println("סעיף א: " + countEven(s1));',
    declare: 'Stack<Integer> s1 = new Stack<Integer>(); build chains with new Node<Integer>(...), trees with new BinNode<Integer>(left, value, right)',
    imports: 'imports — extra imports needed by mainCode/classes/givenCode, only java.util classes (e.g. "java.util.ArrayList"). Usually empty.',
    rules: 'Java 11, no "var", no records, no lambdas in mainCode. A method of the main class is "public static".',
    formats: 'boolean prints true / false; double prints like 2.5 or 3.0; char prints the character; an int[] array printed with Arrays.toString looks like [1, 2, 3].',
    noLib: 'NEVER define Stack, Queue, Node, BinNode or BinTreeNode (they come from the library). No package or import lines.',
  },
  csharp: {
    name: 'C#',
    api: `
Unit4.CollectionsLib — already imported (using System; using Unit4.CollectionsLib;). Exactly these classes and methods exist:

Stack<T>:   new Stack<T>()  · void Push(T x) · T Pop() · T Top() · bool IsEmpty() · string ToString()
Queue<T>:   new Queue<T>()  · void Insert(T x) · T Remove() · T Head() · bool IsEmpty() · string ToString()
Node<T>:    new Node<T>(T value) · new Node<T>(T value, Node<T> next)
            T GetValue() · Node<T> GetNext() · bool HasNext() · void SetValue(T value) · void SetNext(Node<T> next)
BinNode<T>: new BinNode<T>(T value) · new BinNode<T>(BinNode<T> left, T value, BinNode<T> right)
            T GetValue() · BinNode<T> GetLeft() · BinNode<T> GetRight() · bool HasLeft() · bool HasRight()
            void SetValue(T value) · void SetLeft(BinNode<T> left) · void SetRight(BinNode<T> right)
There is NO Count, Size(), Peek(), Clone() or copy method in Stack/Queue (these are NOT System.Collections.Generic). Nothing else exists in this library.
Never write "using System.Collections.Generic;" (its Stack/Queue would clash with the library). If a List is really needed, write System.Collections.Generic.List<int> in full.
Arrays (int[], string[], int[,] or int[][]) and string/Math/Console from System are available.`,
    types: `מספר שלם → int, מספר ממשי → double, תו → char, מחרוזת → string, בוליאני/אמת-שקר → bool,
    מחסנית של מספרים שלמים → Stack<int>, תור של מחרוזות → Queue<string>, שרשרת חוליות / רשימה מקושרת של שלמים → Node<int>,
    עץ בינארי של שלמים → BinNode<int>, מערך של שלמים → int[], מערך דו-ממדי → int[,]. Generics use the C# types directly (Stack<int>, Queue<char>).`,
    names: 'PascalCase method names (CountEven, IsSorted) and PascalCase class names, as is usual in C#; camelCase parameters',
    mainHeader: 'public static void Main(string[] args)',
    print: 'Console.WriteLine("סעיף א: " + CountEven(s1));',
    declare: 'Stack<int> s1 = new Stack<int>(); build chains with new Node<int>(...), trees with new BinNode<int>(left, value, right)',
    imports: 'imports — extra "using" namespaces needed by mainCode/classes/givenCode (e.g. "System.Text"), never System.Collections.Generic. Usually empty.',
    rules: 'C# 10 / .NET 8, no "var", no records, no lambdas or LINQ in mainCode, no top-level statements, no namespace. A method of the main class is "public static". Fields of exercise classes are private, with public Get/Set methods or properties as the exercise describes.',
    noLib: 'NEVER define Stack, Queue, Node or BinNode (they come from the library). No using lines and no namespace.',
    formats: 'bool prints True / False; double prints like 2.5 or 3 (no ".0"); char prints the character; arrays must be printed with string.Join(", ", arr) — looks like 1, 2, 3.',
  },
};

export function language(l) {
  return l === 'csharp' || l === 'cs' || l === 'c#' ? 'csharp' : 'java';
}

export function systemPrompt(lang) {
  const L = LANG[language(lang)];
  return `You help Israeli high-school computer-science students (Bagrut "מבני נתונים" curriculum). The student works in ${L.name}.
The student sends a programming exercise (PDF pages, photos/screenshots, or text from a Word file; usually in Hebrew).
Your job is to produce a READY-TO-RUN ${L.name} SKELETON for the exercise, so the student can immediately start writing the solution and run it.
Even if the exercise itself is written for another language (for example Java code in the question while the student works in C#), describe everything in ${L.name}.
NEVER write the solution of any task — not in code and not in comments. You only describe the structure; the program assembles the code.

Return ONLY JSON that matches the response schema. Field by field:

tasks — one entry for every method (פעולה / פונקציה), constructor or class operation that the exercise asks the student to WRITE.
  • label: the sub-question label as it appears ("א", "ב", "1", "3ב"...). If one sub-question asks for two methods, use the same label for both.
  • If the exercise gives the header (כותרת / חתימה / signature), copy the method name, parameter types, parameter names and return type
    (converted to ${L.name} if the exercise uses another language). Otherwise choose a short meaningful English name for what it does — ${L.names} —
    and meaningful English parameter names.
  • Hebrew types → ${L.name}: ${L.types}
  • kind: "method", or "constructor" when the student must write a constructor (methodName = the class name, returnType = "").
  • className: the class the method belongs to. Static helper methods/functions go in the main class (use the mainClass value).
    If the exercise says "add to class X a method..." / "write in class X...", use className X and isStatic=false.
  • returnType: ${L.name} type, or "void".
  • instructions: the full text of that sub-question in Hebrew, faithful and complete enough to solve it without the original page,
    including examples given in the question (e.g. "לדוגמה: עבור המחסנית 3, 5, 2 יוחזר 2"). Plain text, no markdown. Up to ~700 characters.

otherQuestions — sub-questions that do NOT require writing code (tracing code, what will be printed, complexity, explanations) — label + Hebrew text.

classes — classes that the exercise defines or describes (UML, list of fields, "נתונה המחלקה ...") and that the code needs. NOT library classes.
  • name + code: full compilable ${L.name} code of the class: "public class Name { ... }" with the fields exactly as described (names and types),
    a constructor, getters/setters and a string conversion (toString / ToString) — plus any methods the exercise gives with their implementation.
  • Do NOT include the methods/constructors that the student must write (those are tasks — the program inserts them).
  • ${L.noLib}
  • A class described in the exercise must be defined here even if its name is Student or Car.

givenCode — HELPER methods that the exercise PROVIDES to the student, which the solution may call without writing them. Look for them carefully, in every form:
  • sentences like "נתונה הפעולה ...", "ניתן להשתמש בפעולה ...", "תוכל להשתמש בפעולה שלפניך ללא צורך לממש", "הניחו שקיימת פעולה ...", "הפעולה הבאה כתובה (מומשה) עבורך";
  • a TABLE with a column of description ("תיאור הפעולה") and a column of header ("כותרת הפעולה"), often with one header per language
    (e.g. "בשפת Java — public static Stack<Integer> clone(Stack<Integer> s)", "בשפת C# — ..."): take the header of the student's language (${L.name}) and implement what the description says;
  • "for solving this sub-question ONLY you may use ..." — still givenCode (and never a task), even if it is mentioned inside one sub-question.
  Write a correct, complete implementation of every provided method (full method code with its header, exactly the header given; "public static" when it is in the main class)
  so that the program runs and does exactly what the description says (for instance a "clone"/"copy" of a stack returns an identical new stack and leaves the original unchanged).
  The implementation uses only the library API. className = where it belongs (the main class for static helpers). Never put a method that the student must write here, and never add a method nobody mentioned.
  The tasks and mainCode may call these helpers freely, and mainCode should call a helper only if that makes the test clearer.

mainCode — ONLY the statements of the body of "${L.mainHeader}" (no header, no outer braces). main is the student's TEST of the solution:
running it must show whether each method the student writes works correctly. It must:
  • build example data that fits the exercise — use the example values from the exercise when given, otherwise 5–8 sensible values
    chosen so that a wrong solution gives a different result (not all zeros, not trivial, include the interesting cases of the question);
  • print the data before the calls;
  • call EVERY task with this data (for instance methods create the objects first; for a constructor task create an object with it) and print each result
    with a Hebrew label, e.g. ${L.print} — for void methods print the structure after the call;
  • if several calls change the same structure, build a separate structure (separate variable) for each call;
  • the task bodies are stubs that return 0 / false / null until the student writes them — main must not crash because of that
    (print a returned object directly instead of calling methods on it; don't loop on a returned structure);
  • use only the library API below and the standard basics; no keyboard input — fixed values only;
  • declare with full generic types: ${L.declare};
  • short and clear, with a few short Hebrew comments (// ...).

expectedOutput — EXACTLY what the program prints when the student has solved every task CORRECTLY: every line that mainCode prints, in order,
  each line exactly as it will appear (same Hebrew labels, same spacing). This is shown to the student as "if you solved it correctly, this should be printed".
  Work it out carefully, step by step, by simulating mainCode with a correct solution of every task. Keep the data small so the result is certain.
  How the library prints (IMPORTANT, character by character): a Stack prints [top,...,bottom] with commas and NO spaces: after push 1, push 2, push 3 it prints [3,2,1] (never "[3, 2, 1]");
  a Queue prints [head,...,tail] with commas and NO spaces, e.g. [1,2,3];
  an empty Stack or Queue → []; Node chain → 1 -> 2 -> 3 -> null; BinNode → only its own value (e.g. 7); ${L.formats}
  Lines separated by "\\n". No explanations, no comments — only the printed text.

${L.imports}
title — short Hebrew title (up to 6 words). summary — 1–2 Hebrew sentences: what the exercise is about.
topic — the TYPE of the exercise in 1–3 short lowercase English words joined with "_", for a folder name the student will recognize:
  e.g. "stacks", "queues", "linked_lists", "binary_trees", "arrays", "recursion", "classes_team", "queue_of_stacks".
mainClass — English PascalCase name of the main class: the class name the exercise uses for the methods if there is one, otherwise e.g. "StackTargil".
isExercise / problem — if the files are not a programming exercise or cannot be read: isExercise=false and problem = a short Hebrew explanation
  (what is missing / what to send). Otherwise isExercise=true and problem="".

${L.name}: ${L.rules} All names in English. Hebrew only in strings and comments.
${L.api}`;
}

export function repairPrompt(lang) {
  const L = LANG[language(lang)];
  return `The ${L.name} skeleton that was assembled from your previous JSON did not compile.
Below: your previous JSON, the generated ${L.name} files, and the compiler errors.
Return the corrected JSON (same schema) that fixes ALL the errors — usually in mainCode, classes, givenCode or imports.
Keep the same tasks (same names and signatures) unless a signature itself is not valid ${L.name}.
If you change mainCode, update expectedOutput to match it.
Still never write the solution of any task.`;
}

const S = (description, extra) => Object.assign({ type: 'STRING', description }, extra || {});

/** התבנית של התשובה (responseSchema של Gemini) — זהה ב-Java וב-C# */
export const RESPONSE_SCHEMA = {
  type: 'OBJECT',
  properties: {
    isExercise: { type: 'BOOLEAN' },
    problem: S('Hebrew explanation when isExercise is false, otherwise empty'),
    title: S('short Hebrew title'),
    summary: S('1-2 Hebrew sentences'),
    topic: S('type of the exercise, lowercase English words joined with _'),
    mainClass: S('English PascalCase main class name'),
    imports: { type: 'ARRAY', items: S('extra import / using') },
    classes: {
      type: 'ARRAY',
      items: {
        type: 'OBJECT',
        properties: { name: S('class name'), code: S('full code of the class') },
        required: ['name', 'code'],
        propertyOrdering: ['name', 'code'],
      },
    },
    tasks: {
      type: 'ARRAY',
      items: {
        type: 'OBJECT',
        properties: {
          label: S('sub-question label'),
          kind: S('method or constructor', { enum: ['method', 'constructor'] }),
          className: S('class the method belongs to'),
          isStatic: { type: 'BOOLEAN' },
          returnType: S('return type, "void", or "" for a constructor'),
          methodName: S('method name'),
          parameters: {
            type: 'ARRAY',
            items: {
              type: 'OBJECT',
              properties: { type: S('type'), name: S('parameter name') },
              required: ['type', 'name'],
              propertyOrdering: ['type', 'name'],
            },
          },
          instructions: S('full Hebrew text of the sub-question'),
        },
        required: ['label', 'kind', 'className', 'isStatic', 'returnType', 'methodName', 'parameters', 'instructions'],
        propertyOrdering: ['label', 'kind', 'className', 'isStatic', 'returnType', 'methodName', 'parameters', 'instructions'],
      },
    },
    givenCode: {
      type: 'ARRAY',
      items: {
        type: 'OBJECT',
        properties: { className: S('class the method belongs to'), code: S('full method code') },
        required: ['className', 'code'],
        propertyOrdering: ['className', 'code'],
      },
    },
    otherQuestions: {
      type: 'ARRAY',
      items: {
        type: 'OBJECT',
        properties: { label: S('sub-question label'), text: S('Hebrew text') },
        required: ['label', 'text'],
        propertyOrdering: ['label', 'text'],
      },
    },
    mainCode: S('statements of the main method body'),
    expectedOutput: S('exactly what main prints when all tasks are solved correctly, lines separated by \\n'),
  },
  required: ['isExercise', 'problem', 'title', 'summary', 'topic', 'mainClass', 'imports', 'classes', 'tasks', 'givenCode', 'otherQuestions', 'mainCode', 'expectedOutput'],
  propertyOrdering: ['isExercise', 'problem', 'title', 'summary', 'topic', 'mainClass', 'imports', 'classes', 'tasks', 'givenCode', 'otherQuestions', 'mainCode', 'expectedOutput'],
};
