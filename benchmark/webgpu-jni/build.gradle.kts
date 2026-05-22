import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

val mainClassName = "com.monstrous.gdx.benchmarks.webgpu.WebGPUBenchmarkLauncher"

plugins {
    application
    id("java-library")
}

application {
    mainClass.set(mainClassName)
}

val javaVersion = project.property("javaMain") as String

sourceSets["main"].resources.srcDirs(File("../../tests/assets"))

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

dependencies {
    implementation(project(":benchmark:core"))
    implementation(project(":gdx-webgpu"))
    implementation(project(":backends:backend-desktop-jni"))
}

tasks.named<JavaExec>("run") {
    workingDir = File("../../tests/assets")
    setIgnoreExitValue(true)
    standardInput = System.`in`

    if (DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("benchmark") {
    group = "LibGDX"
    description = "Run desktop WebGPU JNI benchmarks"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = File("../../tests/assets")
    setIgnoreExitValue(true)
    standardInput = System.`in`

    if (DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}
