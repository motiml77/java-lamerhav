#!/usr/bin/env python3
"""
מאגר התרגילים — גרסת C# לכל שאלה (ל-Visual Studio): מוסיף לכל קובץ שאלה את השדה "csharp" — {"question": ..., "exercise": ...} —
אותה שאלה, אותם ציורים, והתרגיל ב-C#: שמות פעולות באות גדולה (SumStack), Stack<int>, Console.WriteLine, str.Length, str[i],
סוגריים מסולסלים בשורה משלהם, ו-True/False בפלט (כמו ש-C# מדפיס).
שאלה שכבר יש בה "csharp" עם "manual": true — לא נדרסת (תיקון ידני).
הבדיקה (tools/test/bank-check.sh) מריצה את הגרסה של C# ב-dsviz-cs ומשווה את הפלט — ההמרה כאן היא רק נקודת ההתחלה.
    python3 tools/bank-cs.py [תיקיית המאגר]
"""
import json, os, re, sys

BANK = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'bank')

STRINGS = re.compile(r'"(?:[^"\\]|\\.)*"|\'(?:[^\'\\]|\\.)*\'')


def outside_strings(code, fn):
    """fn רק על הקוד שמחוץ למחרוזות ולתווים"""
    out, last = [], 0
    for m in STRINGS.finditer(code):
        out.append(fn(code[last:m.start()]))
        out.append(m.group(0))
        last = m.end()
    out.append(fn(code[last:]))
    return ''.join(out)


def cap(w):
    return w[0].upper() + w[1:]


TYPES = [(r'\bInteger\b', 'int'), (r'\bDouble\b', 'double'), (r'\bCharacter\b', 'char'), (r'\bBoolean\b', 'bool'),
         (r'\bboolean\b', 'bool'), (r'\bString\b', 'string')]


def type_cs(t):
    for a, b in TYPES:
        t = re.sub(a, b, t)
    return t


def code_cs(code, names):
    def conv(c):
        c = c.replace('System.out.println(', 'Console.WriteLine(').replace('System.out.print(', 'Console.Write(')
        c = re.sub(r'\bMath\.(max|min|abs|sqrt|pow)\(', lambda m: 'Math.' + cap(m.group(1)) + '(', c)
        c = re.sub(r'\.length\(\)', '.Length', c)
        c = re.sub(r'\.length\b', '.Length', c)
        c = re.sub(r'\.charAt\(([^()]+)\)', r'[\1]', c)
        c = re.sub(r'\.([a-z]\w*)\(', lambda m: '.' + cap(m.group(1)) + '(', c)            # s.push( → s.Push(
        for n in names:                                                                  # sumTo( → SumTo(
            c = re.sub(r'(?<![\w.])' + n + r'\(', cap(n) + '(', c)
        return type_cs(c)
    c = outside_strings(code, conv)
    return allman(c)


def allman(code):
    """סגנון C#: '{' של בלוק בשורה משלו (לא '{ ... }' בשורה אחת, ולא אתחול מערך)"""
    out = []
    for line in code.split('\n'):
        s = line.rstrip()
        if s.endswith('{') and not s.strip() == '{' and not re.search(r'=\s*\{$', s):
            ind = re.match(r'\s*', s).group(0)
            out.append(s[:-1].rstrip())
            out.append(ind + '{')
        elif re.match(r'\s*\}\s*else\b', s):                      # } else {  →  }\n else\n {
            ind = re.match(r'\s*', s).group(0)
            rest = s.strip()[1:].strip()
            out.append(ind + '}')
            if rest.endswith('{'):
                out.append(ind + rest[:-1].rstrip())
                out.append(ind + '{')
            else:
                out.append(ind + rest)
        else:
            out.append(s)
    return '\n'.join(out)


def text_cs(t, names):
    for n in names:
        t = re.sub(r'(?<![\w.])' + n + r'\b', cap(n), t)
    return type_cs(t)


def expected_cs(e):
    e = re.sub(r'\btrue\b', 'True', e)
    e = re.sub(r'\bfalse\b', 'False', e)
    e = re.sub(r'(?<![\d.])(\d+)\.0\b', r'\1', e)   # 5.0 → 5 (כך C# מדפיס double שלם)
    return e


def method_names(ex):
    names = set(t['methodName'] for t in ex['tasks'])
    for c in ex.get('classes', []):
        names.update(re.findall(r'\b(?:public|private|protected)\s+(?:static\s+)?[\w<>\[\]]+\s+([a-z]\w*)\s*\(', c['code']))
    return sorted(names, key=len, reverse=True)


def convert(q):
    ex = q['exercise']
    names = method_names(ex)
    cx = json.loads(json.dumps(ex))
    cx['language'] = 'csharp'
    for t in cx['tasks']:
        t['methodName'] = cap(t['methodName'])
        t['returnType'] = type_cs(t['returnType'])
        for p in t['parameters']:
            p['type'] = type_cs(p['type'])
        t['instructions'] = text_cs(t['instructions'], names)
        t['solution'] = code_cs(t['solution'], names)
    for c in cx.get('classes', []):
        c['code'] = code_cs(c['code'], names)
    cx['mainCode'] = code_cs(cx['mainCode'], names)
    cx['expectedOutput'] = expected_cs(cx['expectedOutput'])
    cq = json.loads(json.dumps(q['question']))
    if 'intro' in cq:
        cq['intro'] = text_cs(cq['intro'], names)
    for s in cq.get('sections', []):
        s['text'] = text_cs(s['text'], names)
    return {'question': cq, 'exercise': cx}


def main():
    n = 0
    for topic in sorted(os.listdir(BANK)):
        d = os.path.join(BANK, topic)
        if not os.path.isdir(d):
            continue
        for f in sorted(os.listdir(d)):
            if not f.endswith('.json'):
                continue
            p = os.path.join(d, f)
            q = json.load(open(p, encoding='utf-8'))
            if q.get('csharp', {}).get('manual'):
                continue
            q['csharp'] = convert(q)
            with open(p, 'w', encoding='utf-8') as o:
                json.dump(q, o, ensure_ascii=False, indent=2)
                o.write('\n')
            n += 1
    print('C#: %d שאלות' % n)


if __name__ == '__main__':
    main()
