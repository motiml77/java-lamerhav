package il.lamerhav.dsviz;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * "שליחת תרגיל": הכנת הקבצים לשליחה.
 *   PDF — כמו שהוא; תמונה — מוקטנת (עד 2000 פיקסלים בצד) ודחוסה; Word (docx) — הטקסט שלו + התמונות שבתוכו;
 *   Word ישן (doc) — לא נתמך (מבקשים לשמור כ-PDF או DOCX); קובץ טקסט — הטקסט.
 * בלי תלות ב-Eclipse (נבדק גם מחוץ לו).
 */
final class ExerciseFiles {
    private ExerciseFiles() {}

    static final int MAX_ITEMS = 10;
    static final long MAX_TOTAL = 14L * 1024 * 1024, MAX_FILE = 10L * 1024 * 1024;
    static final int MAX_SIDE = 2000, MAX_TEXT = 60000;

    /** שגיאה שמוצגת לתלמיד כמו שהיא */
    static final class UserError extends Exception {
        private static final long serialVersionUID = 1L;
        UserError(String msg) { super(msg); }
    }

    /** חלק אחד שנשלח: קובץ (mime + bytes) או טקסט */
    static final class Part {
        final String mime;
        final byte[] data;
        final String text;

        Part(String mime, byte[] data) { this.mime = mime; this.data = data; this.text = null; }
        Part(String text) { this.mime = null; this.data = null; this.text = text; }

        long size() { return data != null ? data.length : text.length() * 2L; }
    }

    /** פריט אחד ברשימה של החלון: קובץ PDF, תמונה, קובץ Word (טקסט + תמונות) או טקסט מודבק */
    static final class Item {
        final String name, kind;   // kind: pdf / image / word / text
        final List<Part> parts = new ArrayList<Part>();

        Item(String name, String kind) { this.name = name; this.kind = kind; }

        long size() {
            long n = 0;
            for (Part p : parts) n += p.size();
            return n;
        }

        /** התמונה הראשונה (לתמונה הממוזערת בחלון), או null */
        byte[] preview() {
            for (Part p : parts) if (p.data != null && p.mime.startsWith("image/") && !p.mime.equals("image/webp")) return p.data;
            return null;
        }
    }

    // ------------------------------------------------------------------ קבצים מהמחשב

    static Item fromFile(File f) throws UserError {
        String name = f.getName(), ext = ext(name);
        if (f.length() > 40L * 1024 * 1024) throw new UserError("הקובץ " + name + " גדול מדי.");
        byte[] data;
        try {
            data = Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            throw new UserError("לא הצלחתי לקרוא את הקובץ " + name + ": " + e.getMessage());
        }
        return fromBytes(name, ext, data);
    }

    static Item fromBytes(String name, String ext, byte[] data) throws UserError {
        if (isPdf(data)) {
            if (data.length > MAX_FILE) throw new UserError("קובץ ה-PDF " + name + " גדול מדי (עד 10MB). אפשר לשלוח רק את העמודים של התרגיל, או צילומי מסך.");
            Item it = new Item(name, "pdf");
            it.parts.add(new Part("application/pdf", data));
            return it;
        }
        if (ext.equals("docx") || ext.equals("docm")) return fromDocx(name, data);
        if (ext.equals("doc") || ext.equals("rtf") || ext.equals("odt"))
            throw new UserError("את הקובץ " + name + " צריך קודם לשמור ב-Word כ-PDF או כ-DOCX (שמירה בשם…).");
        if (ext.equals("txt") || ext.equals("java")) {
            String t = new String(data, StandardCharsets.UTF_8).trim();
            if (t.isEmpty()) throw new UserError("הקובץ " + name + " ריק.");
            Item it = new Item(name, "text");
            it.parts.add(new Part(cut(t)));
            return it;
        }
        if (ext.equals("heic") || ext.equals("heif"))
            throw new UserError("תמונות HEIC (מאייפון) לא נתמכות — שמרו אותה כ-JPG או שלחו צילום מסך.");
        Item it = new Item(name, "image");
        it.parts.add(image(name, data));
        return it;
    }

