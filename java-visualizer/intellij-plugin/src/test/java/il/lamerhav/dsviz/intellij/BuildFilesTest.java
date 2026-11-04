package il.lamerhav.dsviz.intellij;

import junit.framework.TestCase;

/** העריכות של pom.xml / build.gradle / module-info.java — טקסט בלבד (מול mvn ו-gradle אמיתיים: tools/test/ij-build-files.sh) */
public class BuildFilesTest extends TestCase {

    // ------------------------------------------------------------------ Gradle

    public void testGradleAddsToTheMainDependenciesBlock() {
        String in = "plugins { id 'java' }\n\nrepositories { mavenCentral() }\n\ndependencies {\n    testImplementation 'junit:junit:4.13.2'\n}\n";
        String out = BuildFiles.gradle(in, false);
        assertTrue(out, out.contains("dependencies {\n    implementation files('lib/dsviz.jar')\n    testImplementation"));
        assertEquals("רק פעם אחת", 1, count(out, "dsviz.jar"));
    }

    public void testGradleKotlinDsl() {
        String out = BuildFiles.gradle("plugins { java }\ndependencies {\n}\n", true);
        assertTrue(out, out.contains("implementation(files(\"lib/dsviz.jar\"))"));
    }

    public void testGradleWithoutDependenciesBlockGetsOne() {
        String out = BuildFiles.gradle("plugins {\n    id 'java'\n}", false);
        assertTrue(out, out.endsWith("\ndependencies {\n    implementation files('lib/dsviz.jar')\n}\n"));
    }

    public void testGradleAlreadyThereAndCommentedBlock() {
        assertNull(BuildFiles.gradle("dependencies {\n    implementation files('lib/dsviz.jar')\n}\n", false));
        String out = BuildFiles.gradle("// dependencies {\n// }\nplugins { id 'java' }\n", false);
        assertTrue("בלוק בהערה לא נחשב: " + out, out.indexOf("dependencies {\n    implementation") > out.indexOf("plugins"));
    }

    public void testGradleSubprojectsBlockIsNotTheMainOne() {
        String in = "subprojects {\n    dependencies {\n        implementation 'x:y:1'\n    }\n}\n";
        String out = BuildFiles.gradle(in, false);
        assertTrue(out, out.startsWith(in));
        assertTrue("בלוק חדש בסוף, לא בתוך subprojects: " + out, out.endsWith("dependencies {\n    implementation files('lib/dsviz.jar')\n}\n"));
    }

    // ------------------------------------------------------------------ Maven

    private static final String POM = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
            + "    <modelVersion>4.0.0</modelVersion>\n    <groupId>org.example</groupId>\n    <artifactId>stam</artifactId>\n    <version>1.0</version>\n";

    public void testMavenAddsToProjectDependencies() {
        String in = POM + "    <dependencies>\n        <dependency>\n            <groupId>junit</groupId>\n            <artifactId>junit</artifactId>\n        </dependency>\n    </dependencies>\n</project>\n";
        String out = BuildFiles.maven(in);
        assertTrue(out, out.contains("<dependencies>\n        <dependency>\n            <groupId>il.lamerhav</groupId>"));
        assertTrue(out, out.contains("<systemPath>${project.basedir}/lib/dsviz.jar</systemPath>"));
        assertEquals(1, count(out, "<artifactId>dsviz</artifactId>"));
        assertTrue("junit נשאר", out.contains("<artifactId>junit</artifactId>"));
    }

    public void testMavenWithoutDependenciesGetsABlock() {
        String out = BuildFiles.maven(POM + "</project>\n");
        assertTrue(out, out.contains("    <dependencies>\n        <dependency>\n            <groupId>il.lamerhav</groupId>"));
        assertTrue(out, out.trim().endsWith("</dependencies>\n</project>"));
    }

    public void testMavenSkipsDependencyManagementBuildAndProfiles() {
        String in = POM + "    <dependencyManagement>\n        <dependencies>\n            <dependency><groupId>a</groupId><artifactId>b</artifactId></dependency>\n        </dependencies>\n    </dependencyManagement>\n"
                + "    <build>\n        <plugins><plugin><dependencies></dependencies></plugin></plugins>\n    </build>\n</project>\n";
        String out = BuildFiles.maven(in);
        int dm = out.indexOf("</dependencyManagement>");
        assertTrue("התלות מחוץ ל-dependencyManagement: " + out, out.indexOf("il.lamerhav") > dm);
        assertTrue("ולא בתוך build: " + out, out.indexOf("il.lamerhav") > out.indexOf("</build>"));
    }

    public void testMavenAlreadyThereAndCommentedDependencies() {
        assertNull(BuildFiles.maven(POM + "<dependencies><dependency><artifactId>dsviz</artifactId></dependency></dependencies></project>"));
        String out = BuildFiles.maven(POM + "    <!-- <dependencies> -->\n</project>\n");
        assertTrue("הערה לא נחשבת: " + out, out.contains("    <dependencies>\n        <dependency>"));
    }

    public void testMavenNotAPomIsLeftAlone() {
        assertNull(BuildFiles.maven("<html></html>"));
    }

    // ------------------------------------------------------------------ module-info

    public void testRequiresInsertedFirstInTheModule() {
        String out = BuildFiles.requires("module stam {\n}\n", "dsviz");
        assertEquals("module stam {\n    requires dsviz;\n}\n", out);
        assertEquals("module a.b {\n    requires dsviz;\n    requires java.desktop;\n}", BuildFiles.requires("module a.b {\n    requires java.desktop;\n}", "dsviz"));
    }

    public void testRequiresAlreadyThereOrCommentedOrNotAModule() {
        assertNull(BuildFiles.requires("module stam { requires dsviz; }", "dsviz"));
        assertNull(BuildFiles.requires("module stam { requires transitive dsviz; }", "dsviz"));
        String out = BuildFiles.requires("// requires dsviz;\nmodule stam { }", "dsviz");
        assertNotNull("שורה בהערה לא נחשבת", out);
        assertNull(BuildFiles.requires("class A {}", "dsviz"));
    }

    public void testOpenModuleAndModuleWordInComment() {
        assertEquals("open module m {\n    requires dsviz;\n}", BuildFiles.requires("open module m {\n}", "dsviz"));
        assertEquals("/* module x { */ module y {\n    requires dsviz;\n}", BuildFiles.requires("/* module x { */ module y {\n}", "dsviz"));
    }

    private static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) n++;
        return n;
    }
}
