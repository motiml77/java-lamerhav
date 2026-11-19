package il.lamerhav.dsviz;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "הגשה למורה" של תרגיל ממאגר השאלות — מה שמשותף לחלון "השאלה" של Eclipse (BankFloat, SWT) ולזה של Visual Studio / VS Code /
 * IntelliJ (BankVsFloat, Swing): אילו קבצים נשלחים, בדיקת ההרצה (RunCheck) מול הפלט הרצוי של השאלה, והנוסחים.
 * השליחה עצמה, מי מקבל, ותיבת היוצא כשאין אינטרנט — Submit (בכניסה המשותפת).
 */
final class BankSubmit {
    private BankSubmit() {}

    private static volatile long gradesAt;

    /**
     * הציונים מהמורה: ברקע (בלי לעכב את החלון) שואלים את השרת, ושומרים לכל שאלה (BankProgress.syncGrades) — חלון השאלה מתעדכן
     * לבד. רק לתלמיד שמשויך למורה, לכל היותר פעם בחצי דקה; בלי אינטרנט — נשאר מה ששמור
     */
    static void syncGradesLater() {
        if (System.currentTimeMillis() - gradesAt < 30000) return;
        gradesAt = System.currentTimeMillis();
        Thread t = new Thread(BankSubmit::syncGrades, "dsviz-grades");
        t.setDaemon(true);
        t.start();
    }

    static void syncGrades() {
        try {
            if (!"student".equals(Submit.mode())) return;
            Account.Result r = Account.studentWork();
            if (!r.ok()) return;
            Map<String, Object[]> grades = new LinkedHashMap<String, Object[]>();
            for (Object o : Json.arr(r.body.get("work"))) {
                Map<String, Object> w = Json.obj(o);
                String id = Json.str(w.get("bankId"));
                if (!id.isEmpty() && w.get("grade") instanceof Number) grades.put(id, new Object[] { w.get("grade"), Boolean.TRUE.equals(w.get("again")) });
            }
            BankProgress.get().syncGrades(grades);
        } catch (Throwable ignored) {
            // בפעם הבאה
        }
    }

    /** לבדיקות: לשאול שוב מיד */
    static void resetGradesClock() {
        gradesAt = 0;
    }

    /**
     * קובצי הקוד של התרגיל: כל קובץ עם הסיומת הזו שנמצא ישירות בתיקיית התרגיל (גם קבצים שהתלמיד הוסיף שם) — שם → תוכן,
     * והקובץ של main ראשון. קובץ שאי אפשר לקרוא מדלגים עליו.
     */
    static Map<String, String> files(File dir, String ext, String mainClass) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        File[] all = dir == null ? null : dir.listFiles();
        if (all == null) return out;
        Arrays.sort(all);
        File main = new File(dir, mainClass + ext);
        if (main.isFile()) put(out, main);
        for (File f : all) if (f.isFile() && f.getName().toLowerCase().endsWith(ext) && !f.equals(main)) put(out, f);
        return out;
    }

    private static void put(Map<String, String> out, File f) {
        try {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) text = text.substring(1);
            out.put(f.getName(), text);
        } catch (IOException e) {
            // קובץ תפוס / בלי הרשאה — לא נשלח
        }
    }

    /** ההגשה של השאלה: הפרטים שהמורה רואה (נושא, שם השאלה, רמה, התוכנה) והקבצים */
    static Submit.Work work(BankStore.Question q, String topicTitle, Map<String, String> files, String mainClass, String ext, String app) {
        Submit.Work w = new Submit.Work();
        w.source = "bank";
        w.bankId = q.id;
        w.topic = topicTitle == null ? "" : topicTitle;
        w.title = q.title;
        w.level = q.level.title;
        w.app = app;
        w.main = files.containsKey(mainClass + ext) ? mainClass + ext : files.isEmpty() ? "" : files.keySet().iterator().next();
        w.files.putAll(files);
        return w;
    }

    /**
     * בדיקת ההרצה לפני ההגשה: הקוד של התלמיד נבנה ורץ (עד 10 שניות), והפלט מושווה לפלט הרצוי של השאלה.
     * cs — C# (dsvizCs: הפקודה של dsviz-cs); אחרת Java (lib — dsviz.jar, javaExe — ה-java של הסביבה או null)
     */
    static RunCheck.Result check(BankStore.Question q, Map<String, String> files, String mainClass, boolean cs, File lib, String javaExe, List<String> dsvizCs) {
        try {
            return cs ? RunCheck.cs(files, dsvizCs, q.expectedOutput()) : RunCheck.java(files, mainClass, lib, javaExe, q.expectedOutput());
        } catch (Throwable t) {
            return new RunCheck.Result();   // לא נבדק — ההגשה נשלחת בלי בדיקת הרצה
        }
    }

    /** הכותרת של דף ההגשה: "הגשה למורה שרה כהן" */
    static String heading() {
        return Submit.label();
    }

    /** מה נשלח — שורה לדף ההגשה */
    static String filesLine(Map<String, String> files) {
        if (files.isEmpty()) return "לא נמצאו קובצי קוד של התרגיל.";
        return (files.size() == 1 ? "הקובץ שיישלח: " : "הקבצים שיישלחו: ") + String.join(", ", files.keySet());
    }
}
