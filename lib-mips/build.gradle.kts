// 트랙 A: 원조 Logisim 2.7.1에서 Project › Load Library › JAR Library로 불러 쓰는 MIPS 부품 라이브러리.
// 학생 PC의 2.7.1이 어떤 JRE에서 돌지 모르므로 Java 8 바이트코드로 만들고, 외부 의존성 없는 단일 jar로 낸다.

plugins {
    java
}

val logisimJar = rootProject.file("vendor/logisim-2.7.1/logisim-generic-2.7.1.jar")

version = "0.1.0" // 트랙 A 배포 버전(태그 v0.1.0)

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// smoke: JAR 라이브러리 방식 자체를 확인하는 최소 라이브러리(docs/jar-library.md). 배포하지 않는다.
val smoke by sourceSets.creating

dependencies {
    compileOnly(files(logisimJar))
    "smokeCompileOnly"(files(logisimJar))

    testImplementation(files(logisimJar))
    testImplementation(project(":regress")) // CircNormalizer
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:-options") // --release 8 경고
}

tasks.named<JavaCompile>("compileJava") { options.release = 8 }
tasks.named<JavaCompile>("compileSmokeJava") { options.release = 8 }
tasks.named<JavaCompile>("compileTestJava") { options.release = 21 }

tasks.jar {
    archiveFileName = "hcs-mips.jar"
    manifest {
        attributes(
            "Library-Class" to "kr.ac.hallym.hcs.mips.MipsLibrary",
            "Implementation-Title" to "Hallym MIPS component library",
            "Implementation-Version" to project.version,
        )
    }
}

val smokeJar by tasks.registering(Jar::class) {
    archiveFileName = "hcs-smoke.jar"
    destinationDirectory = layout.buildDirectory.dir("libs")
    from(smoke.output)
    manifest {
        attributes("Library-Class" to "kr.ac.hallym.hcs.smoke.SmokeLibrary")
    }
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.jar, smokeJar)
    systemProperty("java.awt.headless", "true")
    systemProperty("java.util.prefs.userRoot", layout.buildDirectory.dir("test-prefs").get().asFile.absolutePath) // 개발자 PC의 Logisim 설정을 바꾸지 않게
    systemProperty("hcs.logisimJar", logisimJar.absolutePath)
    systemProperty("hcs.mipsJar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("hcs.smokeJar", smokeJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("hcs.testsDir", rootProject.file("tests").absolutePath)
    // hcs-asm과 원본 spim 오라클은 make -C native/hcs-asm oracle 로 먼저 빌드한다(tools/ci-local.sh 순서).
    systemProperty("hcs.asm", rootProject.file("native/hcs-asm/build/hcs-asm").absolutePath)
    systemProperty("hcs.spimOracle", rootProject.file("native/hcs-asm/build/oracle/spim").absolutePath)
    systemProperty("hcs.spimDir", rootProject.file("vendor/spim-9.1.24").absolutePath)
    // tests/mips/ref-mips.circ 다시 쓰기: ./gradlew :lib-mips:test -Phcs.update=true
    systemProperty("hcs.update", (findProperty("hcs.update") ?: "false").toString())
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
