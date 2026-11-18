// "צור מחלקה" — המחולל של VS Code (classgen.js) והחלון שלו (createclass.js):
//   1. בדיקות: Student ב-Java וב-C#, תיקון טיפוסים, הודעות על קלט שגוי, ומה שהחלון מציג (Session).
//   2. כל מקרה מ-classgen-cases.txt לקובץ <out>/js/<n>.txt — classgen-tests.sh משווה ל-ClassGen.java.
//   3. לכל מקרה תקין: תיקייה <out>/compile/<n> עם המחלקה ועם Main שקורא לבנאי, לכל get ולכל set (ול-toString) —
//      classgen-tests.sh מקמפל ומריץ (javac מול dsviz.jar, csc מול Unit4.CollectionsLib.dll).
//   4. סריקת פרויקט (classgen-project): המחלקות שמוצעות ברשימה, ומחלקה חדשה עם שדות Student / Queue<Student> מתקמפלת מולן.
//   node classgen-test.js <classgen-cases.txt> <out> [classgen-project]
const fs = require('fs'), path = require('path'), assert = require('assert');
const ROOT = path.join(__dirname, '..', '..');
const G = require(path.join(ROOT, 'vscode-extension', 'classgen'));
const createclass = require(path.join(ROOT, 'vscode-extension', 'createclass'));

let checks = 0;
const eq = (want, got) => { checks++; assert.strictEqual(got, want); };
const ok = (b, what) => { checks++; assert(b, what); };
const opts = (lang, className, ...fields) => ({ lang, className, fields: fields.map(f => { const i = f.lastIndexOf(' '); return { type: f.slice(0, i), name: f.slice(i + 1) }; }) });
const firstError = o => G.check(o).errors[0] || '';

var ownRows = spec => spec.split('/').map(r => { const p = r.split('|'); return { name: p[0], sup: p.length > 1 ? p[1] : '', fields: p.length > 2 ? p[2] : '' }; });
var OWN = ['Vehicle||String model; int year/Bus|Vehicle|int seats/Taxi|vehicle|String company, boolean night/MiniBus|Bus|',
    'A||int x/a|A|int y', 'A|B|int x/B|A|int y', 'Node||int value/Thing|Ghost|', 'Box|Dog|int size', 'Q||int x;int 2y/R|Q|int x'];
var SPLITS = ['Rexy, male, 3.2', '  ', 'new Person("a, b"), "x,y", \',\', f(1, g(2, 3))', '"say \\"hi, you\\"", 2', 'a,,b'];
var OBJECTS = ['Animal|a1|Boxer|Rexy;male;3.2;3;house', 'Animal|a2|Labrador|Toto;male;2.5;4;new Person("Hila")',
    'Dog|d|Dog|"Dasty";female;0.5;3', 'Shape|s|Circle|red;2', 'Animal|a3|Animal|', 'Animal|zoo|[]4|Dog:Dasty;female;0.5;3//Boxer:Rexy;male;3.2;3;42/Animal:Bob',
    'Animal|x|Person|Hila', 'Shape|s|Shape|red', 'Animal|1x|Dog|', '|a|Dog|', 'Animal|zoo|[]40|', 'Animal|zoo|[]2|Person:Hila/Mammal:Numa;female;2',
    'Person|p|Person|He said "hi"'];

var SCHOOL_CS = 'using Unit4.CollectionsLib;\nusing Unit4.BinTreeUtilsLib;\nusing Unit4.BinTreeCanvasLib;\nusing Unit4.GridLib;\n';
var SCHOOL_JAVA = 'import unit4.collectionsLib.*;\n';
/** מה חייב להופיע בפלט של ה-main של כל היררכיה */
var OUTPUT = { animals: 'I am a Boxer', shapes: 'area=12', school: 'Homeroom teacher: ', own: ', seats=' };


// --- Student ב-Java (כמו ClassGenTest.java)
const s = Object.assign(opts('java', 'Student', 'String name', 'int grade', 'boolean active'), { toStr: true });
eq('public class Student {\n'
    + '    private String name;\n    private int grade;\n    private boolean active;\n\n'
    + '    public Student(String name, int grade, boolean active) {\n'
    + '        this.name = name;\n        this.grade = grade;\n        this.active = active;\n    }\n\n'
    + '    public String getName() {\n        return this.name;\n    }\n\n'
    + '    public void setName(String name) {\n        this.name = name;\n    }\n\n'
    + '    public int getGrade() {\n        return this.grade;\n    }\n\n'
    + '    public void setGrade(int grade) {\n        this.grade = grade;\n    }\n\n'
    + '    public boolean getActive() {\n        return this.active;\n    }\n\n'
    + '    public void setActive(boolean active) {\n        this.active = active;\n    }\n\n'
    + '    public String toString() {\n'
    + '        return "Student: name=" + this.name + ", grade=" + this.grade + ", active=" + this.active;\n    }\n}\n', G.generate(s));
