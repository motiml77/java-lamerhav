# לחיצות מקלדת / עכבר אמיתיות (XTest) על חלון לפי השם: python3 xkeys.py <DISPLAY> <ביטוי לשם החלון> <מקש | CLICK:dx,dy> <כמה פעמים> [השהיה במילישניות]
#   לפני הלחיצות — לחיצת עכבר על אמצע החלון למעלה (מקבל פוקוס, כמו תלמיד). מדפיס את המיקום והגודל של החלון, או NOWIN
import sys, re, time
from Xlib import display, X, XK
from Xlib.ext import xtest
d = display.Display(sys.argv[1]); pat = re.compile(sys.argv[2]); key = sys.argv[3]; n = int(sys.argv[4]); gap = int(sys.argv[5]) / 1000.0 if len(sys.argv) > 5 else 0.25
NET = d.intern_atom('_NET_WM_NAME'); UTF8 = d.intern_atom('UTF8_STRING')
def name(w):
    try:
        p = w.get_full_property(NET, UTF8)
        if p and p.value: return p.value.decode('utf-8', 'replace') if isinstance(p.value, bytes) else str(p.value)
        nm = w.get_wm_name()
        return nm.decode('latin-1') if isinstance(nm, bytes) else nm
    except Exception: return None
def find(w, depth):
    try: kids = w.query_tree().children
    except Exception: return None
    for c in kids:
        try: viewable = c.get_attributes().map_state == X.IsViewable
        except Exception: viewable = False
        nm = name(c)
        if nm and viewable and pat.search(nm): return c
        if depth < 2:
            r = find(c, depth + 1)
            if r: return r
    return None
root = d.screen().root
w = find(root, 0)
if not w: print('NOWIN'); sys.exit(1)
g = w.get_geometry(); t = w.translate_coords(root, 0, 0)
x, y = -t.x, -t.y
print('WIN %d,%d %dx%d' % (x, y, g.width, g.height))
if n == 0: sys.exit(0)   # רק בודקים שהחלון פתוח
xtest.fake_input(d, X.MotionNotify, x=x + g.width // 2, y=y + 12); d.sync(); time.sleep(0.2)
xtest.fake_input(d, X.ButtonPress, 1); xtest.fake_input(d, X.ButtonRelease, 1); d.sync(); time.sleep(0.5)
try: w.set_input_focus(X.RevertToParent, X.CurrentTime); d.sync()
except Exception: pass
if key.startswith('CLICK:'):   # CLICK:dx,dy — לחיצות עכבר בנקודה בתוך החלון (dx שלילי — מהקצה הימני), למשל הכפתור "הבא"
    dx, dy = [int(v) for v in key[6:].split(',')]
    px = x + (g.width + dx if dx < 0 else dx); py = y + dy
    for i in range(n):
        xtest.fake_input(d, X.MotionNotify, x=px, y=py); xtest.fake_input(d, X.ButtonPress, 1); xtest.fake_input(d, X.ButtonRelease, 1); d.sync(); time.sleep(gap)
    print('CLICKED %d at %d,%d' % (n, px, py)); sys.exit(0)
kc = d.keysym_to_keycode(XK.string_to_keysym(key))
for i in range(n):
    xtest.fake_input(d, X.KeyPress, kc); xtest.fake_input(d, X.KeyRelease, kc); d.sync(); time.sleep(gap)
print('SENT %d %s' % (n, key))
