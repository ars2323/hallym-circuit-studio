// N-03: Java 엔진 서버(D-133, D-134). headless Logisim 2.7.1 위에서 stdio JSON-RPC(docs/engine-api.md)로
// 파일·모델·편집·시뮬레이션을 연다. 화면(Electron main)이 `java -jar hcs-engine.jar`로 띄운다.
// Logisim 소스와 GUI 없는 kr.ac.hallym.hcs.app.* 코드는 :app 모듈에서 그대로 쓴다(N-27에서 필요한 것만 옮긴다).

plugins {
    java
    application
}

version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":app"))
    // JSON: Gson(Apache-2.0, NOTICE). 컴파일 전용 주석 jar(error_prone_annotations)는 싣지 않는다.
    implementation("com.google.code.gson:gson:2.13.2") {
        exclude(group = "com.google.errorprone")
    }
    // D-006 저장 비교(CircNormalizer, CircEquivalence). 원조 jar는 끌어오지 않고 포크 클래스로 돈다.
    testImplementation(project(":regress")) {
        isTransitive = false
    }
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial", "-Xlint:-rawtypes", "-Xlint:-unchecked",
        "-Xlint:-deprecation", "-Xlint:-removal", "-Xlint:-processing", "-Xlint:-classfile"))
}

// ./gradlew :engine:run 은 개발용. 배포물은 단일 jar(N-04가 JRE와 함께 싼다)라 배포 묶음(zip/tar)은 만들지 않는다.
application {
    mainClass = "kr.ac.hallym.hcs.engine.Main"
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}
tasks.named("distZip") { enabled = false }
tasks.named("distTar") { enabled = false }

// 실행 가능한 단일 jar(D-134): 엔진 + 포크 클래스·Logisim 리소스 + Gson. 화면 전용 짐(도움말 HTML, FlatLaf,
// 글꼴, 캐릭터 그림)은 뺀다. hcs-mips.jar는 넣지 않고 옆에 둔다(원조와 같은 JAR 라이브러리 방식, D-007).
tasks.jar {
    archiveFileName = "hcs-engine.jar"
    manifest {
        attributes(
            "Main-Class" to "kr.ac.hallym.hcs.engine.Main",
            "Implementation-Title" to "Hallym Circuit Studio engine",
            "Implementation-Version" to project.version,
        )
    }
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.map { cp -> cp.map { if (it.isDirectory) it else zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("module-info.class", "META-INF/versions/**")
        exclude("doc/**") // Logisim 도움말(Swing Help 메뉴)
        exclude("com/formdev/**", "LICENSE-FlatLaf.txt") // FlatLaf(Swing 화면)
        exclude("kr/ac/hallym/hcs/app/fonts/**", "kr/ac/hallym/hcs/app/character/**", "kr/ac/hallym/hcs/app/logo/**")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

evaluationDependsOn(":lib-mips")
val mipsJar = project(":lib-mips").tasks.named<Jar>("jar")

// 배포 모양 그대로: hcs-engine.jar 옆에 hcs-mips.jar(엔진이 jar 옆에서 찾는다). 하위 프로세스 테스트가 쓴다.
val stage by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("stage"))
    from(tasks.jar)
    from(mipsJar)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(stage)
    systemProperty("java.awt.headless", "true")
    // 같은 JVM 안의 테스트도 엔진과 같이 메모리 전용 환경설정으로 돈다(Main이 하는 일과 같다)
    systemProperty("java.util.prefs.PreferencesFactory", "kr.ac.hallym.hcs.engine.prefs.MemoryPreferencesFactory")
    systemProperty("hcs.bundledMips", mipsJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("hcs.engineStage", layout.buildDirectory.dir("stage").get().asFile.absolutePath)
    systemProperty("hcs.refMips", rootProject.file("tests/mips/ref-mips.circ").absolutePath)
    systemProperty("hcs.circDir", rootProject.file("tests/circ").absolutePath)
    // 시작 시간·메모리 측정 결과(SubprocessTest.measureStartTimeAndMemory)
    systemProperty("hcs.measureFile", layout.buildDirectory.file("engine-measure.txt").get().asFile.absolutePath)
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    testLogging {
        events("failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
