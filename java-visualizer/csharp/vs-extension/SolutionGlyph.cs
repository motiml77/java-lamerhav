using System;
using System.Collections.Generic;
using System.ComponentModel.Composition;
using System.IO;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Markup;
using System.Windows.Media;
using System.Windows.Shapes;
using Microsoft.VisualStudio.Shell;
using Microsoft.VisualStudio.Shell.Interop;
using Microsoft.VisualStudio.Text;
using Microsoft.VisualStudio.Text.Editor;
using Microsoft.VisualStudio.Text.Formatting;
using Microsoft.VisualStudio.Text.Tagging;
using Microsoft.VisualStudio.Utilities;

namespace DsViz.VsExtension
{
    // "הצעה לפתרון" בתוך הקוד (כמו ה-code mining ב-Eclipse והמנורה בשוליים ב-IntelliJ): מנורה בשולי האינדיקטורים (glyph margin)
    // ליד כל פעולה של סעיף בתרגיל ש-DSViz יצר ויש לה הצעה בחנות. לחיצה פותחת את חלון ההצעה, ממוקד באותה פעולה.
    // רכיב MEF באותו dll של ההרחבה (extension.vsixmanifest: Asset מסוג MefComponent). כל כשל שקט — לא מפיל את Visual Studio.

    /// <summary>התג של המנורה: ההצעה והקובץ שלה</summary>
    internal sealed class SolutionGlyphTag : IGlyphTag
    {
        public readonly Hint Hint;
        public readonly string File;

        public SolutionGlyphTag(Hint hint, string file)
        {
            Hint = hint;
            File = file;
        }
    }

    /// <summary>מי מסומן בקובץ: מחשב מחדש בכל שינוי בטקסט, ובכל פעם שהחנות של ההצעות משתנה</summary>
    internal sealed class SolutionTagger : ITagger<SolutionGlyphTag>, IDisposable
    {
        readonly ITextBuffer _buffer;
        readonly ITextDocumentFactoryService _docs;
        List<LocatedHint> _last = new List<LocatedHint>();
        int _lastVersion = -1;
        string _lastKey = "";
        string _loggedKey = "";

        public event EventHandler<SnapshotSpanEventArgs> TagsChanged;

        public SolutionTagger(ITextBuffer buffer, ITextDocumentFactoryService docs)
        {
            _buffer = buffer;
            _docs = docs;
            _buffer.Changed += OnBufferChanged;
            SolutionHints.StoreChanged += OnStoreChanged;
        }

        public void Dispose()
        {
            _buffer.Changed -= OnBufferChanged;
            SolutionHints.StoreChanged -= OnStoreChanged;
        }

        string FilePath()
        {
            try
            {
                ITextDocument doc;
                if (_docs != null && _docs.TryGetTextDocument(_buffer, out doc)) return doc.FilePath;
            }
            catch (Exception) { }
            return null;
        }

        void OnBufferChanged(object sender, TextContentChangedEventArgs e)
        {
            try
            {
                string before = _lastKey;
                Compute(e.After);
                if (before != _lastKey) RaiseAll(e.After);
            }
            catch (Exception) { }
        }

        void OnStoreChanged(object sender, EventArgs e)
        {
            try
            {
                _lastVersion = -1;
                ITextSnapshot snap = _buffer.CurrentSnapshot;
                Compute(snap);
                RaiseAll(snap);
            }
            catch (Exception) { }
        }

        void RaiseAll(ITextSnapshot snap)
        {
            var h = TagsChanged;
            if (h != null) h(this, new SnapshotSpanEventArgs(new SnapshotSpan(snap, 0, snap.Length)));
        }

