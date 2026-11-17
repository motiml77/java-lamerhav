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