// בלי toStr ובלי ctor: אין toString, ויש בנאי (ברירת מחדל)
ok(!G.generate(opts('java', 'A', 'int x')).includes('toString') && G.generate(opts('java', 'A', 'int x')).includes('public A(int x) {'), 'ברירות מחדל');
ok(!G.generate(Object.assign(opts('java', 'A', 'int x'), { ctor: false })).includes('public A('), 'בלי בנאי');
// C#: namespace X; וגם using רק כשיש מבנה של הקורס
const c = Object.assign(opts('cs', 'Student', 'string name', 'Queue<int> q'), { toStr: true, namespace: 'School', fileScoped: true });
ok(G.generate(c).startsWith('using Unit4.CollectionsLib;\n\nnamespace School;\n\npublic class Student\n{\n    private string name;\n    private Queue<int> q;\n'), G.generate(c));
ok(G.generate(c).includes('    public override string ToString()\n    {\n        return "Student: name=" + this.name + ", q=" + this.q;\n    }\n}\n'), G.generate(c));
// טיפוסים
eq('Queue<Integer>', G.normalizeType(' Queue < int > ', 'java'));
eq('Dictionary<string, int>', G.normalizeType('Dictionary<String, Integer>', 'cs'));
eq('int[,]', G.normalizeType('int[ , ]', 'cs'));
eq(null, G.normalizeType('int[,]', 'java'));
for (const bad of ['void', 'in t', 'List<>', 'Queue<int', 'int[', 'class', '3x', 'a-b', 'Stack<void>']) eq(null, G.normalizeType(bad, 'java'));
// הודעות
eq('כותבים שם למחלקה', firstError(opts('java', ' ', 'int x')));
eq('"class" היא מילה שמורה של Java — בוחרים שם אחר למחלקה', firstError(opts('java', 'class', 'int x')));
eq('"Queue" הוא שם של מחלקה מוכנה — בוחרים שם אחר למחלקה', firstError(opts('java', 'Queue', 'int x')));
eq('כבר יש מחלקה בשם Student (בקובץ Student.java) — בוחרים שם אחר', firstError(Object.assign(opts('java', 'student', 'int x'), { taken: { Student: 'Student.java' } })));
eq('מוסיפים לפחות שדה אחד (טיפוס ושם)', firstError(opts('java', 'A')));
eq('יש שני שדות בשם x', firstError(opts('java', 'A', 'int x', 'double x')));
eq('השדות name ו-Name יוצרים את אותה פעולה (GetName) — משנים שם לאחד מהם', firstError(opts('cs', 'A', 'int name', 'int Name')));
eq('השדה x: הטיפוס "void" לא תקין (למשל int, String, Queue<Integer>)', firstError(opts('java', 'A', 'void x')));
const w = G.check(opts('java', 'point', 'int x'));
ok(w.ok && w.className === 'Point' && w.warnings[0] === 'שם של מחלקה מתחיל באות גדולה — המחלקה תיקרא Point', JSON.stringify(w));
assert.throws(() => G.generate(opts('java', 'A', 'int 1x')), /1x/);
ok(G.preview(opts('java', '', 'int x', 'int 1y')).startsWith('public class MyClass {\n    private int x;\n\n'), 'תצוגה מקדימה');
// מה כבר יש בקובץ
eq('ex1.data', G.packageOf('// package nope;\npackage ex1.data;\n'));
assert.deepStrictEqual(G.namespaceOf('namespace My.App;\nclass P {}'), { name: 'My.App', fileScoped: true });
assert.deepStrictEqual(G.namespaceOf('namespace ConsoleApp1\n{\n}'), { name: 'ConsoleApp1', fileScoped: false });
eq(null, G.namespaceOf('class P {}'));
assert.deepStrictEqual(G.declaredTypes('/* class Hidden */ public class Main { String s = "class Fake"; }\nclass Helper {}\ninterface Shape {}\nenum Color { RED }'), ['Main', 'Helper', 'Shape', 'Color']);
eq(4, G.classLine('package a;\n\nimport x.*;\n\npublic class A {\n}\n'));

// --- import / using למחלקות מהפרויקט, והרשימה בקבוצות
{
    const known = [{ name: 'Student', pkg: 'school.model', file: 'school/model/Student.java' }, { name: 'Car', pkg: 'school.model', file: 'school/model/Car.java' }];
    const o = Object.assign(opts('java', 'Team', 'Student best', 'Queue<Student> line', 'Car[] cars'), { pkg: 'school.app', known });
    ok(G.generate(o).startsWith('package school.app;\n\nimport unit4.collectionsLib.*;\nimport school.model.Car;\nimport school.model.Student;\n\npublic class Team {'), G.generate(o));
    const oc = Object.assign(opts('cs', 'Team', 'Student best', 'Queue<Student> line', 'Shape s'), { namespace: 'School.App',
        known: [{ name: 'Student', pkg: 'School.Model', file: '' }, { name: 'Shape', pkg: 'Geo', file: '' }] });
    ok(G.generate(oc).startsWith('using Unit4.CollectionsLib;\nusing Geo;\nusing Student = School.Model.Student;\n\nnamespace School.App\n{'), G.generate(oc));
    const g0 = G.typeGroups('cs', []);
    assert.deepStrictEqual(g0.map(g => g.title), ['סוגים בסיסיים', 'מבני נתונים של הקורס', 'מחלקות שכבר קיימות בפרויקט', 'מערכים']);
    assert.deepStrictEqual(g0[1].items.map(i => i.type), ['Queue', 'Stack', 'Node', 'BinNode']);
    ok(g0[2].items.length === 0 && g0[2].empty === 'אין עדיין מחלקות אחרות בפרויקט', 'בלי מחלקות');
    eq('Queue<Integer>', G.fullType('Queue', '', 'java'));
    eq('Stack<Student>', G.fullType('Stack ', ' Student', 'java'));
    eq('Node<int>', G.fullType('Node', null, 'cs'));
    const g1 = G.typeGroups('java', known);
    assert.deepStrictEqual(g1[2].items.map(i => i.type), ['Car', 'Student']);
    assert.deepStrictEqual(g1[3].items.map(i => i.type), ['int[]', 'double[]', 'String[]', 'Car[]', 'Student[]']);
    assert.deepStrictEqual(G.elementGroups('java', known)[1].items.map(i => i.type), ['Car', 'Student']);
    checks += 4;
}

