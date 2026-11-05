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
        Metadata = new(
            id: "DsViz.VisualStudio.Toolbar.6f3c1c0e-4a52-4e7e-9d1b-5b2f0c8e7a11",
            version: ExtensionAssemblyVersion,
            publisherName: "Moti Levi",
            displayName: "DSViz — מבני נתונים",
            description: "סרגל כלים: תרגיל חדש, הוספת מבנה נתונים, מאגר שאלות, שליחת תרגיל, הצעה לפתרון והרצה ויזואלית."),
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
