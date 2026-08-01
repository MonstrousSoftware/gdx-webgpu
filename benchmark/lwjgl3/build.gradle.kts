import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

val mainClassName = "com.monstrous.gdx.benchmarks.lwjgl3.Lwjgl3BenchmarkLauncher"

plugins {
    id("java-library")
}

val javaVersion = libs.versions.javaMain.get()

sourceSets["main"].resources.srcDirs(File("../../tests/assets"))

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

dependencies {
    implementation(project(":benchmark:core"))
    implementation(libs.gdxBackendLwjgl3)
    implementation(variantOf(libs.gdxPlatform) { classifier("natives-desktop") })
}

tasks.register<JavaExec>("run") {
    group = "LibGDX"
    description = "Run desktop LWJGL3 benchmarks"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = File("../../tests/assets")
    setIgnoreExitValue(true)
    standardInput = System.`in`

    if (DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("benchmark") {
    group = "LibGDX"
    description = "Run desktop LWJGL3 benchmarks"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = File("../../tests/assets")
    setIgnoreExitValue(true)
    standardInput = System.`in`

    if (DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}
