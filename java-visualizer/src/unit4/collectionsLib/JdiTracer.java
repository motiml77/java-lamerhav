package unit4.collectionsLib;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.CharValue;
import com.sun.jdi.ClassType;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.StringReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.ListeningConnector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;

/**
 * "דיבאגר" קטן: מפעיל את תוכנית התלמיד בתהליך נפרד, עוצר לפני כל שורה בקוד של התלמיד,
 * קורא את המשתנים המקומיים, ומעביר אותם ל-Recorder שבתוך התהליך (שמצלם גם את המבנים).
 * החלון עצמו נפתח בתהליך של התוכנית, כמו בהרצה רגילה.
 * (מקומפל ל-Java 11 — נטען רק כשמבקשים הרצה שורה-שורה)
 */
final class JdiTracer {
    private JdiTracer() {}

    private static final String[] EXCLUDE = { "java.*", "javax.*", "jdk.*", "sun.*", "com.sun.*",
            "unit4.collectionsLib.*", "org.junit.*", "kotlin.*" };

    private VirtualMachine vm;
    private ThreadReference mainThread;
    private StepRequest step;
    private MethodExitRequest exits;
    private ClassType recorder;
    private com.sun.jdi.Field fullField;
    private ArrayReference buffer, objBuffer;
    /** אובייקטים של מחלקות התלמיד שכבר הונחו ב-Recorder.lineObjs: האובייקט → המקום */
    private final Map<ObjectReference, Integer> objSlots = new java.util.HashMap<>();
    private int written, capacity, lines;
    private final Map<ObjectReference, String> immutable = new java.util.HashMap<>();
    private boolean tracing = true;

    static int run(String mainClass, String[] args) throws Exception {
        return new JdiTracer().go(mainClass, args);
    }

    private int go(String mainClass, String[] args) throws Exception {
        ListeningConnector lc = null;
        for (ListeningConnector c : Bootstrap.virtualMachineManager().listeningConnectors())
            if (c.name().equals("com.sun.jdi.SocketListen")) lc = c;
        if (lc == null) throw new IllegalStateException("SocketListen connector not found");
        Map<String, Connector.Argument> ca = lc.defaultArguments();
        if (ca.containsKey("localAddress")) ca.get("localAddress").setValue("127.0.0.1");
        ca.get("port").setValue("0");
        if (ca.containsKey("timeout")) ca.get("timeout").setValue("30000");
        String address = lc.startListening(ca);

        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.add("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=" + address);
        cmd.add("-D" + LineByLine.CHILD_PROPERTY + "=true");
        if (LineByLine.splashReady != null) cmd.add("-D" + Splash.READY_PROPERTY + "=" + LineByLine.splashReady.getPath());
        // עלייה מהירה יותר של התהליך (הקוד של התלמיד קצר — אין צורך בקומפילציה מלאה של ה-JIT)
        cmd.add("-XX:TieredStopAtLevel=1");
        cmd.add("-XX:+UseSerialGC");
        cmd.add("-Xshare:auto");
        for (String p : new String[] { "file.encoding", "stdout.encoding", "stderr.encoding", "java.awt.headless", "dsviz.timing", "dsviz.fonts", "dsviz.debug", "dsviz.objview", "dsviz.licenseUrl", "dsviz.license", "user.home" }) {
            String v = System.getProperty(p);
            if (v != null) cmd.add("-D" + p + "=" + v);
        }
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(LineByLine.class.getName());
        cmd.add(mainClass);
        for (String a : args) cmd.add(a);

        Process proc = new ProcessBuilder(cmd).start();
        pump(proc.getInputStream(), System.out, true);
        pump(proc.getErrorStream(), System.err, true);
        pump(System.in, proc.getOutputStream(), false);

        Timing.mark("ממתין לילד");
        vm = lc.accept(ca);
        Timing.mark("הילד התחבר");
        lc.stopListening(ca);

        EventRequestManager erm = vm.eventRequestManager();
        // סוף main של התלמיד: מפסיקים לעקוב (אחרת הדיבאגר "צועד" גם בתוך הספרייה — איטי מאוד)
        ClassPrepareRequest done = erm.createClassPrepareRequest();
        done.addClassFilter(LineByLine.class.getName());
        done.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        done.enable();
        ClassPrepareRequest cpr = erm.createClassPrepareRequest();
        cpr.addClassFilter(mainClass);
        cpr.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        cpr.enable();

        loop:
        while (true) {
            EventSet set;
            try {
                set = vm.eventQueue().remove();
            } catch (InterruptedException e) {
                break;
            }
            for (Event e : set) {
                if (e instanceof VMDeathEvent || e instanceof VMDisconnectEvent) break loop;
                try {
                    if (e instanceof VMStartEvent) {
                        // ממשיכים עד שמחלקת התלמיד נטענת
                    } else if (e instanceof ClassPrepareEvent) {
                        ReferenceType t = ((ClassPrepareEvent) e).referenceType();
                        for (Method m : t.methodsByName("mainDone")) {
                            BreakpointRequest bp = erm.createBreakpointRequest(m.location());
                            bp.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                            bp.putProperty("done", Boolean.TRUE);
                            bp.enable();
                        }
                        if (!t.name().equals(mainClass)) continue;
                        for (Method m : t.methodsByName("main", "([Ljava/lang/String;)V")) {
                            BreakpointRequest bp = erm.createBreakpointRequest(m.location());
                            bp.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                            bp.enable();
                        }
                    } else if (e instanceof BreakpointEvent && e.request().getProperty("done") != null) {
                        stopTracing();
                    } else if (e instanceof BreakpointEvent) {
                        erm.deleteEventRequest(e.request());
                        mainThread = ((BreakpointEvent) e).thread();
                        onLine(((BreakpointEvent) e).location());
                    } else if (e instanceof StepEvent) {
                        onLine(((StepEvent) e).location());
                    } else if (e instanceof MethodExitEvent) {
                        onExit((MethodExitEvent) e);
                    }
                } catch (Exception ex) {
                    System.err.println("[שורה-שורה] " + ex);
                    if (Boolean.getBoolean("dsviz.debug")) ex.printStackTrace();
                    stopTracing();
                }
            }
            set.resume();
        }
        try {
            return proc.waitFor();
        } catch (InterruptedException e) {
            proc.destroy();
            return 1;
        }
    }

