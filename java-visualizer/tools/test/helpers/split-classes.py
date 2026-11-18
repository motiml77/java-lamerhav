# מפצל קובץ Java עם כמה מחלקות לקובץ לכל מחלקה (public class X ב-X.java) — כמו פרויקט של תלמיד:
#   python3 split-classes.py <קובץ.java> <תיקייה>
import re, sys
src = open(sys.argv[1], encoding='utf-8').read()
for part in re.split(r'\n(?=(?:public )?(?:abstract )?class )', src):
    m = re.search(r'^(?:public )?(?:abstract )?class (\w+)', part, re.M)
    if not m: continue
    body = part[m.start():].rstrip()
    if not body.startswith('public'): body = 'public ' + body
    open('%s/%s.java' % (sys.argv[2], m.group(1)), 'w', encoding='utf-8').write(body + '\n')
