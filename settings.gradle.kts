plugins {
    // JDK toolchain을 자동으로 내려받는다(CLAUDE.md 5절: JDK를 sudo로 설치하지 않는다).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "hallym-circuit-studio"

include("app")
include("lib-mips")
include("regress")
project(":regress").projectDir = file("tests/regress")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