    /**
     * התוכנית עצורה לפני שורה בקוד של התלמיד. כותבים את פרטי השורה כמחרוזת אחת למקום הבא ב-Recorder.lineTexts
     * שבתהליך התוכנית — בלי להריץ שם קוד (כל פנייה לתהליך יקרה; הרצת קוד שם יקרה במיוחד).
     */
    private void onLine(Location loc) throws Exception {
        if (!tracing) return;
        if (recorder == null) {
            List<ReferenceType> r = vm.classesByName("unit4.collectionsLib.Recorder");
            if (r.isEmpty()) return;
            recorder = (ClassType) r.get(0);
            fullField = recorder.fieldByName("linesFull");
            buffer = (ArrayReference) recorder.getValue(recorder.fieldByName("lineTexts"));
            com.sun.jdi.Field lo = recorder.fieldByName("lineObjs");
            Value ob = lo == null ? null : recorder.getValue(lo);
            objBuffer = ob instanceof ArrayReference ? (ArrayReference) ob : null;
            if (buffer == null) { stopTracing(); return; }
            capacity = buffer.length();
        }
        if (++lines % 50 == 0 && ((BooleanValue) recorder.getValue(fullField)).value()) {
            stopTracing();
            return;
        }

        StringBuilder meta = new StringBuilder();
        List<StackFrame> frames = mainThread.frames();
        if (insideRecorder(frames)) return;   // קוד של התלמיד (למשל toString) שהציור קרא לו — לא צעד של התוכנית
        appendFrames(meta, frames);
        meta.append('\u0004');
        // המשתנים המקומיים של המסגרת העליונה
        StackFrame f = frames.get(0);
        Map<ArrayReference, List<String>> arrays = new java.util.LinkedHashMap<>();
        try {
            List<LocalVariable> locals = f.visibleVariables();
            Map<LocalVariable, Value> vals = locals.isEmpty() ? null : f.getValues(locals);
            if (!f.location().method().isStatic()) addVar(meta, "this", f.thisObject());
            for (LocalVariable lv : locals) {
                if (lv.name().equals("args") && lv.typeName().equals("java.lang.String[]")) continue;
                Value v = vals.get(lv);
                addVar(meta, lv.name(), v);
                if (v instanceof ArrayReference) arrays.computeIfAbsent((ArrayReference) v, k -> new ArrayList<>()).add(lv.name());
            }
        } catch (AbsentInformationException noDebugInfo) {
            // קומפילציה בלי מידע על משתנים (javac בלי -g) — מציגים רק את השורה
        }
        // מערכים גם מהפעולות שקראו לפעולה הנוכחית (למשל המערך של main בתוך פעולת עזר)
        int depth = 0;
        for (int i = 1; i < frames.size() && depth < 4; i++) {
            StackFrame fr = frames.get(i);
            if (excluded(fr.location().declaringType().name())) continue;
            depth++;
            try {
                List<LocalVariable> locals = fr.visibleVariables();
                List<LocalVariable> arr = new ArrayList<>();
                for (LocalVariable lv : locals) if (lv.typeName().endsWith("[]") && !lv.name().equals("args")) arr.add(lv);
                if (arr.isEmpty()) continue;
                Map<LocalVariable, Value> vals = fr.getValues(arr);
                for (LocalVariable lv : arr) {
                    Value v = vals.get(lv);
                    if (v instanceof ArrayReference) {
                        List<String> names = arrays.computeIfAbsent((ArrayReference) v, k -> new ArrayList<>());
                        String n = lv.name() + " (" + fr.location().method().name() + ")";
                        if (!names.contains(n) && !names.contains(lv.name())) names.add(n);   // ברקורסיה: אותו שם בכל קריאה — פעם אחת
                    }
                }
            } catch (AbsentInformationException ignored) {
            }
        }
        meta.append('\u0004');
        for (Map.Entry<ArrayReference, List<String>> e : arrays.entrySet()) {
            meta.append(clean(String.join(", ", e.getValue()))).append('\u0002');
            encodeArray(meta, e.getKey());
            meta.append('\u0001');
        }
        if (!write(meta.toString())) return;

        if (step == null) {
            step = vm.eventRequestManager().createStepRequest(mainThread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
            for (String x : EXCLUDE) step.addClassExclusionFilter(x);
            step.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            step.enable();
            // יציאה מכל פעולה של התלמיד — בשביל "מה הפעולה החזירה" (מעקב רקורסיה)
            if (vm.canGetMethodReturnValues()) {
                exits = vm.eventRequestManager().createMethodExitRequest();
                for (String x : EXCLUDE) exits.addClassExclusionFilter(x);
                exits.addThreadFilter(mainThread);
                exits.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                exits.enable();
            }
        }
    }

    /**
     * האם הקוד רץ מתוך הציור של הספרייה (Recorder) — למשל toString של מחלקה של התלמיד שנקרא כדי לכתוב את הכרטיס.
     * צעדים כאלה לא שייכים לתוכנית (ותיעוד שלהם גורם לציור נוסף — ולולאה בלי סוף).
     */
    private static boolean insideRecorder(List<StackFrame> frames) {
        for (StackFrame fr : frames) {
            String n = fr.location().declaringType().name();
            if (n.startsWith("unit4.collectionsLib.Recorder") || n.startsWith("unit4.collectionsLib.StringViz")) return true;
        }
        return false;
    }

    /** מחסנית הקריאות (רק הקוד של התלמיד): מחלקה\u0002פעולה\u0002קובץ\u0002שורה, מופרדות ב-\u0001 */
    private static void appendFrames(StringBuilder meta, List<StackFrame> frames) {
        for (StackFrame fr : frames) {
            Location l = fr.location();
            String cls = l.declaringType().name();
            if (excluded(cls)) continue;
            String file;
            try { file = l.sourceName(); } catch (AbsentInformationException e) { file = ""; }
            meta.append(cls).append('\u0002').append(l.method().name()).append('\u0002').append(file)
                .append('\u0002').append(l.lineNumber()).append('\u0001');
        }
    }

    /**
     * פעולה של התלמיד מסתיימת: \u0005 מסגרות \u0004 הערך שהוחזר (או \u0006 אם void).
     * המסגרות עדיין כוללות את הפעולה שמסתיימת (למעלה).
     */
    private void onExit(MethodExitEvent e) throws Exception {
        if (!tracing || recorder == null) return;
        Method m = e.method();
        if (m.isSynthetic() || m.isStaticInitializer() || m.isConstructor() || m.name().startsWith("lambda$")) return;
        if (m.name().equals("main") && m.isStatic()) return;   // סוף התוכנית — יש לזה צעד משלו
        StringBuilder meta = new StringBuilder("\u0005");
        List<StackFrame> frames = mainThread.frames();
        if (insideRecorder(frames)) return;
        appendFrames(meta, frames);
        meta.append('\u0004');
        if (m.returnTypeName().equals("void")) meta.append('\u0006');
        else meta.append(clean(format(e.returnValue(), 0)));
        write(meta.toString());
    }

    /** כותב מחרוזת למקום הבא ב-Recorder.lineTexts שבתהליך התוכנית. false = נגמר המקום */
    private boolean write(String meta) throws Exception {
        if (written >= capacity) {
            stopTracing();
            return false;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                buffer.setValue(written, vm.mirrorOf(meta));
                break;
            } catch (com.sun.jdi.ObjectCollectedException gcRace) {
                // ה-GC אסף את המחרוזת החדשה לפני ששמרנו אותה — מנסים שוב
            }
        }
        written++;
        return true;
    }

