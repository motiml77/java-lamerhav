#!/usr/bin/env python3
"""
בונה תבנית פרויקט ל-Visual Studio (קובץ zip שמעתיקים ל-Documents\\Visual Studio <שנה>\\Templates\\ProjectTemplates):
"תרגיל מבני נתונים (DSViz)" — פרויקט קונסול עם הספרייה Unit4.CollectionsLib, תרגיל מחסנית מוכן,
ופרופיל הרצה "הרצה ויזואלית" ליד כפתור ההפעלה.
שימוש: make-vs-template.py <Unit4.CollectionsLib.dll> <פרויקט התרגיל (csharp/project/Exercise)> <קובץ zip לפלט>
"""
import sys, zipfile, os

lib, exercise, out = sys.argv[1:4]
program = open(os.path.join(exercise, 'Program.cs'), encoding='utf-8').read()
launch = open(os.path.join(exercise, 'Properties', 'launchSettings.json'), encoding='utf-8').read()

CSPROJ = '''<Project Sdk="Microsoft.NET.Sdk">

  <PropertyGroup>
    <OutputType>Exe</OutputType>
    <TargetFramework>net8.0</TargetFramework>
    <ImplicitUsings>disable</ImplicitUsings>
    <Nullable>disable</Nullable>
    <RootNamespace>$safeprojectname$</RootNamespace>
  </PropertyGroup>

  <ItemGroup>
    <!-- הספרייה של מבני הנתונים (Stack, Queue, Node, BinNode, Student, Car) -->
    <Reference Include="Unit4.CollectionsLib">
      <HintPath>lib\\Unit4.CollectionsLib.dll</HintPath>
    </Reference>
  </ItemGroup>

</Project>
'''

VSTEMPLATE = '''<?xml version="1.0" encoding="utf-8"?>
<VSTemplate Version="3.0.0" xmlns="http://schemas.microsoft.com/developer/vstemplate/2005" Type="Project">
  <TemplateData>
    <Name>תרגיל מבני נתונים (DSViz)</Name>
    <Description>פרויקט קונסול עם הספרייה Unit4.CollectionsLib (Stack, Queue, Node, BinNode, Student, Car) ותרגיל מחסנית מוכן. ליד כפתור ההפעלה הירוק בוחרים "הרצה ויזואלית".</Description>
    <ProjectType>CSharp</ProjectType>
    <SortOrder>1000</SortOrder>
    <CreateNewFolder>true</CreateNewFolder>
    <DefaultName>Targil</DefaultName>
    <ProvideDefaultName>true</ProvideDefaultName>
    <LocationField>Enabled</LocationField>
    <EnableLocationBrowseButton>true</EnableLocationBrowseButton>
    <CreateInPlace>true</CreateInPlace>
    <LanguageTag>csharp</LanguageTag>
    <PlatformTag>windows</PlatformTag>
    <ProjectTypeTag>console</ProjectTypeTag>
  </TemplateData>
  <TemplateContent>
    <Project TargetFileName="$safeprojectname$.csproj" File="Targil.csproj" ReplaceParameters="true">
      <ProjectItem ReplaceParameters="false" TargetFileName="Program.cs">Program.cs</ProjectItem>
      <Folder Name="lib" TargetFolderName="lib">
        <ProjectItem ReplaceParameters="false" TargetFileName="Unit4.CollectionsLib.dll">Unit4.CollectionsLib.dll</ProjectItem>
      </Folder>
      <Folder Name="Properties" TargetFolderName="Properties">
        <ProjectItem ReplaceParameters="false" TargetFileName="launchSettings.json">launchSettings.json</ProjectItem>
      </Folder>
    </Project>
  </TemplateContent>
</VSTemplate>
'''

os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('DSVizExercise.vstemplate', VSTEMPLATE.encode('utf-8-sig'))
    z.writestr('Targil.csproj', CSPROJ.encode('utf-8'))
    z.writestr('Program.cs', program.encode('utf-8-sig'))
    z.writestr('launchSettings.json', launch.encode('utf-8'))
    z.write(lib, 'Unit4.CollectionsLib.dll')
print(out)
