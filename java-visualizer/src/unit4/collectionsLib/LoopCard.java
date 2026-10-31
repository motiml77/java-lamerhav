package unit4.collectionsLib;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.TextLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.JTextComponent;

import unit4.collectionsLib.LoopGuard.Info;
import unit4.collectionsLib.LoopGuard.Verdict;
import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.ProblemView;
import unit4.collectionsLib.Model.Step;
import unit4.collectionsLib.Model.View;

/**
 * הכרטיס "הלולאה לא נגמרת" (באותה שפה כמו כרטיס הרקורסיה שלא נעצרת): מה קרה, למה — מהצעדים שנרשמו
 * (איזה משתנה בתנאי לא השתנה ומה ערכו, p שלא מתקדם, מחסנית שלא מוציאים ממנה, i שרק מתרחק מהסוף) — ומה עושים,
 * בכיוון הנכון (x &gt; 0 → x-- ; i &lt; n → i++ ; p != null → p = p.getNext(); ; !s.isEmpty() → s.pop()).
 * וגם התגית האדומה "∞ הלולאה לא נגמרת" ליד שורת התנאי בקוד. משותף ל-Java (Recorder) ול-C# (TraceViewer).
 */
final class LoopCard {
    private LoopCard() {}

    /** הצעד של העצירה (שורת התנאי של הלולאה): מוסיף לו את הכרטיס (בראש התצוגה) ומחזיר אותו (או null) */
    static ProblemView attach(List<Step> steps, Step st, String verdict) {
        if (st == null) return null;
        ProblemView p;
        try {
            p = card(steps, st, Verdict.parse(verdict));
        } catch (RuntimeException e) {
            p = new ProblemView();
            p.kind = "loop";
            p.what = "הלולאה לא נגמרת";
            p.why = "התוכנית נעצרה כי היא הסתובבה באותה לולאה בלי סוף.";
            p.hint = "בכל סיבוב צריך לשנות משהו שהתנאי של הלולאה בודק, כך שבסוף הוא לא יתקיים.";
            p.line = st.where == null ? -1 : st.where.line;
        }
        st.views.add(0, p);
        return p;
    }

    /** שורה אחת לבדיקות (מצב טקסט): [לולאה kind שורה N: מה | למה | מה עושים] */
    static String text(ProblemView p) {
        return "[לולאה " + (p.name == null ? "" : p.name + " ") + "שורה " + p.line + " " + p.what + " | " + p.why + " | " + p.hint + "]";
    }

    // ------------------------------------------------------------ הכרטיס

    private static String code(String c) {
        return Problems.code(c);
    }