    private static final int MAX_ARRAY = 60, MAX_MATRIX = 20;

    /** ממדים \u0002 נתונים \u0002 ("+" אם קוצר). דו-ממדי: שורות מופרדות ב-\u0007, איברים ב-\u0006 */
    private void encodeArray(StringBuilder meta, ArrayReference a) {
        int n = a.length();
        if (a.referenceType().name().endsWith("[][]")) {
            meta.append("2\u0002");
            List<Value> rows = n == 0 ? new ArrayList<>() : a.getValues(0, Math.min(n, MAX_MATRIX));
            for (int r = 0; r < rows.size(); r++) {
                if (r > 0) meta.append('\u0007');
                if (!(rows.get(r) instanceof ArrayReference)) continue;
                ArrayReference row = (ArrayReference) rows.get(r);
                int m = row.length();
                List<Value> cells = m == 0 ? new ArrayList<>() : row.getValues(0, Math.min(m, MAX_MATRIX));
                for (int c = 0; c < cells.size(); c++) meta.append(c == 0 ? "" : "\u0006").append(clean(cell(cells.get(c))));
            }
            meta.append(n > MAX_MATRIX ? "\u0002+" : "\u0002-");
        } else {
            meta.append("1\u0002");
            List<Value> cells = n == 0 ? new ArrayList<>() : a.getValues(0, Math.min(n, MAX_ARRAY));
            for (int i = 0; i < cells.size(); i++) meta.append(i == 0 ? "" : "\u0006").append(clean(cell(cells.get(i))));
            meta.append(n > MAX_ARRAY ? "\u0002+" : "\u0002-");
        }
    }

