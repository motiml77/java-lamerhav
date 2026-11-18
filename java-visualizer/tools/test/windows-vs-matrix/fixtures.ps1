# (UTF-8 עם BOM) פרויקטי הבדיקה של e2e.ps1 (נטען ב-dot-source): פתרון אחד (CiApps.sln) עם כל סוגי הפרויקטים שתלמיד / מורה פותחים
# ב-Visual Studio — בלי קשר לתיקיית התרגילים של DSViz. כל תוכנית כותבת קובץ סימון (‎%TEMP%\dsviz-ci\run-<שם>.txt‎: שם | ספרייה | תהליך | שעה):
#   - שם: איזו תוכנית באמת רצה (ולא, למשל, תרגיל המחסנית של התבנית);
#   - ספרייה: מאיזה assembly הגיע Node<int> — Unit4 (של בית הספר) או Unit4.CollectionsLib (של DSViz);
#   - תהליך: dsviz-cs בהרצה ויזואלית, שם התוכנית בהרצה רגילה.
# פרויקטי ה-SDK: net8.0. הפרויקטים הקלאסיים: ‎.NET Framework 4.8.

# כמו Path.GetTempPath() בתוכניות (TMP לפני TEMP) — אותה תיקייה שהן כותבות אליה
$script:markDir = Join-Path ([IO.Path]::GetTempPath()) 'dsviz-ci'
$script:ciHelper = @'

static class Ci
{
    public static void Mark(string prog, string lib)
    {
        string dir = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "dsviz-ci");
        System.IO.Directory.CreateDirectory(dir);
        string host = System.Diagnostics.Process.GetCurrentProcess().ProcessName;
        System.IO.File.WriteAllText(System.IO.Path.Combine(dir, "run-" + prog + ".txt"), prog + "|" + lib + "|" + host + "|" + System.DateTime.Now.ToString("HH:mm:ss.fff"));
    }
}
'@
function WriteCode([string]$path, [string]$text) {
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    [IO.File]::WriteAllText($path, ($text -replace "`r?`n", "`r`n"), (New-Object Text.UTF8Encoding($true)))   # כמו Visual Studio: UTF-8 עם BOM, CRLF
}
function ClearMarkers() {
    if (Test-Path $script:markDir) { Get-ChildItem $script:markDir -Filter 'run-*.txt' -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue }
    New-Item -ItemType Directory -Force $script:markDir | Out-Null
}
# הסימון של תוכנית: @{ Prog; Lib; Host; Time } או $null
function ReadMarker([string]$prog) {
    $f = Join-Path $script:markDir "run-$prog.txt"
    if (-not (Test-Path $f)) { return $null }
    try {
        $p = ([IO.File]::ReadAllText($f)).Split('|')
        if ($p.Count -lt 4) { return $null }
        return @{ Prog = $p[0]; Lib = $p[1]; Host = $p[2]; Time = $p[3] }
    } catch { return $null }
}
function Markers() {
    if (-not (Test-Path $script:markDir)) { return '' }
    return (@(Get-ChildItem $script:markDir -Filter 'run-*.txt' -ErrorAction SilentlyContinue | ForEach-Object { try { [IO.File]::ReadAllText($_.FullName) } catch { $_.Name } }) -join ' ; ')
}

