// 엔진 회귀 테스트(PLAN.md 8.3): tests/circ/*.circ를 표준 Logisim 2.7.1 jar의 -tty table로 돌린 결과를
// 기대값으로 저장하고, 같은 입력을 다시 돌려 비교한다. 포크(app/)가 빌드되면 포크 jar도 같은 기대값과 비교한다(#19).
// 학생 PC의 원조 2.7.1과 같은 조건이 되도록 JDK 8에서 돌린다.

plugins {
    java
    application
}

val logisimJar = rootProject.file("vendor/logisim-2.7.1/logisim-generic-2.7.1.jar")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

dependencies {
    implementation(files(logisimJar))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

application {
    // ./gradlew :regress:run --args="generate"  회로를 다시 만든다
    // ./gradlew :regress:run --args="update"    기대값을 표준 jar 결과로 다시 쓴다
    mainClass = "kr.ac.hallym.hcs.regress.Regress"
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    systemProperty("hcs.logisimJar", logisimJar.absolutePath)
    systemProperty("hcs.circDir", rootProject.file("tests/circ").absolutePath)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    systemProperty("java.util.prefs.userRoot", layout.buildDirectory.dir("test-prefs").get().asFile.absolutePath) // 개발자 PC의 Logisim 설정을 바꾸지 않게
    systemProperty("hcs.logisimJar", logisimJar.absolutePath)
    systemProperty("hcs.circDir", rootProject.file("tests/circ").absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
