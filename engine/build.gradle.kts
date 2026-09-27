// N-03: Java 엔진 서버(D-133, D-134). headless Logisim 2.7.1 위에서 stdio JSON-RPC(docs/engine-api.md)로
// 파일·모델·편집·시뮬레이션을 연다. 화면(Electron main)이 `java -jar hcs-engine.jar`로 띄운다.
// Logisim 소스와 GUI 없는 kr.ac.hallym.hcs.app.* 코드는 :app 모듈에서 그대로 쓴다(N-27에서 필요한 것만 옮긴다).

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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
        // FlatLaf(Swing 화면)는 싣지 않으므로 그 라이선스 글도 뺀다
        exclude("com/formdev/**", "META-INF/LICENSE", "META-INF/LICENSE-FlatLaf.txt", "LICENSE-FlatLaf.txt")
        exclude("kr/ac/hallym/hcs/app/fonts/**", "kr/ac/hallym/hcs/app/character/**", "kr/ac/hallym/hcs/app/logo/**")
    }
    // Gson(Apache-2.0) 라이선스 전문. GPL 전문은 포크 클래스와 함께 COPYING.TXT로 들어간다
    from("licenses") { into("META-INF") }
    from(rootProject.file("NOTICE")) { into("META-INF") }
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

// D-129: identity hash를 ID로 쓰지 않는지 상수 identity hash JVM에서 확인할 때: ./gradlew :engine:test -Phcs.constantHash=true
// (@Tag("timing") 측정 테스트는 뺀다). 엔진 id는 동일성(==) 표의 일련번호라 해시 값에 기대지 않는다.
val constantHash = (findProperty("hcs.constantHash") ?: "false").toString() == "true"