function SdkCsproj([string]$items) {
    return "<Project Sdk=`"Microsoft.NET.Sdk`">`r`n`r`n  <PropertyGroup>`r`n    <OutputType>Exe</OutputType>`r`n    <TargetFramework>net8.0</TargetFramework>`r`n    <ImplicitUsings>disable</ImplicitUsings>`r`n    <Nullable>disable</Nullable>`r`n  </PropertyGroup>`r`n$items`r`n</Project>`r`n"
}
function ClassicCsproj([string]$name, [string]$guid, [string]$refs) {
    return @"
<?xml version="1.0" encoding="utf-8"?>
<Project ToolsVersion="15.0" xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
  <Import Project="`$(MSBuildExtensionsPath)\`$(MSBuildToolsVersion)\Microsoft.Common.props" Condition="Exists('`$(MSBuildExtensionsPath)\`$(MSBuildToolsVersion)\Microsoft.Common.props')" />
  <PropertyGroup>
    <Configuration Condition=" '`$(Configuration)' == '' ">Debug</Configuration>
    <Platform Condition=" '`$(Platform)' == '' ">AnyCPU</Platform>
    <ProjectGuid>$guid</ProjectGuid>
    <OutputType>Exe</OutputType>
    <RootNamespace>$name</RootNamespace>
    <AssemblyName>$name</AssemblyName>
    <TargetFrameworkVersion>v4.8</TargetFrameworkVersion>
    <FileAlignment>512</FileAlignment>
    <AutoGenerateBindingRedirects>true</AutoGenerateBindingRedirects>
    <Deterministic>true</Deterministic>
  </PropertyGroup>
  <PropertyGroup Condition=" '`$(Configuration)|`$(Platform)' == 'Debug|AnyCPU' ">
    <PlatformTarget>AnyCPU</PlatformTarget>
    <DebugSymbols>true</DebugSymbols>
    <DebugType>full</DebugType>
    <Optimize>false</Optimize>
    <OutputPath>bin\Debug\</OutputPath>
    <DefineConstants>DEBUG;TRACE</DefineConstants>
  </PropertyGroup>
  <PropertyGroup Condition=" '`$(Configuration)|`$(Platform)' == 'Release|AnyCPU' ">
    <PlatformTarget>AnyCPU</PlatformTarget>
    <DebugType>pdbonly</DebugType>
    <Optimize>true</Optimize>
    <OutputPath>bin\Release\</OutputPath>
    <DefineConstants>TRACE</DefineConstants>
  </PropertyGroup>
  <ItemGroup>
    <Reference Include="System" />
    <Reference Include="System.Core" />
$refs
  </ItemGroup>
  <ItemGroup>
    <Compile Include="Program.cs" />
    <Compile Include="Properties\AssemblyInfo.cs" />
  </ItemGroup>
  <Import Project="`$(MSBuildToolsPath)\Microsoft.CSharp.targets" />
</Project>
"@
}

# Unit4.dll האמיתי — הספרייה שבתי הספר מחלקים (תיקיית Unit4CSharp ב-Google Drive), ‎.NET Framework 4.0 עם WinForms. לא נשמר במאגר: מורד ונבדק
# מול sha256 (כמו tools/test/helpers/school-unit4.sh). מחזיר true אם $dest הוא הקובץ הנכון
$script:SchoolUnit4Sha = '9F47124FC1F7F7A1204EC083636933023CEDA142D23734E76E146A33C96CF4B9'
function Get-SchoolUnit4([string]$dest) {
    $url = 'https://drive.usercontent.google.com/download?id=1r24UUvcESdtZ14be8ud3sEu4IEYsqnQF&export=download&confirm=t'
    New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
    for ($i = 0; $i -lt 4; $i++) {
        if ((Test-Path $dest) -and (Get-FileHash $dest -Algorithm SHA256).Hash -eq $script:SchoolUnit4Sha) { return $true }
        try { Invoke-WebRequest $url -OutFile $dest -UseBasicParsing -TimeoutSec 60 } catch { Info "הורדת Unit4.dll (ניסיון $($i + 1)): $($_.Exception.Message)" }
        if ((Test-Path $dest) -and (Get-FileHash $dest -Algorithm SHA256).Hash -eq $script:SchoolUnit4Sha) { return $true }
        Start-Sleep -Seconds 5
    }
    return $false
}