    static ProblemView card(List<Step> steps, Step st, Verdict v) {
        if (v == null) {
            v = new Verdict();
            v.kind = Verdict.LONG;
            v.line = st.where == null ? -1 : st.where.line;
        }
        boolean cs = Syntax.csharp;
        Frame f = st.where;
        List<String> lines = f == null ? null : SourceFiles.lines(f);
        List<LoopViz.Loop> loops = lines == null ? new ArrayList<LoopViz.Loop>() : LoopViz.parse(lines);
        LoopViz.Loop l = LoopGuard.at(loops, v.line);
        if (l == null) l = LoopGuard.innermost(loops, v.line);
        Info info = LoopGuard.analyze(l, lines);
        ProblemView p = new ProblemView();
        p.kind = "loop";
        p.line = info != null ? info.atLine : v.line;
        int shownLine = l != null ? l.header : v.line;
        p.what = "הלולאה בשורה " + shownLine + " לא נגמרת";
        if (info == null) {
            p.why = "התוכנית נתקעה כאן — היא מסתובבת בלי סוף" + (v.ms >= 1000 ? " (כבר " + secs(v.ms) + ")" : "") + ".";
            p.hint = "בודקים שבכל סיבוב משתנה משהו שהתנאי של הלולאה בודק, כך שבסוף הוא לא יתקיים.";
            return p;
        }
        p.op = info.cond.isEmpty() ? (l.kind.equals("for") ? "for (;;)" : "true") : info.cond;

        // הביקורים בשורת התנאי של הלולאה הזו (באותה קריאה) — מהם רואים מה השתנה ומה לא
        List<Map<String, String>> hist = history(steps, st, l, info);
        Map<String, String> now = vars(st.vars);
        if (now.isEmpty() && !hist.isEmpty()) now = hist.get(hist.size() - 1);
        String main = info.cmpVar != null ? info.cmpVar : info.vars.isEmpty() ? null : info.vars.get(0);
        for (String x : info.vars) if (main == null || kindOf(now.get(x)) != null && kindOf(now.get(main)) == null && info.cmpVar == null) main = x;
        p.name = main;
        int rounds = v.rounds >= 0 ? v.rounds : hist.size() > 1 ? hist.size() - 1 : -1;
        p.index = rounds;
        String ran = rounds >= 2 ? "היא רצה כבר " + rounds + " סיבובים" : v.ms >= 1000 ? "היא רצה כבר " + secs(v.ms) : "";
        String cond = code(p.op);

        // ---------- למה
        StringBuilder why = new StringBuilder();
        if (info.semicolon && !info.constant) {   // (while (true); — שם הבעיה היא התנאי, לא ה-;)
            why.append("יש ").append(code(";")).append(" מיד אחרי ").append(code(header(l))).append(": זה הגוף של הלולאה — פקודה ריקה")
               .append(info.blockAfter ? ", והבלוק " + code("{ ... }") + " שאחריו בכלל לא שייך ללולאה" : "").append(". ");
            why.append(main != null ? "לכן " + code(main) + " לא משתנה, והתנאי " + cond + " נשאר מתקיים." : "לכן התנאי " + cond + " נשאר מתקיים.");
        } else if (v.kind.equals(Verdict.SAME)) {
            why.append("אותו מצב חוזר שוב ושוב");
            String state = stateText(info, now);
            why.append(state.isEmpty() ? " — שום דבר לא משתנה" : ": " + state).append(", ולכן התוכנית תסתובב כך לנצח.");
        } else if (info.constant && !info.exits) {
            why.append(info.cond.isEmpty() ? "ל-" + code("for (;;)") + " אין תנאי בכלל" : "התנאי " + cond + " תמיד מתקיים")
               .append(", ואין בתוך הלולאה ").append(code("break")).append(" שיוציא ממנה");
            if (!ran.isEmpty()) why.append(" (").append(ran).append(")");
            why.append(".");
        } else if (v.kind.equals(Verdict.AWAY) && info.cmpVar != null) {
            List<String> trend = trend(v, hist, info.cmpVar);
            boolean grows = grows(trend);
            if (!ran.isEmpty()) why.append(ran).append(": ");
            if ("!=".equals(info.cmpOp)) {
                why.append(code(info.cmpVar)).append(" דילג על ").append(code(info.cmpBound)).append(" (").append(String.join(", ", trend))
                   .append(") ועכשיו רק מתרחק — הוא אף פעם לא יהיה שווה בדיוק ל-").append(code(info.cmpBound)).append(".");
            } else {
                why.append(code(info.cmpVar)).append(" רק הולך ו").append(grows ? "גדל" : "קטן").append(" (").append(String.join(", ", trend))
                   .append(") — כך הוא אף פעם לא יגיע ל-").append(code(info.cmpBound)).append(", והתנאי ").append(cond).append(" תמיד מתקיים.");
            }
        } else {
            String still = stillText(info, now, hist);
            if (!ran.isEmpty()) why.append(ran).append(", ו");
            why.append("התנאי ").append(cond).append(" עדיין מתקיים");
            why.append(still.isEmpty() ? "." : ": " + still + ".");
            if (still.isEmpty() && v.quiet && v.kind.equals(Verdict.LONG)) why.append(" (התיעוד נעצר בדרך, והלולאה המשיכה לרוץ בלעדיו.)");
        }
        p.why = why.toString();

        // ---------- מה עושים
        p.hint = hint(info, l, v, now, hist, cs, main);
        return p;
    }

    private static String secs(long ms) {
        long s = Math.max(1, ms / 1000);
        return s == 1 ? "שנייה" : s + " שניות";
    }

    private static String header(LoopViz.Loop l) {
        return l.text == null ? l.kind : l.text;
    }

