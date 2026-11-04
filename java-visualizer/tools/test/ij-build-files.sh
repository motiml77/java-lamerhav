#!/bin/sh
# מה שהתוסף של IntelliJ כותב ב-pom.xml / build.gradle / module-info.java — מול mvn, gradle ו-javac אמיתיים:
# הפרויקט (Main עם Queue מהספרייה) נבנה ורץ אחרי העריכה, ולא נבנה לפניה. דורש mvn ו-gradle (ורשת — Maven Central / plugins.gradle.org).
#   sh tools/test/ij-build-files.sh        (אחרי tools/build-all.sh)
set -e
cd "$(dirname "$0")"
ROOT="$(cd ../.. && pwd)"
W="$ROOT/build/test-ij-build"
JAR="$ROOT/release/dsviz.jar"
rm -rf "$W" && mkdir -p "$W/cls"
javac -encoding UTF-8 -d "$W/cls" "$ROOT/intellij-plugin/src/main/java/il/lamerhav/dsviz/intellij/BuildFiles.java" ij-build/BuildFilesCli.java 2>&1 | grep -v "Picked up" || true
edit() { java -cp "$W/cls" il.lamerhav.dsviz.intellij.BuildFilesCli "$@" 2>&1 | grep -v "Picked up"; }
MAIN='package app;
import unit4.collectionsLib.Queue;
public class Main {
    public static void main(String[] args) {
        Queue<Integer> q = new Queue<Integer>();
        q.insert(4); q.insert(7);
        System.out.println("תור " + q.remove() + q.head());
    }
}
'
proj() {   # proj <שם> : תיקייה עם Main והספרייה ב-lib
    mkdir -p "$W/$1/src/main/java/app" "$W/$1/lib" && printf '%s' "$MAIN" > "$W/$1/src/main/java/app/Main.java" && cp "$JAR" "$W/$1/lib/dsviz.jar"
}
FAIL=0
# ---- Maven
proj mvn
cat > "$W/mvn/pom.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.example</groupId>
    <artifactId>stam</artifactId>
    <version>1.0</version>
    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
</project>
XML
if (cd "$W/mvn" && mvn -q -B compile > "$W/mvn-before.log" 2>&1); then echo "  FAIL Maven: נבנה גם בלי התלות"; FAIL=1; else grep -q "package unit4.collectionsLib does not exist" "$W/mvn-before.log" && echo "  ok   Maven לפני: לא נבנה — package unit4.collectionsLib does not exist" || { echo "  FAIL Maven לפני: כשל אחר"; tail -15 "$W/mvn-before.log"; FAIL=1; }; fi
edit maven "$W/mvn/pom.xml" > "$W/mvn/pom.new" && mv "$W/mvn/pom.new" "$W/mvn/pom.xml"
(cd "$W/mvn" && mvn -q -B compile > "$W/mvn-after.log" 2>&1) && [ "$(java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp "$W/mvn/target/classes:$JAR" app.Main 2>&1 | grep -v 'Picked up')" = "תור 47" ] && echo "  ok   Maven אחרי העריכה של התוסף: נבנה ורץ (תור 47)" || { echo "  FAIL Maven אחרי העריכה"; tail -20 "$W/mvn-after.log"; FAIL=1; }
# ---- Gradle (Groovy ו-Kotlin)
for kind in groovy kotlin; do
    proj "gr-$kind"
    if [ $kind = groovy ]; then
        printf "plugins {\n    id 'java'\n}\n\nrepositories {\n    mavenCentral()\n}\n\ntasks.withType(JavaCompile) {\n    options.encoding = 'UTF-8'\n}\n" > "$W/gr-$kind/build.gradle"; f=build.gradle; mode=gradle
    else
        printf 'plugins {\n    java\n}\n\nrepositories {\n    mavenCentral()\n}\n\ntasks.withType<JavaCompile> {\n    options.encoding = "UTF-8"\n}\n' > "$W/gr-$kind/build.gradle.kts"; f=build.gradle.kts; mode=kts
    fi
    echo "rootProject.name = 'stam'" > "$W/gr-$kind/settings.gradle"
    if (cd "$W/gr-$kind" && gradle --no-daemon -q compileJava > "$W/gr-$kind-before.log" 2>&1); then echo "  FAIL Gradle ($kind): נבנה גם בלי התלות"; FAIL=1; else grep -q "does not exist" "$W/gr-$kind-before.log" && echo "  ok   Gradle ($kind) לפני: לא נבנה" || { echo "  FAIL Gradle ($kind) לפני: כשל אחר"; tail -10 "$W/gr-$kind-before.log"; FAIL=1; }; fi
    edit $mode "$W/gr-$kind/$f" > "$W/gr-$kind/$f.new" && mv "$W/gr-$kind/$f.new" "$W/gr-$kind/$f"
    (cd "$W/gr-$kind" && gradle --no-daemon -q compileJava > "$W/gr-$kind-after.log" 2>&1) && [ "$(java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp "$W/gr-$kind/build/classes/java/main:$JAR" app.Main 2>&1 | grep -v 'Picked up')" = "תור 47" ] \
        && echo "  ok   Gradle ($kind) אחרי העריכה של התוסף: נבנה ורץ (תור 47)" || { echo "  FAIL Gradle ($kind) אחרי העריכה"; tail -20 "$W/gr-$kind-after.log"; FAIL=1; }
