using System;
using Microsoft.VisualStudio.Extensibility;
using Microsoft.VisualStudio.Extensibility.Commands;

namespace DsViz.VsExtension;

/// <summary>
/// DSViz ב-Visual Studio: סרגל כלים בפני עצמו (View ← Toolbars ← "DSViz — מבני נתונים"; מוצג בראש החלון) עם הכפתורים:
/// תרגיל חדש · הוספת מבנה נתונים · מאגר שאלות · שליחת תרגיל · הצעה לפתרון · הרצה ויזואלית · הדרכה.
/// כל כפתור מפעיל את הכלי של DSViz שבהתקנה (%LOCALAPPDATA%\DSViz) — אותם חלונות כמו בקיצורים של ההתקנה.
/// </summary>
[VisualStudioContribution]
internal class DsVizExtension : Extension
{
    public override ExtensionConfiguration ExtensionConfiguration => new()
    {
        RequiresInProcessHosting = true,   // extension בתוך התהליך של Visual Studio — מותקנת למשתמש, בלי הרשאות מנהל
        // Metadata — ב-source.extension.vsixmanifest (extension בתוך התהליך)
    };

    [VisualStudioContribution]
    public static ToolbarConfiguration Toolbar => new("%DsViz.Toolbar.DisplayName%")
    {
        Children = new[]
        {
            ToolbarChild.Command<NewExerciseCommand>(),
            ToolbarChild.Command<InsertCommand>(),
            ToolbarChild.Command<BankCommand>(),
            ToolbarChild.Command<SubmitCommand>(),
            ToolbarChild.Command<SolutionCommand>(),
            ToolbarChild.Command<RunCommand>(),
            ToolbarChild.Command<HelpCommand>(),
        },
    };
}