    /** הביקורים בשורת התנאי של הלולאה, באותה קריאה, מההתחלה של הלולאה עד הצעד הזה */
    private static List<Map<String, String>> history(List<Step> steps, Step st, LoopViz.Loop l, Info info) {
        List<Map<String, String>> res = new ArrayList<Map<String, String>>();
        if (steps == null || st.where == null) return res;
        int depth = st.calls.size();
        int i = steps.indexOf(st);
        if (i < 0) i = steps.size();
        for (int k = i - 1; k >= 0 && res.size() < 3000; k--) {
            Step s = steps.get(k);
            if (s.where == null) continue;
            if (s.calls.size() < depth) break;
            if (s.calls.size() > depth) continue;
            if (!same(s.where, st.where)) break;
            if (s.where.line < l.header || s.where.line > l.end) break;
            if (s.kind == Kind.LINE && s.where.line == info.atLine && s.vars != null) res.add(0, vars(s.vars));
        }
        return res;
    }

    private static boolean same(Frame a, Frame b) {
        return a.method.equals(b.method) && a.className.equals(b.className) && (a.file == null ? b.file == null : a.file.equals(b.file));
    }

    private static Map<String, String> vars(List<String[]> vars) {
        Map<String, String> m = new HashMap<String, String>();
        if (vars != null) for (String[] x : vars) m.put(x[0], x[1]);
        return m;
    }

    private static final Pattern STRUCT = Pattern.compile("^(Stack|Queue|Node|BinNode)\\s*\\[(.*)\\]\\s*$");

    /** "Stack" / "Queue" / "Node" / "BinNode" לפי הערך (Stack [1,2]) — או null */
    private static String kindOf(String value) {
        if (value == null) return null;
        Matcher m = STRUCT.matcher(value.trim());
        return m.matches() ? m.group(1) : null;
    }

    private static List<String> items(String value) {
        List<String> res = new ArrayList<String>();
        Matcher m = STRUCT.matcher(value.trim());
        if (!m.matches() || m.group(2).trim().isEmpty()) return res;
        for (String x : m.group(2).split(",")) res.add(x.trim());
        return res;
    }

    private static String count(int n) {
        return n == 1 ? "איבר אחד" : n + " איברים";
    }

    /** מה לא משתנה (בשביל "אותו מצב חוזר"): בכל סיבוב x = 5 / p מצביע כל הזמן על... */
    private static String stateText(Info info, Map<String, String> now) {
        List<String> parts = new ArrayList<String>();
        for (String x : info.vars) {
            String val = now.get(x);
            if (val == null) continue;
            String k = kindOf(val);
            if ("Node".equals(k) || "BinNode".equals(k)) {
                List<String> it = items(val);
                parts.add(code(x) + " מצביע כל הזמן על " + ("Node".equals(k) ? "החוליה" : "הצומת") + (it.isEmpty() ? " הזו" : " עם הערך " + it.get(0)));
            } else if ("Stack".equals(k) || "Queue".equals(k)) {
                parts.add("ב-" + code(x) + " יש כל הזמן " + count(items(val).size()));
            } else {
                parts.add("בכל סיבוב " + code(x + " = " + val));
            }
            if (parts.size() == 2) break;
        }
        return String.join(", ", parts);
    }

