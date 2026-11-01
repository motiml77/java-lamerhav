// DSViz — השרת של "שליחת תרגיל" (Cloudflare Worker) בקובץ אחד: להדבקה ב-Cloudflare → Workers → dsviz-exercise → Edit code.
// נבנה מ-src/ (npm run bundle; קובץ שנוצר) — לא עורכים כאן; עורכים את src/index.js ו-src/prompt.js ובונים מחדש.
// src/index.js
import { Buffer } from "node:buffer";

// src/prompt.js
var LANG = {
  java: {
    name: "Java",
    api: `
unit4.collectionsLib \u2014 imported automatically (import unit4.collectionsLib.*;). Exactly these classes and methods exist:

Stack<T>:   new Stack<T>()  \xB7 void push(T x) \xB7 T pop() \xB7 T top() \xB7 boolean isEmpty() \xB7 String toString()
Queue<T>:   new Queue<T>()  \xB7 void insert(T x) \xB7 T remove() \xB7 T head() \xB7 boolean isEmpty() \xB7 String toString()
Node<T>:    new Node<T>(T value) \xB7 new Node<T>(T value, Node<T> next)
            T getValue() \xB7 Node<T> getNext() \xB7 boolean hasNext() \xB7 void setValue(T value) \xB7 void setNext(Node<T> next)
            (getInfo()/setInfo() are aliases of getValue()/setValue())
BinNode<T>: new BinNode<T>(T value) \xB7 new BinNode<T>(BinNode<T> left, T value, BinNode<T> right)
            T getValue() \xB7 BinNode<T> getLeft() \xB7 BinNode<T> getRight() \xB7 boolean hasLeft() \xB7 boolean hasRight()
            void setValue(T value) \xB7 void setLeft(BinNode<T> left) \xB7 void setRight(BinNode<T> right)
BinTreeNode<T>: older name of a binary tree node (same as BinNode, with getInfo()/setInfo()) \u2014 use it only if the exercise uses it.
There is NO size(), length(), peek(), clone() or copy method in Stack/Queue. Nothing else exists in this library.
Arrays (int[], String[], int[][]) and String/Math/Integer/Character from java.lang are of course available.`,
    types: `\u05DE\u05E1\u05E4\u05E8 \u05E9\u05DC\u05DD \u2192 int, \u05DE\u05E1\u05E4\u05E8 \u05DE\u05DE\u05E9\u05D9 \u2192 double, \u05EA\u05D5 \u2192 char, \u05DE\u05D7\u05E8\u05D5\u05D6\u05EA \u2192 String, \u05D1\u05D5\u05DC\u05D9\u05D0\u05E0\u05D9/\u05D0\u05DE\u05EA-\u05E9\u05E7\u05E8 \u2192 boolean,
    \u05DE\u05D7\u05E1\u05E0\u05D9\u05EA \u05E9\u05DC \u05DE\u05E1\u05E4\u05E8\u05D9\u05DD \u05E9\u05DC\u05DE\u05D9\u05DD \u2192 Stack<Integer>, \u05EA\u05D5\u05E8 \u05E9\u05DC \u05DE\u05D7\u05E8\u05D5\u05D6\u05D5\u05EA \u2192 Queue<String>, \u05E9\u05E8\u05E9\u05E8\u05EA \u05D7\u05D5\u05DC\u05D9\u05D5\u05EA / \u05E8\u05E9\u05D9\u05DE\u05D4 \u05DE\u05E7\u05D5\u05E9\u05E8\u05EA \u05E9\u05DC \u05E9\u05DC\u05DE\u05D9\u05DD \u2192 Node<Integer>,
    \u05E2\u05E5 \u05D1\u05D9\u05E0\u05D0\u05E8\u05D9 \u05E9\u05DC \u05E9\u05DC\u05DE\u05D9\u05DD \u2192 BinNode<Integer>, \u05DE\u05E2\u05E8\u05DA \u05E9\u05DC \u05E9\u05DC\u05DE\u05D9\u05DD \u2192 int[], \u05DE\u05E2\u05E8\u05DA \u05D3\u05D5-\u05DE\u05DE\u05D3\u05D9 \u2192 int[][]. Generics always use wrapper types (Integer, Character, Double, Boolean).`,
    names: "camelCase method names (countEven, isSorted), PascalCase class names",
    mainHeader: "public static void main(String[] args)",
    print: 'System.out.println("\u05E1\u05E2\u05D9\u05E3 \u05D0: " + countEven(s1));',
    declare: "Stack<Integer> s1 = new Stack<Integer>(); build chains with new Node<Integer>(...), trees with new BinNode<Integer>(left, value, right)",
    imports: 'imports \u2014 extra imports needed by mainCode/classes/givenCode, only java.util classes (e.g. "java.util.ArrayList"). Usually empty.',
    rules: 'Java 11, no "var", no records, no lambdas in mainCode. A method of the main class is "public static".',
    formats: "boolean prints true / false; double prints like 2.5 or 3.0; char prints the character; an int[] array printed with Arrays.toString looks like [1, 2, 3].",
    noLib: "NEVER define Stack, Queue, Node, BinNode or BinTreeNode (they come from the library). No package or import lines.",
    book: "public class Book { private String name; private String author; private int pages; ... }",
    noAdvanced: 'No streams, lambdas, "var", the ?: operator, java.util collections (ArrayList, HashMap...), StringBuilder, Arrays/Collections utilities or other advanced tricks.',
    missingClass: '"cannot find symbol: class Book"'
  },
  csharp: {
    name: "C#",
    api: `
Unit4.CollectionsLib \u2014 already imported (using System; using Unit4.CollectionsLib;). Exactly these classes and methods exist:

Stack<T>:   new Stack<T>()  \xB7 void Push(T x) \xB7 T Pop() \xB7 T Top() \xB7 bool IsEmpty() \xB7 string ToString()
Queue<T>:   new Queue<T>()  \xB7 void Insert(T x) \xB7 T Remove() \xB7 T Head() \xB7 bool IsEmpty() \xB7 string ToString()
Node<T>:    new Node<T>(T value) \xB7 new Node<T>(T value, Node<T> next)
            T GetValue() \xB7 Node<T> GetNext() \xB7 bool HasNext() \xB7 void SetValue(T value) \xB7 void SetNext(Node<T> next)
BinNode<T>: new BinNode<T>(T value) \xB7 new BinNode<T>(BinNode<T> left, T value, BinNode<T> right)
            T GetValue() \xB7 BinNode<T> GetLeft() \xB7 BinNode<T> GetRight() \xB7 bool HasLeft() \xB7 bool HasRight()
            void SetValue(T value) \xB7 void SetLeft(BinNode<T> left) \xB7 void SetRight(BinNode<T> right)
There is NO Count, Size(), Peek(), Clone() or copy method in Stack/Queue (these are NOT System.Collections.Generic). Nothing else exists in this library.
Never write "using System.Collections.Generic;" (its Stack/Queue would clash with the library). If a List is really needed, write System.Collections.Generic.List<int> in full.
Arrays (int[], string[], int[,] or int[][]) and string/Math/Console from System are available.`,
    types: `\u05DE\u05E1\u05E4\u05E8 \u05E9\u05DC\u05DD \u2192 int, \u05DE\u05E1\u05E4\u05E8 \u05DE\u05DE\u05E9\u05D9 \u2192 double, \u05EA\u05D5 \u2192 char, \u05DE\u05D7\u05E8\u05D5\u05D6\u05EA \u2192 string, \u05D1\u05D5\u05DC\u05D9\u05D0\u05E0\u05D9/\u05D0\u05DE\u05EA-\u05E9\u05E7\u05E8 \u2192 bool,
    \u05DE\u05D7\u05E1\u05E0\u05D9\u05EA \u05E9\u05DC \u05DE\u05E1\u05E4\u05E8\u05D9\u05DD \u05E9\u05DC\u05DE\u05D9\u05DD \u2192 Stack<int>, \u05EA\u05D5\u05E8 \u05E9\u05DC \u05DE\u05D7\u05E8\u05D5\u05D6\u05D5\u05EA \u2192 Queue<string>, \u05E9\u05E8\u05E9\u05E8\u05EA \u05D7\u05D5\u05DC\u05D9\u05D5\u05EA / \u05E8\u05E9\u05D9\u05DE\u05D4 \u05DE\u05E7\u05D5\u05E9\u05E8\u05EA \u05E9\u05DC \u05E9\u05DC\u05DE\u05D9\u05DD \u2192 Node<int>,
    \u05E2\u05E5 \u05D1\u05D9\u05E0\u05D0\u05E8\u05D9 \u05E9\u05DC \u05E9\u05DC\u05DE\u05D9\u05DD \u2192 BinNode<int>, \u05DE\u05E2\u05E8\u05DA \u05E9\u05DC \u05E9\u05DC\u05DE\u05D9\u05DD \u2192 int[], \u05DE\u05E2\u05E8\u05DA \u05D3\u05D5-\u05DE\u05DE\u05D3\u05D9 \u2192 int[,]. Generics use the C# types directly (Stack<int>, Queue<char>).`,
    names: "PascalCase method names (CountEven, IsSorted) and PascalCase class names, as is usual in C#; camelCase parameters",
    mainHeader: "public static void Main(string[] args)",
    print: 'Console.WriteLine("\u05E1\u05E2\u05D9\u05E3 \u05D0: " + CountEven(s1));',
    declare: "Stack<int> s1 = new Stack<int>(); build chains with new Node<int>(...), trees with new BinNode<int>(left, value, right)",
    imports: 'imports \u2014 extra "using" namespaces needed by mainCode/classes/givenCode (e.g. "System.Text"), never System.Collections.Generic. Usually empty.',
    rules: 'C# 10 / .NET 8, no "var", no records, no lambdas or LINQ in mainCode, no top-level statements, no namespace. A method of the main class is "public static". Fields of exercise classes are private, with public Get/Set methods or properties as the exercise describes.',
    noLib: "NEVER define Stack, Queue, Node or BinNode (they come from the library). No using lines and no namespace.",
    book: "public class Book { private string name; private string author; private int pages; ... }",
    noAdvanced: 'No LINQ, lambdas, "var", the ?: operator, System.Collections.Generic collections, StringBuilder or other advanced tricks.',
    formats: 'bool prints True / False; double prints like 2.5 or 3 (no ".0"); char prints the character; arrays must be printed with string.Join(", ", arr) \u2014 looks like 1, 2, 3.',
    missingClass: `"error CS0246: The type or namespace name 'Book' could not be found"`
  }
};
function language(l) {
  return l === "csharp" || l === "cs" || l === "c#" ? "csharp" : "java";
}
function systemPrompt(lang) {
  const L = LANG[language(lang)];
  return `You help Israeli high-school computer-science students (Bagrut "\u05DE\u05D1\u05E0\u05D9 \u05E0\u05EA\u05D5\u05E0\u05D9\u05DD" curriculum). The student works in ${L.name}.
The student sends a programming exercise (PDF pages, photos/screenshots, or text from a Word file; usually in Hebrew).
Your job is to produce a READY-TO-RUN ${L.name} SKELETON for the exercise, so the student can immediately start writing the solution and run it.
Even if the exercise itself is written for another language (for example Java code in the question while the student works in C#), describe everything in ${L.name}.
The skeleton itself never contains a solution: classes, givenCode, mainCode and instructions never solve a task \u2014 the program assembles the skeleton
with an empty body in every task (the fields of the classes are not a task: they are always written \u2014 see "classes").
Separately, for every task you also write ONE suggested solution in tasks[].solution; it is kept hidden and shown
to the student only if they press "\u05D4\u05E6\u05E2\u05D4 \u05DC\u05E4\u05EA\u05E8\u05D5\u05DF" after trying by themselves.

STEP 1 \u2014 PUT THE PAGES IN ORDER, THEN READ. Every attached part (file, photo, screenshot or text) is introduced by a line like "[\u05E7\u05D5\u05D1\u05E5 2 \u05DE\u05EA\u05D5\u05DA 4: name]".
The parts are often NOT attached in the order of the exercise: students photograph and paste screenshots one by one, in any order, and files may be
sorted by name \u2014 the end of a question may come first and its beginning last. So BEFORE extracting anything:
  1. Look at every part and work out the correct reading order from what is written INSIDE the parts, using every clue: page numbers ("\u05E2\u05DE\u05D5\u05D3 2", "2/3";
     Bagrut pages have "- 3 -" at the top and "/\u05D4\u05DE\u05E9\u05DA \u05D1\u05E2\u05DE\u05D5\u05D3 4/" at the bottom); the numbering of the questions and sub-questions
     (1, 2, 3 / \u05D0, \u05D1, \u05D2 / 2\u05D0, 2\u05D1) \u2014 the introduction, the data and the given classes usually come before the sub-questions;
     a sentence, a list, a table or a code listing that is cut at the bottom of one part and continues at the top of another (also code line numbers
     that continue); "\u05D4\u05DE\u05E9\u05DA", "(\u05D4\u05DE\u05E9\u05DA)", "\u05D4\u05DE\u05E9\u05DA \u05D1\u05E2\u05DE\u05D5\u05D3 \u05D4\u05D1\u05D0"; headers and footers; and the student's note.
     The part numbers and the file names only show the order in which the student attached the parts (pasted screenshots are named "\u05E6\u05D9\u05DC\u05D5\u05DD \u05DE\u05E1\u05DA 1",
     "\u05EA\u05DE\u05D5\u05E0\u05D4 \u05DE\u05D5\u05D3\u05D1\u05E7\u05EA 2"... in the order they were pasted) \u2014 they are NOT evidence of the exercise order.
     Inside one PDF the pages are usually in order \u2014 several files, photos or screenshots usually are not.
     Parts named "X (1)", "X (2)"... are pictures from inside the same file X, in their order there. When X is a Word file, the part "\u05D8\u05E7\u05E1\u05D8 \u05DE\u05EA\u05D5\u05DA X"
     is its text, and those pictures (code, a table, a UML diagram) belong INSIDE that text, where it refers to them \u2014 in readingOrder put the text
     first and then its pictures.
  2. Write in pageNotes what every part starts and ends with, decide the order from that, and write it in readingOrder (both are described below).
     Then read the WHOLE exercise in that order, as one continuous text, and only then fill the other fields.
  3. Never drop a part while ordering and reading: every attached part belongs to the exercise unless it is clearly unrelated (then say so in pageNotes).
     If the student's note says which questions to build, still order and read everything, but build only what the note asks.
     Screenshots that overlap or show the same text twice are merged \u2014 a sub-question, a class or an example whose TEXT appears twice is used once
     (the same label alone is not a duplicate: 2\u05D0 is not 1\u05D0). Every part is still listed in readingOrder.
  The tasks and otherQuestions follow the order of the exercise as you reconstructed it, not the order in which the files were attached.

Return ONLY JSON that matches the response schema. Field by field:

pageNotes \u2014 written BEFORE readingOrder. With more than one part: one short Hebrew line per part, in the order they were attached:
  "<n>: \u05DE\u05EA\u05D7\u05D9\u05DC \u05D1... / \u05E0\u05D2\u05DE\u05E8 \u05D1..." (the page number, the question / sub-question labels, a sentence, a table or code that is cut at the edge),
  e.g. "1: \u05DE\u05EA\u05D7\u05D9\u05DC \u05D1\u05D0\u05DE\u05E6\u05E2 \u05E1\u05E2\u05D9\u05E3 \u05D1 / \u05E0\u05D2\u05DE\u05E8 \u05D1\u05E1\u05D5\u05E3 \u05D4\u05E9\u05D0\u05DC\u05D4" and "2: \u05DE\u05EA\u05D7\u05D9\u05DC \u05D1'\u05E9\u05D0\u05DC\u05D4 3' \u05D5\u05D1\u05DE\u05D7\u05DC\u05E7\u05D4 Team / \u05E0\u05D2\u05DE\u05E8 \u05D1\u05D0\u05DE\u05E6\u05E2 \u05E1\u05E2\u05D9\u05E3 \u05D1". With a single part: "".
readingOrder \u2014 the order decided from pageNotes: the numbers of the parts in the correct reading order ("[\u05E7\u05D5\u05D1\u05E5 3 \u05DE\u05EA\u05D5\u05DA 4: ...]" is 3),
  every part exactly once, e.g. [2, 3, 1, 4]. With a single part: [1].

tasks \u2014 one entry for every method (\u05E4\u05E2\u05D5\u05DC\u05D4 / \u05E4\u05D5\u05E0\u05E7\u05E6\u05D9\u05D4), constructor or class operation that the exercise asks the student to WRITE.
  \u2022 label: the sub-question label as it appears ("\u05D0", "\u05D1", "1", "3\u05D1"...). If one sub-question asks for two methods, use the same label for both.
  \u2022 If the exercise gives the header (\u05DB\u05D5\u05EA\u05E8\u05EA / \u05D7\u05EA\u05D9\u05DE\u05D4 / signature), copy the method name, parameter types, parameter names and return type
    (converted to ${L.name} if the exercise uses another language). Otherwise choose a short meaningful English name for what it does \u2014 ${L.names} \u2014
    and meaningful English parameter names.
  \u2022 Hebrew types \u2192 ${L.name}: ${L.types}
  \u2022 kind: "method", or "constructor" when the student must write a constructor (methodName = the class name, returnType = "").
  \u2022 className: the class the method belongs to. Static helper methods/functions go in the main class (use the mainClass value).
    If the exercise says "add to class X a method..." / "write in class X...", use className X and isStatic=false.
    A className other than mainClass must also be defined in classes, with its fields (see "classes" below).
    A library class (Stack, Queue, Node, BinNode...) is never a className: a method that the exercise puts there is a static method of mainClass
    that receives the structure as a parameter.
  \u2022 returnType: ${L.name} type, or "void".
  \u2022 instructions: the full text of that sub-question in Hebrew, faithful and complete enough to solve it without the original page,
    including examples given in the question (e.g. "\u05DC\u05D3\u05D5\u05D2\u05DE\u05D4: \u05E2\u05D1\u05D5\u05E8 \u05D4\u05DE\u05D7\u05E1\u05E0\u05D9\u05EA 3, 5, 2 \u05D9\u05D5\u05D7\u05D6\u05E8 2"). Plain text, no markdown. Up to ~700 characters.
  \u2022 solution: the suggested solution \u2014 see "tasks[].solution" below.

tasks[].solution \u2014 the COMPLETE ${L.name} code of that method (or constructor): exactly the same header as the task (same name, parameter types and names,
  return type, "public static" or not) and the full body. Write it the way a good Israeli high-school teacher would show it to a 10th\u201312th grade student:
  \u2022 simple and intuitive \u2014 the most natural way a student would think about the problem: one clear idea, step by step, with clear variable names
    (count, sum, temp, current, max...). Prefer the straightforward solution over a short or clever one.
  \u2022 only basic tools: if / else, while and for loops, local variables, the library operations listed below, and helper structures of the same kind
    (for example a temporary Stack or Queue, also to restore the original after going over it). Recursion only when the sub-question asks for it,
    or when it is the natural way (binary trees). ${L.noAdvanced}
  \u2022 CORRECT for every input \u2014 also an empty structure, a single element, duplicates, negative numbers; never crashes on valid input.
  \u2022 when the method goes over a Stack or Queue it received, the structure must be exactly as it was at the end (same values, same order),
    unless the sub-question asks to change it (remove / insert / sort ...).
  \u2022 it may call the givenCode helpers and the methods of other tasks, as the exercise allows.
  \u2022 a few short Hebrew comments (// ...) that explain the idea of each step. Nothing outside the method \u2014 no extra methods, no explanations.

otherQuestions \u2014 sub-questions that do NOT require writing code (tracing code, what will be printed, complexity, explanations) \u2014 label + Hebrew text.

classes \u2014 EVERY class that the code needs besides the main class and the library classes, each one WITH the content that the exercise states:
  (a) classes that the exercise gives or describes (UML, a list of fields, "\u05E0\u05EA\u05D5\u05E0\u05D4 \u05D4\u05DE\u05D7\u05DC\u05E7\u05D4 ...", "\u05DC\u05DB\u05DC ... \u05D9\u05E9 ...");
  (b) classes that the student is asked to write, define or complete ("\u05DB\u05EA\u05D1\u05D5 \u05D0\u05EA \u05D4\u05DE\u05D7\u05DC\u05E7\u05D4 Team ...", "\u05D4\u05D2\u05D3\u05D9\u05E8\u05D5 \u05DE\u05D7\u05DC\u05E7\u05D4 ...", "\u05D4\u05E9\u05DC\u05D9\u05DE\u05D5 \u05D0\u05EA \u05D4\u05DE\u05D7\u05DC\u05E7\u05D4 ...",
      "\u05D4\u05D5\u05E1\u05D9\u05E4\u05D5 \u05DC\u05DE\u05D7\u05DC\u05E7\u05D4 ...") \u2014 the class itself is defined here, with all the fields / properties that the exercise states.
      Even when a sub-question asks only to write the header and the fields of the class, write the fields here (the rest of the code needs them)
      and put that sub-question in otherQuestions. Its constructor and methods are still tasks;
  (c) classes implied by the data the exercise talks about (several values for every item \u2014 a record, a pair, a card...) when tasks or mainCode need them.
  \u2022 name + code: full compilable ${L.name} code of the class: "public class Name { ... }" with the fields exactly as described (names and types as written;
    when they are only described in words, choose sensible English names and ${L.name} types: "\u05DC\u05DB\u05DC \u05E1\u05E4\u05E8 \u05D9\u05E9 \u05E9\u05DD, \u05DE\u05D7\u05D1\u05E8 \u05D5\u05DE\u05E1\u05E4\u05E8 \u05E2\u05DE\u05D5\u05D3\u05D9\u05DD" \u2192
    ${L.book}), a constructor, getters/setters and a string conversion (toString / ToString) \u2014
    plus any methods the exercise gives with their implementation. Setters only for fields that the exercise or the code really changes \u2014 do not add
    extra methods nobody needs. The string conversion is short and simple, with the values only (e.g. "\u05DE\u05E1\u05E2 \u05D1\u05D6\u05DE\u05DF, \u05D9\u05D5\u05E1\u05D9, 320" \u2014 no English words such as
    "by" or "pages"), so that what main prints reads naturally in Hebrew.
  \u2022 Do NOT include the methods/constructors that the student must write (those are tasks \u2014 the program inserts them). If the student must write the
    constructor, a getter or toString of a class, leave out only that one \u2014 the class still gets all its fields and everything else it needs.
  \u2022 HARD RULE: every className in tasks and in givenCode is either mainClass or the name of a class in classes (never a library class \u2014 see className
    above), and every other class that mainCode or a solution creates or uses (except the library classes and the standard ${L.name} classes) is defined
    in classes. Never leave such a class out, and never leave out a field or property that the exercise states \u2014 a class without its fields does not
    compile with mainCode and gives the student nothing to work with. Do not invent fields that the exercise does not mention (a class that really has
    no fields, e.g. only methods, is fine).
  \u2022 ${L.noLib}
  \u2022 A class described in the exercise must be defined here even if its name is Student or Car.

givenCode \u2014 HELPER methods that the exercise PROVIDES to the student, which the solution may call without writing them. Look for them carefully, in every form:
  \u2022 sentences like "\u05E0\u05EA\u05D5\u05E0\u05D4 \u05D4\u05E4\u05E2\u05D5\u05DC\u05D4 ...", "\u05E0\u05D9\u05EA\u05DF \u05DC\u05D4\u05E9\u05EA\u05DE\u05E9 \u05D1\u05E4\u05E2\u05D5\u05DC\u05D4 ...", "\u05EA\u05D5\u05DB\u05DC \u05DC\u05D4\u05E9\u05EA\u05DE\u05E9 \u05D1\u05E4\u05E2\u05D5\u05DC\u05D4 \u05E9\u05DC\u05E4\u05E0\u05D9\u05DA \u05DC\u05DC\u05D0 \u05E6\u05D5\u05E8\u05DA \u05DC\u05DE\u05DE\u05E9", "\u05D4\u05E0\u05D9\u05D7\u05D5 \u05E9\u05E7\u05D9\u05D9\u05DE\u05EA \u05E4\u05E2\u05D5\u05DC\u05D4 ...", "\u05D4\u05E4\u05E2\u05D5\u05DC\u05D4 \u05D4\u05D1\u05D0\u05D4 \u05DB\u05EA\u05D5\u05D1\u05D4 (\u05DE\u05D5\u05DE\u05E9\u05D4) \u05E2\u05D1\u05D5\u05E8\u05DA";
  \u2022 a TABLE with a column of description ("\u05EA\u05D9\u05D0\u05D5\u05E8 \u05D4\u05E4\u05E2\u05D5\u05DC\u05D4") and a column of header ("\u05DB\u05D5\u05EA\u05E8\u05EA \u05D4\u05E4\u05E2\u05D5\u05DC\u05D4"), often with one header per language
    (e.g. "\u05D1\u05E9\u05E4\u05EA Java \u2014 public static Stack<Integer> clone(Stack<Integer> s)", "\u05D1\u05E9\u05E4\u05EA C# \u2014 ..."): take the header of the student's language (${L.name}) and implement what the description says;
  \u2022 "for solving this sub-question ONLY you may use ..." \u2014 still givenCode (and never a task), even if it is mentioned inside one sub-question.
  Write a correct, complete implementation of every provided method (full method code with its header, exactly the header given; "public static" when it is in the main class)
  so that the program runs and does exactly what the description says (for instance a "clone"/"copy" of a stack returns an identical new stack and leaves the original unchanged).
  The implementation uses only the library API. className = where it belongs (the main class for static helpers). Never put a method that the student must write here, and never add a method nobody mentioned.
  The tasks and mainCode may call these helpers freely, and mainCode should call a helper only if that makes the test clearer.

mainCode \u2014 ONLY the statements of the body of "${L.mainHeader}" (no header, no outer braces). main is the student's TEST of the solution:
running it must show whether each method the student writes works correctly. It must:
  \u2022 build example data that fits the exercise \u2014 use the example values from the exercise when given, otherwise 5\u20138 sensible values
    chosen so that a wrong solution gives a different result (not all zeros, not trivial, include the interesting cases of the question);
  \u2022 print the data before the calls;
  \u2022 call EVERY task with this data (for instance methods create the objects first; for a constructor task create an object with it) and print each result
    with a Hebrew label, e.g. ${L.print} \u2014 for void methods print the structure after the call;
  \u2022 if several calls change the same structure, build a separate structure (separate variable) for each call;
  \u2022 the task bodies are stubs that return 0 / false / null until the student writes them \u2014 main must not crash because of that
    (print a returned object directly instead of calling methods on it; don't loop on a returned structure);
  \u2022 use only the library API below and the standard basics; no keyboard input \u2014 fixed values only;
  \u2022 declare with full generic types: ${L.declare};
  \u2022 short and clear, with a few short Hebrew comments (// ...).

expectedOutput \u2014 EXACTLY what the program prints when the student has solved every task CORRECTLY: every line that mainCode prints, in order,
  each line exactly as it will appear (same Hebrew labels, same spacing). This is shown to the student as "if you solved it correctly, this should be printed".
  Work it out carefully, step by step, by simulating mainCode with exactly the solutions you wrote in tasks[].solution
  (and check that they are correct according to the instructions). Keep the data small so the result is certain.
  How the library prints (IMPORTANT, character by character): a Stack prints [top,...,bottom] with commas and NO spaces: after push 1, push 2, push 3 it prints [3,2,1] (never "[3, 2, 1]");
  a Queue prints [head,...,tail] with commas and NO spaces, e.g. [1,2,3];
  an empty Stack or Queue \u2192 []; Node chain \u2192 1 -> 2 -> 3 -> null; BinNode \u2192 only its own value (e.g. 7); ${L.formats}
  Lines separated by "\\n". No explanations, no comments \u2014 only the printed text.

${L.imports}
title \u2014 short Hebrew title (up to 6 words). summary \u2014 1\u20132 Hebrew sentences: what the exercise is about.
topic \u2014 the TYPE of the exercise in 1\u20133 short lowercase English words joined with "_", for a folder name the student will recognize:
  e.g. "stacks", "queues", "linked_lists", "binary_trees", "arrays", "recursion", "classes_team", "queue_of_stacks".
mainClass \u2014 English PascalCase name of the main class: the class name the exercise uses for the methods if there is one, otherwise e.g. "StackTargil".
isExercise / problem \u2014 if the files are not a programming exercise or cannot be read: isExercise=false and problem = a short Hebrew explanation
  (what is missing / what to send). Otherwise isExercise=true and problem="".

SELF-CHECK before you answer (fix anything that fails):
  1. readingOrder contains every attached part exactly once; no question or sub-question of the exercise is missing, and none is taken twice
     from overlapping screenshots.
  2. The HARD RULE of classes holds, and no class in classes is missing a field or property that the exercise states.
  3. mainCode calls every task, and expectedOutput is exactly what it prints with the solutions.

${L.name}: ${L.rules} All names in English. Hebrew only in strings and comments.
${L.api}`;
}
function repairPrompt(lang) {
  const L = LANG[language(lang)];
  return `The ${L.name} skeleton that was assembled from your previous JSON did not compile.
Below: your previous JSON, the generated ${L.name} files, and the compiler errors.
Return the corrected JSON (same schema) that fixes ALL the errors \u2014 usually in mainCode, classes, givenCode or imports.
Keep the same tasks (same names and signatures) unless a signature itself is not valid ${L.name}.
Keep every class in classes with all its fields. If an error shows that a class, a field or a constructor is missing (e.g. ${L.missingClass},
a task whose class is empty), ADD it to classes with the fields that the exercise describes (the task instructions say what they are) \u2014
never delete a task, or a class that the exercise describes or that a task belongs to, to make the code compile (a class that duplicates the main class
or a library / standard class may be removed). Every className in tasks and givenCode must be mainClass or a class in classes.
If you change mainCode, update expectedOutput to match it.
Keep every tasks[].solution (fix it only if it no longer matches the corrected header). The skeleton still never contains a solution.
Keep readingOrder and pageNotes as they are.`;
}
function solutionPrompt(lang) {
  const L = LANG[language(lang)];
  return `You previously produced the JSON below. The program checked your suggested solutions: it ran mainCode with tasks[].solution
instead of the empty bodies, and the result was NOT what expectedOutput says (or it did not compile, or it crashed).
Below: your previous JSON, the ${L.name} file(s) WITH your solutions, and what happened (compiler errors, or the actual output next to expectedOutput).
Find what is wrong \u2014 a mistake in a solution, or a mistake in expectedOutput. The instructions of each task decide what is correct.
Return the corrected JSON (same schema):
  \u2022 fix every wrong solution so it is correct for every input \u2014 still simple, intuitive and at high-school level, as described before;
  \u2022 fix expectedOutput if it is the wrong one, so it is exactly what mainCode prints with CORRECT solutions;
  \u2022 keep tasks (names, headers), classes, givenCode and mainCode exactly as they are, unless one of them is clearly wrong
    (never delete a task, or a class that the exercise describes or that a task belongs to; a solution may use only the fields and methods
    that the classes really have);
  \u2022 keep readingOrder and pageNotes as they are.`;
}
var S = (description, extra) => Object.assign({ type: "STRING", description }, extra || {});
var RESPONSE_SCHEMA = {
  type: "OBJECT",
  properties: {
    isExercise: { type: "BOOLEAN" },
    problem: S("Hebrew explanation when isExercise is false, otherwise empty"),
    pageNotes: S("one line per part: what it starts and ends with (written before readingOrder); empty with a single part"),
    readingOrder: { type: "ARRAY", description: "numbers of the attached parts ([\u05E7\u05D5\u05D1\u05E5 N \u05DE\u05EA\u05D5\u05DA M]) in the correct reading order, decided from pageNotes", items: { type: "INTEGER" } },
    title: S("short Hebrew title"),
    summary: S("1-2 Hebrew sentences"),
    topic: S("type of the exercise, lowercase English words joined with _"),
    mainClass: S("English PascalCase main class name"),
    imports: { type: "ARRAY", items: S("extra import / using") },
    classes: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: { name: S("class name"), code: S("full code of the class, with all the fields the exercise states") },
        required: ["name", "code"],
        propertyOrdering: ["name", "code"]
      }
    },
    tasks: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          label: S("sub-question label"),
          kind: S("method or constructor", { enum: ["method", "constructor"] }),
          className: S("class the method belongs to"),
          isStatic: { type: "BOOLEAN" },
          returnType: S('return type, "void", or "" for a constructor'),
          methodName: S("method name"),
          parameters: {
            type: "ARRAY",
            items: {
              type: "OBJECT",
              properties: { type: S("type"), name: S("parameter name") },
              required: ["type", "name"],
              propertyOrdering: ["type", "name"]
            }
          },
          instructions: S("full Hebrew text of the sub-question"),
          solution: S("suggested solution: the complete code of the method, header and body")
        },
        required: ["label", "kind", "className", "isStatic", "returnType", "methodName", "parameters", "instructions", "solution"],
        propertyOrdering: ["label", "kind", "className", "isStatic", "returnType", "methodName", "parameters", "instructions", "solution"]
      }
    },
    givenCode: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: { className: S("class the method belongs to"), code: S("full method code") },
        required: ["className", "code"],
        propertyOrdering: ["className", "code"]
      }
    },
    otherQuestions: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: { label: S("sub-question label"), text: S("Hebrew text") },
        required: ["label", "text"],
        propertyOrdering: ["label", "text"]
      }
    },
    mainCode: S("statements of the main method body"),
    expectedOutput: S("exactly what main prints when all tasks are solved correctly, lines separated by \\n")
  },
  required: ["isExercise", "problem", "pageNotes", "readingOrder", "title", "summary", "topic", "mainClass", "imports", "classes", "tasks", "givenCode", "otherQuestions", "mainCode", "expectedOutput"],
  propertyOrdering: ["isExercise", "problem", "pageNotes", "readingOrder", "title", "summary", "topic", "mainClass", "imports", "classes", "tasks", "givenCode", "otherQuestions", "mainCode", "expectedOutput"]
};