    /** ערך של תא במערך — קצר, בלי מרכאות */
    private String cell(Value v) {
        if (v instanceof CharValue) return String.valueOf(((CharValue) v).value());
        if (v instanceof StringReference) return shorten(((StringReference) v).value(), 14);
        if (v instanceof ObjectReference && (isStudent((ObjectReference) v) || isCar((ObjectReference) v))) {
            int id = vizIdOf((ObjectReference) v);
            if (id != 0) return "\u0003" + id;   // ה-Recorder בתהליך התוכנית מכיר את האובייקט ובונה ממנו כרטיס
            return isStudent((ObjectReference) v) ? studentLabel((ObjectReference) v, true) : carLabel((ObjectReference) v);
        }
        if (v instanceof ObjectReference && isUserClass((ObjectReference) v)) {
            String ref = userRef((ObjectReference) v);
            return ref != null ? ref : dumpObject((ObjectReference) v);
        }
        String s = format(v, 2);
        return s.startsWith("\u0003") ? s : shorten(s, 14);   // מבנה של הספרייה: הפניה ל-vizId (Recorder מצייר אותו בתוך התא)
    }

    /** מזהה האובייקט (vizId) ש-Recorder.register נתן ל-Student / Car; 0 אם לא נרשם */
    private int vizIdOf(ObjectReference o) {
        com.sun.jdi.Field f = ((ClassType) o.referenceType()).fieldByName("vizId");
        return f == null ? 0 : ((com.sun.jdi.IntegerValue) o.getValue(f)).value();
    }