tasks.test {
    if (constantHash) {
        useJUnitPlatform { excludeTags("timing") }
        jvmArgs("-XX:+UnlockExperimentalVMOptions", "-XX:hashCode=2")
    } else {
        useJUnitPlatform()
    }
    dependsOn(stage)
    systemProperty("java.awt.headless", "true")
    // 같은 JVM 안의 테스트도 엔진과 같이 메모리 전용 환경설정으로 돈다(Main이 하는 일과 같다)
    systemProperty("java.util.prefs.PreferencesFactory", "kr.ac.hallym.hcs.engine.prefs.MemoryPreferencesFactory")
    systemProperty("hcs.bundledMips", mipsJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("hcs.engineStage", layout.buildDirectory.dir("stage").get().asFile.absolutePath)
    systemProperty("hcs.refMips", rootProject.file("tests/mips/ref-mips.circ").absolutePath)
    systemProperty("hcs.circDir", rootProject.file("tests/circ").absolutePath)
    // 화면 가짜 엔진의 Messages 고정 답(electron/tests/fixtures/messages.json, D-143)이 이 엔진의 말과 같은지 본다
    systemProperty("hcs.electronFixtures", rootProject.file("electron/tests/fixtures").absolutePath)
    // 시작 시간·메모리 측정 결과(SubprocessTest.measureStartTimeAndMemory)
    systemProperty("hcs.measureFile", layout.buildDirectory.file("engine-measure.txt").get().asFile.absolutePath)
    // 고장 회로 v2 문구 골든(tests/circ/faults/messages.v2.*.expected) 다시 쓰기: ./gradlew :engine:test -Phcs.update=true
    systemProperty("hcs.update", (findProperty("hcs.update") ?: "false").toString())
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    testLogging {
        events("failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ---- N-04(D-142): 엔진만 돌리는 작은 Java 21 런타임 -----------------------------------------------------------
// ./gradlew :engine:runtime → build/runtime/ (이 OS용 jlink 이미지. Windows 것은 Windows에서 만든다)
//   1. jdeps로 hcs-engine.jar·hcs-mips.jar가 쓰는 모듈을 찾고, jdeps가 못 보는 두 모듈을 더한다: jdk.charsets(한국어
//      Windows의 MS949 같은 시스템 문자 집합: 명령줄의 한글 경로), jdk.unsupported(Gson이 반사로 sun.misc.Unsafe를 찾는다).
//   2. jlink --strip-debug --no-header-files --no-man-pages --generate-cds-archive. 모듈 파일은 압축하지 않는다(압축하면
//      시작이 느리고 메모리를 더 쓴다. 설치 파일은 어차피 NSIS가 압축한다). 힙이 32 GB를 넘을 때만 쓰는 CDS 파일
//      (classes_nocoops.jsa)은 뺀다.
//   3. AppCDS: 엔진을 한 번 돌려(새 파일·편집·저장·demo-datapath와 ref-mips 열기·몇 사이클) 쓴 클래스를
//      runtime/hcs-engine.jsa에 담는다. electron의 engine-locate.ts가 이 런타임으로 띄울 때 -XX:SharedArchiveFile로 준다
//      (설치 폴더로 옮겨 jar 자리가 바뀌어도 JDK 21은 공통 앞부분을 빼고 맞춰 본다).
//   4. 이 런타임과 AppCDS로 engine.hello에 답하는지 확인한다.
// ./gradlew :engine:runtimeZip → build/distributions/hcs-runtime-<os>-x64.zip (CI 산출물, 압축 크기 보고)

abstract class JlinkRuntime : DefaultTask() {
    @get:javax.inject.Inject abstract val execOps: org.gradle.process.ExecOperations
    @get:Nested abstract val launcher: Property<JavaLauncher>
    @get:InputDirectory abstract val stageDir: DirectoryProperty
    @get:InputFiles abstract val trainingCircuits: ConfigurableFileCollection
    @get:Input abstract val extraModules: ListProperty<String>
    @get:OutputDirectory abstract val output: DirectoryProperty
    @get:Internal abstract val workDir: DirectoryProperty

    private fun run(vararg cmd: String, input: String? = null): String {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val r = execOps.exec {
            commandLine(*cmd)
            standardOutput = out
            errorOutput = err
            isIgnoreExitValue = true
            if (input != null) standardInput = ByteArrayInputStream(input.toByteArray(Charsets.UTF_8))
        }
        if (r.exitValue != 0) throw GradleException("${cmd.first()} failed (${r.exitValue}):\n$out\n$err")
        return out.toString(Charsets.UTF_8)
    }

    private fun json(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @TaskAction
    fun build() {
        val home = launcher.get().metadata.installationPath.asFile
        val exe = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        val tool = { name: String -> File(home, "bin/$name$exe").absolutePath }
        // The jars as they are launched: hcs-mips.jar beside hcs-engine.jar (the packaged app's resources/engine).
        val jar = File(stageDir.get().asFile, "hcs-engine.jar")
        val mips = File(stageDir.get().asFile, "hcs-mips.jar")
        val found = run(tool("jdeps"), "--ignore-missing-deps", "--print-module-deps", "--multi-release", "21",
            jar.absolutePath, mips.absolutePath).trim()
        val modules = (found.split(",").map { it.trim() }.filter { it.isNotEmpty() } + extraModules.get()).distinct().sorted()
        val dir = output.get().asFile
        dir.walkBottomUp().forEach { it.setWritable(true); it.delete() }
        run(tool("jlink"), "--add-modules", modules.joinToString(","), "--strip-debug", "--no-header-files",
            "--no-man-pages", "--generate-cds-archive", "--output", dir.absolutePath)
        dir.walkTopDown().filter { it.name == "classes_nocoops.jsa" }.toList().forEach { it.delete() }
        // The JVM writes its archives read-only: an installer or the next build must be able to remove them.
        dir.walkTopDown().filter { it.name.endsWith(".jsa") }.forEach { it.setWritable(true) }
        val java = File(dir, "bin/java$exe").absolutePath

        // AppCDS: one run through what a student does first.  A request that fails (an id that turned out
        // different) only means fewer classes in the archive.
        val work = workDir.get().asFile
        work.deleteRecursively()
        work.mkdirs()
        val circ = trainingCircuits.files.associateBy { it.name }
        val saved = File(work, "train.circ").absolutePath
        val requests = listOf(
            """"engine.hello","params":{"client":"train","version":"0"}""",
            """"file.new","params":{}""",
            """"model.circuit","params":{"fileId":"f1","circuitId":"c1"}""",
            """"model.library","params":{"fileId":"f1"}""",
            """"edit.addComponent","params":{"fileId":"f1","circuitId":"c1","lib":"Gates","name":"AND Gate","loc":[200,200]}""",
            """"edit.addComponent","params":{"fileId":"f1","circuitId":"c1","lib":"Wiring","name":"Pin","loc":[100,190]}""",
            """"edit.addWire","params":{"fileId":"f1","circuitId":"c1","points":[[100,190],[150,190]]}""",
            """"edit.move","params":{"fileId":"f1","circuitId":"c1","ids":["k1"],"dx":10,"dy":0}""",
            """"edit.setAttr","params":{"fileId":"f1","circuitId":"c1","ids":["k2"],"attr":"width","value":"8"}""",
            """"edit.undo","params":{"fileId":"f1"}""",
            """"edit.redo","params":{"fileId":"f1"}""",
            """"sim.watch","params":{"fileId":"f1","circuitId":"c1"}""",
            """"sim.cycles","params":{"fileId":"f1","n":2}""",
            """"file.save","params":{"fileId":"f1","path":${json(saved)}}""",
            """"file.open","params":{"path":${json(circ.getValue("demo-datapath.circ").absolutePath)}}""",
            """"file.open","params":{"path":${json(circ.getValue("ref-mips.circ").absolutePath)}}""",
            """"mips.facts","params":{"fileId":"f3"}""",
            """"model.library","params":{"fileId":"f3"}""",
            """"sim.reset","params":{"fileId":"f3"}""",
            """"sim.cycles","params":{"fileId":"f3","n":20}""",
            """"sim.state","params":{"fileId":"f3"}""",
            """"file.dirty","params":{"fileId":"f3"}""",
        ).mapIndexed { i, r -> "{\"jsonrpc\":\"2.0\",\"id\":${i + 1},\"method\":$r}" }
        val archive = File(dir, "hcs-engine.jsa")
        // The flags electron/src/main/engine-locate.ts gives (hcs.bundledMips too: the engine then does not
        // look for the jar through its own code source).
        val flags = arrayOf("-Djava.awt.headless=true", "-XX:-UsePerfData", "-Xlog:disable", "-Xlog:all=warning:stderr",
            "-Djava.io.tmpdir=${work.absolutePath}", "-Djava.util.prefs.userRoot=${File(work, "prefs").absolutePath}",
            "-Dhcs.bundledMips=${mips.absolutePath}")
        val trained = run(java, "-XX:ArchiveClassesAtExit=${archive.absolutePath}", *flags, "-jar", jar.absolutePath,
            input = requests.joinToString("\n", postfix = "\n"))
        val answered = trained.lines().count { it.contains("\"result\"") }
        if (!archive.isFile) throw GradleException("the AppCDS archive was not written")
        archive.setWritable(true)
        // The runtime with its archive answers hello and opens a file that uses the MIPS library.
        val check = run(java, "-XX:SharedArchiveFile=${archive.absolutePath}", *flags, "-jar", jar.absolutePath, input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"engine.hello","params":{"client":"check","version":"0"}}""",
            """{"jsonrpc":"2.0","id":2,"method":"file.open","params":{"path":${json(circ.getValue("demo-datapath.circ").absolutePath)}}}""",
        ).joinToString("\n", postfix = "\n"))
        if (!check.contains("\"logisim\":\"2.7.1\"") || !check.contains("\"id\":2,\"result\"")) {
            throw GradleException("the runtime did not answer engine.hello and file.open:\n$check")
        }
        work.deleteRecursively()
        val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        logger.lifecycle("runtime: $dir, modules ${modules.joinToString(",")}, ${size / (1024 * 1024)} MB " +
            "(AppCDS ${archive.length() / (1024 * 1024)} MB; training answered $answered of ${requests.size})")
    }
}

val runtime = tasks.register<JlinkRuntime>("runtime") {
    group = "distribution"
    description = "Builds the engine's own Java 21 runtime (jlink, AppCDS) for this OS into build/runtime"
    launcher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    stageDir = layout.dir(stage.map { it.destinationDir })
    trainingCircuits.from(rootProject.file("tests/mips/ref-mips.circ"), rootProject.file("tests/circ/demo-datapath.circ"))
    extraModules = listOf("jdk.charsets", "jdk.unsupported")
    output = layout.buildDirectory.dir("runtime")
    workDir = layout.buildDirectory.dir("tmp/runtime-train")
    dependsOn(stage)
}

tasks.register<Zip>("runtimeZip") {
    group = "distribution"
    description = "Zips build/runtime (the CI artifact; its size is the one reported)"
    val os = System.getProperty("os.name")
    archiveFileName = "hcs-runtime-${if (os.startsWith("Windows")) "windows" else if (os.startsWith("Mac")) "macos" else "linux"}-x64.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from(runtime) { into("runtime") }
}
