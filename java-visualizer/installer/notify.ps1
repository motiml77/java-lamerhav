# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# חלון צף קטן בפינה של המסך בזמן עדכון אוטומטי של DSViz — מימין לשמאל, לא לוקח את הפוקוס מהעבודה:
#   -State updating  "DSViz מתעדכן לגרסה חדשה…" עם פס התקדמות נע — נשאר עד שהתהליך שהפעיל אותו נגמר (או -Seconds)
#   -State done      "✓ DSViz עודכן" — נעלם לבד אחרי -Seconds (ברירת מחדל 8), או בלחיצה
# update.ps1 מפעיל אותו ברקע (לא מחכה לו); setup.nsi מפעיל את "done" בסוף עדכון (/UPDATE).
param([ValidateSet('updating', 'done')][string]$State = 'updating', [int]$Seconds = 0, [int]$WatchPid = 0)
$ErrorActionPreference = 'Stop'
try {
    Add-Type -AssemblyName System.Windows.Forms, System.Drawing
    Add-Type -ReferencedAssemblies System.Windows.Forms, System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Windows.Forms;
public class DsvizToast : Form {
    // לא לוקח פוקוס: לא מפריע למי שכותב קוד באותו רגע
    protected override bool ShowWithoutActivation { get { return true; } }
    protected override CreateParams CreateParams {
        get { CreateParams p = base.CreateParams; p.ExStyle |= 0x08000000 | 0x00000080 | 0x00000008; return p; }   // NOACTIVATE | TOOLWINDOW | TOPMOST
    }
    public DsvizToast() {
        FormBorderStyle = FormBorderStyle.None; ShowInTaskbar = false; TopMost = true;
        StartPosition = FormStartPosition.Manual; BackColor = Color.White;
        RightToLeft = RightToLeft.Yes; RightToLeftLayout = true; DoubleBuffered = true;
    }
    protected override void OnPaint(PaintEventArgs e) {
        base.OnPaint(e);
        using (Pen p = new Pen(Color.FromArgb(0xDF, 0xE3, 0xEA))) e.Graphics.DrawRectangle(p, 0, 0, Width - 1, Height - 1);
    }
}
'@
    $blue = [Drawing.Color]::FromArgb(0x2F, 0x6F, 0xED)
    $green = [Drawing.Color]::FromArgb(0x2E, 0x9E, 0x5B)
    $ink = [Drawing.Color]::FromArgb(0x1B, 0x1F, 0x27)
    $muted = [Drawing.Color]::FromArgb(0x5B, 0x63, 0x70)
    $font = 'Segoe UI'

    $f = New-Object DsvizToast
    $f.Size = New-Object Drawing.Size(340, 92)
    $wa = [Windows.Forms.Screen]::PrimaryScreen.WorkingArea
    $f.Location = New-Object Drawing.Point(($wa.Right - $f.Width - 16), ($wa.Bottom - $f.Height - 16))

    # פס דק בצבע בראש החלון
    $bar = New-Object Windows.Forms.Panel
    $bar.Dock = 'Top'; $bar.Height = 3; $bar.BackColor = $(if ($State -eq 'done') { $green } else { $blue })
    $f.Controls.Add($bar)

    $title = New-Object Windows.Forms.Label
    $title.AutoSize = $false; $title.Location = New-Object Drawing.Point(16, 16); $title.Size = New-Object Drawing.Size(290, 24)
    $title.Font = New-Object Drawing.Font($font, 11, [Drawing.FontStyle]::Bold); $title.ForeColor = $ink
    $title.RightToLeft = 'Yes'; $title.TextAlign = 'MiddleRight'
    $sub = New-Object Windows.Forms.Label
    $sub.AutoSize = $false; $sub.Location = New-Object Drawing.Point(16, 42); $sub.Size = New-Object Drawing.Size(290, 20)
    $sub.Font = New-Object Drawing.Font($font, 9); $sub.ForeColor = $muted
    $sub.RightToLeft = 'Yes'; $sub.TextAlign = 'MiddleRight'
    if ($State -eq 'done') {
        $title.Text = [string][char]0x2713 + ' DSViz עודכן לגרסה החדשה'
        $sub.Text = 'אפשר להמשיך לעבוד כרגיל.'
    } else {
        $title.Text = 'DSViz מתעדכן לגרסה חדשה…'
        $sub.Text = 'זה לוקח כדקה, ברקע. לא צריך לעשות כלום.'
        $pb = New-Object Windows.Forms.ProgressBar
        $pb.Style = 'Marquee'; $pb.MarqueeAnimationSpeed = 30
        $pb.Location = New-Object Drawing.Point(16, 70); $pb.Size = New-Object Drawing.Size(308, 6)
        $f.Controls.Add($pb)
    }
    $close = New-Object Windows.Forms.Label
    $close.Text = [string][char]0x2715; $close.Location = New-Object Drawing.Point(312, 10); $close.Size = New-Object Drawing.Size(20, 20)
    $close.ForeColor = $muted; $close.Cursor = 'Hand'; $close.RightToLeft = 'No'
    $close.Add_Click({ $f.Close() })
    $f.Controls.AddRange(@($title, $sub, $close))
    if ($State -eq 'done') { foreach ($c in @($f, $title, $sub)) { $c.Add_Click({ $f.Close() }) } }

    $limit = if ($Seconds -gt 0) { $Seconds } elseif ($State -eq 'done') { 8 } else { 1800 }
    $start = Get-Date
    $t = New-Object Windows.Forms.Timer
    $t.Interval = 500
    $t.Add_Tick({
        if (((Get-Date) - $start).TotalSeconds -ge $limit) { $f.Close(); return }
        if ($WatchPid -gt 0 -and -not (Get-Process -Id $WatchPid -ErrorAction SilentlyContinue)) { $f.Close() }
    })
    $t.Start()
    [Windows.Forms.Application]::Run($f)
} catch {
    # בלי חלון (שרת, בלי שולחן עבודה) — לא נורא, העדכון עצמו ממשיך
}
