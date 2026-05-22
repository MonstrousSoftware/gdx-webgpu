import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

val mainClassName = "com.monstrous.gdx.benchmarks.webgpu.raw.RawWebGPUSpriteBenchmarkLauncher"

plugins {
    id("java-library")
}

val javaVersion = project.property("javaMain") as String

sourceSets["main"].resources.srcDirs(File("../../../tests/assets"))

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

dependencies {
    implementation(project(":benchmark:webgpu-raw:core"))
    implementation(project(":backends:backend-desktop-jni"))
}

tasks.register<JavaExec>("run") {
    group = "LibGDX"
    description = "Run the raw WebGPU sprite benchmark through JNI"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = File("../../../tests/assets")
    setIgnoreExitValue(true)
    standardInput = System.`in`
    args("--binding=jni")
    jvmArgs("-Dbenchmark.binding=jni")

    if (DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("benchmark") {
    group = "LibGDX"
    description = "Run the raw WebGPU sprite benchmark through JNI"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = File("../../../tests/assets")
    setIgnoreExitValue(true)
    standardInput = System.`in`
    args("--binding=jni")
    jvmArgs("-Dbenchmark.binding=jni")

    if (DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}