done
# ---- module-info: javac עם הספרייה ב-module-path (שם הקובץ dsviz.jar — מודול אוטומטי "dsviz")
mkdir -p "$W/mod/src/app" "$W/mod/lib" && cp "$JAR" "$W/mod/lib/dsviz.jar" && printf '%s' "$MAIN" > "$W/mod/src/app/Main.java"
printf 'module stam {\n}\n' > "$W/mod/src/module-info.java"
if javac --release 21 -cp "$W/mod/lib/dsviz.jar" -d "$W/mod/out0" "$W/mod/src/module-info.java" "$W/mod/src/app/Main.java" > "$W/mod-before.log" 2>&1; then echo "  FAIL module-info: נבנה בלי requires"; FAIL=1; else grep -q "is not visible" "$W/mod-before.log" && echo "  ok   module-info לפני: package unit4.collectionsLib is not visible" || { echo "  FAIL module-info לפני: כשל אחר"; cat "$W/mod-before.log"; FAIL=1; }; fi
edit requires "$W/mod/src/module-info.java" > "$W/mod/mi.new" && mv "$W/mod/mi.new" "$W/mod/src/module-info.java"
javac --release 21 -p "$W/mod/lib" -d "$W/mod/out" "$W/mod/src/module-info.java" "$W/mod/src/app/Main.java" > "$W/mod-after.log" 2>&1 \
    && [ "$(java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp "$W/mod/out:$JAR" app.Main 2>&1 | grep -v 'Picked up')" = "תור 47" ] && echo "  ok   module-info אחרי requires dsviz: נבנה, וגם רץ ב-classpath רגיל (כמו ההרצה הוויזואלית) — תור 47" || { echo "  FAIL module-info אחרי requires"; cat "$W/mod-after.log"; FAIL=1; }
# ---- פרויקט שהקוד שלו לא משתמש בספרייה בכלל: התוסף לא נוגע בו, הוא נבנה כמו שהוא, והריצה הוויזואלית (LineByLine מה-jar של התוסף) עובדת
PLAIN='package app;
public class Main {
    public static void main(String[] args) {
        int sum = 0;
        for (int i = 1; i <= 3; i++) sum += i;
        System.out.println("סכום " + sum);
    }
}
'
visual() {   # visual <שם> <תיקיית classes> : מריץ ב-LineByLine כמו "הרצה ויזואלית" ובודק שהצעדים והפלט הגיעו
    out="$(cd "$W/$1" && timeout 120 java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -Ddsviz.visual=true -cp "$2:$JAR" unit4.collectionsLib.LineByLine app.Main 2>&1 | grep -v 'Picked up')"
    echo "$out" | grep -q "^סכום 6" && echo "$out" | grep -q "התוכנית הסתיימה" && echo "$out" | grep -q "Main.java:5" && echo "  ok   $3: נבנה כמו שהוא (בלי התלות ובלי lib), והריצה הוויזואלית עובדת — סכום 6, צעדים ושורות" || { echo "  FAIL $3: הריצה הוויזואלית"; echo "$out" | head -15; FAIL=1; }
}
plain() {   # plain <שם> : תיקייה עם Main שלא משתמש בספרייה, בלי lib
    mkdir -p "$W/$1/src/main/java/app" && printf '%s' "$PLAIN" > "$W/$1/src/main/java/app/Main.java"
}
untouched() {   # untouched <שם> <קובץ> : אין dsviz בקובץ ואין תיקיית lib
    ! grep -qi dsviz "$W/$1/$2" && [ ! -e "$W/$1/lib" ]
}
plain nolib-mvn
cat > "$W/nolib-mvn/pom.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.example</groupId>
    <artifactId>stam</artifactId>
    <version>1.0</version>
    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
</project>
XML
(cd "$W/nolib-mvn" && mvn -q -B compile > "$W/nolib-mvn.log" 2>&1) && untouched nolib-mvn pom.xml && visual nolib-mvn target/classes "Maven בלי הספרייה" || { echo "  FAIL Maven בלי הספרייה"; tail -15 "$W/nolib-mvn.log"; FAIL=1; }
for kind in groovy kotlin; do
    plain "nolib-gr-$kind"
    if [ $kind = groovy ]; then
        printf "plugins {\n    id 'java'\n}\n\nrepositories {\n    mavenCentral()\n}\n\ntasks.withType(JavaCompile) {\n    options.encoding = 'UTF-8'\n}\n" > "$W/nolib-gr-$kind/build.gradle"; f=build.gradle
    else
        printf 'plugins {\n    java\n}\n\nrepositories {\n    mavenCentral()\n}\n\ntasks.withType<JavaCompile> {\n    options.encoding = "UTF-8"\n}\n' > "$W/nolib-gr-$kind/build.gradle.kts"; f=build.gradle.kts
    fi
    echo "rootProject.name = 'stam'" > "$W/nolib-gr-$kind/settings.gradle"
    (cd "$W/nolib-gr-$kind" && gradle --no-daemon -q compileJava > "$W/nolib-gr-$kind.log" 2>&1) && untouched "nolib-gr-$kind" "$f" && visual "nolib-gr-$kind" build/classes/java/main "Gradle ($kind) בלי הספרייה" || { echo "  FAIL Gradle ($kind) בלי הספרייה"; tail -15 "$W/nolib-gr-$kind.log"; FAIL=1; }
done
plain nolib-mod
mv "$W/nolib-mod/src/main/java/app" "$W/nolib-mod/app" && rm -rf "$W/nolib-mod/src" && printf 'module stam {\n}\n' > "$W/nolib-mod/module-info.java"
(cd "$W/nolib-mod" && javac -encoding UTF-8 --release 21 -d out module-info.java app/Main.java > "$W/nolib-mod.log" 2>&1) && [ "$(cat "$W/nolib-mod/module-info.java")" = "$(printf 'module stam {\n}')" ] && [ ! -e "$W/nolib-mod/lib" ] && visual nolib-mod out "module-info בלי הספרייה" || { echo "  FAIL module-info בלי הספרייה"; cat "$W/nolib-mod.log"; FAIL=1; }
[ $FAIL = 0 ]
