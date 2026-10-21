package il.lamerhav.dsviz.cs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * יצירת פרויקט תרגיל ב-C# בתיקיית התרגילים (DataStructures-CS): Exercises\&lt;שם&gt;\ עם .csproj, Program.cs ו-launchSettings.json
 * (אותם קבצים כמו שהתוסף של VS Code יוצר — vscode-extension/cs.js), ורישום הפרויקט בקובץ ה-.sln כדי ש-Visual Studio יציג אותו.
 */
final class CsProject {
    private CsProject() {}

    static final String LIB = "Unit4.CollectionsLib.dll";
    private static final String CS_PROJECT_TYPE = "{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}";

    static String csproj(String name) {
        return "<Project Sdk=\"Microsoft.NET.Sdk\">\n\n"
                + "  <PropertyGroup>\n"
                + "    <OutputType>Exe</OutputType>\n"
                + "    <TargetFramework>net8.0</TargetFramework>\n"
                + "    <ImplicitUsings>disable</ImplicitUsings>\n"
                + "    <Nullable>disable</Nullable>\n"
                + "    <RootNamespace>" + name + "</RootNamespace>\n"
                + "  </PropertyGroup>\n\n"
                + "  <ItemGroup>\n"
                + "    <!-- הספרייה של מבני הנתונים (Stack, Queue, Node, BinNode, Student, Car) -->\n"
                + "    <Reference Include=\"Unit4.CollectionsLib\">\n"
                + "      <HintPath>$(DsVizLib)</HintPath>\n"
                + "    </Reference>\n"
                + "  </ItemGroup>\n\n"
                + "</Project>\n";
    }

    static final String LAUNCH = "{\n"
            + "  \"profiles\": {\n"
            + "    \"הרצה ויזואלית\": {\n"
            + "      \"commandName\": \"Executable\",\n"
            + "      \"executablePath\": \"%LOCALAPPDATA%\\\\DSViz\\\\cs\\\\dsviz-cs.exe\",\n"
            + "      \"commandLineArgs\": \"\\\"$(ProjectDir)\\\"\",\n"
            + "      \"workingDirectory\": \"$(ProjectDir)\"\n"
            + "    },\n"
            + "    \"הרצה רגילה\": {\n"
            + "      \"commandName\": \"Project\"\n"
            + "    }\n"
            + "  }\n"
            + "}\n";

    /** תיקיית התרגילים (DataStructures-CS) תקינה: יש בה את הספרייה, את Directory.Build.props ופתרון */
    static String check(Path root) {
        if (root == null || !Files.isDirectory(root)) return "תיקיית התרגילים לא נמצאה: " + root;
        if (!Files.exists(root.resolve("lib").resolve(LIB))) return "חסרה הספרייה lib\\" + LIB + " ב-" + root;
        if (!Files.exists(root.resolve("Directory.Build.props"))) return "חסר הקובץ Directory.Build.props ב-" + root;
        return null;
    }

    static Path exercisesDir(Path root) {
        return root.resolve("Exercises");
    }

    /** הפתרון (.sln) של התיקייה, או null */
    static Path solution(Path root) {
        try (java.util.stream.Stream<Path> s = Files.list(root)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sln")).sorted().findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** Targil1, Targil2 ... — השם הפנוי הבא */
    static String nextName(Path root) {
        for (int i = 1; ; i++) if (!taken(root, "Targil" + i)) return "Targil" + i;
    }

    static boolean taken(Path root, String name) {
        if (Files.exists(exercisesDir(root).resolve(name))) return true;
        Path sln = solution(root);
        try {
            if (sln != null && new String(Files.readAllBytes(sln), StandardCharsets.UTF_8).contains("= \"" + name + "\","))
                return true;
        } catch (IOException ignored) {}
        return false;
    }

    /** כותב את הפרויקט ורושם אותו בפתרון. מחזיר את הנתיב ל-Program.cs */
    static Path write(Path root, String name, String code) throws IOException {
        Path dir = exercisesDir(root).resolve(name);
        Files.createDirectories(dir.resolve("Properties"));
        Files.write(dir.resolve(name + ".csproj"), csproj(name).getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("Properties").resolve("launchSettings.json"), LAUNCH.getBytes(StandardCharsets.UTF_8));
        Path file = dir.resolve("Program.cs");
        Files.write(file, ("﻿" + code).getBytes(StandardCharsets.UTF_8));   // עם BOM — כמו קבצים ש-Visual Studio יוצר
        Path sln = solution(root);
        if (sln != null) register(sln, name, "Exercises\\" + name + "\\" + name + ".csproj");
        return file;
    }

    /** מוסיף את הפרויקט לקובץ ה-.sln (שורות Project ... EndProject, והגדרות Debug / Release) — בלי לשנות שום דבר אחר */
    static void register(Path sln, String name, String relPath) throws IOException {
        byte[] raw = Files.readAllBytes(sln);
        boolean bom = raw.length >= 3 && (raw[0] & 0xFF) == 0xEF && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF;
        String text = new String(raw, bom ? 3 : 0, raw.length - (bom ? 3 : 0), StandardCharsets.UTF_8);
        String guid = "{" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT) + "}";
        String nl = text.contains("\r\n") ? "\r\n" : "\n";
        String project = "Project(\"" + CS_PROJECT_TYPE + "\") = \"" + name + "\", \"" + relPath + "\", \"" + guid + "\"" + nl + "EndProject" + nl;
        int global = text.indexOf("Global" + nl);
        if (global < 0) throw new IOException("קובץ ה-sln לא תקין: " + sln);
        text = text.substring(0, global) + project + text.substring(global);
        String cfg = "";
        for (String c : new String[] { "Debug", "Release" })
            cfg += "\t\t" + guid + "." + c + "|Any CPU.ActiveCfg = " + c + "|Any CPU" + nl
                 + "\t\t" + guid + "." + c + "|Any CPU.Build.0 = " + c + "|Any CPU" + nl;
        int sec = text.indexOf("GlobalSection(ProjectConfigurationPlatforms)");
        int end = sec < 0 ? -1 : text.indexOf("\tEndGlobalSection", sec);
        if (end >= 0) text = text.substring(0, end) + cfg + text.substring(end);
        Files.write(sln, ((bom ? "﻿" : "") + text).getBytes(StandardCharsets.UTF_8));
    }
}