        List<LocatedHint> Compute(ITextSnapshot snap)
        {
            lock (this)
            {
                if (snap.Version.VersionNumber == _lastVersion) return _last;
                var found = new List<LocatedHint>();
                string file = FilePath();
                if (file != null)
                {
                    List<Hint> hints = SolutionHints.ForFile(file);
                    if (hints.Count > 0) found = SolutionHints.Locate(snap.GetText(), hints);
                }
                var key = new System.Text.StringBuilder();
                foreach (LocatedHint l in found) key.Append(l.Hint.Key).Append('@').Append(l.Start).Append(';');
                _lastKey = key.ToString();
                _last = found;
                _lastVersion = snap.Version.VersionNumber;
                // ליומן: רק כשיש מנורות (קובץ רגיל לא כותב כלום), פעם אחת לכל תוצאה
                if (found.Count > 0 && _loggedKey != _lastKey)
                {
                    _loggedKey = _lastKey;
                    SolutionHints.Log("solution tags: " + found.Count + " in " + file);
                }
                else if (found.Count == 0 && SolutionHints.Trace && _loggedKey != "none:" + file)
                {
                    _loggedKey = "none:" + file;
                    SolutionHints.Log("solution tags: 0 in " + file);
                }
                return found;
            }
        }

        public IEnumerable<ITagSpan<SolutionGlyphTag>> GetTags(NormalizedSnapshotSpanCollection spans)
        {
            var result = new List<ITagSpan<SolutionGlyphTag>>();
            try
            {
                if (spans.Count == 0) return result;
                ITextSnapshot snap = spans[0].Snapshot;
                string file = FilePath();
                if (file == null) return result;
                foreach (LocatedHint l in Compute(snap))
                {
                    if (l.Start + l.Length > snap.Length) continue;
                    var span = new SnapshotSpan(snap, l.Start, l.Length);
                    if (spans.IntersectsWith(new NormalizedSnapshotSpanCollection(span)))
                        result.Add(new TagSpan<SolutionGlyphTag>(span, new SolutionGlyphTag(l.Hint, file)));
                }
            }
            catch (Exception ex)
            {
                SolutionHints.Log("GetTags failed: " + ex.Message, true);
            }
            return result;
        }
    }

    [Export(typeof(ITaggerProvider))]
    [Name("DsViz.SolutionHint.TaggerProvider")]
    [ContentType("CSharp")]
    [TagType(typeof(SolutionGlyphTag))]
    internal sealed class SolutionTaggerProvider : ITaggerProvider
    {
        static bool _logged;

        [Import]
        internal ITextDocumentFactoryService TextDocumentFactoryService { get; set; }

        public ITagger<T> CreateTagger<T>(ITextBuffer buffer) where T : ITag
        {
            try
            {
                if (buffer == null) return null;
                if (!_logged) { _logged = true; SolutionHints.Log("solution hints: MEF tagger provider active"); }
                SolutionHints.StartWatching();
                return new SolutionTagger(buffer, TextDocumentFactoryService) as ITagger<T>;
            }
            catch (Exception ex)
            {
                SolutionHints.Log("CreateTagger failed: " + ex.Message, true);
                return null;
            }
        }
    }

    [Export(typeof(IGlyphFactoryProvider))]
    [Name("DsViz.SolutionHint.GlyphFactory")]
    [Order(After = "VsTextMarker")]
    [ContentType("CSharp")]
    [TagType(typeof(SolutionGlyphTag))]
    internal sealed class SolutionGlyphFactoryProvider : IGlyphFactoryProvider
    {
        public IGlyphFactory GetGlyphFactory(IWpfTextView view, IWpfTextViewMargin margin)
        {
            SolutionHints.Log("solution hints: MEF glyph factory created");
            return new SolutionGlyphFactory();
        }
    }

    internal sealed class SolutionGlyphFactory : IGlyphFactory
    {
        public const string Title = "הצעה לפתרון";
        readonly HashSet<string> _logged = new HashSet<string>();

