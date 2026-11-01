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
    private volatile boolean tracing = true;

    // לולאה שלא נגמרת (LoopGuard): בזמן התיעוד — כל ביקור בשורת התנאי; אחרי שהתיעוד נעצר / כשאין שורות חדשות — דגימות
    private final Object jdi = new Object();
    private final LoopGuard.Tracker loops = new LoopGuard.Tracker();
    private final LoopGuard.Sampler sampler = new LoopGuard.Sampler();
    private final Map<String, List<LoopViz.Loop>> parsedLoops = new java.util.HashMap<>();
    private final java.util.Set<String> scannedClasses = new java.util.HashSet<>();
    private boolean deterministic = true;
    private com.sun.jdi.Field mutField, verdictField, inputField, maxField, enabledField;
    private volatile long lastResume = System.currentTimeMillis();
    private volatile boolean busy, sampling, finished, stopped;
    private Thread watcher;
    private int maxSteps = -1;
    private static final boolean DEBUG_LOOPS = Boolean.getBoolean("dsviz.debugLoops");

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
        // Mac: החלון מופיע ב-Dock ובתפריט העליון בשם DSViz (ולא "java"). ‎-Xdock קיים רק ב-Java של Mac
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            cmd.add("-Xdock:name=DSViz");
            cmd.add("-Dapple.awt.application.name=DSViz");
        }
        for (String p : new String[] { "file.encoding", "stdout.encoding", "stderr.encoding", "java.awt.headless", "dsviz.timing", "dsviz.fonts", "dsviz.debug", "dsviz.objview", "dsviz.conditions", "dsviz.loopArrowFreeze", "dsviz.input", "dsviz.inputDelay", "dsviz.licenseUrl", "dsviz.license", "user.home", "dsviz.varsFrame" }) {
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
        pump(Keyboard.original(System.in), proc.getOutputStream(), false);   // מה שמקלידים בקונסול / בטרמינל — לתוכנית

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
            busy = true;
            synchronized (jdi) {   // חוט הדגימה (לולאה שלא נגמרת) לא פונה לתהליך באמצע אירוע
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
                        finished = true;
                        stopTracing();
                    } else if (e instanceof BreakpointEvent) {
                        erm.deleteEventRequest(e.request());
                        mainThread = ((BreakpointEvent) e).thread();
                        startWatch();
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
            set.resume();   // ה-main נשאר מושהה אם עצרנו לולאה שלא נגמרת (suspend נוסף)
            }
            lastResume = System.currentTimeMillis();
            busy = false;
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
            mutField = recorder.fieldByName("mutations");
            verdictField = recorder.fieldByName("loopVerdict");
            inputField = recorder.fieldByName("waitingForInput");
            maxField = recorder.fieldByName("maxSteps");
            enabledField = recorder.fieldByName("enabled");
            buffer = (ArrayReference) recorder.getValue(recorder.fieldByName("lineTexts"));
            com.sun.jdi.Field lo = recorder.fieldByName("lineObjs");
            Value ob = lo == null ? null : recorder.getValue(lo);
            objBuffer = ob instanceof ArrayReference ? (ArrayReference) ob : null;
            if (buffer == null) { stopTracing(); return; }
            capacity = buffer.length();
        }
        if (++lines % 50 == 0 && (((BooleanValue) recorder.getValue(fullField)).value() || written >= maxSteps())) {
            capReached();
            return;
        }

        List<StackFrame> frames = mainThread.frames();
        if (insideRecorder(frames)) return;   // קוד של התלמיד (למשל toString) שהציור קרא לו — לא צעד של התוכנית
        String meta = meta(frames);
        if (!write(meta)) return;
        if (checkLoop(frames, meta)) return;   // לולאה שלא נגמרת — עצרנו את התוכנית

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

    /** פרטי השורה (מסגרות \u0004 משתנים \u0004 מערכים \u0004 מבנים של הפעולות הקוראות) — כמו שה-Recorder קורא */
    private String meta(List<StackFrame> frames) throws Exception {
        StringBuilder meta = new StringBuilder();
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
        meta.append('\u0004');
        appendCallerRoots(meta, frames);
        appendFrameVars(meta, frames);   // המשתנים של הפעולות שמחכות — בחירת פעולה במעקב המשתנים
        return meta.toString();
    }

    private static final java.util.Set<String> PRIMITIVES = new java.util.HashSet<>(java.util.Arrays.asList(
            "int", "long", "short", "byte", "char", "boolean", "double", "float", "java.lang.String"));

    /**
     * המבנים (חוליות, מחסניות, תורים, עצים, אובייקטים של התלמיד) שהמשתנים של הפעולות שקראו לפעולה הנוכחית מצביעים עליהם:
     * \u0003 vizId או \u0003u מקום, מופרדים ב-\u0001. כך ה-Recorder יודע שחוליה שרק main מצביע עליה לא "אבדה" בתוך פעולת עזר.
     */
    private void appendCallerRoots(StringBuilder meta, List<StackFrame> frames) {
        java.util.Set<String> done = new java.util.HashSet<>();
        int n = 0;
        for (int i = 1; i < frames.size() && n < 80; i++) {
            StackFrame fr = frames.get(i);
            if (excluded(fr.location().declaringType().name())) continue;
            n++;
            try {
                if (!fr.location().method().isStatic()) addRoot(meta, fr.thisObject(), done);
                List<LocalVariable> refs = new ArrayList<>();
                for (LocalVariable lv : fr.visibleVariables()) if (!PRIMITIVES.contains(lv.typeName())) refs.add(lv);
                if (refs.isEmpty()) continue;
                for (Value v : fr.getValues(refs).values()) addRoot(meta, v, done);
            } catch (AbsentInformationException | RuntimeException ignored) {
                // בלי מידע על המשתנים של המסגרת הזו
            }
        }
    }

    /** כמה פעולות שמחכות (מתחת לפעולה שרצה) שולחות את המשתנים שלהן, וכמה משתנים לכל אחת */
    private static final int FRAME_VARS_DEPTH = 10, FRAME_VARS_MAX = 20, FRAME_SNAPSHOTS = 6;

    /**
     * המשתנים של הפעולות שקראו לפעולה הנוכחית (במעקב המשתנים אפשר לבחור פעולה שמחכה ולראות את המשתנים שלה):
     * חצי חדש בסוף: \u0004 \u0012, ואחריו לכל מסגרת: המספר שלה (0 = העליונה, לפי סדר המסגרות של התלמיד) \u0013 המשתנים
     * (כמו בחצי של המשתנים — שם \u0002 ערך \u0001), והמסגרות מופרדות ב-\u0011. עד FRAME_VARS_DEPTH מסגרות מתחת לעליונה,
     * וגם התחתונה (main) — getValues אחד לכל מסגרת. ה-\u0012 בהתחלה מזהה את החצי (גם אם יתווספו חצאים אחרים).
     */
    private void appendFrameVars(StringBuilder meta, List<StackFrame> frames) {
        meta.append('\u0004').append('\u0012');
        List<StackFrame> user = new ArrayList<>();
        for (StackFrame fr : frames) if (!excluded(fr.location().declaringType().name())) user.add(fr);
        java.util.Set<ObjectReference> snapped = new java.util.HashSet<>();
        boolean first = true;
        for (int k = 1; k < user.size(); k++) {
            if (k > FRAME_VARS_DEPTH && k != user.size() - 1) continue;
            StackFrame fr = user.get(k);
            StringBuilder b = new StringBuilder();
            try {
                int n = 0;
                if (!fr.location().method().isStatic()) {
                    addFrameVar(b, "this", fr.thisObject(), snapped);
                    n++;
                }
                List<LocalVariable> locals = new ArrayList<>();
                for (LocalVariable lv : fr.visibleVariables()) {
                    if (lv.name().equals("args") && lv.typeName().equals("java.lang.String[]")) continue;
                    if (n + locals.size() >= FRAME_VARS_MAX) break;
                    locals.add(lv);
                }
                Map<LocalVariable, Value> vals = locals.isEmpty() ? null : fr.getValues(locals);
                for (LocalVariable lv : locals) addFrameVar(b, lv.name(), vals.get(lv), snapped);
            } catch (AbsentInformationException | RuntimeException e) {
                continue;   // בלי מידע על המשתנים של המסגרת הזו — היא לא נשלחת
            }
            if (!first) meta.append('\u0011');
            first = false;
            meta.append(k).append('\u0013').append(b);
        }
    }

    private void addFrameVar(StringBuilder b, String name, Value v, java.util.Set<ObjectReference> snapped) {
        String ref = v instanceof ObjectReference && isUserClass((ObjectReference) v) ? userRef((ObjectReference) v) : null;
        b.append(frameClean(name)).append('\u0002').append(frameClean(clean(ref != null ? ref : format(v, 0))));
        // הערכים של אובייקט של התלמיד ברגע הזה (כמו במסגרת העליונה) — פעם אחת לכל אובייקט בשורה
        if (ref != null && snapped.size() < FRAME_SNAPSHOTS && snapped.add((ObjectReference) v)) b.append(frameClean(snapshot((ObjectReference) v)));
        b.append('\u0001');
    }

    private static String frameClean(String s) {
        return s.replace('\u0011', ' ').replace('\u0012', ' ').replace('\u0013', ' ');
    }

    private void addRoot(StringBuilder meta, Value v, java.util.Set<String> done) {
        if (!(v instanceof ObjectReference) || v instanceof StringReference) return;
        ObjectReference o = (ObjectReference) v;
        String type = o.referenceType().name();
        if (o instanceof ArrayReference) {   // מערך של חוליות / מבנים
            if (!type.startsWith("unit4.collectionsLib.")) return;
            ArrayReference a = (ArrayReference) o;
            int len = Math.min(a.length(), 60);
            if (len > 0) for (Value x : a.getValues(0, len)) addRoot(meta, x, done);
            return;
        }
        String ref = null;
        if (type.startsWith("unit4.collectionsLib.")) {
            int id = vizIdOf(o);
            if (id != 0) ref = "\u0003" + id;
        } else if (isUserClass(o)) {
            ref = userRef(o);
        }
        if (ref != null && done.add(ref)) meta.append(ref).append('\u0001');
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
        loops.exit(studentDepth(frames));
        appendFrames(meta, frames);
        meta.append('\u0004');
        if (m.returnTypeName().equals("void")) meta.append('\u0006');
        else meta.append(clean(format(e.returnValue(), 0)));
        // האובייקט שהפעולה רצה בתוכו — כפי שהוא ביציאה (למשל age אחרי birthday)
        ObjectReference self = m.isStatic() ? null : frames.get(0).thisObject();
        if (self != null && isUserClass(self)) {
            String ref = userRef(self);
            if (ref != null) meta.append('\u0004').append(ref).append(snapshot(self));
        }
        write(meta.toString());
    }

    /** כותב מחרוזת למקום הבא ב-Recorder.lineTexts שבתהליך התוכנית. false = נגמר המקום */
    private boolean write(String meta) throws Exception {
        if (written >= capacity) {
            capReached();
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
        meta.append(name).append('\u0002').append(clean(text));
        if (ref != null) meta.append(snapshot((ObjectReference) v));
        meta.append('\u0001');
    }

    /**
     * הערכים של המאפיינים הפשוטים (מספרים, תווים, מחרוזות) של אובייקט של התלמיד ברגע הזה: \u0007 שם \u0010 ערך \u000F ...
     * ה-Recorder מצלם את הצעדים מאוחר יותר (כשהתוכנית פונה לספרייה), ואז האובייקט כבר השתנה — למשל age בבנאי.
     * ערך: s + מחרוזת, c + תו, n + מספר / boolean, 0 = null.
     */
    private String snapshot(ObjectReference o) {
        StringBuilder b = new StringBuilder("\u0007");
        int n = 0;
        try {
            for (com.sun.jdi.Field f : o.referenceType().allFields()) {
                if (f.isStatic() || f.isSynthetic() || n >= 12) continue;
                Value x = o.getValue(f);
                String val;
                if (x == null) val = "0";
                else if (x instanceof StringReference) val = "s" + shorten(((StringReference) x).value(), 40);
                else if (x instanceof CharValue) val = "c" + ((CharValue) x).value();
                else if (x instanceof PrimitiveValue) val = "n" + x;
                else continue;
                b.append(n++ == 0 ? "" : "\u000F").append(f.name()).append('\u0010')
                 .append(clean(val).replace('\u000F', ' ').replace('\u0010', ' '));
            }
        } catch (RuntimeException e) {
            return "";
        }
        return n == 0 ? "" : b.toString();
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

    // ------------------------------------------------------------ לולאה שלא נגמרת

    /** מגבלת הצעדים של ה-Recorder (Viz.maxSteps יכול לשנות אותה) — נקראת מחדש מדי פעם */
    private int maxSteps() {
        if (maxSteps < 0 || lines % 500 == 0) {
            try {
                maxSteps = ((com.sun.jdi.IntegerValue) recorder.getValue(maxField)).value();
            } catch (RuntimeException e) {
                maxSteps = 5000;
            }
        }
        return maxSteps + 20;   // קצת מרווח: חלק מהשורות לא הופכות לצעדים
    }

    /** התיעוד מלא: מפסיקים לעצור בכל שורה, והתוכנית רצה במהירות רגילה — אבל דוגמים אותה (אולי היא תקועה בלולאה) */
    private void capReached() {
        stopTracing();
        if (!finished && !stopped) sampling = true;
    }

    private void startWatch() {
        if (watcher != null) return;
        watcher = new Thread(this::watch, "dsviz-loop-watch");
        watcher.setDaemon(true);
        watcher.start();
    }

    /** חוט הדגימה: כשהתיעוד נעצר, או כשאין שורה חדשה כבר QUIET_MS (לולאה בשורה אחת) — דוגמים איפה התוכנית */
    private void watch() {
        while (!stopped && !finished) {
            try {
                Thread.sleep(LoopGuard.SAMPLE_EVERY_MS);
            } catch (InterruptedException e) {
                return;
            }
            try {
                synchronized (jdi) {
                    if (stopped || finished || busy || mainThread == null) continue;
                    boolean quiet = tracing && System.currentTimeMillis() - lastResume > LoopGuard.QUIET_MS;
                    if (!sampling && !quiet) {
                        sampler.reset();
                        continue;
                    }
                    sample(quiet && !sampling);
                }
            } catch (com.sun.jdi.VMDisconnectedException e) {
                return;
            } catch (Exception e) {
                if (DEBUG_LOOPS || Boolean.getBoolean("dsviz.debug")) e.printStackTrace();
            }
        }
    }

    /** דגימה אחת: הלולאות שהתוכנית בתוכן (בכל הפעולות במחסנית), וערכי המשתנים שבתנאים */
    private void sample(boolean quiet) throws Exception {
        if (DEBUG_LOOPS) System.err.println("[loop] sample quiet=" + quiet + " status=" + mainThread.status() + " suspended=" + mainThread.isSuspended());
        if (mainThread.status() != ThreadReference.THREAD_STATUS_RUNNING) {   // ישנה / מחכה / חסומה — לא לולאה שמסתובבת
            sampler.reset();
            return;
        }
        if (recorder != null && inputField != null && ((BooleanValue) recorder.getValue(inputField)).value()) {   // מחכה לקלט מהחלון
            sampler.reset();
            return;
        }
        if (recorder != null && enabledField != null && !((BooleanValue) recorder.getValue(enabledField)).value()) return;   // בלי חלון
        LoopGuard.Sample smp = new LoopGuard.Sample();
        smp.t = System.currentTimeMillis();
        mainThread.suspend();
        try {
            List<StackFrame> frames = mainThread.frames();
            if (waitingForInput(frames)) {
                sampler.reset();
                return;
            }
            if (insideRecorder(frames)) return;   // הספרייה מציירת (למשל מעבדת שורות שהצטברו) — לא נחשב
            List<StackFrame> student = new ArrayList<>();
            for (StackFrame fr : frames) if (!excluded(fr.location().declaringType().name())) student.add(fr);
            if (student.isEmpty()) return;
            smp.studentTop = !excluded(frames.get(0).location().declaringType().name());
            for (int i = 0; i < student.size(); i++) {
                StackFrame fr = student.get(i);
                Location loc = fr.location();
                Model.Frame mf = modelFrame(loc);
                List<String> src = SourceFiles.lines(mf);
                for (LoopViz.Loop l : LoopGuard.around(loopsOf(mf), loc.lineNumber())) {
                    LoopGuard.Info info = LoopGuard.analyze(l, src);
                    if (info == null) continue;
                    LoopGuard.Ctx c = new LoopGuard.Ctx();
                    c.level = student.size() - 1 - i;
                    c.top = i == 0;
                    c.fileKey = mf.packagePath() + mf.file;
                    c.method = loc.method().name() + loc.method().signature();
                    c.info = info;
                    c.vals = condValues(fr, info.vars);
                    smp.ctxs.add(c);
                }
            }
            if (mutField != null) smp.mutations = ((com.sun.jdi.IntegerValue) recorder.getValue(mutField)).value();
        } finally {
            mainThread.resume();
        }
        sampler.quiet = quiet;
        LoopGuard.Hit h = sampler.add(smp);
        if (DEBUG_LOOPS) System.err.println("[loop] ctxs=" + smp.ctxs.size() + " top=" + smp.studentTop + " hit=" + (h == null ? null : h.verdict));
        if (h == null) return;
        h.verdict.quiet = true;
        stopTracing();
        stopInLoop(h);
    }

    /**
     * עוצרים את התוכנית בתוך הלולאה שהדוגם החליט עליה: משהים את ה-main (גם כשהלולאה כבר מקומפלת — נקודת עצירה לא תמיד נתפסת שם)
     * ומשאירים אותו מושהה רק ברגע שהוא בקוד של התלמיד, בלולאה הזו — לא באמצע הדפסה או ציור (שם הוא מחזיק מנעולים
     * שהחלון צריך). אם עוד לא — משחררים לרגע ומנסים שוב.
     */
    private void stopInLoop(LoopGuard.Hit h) throws Exception {
        LoopViz.Loop l = h.ctx.info.loop;
        for (int tries = 0; tries < 600 && !stopped; tries++) {
            if (mainThread.isCollected() || mainThread.status() == ThreadReference.THREAD_STATUS_ZOMBIE) return;
            mainThread.suspend();
            boolean keep = false;
            try {
                List<StackFrame> frames = mainThread.frames();
                boolean safe = !insideRecorder(frames) && (!excluded(frames.get(0).location().declaringType().name()) || tries > 300 && !locked(frames));
                for (int k = 0; safe && k < frames.size(); k++) {
                    Location loc = frames.get(k).location();
                    if (excluded(loc.declaringType().name())) continue;
                    Model.Frame mf = modelFrame(loc);
                    if (!(mf.packagePath() + mf.file).equals(h.ctx.fileKey) || !(loc.method().name() + loc.method().signature()).equals(h.ctx.method)
                            || loc.lineNumber() < l.header || loc.lineNumber() > l.end) continue;
                    setVerdict(h.verdict.toString(), meta(frames.subList(k, frames.size())), h.ctx.info.atLine);
                    keep = true;   // נשאר מושהה
                    return;
                }
            } finally {
                if (!keep) mainThread.resume();
            }
            Thread.sleep(tries < 50 ? 2 : 10);
        }
    }

    /** ה-main באמצע קוד של Java שמחזיק מנעול (הדפסה, חלון) — לא משאירים אותו מושהה שם */
    private static boolean locked(List<StackFrame> frames) {
        for (int i = 0; i < frames.size() && i < 12; i++) {
            String n = frames.get(i).location().declaringType().name();
            if (n.startsWith("java.io.") || n.startsWith("sun.nio.") || n.startsWith("java.awt.") || n.startsWith("javax.swing.")
                    || n.startsWith("sun.awt.") || n.startsWith("java.util.concurrent.")) return true;
        }
        return false;
    }

    /**
     * בזמן התיעוד, בכל שורה: יציאה מלולאות, ובשורת התנאי של לולאה — האם היא לא נגמרת (LoopGuard.Tracker).
     * true = החלטנו, והתוכנית נעצרה.
     */
    private boolean checkLoop(List<StackFrame> frames, String meta) throws Exception {
        StackFrame top = frames.get(0);
        Location loc = top.location();
        Model.Frame mf = modelFrame(loc);
        String fileKey = mf.packagePath() + mf.file, method = loc.method().name() + loc.method().signature();
        int depth = studentDepth(frames), line = loc.lineNumber();
        loops.line(depth, fileKey, method, line);
        LoopViz.Loop l = LoopGuard.at(loopsOf(mf), line);
        if (l == null) return false;
        List<String> src = SourceFiles.lines(mf);
        LoopGuard.Info info = LoopGuard.analyze(l, src);
        if (info == null) return false;
        Map<String, String> vals = condValues(top, info.vars);
        int mut = mutField == null ? 0 : ((com.sun.jdi.IntegerValue) recorder.getValue(mutField)).value();
        int cut = meta.indexOf('\u0004');
        String cheap = cut < 0 ? null : meta.substring(cut) + "#" + mut;
        boolean det = deterministic(frames);
        LoopGuard.Verdict v = loops.header(depth, fileKey, method, info, vals, mut, cheap, () -> deepState(frames), det, System.currentTimeMillis());
        if (v == null) return false;
        stop(v.toString(), meta, info.atLine);
        return true;
    }

    /**
     * עוצרים: מפסיקים לעקוב, כותבים ל-Recorder את ההחלטה (והוא מוסיף את הצעד עם הכרטיס), ומשאירים את ה-main מושהה —
     * בלי לשרוף מעבד. החלון נשאר פתוח; כשסוגרים אותו התהליך נגמר.
     */
    private void stop(String verdict, String meta, int atLine) throws Exception {
        setVerdict(verdict, meta, atLine);
        mainThread.suspend();   // נשאר מושהה גם אחרי set.resume()
    }

    /** כותבים ל-Recorder את ההחלטה ואת פרטי השורה (בשורת התנאי של הלולאה) — והוא מוסיף את הצעד עם הכרטיס */
    private void setVerdict(String verdict, String meta, int atLine) throws Exception {
        stopped = true;
        sampling = false;
        stopTracing();
        String text = verdict + "\u0011" + withLine(meta, atLine);
        for (int attempt = 0; attempt < 3 && verdictField != null; attempt++) {
            try {
                recorder.setValue(verdictField, vm.mirrorOf(text));
                break;
            } catch (com.sun.jdi.ObjectCollectedException gcRace) {
                // ה-GC אסף את המחרוזת לפני ששמרנו — שוב
            }
        }
        if (DEBUG_LOOPS) System.err.println("[loop] stopped: " + verdict);
    }

    /** פרטי השורה, אבל בשורה של התנאי של הלולאה (שם מסמנים בקוד) */
    private static String withLine(String meta, int line) {
        int end = meta.indexOf('\u0001');
        if (end < 0) return meta;
        String[] p = meta.substring(0, end).split("\u0002", -1);
        if (p.length < 4) return meta;
        p[3] = String.valueOf(line);
        return String.join("\u0002", p) + meta.substring(end);
    }

    private static int studentDepth(List<StackFrame> frames) {
        int n = 0;
        for (StackFrame fr : frames) if (!excluded(fr.location().declaringType().name())) n++;
        return n;
    }

    private static Model.Frame modelFrame(Location loc) {
        String file;
        try { file = loc.sourceName(); } catch (AbsentInformationException e) { file = null; }
        return new Model.Frame(loc.declaringType().name(), loc.method().name(), file, loc.lineNumber());
    }

    private List<LoopViz.Loop> loopsOf(Model.Frame f) {
        String key = f.packagePath() + f.file;
        List<LoopViz.Loop> l = parsedLoops.get(key);
        if (l == null) {
            List<String> src = SourceFiles.lines(f);
            parsedLoops.put(key, l = src == null ? new ArrayList<>() : LoopViz.parse(src));
        }
        return l;
    }

    /** האם בקוד של התלמיד (המחלקות שנטענו עד עכשיו) אין אקראיות / קלט / שעון — רק אז "אותו מצב חוזר" אומר לולאה אינסופית */
    private boolean deterministic(List<StackFrame> frames) {
        boolean fresh = false;
        for (StackFrame fr : frames) {
            String cls = fr.location().declaringType().name();
            if (!excluded(cls) && !scannedClasses.contains(cls)) fresh = true;
        }
        if (!fresh) return deterministic;
        for (ReferenceType t : vm.allClasses()) {
            String n = t.name();
            if (excluded(n) || n.contains("[") || !scannedClasses.add(n)) continue;
            try {
                String text = SourceFiles.text(new Model.Frame(n, "", t.sourceName(), 0));
                if (text == null || LoopGuard.nondeterministic(text)) deterministic = false;   // בלי קוד מקור — לא יודעים מה יש בה
            } catch (AbsentInformationException | RuntimeException e) {
                deterministic = false;
            }
        }
        return deterministic;
    }

    /** התוכנית מחכה לקלט מהמקלדת (Scanner / BufferedReader / System.in) — לא לולאה */
    private static boolean waitingForInput(List<StackFrame> frames) {
        for (int i = 0; i < frames.size() && i < 16; i++) {
            String n = frames.get(i).location().declaringType().name();
            if (n.startsWith("java.util.Scanner") || n.equals("java.io.FileInputStream") || n.equals("java.io.BufferedInputStream")
                    || n.equals("java.io.PipedInputStream") || n.equals("java.io.InputStreamReader") || n.equals("java.io.BufferedReader")
                    || n.startsWith("java.io.Console") || n.equals("sun.nio.cs.StreamDecoder") || n.startsWith("jdk.internal.io"))
                return true;
        }
        return false;
    }

    /** ערכי המשתנים שבתנאי, בפעולה הזו: מקומיים, ואם אין — מאפיין של this או משתנה סטטי של המחלקה */
    private Map<String, String> condValues(StackFrame fr, List<String> names) {
        Map<String, String> res = new java.util.HashMap<>();
        if (names.isEmpty()) return res;
        try {
            List<LocalVariable> want = new ArrayList<>();
            for (LocalVariable lv : fr.visibleVariables()) if (names.contains(lv.name())) want.add(lv);
            if (!want.isEmpty()) {
                Map<LocalVariable, Value> vals = fr.getValues(want);
                for (LocalVariable lv : want) res.put(lv.name(), ident(vals.get(lv)));
            }
        } catch (AbsentInformationException ignored) {
            // בלי מידע על משתנים
        }
        for (String n : names) {
            if (res.containsKey(n)) continue;
            try {
                ObjectReference self = fr.location().method().isStatic() ? null : fr.thisObject();
                com.sun.jdi.Field f = self == null ? null : self.referenceType().fieldByName(n);
                if (f != null && !f.isStatic()) {
                    res.put(n, ident(self.getValue(f)));
                    continue;
                }
                ReferenceType t = fr.location().declaringType();
                com.sun.jdi.Field sf = t.fieldByName(n);
                if (sf != null && sf.isStatic()) res.put(n, ident(t.getValue(sf)));
            } catch (RuntimeException ignored) {
                // לא נמצא
            }
        }
        return res;
    }

    /** ערך להשוואה בין סיבובים: מספר / תו / מחרוזת כמו שהם; מבנה של הספרייה — הזהות שלו; אובייקט — זהות + המאפיינים הפשוטים */
    private String ident(Value v) {
        if (v == null) return "null";
        if (v instanceof PrimitiveValue || v instanceof StringReference) return format(v, 0);
        if (v instanceof ArrayReference) return "@" + ((ArrayReference) v).uniqueID() + format(v, 1);
        ObjectReference o = (ObjectReference) v;
        String f = format(o, 0);
        if (f.startsWith("\u0003")) return f;   // מבנה של הספרייה (התוכן — לפי מונה השינויים)
        if (o.referenceType().name().startsWith("java.lang.") && LoopGuard.num(f) != null) return f;   // Integer / Double
        return "@" + o.uniqueID() + snapshot(o);
    }

    /**
     * המצב המלא (בשביל "אותו מצב חוזר"): המשתנים של הפעולה, this, והמשתנים הסטטיים של המחלקות שבמחסנית — עם כל מה שהם מצביעים עליו
     * (מבנים, מערכים, אובייקטים), לפי סדר הגילוי (בלי כתובות אמיתיות). null אם גדול מדי.
     */
    private String deepState(List<StackFrame> frames) {
        StringBuilder b = new StringBuilder();
        Map<ObjectReference, Integer> ids = new java.util.HashMap<>();
        java.util.ArrayDeque<ObjectReference> todo = new java.util.ArrayDeque<>();
        try {
            StackFrame top = frames.get(0);
            List<LocalVariable> locals = top.visibleVariables();
            Map<LocalVariable, Value> vals = locals.isEmpty() ? new java.util.HashMap<>() : top.getValues(locals);
            for (LocalVariable lv : locals) {
                b.append(lv.name()).append('=');
                deepValue(b, vals.get(lv), ids, todo);
                b.append(';');
            }
            b.append("this=");
            deepValue(b, top.location().method().isStatic() ? null : top.thisObject(), ids, todo);
            b.append(';');
            java.util.Set<ReferenceType> types = new java.util.LinkedHashSet<>();
            for (StackFrame fr : frames) if (!excluded(fr.location().declaringType().name())) types.add(fr.location().declaringType());
            for (ReferenceType t : types)
                for (com.sun.jdi.Field f : t.allFields()) {
                    if (!f.isStatic() || f.isSynthetic()) continue;
                    b.append(t.name()).append('.').append(f.name()).append('=');
                    deepValue(b, t.getValue(f), ids, todo);
                    b.append(';');
                }
            int n = 0;
            while (!todo.isEmpty()) {
                if (++n > 400 || b.length() > 300_000) return null;
                ObjectReference o = todo.poll();
                b.append('#').append(ids.get(o)).append('{');
                if (o instanceof ArrayReference) {
                    ArrayReference a = (ArrayReference) o;
                    int len = a.length();
                    if (len > 5000) return null;
                    b.append(len).append(':');
                    if (len > 0) for (Value x : a.getValues()) { deepValue(b, x, ids, todo); b.append(','); }
                } else {
                    List<com.sun.jdi.Field> fs = new ArrayList<>();
                    for (com.sun.jdi.Field f : o.referenceType().allFields()) if (!f.isStatic()) fs.add(f);
                    b.append(o.referenceType().name()).append(':');
                    if (!fs.isEmpty()) {
                        Map<com.sun.jdi.Field, Value> m = o.getValues(fs);
                        for (com.sun.jdi.Field f : fs) { deepValue(b, m.get(f), ids, todo); b.append(','); }
                    }
                }
                b.append('}');
            }
            return b.toString();
        } catch (AbsentInformationException | RuntimeException e) {
            return null;
        }
    }

    private static void deepValue(StringBuilder b, Value v, Map<ObjectReference, Integer> ids, java.util.ArrayDeque<ObjectReference> todo) {
        if (v == null) { b.append("null"); return; }
        if (v instanceof PrimitiveValue) { b.append(v); return; }
        if (v instanceof StringReference) { b.append('"').append(((StringReference) v).value()).append('"'); return; }
        ObjectReference o = (ObjectReference) v;
        if (o instanceof com.sun.jdi.ClassObjectReference || o instanceof ThreadReference || o instanceof com.sun.jdi.ClassLoaderReference) {
            b.append('<').append(o.referenceType().name()).append('>');
            return;
        }
        Integer id = ids.get(o);
        if (id == null) {
            ids.put(o, id = ids.size());
            todo.add(o);
        }
        b.append('@').append(id);
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
