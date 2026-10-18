package unit4.collectionsLib;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import unit4.collectionsLib.Model.Frame;

/** מאתר וקורא את קובצי הקוד של התלמידים, כדי להציג את השורה שרצה ולנחש שמות משתנים */
final class SourceFiles {
    private SourceFiles() {}

    private static final Map<String, List<String>> cache = new HashMap<String, List<String>>();
    private static final List<String> SKIP_DIRS = Arrays.asList("bin", "out", "build", "target", "node_modules");

    static synchronized List<String> lines(Frame f) {
        if (f == null || f.file == null) return null;
        String rel = f.packagePath() + f.file;
        if (cache.containsKey(rel)) return cache.get(rel);
        File file = locate(rel);
        List<String> res = file == null ? null : read(file);
        cache.put(rel, res);
        return res;
    }

    private static final Map<String, String> texts = new HashMap<String, String>();

    /** כל הקובץ כטקסט אחד (בשביל חיפושים) */
    static synchronized String text(Frame f) {
        if (f == null || f.file == null) return null;
        String key = f.packagePath() + f.file;
        if (texts.containsKey(key)) return texts.get(key);
        List<String> l = lines(f);
        String t = l == null ? null : String.join("\n", l);
        texts.put(key, t);
        return t;
    }

    static String line(Frame f) {
        List<String> l = lines(f);
        if (l == null || f.line < 1 || f.line > l.size()) return null;
        String s = l.get(f.line - 1);
        int c = s.indexOf("//");
        return c >= 0 ? s.substring(0, c) : s;
    }

    /** "Stack<Integer> s = new Stack<Integer>();"  →  "s" */
    static String guessNewName(Frame f, String type, boolean onlyIfSingle) {
        String s = line(f);
        if (s == null) return null;
        Matcher all = Pattern.compile("new\\s+" + type + "\\b").matcher(s);
        int count = 0;
        while (all.find()) count++;
        if (count == 0 || (onlyIfSingle && count > 1)) return null;
        Matcher m = Pattern.compile("(\\w+)\\s*=\\s*new\\s+" + type + "\\b").matcher(s);
        return m.find() ? m.group(1) : null;
    }

    /** "Stack<Integer> s = Build.stack(1,2,3);"  →  "s" */
    static String guessAssignName(Frame f) {
        String s = line(f);
        if (s == null) return null;
        Matcher m = Pattern.compile("(\\w+)\\s*=\\s*Build\\s*\\.").matcher(s);
        return m.find() ? m.group(1) : null;
    }

    /** "Viz.watch(arr);"  →  "arr" */
    static String guessWatchName(Frame f) {
        String s = line(f);
        if (s == null) return null;
        Matcher m = Pattern.compile("Viz\\s*\\.\\s*watch\\s*\\(\\s*(\\w+)\\s*\\)").matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static File locate(String rel) {
        File base = new File(System.getProperty("user.dir"));
        String[] prefixes = { "src/", "", "src/main/java/", "java-visualizer/src/" };
        for (String p : prefixes) {
            File f = new File(base, p + rel);
            if (f.isFile()) return f;
        }
        return search(base, rel.replace('/', File.separatorChar), 0);
    }

    private static File search(File dir, String rel, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null || depth > 7) return null;
        for (File k : kids) {
            if (k.isFile() && k.getPath().endsWith(File.separator + rel)) return k;
        }
        for (File k : kids) {
            if (!k.isDirectory() || k.getName().startsWith(".") || SKIP_DIRS.contains(k.getName())) continue;
            File r = search(k, rel, depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    private static List<String> read(File f) {
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            String text = null;
            for (String cs : new String[] { "UTF-8", "windows-1255" }) {
                if (!Charset.isSupported(cs)) continue;
                try {
                    text = Charset.forName(cs).newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes)).toString();
                    break;
                } catch (CharacterCodingException ignored) {}
            }
            if (text == null) text = new String(bytes, Charset.defaultCharset());
            if (text.startsWith("﻿")) text = text.substring(1);
            return new ArrayList<String>(Arrays.asList(text.split("\r?\n", -1)));
        } catch (Exception e) {
            return null;
        }
    }
}