    /** תמונה מהלוח (Ctrl+V), כבר כ-PNG */
    static Item fromPastedImage(byte[] png, int n) throws UserError {
        Item it = new Item("תמונה מודבקת " + n, "image");
        it.parts.add(image(it.name, png));
        return it;
    }

    static Item fromPastedText(String text, int n) throws UserError {
        String t = text == null ? "" : text.trim();
        if (t.length() < 10) throw new UserError("הטקסט שהודבק קצר מדי.");
        Item it = new Item("טקסט מודבק " + n, "text");
        it.parts.add(new Part(cut(t)));
        return it;
    }

    /** בדיקה של כל הרשימה לפני שליחה */
    static void checkAll(List<Item> items) throws UserError {
        if (items.isEmpty()) throw new UserError("עוד לא צירפתם את התרגיל — בחרו קובץ או הדביקו תמונה (Ctrl+V).");
        int files = 0;
        long total = 0, text = 0;
        for (Item it : items) {
            for (Part p : it.parts) {
                if (p.data != null) { files++; total += p.data.length; }
                else text += p.text.length();
            }
        }
        if (files > MAX_ITEMS) throw new UserError("אפשר לשלוח עד " + MAX_ITEMS + " קבצים ותמונות ביחד (כולל תמונות שבתוך קובצי Word).");
        if (total > MAX_TOTAL) throw new UserError("הקבצים גדולים מדי ביחד (עד 14MB). הסירו חלק מהם, או שלחו רק את עמודי התרגיל.");
        if (text > MAX_TEXT) throw new UserError("יש יותר מדי טקסט — שלחו רק את התרגיל עצמו.");
    }

    // ------------------------------------------------------------------ תמונות