    /** למה התנאי עדיין מתקיים: x נשאר 5 / p לא מתקדם / לא מוציאים מ-s / i ו-n גדלים יחד */
    private static String stillText(Info info, Map<String, String> now, List<Map<String, String>> hist) {
        List<String> parts = new ArrayList<String>();
        // i ו-n משתנים יחד, והמרחק לסוף לא קטן
        if (info.cmpVar != null && hist.size() >= 3) {
            Long p0 = LoopGuard.progress(info, hist.get(0)), p1 = LoopGuard.progress(info, hist.get(hist.size() - 1));
            String a0 = hist.get(0).get(info.cmpVar), a1 = hist.get(hist.size() - 1).get(info.cmpVar);
            if (p0 != null && p0.equals(p1) && a0 != null && !a0.equals(a1) && !LoopGuard.isNumber(info.cmpBound))
                return code(info.cmpVar) + " ו-" + code(info.cmpBound) + " משתנים יחד — המרחק ביניהם לא קטן";
        }
        for (String x : info.vars) {
            String val = now.get(x);
            if (val == null) continue;
            boolean changed = false;
            for (Map<String, String> h : hist) if (h.containsKey(x) && !val.equals(h.get(x))) changed = true;
            if (changed) continue;
            String k = kindOf(val);
            boolean touched = info.assigned.contains(x) || info.escapes.contains(x);
            if ("Node".equals(k) || "BinNode".equals(k)) {
                List<String> it = items(val);
                parts.add(code(x) + " לא מתקדם — הוא מצביע כל הזמן על " + ("Node".equals(k) ? "החוליה" : "הצומת") + (it.isEmpty() ? " הזו" : " עם הערך " + it.get(0)));
            } else if ("Stack".equals(k) || "Queue".equals(k)) {
                boolean stack = "Stack".equals(k);
                parts.add("לא מוציאים מ-" + code(x) + " — יש ב" + (stack ? "ה" : "ו") + " כל הזמן " + count(items(val).size()));
            } else if (val.equals("true") || val.equals("false")) {
                parts.add(code(x) + " נשאר " + code(val) + " בכל הסיבובים" + (touched ? "" : " — אף פקודה בלולאה לא משנה אותו"));
            } else {
                parts.add(code(x) + " נשאר " + val + " בכל הסיבובים" + (touched ? "" : " — אף פקודה בלולאה לא משנה אותו"));
            }
            if (parts.size() == 2) break;
        }
        // מבנה שגדל (מכניסים אליו בלולאה)
        if (parts.isEmpty())
            for (String x : info.vars) {
                String k = kindOf(now.get(x));
                if (!("Stack".equals(k) || "Queue".equals(k)) || hist.isEmpty() || hist.get(0).get(x) == null) continue;
                int a = items(hist.get(0).get(x)).size(), b = items(now.get(x)).size();
                if (b > a) parts.add(code(x) + " רק גדל" + ("Stack".equals(k) ? "ה" : "") + " (" + a + " → " + b + " איברים) — " + ("Stack".equals(k) ? "היא אף פעם לא תתרוקן" : "הוא אף פעם לא יתרוקן"));
            }
        return String.join("; ", parts);
    }

    /** הערכים של המשתנה שבהשוואה: מהצעדים (הראשונים והאחרון) — או מההחלטה */
    private static List<String> trend(Verdict v, List<Map<String, String>> hist, String var) {
        List<String> vals = new ArrayList<String>();
        for (Map<String, String> h : hist) {
            String x = h.get(var);
            if (x != null && (vals.isEmpty() || !vals.get(vals.size() - 1).equals(x))) vals.add(x);
        }
        if (vals.size() < 3 && v.trend.size() >= vals.size()) return new ArrayList<String>(v.trend);
        if (vals.size() <= 6) return vals;
        List<String> res = new ArrayList<String>(vals.subList(0, 3));
        res.add("…");
        res.add(vals.get(vals.size() - 1));
        return res;
    }

    private static boolean grows(List<String> trend) {
        Long a = null, b = null;
        for (String t : trend) {
            Long n = LoopGuard.num(t);
            if (n == null) continue;
            if (a == null) a = n;
            b = n;
        }
        return a != null && b != null && b > a;
    }

    // ------------------------------------------------------------ מה עושים

