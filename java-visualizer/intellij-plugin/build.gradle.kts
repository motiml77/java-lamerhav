// DSViz ל-IntelliJ IDEA (Community ו-Ultimate, 2024.2 ומעלה):  gradle buildPlugin → build/distributions/dsviz-intellij-<גרסה>.zip
// הקוד המשותף (Scaffold, NameCheck, Json, WorkerClient, SolutionStore, ClassInfo) נלקח כמו שהוא מהתוסף של Eclipse,
// והספרייה (release/dsviz.jar — tools/build-lib.sh) נכנסת לתוסף בתיקייה runtime/ (לא ב-classpath של IntelliJ עצמו).
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "il.lamerhav"
version = providers.gradleProperty("pluginVersion").get()

val viz = layout.projectDirectory.dir("..")
val libJar = viz.file("release/dsviz.jar")
val ideDir: String? = providers.gradleProperty("ideDir").orNull

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

sourceSets {
    main {
        java {
            srcDir(viz.dir("eclipse-plugin/src"))
            include("il/lamerhav/dsviz/intellij/**", "il/lamerhav/dsviz/IjShared.java",
                    "il/lamerhav/dsviz/Scaffold.java", "il/lamerhav/dsviz/NameCheck.java", "il/lamerhav/dsviz/ClassInfo.java",
                    "il/lamerhav/dsviz/Json.java", "il/lamerhav/dsviz/WorkerClient.java", "il/lamerhav/dsviz/ExerciseFiles.java", "il/lamerhav/dsviz/SolutionStore.java")
        }
    }
}

dependencies {
    intellijPlatform {
        if (ideDir != null) local(ideDir) else intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
        pluginVerifier()
        zipSigner()
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }   // בלי תקרה — גם כל הגרסאות החדשות
        }
    }
    pluginVerification {
        ides {
            if (ideDir != null) local(file(ideDir)) else ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2.6")
            // ‎-PverifyIdeDirs=dir1,dir2 — עוד גרסאות של IntelliJ (מחולצות) לבדיקה
            providers.gradleProperty("verifyIdeDirs").orNull?.split(',')?.filter { it.isNotBlank() }?.forEach { local(file(it)) }
        }
    }
}

tasks {
    withType<JavaCompile> { options.encoding = "UTF-8" }
    processResources {
        // האייקונים של Eclipse (16px + @2x), הדוגמאות הפתורות, Main.java, כתובת השרת ודף נגן ההדרכה
        from(viz.dir("eclipse-plugin/icons")) { into("icons") }
        from(viz.dir("src/examples")) { include("*.java"); into("resources/examples") }
        from(viz.file("src/Main.java")) { into("resources") }
        from(viz.file("eclipse-plugin/ai.properties")) { into("resources") }
        from(viz.dir("help")) { include("player.html", "tutorials.json"); into("resources/help") }
    }
    prepareSandbox {
        doFirst { if (!libJar.asFile.isFile) throw GradleException("חסר release/dsviz.jar — הריצו קודם tools/build-lib.sh") }
        from(libJar) { into("dsviz-intellij/runtime") }
    }
    test {
        systemProperty("dsviz.lib", libJar.asFile.absolutePath)
        systemProperty("dsviz.root", viz.asFile.absolutePath)
        // ‎-Pshots=<תיקייה> — צילומי מסך של החלונות החדשים (ModernUiTest, בלי מסך)
        providers.gradleProperty("shots").orNull?.let { systemProperty("dsviz.shots", it) }
        jvmArgs("-Djava.awt.headless=true", "-Dfile.encoding=UTF-8")
    }
    runIde {
        maxHeapSize = "1500m"
        jvmArgs("-Dide.show.tips.on.startup.default.value=false", "-Didea.trust.all.projects=true", "-Dsun.java2d.uiScale=1")
        providers.gradleProperty("runProject").orNull?.let { args(it) }
        providers.gradleProperty("autotest").orNull?.let { systemProperty("dsviz.autotest", it) }
        providers.gradleProperty("laf").orNull?.let { systemProperty("dsviz.laf", it) }
    }
}
