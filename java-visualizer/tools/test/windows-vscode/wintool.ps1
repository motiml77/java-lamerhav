# כלי עזר ל-vscode-win.js (Windows): לחיצת עכבר אמיתית, רשימת החלונות (גם מוסתרים), מי מקדימה, צילום מסך
#   wintool.ps1 click <x> <y>            — לחיצה אמיתית (SetCursorPos + mouse_event) במיקום על המסך
#   wintool.ps1 windows                   — שורה לכל חלון עליון עם כותרת: pid<TAB>process<TAB>visible<TAB>x,y,w,h<TAB>title
#   wintool.ps1 front <regex>             — החלון הגלוי הראשון שהכותרת שלו מתאימה: "FOUND pid title" + מי נמצא באמצע שלו (WindowFromPoint)
#   wintool.ps1 activate <regex>          — מביא את החלון לקדמה (לפני לחיצה בתוך VS Code)
#   wintool.ps1 shot <file.png>           — צילום של כל המסך
param([string]$Cmd, [string]$A1, [string]$A2)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class W {
    public delegate bool EnumProc(IntPtr h, IntPtr l);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc f, IntPtr l);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern IntPtr WindowFromPoint(POINT p);
    [DllImport("user32.dll")] public static extern IntPtr GetAncestor(IntPtr h, uint f);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, UIntPtr e);
    [DllImport("user32.dll")] public static extern void keybd_event(byte k, byte s, uint f, UIntPtr e);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
}
'@
function All() {
    $out = New-Object System.Collections.Generic.List[object]
    [W]::EnumWindows({ param($h, $l)
        $sb = New-Object Text.StringBuilder 512
        [void][W]::GetWindowText($h, $sb, 512)
        if ($sb.Length -gt 0) {
            $p = [uint32]0; [void][W]::GetWindowThreadProcessId($h, [ref]$p)
            $r = New-Object W+RECT; [void][W]::GetWindowRect($h, [ref]$r)
            $name = try { (Get-Process -Id $p -ErrorAction Stop).ProcessName } catch { '?' }
            $out.Add([pscustomobject]@{ H = $h; Pid = $p; Proc = $name; Visible = [W]::IsWindowVisible($h); R = $r; Title = $sb.ToString() })
        }
        return $true }, [IntPtr]::Zero) | Out-Null
    return $out
}
switch ($Cmd) {
    'click' {
        [void][W]::SetCursorPos([int]$A1, [int]$A2); Start-Sleep -Milliseconds 150
        [W]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 60; [W]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
        "CLICK $A1,$A2"
    }
    'windows' {
        foreach ($w in All) { "{0}`t{1}`t{2}`t{3},{4},{5},{6}`t{7}" -f $w.Pid, $w.Proc, $w.Visible, $w.R.L, $w.R.T, ($w.R.R - $w.R.L), ($w.R.B - $w.R.T), $w.Title }
    }
    'front' {
        $w = All | Where-Object { $_.Visible -and $_.Title -match $A1 -and ($_.R.R - $_.R.L) -gt 50 } | Select-Object -First 1
        if (-not $w) { 'NONE'; break }
        $pt = New-Object W+POINT; $pt.X = [int](($w.R.L + $w.R.R) / 2); $pt.Y = [int](($w.R.T + $w.R.B) / 2)
        $top = [W]::GetAncestor([W]::WindowFromPoint($pt), 2)
        $tp = [uint32]0; [void][W]::GetWindowThreadProcessId($top, [ref]$tp)
        $fg = [W]::GetForegroundWindow(); $fp = [uint32]0; [void][W]::GetWindowThreadProcessId($fg, [ref]$fp)
        "FOUND $($w.Pid) center-pid=$tp foreground-pid=$fp $($w.Title)"
    }
    'activate' {
        $w = All | Where-Object { $_.Visible -and $_.Title -match $A1 } | Select-Object -First 1
        if ($w) {
            # Alt קצר — Windows נותן לתהליך שקיבל קלט להעביר חלון לקדמה
            [W]::keybd_event(0x12, 0, 0, [UIntPtr]::Zero); [W]::keybd_event(0x12, 0, 2, [UIntPtr]::Zero)
            [void][W]::ShowWindow($w.H, 9); [void][W]::SetForegroundWindow($w.H); "ACTIVATED $($w.Title)"
        } else { 'NONE' }
    }
    'shot' {
        $b = [Windows.Forms.SystemInformation]::VirtualScreen
        $bmp = New-Object Drawing.Bitmap $b.Width, $b.Height
        $g = [Drawing.Graphics]::FromImage($bmp); $g.CopyFromScreen($b.Left, $b.Top, 0, 0, $bmp.Size)
        $bmp.Save($A1, [Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $bmp.Dispose(); "SHOT $A1"
    }
}