    private static String hint(Info info, LoopViz.Loop l, Verdict v, Map<String, String> now, List<Map<String, String>> hist, boolean cs, String main) {
        if (info.semicolon && !info.constant)
            return "מוחקים את ה-" + code(";") + " שבסוף שורה " + l.header + (info.blockAfter ? " — ואז הבלוק שאחריו יהיה הגוף של הלולאה." : ".");
        if (info.constant)
            return "כותבים בתוך " + code(l.kind.equals("for") ? "for (...; ...; ...)" : "while (...)") + " את התנאי האמיתי — או מוסיפים בתוך הלולאה "
                    + code("if (...) break;") + " כשצריך לצאת.";
        String k = main == null ? null : kindOf(now.get(main));
        String body = info.body + " " + info.update;
        // רשימה מקושרת / עץ
        if (main != null && ("Node".equals(k) || "BinNode".equals(k) || info.cond.matches(".*\\b" + Pattern.quote(main) + "\\s*\\.\\s*(getNext|GetNext|hasNext|HasNext)\\b.*")
                || (now.get(main) != null && now.get(main).equals("null") && info.cond.contains("null")))) {
            if ("BinNode".equals(k))
                return "בסוף כל סיבוב יורדים לילד:  " + code(main + " = " + main + "." + m("getLeft", cs) + "();") + "  (או " + code(m("getRight", cs) + "()") + ")";
            return "בסוף כל סיבוב מתקדמים לחוליה הבאה:  " + code(main + " = " + main + "." + m("getNext", cs) + "();");
        }
        // מחסנית / תור
        if (main != null && ("Stack".equals(k) || "Queue".equals(k))) {
            boolean stack = "Stack".equals(k);
            String take = m(stack ? "pop" : "remove", cs), peek = m(stack ? "top" : "head", cs), put = m(stack ? "push" : "insert", cs);
            if (Pattern.compile("(?<![.\\w])" + Pattern.quote(main) + "\\s*\\.\\s*" + put + "\\s*\\(").matcher(body).find())
                return "בתוך הלולאה מכניסים בחזרה ל-" + code(main) + " (" + code(put + "()") + ") — כך " + (stack ? "היא אף פעם לא תתרוקן" : "הוא אף פעם לא יתרוקן")
                        + ". מכניסים ל" + (stack ? "מחסנית" : "תור") + " עזר אחר" + (stack ? "ת" : "") + ", או סופרים סיבובים לפי מספר האיברים.";
            if (Pattern.compile("(?<![.\\w])" + Pattern.quote(main) + "\\s*\\.\\s*" + peek + "\\s*\\(").matcher(body).find())
                return code(main + "." + peek + "()") + " רק מציץ ב" + (stack ? "ראש המחסנית" : "ראש התור") + " בלי להוציא — מוציאים באמת:  " + code(main + "." + take + "()");
            return "בכל סיבוב מוציאים איבר:  " + code(main + "." + take + "();");
        }
        // מונה
        if (info.cmpVar != null) {
            String x = info.cmpVar;
            Boolean grow = info.mustGrow();
            if (grow == null) {   // !=
                Long cur = LoopGuard.num(now.get(x)), b = LoopGuard.bound(info.cmpBound, now);
                if (v.kind.equals(Verdict.AWAY) && cur != null && b != null)
                    return "כותבים " + code(x + (cur > b ? " > " : " < ") + info.cmpBound) + " במקום " + code(info.cond) + " — כך הלולאה נעצרת גם כשמדלגים על " + code(info.cmpBound) + ".";
                if (cur != null && b != null) grow = cur < b;
            }
            if (grow != null) {
                String want = x + (grow ? "++" : "--");
                // שינוי בכיוון ההפוך: ב-for (החלק השלישי) או בגוף
                String wrong = wrongStep(info.update, x, grow);
                if (wrong != null) return "בחלק השלישי של ה-for כתוב " + code(wrong) + " — " + (grow ? "הוא מקטין" : "הוא מגדיל") + " את " + code(x) + ". צריך " + code(want) + ".";
                wrong = wrongStep(info.body, x, grow);
                if (wrong != null) return "בתוך הלולאה " + code(wrong) + " " + (grow ? "מקטין" : "מגדיל") + " את " + code(x) + " ומרחיק אותו מהסוף. צריך " + code(want) + ".";
                if (info.assigned.contains(info.cmpBound == null ? "" : info.cmpBound.trim()) && !LoopGuard.isNumber(info.cmpBound))
                    return "לא משנים את " + code(info.cmpBound) + " בתוך הלולאה — או שומרים את הערך שלו במשתנה אחר לפני הלולאה.";
                return "בסוף כל סיבוב " + (grow ? "מגדילים" : "מקטינים") + " את " + code(x) + " — כך הוא יתקרב ל-" + code(info.cmpBound) + " והלולאה תיגמר:  " + code(want + ";");
            }
        }
        // דגל (while (!found))
        if (main != null && now.get(main) != null && (now.get(main).equals("true") || now.get(main).equals("false"))) {
            boolean val = now.get(main).equals("true");
            return "בתוך הלולאה משנים את " + code(main) + " כשמגיעים לסוף:  " + code(main + " = " + !val + ";");
        }
        return "בכל סיבוב צריך לשנות משהו שהתנאי " + code(info.cond.isEmpty() ? "true" : info.cond) + " בודק — כך שבסוף הוא לא יתקיים.";
    }

    /** שם הפעולה בשפה של התלמיד: getNext / GetNext */
    private static String m(String java, boolean cs) {
        return cs ? Character.toUpperCase(java.charAt(0)) + java.substring(1) : java;
    }

