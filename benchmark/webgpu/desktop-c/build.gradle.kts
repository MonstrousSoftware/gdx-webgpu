import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.teavm.gradle.api.OptimizationLevel

plugins {
    id("java-library")
    alias(libs.plugins.gdxTeaVM)
}

val javaVersion = JavaVersion.toVersion(libs.versions.javaWeb.get())
val webgpuCBackend = providers.gradleProperty("webgpuCBackend")
    .orElse("wgpu")
    .map { it.lowercase() }
    .get()
if(webgpuCBackend !in setOf("wgpu", "dawn")) {
    throw GradleException("Unsupported webgpuCBackend '$webgpuCBackend'. Expected 'wgpu' or 'dawn'.")
}

val hostOs = System.getProperty("os.name").lowercase()
val hostArch = System.getProperty("os.arch").lowercase()
val isX64 = hostArch in setOf("amd64", "x86_64", "x64")
val isArm64 = hostArch in setOf("aarch64", "arm64")
val webgpuCPlatform = when {
    (hostOs.contains("mac") || hostOs.contains("darwin")) && isX64 -> "mac_x64"
    (hostOs.contains("mac") || hostOs.contains("darwin")) && isArm64 -> "mac_arm64"
    hostOs.startsWith("windows") && isX64 -> "windows_x64"
    hostOs.contains("linux") && isX64 -> "linux_x64"
    else -> throw GradleException("Unsupported desktop TeaVM C host: os.name='$hostOs', os.arch='$hostArch'")
}

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

dependencies {
    implementation(project(":benchmark:core"))
    implementation(project(":backends:backend-desktop-c"))
    implementation(
        "${libs.versions.jWebGPUGroup.get()}:" +
            "webgpu-desktop-c-${webgpuCBackend}_$webgpuCPlatform:${libs.versions.jWebGPU.get()}"
    )
}

val benchmarkAssetsDirectory = layout.buildDirectory.dir("benchmark-assets")
val prepareBenchmarkAssets = tasks.register<Sync>("prepareBenchmarkAssets") {
    from(file("../../../tests/assets/data/badlogicsmall.jpg"))
    into(benchmarkAssetsDirectory.map { it.dir("data") })
}

tasks.named("generateC") {
    dependsOn(prepareBenchmarkAssets)
}

gdxTeaVM {
    assets.from(benchmarkAssetsDirectory)

    glfw {
        mainClass.set("com.monstrous.gdx.benchmarks.webgpu.teavmc.TeaVMCWebGPUBenchmarkLauncher")
        outputDir.set(layout.buildDirectory.dir("dist/$webgpuCBackend/$webgpuCPlatform"))
        targetFileName.set("gdx_webgpu_benchmark")
        optimization.set(OptimizationLevel.BALANCED)
        debugInformation.set(false)
        obfuscated.set(false)
        minHeapSizeMb.set(64)
        maxHeapSizeMb.set(512)
        buildType.set("Release")
        consoleLog.set(true)
        if(hostOs.startsWith("windows")) {
            cmakeDefinition("CMAKE_MSVC_RUNTIME_LIBRARY", "MultiThreaded")
            cmakeDefinition("JPARSER_JWEBGPU_TEAVMC_LINKAGE", "SHARED_LINKED")
        }
    }
}

fun benchmarkProperty(name: String, defaultValue: String): String {
    return (findProperty(name) as String?) ?: defaultValue
}

val benchmarkArgs = listOf(
    "--test=${benchmarkProperty("benchTest", "sprite2d")}",
    "--sprites=${benchmarkProperty("benchSprites", "8191")}",
    "--seconds=${benchmarkProperty("benchSeconds", "10")}",
    "--warmup=${benchmarkProperty("benchWarmup", "2")}",
    "--width=${benchmarkProperty("benchWidth", "640")}",
    "--height=${benchmarkProperty("benchHeight", "480")}",
    "--rotate=${benchmarkProperty("benchRotate", "true")}",
    "--scale=${benchmarkProperty("benchScale", "true")}",
    "--backend=${benchmarkProperty("nativeBackend", "DEFAULT")}",
    "--samples=${benchmarkProperty("webgpuSamples", "1")}"
)

val releaseDirectory = layout.buildDirectory.dir("dist/$webgpuCBackend/$webgpuCPlatform/c/release")
val executableSuffix = if(hostOs.startsWith("windows")) ".exe" else ""
val benchmarkExecutable = releaseDirectory.map {
    it.file("gdx_webgpu_benchmark_release$executableSuffix")
}

fun Exec.configureBenchmarkExecution(resultFile: String? = null) {
    args(benchmarkArgs)
    if(!resultFile.isNullOrBlank()) {
        args("--resultFile=$resultFile")
    }

    doFirst {
        val executableFile = benchmarkExecutable.get().asFile
        if(!executableFile.isFile) {
            throw GradleException("Expected TeaVM-C benchmark executable was not built: ${executableFile.absolutePath}")
        }
        workingDir(executableFile.parentFile)
        executable(executableFile)
    }
}

tasks.register<Exec>("benchmark") {
    group = "LibGDX"
    description = "Run an already-built WebGPU TeaVM-C benchmark with configurable arguments"
    configureBenchmarkExecution()
}

tasks.register<Exec>("gdx_teavm_glfw_benchmark") {
    group = "LibGDX"
    description = "Generate, build, and run the WebGPU TeaVM-C benchmark with configurable arguments"
    dependsOn("gdx_teavm_glfw_build")
    configureBenchmarkExecution(providers.gradleProperty("benchResultFile").orNull)
}
