'use strict';
// תמיכה ב-C# בתוסף: מציאת dsviz-cs (ההרצה הוויזואלית), הכנת תיקיית העבודה ויצירת פרויקט לתרגיל.
// בלי תלות ב-vscode — כדי שאפשר יהיה לבדוק את זה בנפרד.
const fs = require('fs');
const os = require('os');
const path = require('path');
const cp = require('child_process');
const runner = require('./runner');

const WIN = process.platform === 'win32';
const LIB = 'Unit4.CollectionsLib.dll';

const BUILD_PROPS = `<Project>
  <PropertyGroup>
    <!-- הספרייה: lib\\Unit4.CollectionsLib.dll בתיקיית התרגילים -->
    <DsVizLib>$(MSBuildThisFileDirectory)lib\\Unit4.CollectionsLib.dll</DsVizLib>
  </PropertyGroup>
  <ItemGroup>
    <!-- פעולות העזר מהספרייה (Size / Duplicate) בלי לכתוב Helpers. — בכל הקבצים -->
    <Using Include="Unit4.CollectionsLib.Helpers" Static="true" />
  </ItemGroup>
</Project>
`;

const csproj = name => `<Project Sdk="Microsoft.NET.Sdk">

  <PropertyGroup>
    <OutputType>Exe</OutputType>
    <TargetFramework>net8.0</TargetFramework>
    <ImplicitUsings>disable</ImplicitUsings>
    <Nullable>disable</Nullable>
    <RootNamespace>${name}</RootNamespace>
  </PropertyGroup>

  <ItemGroup>
    <!-- הספרייה של מבני הנתונים (Stack, Queue, Node, BinNode, Student, Car) -->
    <Reference Include="Unit4.CollectionsLib">
      <HintPath>$(DsVizLib)</HintPath>
    </Reference>
  </ItemGroup>

</Project>
`;

const LAUNCH = {
    profiles: {
        'הרצה ויזואלית': {
            commandName: 'Executable',
            executablePath: '%LOCALAPPDATA%\\DSViz\\cs\\dsviz-cs.exe',
            commandLineArgs: '"$(ProjectDir)"',
            workingDirectory: '$(ProjectDir)'
        },
        'הרצה רגילה': { commandName: 'Project' }
    }
};

/**
 * מוצא את dsviz-cs. סדר: הגדרה dsviz.csRunner → מה שההתקנה רשמה (~/.dsviz/cs-path.txt) → ברירת המחדל בהתקנה
 * (%LOCALAPPDATA%\DSViz\cs) → משתנה הסביבה DSVIZ_CS → PATH. מחזיר { command, prefix } או null
 * (prefix = ארגומנטים לפני הנתיב לפרויקט; קובץ dsviz-cs.dll מורץ דרך dotnet).
 */
function findRunner(configured) {
    const local = WIN && process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'DSViz', 'cs', 'dsviz-cs.exe') : null;
    for (const p of [configured, runner.readDsvizFile('cs-path.txt'), local, process.env.DSVIZ_CS]) {
        if (!p || !fs.existsSync(p)) continue;
        return p.toLowerCase().endsWith('.dll') ? { command: 'dotnet', prefix: [p] } : { command: p, prefix: [] };
    }
    const r = cp.spawnSync('dsviz-cs', ['--version'], { encoding: 'utf8', shell: WIN });
    return r.status === 0 ? { command: 'dsviz-cs', prefix: [] } : null;
}

/** התיקייה שבה יושבים הפרויקטים של התרגילים: root/Exercises */
function exerciseDir(root) {
    return path.join(root, 'Exercises');
}

