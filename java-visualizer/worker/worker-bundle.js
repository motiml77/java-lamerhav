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
  \u2022 CORRECT for every VALID input \u2014 every input that fits what the exercise states (e.g. "a non-empty stack", "values between 1 and 7",
    "assume there is room", "no need to check the data"); never crashes on such input. You MAY ASSUME THE INPUT IS VALID: do not add
    checks for invalid input, and do not handle far-fetched edge cases that the exercise does not ask about \u2014 keep the solution simple, like
    a matriculation (\u05D1\u05D2\u05E8\u05D5\u05EA) answer. Ordinary cases that the exercise allows (one element, duplicates, no match, etc.) must still work.
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
  \u2022 start with this Hebrew comment line (exactly): // \u05D0\u05E4\u05E9\u05E8 \u05DC\u05D4\u05E0\u05D9\u05D7 \u05E9\u05D4\u05E7\u05DC\u05D8 \u05EA\u05E7\u05D9\u05DF: \u05D4\u05D1\u05D3\u05D9\u05E7\u05D5\u05EA \u05DB\u05D0\u05DF \u05E8\u05E7 \u05E2\u05DC \u05E7\u05DC\u05D8 \u05EA\u05E7\u05D9\u05DF, \u05D1\u05DC\u05D9 \u05DE\u05E7\u05E8\u05D9 \u05E7\u05E6\u05D4 \u05E8\u05D7\u05D5\u05E7\u05D9\u05DD
  \u2022 test ONLY valid input, as the exercise defines it (you may assume the input is valid): no invalid data, no data that the exercise rules out
    (an empty structure when it says non-empty, a value out of the stated range...), and no far-fetched edge cases \u2014 2\u20134 meaningful checks per task;
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
  \u2022 fix every wrong solution so it is correct for every VALID input (the input may be assumed valid, as the exercise states \u2014 no checks for invalid
    input or far-fetched edge cases) \u2014 still simple, intuitive and at high-school level, as described before;
  \u2022 if mainCode tests input that the exercise rules out (invalid data, an edge case the exercise does not ask about), change that test to valid
    input and update expectedOutput, instead of complicating the solution;
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

// src/http.js
var Fail = class extends Error {
  constructor(status, code, message, detail, extra) {
    super(message);
    this.status = status;
    this.code = code;
    this.detail = detail;
    this.extra = extra;
  }
};
function reply(status, obj, extra) {
  return new Response(JSON.stringify(obj), { status, headers: Object.assign({ "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }, extra || {}) });
}

// src/access/crypto.js
var enc = new TextEncoder();
var dec = new TextDecoder();
function b64url(bytes) {
  const u8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let s = "";
  for (let i = 0; i < u8.length; i += 32768) s += String.fromCharCode.apply(null, u8.subarray(i, i + 32768));
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
function b64urlToBytes(s) {
  const t = String(s || "").replace(/-/g, "+").replace(/_/g, "/");
  const bin = atob(t + "=".repeat((4 - t.length % 4) % 4));
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}
var b64urlJson = (obj) => b64url(enc.encode(JSON.stringify(obj)));
var jsonFromB64url = (s) => JSON.parse(dec.decode(b64urlToBytes(s)));
async function sha256(s) {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", typeof s === "string" ? enc.encode(s) : s));
}
async function sha256Hex(s) {
  return Array.from(await sha256(s), (b) => b.toString(16).padStart(2, "0")).join("");
}
function randomToken(n = 32) {
  return b64url(crypto.getRandomValues(new Uint8Array(n)));
}
var CODE_ABC = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
function teacherCode() {
  const r = crypto.getRandomValues(new Uint8Array(4));
  return Array.from(r, (b) => CODE_ABC[b % CODE_ABC.length]).join("");
}
var HEB_KEYS = { "/": "Q", "'": "W", "\u05E7": "E", "\u05E8": "R", "\u05D0": "T", "\u05D8": "Y", "\u05D5": "U", "\u05DF": "I", "\u05DD": "O", "\u05E4": "P", "\u05E9": "A", "\u05D3": "S", "\u05D2": "D", "\u05DB": "F", "\u05E2": "G", "\u05D9": "H", "\u05D7": "J", "\u05DC": "K", "\u05DA": "L", "\u05D6": "Z", "\u05E1": "X", "\u05D1": "C", "\u05D4": "V", "\u05E0": "B", "\u05DE": "N", "\u05E6": "M" };
function fromHebrewKeys(s) {
  return String(s || "").replace(/[\/'\u05d0-\u05ea]/g, (ch) => HEB_KEYS[ch] || ch);
}
function normalizeCode(s) {
  const c = fromHebrewKeys(s).toUpperCase().replace(/[\s\-_]/g, "");
  return /^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}$/.test(c) ? c : "";
}
var showCode = (c) => c || "";
function sameText(a, b) {
  a = String(a || "");
  b = String(b || "");
  if (a.length !== b.length) return false;
  let x = 0;
  for (let i = 0; i < a.length; i++) x |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return x === 0;
}
function pemBody(pem) {
  return String(pem || "").replace(/\\n/g, "\n").replace(/-----[A-Z ]+-----/g, "").replace(/\s+/g, "");
}
var RS256 = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" };
async function importRsaPrivate(pem) {
  const der = b64urlToBytes(pemBody(pem).replace(/\+/g, "-").replace(/\//g, "_"));
  return crypto.subtle.importKey("pkcs8", der, RS256, true, ["sign"]);
}
async function signRs256(key, text) {
  return new Uint8Array(await crypto.subtle.sign(RS256, key, enc.encode(text)));
}
async function verifyRs256Jwk(jwk, text, sig) {
  const key = await crypto.subtle.importKey("jwk", jwk, RS256, false, ["verify"]);
  return crypto.subtle.verify(RS256, key, sig, enc.encode(text));
}
async function publicSpki(privateKey) {
  const jwk = await crypto.subtle.exportKey("jwk", privateKey);
  const pub = await crypto.subtle.importKey("jwk", { kty: "RSA", n: jwk.n, e: jwk.e, alg: "RS256", ext: true }, RS256, true, ["verify"]);
  const spki = new Uint8Array(await crypto.subtle.exportKey("spki", pub));
  return btoa(String.fromCharCode.apply(null, spki));
}

// src/access/google.js
var AuthError = class extends Error {
};
var JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
var jwksCache = { keys: null, until: 0 };
var tokenCache = { key: "", token: "", until: 0 };
function projectId(env) {
  return String(env.FIREBASE_PROJECT_ID || "").trim();
}
function localHost(h) {
  return /^(127\.0\.0\.1|localhost|\[::1\]):\d+$/.test(String(h || ""));
}
var firestoreEmulator = (env) => localHost(env.FIRESTORE_EMULATOR_HOST) ? env.FIRESTORE_EMULATOR_HOST : "";
var authEmulator = (env) => localHost(env.FIREBASE_AUTH_EMULATOR_HOST) ? env.FIREBASE_AUTH_EMULATOR_HOST : "";
async function serviceToken(env) {
  if (firestoreEmulator(env)) return "owner";
  let sa;
  try {
    sa = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT || "");
  } catch (e) {
    sa = null;
  }
  if (!sa || !sa.client_email || !sa.private_key) throw new Error("FIREBASE_SERVICE_ACCOUNT missing");
  const now = Math.floor(Date.now() / 1e3);
  if (tokenCache.key === sa.client_email && tokenCache.until > now + 300) return tokenCache.token;
  const head = b64urlJson({ alg: "RS256", typ: "JWT", kid: sa.private_key_id || void 0 });
  const body = b64urlJson({
    iss: sa.client_email,
    sub: sa.client_email,
    aud: "https://oauth2.googleapis.com/token",
    scope: "https://www.googleapis.com/auth/datastore",
    iat: now,
    exp: now + 3600
  });
  const key = await importRsaPrivate(sa.private_key);
  const jwt = head + "." + body + "." + b64url(await signRs256(key, head + "." + body));
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: "grant_type=" + encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer") + "&assertion=" + jwt
  });
  const j = await res.json().catch(() => ({}));
  if (!res.ok || !j.access_token) throw new Error("service token: " + res.status + " " + (j.error || ""));
  tokenCache = { key: sa.client_email, token: j.access_token, until: now + (+j.expires_in || 3600) };
  return j.access_token;
}
async function jwks() {
  const now = Date.now();
  if (jwksCache.keys && jwksCache.until > now) return jwksCache.keys;
  const res = await fetch(JWKS_URL);
  if (!res.ok) throw new AuthError("google keys " + res.status);
  const j = await res.json();
  const age = /max-age=(\d+)/.exec(res.headers.get("cache-control") || "");
  jwksCache = { keys: j.keys || [], until: now + 1e3 * Math.min(+(age && age[1]) || 3600, 6 * 3600) };
  return jwksCache.keys;
}
async function verifyIdToken(env, token) {
  const parts = String(token || "").split(".");
  if (parts.length !== 3) throw new AuthError("format");
  let head, claims;
  try {
    head = jsonFromB64url(parts[0]);
    claims = jsonFromB64url(parts[1]);
  } catch (e) {
    throw new AuthError("format");
  }
  const pid = projectId(env);
  if (!pid) throw new AuthError("no project");
  if (authEmulator(env) && head.alg === "none") {
  } else {
    if (head.alg !== "RS256" || !head.kid) throw new AuthError("alg");
    const jwk = (await jwks()).find((k) => k.kid === head.kid);
    if (!jwk) throw new AuthError("kid");
    const ok = await verifyRs256Jwk(jwk, parts[0] + "." + parts[1], b64urlToBytes(parts[2]));
    if (!ok) throw new AuthError("signature");
  }
  const now = Math.floor(Date.now() / 1e3);
  if (claims.aud !== pid || claims.iss !== "https://securetoken.google.com/" + pid) throw new AuthError("audience");
  if (!(claims.exp > now) || !(claims.iat <= now + 300) || !claims.sub) throw new AuthError("expired");
  const provider = claims.firebase && claims.firebase.sign_in_provider;
  if (provider !== "google.com") throw new AuthError("provider");
  if (!claims.email || claims.email_verified !== true) throw new AuthError("email");
  return { uid: String(claims.sub), email: String(claims.email).toLowerCase(), name: String(claims.name || ""), picture: String(claims.picture || "") };
}

// src/access/firestore.js
var DbError = class extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
};
function encodeValue(v) {
  if (v === null || v === void 0) return { nullValue: null };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === "string") return { stringValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(encodeValue) } };
  if (typeof v === "object") return { mapValue: { fields: encodeFields(v) } };
  return { stringValue: String(v) };
}
function encodeFields(obj) {
  const out = {};
  for (const [k, v] of Object.entries(obj || {})) if (v !== void 0 && !k.startsWith("_")) out[k] = encodeValue(v);
  return out;
}
function decodeValue(v) {
  if (!v || "nullValue" in v) return null;
  if ("booleanValue" in v) return v.booleanValue;
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return Number(v.doubleValue);
  if ("stringValue" in v) return v.stringValue;
  if ("timestampValue" in v) return v.timestampValue;
  if ("arrayValue" in v) return (v.arrayValue.values || []).map(decodeValue);
  if ("mapValue" in v) return decodeFields(v.mapValue.fields);
  return null;
}
function decodeFields(f) {
  const out = {};
  for (const [k, v] of Object.entries(f || {})) out[k] = decodeValue(v);
  return out;
}
function decodeDoc(d) {
  if (!d || !d.name) return null;
  const o = decodeFields(d.fields);
  Object.defineProperty(o, "_id", { value: decodeURIComponent(d.name.split("/").pop()), enumerable: false });
  return o;
}
var OPS = { "==": "EQUAL", "!=": "NOT_EQUAL", "<": "LESS_THAN", "<=": "LESS_THAN_OR_EQUAL", ">": "GREATER_THAN", ">=": "GREATER_THAN_OR_EQUAL", "in": "IN", "array-contains": "ARRAY_CONTAINS" };
function db(env) {
  const pid = projectId(env);
  const emu = firestoreEmulator(env);
  const root = `projects/${pid}/databases/(default)/documents`;
  const base = (emu ? `http://${emu}` : "https://firestore.googleapis.com") + "/v1/";
  const name = (path) => `${root}/${path}`;
  const urlOf = (path) => base + root + "/" + String(path).split("/").map(encodeURIComponent).join("/");
  async function call(method, url, body) {
    const token = await serviceToken(env);
    const res = await fetch(url, {
      method,
      headers: { authorization: "Bearer " + token, "content-type": "application/json" },
      body: body === void 0 ? void 0 : JSON.stringify(body)
    });
    const text = await res.text();
    let j = null;
    try {
      j = text ? JSON.parse(text) : null;
    } catch (e) {
      j = null;
    }
    if (!res.ok) {
      const msg = j && j.error && j.error.status + " " + j.error.message || text.slice(0, 200);
      throw new DbError(res.status, msg);
    }
    return j;
  }
  function updateWrite(path, data, opts = {}) {
    const w = { update: { name: name(path), fields: encodeFields(data) } };
    if (opts.merge) w.updateMask = { fieldPaths: Object.keys(data).filter((k) => data[k] !== void 0 && !k.startsWith("_")).map(quoteField) };
    if (opts.exists === true) w.currentDocument = { exists: true };
    if (opts.exists === false) w.currentDocument = { exists: false };
    if (opts.increment) {
      w.updateTransforms = Object.entries(opts.increment).map(([f, n]) => ({ fieldPath: quoteField(f), increment: encodeValue(n) }));
      if (!w.updateMask) w.updateMask = { fieldPaths: [] };
    }
    return w;
  }
  function writer() {
    const writes = [];
    return {
      writes,
      set(path, data) {
        writes.push(updateWrite(path, data));
      },
      create(path, data) {
        writes.push(updateWrite(path, data, { exists: false }));
      },
      update(path, data, increment) {
        writes.push(updateWrite(path, data, { merge: true, exists: true, increment }));
      },
      merge(path, data, increment) {
        writes.push(updateWrite(path, data, { merge: true, increment }));
      },
      remove(path) {
        writes.push({ delete: name(path) });
      }
    };
  }
  const api = {
    async get(path) {
      try {
        return decodeDoc(await call("GET", urlOf(path)));
      } catch (e) {
        if (e.status === 404) return null;
        throw e;
      }
    },
    async getMany(paths) {
      if (!paths.length) return [];
      const j = await call("POST", base + root + ":batchGet", { documents: paths.map(name) });
      const by = /* @__PURE__ */ new Map();
      for (const r of j || []) if (r.found) by.set(r.found.name, decodeDoc(r.found));
      return paths.map((p) => by.get(name(p)) || null);
    },
    /** מחליף את כל המסמך */
    async set(path, data) {
      await api.commit((w) => w.set(path, data));
    },
    /** משנה רק את השדות שנשלחו (יוצר אם אין) */
    async merge(path, data, increment) {
      await api.commit((w) => w.merge(path, data, increment));
    },
    /** משנה רק את השדות שנשלחו — רק אם המסמך קיים */
    async update(path, data, increment) {
      await api.commit((w) => w.update(path, data, increment));
    },
    /** יוצר רק אם אין עדיין — false אם כבר קיים */
    async create(path, data) {
      try {
        await api.commit((w) => w.create(path, data));
        return true;
      } catch (e) {
        if (e.status === 409 || /ALREADY_EXISTS/.test(e.message)) return false;
        throw e;
      }
    },
    async remove(path) {
      await api.commit((w) => w.remove(path));
    },
    async commit(fill, transaction) {
      const w = writer();
      await fill(w);
      if (!w.writes.length && !transaction) return;
      await call("POST", base + root + ":commit", transaction ? { writes: w.writes, transaction } : { writes: w.writes });
    },
    /**
     * שאילתה על אוסף. where: [[שדה, אופרטור, ערך], ...]; orderBy: [שדה, 'asc'|'desc']; limit.
     * (כדי לא להזדקק לאינדקסים מורכבים: סינון על שדה אחד, או מיון בלי סינון — את השאר ממיינים בקוד)
     */
    async query(collection, q = {}) {
      const sq = { from: [{ collectionId: collection }] };
      const filters = (q.where || []).map(([f, op, v]) => ({ fieldFilter: { field: { fieldPath: quoteField(f) }, op: OPS[op], value: Array.isArray(v) && op === "in" ? { arrayValue: { values: v.map(encodeValue) } } : encodeValue(v) } }));
      if (filters.length === 1) sq.where = filters[0];
      else if (filters.length > 1) sq.where = { compositeFilter: { op: "AND", filters } };
      if (q.orderBy) sq.orderBy = [{ field: { fieldPath: quoteField(q.orderBy[0]) }, direction: q.orderBy[1] === "desc" ? "DESCENDING" : "ASCENDING" }];
      if (q.limit) sq.limit = q.limit;
      const j = await call("POST", base + root + ":runQuery", { structuredQuery: sq });
      return (j || []).filter((r) => r.document).map((r) => decodeDoc(r.document));
    },
    /**
     * טרנזקציה: fn(t) קורא עם t.get / t.getMany וכותב עם t.set / t.create / t.update / t.merge / t.remove.
     * אם מישהו אחר שינה את אותם מסמכים באמצע — Firestore דוחה, ומנסים שוב (עד 5 פעמים).
     */
    async tx(fn) {
      let last;
      for (let attempt = 0; attempt < 5; attempt++) {
        const begin = await call("POST", base + root + ":beginTransaction", { options: { readWrite: {} } });
        const transaction = begin.transaction;
        const w = writer();
        const t = Object.assign(w, {
          async get(path) {
            return (await t.getMany([path]))[0];
          },
          async getMany(paths) {
            const j = await call("POST", base + root + ":batchGet", { documents: paths.map(name), transaction });
            const by = /* @__PURE__ */ new Map();
            for (const r of j || []) if (r.found) by.set(r.found.name, decodeDoc(r.found));
            return paths.map((p) => by.get(name(p)) || null);
          }
        });
        let result;
        try {
          result = await fn(t);
        } catch (e) {
          await call("POST", base + root + ":rollback", { transaction }).catch(() => {
          });
          throw e;
        }
        try {
          await call("POST", base + root + ":commit", { writes: w.writes, transaction });
          return result;
        } catch (e) {
          last = e;
          if (!(e.status === 409 || /ABORTED/.test(e.message))) throw e;
          await new Promise((r) => setTimeout(r, 30 + Math.random() * 120 * (attempt + 1)));
        }
      }
      throw last;
    }
  };
  return api;
}
function quoteField(f) {
  return String(f).split(".").map((p) => /^[A-Za-z_][A-Za-z0-9_]*$/.test(p) ? p : "`" + p.replace(/[`\\]/g, "\\$&") + "`").join(".");
}
function timeId() {
  const t = Date.now().toString(36).padStart(9, "0");
  const r = Array.from(crypto.getRandomValues(new Uint8Array(5)), (b) => (b % 36).toString(36)).join("");
  return t + r;
}

// src/access/pay.js
var PAY_PROVIDERS = [];
var page = (title, body) => new Response(
  `<!doctype html><html lang="he" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title>
<style>body{margin:0;font-family:system-ui,sans-serif;background:#F4F6FB;color:#17213A;display:flex;min-height:100vh;align-items:center;justify-content:center}
.c{background:#fff;border:1px solid #E1E6F0;border-radius:18px;padding:32px 36px;max-width:420px;text-align:center;display:flex;flex-direction:column;gap:14px}
button{height:48px;border:0;border-radius:12px;background:#2F6FED;color:#fff;font:700 16px system-ui;cursor:pointer}</style></head><body><div class="c">${body}</div></body></html>`,
  { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } }
);
var DEMO = {
  id: "demo",
  async create(env, pay, origin) {
    return { url: origin + "/pay/demo?id=" + encodeURIComponent(pay.id), ref: "demo-" + pay.id };
  },
  async verify(env, request) {
    const b = await request.json().catch(() => ({}));
    return b && b.id ? { id: String(b.id), status: b.status || "paid", amount: b.amount, ref: "demo-" + b.id, payer: b.payer || null } : null;
  },
  async routes(request, env, url, h) {
    if (url.pathname !== "/pay/demo" || request.method !== "GET") return null;
    const id = String(url.searchParams.get("id") || "").replace(/[^A-Za-z0-9_-]/g, "");
    const p = id ? await h.db().get("payments/" + id) : null;
    if (!p) return page("\u05EA\u05E9\u05DC\u05D5\u05DD", "<h1>\u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05DC\u05D0 \u05E0\u05DE\u05E6\u05D0</h1>");
    const back = p.kind === "seats" ? "/account?r=paid" : "/welcome?r=paid";
    return page("\u05EA\u05E9\u05DC\u05D5\u05DD \u05DC\u05D1\u05D3\u05D9\u05E7\u05D4", `<h1 style="margin:0">\u05EA\u05E9\u05DC\u05D5\u05DD \u05DC\u05D1\u05D3\u05D9\u05E7\u05D4</h1><p>${p.title || ""} \xB7 ${p.amount} \u20AA</p>
<input id="nm" placeholder="\u05E9\u05DD \u05D4\u05DE\u05E9\u05DC\u05DD" value="${p.name || ""}" style="height:44px;border:1.5px solid #C9D2E3;border-radius:10px;padding:0 12px;font:15px system-ui">
<input id="ph" placeholder="\u05D8\u05DC\u05E4\u05D5\u05DF" value="050-1234567" dir="ltr" style="height:44px;border:1.5px solid #C9D2E3;border-radius:10px;padding:0 12px;font:15px system-ui">
<button id="pay" type="button">\u05EA\u05E9\u05DC\u05D5\u05DD (\u05D1\u05D3\u05D9\u05E7\u05D4)</button>
<script>document.getElementById('pay').onclick=async()=>{await fetch('/v1/pay/notify/demo',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({id:${JSON.stringify(id)},status:'paid',amount:${+p.amount},payer:{name:document.getElementById('nm').value,phone:document.getElementById('ph').value,email:${JSON.stringify(p.email)}}})});location.href=${JSON.stringify(back)};};</script>`);
  }
};
var PP_ID = /^[A-Z0-9]{8,40}$/;
var OUR_ID = /^[A-Za-z0-9_-]{8,64}$/;
var ppToken = { key: "", token: "", until: 0 };
var ppBase = (env) => String(env.PAYPAL_ENV || "").toLowerCase() === "live" ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";
var money = (n) => (Math.round(+n * 100) / 100).toFixed(2);
async function ppAccessToken(env) {
  const key = ppBase(env) + "|" + env.PAYPAL_CLIENT_ID;
  const now = Math.floor(Date.now() / 1e3);
  if (ppToken.key === key && ppToken.until > now + 60) return ppToken.token;
  const res = await fetch(ppBase(env) + "/v1/oauth2/token", {
    method: "POST",
    headers: { authorization: "Basic " + btoa(env.PAYPAL_CLIENT_ID + ":" + env.PAYPAL_SECRET), "content-type": "application/x-www-form-urlencoded", accept: "application/json" },
    body: "grant_type=client_credentials"
  });
  const j = await res.json().catch(() => ({}));
  if (!res.ok || !j.access_token) throw new Error("paypal token: " + res.status);
  ppToken = { key, token: j.access_token, until: now + (+j.expires_in || 300) };
  return j.access_token;
}
async function pp(env, method, path, body, requestId) {
  const headers = { authorization: "Bearer " + await ppAccessToken(env), accept: "application/json" };
  if (body !== void 0) headers["content-type"] = "application/json";
  if (requestId) headers["paypal-request-id"] = requestId;
  const res = await fetch(ppBase(env) + path, { method, headers, body: body === void 0 ? void 0 : typeof body === "string" ? body : JSON.stringify(body) });
  return { status: res.status, json: await res.json().catch(() => ({})) };
}
var ppIssue = (r, issue) => Array.isArray(r.json && r.json.details) && r.json.details.some((d) => d && d.issue === issue);
function captureResult(pid, cap, order2) {
  if (!cap || cap.custom_id !== pid || !cap.amount) return null;
  const payer = order2 && order2.payer ? {
    name: [order2.payer.name && order2.payer.name.given_name, order2.payer.name && order2.payer.name.surname].filter(Boolean).join(" "),
    email: order2.payer.email_address || "",
    phone: order2.payer.phone && order2.payer.phone.phone_number && order2.payer.phone.phone_number.national_number || ""
  } : null;
  const base = { id: pid, amount: +cap.amount.value, currency: cap.amount.currency_code, ref: String(cap.id || ""), payer };
  if (cap.status === "COMPLETED") return Object.assign(base, { status: "paid" });
  if (cap.status === "REFUNDED") return { id: pid, status: "refunded", ref: base.ref };
  if (cap.status === "DECLINED" || cap.status === "FAILED") return { id: pid, status: "failed", ref: base.ref };
  return null;
}
async function ppSettleOrder(env, store, pid, orderId) {
  if (!OUR_ID.test(pid) || !PP_ID.test(orderId)) return null;
  const p = await store.get("payments/" + pid);
  if (!p || p.provider !== "paypal" || p.providerOrder !== orderId) return null;
  let r = await pp(env, "POST", "/v2/checkout/orders/" + orderId + "/capture", {}, "capture-" + pid);
  if (r.status === 422 && ppIssue(r, "ORDER_ALREADY_CAPTURED")) r = await pp(env, "GET", "/v2/checkout/orders/" + orderId);
  if (r.status !== 200 && r.status !== 201) return null;
  const order2 = r.json;
  if (order2.id !== orderId) return null;
  const unit = (order2.purchase_units || [])[0];
  const cap = unit && unit.payments && (unit.payments.captures || [])[0];
  return captureResult(pid, cap, order2);
}
var PAYPAL = {
  id: "paypal",
  async create(env, pay, origin) {
    const r = await pp(env, "POST", "/v2/checkout/orders", {
      intent: "CAPTURE",
      purchase_units: [{
        reference_id: pay.id,
        custom_id: pay.id,
        invoice_id: pay.id,
        description: String(pay.title || "DSViz").slice(0, 120),
        amount: { currency_code: pay.currency || "ILS", value: money(pay.amount) }
      }],
      payment_source: { paypal: { experience_context: {
        brand_name: "DSViz",
        locale: "he-IL",
        shipping_preference: "NO_SHIPPING",
        user_action: "PAY_NOW",
        return_url: origin + "/pay/paypal/return?p=" + encodeURIComponent(pay.id),
        cancel_url: origin + "/pay/paypal/cancel?p=" + encodeURIComponent(pay.id)
      } } }
    }, "create-" + pay.id);
    const link = (r.json.links || []).find((l) => l.rel === "payer-action") || (r.json.links || []).find((l) => l.rel === "approve");
    if (r.status !== 200 && r.status !== 201 || !PP_ID.test(String(r.json.id || "")) || !link || !/^https:\/\/www\.(sandbox\.)?paypal\.com\//.test(String(link.href))) {
      throw new Error("paypal create: " + r.status + " " + (r.json.name || ""));
    }
    return { url: link.href, ref: r.json.id, order: r.json.id };
  },
  /** webhook: חתימה מול PayPal, ואז שאילתה חוזרת על ההזמנה / הגבייה. מה שכתוב בהודעה עצמה משמש רק כדי לדעת על מה לשאול */
  async verify(env, request, store) {
    const raw = await request.text();
    if (!env.PAYPAL_WEBHOOK_ID || raw.length > 2e5) throw new Error("paypal webhook: not configured");
    let ev;
    try {
      ev = JSON.parse(raw);
    } catch (e) {
      return null;
    }
    const hd = (n) => String(request.headers.get(n) || "");
    const v = await pp(
      env,
      "POST",
      "/v1/notifications/verify-webhook-signature",
      '{"auth_algo":' + JSON.stringify(hd("paypal-auth-algo")) + ',"cert_url":' + JSON.stringify(hd("paypal-cert-url")) + ',"transmission_id":' + JSON.stringify(hd("paypal-transmission-id")) + ',"transmission_sig":' + JSON.stringify(hd("paypal-transmission-sig")) + ',"transmission_time":' + JSON.stringify(hd("paypal-transmission-time")) + ',"webhook_id":' + JSON.stringify(String(env.PAYPAL_WEBHOOK_ID)) + ',"webhook_event":' + raw + "}"
    );
    if (v.status !== 200 || v.json.verification_status !== "SUCCESS") throw new Error("paypal webhook: bad signature");
    const res = ev && ev.resource || {}, type = String(ev && ev.event_type || "");
    if (type === "CHECKOUT.ORDER.APPROVED") {
      const pid = String(((res.purchase_units || [])[0] || {}).custom_id || "");
      return ppSettleOrder(env, store, pid, String(res.id || ""));
    }
    if (/^PAYMENT\.CAPTURE\./.test(type)) {
      const up = (res.links || []).find((l) => l && l.rel === "up" && /\/v2\/payments\/captures\/[A-Z0-9]+$/.test(String(l.href)));
      const capId = up ? String(up.href).split("/").pop() : String(res.id || "");
      if (!PP_ID.test(capId)) return null;
      const c = await pp(env, "GET", "/v2/payments/captures/" + capId);
      if (c.status !== 200) return null;
      const pid = String(c.json.custom_id || "");
      if (!OUR_ID.test(pid)) return null;
      const p = await store.get("payments/" + pid);
      const orderId = c.json.supplementary_data && c.json.supplementary_data.related_ids && c.json.supplementary_data.related_ids.order_id;
      if (!p || p.provider !== "paypal" || orderId && orderId !== p.providerOrder) return null;
      return captureResult(pid, c.json, null);
    }
    return null;
  },
  async routes(request, env, url, h) {
    if (request.method !== "GET" || url.pathname !== "/pay/paypal/return" && url.pathname !== "/pay/paypal/cancel") return null;
    const pid = String(url.searchParams.get("p") || ""), store = h.db();
    const p = OUR_ID.test(pid) ? await store.get("payments/" + pid) : null;
    const home = p && p.kind === "seats" ? "/account" : "/welcome";
    const go = (to) => new Response(null, { status: 302, headers: { location: to, "cache-control": "no-store", "referrer-policy": "no-referrer" } });
    if (!p || p.provider !== "paypal") return go("/welcome?r=error");
    if (url.pathname === "/pay/paypal/cancel") return go(home + (home === "/welcome" ? "?r=pay" : ""));
    const n = await ppSettleOrder(env, store, pid, String(url.searchParams.get("token") || "")).catch(() => null);
    if (n) await h.settlePayment(n);
    return go(home + "?r=paid");
  }
};
PAY_PROVIDERS.push("paypal");
function payProvider(env) {
  const id = String(env.PAY_PROVIDER || "").toLowerCase().trim();
  if (id === "demo") return firestoreEmulator(env) ? DEMO : null;
  if (id === "paypal") return env.PAYPAL_CLIENT_ID && env.PAYPAL_SECRET ? PAYPAL : null;
  return null;
}

// src/access/assets.gen.js
var ASSETS = { "/account.html": '<!doctype html>\n<html lang="he" dir="rtl">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1">\n<title>\u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05E9\u05DC\u05D9 \u2014 \u05DE\u05D0\u05D2\u05E8 \u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD</title>\n<link rel="preconnect" href="https://fonts.googleapis.com">\n<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Assistant:wght@400;600;700&family=Rubik:wght@500;600;700&family=JetBrains+Mono:wght@600&display=swap">\n<link rel="stylesheet" href="/static/app.css">\n</head>\n<body>\n<div id="app"><div class="loading">\u05E8\u05D2\u05E2\u2026</div></div>\n<script type="module" src="/static/account.js"></script>\n</body>\n</html>\n', "/account.js": "// /account \u2014 \u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05E9\u05DC\u05D9 \u05D1\u05D3\u05E4\u05D3\u05E4\u05DF: \u05DE\u05D5\u05E8\u05D4 (\u05D4\u05E7\u05D5\u05D3, \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA, \u05D1\u05E7\u05E9\u05D5\u05EA \u05DE\u05D4\u05D4\u05E0\u05D4\u05DC\u05D4) / \u05EA\u05DC\u05DE\u05D9\u05D3 (\u05DE\u05E6\u05D1 \u05D4\u05D2\u05D9\u05E9\u05D4, \u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD) / \u05D4\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05D4\u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD\nimport { boot, currentUser, googleSignIn, api, h, icon, ICONS, googleLogo, topBar, fmtDate, ago, daysSince, busy, toast, ask, logoutWeb } from '/static/common.js';\n\nconst app = document.getElementById('app');\nlet user = null;\nlet me = null;\n\nconst STATUS = { open: ['\u05DE\u05DE\u05EA\u05D9\u05E0\u05D4', 'orange'], approved: ['\u05D0\u05D5\u05E9\u05E8\u05D4', 'green'], declined: ['\u05E0\u05D3\u05D7\u05EA\u05D4', 'gray'] };\n\nfunction requestText(r) {\n  if (r.kind === 'seats') return `${r.amount} \u05E2\u05DE\u05D3\u05D5\u05EA \u05E0\u05D5\u05E1\u05E4\u05D5\u05EA`;   // \u05D1\u05E7\u05E9\u05D5\u05EA \u05D9\u05E9\u05E0\u05D5\u05EA\n  if (r.kind === 'release') return `\u05E9\u05D7\u05E8\u05D5\u05E8 \u05F4${r.stationName}\u05F4`;\n  return '\u05D4\u05E8\u05E9\u05D0\u05EA \u05DE\u05D5\u05E8\u05D4';\n}\n\nfunction requestDialog(kind, station) {   // \u05E8\u05E7 \"\u05DC\u05E9\u05D7\u05E8\u05E8 \u05DE\u05D7\u05E9\u05D1\" \u2014 \u05E2\u05DE\u05D3\u05D5\u05EA \u05E0\u05D5\u05E1\u05E4\u05D5\u05EA \u05E7\u05D5\u05E0\u05D9\u05DD \u05D1\u05D7\u05D1\u05D9\u05DC\u05D4\n  const d = h('dialog', { class: 'modal', 'aria-labelledby': 'rq-t' });\n  const amount = h('input', { class: 'field num', type: 'number', min: '1', max: '500', value: '5', id: 'rq-n', style: { width: '96px' } });\n  const note = h('textarea', { class: 'field', rows: '2', id: 'rq-note', placeholder: kind === 'seats' ? '\u05DC\u05DE\u05E9\u05DC: \u05E0\u05E4\u05EA\u05D7\u05D4 \u05DE\u05E2\u05D1\u05D3\u05D4 \u05D7\u05D3\u05E9\u05D4 \u05E2\u05DD 10 \u05DE\u05D7\u05E9\u05D1\u05D9\u05DD' : '\u05DC\u05DE\u05E9\u05DC: \u05D4\u05DE\u05D7\u05E9\u05D1 \u05D4\u05D5\u05D7\u05DC\u05E3 \u05D1\u05D7\u05D3\u05E9' });\n  const send = h('button', { type: 'button', class: 'btn primary' }, '\u05E9\u05DC\u05D9\u05D7\u05EA \u05D4\u05D1\u05E7\u05E9\u05D4');\n  const close = () => { d.close(); d.remove(); };\n  send.onclick = () => busy(send, async () => {\n    const body = kind === 'seats' ? { kind, amount: +amount.value, note: note.value } : { kind, stationId: station.id, note: note.value };\n    const r = await api('POST', '/v1/me/request', body);\n    close();\n    toast(r.already ? '\u05DB\u05D1\u05E8 \u05D9\u05E9 \u05D1\u05E7\u05E9\u05D4 \u05DB\u05D6\u05D5 \u05E9\u05DE\u05D7\u05DB\u05D4 \u05DC\u05D4\u05E0\u05D4\u05DC\u05D4.' : '\u05D4\u05D1\u05E7\u05E9\u05D4 \u05E0\u05E9\u05DC\u05D7\u05D4 \u05DC\u05D4\u05E0\u05D4\u05DC\u05D4.');\n    load();\n  });\n  d.append(h('div', { class: 'in' },\n    h('h2', { id: 'rq-t' }, kind === 'seats' ? '\u05D1\u05E7\u05E9\u05EA \u05E2\u05DE\u05D3\u05D5\u05EA \u05E0\u05D5\u05E1\u05E4\u05D5\u05EA' : `\u05D1\u05E7\u05E9\u05D4 \u05DC\u05E9\u05D7\u05E8\u05E8 \u05D0\u05EA \u05F4${station.name}\u05F4`),\n    h('p', { class: 'muted' }, kind === 'seats' ? '\u05D4\u05D4\u05E0\u05D4\u05DC\u05D4 \u05E8\u05D5\u05D0\u05D4 \u05D0\u05EA \u05D4\u05D1\u05E7\u05E9\u05D4 \u05D5\u05DE\u05D7\u05DC\u05D9\u05D8\u05D4. \u05D0\u05D7\u05E8\u05D9 \u05D4\u05D0\u05D9\u05E9\u05D5\u05E8 \u05D4\u05DE\u05E1\u05E4\u05E8 \u05D4\u05D7\u05D3\u05E9 \u05DE\u05D5\u05E4\u05D9\u05E2 \u05DB\u05D0\u05DF \u05DE\u05D9\u05D3.' : '\u05D0\u05D7\u05E8\u05D9 \u05E9\u05D4\u05D4\u05E0\u05D4\u05DC\u05D4 \u05DE\u05E9\u05D7\u05E8\u05E8\u05EA, \u05D4\u05D4\u05D9\u05EA\u05E8 \u05D7\u05D5\u05D6\u05E8 \u05D0\u05DC\u05D9\u05DA \u05D5\u05D0\u05E4\u05E9\u05E8 \u05DC\u05D7\u05D1\u05E8 \u05DE\u05D7\u05E9\u05D1 \u05D0\u05D7\u05E8.'),\n    kind === 'seats' ? h('label', { class: 'lbl', for: 'rq-n', style: { flexDirection: 'row', alignItems: 'center', gap: '12px' } }, '\u05DB\u05DE\u05D4 \u05E2\u05DE\u05D3\u05D5\u05EA \u05DC\u05D4\u05D5\u05E1\u05D9\u05E3', amount) : null,\n    h('label', { class: 'lbl', for: 'rq-note' }, '\u05D4\u05E1\u05D1\u05E8 \u05E7\u05E6\u05E8 (\u05DC\u05D0 \u05D7\u05D5\u05D1\u05D4)', note),\n    h('div', { class: 'row' }, send, h('button', { type: 'button', class: 'btn', onclick: close }, '\u05D1\u05D9\u05D8\u05D5\u05DC'))));\n  d.addEventListener('cancel', e => { e.preventDefault(); close(); });\n  document.body.append(d);\n  d.showModal();\n}\n\nfunction teacherCards(t) {\n  const copy = h('button', { type: 'button', class: 'btn primary grow' }, '\u05D4\u05E2\u05EA\u05E7\u05EA \u05D4\u05E7\u05D5\u05D3');\n  copy.onclick = async () => { try { await navigator.clipboard.writeText(t.code); toast('\u05D4\u05E7\u05D5\u05D3 \u05D4\u05D5\u05E2\u05EA\u05E7'); } catch (e) { toast(t.code); } };\n  const free = Math.max(0, t.seats - t.used);\n  const pct = t.seats ? Math.min(100, Math.round(100 * t.used / t.seats)) : 100;\n  const stations = t.stations || [];\n  const pendingRelease = new Set(me.requests.filter(r => r.kind === 'release' && r.status === 'open').map(r => r.stationName));\n  return [\n    h('section', { class: 'card' },\n      h('h2', null, '\u05E7\u05D5\u05D3 \u05D4\u05DE\u05D5\u05E8\u05D4'),\n      h('p', { class: 'muted' }, '\u05DE\u05E7\u05DC\u05D9\u05D3\u05D9\u05DD \u05D0\u05EA \u05D4\u05E7\u05D5\u05D3 \u05D4\u05D6\u05D4 \u05E4\u05E2\u05DD \u05D0\u05D7\u05EA \u05D1\u05DB\u05DC \u05DE\u05D7\u05E9\u05D1 \u05E9\u05DC \u05D4\u05DB\u05D9\u05EA\u05D4 (\"\u05E2\u05DE\u05D3\u05EA \u05DE\u05D7\u05E9\u05D1\"). \u05DB\u05DC \u05DE\u05D7\u05E9\u05D1 \u05E9\u05DE\u05EA\u05D7\u05D1\u05E8 \u05DE\u05E9\u05EA\u05DE\u05E9 \u05D1\u05D4\u05D9\u05EA\u05E8 \u05D0\u05D7\u05D3.'),\n      h('div', { class: 'codebox' }, h('span', { class: 'mono' }, t.code)),\n      h('div', { class: 'row' }, copy),\n      h('p', { class: 'small muted' }, '\u05D4\u05E7\u05D5\u05D3 \u05E7\u05D1\u05D5\u05E2 \u2014 \u05D0\u05D5\u05EA\u05D5 \u05E7\u05D5\u05D3 \u05DC\u05DB\u05DC \u05D4\u05E9\u05E0\u05D9\u05DD.')),\n    h('section', { class: 'card' },\n      h('div', { class: 'row', style: { justifyContent: 'space-between', alignItems: 'baseline' } }, h('h2', null, '\u05E2\u05DE\u05D3\u05D5\u05EA \u05DE\u05D7\u05E9\u05D1'),\n        h('span', { class: 'muted' }, h('b', { style: { color: 'var(--ink)', fontSize: '20px' } }, String(t.used)), ` \u05DE\u05EA\u05D5\u05DA ${t.seats} \u05D1\u05E9\u05D9\u05DE\u05D5\u05E9`)),\n      h('span', { class: 'bar big ' + (free ? 'ok' : 'full') }, h('span', { style: { width: pct + '%' } })),\n      t.sends ? h('div', { class: 'small', style: { marginTop: '10px' } }, '\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05EA\u05E8\u05D2\u05D9\u05DC \u05D4\u05D7\u05D5\u05D3\u05E9 (\u05D4\u05DE\u05D5\u05E8\u05D4 \u05D5\u05DB\u05DC \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA \u05D9\u05D7\u05D3): ', h('b', null, String(t.sends.n)), ` \u05DE\u05EA\u05D5\u05DA ${t.sends.limit}`, t.sends.n >= t.sends.limit ? ' \u2014 \u05D4\u05DE\u05E0\u05D4 \u05E0\u05D2\u05DE\u05E8\u05D4, \u05EA\u05EA\u05D7\u05D3\u05E9 \u05D1\u05EA\u05D7\u05D9\u05DC\u05EA \u05D4\u05D7\u05D5\u05D3\u05E9 \u05D4\u05D1\u05D0' : '') : null,\n      h('div', { class: 'small muted' }, free ? `\u05E0\u05E9\u05D0\u05E8\u05D5 ${free} \u05D4\u05D9\u05EA\u05E8\u05D9\u05DD \u05E4\u05E0\u05D5\u05D9\u05D9\u05DD` : '\u05DB\u05DC \u05D4\u05D4\u05D9\u05EA\u05E8\u05D9\u05DD \u05D1\u05E9\u05D9\u05DE\u05D5\u05E9 \u2014 \u05DE\u05D7\u05E9\u05D1 \u05D7\u05D3\u05E9 \u05D9\u05E7\u05D1\u05DC \"\u05D0\u05D9\u05DF \u05E2\u05D5\u05D3 \u05D4\u05D9\u05EA\u05E8\u05D9\u05DD\"'),\n      h('div', { class: 'list' }, stations.length ? stations.map(s => h('div', { class: 'item' + (daysSince(s.lastSeen) > 30 ? ' stale' : '') },\n        h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, s.name), h('div', { class: 'small muted' }, (daysSince(s.lastSeen) > 30 ? '\u05DC\u05D0 \u05E0\u05E8\u05D0\u05EA\u05D4 ' + daysSince(s.lastSeen) + ' \u05D9\u05D5\u05DD' : '\u05E4\u05E2\u05D9\u05DC\u05D4 ' + ago(s.lastSeen)))),\n        pendingRelease.has(s.name) ? h('span', { class: 'pill orange' }, '\u05D1\u05E7\u05E9\u05D4 \u05E0\u05E9\u05DC\u05D7\u05D4')\n          : h('a', { href: '#', class: 'small', style: { fontWeight: 600 }, onclick: e => { e.preventDefault(); requestDialog('release', s); } }, '\u05D1\u05E7\u05E9\u05D4 \u05DC\u05E9\u05D7\u05E8\u05E8')))\n        : h('div', { class: 'empty' }, '\u05E2\u05D5\u05D3 \u05D0\u05D9\u05DF \u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD.')),\n      h('span', { class: 'small muted' }, '\u05DE\u05D7\u05E9\u05D1 \u05E9\u05D4\u05D5\u05D7\u05DC\u05E3? \"\u05D1\u05E7\u05E9\u05D4 \u05DC\u05E9\u05D7\u05E8\u05E8\" \u2014 \u05D5\u05D4\u05D4\u05E0\u05D4\u05DC\u05D4 \u05DE\u05D7\u05D6\u05D9\u05E8\u05D4 \u05D0\u05EA \u05D4\u05D4\u05D9\u05EA\u05E8.')),\n    packagesCard(),\n  ];\n}\n\nfunction packagesCard() {\n  const list = me.packages || [];\n  return h('section', { class: 'card', style: { gridColumn: '1 / -1' } },\n    h('div', { class: 'row wrap', style: { justifyContent: 'space-between', alignItems: 'baseline' } }, h('h2', null, '\u05E2\u05D5\u05D3 \u05E2\u05DE\u05D3\u05D5\u05EA \u05DE\u05D7\u05E9\u05D1'),\n      h('span', { class: 'small muted' }, '\u05D4\u05E2\u05DE\u05D3\u05D5\u05EA \u05DE\u05EA\u05D5\u05D5\u05E1\u05E4\u05D5\u05EA \u05DE\u05D9\u05D3 \u05D0\u05D7\u05E8\u05D9 \u05D4\u05EA\u05E9\u05DC\u05D5\u05DD.')),\n    list.length ? h('div', { class: 'packs' }, list.map(p => {\n      const b = h('button', { type: 'button', class: 'btn primary wide' }, `\u05E7\u05E0\u05D9\u05D9\u05D4 \xB7 ${p.price} \u20AA`);\n      b.onclick = () => busy(b, async () => { const r = await api('POST', '/v1/pay/start', { kind: 'seats', package: p.index }); location.href = r.url; });\n      return h('div', { class: 'pack' }, h('div', { class: 'display', style: { fontSize: '30px', fontWeight: 700 } }, String(p.seats)), h('div', { class: 'muted' }, '\u05E2\u05DE\u05D3\u05D5\u05EA \u05E0\u05D5\u05E1\u05E4\u05D5\u05EA'), b);\n    })) : h('p', { class: 'muted' }, '\u05D4\u05D7\u05D1\u05D9\u05DC\u05D5\u05EA \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E4\u05EA\u05D5\u05D7\u05D5\u05EA \u05DC\u05E7\u05E0\u05D9\u05D9\u05D4. \u05D1\u05D9\u05E0\u05EA\u05D9\u05D9\u05DD \u2014 \u05E4\u05D5\u05E0\u05D9\u05DD \u05DC\u05D4\u05E0\u05D4\u05DC\u05D4.'));\n}\n\nasync function waitSeats() {\n  // \u05D7\u05D6\u05E8\u05D4 \u05DE\u05D3\u05E3 \u05D4\u05EA\u05E9\u05DC\u05D5\u05DD: \u05DE\u05D7\u05DB\u05D9\u05DD \u05E9\u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05D9\u05D0\u05D5\u05E9\u05E8 (\u05D1\u05D3\u05E8\u05DA \u05DB\u05DC\u05DC \u05DB\u05DE\u05D4 \u05E9\u05E0\u05D9\u05D5\u05EA) \u05D5\u05D0\u05D6 \u05DE\u05E6\u05D9\u05D2\u05D9\u05DD \u05D0\u05EA \u05D4\u05DE\u05E1\u05E4\u05E8 \u05D4\u05D7\u05D3\u05E9\n  const since = Date.now() - 15 * 60000;\n  for (let i = 0; i < 40; i++) {\n    const m = await api('GET', '/v1/me').catch(() => null);\n    const paid = m && m.payments.find(p => p.kind === 'seats' && p.status === 'paid' && Date.parse(p.paidAt) > since);\n    if (paid) { history.replaceState(null, '', '/account'); toast(`\u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05D4\u05EA\u05E7\u05D1\u05DC \u2014 \u05E0\u05D5\u05E1\u05E4\u05D5 ${paid.seats} \u05E2\u05DE\u05D3\u05D5\u05EA`); return; }\n    await new Promise(r => setTimeout(r, 3000));\n  }\n  toast('\u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05D0\u05D5\u05E9\u05E8. \u05D0\u05DD \u05D7\u05D5\u05D9\u05D1\u05EA \u2014 \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA \u05D9\u05EA\u05D5\u05D5\u05E1\u05E4\u05D5 \u05DC\u05D1\u05D3 \u05D1\u05D3\u05E7\u05D5\u05EA \u05D4\u05E7\u05E8\u05D5\u05D1\u05D5\u05EA.', true);\n}\n\nfunction requestsCard() {\n  if (!me.requests.length) return null;\n  return h('section', { class: 'card' }, h('h2', null, '\u05D4\u05D1\u05E7\u05E9\u05D5\u05EA \u05E9\u05DC\u05D9'),\n    h('div', { class: 'stack' }, me.requests.map(r => h('div', { class: 'list' }, h('div', { class: 'item' },\n      h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, requestText(r)),\n        h('div', { class: 'small muted' }, r.status === 'open' ? '\u05E0\u05E9\u05DC\u05D7\u05D4 ' + ago(r.createdAt) : (r.status === 'approved' ? '\u05D0\u05D5\u05E9\u05E8\u05D4 ' : '\u05E0\u05D3\u05D7\u05EA\u05D4 ') + fmtDate(r.decidedAt) + (r.reason ? ' \xB7 ' + r.reason : ''))),\n      h('span', { class: 'pill ' + STATUS[r.status][1] }, STATUS[r.status][0]))))));\n}\n\nfunction studentCard() {\n  const s = me.student;\n  return h('section', { class: 'card' }, h('h2', null, '\u05DE\u05D0\u05D2\u05E8 \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD'),\n    s.sends && s.open ? h('div', { class: 'small', style: { marginBottom: '8px' } }, '\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05EA\u05E8\u05D2\u05D9\u05DC \u05D4\u05D7\u05D5\u05D3\u05E9: ', h('b', null, String(s.sends.n)), ` \u05DE\u05EA\u05D5\u05DA ${s.sends.limit}`) : null,\n    s.open ? h('div', { class: 'row' }, h('span', { class: 'pill green' }, '\u05E4\u05EA\u05D5\u05D7'), h('span', null, `\u05E2\u05D3 ${fmtDate(s.until)}${s.source === 'manual' ? ' \xB7 \u05D4\u05E2\u05E0\u05E7\u05D4 \u05DE\u05D4\u05D4\u05E0\u05D4\u05DC\u05D4' : ''}`))\n      : h('div', { class: 'stack' }, h('div', { class: 'row' }, h('span', { class: 'pill gray' }, '\u05E1\u05D2\u05D5\u05E8'), h('span', { class: 'muted' }, me.canPay ? `\u05EA\u05E9\u05DC\u05D5\u05DD \u05D0\u05D7\u05D3 \u05E9\u05DC ${me.price} \u20AA \u05E4\u05D5\u05EA\u05D7 \u05E2\u05D3 ${fmtDate(me.accessUntil)}` : '\u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E4\u05EA\u05D5\u05D7')),\n        me.canPay ? h('a', { class: 'btn primary', href: '/welcome?r=pay', style: { alignSelf: 'flex-start' } }, '\u05DC\u05EA\u05E9\u05DC\u05D5\u05DD') : null),\n    me.payments.length ? h('div', { class: 'list' }, me.payments.map(p => h('div', { class: 'item' },\n      h('div', { class: 'grow' }, `${p.kind === 'seats' ? `\u05D7\u05D1\u05D9\u05DC\u05EA ${p.seats} \u05E2\u05DE\u05D3\u05D5\u05EA \xB7 ` : ''}${p.amount} \u20AA \xB7 ${fmtDate(p.paidAt || p.createdAt)}`),\n      h('span', { class: 'pill ' + (p.status === 'paid' ? 'green' : 'gray') }, p.status === 'paid' ? '\u05E9\u05D5\u05DC\u05DD' : p.status === 'refunded' ? '\u05D4\u05D5\u05D7\u05D6\u05E8' : '\u05DC\u05D0 \u05D4\u05D5\u05E9\u05DC\u05DD')))) : null);\n}\n\nfunction devicesCard() {\n  const items = me.devices;\n  return h('section', { class: 'card' }, h('h2', null, '\u05D4\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05E9\u05DC\u05D9'),\n    h('p', { class: 'small muted' }, '\u05D4\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05E9\u05D1\u05D4\u05DD \u05E0\u05DB\u05E0\u05E1\u05EA \u05E2\u05DD \u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05D4\u05D6\u05D4. \u05DE\u05D7\u05E9\u05D1 \u05E9\u05E0\u05D5\u05EA\u05E7 \u2014 \u05D9\u05E6\u05D8\u05E8\u05DA \u05DC\u05D4\u05EA\u05D7\u05D1\u05E8 \u05E9\u05D5\u05D1.'),\n    h('div', { class: 'list' }, items.length ? items.map(d => {\n      const b = h('button', { type: 'button', class: 'btn sm' }, '\u05E0\u05D9\u05EA\u05D5\u05E7');\n      b.onclick = async () => {\n        if (!await ask({ title: `\u05DC\u05E0\u05EA\u05E7 \u05D0\u05EA \u05F4${d.name || '\u05D4\u05DE\u05D7\u05E9\u05D1'}\u05F4?`, text: '\u05D1\u05DE\u05D7\u05E9\u05D1 \u05D4\u05D6\u05D4 \u05D9\u05E6\u05D8\u05E8\u05DB\u05D5 \u05DC\u05D4\u05EA\u05D7\u05D1\u05E8 \u05E9\u05D5\u05D1 \u05DB\u05D3\u05D9 \u05DC\u05E4\u05EA\u05D5\u05D7 \u05D0\u05EA \u05D4\u05DE\u05D0\u05D2\u05E8.', ok: '\u05E0\u05D9\u05EA\u05D5\u05E7', danger: true })) return;\n        busy(b, async () => { await api('POST', '/v1/me/devices/revoke', { id: d.id }); toast('\u05D4\u05DE\u05D7\u05E9\u05D1 \u05E0\u05D5\u05EA\u05E7'); load(); });\n      };\n      return h('div', { class: 'item' }, icon(ICONS.monitor, 20, '#5A6478'),\n        h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, d.name || '\u05DE\u05D7\u05E9\u05D1'), h('div', { class: 'small muted' }, (d.role === 'teacher' ? '\u05DB\u05DE\u05D5\u05E8\u05D4 \xB7 ' : '') + '\u05E4\u05E2\u05D9\u05DC ' + ago(d.lastSeen))), b);\n    }) : h('div', { class: 'empty' }, '\u05D0\u05D9\u05DF \u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD.')));\n}\n\nasync function load() {\n  me = await api('GET', '/v1/me');\n  const badge = me.teacher ? h('span', { class: 'pill blue' }, '\u05DE\u05D5\u05E8\u05D4') : null;\n  const head = h('div', { class: 'titlebar' }, h('h1', null, me.teacher ? '\u05D4\u05DC\u05D5\u05D7 \u05E9\u05DC \u05D4\u05DE\u05D5\u05E8\u05D4' : '\u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05E9\u05DC\u05D9'),\n    me.admin ? h('a', { class: 'btn dark', href: '/admin' }, icon(ICONS.shield, 18, '#fff'), '\u05DE\u05E1\u05D5\u05E3 \u05D4\u05D4\u05E0\u05D4\u05DC\u05D4') : null);\n  const body = me.teacher\n    ? [h('div', { class: 'grid2' }, ...teacherCards(me.teacher)), h('div', { class: 'grid2' }, requestsCard() || h('span'), devicesCard())]\n    : [h('div', { class: 'grid2' }, studentCard(), devicesCard())];\n  if (me.teacher && !me.teacher.active) body.unshift(h('div', { class: 'warn-box' }, '\u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05E9\u05DC\u05DA \u05DB\u05DE\u05D5\u05E8\u05D4 \u05D4\u05D5\u05E9\u05D1\u05EA \u2014 \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA \u05E9\u05DC\u05DA \u05E0\u05E0\u05E2\u05DC\u05D5\u05EA \u05D1\u05D1\u05D3\u05D9\u05E7\u05D4 \u05D4\u05D1\u05D0\u05D4. \u05E4\u05D5\u05E0\u05D9\u05DD \u05DC\u05D4\u05E0\u05D4\u05DC\u05D4.'));\n  app.replaceChildren(topBar(user, { badge, onLogout: async () => { await logoutWeb(); location.reload(); } }), h('main', { class: 'page' }, head, ...body));\n}\n\nasync function main() {\n  try { await boot(); } catch (e) { app.replaceChildren(topBar(null), h('div', { class: 'loading' }, '\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E4\u05EA\u05D5\u05D7\u05D4.')); return; }\n  user = await currentUser();\n  if (!user) {\n    const b = h('button', { type: 'button', class: 'gbtn' }, googleLogo(), '\u05D4\u05EA\u05D7\u05D1\u05E8\u05D5\u05EA \u05E2\u05DD Google');\n    b.onclick = () => busy(b, async () => { user = await googleSignIn(); await load(); });\n    app.replaceChildren(topBar(null), h('main', { class: 'center' }, h('div', { class: 'panel' },\n      h('h1', null, '\u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05E9\u05DC\u05D9'), h('p', { class: 'muted', style: { lineHeight: '1.6' } }, '\u05DE\u05D5\u05E8\u05D9\u05DD \u05E8\u05D5\u05D0\u05D9\u05DD \u05DB\u05D0\u05DF \u05D0\u05EA \u05E7\u05D5\u05D3 \u05D4\u05DE\u05D5\u05E8\u05D4 \u05D5\u05D0\u05EA \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA; \u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD \u2014 \u05D0\u05EA \u05DE\u05E6\u05D1 \u05D4\u05DE\u05D0\u05D2\u05E8 \u05D5\u05D4\u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD.'), b)));\n    return;\n  }\n  try {\n    if (new URLSearchParams(location.search).get('r') === 'paid') { await load(); await waitSeats(); }\n    await load();\n  } catch (e) { app.replaceChildren(topBar(user), h('div', { class: 'loading' }, e.message)); }\n}\nmain();\n", "/admin.html": '<!doctype html>\n<html lang="he" dir="rtl">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1">\n<title>\u05DE\u05E1\u05D5\u05E3 \u05D4\u05D4\u05E0\u05D4\u05DC\u05D4 \u2014 DSViz</title>\n<link rel="preconnect" href="https://fonts.googleapis.com">\n<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Assistant:wght@400;600;700&family=Rubik:wght@500;600;700&family=JetBrains+Mono:wght@600&display=swap">\n<link rel="stylesheet" href="/static/app.css">\n</head>\n<body>\n<div id="app"><div class="loading">\u05E8\u05D2\u05E2\u2026</div></div>\n<script type="module" src="/static/admin.js"></script>\n</body>\n</html>\n', "/admin.js": "// /admin \u2014 \u05DE\u05E1\u05D5\u05E3 \u05D4\u05D4\u05E0\u05D4\u05DC\u05D4: \u05E1\u05E7\u05D9\u05E8\u05D4, \u05D1\u05E7\u05E9\u05D5\u05EA, \u05DE\u05D5\u05E8\u05D9\u05DD \u05D5\u05E2\u05DE\u05D3\u05D5\u05EA, \u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD \u05D5\u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD, \u05D9\u05D5\u05DE\u05DF \u05E4\u05E2\u05D5\u05DC\u05D5\u05EA, \u05D4\u05D2\u05D3\u05E8\u05D5\u05EA.\n// \u05E8\u05E7 \u05DB\u05EA\u05D5\u05D1\u05D5\u05EA \u05E9\u05D1-ADMIN_EMAILS (\u05D1\u05D4\u05D2\u05D3\u05E8\u05D5\u05EA \u05D4\u05E9\u05E8\u05EA). \u05DB\u05DC \u05E4\u05E2\u05D5\u05DC\u05D4 \u05E0\u05D1\u05D3\u05E7\u05EA \u05D1\u05E9\u05E8\u05EA \u05D5\u05E0\u05E8\u05E9\u05DE\u05EA \u05D1\u05D9\u05D5\u05DE\u05DF.\nimport { boot, currentUser, googleSignIn, api, h, icon, ICONS, googleLogo, avatar, fmtDate, ago, daysSince, busy, toast, ask, logoutWeb } from '/static/common.js';\n\nconst app = document.getElementById('app');\nlet user = null;\nlet settingsCache = null;\nlet openCount = 0;\nlet alertsUnseen = 0;\nlet alertTimer = null;\nconst cache = { teachers: null, students: null };\n\nconst NAV = [\n  ['overview', '\u05E1\u05E7\u05D9\u05E8\u05D4', ICONS.grid],\n  ['requests', '\u05D1\u05E7\u05E9\u05D5\u05EA', ICONS.msg],\n  ['teachers', '\u05DE\u05D5\u05E8\u05D9\u05DD \u05D5\u05E2\u05DE\u05D3\u05D5\u05EA', ICONS.user],\n  ['students', '\u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD \u05D5\u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD', ICONS.cap],\n  ['log', '\u05D9\u05D5\u05DE\u05DF \u05E4\u05E2\u05D5\u05DC\u05D5\u05EA', ICONS.list],\n  ['settings', '\u05D4\u05D2\u05D3\u05E8\u05D5\u05EA', ICONS.gear],\n];\n\nconst ACTIONS = {\n  'signin.teacher': '\u05DB\u05E0\u05D9\u05E1\u05EA \u05DE\u05D5\u05E8\u05D4', 'signin.student': '\u05DB\u05E0\u05D9\u05E1\u05EA \u05EA\u05DC\u05DE\u05D9\u05D3', 'station.claim': '\u05E2\u05DE\u05D3\u05D4 \u05D7\u05D3\u05E9\u05D4 \u05D7\u05D5\u05D1\u05E8\u05D4', 'station.again': '\u05E2\u05DE\u05D3\u05D4 \u05D4\u05EA\u05D7\u05D1\u05E8\u05D4 \u05DE\u05D7\u05D3\u05E9',\n  'station.bad-code': '\u05E0\u05D9\u05E1\u05D9\u05D5\u05DF \u05D7\u05D9\u05D1\u05D5\u05E8 \u05E2\u05DD \u05E7\u05D5\u05D3 \u05DE\u05D5\u05E8\u05D4 \u05E9\u05D2\u05D5\u05D9', 'station.release': '\u05E2\u05DE\u05D3\u05D4 \u05E9\u05D5\u05D7\u05E8\u05E8\u05D4', 'teacher.add': '\u05DE\u05D5\u05E8\u05D4 \u05E0\u05D5\u05E1\u05E3', 'teacher.update': '\u05E9\u05D9\u05E0\u05D5\u05D9 \u05D0\u05E6\u05DC \u05DE\u05D5\u05E8\u05D4',\n  'teacher.new-code': '\u05E7\u05D5\u05D3 \u05DE\u05D5\u05E8\u05D4 \u05D7\u05D3\u05E9', 'request.teacher': '\u05D1\u05E7\u05E9\u05EA \u05D4\u05E8\u05E9\u05D0\u05EA \u05DE\u05D5\u05E8\u05D4', 'request.seats': '\u05D1\u05E7\u05E9\u05EA \u05E2\u05DE\u05D3\u05D5\u05EA \u05E0\u05D5\u05E1\u05E4\u05D5\u05EA', 'request.release': '\u05D1\u05E7\u05E9\u05D4 \u05DC\u05E9\u05D7\u05E8\u05E8 \u05DE\u05D7\u05E9\u05D1',\n  'request.decline': '\u05D1\u05E7\u05E9\u05D4 \u05E0\u05D3\u05D7\u05EA\u05D4', 'student.grant': '\u05D4\u05E2\u05E0\u05E7\u05EA \u05D2\u05D9\u05E9\u05D4 \u05DC\u05EA\u05DC\u05DE\u05D9\u05D3', 'student.revoke': '\u05D1\u05D9\u05D8\u05D5\u05DC \u05D2\u05D9\u05E9\u05D4 \u05DC\u05EA\u05DC\u05DE\u05D9\u05D3', 'session.revoke': '\u05DE\u05D7\u05E9\u05D1 \u05E0\u05D5\u05EA\u05E7',\n  'payment.paid': '\u05EA\u05E9\u05DC\u05D5\u05DD \u05D4\u05EA\u05E7\u05D1\u05DC', 'payment.seats': '\u05D7\u05D1\u05D9\u05DC\u05EA \u05E2\u05DE\u05D3\u05D5\u05EA \u05E0\u05E7\u05E0\u05EA\u05D4', 'payment.refunded': '\u05D4\u05D7\u05D6\u05E8 \u05DB\u05E1\u05E4\u05D9', 'payment.failed': '\u05EA\u05E9\u05DC\u05D5\u05DD \u05E0\u05DB\u05E9\u05DC', settings: '\u05E9\u05D9\u05E0\u05D5\u05D9 \u05D1\u05D4\u05D2\u05D3\u05E8\u05D5\u05EA',\n};\nconst dotFor = a => (a.startsWith('payment.') ? 'green' : a === 'station.bad-code' ? 'warn' : /^(teacher|student|settings|session|request\\.decline)/.test(a) ? 'dark' : '');\nconst REQ_KIND = { teacher: ['\u05D4\u05E8\u05E9\u05D0\u05EA \u05DE\u05D5\u05E8\u05D4', 'blue'], seats: ['\u05EA\u05D5\u05E1\u05E4\u05EA \u05E2\u05DE\u05D3\u05D5\u05EA', 'orange'], release: ['\u05E9\u05D7\u05E8\u05D5\u05E8 \u05DE\u05D7\u05E9\u05D1', 'purple'] };\n\n// ------------------------------------------------------------------ \u05E1\u05D3\u05E8 \u05DC\u05E4\u05D9 \u05D6\u05DE\u05DF (\u05E0\u05D6\u05DB\u05E8 \u05D1\u05D3\u05E4\u05D3\u05E4\u05DF \u05D4\u05D6\u05D4)\n\nconst ORDER_KEY = 'dsviz.admin.order';\nconst ORDER = (() => { try { return JSON.parse(localStorage.getItem(ORDER_KEY) || '{}') || {}; } catch (e) { return {}; } })();\nconst orderOf = (key, def = 'desc') => (['asc', 'desc', 'none'].includes(ORDER[key]) ? ORDER[key] : def);\nfunction setOrder(key, v) {\n  ORDER[key] = v;\n  try { localStorage.setItem(ORDER_KEY, JSON.stringify(ORDER)); } catch (e) { /* \u05D1\u05DC\u05D9 \u05D6\u05D9\u05DB\u05E8\u05D5\u05DF \u2014 \u05E8\u05E7 \u05DC\u05D1\u05D9\u05E7\u05D5\u05E8 \u05D4\u05D6\u05D4 */ }\n}\n/** \u05DE\u05D9\u05D5\u05DF \u05DC\u05E4\u05D9 \u05E9\u05D3\u05D4 \u05D6\u05DE\u05DF (ISO). \u05D1\u05DC\u05D9 \u05E2\u05E8\u05DA \u2014 \u05EA\u05DE\u05D9\u05D3 \u05D1\u05E1\u05D5\u05E3 */\nfunction byTime(list, field, dir) {\n  if (dir !== 'asc' && dir !== 'desc') return list.slice();\n  return list.slice().sort((a, b) => {\n    const x = a[field] || '', y = b[field] || '';\n    if (!x || !y) return x ? -1 : y ? 1 : 0;\n    return dir === 'asc' ? String(x).localeCompare(String(y)) : String(y).localeCompare(String(x));\n  });\n}\n/** \u05DB\u05E4\u05EA\u05D5\u05E8\u05D9 \"\u05D4\u05D7\u05D3\u05E9 \u05E7\u05D5\u05D3\u05DD / \u05D4\u05D9\u05E9\u05DF \u05E7\u05D5\u05D3\u05DD\". onChange(dir) \u05DE\u05E6\u05D9\u05D9\u05E8 \u05DE\u05D7\u05D3\u05E9 \u05D0\u05EA \u05D4\u05E8\u05E9\u05D9\u05DE\u05D4 */\nfunction orderToggle(key, onChange, def = 'desc') {\n  const box = h('div', { class: 'order', role: 'group', 'aria-label': '\u05E1\u05D3\u05E8 \u05DC\u05E4\u05D9 \u05D6\u05DE\u05DF' });\n  const btns = [['desc', '\u05D4\u05D7\u05D3\u05E9 \u05E7\u05D5\u05D3\u05DD'], ['asc', '\u05D4\u05D9\u05E9\u05DF \u05E7\u05D5\u05D3\u05DD']].map(([v, label]) => {\n    const b = h('button', { type: 'button', 'aria-pressed': String(orderOf(key, def) === v) }, label);\n    b.onclick = () => {\n      if (orderOf(key, def) === v) return;\n      setOrder(key, v);\n      btns.forEach(([bv, bb]) => bb.setAttribute('aria-pressed', String(bv === v)));\n      onChange(v);\n    };\n    return [v, b];\n  });\n  box.append(icon(ICONS.clock, 15, '#5A6478'), ...btns.map(x => x[1]));\n  return box;\n}\n/** \u05DB\u05D5\u05EA\u05E8\u05EA \u05E2\u05DE\u05D5\u05D3\u05D4 \u05E9\u05DE\u05DE\u05D9\u05D9\u05E0\u05EA \u05DC\u05E4\u05D9 \u05D6\u05DE\u05DF: \u05DC\u05D7\u05D9\u05E6\u05D4 \u2014 \u05D4\u05D7\u05D3\u05E9 \u05E7\u05D5\u05D3\u05DD, \u05E9\u05D5\u05D1 \u2014 \u05D4\u05D9\u05E9\u05DF \u05E7\u05D5\u05D3\u05DD, \u05E9\u05D5\u05D1 \u2014 \u05D1\u05DC\u05D9 \u05DE\u05D9\u05D5\u05DF */\nfunction timeHeader(label, key, onChange) {\n  const cur = orderOf(key, 'none');\n  const arrow = cur === 'desc' ? ' \u2193' : cur === 'asc' ? ' \u2191' : '';\n  const b = h('button', { type: 'button', class: 'thsort' + (cur !== 'none' ? ' on' : ''), title: '\u05DE\u05D9\u05D5\u05DF \u05DC\u05E4\u05D9 \u05D6\u05DE\u05DF', 'aria-label': `${label} \u2014 ${cur === 'desc' ? '\u05D4\u05D7\u05D3\u05E9 \u05E7\u05D5\u05D3\u05DD' : cur === 'asc' ? '\u05D4\u05D9\u05E9\u05DF \u05E7\u05D5\u05D3\u05DD' : '\u05D1\u05DC\u05D9 \u05DE\u05D9\u05D5\u05DF'}` }, label, arrow);\n  b.onclick = () => { setOrder(key, cur === 'none' ? 'desc' : cur === 'desc' ? 'asc' : 'none'); onChange(); };\n  return h('th', { 'aria-sort': cur === 'desc' ? 'descending' : cur === 'asc' ? 'ascending' : 'none' }, b);\n}\n/** \u05EA\u05D0\u05E8\u05D9\u05DA \u05D5\u05E9\u05E2\u05D4 \u05DE\u05DC\u05D0\u05D9\u05DD: 10.11.2026 14:05 */\nfunction fmtWhen(iso) {\n  const d = new Date(iso);\n  if (!iso || isNaN(d)) return '';\n  return `${fmtDate(iso)} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;\n}\n\n// ------------------------------------------------------------------ \u05DE\u05E1\u05D2\u05E8\u05EA\n\nfunction route() {\n  const m = /^#([a-z]+)(?:=(.*))?$/.exec(location.hash || '');\n  return m ? { page: m[1], arg: decodeURIComponent(m[2] || '') } : { page: 'overview', arg: '' };\n}\n\nfunction shell(content) {\n  const r = route();\n  const nav = h('nav', { class: 'side', 'aria-label': '\u05EA\u05E4\u05E8\u05D9\u05D8' },\n    h('div', { class: 'brand' }, h('div', { class: 'logo dark' }, icon(ICONS.shield, 19, '#fff')), h('div', null, '\u05DE\u05E1\u05D5\u05E3 \u05D4\u05D4\u05E0\u05D4\u05DC\u05D4', h('small', null, 'DSViz \xB7 \u05DE\u05D0\u05D2\u05E8 \u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD'))),\n    h('div', { class: 'nav', style: { display: 'flex', flexDirection: 'column', gap: '4px' } }, NAV.map(([id, label, ic]) => h('a', { href: '#' + id, 'aria-current': r.page === id || (id === 'teachers' && r.page === 'teacher') ? 'page' : null },\n      icon(ic, 18), h('span', null, label), id === 'requests' && openCount ? h('span', { class: 'count', 'aria-label': openCount + ' \u05E4\u05EA\u05D5\u05D7\u05D5\u05EA' }, String(openCount)) : null))));\n  const header = h('header', null, searchBox(), h('div', { class: 'grow' }), bell(),\n    h('div', { class: 'who' }, h('div', { class: 'meta' }, h('span', { class: 'ltr' }, user.email),\n      h('a', { href: '#', onclick: async e => { e.preventDefault(); await logoutWeb(); location.reload(); } }, '\u05D4\u05EA\u05E0\u05EA\u05E7\u05D5\u05EA')), avatar(user, true)));\n  app.replaceChildren(h('div', { class: 'shell' }, nav, h('div', { class: 'main' }, header, h('main', { class: 'content', id: 'content' }, content))));\n}\n\nfunction searchBox() {\n  const input = h('input', { type: 'search', placeholder: '\u05D7\u05D9\u05E4\u05D5\u05E9 \u05DE\u05D5\u05E8\u05D4, \u05EA\u05DC\u05DE\u05D9\u05D3, \u05E7\u05D5\u05D3 \u05DE\u05D5\u05E8\u05D4 \u05D0\u05D5 \u05DE\u05D7\u05E9\u05D1', 'aria-label': '\u05D7\u05D9\u05E4\u05D5\u05E9', autocomplete: 'off' });\n  const results = h('div', { class: 'results', hidden: true, role: 'listbox' });\n  const box = h('label', { class: 'search' }, icon(ICONS.search, 18, '#5A6478'), input, results);\n  let loading = null;\n  const ensure = () => loading || (loading = Promise.all([\n    cache.teachers ? cache.teachers : api('GET', '/v1/admin/teachers').then(r => (cache.teachers = r.teachers)),\n    cache.students ? cache.students : api('GET', '/v1/admin/students').then(r => (cache.students = r.students)),\n    api('GET', '/v1/admin/stations').then(r => r.stations),\n  ]));\n  input.addEventListener('focus', () => ensure().catch(() => {}));\n  input.addEventListener('input', async () => {\n    const q = input.value.trim().toLowerCase().replace(/-/g, '');\n    if (q.length < 2) { results.hidden = true; return; }\n    const [teachers, students, stations] = await ensure();\n    const hits = [];\n    for (const t of teachers) if ((t.name + ' ' + t.email + ' ' + t.school + ' ' + (t.phone || '') + ' ' + t.code.replace('-', '')).toLowerCase().includes(q)) hits.push(['\u05DE\u05D5\u05E8\u05D4', t.name || t.email, t.email, '#teacher=' + encodeURIComponent(t.email)]);\n    for (const s of stations) if (String(s.name).toLowerCase().includes(q)) hits.push(['\u05DE\u05D7\u05E9\u05D1', s.name, s.teacherName || s.teacher, '#teacher=' + encodeURIComponent(s.teacher)]);\n    for (const s of students) if ((s.name + ' ' + s.email).toLowerCase().includes(q)) hits.push(['\u05EA\u05DC\u05DE\u05D9\u05D3', s.name || s.email, s.email, '#students=' + encodeURIComponent(s.email)]);\n    results.replaceChildren(...(hits.length ? hits.slice(0, 12).map(([kind, title, sub, href]) => h('button', { type: 'button', role: 'option', onclick: () => { results.hidden = true; input.value = ''; location.hash = href; } },\n      h('span', { class: 'pill gray' }, kind), h('span', { class: 'grow' }, h('b', null, title), ' ', h('span', { class: 'small muted ltr' }, sub))))\n      : [h('div', { class: 'empty' }, '\u05DC\u05D0 \u05E0\u05DE\u05E6\u05D0')]));\n    results.hidden = false;\n  });\n  input.addEventListener('keydown', e => { if (e.key === 'Escape') results.hidden = true; });\n  document.addEventListener('click', e => { if (!box.contains(e.target)) results.hidden = true; });\n  return box;\n}\n\nconst loadingView = () => h('div', { class: 'loading' }, '\u05D8\u05D5\u05E2\u05DF\u2026');\nasync function render() {\n  const r = route();\n  shell(loadingView());\n  const content = document.getElementById('content');\n  try {\n    const views = { overview, requests, teachers, teacher: teachers, students, log: logView, settings: settingsView };\n    const v = await (views[r.page] || overview)(r.arg);\n    content.replaceChildren(...[v].flat(Infinity));\n  } catch (e) {\n    content.replaceChildren(h('div', { class: 'warn-box' }, e.message || String(e)));\n  }\n}\nfunction refreshCount(n) {\n  openCount = n;\n  const a = document.querySelector('.nav a[href=\"#requests\"]');\n  if (!a) return;\n  const old = a.querySelector('.count');\n  if (old) old.remove();\n  if (n) a.append(h('span', { class: 'count' }, String(n)));\n}\n\n// ------------------------------------------------------------------ \u05D4\u05EA\u05E8\u05D0\u05D5\u05EA: \u05DE\u05D5\u05E8\u05D4 / \u05EA\u05DC\u05DE\u05D9\u05D3 \u05E9\u05D4\u05E6\u05D8\u05E8\u05E4\u05D5, \u05D1\u05E7\u05E9\u05EA \u05D4\u05E8\u05E9\u05D0\u05D4, \u05EA\u05E9\u05DC\u05D5\u05DD (\u05E2\u05D9\u05D2\u05D5\u05DC \u05D0\u05D3\u05D5\u05DD \u05E2\u05D3 \u05E9\u05E4\u05D5\u05EA\u05D7\u05D9\u05DD)\n\nconst ALERT_DOT = { 'student.new': 'blue', 'teacher.first': 'blue', 'teacher.request': 'warn', 'payment.paid': 'green', 'payment.seats': 'green' };\n\nfunction bell() {\n  const dot = h('span', { class: 'bell-dot', hidden: !alertsUnseen }, alertsUnseen > 99 ? '99+' : String(alertsUnseen || ''));\n  const btn = h('button', { type: 'button', class: 'bell', id: 'bell', 'aria-haspopup': 'dialog', 'aria-expanded': 'false', 'aria-label': alertsUnseen ? `\u05D4\u05EA\u05E8\u05D0\u05D5\u05EA \u2014 ${alertsUnseen} \u05D7\u05D3\u05E9\u05D5\u05EA` : '\u05D4\u05EA\u05E8\u05D0\u05D5\u05EA', title: '\u05D4\u05EA\u05E8\u05D0\u05D5\u05EA' },\n    icon(ICONS.bell, 21, '#17213A'), dot);\n  const panel = h('div', { class: 'alerts', hidden: true, role: 'dialog', 'aria-label': '\u05D4\u05EA\u05E8\u05D0\u05D5\u05EA' });\n  const wrap = h('div', { class: 'bell-wrap' }, btn, panel);\n  const close = () => { panel.hidden = true; btn.setAttribute('aria-expanded', 'false'); };\n  btn.onclick = async () => {\n    if (!panel.hidden) return close();\n    panel.hidden = false;\n    btn.setAttribute('aria-expanded', 'true');\n    panel.replaceChildren(h('div', { class: 'empty' }, '\u05D8\u05D5\u05E2\u05DF\u2026'));\n    try {\n      const r = await api('GET', '/v1/admin/alerts?limit=30');\n      const item = a => h('button', { type: 'button', class: 'alert-item' + (a.seen ? '' : ' new'), onclick: () => { close(); location.hash = a.go; } },\n        h('span', { class: 'dot ' + (ALERT_DOT[a.kind] || '') }),\n        h('span', { class: 'grow stack', style: { gap: '2px', textAlign: 'right' } },\n          h('b', null, a.title),\n          h('span', null, a.name || '', a.name ? ' \xB7 ' : '', h('span', { class: 'ltr muted' }, a.email)),\n          a.detail ? h('span', { class: 'small muted' }, a.detail) : null,\n          h('span', { class: 'small muted' }, h('time', { datetime: a.at, title: fmtWhen(a.at) }, ago(a.at)))));\n      panel.replaceChildren(\n        h('div', { class: 'alerts-head' }, h('b', null, '\u05D4\u05EA\u05E8\u05D0\u05D5\u05EA'), h('span', { class: 'small muted' }, r.unseen ? `${r.unseen} \u05D7\u05D3\u05E9\u05D5\u05EA` : '\u05D0\u05D9\u05DF \u05D7\u05D3\u05E9\u05D5\u05EA')),\n        r.alerts.length ? h('div', { class: 'alerts-list' }, r.alerts.map(item)) : h('div', { class: 'empty' }, '\u05E2\u05D5\u05D3 \u05D0\u05D9\u05DF \u05D4\u05EA\u05E8\u05D0\u05D5\u05EA. \u05DB\u05E9\u05DE\u05D5\u05E8\u05D4 \u05D0\u05D5 \u05EA\u05DC\u05DE\u05D9\u05D3 \u05DE\u05E6\u05D8\u05E8\u05E4\u05D9\u05DD \u2014 \u05D6\u05D4 \u05D9\u05D5\u05E4\u05D9\u05E2 \u05DB\u05D0\u05DF.'));\n      // \u05E4\u05EA\u05D9\u05D7\u05EA \u05D4\u05E8\u05E9\u05D9\u05DE\u05D4 = \u05E0\u05E7\u05E8\u05D0\u05D5 (\u05DE\u05D4 \u05E9\u05D4\u05D9\u05D4 \u05D7\u05D3\u05E9 \u05E0\u05E9\u05D0\u05E8 \u05DE\u05E1\u05D5\u05DE\u05DF \u05D1\u05E8\u05E9\u05D9\u05DE\u05D4 \u05D4\u05E4\u05EA\u05D5\u05D7\u05D4)\n      if (r.unseen) { await api('POST', '/v1/admin/alerts/seen', { all: true }); setAlerts(0); }\n    } catch (e) {\n      panel.replaceChildren(h('div', { class: 'warn-box' }, e.message || String(e)));\n    }\n  };\n  document.addEventListener('click', e => { if (!wrap.contains(e.target)) close(); });\n  wrap.addEventListener('keydown', e => { if (e.key === 'Escape') { close(); btn.focus(); } });\n  return wrap;\n}\n\n/** \u05DE\u05E2\u05D3\u05DB\u05DF \u05D0\u05EA \u05D4\u05E2\u05D9\u05D2\u05D5\u05DC \u05D4\u05D0\u05D3\u05D5\u05DD \u05D5\u05D0\u05EA \u05DB\u05D5\u05EA\u05E8\u05EA \u05D4\u05DC\u05E9\u05D5\u05E0\u05D9\u05EA */\nfunction setAlerts(n) {\n  alertsUnseen = n;\n  const btn = document.getElementById('bell');\n  if (btn) {\n    const dot = btn.querySelector('.bell-dot');\n    dot.hidden = !n;\n    dot.textContent = n > 99 ? '99+' : String(n || '');\n    btn.setAttribute('aria-label', n ? `\u05D4\u05EA\u05E8\u05D0\u05D5\u05EA \u2014 ${n} \u05D7\u05D3\u05E9\u05D5\u05EA` : '\u05D4\u05EA\u05E8\u05D0\u05D5\u05EA');\n  }\n  document.title = (n ? `(${n}) ` : '') + '\u05DE\u05E1\u05D5\u05E3 \u05D4\u05D4\u05E0\u05D4\u05DC\u05D4';\n}\n\n/** \u05D1\u05D5\u05D3\u05E7 \u05D4\u05EA\u05E8\u05D0\u05D5\u05EA \u05D7\u05D3\u05E9\u05D5\u05EA \u05DB\u05DC \u05D3\u05E7\u05D4, \u05DB\u05DC \u05E2\u05D5\u05D3 \u05D4\u05DE\u05E1\u05D5\u05E3 \u05E4\u05EA\u05D5\u05D7 */\nfunction watchAlerts() {\n  const tick = () => api('GET', '/v1/admin/alerts?limit=1').then(r => setAlerts(r.unseen)).catch(() => {});\n  tick();\n  clearInterval(alertTimer);\n  alertTimer = setInterval(() => { if (!document.hidden) tick(); }, 60000);\n  document.addEventListener('visibilitychange', () => { if (!document.hidden) tick(); });\n}\n\n// ------------------------------------------------------------------ \u05E1\u05E7\u05D9\u05E8\u05D4\n\nasync function overview() {\n  const o = await api('GET', '/v1/admin/overview');\n  refreshCount(o.counts.openRequests);\n  setAlerts(o.counts.alertsUnseen || 0);\n  const c = o.counts;\n  const s = settingsCache || (settingsCache = (await api('GET', '/v1/admin/settings')).settings);\n  const pct = c.seats ? Math.round(100 * c.used / c.seats) : 0;\n  const kpi = (href, k, v, sub, bar) => h('a', { class: 'kpi', href }, h('span', { class: 'k' }, k), v, sub ? h('span', { class: 'small muted' }, sub) : null, bar || null);\n  const attention = [\n    ...o.requests.map(r => requestCard(r, { compact: true, after: render })),\n    ...o.full.map(t => h('div', { class: 'req row wrap' }, h('span', { class: 'dot warn', style: { marginTop: 0 } }),\n      h('div', { class: 'grow', style: { minWidth: '220px' } }, h('b', null, t.name || t.email), ` \u2014 \u05DB\u05DC ${t.seats} \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA \u05D1\u05E9\u05D9\u05DE\u05D5\u05E9. \u05E2\u05DE\u05D3\u05D4 \u05D7\u05D3\u05E9\u05D4 \u05E9\u05EA\u05E0\u05E1\u05D4 \u05DC\u05D4\u05EA\u05D7\u05D1\u05E8 \u05EA\u05E7\u05D1\u05DC \u05F4\u05D0\u05D9\u05DF \u05E2\u05D5\u05D3 \u05D4\u05D9\u05EA\u05E8\u05D9\u05DD\u05F4.`),\n      h('a', { class: 'btn sm', href: '#teacher=' + encodeURIComponent(t.email) }, '\u05DC\u05DE\u05D5\u05E8\u05D4'))),\n  ];\n  return [\n    h('div', { class: 'titlebar' }, h('h1', null, '\u05E1\u05E7\u05D9\u05E8\u05D4'), h('span', { class: 'small muted' }, `\u05D4\u05D2\u05D9\u05E9\u05D4 \u05D1\u05EA\u05E9\u05DC\u05D5\u05DD \u05E4\u05EA\u05D5\u05D7\u05D4 \u05E2\u05D3 ${fmtDate(s.accessUntil)} \xB7 \u05E9\u05DC\u05D9\u05D7\u05D4 \u05DC-Gemini: ${s.requireLoginForGemini ? '\u05E8\u05E7 \u05DC\u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD' : '\u05E4\u05EA\u05D5\u05D7\u05D4 \u05DC\u05DB\u05D5\u05DC\u05DD'}`)),\n    h('div', { class: 'kpis' },\n      kpi('#teachers', '\u05DE\u05D5\u05E8\u05D9\u05DD \u05E4\u05E2\u05D9\u05DC\u05D9\u05DD', h('b', { class: 'v' }, String(c.teachers)), c.teachersBlocked ? `\u05D5\u05E2\u05D5\u05D3 ${c.teachersBlocked} \u05DE\u05D5\u05E9\u05D1\u05EA\u05D9\u05DD` : ''),\n      kpi('#teachers', '\u05E2\u05DE\u05D3\u05D5\u05EA \u05DE\u05D7\u05E9\u05D1 \u05D1\u05E9\u05D9\u05DE\u05D5\u05E9', h('span', { class: 'v' }, String(c.used), ' ', h('small', null, `\u05DE\u05EA\u05D5\u05DA ${c.seats}`)), '', h('span', { class: 'bar' }, h('span', { style: { width: pct + '%' } }))),\n      kpi('#students', '\u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD \u05E2\u05DD \u05D2\u05D9\u05E9\u05D4', h('b', { class: 'v' }, String(c.students)), `${c.studentsPaid} \u05D1\u05EA\u05E9\u05DC\u05D5\u05DD \xB7 ${c.studentsManual} \u05D4\u05E2\u05E0\u05E7\u05D4 \u05D9\u05D3\u05E0\u05D9\u05EA`),\n      kpi('#students=payments', '\u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD \u05D4\u05D7\u05D5\u05D3\u05E9', h('b', { class: 'v' }, String(c.paymentsMonth)), c.refundsMonth ? `${c.refundsMonth} \u05D4\u05D5\u05D7\u05D6\u05E8\u05D5` : '')),\n    h('div', { class: 'two' },\n      h('section', { class: 'card' }, h('div', { class: 'row', style: { justifyContent: 'space-between' } }, h('h2', null, '\u05D3\u05D5\u05E8\u05E9 \u05D8\u05D9\u05E4\u05D5\u05DC'), h('a', { href: '#requests', class: 'small', style: { fontWeight: 600 } }, '\u05DB\u05DC \u05D4\u05D1\u05E7\u05E9\u05D5\u05EA')),\n        attention.length ? attention : h('div', { class: 'empty' }, '\u05D0\u05D9\u05DF \u05DB\u05E8\u05D2\u05E2 \u05E9\u05D5\u05DD \u05D3\u05D1\u05E8 \u05E9\u05DE\u05D7\u05DB\u05D4 \u05DC\u05DA.')),\n      recentCard(o.recent)),\n  ];\n}\n\nfunction recentCard(recent) {\n  const body = h('div', null, feed(byTime(recent, 'at', orderOf('overview.recent'))));\n  const toggle = orderToggle('overview.recent', dir => body.replaceChildren(feed(byTime(recent, 'at', dir))));\n  return h('section', { class: 'card' },\n    h('div', { class: 'row wrap', style: { justifyContent: 'space-between' } }, h('h2', null, '\u05E4\u05E2\u05D9\u05DC\u05D5\u05EA \u05D0\u05D7\u05E8\u05D5\u05E0\u05D4'),\n      h('div', { class: 'row wrap', style: { gap: '10px' } }, toggle, h('a', { href: '#log', class: 'small', style: { fontWeight: 600 } }, '\u05D9\u05D5\u05DE\u05DF \u05DE\u05DC\u05D0'))),\n    body);\n}\n\nfunction feed(list) {\n  if (!list.length) return h('div', { class: 'empty' }, '\u05E2\u05D5\u05D3 \u05D0\u05D9\u05DF \u05E4\u05E2\u05D9\u05DC\u05D5\u05EA.');\n  return h('ol', { class: 'feed' }, list.map(l => h('li', null, h('span', { class: 'dot ' + dotFor(l.action) }),\n    h('div', { class: 'stack', style: { gap: '2px' } },\n      h('span', null, ACTIONS[l.action] || l.action, l.target ? ' \u2014 ' : '', l.target ? h('span', { class: l.target.includes('@') ? 'ltr' : '' }, l.target) : null, l.detail ? ` (${l.detail})` : ''),\n      h('span', { class: 'small muted' }, h('time', { datetime: l.at, title: fmtWhen(l.at) }, ago(l.at)), ` \xB7 ${fmtWhen(l.at)} \xB7 ${l.actor === user.email ? '\u05D0\u05EA/\u05D4' : l.actor}`)))));\n}\n\n// ------------------------------------------------------------------ \u05D1\u05E7\u05E9\u05D5\u05EA\n\nfunction requestCard(r, { compact, after } = {}) {\n  const [kindLabel, kindColor] = REQ_KIND[r.kind] || [r.kind, 'gray'];\n  const who = h('div', { style: { fontSize: '16px', lineHeight: '1.5' } }, h('b', null, r.name || r.email), r.school ? ` \xB7 ${r.school}` : '', ' \xB7 ', h('span', { class: 'ltr muted' }, r.email));\n  let what, input = null;\n  if (r.kind === 'seats') {\n    const b = r.before || {};\n    what = h('div', null, '\u05DE\u05D1\u05E7\u05E9/\u05EA ', h('b', null, `${r.amount} \u05E2\u05DE\u05D3\u05D5\u05EA \u05E0\u05D5\u05E1\u05E4\u05D5\u05EA`), b.seats !== undefined ? `. \u05DB\u05E9\u05E0\u05E9\u05DC\u05D7\u05D4: ${b.used} \u05DE\u05EA\u05D5\u05DA ${b.seats} \u05D1\u05E9\u05D9\u05DE\u05D5\u05E9.` : '.');\n    input = h('input', { class: 'field num', type: 'number', min: '1', max: '2000', value: String(r.amount), style: { width: '90px', height: '40px' }, 'aria-label': '\u05DB\u05DE\u05D4 \u05DC\u05D4\u05D5\u05E1\u05D9\u05E3' });\n  } else if (r.kind === 'release') {\n    what = h('div', null, '\u05DE\u05D1\u05E7\u05E9/\u05EA \u05DC\u05E9\u05D7\u05E8\u05E8 \u05D0\u05EA ', h('b', null, `\u05F4${r.stationName}\u05F4`), '. \u05D0\u05D7\u05E8\u05D9 \u05D4\u05E9\u05D7\u05E8\u05D5\u05E8 \u05D4\u05D4\u05D9\u05EA\u05E8 \u05D7\u05D5\u05D6\u05E8 \u05D0\u05DC\u05D9\u05D5/\u05D4.');\n  } else {\n    what = h('div', null, '\u05E0\u05DB\u05E0\u05E1/\u05D4 \u05DB\u05DE\u05D5\u05E8\u05D4, \u05D5\u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05DE\u05D5\u05D2\u05D3\u05E8.', r.computers ? ` \u05DB-${r.computers} \u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05D1\u05DB\u05D9\u05EA\u05D4.` : '', r.phone ? [' \u05D8\u05DC\u05E4\u05D5\u05DF: ', h('span', { class: 'ltr' }, r.phone)] : '');\n    input = h('input', { class: 'field num', type: 'number', min: '0', max: '2000', value: String(r.computers || (settingsCache && settingsCache.defaultSeats) || 30), style: { width: '90px', height: '40px' }, 'aria-label': '\u05DE\u05E1\u05E4\u05E8 \u05E2\u05DE\u05D3\u05D5\u05EA' });\n  }\n  const approveLabel = r.kind === 'seats' ? '\u05D0\u05D9\u05E9\u05D5\u05E8' : r.kind === 'release' ? '\u05E9\u05D7\u05E8\u05D5\u05E8 \u05D4\u05DE\u05D7\u05E9\u05D1' : '\u05D4\u05D2\u05D3\u05E8\u05D4 \u05DB\u05DE\u05D5\u05E8\u05D4';\n  const approve = h('button', { type: 'button', class: 'btn primary grow' }, approveLabel);\n  const decline = h('button', { type: 'button', class: 'btn' }, '\u05D3\u05D7\u05D9\u05D9\u05D4');\n  approve.onclick = () => busy(approve, async () => {\n    const body = { id: r.id, decision: 'approve' };\n    if (r.kind === 'seats') body.amount = +input.value;\n    if (r.kind === 'teacher') body.seats = +input.value;\n    const res = await api('POST', '/v1/admin/request', body);\n    cache.teachers = null;\n    toast(res.result || '\u05D0\u05D5\u05E9\u05E8');\n    if (after) after();\n  });\n  decline.onclick = async () => {\n    const reason = await promptText('\u05DC\u05D3\u05D7\u05D5\u05EA \u05D0\u05EA \u05D4\u05D1\u05E7\u05E9\u05D4?', '\u05E1\u05D9\u05D1\u05D4 (\u05D4\u05DE\u05D5\u05E8\u05D4 \u05E8\u05D5\u05D0\u05D4 \u05D0\u05D5\u05EA\u05D4 \u05D1\u05DC\u05D5\u05D7 \u05E9\u05DC\u05D5; \u05DC\u05D0 \u05D7\u05D5\u05D1\u05D4)');\n    if (reason === null) return;\n    busy(decline, async () => { await api('POST', '/v1/admin/request', { id: r.id, decision: 'decline', reason }); toast('\u05D4\u05D1\u05E7\u05E9\u05D4 \u05E0\u05D3\u05D7\u05EA\u05D4'); if (after) after(); });\n  };\n  const actions = h('div', { class: 'act' },\n    input ? h('label', { class: 'lbl', style: { flexDirection: 'row', alignItems: 'center', gap: '10px' } }, r.kind === 'seats' ? '\u05DB\u05DE\u05D4 \u05DC\u05D4\u05D5\u05E1\u05D9\u05E3' : '\u05DE\u05E1\u05E4\u05E8 \u05E2\u05DE\u05D3\u05D5\u05EA', input) : null,\n    h('div', { class: 'row' }, approve, decline));\n  return h('article', { class: 'req' },\n    h('div', { class: 'row wrap', style: { gap: '8px' } }, h('span', { class: 'pill ' + kindColor }, kindLabel), h('span', { class: 'small muted' }, ago(r.createdAt))),\n    h('div', { class: 'body' }, h('div', { class: 'grow stack', style: { minWidth: '240px', gap: '6px' } }, who, what, r.note ? h('div', { class: 'note' }, `\u05F4${r.note}\u05F4`) : null), compact ? null : actions),\n    compact ? actions : null);\n}\n\nfunction promptText(title, label) {\n  return new Promise(resolve => {\n    const d = h('dialog', { class: 'modal', 'aria-labelledby': 'pt-t' });\n    const f = h('textarea', { class: 'field', rows: '2', id: 'pt-f' });\n    const end = v => { d.close(); d.remove(); resolve(v); };\n    d.append(h('div', { class: 'in' }, h('h2', { id: 'pt-t' }, title), h('label', { class: 'lbl', for: 'pt-f' }, label, f),\n      h('div', { class: 'row' }, h('button', { type: 'button', class: 'btn primary', onclick: () => end(f.value) }, '\u05D0\u05D9\u05E9\u05D5\u05E8'), h('button', { type: 'button', class: 'btn', onclick: () => end(null) }, '\u05D1\u05D9\u05D8\u05D5\u05DC'))));\n    d.addEventListener('cancel', e => { e.preventDefault(); end(null); });\n    document.body.append(d);\n    d.showModal();\n  });\n}\n\nasync function requests(arg) {\n  const done = arg === 'done';\n  if (!settingsCache) settingsCache = (await api('GET', '/v1/admin/settings')).settings;\n  const r = await api('GET', '/v1/admin/requests' + (done ? '?status=done' : ''));\n  if (!done) refreshCount(r.requests.length);\n  const tabs = h('div', { class: 'tabs', role: 'tablist' },\n    h('button', { type: 'button', role: 'tab', 'aria-selected': String(!done), onclick: () => { location.hash = '#requests'; } }, `\u05E4\u05EA\u05D5\u05D7\u05D5\u05EA${!done ? ' \xB7 ' + r.requests.length : ''}`),\n    h('button', { type: 'button', role: 'tab', 'aria-selected': String(done), onclick: () => { location.hash = '#requests=done'; } }, '\u05D8\u05D5\u05E4\u05DC\u05D5'));\n  const doneRow = x => h('tr', null,\n    h('td', null, h('span', { class: 'pill ' + (REQ_KIND[x.kind] || ['', 'gray'])[1] }, (REQ_KIND[x.kind] || [x.kind])[0]), ' ', x.kind === 'seats' ? `${x.amount} \u05E2\u05DE\u05D3\u05D5\u05EA` : x.stationName || ''),\n    h('td', null, h('div', { class: 'cellname' }, h('b', null, x.name || x.email), h('span', { class: 'ltr' }, x.email))),\n    h('td', { class: 'muted' }, fmtDate(x.createdAt)),\n    h('td', null, h('span', { class: 'pill ' + (x.status === 'approved' ? 'green' : 'gray') }, x.status === 'approved' ? '\u05D0\u05D5\u05E9\u05E8\u05D4' : '\u05E0\u05D3\u05D7\u05EA\u05D4'), ' ', h('span', { class: 'small muted' }, x.result || x.reason || '')));\n  const doneTable = () => h('div', { class: 'tablewrap' }, h('table', { class: 't' },\n    h('thead', null, h('tr', null, ['\u05D1\u05E7\u05E9\u05D4', '\u05DE\u05D9', '\u05DE\u05EA\u05D9', '\u05D4\u05D7\u05DC\u05D8\u05D4'].map(x => h('th', null, x)))),\n    h('tbody', null, r.requests.map(doneRow))));\n  const list = done\n    ? (r.requests.length ? doneTable() : h('div', { class: 'empty' }, '\u05E2\u05D5\u05D3 \u05D0\u05D9\u05DF \u05D1\u05E7\u05E9\u05D5\u05EA \u05E9\u05D8\u05D5\u05E4\u05DC\u05D5.'))\n    : (r.requests.length ? r.requests.map(x => requestCard(x, { after: render })) : h('div', { class: 'card' }, h('div', { class: 'empty' }, '\u05D0\u05D9\u05DF \u05D1\u05E7\u05E9\u05D5\u05EA \u05E4\u05EA\u05D5\u05D7\u05D5\u05EA.')));\n  return [h('div', { class: 'stack', style: { gap: '6px' } }, h('h1', null, '\u05D1\u05E7\u05E9\u05D5\u05EA'),\n    h('p', { class: 'muted' }, '\u05DE\u05D5\u05E8\u05D9\u05DD \u05DE\u05D1\u05E7\u05E9\u05D9\u05DD \u05DB\u05D0\u05DF \u05DC\u05E9\u05D7\u05E8\u05E8 \u05DE\u05D7\u05E9\u05D1 \u05E9\u05D4\u05D5\u05D7\u05DC\u05E3, \u05D5\u05D7\u05E9\u05D1\u05D5\u05E0\u05D5\u05EA \u05E9\u05E0\u05DB\u05E0\u05E1\u05D5 \u05DB\u05DE\u05D5\u05E8\u05D4 \u05DE\u05D1\u05E7\u05E9\u05D9\u05DD \u05D4\u05E8\u05E9\u05D0\u05D4. \u05E2\u05DE\u05D3\u05D5\u05EA \u05E0\u05D5\u05E1\u05E4\u05D5\u05EA \u05D4\u05DE\u05D5\u05E8\u05D9\u05DD \u05E7\u05D5\u05E0\u05D9\u05DD \u05D1\u05D7\u05D1\u05D9\u05DC\u05D5\u05EA \u2014 \u05D5\u05DE\u05EA\u05D5\u05D5\u05E1\u05E4\u05D5\u05EA \u05DC\u05D1\u05D3 \u05D0\u05D7\u05E8\u05D9 \u05D4\u05EA\u05E9\u05DC\u05D5\u05DD.')), tabs, list,\n  h('p', { class: 'small muted' }, '\u05DB\u05DC \u05D0\u05D9\u05E9\u05D5\u05E8 \u05D5\u05D3\u05D7\u05D9\u05D9\u05D4 \u05E0\u05E8\u05E9\u05DE\u05D9\u05DD \u05D1\u05D9\u05D5\u05DE\u05DF \u05D4\u05E4\u05E2\u05D5\u05DC\u05D5\u05EA, \u05D5\u05D4\u05DE\u05D5\u05E8\u05D4 \u05E8\u05D5\u05D0\u05D4 \u05D0\u05EA \u05D4\u05EA\u05E9\u05D5\u05D1\u05D4 \u05D1\u05DC\u05D5\u05D7 \u05E9\u05DC\u05D5.')];\n}\n\n// ------------------------------------------------------------------ \u05DE\u05D5\u05E8\u05D9\u05DD \u05D5\u05E2\u05DE\u05D3\u05D5\u05EA\n\nlet teacherFilter = 'all';\n\nasync function teachers(selected) {\n  if (!cache.teachers) cache.teachers = (await api('GET', '/v1/admin/teachers')).teachers;\n  if (!settingsCache) settingsCache = (await api('GET', '/v1/admin/settings')).settings;\n  const all = cache.teachers;\n  const full = t => t.active && t.seats > 0 && t.used >= t.seats;\n  const groups = { all: ['\u05D4\u05DB\u05D5\u05DC', () => true], active: ['\u05E4\u05E2\u05D9\u05DC\u05D9\u05DD', t => t.active], full: ['\u05DB\u05DC \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA \u05D1\u05E9\u05D9\u05DE\u05D5\u05E9', full], blocked: ['\u05DE\u05D5\u05E9\u05D1\u05EA\u05D9\u05DD', t => !t.active] };\n  const shown = byTime(all.filter(groups[teacherFilter][1]), 'lastSignIn', orderOf('teachers.lastSignIn', 'none'));\n  const filters = h('div', { class: 'filters' }, Object.entries(groups).map(([id, [label, fn]]) => h('button', { type: 'button', 'aria-pressed': String(teacherFilter === id), onclick: () => { teacherFilter = id; render(); } }, `${label} \xB7 ${all.filter(fn).length}`)));\n  const table = h('div', { class: 'tablewrap' }, h('table', { class: 't' },\n    h('thead', null, h('tr', null, ['\u05DE\u05D5\u05E8\u05D4', '\u05D1\u05D9\u05EA \u05E1\u05E4\u05E8', '\u05E2\u05DE\u05D3\u05D5\u05EA', '\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05D4\u05D7\u05D5\u05D3\u05E9'].map(x => h('th', null, x)), timeHeader('\u05DB\u05E0\u05D9\u05E1\u05D4 \u05D0\u05D7\u05E8\u05D5\u05E0\u05D4', 'teachers.lastSignIn', render), h('th', null, '\u05DE\u05E6\u05D1'))),\n    h('tbody', null, shown.length ? shown.map(t => {\n      const pct = t.seats ? Math.min(100, Math.round(100 * t.used / t.seats)) : (t.used ? 100 : 0);\n      const tr = h('tr', { class: 'click' + (t.email === selected ? ' sel' : ''), tabindex: '0', onclick: () => { location.hash = '#teacher=' + encodeURIComponent(t.email); }, onkeydown: e => { if (e.key === 'Enter') location.hash = '#teacher=' + encodeURIComponent(t.email); } },\n        h('td', null, h('div', { class: 'cellname' }, h('b', null, t.name || '\u2014'), h('span', { class: 'ltr' }, t.email))),\n        h('td', null, t.school || '\u2014'),\n        h('td', null, h('div', { class: 'seats' }, h('span', null, h('b', null, String(t.used)), ` / ${t.seats}`), h('span', { class: 'bar' + (full(t) ? ' full' : '') }, h('span', { style: { width: pct + '%' } })))),\n        h('td', null, t.sends ? h('span', { class: t.sends.n >= t.sends.limit ? 'pill orange' : '' }, h('b', null, String(t.sends.n)), ` / ${t.sends.limit}`) : '\u2014'),\n        h('td', { class: 'muted', title: fmtWhen(t.lastSignIn) }, t.lastSignIn ? ago(t.lastSignIn) : '\u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E0\u05DB\u05E0\u05E1/\u05D4'),\n        h('td', null, t.active ? h('span', { class: 'pill green' }, '\u05E4\u05E2\u05D9\u05DC') : h('span', { class: 'pill gray' }, '\u05DE\u05D5\u05E9\u05D1\u05EA')));\n      return tr;\n    }) : h('tr', null, h('td', { colspan: '6' }, h('div', { class: 'empty' }, all.length ? '\u05D0\u05D9\u05DF \u05DE\u05D5\u05E8\u05D9\u05DD \u05D1\u05E7\u05D1\u05D5\u05E6\u05D4 \u05D4\u05D6\u05D5.' : '\u05E2\u05D5\u05D3 \u05D0\u05D9\u05DF \u05DE\u05D5\u05E8\u05D9\u05DD \u2014 \u05DE\u05D5\u05E1\u05D9\u05E4\u05D9\u05DD \u05D1\u05DB\u05E4\u05EA\u05D5\u05E8 \"\u05D4\u05D5\u05E1\u05E4\u05EA \u05DE\u05D5\u05E8\u05D4\".'))))));\n  const main = h('div', { class: 'grow stack', style: { gap: '16px' } },\n    h('div', { class: 'titlebar' }, h('h1', null, '\u05DE\u05D5\u05E8\u05D9\u05DD \u05D5\u05E2\u05DE\u05D3\u05D5\u05EA'), h('button', { type: 'button', class: 'btn primary', onclick: addTeacher }, icon(ICONS.plus, 18, '#fff', 2.4), '\u05D4\u05D5\u05E1\u05E4\u05EA \u05DE\u05D5\u05E8\u05D4')),\n    filters, table, h('p', { class: 'small muted' }, '\u05DC\u05D5\u05D7\u05E6\u05D9\u05DD \u05E2\u05DC \u05DE\u05D5\u05E8\u05D4 \u05DB\u05D3\u05D9 \u05DC\u05E9\u05E0\u05D5\u05EA \u05D0\u05EA \u05DE\u05E1\u05E4\u05E8 \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA, \u05DC\u05E8\u05D0\u05D5\u05EA \u05D0\u05EA \u05D4\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05E9\u05DC\u05D5 \u05D5\u05DC\u05E9\u05D7\u05E8\u05E8 \u05E2\u05DE\u05D3\u05D4.'));\n  if (!selected) return main;\n  return h('div', { class: 'split' }, main, await teacherDrawer(selected));\n}\n\n/** \u05DE\u05E0\u05EA \u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05D7\u05D5\u05D3\u05E9\u05D9\u05EA \u05D9\u05D3\u05E0\u05D9\u05EA \u05DC\u05DE\u05D5\u05E8\u05D4: \u05E7\u05D5\u05D1\u05E2\u05D9\u05DD \u05DE\u05E1\u05E4\u05E8, \u05D0\u05D5 \u05D7\u05D5\u05D6\u05E8\u05D9\u05DD \u05DC\u05D1\u05E8\u05D9\u05E8\u05EA \u05D4\u05DE\u05D7\u05D3\u05DC (\u05E2\u05DE\u05D3\u05D5\u05EA \xD7 \u05DE\u05E0\u05D4 \u05DC\u05E2\u05DE\u05D3\u05D4) */\nfunction quotaSection(t) {\n  const per = (settingsCache && settingsCache.monthlyPerSeat) || 0;\n  const auto = per * Math.max(1, t.seats);\n  const manual = t.monthlyLimit !== null && t.monthlyLimit !== undefined;\n  const cur = t.sends ? t.sends.limit : (manual ? t.monthlyLimit : auto);\n  const val = h('input', { class: 'field num', type: 'number', min: '0', max: '100000', value: String(cur), 'aria-label': '\u05DE\u05E0\u05EA \u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05D7\u05D5\u05D3\u05E9\u05D9\u05EA' });\n  const save = h('button', { type: 'button', class: 'btn primary grow' }, '\u05E9\u05DE\u05D9\u05E8\u05D4');\n  const clear = h('button', { type: 'button', class: 'btn' }, '\u05D1\u05E8\u05D9\u05E8\u05EA \u05DE\u05D7\u05D3\u05DC');\n  clear.disabled = !manual;\n  const note = h('div', { class: 'note', style: { background: '#fff', color: 'var(--ink)' } },\n    manual ? `\u05E0\u05E7\u05D1\u05E2 \u05D9\u05D3\u05E0\u05D9\u05EA: ${t.monthlyLimit} \u05D1\u05D7\u05D5\u05D3\u05E9 (\u05D1\u05E8\u05D9\u05E8\u05EA \u05D4\u05DE\u05D7\u05D3\u05DC \u05D4\u05D9\u05D9\u05EA\u05D4 ${auto}).` : `\u05D1\u05E8\u05D9\u05E8\u05EA \u05DE\u05D7\u05D3\u05DC: ${per} \u05DC\u05DB\u05DC \u05E2\u05DE\u05D3\u05D4 \xD7 ${Math.max(1, t.seats)} = ${auto} \u05D1\u05D7\u05D5\u05D3\u05E9.`,\n    t.sends ? ` \u05E0\u05E9\u05DC\u05D7\u05D5 \u05D4\u05D7\u05D5\u05D3\u05E9: ${t.sends.n}.` : '');\n  save.onclick = () => busy(save, async () => {\n    const n = Math.max(0, Math.round(+val.value || 0));\n    await api('POST', '/v1/admin/teacher', { email: t.email, monthlyLimit: n });\n    cache.teachers = null; toast(`\u05DE\u05E0\u05EA \u05D4\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05D4\u05D7\u05D5\u05D3\u05E9\u05D9\u05EA: ${n}`); render();\n  });\n  clear.onclick = () => busy(clear, async () => {\n    await api('POST', '/v1/admin/teacher', { email: t.email, monthlyLimit: null });\n    cache.teachers = null; toast('\u05D7\u05D6\u05E8\u05D4 \u05DC\u05D1\u05E8\u05D9\u05E8\u05EA \u05D4\u05DE\u05D7\u05D3\u05DC'); render();\n  });\n  return h('section', { class: 'stack', style: { background: 'var(--bg)', borderRadius: '14px', padding: '16px', gap: '12px' } },\n    h('div', { class: 'row', style: { justifyContent: 'space-between', alignItems: 'baseline' } }, h('h3', null, '\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05EA\u05E8\u05D2\u05D9\u05DC \u05D1\u05D7\u05D5\u05D3\u05E9'), h('span', { class: 'small muted' }, '\u05D4\u05DE\u05D5\u05E8\u05D4 \u05D5\u05DB\u05DC \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA \u05E9\u05DC\u05D5 \u05D9\u05D7\u05D3')),\n    val, note, h('div', { class: 'row' }, save, clear));\n}\n\nasync function teacherDrawer(email) {\n  const d = await api('GET', '/v1/admin/teacher?email=' + encodeURIComponent(email));\n  const t = d.teacher;\n  const val = h('input', { class: 'field num', type: 'number', min: '0', max: '10000', value: String(t.seats), 'aria-label': '\u05DE\u05E1\u05E4\u05E8 \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA' });\n  const preview = h('div', { class: 'note', style: { background: '#fff', color: 'var(--ink)' } });\n  const save = h('button', { type: 'button', class: 'btn primary grow' }, '\u05E9\u05DE\u05D9\u05E8\u05D4');\n  const reset = h('button', { type: 'button', class: 'btn' }, '\u05D1\u05D9\u05D8\u05D5\u05DC');\n  const update = () => {\n    const n = Math.max(0, Math.round(+val.value || 0));\n    const changed = n !== t.seats;\n    save.disabled = !changed; reset.disabled = !changed;\n    const sends = t.sends ? ` \xB7 \u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05D4\u05D7\u05D5\u05D3\u05E9: ${t.sends.n} \u05DE\u05EA\u05D5\u05DA ${t.sends.limit}` : '';\n    if (!changed) preview.textContent = `${t.used} \u05D1\u05E9\u05D9\u05DE\u05D5\u05E9 \xB7 ${Math.max(0, t.seats - t.used)} \u05E4\u05E0\u05D5\u05D9\u05D5\u05EA${sends}.`;\n    else if (n >= t.used) preview.replaceChildren(`${t.seats} \u2190 `, h('b', null, String(n)), `: \u05D0\u05D7\u05E8\u05D9 \u05D4\u05E9\u05DE\u05D9\u05E8\u05D4 \u05D9\u05D4\u05D9\u05D5 ${n - t.used} \u05E2\u05DE\u05D3\u05D5\u05EA \u05E4\u05E0\u05D5\u05D9\u05D5\u05EA, \u05D5\u05DE\u05E0\u05EA \u05D4\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05D4\u05D7\u05D5\u05D3\u05E9\u05D9\u05EA \u05EA\u05D4\u05D9\u05D4 ${n * (settingsCache.monthlyPerSeat || 0)}. \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA \u05E9\u05DB\u05D1\u05E8 \u05DE\u05D7\u05D5\u05D1\u05E8\u05D5\u05EA \u05DC\u05D0 \u05DE\u05E9\u05EA\u05E0\u05D5\u05EA.`);\n    else preview.replaceChildren(`${t.seats} \u2190 `, h('b', null, String(n)), `: \u05E4\u05D7\u05D5\u05EA \u05DE-${t.used} \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA \u05E9\u05D1\u05E9\u05D9\u05DE\u05D5\u05E9. \u05D0\u05E3 \u05E2\u05DE\u05D3\u05D4 \u05DC\u05D0 \u05EA\u05E0\u05D5\u05EA\u05E7 \u2014 \u05E8\u05E7 \u05E2\u05DE\u05D3\u05D5\u05EA \u05D7\u05D3\u05E9\u05D5\u05EA \u05DC\u05D0 \u05D9\u05D5\u05DB\u05DC\u05D5 \u05DC\u05D4\u05EA\u05D7\u05D1\u05E8 \u05E2\u05D3 \u05E9\u05D9\u05E9\u05EA\u05D7\u05E8\u05E8\u05D5 ${t.used - n}.`);\n  };\n  const step = k => { val.value = String(Math.max(0, (+val.value || 0) + k)); update(); };\n  val.addEventListener('input', update);\n  reset.onclick = () => { val.value = String(t.seats); update(); };\n  save.onclick = async () => {\n    const n = Math.max(0, Math.round(+val.value || 0));\n    if (n < t.used && !await ask({ title: '\u05DE\u05DB\u05E1\u05D4 \u05E7\u05D8\u05E0\u05D4 \u05DE\u05DE\u05D4 \u05E9\u05D1\u05E9\u05D9\u05DE\u05D5\u05E9', text: `\u05DC${t.name || '\u05DE\u05D5\u05E8\u05D4'} \u05DE\u05D7\u05D5\u05D1\u05E8\u05D5\u05EA ${t.used} \u05E2\u05DE\u05D3\u05D5\u05EA. \u05D0\u05E3 \u05D0\u05D7\u05EA \u05DC\u05D0 \u05EA\u05E0\u05D5\u05EA\u05E7; \u05DB\u05D3\u05D9 \u05DC\u05E4\u05E0\u05D5\u05EA \u05DE\u05E7\u05D5\u05DD \u2014 \u05DE\u05E9\u05D7\u05E8\u05E8\u05D9\u05DD \u05E2\u05DE\u05D3\u05D5\u05EA \u05D1\u05E8\u05E9\u05D9\u05DE\u05D4 \u05DC\u05DE\u05D8\u05D4.`, ok: '\u05E9\u05DE\u05D9\u05E8\u05D4 \u05D1\u05DB\u05DC \u05D6\u05D0\u05EA' })) return;\n    busy(save, async () => { await api('POST', '/v1/admin/teacher', { email: t.email, seats: n }); cache.teachers = null; toast(`\u05DE\u05DB\u05E1\u05EA \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA: ${t.seats} \u2190 ${n}`); render(); });\n  };\n  update();\n  const copy = h('button', { type: 'button', class: 'btn sm' }, '\u05D4\u05E2\u05EA\u05E7\u05D4');\n  copy.onclick = async () => { try { await navigator.clipboard.writeText(t.code); toast('\u05D4\u05E7\u05D5\u05D3 \u05D4\u05D5\u05E2\u05EA\u05E7'); } catch (e) { toast(t.code); } };\n  const toggle = h('button', { type: 'button', class: t.active ? 'btn sm danger' : 'btn sm primary' }, t.active ? '\u05D4\u05E9\u05D1\u05EA\u05EA \u05D4\u05DE\u05D5\u05E8\u05D4' : '\u05D4\u05E4\u05E2\u05DC\u05D4 \u05DE\u05D7\u05D3\u05E9');\n  toggle.onclick = async () => {\n    if (t.active && !await ask({ title: `\u05DC\u05D4\u05E9\u05D1\u05D9\u05EA \u05D0\u05EA ${t.name || t.email}?`, text: `\u05D4\u05DC\u05D5\u05D7 \u05E9\u05DC \u05D4\u05DE\u05D5\u05E8\u05D4 \u05E0\u05E1\u05D2\u05E8, \u05D5\u05DB\u05DC \u05DE\u05D7\u05E9\u05D1 \u05E9\u05DC\u05D5 \u05E0\u05E0\u05E2\u05DC \u05D1\u05E4\u05E2\u05DD \u05D4\u05D1\u05D0\u05D4 \u05E9\u05D4\u05D5\u05D0 \u05DE\u05D7\u05D5\u05D1\u05E8 \u05DC\u05D0\u05D9\u05E0\u05D8\u05E8\u05E0\u05D8. \u05D0\u05E4\u05E9\u05E8 \u05DC\u05D4\u05E4\u05E2\u05D9\u05DC \u05DE\u05D7\u05D3\u05E9 \u05D1\u05DB\u05DC \u05E8\u05D2\u05E2.`, ok: '\u05D4\u05E9\u05D1\u05EA\u05D4', danger: true })) return;\n    busy(toggle, async () => { await api('POST', '/v1/admin/teacher', { email: t.email, active: !t.active }); cache.teachers = null; toast(t.active ? '\u05D4\u05DE\u05D5\u05E8\u05D4 \u05D4\u05D5\u05E9\u05D1\u05EA' : '\u05D4\u05DE\u05D5\u05E8\u05D4 \u05D4\u05D5\u05E4\u05E2\u05DC'); render(); });\n  };\n  const edit = h('button', { type: 'button', class: 'btn sm' }, '\u05E2\u05E8\u05D9\u05DB\u05EA \u05E4\u05E8\u05D8\u05D9\u05DD');\n  edit.onclick = () => editTeacher(t);\n  const stationItem = s => {\n    const stale = daysSince(s.lastSeen) > 30;\n    const rel = h('button', { type: 'button', class: 'btn sm' }, '\u05E9\u05D7\u05E8\u05D5\u05E8');\n    rel.onclick = async () => {\n      if (!await ask({ title: `\u05DC\u05E9\u05D7\u05E8\u05E8 \u05D0\u05EA \u05F4${s.name}\u05F4?`, text: `\u05D4\u05D4\u05D9\u05EA\u05E8 \u05D7\u05D5\u05D6\u05E8 \u05DC${t.name || '\u05DE\u05D5\u05E8\u05D4'}. \u05D4\u05DE\u05D7\u05E9\u05D1 \u05D4\u05D6\u05D4 \u05E0\u05E0\u05E2\u05DC \u05D1\u05E4\u05E2\u05DD \u05D4\u05D1\u05D0\u05D4 \u05E9\u05D4\u05D5\u05D0 \u05DE\u05D7\u05D5\u05D1\u05E8 \u05DC\u05D0\u05D9\u05E0\u05D8\u05E8\u05E0\u05D8, \u05D5\u05D9\u05E6\u05D8\u05E8\u05DA \u05D0\u05EA \u05E7\u05D5\u05D3 \u05D4\u05DE\u05D5\u05E8\u05D4 \u05DB\u05D3\u05D9 \u05DC\u05D4\u05EA\u05D7\u05D1\u05E8 \u05E9\u05D5\u05D1.`, ok: '\u05E9\u05D7\u05E8\u05D5\u05E8' })) return;\n      busy(rel, async () => { await api('POST', '/v1/admin/station/release', { id: s.id }); cache.teachers = null; toast('\u05D4\u05E2\u05DE\u05D3\u05D4 \u05E9\u05D5\u05D7\u05E8\u05E8\u05D4'); render(); });\n    };\n    return h('div', { class: 'item' + (stale ? ' stale' : ''), 'data-seen': s.lastSeen || '', 'data-claimed': s.claimedAt || '' }, h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, s.name),\n      h('div', { class: 'small', style: { color: stale ? 'var(--orange)' : 'var(--muted)' } }, stale ? `\u05DC\u05D0 \u05E0\u05E8\u05D0\u05EA\u05D4 ${daysSince(s.lastSeen)} \u05D9\u05D5\u05DD \u2014 \u05D0\u05D5\u05DC\u05D9 \u05D4\u05D5\u05D7\u05DC\u05E4\u05D4` : `\u05D7\u05D5\u05D1\u05E8\u05D4 ${fmtWhen(s.claimedAt)} \xB7 \u05E4\u05E2\u05D9\u05DC\u05D4 ${ago(s.lastSeen)}`, stale ? ` \xB7 \u05D7\u05D5\u05D1\u05E8\u05D4 ${fmtWhen(s.claimedAt)}` : '')), rel);\n  };\n  // \u05D4\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05E9\u05DC \u05D4\u05DE\u05D5\u05E8\u05D4 (\u05D4\u05DE\u05E2\u05D1\u05D3\u05D4): \u05DC\u05E4\u05D9 \u05E4\u05E2\u05D9\u05DC\u05D5\u05EA \u05D0\u05D7\u05E8\u05D5\u05E0\u05D4 \u05D0\u05D5 \u05DC\u05E4\u05D9 \u05DE\u05D5\u05E2\u05D3 \u05D4\u05D7\u05D9\u05D1\u05D5\u05E8, \u05D4\u05D7\u05D3\u05E9 \u05D0\u05D5 \u05D4\u05D9\u05E9\u05DF \u05E7\u05D5\u05D3\u05DD\n  const STFIELD = { lastSeen: '\u05E4\u05E2\u05D9\u05DC\u05D5\u05EA \u05D0\u05D7\u05E8\u05D5\u05E0\u05D4', claimedAt: '\u05DE\u05D5\u05E2\u05D3 \u05D4\u05D7\u05D9\u05D1\u05D5\u05E8' };\n  let stField = STFIELD[ORDER['stations.field']] ? ORDER['stations.field'] : 'lastSeen';\n  const stList = h('div', { class: 'list' });\n  const drawStations = () => stList.replaceChildren(...(d.stations.length ? byTime(d.stations, stField, orderOf('stations')).map(stationItem) : [h('div', { class: 'empty' }, '\u05E2\u05D5\u05D3 \u05D0\u05D9\u05DF \u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD.')]));\n  drawStations();\n  const stFieldSel = h('select', { class: 'field sm', 'aria-label': '\u05DE\u05D9\u05D5\u05DF \u05D4\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05DC\u05E4\u05D9', onchange: e => { stField = e.target.value; setOrder('stations.field', stField); drawStations(); } },\n    Object.entries(STFIELD).map(([v, label]) => h('option', { value: v, selected: v === stField }, label)));\n  const stOrder = d.stations.length > 1 ? h('div', { class: 'row wrap', style: { gap: '8px' } }, stFieldSel, orderToggle('stations', drawStations)) : null;\n  return h('aside', { class: 'drawer card', 'aria-label': '\u05E4\u05E8\u05D8\u05D9 \u05D4\u05DE\u05D5\u05E8\u05D4' },\n    h('div', { class: 'row', style: { alignItems: 'flex-start', justifyContent: 'space-between' } },\n      h('div', { class: 'stack', style: { gap: '2px' } }, h('h2', { style: { fontSize: '21px' } }, t.name || t.email), h('span', { class: 'small muted' }, h('span', { class: 'ltr' }, t.email), t.school ? ' \xB7 ' + t.school : '', t.phone ? [' \xB7 ', h('span', { class: 'ltr' }, t.phone)] : ''),\n        t.active ? null : h('span', { class: 'pill gray', style: { alignSelf: 'flex-start', marginTop: '6px' } }, '\u05DE\u05D5\u05E9\u05D1\u05EA')),\n      h('a', { class: 'btn sm', href: '#teachers', 'aria-label': '\u05E1\u05D2\u05D9\u05E8\u05D4' }, icon(ICONS.x, 16))),\n    h('section', { class: 'stack', style: { background: 'var(--bg)', borderRadius: '14px', padding: '16px', gap: '12px' } },\n      h('div', { class: 'row', style: { justifyContent: 'space-between', alignItems: 'baseline' } }, h('h3', null, '\u05DE\u05DB\u05E1\u05EA \u05E2\u05DE\u05D3\u05D5\u05EA'), h('span', { class: 'small muted' }, `\u05D1\u05E9\u05D9\u05DE\u05D5\u05E9 ${t.used}`)),\n      h('div', { class: 'stepper' }, h('button', { type: 'button', class: 'btn', 'aria-label': '\u05E4\u05D7\u05D5\u05EA \u05E2\u05DE\u05D3\u05D4', onclick: () => step(-1) }, icon(ICONS.minus, 18, '#17213A', 2.4)), val,\n        h('button', { type: 'button', class: 'btn', 'aria-label': '\u05E2\u05D5\u05D3 \u05E2\u05DE\u05D3\u05D4', onclick: () => step(1) }, icon(ICONS.plus, 18, '#17213A', 2.4))),\n      h('div', { class: 'row' }, [5, 10, 20].map(k => h('button', { type: 'button', class: 'btn sm grow', onclick: () => step(k) }, '\u200E+' + k))),\n      preview, h('div', { class: 'row' }, save, reset)),\n    quotaSection(t),\n    h('section', { class: 'row wrap' }, h('div', { class: 'grow stack', style: { gap: '2px' } }, h('span', { class: 'small muted', style: { fontWeight: 600 } }, '\u05E7\u05D5\u05D3 \u05D4\u05DE\u05D5\u05E8\u05D4'), h('span', { class: 'mono', style: { fontSize: '20px', textAlign: 'right' } }, t.code)), copy),\n    h('section', { class: 'stack' }, h('h3', null, `\u05D4\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05E9\u05DC ${t.name || '\u05D4\u05DE\u05D5\u05E8\u05D4'} (${d.stations.length})`), stOrder, stList),\n    t.note ? h('div', { class: 'note' }, t.note) : null,\n    h('section', { class: 'row wrap', style: { borderTop: '1px solid var(--line)', paddingTop: '14px' } }, edit, h('div', { class: 'grow' }), toggle));\n}\n\nfunction teacherForm(title, t, onSave) {\n  const d = h('dialog', { class: 'modal', 'aria-labelledby': 'tf-t' });\n  const email = h('input', { class: 'field ltr', type: 'email', id: 'tf-e', placeholder: 'name@gmail.com', value: t ? t.email : '', disabled: !!t });\n  const name = h('input', { class: 'field', type: 'text', id: 'tf-n', value: t ? t.name : '' });\n  const school = h('input', { class: 'field', type: 'text', id: 'tf-s', value: t ? t.school : '' });\n  const seats = h('input', { class: 'field', type: 'number', min: '0', max: '10000', id: 'tf-c', value: String(t ? t.seats : settingsCache.defaultSeats) });\n  const phone = h('input', { class: 'field ltr', type: 'tel', id: 'tf-p', value: t ? t.phone || '' : '', placeholder: '050-1234567', style: { textAlign: 'left' } });\n  const note = h('input', { class: 'field', type: 'text', id: 'tf-o', value: t ? t.note : '', placeholder: '\u05DC\u05DE\u05E9\u05DC: \u05D4\u05E1\u05DB\u05DD \u05E2\u05D3 \u05E1\u05D5\u05E3 \u05D4\u05E9\u05E0\u05D4' });\n  const ok = h('button', { type: 'button', class: 'btn primary' }, t ? '\u05E9\u05DE\u05D9\u05E8\u05D4' : '\u05D4\u05D5\u05E1\u05E4\u05D4');\n  const close = () => { d.close(); d.remove(); };\n  ok.onclick = () => busy(ok, async () => { await onSave({ email: email.value.trim(), name: name.value, school: school.value, phone: phone.value, seats: +seats.value, note: note.value }); close(); });\n  d.append(h('div', { class: 'in' }, h('h2', { id: 'tf-t' }, title),\n    t ? null : h('p', { class: 'small muted' }, '\u05D7\u05E9\u05D1\u05D5\u05DF Google \u05E9\u05DC \u05D4\u05DE\u05D5\u05E8\u05D4. \u05D0\u05D7\u05E8\u05D9 \u05D4\u05D4\u05D5\u05E1\u05E4\u05D4 \u05D4\u05DE\u05D5\u05E8\u05D4 \u05E0\u05DB\u05E0\u05E1/\u05EA \u05DE\u05EA\u05D5\u05DA DSViz (\"\u05DB\u05E0\u05D9\u05E1\u05EA \u05DE\u05D5\u05E8\u05D4\") \u05D5\u05E8\u05D5\u05D0\u05D4 \u05D0\u05EA \u05D4\u05E7\u05D5\u05D3 \u05E9\u05DC\u05D5.'),\n    h('label', { class: 'lbl', for: 'tf-e' }, '\u05D7\u05E9\u05D1\u05D5\u05DF Google', email), h('label', { class: 'lbl', for: 'tf-n' }, '\u05E9\u05DD', name),\n    h('label', { class: 'lbl', for: 'tf-s' }, '\u05D1\u05D9\u05EA \u05E1\u05E4\u05E8', school), t ? null : h('label', { class: 'lbl', for: 'tf-c' }, '\u05DE\u05E1\u05E4\u05E8 \u05E2\u05DE\u05D3\u05D5\u05EA', seats),\n    h('label', { class: 'lbl', for: 'tf-p' }, '\u05D8\u05DC\u05E4\u05D5\u05DF \u05E0\u05D9\u05D9\u05D3', phone),\n    h('label', { class: 'lbl', for: 'tf-o' }, '\u05D4\u05E2\u05E8\u05D4 (\u05E8\u05E7 \u05DC\u05D4\u05E0\u05D4\u05DC\u05D4)', note),\n    h('div', { class: 'row' }, ok, h('button', { type: 'button', class: 'btn', onclick: close }, '\u05D1\u05D9\u05D8\u05D5\u05DC'))));\n  d.addEventListener('cancel', e => { e.preventDefault(); close(); });\n  document.body.append(d);\n  d.showModal();\n  (t ? name : email).focus();\n}\n\nfunction addTeacher() {\n  teacherForm('\u05D4\u05D5\u05E1\u05E4\u05EA \u05DE\u05D5\u05E8\u05D4', null, async v => {\n    await api('POST', '/v1/admin/teacher', Object.assign({ create: true }, v));\n    cache.teachers = null;\n    toast('\u05D4\u05DE\u05D5\u05E8\u05D4 \u05E0\u05D5\u05E1\u05E3/\u05D4');\n    location.hash = '#teacher=' + encodeURIComponent(v.email.toLowerCase());\n    render();\n  });\n}\nfunction editTeacher(t) {\n  teacherForm('\u05E2\u05E8\u05D9\u05DB\u05EA \u05E4\u05E8\u05D8\u05D9\u05DD', t, async v => {\n    await api('POST', '/v1/admin/teacher', { email: t.email, name: v.name, school: v.school, phone: v.phone, note: v.note });\n    cache.teachers = null;\n    toast('\u05E0\u05E9\u05DE\u05E8');\n    render();\n  });\n}\n\n// ------------------------------------------------------------------ \u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD \u05D5\u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD\n\nlet studentFilter = 'open';\n\nasync function students(arg) {\n  if (arg === 'payments') return payments();\n  const r = await api('GET', '/v1/admin/students');\n  cache.students = r.students;\n  const all = r.students;\n  const groups = { open: ['\u05E2\u05DD \u05D2\u05D9\u05E9\u05D4', s => s.open], manual: ['\u05D4\u05E2\u05E0\u05E7\u05D4 \u05D9\u05D3\u05E0\u05D9\u05EA', s => s.open && s.source === 'manual'], unpaid: ['\u05E0\u05DB\u05E0\u05E1\u05D5 \u05D5\u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E9\u05D9\u05DC\u05DE\u05D5', s => !s.open], all: ['\u05D4\u05DB\u05D5\u05DC', () => true] };\n  const shown = byTime(all.filter(groups[studentFilter][1]), 'lastSignIn', orderOf('students.lastSignIn', 'none'));\n  const sourcePill = s => s.open ? (s.source === 'manual' ? h('span', { class: 'pill purple' }, '\u05D4\u05E2\u05E0\u05E7\u05D4 \u05D9\u05D3\u05E0\u05D9\u05EA') : h('span', { class: 'pill green' }, '\u05EA\u05E9\u05DC\u05D5\u05DD' + (s.paidAt ? ' \xB7 ' + fmtDate(s.paidAt) : '')))\n    : s.source === 'refunded' ? h('span', { class: 'pill gray' }, '\u05D4\u05D5\u05D7\u05D6\u05E8') : s.source === 'revoked' ? h('span', { class: 'pill gray' }, '\u05D1\u05D5\u05D8\u05DC') : h('span', { class: 'pill gray' }, '\u05DC\u05D0 \u05E9\u05D9\u05DC\u05DD/\u05D4');\n  const table = h('div', { class: 'tablewrap' }, h('table', { class: 't' },\n    h('thead', null, h('tr', null, ['\u05EA\u05DC\u05DE\u05D9\u05D3', '\u05D8\u05DC\u05E4\u05D5\u05DF', '\u05D2\u05D9\u05E9\u05D4 \u05E2\u05D3', '\u05D0\u05D9\u05DA', '\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05D4\u05D7\u05D5\u05D3\u05E9'].map(x => h('th', null, x)), timeHeader('\u05DB\u05E0\u05D9\u05E1\u05D4 \u05D0\u05D7\u05E8\u05D5\u05E0\u05D4', 'students.lastSignIn', render), h('th', null, ''))),\n    h('tbody', null, shown.length ? shown.slice(0, 400).map(s => h('tr', null,\n      h('td', null, h('div', { class: 'cellname' }, h('b', null, s.name || '\u2014'), h('span', { class: 'ltr' }, s.email))),\n      h('td', { class: 'ltr muted' }, s.phone || '\u2014'),\n      h('td', null, s.open ? fmtDate(s.until) : '\u2014'), h('td', null, sourcePill(s)),\n      h('td', null, s.sends ? h('span', { class: s.sends.n >= s.sends.limit ? 'pill orange' : '' }, h('b', null, String(s.sends.n)), ` / ${s.sends.limit}`) : '\u2014'),\n      h('td', { class: 'muted', title: fmtWhen(s.lastSignIn) }, s.lastSignIn ? ago(s.lastSignIn) : '\u2014'),\n      h('td', null, h('button', { type: 'button', class: 'btn sm', onclick: () => studentDialog(s.email) }, '\u05E4\u05E8\u05D8\u05D9\u05DD'))))\n      : h('tr', null, h('td', { colspan: '7' }, h('div', { class: 'empty' }, '\u05D0\u05D9\u05DF \u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD \u05D1\u05E7\u05D1\u05D5\u05E6\u05D4 \u05D4\u05D6\u05D5.'))))));\n  if (arg && arg.includes('@')) setTimeout(() => studentDialog(arg), 0);\n  return h('div', { class: 'split' },\n    h('div', { class: 'grow stack', style: { gap: '16px' } },\n      h('h1', null, '\u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD \u05D5\u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD'),\n      h('div', { class: 'tabs', role: 'tablist' }, h('button', { type: 'button', role: 'tab', 'aria-selected': 'true' }, `\u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD \xB7 ${all.filter(s => s.open).length}`),\n        h('button', { type: 'button', role: 'tab', 'aria-selected': 'false', onclick: () => { location.hash = '#students=payments'; } }, '\u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD')),\n      h('div', { class: 'filters' }, Object.entries(groups).map(([id, [label, fn]]) => h('button', { type: 'button', 'aria-pressed': String(studentFilter === id), onclick: () => { studentFilter = id; render(); } }, `${label} \xB7 ${all.filter(fn).length}`))),\n      table, shown.length > 400 ? h('p', { class: 'small muted' }, `\u05DE\u05D5\u05E6\u05D2\u05D9\u05DD 400 \u05DE\u05EA\u05D5\u05DA ${shown.length} \u2014 \u05DE\u05E9\u05EA\u05DE\u05E9\u05D9\u05DD \u05D1\u05D7\u05D9\u05E4\u05D5\u05E9 \u05DC\u05DE\u05E2\u05DC\u05D4.`) : null),\n    grantPanel());\n}\n\nfunction grantPanel() {\n  const emails = h('textarea', { class: 'field ltr', rows: '3', id: 'g-e', placeholder: 'name@gmail.com', style: { textAlign: 'left' } });\n  const until = h('input', { class: 'field', type: 'date', id: 'g-u', value: (settingsCache && settingsCache.accessUntil) || '' });\n  const note = h('input', { class: 'field', type: 'text', id: 'g-n', placeholder: '\u05DC\u05DE\u05E9\u05DC: \u05D4\u05E1\u05DB\u05DD \u05E2\u05DD \u05EA\u05D9\u05DB\u05D5\u05DF \u05D4\u05D3\u05E8' });\n  const go = h('button', { type: 'button', class: 'btn primary lg' }, '\u05D4\u05E2\u05E0\u05E7\u05EA \u05D2\u05D9\u05E9\u05D4');\n  go.onclick = () => busy(go, async () => {\n    const list = emails.value.split(/[\\s,;]+/).filter(Boolean);\n    if (!list.length) { emails.focus(); throw new Error('\u05DB\u05EA\u05D1\u05D5 \u05DC\u05E4\u05D7\u05D5\u05EA \u05DB\u05EA\u05D5\u05D1\u05EA \u05D0\u05D7\u05EA.'); }\n    const r = await api('POST', '/v1/admin/student', { emails: list, until: until.value, note: note.value });\n    toast(r.count === 1 ? '\u05D4\u05D2\u05D9\u05E9\u05D4 \u05E0\u05D9\u05EA\u05E0\u05D4' : `\u05D4\u05D2\u05D9\u05E9\u05D4 \u05E0\u05D9\u05EA\u05E0\u05D4 \u05DC-${r.count} \u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD`);\n    render();\n  });\n  if (!settingsCache) api('GET', '/v1/admin/settings').then(r => { settingsCache = r.settings; until.value = settingsCache.accessUntil; }).catch(() => {});\n  return h('aside', { class: 'drawer card', 'aria-label': '\u05D4\u05E2\u05E0\u05E7\u05EA \u05D2\u05D9\u05E9\u05D4' }, h('h2', null, '\u05D4\u05E2\u05E0\u05E7\u05EA \u05D2\u05D9\u05E9\u05D4 \u05D1\u05DC\u05D9 \u05EA\u05E9\u05DC\u05D5\u05DD'),\n    h('p', { class: 'small muted', style: { lineHeight: '1.55' } }, '\u05DC\u05EA\u05DC\u05DE\u05D9\u05D3 \u05E9\u05E7\u05D9\u05D1\u05DC \u05D4\u05E0\u05D7\u05D4 \u05DE\u05DC\u05D0\u05D4, \u05DC\u05D4\u05E1\u05DB\u05DD \u05E2\u05DD \u05D1\u05D9\u05EA \u05E1\u05E4\u05E8, \u05D0\u05D5 \u05DB\u05E9\u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05E2\u05D1\u05E8 \u05D1\u05D3\u05E8\u05DA \u05D0\u05D7\u05E8\u05EA. \u05D4\u05EA\u05DC\u05DE\u05D9\u05D3 \u05E0\u05DB\u05E0\u05E1 \u05E2\u05DD \u05D0\u05D5\u05EA\u05D5 \u05D7\u05E9\u05D1\u05D5\u05DF Google \u2014 \u05D5\u05D4\u05DE\u05D0\u05D2\u05E8 \u05E4\u05EA\u05D5\u05D7.'),\n    h('label', { class: 'lbl', for: 'g-e' }, '\u05D7\u05E9\u05D1\u05D5\u05E0\u05D5\u05EA Google (\u05DB\u05EA\u05D5\u05D1\u05EA \u05D1\u05DB\u05DC \u05E9\u05D5\u05E8\u05D4)', emails), h('label', { class: 'lbl', for: 'g-u' }, '\u05E4\u05EA\u05D5\u05D7 \u05E2\u05D3', until),\n    h('label', { class: 'lbl', for: 'g-n' }, '\u05E1\u05D9\u05D1\u05D4 (\u05E0\u05E9\u05DE\u05E8\u05EA \u05D1\u05D9\u05D5\u05DE\u05DF)', note), go);\n}\n\nasync function studentDialog(email) {\n  const r = await api('GET', '/v1/admin/student?email=' + encodeURIComponent(email));\n  const d = h('dialog', { class: 'modal', 'aria-labelledby': 'sd-t', style: { width: 'min(560px, calc(100vw - 32px))' } });\n  const close = () => { d.close(); d.remove(); };\n  const s = r.student || {};\n  const open = s.until && s.until >= new Date().toISOString().slice(0, 10);\n  const extend = h('button', { type: 'button', class: 'btn primary' }, open ? '\u05D4\u05D0\u05E8\u05DB\u05D4' : '\u05D4\u05E2\u05E0\u05E7\u05EA \u05D2\u05D9\u05E9\u05D4');\n  extend.onclick = () => busy(extend, async () => {\n    await api('POST', '/v1/admin/student', { email, until: settingsCache ? settingsCache.accessUntil : s.until, note: '\u05DE\u05EA\u05D5\u05DA \u05E4\u05E8\u05D8\u05D9 \u05D4\u05EA\u05DC\u05DE\u05D9\u05D3' });\n    toast('\u05D4\u05D2\u05D9\u05E9\u05D4 \u05E2\u05D5\u05D3\u05DB\u05E0\u05D4'); close(); render();\n  });\n  const revoke = h('button', { type: 'button', class: 'btn danger' }, '\u05D1\u05D9\u05D8\u05D5\u05DC \u05D4\u05D2\u05D9\u05E9\u05D4');\n  revoke.onclick = async () => {\n    if (!await ask({ title: '\u05DC\u05D1\u05D8\u05DC \u05D0\u05EA \u05D4\u05D2\u05D9\u05E9\u05D4?', text: '\u05D4\u05DE\u05D0\u05D2\u05E8 \u05E0\u05E0\u05E2\u05DC \u05D0\u05E6\u05DC \u05D4\u05EA\u05DC\u05DE\u05D9\u05D3 \u05D1\u05D1\u05D3\u05D9\u05E7\u05D4 \u05D4\u05D1\u05D0\u05D4. \u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD \u05DC\u05D0 \u05DE\u05D5\u05D7\u05D6\u05E8\u05D9\u05DD \u05DE\u05DB\u05D0\u05DF \u2014 \u05D4\u05D7\u05D6\u05E8 \u05E2\u05D5\u05E9\u05D9\u05DD \u05D1\u05E9\u05D9\u05E8\u05D5\u05EA \u05D4\u05EA\u05E9\u05DC\u05D5\u05DD.', ok: '\u05D1\u05D9\u05D8\u05D5\u05DC \u05D4\u05D2\u05D9\u05E9\u05D4', danger: true })) return;\n    busy(revoke, async () => { await api('POST', '/v1/admin/student', { email, mode: 'revoke' }); toast('\u05D4\u05D2\u05D9\u05E9\u05D4 \u05D1\u05D5\u05D8\u05DC\u05D4'); close(); render(); });\n  };\n  const devList = h('div', { class: 'list' });\n  function drawDevices() {\n    devList.replaceChildren(...(r.devices.length ? byTime(r.devices, 'lastSeen', orderOf('devices')).map(x => {\n      const b = h('button', { type: 'button', class: 'btn sm' }, '\u05E0\u05D9\u05EA\u05D5\u05E7');\n      b.onclick = () => busy(b, async () => { await api('POST', '/v1/admin/session/revoke', { id: x.id }); r.devices = r.devices.filter(y => y.id !== x.id); drawDevices(); toast('\u05D4\u05DE\u05D7\u05E9\u05D1 \u05E0\u05D5\u05EA\u05E7'); });\n      return h('div', { class: 'item' }, h('div', { class: 'grow' }, h('div', { style: { fontWeight: 600 } }, x.name || '\u05DE\u05D7\u05E9\u05D1'),\n        h('div', { class: 'small muted' }, '\u05E4\u05E2\u05D9\u05DC ' + ago(x.lastSeen), x.lastSeen ? ` \xB7 ${fmtWhen(x.lastSeen)}` : '', x.createdAt ? ` \xB7 \u05D7\u05D5\u05D1\u05E8 ${fmtWhen(x.createdAt)}` : '')), b);\n    }) : [h('div', { class: 'empty' }, '\u05D0\u05D9\u05DF \u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD.')]));\n  }\n  drawDevices();\n  d.append(h('div', { class: 'in' },\n    h('div', { class: 'row', style: { justifyContent: 'space-between' } }, h('h2', { id: 'sd-t' }, s.name || email), h('button', { type: 'button', class: 'btn sm', 'aria-label': '\u05E1\u05D2\u05D9\u05E8\u05D4', onclick: close }, icon(ICONS.x, 16))),\n    h('div', { class: 'small muted ltr', style: { textAlign: 'right' } }, email),\n    h('div', { class: 'row' }, open ? h('span', { class: 'pill green' }, `\u05E4\u05EA\u05D5\u05D7 \u05E2\u05D3 ${fmtDate(s.until)}`) : h('span', { class: 'pill gray' }, '\u05E1\u05D2\u05D5\u05E8'), s.source === 'manual' ? h('span', { class: 'pill purple' }, '\u05D4\u05E2\u05E0\u05E7\u05D4 \u05D9\u05D3\u05E0\u05D9\u05EA') : null),\n    s.phone ? h('div', { class: 'small muted' }, '\u05D8\u05DC\u05E4\u05D5\u05DF: ', h('span', { class: 'ltr' }, s.phone)) : null,\n    r.sends ? h('div', null, '\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05EA\u05E8\u05D2\u05D9\u05DC \u05D4\u05D7\u05D5\u05D3\u05E9: ', h('b', null, String(r.sends.n)), ` \u05DE\u05EA\u05D5\u05DA ${r.sends.limit}`) : null,\n    s.note ? h('div', { class: 'note' }, s.note) : null,\n    h('h3', null, '\u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD'),\n    h('div', { class: 'list' }, r.payments.length ? r.payments.map(p => h('div', { class: 'item' }, h('div', { class: 'grow' }, `${p.amount} \u20AA \xB7 ${fmtDate(p.paidAt || p.createdAt)}`, p.payerName ? ` \xB7 ${p.payerName}` : '', p.payerPhone ? h('span', { class: 'ltr' }, ' \xB7 ' + p.payerPhone) : null, p.ref ? h('span', { class: 'small muted ltr' }, ' ' + p.ref) : null),\n      h('span', { class: 'pill ' + (p.status === 'paid' ? 'green' : 'gray') }, ({ paid: '\u05E9\u05D5\u05DC\u05DD', refunded: '\u05D4\u05D5\u05D7\u05D6\u05E8', pending: '\u05DC\u05D0 \u05D4\u05D5\u05E9\u05DC\u05DD', failed: '\u05E0\u05DB\u05E9\u05DC', mismatch: '\u05E1\u05DB\u05D5\u05DD \u05DC\u05D0 \u05EA\u05D5\u05D0\u05DD' })[p.status] || p.status)))\n      : h('div', { class: 'empty' }, '\u05D0\u05D9\u05DF \u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD.')),\n    h('div', { class: 'row wrap', style: { justifyContent: 'space-between' } }, h('h3', null, '\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD'), r.devices.length > 1 ? orderToggle('devices', drawDevices) : null),\n    devList,\n    h('div', { class: 'row' }, extend, open ? revoke : null)));\n  d.addEventListener('cancel', e => { e.preventDefault(); close(); });\n  document.body.append(d);\n  d.showModal();\n}\n\nasync function payments() {\n  const r = await api('GET', '/v1/admin/payments');\n  const paid = r.payments.filter(p => p.status === 'paid');\n  const total = paid.reduce((n, p) => n + (+p.amount || 0), 0);\n  const label = { paid: ['\u05E9\u05D5\u05DC\u05DD', 'green'], refunded: ['\u05D4\u05D5\u05D7\u05D6\u05E8', 'gray'], pending: ['\u05DE\u05DE\u05EA\u05D9\u05DF', 'orange'], failed: ['\u05E0\u05DB\u05E9\u05DC', 'gray'], mismatch: ['\u05E1\u05DB\u05D5\u05DD \u05DC\u05D0 \u05EA\u05D5\u05D0\u05DD', 'orange'] };\n  return h('div', { class: 'stack', style: { gap: '16px' } },\n    h('div', { class: 'titlebar' }, h('h1', null, '\u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD \u05D5\u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD'), h('span', { class: 'small muted' }, `${paid.length} \u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD \xB7 ${total.toLocaleString('he-IL')} \u20AA (\u05D1\u05E8\u05E9\u05D9\u05DE\u05D4 \u05DC\u05DE\u05D8\u05D4)`)),\n    h('div', { class: 'tabs', role: 'tablist' }, h('button', { type: 'button', role: 'tab', 'aria-selected': 'false', onclick: () => { location.hash = '#students'; } }, '\u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD'),\n      h('button', { type: 'button', role: 'tab', 'aria-selected': 'true' }, `\u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD \xB7 ${r.payments.length}`)),\n    h('div', { class: 'tablewrap' }, h('table', { class: 't', style: { minWidth: '900px' } },\n      h('thead', null, h('tr', null, timeHeader('\u05EA\u05D0\u05E8\u05D9\u05DA', 'payments.date', render), ['\u05DE\u05D4 \u05E0\u05E7\u05E0\u05D4', '\u05D7\u05E9\u05D1\u05D5\u05DF', '\u05D4\u05DE\u05E9\u05DC\u05DD', '\u05D8\u05DC\u05E4\u05D5\u05DF', '\u05E1\u05DB\u05D5\u05DD', '\u05DE\u05E6\u05D1', '\u05D0\u05E1\u05DE\u05DB\u05EA\u05D4'].map(x => h('th', null, x)))),\n      h('tbody', null, r.payments.length ? byTime(r.payments.map(p => ({ ...p, when: p.paidAt || p.createdAt })), 'when', orderOf('payments.date', 'none')).map(p => h('tr', null,\n        h('td', null, fmtDate(p.paidAt || p.createdAt)),\n        h('td', null, p.kind === 'seats' ? h('span', { class: 'pill blue' }, `\u05D7\u05D1\u05D9\u05DC\u05EA ${p.seats} \u05E2\u05DE\u05D3\u05D5\u05EA`) : h('span', { class: 'pill gray' }, '\u05DE\u05D0\u05D2\u05E8 \u05DC\u05EA\u05DC\u05DE\u05D9\u05D3')),\n        h('td', null, h('div', { class: 'cellname' }, h('b', null, p.name || '\u2014'), h('span', { class: 'ltr' }, p.email))),\n        h('td', null, h('div', { class: 'cellname' }, h('b', null, p.payerName || '\u2014'), p.payerEmail && p.payerEmail !== p.email ? h('span', { class: 'ltr' }, p.payerEmail) : null)),\n        h('td', { class: 'ltr' }, p.payerPhone ? h('a', { href: 'tel:' + p.payerPhone.replace(/[^0-9+]/g, '') }, p.payerPhone) : '\u2014'),\n        h('td', null, `${p.amount} \u20AA`), h('td', null, h('span', { class: 'pill ' + (label[p.status] || ['', 'gray'])[1] }, (label[p.status] || [p.status])[0])),\n        h('td', { class: 'small muted ltr' }, p.ref || '')))\n        : h('tr', null, h('td', { colspan: '8' }, h('div', { class: 'empty' }, '\u05E2\u05D5\u05D3 \u05D0\u05D9\u05DF \u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD.')))))));\n}\n\n// ------------------------------------------------------------------ \u05D9\u05D5\u05DE\u05DF\n\nasync function logView() {\n  const PAGE = 150;\n  const box = h('section', { class: 'card' });\n  const more = h('button', { type: 'button', class: 'btn', hidden: true }, '\u05E2\u05D5\u05D3');\n  let last = '';\n  let dir = orderOf('log');\n  const url = () => `/v1/admin/log?limit=${PAGE}&order=${dir}` + (last ? `&${dir === 'asc' ? 'after' : 'before'}=${encodeURIComponent(last)}` : '');\n  const load = async reset => {\n    if (reset) last = '';\n    const n = await api('GET', url());\n    if (reset) box.replaceChildren(feed(n.log));\n    else if (n.log.length) box.append(feed(n.log));\n    if (n.log.length) last = n.log[n.log.length - 1].at;\n    more.hidden = n.log.length < PAGE;\n  };\n  await load(true);\n  const toggle = orderToggle('log', async d => { dir = d; box.replaceChildren(loadingView()); try { await load(true); } catch (e) { box.replaceChildren(h('div', { class: 'warn-box' }, e.message || String(e))); } });\n  more.onclick = () => busy(more, () => load(false));\n  return [h('div', { class: 'titlebar' }, h('h1', null, '\u05D9\u05D5\u05DE\u05DF \u05E4\u05E2\u05D5\u05DC\u05D5\u05EA'), toggle),\n    h('p', { class: 'muted' }, '\u05DB\u05DC \u05DE\u05D4 \u05E9\u05E7\u05E8\u05D4 \u05D1\u05DE\u05E2\u05E8\u05DB\u05EA: \u05DB\u05E0\u05D9\u05E1\u05D5\u05EA, \u05E2\u05DE\u05D3\u05D5\u05EA, \u05D1\u05E7\u05E9\u05D5\u05EA, \u05EA\u05E9\u05DC\u05D5\u05DE\u05D9\u05DD, \u05D5\u05DE\u05D4 \u05E9\u05E2\u05E9\u05D5 \u05D4\u05DE\u05E0\u05D4\u05DC\u05D9\u05DD.'), box, more];\n}\n\n// ------------------------------------------------------------------ \u05D4\u05D2\u05D3\u05E8\u05D5\u05EA\n\nasync function settingsView() {\n  const r = await api('GET', '/v1/admin/settings');\n  settingsCache = r.settings;\n  const s = r.settings;\n  const num = (id, label, v, min, max) => {\n    const f = h('input', { class: 'field', type: 'number', id: 's-' + id, value: String(v), min: String(min), max: String(max) });\n    f.dataset.key = id;\n    return h('label', { class: 'lbl', for: 's-' + id }, label, f);\n  };\n  const gem = h('input', { type: 'checkbox', role: 'switch', id: 's-gem', checked: s.requireLoginForGemini });\n  const gemLabel = h('span', null, s.requireLoginForGemini ? '\u05D3\u05D5\u05DC\u05E7' : '\u05DB\u05D1\u05D5\u05D9');\n  gem.addEventListener('change', () => { gemLabel.textContent = gem.checked ? '\u05D3\u05D5\u05DC\u05E7' : '\u05DB\u05D1\u05D5\u05D9'; });\n  const until = h('input', { class: 'field', type: 'date', id: 's-until', value: s.accessUntil });\n  const mailOn = h('input', { type: 'checkbox', role: 'switch', id: 's-mail', checked: s.alertEmail !== false });\n  const mailLabel = h('span', null, s.alertEmail !== false ? '\u05D3\u05D5\u05DC\u05E7' : '\u05DB\u05D1\u05D5\u05D9');\n  mailOn.addEventListener('change', () => { mailLabel.textContent = mailOn.checked ? '\u05D3\u05D5\u05DC\u05E7' : '\u05DB\u05D1\u05D5\u05D9'; });\n  const am = r.alertMail || {};\n  const form = h('div', { class: 'stack', style: { gap: '18px', maxWidth: '900px' } },\n    h('section', { class: 'card' },\n      h('div', { class: 'row', style: { alignItems: 'flex-start', gap: '16px' } },\n        h('div', { class: 'grow stack', style: { gap: '6px' } }, h('h2', null, '\u05E9\u05DC\u05D9\u05D7\u05EA \u05EA\u05E8\u05D2\u05D9\u05DC \u05DC-Gemini \u2014 \u05E8\u05E7 \u05DC\u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD'),\n          h('p', { class: 'muted', style: { lineHeight: '1.55' } }, '\u05DB\u05E9\u05D4\u05DE\u05EA\u05D2 \u05D3\u05D5\u05DC\u05E7, \u05E8\u05E7 \u05DE\u05D5\u05E8\u05D4 \u05D5\u05EA\u05DC\u05DE\u05D9\u05D3 \u05E9\u05E9\u05D9\u05DC\u05DD (\u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD \u05E2\u05DD Google) \u05D9\u05DB\u05D5\u05DC\u05D9\u05DD \u05DC\u05E9\u05DC\u05D5\u05D7 \u05EA\u05E8\u05D2\u05D9\u05DC \u05D5\u05DC\u05E7\u05D1\u05DC \u05D4\u05E6\u05E2\u05D4 \u05DC\u05E4\u05EA\u05E8\u05D5\u05DF. \u05DB\u05E9\u05D4\u05D5\u05D0 \u05DB\u05D1\u05D5\u05D9 \u2014 \u05DB\u05DC \u05DE\u05D9 \u05E9\u05DE\u05D5\u05EA\u05E7\u05DF \u05D0\u05E6\u05DC\u05D5 DSViz. \u05E2\u05DE\u05D3\u05EA \u05DE\u05D7\u05E9\u05D1 \u05D1\u05DB\u05D9\u05EA\u05D4 \u05DC\u05D0 \u05E9\u05D5\u05DC\u05D7\u05EA \u05DC\u05E2\u05D5\u05DC\u05DD \u2014 \u05D1\u05E2\u05DE\u05D3\u05D4 \u05E2\u05D5\u05D1\u05D3\u05D9\u05DD \u05E8\u05E7 \u05E2\u05DC \u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD \u05DE\u05DE\u05D0\u05D2\u05E8 \u05D4\u05E9\u05D0\u05DC\u05D5\u05EA.')),\n        h('label', { class: 'switch', for: 's-gem' }, h('span', { class: 'sr' }, '\u05E8\u05E7 \u05DC\u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD'), gem, h('span', { class: 'track', 'aria-hidden': 'true' }, h('span', { class: 'knob' })), gemLabel)),\n      h('div', { class: 'warn-box' }, '\u05DE\u05D3\u05DC\u05D9\u05E7\u05D9\u05DD \u05E8\u05E7 \u05D0\u05D7\u05E8\u05D9 \u05E9\u05DB\u05DC \u05D4\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05E2\u05D5\u05D3\u05DB\u05E0\u05D5 \u05DC\u05D2\u05E8\u05E1\u05D4 \u05E2\u05DD \u05D4\u05D4\u05EA\u05D7\u05D1\u05E8\u05D5\u05EA \u2014 \u05DE\u05D7\u05E9\u05D1 \u05E2\u05DD \u05D2\u05E8\u05E1\u05D4 \u05D9\u05E9\u05E0\u05D4 \u05D9\u05E7\u05D1\u05DC \u05F4\u05E6\u05E8\u05D9\u05DA \u05DC\u05D4\u05EA\u05D7\u05D1\u05E8 / \u05DC\u05E2\u05D3\u05DB\u05DF \u05D0\u05EA DSViz\u05F4.'),\n      h('div', { class: 'form3' }, num('monthlyPerSeat', '\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05D1\u05D7\u05D5\u05D3\u05E9 \u05DC\u05DE\u05D5\u05E8\u05D4 \u2014 \u05DC\u05DB\u05DC \u05E2\u05DE\u05D3\u05D4 \u05E9\u05DC\u05D5 (\u05D4\u05E2\u05DE\u05D3\u05D5\u05EA \u05E2\u05E6\u05DE\u05DF \u05DC\u05D0 \u05E9\u05D5\u05DC\u05D7\u05D5\u05EA)', s.monthlyPerSeat, 0, 1000), num('monthlyStudent', '\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05D1\u05D7\u05D5\u05D3\u05E9 \u2014 \u05EA\u05DC\u05DE\u05D9\u05D3 \u05E8\u05E9\u05D5\u05DD', s.monthlyStudent, 0, 5000))),\n    h('section', { class: 'card' },\n      h('div', { class: 'row', style: { alignItems: 'flex-start', gap: '16px' } },\n        h('div', { class: 'grow stack', style: { gap: '6px' } }, h('h2', null, '\u05D4\u05EA\u05E8\u05D0\u05D5\u05EA \u05DB\u05E9\u05DE\u05E6\u05D8\u05E8\u05E4\u05D9\u05DD'),\n          h('p', { class: 'muted', style: { lineHeight: '1.55' } }, '\u05EA\u05DC\u05DE\u05D9\u05D3 \u05D7\u05D3\u05E9, \u05DE\u05D5\u05E8\u05D4 \u05E9\u05E0\u05DB\u05E0\u05E1 \u05D1\u05E4\u05E2\u05DD \u05D4\u05E8\u05D0\u05E9\u05D5\u05E0\u05D4, \u05D1\u05E7\u05E9\u05D4 \u05DC\u05D4\u05E8\u05E9\u05D0\u05EA \u05DE\u05D5\u05E8\u05D4 \u05D5\u05EA\u05E9\u05DC\u05D5\u05DD \u2014 \u05DE\u05D5\u05E4\u05D9\u05E2\u05D9\u05DD \u05D1\u05E4\u05E2\u05DE\u05D5\u05DF \u05DC\u05DE\u05E2\u05DC\u05D4 \u05E2\u05DD \u05E2\u05D9\u05D2\u05D5\u05DC \u05D0\u05D3\u05D5\u05DD, ',\n            '\u05D5\u05D1\u05DE\u05D9\u05D9\u05DC', am.to ? [' \u05DC-', h('span', { class: 'ltr', style: { fontWeight: 600 } }, am.to)] : '', '.')),\n        h('label', { class: 'switch', for: 's-mail' }, h('span', { class: 'sr' }, '\u05D4\u05EA\u05E8\u05D0\u05D5\u05EA \u05D1\u05DE\u05D9\u05D9\u05DC'), mailOn, h('span', { class: 'track', 'aria-hidden': 'true' }, h('span', { class: 'knob' })), mailLabel)),\n      am.ready ? null : h('div', { class: 'warn-box' }, '\u05E9\u05DC\u05D9\u05D7\u05EA \u05D4\u05DE\u05D9\u05D9\u05DC \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05DE\u05D5\u05D2\u05D3\u05E8\u05EA \u05D1\u05E9\u05E8\u05EA (RESEND_API_KEY). \u05E2\u05D3 \u05D0\u05D6 \u05D4\u05D4\u05EA\u05E8\u05D0\u05D5\u05EA \u05DE\u05D5\u05E4\u05D9\u05E2\u05D5\u05EA \u05E8\u05E7 \u05D1\u05E4\u05E2\u05DE\u05D5\u05DF.')),\n    h('section', { class: 'card' }, h('h2', null, '\u05EA\u05E9\u05DC\u05D5\u05DD \u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD'),\n      h('div', { class: 'form3' }, num('price', '\u05DE\u05D7\u05D9\u05E8 (\u20AA) \u2014 0 = \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E4\u05EA\u05D5\u05D7', s.price, 0, 5000),\n        h('label', { class: 'lbl', for: 's-until' }, '\u05EA\u05E9\u05DC\u05D5\u05DD \u05E4\u05D5\u05EA\u05D7 \u05D0\u05EA \u05D4\u05DE\u05D0\u05D2\u05E8 \u05E2\u05D3', until),\n        h('div', { class: 'lbl' }, '\u05E9\u05D9\u05E8\u05D5\u05EA \u05D4\u05EA\u05E9\u05DC\u05D5\u05DD', h('span', { class: 'field', style: { display: 'flex', alignItems: 'center', background: 'var(--bg)', borderColor: 'var(--bg)', fontWeight: 600, color: 'var(--muted)' } }, r.provider || '\u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E0\u05D1\u05D7\u05E8')))),\n    h('section', { class: 'card' }, h('h2', null, '\u05D7\u05D1\u05D9\u05DC\u05D5\u05EA \u05E2\u05DE\u05D3\u05D5\u05EA \u05DC\u05DE\u05D5\u05E8\u05D9\u05DD'),\n      h('p', { class: 'small muted' }, '\u05D4\u05DE\u05D5\u05E8\u05D4 \u05E7\u05D5\u05E0\u05D4 \u05D7\u05D1\u05D9\u05DC\u05D4 \u05D1\u05DC\u05D5\u05D7 \u05E9\u05DC\u05D5, \u05D5\u05D4\u05E2\u05DE\u05D3\u05D5\u05EA \u05DE\u05EA\u05D5\u05D5\u05E1\u05E4\u05D5\u05EA \u05DC\u05D5 \u05DE\u05D9\u05D3 \u05D0\u05D7\u05E8\u05D9 \u05E9\u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05DE\u05D0\u05D5\u05E9\u05E8. \u05DE\u05D7\u05D9\u05E8 0 \u2014 \u05D4\u05D7\u05D1\u05D9\u05DC\u05D4 \u05DC\u05D0 \u05DE\u05D5\u05E6\u05D2\u05EA.'),\n      h('div', { class: 'form3' }, (s.packages || []).map((p, i) => h('div', { class: 'stack', style: { gap: '8px', background: 'var(--bg)', borderRadius: '12px', padding: '12px' } },\n        h('label', { class: 'lbl', for: 'pk-s' + i }, '\u05E2\u05DE\u05D3\u05D5\u05EA', h('input', { class: 'field', type: 'number', min: '1', max: '500', id: 'pk-s' + i, value: String(p.seats), 'data-pk': i, 'data-f': 'seats' })),\n        h('label', { class: 'lbl', for: 'pk-p' + i }, '\u05DE\u05D7\u05D9\u05E8 (\u20AA)', h('input', { class: 'field', type: 'number', min: '0', max: '20000', id: 'pk-p' + i, value: String(p.price), 'data-pk': i, 'data-f': 'price' })))))),\n    h('section', { class: 'card' }, h('h2', null, '\u05E2\u05DE\u05D3\u05D5\u05EA \u05D5\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD'),\n      h('div', { class: 'form2' }, num('defaultSeats', '\u05E2\u05DE\u05D3\u05D5\u05EA \u05D7\u05D9\u05E0\u05DD \u05DC\u05DE\u05D5\u05E8\u05D4 \u05D7\u05D3\u05E9', s.defaultSeats, 0, 2000), num('offlineDays', '\u05D9\u05DE\u05D9\u05DD \u05D1\u05DC\u05D9 \u05D0\u05D9\u05E0\u05D8\u05E8\u05E0\u05D8 (0 = \u05D1\u05DC\u05D9 \u05D4\u05D2\u05D1\u05DC\u05D4)', s.offlineDays, 0, 365),\n        num('studentDevices', '\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05DC\u05DB\u05DC \u05EA\u05DC\u05DE\u05D9\u05D3', s.studentDevices, 1, 20), num('teacherDevices', '\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05DC\u05DB\u05DC \u05DE\u05D5\u05E8\u05D4', s.teacherDevices, 1, 50))),\n    h('section', { class: 'card' }, h('h2', null, '\u05DE\u05E0\u05D4\u05DC\u05D9\u05DD'),\n      h('div', { class: 'stack' }, r.admins.map((a, i) => h('div', { class: 'row' }, h('span', { class: 'ltr', style: { fontWeight: 600 } }, a), i === 0 ? h('span', { class: 'pill blue' }, '\u05DE\u05E0\u05D4\u05DC \u05E8\u05D0\u05E9\u05D9') : null))),\n      h('p', { class: 'small muted' }, '\u05E8\u05E9\u05D9\u05DE\u05EA \u05D4\u05DE\u05E0\u05D4\u05DC\u05D9\u05DD \u05E0\u05E7\u05D1\u05E2\u05EA \u05D1\u05D4\u05D2\u05D3\u05E8\u05D5\u05EA \u05D4\u05E9\u05E8\u05EA (ADMIN_EMAILS), \u05DB\u05D3\u05D9 \u05E9\u05D0\u05E3 \u05D0\u05D7\u05D3 \u05DC\u05D0 \u05D9\u05D5\u05DB\u05DC \u05DC\u05D4\u05D5\u05E1\u05D9\u05E3 \u05D0\u05EA \u05E2\u05E6\u05DE\u05D5 \u05DE\u05DB\u05D0\u05DF.'),\n      r.passKey ? null : h('div', { class: 'warn-box' }, '\u05D7\u05E1\u05E8 \u05DE\u05E4\u05EA\u05D7 \u05D4\u05D7\u05EA\u05D9\u05DE\u05D4 (PASS_KEY) \u05D1\u05E9\u05E8\u05EA \u2014 \u05D1\u05DC\u05D9 \u05D6\u05D4 \u05D4\u05DE\u05D0\u05D2\u05E8 \u05DC\u05D0 \u05E0\u05E4\u05EA\u05D7 \u05D1\u05DC\u05D9 \u05D0\u05D9\u05E0\u05D8\u05E8\u05E0\u05D8.')));\n  const save = h('button', { type: 'button', class: 'btn primary lg' }, '\u05E9\u05DE\u05D9\u05E8\u05EA \u05D4\u05E9\u05D9\u05E0\u05D5\u05D9\u05D9\u05DD');\n  save.onclick = () => busy(save, async () => {\n    const body = { requireLoginForGemini: gem.checked, accessUntil: until.value, alertEmail: mailOn.checked };\n    for (const f of form.querySelectorAll('input[data-key]')) body[f.dataset.key] = +f.value;\n    body.packages = (s.packages || []).map((p, i) => ({ seats: +form.querySelector(`[data-pk=\"${i}\"][data-f=\"seats\"]`).value, price: +form.querySelector(`[data-pk=\"${i}\"][data-f=\"price\"]`).value }));\n    if (gem.checked && !s.requireLoginForGemini && !await ask({ title: '\u05DC\u05D4\u05D3\u05DC\u05D9\u05E7 \"\u05E8\u05E7 \u05DC\u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD\"?', text: '\u05DE\u05E2\u05DB\u05E9\u05D9\u05D5 \u05DE\u05D9 \u05E9\u05DC\u05D0 \u05DE\u05D7\u05D5\u05D1\u05E8 (\u05D0\u05D5 \u05E9\u05D4\u05D2\u05E8\u05E1\u05D4 \u05D0\u05E6\u05DC\u05D5 \u05D9\u05E9\u05E0\u05D4) \u05DC\u05D0 \u05D9\u05D5\u05DB\u05DC \u05DC\u05E9\u05DC\u05D5\u05D7 \u05EA\u05E8\u05D2\u05D9\u05DC \u05DC-Gemini.', ok: '\u05D4\u05D3\u05DC\u05E7\u05D4' })) return;\n    await api('POST', '/v1/admin/settings', body);\n    toast('\u05E0\u05E9\u05DE\u05E8 \u2014 \u05E0\u05DB\u05E0\u05E1 \u05DC\u05EA\u05D5\u05E7\u05E3 \u05EA\u05D5\u05DA \u05D3\u05E7\u05D4');\n    render();\n  });\n  return [h('h1', null, '\u05D4\u05D2\u05D3\u05E8\u05D5\u05EA'), form, h('div', { class: 'row' }, save, h('span', { class: 'small muted' }, '\u05E9\u05D9\u05E0\u05D5\u05D9 \u05E0\u05DB\u05E0\u05E1 \u05DC\u05EA\u05D5\u05E7\u05E3 \u05EA\u05D5\u05DA \u05D3\u05E7\u05D4.'))];\n}\n\n// ------------------------------------------------------------------ \u05D4\u05EA\u05D7\u05DC\u05D4\n\nasync function main() {\n  try { await boot(); } catch (e) { app.replaceChildren(h('div', { class: 'loading' }, '\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E4\u05EA\u05D5\u05D7\u05D4 \u05D1\u05E9\u05E8\u05EA (ACCESS).')); return; }\n  user = await currentUser();\n  const signin = msg => {\n    const b = h('button', { type: 'button', class: 'gbtn' }, googleLogo(), '\u05D4\u05EA\u05D7\u05D1\u05E8\u05D5\u05EA \u05E2\u05DD Google');\n    b.onclick = () => busy(b, async () => { user = await googleSignIn(); start(); });\n    app.replaceChildren(h('main', { class: 'center', style: { minHeight: '100vh' } }, h('div', { class: 'panel' },\n      h('div', { class: 'logo dark', style: { width: '48px', height: '48px', borderRadius: '12px' } }, icon(ICONS.shield, 26, '#fff')),\n      h('h1', null, '\u05DE\u05E1\u05D5\u05E3 \u05D4\u05D4\u05E0\u05D4\u05DC\u05D4'), h('p', { class: 'muted' }, msg || '\u05E0\u05DB\u05E0\u05E1\u05D9\u05DD \u05E2\u05DD \u05D7\u05E9\u05D1\u05D5\u05DF Google \u05E9\u05DC \u05D4\u05D4\u05E0\u05D4\u05DC\u05D4.'), b,\n      user ? h('a', { href: '#', class: 'small', onclick: async e => { e.preventDefault(); await logoutWeb(); user = null; signin(); } }, '\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05DD \u05D7\u05E9\u05D1\u05D5\u05DF \u05D0\u05D7\u05E8') : null)));\n  };\n  const start = async () => {\n    try {\n      await api('GET', '/v1/admin/settings').then(r => { settingsCache = r.settings; });\n    } catch (e) {\n      return signin(e.code === 'admin' ? `\u05D4\u05D7\u05E9\u05D1\u05D5\u05DF ${user.email} \u05DC\u05D0 \u05DE\u05D5\u05D2\u05D3\u05E8 \u05DB\u05DE\u05E0\u05D4\u05DC.` : e.message);\n    }\n    window.addEventListener('hashchange', render);\n    render();\n    watchAlerts();\n  };\n  if (!user) return signin();\n  start();\n}\nmain();\n", "/app.css": `/* DSViz \u2014 \u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05DC\u05DE\u05D0\u05D2\u05E8 \u05D5\u05DE\u05E1\u05D5\u05E3 \u05D4\u05D4\u05E0\u05D4\u05DC\u05D4. \u05D0\u05D5\u05EA\u05DD \u05E6\u05D1\u05E2\u05D9\u05DD \u05DB\u05DE\u05D5 \u05D4\u05EA\u05D5\u05E1\u05E3 \u05D5\u05E2\u05D9\u05E6\u05D5\u05D1 \u05D4\u05DE\u05E1\u05DB\u05D9\u05DD */
:root {
  --bg: #F4F6FB; --card: #FFFFFF; --ink: #17213A; --muted: #5A6478; --line: #E1E6F0; --line2: #EEF1F6; --field: #C9D2E3;
  --blue: #2F6FED; --blue-ink: #1E4FB8; --blue-soft: #E8EFFD; --blue-row: #F2F6FF;
  --green: #1F7A44; --green-soft: #E3F4EA; --green-bar: #2E9E5B;
  --orange: #9A3412; --orange-soft: #FFEDD5; --warn: #C2410C; --warn-bg: #FFF7ED;
  --purple: #3F3A8F; --purple-soft: #ECEBFB; --red: #B42318; --red-line: #E8B4A0; --navy: #17213A;
  --radius: 14px;
}
* { box-sizing: border-box; }
html, body { margin: 0; }
body { background: var(--bg); color: var(--ink); font-family: 'Assistant', system-ui, -apple-system, 'Segoe UI', Arial, sans-serif; font-size: 15px; line-height: 1.45; }
h1, h2, h3, .display { font-family: 'Rubik', 'Assistant', system-ui, sans-serif; }
h1 { margin: 0; font-size: 26px; font-weight: 700; }
h2 { margin: 0; font-size: 19px; font-weight: 600; }
h3 { margin: 0; font-size: 16px; font-weight: 700; }
p { margin: 0; }
a { color: var(--blue); text-decoration: none; }
a:hover { color: var(--blue-ink); }
.muted { color: var(--muted); }
.small { font-size: 13px; }
.ltr { direction: ltr; unicode-bidi: isolate; }
.mono { font-family: 'JetBrains Mono', ui-monospace, Consolas, monospace; font-weight: 600; letter-spacing: 2px; direction: ltr; unicode-bidi: isolate; }
.sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
[hidden] { display: none !important; }

/* \u05DB\u05E4\u05EA\u05D5\u05E8\u05D9\u05DD \u05D5\u05E9\u05D3\u05D5\u05EA */
.btn { height: 44px; padding: 0 18px; border-radius: 11px; border: 1.5px solid var(--field); background: var(--card); color: var(--ink); font: 600 15px inherit; font-family: inherit; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; gap: 8px; white-space: nowrap; }
.btn:hover { border-color: #AEB9CF; }
.btn:focus-visible, .field:focus-visible, .nav a:focus-visible { outline: 3px solid #9DBBF7; outline-offset: 2px; }
.btn.primary { background: var(--blue); border-color: var(--blue); color: #fff; font-weight: 700; }
.btn.primary:hover { background: var(--blue-ink); border-color: var(--blue-ink); }
.btn.dark { background: var(--navy); border-color: var(--navy); color: #fff; }
.btn.ghost { border-color: transparent; background: transparent; color: var(--muted); }
.btn.danger { color: var(--red); border-color: var(--red-line); font-weight: 700; }
.btn.sm { height: 34px; padding: 0 12px; font-size: 14px; border-radius: 9px; }
.btn.lg { height: 52px; font-size: 17px; border-radius: 12px; }
.btn.wide { width: 100%; }
.btn[disabled] { opacity: .55; cursor: default; }
.field { height: 44px; border: 1.5px solid var(--field); border-radius: 11px; padding: 0 12px; font: 15px inherit; font-family: inherit; color: var(--ink); background: #fff; width: 100%; }
textarea.field { height: auto; padding: 10px 12px; resize: vertical; min-height: 64px; }
.field.num { text-align: center; font-family: 'Rubik', sans-serif; font-weight: 700; font-size: 17px; }
label.lbl { display: flex; flex-direction: column; gap: 6px; font-size: 14px; font-weight: 700; }

/* \u05DB\u05E8\u05D8\u05D9\u05E1\u05D9\u05DD \u05D5\u05EA\u05D2\u05D9\u05D5\u05EA */
.card { background: var(--card); border: 1px solid var(--line); border-radius: 16px; padding: 20px 22px; display: flex; flex-direction: column; gap: 14px; }
.pill { display: inline-flex; align-items: center; font-size: 12.5px; font-weight: 700; border-radius: 999px; padding: 2px 10px; white-space: nowrap; }
.pill.green { color: var(--green); background: var(--green-soft); }
.pill.blue { color: var(--blue-ink); background: var(--blue-soft); }
.pill.orange { color: var(--orange); background: var(--orange-soft); }
.pill.purple { color: var(--purple); background: var(--purple-soft); }
.pill.gray { color: var(--muted); background: var(--line2); }
.bar { height: 6px; border-radius: 999px; background: var(--line); overflow: hidden; display: block; }
.bar > span { display: block; height: 100%; background: var(--blue); border-radius: 999px; }
.bar.full > span { background: var(--warn); }
.bar.big { height: 10px; }
.bar.ok > span { background: var(--green-bar); }
.note { font-size: 14px; color: var(--muted); background: var(--bg); border-radius: 10px; padding: 8px 12px; }
.warn-box { font-size: 14px; line-height: 1.5; background: var(--warn-bg); color: #7C2D12; border-radius: 10px; padding: 10px 14px; }
.row { display: flex; align-items: center; gap: 10px; }
.row.wrap { flex-wrap: wrap; }
.grow { flex: 1; min-width: 0; }
.stack { display: flex; flex-direction: column; gap: 10px; }
.list { border: 1px solid var(--line); border-radius: 12px; display: flex; flex-direction: column; overflow: hidden; }
.list > .item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-bottom: 1px solid var(--line2); }
.list > .item:last-child { border-bottom: 0; }
.list > .item.stale { background: var(--warn-bg); }
.empty { padding: 18px; text-align: center; color: var(--muted); }

/* \u05D3\u05E3 \u05DE\u05DE\u05D5\u05E8\u05DB\u05D6 (\u05DB\u05E0\u05D9\u05E1\u05D4, \u05D0\u05D7\u05E8\u05D9 \u05DB\u05E0\u05D9\u05E1\u05D4) */
.top { height: 60px; padding: 0 24px; display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid var(--line); gap: 12px; }
.brand { display: flex; align-items: center; gap: 12px; font-family: 'Rubik', sans-serif; font-weight: 600; font-size: 17px; }
.logo { width: 34px; height: 34px; border-radius: 9px; background: var(--blue); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.logo.dark { background: var(--navy); }
.who { display: flex; align-items: center; gap: 10px; }
.who .meta { display: flex; flex-direction: column; align-items: flex-end; line-height: 1.2; font-size: 14px; font-weight: 600; }
.avatar { width: 38px; height: 38px; border-radius: 50%; background: var(--blue); color: #fff; font-family: 'Rubik', sans-serif; font-weight: 600; font-size: 17px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; overflow: hidden; }
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.center { min-height: calc(100vh - 60px); display: flex; align-items: center; justify-content: center; padding: 24px 16px; }
.panel { width: 100%; max-width: 520px; background: #fff; border: 1px solid var(--line); border-radius: 18px; padding: 32px 36px; display: flex; flex-direction: column; gap: 16px; }
.panel.c { align-items: center; text-align: center; }
.badge-icon { width: 64px; height: 64px; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.badge-icon.green { background: var(--green-soft); } .badge-icon.blue { background: var(--blue-soft); } .badge-icon.orange { background: var(--orange-soft); }
.gbtn { height: 52px; border-radius: 12px; border: 1.5px solid var(--field); background: #fff; font: 700 16px inherit; font-family: inherit; color: var(--ink); display: flex; align-items: center; justify-content: center; gap: 12px; cursor: pointer; width: 100%; }
.gbtn:hover { border-color: #AEB9CF; background: #FAFBFD; }
.price { border: 2px solid var(--blue); border-radius: 14px; padding: 18px 20px; display: flex; align-items: center; justify-content: space-between; gap: 12px; text-align: right; width: 100%; }
.chips { display: flex; flex-wrap: wrap; gap: 8px; }
.chip { border: 1px solid var(--field); border-radius: 999px; padding: 6px 14px; font-size: 14px; font-weight: 600; }
.spinner { width: 56px; height: 56px; border-radius: 50%; border: 6px solid var(--line); border-top-color: var(--blue); animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* \u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05E9\u05DC\u05D9 */
.page { max-width: 1040px; margin: 0 auto; padding: 26px 20px 40px; display: flex; flex-direction: column; gap: 20px; }
.grid2 { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; align-items: start; }
.codebox { background: var(--bg); border: 1.5px dashed var(--field); border-radius: 14px; padding: 18px; display: flex; align-items: center; justify-content: center; font-size: 34px; }

.packs { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.pack { border: 1.5px solid var(--line); border-radius: 14px; padding: 18px; display: flex; flex-direction: column; align-items: center; gap: 8px; text-align: center; }
@media (max-width: 860px) { .packs { grid-template-columns: 1fr; } }

/* \u05DE\u05E1\u05D5\u05E3 \u05D4\u05D4\u05E0\u05D4\u05DC\u05D4 */
.shell { display: flex; min-height: 100vh; }
.side { width: 236px; flex-shrink: 0; background: #fff; border-left: 1px solid var(--line); padding: 20px 14px; display: flex; flex-direction: column; gap: 4px; position: sticky; top: 0; height: 100vh; }
.side .brand { padding: 0 8px 18px; font-size: 16px; }
.side .brand small { display: block; font-family: 'Assistant', sans-serif; font-weight: 400; font-size: 12px; color: var(--muted); }
.nav a { display: flex; align-items: center; gap: 10px; height: 42px; padding: 0 12px; border-radius: 10px; color: var(--ink); font-weight: 600; font-size: 15px; }
.nav a:hover { background: var(--bg); }
.nav a[aria-current="page"] { background: var(--blue-soft); color: var(--blue-ink); font-weight: 700; }
.nav .count { min-width: 22px; height: 22px; border-radius: 999px; background: var(--warn); color: #fff; font-size: 12px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center; padding: 0 6px; margin-inline-start: auto; }
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.main > header { height: 64px; padding: 0 28px; display: flex; align-items: center; gap: 16px; background: #fff; border-bottom: 1px solid var(--line); position: sticky; top: 0; z-index: 5; }
.search { flex: 1; max-width: 520px; height: 42px; display: flex; align-items: center; gap: 10px; padding: 0 14px; border: 1.5px solid var(--field); border-radius: 11px; background: #fff; position: relative; }
.search input { flex: 1; border: 0; outline: 0; font: 15px inherit; font-family: inherit; background: transparent; color: var(--ink); min-width: 0; }
.results { position: absolute; top: 46px; right: 0; left: 0; background: #fff; border: 1px solid var(--line); border-radius: 12px; box-shadow: 0 12px 30px rgba(23,33,58,.12); max-height: 360px; overflow: auto; z-index: 10; }
.results button { display: flex; width: 100%; text-align: right; gap: 10px; align-items: center; padding: 10px 14px; border: 0; background: #fff; font: 15px inherit; font-family: inherit; color: var(--ink); cursor: pointer; border-bottom: 1px solid var(--line2); }
.results button:hover, .results button:focus { background: var(--blue-row); outline: 0; }
.bell-wrap { position: relative; }
.bell { position: relative; width: 42px; height: 42px; border-radius: 11px; border: 1.5px solid var(--field); background: #fff; display: inline-flex; align-items: center; justify-content: center; cursor: pointer; }
.bell:hover, .bell[aria-expanded="true"] { background: var(--blue-row); }
.bell:focus-visible { outline: 3px solid var(--blue); outline-offset: 2px; }
.bell-dot { position: absolute; top: -6px; inset-inline-end: -6px; min-width: 20px; height: 20px; padding: 0 5px; border-radius: 999px; background: #E5372B; color: #fff; font-size: 12px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center; border: 2px solid #fff; }
.bell-dot[hidden] { display: none; }
.alerts { position: absolute; top: 50px; inset-inline-end: 0; width: min(380px, calc(100vw - 32px)); background: #fff; border: 1px solid var(--line); border-radius: 14px; box-shadow: 0 16px 36px rgba(23,33,58,.16); z-index: 20; overflow: hidden; }
.alerts-head { display: flex; justify-content: space-between; align-items: baseline; padding: 12px 16px; border-bottom: 1px solid var(--line2); }
.alerts-list { max-height: 420px; overflow: auto; }
.alert-item { display: flex; gap: 10px; align-items: flex-start; width: 100%; padding: 12px 16px; border: 0; border-bottom: 1px solid var(--line2); background: #fff; font: 14px inherit; font-family: inherit; color: var(--ink); cursor: pointer; text-align: right; }
.alert-item:hover, .alert-item:focus { background: var(--blue-row); outline: 0; }
.alert-item.new { background: #FFF6F5; }
.alert-item .dot { margin-top: 6px; flex: none; }
.content { flex: 1; padding: 26px 28px 40px; display: flex; flex-direction: column; gap: 18px; max-width: 1280px; width: 100%; }
.titlebar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.kpi { background: #fff; border: 1px solid var(--line); border-radius: 14px; padding: 16px 18px; display: flex; flex-direction: column; gap: 6px; color: var(--ink); }
.kpi:hover { border-color: #C7D2E6; color: var(--ink); }
.kpi .k { font-size: 14px; color: var(--muted); font-weight: 600; }
.kpi .v { font-family: 'Rubik', sans-serif; font-size: 30px; font-weight: 700; line-height: 1.1; }
.kpi .v small { font-family: 'Assistant', sans-serif; font-size: 15px; color: var(--muted); font-weight: 400; }
.two { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr); gap: 18px; align-items: start; }
.split { display: flex; gap: 20px; align-items: flex-start; }
.drawer { width: 420px; flex-shrink: 0; position: sticky; top: 84px; }
.filters { display: flex; gap: 8px; flex-wrap: wrap; }
.filters button { height: 36px; padding: 0 14px; border-radius: 999px; border: 1.5px solid var(--field); background: #fff; color: var(--ink); font: 600 14px inherit; font-family: inherit; cursor: pointer; }
.filters button[aria-pressed="true"] { background: var(--navy); border-color: var(--navy); color: #fff; }
.order { display: inline-flex; align-items: center; gap: 4px; padding: 3px; border-radius: 999px; background: var(--bg); border: 1px solid var(--line); }
.order svg { margin: 0 6px 0 4px; flex-shrink: 0; }
.order button { height: 30px; padding: 0 12px; border-radius: 999px; border: 0; background: transparent; color: var(--muted); font: 600 13px inherit; font-family: inherit; cursor: pointer; white-space: nowrap; }
.order button[aria-pressed="true"] { background: #fff; color: var(--ink); box-shadow: 0 1px 3px rgba(23, 33, 58, .14); }
.order button:focus-visible, .thsort:focus-visible { outline: 2px solid var(--blue); outline-offset: 2px; }
.thsort { border: 0; background: transparent; padding: 0; font: inherit; color: inherit; cursor: pointer; white-space: nowrap; }
.thsort:hover, .thsort.on { color: var(--blue-ink); }
select.field.sm { height: 36px; width: auto; padding: 0 10px; font-size: 14px; }
.tabs { display: flex; gap: 6px; border-bottom: 1px solid var(--line); }
.tabs button { height: 42px; padding: 0 16px; border: 0; border-bottom: 3px solid transparent; background: transparent; font: 600 15px inherit; font-family: inherit; color: var(--muted); cursor: pointer; }
.tabs button[aria-selected="true"] { border-bottom-color: var(--blue); color: var(--blue-ink); font-weight: 700; }
.tablewrap { background: #fff; border: 1px solid var(--line); border-radius: 14px; overflow-x: auto; }
table.t { width: 100%; border-collapse: collapse; font-size: 15px; min-width: 640px; }
table.t th { text-align: right; color: var(--muted); font-size: 13px; font-weight: 700; padding: 12px 16px; border-bottom: 1px solid var(--line); white-space: nowrap; }
table.t td { padding: 12px 16px; border-bottom: 1px solid var(--line2); vertical-align: middle; }
table.t tr:last-child td { border-bottom: 0; }
table.t tbody tr.click { cursor: pointer; }
table.t tbody tr.click:hover { background: #FAFBFE; }
table.t tbody tr.sel { background: var(--blue-row); box-shadow: inset -3px 0 0 var(--blue); }
.cellname { display: flex; flex-direction: column; }
.cellname b { font-weight: 700; }
.cellname span { font-size: 13px; color: var(--muted); }
.seats { display: flex; flex-direction: column; gap: 5px; width: 120px; }
.stepper { display: flex; align-items: center; gap: 10px; }
.stepper .btn { width: 48px; height: 48px; padding: 0; border-radius: 12px; }
.stepper .field { height: 48px; font-size: 24px; border-color: var(--blue); border-radius: 12px; }
.feed { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
.feed li { display: flex; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--line2); }
.feed li:last-child { border-bottom: 0; }
.dot { width: 8px; height: 8px; margin-top: 7px; border-radius: 50%; background: var(--blue); flex-shrink: 0; }
.dot.green { background: var(--green); } .dot.dark { background: var(--navy); } .dot.warn { background: var(--warn); }
.req { border: 1px solid var(--line); border-radius: 12px; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px; background: #fff; }
.req .body { display: flex; gap: 24px; flex-wrap: wrap; align-items: flex-start; }
.req .act { display: flex; flex-direction: column; gap: 8px; width: 300px; max-width: 100%; }
.switch { display: inline-flex; align-items: center; gap: 10px; cursor: pointer; font-weight: 700; font-size: 14px; }
.switch input { position: absolute; opacity: 0; width: 1px; height: 1px; }
.switch .track { width: 52px; height: 30px; border-radius: 999px; background: var(--field); padding: 3px; display: flex; transition: background .15s; }
.switch .knob { width: 24px; height: 24px; border-radius: 50%; background: #fff; box-shadow: 0 1px 2px rgba(23,33,58,.25); transition: transform .15s; }
.switch input:checked + .track { background: var(--green-bar); }
.switch input:checked + .track .knob { transform: translateX(-22px); }
.switch input:focus-visible + .track { outline: 3px solid #9DBBF7; outline-offset: 2px; }
.form3 { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.form2 { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
dialog.modal { border: 0; border-radius: 18px; padding: 0; width: min(480px, calc(100vw - 32px)); box-shadow: 0 24px 60px rgba(23,33,58,.25); color: var(--ink); }
dialog.modal::backdrop { background: rgba(23,33,58,.35); }
dialog.modal .in { padding: 24px; display: flex; flex-direction: column; gap: 14px; }
.toast { position: fixed; bottom: 22px; left: 50%; transform: translateX(-50%); background: var(--navy); color: #fff; padding: 12px 20px; border-radius: 12px; font-weight: 600; z-index: 100; box-shadow: 0 10px 30px rgba(23,33,58,.25); max-width: calc(100vw - 32px); }
.toast.err { background: var(--red); }
.loading { padding: 40px; text-align: center; color: var(--muted); }

@media (max-width: 1100px) { .split { flex-direction: column; } .drawer { width: auto; position: static; align-self: stretch; } .two { grid-template-columns: 1fr; } }
@media (max-width: 860px) {
  .shell { flex-direction: column; }
  .side { width: auto; height: auto; position: static; flex-direction: row; overflow-x: auto; padding: 8px; border-left: 0; border-bottom: 1px solid var(--line); }
  .side .brand { display: none; }
  .nav { display: flex; gap: 4px; }
  .nav a { white-space: nowrap; }
  .main > header { padding: 0 16px; position: static; }
  .main > header .who .meta { display: none; }
  .content { padding: 18px 16px 32px; }
  .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .grid2, .form3, .form2 { grid-template-columns: 1fr; }
  .panel { padding: 24px 20px; }
  .top { padding: 0 16px; }
  .top .who .meta { display: none; }
}
`, "/common.js": "// \u05DE\u05E9\u05D5\u05EA\u05E3 \u05DC\u05DB\u05DC \u05D4\u05D3\u05E4\u05D9\u05DD: Firebase (\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05DD Google), \u05E7\u05E8\u05D9\u05D0\u05D5\u05EA \u05DC\u05E9\u05E8\u05EA, \u05D5\u05D1\u05E0\u05D9\u05D9\u05EA \u05EA\u05E6\u05D5\u05D2\u05D4 \u05D1\u05DC\u05D9 innerHTML (\u05D0\u05D9\u05DF \u05D4\u05D6\u05E8\u05E7\u05EA \u05E7\u05D5\u05D3 \u05DE\u05E0\u05EA\u05D5\u05E0\u05D9\u05DD)\nimport { initializeApp } from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';\nimport {\n  getAuth, GoogleAuthProvider, signInWithPopup, onAuthStateChanged, signOut, connectAuthEmulator,\n} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';\n\nexport let auth = null;\nexport let config = null;\n\nexport async function boot() {\n  const res = await fetch('/v1/access/config', { cache: 'no-store' });\n  config = await res.json();\n  if (!config.enabled) throw new Error('\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E4\u05EA\u05D5\u05D7\u05D4.');\n  const app = initializeApp(config.firebase);\n  auth = getAuth(app);\n  auth.languageCode = 'he';\n  if (config.emulator && config.emulator.auth) connectAuthEmulator(auth, 'http://' + config.emulator.auth, { disableWarnings: true });\n  return config;\n}\n\n/** \u05D4\u05DE\u05E9\u05EA\u05DE\u05E9 \u05D4\u05DE\u05D7\u05D5\u05D1\u05E8 (\u05D0\u05D5 null) \u2014 \u05D0\u05D7\u05E8\u05D9 \u05E9-Firebase \u05E1\u05D9\u05D9\u05DD \u05DC\u05D1\u05D3\u05D5\u05E7 \u05D0\u05EA \u05DE\u05D4 \u05E9\u05E0\u05E9\u05DE\u05E8 \u05D1\u05D3\u05E4\u05D3\u05E4\u05DF */\nexport function currentUser() {\n  return new Promise(resolve => {\n    const stop = onAuthStateChanged(auth, u => { stop(); resolve(u); });\n  });\n}\n\nexport async function googleSignIn() {\n  const p = new GoogleAuthProvider();\n  p.setCustomParameters({ prompt: 'select_account' });\n  const r = await signInWithPopup(auth, p);\n  return r.user;\n}\n\nexport async function logoutWeb() {\n  await signOut(auth);\n}\n\nexport class ApiError extends Error {\n  constructor(status, code, message) { super(message); this.status = status; this.code = code; }\n}\n\nexport async function api(method, path, body) {\n  const headers = {};\n  const u = auth && auth.currentUser;\n  if (u) headers.authorization = 'Bearer ' + await u.getIdToken();\n  if (body !== undefined) headers['content-type'] = 'application/json';\n  let res;\n  try {\n    res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), cache: 'no-store' });\n  } catch (e) {\n    throw new ApiError(0, 'network', '\u05D0\u05D9\u05DF \u05D7\u05D9\u05D1\u05D5\u05E8 \u05DC\u05E9\u05E8\u05EA. \u05D1\u05D5\u05D3\u05E7\u05D9\u05DD \u05D0\u05EA \u05D4\u05D0\u05D9\u05E0\u05D8\u05E8\u05E0\u05D8 \u05D5\u05DE\u05E0\u05E1\u05D9\u05DD \u05E9\u05D5\u05D1.');\n  }\n  const j = await res.json().catch(() => ({}));\n  if (!res.ok || j.ok === false) throw new ApiError(res.status, j.code || 'error', j.error || '\u05DE\u05E9\u05D4\u05D5 \u05D4\u05E9\u05EA\u05D1\u05E9. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1.');\n  return j;\n}\n\n/** h('div', {class: 'x', onclick: fn}, '\u05D8\u05E7\u05E1\u05D8', child...) \u2014 \u05D8\u05E7\u05E1\u05D8 \u05EA\u05DE\u05D9\u05D3 \u05DB\u05D8\u05E7\u05E1\u05D8, \u05D0\u05E3 \u05E4\u05E2\u05DD \u05DC\u05D0 \u05DB-HTML */\nexport function h(tag, attrs, ...kids) {\n  const el = document.createElement(tag);\n  for (const [k, v] of Object.entries(attrs || {})) {\n    if (v === undefined || v === null || v === false) continue;\n    if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v);\n    else if (k === 'class') el.className = v;\n    else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);\n    else if (k === 'html') throw new Error('no html');\n    else el.setAttribute(k, v === true ? '' : String(v));\n  }\n  for (const kid of kids.flat(Infinity)) {\n    if (kid === undefined || kid === null || kid === false) continue;\n    el.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));\n  }\n  return el;\n}\n\nconst SVG_NS = 'http://www.w3.org/2000/svg';\n/** \u05D0\u05D9\u05D9\u05E7\u05D5\u05DF \u05E7\u05D5 (24\xD724) \u05DE\u05EA\u05D5\u05DA \u05E8\u05E9\u05D9\u05DE\u05EA path-\u05D9\u05DD */\nexport function icon(paths, size = 18, color = 'currentColor', width = 2) {\n  const s = document.createElementNS(SVG_NS, 'svg');\n  s.setAttribute('width', size); s.setAttribute('height', size); s.setAttribute('viewBox', '0 0 24 24');\n  s.setAttribute('fill', 'none'); s.setAttribute('stroke', color); s.setAttribute('stroke-width', width);\n  s.setAttribute('stroke-linecap', 'round'); s.setAttribute('stroke-linejoin', 'round'); s.setAttribute('aria-hidden', 'true');\n  for (const d of paths) {\n    const tag = d.startsWith('c:') ? 'circle' : d.startsWith('r:') ? 'rect' : 'path';\n    const e = document.createElementNS(SVG_NS, tag);\n    if (tag === 'circle') { const [cx, cy, r] = d.slice(2).split(','); e.setAttribute('cx', cx); e.setAttribute('cy', cy); e.setAttribute('r', r); }\n    else if (tag === 'rect') { const [x, y, w, hh, rx] = d.slice(2).split(','); e.setAttribute('x', x); e.setAttribute('y', y); e.setAttribute('width', w); e.setAttribute('height', hh); e.setAttribute('rx', rx || 0); }\n    else e.setAttribute('d', d);\n    s.append(e);\n  }\n  return s;\n}\nexport const ICONS = {\n  book: ['M4 19V5a2 2 0 0 1 2-2h12v18H6a2 2 0 0 1-2-2z', 'M8 7h6', 'M8 11h4'],\n  shield: ['M12 3l8 4v5c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V7z'],\n  grid: ['r:3,3,7,9,1.5', 'r:14,3,7,5,1.5', 'r:14,12,7,9,1.5', 'r:3,16,7,5,1.5'],\n  msg: ['M4 4h16v12H8l-4 4z'],\n  user: ['c:12,8,4', 'M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6'],\n  cap: ['M2 9l10-5 10 5-10 5z', 'M6 11v5c3 2 9 2 12 0v-5'],\n  list: ['M8 6h13', 'M8 12h13', 'M8 18h13', 'M3 6h.01', 'M3 12h.01', 'M3 18h.01'],\n  gear: ['c:12,12,3', 'M12 2v3', 'M12 19v3', 'M2 12h3', 'M19 12h3', 'M4.9 4.9l2.1 2.1', 'M17 17l2.1 2.1', 'M4.9 19.1L7 17', 'M17 7l2.1-2.1'],\n  search: ['c:11,11,7', 'M20 20l-3.5-3.5'],\n  plus: ['M12 5v14', 'M5 12h14'],\n  minus: ['M5 12h14'],\n  x: ['M6 6l12 12', 'M18 6L6 18'],\n  check: ['M5 12.5l4.5 4.5L19 7.5'],\n  lock: ['r:5,11,14,10,2', 'M8 11V8a4 4 0 0 1 8 0v3'],\n  info: ['c:12,12,9', 'M12 8h.01', 'M11 12h1v5h1'],\n  monitor: ['r:3,4,18,12,2', 'M8 20h8', 'M12 16v4'],\n  clock: ['c:12,12,9', 'M12 7v5l3.5 2'],\n  bell: ['M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9', 'M10.3 21a1.94 1.94 0 0 0 3.4 0'],\n};\n\nexport function googleLogo() {\n  const s = document.createElementNS(SVG_NS, 'svg');\n  s.setAttribute('width', 20); s.setAttribute('height', 20); s.setAttribute('viewBox', '0 0 48 48'); s.setAttribute('aria-hidden', 'true');\n  for (const [fill, d] of [\n    ['#FFC107', 'M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z'],\n    ['#FF3D00', 'M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z'],\n    ['#4CAF50', 'M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z'],\n    ['#1976D2', 'M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z'],\n  ]) { const p = document.createElementNS(SVG_NS, 'path'); p.setAttribute('fill', fill); p.setAttribute('d', d); s.append(p); }\n  return s;\n}\n\nexport function avatar(user, dark) {\n  const a = h('div', { class: 'avatar', style: dark ? { background: '#17213A' } : null });\n  const letter = String((user && (user.displayName || user.email)) || '?').trim().charAt(0).toUpperCase();\n  if (user && user.photoURL) {\n    const img = h('img', { src: user.photoURL, alt: '', referrerpolicy: 'no-referrer' });\n    img.onerror = () => { img.remove(); a.textContent = letter; };\n    a.append(img);\n  } else a.textContent = letter;\n  return a;\n}\n\nconst DAY = 86400000;\nexport function fmtDate(iso) {\n  if (!iso) return '\u2014';\n  const d = new Date(/^\\d{4}-\\d{2}-\\d{2}$/.test(iso) ? iso + 'T12:00:00' : iso);\n  return isNaN(d) ? '\u2014' : `${d.getDate()}.${d.getMonth() + 1}.${d.getFullYear()}`;\n}\nexport function fmtTime(iso) {\n  const d = new Date(iso);\n  return isNaN(d) ? '' : d.toLocaleTimeString('he-IL', { hour: '2-digit', minute: '2-digit' });\n}\n/** \"\u05D4\u05D9\u05D5\u05DD\" / \"\u05D0\u05EA\u05DE\u05D5\u05DC\" / \"\u05DC\u05E4\u05E0\u05D9 3 \u05D9\u05DE\u05D9\u05DD\" / \u05EA\u05D0\u05E8\u05D9\u05DA */\nexport function ago(iso) {\n  if (!iso) return '\u2014';\n  const d = new Date(iso);\n  if (isNaN(d)) return '\u2014';\n  const start = new Date(); start.setHours(0, 0, 0, 0);\n  const days = Math.floor((start - d) / DAY) + 1;\n  if (d >= start) {\n    const min = Math.floor((Date.now() - d) / 60000);\n    if (min < 1) return '\u05E2\u05DB\u05E9\u05D9\u05D5';\n    if (min < 60) return `\u05DC\u05E4\u05E0\u05D9 ${min} \u05D3\u05E7\u05D5\u05EA`;\n    return '\u05D4\u05D9\u05D5\u05DD, ' + fmtTime(iso);\n  }\n  if (days === 1) return '\u05D0\u05EA\u05DE\u05D5\u05DC, ' + fmtTime(iso);\n  if (days < 30) return `\u05DC\u05E4\u05E0\u05D9 ${days} \u05D9\u05DE\u05D9\u05DD`;\n  return fmtDate(iso);\n}\nexport const daysSince = iso => (iso ? Math.floor((Date.now() - new Date(iso)) / DAY) : 9999);\n\nlet toastTimer = null;\nexport function toast(msg, err) {\n  let t = document.querySelector('.toast');\n  if (!t) { t = h('div', { class: 'toast', role: 'status', 'aria-live': 'polite' }); document.body.append(t); }\n  t.className = 'toast' + (err ? ' err' : '');\n  t.textContent = msg;\n  t.hidden = false;\n  clearTimeout(toastTimer);\n  toastTimer = setTimeout(() => { t.hidden = true; }, err ? 6000 : 3200);\n}\n\n/** \u05D7\u05DC\u05D5\u05DF \u05D0\u05D9\u05E9\u05D5\u05E8 \u2014 \u05DE\u05D7\u05D6\u05D9\u05E8 true / false */\nexport function ask({ title, text, ok = '\u05D0\u05D9\u05E9\u05D5\u05E8', danger = false, cancel = '\u05D1\u05D9\u05D8\u05D5\u05DC' }) {\n  return new Promise(resolve => {\n    const d = h('dialog', { class: 'modal', 'aria-labelledby': 'ask-t' });\n    const done = v => { d.close(); d.remove(); resolve(v); };\n    d.append(h('div', { class: 'in' },\n      h('h2', { id: 'ask-t' }, title),\n      text ? h('p', { class: 'muted', style: { lineHeight: '1.55' } }, text) : null,\n      h('div', { class: 'row', style: { justifyContent: 'flex-start', marginTop: '6px' } },\n        h('button', { type: 'button', class: 'btn ' + (danger ? 'danger' : 'primary'), onclick: () => done(true) }, ok),\n        h('button', { type: 'button', class: 'btn', onclick: () => done(false) }, cancel))));\n    d.addEventListener('cancel', e => { e.preventDefault(); done(false); });\n    document.body.append(d);\n    d.showModal();\n  });\n}\n\n/** \u05DB\u05E4\u05EA\u05D5\u05E8 \u05E9\u05DE\u05E8\u05D0\u05D4 \"\u05E8\u05D2\u05E2...\" \u05D1\u05D6\u05DE\u05DF \u05D4\u05E4\u05E2\u05D5\u05DC\u05D4, \u05D5\u05DE\u05E6\u05D9\u05D2 \u05E9\u05D2\u05D9\u05D0\u05D4 \u05D1\u05D4\u05D5\u05D3\u05E2\u05D4 */\nexport async function busy(btn, fn) {\n  const label = btn.textContent;\n  btn.disabled = true;\n  btn.setAttribute('aria-busy', 'true');\n  try { return await fn(); } catch (e) { toast(e.message || String(e), true); return undefined; } finally {\n    btn.disabled = false; btn.removeAttribute('aria-busy'); if (btn.textContent === '\u05E8\u05D2\u05E2\u2026') btn.textContent = label;\n  }\n}\n\nexport function topBar(user, { badge, onLogout, dark } = {}) {\n  return h('header', { class: 'top' },\n    h('div', { class: 'brand' }, h('div', { class: 'logo' + (dark ? ' dark' : '') }, icon(ICONS.book, 20, '#fff')), '\u05DE\u05D0\u05D2\u05E8 \u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD', badge || null),\n    user ? h('div', { class: 'who' },\n      h('div', { class: 'meta' }, h('span', { class: 'ltr' }, user.email), onLogout ? h('a', { href: '#', onclick: e => { e.preventDefault(); onLogout(); } }, '\u05D4\u05EA\u05E0\u05EA\u05E7\u05D5\u05EA') : null),\n      avatar(user)) : h('span'));\n}\n", "/signin.html": '<!doctype html>\n<html lang="he" dir="rtl">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1">\n<title>\u05DB\u05E0\u05D9\u05E1\u05D4 \u2014 \u05DE\u05D0\u05D2\u05E8 \u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD</title>\n<link rel="preconnect" href="https://fonts.googleapis.com">\n<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Assistant:wght@400;600;700&family=Rubik:wght@500;600;700&display=swap">\n<link rel="stylesheet" href="/static/app.css">\n</head>\n<body>\n<div id="app"><div class="loading">\u05E8\u05D2\u05E2\u2026</div></div>\n<script type="module" src="/static/signin.js"></script>\n</body>\n</html>\n', "/signin.js": `// /signin \u2014 \u05E0\u05E4\u05EA\u05D7 \u05DE\u05EA\u05D5\u05DA DSViz (\u05DE\u05D5\u05E8\u05D4 / \u05EA\u05DC\u05DE\u05D9\u05D3). \u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05DD Google \u2192 \u05E7\u05D5\u05D3 \u05D7\u05D3-\u05E4\u05E2\u05DE\u05D9 \u05DE\u05D4\u05E9\u05E8\u05EA \u2192 \u05D7\u05D6\u05E8\u05D4 \u05DC\u05EA\u05D5\u05DB\u05E0\u05D4 (http://127.0.0.1:port)
import { boot, googleSignIn, api, h, icon, ICONS, googleLogo, topBar } from '/static/common.js';

const app = document.getElementById('app');
const q = new URLSearchParams(location.search);
const role = q.get('role') === 'teacher' ? 'teacher' : 'student';
const port = +q.get('port');
const state = q.get('state') || '';
const challenge = q.get('challenge') || '';
const fromApp = Number.isInteger(port) && port >= 1024 && port <= 65535 && /^[A-Za-z0-9_-]{16,64}$/.test(state) && /^[A-Za-z0-9_-]{43}$/.test(challenge);

function show(...kids) {
  app.replaceChildren(topBar(null), h('main', { class: 'center' }, h('div', { class: 'panel' }, ...kids)));
}

function backToApp(code) {
  show(h('div', { class: 'row', style: { gap: '14px' } }, h('div', { class: 'spinner', style: { width: '32px', height: '32px', borderWidth: '4px' } }), h('h1', { style: { fontSize: '22px' } }, '\u05DE\u05D7\u05D6\u05D9\u05E8\u05D9\u05DD \u05D0\u05D5\u05EA\u05DA \u05DC\u05EA\u05D5\u05DB\u05E0\u05D4\u2026')),
    h('p', { class: 'muted' }, '\u05D0\u05DD \u05D4\u05D3\u05E3 \u05DC\u05D0 \u05DE\u05E9\u05EA\u05E0\u05D4 \u05EA\u05D5\u05DA \u05DB\u05DE\u05D4 \u05E9\u05E0\u05D9\u05D5\u05EA \u2014 \u05D7\u05D5\u05D6\u05E8\u05D9\u05DD \u05DC-DSViz, \u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05DB\u05D1\u05E8 \u05E9\u05DD.'));
  location.replace(\`http://127.0.0.1:\${port}/done?code=\${encodeURIComponent(code)}&state=\${encodeURIComponent(state)}\`);
}

async function main() {
  try { await boot(); } catch (e) {
    show(h('h1', null, '\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E4\u05EA\u05D5\u05D7\u05D4'), h('p', { class: 'muted' }, '\u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1 \u05DE\u05D0\u05D5\u05D7\u05E8 \u05D9\u05D5\u05EA\u05E8.'));
    return;
  }
  if (!fromApp) {
    show(h('h1', null, '\u05E4\u05D5\u05EA\u05D7\u05D9\u05DD \u05D0\u05EA \u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05DE\u05EA\u05D5\u05DA DSViz'),
      h('p', { class: 'muted', style: { lineHeight: '1.6' } }, '\u05D1\u05EA\u05D5\u05DB\u05E0\u05D4 \u05E4\u05D5\u05EA\u05D7\u05D9\u05DD \u05D0\u05EA \u05DE\u05D0\u05D2\u05E8 \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD, \u05D1\u05D5\u05D7\u05E8\u05D9\u05DD "\u05DE\u05D5\u05E8\u05D4" \u05D0\u05D5 "\u05EA\u05DC\u05DE\u05D9\u05D3" \u05D5\u05DC\u05D5\u05D7\u05E6\u05D9\u05DD "\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05DD Google" \u2014 \u05D5\u05D4\u05D3\u05E3 \u05D4\u05D6\u05D4 \u05E0\u05E4\u05EA\u05D7 \u05E2\u05DD \u05D4\u05DB\u05D5\u05DC \u05DE\u05D5\u05DB\u05DF. \u05DB\u05D0\u05DF \u05D1\u05D3\u05E4\u05D3\u05E4\u05DF \u05D0\u05E4\u05E9\u05E8 \u05DC\u05E8\u05D0\u05D5\u05EA \u05D0\u05EA \u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05E9\u05DC\u05DA:'),
      h('a', { class: 'btn primary lg', href: '/account' }, '\u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05E9\u05DC\u05D9'));
    return;
  }
  const err = h('p', { role: 'alert', class: 'warn-box', hidden: true });
  const btn = h('button', { type: 'button', class: 'gbtn' }, googleLogo(), '\u05D4\u05EA\u05D7\u05D1\u05E8\u05D5\u05EA \u05E2\u05DD Google');
  btn.addEventListener('click', async () => {
    err.hidden = true;
    btn.disabled = true;
    try {
      const user = await googleSignIn();
      const r = await api('POST', '/v1/auth/exchange', { idToken: await user.getIdToken(), role, challenge });
      backToApp(r.code);
    } catch (e) {
      btn.disabled = false;
      const closed = /popup-closed|cancelled-popup|user-cancelled/.test(String(e && e.code));
      const blocked = /popup-blocked/.test(String(e && e.code));
      err.textContent = closed ? '\u05D4\u05D7\u05DC\u05D5\u05DF \u05E9\u05DC Google \u05E0\u05E1\u05D2\u05E8 \u05DC\u05E4\u05E0\u05D9 \u05E9\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05D4\u05E1\u05EA\u05D9\u05D9\u05DE\u05D4. \u05DC\u05D5\u05D7\u05E6\u05D9\u05DD \u05E9\u05D5\u05D1.'
        : blocked ? '\u05D4\u05D3\u05E4\u05D3\u05E4\u05DF \u05D7\u05E1\u05DD \u05D0\u05EA \u05D4\u05D7\u05DC\u05D5\u05DF \u05E9\u05DC Google. \u05DE\u05D0\u05E9\u05E8\u05D9\u05DD \u05D7\u05DC\u05D5\u05E0\u05D5\u05EA \u05E7\u05D5\u05E4\u05E6\u05D9\u05DD \u05DC\u05D0\u05EA\u05E8 \u05D4\u05D6\u05D4 \u05D5\u05DC\u05D5\u05D7\u05E6\u05D9\u05DD \u05E9\u05D5\u05D1.'
        : (e && e.message) || '\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05DC\u05D0 \u05D4\u05E6\u05DC\u05D9\u05D7\u05D4. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1.';
      err.hidden = false;
    }
  });
  const teacher = role === 'teacher';
  show(
    h('div', { class: 'badge-icon blue', style: { width: '56px', height: '56px' } }, icon(teacher ? ICONS.user : ICONS.cap, 28, '#1E4FB8')),
    h('h1', null, teacher ? '\u05DB\u05E0\u05D9\u05E1\u05EA \u05DE\u05D5\u05E8\u05D4' : '\u05DB\u05E0\u05D9\u05E1\u05EA \u05EA\u05DC\u05DE\u05D9\u05D3'),
    h('p', { class: 'muted', style: { lineHeight: '1.6', fontSize: '16px' } }, teacher
      ? '\u05E0\u05DB\u05E0\u05E1\u05D9\u05DD \u05E2\u05DD \u05D7\u05E9\u05D1\u05D5\u05DF Google \u05E9\u05D4\u05D4\u05E0\u05D4\u05DC\u05D4 \u05D4\u05D2\u05D3\u05D9\u05E8\u05D4 \u05DB\u05DE\u05D5\u05E8\u05D4. \u05D0\u05D7\u05E8\u05D9 \u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05D4\u05D3\u05E4\u05D3\u05E4\u05DF \u05DE\u05D7\u05D6\u05D9\u05E8 \u05D0\u05D5\u05EA\u05DA \u05DC\u05EA\u05D5\u05DB\u05E0\u05D4, \u05D5\u05D4\u05DE\u05D7\u05E9\u05D1 \u05D6\u05D5\u05DB\u05E8.'
      : '\u05E0\u05DB\u05E0\u05E1\u05D9\u05DD \u05E2\u05DD \u05D7\u05E9\u05D1\u05D5\u05DF Google. \u05D0\u05DD \u05DB\u05D1\u05E8 \u05E9\u05D9\u05DC\u05DE\u05EA \u2014 \u05D4\u05DE\u05D0\u05D2\u05E8 \u05E0\u05E4\u05EA\u05D7 \u05DE\u05D9\u05D3, \u05D2\u05DD \u05D1\u05DE\u05D7\u05E9\u05D1 \u05D0\u05D7\u05E8. \u05D0\u05D7\u05E8\u05D9 \u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05D4\u05DE\u05D7\u05E9\u05D1 \u05D6\u05D5\u05DB\u05E8.'),
    btn, err,
    h('p', { class: 'small muted' }, '\u05DC\u05D0 \u05DE\u05E7\u05DC\u05D9\u05D3\u05D9\u05DD \u05E1\u05D9\u05E1\u05DE\u05D4 \u05D1\u05EA\u05D5\u05DA \u05D4\u05EA\u05D5\u05DB\u05E0\u05D4 \u2014 \u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05DD Google \u05E0\u05E2\u05E9\u05D9\u05EA \u05D0\u05E6\u05DC Google.'));
}
main();
`, "/welcome.html": '<!doctype html>\n<html lang="he" dir="rtl">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1">\n<title>\u05DE\u05D0\u05D2\u05E8 \u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD</title>\n<link rel="preconnect" href="https://fonts.googleapis.com">\n<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Assistant:wght@400;600;700&family=Rubik:wght@500;600;700&display=swap">\n<link rel="stylesheet" href="/static/app.css">\n</head>\n<body>\n<div id="app"><div class="loading">\u05E8\u05D2\u05E2\u2026</div></div>\n<script type="module" src="/static/welcome.js"></script>\n</body>\n</html>\n', "/welcome.js": `// /welcome \u2014 \u05D0\u05D7\u05E8\u05D9 \u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05DE\u05EA\u05D5\u05DA \u05D4\u05EA\u05D5\u05DB\u05E0\u05D4 (\u05D4\u05EA\u05D5\u05E1\u05E3 \u05DE\u05E4\u05E0\u05D4 \u05DC\u05DB\u05D0\u05DF \u05E2\u05DD \u05D4\u05DE\u05E6\u05D1): \u05D4\u05E6\u05DC\u05D7\u05D4 / \u05EA\u05E9\u05DC\u05D5\u05DD / \u05D1\u05E7\u05E9\u05EA \u05D4\u05E8\u05E9\u05D0\u05EA \u05DE\u05D5\u05E8\u05D4 / \u05DE\u05D7\u05E9\u05D1\u05D9\u05DD
import { boot, currentUser, googleSignIn, api, h, icon, ICONS, googleLogo, topBar, fmtDate, ago, busy, toast, logoutWeb } from '/static/common.js';

const app = document.getElementById('app');
const q = new URLSearchParams(location.search);
const r = q.get('r') || 'ok';
const role = q.get('role') === 'teacher' ? 'teacher' : 'student';
let user = null;

function show(...kids) {
  app.replaceChildren(topBar(user, { onLogout: async () => { await logoutWeb(); location.href = '/account'; } }),
    h('main', { class: 'center' }, h('div', { class: 'panel c' }, ...kids)));
}
const done = (title, text, ...more) => show(
  h('div', { class: 'badge-icon green', style: { width: '72px', height: '72px' } }, icon(ICONS.check, 38, '#1F7A44', 2.4)),
  h('h1', null, title), h('p', { class: 'muted', style: { fontSize: '16px', lineHeight: '1.6' } }, text), ...more);

async function needUser() {
  user = await currentUser();
  if (user) return user;
  return new Promise(resolve => {
    const b = h('button', { type: 'button', class: 'gbtn' }, googleLogo(), '\u05D4\u05EA\u05D7\u05D1\u05E8\u05D5\u05EA \u05E2\u05DD Google');
    b.onclick = () => busy(b, async () => { user = await googleSignIn(); resolve(user); });
    show(h('h1', null, '\u05E2\u05D5\u05D3 \u05E6\u05E2\u05D3 \u05D0\u05D7\u05D3'), h('p', { class: 'muted' }, '\u05E0\u05DB\u05E0\u05E1\u05D9\u05DD \u05E9\u05D5\u05D1 \u05E2\u05DD \u05D0\u05D5\u05EA\u05D5 \u05D7\u05E9\u05D1\u05D5\u05DF Google \u05DB\u05D3\u05D9 \u05DC\u05D4\u05DE\u05E9\u05D9\u05DA.'), b);
  });
}

function openScreen(me) {
  done('\u05D4\u05DE\u05D0\u05D2\u05E8 \u05E4\u05EA\u05D5\u05D7', \`\u05DE\u05E2\u05DB\u05E9\u05D9\u05D5 \u05D4\u05DE\u05D7\u05E9\u05D1 \u05D6\u05D5\u05DB\u05E8 \u05D0\u05D5\u05EA\u05DA: \u05D7\u05D5\u05D6\u05E8\u05D9\u05DD \u05DC-DSViz \u05D5\u05E4\u05D5\u05EA\u05D7\u05D9\u05DD \u05D0\u05EA \u05DE\u05D0\u05D2\u05E8 \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD.\${me && me.student && me.student.until ? ' \u05E4\u05EA\u05D5\u05D7 \u05E2\u05D3 ' + fmtDate(me.student.until) + '.' : ''}\`);
}

async function pay() {
  await needUser();
  const me = await api('GET', '/v1/me');
  if (me.student.open) return openScreen(me);
  if (!me.canPay) {
    return show(h('div', { class: 'badge-icon orange' }, icon(ICONS.info, 30, '#9A3412')), h('h1', null, '\u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E4\u05EA\u05D5\u05D7'),
      h('p', { class: 'muted' }, '\u05E2\u05D5\u05D3 \u05DE\u05E2\u05D8 \u05D0\u05E4\u05E9\u05E8 \u05D9\u05D4\u05D9\u05D4 \u05DC\u05E9\u05DC\u05DD \u05DB\u05D0\u05DF. \u05D1\u05D9\u05E0\u05EA\u05D9\u05D9\u05DD, \u05D0\u05DD \u05D4\u05DE\u05D5\u05E8\u05D4 \u05E4\u05EA\u05D7 \u05E2\u05DE\u05D3\u05D4 \u05D1\u05DB\u05D9\u05EA\u05D4 \u2014 \u05D4\u05DE\u05D0\u05D2\u05E8 \u05E4\u05EA\u05D5\u05D7 \u05E9\u05DD.'));
  }
  const paypal = me.payBy === 'paypal';
  const go = h('button', { type: 'button', class: 'btn primary lg wide' }, icon(ICONS.lock, 20, '#fff'), paypal ? '\u05DE\u05E2\u05D1\u05E8 \u05DC\u05EA\u05E9\u05DC\u05D5\u05DD \u05DE\u05D0\u05D5\u05D1\u05D8\u05D7 \u05D1-PayPal' : '\u05DE\u05E2\u05D1\u05E8 \u05DC\u05EA\u05E9\u05DC\u05D5\u05DD \u05DE\u05D0\u05D5\u05D1\u05D8\u05D7');
  go.onclick = () => busy(go, async () => {
    const s = await api('POST', '/v1/pay/start', {});
    if (s.already) return openScreen(await api('GET', '/v1/me'));
    location.href = s.url;
  });
  show(
    h('div', { class: 'stack', style: { gap: '6px', textAlign: 'right', width: '100%' } },
      h('h1', null, '\u05E4\u05EA\u05D9\u05D7\u05EA \u05D4\u05DE\u05D0\u05D2\u05E8 \u05DC\u05E9\u05E0\u05EA \u05D4\u05DC\u05D9\u05DE\u05D5\u05D3\u05D9\u05DD'), h('p', { class: 'muted', style: { fontSize: '16px' } }, '\u05EA\u05E9\u05DC\u05D5\u05DD \u05D0\u05D7\u05D3 \u2014 \u05D1\u05DC\u05D9 \u05DE\u05E0\u05D5\u05D9 \u05DE\u05EA\u05D7\u05D3\u05E9 \u05D5\u05D1\u05DC\u05D9 \u05D7\u05D9\u05D5\u05D1\u05D9\u05DD \u05E0\u05D5\u05E1\u05E4\u05D9\u05DD.')),
    h('div', { class: 'price' },
      h('div', { class: 'stack', style: { gap: '4px' } }, h('span', { class: 'display', style: { fontWeight: 600, fontSize: '18px' } }, '\u05DE\u05D0\u05D2\u05E8 \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD'),
        h('span', { class: 'small muted' }, \`\u05E4\u05EA\u05D5\u05D7 \u05E2\u05D3 \${fmtDate(me.accessUntil)} \xB7 \u05D1\u05DB\u05DC \u05D4\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05E9\u05DC\u05DA\`)),
      h('div', { class: 'row', style: { gap: '4px', alignItems: 'baseline' } }, h('span', { class: 'display', style: { fontWeight: 700, fontSize: '32px' } }, String(me.price)), h('span', { style: { fontSize: '18px', fontWeight: 600 } }, '\u20AA'))),
    h('div', { class: 'stack', style: { gap: '8px', width: '100%', textAlign: 'right' } }, h('span', { class: 'small', style: { fontWeight: 700 } }, '\u05D0\u05E4\u05E9\u05E8 \u05DC\u05E9\u05DC\u05DD \u05D1:'),
      h('div', { class: 'chips' }, (paypal ? ['PayPal', '\u05DB\u05E8\u05D8\u05D9\u05E1 \u05D0\u05E9\u05E8\u05D0\u05D9 (\u05D3\u05E8\u05DA PayPal)'] : ['\u05D1\u05D9\u05D8', '\u05DB\u05E8\u05D8\u05D9\u05E1 \u05D0\u05E9\u05E8\u05D0\u05D9', 'Apple Pay', 'Google Pay']).map(x => h('span', { class: 'chip' }, x)))),
    go,
    h('p', { class: 'small muted', style: { textAlign: 'right' } }, paypal
      ? '\u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05E0\u05E2\u05E9\u05D4 \u05D1\u05D3\u05E3 \u05D4\u05DE\u05D0\u05D5\u05D1\u05D8\u05D7 \u05E9\u05DC PayPal \u2014 \u05D0\u05E0\u05D7\u05E0\u05D5 \u05DC\u05D0 \u05E8\u05D5\u05D0\u05D9\u05DD \u05D0\u05EA \u05E4\u05E8\u05D8\u05D9 \u05D4\u05DB\u05E8\u05D8\u05D9\u05E1 \u05D0\u05D5 \u05D4\u05D7\u05E9\u05D1\u05D5\u05DF. \u05D0\u05D9\u05E9\u05D5\u05E8 \u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05E0\u05E9\u05DC\u05D7 \u05DE-PayPal \u05DC\u05DE\u05D9\u05D9\u05DC.'
      : '\u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05E0\u05E2\u05E9\u05D4 \u05D1\u05D3\u05E3 \u05D4\u05DE\u05D0\u05D5\u05D1\u05D8\u05D7 \u05E9\u05DC \u05D7\u05D1\u05E8\u05EA \u05D4\u05E1\u05DC\u05D9\u05E7\u05D4 \u2014 \u05D0\u05E0\u05D7\u05E0\u05D5 \u05DC\u05D0 \u05E8\u05D5\u05D0\u05D9\u05DD \u05D0\u05EA \u05E4\u05E8\u05D8\u05D9 \u05D4\u05DB\u05E8\u05D8\u05D9\u05E1. \u05E7\u05D1\u05DC\u05D4 \u05E0\u05E9\u05DC\u05D7\u05EA \u05D0\u05D5\u05D8\u05D5\u05DE\u05D8\u05D9\u05EA \u05DC\u05DE\u05D9\u05D9\u05DC.'));
}

async function waitPaid() {
  await needUser();
  show(h('div', { class: 'spinner' }), h('h1', null, '\u05DE\u05D7\u05DB\u05D9\u05DD \u05DC\u05D0\u05D9\u05E9\u05D5\u05E8 \u05D4\u05EA\u05E9\u05DC\u05D5\u05DD'),
    h('p', { class: 'muted', style: { lineHeight: '1.6' } }, '\u05D6\u05D4 \u05DC\u05D5\u05E7\u05D7 \u05D1\u05D3\u05E8\u05DA \u05DB\u05DC\u05DC \u05DB\u05DE\u05D4 \u05E9\u05E0\u05D9\u05D5\u05EA. \u05D2\u05DD \u05D4\u05D7\u05DC\u05D5\u05DF \u05D1\u05EA\u05D5\u05DB\u05E0\u05D4 \u05DE\u05EA\u05E2\u05D3\u05DB\u05DF \u05DC\u05D1\u05D3 \u2014 \u05DC\u05D0 \u05E6\u05E8\u05D9\u05DA \u05DC\u05DC\u05D7\u05D5\u05E5 \u05E2\u05DC \u05DB\u05DC\u05D5\u05DD.'));
  for (let i = 0; i < 60; i++) {
    const me = await api('GET', '/v1/me').catch(() => null);
    if (me && me.student.open) return openScreen(me);
    await new Promise(res => setTimeout(res, 3000));
  }
  show(h('h1', null, '\u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05D0\u05D5\u05E9\u05E8'), h('p', { class: 'muted' }, '\u05D0\u05DD \u05D7\u05D5\u05D9\u05D1\u05EA \u2014 \u05D4\u05D0\u05D9\u05E9\u05D5\u05E8 \u05D9\u05D2\u05D9\u05E2 \u05D1\u05D3\u05E7\u05D5\u05EA \u05D4\u05E7\u05E8\u05D5\u05D1\u05D5\u05EA, \u05D5\u05D4\u05DE\u05D0\u05D2\u05E8 \u05D9\u05D9\u05E4\u05EA\u05D7 \u05DC\u05D1\u05D3. \u05D0\u05E4\u05E9\u05E8 \u05DC\u05E1\u05D2\u05D5\u05E8 \u05D0\u05EA \u05D4\u05D3\u05E3.'),
    h('a', { class: 'btn', href: '/welcome?r=paid' }, '\u05DC\u05D1\u05D3\u05D5\u05E7 \u05E9\u05D5\u05D1'));
}

async function notTeacher() {
  await needUser();
  const school = h('input', { class: 'field', type: 'text', placeholder: '\u05E9\u05DD \u05D1\u05D9\u05EA \u05D4\u05E1\u05E4\u05E8 \u05D5\u05D4\u05E2\u05D9\u05E8', id: 'school' });
  const computers = h('input', { class: 'field', type: 'number', min: '0', max: '2000', placeholder: '30', id: 'computers' });
  const phone = h('input', { class: 'field ltr', type: 'tel', inputmode: 'numeric', placeholder: '050-1234567', id: 'phone', autocomplete: 'tel', style: { textAlign: 'left' } });
  const send = h('button', { type: 'button', class: 'btn primary lg wide' }, '\u05E9\u05DC\u05D9\u05D7\u05EA \u05D1\u05E7\u05E9\u05D4 \u05DC\u05D4\u05E0\u05D4\u05DC\u05D4');
  send.onclick = () => busy(send, async () => {
    if (!school.value.trim()) { school.focus(); return toast('\u05DB\u05EA\u05D1\u05D5 \u05D0\u05EA \u05E9\u05DD \u05D1\u05D9\u05EA \u05D4\u05E1\u05E4\u05E8.', true); }
    let ph = phone.value.replace(/[\\s\\-().]/g, '').replace(/^\\+?972/, '0');
    if (!/^05\\d{8}$/.test(ph)) { phone.focus(); return toast('\u05DB\u05EA\u05D1\u05D5 \u05E0\u05D9\u05D9\u05D3 \u05EA\u05E7\u05E0\u05D9 \u05E9\u05DC 10 \u05E1\u05E4\u05E8\u05D5\u05EA, \u05DC\u05DE\u05E9\u05DC 050-1234567.', true); }
    await api('POST', '/v1/me/request', { kind: 'teacher', school: school.value, phone: ph, computers: +computers.value || 0 });
    done('\u05D4\u05D1\u05E7\u05E9\u05D4 \u05E0\u05E9\u05DC\u05D7\u05D4', '\u05D0\u05D7\u05E8\u05D9 \u05E9\u05D4\u05D4\u05E0\u05D4\u05DC\u05D4 \u05DE\u05D0\u05E9\u05E8\u05EA, \u05E0\u05DB\u05E0\u05E1\u05D9\u05DD \u05E9\u05D5\u05D1 \u05DE\u05EA\u05D5\u05DA DSViz \u05E2\u05DD \u05D0\u05D5\u05EA\u05D5 \u05D7\u05E9\u05D1\u05D5\u05DF \u2014 \u05D5\u05D4\u05DC\u05D5\u05D7 \u05E9\u05DC \u05D4\u05DE\u05D5\u05E8\u05D4 \u05E0\u05E4\u05EA\u05D7.');
  });
  show(
    h('div', { class: 'badge-icon blue', style: { width: '56px', height: '56px' } }, icon(ICONS.info, 28, '#1E4FB8')),
    h('h1', null, '\u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05DE\u05D5\u05D2\u05D3\u05E8 \u05DB\u05DE\u05D5\u05E8\u05D4'),
    h('p', { class: 'muted', style: { lineHeight: '1.6' } }, '\u05D0\u05EA \u05D4\u05DE\u05D5\u05E8\u05D9\u05DD \u05DE\u05D2\u05D3\u05D9\u05E8\u05D4 \u05D4\u05D4\u05E0\u05D4\u05DC\u05D4. \u05D0\u05E4\u05E9\u05E8 \u05DC\u05E9\u05DC\u05D5\u05D7 \u05D1\u05E7\u05E9\u05D4 \u05DE\u05DB\u05D0\u05DF \u2014 \u05D0\u05D7\u05E8\u05D9 \u05D4\u05D0\u05D9\u05E9\u05D5\u05E8 \u05E0\u05DB\u05E0\u05E1\u05D9\u05DD \u05E9\u05D5\u05D1 \u05E2\u05DD \u05D0\u05D5\u05EA\u05D5 \u05D7\u05E9\u05D1\u05D5\u05DF.'),
    h('div', { class: 'stack', style: { width: '100%', textAlign: 'right', gap: '12px' } },
      h('label', { class: 'lbl', for: 'school' }, '\u05D1\u05D9\u05EA \u05D4\u05E1\u05E4\u05E8', school),
      h('label', { class: 'lbl', for: 'phone' }, '\u05D8\u05DC\u05E4\u05D5\u05DF \u05E0\u05D9\u05D9\u05D3 (10 \u05E1\u05E4\u05E8\u05D5\u05EA)', phone),
      h('label', { class: 'lbl', for: 'computers' }, '\u05DB\u05DE\u05D4 \u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05D9\u05E9 \u05D1\u05DB\u05D9\u05EA\u05D4 (\u05D1\u05E2\u05E8\u05DA)', computers)),
    send,
    h('a', { href: '#', class: 'small', onclick: async e => { e.preventDefault(); await logoutWeb(); user = null; notTeacher(); } }, '\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05DD \u05D7\u05E9\u05D1\u05D5\u05DF \u05D0\u05D7\u05E8'));
}

async function devices() {
  await needUser();
  const me = await api('GET', '/v1/me');
  const list = h('div', { class: 'list', style: { width: '100%' } });
  const fill = items => list.replaceChildren(...(items.length ? items.map(d => h('div', { class: 'item' },
    h('div', { class: 'grow', style: { textAlign: 'right' } }, h('div', { style: { fontWeight: 600 } }, d.name || '\u05DE\u05D7\u05E9\u05D1'), h('div', { class: 'small muted' }, '\u05E4\u05E2\u05D9\u05DC ' + ago(d.lastSeen))),
    (() => { const b = h('button', { type: 'button', class: 'btn sm' }, '\u05E0\u05D9\u05EA\u05D5\u05E7'); b.onclick = () => busy(b, async () => { await api('POST', '/v1/me/devices/revoke', { id: d.id }); fill(items.filter(x => x !== d)); toast('\u05D4\u05DE\u05D7\u05E9\u05D1 \u05E0\u05D5\u05EA\u05E7. \u05E2\u05DB\u05E9\u05D9\u05D5 \u05D7\u05D5\u05D6\u05E8\u05D9\u05DD \u05DC\u05EA\u05D5\u05DB\u05E0\u05D4 \u05D5\u05DC\u05D5\u05D7\u05E6\u05D9\u05DD \u05E9\u05D5\u05D1 "\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05DD Google".'); }); return b; })()))
    : [h('div', { class: 'empty' }, '\u05D0\u05D9\u05DF \u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD \u2014 \u05D7\u05D5\u05D6\u05E8\u05D9\u05DD \u05DC\u05EA\u05D5\u05DB\u05E0\u05D4 \u05D5\u05DC\u05D5\u05D7\u05E6\u05D9\u05DD "\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05DD Google".')]));
  fill(me.devices);
  show(h('h1', null, '\u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05DB\u05D1\u05E8 \u05DE\u05D7\u05D5\u05D1\u05E8 \u05D1\u05DB\u05DC \u05D4\u05DE\u05D7\u05E9\u05D1\u05D9\u05DD \u05D4\u05DE\u05D5\u05EA\u05E8\u05D9\u05DD'),
    h('p', { class: 'muted', style: { lineHeight: '1.6' } }, '\u05DE\u05E0\u05EA\u05E7\u05D9\u05DD \u05DE\u05D7\u05E9\u05D1 \u05E9\u05DB\u05D1\u05E8 \u05DC\u05D0 \u05D1\u05E9\u05D9\u05DE\u05D5\u05E9, \u05D5\u05D7\u05D5\u05D6\u05E8\u05D9\u05DD \u05DC\u05EA\u05D5\u05DB\u05E0\u05D4 \u05DC\u05DC\u05D7\u05D5\u05E5 \u05E9\u05D5\u05D1 "\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05DD Google".'), list);
}

async function main() {
  try { await boot(); } catch (e) { show(h('h1', null, '\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E4\u05EA\u05D5\u05D7\u05D4')); return; }
  try {
    if (r === 'pay') return await pay();
    if (r === 'paid') return await waitPaid();
    if (r === 'not-teacher') return await notTeacher();
    if (r === 'devices') return await devices();
    user = await currentUser();
    if (r === 'blocked') {
      return show(h('div', { class: 'badge-icon orange' }, icon(ICONS.lock, 30, '#9A3412')), h('h1', null, '\u05D4\u05D2\u05D9\u05E9\u05D4 \u05E9\u05DC \u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05D4\u05D5\u05E9\u05D1\u05EA\u05D4'),
        h('p', { class: 'muted' }, '\u05E4\u05D5\u05E0\u05D9\u05DD \u05DC\u05D4\u05E0\u05D4\u05DC\u05D4 \u05DB\u05D3\u05D9 \u05DC\u05D1\u05E8\u05E8.'));
    }
    if (r === 'error') {
      return show(h('h1', null, '\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05DC\u05D0 \u05D4\u05E6\u05DC\u05D9\u05D7\u05D4'), h('p', { class: 'muted' }, q.get('m') || '\u05D7\u05D5\u05D6\u05E8\u05D9\u05DD \u05DC\u05EA\u05D5\u05DB\u05E0\u05D4, \u05DC\u05D5\u05D7\u05E6\u05D9\u05DD "\u05D7\u05D6\u05E8\u05D4" \u05D5\u05E9\u05D5\u05D1 "\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05DD Google".'));
    }
    if (role === 'teacher') {
      return done('\u05E0\u05DB\u05E0\u05E1\u05EA \u05D1\u05D4\u05E6\u05DC\u05D7\u05D4', '\u05D0\u05E4\u05E9\u05E8 \u05DC\u05D7\u05D6\u05D5\u05E8 \u05DC-DSViz \u2014 \u05DE\u05D0\u05D2\u05E8 \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD \u05E4\u05EA\u05D5\u05D7, \u05D5\u05E9\u05DC\u05D9\u05D7\u05EA \u05EA\u05E8\u05D2\u05D9\u05DC \u05D6\u05DE\u05D9\u05E0\u05D4.',
        h('a', { class: 'btn', href: '/account' }, '\u05DC\u05DC\u05D5\u05D7 \u05D4\u05DE\u05D5\u05E8\u05D4 \u2014 \u05D4\u05E7\u05D5\u05D3 \u05D5\u05D4\u05E2\u05DE\u05D3\u05D5\u05EA'));
    }
    const me = user ? await api('GET', '/v1/me').catch(() => null) : null;
    openScreen(me);
  } catch (e) {
    show(h('h1', null, '\u05DE\u05E9\u05D4\u05D5 \u05D4\u05E9\u05EA\u05D1\u05E9'), h('p', { class: 'muted' }, e.message || String(e)));
  }
}
main();
` };

// src/access/pages.js
var TYPES = { html: "text/html; charset=utf-8", js: "text/javascript; charset=utf-8", css: "text/css; charset=utf-8", svg: "image/svg+xml" };
var PAGES = { "/signin": "/signin.html", "/welcome": "/welcome.html", "/account": "/account.html", "/admin": "/admin.html" };
var SECURITY = {
  "x-content-type-options": "nosniff",
  "referrer-policy": "no-referrer",
  "x-frame-options": "DENY",
  "content-security-policy": "frame-ancestors 'none'; base-uri 'none'; object-src 'none'",
  "strict-transport-security": "max-age=31536000",
  "permissions-policy": "camera=(), microphone=(), geolocation=(), payment=()"
};
async function servePage(path, env, url) {
  const file = PAGES[path] || (path.startsWith("/static/") ? path.slice(7) : "");
  const text = file && ASSETS[file];
  if (text === void 0 || !file) return null;
  const ext = file.split(".").pop();
  const cache = ext === "html" ? "no-store" : "public, max-age=3600";
  return new Response(text, { headers: Object.assign({ "content-type": TYPES[ext] || "text/plain; charset=utf-8", "cache-control": cache }, SECURITY) });
}

// src/access/alerts.js
var ALERT_KINDS = {
  "student.new": { title: "\u05EA\u05DC\u05DE\u05D9\u05D3 \u05D7\u05D3\u05E9 \u05D4\u05E6\u05D8\u05E8\u05E3", go: (a) => "#students=" + encodeURIComponent(a.email) },
  "teacher.first": { title: "\u05DE\u05D5\u05E8\u05D4 \u05E0\u05DB\u05E0\u05E1 \u05D1\u05E4\u05E2\u05DD \u05D4\u05E8\u05D0\u05E9\u05D5\u05E0\u05D4", go: (a) => "#teacher=" + encodeURIComponent(a.email) },
  "teacher.request": { title: "\u05D1\u05E7\u05E9\u05D4 \u05D7\u05D3\u05E9\u05D4 \u05DC\u05D4\u05E8\u05E9\u05D0\u05EA \u05DE\u05D5\u05E8\u05D4", go: () => "#requests" },
  "payment.paid": { title: "\u05EA\u05DC\u05DE\u05D9\u05D3 \u05E9\u05D9\u05DC\u05DD", go: (a) => "#students=" + encodeURIComponent(a.email) },
  "payment.seats": { title: "\u05DE\u05D5\u05E8\u05D4 \u05E7\u05E0\u05D4 \u05D7\u05D1\u05D9\u05DC\u05EA \u05E2\u05DE\u05D3\u05D5\u05EA", go: (a) => "#teacher=" + encodeURIComponent(a.email) }
};
var WAIT = Symbol("waitUntil");
var MAIL_PER_DAY = 60;
var clip = (s, n) => String(s == null ? "" : s).replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, n);
var esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
async function alert(env, store, a, opts) {
  try {
    const kind = ALERT_KINDS[a.kind] ? a.kind : "";
    if (!kind) return;
    const doc = { kind, email: clip(a.email, 120).toLowerCase(), name: clip(a.name, 80), detail: clip(a.detail, 200), at: (/* @__PURE__ */ new Date()).toISOString(), seen: false };
    await store.set("alerts/" + timeId(), doc);
    if (opts && opts.email === false) return;
    const send = mail(env, doc).catch((e) => console.error("alert mail", e && e.message));
    if (typeof env[WAIT] === "function") env[WAIT](send);
    else await send;
  } catch (e) {
    console.error("alert", e && e.message);
  }
}
function alertTo(env) {
  const to = String(env.ALERT_EMAIL || String(env.ADMIN_EMAILS || "").split(",")[0] || "").trim().toLowerCase();
  return /^[^\s@/]{1,64}@[^\s@/]{1,190}\.[^\s@/.]{2,}$/.test(to) ? to : "";
}
var mailReady = (env) => !!(env.RESEND_API_KEY && alertTo(env));
async function mail(env, a) {
  if (!mailReady(env)) return;
  const kv = env.USAGE && typeof env.USAGE.get === "function" ? env.USAGE : env.STATS && typeof env.STATS.get === "function" ? env.STATS : null;
  if (kv) {
    const key = "alertmail:" + a.at.slice(0, 10);
    const n = +await kv.get(key) || 0;
    if (n >= MAIL_PER_DAY) return;
    await kv.put(key, String(n + 1), { expirationTtl: 3 * 86400 });
  }
  const k = ALERT_KINDS[a.kind];
  const who = a.name ? a.name + " (" + a.email + ")" : a.email;
  const base = String(env.PUBLIC_URL || "https://dsviz-exercise.motiml77.workers.dev").replace(/\/+$/, "");
  const link = base + "/admin" + k.go(a);
  const lines = [k.title + ": " + who, a.detail || "", "", "\u05D1\u05DE\u05E1\u05D5\u05E3 \u05D4\u05D4\u05E0\u05D4\u05DC\u05D4: " + link].filter((x, i) => x || i === 2);
  const html = '<div dir="rtl" style="font-family:Arial,sans-serif;font-size:15px;line-height:1.6;color:#17213A"><p style="margin:0 0 6px"><b>' + esc(k.title) + '</b></p><p style="margin:0 0 6px">' + esc(a.name || "") + (a.name ? " \xB7 " : "") + '<span dir="ltr">' + esc(a.email) + "</span></p>" + (a.detail ? '<p style="margin:0 0 6px;color:#5A6478">' + esc(a.detail) + "</p>" : "") + '<p style="margin:14px 0 0"><a href="' + esc(link) + '" style="background:#2F6FED;color:#fff;padding:9px 16px;border-radius:9px;text-decoration:none;font-weight:700">\u05E4\u05EA\u05D9\u05D7\u05D4 \u05D1\u05DE\u05E1\u05D5\u05E3 \u05D4\u05D4\u05E0\u05D4\u05DC\u05D4</a></p></div>';
  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { authorization: "Bearer " + env.RESEND_API_KEY, "content-type": "application/json" },
    body: JSON.stringify({ from: env.ALERT_FROM || "DSViz <onboarding@resend.dev>", to: [alertTo(env)], subject: "DSViz: " + k.title + " \u2014 " + (a.name || a.email), text: lines.join("\n"), html })
  });
  if (!res.ok) throw new Error("resend " + res.status);
}
async function listAlerts(store, limit) {
  const [recent, unseen] = await Promise.all([
    store.query("alerts", { orderBy: ["at", "desc"], limit: Math.min(Math.max(+limit || 30, 1), 100) }),
    store.query("alerts", { where: [["seen", "==", false]], limit: 200 })
  ]);
  return {
    ok: true,
    unseen: unseen.length,
    alerts: recent.map((a) => ({
      id: a._id,
      kind: a.kind,
      title: (ALERT_KINDS[a.kind] || {}).title || a.kind,
      email: a.email,
      name: a.name,
      detail: a.detail,
      at: a.at,
      seen: !!a.seen,
      go: ALERT_KINDS[a.kind] ? ALERT_KINDS[a.kind].go(a) : "#overview"
    }))
  };
}
async function markSeen(store, b) {
  const ids = b && b.all ? (await store.query("alerts", { where: [["seen", "==", false]], limit: 400 })).map((a) => a._id) : (Array.isArray(b && b.ids) ? b.ids : []).map((x) => String(x)).filter((x) => /^[a-z0-9]{8,40}$/.test(x)).slice(0, 100);
  for (let i = 0; i < ids.length; i += 50) {
    const part = ids.slice(i, i + 50);
    await store.commit((w) => {
      for (const id of part) w.update("alerts/" + id, { seen: true });
    });
  }
  return { ok: true, marked: ids.length };
}

// src/access/index.js
var DEFAULTS = {
  price: 0,
  // ₪ — 0 = עוד לא נקבע (אי אפשר לשלם)
  accessUntil: "2027-08-31",
  // תשלום פותח את המאגר עד התאריך הזה
  offlineDays: 0,
  // ימים שהמאגר נפתח בלי אינטרנט — 0 = בלי הגבלה (רק הכניסה הראשונה צריכה אינטרנט)
  studentDevices: 3,
  teacherDevices: 5,
  defaultSeats: 0,
  // מורה חדש מקבל — ואת השאר קונה בחבילות
  packages: [{ seats: 10, price: 0 }, { seats: 20, price: 0 }, { seats: 30, price: 0 }],
  // חבילות עמדות למורים (מחיר 0 = לא נמכרת)
  requireLoginForGemini: false,
  // שליחת תרגיל ל-Gemini רק למחוברים
  monthlyPerSeat: 4,
  // שליחות בחודש למורה: לכל עמדה שלו × מספר העמדות (העמדות עצמן לא שולחות — רק מאגר השאלות)
  monthlyStudent: 30,
  // שליחות בחודש לתלמיד רשום (מייל)
  alertEmail: true
  // התראה במייל למנהל כשמצטרף מורה / תלמיד (alerts.js); במסוף — תמיד
};
var SETTING_LIMITS = {
  price: [0, 5e3],
  offlineDays: [0, 365],
  studentDevices: [1, 20],
  teacherDevices: [1, 50],
  defaultSeats: [0, 2e3],
  monthlyPerSeat: [0, 1e3],
  monthlyStudent: [0, 5e3]
};
var SESSION_TOUCH_MS = 6 * 3600 * 1e3;
var settingsCache = { at: 0, pid: "", value: null };
var keyCache = { pem: "", key: null, spki: "" };
function accessEnabled(env) {
  return String(env.ACCESS || "").toLowerCase() === "on" && !!projectId(env);
}
var nowIso = () => (/* @__PURE__ */ new Date()).toISOString();
var today = () => nowIso().slice(0, 10);
var month = () => nowIso().slice(0, 7);
var clip2 = (s, n) => String(s == null ? "" : s).replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, n);
var normEmail = (e) => String(e || "").trim().toLowerCase();
var emailOk = (e) => /^[^\s@/]{1,64}@[^\s@/]{1,190}\.[^\s@/.]{2,}$/.test(e);
var dateOk = (d) => /^\d{4}-\d{2}-\d{2}$/.test(String(d || "")) && !isNaN(Date.parse(d));
function phoneNorm(p) {
  let d = String(p == null ? "" : p).replace(/[\s\-().]/g, "");
  if (/^\+?972/.test(d)) d = "0" + d.replace(/^\+?972/, "");
  return /^05\d{8}$/.test(d) ? d : "";
}
var machineOk = (m) => /^[a-f0-9]{16,64}$/.test(String(m || ""));
var intIn = (v, lo, hi) => Number.isInteger(+v) && +v >= lo && +v <= hi;
async function readJson(request) {
  const text = await request.text();
  if (text.length > 2e5) throw new Fail(413, "too_big", "\u05D4\u05D1\u05E7\u05E9\u05D4 \u05D2\u05D3\u05D5\u05DC\u05D4 \u05DE\u05D3\u05D9.");
  try {
    return text ? JSON.parse(text) || {} : {};
  } catch (e) {
    throw new Fail(400, "format", "\u05DC\u05D0 \u05D4\u05E6\u05DC\u05D7\u05EA\u05D9 \u05DC\u05E7\u05E8\u05D5\u05D0 \u05D0\u05EA \u05D4\u05D1\u05E7\u05E9\u05D4");
  }
}
function admins(env) {
  return String(env.ADMIN_EMAILS || "").split(",").map(normEmail).filter(Boolean);
}
async function settings(env, fresh) {
  const pid = projectId(env);
  if (!fresh && settingsCache.value && settingsCache.pid === pid && Date.now() - settingsCache.at < 3e4) return settingsCache.value;
  const d = await db(env).get("config/settings");
  const value = Object.assign({}, DEFAULTS, d || {});
  settingsCache = { at: Date.now(), pid, value };
  return value;
}
async function passKey(env) {
  const pem = env.PASS_KEY || "";
  if (!pem) return null;
  if (keyCache.pem !== pem) keyCache = { pem, key: await importRsaPrivate(pem), spki: "" };
  return keyCache.key;
}
var APPS = ["dsviz", "automata"];
function appOf(v) {
  const a = String(v || "").trim().toLowerCase();
  return APPS.includes(a) ? a : "";
}
function appDetail(app, more) {
  return [app ? "app=" + app : "", more || ""].filter(Boolean).join(" \xB7 ");
}
async function log(store, actor, action, target, detail) {
  try {
    await store.set("log/" + timeId(), { at: nowIso(), actor: clip2(actor, 120), action, target: clip2(target, 200), detail: clip2(detail, 400) });
  } catch (e) {
    console.error("log", e && e.message);
  }
}
async function webUser(request, env) {
  const m = /^Bearer\s+(\S+)$/.exec(request.headers.get("authorization") || "");
  if (!m) throw new Fail(401, "login", "\u05E6\u05E8\u05D9\u05DA \u05DC\u05D4\u05EA\u05D7\u05D1\u05E8 \u05E2\u05DD Google.");
  try {
    return await verifyIdToken(env, m[1]);
  } catch (e) {
    if (e instanceof AuthError) throw new Fail(401, "login", "\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E4\u05D2\u05D4 \u2014 \u05DE\u05EA\u05D7\u05D1\u05E8\u05D9\u05DD \u05E9\u05D5\u05D1.");
    throw e;
  }
}
async function adminUser(request, env) {
  const u = await webUser(request, env);
  if (!admins(env).includes(u.email)) throw new Fail(403, "admin", "\u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05D4\u05D6\u05D4 \u05DC\u05D0 \u05DE\u05D5\u05D2\u05D3\u05E8 \u05DB\u05DE\u05E0\u05D4\u05DC.");
  return u;
}
async function makePass(env, s, p) {
  const key = await passKey(env);
  if (!key) return "";
  const now = Math.floor(Date.now() / 1e3);
  let exp = now + (s.offlineDays > 0 ? s.offlineDays : 3650) * 86400;
  if (p.until) exp = Math.min(exp, Math.floor(Date.parse(p.until + "T23:59:59Z") / 1e3));
  const apps = Array.isArray(p.apps) && p.apps.length ? p.apps.filter((a) => APPS.includes(a)) : APPS.slice();
  const payload = { v: 1, kind: p.kind, sub: p.sub, name: p.name || "", email: p.email || "", until: p.until || "", mid: p.machine || "", apps, iat: now, exp };
  if (p.teacher) payload.teacher = p.teacher;
  const text = b64urlJson(payload);
  return text + "." + b64url(await signRs256(key, text));
}
function parseToken(token) {
  const m = /^([us])\.([A-Za-z0-9_-]{8,64})\.([A-Za-z0-9_-]{20,100})$/.exec(String(token || "").trim());
  return m ? { kind: m[1], id: m[2], secret: m[3] } : null;
}
async function teacherView(t, stations) {
  return {
    email: t.email,
    name: t.name || "",
    school: t.school || "",
    code: showCode(t.code),
    seats: t.seats || 0,
    used: t.used || 0,
    active: !!t.active,
    stations: stations ? stations.map((x) => ({ id: x._id, name: x.name, claimedAt: x.claimedAt, lastSeen: x.lastSeen })) : void 0
  };
}
var STATION_TOKENS = 8;
var STATION_MACHINES = 8;
var stationTokens = (st) => [st.tokenHash].concat(Array.isArray(st.tokens) ? st.tokens : []).filter(Boolean);
var stationMachines = (st) => [st.machine].concat(Array.isArray(st.machines) ? st.machines : []).filter(Boolean);
var uniq = (a) => Array.from(new Set(a));
var CLAIM_WHY = {
  none: "\u05D0\u05D9\u05DF \u05D1\u05DE\u05D7\u05E9\u05D1 \u05DB\u05E0\u05D9\u05E1\u05D4 \u05E9\u05DE\u05D5\u05E8\u05D4 (\u05D4\u05EA\u05E7\u05E0\u05D4 \u05D7\u05D3\u05E9\u05D4, \u05DE\u05E9\u05EA\u05DE\u05E9 Windows \u05D0\u05D7\u05E8, \u05D0\u05D5 \u05DE\u05D7\u05E9\u05D1 \u05E9\u05E9\u05D5\u05D7\u05D6\u05E8)",
  unreadable: "\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05D4\u05E9\u05DE\u05D5\u05E8\u05D4 \u05DC\u05D0 \u05E0\u05E4\u05EA\u05D7\u05D4 (\u05DE\u05D6\u05D4\u05D4 \u05D4\u05DE\u05D7\u05E9\u05D1 \u05D4\u05E9\u05EA\u05E0\u05D4, \u05D0\u05D5 \u05E9\u05D4\u05E7\u05D5\u05D1\u05E5 \u05E4\u05D2\u05D5\u05DD)",
  pass: "\u05D4\u05D0\u05D9\u05E9\u05D5\u05E8 \u05D4\u05E9\u05DE\u05D5\u05E8 \u05DC\u05D0 \u05D0\u05D5\u05DE\u05EA (\u05EA\u05D0\u05E8\u05D9\u05DA \u05D4\u05DE\u05D7\u05E9\u05D1, \u05D0\u05D5 \u05D2\u05E8\u05E1\u05D4 \u05D9\u05E9\u05E0\u05D4)",
  "server-invalid": "\u05D4\u05E9\u05E8\u05EA \u05D3\u05D7\u05D4 \u05D0\u05EA \u05D4\u05DB\u05E8\u05D8\u05D9\u05E1 \u05D4\u05E9\u05DE\u05D5\u05E8",
  "server-revoked": "\u05D4\u05E2\u05DE\u05D3\u05D4 \u05E9\u05D5\u05D7\u05E8\u05E8\u05D4 (\u05DE\u05D4\u05DE\u05D5\u05E8\u05D4 \u05D0\u05D5 \u05DE\u05D4\u05D4\u05E0\u05D4\u05DC\u05D4)"
};
var REJECT_WHY = { gone: "\u05D0\u05D9\u05DF \u05E2\u05DE\u05D3\u05D4 \u05DB\u05D6\u05D5", released: "\u05D4\u05E2\u05DE\u05D3\u05D4 \u05E9\u05D5\u05D7\u05E8\u05E8\u05D4", token: "\u05D4\u05DB\u05E8\u05D8\u05D9\u05E1 \u05DC\u05D0 \u05E9\u05D9\u05D9\u05DA \u05DC\u05E2\u05DE\u05D3\u05D4 (\u05D4\u05EA\u05D7\u05D1\u05E8\u05D5\u05EA \u05D7\u05D3\u05E9\u05D4 \u05D4\u05D7\u05DC\u05D9\u05E4\u05D4 \u05D0\u05D5\u05EA\u05D5)", machine: "\u05DE\u05D6\u05D4\u05D4 \u05D4\u05DE\u05D7\u05E9\u05D1 \u05E9\u05D5\u05E0\u05D4 \u05DE\u05D6\u05D4 \u05E9\u05DC \u05D4\u05E2\u05DE\u05D3\u05D4" };
async function resolveToken(env, token, machine) {
  const store = db(env);
  const p = parseToken(token);
  if (!p) return { status: "invalid" };
  const hash = await sha256Hex(p.secret);
  if (p.kind === "s") {
    const st2 = await store.get("stations/" + p.id);
    if (!st2) return { status: "invalid", why: "gone", stationId: p.id };
    if (!st2.active) return { status: "revoked", why: "released", stationId: p.id, stationDoc: st2 };
    if (!stationTokens(st2).some((h) => sameText(h, hash))) return { status: "invalid", why: "token", stationId: p.id, stationDoc: st2 };
    const machines = stationMachines(st2);
    if (machine && machines.length && !machines.includes(machine)) return { status: "invalid", why: "machine", stationId: p.id, stationDoc: st2 };
    const t = await store.get("teachers/" + st2.teacher);
    const base = {
      kind: "station",
      subject: "station:" + p.id,
      station: { id: p.id, name: st2.name, teacher: st2.teacher },
      machine: machine && machines.includes(machine) ? machine : st2.machine,
      profile: { role: "station", name: st2.name, email: "" },
      teacher: t ? { name: t.name || "", school: t.school || "" } : null
    };
    if (!t || !t.active) return Object.assign(base, { status: "blocked" });
    if (!st2.lastSeen || Date.now() - Date.parse(st2.lastSeen) > SESSION_TOUCH_MS) {
      await store.merge("stations/" + p.id, { lastSeen: nowIso() }).catch(() => {
      });
    }
    return Object.assign(base, { status: "ok" });
  }
  const se = await store.get("sessions/" + p.id);
  if (!se || se.revoked || !sameText(se.tokenHash, hash)) return { status: se && se.revoked ? "revoked" : "invalid" };
  if (machine && se.machine && se.machine !== machine) return { status: "invalid" };
  const profile = { role: se.role, email: se.email, name: se.name || "" };
  const touch = () => !se.lastSeen || Date.now() - Date.parse(se.lastSeen) > SESSION_TOUCH_MS ? store.merge("sessions/" + p.id, { lastSeen: nowIso() }).catch(() => {
  }) : null;
  if (se.role === "teacher") {
    const t = await store.get("teachers/" + se.email);
    const base = { kind: "teacher", subject: "teacher:" + se.email, session: p.id, machine: se.machine, profile };
    if (!t) return Object.assign(base, { status: "not-teacher" });
    if (!t.active) return Object.assign(base, { status: "blocked" });
    await touch();
    return Object.assign(base, { status: "ok", teacher: await teacherView(t) });
  }
  const st = await store.get("students/" + se.email);
  const until = st && st.accessUntil || "";
  await touch();
  return {
    kind: "student",
    subject: "student:" + se.email,
    session: p.id,
    machine: se.machine,
    profile,
    until,
    status: until && until >= today() ? "ok" : "pay"
  };
}
async function accessReply(env, r, extra) {
  const s = await settings(env);
  const out = { ok: true, status: r.status, profile: r.profile || null };
  if (r.teacher) out.teacher = r.teacher;
  if (r.station) out.station = { id: r.station.id, name: r.station.name };
  if (r.until) out.until = r.until;
  if (r.why) out.why = r.why;
  Object.assign(out, extra || {});
  if (r.status === "ok") {
    out.pass = await makePass(env, s, {
      kind: r.kind,
      sub: r.subject,
      name: r.profile && r.profile.name,
      email: r.profile && r.profile.email,
      machine: r.machine,
      until: r.kind === "student" ? r.until : "",
      teacher: r.kind === "station" && r.teacher ? r.teacher.name : void 0
    });
  }
  if (r.status === "pay") Object.assign(out, { price: s.price, accessUntil: s.accessUntil, canPay: s.price > 0 && !!payProvider(env) });
  return out;
}
async function exchange(request, env) {
  const b = await readJson(request);
  const user = await verifyIdToken(env, b.idToken).catch((e) => {
    throw new Fail(401, "login", "\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05DD Google \u05DC\u05D0 \u05D4\u05E6\u05DC\u05D9\u05D7\u05D4. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1.");
  });
  const role = b.role === "teacher" ? "teacher" : "student";
  if (!/^[A-Za-z0-9_-]{43}$/.test(String(b.challenge || ""))) throw new Fail(400, "format", "\u05D7\u05E1\u05E8 \u05E7\u05D5\u05D3 \u05DE\u05D4\u05EA\u05D5\u05DB\u05E0\u05D4 \u2014 \u05E4\u05D5\u05EA\u05D7\u05D9\u05DD \u05D0\u05EA \u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05DE\u05EA\u05D5\u05DA DSViz.");
  const code = randomToken(24);
  await db(env).set("authCodes/" + code, {
    uid: user.uid,
    email: user.email,
    name: user.name,
    picture: user.picture,
    role,
    challenge: b.challenge,
    exp: Date.now() + 5 * 60 * 1e3,
    createdAt: nowIso()
  });
  return { ok: true, code };
}
async function newSession(env, s, who, role, machine, machineName, client) {
  const store = db(env);
  const limit = role === "teacher" ? s.teacherDevices : s.studentDevices;
  const mine = (await store.query("sessions", { where: [["email", "==", who.email]] })).filter((x) => !x.revoked);
  const same = mine.filter((x) => x.machine === machine);
  const others = mine.filter((x) => x.machine !== machine);
  if (others.length >= limit) {
    return { status: "devices", limit, devices: others.map((x) => ({ name: x.machineName || "", lastSeen: x.lastSeen || x.createdAt })) };
  }
  const sid = randomToken(12), secret = randomToken(32);
  const at = nowIso();
  await store.commit(async (w) => {
    for (const x of same) w.update("sessions/" + x._id, { revoked: true, revokedAt: at, revokedBy: "same-machine" });
    w.create("sessions/" + sid, {
      email: who.email,
      uid: who.uid || "",
      name: who.name || "",
      role,
      machine,
      machineName: clip2(machineName, 60),
      client: clip2(client, 60),
      tokenHash: await sha256Hex(secret),
      revoked: false,
      createdAt: at,
      lastSeen: at
    });
  });
  return { status: "new", token: "u." + sid + "." + secret, sid };
}
async function redeem(request, env) {
  const b = await readJson(request);
  const store = db(env);
  const code = String(b.code || "");
  if (!/^[A-Za-z0-9_-]{20,64}$/.test(code)) throw new Fail(400, "format", "\u05E7\u05D5\u05D3 \u05DB\u05E0\u05D9\u05E1\u05D4 \u05DC\u05D0 \u05EA\u05E7\u05D9\u05DF");
  if (!machineOk(b.machine)) throw new Fail(400, "format", "\u05D7\u05E1\u05E8 \u05DE\u05D6\u05D4\u05D4 \u05DE\u05D7\u05E9\u05D1");
  const doc = await store.tx(async (t) => {
    const d = await t.get("authCodes/" + code);
    if (d) t.remove("authCodes/" + code);
    return d;
  });
  if (!doc || doc.exp < Date.now()) throw new Fail(400, "expired", '\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E4\u05D2\u05D4. \u05D1\u05D7\u05DC\u05D5\u05DF \u05E9\u05DC DSViz \u05DC\u05D5\u05D7\u05E6\u05D9\u05DD "\u05D7\u05D6\u05E8\u05D4" \u05D5\u05E9\u05D5\u05D1 "\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05DD Google".');
  const want = b64url(await sha256(String(b.verifier || "")));
  if (!sameText(want, doc.challenge)) throw new Fail(400, "expired", '\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05DC\u05D0 \u05EA\u05D5\u05D0\u05DE\u05EA \u05DC\u05D7\u05DC\u05D5\u05DF \u05D4\u05D6\u05D4. \u05D1\u05D7\u05DC\u05D5\u05DF \u05E9\u05DC DSViz \u05DC\u05D5\u05D7\u05E6\u05D9\u05DD "\u05D7\u05D6\u05E8\u05D4" \u05D5\u05E9\u05D5\u05D1 "\u05DB\u05E0\u05D9\u05E1\u05D4 \u05E2\u05DD Google".');
  const s = await settings(env);
  const who = { email: doc.email, uid: doc.uid, name: doc.name };
  const profile = { role: doc.role, email: doc.email, name: doc.name || "", picture: doc.picture || "" };
  if (doc.role === "teacher") {
    const t = await store.get("teachers/" + doc.email);
    if (!t) return { ok: true, status: "not-teacher", profile };
    if (!t.active) return { ok: true, status: "blocked", profile };
  }
  const ses = await newSession(env, s, who, doc.role, b.machine, b.machineName, b.client);
  if (ses.status === "devices") return { ok: true, status: "devices", profile, limit: ses.limit, devices: ses.devices };
  let joined = "";
  if (doc.role === "student") {
    const prev = await store.get("students/" + doc.email);
    await store.merge("students/" + doc.email, { email: doc.email, uid: doc.uid, name: doc.name || "", lastSignIn: nowIso() });
    if (!prev || !prev.lastSignIn) joined = "student.new";
  } else {
    const prev = await store.get("teachers/" + doc.email);
    await store.merge("teachers/" + doc.email, { lastSignIn: nowIso(), uid: doc.uid });
    if (prev && !prev.lastSignIn) joined = "teacher.first";
  }
  if (joined) await alert(env, store, { kind: joined, email: doc.email, name: doc.name, detail: clip2(b.machineName, 60) ? "\u05DE\u05D7\u05E9\u05D1: " + clip2(b.machineName, 60) : "" }, { email: s.alertEmail !== false });
  await log(store, doc.email, doc.role === "teacher" ? "signin.teacher" : "signin.student", clip2(b.machineName, 60), appDetail(appOf(b.app)));
  const r = await resolveToken(env, ses.token, b.machine);
  return accessReply(env, r, { token: ses.token });
}
async function check(request, env) {
  const b = await readJson(request);
  const r = await resolveToken(env, b.token, machineOk(b.machine) ? b.machine : "");
  if (r.stationDoc && r.why) await logStationReject(env, r).catch(() => {
  });
  return accessReply(env, r);
}
async function logStationReject(env, r) {
  const st = r.stationDoc, mark = today() + ":" + r.why;
  if (st.rejectLog === mark) return;
  const store = db(env);
  await store.merge("stations/" + r.stationId, { rejectLog: mark });
  await log(store, "\u05DE\u05D7\u05E9\u05D1: " + (st.name || ""), "station.rejected", st.teacher || "", REJECT_WHY[r.why] || r.why);
}
async function logout(request, env) {
  const b = await readJson(request);
  const p = parseToken(b.token);
  if (p && p.kind === "u") {
    const store = db(env);
    const se = await store.get("sessions/" + p.id);
    if (se && sameText(se.tokenHash, await sha256Hex(p.secret)) && !se.revoked) {
      await store.update("sessions/" + p.id, { revoked: true, revokedAt: nowIso(), revokedBy: "logout" });
    }
  }
  return { ok: true };
}
async function claim(request, env) {
  const b = await readJson(request);
  const store = db(env);
  const code = normalizeCode(b.code);
  const name = clip2(b.name, 60);
  if (!machineOk(b.machine)) throw new Fail(400, "format", "\u05D7\u05E1\u05E8 \u05DE\u05D6\u05D4\u05D4 \u05DE\u05D7\u05E9\u05D1");
  if (!name) throw new Fail(400, "name", '\u05DB\u05EA\u05D1\u05D5 \u05E9\u05DD \u05DC\u05DE\u05D7\u05E9\u05D1 \u2014 \u05DC\u05DE\u05E9\u05DC "\u05DE\u05E2\u05D1\u05D3\u05D4 2 \xB7 \u05E2\u05DE\u05D3\u05D4 14".');
  if (!code) {
    const lim = await badCodeLimit(env, request, true);
    await log(store, "\u05DE\u05D7\u05E9\u05D1: " + name, "station.bad-code", "", badCodeDetail(b.code, "", lim));
    return { ok: true, status: "bad-code" };
  }
  await badCodeLimit(env, request, false);
  const secret = randomToken(32);
  const hash = await sha256Hex(secret);
  const at = nowIso();
  const stable = machineOk(b.stable) && b.stable !== b.machine ? b.stable : "";
  const res = await store.tx(async (t) => {
    const c = await t.get("teacherCodes/" + code);
    if (!c) return { status: "bad-code" };
    let stationId = (await sha256Hex(c.teacher + "|" + b.machine)).slice(0, 32);
    const keyId = stable ? (await sha256Hex(c.teacher + "|stable|" + stable)).slice(0, 32) : "";
    let [teacher, st, key] = await t.getMany(["teachers/" + c.teacher, "stations/" + stationId].concat(keyId ? ["stationKeys/" + keyId] : []));
    if (!teacher || teacher.code !== code) return { status: "bad-code" };
    if (!teacher.active) return { status: "blocked", teacher };
    let same = "";
    if (!(st && st.active) && key && key.station && key.station !== stationId) {
      const other = await t.get("stations/" + key.station);
      if (other && other.active && other.teacher === c.teacher) {
        st = other;
        stationId = key.station;
        same = "stable";
      }
    }
    if (keyId && !(key && key.station === stationId)) t.set("stationKeys/" + keyId, { station: stationId, teacher: c.teacher, at });
    if (st && st.active) {
      t.update("stations/" + stationId, {
        tokenHash: hash,
        tokens: uniq(stationTokens(st)).slice(0, STATION_TOKENS - 1),
        machine: b.machine,
        machines: uniq([b.machine].concat(stationMachines(st))).slice(0, STATION_MACHINES),
        name,
        lastSeen: at,
        client: clip2(b.client, 60),
        againAt: at,
        againCount: (st.againCount || 0) + 1
      });
      return { status: "ok", stationId, teacher, again: true, same };
    }
    if ((teacher.used || 0) >= (teacher.seats || 0)) return { status: "full", teacher };
    t.set("stations/" + stationId, {
      teacher: c.teacher,
      teacherName: teacher.name || "",
      name,
      machine: b.machine,
      machines: [b.machine],
      tokenHash: hash,
      tokens: [],
      active: true,
      claimedAt: at,
      lastSeen: at,
      client: clip2(b.client, 60)
    });
    t.update("teachers/" + c.teacher, { updatedAt: at }, { used: 1 });
    return { status: "ok", stationId, teacher };
  });
  if (res.status === "bad-code") {
    const lim = await badCodeLimit(env, request, true);
    const near = await nearTeacher(store, code);
    await log(
      store,
      "\u05DE\u05D7\u05E9\u05D1: " + name,
      "station.bad-code",
      near.join(", "),
      badCodeDetail(b.code, code, lim) + (near.length ? " \xB7 \u05DB\u05E0\u05E8\u05D0\u05D4 \u05D8\u05E2\u05D5\u05EA \u05D4\u05E7\u05DC\u05D3\u05D4 \u05D1\u05E7\u05D5\u05D3 \u05E9\u05DC " + (near.length > 1 ? "\u05D0\u05D7\u05D3 \u05D4\u05DE\u05D5\u05E8\u05D9\u05DD" : "\u05D4\u05DE\u05D5\u05E8\u05D4") : "")
    );
    return { ok: true, status: "bad-code" };
  }
  const tv = res.teacher ? { name: res.teacher.name || "", school: res.teacher.school || "", seats: res.teacher.seats || 0, used: (res.teacher.used || 0) + (res.status === "ok" && !res.again ? 1 : 0) } : null;
  if (res.status !== "ok") return { ok: true, status: res.status, teacher: tv };
  const why = CLAIM_WHY[String(b.why || "")] || "";
  await log(
    store,
    "\u05DE\u05D7\u05E9\u05D1: " + name,
    res.again ? "station.again" : "station.claim",
    res.teacher.email,
    appDetail(appOf(b.app), `${tv.used}/${tv.seats}`) + (res.same === "stable" ? " \xB7 \u05D0\u05D5\u05EA\u05D5 \u05DE\u05D7\u05E9\u05D1 \u05E2\u05DD \u05DE\u05D6\u05D4\u05D4 \u05D7\u05D3\u05E9" : "") + (res.again && why ? " \xB7 " + why : "")
  );
  const token = "s." + res.stationId + "." + secret;
  const r = await resolveToken(env, token, b.machine);
  return accessReply(env, r, { token, teacher: tv });
}
async function badCodeLimit(env, request, failed) {
  const ip = request.headers.get("cf-connecting-ip") || "local";
  const hour = (/* @__PURE__ */ new Date()).toISOString().slice(0, 13);
  const src = (await sha256Hex("badcode-src|" + hour.slice(0, 10) + "|" + ip)).slice(0, 4);
  const kv = env.USAGE && typeof env.USAGE.get === "function" ? env.USAGE : env.STATS && typeof env.STATS.get === "function" ? env.STATS : null;
  if (!kv) return { count: 0, src };
  const key = "badcode:" + hour + ":" + ip;
  const n = +await kv.get(key) || 0;
  if (n >= 30) throw new Fail(429, "rate", "\u05D9\u05D5\u05EA\u05E8 \u05DE\u05D3\u05D9 \u05E0\u05D9\u05E1\u05D9\u05D5\u05E0\u05D5\u05EA \u05E2\u05DD \u05E7\u05D5\u05D3 \u05E9\u05D2\u05D5\u05D9. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1 \u05D1\u05E2\u05D5\u05D3 \u05E9\u05E2\u05D4.");
  if (failed) await kv.put(key, String(n + 1), { expirationTtl: 7200 });
  return { count: failed ? n + 1 : n, src };
}
async function nearTeacher(store, code) {
  if (!code) return [];
  try {
    const all = await store.query("teacherCodes", { limit: 5e3 });
    const close = (c) => {
      if (!c || c.length !== code.length || c === code) return false;
      const diff = [];
      for (let i = 0; i < c.length; i++) if (c[i] !== code[i]) diff.push(i);
      return diff.length === 1 || diff.length === 2 && diff[1] === diff[0] + 1 && c[diff[0]] === code[diff[1]] && c[diff[1]] === code[diff[0]];
    };
    return all.filter((d) => close(d._id)).map((d) => String(d.teacher || "")).filter(Boolean).slice(0, 3);
  } catch (e) {
    return [];
  }
}
function badCodeDetail(typed, code, lim) {
  const raw = String(typed || "").trim();
  const shown = code ? code.slice(0, 3) + "?" : raw ? '"' + Array.from(raw).slice(0, 3).join("") + (Array.from(raw).length > 3 ? "\u2026" : "") + '" \u2014 \u05DC\u05D0 \u05D1\u05E4\u05D5\u05E8\u05DE\u05D8 \u05E9\u05DC \u05E7\u05D5\u05D3 \u05DE\u05D5\u05E8\u05D4' : "\u05E8\u05D9\u05E7";
  const parts = ["\u05E7\u05D5\u05D3: " + shown];
  if (lim && lim.count) parts.push(`\u05E0\u05D9\u05E1\u05D9\u05D5\u05DF ${lim.count} \u05D1\u05E9\u05E2\u05D4 \u05D4\u05D6\u05D5 \u05DE\u05D0\u05D5\u05EA\u05D5 \u05DE\u05E7\u05D5\u05E8`);
  if (lim && lim.src) parts.push("\u05DE\u05E7\u05D5\u05E8 #" + lim.src);
  return parts.join(" \xB7 ");
}
var STATION_AI = "\u05D4\u05DE\u05D7\u05E9\u05D1 \u05D4\u05D6\u05D4 \u05DE\u05D7\u05D5\u05D1\u05E8 \u05DB\u05E2\u05DE\u05D3\u05EA \u05DE\u05D7\u05E9\u05D1 \u05E9\u05DC \u05D4\u05DB\u05D9\u05EA\u05D4, \u05D5\u05D1\u05E2\u05DE\u05D3\u05D4 \u05E2\u05D5\u05D1\u05D3\u05D9\u05DD \u05E2\u05DC \u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD \u05DE\u05DE\u05D0\u05D2\u05E8 \u05D4\u05E9\u05D0\u05DC\u05D5\u05EA. \u05E9\u05DC\u05D9\u05D7\u05EA \u05EA\u05E8\u05D2\u05D9\u05DC \u05D7\u05D3\u05E9 \u05DC\u05D1\u05D3\u05D9\u05E7\u05D4 \u05D1-AI \u05E4\u05EA\u05D5\u05D7\u05D4 \u05DC\u05DE\u05D5\u05E8\u05D9\u05DD \u05D5\u05DC\u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD \u05E9\u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD \u05E2\u05DD Google, \u05D1\u05DE\u05D7\u05E9\u05D1 \u05E9\u05DC\u05D4\u05DD.";
async function geminiGate(request, env) {
  if (!accessEnabled(env)) return null;
  const s = await settings(env);
  const token = request.headers.get("x-dsviz-access") || "";
  if (!s.requireLoginForGemini && !token) return null;
  if (!token) throw new Fail(401, "login", '\u05E9\u05DC\u05D9\u05D7\u05EA \u05EA\u05E8\u05D2\u05D9\u05DC \u05E4\u05EA\u05D5\u05D7\u05D4 \u05DC\u05DE\u05D5\u05E8\u05D9\u05DD \u05D5\u05DC\u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD \u05E8\u05E9\u05D5\u05DE\u05D9\u05DD \u2014 \u05E6\u05E8\u05D9\u05DA \u05DC\u05D4\u05EA\u05D7\u05D1\u05E8 \u05E4\u05E2\u05DD \u05D0\u05D7\u05EA \u05D1\u05DE\u05D7\u05E9\u05D1: \u05D1\u05E1\u05E8\u05D2\u05DC \u05E9\u05DC DSViz \u05DC\u05D5\u05D7\u05E6\u05D9\u05DD \u05E2\u05DC \u05DE\u05D0\u05D2\u05E8 \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD ("\u05DE\u05D0\u05D2\u05E8 \u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD" / "\u05DE\u05D0\u05D2\u05E8 \u05E9\u05D0\u05DC\u05D5\u05EA") \u05D5\u05E0\u05DB\u05E0\u05E1\u05D9\u05DD. \u05D0\u05DD \u05D0\u05D9\u05DF \u05DB\u05E4\u05EA\u05D5\u05E8 \u05DB\u05D6\u05D4 \u2014 \u05DE\u05E2\u05D3\u05DB\u05E0\u05D9\u05DD \u05D0\u05EA DSViz.');
  const r = await resolveToken(env, token, request.headers.get("x-dsviz-machine") || "");
  if (r.kind === "station" && (r.status === "ok" || r.status === "blocked")) throw new Fail(403, "station", STATION_AI);
  if (r.status !== "ok") {
    if (!s.requireLoginForGemini) return null;
    if (r.status === "pay") throw new Fail(403, "pay", "\u05E9\u05DC\u05D9\u05D7\u05EA \u05EA\u05E8\u05D2\u05D9\u05DC \u05E4\u05EA\u05D5\u05D7\u05D4 \u05DC\u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD \u05E8\u05E9\u05D5\u05DE\u05D9\u05DD \u2014 \u05E6\u05E8\u05D9\u05DA \u05DC\u05E4\u05EA\u05D5\u05D7 \u05D0\u05EA \u05D4\u05DE\u05D0\u05D2\u05E8 (\u05EA\u05E9\u05DC\u05D5\u05DD).");
    if (r.status === "blocked") throw new Fail(403, "blocked", "\u05D4\u05D2\u05D9\u05E9\u05D4 \u05E9\u05DC \u05D4\u05DE\u05D5\u05E8\u05D4 \u05D4\u05D6\u05D4 \u05D4\u05D5\u05E9\u05D1\u05EA\u05D4. \u05E4\u05D5\u05E0\u05D9\u05DD \u05DC\u05DE\u05D5\u05E8\u05D4 \u05D0\u05D5 \u05DC\u05D4\u05E0\u05D4\u05DC\u05D4.");
    throw new Fail(401, "login", "\u05D4\u05DB\u05E0\u05D9\u05E1\u05D4 \u05D1\u05DE\u05D7\u05E9\u05D1 \u05D4\u05D6\u05D4 \u05DB\u05D1\u05E8 \u05DC\u05D0 \u05D1\u05EA\u05D5\u05E7\u05E3 \u2014 \u05DE\u05EA\u05D7\u05D1\u05E8\u05D9\u05DD \u05E9\u05D5\u05D1.");
  }
  const owner = r.profile.email;
  const store = db(env);
  let limit = s.monthlyStudent;
  if (r.kind !== "student") {
    const t = await store.get("teachers/" + owner);
    limit = teacherLimit(s, t);
  }
  const key = await usageKey(r.kind === "student" ? "student" : "teacher", owner);
  const u = await store.get(key);
  if (s.requireLoginForGemini && u && u.n >= limit) {
    throw new Fail(429, "monthly", r.kind === "student" ? `\u05E0\u05D9\u05E6\u05DC\u05EA\u05DD \u05D0\u05EA ${limit} \u05D4\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05E9\u05DC \u05D4\u05D7\u05D5\u05D3\u05E9. \u05D4\u05DE\u05E0\u05D4 \u05DE\u05EA\u05D7\u05D3\u05E9\u05EA \u05D1\u05EA\u05D7\u05D9\u05DC\u05EA \u05D4\u05D7\u05D5\u05D3\u05E9 \u05D4\u05D1\u05D0.` : `\u05D4\u05DE\u05D5\u05E8\u05D4 \u05E0\u05D9\u05E6\u05DC \u05D0\u05EA ${limit} \u05D4\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05E9\u05DC \u05D4\u05D7\u05D5\u05D3\u05E9. \u05D4\u05DE\u05E0\u05D4 \u05DE\u05EA\u05D7\u05D3\u05E9\u05EA \u05D1\u05EA\u05D7\u05D9\u05DC\u05EA \u05D4\u05D7\u05D5\u05D3\u05E9 \u05D4\u05D1\u05D0, \u05D0\u05D5 \u05D0\u05E4\u05E9\u05E8 \u05DC\u05E4\u05E0\u05D5\u05EA \u05DC\u05D4\u05E0\u05D4\u05DC\u05D4 / \u05DC\u05E7\u05E0\u05D5\u05EA \u05E2\u05D5\u05D3 \u05E2\u05DE\u05D3\u05D5\u05EA.`);
  }
  await store.merge(key, { owner, month: month(), kind: r.kind === "student" ? "student" : "teacher", limit }, { n: 1 });
  const refund = () => store.merge(key, {}, { n: -1 }).catch(() => {
  });
  return { subject: r.subject, kind: r.kind, refund };
}
async function me(request, env) {
  const u = await webUser(request, env);
  const store = db(env);
  const s = await settings(env);
  const [t, st] = await store.getMany(["teachers/" + u.email, "students/" + u.email]);
  const [stations, requests, sessions, payments] = await Promise.all([
    t ? store.query("stations", { where: [["teacher", "==", u.email]] }) : [],
    store.query("requests", { where: [["email", "==", u.email]] }),
    store.query("sessions", { where: [["email", "==", u.email]] }),
    store.query("payments", { where: [["email", "==", u.email]] })
  ]);
  const until = st && st.accessUntil || "";
  return {
    ok: true,
    email: u.email,
    name: u.name,
    picture: u.picture,
    admin: admins(env).includes(u.email),
    teacher: t ? Object.assign(await teacherView(t, stations.filter((x) => x.active).sort((a, b) => String(a.name).localeCompare(String(b.name), "he"))), { sends: (await monthlySends(env, [t]))[0], phone: t.phone || "" }) : null,
    student: { until, open: !!until && until >= today(), source: st && st.source || "", sends: (await monthlySends(env, [{ email: u.email, kind: "student" }]))[0] },
    requests: requests.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))).slice(0, 12).map((r) => ({
      id: r._id,
      kind: r.kind,
      amount: r.amount || 0,
      stationName: r.stationName || "",
      status: r.status,
      createdAt: r.createdAt,
      decidedAt: r.decidedAt || "",
      reason: r.reason || ""
    })),
    devices: sessions.filter((x) => !x.revoked).map((x) => ({ id: x._id, name: x.machineName || "", role: x.role, lastSeen: x.lastSeen || x.createdAt })),
    payments: payments.filter((p) => p.status !== "pending").sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))).map((p) => ({ kind: p.kind || "student", seats: p.seats || 0, amount: p.amount, status: p.status, createdAt: p.createdAt, paidAt: p.paidAt || "" })),
    price: s.price,
    accessUntil: s.accessUntil,
    canPay: s.price > 0 && !!payProvider(env),
    payBy: payProvider(env) ? payProvider(env).id : "",
    offlineDays: s.offlineDays,
    packages: t && payProvider(env) ? (s.packages || []).map((p, i) => ({ index: i, seats: p.seats, price: p.price })).filter((p) => p.price > 0) : []
  };
}
async function myRequest(request, env) {
  const u = await webUser(request, env);
  const b = await readJson(request);
  const store = db(env);
  const kind = ["teacher", "release"].includes(b.kind) ? b.kind : "";
  if (!kind) throw new Fail(400, "format", "\u05E1\u05D5\u05D2 \u05D1\u05E7\u05E9\u05D4 \u05DC\u05D0 \u05DE\u05D5\u05DB\u05E8");
  const t = await store.get("teachers/" + u.email);
  const open = (await store.query("requests", { where: [["email", "==", u.email]] })).filter((r) => r.status === "open" && r.kind === kind);
  const doc = { kind, email: u.email, name: u.name || "", note: clip2(b.note, 300), status: "open", createdAt: nowIso() };
  if (kind === "teacher") {
    if (t) throw new Fail(400, "exists", "\u05D4\u05D7\u05E9\u05D1\u05D5\u05DF \u05DB\u05D1\u05E8 \u05DE\u05D5\u05D2\u05D3\u05E8 \u05DB\u05DE\u05D5\u05E8\u05D4 \u2014 \u05E0\u05DB\u05E0\u05E1\u05D9\u05DD \u05E9\u05D5\u05D1.");
    if (open.length) return { ok: true, id: open[0]._id, already: true };
    const phone = phoneNorm(b.phone);
    if (!phone) throw new Fail(400, "phone", "\u05E6\u05E8\u05D9\u05DA \u05DE\u05E1\u05E4\u05E8 \u05D8\u05DC\u05E4\u05D5\u05DF \u05E0\u05D9\u05D9\u05D3 \u05EA\u05E7\u05E0\u05D9 \u2014 10 \u05E1\u05E4\u05E8\u05D5\u05EA (\u05DC\u05DE\u05E9\u05DC 050-1234567).");
    Object.assign(doc, { school: clip2(b.school, 120), phone, computers: intIn(b.computers, 0, 2e3) ? +b.computers : 0 });
  } else {
    if (!t || !t.active) throw new Fail(403, "teacher", "\u05E8\u05E7 \u05DE\u05D5\u05E8\u05D4 \u05E4\u05E2\u05D9\u05DC \u05D9\u05DB\u05D5\u05DC \u05DC\u05E9\u05DC\u05D5\u05D7 \u05D1\u05E7\u05E9\u05D4 \u05DB\u05D6\u05D5.");
    doc.school = t.school || "";
    {
      const st = await store.get("stations/" + clip2(b.stationId, 64));
      if (!st || st.teacher !== u.email || !st.active) throw new Fail(400, "station", "\u05D4\u05DE\u05D7\u05E9\u05D1 \u05DC\u05D0 \u05E0\u05DE\u05E6\u05D0 \u05D1\u05E8\u05E9\u05D9\u05DE\u05D4 \u05E9\u05DC\u05DA.");
      if (open.some((r) => r.stationId === st._id)) return { ok: true, id: open.find((r) => r.stationId === st._id)._id, already: true };
      Object.assign(doc, { stationId: st._id, stationName: st.name || "" });
    }
  }
  const id = timeId();
  await store.set("requests/" + id, doc);
  await log(store, u.email, "request." + kind, doc.stationName || "", doc.amount ? String(doc.amount) : doc.school || "");
  if (kind === "teacher") {
    const s = await settings(env);
    await alert(env, store, { kind: "teacher.request", email: u.email, name: u.name, detail: [doc.school, doc.phone, doc.computers ? doc.computers + " \u05DE\u05D7\u05E9\u05D1\u05D9\u05DD" : ""].filter(Boolean).join(" \xB7 ") }, { email: s.alertEmail !== false });
  }
  return { ok: true, id };
}
async function myRevokeDevice(request, env) {
  const u = await webUser(request, env);
  const b = await readJson(request);
  const store = db(env);
  const se = await store.get("sessions/" + clip2(b.id, 64));
  if (!se || se.email !== u.email) throw new Fail(404, "not_found", "\u05D4\u05DE\u05D7\u05E9\u05D1 \u05DC\u05D0 \u05E0\u05DE\u05E6\u05D0.");
  if (!se.revoked) await store.update("sessions/" + se._id, { revoked: true, revokedAt: nowIso(), revokedBy: "self" });
  return { ok: true };
}
async function assignCode(store, email) {
  for (let i = 0; i < 8; i++) {
    const c = teacherCode();
    if (await store.create("teacherCodes/" + c, { teacher: email, createdAt: nowIso() })) return c;
  }
  throw new Fail(500, "internal", "\u05DC\u05D0 \u05D4\u05E6\u05DC\u05D7\u05EA\u05D9 \u05DC\u05D9\u05E6\u05D5\u05E8 \u05E7\u05D5\u05D3 \u05DE\u05D5\u05E8\u05D4. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1.");
}
async function payStart(request, env) {
  const u = await webUser(request, env);
  const b = await readJson(request);
  const s = await settings(env);
  const provider = payProvider(env);
  if (!provider) throw new Fail(503, "pay_off", "\u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E4\u05EA\u05D5\u05D7. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1 \u05D1\u05E7\u05E8\u05D5\u05D1.");
  const store = db(env);
  const pid = timeId() + randomToken(4).replace(/[-_]/g, "x");
  let pay;
  if (b.kind === "seats") {
    const t = await store.get("teachers/" + u.email);
    if (!t || !t.active) throw new Fail(403, "teacher", "\u05E8\u05E7 \u05DE\u05D5\u05E8\u05D4 \u05E4\u05E2\u05D9\u05DC \u05D9\u05DB\u05D5\u05DC \u05DC\u05E7\u05E0\u05D5\u05EA \u05E2\u05DE\u05D3\u05D5\u05EA.");
    const pk = (s.packages || [])[+b.package];
    if (!pk || !(pk.price > 0)) throw new Fail(400, "package", "\u05D4\u05D7\u05D1\u05D9\u05DC\u05D4 \u05D4\u05D6\u05D5 \u05DC\u05D0 \u05E0\u05DE\u05DB\u05E8\u05EA \u05DB\u05E8\u05D2\u05E2.");
    pay = { kind: "seats", seats: pk.seats, amount: pk.price, title: `\u05D7\u05D1\u05D9\u05DC\u05EA ${pk.seats} \u05E2\u05DE\u05D3\u05D5\u05EA` };
  } else {
    if (!(s.price > 0)) throw new Fail(503, "pay_off", "\u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05E2\u05D5\u05D3 \u05DC\u05D0 \u05E4\u05EA\u05D5\u05D7. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1 \u05D1\u05E7\u05E8\u05D5\u05D1.");
    const st = await store.get("students/" + u.email);
    if (st && st.accessUntil && st.accessUntil >= s.accessUntil) return { ok: true, already: true };
    pay = { kind: "student", amount: s.price, accessUntil: s.accessUntil, title: "\u05DE\u05D0\u05D2\u05E8 \u05D4\u05EA\u05E8\u05D2\u05D9\u05DC\u05D9\u05DD \u05DC\u05E9\u05E0\u05EA \u05D4\u05DC\u05D9\u05DE\u05D5\u05D3\u05D9\u05DD" };
  }
  const hourAgo = new Date(Date.now() - 3600 * 1e3).toISOString();
  const open = (await store.query("payments", { where: [["email", "==", u.email]] })).filter((x) => x.status === "pending" && String(x.createdAt) > hourAgo);
  if (open.length >= 10) throw new Fail(429, "rate", "\u05D9\u05D5\u05EA\u05E8 \u05DE\u05D3\u05D9 \u05E0\u05D9\u05E1\u05D9\u05D5\u05E0\u05D5\u05EA \u05EA\u05E9\u05DC\u05D5\u05DD. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1 \u05D1\u05E2\u05D5\u05D3 \u05E9\u05E2\u05D4.");
  Object.assign(pay, { email: u.email, uid: u.uid, name: u.name || "", currency: "ILS", provider: provider.id, status: "pending", createdAt: nowIso() });
  await store.set("payments/" + pid, pay);
  const origin = new URL(request.url).origin;
  let made;
  try {
    made = await provider.create(env, Object.assign({ id: pid }, pay), origin);
  } catch (e) {
    console.error("pay create", e && e.message);
    await store.merge("payments/" + pid, { status: "failed", note: "\u05E9\u05D9\u05E8\u05D5\u05EA \u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05DC\u05D0 \u05E2\u05E0\u05D4", updatedAt: nowIso() }).catch(() => {
    });
    throw new Fail(502, "pay_down", "\u05E9\u05D9\u05E8\u05D5\u05EA \u05D4\u05EA\u05E9\u05DC\u05D5\u05DD \u05DC\u05D0 \u05D6\u05DE\u05D9\u05DF \u05DB\u05E8\u05D2\u05E2. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1 \u05D1\u05E2\u05D5\u05D3 \u05DB\u05DE\u05D4 \u05D3\u05E7\u05D5\u05EA.");
  }
  await store.merge("payments/" + pid, { providerRef: clip2(made.ref, 120), providerOrder: clip2(made.order || made.ref, 120) });
  return { ok: true, url: made.url, id: pid };
}
async function payNotify(request, env, providerId) {
  const provider = payProvider(env);
  if (!provider || provider.id !== providerId) throw new Fail(404, "not_found", "\u05DB\u05EA\u05D5\u05D1\u05EA \u05DC\u05D0 \u05DE\u05D5\u05DB\u05E8\u05EA");
  const store = db(env);
  let n;
  try {
    n = await provider.verify(env, request, store);
  } catch (e) {
    console.error("pay notify", e && e.message);
    throw new Fail(400, "notify", "\u05D4\u05D4\u05D5\u05D3\u05E2\u05D4 \u05DC\u05D0 \u05D0\u05D5\u05DE\u05EA\u05D4.");
  }
  if (!n || !n.id) return { ok: true, ignored: true };
  await settlePayment(env, n);
  return provider.ack ? provider.ack() : { ok: true };
}
async function settlePayment(env, n) {
  const store = db(env);
  const result = await store.tx(async (t) => {
    const p = await t.get("payments/" + n.id);
    if (!p) return { skip: "unknown" };
    if (n.status === "paid" && p.status !== "paid") {
      const noAmount = n.amount === void 0 && p.provider !== "demo";
      const wrongAmount = n.amount !== void 0 && Math.round(+n.amount * 100) !== Math.round(+p.amount * 100);
      const wrongCurrency = !!n.currency && !!p.currency && String(n.currency).toUpperCase() !== String(p.currency).toUpperCase();
      if (noAmount || wrongAmount || wrongCurrency) {
        t.update("payments/" + n.id, { status: "mismatch", note: `\u05E9\u05D5\u05DC\u05DD ${n.amount === void 0 ? "?" : n.amount} ${n.currency || ""} \u05D1\u05DE\u05E7\u05D5\u05DD ${p.amount} ${p.currency || ""}`.replace(/\s+/g, " "), updatedAt: nowIso() });
        return { skip: "mismatch", p };
      }
    }
    if (p.status === n.status) return { skip: "same", p };
    const seats = p.kind === "seats";
    const at = nowIso();
    const payer = n.payer ? { payerName: clip2(n.payer.name, 80), payerEmail: clip2(n.payer.email, 120), payerPhone: clip2(n.payer.phone, 30) } : {};
    if (seats) {
      const teacher = await t.get("teachers/" + p.email);
      if (n.status === "paid" && (p.status === "pending" || p.status === "failed")) {
        t.update("payments/" + n.id, Object.assign({ status: "paid", paidAt: at, providerRef: clip2(n.ref || p.providerRef, 120) }, payer));
        if (teacher) t.update("teachers/" + p.email, { updatedAt: at }, { seats: p.seats });
        return { done: "seats", p, before: teacher ? teacher.seats || 0 : 0 };
      }
      if (n.status === "refunded" && p.status === "paid") {
        t.update("payments/" + n.id, { status: "refunded", refundedAt: at });
        if (teacher) t.update("teachers/" + p.email, { seats: Math.max(0, (teacher.seats || 0) - p.seats), updatedAt: at });
        return { done: "refunded", p };
      }
    } else {
      const st = await t.get("students/" + p.email);
      if (n.status === "paid" && (p.status === "pending" || p.status === "failed")) {
        t.update("payments/" + n.id, Object.assign({ status: "paid", paidAt: at, providerRef: clip2(n.ref || p.providerRef, 120) }, payer));
        const until = st && st.accessUntil && st.accessUntil > p.accessUntil ? st.accessUntil : p.accessUntil;
        t.merge("students/" + p.email, { email: p.email, accessUntil: until, source: "payment", lastPayment: n.id, paidAt: at, phone: payer.payerPhone || st && st.phone || "" });
        return { done: "paid", p };
      }
      if (n.status === "refunded" && p.status === "paid") {
        t.update("payments/" + n.id, { status: "refunded", refundedAt: at });
        if (st && st.lastPayment === n.id) t.merge("students/" + p.email, { accessUntil: null, source: "refunded" });
        return { done: "refunded", p };
      }
    }
    if (n.status === "failed" && p.status === "pending") {
      t.update("payments/" + n.id, { status: "failed", updatedAt: at });
      return { done: "failed", p };
    }
    return { skip: "state", p };
  });
  if (result.done) {
    const what = result.done === "seats" ? `\u05D7\u05D1\u05D9\u05DC\u05EA ${result.p.seats} \u05E2\u05DE\u05D3\u05D5\u05EA \xB7 ${result.before} \u2190 ${result.before + result.p.seats}` : "";
    await log(store, "\u05EA\u05E9\u05DC\u05D5\u05DD", "payment." + result.done, result.p.email, [`${result.p.amount} \u20AA`, what].filter(Boolean).join(" \xB7 "));
    if (result.done === "paid" || result.done === "seats") {
      const s = await settings(env);
      await alert(env, store, {
        kind: result.done === "seats" ? "payment.seats" : "payment.paid",
        email: result.p.email,
        name: result.p.name,
        detail: [`${result.p.amount} \u20AA`, what].filter(Boolean).join(" \xB7 ")
      }, { email: s.alertEmail !== false });
    }
  }
  return result;
}
function teacherLimit(s, t) {
  if (t && Number.isInteger(t.monthlyLimit) && t.monthlyLimit >= 0) return t.monthlyLimit;
  return s.monthlyPerSeat * Math.max(1, t && t.seats || 0);
}
async function usageKey(kind, email) {
  return "usage/" + month() + "_" + (await sha256Hex(kind + ":" + normEmail(email))).slice(0, 24);
}
function teacherRow(t) {
  return {
    email: t.email || t._id,
    name: t.name || "",
    school: t.school || "",
    code: showCode(t.code),
    seats: t.seats || 0,
    used: t.used || 0,
    phone: t.phone || "",
    monthlyLimit: Number.isInteger(t.monthlyLimit) ? t.monthlyLimit : null,
    active: !!t.active,
    note: t.note || "",
    createdAt: t.createdAt || "",
    lastSignIn: t.lastSignIn || ""
  };
}
async function adminOverview(env) {
  const store = db(env);
  const monthStart = today().slice(0, 8) + "01";
  const [teachers, requests, students, payments, recent, unseen] = await Promise.all([
    store.query("teachers"),
    store.query("requests", { where: [["status", "==", "open"]] }),
    store.query("students", { where: [["accessUntil", ">=", today()]] }),
    store.query("payments", { where: [["createdAt", ">=", monthStart]] }),
    store.query("log", { orderBy: ["at", "desc"], limit: 15 }),
    store.query("alerts", { where: [["seen", "==", false]], limit: 200 })
  ]);
  const active = teachers.filter((t) => t.active);
  const full = active.filter((t) => (t.seats || 0) > 0 && (t.used || 0) >= (t.seats || 0));
  return {
    ok: true,
    counts: {
      teachers: active.length,
      teachersBlocked: teachers.length - active.length,
      seats: active.reduce((n, t) => n + (t.seats || 0), 0),
      used: active.reduce((n, t) => n + (t.used || 0), 0),
      students: students.length,
      studentsPaid: students.filter((s) => s.source === "payment").length,
      studentsManual: students.filter((s) => s.source === "manual").length,
      paymentsMonth: payments.filter((p) => p.status === "paid" || p.status === "refunded").length,
      refundsMonth: payments.filter((p) => p.status === "refunded").length,
      openRequests: requests.length,
      alertsUnseen: unseen.length
    },
    requests: requests.sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt))).map(requestRow),
    full: full.map(teacherRow),
    recent: recent.map((l) => ({ at: l.at, actor: l.actor, action: l.action, target: l.target, detail: l.detail }))
  };
}
function requestRow(r) {
  return {
    id: r._id,
    kind: r.kind,
    email: r.email,
    name: r.name || "",
    school: r.school || "",
    phone: r.phone || "",
    amount: r.amount || 0,
    computers: r.computers || 0,
    stationId: r.stationId || "",
    stationName: r.stationName || "",
    note: r.note || "",
    status: r.status,
    createdAt: r.createdAt,
    decidedAt: r.decidedAt || "",
    decidedBy: r.decidedBy || "",
    reason: r.reason || "",
    result: r.result || "",
    before: r.before || null
  };
}
async function monthlySends(env, rows) {
  const store = db(env), s = await settings(env);
  const keys = await Promise.all(rows.map((r) => usageKey(r.kind || "teacher", r.email || r._id)));
  const docs = keys.length ? await store.getMany(keys) : [];
  return rows.map((r, i) => ({ n: docs[i] && docs[i].n || 0, limit: (r.kind || "teacher") === "student" ? s.monthlyStudent : teacherLimit(s, r), manual: (r.kind || "teacher") !== "student" && Number.isInteger(r.monthlyLimit) }));
}
async function adminTeachers(env) {
  const store = db(env);
  const teachers = await store.query("teachers");
  const sends = await monthlySends(env, teachers);
  return { ok: true, teachers: teachers.map((t, i) => Object.assign(teacherRow(t), { sends: sends[i] })).sort((a, b) => a.name.localeCompare(b.name, "he") || a.email.localeCompare(b.email)) };
}
async function adminTeacher(env, url) {
  const store = db(env);
  const email = normEmail(url.searchParams.get("email"));
  const t = await store.get("teachers/" + email);
  if (!t) throw new Fail(404, "not_found", "\u05D4\u05DE\u05D5\u05E8\u05D4 \u05DC\u05D0 \u05E0\u05DE\u05E6\u05D0.");
  const stations = await store.query("stations", { where: [["teacher", "==", email]] });
  return {
    ok: true,
    teacher: Object.assign(teacherRow(t), { sends: (await monthlySends(env, [t]))[0] }),
    stations: stations.filter((x) => x.active).map((x) => ({ id: x._id, name: x.name, claimedAt: x.claimedAt, lastSeen: x.lastSeen })).sort((a, b) => String(a.name).localeCompare(String(b.name), "he"))
  };
}
async function adminSaveTeacher(env, u, b) {
  const store = db(env);
  const s = await settings(env);
  const email = normEmail(b.email);
  if (!emailOk(email)) throw new Fail(400, "email", "\u05DB\u05EA\u05D5\u05D1\u05EA \u05D4\u05DE\u05D9\u05D9\u05DC \u05DC\u05D0 \u05EA\u05E7\u05D9\u05E0\u05D4.");
  if (b.seats !== void 0 && !intIn(b.seats, 0, 1e4)) throw new Fail(400, "seats", "\u05DE\u05E1\u05E4\u05E8 \u05D4\u05E2\u05DE\u05D3\u05D5\u05EA: \u05D1\u05D9\u05DF 0 \u05DC-10000.");
  if (b.phone !== void 0 && String(b.phone).trim() !== "") {
    b = Object.assign({}, b, { phone: phoneNorm(b.phone) });
    if (!b.phone) throw new Fail(400, "phone", "\u05DE\u05E1\u05E4\u05E8 \u05D8\u05DC\u05E4\u05D5\u05DF \u05DC\u05D0 \u05EA\u05E7\u05D9\u05DF \u2014 \u05E0\u05D9\u05D9\u05D3 \u05D9\u05E9\u05E8\u05D0\u05DC\u05D9 \u05E9\u05DC 10 \u05E1\u05E4\u05E8\u05D5\u05EA (\u05DC\u05DE\u05E9\u05DC 050-1234567).");
  } else if (b.phone !== void 0) b = Object.assign({}, b, { phone: "" });
  if (b.monthlyLimit !== void 0) {
    if (b.monthlyLimit === null || String(b.monthlyLimit).trim() === "") b = Object.assign({}, b, { monthlyLimit: null });
    else if (!intIn(b.monthlyLimit, 0, 1e5)) throw new Fail(400, "monthlyLimit", "\u05DE\u05E0\u05EA \u05D4\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05D4\u05D7\u05D5\u05D3\u05E9\u05D9\u05EA: \u05DE\u05E1\u05E4\u05E8 \u05E9\u05DC\u05DD \u05D1\u05D9\u05DF 0 \u05DC-100000 (\u05D0\u05D5 \u05E8\u05D9\u05E7 \u05DC\u05D1\u05E8\u05D9\u05E8\u05EA \u05D4\u05DE\u05D7\u05D3\u05DC).");
    else b = Object.assign({}, b, { monthlyLimit: +b.monthlyLimit });
  }
  const at = nowIso();
  if (b.create) {
    const exists = await store.get("teachers/" + email);
    if (exists) throw new Fail(409, "exists", "\u05D4\u05DE\u05D5\u05E8\u05D4 \u05DB\u05D1\u05E8 \u05D1\u05E8\u05E9\u05D9\u05DE\u05D4.");
    const code = await assignCode(store, email);
    const seats = b.seats !== void 0 ? +b.seats : s.defaultSeats;
    const ok = await store.create("teachers/" + email, {
      email,
      name: clip2(b.name, 80),
      school: clip2(b.school, 120),
      phone: b.phone || "",
      seats,
      used: 0,
      code,
      active: true,
      note: clip2(b.note, 300),
      createdAt: at,
      updatedAt: at,
      createdBy: u.email
    });
    if (!ok) {
      await store.remove("teacherCodes/" + code);
      throw new Fail(409, "exists", "\u05D4\u05DE\u05D5\u05E8\u05D4 \u05DB\u05D1\u05E8 \u05D1\u05E8\u05E9\u05D9\u05DE\u05D4.");
    }
    await log(store, u.email, "teacher.add", email, `${seats} \u05E2\u05DE\u05D3\u05D5\u05EA`);
    return { created: true, seats };
  }
  const changes = [];
  await store.tx(async (t) => {
    const cur = await t.get("teachers/" + email);
    if (!cur) throw new Fail(404, "not_found", "\u05D4\u05DE\u05D5\u05E8\u05D4 \u05DC\u05D0 \u05E0\u05DE\u05E6\u05D0.");
    const upd = { updatedAt: at };
    changes.length = 0;
    if (b.seats !== void 0 && +b.seats !== cur.seats) {
      upd.seats = +b.seats;
      changes.push(`\u05DE\u05DB\u05E1\u05D4 ${cur.seats || 0} \u2190 ${+b.seats}`);
    }
    if (b.monthlyLimit !== void 0) {
      const before = Number.isInteger(cur.monthlyLimit) ? cur.monthlyLimit : null;
      if (b.monthlyLimit !== before) {
        upd.monthlyLimit = b.monthlyLimit;
        changes.push(`\u05E9\u05DC\u05D9\u05D7\u05D5\u05EA \u05D1\u05D7\u05D5\u05D3\u05E9 ${before === null ? "\u05D1\u05E8\u05D9\u05E8\u05EA \u05DE\u05D7\u05D3\u05DC" : before} \u2190 ${b.monthlyLimit === null ? "\u05D1\u05E8\u05D9\u05E8\u05EA \u05DE\u05D7\u05D3\u05DC" : b.monthlyLimit}`);
      }
    }
    if (b.active !== void 0 && !!b.active !== !!cur.active) {
      upd.active = !!b.active;
      changes.push(b.active ? "\u05D4\u05D5\u05E4\u05E2\u05DC" : "\u05D4\u05D5\u05E9\u05D1\u05EA");
    }
    for (const [k, n] of [["name", 80], ["school", 120], ["note", 300], ["phone", 20]]) {
      if (b[k] !== void 0 && clip2(b[k], n) !== (cur[k] || "")) {
        upd[k] = clip2(b[k], n);
        changes.push(k);
      }
    }
    t.update("teachers/" + email, upd);
  });
  if (changes.length) await log(store, u.email, "teacher.update", email, changes.join(", "));
  return { changes };
}
async function adminReleaseStation(env, u, id, why) {
  const store = db(env);
  const out = await store.tx(async (t) => {
    const st = await t.get("stations/" + id);
    if (!st) throw new Fail(404, "not_found", "\u05D4\u05E2\u05DE\u05D3\u05D4 \u05DC\u05D0 \u05E0\u05DE\u05E6\u05D0\u05D4.");
    if (!st.active) return { already: true, st };
    const teacher = await t.get("teachers/" + st.teacher);
    const at = nowIso();
    t.update("stations/" + id, { active: false, releasedAt: at, releasedBy: u.email });
    if (teacher) t.update("teachers/" + st.teacher, { used: Math.max(0, (teacher.used || 0) - 1), updatedAt: at });
    return { st };
  });
  if (!out.already) await log(store, u.email, "station.release", out.st.teacher, `${out.st.name}${why ? " \xB7 " + why : ""}`);
  return out;
}
async function adminStations(env, url) {
  const store = db(env);
  const teacher = normEmail(url.searchParams.get("teacher"));
  const list = teacher ? await store.query("stations", { where: [["teacher", "==", teacher]] }) : await store.query("stations", { where: [["active", "==", true]] });
  return {
    ok: true,
    stations: list.filter((x) => x.active).map((x) => ({ id: x._id, name: x.name, teacher: x.teacher, teacherName: x.teacherName || "", claimedAt: x.claimedAt, lastSeen: x.lastSeen })).sort((a, b) => String(a.lastSeen).localeCompare(String(b.lastSeen)))
  };
}
async function adminStudents(env, url) {
  const store = db(env);
  const all = await store.query("students", { limit: 5e3 });
  const t = today();
  const rows = all.map((s) => ({
    email: s.email || s._id,
    name: s.name || "",
    until: s.accessUntil || "",
    open: !!s.accessUntil && s.accessUntil >= t,
    source: s.source || "",
    note: s.note || "",
    paidAt: s.paidAt || "",
    lastSignIn: s.lastSignIn || "",
    phone: s.phone || ""
  }));
  const sends = await monthlySends(env, rows.map((r) => ({ email: r.email, kind: "student" })));
  rows.forEach((r, i) => {
    r.sends = sends[i];
  });
  return { ok: true, students: rows.sort((a, b) => String(b.paidAt || b.lastSignIn).localeCompare(String(a.paidAt || a.lastSignIn))) };
}
async function adminStudent(env, url) {
  const store = db(env);
  const email = normEmail(url.searchParams.get("email"));
  const [st, payments, sessions] = await Promise.all([
    store.get("students/" + email),
    store.query("payments", { where: [["email", "==", email]] }),
    store.query("sessions", { where: [["email", "==", email]] })
  ]);
  return {
    ok: true,
    email,
    student: st ? { name: st.name || "", until: st.accessUntil || "", source: st.source || "", note: st.note || "", phone: st.phone || "" } : null,
    sends: (await monthlySends(env, [{ email, kind: "student" }]))[0],
    payments: payments.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))).map((p) => ({ id: p._id, kind: p.kind || "student", seats: p.seats || 0, amount: p.amount, status: p.status, provider: p.provider, ref: p.providerRef || "", createdAt: p.createdAt, paidAt: p.paidAt || "", payerName: p.payerName || "", payerPhone: p.payerPhone || "", payerEmail: p.payerEmail || "" })),
    devices: sessions.filter((x) => !x.revoked).map((x) => ({ id: x._id, name: x.machineName || "", role: x.role, lastSeen: x.lastSeen || x.createdAt, createdAt: x.createdAt || "" }))
  };
}
async function adminGrant(env, u, b) {
  const store = db(env);
  const emails = (Array.isArray(b.emails) ? b.emails : String(b.email || "").split(/[\s,;]+/)).map(normEmail).filter(Boolean);
  if (!emails.length) throw new Fail(400, "email", "\u05D7\u05E1\u05E8\u05D4 \u05DB\u05EA\u05D5\u05D1\u05EA \u05DE\u05D9\u05D9\u05DC.");
  if (emails.length > 500) throw new Fail(400, "email", "\u05E2\u05D3 500 \u05DB\u05EA\u05D5\u05D1\u05D5\u05EA \u05D1\u05D1\u05EA \u05D0\u05D7\u05EA.");
  const bad = emails.filter((e) => !emailOk(e));
  if (bad.length) throw new Fail(400, "email", "\u05DB\u05EA\u05D5\u05D1\u05D5\u05EA \u05DC\u05D0 \u05EA\u05E7\u05D9\u05E0\u05D5\u05EA: " + bad.slice(0, 5).join(", "));
  const revoke = b.mode === "revoke";
  if (!revoke && !dateOk(b.until)) throw new Fail(400, "until", "\u05EA\u05D0\u05E8\u05D9\u05DA \u05DC\u05D0 \u05EA\u05E7\u05D9\u05DF.");
  const at = nowIso();
  for (let i = 0; i < emails.length; i += 200) {
    await store.commit((w) => {
      for (const e of emails.slice(i, i + 200)) {
        w.merge("students/" + e, revoke ? { email: e, accessUntil: null, source: "revoked", note: clip2(b.note, 200), updatedAt: at, updatedBy: u.email } : { email: e, accessUntil: b.until, source: "manual", note: clip2(b.note, 200), updatedAt: at, updatedBy: u.email });
      }
    });
  }
  await log(store, u.email, revoke ? "student.revoke" : "student.grant", emails.length === 1 ? emails[0] : `${emails.length} \u05EA\u05DC\u05DE\u05D9\u05D3\u05D9\u05DD`, `${revoke ? "" : "\u05E2\u05D3 " + b.until}${b.note ? " \xB7 " + clip2(b.note, 120) : ""}`);
  return { ok: true, count: emails.length };
}
async function adminRequests(env, url) {
  const store = db(env);
  const done = url.searchParams.get("status") === "done";
  const list = done ? (await store.query("requests", { orderBy: ["createdAt", "desc"], limit: 200 })).filter((r) => r.status !== "open") : await store.query("requests", { where: [["status", "==", "open"]] });
  return { ok: true, requests: list.sort((a, b) => done ? String(b.decidedAt).localeCompare(String(a.decidedAt)) : String(a.createdAt).localeCompare(String(b.createdAt))).map(requestRow) };
}
async function adminDecide(env, u, b) {
  const store = db(env);
  const r = await store.get("requests/" + clip2(b.id, 64));
  if (!r) throw new Fail(404, "not_found", "\u05D4\u05D1\u05E7\u05E9\u05D4 \u05DC\u05D0 \u05E0\u05DE\u05E6\u05D0\u05D4.");
  if (r.status !== "open") throw new Fail(409, "done", "\u05D4\u05D1\u05E7\u05E9\u05D4 \u05DB\u05D1\u05E8 \u05D8\u05D5\u05E4\u05DC\u05D4.");
  const at = nowIso();
  let result = "";
  if (b.decision === "approve") {
    if (r.kind === "teacher") {
      const seats = b.seats !== void 0 ? +b.seats : (await settings(env)).defaultSeats;
      const exists = await store.get("teachers/" + r.email);
      if (exists) await adminSaveTeacher(env, u, { email: r.email, active: true, seats: b.seats !== void 0 ? seats : void 0 });
      else await adminSaveTeacher(env, u, { email: r.email, name: r.name, school: r.school, phone: r.phone || "", seats, create: true });
      result = `\u05D4\u05D5\u05D2\u05D3\u05E8 \u05DB\u05DE\u05D5\u05E8\u05D4 \xB7 ${seats} \u05E2\u05DE\u05D3\u05D5\u05EA`;
    } else if (r.kind === "seats") {
      const add = b.amount !== void 0 ? +b.amount : r.amount;
      if (!intIn(add, 1, 2e3)) throw new Fail(400, "amount", "\u05DB\u05DE\u05D4 \u05DC\u05D4\u05D5\u05E1\u05D9\u05E3? \u05DE\u05E1\u05E4\u05E8 \u05D1\u05D9\u05DF 1 \u05DC-2000.");
      const t = await store.get("teachers/" + r.email);
      if (!t) throw new Fail(404, "not_found", "\u05D4\u05DE\u05D5\u05E8\u05D4 \u05DC\u05D0 \u05E0\u05DE\u05E6\u05D0.");
      await adminSaveTeacher(env, u, { email: r.email, seats: (t.seats || 0) + add });
      result = `\u05DE\u05DB\u05E1\u05D4 ${t.seats || 0} \u2190 ${(t.seats || 0) + add}`;
    } else if (r.kind === "release") {
      await adminReleaseStation(env, u, r.stationId, "\u05DC\u05E4\u05D9 \u05D1\u05E7\u05E9\u05EA \u05D4\u05DE\u05D5\u05E8\u05D4");
      result = `\u05E9\u05D5\u05D7\u05E8\u05E8: ${r.stationName}`;
    }
    await store.update("requests/" + r._id, { status: "approved", decidedAt: at, decidedBy: u.email, result });
  } else if (b.decision === "decline") {
    await store.update("requests/" + r._id, { status: "declined", decidedAt: at, decidedBy: u.email, reason: clip2(b.reason, 300) });
    await log(store, u.email, "request.decline", r.email, r.kind);
  } else {
    throw new Fail(400, "format", "\u05D0\u05D9\u05E9\u05D5\u05E8 \u05D0\u05D5 \u05D3\u05D7\u05D9\u05D9\u05D4?");
  }
  return { ok: true, result };
}
async function adminLog(env, url) {
  const store = db(env);
  const limit = Math.min(500, Math.max(10, +url.searchParams.get("limit") || 100));
  const asc = url.searchParams.get("order") === "asc";
  const cursor = url.searchParams.get(asc ? "after" : "before");
  const list = await store.query("log", {
    ...cursor ? { where: [["at", asc ? ">" : "<", cursor]] } : {},
    orderBy: ["at", asc ? "asc" : "desc"],
    limit
  });
  return { ok: true, log: list.map((l) => ({ at: l.at, actor: l.actor, action: l.action, target: l.target, detail: l.detail })) };
}
async function adminPayments(env, url) {
  const store = db(env);
  const list = await store.query("payments", { orderBy: ["createdAt", "desc"], limit: Math.min(1e3, +url.searchParams.get("limit") || 300) });
  return {
    ok: true,
    payments: list.filter((p) => p.status !== "pending" || Date.now() - Date.parse(p.createdAt) < 864e5).map((p) => ({
      id: p._id,
      email: p.email,
      name: p.name || "",
      kind: p.kind || "student",
      seats: p.seats || 0,
      amount: p.amount,
      status: p.status,
      provider: p.provider,
      ref: p.providerRef || "",
      createdAt: p.createdAt,
      paidAt: p.paidAt || "",
      payerName: p.payerName || "",
      payerEmail: p.payerEmail || "",
      payerPhone: p.payerPhone || ""
    }))
  };
}
async function adminSettings(env, u, b) {
  const store = db(env);
  if (b) {
    const upd = {};
    const changes = [];
    const cur = await settings(env, true);
    for (const [k, [lo, hi]] of Object.entries(SETTING_LIMITS)) {
      if (b[k] === void 0) continue;
      if (!intIn(b[k], lo, hi)) throw new Fail(400, "format", `\u05E2\u05E8\u05DA \u05DC\u05D0 \u05EA\u05E7\u05D9\u05DF: ${k}`);
      if (+b[k] !== cur[k]) {
        upd[k] = +b[k];
        changes.push(`${k}: ${cur[k]} \u2190 ${+b[k]}`);
      }
    }
    if (b.accessUntil !== void 0) {
      if (!dateOk(b.accessUntil)) throw new Fail(400, "format", "\u05EA\u05D0\u05E8\u05D9\u05DA \u05DC\u05D0 \u05EA\u05E7\u05D9\u05DF.");
      if (b.accessUntil !== cur.accessUntil) {
        upd.accessUntil = b.accessUntil;
        changes.push(`accessUntil: ${cur.accessUntil} \u2190 ${b.accessUntil}`);
      }
    }
    if (b.packages !== void 0) {
      if (!Array.isArray(b.packages) || b.packages.length > 6 || !b.packages.every((p) => p && intIn(p.seats, 1, 500) && intIn(p.price, 0, 2e4))) throw new Fail(400, "format", "\u05D7\u05D1\u05D9\u05DC\u05D5\u05EA: \u05E2\u05D3 6, \u05E2\u05DE\u05D3\u05D5\u05EA 1\u2013500, \u05DE\u05D7\u05D9\u05E8 0\u201320000.");
      const pk = b.packages.map((p) => ({ seats: +p.seats, price: +p.price }));
      if (JSON.stringify(pk) !== JSON.stringify(cur.packages)) {
        upd.packages = pk;
        changes.push("\u05D7\u05D1\u05D9\u05DC\u05D5\u05EA: " + pk.map((p) => `${p.seats}=${p.price}\u20AA`).join(", "));
      }
    }
    if (b.alertEmail !== void 0 && !!b.alertEmail !== (cur.alertEmail !== false)) {
      upd.alertEmail = !!b.alertEmail;
      changes.push(b.alertEmail ? "\u05D4\u05EA\u05E8\u05D0\u05D5\u05EA \u05D1\u05DE\u05D9\u05D9\u05DC: \u05D3\u05D5\u05DC\u05E7" : "\u05D4\u05EA\u05E8\u05D0\u05D5\u05EA \u05D1\u05DE\u05D9\u05D9\u05DC: \u05DB\u05D1\u05D5\u05D9");
    }
    if (b.requireLoginForGemini !== void 0 && !!b.requireLoginForGemini !== !!cur.requireLoginForGemini) {
      upd.requireLoginForGemini = !!b.requireLoginForGemini;
      changes.push(b.requireLoginForGemini ? "Gemini: \u05E8\u05E7 \u05DC\u05DE\u05D7\u05D5\u05D1\u05E8\u05D9\u05DD" : "Gemini: \u05E4\u05EA\u05D5\u05D7 \u05DC\u05DB\u05D5\u05DC\u05DD");
    }
    if (changes.length) {
      upd.updatedAt = nowIso();
      upd.updatedBy = u.email;
      await store.merge("config/settings", upd);
      settingsCache.at = 0;
      await log(store, u.email, "settings", "", changes.join(", "));
    }
  }
  const s = await settings(env, true);
  const key = await passKey(env);
  return {
    ok: true,
    settings: Object.fromEntries(Object.keys(DEFAULTS).map((k) => [k, s[k]])),
    admins: admins(env),
    provider: payProvider(env) ? payProvider(env).id : "",
    providers: PAY_PROVIDERS,
    passKey: !!key,
    signedIn: u.email,
    alertMail: { ready: mailReady(env), to: alertTo(env) }
  };
}
var JSON_ROUTES = {
  "GET /v1/access/config": async (req, env) => {
    const s = await settings(env);
    return {
      ok: true,
      enabled: true,
      requireLogin: !!s.requireLoginForGemini,
      price: s.price,
      accessUntil: s.accessUntil,
      offlineDays: s.offlineDays,
      canPay: s.price > 0 && !!payProvider(env),
      firebase: { apiKey: env.FIREBASE_API_KEY || "", authDomain: env.FIREBASE_AUTH_DOMAIN || projectId(env) + ".firebaseapp.com", projectId: projectId(env) },
      emulator: firestoreEmulator(env) ? { auth: env.FIREBASE_AUTH_EMULATOR_HOST || "" } : void 0
    };
  },
  "GET /v1/access/key": async (req, env) => {
    const key = await passKey(env);
    if (!key) throw new Fail(503, "config", "PASS_KEY \u05DC\u05D0 \u05D4\u05D5\u05D2\u05D3\u05E8");
    if (!keyCache.spki) keyCache.spki = await publicSpki(key);
    return { ok: true, spki: keyCache.spki };
  },
  "POST /v1/auth/exchange": exchange,
  "POST /v1/auth/redeem": redeem,
  "POST /v1/access/check": check,
  "POST /v1/access/logout": logout,
  "POST /v1/station/claim": claim,
  "GET /v1/me": me,
  "POST /v1/me/request": myRequest,
  "POST /v1/me/devices/revoke": myRevokeDevice,
  "POST /v1/pay/start": payStart
};
var ADMIN_ROUTES = {
  "GET /v1/admin/overview": (env, u, url) => adminOverview(env),
  "GET /v1/admin/teachers": (env, u, url) => adminTeachers(env),
  "GET /v1/admin/teacher": (env, u, url) => adminTeacher(env, url),
  "POST /v1/admin/teacher": async (env, u, url, b) => Object.assign({ ok: true }, await adminSaveTeacher(env, u, b)),
  "GET /v1/admin/stations": (env, u, url) => adminStations(env, url),
  "POST /v1/admin/station/release": async (env, u, url, b) => {
    await adminReleaseStation(env, u, clip2(b.id, 64), clip2(b.reason, 120));
    return { ok: true };
  },
  "GET /v1/admin/students": (env, u, url) => adminStudents(env, url),
  "GET /v1/admin/student": (env, u, url) => adminStudent(env, url),
  "POST /v1/admin/student": (env, u, url, b) => adminGrant(env, u, b),
  "POST /v1/admin/session/revoke": async (env, u, url, b) => {
    const store = db(env);
    const se = await store.get("sessions/" + clip2(b.id, 64));
    if (!se) throw new Fail(404, "not_found", "\u05DC\u05D0 \u05E0\u05DE\u05E6\u05D0.");
    if (!se.revoked) await store.update("sessions/" + se._id, { revoked: true, revokedAt: nowIso(), revokedBy: u.email });
    await log(store, u.email, "session.revoke", se.email, se.machineName || "");
    return { ok: true };
  },
  "GET /v1/admin/payments": (env, u, url) => adminPayments(env, url),
  "GET /v1/admin/requests": (env, u, url) => adminRequests(env, url),
  "POST /v1/admin/request": (env, u, url, b) => adminDecide(env, u, b),
  "GET /v1/admin/log": (env, u, url) => adminLog(env, url),
  "GET /v1/admin/settings": (env, u) => adminSettings(env, u, null),
  "GET /v1/admin/alerts": (env, u, url) => listAlerts(db(env), url.searchParams.get("limit")),
  "POST /v1/admin/alerts/seen": (env, u, url, b) => markSeen(db(env), b),
  "POST /v1/admin/settings": (env, u, url, b) => adminSettings(env, u, b)
};
async function handleAccess(request, env, ctx, url) {
  const path = url.pathname;
  const isOurs = path.startsWith("/v1/access/") || path.startsWith("/v1/auth/") || path.startsWith("/v1/station/") || path === "/v1/me" || path.startsWith("/v1/me/") || path.startsWith("/v1/admin/") || path.startsWith("/v1/pay/") || path.startsWith("/pay/") || ["/signin", "/welcome", "/account", "/admin"].includes(path) || path.startsWith("/static/");
  if (!isOurs) return null;
  if (ctx && typeof ctx.waitUntil === "function") env = Object.assign({}, env, { [WAIT]: (p) => ctx.waitUntil(p) });
  if (!accessEnabled(env)) {
    if (path === "/v1/access/config") return reply(200, { ok: true, enabled: false });
    throw new Fail(404, "not_found", "\u05DB\u05EA\u05D5\u05D1\u05EA \u05DC\u05D0 \u05DE\u05D5\u05DB\u05E8\u05EA");
  }
  if (request.method === "GET" && !path.startsWith("/v1/")) {
    const page2 = await servePage(path, env, url);
    if (page2) return page2;
  }
  const notify = /^\/v1\/pay\/notify\/([a-z0-9-]{2,20})$/.exec(path);
  if (notify) return reply(200, await payNotify(request, env, notify[1]));
  const provider = payProvider(env);
  if (provider && provider.routes) {
    const r = await provider.routes(request, env, url, { settlePayment: (n) => settlePayment(env, n), db: () => db(env) });
    if (r) return r;
  }
  const key = request.method + " " + path;
  if (JSON_ROUTES[key]) return reply(200, await JSON_ROUTES[key](request, env, url));
  if (ADMIN_ROUTES[key]) {
    const u = await adminUser(request, env);
    const b = request.method === "POST" ? await readJson(request) : null;
    return reply(200, await ADMIN_ROUTES[key](env, u, url, b));
  }
  throw new Fail(404, "not_found", "\u05DB\u05EA\u05D5\u05D1\u05EA \u05DC\u05D0 \u05DE\u05D5\u05DB\u05E8\u05EA");
}

// src/index.js
var LIMITS = {
  files: 10,
  fileBytes: 1024 * 1024,
  totalBytes: 1024 * 1024,
  // הגבלה של בעל המערכת: עד 1MB ביחד לכל שליחה
  textChars: 6e4,
  noteChars: 600,
  repairChars: 12e4
};
var TYPES2 = {
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
      if (e instanceof Fail) res = reply(e.status, Object.assign({ ok: false, code: e.code, error: e.message }, e.detail ? { detail: String(e.detail).slice(0, 300) } : {}, e.extra || {}));
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
var VERSION = "2026-11-05.1";
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
  const acc = await handleAccess(request, env, ctx, url);
  if (acc) return acc;
  if (request.method !== "POST") throw new Fail(405, "method", "\u05E8\u05E7 POST");
  if (url.pathname === "/v1/license") return reply(200, await license(request, env));
  const site = url.pathname === "/v1/site/exercise";
  const probe = url.pathname === "/v1/health/gemini";
  if (url.pathname !== "/v1/exercise" && url.pathname !== "/v1/repair" && !site && !probe) throw new Fail(404, "not_found", "\u05DB\u05EA\u05D5\u05D1\u05EA \u05DC\u05D0 \u05DE\u05D5\u05DB\u05E8\u05EA");
  if (site) {
    if (!siteOrigin(request, env)) throw new Fail(403, "origin", "\u05D0\u05E4\u05E9\u05E8 \u05DC\u05E9\u05DC\u05D5\u05D7 \u05DE\u05DB\u05D0\u05DF \u05E8\u05E7 \u05DE\u05D4\u05D0\u05EA\u05E8 \u05E9\u05DC DSViz.");
  } else if (env.APP_KEY && request.headers.get("x-dsviz-key") !== env.APP_KEY) throw new Fail(401, "key", "\u05D4\u05E9\u05E8\u05EA \u05DC\u05D0 \u05DE\u05DB\u05D9\u05E8 \u05D0\u05EA \u05D4\u05EA\u05D5\u05E1\u05E3 \u05D4\u05D6\u05D4 (\u05DE\u05E4\u05EA\u05D7 \u05E9\u05D2\u05D5\u05D9). \u05E2\u05D3\u05DB\u05E0\u05D5 \u05D0\u05EA \u05D4\u05EA\u05D5\u05E1\u05E3.");
  if (!env.OPENAI_API_KEY && !env.GEMINI_API_KEY) throw new Fail(503, "config", "\u05D4\u05E9\u05E8\u05EA \u05E2\u05D3\u05D9\u05D9\u05DF \u05DC\u05D0 \u05D4\u05D5\u05D2\u05D3\u05E8 (\u05D7\u05E1\u05E8 \u05DE\u05E4\u05EA\u05D7 \u05D4-AI).");
  const ip = request.headers.get("cf-connecting-ip") || "local";
  const gate = site || probe ? null : await geminiGate(request, env);
  await rateLimit(env, gate ? gate.subject : ip, site);
  if (probe) return reply(200, { ok: true, version: VERSION, models: env.OPENAI_API_KEY ? await probeOpenAI(env) : await probeGemini(env) });
  const declared = +(request.headers.get("content-length") || 0);
  if (declared > LIMITS.totalBytes * 1.5) throw new Fail(413, "too_big", "\u05D4\u05E7\u05D1\u05E6\u05D9\u05DD \u05D2\u05D3\u05D5\u05DC\u05D9\u05DD \u05DE\u05D3\u05D9 (\u05E2\u05D3 1MB \u05D1\u05D9\u05D7\u05D3).");
  let req;
  if (url.pathname === "/v1/exercise" || site) req = await exerciseParts(request);
  else req = await repairParts(request);
  let result;
  try {
    result = env.OPENAI_API_KEY ? await askOpenAI(env, req.userParts, req.lang) : await askGemini(env, req.userParts, req.lang);
  } catch (e) {
    if (gate && gate.refund && !(e instanceof Fail && e.status < 500 && e.status !== 429)) await gate.refund();
    throw e;
  }
  const exercise = check2(result.data, req.previous);
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
    if (f.size > LIMITS.fileBytes) throw new Fail(413, "too_big", "\u05D0\u05D7\u05D3 \u05D4\u05E7\u05D1\u05E6\u05D9\u05DD \u05D2\u05D3\u05D5\u05DC \u05DE\u05D3\u05D9 (\u05E2\u05D3 1MB).");
    total += f.size;
  }
  if (total > LIMITS.totalBytes) throw new Fail(413, "too_big", "\u05D4\u05E7\u05D1\u05E6\u05D9\u05DD \u05D2\u05D3\u05D5\u05DC\u05D9\u05DD \u05DE\u05D3\u05D9 (\u05E2\u05D3 1MB \u05D1\u05D9\u05D7\u05D3).");
  const howMany = files.length + texts.length;
  const parts = [JSON.stringify({ text: orderNotice(howMany) })];
  for (const [i, f] of files.entries()) {
    const label = typeof names[i] === "string" && names[i] ? names[i] : f.name || "\u05E7\u05D5\u05D1\u05E5";
    const buf = new Uint8Array(await f.arrayBuffer());
    const mime = String(f.type || "").toLowerCase().split(";")[0].trim();
    const ok = TYPES2[mime];
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
  let last = null, busy = false, config = null;
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
      const g = googleError(raw);
      if (serverSide(res.status, g)) {
        config = `${model}: ${res.status} ${g.status} ${g.reason}`.trim();
        last = config;
        continue;
      }
      throw new Fail(502, "gemini", "\u05E9\u05E8\u05EA \u05D4-AI \u05DC\u05D0 \u05D4\u05E6\u05DC\u05D9\u05D7 \u05DC\u05E7\u05E8\u05D5\u05D0 \u05D0\u05EA \u05D4\u05E7\u05D1\u05E6\u05D9\u05DD. \u05E0\u05E1\u05D5 \u05DC\u05E9\u05DC\u05D5\u05D7 \u05E6\u05D9\u05DC\u05D5\u05DD \u05DE\u05E1\u05DA \u05D1\u05E8\u05D5\u05E8 \u05D0\u05D5 PDF.");
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
  if (config && !busy) throw new Fail(503, "service", "\u05E9\u05DC\u05D9\u05D7\u05EA \u05EA\u05E8\u05D2\u05D9\u05DC \u05DC\u05D0 \u05D6\u05DE\u05D9\u05E0\u05D4 \u05DB\u05E8\u05D2\u05E2 \u05D1\u05D2\u05DC\u05DC \u05EA\u05E7\u05DC\u05D4 \u05D1\u05E9\u05E8\u05EA (\u05DC\u05D0 \u05D1\u05E7\u05D1\u05E6\u05D9\u05DD \u05E9\u05DC\u05DB\u05DD). \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1 \u05DE\u05D0\u05D5\u05D7\u05E8 \u05D9\u05D5\u05EA\u05E8.", config);
  if (busy) throw new Fail(429, "quota", "\u05E9\u05E8\u05EA \u05D4-AI \u05E2\u05DE\u05D5\u05E1 \u05DC\u05E2\u05DB\u05E9\u05D9\u05D5 (\u05DE\u05DB\u05E1\u05EA \u05D4\u05E9\u05D9\u05DE\u05D5\u05E9 \u05D4\u05D2\u05D9\u05E2\u05D4 \u05DC\u05DE\u05E7\u05E1\u05D9\u05DE\u05D5\u05DD). \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1 \u05D1\u05E2\u05D5\u05D3 \u05D3\u05E7\u05D4 (\u05D0\u05D5 \u05DE\u05D7\u05E8, \u05D0\u05DD \u05E0\u05D2\u05DE\u05E8\u05D4 \u05D4\u05DE\u05DB\u05E1\u05D4 \u05D4\u05D9\u05D5\u05DE\u05D9\u05EA).");
  throw new Fail(502, "gemini", "\u05DC\u05D0 \u05D4\u05EA\u05E7\u05D1\u05DC\u05D4 \u05EA\u05E9\u05D5\u05D1\u05D4 \u05EA\u05E7\u05D9\u05E0\u05D4 \u05DE\u05E9\u05E8\u05EA \u05D4-AI. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1.");
}
var OPENAI_BASE = "https://api.openai.com";
var DEFAULT_OPENAI_MODELS = "gpt-5.6-luna";
function openaiModels(env) {
  return String(env.OPENAI_MODELS || DEFAULT_OPENAI_MODELS).split(",").map((x) => x.trim()).filter(Boolean);
}
function openaiSchema(n) {
  if (Array.isArray(n)) return n.map(openaiSchema);
  if (n && typeof n === "object") {
    const o = {};
    for (const [k, v] of Object.entries(n)) {
      if (k === "propertyOrdering") continue;
      o[k] = k === "type" && typeof v === "string" ? v.toLowerCase() : openaiSchema(v);
    }
    return o;
  }
  return n;
}
var OPENAI_SCHEMA = openaiSchema(RESPONSE_SCHEMA);
function openaiParts(userParts) {
  const inline = /^\{"inlineData":\{"mimeType":"([^"]+)","data":"([A-Za-z0-9+/=]*)"\}\}$/;
  return userParts.map((p) => {
    if (p.startsWith('{"inlineData"')) {
      const m = inline.exec(p);
      if (!m) throw new Fail(500, "internal", "\u05E9\u05D2\u05D9\u05D0\u05D4 \u05E4\u05E0\u05D9\u05DE\u05D9\u05EA \u05D1\u05D4\u05DB\u05E0\u05EA \u05D4\u05E7\u05D1\u05E6\u05D9\u05DD");
      return m[1] === "application/pdf" ? '{"type":"file","file":{"filename":"exercise.pdf","file_data":"data:application/pdf;base64,' + m[2] + '"}}' : '{"type":"image_url","image_url":{"url":"data:' + m[1] + ";base64," + m[2] + '"}}';
    }
    return JSON.stringify({ type: "text", text: JSON.parse(p).text });
  });
}
async function askOpenAI(env, userParts, lang) {
  const base = env.OPENAI_BASE || OPENAI_BASE;
  const content = "[" + openaiParts(userParts).join(",") + "]";
  const bodyFor = (model) => '{"model":' + JSON.stringify(model) + ',"messages":[{"role":"system","content":' + JSON.stringify(systemPrompt(lang)) + '},{"role":"user","content":' + content + '}],"response_format":' + JSON.stringify({ type: "json_schema", json_schema: { name: "exercise", schema: OPENAI_SCHEMA, strict: false } }) + ',"max_completion_tokens":' + (+env.OPENAI_MAX_TOKENS || 3e4) + "}";
  let last = null, busy = false, config = null;
  for (const model of openaiModels(env)) {
    let res, raw;
    try {
      res = await fetch(`${base}/v1/chat/completions`, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: "Bearer " + env.OPENAI_API_KEY },
        body: bodyFor(model),
        signal: AbortSignal.timeout(+(env.TIMEOUT_MS || 1e5))
      });
      raw = await res.text();
    } catch (e) {
      last = `${model}: ${e && e.name === "TimeoutError" ? "timeout" : String(e)}`;
      continue;
    }
    if (res.status === 429 || res.status >= 500 || res.status === 404) {
      let code = "";
      try {
        code = String(JSON.parse(raw).error.code || "");
      } catch (e) {
      }
      if (res.status === 429 && code === "insufficient_quota") config = `${model}: 429 insufficient_quota`;
      else if (res.status === 429 || res.status === 503) busy = true;
      last = `${model}: ${res.status} ${raw.slice(0, 300)}`;
      continue;
    }
    if (!res.ok) {
      console.error("openai", model, res.status, raw.slice(0, 500));
      if (res.status === 401 || res.status === 403) {
        config = `${model}: ${res.status}`;
        last = config;
        continue;
      }
      throw new Fail(502, "ai", "\u05E9\u05E8\u05EA \u05D4-AI \u05DC\u05D0 \u05D4\u05E6\u05DC\u05D9\u05D7 \u05DC\u05E7\u05E8\u05D5\u05D0 \u05D0\u05EA \u05D4\u05E7\u05D1\u05E6\u05D9\u05DD. \u05E0\u05E1\u05D5 \u05DC\u05E9\u05DC\u05D5\u05D7 \u05E6\u05D9\u05DC\u05D5\u05DD \u05DE\u05E1\u05DA \u05D1\u05E8\u05D5\u05E8 \u05D0\u05D5 PDF.");
    }
    let data = null, finish = "";
    try {
      const j = JSON.parse(raw);
      const ch = j.choices && j.choices[0];
      finish = ch && ch.finish_reason || "";
      const u = j.usage || {};
      console.log("openai usage", model, u.prompt_tokens, u.completion_tokens);
      data = JSON.parse(stripFence(ch && ch.message && ch.message.content || ""));
    } catch (e) {
      last = `${model}: bad json (${finish})`;
      if (finish === "length") console.error("openai length", model, raw.length);
      continue;
    }
    return { data, model };
  }
  console.error("all openai models failed", last);
  if (config && !busy) throw new Fail(503, "service", "\u05E9\u05DC\u05D9\u05D7\u05EA \u05EA\u05E8\u05D2\u05D9\u05DC \u05DC\u05D0 \u05D6\u05DE\u05D9\u05E0\u05D4 \u05DB\u05E8\u05D2\u05E2 \u05D1\u05D2\u05DC\u05DC \u05EA\u05E7\u05DC\u05D4 \u05D1\u05E9\u05E8\u05EA (\u05DC\u05D0 \u05D1\u05E7\u05D1\u05E6\u05D9\u05DD \u05E9\u05DC\u05DB\u05DD). \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1 \u05DE\u05D0\u05D5\u05D7\u05E8 \u05D9\u05D5\u05EA\u05E8.", config);
  if (busy) throw new Fail(429, "quota", "\u05E9\u05E8\u05EA \u05D4-AI \u05E2\u05DE\u05D5\u05E1 \u05DC\u05E2\u05DB\u05E9\u05D9\u05D5. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1 \u05D1\u05E2\u05D5\u05D3 \u05D3\u05E7\u05D4.");
  throw new Fail(502, "ai", "\u05DC\u05D0 \u05D4\u05EA\u05E7\u05D1\u05DC\u05D4 \u05EA\u05E9\u05D5\u05D1\u05D4 \u05EA\u05E7\u05D9\u05E0\u05D4 \u05DE\u05E9\u05E8\u05EA \u05D4-AI. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1.");
}
async function probeOpenAI(env) {
  const base = env.OPENAI_BASE || OPENAI_BASE;
  const out = [];
  for (const model of openaiModels(env)) {
    try {
      const res = await fetch(`${base}/v1/chat/completions`, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: "Bearer " + env.OPENAI_API_KEY },
        body: JSON.stringify({ model, messages: [{ role: "user", content: "ping" }], max_completion_tokens: 16 }),
        signal: AbortSignal.timeout(2e4)
      });
      const raw = await res.text();
      let e = {};
      try {
        e = JSON.parse(raw).error || {};
      } catch (x) {
      }
      out.push({ model, http: res.status, status: res.ok ? "OK" : String(e.type || ""), reason: res.ok ? "" : String(e.code || ""), message: res.ok ? "" : String(e.message || "").slice(0, 200) });
    } catch (e) {
      out.push({ model, http: 0, status: e && e.name === "TimeoutError" ? "TIMEOUT" : "NETWORK", reason: "", message: String(e).slice(0, 200) });
    }
  }
  return out;
}
function googleError(raw) {
  try {
    const e = JSON.parse(raw).error || {};
    const reason = (Array.isArray(e.details) ? e.details : []).map((d) => d && d.reason).filter(Boolean)[0] || "";
    return { status: String(e.status || ""), reason: String(reason), message: String(e.message || "").slice(0, 200) };
  } catch (x) {
    return { status: "", reason: "", message: String(raw || "").slice(0, 200) };
  }
}
function serverSide(http, g) {
  return http === 401 || http === 403 || /API_KEY|SERVICE_DISABLED|BILLING|ACCESS_TOKEN|CONSUMER_SUSPENDED/i.test(g.reason) || /UNAUTHENTICATED|PERMISSION_DENIED|FAILED_PRECONDITION/.test(g.status) || /api key|billing|not supported for generateContent|location is not supported/i.test(g.message);
}
async function probeGemini(env) {
  const base = env.GEMINI_BASE || GEMINI_BASE;
  const out = [];
  for (const model of models(env)) {
    try {
      const res = await fetch(`${base}/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
        method: "POST",
        headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
        body: JSON.stringify({ contents: [{ role: "user", parts: [{ text: "ping" }] }], generationConfig: { maxOutputTokens: 16 } }),
        signal: AbortSignal.timeout(2e4)
      });
      const raw = await res.text();
      const g = res.ok ? { status: "OK", reason: "", message: "" } : googleError(raw);
      out.push({ model, http: res.status, status: g.status, reason: g.reason, message: g.message, serverSide: !res.ok && serverSide(res.status, g) });
    } catch (e) {
      out.push({ model, http: 0, status: e && e.name === "TimeoutError" ? "TIMEOUT" : "NETWORK", reason: "", message: String(e).slice(0, 200) });
    }
  }
  return out;
}
function stripFence(t) {
  const s = String(t || "").trim();
  const m = /^```[A-Za-z#]*\s*([\s\S]*?)\s*```$/.exec(s);
  return m ? m[1] : s;
}
function check2(d, previous) {
  if (!d || typeof d !== "object") throw new Fail(502, "gemini", "\u05DC\u05D0 \u05D4\u05EA\u05E7\u05D1\u05DC\u05D4 \u05EA\u05E9\u05D5\u05D1\u05D4 \u05EA\u05E7\u05D9\u05E0\u05D4 \u05DE\u05E9\u05E8\u05EA \u05D4-AI. \u05E0\u05E1\u05D5 \u05E9\u05D5\u05D1.");
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
  googleError,
  licenseSignature,
  openaiParts,
  openaiSchema,
  serverSide,
  verifyLicense
};
