# (נשמר ב-UTF-8 עם BOM — אחרת PowerShell של Windows קורא את העברית לא נכון)
# חלון צף קטן בפינה של המסך בזמן עדכון אוטומטי של DSViz — מימין לשמאל, לא לוקח את הפוקוס מהעבודה:
#   -State found     "נמצאה גרסה חדשה — מורידים ומתקינים ברקע" עם פס התקדמות נע — נעלם לבד אחרי -Seconds (ברירת מחדל 10)
#   -State waiting   "הגרסה החדשה ירדה — תותקן כשסוגרים את Eclipse" — נעלם לבד אחרי -Seconds (ברירת מחדל 10)
#   -State download  "מוריד עדכון ל-DSViz" עם אחוזים אמיתיים (פס מתמלא + "37%") — מתוך קובץ ההתקדמות (-ProgressFile); בלי כפתור סגירה, נשאר עד שההורדה נגמרת
#   -State updating  "DSViz מתעדכן…" עם אחוזים (שלבי ההתקנה, מתוך קובץ ההתקדמות) — בלי כפתור סגירה, נשאר עד שהתהליך שהפעיל אותו נגמר (או -Seconds)
#   -State done      "✓ DSViz עודכן" — נעלם לבד אחרי -Seconds (ברירת מחדל 8), או בלחיצה
#   -State vsready   "✓ סרגל הכלים של DSViz מוכן — יופיע כשפותחים את Visual Studio" (install-vsix.ps1 -WhenClosed, אחרי ש-Visual Studio נסגר)
# קובץ ההתקדמות (-ProgressFile, ‎%TEMP%\dsviz-progress.txt‎): שורה אחת "סוג אחוז" — ‎download 37‎ (update.ps1 כותב בזמן ההורדה),
# ‎install 55‎ (update.ps1 מתחיל ב-0, ו-setup.nsi מעדכן בכל שלב של ההתקנה), ‎ready / done / failed‎ (החלון נסגר). אחוז שלילי — לא ידוע (פס נע).
# update.ps1 מפעיל אותו ברקע (לא מחכה לו); setup.nsi מפעיל את "done" בסוף עדכון (/UPDATE).
param([ValidateSet('found', 'waiting', 'updating', 'done', 'vsready', 'download')][string]$State = 'updating', [int]$Seconds = 0, [int]$WatchPid = 0, [string]$Apps = '', [string]$ProgressFile = '')
$ErrorActionPreference = 'Stop'
# קורא את קובץ ההתקדמות (הכותב עדיין יכול לכתוב בזמן הקריאה). לא קיים / באמצע כתיבה — $null
function ReadProg {
    try {
        if (-not $ProgressFile -or -not (Test-Path -LiteralPath $ProgressFile)) { return $null }
        $fs = [IO.File]::Open($ProgressFile, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
        try { $txt = (New-Object IO.StreamReader($fs)).ReadToEnd().Trim() } finally { $fs.Close() }
        $w = $txt -split '\s+'
        if ($w.Count -ge 2 -and $w[1] -match '^-?\d+$') { return @{ kind = $w[0]; pct = [int]$w[1] } }
    } catch { }
    return $null
}
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
    $bar.Dock = 'Top'; $bar.Height = 3; $bar.BackColor = $(if ($State -eq 'done' -or $State -eq 'vsready') { $green } else { $blue })
    $f.Controls.Add($bar)

    $title = New-Object Windows.Forms.Label
    $title.AutoSize = $false; $title.Location = New-Object Drawing.Point(16, 16); $title.Size = New-Object Drawing.Size(290, 24)
    $title.Font = New-Object Drawing.Font($font, 11, [Drawing.FontStyle]::Bold); $title.ForeColor = $ink
    $title.RightToLeft = 'Yes'; $title.TextAlign = 'MiddleRight'
    $sub = New-Object Windows.Forms.Label
    $sub.AutoSize = $false; $sub.Location = New-Object Drawing.Point(16, 42); $sub.Size = New-Object Drawing.Size(290, 20)
    $sub.Font = New-Object Drawing.Font($font, 9); $sub.ForeColor = $muted
    $sub.RightToLeft = 'Yes'; $sub.TextAlign = 'MiddleRight'
    if ($State -eq 'done' -or $State -eq 'waiting' -or $State -eq 'vsready') {   # שתי שורות: מתי העדכון נכנס לתוקף
        $sub.Size = New-Object Drawing.Size(290, 36)
        $f.Height = 100
        $f.Location = New-Object Drawing.Point(($wa.Right - $f.Width - 16), ($wa.Bottom - $f.Height - 16))
    }
    if ($State -eq 'done') {
        $title.Text = [string][char]0x2713 + ' DSViz עודכן לגרסה החדשה'
        $sub.Text = 'נכנס לתוקף בפתיחה הבאה של Eclipse / Visual Studio / IntelliJ.'
    } elseif ($State -eq 'vsready') {
        $title.Text = [string][char]0x2713 + ' סרגל הכלים של DSViz מוכן'
        $sub.Text = 'הוא יופיע כשפותחים את Visual Studio.'
    } elseif ($State -eq 'waiting') {
        $title.Text = 'הגרסה החדשה של DSViz ירדה'
        $sub.Text = if ($Apps) { "היא תותקן לבד ברגע שייסגרו: $Apps" } else { 'היא תותקן לבד ברגע שסוגרים את Eclipse / Visual Studio.' }
    } elseif ($State -eq 'found') {
        $title.Text = 'נמצאה גרסה חדשה של DSViz'
        $sub.Text = 'מורידים אותה ברקע. היא תותקן לבד כשסוגרים את Eclipse / Visual Studio.'
        $pb = New-Object Windows.Forms.ProgressBar
        $pb.Style = 'Marquee'; $pb.MarqueeAnimationSpeed = 30
        $pb.Location = New-Object Drawing.Point(16, 70); $pb.Size = New-Object Drawing.Size(308, 6)
        $f.Controls.Add($pb)
    } else {
        # download / updating — אחוזים: "37%" בצד שמאל של הכותרת, ופס שמתמלא (עד שמגיע הנתון הראשון — פס נע)
        if ($State -eq 'download') {
            $title.Text = 'מוריד עדכון ל-DSViz'
            $sub.Text = 'ההורדה ברקע — אפשר להמשיך לעבוד.'
        } else {
            $title.Text = 'DSViz מתעדכן לגרסה חדשה'
            $sub.Text = 'ההתקנה ברקע, כדקה. אפשר להמשיך לעבוד.'
        }
        $title.Location = New-Object Drawing.Point(84, 16); $title.Size = New-Object Drawing.Size(222, 24)
        $pctLabel = New-Object Windows.Forms.Label
        $pctLabel.AutoSize = $false; $pctLabel.Location = New-Object Drawing.Point(16, 12); $pctLabel.Size = New-Object Drawing.Size(66, 28)
        $pctLabel.Font = New-Object Drawing.Font($font, 14, [Drawing.FontStyle]::Bold); $pctLabel.ForeColor = $blue
        $pctLabel.RightToLeft = 'No'; $pctLabel.TextAlign = 'MiddleLeft'; $pctLabel.Text = [string][char]0x2026
        $f.Controls.Add($pctLabel)
        $pb = New-Object Windows.Forms.ProgressBar
        $pb.Style = 'Marquee'; $pb.MarqueeAnimationSpeed = 30; $pb.Minimum = 0; $pb.Maximum = 100
        $pb.Location = New-Object Drawing.Point(16, 70); $pb.Size = New-Object Drawing.Size(308, 8)
        $f.Controls.Add($pb)
    }
    $close = New-Object Windows.Forms.Label
    $close.Text = [string][char]0x2715; $close.Location = New-Object Drawing.Point(312, 10); $close.Size = New-Object Drawing.Size(20, 20)
    $close.ForeColor = $muted; $close.Cursor = 'Hand'; $close.RightToLeft = 'No'
    $close.Add_Click({ $f.Close() })
    $progressState = ($State -eq 'download' -or $State -eq 'updating')
    $close.Visible = -not $progressState   # בהורדה ובהתקנה אין כפתור סגירה — ההתקדמות נשארת על המסך עד הסוף
    $f.Controls.AddRange(@($title, $sub, $close))
    if (-not $progressState) { foreach ($c in @($f, $title, $sub)) { $c.Add_Click({ $f.Close() }) } }

    $limit = if ($Seconds -gt 0) { $Seconds } elseif ($State -eq 'done' -or $State -eq 'vsready') { 8 } elseif ($progressState) { 7200 } else { 10 }
    $start = Get-Date
    $script:shown = -1; $script:target = -1; $script:since = 0
    $want = if ($State -eq 'download') { 'download' } else { 'install' }
    $t = New-Object Windows.Forms.Timer
    $t.Interval = 500
    $t.Add_Tick({
        if (((Get-Date) - $start).TotalSeconds -ge $limit) { $f.Close(); return }
        if ($WatchPid -gt 0 -and -not (Get-Process -Id $WatchPid -ErrorAction SilentlyContinue)) { $f.Close(); return }
        if ($progressState -and $ProgressFile) {
            $p = ReadProg
            if ($p) {
                # השלב הבא כבר התחיל (או שההורדה / ההתקנה נכשלה) — החלון הזה סיים את תפקידו
                if ($State -eq 'download' -and $p.kind -ne 'download') { $f.Close(); return }
                if ($State -eq 'updating' -and ($p.kind -eq 'done' -or $p.kind -eq 'failed')) { $f.Close(); return }
                if ($p.kind -eq $want -and $p.pct -ge 0 -and $p.pct -gt $script:target) { $script:target = $p.pct; $script:since = 0 }
            }
            if ($script:target -ge 0) {
                if ($State -eq 'download') {
                    $script:shown = $script:target   # הורדה — האחוז האמיתי
                } else {
                    # התקנה — האחוז האחרון שדווח; בין דיווח לדיווח (כמה שניות) מתקדמים לאט עד 8 נקודות, כדי שלא ייראה תקוע
                    $script:since++
                    if ($script:shown -lt $script:target) { $script:shown = [Math]::Min($script:target, [Math]::Max(0, $script:shown) + 3) }
                    elseif ($script:since -gt 4 -and ($script:since % 4) -eq 0 -and $script:shown -lt [Math]::Min(99, $script:target + 8)) { $script:shown++ }
                }
                if ($pb.Style -ne 'Continuous') { $pb.Style = 'Continuous' }
                $v = [Math]::Max(0, [Math]::Min(100, $script:shown))
                $pb.Value = $v
                $pctLabel.Text = "$v%"
            }
        }
    })
    $t.Start()
    [Windows.Forms.Application]::Run($f)
} catch {
    # בלי חלון (שרת, בלי שולחן עבודה) — לא נורא, העדכון עצמו ממשיך
}