// --- מה שהחלון מציג (Session): שגיאה ראשונה, אזהרות, קוד, שם קובץ, כפתור מושבת
{
    const ses = new createclass.Session({ lang: 'java', pkg: 'ex1', taken: { Main: 'Main.java' }, dir: '/tmp/x',
        known: [{ name: 'Car', pkg: 'garage', file: 'garage/Car.java' }] });
    const init = ses.initial();
    ok(init.fields.length === 1 && init.fields[0].base === 'int' && init.fields[0].name === '' && init.ctor === true && init.toStr === false, JSON.stringify(init));
    let v = ses.view(init);
    ok(!v.ok && v.error === 'כותבים שם למחלקה' && v.code.startsWith('package ex1;\n\npublic class MyClass {'), JSON.stringify(v));
    v = ses.view({ className: 'student', fields: [{ type: 'String', name: 'name' }, { type: 'int', name: 'grade' }, { type: 'boolean', name: 'active' }], ctor: true, toStr: true });
    ok(v.ok && v.error === null && v.file === 'Student.java' && v.warnings.length === 2 && v.warnings[0].includes('תיקרא Student'), JSON.stringify(v));
    ok(v.code.includes('public boolean getActive()') && v.code.includes('public String toString()'), v.code);
    v = ses.view({ className: 'Main', fields: [{ type: 'int', name: 'x' }], ctor: true, toStr: false });
    ok(!v.ok && v.error.includes('כבר יש מחלקה בשם Main'), v.error);
    // שורה של החלון: Queue + Car (נבחרה מהרשימה, מהחבילה garage) ← Queue<Car> ו-import garage.Car
    v = ses.view({ className: 'Lot', fields: [{ base: 'Queue', elem: 'Car', name: 'cars', refs: { Car: 'garage' } }, { base: 'Car', elem: '', name: 'first', refs: {} }], ctor: true });
    ok(v.ok && v.code.startsWith('package ex1;\n\nimport unit4.collectionsLib.*;\nimport garage.Car;\n\npublic class Lot {\n    private Queue<Car> cars;\n    private Car first;'), v.code);
    v = ses.view({ className: 'Lot', fields: [{ base: 'Stack', elem: '', name: 's', refs: {} }], ctor: true });
    ok(v.code.includes('private Stack<Integer> s;'), 'בלי טיפוס בפנים — Integer');
    const html = createclass.html(createclass.options('java', ses.initial(), ses.known));
    ok(html.includes('dir="rtl"') && html.includes('צור מחלקה') && !html.includes('BinNode<Integer>'), 'הנתונים עוברים כ-JSON מוגן');
    ok(html.includes('"title":"מחלקות שכבר קיימות בפרויקט"') && html.includes('"type":"Car","hint":"","pkg":"garage"') && html.includes('"defaultElem":"Integer"'), 'הקבוצות בדף');
}

// --- סריקת הפרויקט (classgen-project): Student ו-Car מוצעים (גם כמערך ובתוך מבנה); Student כפול — עם ה-package באפור
const projectDir = process.argv[4];
function scanText(lang, known) {
    let t = known.map(k => k.name + '|' + k.pkg + '|' + k.file + ' # ' + G.modelText(k) + '\n').join('');
    for (const groups of [G.typeGroups(lang, known), G.elementGroups(lang, known)])
        for (const g of groups) {
            t += '# ' + g.title + '\n';
            if (!g.items.length) t += '(' + g.empty + ')\n';
            for (const i of g.items) t += i.type + '|' + i.hint + '\n';
        }
    return t;
}
const scans = {};
if (projectDir) for (const lang of ['java', 'cs']) {
    const known = G.scanProject(path.join(projectDir, lang), lang);
    scans[lang] = known;
    const text = scanText(lang, known);
    const M = lang === 'cs' ? 'School.Model' : 'school.model', O = lang === 'cs' ? 'School.Other' : 'school.other';
    ok(text.includes('\nCar|\n') && text.includes('\nCar[]|\n') && text.includes('\nStudent|' + M + '\n') && text.includes('\nStudent|' + O + '\n')
        && !/Ghost|Grade|InComment|InString/.test(text), text);
    // מה שהחלון מקבל מ-context: אותה סריקה, ה-package / namespace של הקובץ, ותיקיית הסריקה
    const file = lang === 'cs' ? path.join(projectDir, 'cs', 'Model', 'Car.cs') : path.join(projectDir, 'java', 'src', 'school', 'model', 'Car.java');
    const cx = createclass.context(lang, file, fs.readFileSync(file, 'utf8'));
    ok(cx.root === (lang === 'cs' ? path.join(projectDir, 'cs') : path.join(projectDir, 'java', 'src')) && cx.known.length === known.length
        && (lang === 'cs' ? cx.namespace === 'School.Model' && cx.fileScoped : cx.pkg === 'school.model'), JSON.stringify(cx).slice(0, 300));
    // Car כבר קיים באותה חבילה — נחסם
    ok(new createclass.Session(cx).view({ className: 'Car', fields: [{ base: 'int', name: 'x' }] }).error.includes('כבר יש מחלקה בשם Car'), 'Car תפוס');
}

