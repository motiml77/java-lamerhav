package unit4.collectionsLib;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** צביעת קוד Java: מילים שמורות, טיפוסים, פעולות, משתנים, מחרוזות, מספרים והערות */
final class Syntax {
    private Syntax() {}

    static final int PLAIN = 0, KEYWORD = 1, CONTROL = 2, TYPE = 3, METHOD = 4, VARIABLE = 5, STRING = 6, NUMBER = 7, COMMENT = 8;

    private static final Color[] COLORS = {
        new Color(0x1D2433),   // רגיל
        new Color(0x0033B3),   // מילה שמורה (public, static, int, null...)
        new Color(0x9C27B0),   // בקרה (if, while, return, new...)
        new Color(0x0E7490),   // טיפוס (Stack, String, Node...)
        new Color(0x8A5A00),   // פעולה (push(), getValue()...)
        new Color(0x1F4E9A),   // משתנה
        new Color(0xB4281E),   // מחרוזת / תו
        new Color(0x15803D),   // מספר
        new Color(0x6B7385)    // הערה
    };

    static Color color(int kind) {
        return COLORS[kind];
    }

    /** true = צובעים קוד C# (מילים שמורות של C# במקום של Java) — מופעל ע"י TraceViewer */
    static boolean csharp;

    private static final Set<String> CS_CONTROL = new HashSet<String>(Arrays.asList(
            "if", "else", "while", "for", "foreach", "do", "switch", "case", "default", "break", "continue", "return",
            "new", "try", "catch", "finally", "throw", "yield", "goto", "lock", "using"));

    private static final Set<String> CS_KEYWORDS = new HashSet<String>(Arrays.asList(
            "public", "private", "protected", "internal", "static", "readonly", "const", "void", "class", "struct", "interface",
            "namespace", "int", "double", "bool", "char", "long", "float", "short", "byte", "string", "decimal", "object",
            "var", "this", "base", "null", "true", "false", "is", "as", "in", "out", "ref", "params", "override", "virtual",
            "abstract", "sealed", "enum", "typeof", "sizeof", "get", "set", "async", "await"));

    private static final Set<String> CONTROL_WORDS = new HashSet<String>(Arrays.asList(
            "if", "else", "while", "for", "do", "switch", "case", "default", "break", "continue", "return",
            "new", "try", "catch", "finally", "throw", "throws"));

    private static final Set<String> KEYWORDS = new HashSet<String>(Arrays.asList(
            "public", "private", "protected", "static", "final", "void", "class", "interface", "extends", "implements",
            "import", "package", "int", "double", "boolean", "char", "long", "float", "short", "byte", "this", "super",
            "null", "true", "false", "instanceof", "abstract", "enum", "var"));

    /** תו בתוך שם — בלי סימני הכיוון הנסתרים (LRM / RLE...) שמוסיפים לתצוגה */
    private static boolean part(char ch) {
        return Character.isJavaIdentifierPart(ch) && !(ch >= '\u200E' && ch <= '\u200F') && !(ch >= '\u202A' && ch <= '\u202E');
    }

    /** כל ההבלטות בטקסט: { התחלה, סוף, סוג } (מה שלא מופיע — רגיל) */
    static List<int[]> tokens(String s) {
        List<int[]> out = new ArrayList<int[]>();
        int n = s.length(), i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {            // הערת שורה
                int e = s.indexOf('\n', i);
                if (e < 0) e = n;
                out.add(new int[] { i, e, COMMENT });
                i = e;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {     // הערת בלוק
                int e = s.indexOf("*/", i + 2);
                e = e < 0 ? n : e + 2;
                out.add(new int[] { i, e, COMMENT });
                i = e;
            } else if (c == '"' || c == '\'') {                               // מחרוזת / תו
                int e = i + 1;
                while (e < n && s.charAt(e) != c && s.charAt(e) != '\n') {
                    if (s.charAt(e) == '\\') e++;
                    e++;
                }
                e = Math.min(n, e + 1);
                out.add(new int[] { i, e, STRING });
                i = e;
            } else if (Character.isDigit(c)) {                                // מספר
                int e = i + 1;
                while (e < n && (Character.isLetterOrDigit(s.charAt(e)) || s.charAt(e) == '.')) e++;
                out.add(new int[] { i, e, NUMBER });
                i = e;
            } else if (Character.isJavaIdentifierStart(c)) {                  // מילה
                int e = i + 1;
                while (e < n && part(s.charAt(e))) e++;
                String w = s.substring(i, e);
                int k = e;
                while (k < n && s.charAt(k) == ' ') k++;
                boolean call = k < n && s.charAt(k) == '(';
                int kind;
                if ((csharp ? CS_CONTROL : CONTROL_WORDS).contains(w)) kind = CONTROL;
                else if ((csharp ? CS_KEYWORDS : KEYWORDS).contains(w)) kind = KEYWORD;
                else if (call) kind = METHOD;
                else if (Character.isUpperCase(w.charAt(0)) && !w.equals(w.toUpperCase())) kind = TYPE;
                else kind = VARIABLE;
                out.add(new int[] { i, e, kind });
                i = e;
            } else {
                i++;
            }
        }
        return out;
    }
}
