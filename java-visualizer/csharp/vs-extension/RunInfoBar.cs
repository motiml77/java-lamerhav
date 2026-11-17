using System;
using System.Collections.Generic;
using Microsoft.VisualStudio.Imaging;
using Microsoft.VisualStudio.Shell;
using Microsoft.VisualStudio.Shell.Interop;

namespace DsViz.VsExtension
{
    /// <summary>
    /// שורת הודעה (InfoBar) בראש החלון הראשי של Visual Studio: לא חוסמת, לא גונבת פוקוס, נסגרת ב-X. משמשת את StartupFollower להסביר
    /// שההרצה הרגילה מריצה פרויקט אחר מהקובץ הפתוח, עם כפתור "הרצה ויזואלית של הקובץ". שורה אחת בכל פעם: הודעה חדשה מחליפה את הקודמת,
    /// ואותה הודעה בדיוק (אותו קובץ ואותו פרויקט) לא מוצגת פעמיים. כל כשל שקט — בלי הודעה, Visual Studio ממשיך כרגיל.
    /// </summary>
    internal sealed class RunInfoBar : IVsInfoBarUIEvents
    {
        readonly IServiceProvider _services;
        IVsInfoBarUIElement _element;
        uint _cookie;
        string _key;
        Action[] _actions = new Action[0];

        public RunInfoBar(IServiceProvider services)
        {
            _services = services;
        }

        /// <summary>מציג את ההודעה. key — מזהה של ההודעה (קובץ + פרויקט): אם היא כבר מוצגת — לא מוסיפים עוד אחת</summary>
        public void Show(string key, string text, string[] buttons, Action[] actions)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            try
            {
                if (_element != null && key == _key) return;
                Close();
                var shell = _services.GetService(typeof(SVsShell)) as IVsShell;
                var factory = _services.GetService(typeof(SVsInfoBarUIFactory)) as IVsInfoBarUIFactory;
                if (shell == null || factory == null) return;
                object hostObject;
                if (shell.GetProperty((int)__VSSPROPID7.VSSPROPID_MainWindowInfoBarHost, out hostObject) != 0) return;
                var host = hostObject as IVsInfoBarHost;
                if (host == null) return;
                var items = new List<IVsInfoBarActionItem>();
                for (int i = 0; i < buttons.Length; i++) items.Add(new InfoBarButton(buttons[i], i));
                var model = new InfoBarModel(new IVsInfoBarTextSpan[] { new InfoBarTextSpan("DSViz: " + text) }, items, KnownMonikers.StatusInformation, true);
                IVsInfoBarUIElement element = factory.CreateInfoBar(model);
                if (element == null) return;
                uint cookie;
                element.Advise(this, out cookie);
                host.AddInfoBar(element);
                _element = element;
                _cookie = cookie;
                _key = key;
                _actions = actions ?? new Action[0];
                SolutionHints.Log("info bar shown: " + text);
            }
            catch (Exception ex)
            {
                SolutionHints.Log("info bar failed: " + ex.Message, true);
            }
        }

        public void Close()
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            IVsInfoBarUIElement element = _element;
            if (element == null) return;
            Forget(element);
            try { element.Close(); } catch (Exception) { }
        }

        void Forget(IVsInfoBarUIElement element)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            if (!ReferenceEquals(element, _element)) return;
            try { element.Unadvise(_cookie); } catch (Exception) { }
            _element = null;
            _key = null;
            _actions = new Action[0];
        }

        public void OnClosed(IVsInfoBarUIElement infoBarUIElement)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            Forget(infoBarUIElement);
        }

        public void OnActionItemClicked(IVsInfoBarUIElement infoBarUIElement, IVsInfoBarActionItem actionItem)
        {
            ThreadHelper.ThrowIfNotOnUIThread();
            try
            {
                int index = actionItem != null && actionItem.ActionContext is int ? (int)actionItem.ActionContext : -1;
                Action[] actions = _actions;
                Close();   // הכפתור נלחץ — ההודעה עשתה את שלה
                if (index >= 0 && index < actions.Length) actions[index]();
            }
            catch (Exception ex)
            {
                SolutionHints.Log("info bar action failed: " + ex.Message, true);
            }
        }
    }
}
