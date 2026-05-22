import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

val mainClassName = "com.monstrous.gdx.benchmarks.webgpu.raw.RawWebGPUSpriteBenchmarkLauncher"

plugins {
    application
    id("java-library")
}

application {
    mainClass.set(mainClassName)
}

val javaVersion = project.property("javaFFM") as String

sourceSets["main"].resources.srcDirs(File("../../../tests/assets"))

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

dependencies {
    implementation(project(":benchmark:webgpu-raw:core"))
    implementation(project(":backends:backend-desktop-ffm"))
}

tasks.named<JavaExec>("run") {
    workingDir = File("../../../tests/assets")
    setIgnoreExitValue(true)
    standardInput = System.`in`
    args("--binding=ffm")
    jvmArgs("-Dbenchmark.binding=ffm")

    if (DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("benchmark") {
    group = "LibGDX"
    description = "Run the raw WebGPU sprite benchmark through FFM"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = File("../../../tests/assets")
    setIgnoreExitValue(true)
    standardInput = System.`in`
    args("--binding=ffm")
    jvmArgs("-Dbenchmark.binding=ffm")

    if (DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}
