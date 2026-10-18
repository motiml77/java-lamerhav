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
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
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
    private ClassType recorder;
    private com.sun.jdi.Field fullField;
    private ArrayReference buffer;
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
        for (String p : new String[] { "file.encoding", "stdout.encoding", "stderr.encoding", "java.awt.headless" }) {
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

        vm = lc.accept(ca);
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
            if (buffer == null) { stopTracing(); return; }
            capacity = buffer.length();
        }
        if (++lines % 50 == 0 && ((BooleanValue) recorder.getValue(fullField)).value()) {
            stopTracing();
            return;
        }

        StringBuilder meta = new StringBuilder();
        // מחסנית הקריאות (רק הקוד של התלמיד)
        List<StackFrame> frames = mainThread.frames();
        for (StackFrame fr : frames) {
            Location l = fr.location();
            String cls = l.declaringType().name();
            if (excluded(cls)) continue;
            String file;
            try { file = l.sourceName(); } catch (AbsentInformationException e) { file = ""; }
            meta.append(cls).append('\u0002').append(l.method().name()).append('\u0002').append(file)
                .append('\u0002').append(l.lineNumber()).append('\u0001');
        }
        meta.append('\u0004');
        // המשתנים המקומיים של המסגרת העליונה
        StackFrame f = frames.get(0);
        try {
            List<LocalVariable> locals = f.visibleVariables();
            Map<LocalVariable, Value> vals = locals.isEmpty() ? null : f.getValues(locals);
            if (!f.location().method().isStatic()) addVar(meta, "this", f.thisObject());
            for (LocalVariable lv : locals) {
                if (lv.name().equals("args") && lv.typeName().equals("java.lang.String[]")) continue;
                addVar(meta, lv.name(), vals.get(lv));
            }
        } catch (AbsentInformationException noDebugInfo) {
            // קומפילציה בלי מידע על משתנים (javac בלי -g) — מציגים רק את השורה
        }
        if (written >= capacity) {
            stopTracing();
            return;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                buffer.setValue(written, vm.mirrorOf(meta.toString()));
                break;
            } catch (com.sun.jdi.ObjectCollectedException gcRace) {
                // ה-GC אסף את המחרוזת החדשה לפני ששמרנו אותה — מנסים שוב
            }
        }
        written++;

        if (step == null) {
            step = vm.eventRequestManager().createStepRequest(mainThread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
            for (String x : EXCLUDE) step.addClassExclusionFilter(x);
            step.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            step.enable();
        }
    }

    private static boolean excluded(String cls) {
        for (String x : EXCLUDE) if (cls.startsWith(x.substring(0, x.length() - 1))) return true;
        return false;
    }

    private void addVar(StringBuilder meta, String name, Value v) {
        String text = format(v, 0);
        meta.append(name).append('\u0002')
            .append(text.replace('\u0001', ' ').replace('\u0002', ' ').replace('\u0004', ' '))
            .append('\u0001');
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
        String cached = immutable.get(o);
        if (cached != null) return cached;
        ReferenceType type = o.referenceType();
        String name = type.name();
        String res;
        if (o instanceof StringReference) {
            res = "\"" + shorten(((StringReference) o).value(), 30) + "\"";
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