    /** מחלקה של התלמיד (לא של Java ולא של הספרייה) */
    private static boolean isUserClass(ObjectReference o) {
        String n = o.referenceType().name();
        return !(o instanceof StringReference) && !(o instanceof ArrayReference) && o.referenceType() instanceof ClassType
                && !n.startsWith("java.") && !n.startsWith("javax.") && !n.startsWith("jdk.") && !n.startsWith("sun.")
                && !n.startsWith("com.sun.") && !n.startsWith("unit4.collectionsLib.") && !((ClassType) o.referenceType()).isEnum();
    }

    /** תיאור אובייקט של מחלקה כללית: \u000E סוג \u000F מאפיין \u0010 ערך ... (ה-Recorder בונה ממנו כרטיס) */
    private String dumpObject(ObjectReference o) {
        ReferenceType t = o.referenceType();
        StringBuilder b = new StringBuilder("\u000E").append(dumpClean(t.name().substring(t.name().lastIndexOf('.') + 1)));
        int n = 0;
        for (com.sun.jdi.Field f : t.allFields()) {
            if (f.isStatic() || f.isSynthetic()) continue;
            if (n++ >= 12) break;
            String val = format(o.getValue(f), 1);
            if (val.startsWith("\u0003")) val = "•";
            b.append('\u000F').append(dumpClean(f.name())).append('\u0010').append(dumpClean(shorten(val, 24)));
        }
        return b.toString();
    }

