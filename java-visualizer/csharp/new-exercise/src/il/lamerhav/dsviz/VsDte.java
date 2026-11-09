package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Visual Studio שכבר פתוח עם הפתרון: "שליחת תרגיל" מוסיפה את הפרויקט החדש דרך האוטומציה של Visual Studio (DTE), ופותחת בו את הקובץ —
 * בלי לגעת בקובץ ה-.sln מבחוץ (אז אין "File Modification Detected" / Reload) ובלי לפתוח את ה-.sln שוב (אז אין Visual Studio שני).
 * אותו מנגנון כמו מאגר התרגילים (BankVs.inOpenVs / openInRunningVs). ה-Visual Studio הנכון נמצא לפי נתיב הפתרון שפתוח בו
 * (טבלת האובייקטים הרצים של Windows — לא סתם "ה-Visual Studio הראשון"). לא Windows / לא נמצא — false, והקורא חוזר להתנהגות הרגילה.
 */
final class VsDte {
    private VsDte() {}

    static boolean windows() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }

    /** פועל רק ב-Windows, ולא בבדיקות שמבקשות לא לפתוח כלום (-Ddsviz.noOpen=true) */
    static boolean enabled() {
        return windows() && !Boolean.getBoolean("dsviz.noOpen");
    }

    /** מחפש ב-Running Object Table את כל מופעי Visual Studio ($d = זה שהפתרון שלו הוא DSVIZ_SLN) */
    private static final String FIND = "$ErrorActionPreference='Stop'; "
            + "Add-Type -TypeDefinition 'using System; using System.Collections.Generic; using System.Runtime.InteropServices; using System.Runtime.InteropServices.ComTypes; "
            + "public static class DsRot { "
            + "[DllImport(\"ole32.dll\")] static extern int GetRunningObjectTable(int r, out IRunningObjectTable t); "
            + "[DllImport(\"ole32.dll\")] static extern int CreateBindCtx(int r, out IBindCtx c); "
            + "public static List<object> Dtes() { List<object> res = new List<object>(); IRunningObjectTable rot; "
            + "if (GetRunningObjectTable(0, out rot) != 0) return res; IEnumMoniker en; rot.EnumRunning(out en); en.Reset(); IMoniker[] m = new IMoniker[1]; "
            + "while (en.Next(1, m, IntPtr.Zero) == 0) { IBindCtx c; CreateBindCtx(0, out c); string n; m[0].GetDisplayName(c, null, out n); "
            + "if (n != null && n.StartsWith(\"!VisualStudio.DTE\")) { object o; if (rot.GetObject(m[0], out o) == 0 && o != null) res.Add(o); } } return res; } }'; "
            + "$sln=[IO.Path]::GetFullPath($env:DSVIZ_SLN); $d=$null; "
            // כש-Visual Studio עסוק ("Working on it") הוא דוחה את הקריאה — מנסים שוב; אין אף Visual Studio פתוח — מיד
            + "for($i=0; $i -lt 6 -and -not $d; $i++){ $all=@([DsRot]::Dtes()); if($all.Count -eq 0){ break }; "
            + "foreach($o in $all){ try { if($o.Solution.IsOpen -and ([IO.Path]::GetFullPath($o.Solution.FullName) -eq $sln)){ $d=$o; break } } catch {} }; "
            + "if(-not $d){ Start-Sleep -Milliseconds 1500 } }; "
            + "if(-not $d){ Write-Output 'NO-VS-WITH-SOLUTION'; exit 3 }; ";

    /**
     * Visual Studio פתוח עם הפתרון sln: הוא עצמו מוסיף את csproj ושומר את הפתרון. true — הפרויקט נוסף ונשמר (בדקנו בקובץ ה-.sln).
     * false — אין Visual Studio עם הפתרון הזה / לא הצליח: אז הקורא רושם את הפרויקט בקובץ ה-.sln כרגיל.
     */
    static boolean add(Path sln, Path csproj) {
        if (!enabled() || sln == null) return false;
        String name = csproj.getFileName().toString().replaceFirst("\\.csproj$", "");
        String ps = FIND
                + "for($i=0; $i -lt 20; $i++){ try { $null=$d.Solution.AddFromFile($env:DSVIZ_ARG, $false); break } catch { Write-Output ('RETRY ' + $_); Start-Sleep -Milliseconds 1500 } }; "
                + "if($i -ge 20){ exit 4 }; "
                // שומרים, ובודקים בקובץ ה-.sln עצמו שהפרויקט נרשם (לא — קוד 6, והקורא רושם בקובץ)
                + "for($k=0; $k -lt 10; $k++){ try { $d.Solution.SaveAs($d.Solution.FullName) } catch { Write-Output ('SAVE ' + $_) }; Start-Sleep -Milliseconds 700; "
                + "if(([IO.File]::ReadAllText($sln)).Contains($env:DSVIZ_WANT)){ Write-Output ('OK add ' + $env:DSVIZ_NAME); exit 0 }; Start-Sleep -Milliseconds 800 }; "
                + "Write-Output 'NOT-SAVED'; exit 6";
        int code = powershell(ps, 90, "DSVIZ_SLN", sln.toAbsolutePath().toString(), "DSVIZ_ARG", csproj.toAbsolutePath().toString(),
                "DSVIZ_NAME", name, "DSVIZ_WANT", "\"" + name + "\"");
        return code == 0;
    }

    /**
     * פותח את הקובץ ב-Visual Studio שפתוח עם הפתרון (הסמן בשורה line, מ-1), מקדים את החלון, והופך את הפרויקט ל-Startup Project
     * (כדי שהכפתור "הרצה ויזואלית" בסרגל והכפתור הירוק יריצו את התרגיל החדש). true — נפתח.
     */
    static boolean open(File f, int line, Path sln, Path csproj) {
        if (!enabled() || sln == null) return false;
        String project = csproj.getFileName().toString().replaceFirst("\\.csproj$", "");
        String rel;
        try {
            rel = sln.toAbsolutePath().getParent().relativize(csproj.toAbsolutePath()).toString().replace('/', '\\');
        } catch (IllegalArgumentException e) {
            rel = csproj.toString();
        }
        String ps = FIND
                + "for($i=0; $i -lt 15; $i++){ try { "
                + "$null=$d.ItemOperations.OpenFile($env:DSVIZ_FILE); "
                + "if([int]$env:DSVIZ_LINE -gt 0){ $d.ActiveDocument.Selection.GotoLine([int]$env:DSVIZ_LINE, $true) }; "
                + "$d.MainWindow.Activate(); break } catch { Write-Output ('RETRY ' + $_); Start-Sleep -Milliseconds 1500 } }; if($i -ge 15){ exit 4 }; "
                // Startup Project: ב-PowerShell פרויקטי C# מגיעים בלי שם — קובעים לפי הנתיב בפתרון (UniqueName), ובודקים שנקלט
                + "for($j=0; $j -lt 20; $j++){ try { "
                + "try { $d.Solution.SolutionBuild.StartupProjects=$env:DSVIZ_REL } catch { Write-Output ('SB1 ' + $_) }; "
                + "$now=(@($d.Solution.SolutionBuild.StartupProjects) -join ','); "
                + "if($now -notlike ('*' + $env:DSVIZ_PROJECT + '*')){ try { $d.Solution.Properties.Item('StartupProject').Value=$env:DSVIZ_PROJECT } catch { Write-Output ('PROP ' + $_) }; $now=(@($d.Solution.SolutionBuild.StartupProjects) -join ',') }; "
                + "Write-Output ('STARTUP ' + $env:DSVIZ_REL + ' -> ' + $now); if($now -like ('*' + $env:DSVIZ_PROJECT + '*')){ exit 0 } "
                + "} catch { Write-Output ('START ' + $_) }; Start-Sleep -Milliseconds 1500 }; exit 0";
        int code = powershell(ps, 120, "DSVIZ_SLN", sln.toAbsolutePath().toString(), "DSVIZ_FILE", f.getAbsolutePath(),
                "DSVIZ_LINE", String.valueOf(line), "DSVIZ_PROJECT", project, "DSVIZ_REL", rel);
        return code == 0;
    }

    /** PowerShell עם התסריט מקודד (-EncodedCommand) ומשתני סביבה (שם, ערך, ...). הפלט — ביומן dsviz-vs-dte.txt בתיקייה הזמנית. קוד היציאה, או -1 */
    static int powershell(String script, int timeoutSec, String... env) {
        try {
            String enc = java.util.Base64.getEncoder().encodeToString(("$ProgressPreference='SilentlyContinue'; " + script).getBytes(StandardCharsets.UTF_16LE));
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc);
            for (int i = 0; i + 1 < env.length; i += 2) pb.environment().put(env[i], env[i + 1]);
            File log = new File(System.getProperty("java.io.tmpdir"), "dsviz-vs-dte.txt");
            try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(log, true), StandardCharsets.UTF_8)) {
                w.write("--- " + new java.util.Date() + " " + (env.length > 3 ? env[3] : "") + "\n");
            }
            pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log));
            Process p = pb.start();
            p.getOutputStream().close();
            if (!p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return -1;
            }
            int code = p.exitValue();
            try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(log, true), StandardCharsets.UTF_8)) {
                w.write("exit " + code + "\n");
            }
            return code;
        } catch (Exception e) {
            return -1;
        }
    }
}
