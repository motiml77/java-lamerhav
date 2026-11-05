using System;
using System.Diagnostics;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.VisualStudio.Extensibility;
using Microsoft.VisualStudio.Extensibility.Commands;
using Microsoft.VisualStudio.Extensibility.Shell;
using Microsoft.VisualStudio.RpcContracts.Commands;

namespace DsViz.VsExtension;

/// <summary>הכלים של DSViz שבהתקנה: dsviz-cs.exe --tool &lt;מצב&gt; (חלונות Swing) ו-dsviz-cs.exe &lt;קובץ&gt; (הרצה ויזואלית)</summary>
internal static class DsVizTools
{
    public static string InstallDir => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "DSViz");
    public static string Runner => Path.Combine(InstallDir, "cs", "dsviz-cs.exe");
    public static bool Installed => File.Exists(Runner);

    public static void Start(params string[] args)
    {
        // net472: אין ArgumentList — מרכאות לכל ארגומנט
        var parts = new System.Collections.Generic.List<string>();
        foreach (string a in args) parts.Add("\"" + a.Replace("\"", "\\\"") + "\"");
        var psi = new ProcessStartInfo(Runner, string.Join(" ", parts)) { UseShellExecute = false, CreateNoWindow = true };
        Process.Start(psi);
    }

    public static Task Missing(IClientContext context, CancellationToken ct) =>
        context.Extensibility.Shell().ShowPromptAsync("DSViz לא מותקן במחשב הזה — מריצים את DSViz-Setup.exe.", PromptOptions.OK, ct);
}

internal abstract class ToolCommand : Command
{
    protected abstract string Mode { get; }

    public override Task ExecuteCommandAsync(IClientContext context, CancellationToken cancellationToken)
    {
        if (!DsVizTools.Installed) return DsVizTools.Missing(context, cancellationToken);
        DsVizTools.Start("--tool", Mode, "");
        return Task.CompletedTask;
    }
}

[VisualStudioContribution]
internal class NewExerciseCommand : ToolCommand
{
    protected override string Mode => "new";
    public override CommandConfiguration CommandConfiguration => new("%DsViz.NewExercise.DisplayName%")
    {
        Icon = new(ImageMoniker.KnownValues.AddItem, IconSettings.IconAndText),
    };
}

[VisualStudioContribution]
internal class InsertCommand : ToolCommand
{
    protected override string Mode => "insert";
    public override CommandConfiguration CommandConfiguration => new("%DsViz.Insert.DisplayName%")
    {
        Icon = new(ImageMoniker.KnownValues.AddClass, IconSettings.IconAndText),
    };
}

[VisualStudioContribution]
internal class BankCommand : ToolCommand
{
    protected override string Mode => "bank";
    public override CommandConfiguration CommandConfiguration => new("%DsViz.Bank.DisplayName%")
    {
        Icon = new(ImageMoniker.KnownValues.FolderOpened, IconSettings.IconAndText),
    };
}

[VisualStudioContribution]
internal class SubmitCommand : ToolCommand
{
    protected override string Mode => "submit";
    public override CommandConfiguration CommandConfiguration => new("%DsViz.Submit.DisplayName%")
    {
        Icon = new(ImageMoniker.KnownValues.Upload, IconSettings.IconAndText),
    };
}

[VisualStudioContribution]
internal class SolutionCommand : ToolCommand
{
    protected override string Mode => "solution";
    public override CommandConfiguration CommandConfiguration => new("%DsViz.Solution.DisplayName%")
    {
        Icon = new(ImageMoniker.KnownValues.StatusInformation, IconSettings.IconAndText),
    };
}

[VisualStudioContribution]
internal class HelpCommand : ToolCommand
{
    protected override string Mode => "help";
    public override CommandConfiguration CommandConfiguration => new("%DsViz.Help.DisplayName%")
    {
        Icon = new(ImageMoniker.KnownValues.StatusHelp, IconSettings.IconAndText),
    };
}

/// <summary>הרצה ויזואלית של הקובץ הפתוח (ושאר קבצי הפרויקט שלו) — כמו "הרצה ויזואלית" בתפריט ההפעלה</summary>
[VisualStudioContribution]
internal class RunCommand : Command
{
    public override CommandConfiguration CommandConfiguration => new("%DsViz.Run.DisplayName%")
    {
        Icon = new(ImageMoniker.KnownValues.Run, IconSettings.IconAndText),
    };

    public override async Task ExecuteCommandAsync(IClientContext context, CancellationToken cancellationToken)
    {
        if (!DsVizTools.Installed)
        {
            await DsVizTools.Missing(context, cancellationToken);
            return;
        }
        var view = await context.GetActiveTextViewAsync(cancellationToken);
        string? file = view?.Uri is { IsFile: true } u ? u.LocalPath : null;
        if (file == null || !file.EndsWith(".cs", StringComparison.OrdinalIgnoreCase))
        {
            await context.Extensibility.Shell().ShowPromptAsync("פותחים קובץ C# של התרגיל (עם Main) ולוחצים שוב.", PromptOptions.OK, cancellationToken);
            return;
        }
        DsVizTools.Start(file);
    }
}