    private static String dumpClean(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) b.append(c < 32 ? ' ' : c);
        return b.toString();
    }

    private static boolean isCar(ObjectReference o) {
        return o.referenceType().name().equals("unit4.collectionsLib.Car");
    }

    /** מכונית: "Mazda 2019" */
    private String carLabel(ObjectReference o) {
        ClassType t = (ClassType) o.referenceType();
        com.sun.jdi.Field fm = t.fieldByName("manufacturer"), fy = t.fieldByName("year");
        Map<com.sun.jdi.Field, Value> m = o.getValues(java.util.Arrays.asList(fm, fy));
        String maker = m.get(fm) instanceof StringReference ? ((StringReference) m.get(fm)).value() : "?";
        return maker + " " + m.get(fy);
    }

    private static boolean isStudent(ObjectReference o) {
        return o.referenceType().name().equals("unit4.collectionsLib.Student");
    }

    /** תלמיד: "נועה 84" (קצר) או "נועה לוי (84)" */
    private String studentLabel(ObjectReference o, boolean shortForm) {
        ClassType t = (ClassType) o.referenceType();
        com.sun.jdi.Field fn = t.fieldByName("name"), fg = t.fieldByName("grades");
        Map<com.sun.jdi.Field, Value> m = o.getValues(java.util.Arrays.asList(fn, fg));
        String name = m.get(fn) instanceof StringReference ? ((StringReference) m.get(fn)).value() : "?";
        long avg = 0;
        if (m.get(fg) instanceof ArrayReference) {
            ArrayReference g = (ArrayReference) m.get(fg);
            List<Value> gs = g.length() == 0 ? new ArrayList<>() : g.getValues();
            long sum = 0;
            for (Value x : gs) sum += ((com.sun.jdi.IntegerValue) x).value();
            avg = gs.isEmpty() ? 0 : Math.round((double) sum / gs.size());
        }
        return shortForm ? name.trim().split("\\s+")[0] + " " + avg : name + " (" + avg + ")";
    }

    private static String clean(String s) {
        return s.replace('\u0001', ' ').replace('\u0002', ' ').replace('\u0004', ' ').replace('\u0006', ' ').replace('\u0007', ' ');
    }

    private static boolean excluded(String cls) {
        for (String x : EXCLUDE) if (cls.startsWith(x.substring(0, x.length() - 1))) return true;
        return false;
    }

    /**
     * אובייקט של מחלקה של התלמיד: מניחים אותו ב-Recorder.lineObjs (כתיבה לתא במערך — בלי להריץ קוד בתהליך)
     * ומחזירים \u0003u + המקום; null אם אין מקום.
     */
    private String userRef(ObjectReference o) {
        if (objBuffer == null) return null;
        Integer slot = objSlots.get(o);
        if (slot == null) {
            if (objSlots.size() >= objBuffer.length()) return null;
            slot = objSlots.size();
            try {
                objBuffer.setValue(slot, o);
            } catch (Exception e) {
                return null;
            }
            objSlots.put(o, slot);
        }
        return "\u0003u" + slot;
    }

    private void addVar(StringBuilder meta, String name, Value v) {
        String ref = v instanceof ObjectReference && isUserClass((ObjectReference) v) ? userRef((ObjectReference) v) : null;
        String text = ref != null ? ref : format(v, 0);
        meta.append(name).append('\u0002').append(clean(text)).append('\u0001');
    }

    /** מבנה של הספרייה → \u0003 + vizId (ה-Recorder יודע לאיזה אובייקט זה שייך). השאר → טקסט */
    private String format(Value v, int depth) {
        if (v == null) return "null";
        if (v instanceof CharValue) return "'" + ((CharValue) v).value() + "'";
        if (v instanceof PrimitiveValue) return v.toString();
        if (v instanceof ArrayReference) {
            ArrayReference a = (ArrayReference) v;
            if (depth > 1) return "[...]";
            int n = a.length();
            StringBuilder b = new StringBuilder("[");
            List<Value> items = n == 0 ? new ArrayList<>() : a.getValues(0, Math.min(n, 12));
            for (int i = 0; i < items.size(); i++) b.append(i == 0 ? "" : ", ").append(format(items.get(i), depth + 1));
            if (n > 12) b.append(", …");
            return b.append(']').toString();
        }
        ObjectReference o = (ObjectReference) v;
        if ((isStudent(o) || isCar(o)) && depth == 0) {   // משתנה / ערך החזרה: הזהות (בתוך רשימה "[...]" — רק טקסט)
            int id = vizIdOf(o);
            if (id != 0) return "\u0003" + id;
        }
        if (isStudent(o)) return "Student " + studentLabel(o, false);
        if (isCar(o)) return "Car " + carLabel(o);
        String cached = immutable.get(o);
        if (cached != null) return cached;
        ReferenceType type = o.referenceType();
        String name = type.name();
        String res;
        if (o instanceof StringReference) {
            return "\"" + shorten(((StringReference) o).value(), depth == 0 ? 80 : 30) + "\"";   // משתנה מחרוזת: ארוך יותר (מוצג גם כתאים)
        } else if (name.startsWith("unit4.collectionsLib.") && type instanceof ClassType
                && ((ClassType) type).fieldByName("vizId") != null) {
            int id = ((com.sun.jdi.IntegerValue) o.getValue(((ClassType) type).fieldByName("vizId"))).value();
            res = id == 0 ? name.substring(name.lastIndexOf('.') + 1) : "\u0003" + id;
        } else if (name.startsWith("java.lang.") && type instanceof ClassType && ((ClassType) type).fieldByName("value") != null) {
            Value inner = o.getValue(((ClassType) type).fieldByName("value"));   // Integer, Double, Character...
            res = inner instanceof PrimitiveValue ? format(inner, depth) : name.substring(name.lastIndexOf('.') + 1);
        } else {
            return name.substring(name.lastIndexOf('.') + 1);
        }
        if (immutable.size() < 20000) immutable.put(o, res);   // מחרוזות, מספרים עטופים ומזהי מבנים לא משתנים
        return res;
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void stopTracing() {
        tracing = false;
        if (step != null) {
            try { vm.eventRequestManager().deleteEventRequest(step); } catch (Exception ignored) {}
            step = null;
        }
        if (exits != null) {
            try { vm.eventRequestManager().deleteEventRequest(exits); } catch (Exception ignored) {}
            exits = null;
        }
    }

    private static void pump(final InputStream in, final OutputStream out, final boolean flushEach) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[4096];
            try {
                for (int n; (n = in.read(buf)) > 0;) {
                    out.write(buf, 0, n);
                    if (flushEach || in.available() == 0) out.flush();
                }
            } catch (IOException ignored) {
            } finally {
                if (out != System.out && out != System.err) {
                    try { out.close(); } catch (IOException ignored) {}
                }
            }
        }, "dsviz-pump");
        t.setDaemon(true);
        t.start();
    }
}
