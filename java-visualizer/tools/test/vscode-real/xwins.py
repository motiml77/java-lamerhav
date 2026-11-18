# מדפיס את שמות החלונות העליונים במסך X (שורה לכל חלון ממופה)
import sys
from Xlib import display, X
d = display.Display(sys.argv[1] if len(sys.argv) > 1 else None)
NET = d.intern_atom('_NET_WM_NAME'); UTF8 = d.intern_atom('UTF8_STRING')
def name(w):
    try:
        p = w.get_full_property(NET, UTF8)
        if p and p.value: return p.value.decode('utf-8', 'replace') if isinstance(p.value, bytes) else str(p.value)
        n = w.get_wm_name()
        return n.decode('latin-1') if isinstance(n, bytes) else n
    except Exception: return None
def walk(w, depth):
    try: kids = w.query_tree().children
    except Exception: return
    for c in kids:
        try: viewable = c.get_attributes().map_state == X.IsViewable
        except Exception: viewable = False
        n = name(c)
        if n and viewable: print(n)
        if depth < 2: walk(c, depth + 1)
walk(d.screen().root, 0)