# יוצר את הפתרון ב-$dir. $schoolSource: tools/test/helpers/SchoolUnit4.cs (ספרייה מדומה — בשביל ProjectReference והספרייה המורחבת, שצריכות קוד מקור).
# $realSchoolDll: Unit4.dll האמיתי (Get-SchoolUnit4) — אליו מפנה הפרויקט הקלאסי; ריק — בונים במקומו את המדומה. מחזיר טבלה: Sln, Dir
function New-CiApps([string]$dir, [string]$schoolSource, [string]$realSchoolDll) {
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
    New-Item -ItemType Directory -Force $dir | Out-Null
    $projects = New-Object System.Collections.Generic.List[object]
    function Add([string]$name, [string]$guid) { $projects.Add(@{ Name = $name; Guid = $guid }) }

    # --- ספריית בית הספר: namespace Unit4.CollectionsLib ב-assembly בשם Unit4, עם ה-API של ספריית הבגרות (tools/test/helpers/SchoolUnit4.cs —
    #     אותו קובץ שמול ה-API שלו cs-tests.sh משווה את הספרייה של DSViz, חבר-חבר)
    $school = [IO.File]::ReadAllText($schoolSource)
    # 1. כקובץ DLL מוכן — הפרויקט הקלאסי מפנה אליו ב-HintPath: Unit4.dll האמיתי של בתי הספר. אם לא הורד — המדומה, במהדר של ‎.NET Framework
    #    שיש בכל Windows (C# 5 — מספיק לקוד הזה), בלי תלות בגרסת Visual Studio
    New-Item -ItemType Directory -Force (Join-Path $dir 'school-bin') | Out-Null
    $real = [bool]($realSchoolDll -and (Test-Path $realSchoolDll))
    if ($real) { Copy-Item $realSchoolDll (Join-Path $dir 'school-bin\Unit4.dll') -Force }
    else {
        WriteCode (Join-Path $dir 'school-bin\Lib.cs') $school
        $csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
        $o = & $csc /nologo /target:library "/out:$(Join-Path $dir 'school-bin\Unit4.dll')" (Join-Path $dir 'school-bin\Lib.cs') 2>&1
        if (-not (Test-Path (Join-Path $dir 'school-bin\Unit4.dll'))) { Info "csc (Unit4.dll של בית הספר): $($o -join ' ')" }
    }
    # 2. כפרויקט שקובץ הפרויקט שלו נקרא אחרת (CollectionsLib.csproj, AssemblyName=Unit4) — בשביל ProjectReference
    WriteCode (Join-Path $dir 'CollectionsLib\Lib.cs') $school
    WriteCode (Join-Path $dir 'CollectionsLib\CollectionsLib.csproj') "<Project Sdk=`"Microsoft.NET.Sdk`">`r`n  <PropertyGroup>`r`n    <TargetFramework>netstandard2.0</TargetFramework>`r`n    <AssemblyName>Unit4</AssemblyName>`r`n    <RootNamespace>Unit4.CollectionsLib</RootNamespace>`r`n  </PropertyGroup>`r`n</Project>`r`n"
    Add 'CollectionsLib' '{C0110001-0000-4000-8000-000000000001}'
    # 3. ספרייה של בית ספר שהוסיפה חבר שאין בספרייה של DSViz (Stack<T>.Count()) — קוד שמשתמש בו נבנה ורץ בהרצה הרגילה; ההרצה הוויזואלית מסבירה
    $schoolExt = $school.Replace('public bool IsEmpty() { return head == null; }', 'public bool IsEmpty() { return head == null; }' + "`r`n" + '        public int Count() { int c = 0; for (Node<T> p = head; p != null; p = p.GetNext()) c++; return c; }')
    if ($schoolExt -eq $school) { Info 'הכנת הספרייה המורחבת (Count) נכשלה' }
    WriteCode (Join-Path $dir 'CollectionsLibExt\Lib.cs') $schoolExt
    WriteCode (Join-Path $dir 'CollectionsLibExt\CollectionsLibExt.csproj') "<Project Sdk=`"Microsoft.NET.Sdk`">`r`n  <PropertyGroup>`r`n    <TargetFramework>netstandard2.0</TargetFramework>`r`n    <AssemblyName>Unit4</AssemblyName>`r`n    <RootNamespace>Unit4.CollectionsLib</RootNamespace>`r`n  </PropertyGroup>`r`n</Project>`r`n"
    Add 'CollectionsLibExt' '{C0110001-0000-4000-8000-00000000000A}'

    $schoolProgram = @'
using System;
using Unit4.CollectionsLib;

class Program
{
    static void Main(string[] args)
    {
        Stack<int> s = new Stack<int>();
        s.Push(3);
        s.Push(4);
        Node<int> n = new Node<int>(5);
        int sum = s.Pop() + s.Pop() + n.GetValue();
        Ci.Mark("PROG", typeof(Node<int>).Assembly.GetName().Name);
        Console.WriteLine("PROG " + sum);
    }
}
'@

    # --- SdkApp: Console App רגיל עם Main
    WriteCode (Join-Path $dir 'SdkApp\Program.cs') (@'
using System;

class Program
{
    static void Main(string[] args)
    {
        int sum = 0;
        for (int i = 1; i <= 3; i++)
        {
            sum += i;
        }
        Ci.Mark("sdk", "");
        Console.WriteLine("SDK-APP " + sum);
    }
}
'@ + $script:ciHelper)
    WriteCode (Join-Path $dir 'SdkApp\SdkApp.csproj') (SdkCsproj '')
    Add 'SdkApp' '{C0110001-0000-4000-8000-000000000002}'

    # --- PolyApp: הורשה ופולימורפיזם כמו פרויקט של Visual Studio — namespace, מחלקה בכל קובץ (tools/test/objects/cs/poly/pets/Pets.cs, מפוצל)
    WriteCode (Join-Path $dir 'PolyApp\Program.cs') (@'
using System;

namespace PolyApp
{
    public class Program
    {
        public static void Main()
        {
            Animal a = new Animal("Bob", 3);
            Dog d = new Dog("Rexy", 5, "Boxer", new Person("Hila"));
            Cat c = new Cat("Mitzi", 2, true);
            Animal pet = d;
            Console.WriteLine(pet.Say());
            Console.WriteLine(d.Bark());
            Ci.Mark("poly", "");
        }
    }
}
'@ + $script:ciHelper)
    WriteCode (Join-Path $dir 'PolyApp\Animal.cs') @'
using System;

namespace PolyApp
{
    public class Animal
    {
        private string name;
        private int age;

        public Animal(string name, int age)
        {
            this.name = name;
            this.age = age;
        }

        public string GetName() { return name; }

        public virtual string Say() { return "..."; }
    }
}
'@
    WriteCode (Join-Path $dir 'PolyApp\Dog.cs') @'
using System;

namespace PolyApp
{
    public class Dog : Animal
    {
        private string breed;
        private Person owner;      // מקבל מבחוץ

        public Dog(string name, int age, string breed, Person owner) : base(name, age)
        {
            this.breed = breed;
            this.owner = owner;
        }

        public override string Say() { return "Woof"; }

        public string Bark() { return base.Say() + "!"; }

        public void Fetch() { }
    }
}
'@
    WriteCode (Join-Path $dir 'PolyApp\Cat.cs') @'
using System;

namespace PolyApp
{
    public class Cat : Animal
    {
        private bool indoor;
        private Collar collar;

        public Cat(string n, int a, bool inside) : base(n, a)
        {
            this.indoor = inside;
            this.collar = new Collar("red"); // יוצר בעצמו
        }

        public override string Say() { return "Meow"; }
    }
}
'@
    WriteCode (Join-Path $dir 'PolyApp\Person.cs') @'
using System;

namespace PolyApp
{
    public class Person
    {
        private string name;

        public Person(string name) { this.name = name; }
    }
}
'@
    WriteCode (Join-Path $dir 'PolyApp\Collar.cs') @'
using System;

namespace PolyApp
{
    public class Collar
    {
        private string color;

        public Collar(string color) { this.color = color; }
    }
}
'@
    WriteCode (Join-Path $dir 'PolyApp\PolyApp.csproj') (SdkCsproj '')
    Add 'PolyApp' '{C0110001-0000-4000-8000-00000000000D}'

    # --- TopApp: top-level statements (התבנית החדשה של Visual Studio)
    WriteCode (Join-Path $dir 'TopApp\Program.cs') @'
using System;

int sum = 0;
for (int i = 1; i <= 4; i++)
{
    sum += i;
}
string dir = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "dsviz-ci");
System.IO.Directory.CreateDirectory(dir);
System.IO.File.WriteAllText(System.IO.Path.Combine(dir, "run-top.txt"), "top||" + System.Diagnostics.Process.GetCurrentProcess().ProcessName + "|" + DateTime.Now.ToString("HH:mm:ss.fff"));
Console.WriteLine("TOP-APP " + sum);
'@
    WriteCode (Join-Path $dir 'TopApp\TopApp.csproj') (SdkCsproj '')
    Add 'TopApp' '{C0110001-0000-4000-8000-000000000003}'

    # --- SchoolRef: פרויקט SDK עם ProjectReference לספריית בית הספר (שם הקובץ CollectionsLib.csproj, ה-assembly Unit4)
    WriteCode (Join-Path $dir 'SchoolRef\Program.cs') (($schoolProgram -creplace 'PROG', 'schoolref') + $script:ciHelper)
    WriteCode (Join-Path $dir 'SchoolRef\SchoolRef.csproj') (SdkCsproj "`r`n  <ItemGroup>`r`n    <ProjectReference Include=`"..\CollectionsLib\CollectionsLib.csproj`" />`r`n  </ItemGroup>`r`n")
    Add 'SchoolRef' '{C0110001-0000-4000-8000-000000000004}'

    # --- SchoolClassic: Console App (.NET Framework) קלאסי שמפנה ל-Unit4.dll של בית הספר (HintPath). עם Unit4.dll האמיתי — הקוד משתמש גם במה
    #     שיש רק בספרייה של בתי הספר (StackInt, BinTreeUtils) ומדפיס חוליה; את מה שהדפיס הוא כותב בסימון school-out, כדי להשוות בין ההרצה
    #     הרגילה (Unit4.dll) להרצה הוויזואלית (הספרייה של DSViz): אותו פלט בדיוק. הצפוי: [4,3] [5,1] 4 h1 c3 full=True
    #     בראש הקובץ גם using Unit4.BinTreeCanvasLib; ו-using Unit4.GridLib; — כמו בתבנית של בתי הספר (GridLib לא קיים בספרייה של DSViz;
    #     ההרצה הוויזואלית צריכה להתחיל בכל זאת)
    $g = '{C0110001-0000-4000-8000-000000000005}'
    $realSchoolProgram = @'
using System;
using Unit4.CollectionsLib;
using Unit4.BinTreeCanvasLib;
using Unit4.BinTreeUtilsLib;
using Unit4.GridLib;

class Program
{
    static void Main(string[] args)
    {
        Stack<int> s = new Stack<int>();
        s.Push(3);
        s.Push(4);
        StackInt si = new StackInt();
        si.Push(1);
        si.Push(5);
        Node<int> n = new Node<int>(4, new Node<int>(8));
        BinNode<int> t = new BinNode<int>(new BinNode<int>(1), 2, new BinNode<int>(3));
        string text = s + " " + si + " " + n + " h" + BinTreeUtils.Height(t) + " c" + BinTreeUtils.NumOfNodes(t) + " full=" + BinTreeUtils.IsFull(t);
        Console.WriteLine(text);
        Ci.Mark("school-out", text);
        Ci.Mark("school", typeof(Node<int>).Assembly.GetName().Name);
    }
}
'@
    WriteCode (Join-Path $dir 'SchoolClassic\Program.cs') ($(if ($real) { $realSchoolProgram } else { $schoolProgram -creplace 'PROG', 'school' }) + $script:ciHelper)
    WriteCode (Join-Path $dir 'SchoolClassic\Properties\AssemblyInfo.cs') "using System.Reflection;`r`n[assembly: AssemblyTitle(`"SchoolClassic`")]`r`n[assembly: AssemblyVersion(`"1.0.0.0`")]`r`n"
    WriteCode (Join-Path $dir 'SchoolClassic\SchoolClassic.csproj') (ClassicCsproj 'SchoolClassic' $g "    <Reference Include=`"Unit4`">`r`n      <HintPath>..\school-bin\Unit4.dll</HintPath>`r`n    </Reference>")
    Add 'SchoolClassic' $g

    # --- ClassicDsviz: Console App (.NET Framework) קלאסי שהקוד שלו משתמש בספרייה בלי שום הפניה — DSViz.targets מוסיף את הספרייה של DSViz
    $g = '{C0110001-0000-4000-8000-000000000006}'
    WriteCode (Join-Path $dir 'ClassicDsviz\Program.cs') (($schoolProgram -creplace 'PROG', 'classicdsviz') + $script:ciHelper)
    WriteCode (Join-Path $dir 'ClassicDsviz\Properties\AssemblyInfo.cs') "using System.Reflection;`r`n[assembly: AssemblyTitle(`"ClassicDsviz`")]`r`n[assembly: AssemblyVersion(`"1.0.0.0`")]`r`n"
    WriteCode (Join-Path $dir 'ClassicDsviz\ClassicDsviz.csproj') (ClassicCsproj 'ClassicDsviz' $g '')
    Add 'ClassicDsviz' $g

    # --- PlainStack: פרויקט שאין לו שום קשר ל-DSViz — Stack<int> של ‎.NET עם Count, עם ImplicitUsings (כמו Console App חדש)
    WriteCode (Join-Path $dir 'PlainStack\Program.cs') ("var s = new Stack<int>();`r`ns.Push(1);`r`ns.Push(2);`r`nCi.Mark(`"plain`", typeof(Stack<int>).Assembly.GetName().Name);`r`nConsole.WriteLine(`"PLAIN `" + s.Count);`r`n" + $script:ciHelper)
    WriteCode (Join-Path $dir 'PlainStack\PlainStack.csproj') "<Project Sdk=`"Microsoft.NET.Sdk`">`r`n`r`n  <PropertyGroup>`r`n    <OutputType>Exe</OutputType>`r`n    <TargetFramework>net8.0</TargetFramework>`r`n    <ImplicitUsings>enable</ImplicitUsings>`r`n    <Nullable>enable</Nullable>`r`n  </PropertyGroup>`r`n`r`n</Project>`r`n"
    Add 'PlainStack' '{C0110001-0000-4000-8000-000000000007}'

    # --- NoMain: Program.cs עם Main (שלא אמור לרוץ), ו-Helper.cs בלי Main — "הרצה ויזואלית" של Helper.cs מסבירה, ולא מריצה את Program.cs
    WriteCode (Join-Path $dir 'NoMain\Program.cs') (@'
using System;

class Program
{
    static void Main(string[] args)
    {
        Ci.Mark("nomain-program", "");
        Console.WriteLine("NOMAIN " + Helper.Twice(4));
    }
}
'@ + $script:ciHelper)
    WriteCode (Join-Path $dir 'NoMain\Helper.cs') "static class Helper`r`n{`r`n    public static int Twice(int x)`r`n    {`r`n        return x * 2;`r`n    }`r`n}`r`n"
    WriteCode (Join-Path $dir 'NoMain\NoMain.csproj') (SdkCsproj '')
    Add 'NoMain' '{C0110001-0000-4000-8000-000000000008}'

    # --- EmptyMain: Main ריקה (רק פעולה שלא נקראת)
    WriteCode (Join-Path $dir 'EmptyMain\Program.cs') "using System;`r`n`r`nclass Program`r`n{`r`n    static int Count(int n)`r`n    {`r`n        return n + 1;`r`n    }`r`n`r`n    static void Main(string[] args)`r`n    {`r`n    }`r`n}`r`n"
    WriteCode (Join-Path $dir 'EmptyMain\EmptyMain.csproj') (SdkCsproj '')
    Add 'EmptyMain' '{C0110001-0000-4000-8000-000000000009}'

    # --- WaitApp: Unit4.dll האמיתי של בתי הספר (Reference; בלעדיו — הספרייה המדומה ב-ProjectReference), והתוכנית מחכה לקלט (Console.ReadLine) — ההרצה הרגילה נשארת פתוחה, ובזמן הזה
    #     ההרצה הוויזואלית של אותו קובץ. הסימון: run-wait-<תהליך>.txt (WaitApp בהרצה הרגילה, dsviz-cs בהרצה הוויזואלית)
    WriteCode (Join-Path $dir 'WaitApp\Program.cs') (@'
using System;
using Unit4.CollectionsLib;

class Program
{
    static void Main(string[] args)
    {
        Stack<int> s = new Stack<int>();
        s.Push(7);
        Ci.Mark("wait-" + System.Diagnostics.Process.GetCurrentProcess().ProcessName, typeof(Stack<int>).Assembly.GetName().Name);
        Console.WriteLine("WAIT " + s.Top() + " - press Enter");
        string line = Console.ReadLine();
        Console.WriteLine("got " + line);
    }
}
'@ + $script:ciHelper)
    if ($real) { WriteCode (Join-Path $dir 'WaitApp\WaitApp.csproj') (SdkCsproj "`r`n  <ItemGroup>`r`n    <Reference Include=`"Unit4`">`r`n      <HintPath>..\school-bin\Unit4.dll</HintPath>`r`n    </Reference>`r`n  </ItemGroup>`r`n") }
    else { WriteCode (Join-Path $dir 'WaitApp\WaitApp.csproj') (SdkCsproj "`r`n  <ItemGroup>`r`n    <ProjectReference Include=`"..\CollectionsLib\CollectionsLib.csproj`" />`r`n  </ItemGroup>`r`n") }
    Add 'WaitApp' '{C0110001-0000-4000-8000-00000000000B}'

    # --- SchoolExt: קוד שמשתמש ב-Stack<int>.Count() של הספרייה המורחבת (חבר שאין בספרייה של DSViz)
    WriteCode (Join-Path $dir 'SchoolExt\Program.cs') (@'
using System;
using Unit4.CollectionsLib;

class Program
{
    static void Main(string[] args)
    {
        Stack<int> s = new Stack<int>();
        s.Push(4);
        s.Push(9);
        Ci.Mark("schoolext", typeof(Stack<int>).Assembly.GetName().Name);
        Console.WriteLine("COUNT " + s.Count());
    }
}
'@ + $script:ciHelper)
    WriteCode (Join-Path $dir 'SchoolExt\SchoolExt.csproj') (SdkCsproj "`r`n  <ItemGroup>`r`n    <ProjectReference Include=`"..\CollectionsLibExt\CollectionsLibExt.csproj`" />`r`n  </ItemGroup>`r`n")
    Add 'SchoolExt' '{C0110001-0000-4000-8000-00000000000C}'

    # --- קובץ בודד עם שגיאת קומפילציה (לא שייך לאף פרויקט; בתיקייה בלי ‎.csproj‎ מעליה)
    WriteCode (Join-Path $dir 'loose\Bad.cs') "using System;`r`n`r`nclass Bad`r`n{`r`n    static void Main(string[] args)`r`n    {`r`n        int x = `"not a number`";`r`n        Console.WriteLine(x);`r`n    }`r`n}`r`n"

    # --- הפתרון
    $t = "`t"
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add(''); $lines.Add('Microsoft Visual Studio Solution File, Format Version 12.00'); $lines.Add('# Visual Studio Version 17'); $lines.Add('VisualStudioVersion = 17.0.31903.59'); $lines.Add('MinimumVisualStudioVersion = 10.0.40219.1')
    foreach ($p in $projects) { $lines.Add("Project(`"{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}`") = `"$($p.Name)`", `"$($p.Name)\$($p.Name).csproj`", `"$($p.Guid)`""); $lines.Add('EndProject') }
    $lines.Add('Global')
    $lines.Add("${t}GlobalSection(SolutionConfigurationPlatforms) = preSolution"); $lines.Add("$t${t}Debug|Any CPU = Debug|Any CPU"); $lines.Add("$t${t}Release|Any CPU = Release|Any CPU"); $lines.Add("${t}EndGlobalSection")
    $lines.Add("${t}GlobalSection(ProjectConfigurationPlatforms) = postSolution")
    foreach ($p in $projects) { foreach ($c in 'Debug', 'Release') { $lines.Add("$t$t$($p.Guid).$c|Any CPU.ActiveCfg = $c|Any CPU"); $lines.Add("$t$t$($p.Guid).$c|Any CPU.Build.0 = $c|Any CPU") } }
    $lines.Add("${t}EndGlobalSection")
    $lines.Add("${t}GlobalSection(SolutionProperties) = preSolution"); $lines.Add("$t${t}HideSolutionNode = FALSE"); $lines.Add("${t}EndGlobalSection")
    $lines.Add('EndGlobal')
    $sln = Join-Path $dir 'CiApps.sln'
    [IO.File]::WriteAllText($sln, (($lines -join "`r`n") + "`r`n"), (New-Object Text.UTF8Encoding($true)))
    return @{ Sln = $sln; Dir = $dir }
}
