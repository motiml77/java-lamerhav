package il.lamerhav.dsviz.intellij;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;

/**
 * חלון "שליחת תרגיל": מצרפים את דף התרגיל — קובצי PDF / Word / תמונות, או צילום מסך שמדביקים (Ctrl+V) — ובוחרים לאיזו תיקיית קוד.
 * השליחה עצמה (SubmitCli, אותו מנוע כמו ב-Eclipse וב-VS Code) — Submitter.
 */
final class SubmitDialog extends DialogWrapper {
    /** קובץ ברשימה: הנתיב (תמונה מודבקת נשמרת לקובץ זמני) והשם להצגה */
    static final class Item {
        final File file;
        final String name;

        Item(File file, String name) {
            this.file = file;
            this.name = name;
        }
    }

    private final Project project;
    private final List<ExerciseDialog.Target> targets;
    private final DefaultListModel<Item> model = new DefaultListModel<Item>();
    private JBList<Item> list;
    private JBTextArea note;
    private ComboBox<ExerciseDialog.Target> targetCombo;
    private int pasted;

    SubmitDialog(Project project, List<ExerciseDialog.Target> targets) {
        super(project, true);
        this.project = project;
        this.targets = targets;
        setTitle("שליחת תרגיל");
        setOKButtonText("שליחה");
        setCancelButtonText("ביטול");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        root.add(Forms.header("שליחת תרגיל", "מצרפים את דף התרגיל ומקבלים קוד מוכן: מחלקות, main ופעולה לכל סעיף."), BorderLayout.NORTH);
        Forms.Column c = new Forms.Column();
        c.section("דף התרגיל");
        c.add(Forms.muted("<html>קובץ PDF או Word, תמונה — או צילום מסך: מצלמים את התרגיל, לוחצים כאן ו-Ctrl+V. אפשר כמה קבצים (עד 10).</html>"));
        list = new JBList<Item>(model);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.getEmptyText().setText("עוד לא צורף כלום — \"בחירת קבצים…\" או Ctrl+V");
        list.setCellRenderer(new ColoredListCellRenderer<Item>() {
            @Override
            protected void customizeCellRenderer(JList<? extends Item> l, Item it, int index, boolean selected, boolean focus) {
                String n = it.name.toLowerCase();
                setIcon(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") ? AllIcons.FileTypes.Image
                        : n.endsWith(".txt") ? AllIcons.FileTypes.Text : AllIcons.FileTypes.Any_type);
                append(it.name);
                append("   " + Math.max(1, it.file.length() / 1024) + " KB", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        });
        list.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || s.isDataFlavorSupported(DataFlavor.imageFlavor);
            }

            @Override
            public boolean importData(TransferSupport s) {
                return addFrom(s.getTransferable());
            }
        });
        JBScrollPane scroll = new JBScrollPane(list);
        scroll.setPreferredSize(new Dimension(JBUI.scale(460), JBUI.scale(140)));
        c.add(scroll);
        javax.swing.JButton choose = new javax.swing.JButton("בחירת קבצים…", AllIcons.Actions.MenuOpen);
        javax.swing.JButton paste = new javax.swing.JButton("הדבקה (Ctrl+V)", AllIcons.Actions.MenuPaste);
        javax.swing.JButton remove = new javax.swing.JButton("הסרה", AllIcons.General.Remove);
        choose.addActionListener(e -> choose());
        paste.addActionListener(e -> paste());
        remove.addActionListener(e -> {
            int[] sel = list.getSelectedIndices();
            for (int i = sel.length - 1; i >= 0; i--) model.remove(sel[i]);
        });
        c.add(Forms.row(choose, paste, remove));
        c.section("הערה (לא חובה)");
        note = new JBTextArea(2, 40);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.getEmptyText().setText("למשל: רק סעיפים א-ב, או \"התרגיל בעמוד 2\"");
        c.add(new JBScrollPane(note));
        c.section("לאיזו תיקייה");
        targetCombo = new ComboBox<ExerciseDialog.Target>(targets.toArray(new ExerciseDialog.Target[0]));
        c.add(Forms.labeled(new JBLabel("תיקיית קוד:"), targetCombo));
        c.add(Forms.muted("<html>התרגיל נכנס לחבילה משלו (למשל ex01_stacks), והסמן יחכה במקום הפתרון של הסעיף הראשון.</html>"), 6);
        root.add(Forms.card(c.done()), BorderLayout.CENTER);
        // Ctrl+V בכל מקום בחלון (חוץ משדה ההערה — שם מדביקים טקסט)
        root.registerKeyboardAction(e -> paste(), KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        Forms.rtl(root, targetCombo);
        return root;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return list;
    }

    @Override
    protected ValidationInfo doValidate() {
        return model.isEmpty() ? new ValidationInfo("עוד לא צירפתם את התרגיל — בחרו קובץ או הדביקו תמונה (Ctrl+V).", list) : null;
    }

    private void choose() {
        FileChooserDescriptor d = new FileChooserDescriptor(true, false, false, false, false, true);
        d.setTitle("בחירת דף התרגיל");
        d.withFileFilter(f -> f.getExtension() != null && f.getExtension().toLowerCase().matches("pdf|docx|doc|png|jpg|jpeg|gif|bmp|webp|txt"));
        for (VirtualFile f : FileChooser.chooseFiles(d, project, null)) add(new File(f.getPath()), f.getName());
    }

    /** Ctrl+V: תמונה (צילום מסך), קבצים שהועתקו, או טקסט */
    void paste() {
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        try {
            Transferable t = cb.getContents(null);
            if (t != null && addFrom(t)) return;
            if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                if (note.isFocusOwner()) {
                    note.paste();
                    return;
                }
                String text = (String) t.getTransferData(DataFlavor.stringFlavor);
                if (text != null && text.trim().length() >= 10) {
                    File f = File.createTempFile("dsviz-text-", ".txt");
                    Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
                    add(f, "טקסט מודבק " + (++pasted) + ".txt");
                }
            }
        } catch (Exception ignored) {
            // אין מה להדביק
        }
    }

    @SuppressWarnings("unchecked")
    private boolean addFrom(Transferable t) {
        try {
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                for (File f : (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor)) add(f, f.getName());
                return true;
            }
            if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                BufferedImage b = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB);
                b.getGraphics().drawImage(img, 0, 0, null);
                File f = File.createTempFile("dsviz-paste-", ".png");
                ImageIO.write(b, "png", f);
                add(f, "צילום מסך " + (++pasted) + ".png");
                return true;
            }
        } catch (Exception ignored) {
            // לא תמונה ולא קבצים
        }
        return false;
    }

    /** מוסיף קובץ לרשימה (גם לבדיקות ולצילומי מסך) */
    void add(File f, String name) {
        if (f.isFile()) model.addElement(new Item(f, name));
    }

    List<Item> items() {
        List<Item> l = new ArrayList<Item>();
        for (int i = 0; i < model.size(); i++) l.add(model.get(i));
        return l;
    }

    String note() {
        return note.getText().trim();
    }

    ExerciseDialog.Target target() {
        return (ExerciseDialog.Target) targetCombo.getSelectedItem();
    }
}
