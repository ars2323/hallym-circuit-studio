// 트랙 B: Logisim 2.7.1 포크. app/src, app/resources, app/doc은 원본 jar에서 그대로 들여온 것이다(#3).
// 원본 jar에 소스 없이 클래스로만 들어 있던 서드파티(ColorPicker, FontChooser, JavaHelp, MRJAdapter)는
// vendor jar에서 꺼내 함께 묶는다. 엔진 소스는 원본과 같아야 한다(tools/check-engine-unchanged.sh).

plugins {
    java
    application
}

version = "1.0.3"

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
        resources.include("resources/**", "doc/**")
    }
    test {
        java.setSrcDirs(listOf("src-test")) // src/는 원본 소스 트리라 테스트는 따로 둔다
        resources.setSrcDirs(emptyList<String>())
    }
}

dependencies {
    implementation(files(thirdParty))
    implementation("com.formdev:flatlaf:3.7.2") // Apache-2.0, NOTICE
    testImplementation(project(":regress"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    from("src-hcs") { include("**/*.properties") } // 포크 문구 번들은 코드 옆에 둔다
    // Help › Examples(V-07): 사람이 그린 예제 회로를 번들한다(tests/circ와 같은 파일)
    from(rootProject.file("tests/circ")) {
        include("demo-datapath.circ", "console-demo.circ", "stack-demo.circ")
        into("kr/ac/hallym/hcs/app/examples")
    }
    from(rootProject.file("assets/fonts/pretendard")) { into("kr/ac/hallym/hcs/app/fonts") } // OFL, LICENSE.txt 포함
    // 첫 실행 안내의 캐릭터(한림대학교 소유, 원본 그대로). 쓰는 두 장만 넣는다.
    from(rootProject.file("assets/hallym/character")) {
        include("haram-hari-greeting.png", "haram-hari-ok.png", "haram-hari.png")
        into("kr/ac/hallym/hcs/app/character")
    }
    // About 창과 앱 아이콘(E-11·E-12): 학교 엠블럼과 앱 아이콘(원형 그대로 만든 파생 PNG), 라이선스·고지 원문
    from(rootProject.file("assets/hallym/logo")) {
        include("app-*.png", "emblem-a-navy-112.png", "emblem-a-navy-112@2x.png")
        into("kr/ac/hallym/hcs/app/logo")
    }
    from(rootProject.files("LICENSE", "NOTICE")) { into("kr/ac/hallym/hcs/app/about") }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    // 2011년 코드: raw type·deprecated 경고는 원본 그대로 둔다
    options.compilerArgs.addAll(listOf("-Xlint:none", "-nowarn"))
}

application {
    mainClass = "com.cburch.logisim.Main"
}

tasks.jar {
    archiveFileName = "hallym-circuit-studio.jar"
    manifest {
        attributes(
            "Main-Class" to "com.cburch.logisim.Main",
            "Implementation-Version" to project.version,
            "Multi-Release" to "true", // FlatLaf의 META-INF/versions/9 클래스
        )
    }
    from(zipTree(thirdParty.map { it.archiveFile })) // 실행 가능한 단일 jar
    from(configurations.runtimeClasspath.map { cp -> cp.filter { it.name.startsWith("flatlaf") }.map { zipTree(it) } }) {
        exclude("META-INF/versions/**/module-info.class", "module-info.class", "META-INF/MANIFEST.MF")
        rename("^LICENSE$", "LICENSE-FlatLaf.txt") // Apache-2.0 전문
    }
    from(rootProject.file("LICENSE")) { rename { "COPYING.TXT" } }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

evaluationDependsOn(":lib-mips")
val mipsJar = project(":lib-mips").tasks.named<Jar>("jar")

// 개발용 배치: 포크 jar 옆 lib/에 번들 라이브러리(hcs-mips.jar)와 hcs-asm을 둔다(D-007). 배포 zip은 2c(#29).
val stage by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("stage"))
    from(tasks.jar)
    into("lib") {
        from(mipsJar)
        from(rootProject.file("native/hcs-asm/build")) { include("hcs-asm", "hcs-asm.exe") }
    }
}

// D-129: 모든 객체의 identity hash가 같은 상수인 JVM. System.identityHashCode는 고유하지 않으므로 열쇠·서명으로
// 쓰면 이 JVM에서 드러난다(촬영 도구가 같은 옵션으로 돈다).
val constantIdentityHash = listOf("-XX:+UnlockExperimentalVMOptions", "-XX:hashCode=2")

// 단위 테스트와 GUI 테스트(@Tag("gui"), 화면이 필요)가 함께 쓰는 설정.
// prefs·config: 환경설정 폴더 이름(build/ 아래). 같은 테스트를 다른 JVM 옵션으로 도는 작업은 따로 둔다.
fun Test.hcsTestSetup(headless: Boolean, prefs: String = "test-prefs", config: String = "test-config") {
    dependsOn(tasks.jar, mipsJar)
    // GUI 테스트 등을 상수 identity hash로 한 번 돌려 볼 때: ./gradlew :app:guiTest -Phcs.constantHash=true (D-129)
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
    systemProperty("hcs.forkJar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("hcs.logisimJar", logisimJar.absolutePath)
    systemProperty("hcs.circDir", rootProject.file("tests/circ").absolutePath)
    systemProperty("hcs.testsDir", rootProject.file("tests").absolutePath)
    // 기록 엔진 테스트가 .s를 어셈블한다(make -C native/hcs-asm 먼저, tools/ci-local.sh 순서)
    systemProperty("hcs.asm", rootProject.file("native/hcs-asm/build/hcs-asm").absolutePath)
    // tests/circ/demo-datapath.circ 다시 쓰기: ./gradlew :app:test -Phcs.update=true
    systemProperty("hcs.update", (findProperty("hcs.update") ?: "false").toString())
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

// GUI 스모크 테스트(PLAN.md 11.16): 실제 창을 만든다. 화면이 필요하다: xvfb-run -a ./gradlew :app:guiTest
val guiTest by tasks.registering(Test::class) {
    description = "Runs GUI tests (@Tag(\"gui\")); needs a display such as Xvfb."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("gui") }
    hcsTestSetup(headless = false)
    systemProperty("java.util.prefs.userRoot", layout.buildDirectory.dir("gui-test-prefs").get().asFile.absolutePath)
}
