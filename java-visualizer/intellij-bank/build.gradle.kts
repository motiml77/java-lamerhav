// "מאגר תרגילים" ל-IntelliJ IDEA — תוסף לבדיקה בלבד (לא בהתקנה לתלמידים), בתיקייה משלו (העדכון האוטומטי של DSViz לא נוגע בו):
//   gradle buildPlugin → build/distributions/dsviz-bank-intellij-<גרסה>.zip  (Settings → Plugins → ⚙ → Install Plugin from Disk)
// תלוי בתוסף הראשי (il.lamerhav.dsviz): Java (JdkFinder), dsviz.jar, ההצעות לפתרון, וההרצה הוויזואלית (VisualRunner).
// החלונות עצמם — אותו כלי כמו ב-Visual Studio (dsviz-bank-vs.jar, במצב --ij), בתהליך נפרד, עם גשר מקומי ל-IntelliJ.
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "il.lamerhav"
version = providers.gradleProperty("pluginVersion").get()

val viz = layout.projectDirectory.dir("..")
val mainZip = viz.file("intellij-plugin/build/distributions/dsviz-intellij-1.0.1.zip")
val bankJar = viz.file("build/lab-vs/dsviz-bank-vs.jar")
val libJar = viz.file("release/dsviz.jar")
val ideDir: String? = providers.gradleProperty("ideDir").orNull

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    intellijPlatform {
        if (ideDir != null) local(ideDir) else intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.java")
        localPlugin(mainZip.asFile.path)
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
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            if (ideDir != null) local(file(ideDir)) else ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2.6")
        }
    }
}

tasks {
    withType<JavaCompile> { options.encoding = "UTF-8" }
    processResources {
        from(viz.dir("eclipse-bank/icons")) { include("bank*.png", "question*.png"); into("icons") }
    }
    prepareSandbox {
        doFirst { if (!bankJar.asFile.isFile) throw GradleException("חסר build/lab-vs/dsviz-bank-vs.jar — מריצים קודם tools/build-bank-vs.sh") }
        from(bankJar) { into("dsviz-bank-intellij/runtime") }
    }
    test {
        systemProperty("dsviz.lib", libJar.asFile.absolutePath)
        systemProperty("dsviz.bankJar", bankJar.asFile.absolutePath)
        jvmArgs("-Djava.awt.headless=true", "-Dfile.encoding=UTF-8")
    }
    runIde {
        maxHeapSize = "1500m"
        jvmArgs("-Dide.show.tips.on.startup.default.value=false", "-Didea.trust.all.projects=true", "-Dsun.java2d.uiScale=1",
            "-Djb.privacy.policy.text=<!--999.999-->", "-Djb.consents.confirmation.enabled=false", "-Didea.initially.ask.config=never")
        providers.gradleProperty("runProject").orNull?.let { args(it) }
        // התוסף הראשי מוצא את dsviz.jar לפי המקום של ה-jar שלו — כשהתוספים נטענים מתיקייה (runIde) זה לא עובד; נותנים לו את הנתיב
        systemProperty("dsviz.lib", libJar.asFile.absolutePath)
        providers.gradleProperty("bankAutotest").orNull?.let { systemProperty("dsviz.bankAutotest", it) }
        providers.gradleProperty("bankAutotestCp").orNull?.let { systemProperty("dsviz.bankAutotestCp", it) }
    }
}