// --- הורשה (כמו ClassGenTest.poly): מה נקרא מהקוד, virtual ב-C#, צבעים כמו בהרצה, היררכיות, הוסף עצם
{
    const ks = G.models('public abstract class Shape extends Base implements Cloneable {\n  @Deprecated protected int a = 1, b = 2;\n  private static int n;\n'
        + '  private java.util.List<String> names = new java.util.ArrayList<String>();\n  public Shape() { this(1); }\n  public Shape(int a, String b) { }\n'
        + '  private Shape(int a, int b, int c) { }\n  public abstract double area();\n  protected void grow(final int by, String why) { if (by > 0) { a++; } }\n'
        + '  public final String id() { return "x { y }"; }\n  class Inner { void hidden() {} }\n}\ninterface Walker { void walk(); default void rest() { } }\nfinal class Locked { }\n', 'Shape.java');
    eq('Shape : Base abstract | int a; int b; java.util.List<String> names; | ctor(int a, String b) | [public abstract] double area()!; [protected] void grow(int by, String why); [public final] String id();', G.modelText(ks[0]));
    eq('Walker interface | | ctor(-) | void walk()!; [default] void rest();', G.modelText(ks[1]));
    eq('Locked final | | ctor(-) |', G.modelText(ks[2]));
    const src = 'public class A\n{\n    public string Kind() { return "a"; }\n}\npublic class B\n{\n    public string Kind(int x) { return "b"; }\n    public string Kind()\n    {\n        return "b";\n    }\n}\n';
    eq(src.replace('    public string Kind()\n    {', '    public virtual string Kind()\n    {'), G.addVirtual(src, 'B', 'Kind', ''));
    const done = G.addVirtual(src, 'B', 'Kind', '');
    eq(done, G.addVirtual(done, 'B', 'Kind', ''));
    const zoo = G.treeFiles(G.trees()[0], { lang: 'java', pkg: '', namespace: '', taken: new Map(), known: [] }, true, false);
    const col = G.colors(zoo.known.slice().sort((a, b) => (a.name < b.name ? -1 : 1)));
    eq('Animal=0 Mammal=1 Dog=2 Boxer=3 Labrador=4 Person=-1', [...col].map(([k, v]) => k + '=' + v).join(' '));
    eq('#c0392b', G.hex(G.ink(col.get('Animal'))));
    ok(zoo.ok && zoo.files.length === 7 && zoo.files[6][0] === 'ZooDemo.java', zoo.errors.join());
    const bad = G.treeFiles(G.trees()[0], { lang: 'cs', taken: { Dog: 'Dog.cs' }, known: [] }, true, false);
    ok(!bad.ok && bad.errors[0] === 'Dog: כבר יש מחלקה בשם Dog (בקובץ Dog.cs) — בוחרים שם אחר', bad.errors.join());
    eq('"Helpers" הוא שם של מחלקה בספריית הקורס (Unit4) — מחלקה בשם הזה תתנגש ב-using Unit4.CollectionsLib; בוחרים שם אחר למחלקה', firstError(opts('cs', 'Helpers', 'int x')));
    const pup = Object.assign(opts('java', 'Puppy', 'int milk'), { known: zoo.known, superName: 'Dog' });
    eq('השדה milk כבר קיים במחלקה Mammal — המחלקה יורשת אותו. בוחרים שם אחר', firstError(pup));
    const sh = G.treeFiles(G.trees()[1], { lang: 'java', known: [] }, false, false);
    const sq = Object.assign(opts('java', 'Square', 'double side'), { known: sh.known, superName: 'Shape' });
    ok(G.generate(sq).includes('    @Override\n    public double area() {\n        return 0;\n    }\n'), G.generate(sq));
    eq('    Animal a1 = new Boxer("Rexy", "male", 3.2, 3, "house");', G.objectCode({ lang: 'java', varType: 'Animal', varName: 'a1', cls: 'Boxer', indent: '    ',
        args: ['Rexy', 'male', '3.2', '3', 'house'] }, zoo.known).code);
    eq('"house"', G.literal('object', 'house', 'cs'));
    eq("'x'", G.literal('char', 'x', 'java'));
}