    static Part image(String name, byte[] data) throws UserError {
        if (isWebp(data)) {
            if (data.length > 4L * 1024 * 1024) throw new UserError("התמונה " + name + " גדולה מדי.");
            return new Part("image/webp", data);
        }
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(data));
        } catch (Exception e) {
            img = null;
        }
        if (img == null) throw new UserError("הקובץ " + name + " הוא לא תמונה שאפשר לקרוא (שלחו PDF, PNG, JPG או Word).");
        int w = img.getWidth(), h = img.getHeight();
        boolean small = Math.max(w, h) <= MAX_SIDE;
        if (small && isJpeg(data) && data.length <= 2L * 1024 * 1024) return new Part("image/jpeg", data);
        if (small && isPng(data) && data.length <= 1536L * 1024) return new Part("image/png", data);
        BufferedImage rgb = scaled(img, small ? 1 : (double) MAX_SIDE / Math.max(w, h));
        try {
            if (isPng(data) || !isJpeg(data)) {   // צילומי מסך: PNG שומר על טקסט חד — אם הוא לא גדול מדי
                byte[] png = png(rgb);
                if (png.length <= 1536L * 1024) return new Part("image/png", png);
            }
            return new Part("image/jpeg", jpeg(rgb, 0.88f));
        } catch (IOException e) {
            throw new UserError("לא הצלחתי להכין את התמונה " + name + ": " + e.getMessage());
        }
    }

    private static BufferedImage scaled(BufferedImage src, double k) {
        int w = Math.max(1, (int) Math.round(src.getWidth() * k)), h = Math.max(1, (int) Math.round(src.getHeight() * k));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);   // שקיפות → רקע לבן
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpeg(BufferedImage img, float quality) throws IOException {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
        if (!it.hasNext()) throw new IOException("אין כותב JPEG");
        ImageWriter w = it.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            w.setOutput(ios);
            ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(quality);
            w.write(null, new IIOImage(img, null, null), p);
        } finally {
            w.dispose();
        }
        return out.toByteArray();
    }

    /** תמונה קטנה מאוד (סמל, קו מפריד) — לפי המידות, בלי לפענח את כל התמונה */
    private static boolean tiny(byte[] data) {
        try (javax.imageio.stream.ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<javax.imageio.ImageReader> it = ImageIO.getImageReaders(in);
            if (!it.hasNext()) return true;
            javax.imageio.ImageReader r = it.next();
            try {
                r.setInput(in);
                return r.getWidth(0) < 80 || r.getHeight(0) < 40;
            } finally {
                r.dispose();
            }
        } catch (Exception e) {
            return true;
        }
    }

    // ------------------------------------------------------------------ Word (docx)

    static Item fromDocx(String name, byte[] data) throws UserError {
        Item it = new Item(name, "word");
        String text = null;
        List<Part> images = new ArrayList<Part>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String n = e.getName();
                if (n.equals("word/document.xml")) text = docxText(readAll(z, 30L * 1024 * 1024));
                else if (n.startsWith("word/media/") && images.size() < 6) {
                    String x = ext(n);
                    if (x.equals("png") || x.equals("jpg") || x.equals("jpeg") || x.equals("gif") || x.equals("bmp")) {
                        byte[] img = readAll(z, 15L * 1024 * 1024);
                        if (!tiny(img)) {   // סמלים וקווים קטנים — לא שולחים
                            try {
                                images.add(image(n, img));
                            } catch (UserError ignored) {
                                // תמונה שאי אפשר לקרוא — מדלגים
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw new UserError("לא הצלחתי לקרוא את קובץ ה-Word " + name + " (אולי הוא פגום?). אפשר לשמור אותו כ-PDF ולשלוח.");
        }
        if (text == null) throw new UserError("הקובץ " + name + " הוא לא קובץ Word תקין. אפשר לשמור אותו כ-PDF ולשלוח.");
        if (!text.trim().isEmpty()) it.parts.add(new Part(cut(text.trim())));
        it.parts.addAll(images);
        if (it.parts.isEmpty()) throw new UserError("בקובץ " + name + " אין טקסט או תמונות.");
        return it;
    }

    /** הטקסט של מסמך Word: פסקה בכל שורה, טבלאות עם | בין התאים, ונוסחאות */
    static String docxText(byte[] xml) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);   // בלי ישויות חיצוניות (XXE)
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {   // בלי הדפסות לקונסול — השגיאה עולה כחריגה
                @Override
                public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            Document d = b.parse(new ByteArrayInputStream(xml));
            StringBuilder out = new StringBuilder();
            walk(d.getDocumentElement(), out);
            return out.toString().replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    private static void walk(Node n, StringBuilder out) {
        String ln = n.getLocalName();
        if ("p".equals(ln)) {
            paragraph(n, out);
            out.append('\n');
            return;
        }
        for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) walk(c, out);
        if ("tc".equals(ln)) {
            while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') out.setLength(out.length() - 1);
            out.append(" | ");
        } else if ("tr".equals(ln)) out.append('\n');
    }

    private static void paragraph(Node n, StringBuilder out) {
        for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
            String ln = c.getLocalName();
            if (ln == null) continue;
            if (ln.equals("t")) out.append(c.getTextContent());
            else if (ln.equals("tab")) out.append('\t');
            else if (ln.equals("br") || ln.equals("cr")) out.append('\n');
            else if (ln.equals("p")) { paragraph(c, out); out.append('\n'); }
            else paragraph(c, out);
        }
    }

    // ------------------------------------------------------------------ עזרים

    static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static String cut(String t) {
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
    }

    private static byte[] readAll(InputStream in, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        long n = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            n += r;
            if (n > max) throw new IOException("גדול מדי");
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    static boolean isPdf(byte[] b) { return b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F'; }
    static boolean isPng(byte[] b) { return b.length > 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'; }
    static boolean isJpeg(byte[] b) { return b.length > 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff; }
    static boolean isWebp(byte[] b) { return b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E'; }
}