/** מכין את התיקייה: lib/Unit4.CollectionsLib.dll ו-Directory.Build.props (שמצביע עליו). warnings = הודעות למשתמש */
function setup(root, libSource) {
    const warnings = [];
    const libDir = path.join(root, 'lib');
    fs.mkdirSync(libDir, { recursive: true });
    const target = path.join(libDir, LIB);
    const fresh = fs.readFileSync(libSource);
    if (!fs.existsSync(target) || !fs.readFileSync(target).equals(fresh)) fs.writeFileSync(target, fresh);
    const props = path.join(root, 'Directory.Build.props');
    const had = fs.existsSync(props) ? fs.readFileSync(props, 'utf8') : null;
    // הקובץ שלנו מגרסה קודמת (בלי פעולות העזר) — מתעדכן; קובץ של המשתמש — רק הודעה
    if (had === null || (had.includes('DsVizLib') && !had.includes('Unit4.CollectionsLib.Helpers') && /^<Project>\s*<PropertyGroup>\s*<!--[^>]*-->\s*<DsVizLib>[^<]*<\/DsVizLib>\s*<\/PropertyGroup>\s*<\/Project>\s*$/.test(had)))
        fs.writeFileSync(props, BUILD_PROPS);
    else if (!had.includes('DsVizLib'))
        warnings.push('יש ב-' + root + ' קובץ Directory.Build.props משלכם — יש להוסיף לו: <DsVizLib>$(MSBuildThisFileDirectory)lib\\' + LIB + '</DsVizLib>');
    return { warnings };
}

/** Targil1, Targil2 ... — השם הפנוי הבא (תיקייה) */
function nextProjectName(dir, prefix) {
    for (let i = 1; ; i++) if (!fs.existsSync(path.join(dir, prefix + i))) return prefix + i;
}

/** כותב פרויקט לתרגיל: תיקייה עם .csproj, Program.cs ו-launchSettings. מחזיר את הנתיב ל-Program.cs */
function writeProject(dir, name, code) {
    const p = path.join(dir, name);
    fs.mkdirSync(path.join(p, 'Properties'), { recursive: true });
    fs.writeFileSync(path.join(p, name + '.csproj'), csproj(name), 'utf8');
    fs.writeFileSync(path.join(p, 'Properties', 'launchSettings.json'), JSON.stringify(LAUNCH, null, 2) + '\n', 'utf8');
    const file = path.join(p, 'Program.cs');
    fs.writeFileSync(file, code, 'utf8');
    return file;
}

/** תיקיית הפרויקט (עם .csproj) של קובץ: עולים מהתיקייה שלו עד root. null אם אין */
function projectDirOf(file, root) {
    let d = path.dirname(file);
    for (;;) {
        try { if (fs.readdirSync(d).some(f => f.endsWith('.csproj'))) return d; } catch (e) { return null; }
        const up = path.dirname(d);
        if (up === d || (root && !d.startsWith(root))) return null;
        d = up;
    }
}

/** באיזו שפה העבודה בתיקייה: 'java' / 'csharp', או null אם לא ברור (אין כלום, או שיש את שתיהן) */
function detectLanguage(root) {
    const has = f => fs.existsSync(path.join(root, f));
    const csFound = (() => {
        try {
            if (fs.readdirSync(root).some(f => f.endsWith('.sln') || f.endsWith('.csproj'))) return true;
            const ex = path.join(root, 'Exercises');
            return fs.existsSync(ex) || has(path.join('lib', LIB));
        } catch (e) { return false; }
    })();
    const javaFound = has('.classpath') || has(path.join('lib', 'dsviz.jar')) || (has('src') && fs.readdirSync(path.join(root, 'src')).some(f => f.endsWith('.java')));
    if (csFound && !javaFound) return 'csharp';
    if (javaFound && !csFound) return 'java';
    return null;
}

/** תיקיית התרגילים ב-C# שההתקנה הכינה (~/.dsviz/project-cs-path.txt), או null */
function installedProject() {
    const p = runner.readDsvizFile('project-cs-path.txt');
    return p && fs.existsSync(path.join(p, 'DataStructures-CS.sln')) ? p : null;
}

module.exports = { LIB, findRunner, exerciseDir, setup, nextProjectName, writeProject, projectDirOf, detectLanguage, installedProject, csproj, LAUNCH, BUILD_PROPS };