// src/index.js
var LIMITS = {
  files: 10,
  fileBytes: 10 * 1024 * 1024,
  totalBytes: 14 * 1024 * 1024,
  // ב-base64 זה כ-19MB — מתחת למגבלת 20MB של בקשה ל-Gemini
  textChars: 6e4,
  noteChars: 600,
  repairChars: 12e4
};
var TYPES = {
  "application/pdf": (b) => b[0] === 37 && b[1] === 80 && b[2] === 68 && b[3] === 70,
  // %PDF
  "image/png": (b) => b[0] === 137 && b[1] === 80 && b[2] === 78 && b[3] === 71,
  "image/jpeg": (b) => b[0] === 255 && b[1] === 216 && b[2] === 255,
  "image/webp": (b) => b[0] === 82 && b[1] === 73 && b[2] === 70 && b[3] === 70 && b[8] === 87 && b[9] === 69
  // RIFF....WE
};
var DEFAULT_MODELS = "gemini-flash-latest,gemini-flash-lite-latest";
var GEMINI_BASE = "https://generativelanguage.googleapis.com";
var RELEASE = "https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/release/";
var DOWNLOADS = { windows: RELEASE + "DSViz-Setup.exe", zip: RELEASE + "dsviz-install.zip" };
var DEFAULT_SITE_ORIGINS = "https://motiml77.github.io";
var src_default = {
  async fetch(request, env, ctx) {
    env = env || {};
    let res;
    try {
      res = await handle(request, env, ctx);
    } catch (e) {
      if (e instanceof Fail) res = reply(e.status, { ok: false, code: e.code, error: e.message });
      else {
        console.error("unexpected", e && e.stack || e);
        res = reply(500, { ok: false, code: "internal", error: "\u05EA\u05E7\u05DC\u05D4 \u05D1\u05E9\u05E8\u05EA. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1 \u05D1\u05E2\u05D5\u05D3 \u05D3\u05E7\u05D4." });
      }
    }
    return withCors(request, env, res);
  }
};
function siteOrigin(request, env) {
  const origin = request.headers.get("origin") || "";
  const allowed = String(env.SITE_ORIGINS || DEFAULT_SITE_ORIGINS).split(",").map((x) => x.trim().replace(/\/+$/, "")).filter(Boolean);
  return allowed.includes(origin) ? origin : "";
}
function withCors(request, env, res) {
  const path = new URL(request.url).pathname;
  let origin = "";
  if (path === "/v1/stats") origin = "*";
  else if (path.startsWith("/v1/site/")) origin = siteOrigin(request, env);
  if (!origin) return res;
  const h = new Headers(res.headers);
  h.set("access-control-allow-origin", origin);
  h.set("vary", "origin");
  return new Response(res.body, { status: res.status, headers: h });
}
var Fail = class extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
};
function reply(status, obj, extra) {
  return new Response(JSON.stringify(obj), { status, headers: Object.assign({ "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }, extra || {}) });
}
var VERSION = "2026-11-01";
async function handle(request, env, ctx) {
  const url = new URL(request.url);
  if (request.method === "GET" && (url.pathname === "/v1/health" || url.pathname === "/")) {
    return reply(200, { ok: true, service: "dsviz-exercise", version: VERSION, models: models(env) });
  }
  const dl = /^\/dl\/(windows|zip)$/.exec(url.pathname);
  if (request.method === "GET" && dl) {
    const counting = count(env, dl[1]).catch((e) => console.error("count", e));
    if (ctx && typeof ctx.waitUntil === "function") ctx.waitUntil(counting);
    else await counting;
    return new Response(null, { status: 302, headers: { location: DOWNLOADS[dl[1]], "cache-control": "no-store" } });
  }
  const hit = /^\/v1\/count\/(windows|zip|mac)$/.exec(url.pathname);
  if (request.method === "POST" && hit) {
    const counting = count(env, hit[1]).catch((e) => console.error("count", e));
    if (ctx && typeof ctx.waitUntil === "function") ctx.waitUntil(counting);
    else await counting;
    return new Response(null, { status: 204, headers: { "access-control-allow-origin": "*", "cache-control": "no-store" } });
  }
  if (request.method === "GET" && url.pathname === "/v1/stats") return reply(200, await stats(env), { "cache-control": "public, max-age=60" });
  if (request.method === "OPTIONS" && url.pathname.startsWith("/v1/site/")) {
    if (!siteOrigin(request, env)) throw new Fail(403, "origin", "\u05D4\u05D0\u05EA\u05E8 \u05D4\u05D6\u05D4 \u05DC\u05D0 \u05DE\u05D5\u05E8\u05E9\u05D4");
    return new Response(null, { status: 204, headers: { "access-control-allow-methods": "POST", "access-control-allow-headers": "content-type", "access-control-max-age": "86400" } });
  }
  if (request.method !== "POST") throw new Fail(405, "method", "\u05E8\u05E7 POST");
  if (url.pathname === "/v1/license") return reply(200, await license(request, env));
  const site = url.pathname === "/v1/site/exercise";
  if (url.pathname !== "/v1/exercise" && url.pathname !== "/v1/repair" && !site) throw new Fail(404, "not_found", "\u05DB\u05EA\u05D5\u05D1\u05EA \u05DC\u05D0 \u05DE\u05D5\u05DB\u05E8\u05EA");
  if (site) {
    if (!siteOrigin(request, env)) throw new Fail(403, "origin", "\u05D0\u05E4\u05E9\u05E8 \u05DC\u05E9\u05DC\u05D5\u05D7 \u05DE\u05DB\u05D0\u05DF \u05E8\u05E7 \u05DE\u05D4\u05D0\u05EA\u05E8 \u05E9\u05DC DSViz.");
  } else if (env.APP_KEY && request.headers.get("x-dsviz-key") !== env.APP_KEY) throw new Fail(401, "key", "\u05D4\u05E9\u05E8\u05EA \u05DC\u05D0 \u05DE\u05DB\u05D9\u05E8 \u05D0\u05EA \u05D4\u05EA\u05D5\u05E1\u05E3 \u05D4\u05D6\u05D4 (\u05DE\u05E4\u05EA\u05D7 \u05E9\u05D2\u05D5\u05D9). \u05E2\u05D3\u05DB\u05E0\u05D5 \u05D0\u05EA \u05D4\u05EA\u05D5\u05E1\u05E3.");
  if (!env.GEMINI_API_KEY) throw new Fail(503, "config", "\u05D4\u05E9\u05E8\u05EA \u05E2\u05D3\u05D9\u05D9\u05DF \u05DC\u05D0 \u05D4\u05D5\u05D2\u05D3\u05E8 (\u05D7\u05E1\u05E8 \u05DE\u05E4\u05EA\u05D7 Gemini).");
  const ip = request.headers.get("cf-connecting-ip") || "local";
  await rateLimit(env, ip, site);
  const declared = +(request.headers.get("content-length") || 0);
  if (declared > LIMITS.totalBytes * 1.5) throw new Fail(413, "too_big", "\u05D4\u05E7\u05D1\u05E6\u05D9\u05DD \u05D2\u05D3\u05D5\u05DC\u05D9\u05DD \u05DE\u05D3\u05D9 (\u05E2\u05D3 14MB \u05D1\u05D9\u05D7\u05D3).");
  let req;
  if (url.pathname === "/v1/exercise" || site) req = await exerciseParts(request);
  else req = await repairParts(request);
  const result = await askGemini(env, req.userParts, req.lang);
  const exercise = check(result.data, req.previous);
  exercise.language = req.lang;
  return reply(200, { ok: true, model: result.model, exercise });
}
var DEFAULT_LOCK_MESSAGE = "\u05D4\u05EA\u05E6\u05D5\u05D2\u05D4 \u05D4\u05D5\u05D5\u05D9\u05D6\u05D5\u05D0\u05DC\u05D9\u05EA \u05D3\u05D5\u05E8\u05E9\u05EA \u05E8\u05D9\u05E9\u05D9\u05D5\u05DF. \u05D0\u05E4\u05E9\u05E8 \u05DC\u05D4\u05DE\u05E9\u05D9\u05DA \u05DC\u05E2\u05D1\u05D5\u05D3 \u05D5\u05DC\u05D4\u05E8\u05D9\u05E5 \u05DB\u05E8\u05D2\u05D9\u05DC \u05D1-Eclipse \u2014 \u05E8\u05E7 \u05D4\u05D7\u05DC\u05D5\u05DF \u05D4\u05D5\u05D5\u05D9\u05D6\u05D5\u05D0\u05DC\u05D9 \u05E0\u05E2\u05D5\u05DC.";
async function licenseSignature(secret, id) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(id)));
  return Array.from(sig.slice(0, 8), (b) => b.toString(16).padStart(2, "0")).join("");
}
async function verifyLicense(env, code) {
  const m = /^DSV-([A-Za-z0-9._@+-]{1,80})-([0-9a-f]{16})$/.exec(String(code || "").trim());
  if (!m || !env.LICENSE_SECRET) return null;
  const revoked = String(env.REVOKED || "").split(",").map((x) => x.trim()).filter(Boolean);
  if (revoked.includes(m[1])) return null;
  return await licenseSignature(env.LICENSE_SECRET, m[1]) === m[2] ? m[1] : null;
}
async function license(request, env) {
  let body = {};
  try {
    body = await request.json() || {};
  } catch (e) {
    body = {};
  }
  if (String(env.PAYWALL || "").toLowerCase() !== "on") return { ok: true, locked: false, licensed: false };
  const id = await verifyLicense(env, body.code);
  if (id) return { ok: true, locked: false, licensed: true };
  return { ok: true, locked: true, licensed: false, message: env.PAYWALL_MESSAGE || DEFAULT_LOCK_MESSAGE, payUrl: env.PAY_URL || "" };
}
async function exerciseParts(request) {
  const type = request.headers.get("content-type") || "";
  if (!type.startsWith("multipart/form-data")) throw new Fail(415, "format", "\u05E4\u05D5\u05E8\u05DE\u05D8 \u05D1\u05E7\u05E9\u05D4 \u05DC\u05D0 \u05E0\u05DB\u05D5\u05DF");
  let form;
  try {
    form = await request.formData();
  } catch (e) {
    throw new Fail(400, "format", "\u05DC\u05D0 \u05D4\u05E6\u05DC\u05D7\u05EA\u05D9 \u05DC\u05E7\u05E8\u05D5\u05D0 \u05D0\u05EA \u05D4\u05D1\u05E7\u05E9\u05D4");
  }
  let meta = {};
  let rawMeta = form.get("meta");
  const tooMuchText = () => new Fail(413, "too_big", "\u05D4\u05D8\u05E7\u05E1\u05D8 \u05D0\u05E8\u05D5\u05DA \u05DE\u05D3\u05D9 \u2014 \u05E9\u05DC\u05D7\u05D5 \u05E8\u05E7 \u05D0\u05EA \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC \u05E2\u05E6\u05DE\u05D5.");
  if (rawMeta && typeof rawMeta === "object" && typeof rawMeta.text === "function") {
    if (rawMeta.size >= LIMITS.textChars * 12) throw tooMuchText();
    rawMeta = await rawMeta.text();
  }
  if (typeof rawMeta === "string") {
    if (rawMeta.length >= LIMITS.textChars * 4) throw tooMuchText();
    try {
      meta = JSON.parse(rawMeta) || {};
    } catch (e) {
      throw new Fail(400, "format", "\u05DC\u05D0 \u05D4\u05E6\u05DC\u05D7\u05EA\u05D9 \u05DC\u05E7\u05E8\u05D5\u05D0 \u05D0\u05EA \u05D4\u05D1\u05E7\u05E9\u05D4");
    }
  }
  const texts = Array.isArray(meta.texts) ? meta.texts.filter((t) => t && typeof t.text === "string" && t.text.trim()) : [];
  const textChars = texts.reduce((n, t) => n + t.text.length, 0);
  if (textChars > LIMITS.textChars) throw tooMuchText();
  const note = typeof meta.note === "string" ? meta.note.trim().slice(0, LIMITS.noteChars) : "";
  const names = Array.isArray(meta.names) ? meta.names : [];
  const lang = language(meta.language);
  const files = form.getAll("file").filter((f) => f && typeof f === "object" && typeof f.arrayBuffer === "function");
  if (files.length + texts.length === 0) throw new Fail(400, "empty", "\u05DC\u05D0 \u05E6\u05D5\u05E8\u05E3 \u05E9\u05D5\u05DD \u05E7\u05D5\u05D1\u05E5.");
  if (files.length > LIMITS.files) throw new Fail(413, "too_many", `\u05D0\u05E4\u05E9\u05E8 \u05DC\u05E9\u05DC\u05D5\u05D7 \u05E2\u05D3 ${LIMITS.files} \u05E7\u05D1\u05E6\u05D9\u05DD.`);
  let total = 0;
  for (const f of files) {
    if (f.size > LIMITS.fileBytes) throw new Fail(413, "too_big", "\u05D0\u05D7\u05D3 \u05D4\u05E7\u05D1\u05E6\u05D9\u05DD \u05D2\u05D3\u05D5\u05DC \u05DE\u05D3\u05D9 (\u05E2\u05D3 10MB).");
    total += f.size;
  }
  if (total > LIMITS.totalBytes) throw new Fail(413, "too_big", "\u05D4\u05E7\u05D1\u05E6\u05D9\u05DD \u05D2\u05D3\u05D5\u05DC\u05D9\u05DD \u05DE\u05D3\u05D9 (\u05E2\u05D3 14MB \u05D1\u05D9\u05D7\u05D3).");
  const howMany = files.length + texts.length;
  const parts = [JSON.stringify({ text: orderNotice(howMany) })];
  for (const [i, f] of files.entries()) {
    const label = typeof names[i] === "string" && names[i] ? names[i] : f.name || "\u05E7\u05D5\u05D1\u05E5";
    const buf = new Uint8Array(await f.arrayBuffer());
    const mime = String(f.type || "").toLowerCase().split(";")[0].trim();
    const ok = TYPES[mime];
    if (!ok || buf.length < 12 || !ok(buf)) throw new Fail(415, "file_type", `\u05E1\u05D5\u05D2 \u05D4\u05E7\u05D5\u05D1\u05E5 ${clean(label)} \u05DC\u05D0 \u05E0\u05EA\u05DE\u05DA \u2014 \u05E9\u05DC\u05D7\u05D5 PDF, \u05EA\u05DE\u05D5\u05E0\u05D4 (PNG/JPG) \u05D0\u05D5 Word.`);
    parts.push(JSON.stringify({ text: `[\u05E7\u05D5\u05D1\u05E5 ${i + 1} \u05DE\u05EA\u05D5\u05DA ${howMany}: ${clean(label)}]` }));
    parts.push('{"inlineData":{"mimeType":"' + mime + '","data":"' + toBase64(buf) + '"}}');
  }
  for (const [i, t] of texts.entries()) {
    parts.push(JSON.stringify({ text: `[\u05E7\u05D5\u05D1\u05E5 ${files.length + i + 1} \u05DE\u05EA\u05D5\u05DA ${howMany}: \u05D8\u05E7\u05E1\u05D8 \u05DE\u05EA\u05D5\u05DA ${clean(t.name || "\u05E7\u05D5\u05D1\u05E5")}]
${t.text}` }));
  }
  if (howMany > 1) parts.push(JSON.stringify({ text: orderReminder(howMany) }));
  if (note) parts.push(JSON.stringify({ text: `\u05D4\u05E2\u05E8\u05D4 \u05DE\u05D4\u05EA\u05DC\u05DE\u05D9\u05D3 (\u05DC\u05D2\u05D1\u05D9 \u05DE\u05D4 \u05DC\u05D1\u05E0\u05D5\u05EA, \u05DC\u05D0 \u05D4\u05D5\u05E8\u05D0\u05D5\u05EA \u05DB\u05DC\u05DC\u05D9\u05D5\u05EA): ${note}` }));
  return { userParts: parts, lang, previous: null };
}
function orderNotice(total) {
  if (total === 1) {
    return '\u05D6\u05D4 \u05D3\u05E3 \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC \u05E9\u05E9\u05DC\u05D7 \u05D4\u05EA\u05DC\u05DE\u05D9\u05D3 \u2014 \u05D7\u05DC\u05E7 \u05D0\u05D7\u05D3, \u05DE\u05E1\u05D5\u05DE\u05DF "[\u05E7\u05D5\u05D1\u05E5 1 \u05DE\u05EA\u05D5\u05DA 1: \u05E9\u05DD]" (readingOrder = [1], pageNotes = ""). \u05D1\u05E0\u05D4 \u05D0\u05EA \u05D4\u05DE\u05E2\u05D8\u05E4\u05EA \u05DC\u05E4\u05D9 \u05D4\u05D4\u05E0\u05D7\u05D9\u05D5\u05EA \u05D5\u05D4\u05EA\u05D1\u05E0\u05D9\u05EA \u2014 \u05DB\u05D5\u05DC\u05DC \u05DB\u05DC \u05DE\u05D7\u05DC\u05E7\u05D4 \u05E9\u05D4\u05EA\u05E8\u05D2\u05D9\u05DC \u05DE\u05EA\u05D0\u05E8 \u05D0\u05D5 \u05DE\u05D1\u05E7\u05E9 \u05DC\u05DB\u05EA\u05D5\u05D1, \u05E2\u05DD \u05D4\u05EA\u05DB\u05D5\u05E0\u05D5\u05EA \u05E9\u05DC\u05D4.';
  }
  return `\u05D6\u05D4 \u05D3\u05E3 \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC \u05E9\u05E9\u05DC\u05D7 \u05D4\u05EA\u05DC\u05DE\u05D9\u05D3 \u2014 ${total} \u05D7\u05DC\u05E7\u05D9\u05DD (\u05E7\u05D1\u05E6\u05D9\u05DD, \u05EA\u05DE\u05D5\u05E0\u05D5\u05EA \u05D0\u05D5 \u05D8\u05E7\u05E1\u05D8), \u05D5\u05DB\u05DC \u05D7\u05DC\u05E7 \u05DE\u05E1\u05D5\u05DE\u05DF "[\u05E7\u05D5\u05D1\u05E5 N \u05DE\u05EA\u05D5\u05DA ${total}: \u05E9\u05DD]".
\u05E9\u05D9\u05DD \u05DC\u05D1: \u05D4\u05E7\u05D1\u05E6\u05D9\u05DD / \u05D4\u05EA\u05DE\u05D5\u05E0\u05D5\u05EA \u05DC\u05D0 \u05D1\u05D4\u05DB\u05E8\u05D7 \u05E6\u05D5\u05E8\u05E4\u05D5 \u05DC\u05E4\u05D9 \u05D4\u05E1\u05D3\u05E8 \u05E9\u05DC \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC (\u05D1\u05EA\u05D5\u05DA PDF \u05D0\u05D7\u05D3 \u05D4\u05E2\u05DE\u05D5\u05D3\u05D9\u05DD \u05D1\u05D3\u05E8\u05DA \u05DB\u05DC\u05DC \u05DC\u05E4\u05D9 \u05D4\u05E1\u05D3\u05E8). \u05DE\u05E1\u05E4\u05E8\u05D9 \u05D4\u05D7\u05DC\u05E7\u05D9\u05DD \u05D5\u05E9\u05DE\u05D5\u05EA \u05D4\u05E7\u05D1\u05E6\u05D9\u05DD \u05D4\u05DD \u05E8\u05E7 \u05D4\u05E1\u05D3\u05E8 \u05E9\u05D1\u05D5 \u05D4\u05EA\u05DC\u05DE\u05D9\u05D3 \u05E6\u05D9\u05E8\u05E3 \u05D0\u05D5\u05EA\u05DD. \u05E7\u05D5\u05D3\u05DD \u05DB\u05DC \u05E7\u05D1\u05E2 \u05D0\u05EA \u05E1\u05D3\u05E8 \u05D4\u05E7\u05E8\u05D9\u05D0\u05D4 \u05D4\u05E0\u05DB\u05D5\u05DF \u05DC\u05E4\u05D9 \u05DE\u05D4 \u05E9\u05DB\u05EA\u05D5\u05D1 \u05D1\u05EA\u05D5\u05DA \u05D4\u05D7\u05DC\u05E7\u05D9\u05DD \u2014 \u05DE\u05E1\u05E4\u05E8\u05D9 \u05D4\u05E2\u05DE\u05D5\u05D3\u05D9\u05DD, \u05DE\u05E1\u05E4\u05D5\u05E8 \u05D4\u05E9\u05D0\u05DC\u05D5\u05EA \u05D5\u05D4\u05E1\u05E2\u05D9\u05E4\u05D9\u05DD (1, 2, 3 / \u05D0, \u05D1, \u05D2), \u05DE\u05E9\u05E4\u05D8, \u05D8\u05D1\u05DC\u05D4 \u05D0\u05D5 \u05E7\u05D5\u05D3 \u05E9\u05DE\u05DE\u05E9\u05D9\u05DB\u05D9\u05DD \u05DE\u05D7\u05DC\u05E7 \u05DC\u05D7\u05DC\u05E7, \u05D5\u05E1\u05D9\u05DE\u05E0\u05D9 "\u05D4\u05DE\u05E9\u05DA" \u2014 \u05DB\u05EA\u05D5\u05D1 \u05D1-pageNotes \u05D1\u05DE\u05D4 \u05DE\u05EA\u05D7\u05D9\u05DC \u05D5\u05D1\u05DE\u05D4 \u05E0\u05D2\u05DE\u05E8 \u05DB\u05DC \u05D7\u05DC\u05E7, \u05D5\u05D0\u05EA \u05D4\u05E1\u05D3\u05E8 \u05D1-readingOrder. \u05E8\u05E7 \u05D0\u05D7\u05E8 \u05DB\u05DA \u05E7\u05E8\u05D0 \u05D0\u05EA \u05DB\u05DC \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC \u05DC\u05E4\u05D9 \u05D4\u05E1\u05D3\u05E8 \u05D4\u05D6\u05D4 (\u05D1\u05DC\u05D9 \u05DC\u05D3\u05DC\u05D2 \u05E2\u05DC \u05D0\u05E3 \u05D7\u05DC\u05E7 \u2014 \u05DB\u05DC \u05D7\u05DC\u05E7 \u05E0\u05DB\u05E0\u05E1 \u05DC-readingOrder; \u05E1\u05E2\u05D9\u05E3, \u05DE\u05D7\u05DC\u05E7\u05D4 \u05D0\u05D5 \u05D3\u05D5\u05D2\u05DE\u05D4 \u05E9\u05DE\u05D5\u05E4\u05D9\u05E2\u05D9\u05DD \u05D1\u05E9\u05E0\u05D9 \u05E6\u05D9\u05DC\u05D5\u05DE\u05D9\u05DD \u05D7\u05D5\u05E4\u05E4\u05D9\u05DD \u05E0\u05DC\u05E7\u05D7\u05D9\u05DD \u05E4\u05E2\u05DD \u05D0\u05D7\u05EA), \u05D5\u05D1\u05E0\u05D4 \u05D0\u05EA \u05D4\u05DE\u05E2\u05D8\u05E4\u05EA \u05DC\u05E4\u05D9 \u05D4\u05D4\u05E0\u05D7\u05D9\u05D5\u05EA \u05D5\u05D4\u05EA\u05D1\u05E0\u05D9\u05EA \u2014 \u05DB\u05D5\u05DC\u05DC \u05DB\u05DC \u05DE\u05D7\u05DC\u05E7\u05D4 \u05E9\u05D4\u05EA\u05E8\u05D2\u05D9\u05DC \u05DE\u05EA\u05D0\u05E8 \u05D0\u05D5 \u05DE\u05D1\u05E7\u05E9 \u05DC\u05DB\u05EA\u05D5\u05D1, \u05E2\u05DD \u05D4\u05EA\u05DB\u05D5\u05E0\u05D5\u05EA \u05E9\u05DC\u05D4.`;
}
function orderReminder(total) {
  return `\u05E1\u05D5\u05E3 \u05D4\u05D7\u05DC\u05E7\u05D9\u05DD (${total}). \u05D6\u05DB\u05D5\u05E8: \u05E7\u05D5\u05D3\u05DD \u05E7\u05D1\u05E2 \u05DE\u05D4\u05EA\u05D5\u05DB\u05DF \u05D0\u05EA \u05E1\u05D3\u05E8 \u05D4\u05E7\u05E8\u05D9\u05D0\u05D4 (pageNotes \u05D5\u05D0\u05D6 readingOrder), \u05E7\u05E8\u05D0 \u05D0\u05EA \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC \u05DC\u05E4\u05D9 \u05D4\u05E1\u05D3\u05E8 \u05D4\u05D6\u05D4, \u05D5\u05E8\u05E7 \u05D0\u05D6 \u05DE\u05DC\u05D0 \u05D0\u05EA \u05E9\u05D0\u05E8 \u05D4\u05E9\u05D3\u05D5\u05EA.`;
}
async function repairParts(request) {
  const text = await request.text();
  if (text.length > LIMITS.repairChars) throw new Fail(413, "too_big", "\u05D4\u05D1\u05E7\u05E9\u05D4 \u05D2\u05D3\u05D5\u05DC\u05D4 \u05DE\u05D3\u05D9.");
  let body;
  try {
    body = JSON.parse(text);
  } catch (e) {
    throw new Fail(400, "format", "\u05DC\u05D0 \u05D4\u05E6\u05DC\u05D7\u05EA\u05D9 \u05DC\u05E7\u05E8\u05D5\u05D0 \u05D0\u05EA \u05D4\u05D1\u05E7\u05E9\u05D4");
  }
  const previous = body && body.exercise;
  const errors = Array.isArray(body && body.errors) ? body.errors.map(String).slice(0, 40) : [];
  const files = body && body.files && typeof body.files === "object" ? body.files : {};
  if (!previous || typeof previous !== "object" || !errors.length) throw new Fail(400, "format", "\u05D7\u05E1\u05E8\u05D9\u05DD \u05E4\u05E8\u05D8\u05D9\u05DD \u05DC\u05EA\u05D9\u05E7\u05D5\u05DF");
  const lang = language(body.language || previous && previous.language);
  const solution = body.mode === "solution";
  let msg = (solution ? solutionPrompt(lang) : repairPrompt(lang)) + "\n\nPrevious JSON:\n" + JSON.stringify(previous) + "\n";
  for (const [name, code] of Object.entries(files).slice(0, 12)) msg += `
----- ${clean(name)} -----
${String(code)}
`;
  msg += (solution ? "\nWhat happened:\n" : "\nCompiler errors:\n") + errors.map((e) => "- " + e).join("\n");
  return { userParts: [JSON.stringify({ text: msg })], previous, lang };
}
function models(env) {
  return String(env.MODELS || DEFAULT_MODELS).split(",").map((s) => s.trim()).filter(Boolean);
}
async function askGemini(env, userParts, lang) {
  const cfg = {
    responseMimeType: "application/json",
    responseSchema: RESPONSE_SCHEMA,
    temperature: 0.2,
    // המחשבה של המודל (thinking) והתשובה חולקות את אותה מכסת טוקנים: בתרגיל ארוך עם הרבה תמונות 32K עלולים לא להספיק,
    // והתשובה נחתכת (MAX_TOKENS → JSON שבור → המודל הבא, החלש יותר). 65536 — המקסימום של מודלי Flash
    maxOutputTokens: 65536
  };
  const head = '{"systemInstruction":' + JSON.stringify({ parts: [{ text: systemPrompt(lang) }] }) + ',"contents":[{"role":"user","parts":[' + userParts.join(",") + "]}]";
  const bodyFor = (c) => head + ',"generationConfig":' + JSON.stringify(c) + "}";
  const base = env.GEMINI_BASE || GEMINI_BASE;
  let last = null, busy = false;
  for (const model of models(env)) {
    let res, raw;
    try {
      const call = (c) => fetch(`${base}/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
        method: "POST",
        headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
        body: bodyFor(c),
        signal: AbortSignal.timeout(+(env.TIMEOUT_MS || 1e5))
      });
      res = await call(cfg);
      raw = await res.text();
      if (res.status === 400 && /max[_ ]?output[_ ]?tokens/i.test(raw)) {
        console.error("gemini maxOutputTokens rejected", model, raw.slice(0, 200));
        res = await call(Object.assign({}, cfg, { maxOutputTokens: 32768 }));
        raw = await res.text();
      }
    } catch (e) {
      last = `${model}: ${e && e.name === "TimeoutError" ? "timeout" : String(e)}`;
      continue;
    }
    if (res.status === 429 || res.status >= 500 || res.status === 404) {
      if (res.status === 429 || res.status === 503) busy = true;
      last = `${model}: ${res.status} ${raw.slice(0, 300)}`;
      continue;
    }
    if (!res.ok) {
      console.error("gemini", model, res.status, raw.slice(0, 500));
      throw new Fail(502, "gemini", "Gemini \u05DC\u05D0 \u05D4\u05E6\u05DC\u05D9\u05D7 \u05DC\u05E7\u05E8\u05D5\u05D0 \u05D0\u05EA \u05D4\u05E7\u05D1\u05E6\u05D9\u05DD. \u05E0\u05E1\u05D5 \u05DC\u05E9\u05DC\u05D5\u05D7 \u05E6\u05D9\u05DC\u05D5\u05DD \u05DE\u05E1\u05DA \u05D1\u05E8\u05D5\u05E8 \u05D0\u05D5 PDF.");
    }
    let data = null, finish = "";
    try {
      const j = JSON.parse(raw);
      const cand = j.candidates && j.candidates[0];
      finish = cand && cand.finishReason || j.promptFeedback && j.promptFeedback.blockReason || "";
      const text = (cand && cand.content && cand.content.parts || []).filter((p) => !p.thought && typeof p.text === "string").map((p) => p.text).join("");
      data = JSON.parse(stripFence(text));
    } catch (e) {
      last = `${model}: bad json (${finish})`;
      if (finish === "MAX_TOKENS") console.error("gemini MAX_TOKENS", model, raw.length);
      continue;
    }
    return { data, model };
  }
  console.error("all models failed", last);
  if (busy) throw new Fail(429, "quota", "\u05D4\u05D2\u05E2\u05E0\u05D5 \u05DC\u05DE\u05DB\u05E1\u05D4 \u05E9\u05DC Gemini \u05DC\u05E2\u05DB\u05E9\u05D9\u05D5. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1 \u05D1\u05E2\u05D5\u05D3 \u05D3\u05E7\u05D4 (\u05D0\u05D5 \u05DE\u05D7\u05E8, \u05D0\u05DD \u05E0\u05D2\u05DE\u05E8\u05D4 \u05D4\u05DE\u05DB\u05E1\u05D4 \u05D4\u05D9\u05D5\u05DE\u05D9\u05EA).");
  throw new Fail(502, "gemini", "\u05DC\u05D0 \u05D4\u05EA\u05E7\u05D1\u05DC\u05D4 \u05EA\u05E9\u05D5\u05D1\u05D4 \u05EA\u05E7\u05D9\u05E0\u05D4 \u05DE-Gemini. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1.");
}
function stripFence(t) {
  const s = String(t || "").trim();
  const m = /^```[A-Za-z#]*\s*([\s\S]*?)\s*```$/.exec(s);
  return m ? m[1] : s;
}
function check(d, previous) {
  if (!d || typeof d !== "object") throw new Fail(502, "gemini", "\u05DC\u05D0 \u05D4\u05EA\u05E7\u05D1\u05DC\u05D4 \u05EA\u05E9\u05D5\u05D1\u05D4 \u05EA\u05E7\u05D9\u05E0\u05D4 \u05DE-Gemini. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1.");
  if (d.isExercise === false) {
    throw new Fail(422, "not_exercise", String(d.problem || "\u05DC\u05D0 \u05DE\u05E6\u05D0\u05EA\u05D9 \u05D1\u05E7\u05D1\u05E6\u05D9\u05DD \u05EA\u05E8\u05D2\u05D9\u05DC \u05EA\u05DB\u05E0\u05D5\u05EA. \u05E9\u05DC\u05D7\u05D5 \u05D0\u05EA \u05D3\u05E3 \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC (\u05E6\u05D9\u05DC\u05D5\u05DD \u05D1\u05E8\u05D5\u05E8 \u05D0\u05D5 PDF).").slice(0, 400));
  }
  const arr = (k) => Array.isArray(d[k]) ? d[k] : [];
  const out = {
    // סדר הקריאה שהמודל קבע לקבצים — נשאר בתשובה: התוספים והאתר מתעלמים ממנו (שדה לא מוכר), הוא עוזר לבדוק תקלות
    // ("למה סעיף חסר?"), ובבקשת תיקון הוא חוזר למודל עם שאר ה-JSON, והמודל מעתיק אותו כמו שהוא
    pageNotes: str(d.pageNotes, 1500),
    readingOrder: order(d.readingOrder),
    title: str(d.title, 120),
    summary: str(d.summary, 600),
    topic: str(d.topic || d.packageName, 60),
    mainClass: str(d.mainClass, 60),
    imports: arr("imports").map((x) => str(x, 80)).slice(0, 20),
    classes: arr("classes").filter((c) => c && c.name && c.code).map((c) => ({ name: str(c.name, 60), code: str(c.code, 2e4) })).slice(0, 12),
    tasks: arr("tasks").filter((t) => t && t.methodName).map((t) => ({
      label: str(t.label, 20),
      kind: t.kind === "constructor" ? "constructor" : "method",
      className: str(t.className, 60),
      isStatic: t.isStatic !== false,
      returnType: str(t.returnType, 80),
      methodName: str(t.methodName, 60),
      parameters: (Array.isArray(t.parameters) ? t.parameters : []).filter((p) => p && p.name).map((p) => ({ type: str(p.type, 80), name: str(p.name, 40) })).slice(0, 12),
      instructions: str(t.instructions, 2e3),
      solution: stripFence(str(t.solution, 12e3))
    })).slice(0, 25),
    givenCode: arr("givenCode").filter((g) => g && g.code).map((g) => ({ className: str(g.className, 60), code: str(g.code, 2e4) })).slice(0, 20),
    otherQuestions: arr("otherQuestions").filter((q) => q && q.text).map((q) => ({ label: str(q.label, 20), text: str(q.text, 2e3) })).slice(0, 20),
    mainCode: str(d.mainCode, 3e4),
    expectedOutput: str(d.expectedOutput, 4e3)
  };
  if (!out.tasks.length && !out.otherQuestions.length) {
    throw new Fail(422, "not_exercise", "\u05DC\u05D0 \u05DE\u05E6\u05D0\u05EA\u05D9 \u05D1\u05EA\u05E8\u05D2\u05D9\u05DC \u05E1\u05E2\u05D9\u05E4\u05D9\u05DD \u05E9\u05E6\u05E8\u05D9\u05DA \u05DC\u05DB\u05EA\u05D5\u05D1 \u05D1\u05D4\u05DD \u05E7\u05D5\u05D3.");
  }
  if (previous && !out.tasks.length) throw new Fail(502, "gemini", "\u05D4\u05EA\u05D9\u05E7\u05D5\u05DF \u05E9\u05D4\u05EA\u05E7\u05D1\u05DC \u05DC\u05D0 \u05EA\u05E7\u05D9\u05DF.");
  return out;
}
function str(v, max) {
  return typeof v === "string" ? v.slice(0, max) : "";
}
function order(v) {
  const out = [];
  for (const x of Array.isArray(v) ? v : []) {
    const k = typeof x === "number" ? x : typeof x === "string" && /^\s*\d{1,3}\s*$/.test(x) ? +x : NaN;
    if (Number.isInteger(k) && k >= 1 && k <= 100 && !out.includes(k)) out.push(k);
  }
  return out.slice(0, 40);
}
function clean(s) {
  return String(s).replace(/[\u0000-\u001f"\\]/g, " ").slice(0, 120);
}
function toBase64(u8) {
  if (typeof u8.toBase64 === "function") return u8.toBase64();
  return Buffer.from(u8.buffer, u8.byteOffset, u8.byteLength).toString("base64");
}
async function count(env, kind) {
  if (!env.STATS || typeof env.STATS.get !== "function") return;
  const n = +await env.STATS.get("dl:" + kind) || 0;
  await env.STATS.put("dl:" + kind, String(n + 1));
}
async function stats(env) {
  if (!env.STATS || typeof env.STATS.get !== "function") return { ok: true, counting: false, downloads: null };
  const windows = +await env.STATS.get("dl:windows") || 0, zip = +await env.STATS.get("dl:zip") || 0;
  const mac = +await env.STATS.get("dl:mac") || 0;
  const base = +(env.DOWNLOADS_BEFORE || 0);
  return { ok: true, counting: true, downloads: { windows, zip, mac, total: windows + zip + mac + base } };
}
async function rateLimit(env, ip, site) {
  if (env.LIMITER && typeof env.LIMITER.limit === "function") {
    const { success } = await env.LIMITER.limit({ key: ip });
    if (!success) throw new Fail(429, "rate", "\u05D9\u05D5\u05EA\u05E8 \u05DE\u05D3\u05D9 \u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05D1\u05D3\u05E7\u05D4 \u05D4\u05D0\u05D7\u05E8\u05D5\u05E0\u05D4. \u05D7\u05DB\u05D5 \u05D3\u05E7\u05D4 \u05D5\u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1.");
  }
  if (env.USAGE && typeof env.USAGE.get === "function") {
    const day = (/* @__PURE__ */ new Date()).toISOString().slice(0, 10);
    const key = `${site ? "s" : "d"}:${day}:${ip}`;
    const n = +await env.USAGE.get(key) || 0;
    if (n >= +(site ? env.SITE_DAILY_LIMIT || 8 : env.DAILY_LIMIT || 40)) throw new Fail(429, "daily", "\u05D4\u05D2\u05E2\u05EA\u05DD \u05DC\u05DE\u05E1\u05E4\u05E8 \u05D4\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05D4\u05DE\u05E8\u05D1\u05D9 \u05DC\u05D4\u05D9\u05D5\u05DD \u05DE\u05D4\u05DE\u05D7\u05E9\u05D1 \u05D4\u05D6\u05D4. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1 \u05DE\u05D7\u05E8.");
    await env.USAGE.put(key, String(n + 1), { expirationTtl: 60 * 60 * 36 });
  }
}
export {
  src_default as default,
  licenseSignature,
  verifyLicense
};