// --- כל המקרים
const [casesFile, out] = process.argv.slice(2);
if (casesFile && out) {
    const jsDir = path.join(out, 'js'), compileDir = path.join(out, 'compile');
    fs.rmSync(jsDir, { recursive: true, force: true });
    fs.rmSync(compileDir, { recursive: true, force: true });
    fs.mkdirSync(jsDir, { recursive: true });
    fs.mkdirSync(compileDir, { recursive: true });
    let n = 0, projects = 0;
    for (let line of fs.readFileSync(casesFile, 'utf8').split('\n')) {
        line = line.replace(/\r$/, '');
        if (!line.trim() || line.startsWith('#')) continue;
        n++;
        const noCompile = line.startsWith('!');
        const o = G.parseCase(noCompile ? line.slice(1) : line, path.dirname(path.resolve(casesFile)));
        fs.writeFileSync(path.join(jsDir, n + '.txt'), G.describe(o));
        const chk = G.check(o);
        if (!chk.ok || noCompile) continue;
        projects++;
        writeProject(path.join(compileDir, String(n)), o, chk, (line.split('|')[13] || '') ? path.join(path.dirname(path.resolve(casesFile)), line.split('|')[13]) : null);
    }
    if (projectDir) {
        for (const lang of ['java', 'cs']) fs.writeFileSync(path.join(jsDir, 'scan-' + lang + '.txt'), scanText(lang, scans[lang]));
        scanProjects(compileDir);
        projects += 2;
    }
    let t = 0;
    for (const [name, text] of treeCases()) { fs.writeFileSync(path.join(jsDir, 'tree-' + name + '.txt'), text); t++; }
    const tp = treeProjects(path.join(out, 'trees'));
    console.log(`classgen (JS): ${checks} בדיקות עברו, ${n} מקרים ו-${t} היררכיות / עצמים נכתבו, ${projects} פרויקטים ו-${tp} פרויקטים של היררכיות לקומפילציה`);
} else console.log(`classgen (JS): ${checks} בדיקות עברו`);

// ------------------------------------------------------------------ היררכיות: אותו טקסט כמו ClassGenTest.treeCases

function base(lang, ns, known) {
    return { lang, pkg: lang === 'cs' ? '' : ns, namespace: lang === 'cs' ? ns : '', fileScoped: false, taken: new Map(), known: known || [] };
}

function treeText(tr, r) {
    let s = '';
    for (const e of r.errors) s += 'ERROR ' + e + '\n';
    for (const w of r.warnings) s += 'WARN ' + w + '\n';
    s += 'LAYOUT' + G.treeLayout(tr, []).map(l => ' ' + l.join(':')).join('') + '\n';
    for (const [f, src] of r.files) s += '--- ' + f + '\n' + src;
    return s;
}

function objOptions(lang, spec) {
    const p = spec.split('|');
    const o = { lang, varType: p[0], varName: p[1], indent: '    ', args: [], cells: [], array: false };
    if (p[2].startsWith('[]')) {
        o.array = true;
        o.size = parseInt(p[2].slice(2), 10);
        for (const cell of p[3].split('/')) {
            const c = [];
            if (cell) {
                const colon = cell.indexOf(':');
                c.push(colon < 0 ? cell : cell.slice(0, colon));
                if (colon >= 0) c.push(...cell.slice(colon + 1).split(';'));
            }
            o.cells.push(c);
        }
    } else {
        o.cls = p[2];
        if (p[3]) o.args.push(...p[3].split(';'));
    }
    return o;
}

function treeCases() {
    const outList = [];
    for (const lang of ['java', 'cs'])
        for (const ns of ['', lang === 'cs' ? 'Zoo1' : 'zoo.app']) {
            for (const tr of G.trees())
                for (const run of [false, true]) {
                    if (run && lang !== 'cs') continue;
                    const b = base(lang, ns);
                    b.fileScoped = run;
                    outList.push([lang + '-' + tr.id + (ns ? '-ns' : '') + (run ? '-run' : ''), treeText(tr, G.treeFiles(tr, b, true, run))]);
                }
            OWN.forEach((spec, i) => {
                const errors = [];
                const proj = G.treeFiles(G.trees()[0], base(lang, ns), false, false).known;
                const tr = G.ownTree(ownRows(spec), proj, errors);
                const r = G.treeFiles(tr, base(lang, ns, proj), true, false);
                r.errors.unshift(...errors);
                outList.push([lang + '-own' + i + (ns ? '-ns' : ''), treeText(tr, r)]);
            });
            const known = G.treeFiles(G.trees()[0], base(lang, ns), false, false).known.concat(G.treeFiles(G.trees()[1], base(lang, ns), false, false).known);
            let s = '';
            for (const spec of OBJECTS) {
                const r = G.objectCode(objOptions(lang, spec), known);
                s += '## ' + spec + '\n' + r.errors.map(e => 'ERROR ' + e + '\n').join('') + r.code + '\n';
            }
            for (const type of ['Animal', 'Dog', 'Shape', 'Person', 'Nope'])
                s += 'CLASSES ' + type + ':' + G.objectClasses(known, type).map(k => ' ' + k.name + '('
                    + G.ctorParams(known, k.name).map(p => p.type + ' ' + p.name + '@' + G.paramOwner(known, k.name, p.name)).join(', ') + ')').join('') + '\n';
            for (const t of SPLITS) s += 'SPLIT ' + t + ' → ' + G.splitArgs(t).join(' | ') + '\n';
            outList.push([lang + '-objects' + (ns ? '-ns' : ''), s]);
        }
    return outList;
}

