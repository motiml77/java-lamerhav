using System;
using Microsoft.VisualStudio.Shell;
using Microsoft.VisualStudio.Shell.Interop;
using WinForms = System.Windows.Forms;

namespace DsViz.VsExtension
{
    /// <summary>
    /// הודעה בעברית מימין לשמאל. החלון של Visual Studio (VsShellUtilities.ShowMessageBox) תמיד משמאל לימין, ולכן שורה
    /// שמערבת עברית ואנגלית ("כל מה ש-DSViz מוסיף ל-Visual Studio ייכבה") יוצאת בסדר הפוך והטקסט נצמד לשמאל.
    /// כאן: חלון ההודעה של Windows עם RtlReading + RightAlign — הכותרת, הטקסט והכפתורים מימין לשמאל.
    /// </summary>
    internal static class RtlBox
    {
        sealed class Owner : WinForms.IWin32Window
        {
            readonly IntPtr handle;
            public Owner(IntPtr h) { handle = h; }
            public IntPtr Handle { get { return handle; } }
        }

        /// <summary>
        /// הודעת שגיאה עם שני כפתורים: "סגירה" (ברירת המחדל, גם Esc) ו"דיווח על הבעיה". true — המשתמש ביקש לדווח.
        /// חלון של WinForms מימין לשמאל (MessageBox לא מאפשר כיתוב משלנו על הכפתורים)
        /// </summary>
        public static bool AskReport(IServiceProvider services, string text, string caption)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            IntPtr owner = IntPtr.Zero;
            try { var shell = services == null ? null : services.GetService(typeof(SVsUIShell)) as IVsUIShell; if (shell != null) shell.GetDialogOwnerHwnd(out owner); } catch { owner = IntPtr.Zero; }
            using (var f = new WinForms.Form())
            {
                f.Text = caption;
                f.RightToLeft = WinForms.RightToLeft.Yes;
                f.RightToLeftLayout = true;
                f.FormBorderStyle = WinForms.FormBorderStyle.FixedDialog;
                f.MinimizeBox = false; f.MaximizeBox = false; f.ShowInTaskbar = false;
                f.StartPosition = owner == IntPtr.Zero ? WinForms.FormStartPosition.CenterScreen : WinForms.FormStartPosition.CenterParent;
                f.Font = new System.Drawing.Font("Segoe UI", 10f);
                f.ClientSize = new System.Drawing.Size(620, 330);
                var box = new WinForms.TextBox
                {
                    Multiline = true, ReadOnly = true, ScrollBars = WinForms.ScrollBars.Vertical, Text = (text ?? "").Replace("\r\n", "\n").Replace("\n", "\r\n"),
                    Left = 14, Top = 14, Width = 592, Height = 230, BackColor = System.Drawing.SystemColors.Window, TabStop = false,
                    RightToLeft = WinForms.RightToLeft.Yes,
                };
                var hint = new WinForms.Label
                {
                    Text = "חושבים שזו תקלה של DSViz? \"דיווח על הבעיה\" שולח לצוות את ההודעה ופרטים טכניים (בלי הקוד שלך).",
                    Left = 14, Top = 250, Width = 592, Height = 36, ForeColor = System.Drawing.SystemColors.GrayText,
                };
                var close = new WinForms.Button { Text = "סגירה", DialogResult = WinForms.DialogResult.Cancel, Width = 110, Height = 30, Top = 290, Left = 496 };
                var report = new WinForms.Button { Text = "דיווח על הבעיה", DialogResult = WinForms.DialogResult.Yes, Width = 150, Height = 30, Top = 290, Left = 334 };
                box.AccessibleName = box.Text.Length > 2000 ? box.Text.Substring(0, 2000) : box.Text;   // קוראי מסך (וגם הבדיקות האוטומטיות) קוראים את ההודעה
                f.Controls.AddRange(new WinForms.Control[] { box, hint, close, report });
                f.AcceptButton = close;
                f.CancelButton = close;
                f.Shown += (s, e) => close.Focus();
                var r = owner == IntPtr.Zero ? f.ShowDialog() : f.ShowDialog(new Owner(owner));
                return r == WinForms.DialogResult.Yes;
            }
        }

        /// <summary>true = נלחץ "אישור" (OK). okCancel: שני כפתורים (אישור / ביטול); warning: סימן אזהרה במקום "מידע"</summary>
        public static bool Show(IServiceProvider services, string text, string caption, bool okCancel, bool warning)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            IVsUIShell shell = null;
            IntPtr owner = IntPtr.Zero;
            try
            {
                shell = services == null ? null : services.GetService(typeof(SVsUIShell)) as IVsUIShell;
                if (shell != null) shell.GetDialogOwnerHwnd(out owner);
            }
            catch { owner = IntPtr.Zero; }
            bool modeless = false;
            try { if (shell != null) { shell.EnableModeless(0); modeless = true; } } catch { }
            try
            {
                WinForms.MessageBoxOptions rtl = WinForms.MessageBoxOptions.RtlReading | WinForms.MessageBoxOptions.RightAlign;
                WinForms.MessageBoxButtons buttons = okCancel ? WinForms.MessageBoxButtons.OKCancel : WinForms.MessageBoxButtons.OK;
                WinForms.MessageBoxIcon icon = warning ? WinForms.MessageBoxIcon.Warning : WinForms.MessageBoxIcon.Information;
                WinForms.DialogResult r = owner == IntPtr.Zero
                    ? WinForms.MessageBox.Show(text, caption, buttons, icon, WinForms.MessageBoxDefaultButton.Button1, rtl)
                    : WinForms.MessageBox.Show(new Owner(owner), text, caption, buttons, icon, WinForms.MessageBoxDefaultButton.Button1, rtl);
                return r == WinForms.DialogResult.OK;
            }
            finally
            {
                try { if (modeless) shell.EnableModeless(1); } catch { }
            }
        }
    }
}