    /** ‎x++‎ / ‎x += 2‎ / ‎x = x - 1‎ בכיוון ההפוך ממה שצריך — הטקסט שלו, או null */
    private static String wrongStep(String code, String x, boolean grow) {
        if (code == null || code.isEmpty()) return null;
        String q = Pattern.quote(x);
        Matcher m = Pattern.compile("(?<![.\\w$])(" + q + "\\s*(\\+\\+|--)|(\\+\\+|--)\\s*" + q + "(?![\\w$])|" + q + "\\s*([-+])=\\s*(\\d+)|" + q
                + "\\s*=\\s*" + q + "\\s*([-+])\\s*(\\d+))").matcher(code);
        while (m.find()) {
            String op = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4) != null ? m.group(4) : m.group(6);
            boolean up = op.startsWith("+");
            if (up != grow) return m.group(1).replaceAll("\\s+", " ").trim();
        }
        return null;
    }

    // ------------------------------------------------------------ התגית בקוד

    /** הטקסט של צעד העצירה (Recorder ב-Java, Recorder.cs ב-C#) */
    static final String STOP_TEXT = "הלולאה לא נגמרת — התוכנית נעצרה";

    /** הצעד שבו התוכנית נעצרה בגלל לולאה שלא נגמרת */
    static boolean isStop(Step s) {
        return s != null && s.kind == Kind.ERROR && s.text != null && s.text.startsWith(STOP_TEXT);
    }

    /** השורה שצריך לסמן בה "∞ הלולאה לא נגמרת" בצעד הזה (או -1) */
    static int tagLine(Step s) {
        if (s == null) return -1;
        for (View v : s.views) if (v instanceof ProblemView && "loop".equals(((ProblemView) v).kind)) return ((ProblemView) v).line;
        return -1;
    }

    static final Color RED = new Color(0xDC2626);

    /** תגית אדומה מימין לשורת התנאי (באותו סגנון כמו התגיות של התנאים — CondPaint) */
    static void paintTag(Graphics2D g, JTextComponent c, int line, Map<Integer, Integer> used) {
        Rectangle r = CondPaint.lineRect(c, line);
        if (r == null) return;
        Font f = Theme.smallBold != null ? Theme.smallBold : Theme.small;
        if (f == null) f = new Font(Font.DIALOG, Font.BOLD, 13);
        String text = "הלולאה לא נגמרת";
        TextLayout tl = CondPaint.rtl(text, f, g.getFontRenderContext());
        int h = Math.min(r.height - 2, 20), y = r.y + (r.height - h) / 2;
        int sym = h + 6;   // הסימן ∞ — מצויר (לא תלוי בגופן)
        int w = (int) Math.ceil(tl.getAdvance()) + sym + 18;
        Integer after = used.get(line);
        int x = after != null ? after + 8 : CondPaint.lineEndX(c, line) + 22;
        Rectangle vis = c.getVisibleRect();
        if (x + w > vis.x + vis.width - 10) x = Math.max(vis.x + 40, vis.x + vis.width - 10 - w);
        used.put(line, x + w);
        g.setColor(new Color(255, 255, 255, 240));
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(RED);
        g.fillRoundRect(x, y, w, h, h, h);
        // ∞ מימין (קריאה מימין לשמאל), הטקסט משמאלו — לבן על אדום
        int sx = x + w - 8 - sym, cy = y + h / 2;
        infinity(g, sx, cy, sym, Color.WHITE);
        g.setColor(Color.WHITE);
        tl.draw(g, sx - 6 - tl.getAdvance(), y + (h + tl.getAscent() - tl.getDescent()) / 2f);
    }

    /** הסימן ∞: שתי לולאות, ברוחב w ובמרכז הגובה cy */
    static void infinity(Graphics2D g, int x, int cy, int w, Color col) {
        Graphics2D d = (Graphics2D) g.create();
        d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        d.setColor(col);
        d.setStroke(new java.awt.BasicStroke(Math.max(1.6f, w / 9f), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        float r = w / 4f, hh = w / 3.2f;
        java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
        float mx = x + w / 2f;
        p.moveTo(mx, cy);
        p.curveTo(mx + r * 0.6f, cy - hh, x + w, cy - hh, x + w, cy);
        p.curveTo(x + w, cy + hh, mx + r * 0.6f, cy + hh, mx, cy);
        p.curveTo(mx - r * 0.6f, cy - hh, x, cy - hh, x, cy);
        p.curveTo(x, cy + hh, mx - r * 0.6f, cy + hh, mx, cy);
        d.draw(p);
        d.dispose();
    }
}