// ------------------------------------------------------------------ היררכיות: פרויקטים לקומפילציה, להרצה ולהרצה הוויזואלית (classgen-tests.sh)

/** כל קובץ עם שורות ה-using / import של בית הספר בהתחלה (אחרי package ב-Java) */
function withSchool(src, lang) {
    if (lang === 'cs') return SCHOOL_CS + '\n' + src;
    const m = /^package [\w.]+;\n\n/.exec(src);
    return m ? m[0] + SCHOOL_JAVA + '\n' + src.slice(m[0].length) : SCHOOL_JAVA + '\n' + src;
}

function treeProjects(dir) {
    fs.rmSync(dir, { recursive: true, force: true });
    const proj = G.treeFiles(G.trees()[0], base('java', ''), false, false).known;
    const list = G.trees().map(tr => ({ tr, id: tr.id })).concat([{ tr: null, id: 'own' }]);
    let count = 0;
    const write = (name, lang, mode, main, files, expect, extra) => {
        const d = path.join(dir, name), src = path.join(d, 'src');
        fs.mkdirSync(src, { recursive: true });
        for (const [f, text] of files) {
            const p = path.join(src, f);
            fs.mkdirSync(path.dirname(p), { recursive: true });
            fs.writeFileSync(p, text);
        }
        fs.writeFileSync(path.join(d, 'lang.txt'), lang);
        fs.writeFileSync(path.join(d, 'mode.txt'), mode);
        fs.writeFileSync(path.join(d, 'main.txt'), main);
        fs.writeFileSync(path.join(d, 'expect.txt'), expect.join('\n') + '\n');
        if (extra) fs.writeFileSync(path.join(d, 'visual.txt'), extra.join('\n') + '\n');
        count++;
    };
    for (const { tr: ready, id } of list) {
        for (const lang of ['java', 'cs']) {
            const own = () => G.ownTree(ownRows(OWN[0]), [], []);
            const tr = ready || own();
            const gen = (ns, run) => {
                const r = G.treeFiles(tr, base(lang, ns), true, run);
                if (!r.ok) throw new Error(id + ' ' + lang + ': ' + r.errors.join('; '));
                return r;
            };
            const out = [OUTPUT[id]];
            // הצבעים שהחלונות מציגים (ClassGen.colors) — חייבים להיות אותם צבעים כמו בהרצה הוויזואלית
            const plain = gen('', false);
            const col = G.colors(sorted(plain.known.filter(k => tr.classes.some(c => c.name === k.name))));
            const tokens = [];
            for (const [n, v] of col) {
                const k = plain.known.find(x => x.name === n);
                if (v >= 0) tokens.push('C ' + n + (k.sup && col.get(k.sup) !== undefined && col.get(k.sup) >= 0 ? '<' + k.sup : '') + '=' + G.hex(G.ink(v)));
                // קופסאות זו בתוך זו: כל מחלקה יורשת מצוירת בתוך הקופסה של מחלקת העל שלה
                if (v >= 0 && k.sup) tokens.push('B > ' + n + '{');
            }
            if (lang === 'java') {
                write('java-plain-' + id, 'java', 'plain', tr.main, plain.files, out);
                write('java-dsviz-' + id, 'java', 'dsviz', tr.main, plain.files.map(([f, s]) => [f, withSchool(s, 'java')]), out, tokens);
                const pk = gen('zoo.app', false);
                write('java-pkg-' + id, 'java', 'dsviz', 'zoo.app.' + tr.main, pk.files.map(([f, s]) => ['zoo/app/' + f, withSchool(s, 'java')]), out);
            } else {
                write('cs-plain-' + id, 'cs', 'plain', tr.main, plain.files, out);
                write('cs-visual-' + id, 'cs', 'dsviz', tr.main, plain.files.map(([f, s]) => [f, withSchool(s, 'cs')]), out, tokens);
                // classes ב-namespace אחר, ו-Program שעושה using לשניהם — המצב שבו שם כמו Node היה דו-משמעי
                const nsr = gen('Zoo1', true);
                const program = SCHOOL_CS + 'using Zoo1;\n\nnamespace App\n{\n    public static class Program\n    {\n        public static void Main()\n        {\n            '
                    + tr.main + '.Run();\n        }\n    }\n}\n';
                write('cs-ns-' + id, 'cs', 'dsviz', 'App.Program', nsr.files.map(([f, s]) => [f, withSchool(s, 'cs')]).concat([['Program.cs', program]]), out);
                if (id === 'animals' || id === 'own') {
                    const run = gen('', true);
                    write('cs-project-' + id, 'cs', 'project', tr.main, run.files, out);
                    write('cs-sdk-' + id, 'cs', 'sdk', tr.main, run.files.concat([['Program.cs', 'public static class Program\n{\n    public static void Main()\n    {\n        ' + tr.main + '.Run();\n    }\n}\n']]), out);
                }
            }
        }
    }
    return count;
}

function sorted(known) {
    return known.slice().sort((a, b) => a.name === b.name ? (a.pkg < b.pkg ? -1 : a.pkg > b.pkg ? 1 : 0) : (a.name < b.name ? -1 : 1));
}

