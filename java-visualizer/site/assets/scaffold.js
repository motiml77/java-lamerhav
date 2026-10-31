// "שליחת תרגיל" באתר: בונה את קובצי ה-Java (או ה-C#) מהתשובה של השרת — העתקה מדויקת של AiScaffold.java
// (eclipse-plugin/src/il/lamerhav/dsviz). אותו קלט — אותו פלט, תו בתו (נבדק ב-tools/test/scaffold-parity.js).
// כללי Java שצריך לחקות: trim מוריד רק תווים עד רווח, \s הוא 6 תווים בלבד, $ מתאים גם לפני שורה חדשה אחרונה, split מוריד ריקים בסוף.
(function (root, factory) {
    if (typeof module === 'object' && module.exports) module.exports = factory();
    else root.DSVizScaffold = factory();
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    // ------------------------------------------------------------------ כללי Java
    const S = '[ \\t\\n\\x0B\\f\\r]';   // \s של Java
    const END = '(?=(?:\\r\\n|[\\n\\r\\u0085\\u2028\\u2029])?$)';   // $ של Java (בלי MULTILINE)
    const NOT_EOL = '[^\\n\\r\\u0085\\u2028\\u2029]';   // . של Java (בלי DOTALL)

    function trim(s) { return s.replace(/^[\x00-\x20]+|[\x00-\x20]+$/g, ''); }
    function isBlank(s) { return trim(s) === ''; }
    function replaceAllLit(s, from, to) { return s.split(from).join(to); }
    function quote(s) { return s.replace(/[\\^$.*+?()[\]{}|\/]/g, '\\$&'); }
    function jsplit(s, sep) {   // String.split של Java (בלי limit): בלי מחרוזות ריקות בסוף
        if (s.indexOf(sep) < 0) return [s];
        const a = s.split(sep);
        while (a.length && a[a.length - 1] === '') a.pop();
        return a;
    }
    function setOf(a) { return new Set(a); }

    const LIBRARY = setOf(['Stack', 'Queue', 'Node', 'BinNode', 'BinTreeNode']);
    const CS_KEYWORDS = setOf([
        'abstract', 'as', 'base', 'bool', 'break', 'byte', 'case', 'catch', 'char', 'checked', 'class', 'const', 'continue', 'decimal',
        'default', 'delegate', 'do', 'double', 'else', 'enum', 'event', 'explicit', 'extern', 'false', 'finally', 'fixed', 'float', 'for',
        'foreach', 'goto', 'if', 'implicit', 'in', 'int', 'interface', 'internal', 'is', 'lock', 'long', 'namespace', 'new', 'null', 'object',
        'operator', 'out', 'override', 'params', 'private', 'protected', 'public', 'readonly', 'ref', 'return', 'sbyte', 'sealed', 'short',
        'sizeof', 'stackalloc', 'static', 'string', 'struct', 'switch', 'this', 'throw', 'true', 'try', 'typeof', 'uint', 'ulong', 'unchecked',
        'unsafe', 'ushort', 'using', 'virtual', 'void', 'volatile', 'while', 'var', 'record', 'Main', 'Console', 'Math', 'String', 'Object', 'System']);
    const KEYWORDS = setOf([
        'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char', 'class', 'const', 'continue', 'default', 'do',
        'double', 'else', 'enum', 'extends', 'final', 'finally', 'float', 'for', 'goto', 'if', 'implements', 'import', 'instanceof',
        'int', 'interface', 'long', 'native', 'new', 'package', 'private', 'protected', 'public', 'return', 'short', 'static',
        'strictfp', 'super', 'switch', 'synchronized', 'this', 'throw', 'throws', 'transient', 'try', 'void', 'volatile', 'while',
        'true', 'false', 'null', 'var', 'record', 'yield', 'main', 'String', 'Object', 'System', 'Math', 'Integer']);
    const IDENT = /^[A-Za-z_][A-Za-z0-9_]*$/;
    // מזהה בקוד שהמודל כתב (גם בעברית — לפני שתוקן)
    const WORD_SRC = '[\\p{L}_$][\\p{L}\\p{N}_$]*';
    const WORD_FULL = new RegExp('^' + WORD_SRC + '$', 'u');
    const BEFORE_NAME = new RegExp('[\\p{L}\\p{N}_$.]', 'u');
    const TYPE = new RegExp('^[A-Za-z_][A-Za-z0-9_]*(' + S + '*<[A-Za-z0-9_<>,\\t\\n\\x0B\\f\\r \\[\\]?]*>)?(' + S + '*\\[[,\\t\\n\\x0B\\f\\r ]*\\])*$');
    const IMPORT = /^java\.util(\.[a-z]+)*\.([A-Z][A-Za-z0-9_]*|\*)$/;
    const USING = /^System(\.[A-Z][A-Za-z0-9]*)+$/;
    const MARK = '====' + rtl('כאן כותבים את התשובה') + '====';
    // רק לבדיקות: שורת return חשופה בסוף פעולה שמחזירה ערך
    let testReturns = false;

    function rtl(s) { return '‫' + s + '‬'; }

    // שורת הסימון בתוך הפעולה: כשיש כמה סעיפים — "כאן עונים סעיף א'", אחרת "כאן כותבים את התשובה"
    function markFor(t, several) {
        if (!several) return MARK;
        let l = t.label == null ? '' : trim(t.label);
        if (/^[א-ת]$/.test(l)) l += "'";
        return '====' + rtl('כאן עונים סעיף ' + l) + '====';
    }

    // ------------------------------------------------------------------ המודל של התרגיל

    function isCtor(t) { return t.kind === 'constructor'; }

    function newTask(cs) {
        return { cs: cs, label: '', kind: 'method', className: '', returnType: 'void', methodName: '', instructions: '',
                 solution: '', isStatic: true, params: [] };   // params: [{type, name}]
    }

    function newExercise() {
        return { cs: false, title: '', summary: '', topic: '', mainClass: '', mainCode: '', expectedOutput: '',
                 imports: [], classes: new Map(), tasks: [], given: [], others: [] };   // given: [{className, code}], others: [{label, text}]
    }

    // Json.obj / arr / str
    function get(o, k) { return o !== null && typeof o === 'object' && Object.prototype.hasOwnProperty.call(o, k) ? o[k] : undefined; }
    function jobj(o) { return o !== null && typeof o === 'object' && !Array.isArray(o) ? o : {}; }
    function jarr(o) { return Array.isArray(o) ? o : []; }
    function jstr(o) { return typeof o === 'string' ? o : ''; }

    // הערה לתלמיד מעל main: מה צריך להיות מודפס על המסך אם הפתרון נכון
    function expectedComment(expected) {
        const out = [];
        if (expected == null || isBlank(expected)) return out;
        out.push('    //');
        out.push('    // ' + rtl('אם פתרת נכון את התרגיל, צריך להיות מודפס על המסך:'));
        const lines = jsplit(expected, '\n');
        for (let i = 0; i < lines.length && i < 40; i++) out.push('    //     ' + rtl(replaceAllLit(lines[i], '\t', '    ')));
        if (lines.length > 40) out.push('    //     ...');
        return out;
    }

    const TRAIL_WS = new RegExp(S + '+' + END, 'g');
    function stripTrail(s) { return s.replace(TRAIL_WS, ''); }

    // קורא את התשובה של השרת (אובייקט אחרי JSON.parse) ומנקה אותה: שמות חוקיים, טיפוסים חוקיים, בלי כפילויות
    function read(j) {
        j = jobj(j);
        const e = newExercise();
        e.cs = jstr(get(j, 'language')) === 'csharp';
        const cs = e.cs;
        e.title = oneLine(jstr(get(j, 'title')), 120);
        e.summary = trim(jstr(get(j, 'summary')));
        e.topic = jstr(get(j, 'topic'));
        if (e.topic === '') e.topic = jstr(get(j, 'packageName'));
        e.mainCode = jstr(get(j, 'mainCode'));
        e.expectedOutput = stripTrail(replaceAllLit(jstr(get(j, 'expectedOutput')), '\r', ''));
        for (const o of jarr(get(j, 'imports'))) {
            const s = trim(trim(jstr(o)).replace(new RegExp('^(import|using)' + S + '+'), '').replace(new RegExp(';' + END), ''));
            const ok = cs ? USING.test(s) && !s.startsWith('System.Collections.Generic') : IMPORT.test(s);
            if (ok && e.imports.indexOf(s) < 0) e.imports.push(s);
        }
        // המחלקות מהתרגיל (לא של הספרייה)
        for (const o of jarr(get(j, 'classes'))) {
            const c = jobj(o);
            const name = ident(jstr(get(c, 'name')), '', cs);
            if (name === '' || LIBRARY.has(name) || e.classes.has(name)) continue;
            e.classes.set(name, jstr(get(c, 'code')));
        }
        let main = ident(jstr(get(j, 'mainClass')), 'Targil', cs);
        if (LIBRARY.has(main)) main = main + 'Targil';
        e.mainClass = main;

        const signatures = new Set();
        let n = 0;
        for (const o of jarr(get(j, 'tasks'))) {
            const m = jobj(o);
            const t = newTask(cs);
            n++;
            t.label = oneLine(jstr(get(m, 'label')), 20);
            if (t.label === '') t.label = String(n);
            t.kind = jstr(get(m, 'kind')) === 'constructor' ? 'constructor' : 'method';
            t.className = ident(jstr(get(m, 'className')), main, cs);
            if (LIBRARY.has(t.className)) t.className = main;
            if (t.className !== main && !e.classes.has(t.className)) {
                e.classes.set(t.className, '');   // מחלקה שהסעיף שייך אליה אבל לא תוארה — נוצרת, עם הערה להוסיף את התכונות מהתרגיל
            }
            t.isStatic = get(m, 'isStatic') !== false && !isCtor(t);
            if (t.className === main && !isCtor(t)) t.isStatic = true;   // במחלקה הראשית: פעולות סטטיות שנקראות מ-main
            t.instructions = trim(jstr(get(m, 'instructions')));
            const pnames = new Set();
            for (const po of jarr(get(m, 'parameters'))) {
                const p = jobj(po);
                let ty = type(jstr(get(p, 'type')), cs);
                if (ty == null) ty = 'int';
                let name = ident(jstr(get(p, 'name')), 'p' + (t.params.length + 1), cs);
                while (pnames.has(name)) name = name + '2';
                pnames.add(name);
                t.params.push({ type: ty, name: name });
            }
            if (isCtor(t)) {
                t.methodName = t.className;
                t.returnType = '';
            } else {
                t.methodName = ident(jstr(get(m, 'methodName')), (cs ? 'Task' : 'task') + n, cs);
                const rt = trim(jstr(get(m, 'returnType')));
                t.returnType = rt === '' ? 'void' : rt === 'void' ? 'void' : type(rt, cs);
                if (t.returnType == null) t.returnType = 'void';
                if (t.methodName === t.className) t.methodName = (cs ? 'Do' : 'do') + t.methodName;
            }
            // אותה חתימה פעמיים — שם אחר לשנייה
            let sig = t.className + '.' + t.methodName + '(' + paramTypes(t) + ')';
            for (let k = 2; signatures.has(sig); k++) {
                if (isCtor(t)) break;
                t.methodName = t.methodName.replace(/\d+$/, '') + k;
                sig = t.className + '.' + t.methodName + '(' + paramTypes(t) + ')';
            }
            if (signatures.has(sig)) continue;   // בנאי כפול
            signatures.add(sig);
            t.solution = fitSolution(t, jstr(get(m, 'solution')), trim(jstr(get(m, 'methodName'))));
            e.tasks.push(t);
        }
        for (const o of jarr(get(j, 'givenCode'))) {
            const g = jobj(o);
            const code = trim(jstr(get(g, 'code')));
            if (code === '') continue;
            let cls = ident(jstr(get(g, 'className')), main, cs);
            if (cls !== main && !e.classes.has(cls)) cls = main;
            e.given.push({ className: cls, code: code });
        }
        for (const o of jarr(get(j, 'otherQuestions'))) {
            const q = jobj(o);
            const text = trim(jstr(get(q, 'text')));
            if (text !== '') e.others.push({ label: oneLine(jstr(get(q, 'label')), 20), text: text });
        }
        if (e.classes.has(main)) {   // המחלקה הראשית תוארה כמחלקה בתרגיל — הקוד שלה הוא הבסיס
            const code = e.classes.get(main);
            e.classes.delete(main);
            e.classes.set(main, code);
        }
        return e;
    }

    // שם התיקייה של התרגיל: Java — ex01_stacks, C# — Ex01_Stacks. exists — השמות שכבר תפוסים (Set או מערך)
    function folderName(topic, exists, cs) {
        let t = topic == null ? '' : topic.toLowerCase().replace(/[^a-z0-9_]/g, '_').replace(/_+/g, '_').replace(/^[_0-9]+|_+$/g, '');
        if (t === '') t = 'targil';
        if (t.length > 30) t = t.substring(0, 30).replace(/_+$/, '');
        let max = 0;
        const num = new RegExp('^[eE][xX](\\d+)_' + NOT_EOL + '*$');
        const lower = new Set();
        for (const x of exists || []) {
            lower.add(x.toLowerCase());
            const m = num.exec(x);
            if (m) {
                const v = Number(m[1]);
                if (v > 2147483647) throw new Error('NumberFormatException: ' + m[1]);
                max = Math.max(max, v);
            }
        }
        if (cs) {
            let p = '';
            for (const w of t.split('_')) if (w !== '') p += w.charAt(0).toUpperCase() + w.substring(1);
            t = p;
        }
        for (let n = max + 1; ; n++) {
            const name = (cs ? 'Ex' : 'ex') + (n < 10 ? '0' : '') + n + '_' + t;
            if (!lower.has(name.toLowerCase())) return name;
        }
    }

    // ------------------------------------------------------------------ בניית הקבצים

    // pkg — החבילה (ב-Java). sources — שמות הקבצים ששלח התלמיד (לא בשימוש, כמו ב-Java).
    // withSolutions — בכל סעיף ההצעה לפתרון במקום הפעולה הריקה (לבדיקה בלבד)
    function build(e, pkg, sources, withSolutions) {
        const cs = e.cs;
        const out = [];
        const all = new Set(e.classes.keys());
        all.add(e.mainClass);
        // קודם המחלקה הראשית, אחר כך מחלקות התרגיל
        const order = [e.mainClass];
        for (const c of all) if (c !== e.mainClass) order.push(c);
        const firstTask = e.tasks.length === 0 ? null : e.tasks[0];
        for (const cls of order) {
            const isMain = cls === e.mainClass;
            let b = '';
            if (cs) b += 'using System;\nusing Unit4.CollectionsLib;\n';
            else b += 'package ' + pkg + ';\n\n' + 'import unit4.collectionsLib.*;\n';
            const code = e.classes.has(cls) ? e.classes.get(cls) : null;
            let uses = code == null ? '' : code;   // הקוד של הקובץ — כדי לייבא רק מה שבאמת בשימוש
            for (const g of e.given) if (g.className === cls) uses += '\n' + g.code;
            for (const t of e.tasks) {
                if (t.className !== cls) continue;
                uses += '\n' + t.returnType;
                for (const p of t.params) uses += ' ' + p.type;
            }
            if (isMain) uses += '\n' + e.mainCode;
            for (const imp of e.imports) {
                if (cs) {   // ב-C# מוסיפים לכל הקבצים
                    b += 'using ' + imp + ';\n';
                    continue;
                }
                const simple = imp.substring(imp.lastIndexOf('.') + 1);
                if (simple === '*' || new RegExp('\\b' + quote(simple) + '\\b').test(uses)) b += 'import ' + imp + ';\n';
            }
            b += '\n';

            const body = [];   // השורות שנכנסות לגוף המחלקה (מוזחות ב-4)
            let cursor = -1;
            for (const t of e.tasks) {
                if (t.className !== cls) continue;
                const at = body.length;
                const c = withSolutions ? solved(body, t) : stub(body, t, e.tasks.length > 1);
                if (t === firstTask) cursor = at + c;
            }
            let anyGiven = false;
            for (const g of e.given) {
                if (g.className !== cls) continue;
                if (!anyGiven) {
                    body.push('');
                    body.push('    // ---------- ' + rtl('נתון בתרגיל — אין צורך לשנות') + ' ----------');
                    anyGiven = true;
                }
                body.push('');
                for (const l of reindent(g.code, 4)) body.push(l);
            }
            const hasMain = code != null && (cs ? new RegExp('static' + S + '+(void|int)' + S + '+Main' + S + '*\\(')
                                                : new RegExp('static' + S + '+void' + S + '+main' + S + '*\\(')).test(code);
            if (isMain && !hasMain) {
                body.push('');
                body.push('');
                body.push('');
                body.push('    // ==================== ' + rtl('מכאן והלאה בדיקה של הפתרון') + ' ====================');
                body.push('    // ' + rtl('אין צורך לשנות — אפשר לשנות את הערכים כדי לבדוק מקרים נוספים'));
                for (const l of expectedComment(e.expectedOutput)) body.push(l);
                body.push('');
                if (cs) {
                    body.push('    public static void Main(string[] args)');
                    body.push('    {');
                } else body.push('    public static void main(String[] args) {');
                const mc = reindent(stripMainWrapper(e.mainCode, cs), 8);
                if (mc.length === 0) mc.push('        // ' + rtl('כאן בונים נתונים לדוגמה וקוראים לפעולות'));
                for (const l of mc) body.push(l);
                body.push('    }');
            }

            // הרכבה: קוד המחלקה מהתרגיל (או מחלקה ריקה) + הגוף לפני הסוגר האחרון
            const cls0 = classLines(cls, code, isMain, cs);
            let close = cls0.length - 1;
            while (close > 0 && !trim(cls0[close]).startsWith('}')) close--;
            for (let i = 0; i < close; i++) b += cls0[i] + '\n';
            if (close > 1 && !isBlank(cls0[close - 1]) && body.length > 0 && body[0] !== '') b += '\n';
            for (const l of body) b += l + '\n';
            for (let i = close; i < cls0.length; i++) b += cls0[i] + '\n';
            const text = tidy(b);
            let cur = -1;
            if (cursor >= 0) {   // הסמן: בשורה שמתחת לשורת הסימון הראשונה
                const ls = text.split('\n');
                const mk = '// ' + markFor(firstTask, e.tasks.length > 1);
                for (let i = 0; i < ls.length; i++) if (trim(ls[i]) === mk) { cur = i + 1; break; }
            }
            out.push({ className: cls, code: text, cursorLine: cur, cs: cs, fileName: cls + (cs ? '.cs' : '.java') });
        }
        return out;
    }

    // פעולה לסעיף אחד. מחזיר את מספר השורה (בתוך מה שנוסף) שבה מחכה הסמן
    function stub(out, t, several) {
        out.push('');
        if (t.cs) {
            out.push('    ' + header(t));
            out.push('    {');
        } else out.push('    ' + header(t) + ' {');
        out.push('        // ' + markFor(t, several));
        const cursor = out.length;
        out.push('        ');
        if (testReturns && !isCtor(t) && t.returnType !== 'void') out.push('        return ' + defaultValue(t.returnType) + ';');
        out.push('    }');
        return cursor;
    }

    // הפעולה עם ההצעה לפתרון (לבדיקה). סעיף בלי הצעה — פעולה ריקה שמחזירה ערך זמני
    function solved(out, t) {
        if (t.solution === '') {
            const keep = testReturns;
            testReturns = true;
            try {
                return stub(out, t, false);
            } finally {
                testReturns = keep;
            }
        }
        out.push('');
        for (const l of reindent(t.solution, 4)) out.push(l);
        return -1;
    }

    // ------------------------------------------------------------------ ההצעה לפתרון

    const FENCE = new RegExp('^' + S + '*```[A-Za-z#]*[ ]*\\n([\\s\\S]*?)' + S + '*```' + S + '*$');
    const NEW_BEFORE = new RegExp('\\bnew' + S + '*$');

    // ההצעה לפתרון של סעיף, עם הכותרת של הסעיף בדיוק; שמות שתוקנו מוחלפים גם בגוף. "" כשאין הצעה שאפשר להשתמש בה
    function fitSolution(t, code, nameInJson) {
        let c = code == null ? '' : replaceAllLit(replaceAllLit(code, '\r', ''), '\t', '    ');
        const fence = FENCE.exec(c);
        if (fence) c = fence[1];
        c = reindent(c, 0).join('\n');
        if (c === '' || c.indexOf('{') < 0) return '';
        const depth = new Int32Array(c.length + 1);
        const isCode = lex(c, depth);
        // הכותרת: השם של הפעולה ואחריו "(" — בעומק 0, לא בהערה ולא אחרי new או נקודה
        let name = -1, nameEnd = -1;
        const names = [t.methodName];
        if (nameInJson != null && WORD_FULL.test(nameInJson)) names.push(nameInJson);
        names.push(WORD_SRC);
        for (const n of names) {
            const generic = n.startsWith('[');
            const re = new RegExp('(' + (generic ? n : quote(n)) + ')' + S + '*\\(', 'gu');
            let m;
            while ((m = re.exec(c)) !== null) {
                const at = m.index;
                if (at > 0 && BEFORE_NAME.test(charBefore(c, at))) { re.lastIndex = at + 1; continue; }   // (?<![\p{L}\p{N}_$.])
                if (!isCode[at] || depth[at] !== 0 || NEW_BEFORE.test(c.substring(0, at))) continue;
                if (generic && (KEYWORDS.has(m[1]) || CS_KEYWORDS.has(m[1]))) continue;
                name = at;
                nameEnd = at + m[1].length;
                break;
            }
            if (name >= 0) break;
        }
        if (name < 0) return '';
        const open = c.indexOf('(', nameEnd);
        let close = -1, level = 0;
        for (let i = open; i < c.length; i++) {
            if (!isCode[i]) continue;
            if (c.charAt(i) === '(') level++;
            else if (c.charAt(i) === ')' && --level === 0) { close = i; break; }
        }
        if (close < 0) return '';
        let brace = -1;
        for (let i = close + 1; i < c.length; i++) {
            if (!isCode[i]) continue;
            const ch = c.charAt(i);
            if (ch === '{') { brace = i; break; }
            if (ch === ';' || ch === '}') return '';   // הכרזה בלי גוף
        }
        if (brace < 0) return '';
        let from = c.lastIndexOf('\n', name) + 1;
        for (let i = from; i < name; i++) if (isCode[i] && (c.charAt(i) === ';' || c.charAt(i) === '}')) from = i + 1;
        while (from < name && c.charAt(from) === ' ') from++;
        const lineStart = c.lastIndexOf('\n', from) + 1;
        const indent = isBlank(c.substring(lineStart, from)) ? c.substring(lineStart, from) : '';

        // שמות שצריך להחליף בגוף: הפעולה (אם המודל קרא לה אחרת) והפרמטרים (לפי הסדר)
        const rename = new Map();
        const oldName = c.substring(name, nameEnd);
        if (!isCtor(t) && oldName !== t.methodName) rename.set(oldName, t.methodName);
        const oldParams = paramNames(c.substring(open + 1, close));
        if (oldParams.length === t.params.length) {
            for (let i = 0; i < oldParams.length; i++)
                if (oldParams[i] !== t.params[i].name) rename.set(oldParams[i], t.params[i].name);
        }
        let rest = c.substring(brace + 1);
        if (rename.size > 0) rest = renameIdents(rest, isCode, brace + 1, rename);
        let b = c.substring(0, lineStart);
        b += indent + header(t);
        if (t.cs) b += '\n' + indent + '{';
        else b += ' {';
        b += rest;
        return rtlComments(reindent(b, 0)).join('\n');
    }

    // התו (נקודת הקוד) שלפני המקום at
    function charBefore(s, at) {
        const lo = s.charCodeAt(at - 1);
        if (lo >= 0xDC00 && lo <= 0xDFFF && at >= 2) {
            const hi = s.charCodeAt(at - 2);
            if (hi >= 0xD800 && hi <= 0xDBFF) return s.substring(at - 2, at);
        }
        return s.charAt(at - 1);
    }

    // הכותרת של הפעולה של הסעיף — בדיוק כמו במעטפת (בלי "{")
    function header(t) {
        let sig = 'public ';
        if (t.isStatic) sig += 'static ';
        if (!isCtor(t)) sig += t.returnType + ' ';
        sig += t.methodName + '(';
        for (let i = 0; i < t.params.length; i++) {
            if (i > 0) sig += ', ';
            sig += t.params[i].type + ' ' + t.params[i].name;
        }
        return sig + ')';
    }

    // סימון לכל תו: true — קוד (לא הערה, לא מחרוזת ולא תו); depth — עומק הסוגריים המסולסלים לפני התו
    function lex(c, depth) {
        const n = c.length;
        const code = new Uint8Array(n);
        let d = 0;
        for (let i = 0; i < n; i++) {
            const ch = c.charAt(i);
            depth[i] = d;
            if (ch === '/' && i + 1 < n && c.charAt(i + 1) === '/') {
                while (i < n && c.charAt(i) !== '\n') depth[i++] = d;
                if (i < n) { depth[i] = d; code[i] = 1; }
                continue;
            }
            if (ch === '/' && i + 1 < n && c.charAt(i + 1) === '*') {
                let end = c.indexOf('*/', i + 2);
                end = end < 0 ? n : end + 2;
                while (i < end) depth[i++] = d;
                i--;
                continue;
            }
            if (ch === '"' || ch === "'") {
                depth[i] = d;
                let j = i + 1;
                while (j < n && c.charAt(j) !== ch && c.charAt(j) !== '\n') {
                    if (c.charAt(j) === '\\') j++;
                    j++;
                }
                for (let k = i + 1; k <= Math.min(j, n - 1); k++) depth[k] = d;
                i = Math.min(j, n - 1);
                continue;
            }
            code[i] = 1;
            if (ch === '{') d++;
            else if (ch === '}') d = Math.max(0, d - 1);
        }
        depth[n] = d;
        return code;
    }

    const PARAM_NAME = new RegExp('(' + WORD_SRC + ')' + S + '*(\\[' + S + '*\\]' + S + '*)*' + END, 'u');

    // השמות של הפרמטרים ברשימה "Stack<Integer> s, int x" (גם "int a[]")
    function paramNames(list) {
        const out = [];
        if (isBlank(list)) return out;
        let level = 0, start = 0;
        for (let i = 0; i <= list.length; i++) {
            const ch = i < list.length ? list.charAt(i) : ',';
            if (ch === '<' || ch === '(' || ch === '[') level++;
            else if (ch === '>' || ch === ')' || ch === ']') level--;
            else if (ch === ',' && level <= 0) {
                const m = PARAM_NAME.exec(trim(list.substring(start, i)));
                out.push(m ? m[1] : '');
                start = i + 1;
            }
        }
        return out;
    }

    // החלפת שמות בקוד בלבד — לא בהערות ובמחרוזות, ולא אחרי נקודה. שם שכבר תפוס — לא מחליפים
    function renameIdents(s, isCode, offset, rename) {
        const used = new Set();
        const all = new RegExp(WORD_SRC, 'gu');
        let m;
        while ((m = all.exec(s)) !== null) if (isCode[offset + m.index]) used.add(m[0]);
        const ok = new Map();
        for (const [k, v] of rename) if (!used.has(v) || rename.has(v)) ok.set(k, v);
        let b = '', last = 0;
        const re = new RegExp(WORD_SRC, 'gu');
        while ((m = re.exec(s)) !== null) {
            const to = ok.get(m[0]);
            if (to === undefined || !isCode[offset + m.index] || (m.index > 0 && s.charAt(m.index - 1) === '.')) continue;
            b += s.substring(last, m.index) + to;
            last = m.index + m[0].length;
        }
        return b + s.substring(last);
    }

    const HEBREW_LINE = new RegExp('^' + NOT_EOL + '*[\\u0590-\\u05FF]' + NOT_EOL + '*$');

    // הערות "//" בעברית — עטופות בסימון כיוון
    function rtlComments(lines) {
        const out = [];
        for (const l of lines) {
            const depth = new Int32Array(l.length + 1);
            const isCode = lex(l, depth);
            let at = -1;
            for (let i = 0; i + 1 < l.length; i++) {
                if (l.charAt(i) === '/' && l.charAt(i + 1) === '/' && isCode.length > i && !isCode[i]
                    && (i === 0 || isCode[i - 1] || isBlank(l.substring(0, i)))) { at = i; break; }
            }
            if (at >= 0) {
                const text = trim(l.substring(at + 2));
                if (HEBREW_LINE.test(text) && text.indexOf('‫') < 0) {
                    out.push(l.substring(0, at) + '// ' + rtl(text));
                    continue;
                }
            }
            out.push(l);
        }
        return out;
    }

    // אחרי סבב תיקון: לוקחים רק את ההצעות לפתרון ואת הפלט הצפוי. מחזיר true אם הפלט הצפוי השתנה
    function takeSolutions(e, fixed) {
        for (let i = 0; i < e.tasks.length; i++) {
            const t = e.tasks[i];
            let f = null;
            for (const x of fixed.tasks)
                if (x.className === t.className && x.methodName === t.methodName && x.params.length === t.params.length) { f = x; break; }
            if (f == null && fixed.tasks.length === e.tasks.length) f = fixed.tasks[i];
            if (f != null && f.solution !== '') {
                const s = fitSolution(t, f.solution, f.methodName);
                if (s !== '') t.solution = s;
            }
        }
        const old = e.expectedOutput, now = stripTrail(replaceAllLit(fixed.expectedOutput, '\r', ''));
        if (now !== '') e.expectedOutput = now;
        return old !== e.expectedOutput;
    }

    // שורות ריקות מיותרות: בראש הקובץ, כפולות, אחרי "{" ולפני "}"
    function tidy(code) {
        const out = [];
        for (const l of code.split('\n')) {
            const t = trim(l);
            if (l === '' && (out.length === 0 || out[out.length - 1] === '' || out[out.length - 1].endsWith('{'))) continue;
            if (t.startsWith('}')) while (out.length > 0 && out[out.length - 1] === '') out.pop();
            out.push(l);
        }
        return out.join('\n');
    }

    function defaultValue(type) {
        switch (type) {
            case 'int': case 'long': case 'short': case 'byte': case 'double': case 'float': case 'decimal': return '0';
            case 'boolean': case 'bool': return 'false';
            case 'char': return "' '";
            default: return 'null';
        }
    }

    const NS_FILE = new RegExp('^namespace' + S + '+[\\w.]+' + S + '*;' + S + '*([\\s\\S]*)$');
    const NS_BLOCK = new RegExp('^namespace' + S + '+[\\w.]+' + S + '*\\{([\\s\\S]*)\\}$');
    const INTERNAL = new RegExp('^internal' + S + '+');

    // חלק מההערה שבמחלקה ריקה (לבדיקות)
    const ADD_FIELDS = 'הוסיפו כאן את התכונות שמתוארות בתרגיל';

    // ההערה בתוך מחלקה שסעיף שייך אליה, אבל המודל לא תיאר אותה (בלי קוד), או שתיאר אותה ריקה ("public class Book { }"):
    // המחלקה נוצרת בכל זאת — כדי שהכול יתקמפל — והתלמיד מתבקש להוסיף בה את התכונות שמתוארות בתרגיל
    function addFields(cls) {
        return 'המחלקה ' + cls + ' מהתרגיל — ' + ADD_FIELDS;
    }

    // קוד המחלקה מהתרגיל, מנוקה (בלי package/import/using/namespace), או מחלקה ריקה
    function classLines(cls, code, isMain, cs) {
        const lines = [];
        let c = code == null ? '' : replaceAllLit(code, '\r', '');
        let kept = '';
        for (const l of c.split('\n')) {
            const t = trim(l);
            if (t.startsWith('package ') || t.startsWith('import ') || (t.startsWith('using ') && t.endsWith(';'))) continue;
            kept += l + '\n';
        }
        c = trim(kept);
        if (cs) {   // namespace שעוטף את המחלקה — מורידים
            let ns = NS_FILE.exec(c);
            if (ns) c = trim(ns[1]);
            ns = NS_BLOCK.exec(c);
            if (ns) c = trim(ns[1]);
        }
        const open = cs ? 'public class ' + cls : 'public class ' + cls + ' {';
        // (?m)^ של Java: גם אחרי \u0085
        const re = new RegExp('(^|[\\n\\r\\u0085\\u2028\\u2029])' + S + '*(public' + S + '+|internal' + S + '+|abstract' + S + '+|final' + S + '+|sealed'
                              + S + '+|partial' + S + '+)*class' + S + '+' + quote(cls) + '\\b');
        const m = c === '' ? null : re.exec(c);
        if (c === '' || !m || c.lastIndexOf('}') < 0) {
            lines.push(open);
            if (cs) lines.push('{');
            if (c !== '') for (const l of reindent(c, 4)) lines.push(l);   // קוד בלי הכרזת מחלקה — עוטפים
            else if (!isMain) lines.push('    // ' + rtl(addFields(cls)));   // מחלקה שהסעיף שייך אליה אבל לא תוארה בתשובה
            lines.push('}');
            return lines;
        }
        c = trim(c.substring(m.index + m[1].length));
        if (cs) c = c.replace(INTERNAL, '');
        if (!c.startsWith('public')) c = 'public ' + c;
        const brace = c.indexOf('{');
        if (!isMain && brace > 0 && c.endsWith('}') && isBlank(c.substring(brace + 1, c.length - 1))) {
            // מחלקה ריקה ("public class Book { }") — כמו מחלקה שלא תוארה: עם ההערה להוסיף את התכונות מהתרגיל
            const head = trim(c.substring(0, brace)).replace(new RegExp(S + '+', 'g'), ' ');
            lines.push(cs ? head : head + ' {');
            if (cs) lines.push('{');
            lines.push('    // ' + rtl(addFields(cls)));
            lines.push('}');
            return lines;
        }
        for (const l of reindent(c, 0)) lines.push(l);
        return lines;
    }

    // אם המודל שלח main שלם במקום רק את הגוף — לוקחים את הגוף
    function stripMainWrapper(code, cs) {
        const c = code == null ? '' : trim(code);
        const m = new RegExp('^(public' + S + '+)?static' + S + '+void' + S + '+' + (cs ? 'Main' : 'main') + S + '*\\([^)]*\\)' + S + '*\\{([\\s\\S]*)\\}' + END).exec(c);
        return m ? m[2] : c;
    }

    // ------------------------------------------------------------------ עזרים

    // מזיח את כל השורות: מוריד את ההזחה המשותפת ומוסיף indent רווחים
    function reindent(code, indent) {
        const res = [];
        if (code == null) return res;
        const lines = replaceAllLit(replaceAllLit(code, '\r', ''), '\t', '    ').split('\n');
        let from = 0, to = lines.length;
        while (from < to && isBlank(lines[from])) from++;
        while (to > from && isBlank(lines[to - 1])) to--;
        let min = Infinity;
        for (let i = from; i < to; i++) {
            if (isBlank(lines[i])) continue;
            let k = 0;
            while (k < lines[i].length && lines[i].charAt(k) === ' ') k++;
            min = Math.min(min, k);
        }
        if (min === Infinity) min = 0;
        // המודל לפעמים שולח את השורה הראשונה בלי הזחה (חתוכה) ואת כל השאר מוזחות
        let restMin = Infinity;
        for (let i = from + 1; i < to; i++) {
            if (isBlank(lines[i])) continue;
            let k = 0;
            while (k < lines[i].length && lines[i].charAt(k) === ' ') k++;
            restMin = Math.min(restMin, k);
        }
        const firstCut = to - from > 1 && restMin !== Infinity && restMin > min && min === 0 && !trim(lines[from]).startsWith('}');
        const pad = ' '.repeat(indent);
        for (let i = from; i < to; i++) {
            const l = lines[i];
            const strip = firstCut && i > from ? restMin : min;
            res.push(isBlank(l) ? '' : pad + stripTrail(l.substring(Math.min(strip, l.length))));
        }
        return res;
    }

    function ident(s, fallback, cs) {
        const t = s == null ? '' : trim(s);
        if (!IDENT.test(t) || (cs ? CS_KEYWORDS : KEYWORDS).has(t) || t.length > 60) return fallback;
        return t;
    }

    const WS_RUN = new RegExp(S + '+', 'g');
    const AROUND_PUNCT = new RegExp(S + '*([<>,\\[\\]])' + S + '*', 'g');

    // טיפוס חוקי (כולל גנריות ומערכים) בשפה שנבחרה, או null
    function type(s, cs) {
        let t = s == null ? '' : replaceAllLit(trim(s).replace(WS_RUN, ' ').replace(AROUND_PUNCT, '$1'), ',', ', ');
        if (t === '' || !TYPE.test(t)) return null;
        let depth = 0;
        for (const c of t) {
            if (c === '<') depth++;
            if (c === '>' && --depth < 0) return null;
        }
        if (depth !== 0) return null;
        if (cs) {   // שמות של Java שנכנסו בטעות — לשמות של C#
            return t.replace(/\bboolean\b/g, 'bool').replace(/\bString\b/g, 'string').replace(/\bInteger\b/g, 'int')
                    .replace(/\bCharacter\b/g, 'char').replace(/\bDouble\b/g, 'double').replace(/\bBoolean\b/g, 'bool');
        }
        if (t.indexOf(',]') >= 0) return null;   // [,] — רק ב-C#
        t = t.replace(/\bbool\b/g, 'boolean').replace(/\bstring\b/g, 'String');
        // טיפוסים פרימיטיביים בתוך גנריות — מחליפים לעוטפים
        return t.replace(/<int>/g, '<Integer>').replace(/<char>/g, '<Character>').replace(/<double>/g, '<Double>').replace(/<boolean>/g, '<Boolean>');
    }

    const GENERIC = new RegExp('<' + NOT_EOL + '*>', 'g');

    function paramTypes(t) {
        let b = '';
        for (const p of t.params) b += p.type.replace(GENERIC, '') + ',';
        return b;
    }

    function oneLine(s, max) {
        const t = s == null ? '' : trim(s.replace(WS_RUN, ' '));
        return t.length > max ? t.substring(0, max) : t;
    }

    return {
        LIBRARY: LIBRARY,
        MARK: MARK,
        ADD_FIELDS: ADD_FIELDS,
        markFor: markFor,
        rtl: rtl,
        read: read,
        folderName: folderName,
        build: function (e, pkg, sources, withSolutions) { return build(e, pkg, sources, !!withSolutions); },
        fitSolution: fitSolution,
        takeSolutions: takeSolutions,
        header: header,
        get testReturns() { return testReturns; },
        set testReturns(v) { testReturns = !!v; }
    };
});