        public UIElement GenerateGlyph(IWpfTextViewLine line, IGlyphTag tag)
        {
            try
            {
                var t = tag as SolutionGlyphTag;
                if (t == null) return null;
                if (_logged.Add(t.File + "|" + t.Hint.Key)) SolutionHints.Log("solution glyph: " + t.Hint.Method + " in " + t.File);
                var button = new Button
                {
                    Content = Lamp(),
                    Width = 16,
                    Height = 16,
                    Padding = new Thickness(0),
                    BorderThickness = new Thickness(0),
                    Background = Brushes.Transparent,
                    Cursor = Cursors.Hand,
                    Focusable = false,
                    ToolTip = Title + " — " + t.Hint.Method + "()",
                    Template = PlainTemplate()
                };
                AutomationProperties.SetName(button, Title);
                AutomationProperties.SetAutomationId(button, "DsViz.SolutionHint." + t.Hint.Method);
                button.Click += (s, e) =>
                {
                    e.Handled = true;
                    Open(t);
                };
                return button;
            }
            catch (Exception ex)
            {
                SolutionHints.Log("GenerateGlyph failed: " + ex.Message, true);
                return null;
            }
        }

        /// <summary>כפתור בלי מסגרת ורקע של ערכת הנושא — רק המנורה</summary>
        static ControlTemplate PlainTemplate()
        {
            try
            {
                return (ControlTemplate)XamlReader.Parse(
                    "<ControlTemplate xmlns='http://schemas.microsoft.com/winfx/2006/xaml/presentation' TargetType='Button'>" +
                    "<ContentPresenter HorizontalAlignment='Center' VerticalAlignment='Center'/></ControlTemplate>");
            }
            catch (Exception) { return null; }
        }

        /// <summary>מנורה מצוירת ב-WPF (אמוג'י מוצג כריבוע ריק בגופנים של Windows): נורה צהובה, צוואר ובסיס</summary>
        static UIElement Lamp()
        {
            var c = new Canvas { Width = 14, Height = 14 };
            var bulb = new Ellipse { Width = 9, Height = 9, Fill = new SolidColorBrush(Color.FromRgb(0xFF, 0xC8, 0x3D)), Stroke = new SolidColorBrush(Color.FromRgb(0xB0, 0x82, 0x00)), StrokeThickness = 1 };
            Canvas.SetLeft(bulb, 2.5); Canvas.SetTop(bulb, 0.5);
            var neck = new Rectangle { Width = 5, Height = 2.5, Fill = new SolidColorBrush(Color.FromRgb(0xFF, 0xC8, 0x3D)), Stroke = new SolidColorBrush(Color.FromRgb(0xB0, 0x82, 0x00)), StrokeThickness = 0.8 };
            Canvas.SetLeft(neck, 4.5); Canvas.SetTop(neck, 8.5);
            var cap = new Rectangle { Width = 4, Height = 1.6, Fill = new SolidColorBrush(Color.FromRgb(0x6B, 0x72, 0x80)) };
            Canvas.SetLeft(cap, 5); Canvas.SetTop(cap, 11.4);
            c.Children.Add(bulb); c.Children.Add(neck); c.Children.Add(cap);
            return c;
        }

        static void Open(SolutionGlyphTag t)
        {
            try
            {
                SolutionHints.Log("solution glyph clicked: " + t.Hint.Method + " in " + t.File);
                if (!File.Exists(DsVizTool.Runner))
                {
                    ThreadHelper.ThrowIfNotOnUIThread();
                    VsShellUtilities.ShowMessageBox(ServiceProvider.GlobalProvider, "DSViz לא מותקן במחשב הזה — מריצים את DSViz-Setup.exe.", "DSViz",
                        OLEMSGICON.OLEMSGICON_INFO, OLEMSGBUTTON.OLEMSGBUTTON_OK, OLEMSGDEFBUTTON.OLEMSGDEFBUTTON_FIRST);
                    return;
                }
                string dir = System.IO.Path.GetDirectoryName(t.File) ?? "";
                DsVizTool.Start("--tool", "solution", dir, "--file", t.File, "--method", t.Hint.Method, "--params", t.Hint.ParamCount.ToString());
            }
            catch (Exception ex)
            {
                SolutionHints.Log("open solution failed: " + ex.Message, true);
            }
        }
    }
}
