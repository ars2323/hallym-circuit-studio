// 트랙 A: 원조 Logisim 2.7.1에서 Project › Load Library › JAR Library로 불러 쓰는 MIPS 부품 라이브러리.
// 학생 PC의 2.7.1이 어떤 JRE에서 돌지 모르므로 Java 8 바이트코드로 만들고, 외부 의존성 없는 단일 jar로 낸다.

plugins {
    java
    id("info.solidsoft.pitest") version "1.19.0" // Z-24: 돌연변이 테스트(PIT)
}

val logisimJar = rootProject.file("vendor/logisim-2.7.1/logisim-generic-2.7.1.jar")

version = "1.0.3" // 트랙 A 배포 버전(태그 v1.0.3)

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// 두 트랙 공용 코드(D-125): 실행 이미지(.hmx) 모델·파서, 디스어셈블러. Java 8, 외부 의존성 없음, GUI 없음.
// lib-mips jar(트랙 A)와 포크 앱(트랙 B, app/build.gradle.kts)에 같은 소스를 함께 컴파일한다.
sourceSets {
    main {
        java.srcDir("src/shared/java")
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

// 단위 테스트와 돌연변이 테스트(pitest)가 함께 쓰는 시스템 속성
val testProperties = mapOf(
    "java.awt.headless" to "true",
    "java.util.prefs.userRoot" to layout.buildDirectory.dir("test-prefs").get().asFile.absolutePath, // 개발자 PC의 Logisim 설정을 바꾸지 않게
    "hcs.logisimJar" to logisimJar.absolutePath,
    "hcs.mipsJar" to layout.buildDirectory.file("libs/hcs-mips.jar").get().asFile.absolutePath,
    "hcs.smokeJar" to layout.buildDirectory.file("libs/hcs-smoke.jar").get().asFile.absolutePath,
    // SPIM의 결과는 tests/spim-oracle 등에 굳혀 둔 파일로 대조한다(vendor/spim과 hcs-asm은 지웠다, D-141)
    "hcs.testsDir" to rootProject.file("tests").absolutePath,
)

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.jar, smokeJar)
    systemProperties(testProperties)
    // tests/mips/ref-mips.circ 다시 쓰기: ./gradlew :lib-mips:test -Phcs.update=true
    systemProperty("hcs.update", (findProperty("hcs.update") ?: "false").toString())
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    // D-127: 디스어셈블러 골든 대조 줄 수, D-138: Hallym MIPS 골든에서 비교한 레지스터와 비교하지 않은 것을 빌드 로그에 남긴다
    addTestOutputListener(object : TestOutputListener {
        override fun onOutput(test: TestDescriptor, event: TestOutputEvent) {
            val m = event.message
            if (m.startsWith("disasm goldens:") || m.startsWith("hmx goldens:")) logger.lifecycle(m.trim())
        }
    })
}

// Z-24(D-138): 두 트랙 공용 로더·디스어셈블러와 트랙 A 불러오기(ProgramLoader)의 돌연변이 테스트.
// ./gradlew :lib-mips:pitest. 보고서: lib-mips/build/reports/pitest/index.html.
// 죽인 돌연변이 비율이 문턱보다 낮으면 실패한다(CI Linux).
pitest {
    pitestVersion = "1.30.0"
    junit5PluginVersion = "1.2.3"
    targetClasses = setOf("kr.ac.hallym.hcs.mips.image.*", "kr.ac.hallym.hcs.mips.disasm.*",
        "kr.ac.hallym.hcs.mips.ProgramLoader*")
    // 공용 코드의 단위 테스트와 불러오기 테스트. ref-mips를 SPIM 오라클과 대조하는 RefMipsTest 같은 긴 통합 테스트는 넣지 않는다.
    targetTests = setOf("kr.ac.hallym.hcs.mips.image.*", "kr.ac.hallym.hcs.mips.disasm.*",
        "kr.ac.hallym.hcs.mips.ProgramLoaderTest", "kr.ac.hallym.hcs.mips.LoadSummaryTest",
        "kr.ac.hallym.hcs.mips.HmxConsistencyTest", "kr.ac.hallym.hcs.mips.ProgramLoadIntegrationTest",
        "kr.ac.hallym.hcs.mips.HallymMipsGoldenTest", "kr.ac.hallym.hcs.mips.MergedLoadTest")
    threads = 4
    mutationThreshold = 95 // 지금 98.7%(D-138). 남은 8개는 같은 동작(equivalent) 돌연변이다
    timestampedReports = false
    outputFormats = setOf("HTML", "XML")
    jvmArgs = testProperties.map { (k, v) -> "-D$k=$v" }
}

tasks.named("pitest") { dependsOn(tasks.jar, smokeJar) }