/**
 * המחלקה החדשה Team בפרויקט שיש בו כבר Student ו-Car (ועוד Student בחבילה אחרת): שדות Student, Queue<Student> ו-Car[]
 * — כמו שבוחרים בחלון (מהרשימה, עם החבילה); ה-import / using נוספים לבד. Main שמשתמש בכל get / set
 */
function scanProjects(compileDir) {
    // Java
    const jd = path.join(compileDir, 'scan-java'), src = path.join(jd, 'src');
    fs.cpSync(path.join(projectDir, 'java', 'src'), src, { recursive: true });
    const jo = { lang: 'java', className: 'Team', pkg: 'school.app', ctor: true, toStr: true, known: scans.java, taken: createclass.takenNames(path.join(src, 'school', 'app'), 'java'),
        fields: [{ type: G.fullType('Student', '', 'java'), name: 'best', refs: { Student: 'school.model' } },
            { type: G.fullType('Queue', 'Student', 'java'), name: 'line', refs: { Student: 'school.model' } },
            { type: 'Car[]', name: 'cars', refs: { Car: 'school.model' } }] };
    const jc = G.check(jo);
    assert(jc.ok && jc.warnings.length === 0, JSON.stringify(jc));
    const team = G.generate(jo);
    assert(team.startsWith('package school.app;\n\nimport unit4.collectionsLib.*;\nimport school.model.Car;\nimport school.model.Student;\n\npublic class Team {'), team);
    fs.writeFileSync(path.join(src, 'school', 'app', 'Team.java'), team);
    fs.writeFileSync(path.join(src, 'school', 'app', 'CheckMain.java'), `package school.app;

import unit4.collectionsLib.*;
import school.model.Car;
import school.model.Student;

public class CheckMain {
    public static void main(String[] args) {
        Student noa = new Student("Noa");
        Queue<Student> line = new Queue<Student>();
        line.insert(noa);
        Car[] cars = { new Car("Mazda") };
        Team t = new Team(noa, line, cars);
        if (t.getBest() != noa || t.getLine() != line || t.getCars() != cars) throw new RuntimeException("בנאי");
        Student dan = new Student("Dan");
        t.setBest(dan);
        if (t.getBest() != dan || !t.getBest().getName().equals("Dan")) throw new RuntimeException("set/get");
        System.out.println(t);
        System.out.println("OK Team");
    }
}
`);
    fs.writeFileSync(path.join(jd, 'main.txt'), 'school.app.CheckMain');
    fs.writeFileSync(path.join(jd, 'lang.txt'), 'java');
    // C# — הקבצים של הפרויקט (בלי ה-Main שלו) + Team.cs + Main לבדיקה
    const cd = path.join(compileDir, 'scan-cs');
    fs.mkdirSync(cd, { recursive: true });
    fs.copyFileSync(path.join(projectDir, 'cs', 'Model', 'Student.cs'), path.join(cd, 'ModelStudent.cs'));
    fs.copyFileSync(path.join(projectDir, 'cs', 'Model', 'Car.cs'), path.join(cd, 'ModelCar.cs'));
    fs.copyFileSync(path.join(projectDir, 'cs', 'Other', 'Student.cs'), path.join(cd, 'OtherStudent.cs'));
    const co = { lang: 'cs', className: 'Team', namespace: 'School.App', ctor: true, toStr: true, known: scans.cs, taken: new Map(),
        fields: [{ type: G.fullType('Student', '', 'cs'), name: 'best', refs: { Student: 'School.Model' } },
            { type: G.fullType('Queue', 'Student', 'cs'), name: 'line', refs: { Student: 'School.Model' } },
            { type: 'Car[]', name: 'cars', refs: { Car: 'School.Model' } }] };
    const cteam = G.generate(co);
    assert(cteam.startsWith('using Unit4.CollectionsLib;\nusing Car = School.Model.Car;\nusing Student = School.Model.Student;\n\nnamespace School.App\n{'), cteam);
    fs.writeFileSync(path.join(cd, 'Team.cs'), cteam);
    fs.writeFileSync(path.join(cd, 'Program.cs'), `using Unit4.CollectionsLib;
using School.App;
using Car = School.Model.Car;
using Student = School.Model.Student;

public static class CheckProgram
{
    public static void Main()
    {
        Student noa = new Student("Noa");
        Queue<Student> line = new Queue<Student>();
        line.Insert(noa);
        Car[] cars = { new Car("Mazda") };
        Team t = new Team(noa, line, cars);
        if (t.GetBest() != noa || t.GetLine() != line || t.GetCars() != cars) throw new System.Exception("בנאי");
        Student dan = new Student("Dan");
        t.SetBest(dan);
        if (t.GetBest() != dan || t.GetBest().GetName() != "Dan") throw new System.Exception("set/get");
        System.Console.WriteLine(t);
        System.Console.WriteLine("OK Team");
    }
}
`);
    fs.writeFileSync(path.join(cd, 'main.txt'), 'CheckProgram');
    fs.writeFileSync(path.join(cd, 'lang.txt'), 'cs');
}

