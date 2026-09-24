// 트랙 B: Logisim 2.7.1 포크. app/src, app/resources, app/doc은 원본 jar에서 그대로 들여온 것이다(#3).
// 원본 jar에 소스 없이 클래스로만 들어 있던 서드파티(ColorPicker, FontChooser, JavaHelp, MRJAdapter)는
// vendor jar에서 꺼내 함께 묶는다. 엔진 소스는 원본과 같아야 한다(tools/check-engine-unchanged.sh).

plugins {
    java
    application
}

version = "0.2.0-SNAPSHOT"

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
        java.setSrcDirs(listOf("src", "src-hcs")) // src-hcs: 포크가 더한 코드(kr.ac.hallym.hcs.app)
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

application {
    mainClass = "com.cburch.logisim.Main"
}

tasks.jar {
    archiveFileName = "hallym-circuit-studio.jar"
    manifest {
        attributes("Main-Class" to "com.cburch.logisim.Main", "Implementation-Version" to project.version)
    }
    from(zipTree(thirdParty.map { it.archiveFile })) // 실행 가능한 단일 jar
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

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.jar, mipsJar)
    systemProperty("hcs.mipsJar", mipsJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("hcs.refMips", rootProject.file("tests/mips/ref-mips.circ").absolutePath)
    systemProperty("java.awt.headless", "true")
    systemProperty("hcs.forkJar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("hcs.logisimJar", logisimJar.absolutePath)
    systemProperty("hcs.circDir", rootProject.file("tests/circ").absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
