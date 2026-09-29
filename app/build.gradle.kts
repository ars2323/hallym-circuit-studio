// 트랙 B: Logisim 2.7.1 포크. app/src, app/resources는 원본 jar에서 그대로 들여온 것이다(#3).
// 원본 jar에 소스 없이 클래스로만 들어 있던 서드파티(ColorPicker, FontChooser, JavaHelp, MRJAdapter)는
// vendor jar에서 꺼내 함께 묶는다. 엔진 소스는 원본과 같아야 한다(tools/check-engine-unchanged.sh).
// N-27(D-163): Swing 화면(v1 앱)은 지웠다(옛 코드는 태그 swing-final). 이 모듈은 이제 엔진(engine/)이 쓰는 라이브러리다:
// 원조 Logisim 소스와 GUI 없는 kr.ac.hallym.hcs.app.* 코드(넷·진단·기록·경로·식별자·등록표·확장 정보). 실행 jar를
// 만들지 않는다.

plugins {
    `java-library`
}

val logisimJar = rootProject.file("vendor/logisim-2.7.1/logisim-generic-2.7.1.jar")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// 원본 jar의 서드파티 클래스와 그 리소스만 꺼낸 jar
val thirdParty by tasks.registering(Jar::class) {
    archiveFileName = "logisim-2.7.1-third-party.jar"
    destinationDirectory = layout.buildDirectory.dir("third-party")
    from(zipTree(logisimJar)) {
        include("com/bric/**", "com/connectina/**", "com/sun/java/help/**", "javax/help/**", "net/roydesign/**")
    }
}

sourceSets {
    main {
        // src-hcs: 포크가 더한 코드(kr.ac.hallym.hcs.app). lib-mips/src/shared/java: 두 트랙 공용 코드(D-125)
        java.setSrcDirs(listOf("src", "src-hcs", rootProject.file("lib-mips/src/shared/java")))
        resources.setSrcDirs(listOf("."))
        resources.include("resources/**")
    }
    test {
        java.setSrcDirs(listOf("src-test")) // src/는 원본 소스 트리라 테스트는 따로 둔다
        resources.setSrcDirs(emptyList<String>())
    }
}

dependencies {
    implementation(files(thirdParty))
    testImplementation(project(":regress"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    from("src-hcs") { include("**/*.properties") } // 포크 문구 번들은 코드 옆에 둔다
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    // 2011년 코드: raw type·deprecated 경고는 원본 그대로 둔다
    options.compilerArgs.addAll(listOf("-Xlint:none", "-nowarn"))
}

evaluationDependsOn(":lib-mips")
val mipsJar = project(":lib-mips").tasks.named<Jar>("jar")

// 시험 전용: 포크 클래스를 원조 시작점(com.cburch.logisim.Main)으로 돌리는 jar. 엔진 회귀(ForkEngineRegressionTest)와
// .circ 라이브러리 시험이 원조 jar와 같은 방법(-tty table)으로 돌린다. 배포하지 않는다(D-163).
val ttyJar by tasks.registering(Jar::class) {
    archiveFileName = "fork-tty.jar"
    destinationDirectory = layout.buildDirectory.dir("tty")
    manifest {
        attributes("Main-Class" to "com.cburch.logisim.Main")
    }
    from(sourceSets["main"].output)
    from(zipTree(thirdParty.map { it.archiveFile }))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// D-129: 모든 객체의 identity hash가 같은 상수인 JVM. System.identityHashCode는 고유하지 않으므로 열쇠·서명으로
// 쓰면 이 JVM에서 드러난다(촬영 도구가 같은 옵션으로 돈다).
val constantIdentityHash = listOf("-XX:+UnlockExperimentalVMOptions", "-XX:hashCode=2")

// 단위 테스트 설정.
// prefs·config: 환경설정 폴더 이름(build/ 아래). 같은 테스트를 다른 JVM 옵션으로 도는 작업은 따로 둔다.
fun Test.hcsTestSetup(headless: Boolean, prefs: String = "test-prefs", config: String = "test-config") {
    dependsOn(mipsJar, ttyJar)
    // 같은 곳에서 되풀이된 예외에도 스택을 남긴다: 동시성 테스트가 원조 자체의 경합을 스택으로 가려낸다(D-143, LogisimRace)
    jvmArgs("-XX:-OmitStackTraceInFastThrow")
    // 상수 identity hash로 한 번 돌려 볼 때: ./gradlew :app:test -Phcs.constantHash=true (D-129)
    if ((findProperty("hcs.constantHash") ?: "false").toString() == "true") {
        jvmArgs(constantIdentityHash)
    }
    systemProperty("hcs.mipsJar", mipsJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("hcs.refMips", rootProject.file("tests/mips/ref-mips.circ").absolutePath)
    systemProperty("java.awt.headless", headless.toString())
    // 문구 기대값은 영어 기준(V-02). 한국어 문구는 UiLanguageTest·FaultCollectionTest가 따로 고른다.
    // 언어를 바꾼 테스트가 환경설정에 남기므로 매번 비운 채 시작한다.
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    doFirst { delete(layout.buildDirectory.dir(prefs)) }
    // Logisim은 언어 등을 Java 환경설정에 저장한다. 테스트가 개발자 PC의 설정을 바꾸지 않게 따로 둔다.
    systemProperty("java.util.prefs.userRoot", layout.buildDirectory.dir(prefs).get().asFile.absolutePath)
    systemProperty("hcs.configDir", layout.buildDirectory.dir(config).get().asFile.absolutePath)
    systemProperty("hcs.forkJar", ttyJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("hcs.logisimJar", logisimJar.absolutePath)
    systemProperty("hcs.circDir", rootProject.file("tests/circ").absolutePath)
    // 기록 엔진 테스트는 굳혀 둔 실행 이미지(tests/hmx)를 올린다(hcs-asm은 없어졌다, D-141)
    systemProperty("hcs.testsDir", rootProject.file("tests").absolutePath)
    // tests/circ/demo-datapath.circ 다시 쓰기: ./gradlew :app:test -Phcs.update=true
    systemProperty("hcs.update", (findProperty("hcs.update") ?: "false").toString())
    // 편집 동등성 골든(N-01, tests/parity): -Dparity.update=true로 다시 쓰기, -Dparity.only=장면,…로 일부만
    for (p in listOf("parity.update", "parity.only")) {
        (findProperty(p) ?: System.getProperty(p))?.let { systemProperty(p, it.toString()) }
    }
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.test {
    useJUnitPlatform { excludeTags("gui") }
    hcsTestSetup(headless = true)
}

// D-129: 단위 테스트 전체를 identity hash가 모두 같은 JVM에서 한 번 더 돈다. identityHashCode를 열쇠·방문 표시·서명으로
// 쓰면(고유하지 않다) 여기서 틀린다. CI Linux 작업이 보통 테스트 뒤에 돌린다. 벽시계 시간을 재는 @Tag("timing")은 뺀다:
// 이 JVM에서는 원조 엔진의 해시 표도 한 칸에 몰려 ref-mips가 수십 배 느리게 돌아 시간 상한이 뜻이 없다.
val testConstantIdentityHash by tasks.registering(Test::class) {
    description = "Runs the unit tests with every identity hash code equal (-XX:hashCode=2, D-129)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { excludeTags("gui", "timing") }
    hcsTestSetup(headless = true, prefs = "hash-test-prefs", config = "hash-test-config")
    jvmArgs(constantIdentityHash)
    mustRunAfter(tasks.test)
}