/** ערך לבדיקה מכל טיפוס (k = 0 / 1 — שני ערכים שונים), או null */
function sample(type, k, cs) {
    const prim = cs
        ? { int: ['7', '8'], double: ['2.5', '3.5'], bool: ['true', 'false'], char: ["'a'", "'b'"], string: ['"abc"', '"xyz"'], long: ['7L', '8L'] }
        : { int: ['7', '8'], double: ['2.5', '3.5'], boolean: ['true', 'false'], char: ["'a'", "'b'"], String: ['"abc"', '"xyz"'], long: ['7L', '8L'] };
    if (prim[type]) return prim[type][k];
    if (cs && /^\w+\?$/.test(type)) return k ? 'null' : (sample(type.slice(0, -1), 0, cs) || 'null');
    const arr = /^([A-Za-z_][\w.]*(?:<.*>)?)((?:\[,*\])+)$/.exec(type);
    if (arr) {
        if (!cs && arr[1].includes('<')) return 'null';   // אין new Queue<Integer>[1] ב-Java
        const dims = arr[2].match(/\[,*\]/g);
        const first = dims[0] === '[]' ? '[' + (k + 1) + ']' : '[' + dims[0].slice(1, -1).split(',').map(() => k + 1).join(', ') + ']';
        return 'new ' + arr[1] + first + dims.slice(1).join('');
    }
    if (/^(Queue|Stack)</.test(type)) return 'new ' + type + '()';
    return 'null';
}

function writeProject(dir, o, chk, extra) {
    const cs = o.lang === 'cs', name = chk.className, fields = chk.fields;
    const ns = cs ? o.namespace : o.pkg;
    const src = cs ? dir : path.join(dir, 'src', ...(ns ? ns.split('.') : []));
    fs.mkdirSync(src, { recursive: true });
    fs.writeFileSync(path.join(src, G.fileName(name, o.lang)), G.generate(o));
    // הורשה: הקובץ של מחלקת העל (מ-classgen-poly) ליד המחלקה, עם virtual שהמחולל מוסיף (C#)
    if (extra) {
        let text = fs.readFileSync(extra, 'utf8');
        for (const e of chk.edits) text = G.addVirtual(text, e.className, e.method, e.params);
        fs.writeFileSync(path.join(src, 'Base' + path.basename(extra)), text);
    }
    const get = f => G.getter(f.name, o.lang) + '()', set = (f, v) => G.setter(f.name, o.lang) + '(' + v + ')';
    const fail = cs ? 'throw new System.Exception' : 'throw new RuntimeException';
    const L = [];
    fields.forEach((f, i) => { L.push(`${f.type} a${i} = ${sample(f.type, 0, cs)};`); L.push(`${f.type} b${i} = ${sample(f.type, 1, cs)};`); });
    const supArgs = chk.superParams.map(p => sample(p.type, 0, cs));
    const withCtor = o.ctor !== false || chk.superParams.length > 0;
    L.push(withCtor ? `${name} o = new ${name}(${supArgs.concat(fields.map((f, i) => 'a' + i)).join(', ')});` : `${name} o = new ${name}();`);
    if (withCtor) fields.forEach((f, i) => L.push(`if (o.${get(f)} != a${i}) ${fail}("בנאי: ${f.name}");`));
    fields.forEach((f, i) => { L.push(`o.${set(f, 'b' + i)};`); L.push(`if (o.${get(f)} != b${i}) ${fail}("set/get: ${f.name}");`); });
    // כל דריסה וכל פעולה חדשה נקראת (מותר לקרוא לפעולה protected רק מבפנים — מדלגים)
    for (const m of chk.overrides.map(ov => ov.method).concat(chk.methods)) {
        if (/\bprotected\b/.test(m.mods || '') || m.isAbstract) continue;
        const call = `o.${m.name}(${G.paramList(m.params).map(p => sample(p.type, 0, cs)).join(', ')})`;
        L.push(m.ret === 'void' ? call + ';' : (cs ? `System.Console.WriteLine(${call});` : `System.out.println(${call});`));
    }
    if (o.toStr) L.push(cs ? 'System.Console.WriteLine(o.ToString());' : 'System.out.println(o.toString());');
    L.push(cs ? 'System.Console.WriteLine("OK " + "' + name + '");' : 'System.out.println("OK " + "' + name + '");');
    const body = L.map(l => '        ' + l).join('\n');
    if (cs) {
        // using Unit4.CollectionsLib רק כשצריך: בספרייה יש גם Student / Car, ויחד עם using של ה-namespace שלנו זה דו-משמעי
        const using = (ns ? 'using ' + ns + ';\n' : '') + (chk.course ? 'using Unit4.CollectionsLib;\n' : '');
        fs.writeFileSync(path.join(dir, 'Program.cs'), `${using}\npublic static class CheckProgram\n{\n    public static void Main()\n    {\n${body}\n    }\n}\n`);
        fs.writeFileSync(path.join(dir, 'main.txt'), 'CheckProgram');
    } else {
        fs.writeFileSync(path.join(src, 'CheckMain.java'), (ns ? `package ${ns};\n\n` : '') + `import unit4.collectionsLib.*;\n\npublic class CheckMain {\n    public static void main(String[] args) {\n${body}\n    }\n}\n`);
        fs.writeFileSync(path.join(dir, 'main.txt'), (ns ? ns + '.' : '') + 'CheckMain');
    }
    fs.writeFileSync(path.join(dir, 'lang.txt'), o.lang);
}
