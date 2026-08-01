import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

val mainClassName = "com.monstrous.gdx.benchmarks.webgpu.WebGPUBenchmarkLauncher"

plugins {
    id("java-library")
}

val javaVersion = libs.versions.javaFFM.get()
val jWebGPUVersion = libs.versions.jWebGPU.get()
val webgpuImplementation = ((findProperty("webgpu") as String?) ?: "WGPU").uppercase()
if (webgpuImplementation != "WGPU" && webgpuImplementation != "DAWN") {
    throw GradleException("Unsupported jWebGPU implementation: $webgpuImplementation")
}

val currentOperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
val currentArchitecture = DefaultNativePlatform.getCurrentArchitecture().name.lowercase()
val currentPlatform = when {
    currentOperatingSystem.isWindows -> "windows_x64"
    currentOperatingSystem.isLinux -> "linux_x64"
    currentOperatingSystem.isMacOsX &&
        (currentArchitecture.contains("aarch64") || currentArchitecture.contains("arm64")) -> "mac_arm64"
    currentOperatingSystem.isMacOsX -> "mac_x64"
    else -> throw GradleException(
        "Unsupported desktop platform: ${currentOperatingSystem.name} $currentArchitecture"
    )
}
val directWebgpuNativeRuntime = configurations.create("directWebgpuNativeRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

sourceSets["main"].resources.srcDirs(File("../../../tests/assets"))

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

dependencies {
    implementation(project(":benchmark:webgpu:core"))
    implementation(project(":backends:backend-desktop-ffm"))
    add(
        directWebgpuNativeRuntime.name,
        "${libs.versions.jWebGPUGroup.get()}:" +
            "webgpu-desktop-ffm-${webgpuImplementation.lowercase()}_$currentPlatform:$jWebGPUVersion"
    )
}

tasks.register<JavaExec>("run") {
    group = "LibGDX"
    description = "Run desktop WebGPU FFM benchmarks"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath + directWebgpuNativeRuntime
    workingDir = rootProject.file("tests/assets")
    setIgnoreExitValue(false)
    standardInput = System.`in`
    args("--binding=ffm", "--webgpu=$webgpuImplementation")
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "-Dbenchmark.binding=ffm",
        "-Djwebgpu.backend=$webgpuImplementation"
    )

    if (DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("benchmark") {
    group = "LibGDX"
    description = "Run desktop WebGPU FFM benchmarks"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath + directWebgpuNativeRuntime
    workingDir = rootProject.file("tests/assets")
    setIgnoreExitValue(false)
    standardInput = System.`in`
    args("--binding=ffm", "--webgpu=$webgpuImplementation")
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "-Dbenchmark.binding=ffm",
        "-Djwebgpu.backend=$webgpuImplementation"
    )

    if (DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}
