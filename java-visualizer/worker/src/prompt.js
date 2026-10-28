// ההנחיות למודל, תיאור הספרייה והתבנית (schema) של התשובה.
// הכול נמצא כאן, בשרת — התוסף שולח רק את דף התרגיל, ולכן אי אפשר להשתמש בשרת לבקשות אחרות.

export const LIBRARY_API = `
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
Arrays (int[], String[], int[][]) and String/Math/Integer/Character from java.lang are of course available.
`;

export const SYSTEM_PROMPT = `You help Israeli high-school computer-science students (Java, Bagrut "מבני נתונים" curriculum).
The student sends a programming exercise (PDF pages, photos/screenshots, or text from a Word file; usually in Hebrew).
Your job is to produce a READY-TO-RUN Java SKELETON for the exercise, so the student can immediately start writing the solution and run it.
NEVER write the solution of any task — not in code and not in comments. You only describe the structure; the program assembles the code.

Return ONLY JSON that matches the response schema. Field by field:

tasks — one entry for every method (פעולה / פונקציה), constructor or class operation that the exercise asks the student to WRITE.
  • label: the sub-question label as it appears ("א", "ב", "1", "3ב"...). If one sub-question asks for two methods, use the same label for both.
  • If the exercise gives the header (כותרת / חתימה / signature), copy the method name, parameter types, parameter names and return type EXACTLY.
    Otherwise choose a short meaningful English camelCase name for what it does (countEven, isSorted, removeMax...) and meaningful English parameter names.
  • Hebrew types → Java: מספר שלם → int, מספר ממשי → double, תו → char, מחרוזת → String, בוליאני/אמת-שקר → boolean,
    מחסנית של מספרים שלמים → Stack<Integer>, תור של מחרוזות → Queue<String>, שרשרת חוליות / רשימה מקושרת של שלמים → Node<Integer>,
    עץ בינארי של שלמים → BinNode<Integer>, מערך של שלמים → int[], מערך דו-ממדי → int[][]. Generics always use wrapper types.
  • kind: "method", or "constructor" when the student must write a constructor (methodName = the class name, returnType = "").
  • className: the class the method belongs to. Static helper methods/functions go in the main class (use the mainClass value).
    If the exercise says "add to class X a method..." / "write in class X...", use className X and isStatic=false.
  • returnType: Java type, or "void".
  • instructions: the full text of that sub-question in Hebrew, faithful and complete enough to solve it without the original page,
    including examples given in the question (e.g. "לדוגמה: עבור המחסנית 3, 5, 2 יוחזר 2"). Plain text, no markdown. Up to ~700 characters.

otherQuestions — sub-questions that do NOT require writing code (tracing code, what will be printed, complexity, explanations) — label + Hebrew text.

classes — classes that the exercise defines or describes (UML, list of fields, "נתונה המחלקה ...") and that the code needs. NOT library classes.
  • name + code: full compilable Java code of the class: "public class Name { ... }" with the fields exactly as described (names and types),
    a constructor, getters/setters and toString — plus any methods the exercise gives with their implementation.
  • Do NOT include the methods/constructors that the student must write (those are tasks — the program inserts them).
  • NEVER define Stack, Queue, Node, BinNode or BinTreeNode (they come from the library). No package or import lines.
  • A class described in the exercise must be defined here even if its name is Student or Car.

givenCode — methods that the exercise gives as already existing ("נתונה הפעולה ...", "ניתן להשתמש בפעולה ... שכותרתה ...", "הניחו שקיימת פעולה ...")
  and that the solution may call. Write a correct, complete implementation of each one (full method code with its header; "public static" when it is in the main class)
  so that the program runs. className = where it belongs. Never put a task method here.

mainCode — ONLY the statements of the body of "public static void main(String[] args)" (no header, no outer braces). It must:
  • build example data that fits the exercise — use the example values from the exercise when given, otherwise 5–8 sensible values;
  • print the data before the calls (System.out.println("מחסנית: " + s1););
  • call EVERY task (for instance methods create the objects first; for a constructor task create an object with it) and print each result
    with a Hebrew label, e.g. System.out.println("סעיף א: " + countEven(s1)); — for void methods print the structure after the call;
  • if several calls change the same structure, build a separate structure (separate variable) for each call;
  • the task bodies are stubs that return 0 / false / null until the student writes them — main must not crash because of that
    (print a returned object directly instead of calling methods on it; don't loop on a returned structure);
  • use only the library API below, java.lang and the imports you list; no Scanner / keyboard input — fixed values only;
  • declare with full generic types: Stack<Integer> s1 = new Stack<Integer>(); build chains with new Node<Integer>(...), trees with new BinNode<Integer>(left, value, right);
  • short and clear, with a few short Hebrew comments (// ...).

imports — extra imports needed by mainCode/classes/givenCode, only java.util classes (e.g. "java.util.ArrayList"). Usually empty.
title — short Hebrew title (up to 6 words). summary — 1–2 Hebrew sentences: what the exercise is about.
mainClass — English PascalCase name of the main class: the class name the exercise uses for the methods if there is one, otherwise e.g. "StackTargil".
packageName — short lowercase English name for this exercise (letters, digits, underscore), e.g. "queue_targil".
isExercise / problem — if the files are not a programming exercise or cannot be read: isExercise=false and problem = a short Hebrew explanation
  (what is missing / what to send). Otherwise isExercise=true and problem="".

Java: Java 11, no "var", no records, no lambdas in mainCode. All names in English. Hebrew only in strings and comments.

${LIBRARY_API}`;

export const REPAIR_PROMPT = `The Java skeleton that was assembled from your previous JSON did not compile.
Below: your previous JSON, the generated Java files, and the compiler errors.
Return the corrected JSON (same schema) that fixes ALL the errors — usually in mainCode, classes, givenCode or imports.
Keep the same tasks (same names and signatures) unless a signature itself is not valid Java.
Still never write the solution of any task.`;

const S = (description, extra) => Object.assign({ type: 'STRING', description }, extra || {});

/** התבנית של התשובה (responseSchema של Gemini) */
export const RESPONSE_SCHEMA = {
  type: 'OBJECT',
  properties: {
    isExercise: { type: 'BOOLEAN' },
    problem: S('Hebrew explanation when isExercise is false, otherwise empty'),
    title: S('short Hebrew title'),
    summary: S('1-2 Hebrew sentences'),
    packageName: S('lowercase English package name'),
    mainClass: S('English PascalCase main class name'),
    imports: { type: 'ARRAY', items: S('java.util class to import') },
    classes: {
      type: 'ARRAY',
      items: {
        type: 'OBJECT',
        properties: { name: S('class name'), code: S('full Java code of the class') },
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
          returnType: S('Java return type, "void", or "" for a constructor'),
          methodName: S('method name'),
          parameters: {
            type: 'ARRAY',
            items: {
              type: 'OBJECT',
              properties: { type: S('Java type'), name: S('parameter name') },
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
        properties: { className: S('class the method belongs to'), code: S('full Java method code') },
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
  },
  required: ['isExercise', 'problem', 'title', 'summary', 'packageName', 'mainClass', 'imports', 'classes', 'tasks', 'givenCode', 'otherQuestions', 'mainCode'],
  propertyOrdering: ['isExercise', 'problem', 'title', 'summary', 'packageName', 'mainClass', 'imports', 'classes', 'tasks', 'givenCode', 'otherQuestions', 'mainCode'],
};
