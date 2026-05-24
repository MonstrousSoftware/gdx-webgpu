import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.time.Instant
import java.util.concurrent.TimeUnit

data class BenchmarkReportRow(
    val backend: String,
    val test: String,
    val sprites: String,
    val vsync: String,
    val avgFps: String,
    val minFps: String,
    val maxFps: String,
    val samples: String
)

fun benchmarkProperty(name: String, defaultValue: String): String {
    return (findProperty(name) as String?) ?: defaultValue
}

fun sharedBenchmarkArgs(): List<String> {
    return listOf(
        "--test=${benchmarkProperty("benchTest", "sprite2d")}",
        "--sprites=${benchmarkProperty("benchSprites", "8191")}",
        "--seconds=${benchmarkProperty("benchSeconds", "10")}",
        "--warmup=${benchmarkProperty("benchWarmup", "2")}",
        "--width=${benchmarkProperty("benchWidth", "640")}",
        "--height=${benchmarkProperty("benchHeight", "480")}",
        "--rotate=${benchmarkProperty("benchRotate", "true")}",
        "--scale=${benchmarkProperty("benchScale", "true")}"
    )
}

fun sprite2dArgs(): List<String> {
    return listOf(
        "--test=sprite2d",
        "--sprites=${benchmarkProperty("benchSprites", "8191")}",
        "--seconds=${benchmarkProperty("benchSeconds", "10")}",
        "--warmup=${benchmarkProperty("benchWarmup", "2")}",
        "--width=${benchmarkProperty("benchWidth", "640")}",
        "--height=${benchmarkProperty("benchHeight", "480")}",
        "--rotate=${benchmarkProperty("benchRotate", "true")}",
        "--scale=${benchmarkProperty("benchScale", "true")}"
    )
}

val matrixResultsFile = layout.buildDirectory.file("benchmark-results/sprite2d-matrix/results.tsv")
val matrixReportFile = layout.buildDirectory.file("benchmark-results/sprite2d-matrix/results.md")

fun sprite2dMatrixArgs(): List<String> {
    return sprite2dArgs() + "--resultFile=${matrixResultsFile.get().asFile.absolutePath}"
}

fun parseBenchmarkReportRows(file: File): List<BenchmarkReportRow> {
    if (!file.isFile) {
        return emptyList()
    }

    return file.readLines()
        .drop(1)
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 8) {
                null
            } else {
                BenchmarkReportRow(
                    parts[0],
                    parts[1],
                    parts[2],
                    parts[3],
                    parts[4],
                    parts[5],
                    parts[6],
                    parts[7]
                )
            }
        }
}

fun writeBenchmarkMarkdownReport(resultFile: File, reportFile: File) {
    val rows = parseBenchmarkReportRows(resultFile)
    reportFile.parentFile.mkdirs()
    reportFile.writeText(buildString {
        appendLine("# SpriteBatch 2D Benchmark Matrix")
        appendLine()
        appendLine("Generated: ${Instant.now()}")
        appendLine()
        appendLine("Missing backend rows mean that run did not reach `BENCH_RESULT`; check the console output for the failure.")
        appendLine()
        appendLine("| Backend | Test | Sprites | VSync | Avg FPS | Min FPS | Max FPS | Samples |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|---:|")
        for (row in rows) {
            appendLine("| ${row.backend} | ${row.test} | ${row.sprites} | ${row.vsync} | ${row.avgFps} | ${row.minFps} | ${row.maxFps} | ${row.samples} |")
        }
        if (rows.isEmpty()) {
            appendLine()
            appendLine("No successful benchmark results were written.")
        }
    })
}

fun runtimeClasspath(projectPath: String) =
    project(projectPath).extensions.getByType(SourceSetContainer::class.java)
        .named("main").get().runtimeClasspath

fun JavaExec.configureBenchmarkProcess(projectPath: String, mainClassName: String, benchmarkArgs: List<String>) {
    mainClass.set(mainClassName)
    classpath = runtimeClasspath(projectPath)
    workingDir = file("../tests/assets")
    args(benchmarkArgs)
    setIgnoreExitValue(false)
    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err

    if (DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

fun JavaExec.configureWebgpuBenchmarkProcess(projectPath: String, benchmarkArgs: List<String>) {
    configureBenchmarkProcess(
        projectPath,
        "com.monstrous.gdx.benchmarks.webgpu.WebGPUBenchmarkLauncher",
        benchmarkArgs
    )
}

fun JavaExec.configureRawWebgpuBenchmarkProcess(projectPath: String, benchmarkArgs: List<String>) {
    configureBenchmarkProcess(
        projectPath,
        "com.monstrous.gdx.benchmarks.webgpu.raw.RawWebGPUSpriteBenchmarkLauncher",
        benchmarkArgs
    )
}

fun JavaExec.configureLwjgl3BenchmarkProcess(benchmarkArgs: List<String>) {
    configureBenchmarkProcess(
        ":benchmark:lwjgl3",
        "com.monstrous.gdx.benchmarks.lwjgl3.Lwjgl3BenchmarkLauncher",
        benchmarkArgs
    )
}

fun nativeExecutableName(imageName: String): String {
    return if (DefaultNativePlatform.getCurrentOperatingSystem().isWindows) "$imageName.exe" else imageName
}

fun benchmarkTimeoutSeconds(): Long {
    return benchmarkProperty("benchWarmup", "2").toLong() + benchmarkProperty("benchSeconds", "10").toLong() + 30
}

fun runNativeBenchmark(executable: File, workingDir: File, benchmarkArgs: List<String>, backendName: String) {
    if (!executable.isFile) {
        throw GradleException("Expected $backendName benchmark executable was not built: ${executable.absolutePath}")
    }

    val processBuilder = ProcessBuilder(listOf(executable.absolutePath) + benchmarkArgs)
        .directory(workingDir)
        .redirectErrorStream(true)
    val process = processBuilder.start()
    val outputThread = Thread {
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { println(it) }
        }
    }
    outputThread.isDaemon = true
    outputThread.start()

    val timeoutSeconds = benchmarkTimeoutSeconds()
    val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
    if (!completed) {
        process.destroyForcibly()
        process.waitFor(5, TimeUnit.SECONDS)
        outputThread.join(1000)
        throw GradleException("$backendName benchmark timed out after ${timeoutSeconds}s")
    }
    outputThread.join(1000)
    if (process.exitValue() != 0) {
        throw GradleException("$backendName benchmark failed with exit code ${process.exitValue()}")
    }
}

fun runGraalvmReleaseBenchmark(benchmarkArgs: List<String>, binding: String = "jni") {
    val graalvmProject = if (binding == "ffm") {
        project(":benchmark:graalvm:desktop-ffm")
    } else {
        project(":benchmark:graalvm:desktop-jni")
    }
    val outputDir = graalvmProject.layout.buildDirectory.dir("native/nativeReleaseCompile").get().asFile
    val executableName = if (binding == "ffm") {
        "benchmark-webgpu-graalvm-ffm-release"
    } else {
        "benchmark-webgpu-graalvm-jni-release"
    }
    runNativeBenchmark(executable = outputDir.resolve(nativeExecutableName(executableName)), workingDir = outputDir, benchmarkArgs = benchmarkArgs, backendName = "GraalVM WebGPU $binding")
}

val sharedArgs = sharedBenchmarkArgs()

val compareWebgpu = tasks.register<JavaExec>("compareWebgpu") {
    group = "LibGDX"
    description = "Run the WebGPU side of benchmark comparison"
    dependsOn(":benchmark:webgpu:desktop-jni:classes")
    configureBenchmarkProcess(
        ":benchmark:webgpu:desktop-jni",
        "com.monstrous.gdx.benchmarks.webgpu.WebGPUBenchmarkLauncher",
        sharedArgs + listOf(
            "--webgpu=${benchmarkProperty("webgpu", "WGPU")}",
            "--backend=${benchmarkProperty("nativeBackend", "DEFAULT")}",
            "--samples=${benchmarkProperty("webgpuSamples", "1")}",
            "--binding=jni"
        )
    )
}

val compareLwjgl3 = tasks.register<JavaExec>("compareLwjgl3") {
    group = "LibGDX"
    description = "Run the LWJGL3 side of benchmark comparison"
    dependsOn(":benchmark:lwjgl3:classes")
    mustRunAfter(compareWebgpu)
    configureBenchmarkProcess(
        ":benchmark:lwjgl3",
        "com.monstrous.gdx.benchmarks.lwjgl3.Lwjgl3BenchmarkLauncher",
        sharedArgs
    )
}

tasks.register("compare") {
    group = "LibGDX"
    description = "Run WebGPU and LWJGL3 benchmarks with the same shared benchmark case"
    dependsOn(compareWebgpu, compareLwjgl3)

    doLast {
        println("BENCH_COMPARE_DONE Compare the BENCH_RESULT avgFps lines above.")
    }
}

fun registerComparePreset(
    taskName: String,
    descriptionText: String,
    sharedArgs: List<String>,
    webgpuArgs: List<String>,
    webgpuProjectPath: String = ":benchmark:webgpu:desktop-jni"
) {
    val webgpuTask = tasks.register<JavaExec>("${taskName}Webgpu") {
        group = "LibGDX"
        description = "Run the WebGPU side for $taskName"
        dependsOn("$webgpuProjectPath:classes")
        configureWebgpuBenchmarkProcess(webgpuProjectPath, sharedArgs + webgpuArgs)
    }

    val lwjgl3Task = tasks.register<JavaExec>("${taskName}Lwjgl3") {
        group = "LibGDX"
        description = "Run the LWJGL3 side for $taskName"
        dependsOn(":benchmark:lwjgl3:classes")
        mustRunAfter(webgpuTask)
        configureLwjgl3BenchmarkProcess(sharedArgs)
    }

    tasks.register(taskName) {
        group = "LibGDX"
        description = descriptionText
        dependsOn(webgpuTask, lwjgl3Task)

        doLast {
            println("BENCH_COMPARE_DONE Compare the BENCH_RESULT avgFps lines above.")
        }
    }
}

fun registerWebgpuOnlyPreset(
    taskName: String,
    descriptionText: String,
    projectPath: String,
    sharedArgs: List<String>,
    webgpuArgs: List<String>
) = tasks.register<JavaExec>(taskName) {
        group = "LibGDX"
        description = descriptionText
        dependsOn("$projectPath:classes")
        configureWebgpuBenchmarkProcess(projectPath, sharedArgs + webgpuArgs)
    }

registerComparePreset(
    "compareSprite2d",
    "Compare SpriteBatch 2D with 8191 sprites on WebGPU WGPU DEFAULT and LWJGL3",
    sprite2dArgs(),
    listOf("--webgpu=WGPU", "--backend=DEFAULT", "--samples=1", "--binding=jni")
)

registerComparePreset(
    "compareSprite2dVulkan",
    "Compare SpriteBatch 2D with 8191 sprites on WebGPU WGPU VULKAN and LWJGL3",
    sprite2dArgs(),
    listOf("--webgpu=WGPU", "--backend=VULKAN", "--samples=1", "--binding=jni")
)

registerComparePreset(
    "compareSprite2dOpenGL",
    "Compare SpriteBatch 2D with 8191 sprites on WebGPU WGPU OPENGL and LWJGL3",
    sprite2dArgs(),
    listOf("--webgpu=WGPU", "--backend=OPENGL", "--samples=1", "--binding=jni")
)

registerComparePreset(
    "compareSprite2dD3D12",
    "Compare SpriteBatch 2D with 8191 sprites on WebGPU WGPU D3D12 and LWJGL3",
    sprite2dArgs(),
    listOf("--webgpu=WGPU", "--backend=D3D12", "--samples=1", "--binding=jni")
)

registerComparePreset(
    "compareSprite2dDawn",
    "Compare SpriteBatch 2D with 8191 sprites on DAWN DEFAULT and LWJGL3",
    sprite2dArgs(),
    listOf("--webgpu=DAWN", "--backend=DEFAULT", "--samples=1", "--binding=jni")
)

registerComparePreset(
    "compareSprite2dFfm",
    "Compare SpriteBatch 2D with 8191 sprites on WebGPU FFM WGPU DEFAULT and LWJGL3",
    sprite2dArgs(),
    listOf("--webgpu=WGPU", "--backend=DEFAULT", "--samples=1", "--binding=ffm"),
    ":benchmark:webgpu:desktop-ffm"
)

registerComparePreset(
    "compareSprite2dFfmVulkan",
    "Compare SpriteBatch 2D with 8191 sprites on WebGPU FFM WGPU VULKAN and LWJGL3",
    sprite2dArgs(),
    listOf("--webgpu=WGPU", "--backend=VULKAN", "--samples=1", "--binding=ffm"),
    ":benchmark:webgpu:desktop-ffm"
)

registerComparePreset(
    "compareSprite2dFfmOpenGL",
    "Compare SpriteBatch 2D with 8191 sprites on WebGPU FFM WGPU OPENGL and LWJGL3",
    sprite2dArgs(),
    listOf("--webgpu=WGPU", "--backend=OPENGL", "--samples=1", "--binding=ffm"),
    ":benchmark:webgpu:desktop-ffm"
)

registerComparePreset(
    "compareSprite2dFfmD3D12",
    "Compare SpriteBatch 2D with 8191 sprites on WebGPU FFM WGPU D3D12 and LWJGL3",
    sprite2dArgs(),
    listOf("--webgpu=WGPU", "--backend=D3D12", "--samples=1", "--binding=ffm"),
    ":benchmark:webgpu:desktop-ffm"
)

registerComparePreset(
    "compareSprite2dFfmDawn",
    "Compare SpriteBatch 2D with 8191 sprites on WebGPU FFM DAWN DEFAULT and LWJGL3",
    sprite2dArgs(),
    listOf("--webgpu=DAWN", "--backend=DEFAULT", "--samples=1", "--binding=ffm"),
    ":benchmark:webgpu:desktop-ffm"
)

tasks.register("compareSprite2dGraalvm") {
    group = "LibGDX"
    description = "Run SpriteBatch 2D on the optimized GraalVM WebGPU JNI native image"
    dependsOn(":benchmark:graalvm:desktop-jni:copyBenchmarkAssetsToReleaseNativeCompile")

    doLast {
        runGraalvmReleaseBenchmark(
            sprite2dArgs() + listOf(
                "--webgpu=${benchmarkProperty("webgpu", "WGPU")}",
                "--backend=${benchmarkProperty("nativeBackend", "DEFAULT")}",
                "--samples=${benchmarkProperty("webgpuSamples", "1")}",
                "--binding=graalvm-jni"
            )
        )
    }
}

tasks.register("compareSprite2dGraalvmFfm") {
    group = "LibGDX"
    description = "Run SpriteBatch 2D on the optimized GraalVM WebGPU FFM native image"
    dependsOn(":benchmark:graalvm:desktop-ffm:copyBenchmarkAssetsToReleaseNativeCompile")

    doLast {
        runGraalvmReleaseBenchmark(
            sprite2dArgs() + listOf(
                "--webgpu=${benchmarkProperty("webgpu", "WGPU")}",
                "--backend=${benchmarkProperty("nativeBackend", "DEFAULT")}",
                "--samples=${benchmarkProperty("webgpuSamples", "1")}",
                "--binding=graalvm-ffm"
            ),
            "ffm"
        )
    }
}

tasks.register<JavaExec>("rawSprite2dWgpuJni") {
    group = "LibGDX"
    description = "Run the isolated raw WebGPU sprite benchmark through JNI on WGPU DEFAULT"
    dependsOn(":benchmark:webgpu-raw:desktop-jni:classes")
    configureRawWebgpuBenchmarkProcess(
        ":benchmark:webgpu-raw:desktop-jni",
        sprite2dArgs() + listOf("--webgpu=WGPU", "--backend=DEFAULT", "--samples=1", "--binding=jni")
    )
}

tasks.register<JavaExec>("rawSprite2dWgpuFfm") {
    group = "LibGDX"
    description = "Run the isolated raw WebGPU sprite benchmark through FFM on WGPU DEFAULT"
    dependsOn(":benchmark:webgpu-raw:desktop-ffm:classes")
    configureRawWebgpuBenchmarkProcess(
        ":benchmark:webgpu-raw:desktop-ffm",
        sprite2dArgs() + listOf("--webgpu=WGPU", "--backend=DEFAULT", "--samples=1", "--binding=ffm")
    )
}

val matrixSprite2dJniDefault = registerWebgpuOnlyPreset(
    "matrixSprite2dJniDefault",
    "Run SpriteBatch 2D on JNI WGPU DEFAULT",
    ":benchmark:webgpu:desktop-jni",
    sprite2dMatrixArgs(),
    listOf("--webgpu=WGPU", "--backend=DEFAULT", "--samples=1", "--binding=jni")
)

val matrixSprite2dJniVulkan = registerWebgpuOnlyPreset(
    "matrixSprite2dJniVulkan",
    "Run SpriteBatch 2D on JNI WGPU VULKAN",
    ":benchmark:webgpu:desktop-jni",
    sprite2dMatrixArgs(),
    listOf("--webgpu=WGPU", "--backend=VULKAN", "--samples=1", "--binding=jni")
)

val matrixSprite2dJniOpenGL = registerWebgpuOnlyPreset(
    "matrixSprite2dJniOpenGL",
    "Run SpriteBatch 2D on JNI WGPU OPENGL",
    ":benchmark:webgpu:desktop-jni",
    sprite2dMatrixArgs(),
    listOf("--webgpu=WGPU", "--backend=OPENGL", "--samples=1", "--binding=jni")
)

val matrixSprite2dJniD3D12 = registerWebgpuOnlyPreset(
    "matrixSprite2dJniD3D12",
    "Run SpriteBatch 2D on JNI WGPU D3D12",
    ":benchmark:webgpu:desktop-jni",
    sprite2dMatrixArgs(),
    listOf("--webgpu=WGPU", "--backend=D3D12", "--samples=1", "--binding=jni")
)

val matrixSprite2dJniDawnDefault = registerWebgpuOnlyPreset(
    "matrixSprite2dJniDawnDefault",
    "Run SpriteBatch 2D on JNI DAWN DEFAULT",
    ":benchmark:webgpu:desktop-jni",
    sprite2dMatrixArgs(),
    listOf("--webgpu=DAWN", "--backend=DEFAULT", "--samples=1", "--binding=jni")
)

val matrixSprite2dFfmDefault = registerWebgpuOnlyPreset(
    "matrixSprite2dFfmDefault",
    "Run SpriteBatch 2D on FFM WGPU DEFAULT",
    ":benchmark:webgpu:desktop-ffm",
    sprite2dMatrixArgs(),
    listOf("--webgpu=WGPU", "--backend=DEFAULT", "--samples=1", "--binding=ffm")
)

val matrixSprite2dFfmVulkan = registerWebgpuOnlyPreset(
    "matrixSprite2dFfmVulkan",
    "Run SpriteBatch 2D on FFM WGPU VULKAN",
    ":benchmark:webgpu:desktop-ffm",
    sprite2dMatrixArgs(),
    listOf("--webgpu=WGPU", "--backend=VULKAN", "--samples=1", "--binding=ffm")
)

val matrixSprite2dFfmOpenGL = registerWebgpuOnlyPreset(
    "matrixSprite2dFfmOpenGL",
    "Run SpriteBatch 2D on FFM WGPU OPENGL",
    ":benchmark:webgpu:desktop-ffm",
    sprite2dMatrixArgs(),
    listOf("--webgpu=WGPU", "--backend=OPENGL", "--samples=1", "--binding=ffm")
)

val matrixSprite2dFfmD3D12 = registerWebgpuOnlyPreset(
    "matrixSprite2dFfmD3D12",
    "Run SpriteBatch 2D on FFM WGPU D3D12",
    ":benchmark:webgpu:desktop-ffm",
    sprite2dMatrixArgs(),
    listOf("--webgpu=WGPU", "--backend=D3D12", "--samples=1", "--binding=ffm")
)

val matrixSprite2dFfmDawnDefault = registerWebgpuOnlyPreset(
    "matrixSprite2dFfmDawnDefault",
    "Run SpriteBatch 2D on FFM DAWN DEFAULT",
    ":benchmark:webgpu:desktop-ffm",
    sprite2dMatrixArgs(),
    listOf("--webgpu=DAWN", "--backend=DEFAULT", "--samples=1", "--binding=ffm")
)

val matrixSprite2dLwjgl3 = tasks.register<JavaExec>("matrixSprite2dLwjgl3") {
    group = "LibGDX"
    description = "Run SpriteBatch 2D on LWJGL3"
    dependsOn(":benchmark:lwjgl3:classes")
    configureLwjgl3BenchmarkProcess(sprite2dMatrixArgs())
}

val matrixSprite2dGraalvmDefault = tasks.register("matrixSprite2dGraalvmDefault") {
    group = "LibGDX"
    description = "Run SpriteBatch 2D on GraalVM WebGPU JNI WGPU DEFAULT"
    dependsOn(":benchmark:graalvm:desktop-jni:copyBenchmarkAssetsToReleaseNativeCompile")

    doLast {
        try {
            runGraalvmReleaseBenchmark(
                sprite2dMatrixArgs() + listOf(
                    "--webgpu=WGPU",
                    "--backend=DEFAULT",
                    "--samples=1",
                    "--binding=graalvm-jni"
                )
            )
        } catch (e: GradleException) {
            logger.error("BENCH_MATRIX_BACKEND_FAILED backend=webgpu-graalvm-jni-WGPU-DEFAULT message=${e.message}")
        }
    }
}

val matrixSprite2dGraalvmFfmDefault = tasks.register("matrixSprite2dGraalvmFfmDefault") {
    group = "LibGDX"
    description = "Run SpriteBatch 2D on GraalVM WebGPU FFM WGPU DEFAULT"
    dependsOn(":benchmark:graalvm:desktop-ffm:copyBenchmarkAssetsToReleaseNativeCompile")

    doLast {
        try {
            runGraalvmReleaseBenchmark(
                sprite2dMatrixArgs() + listOf(
                    "--webgpu=WGPU",
                    "--backend=DEFAULT",
                    "--samples=1",
                    "--binding=graalvm-ffm"
                ),
                "ffm"
            )
        } catch (e: GradleException) {
            logger.error("BENCH_MATRIX_BACKEND_FAILED backend=webgpu-graalvm-ffm-WGPU-DEFAULT message=${e.message}")
        }
    }
}

val matrixSprite2dRawJniDefault = tasks.register<JavaExec>("matrixSprite2dRawJniDefault") {
    group = "LibGDX"
    description = "Run raw SpriteBatch 2D on JNI WGPU DEFAULT"
    dependsOn(":benchmark:webgpu-raw:desktop-jni:classes")
    configureRawWebgpuBenchmarkProcess(
        ":benchmark:webgpu-raw:desktop-jni",
        sprite2dMatrixArgs() + listOf("--webgpu=WGPU", "--backend=DEFAULT", "--samples=1", "--binding=jni")
    )
}

val matrixSprite2dRawFfmDefault = tasks.register<JavaExec>("matrixSprite2dRawFfmDefault") {
    group = "LibGDX"
    description = "Run raw SpriteBatch 2D on FFM WGPU DEFAULT"
    dependsOn(":benchmark:webgpu-raw:desktop-ffm:classes")
    configureRawWebgpuBenchmarkProcess(
        ":benchmark:webgpu-raw:desktop-ffm",
        sprite2dMatrixArgs() + listOf("--webgpu=WGPU", "--backend=DEFAULT", "--samples=1", "--binding=ffm")
    )
}

val prepareSprite2dMatrixReport = tasks.register("prepareSprite2dMatrixReport") {
    group = "LibGDX"
    description = "Clear previous SpriteBatch 2D matrix benchmark report data"

    doLast {
        val resultFile = matrixResultsFile.get().asFile
        val reportFile = matrixReportFile.get().asFile
        resultFile.parentFile.mkdirs()
        resultFile.delete()
        reportFile.delete()
    }
}

listOf(
    matrixSprite2dJniDefault,
    matrixSprite2dJniVulkan,
    matrixSprite2dJniOpenGL,
    matrixSprite2dJniD3D12,
    matrixSprite2dJniDawnDefault,
    matrixSprite2dFfmDefault,
    matrixSprite2dFfmVulkan,
    matrixSprite2dFfmOpenGL,
    matrixSprite2dFfmD3D12,
    matrixSprite2dFfmDawnDefault,
    matrixSprite2dLwjgl3,
    matrixSprite2dRawJniDefault,
    matrixSprite2dRawFfmDefault
).forEach { taskProvider ->
    taskProvider.configure {
        dependsOn(prepareSprite2dMatrixReport)
        setIgnoreExitValue(true)
    }
}

matrixSprite2dGraalvmDefault.configure {
    dependsOn(prepareSprite2dMatrixReport)
}

matrixSprite2dGraalvmFfmDefault.configure {
    dependsOn(prepareSprite2dMatrixReport)
}

matrixSprite2dJniVulkan.configure { mustRunAfter(matrixSprite2dJniDefault) }
matrixSprite2dJniOpenGL.configure { mustRunAfter(matrixSprite2dJniVulkan) }
matrixSprite2dJniD3D12.configure { mustRunAfter(matrixSprite2dJniOpenGL) }
matrixSprite2dJniDawnDefault.configure { mustRunAfter(matrixSprite2dJniD3D12) }
matrixSprite2dFfmDefault.configure { mustRunAfter(matrixSprite2dJniDawnDefault) }
matrixSprite2dFfmVulkan.configure { mustRunAfter(matrixSprite2dFfmDefault) }
matrixSprite2dFfmOpenGL.configure { mustRunAfter(matrixSprite2dFfmVulkan) }
matrixSprite2dFfmD3D12.configure { mustRunAfter(matrixSprite2dFfmOpenGL) }
matrixSprite2dFfmDawnDefault.configure { mustRunAfter(matrixSprite2dFfmD3D12) }
matrixSprite2dLwjgl3.configure { mustRunAfter(matrixSprite2dFfmDawnDefault) }
matrixSprite2dGraalvmDefault.configure { mustRunAfter(matrixSprite2dLwjgl3) }
matrixSprite2dGraalvmFfmDefault.configure { mustRunAfter(matrixSprite2dGraalvmDefault) }
matrixSprite2dRawJniDefault.configure { mustRunAfter(matrixSprite2dGraalvmFfmDefault) }
matrixSprite2dRawFfmDefault.configure { mustRunAfter(matrixSprite2dRawJniDefault) }

tasks.register("compareSprite2dMatrix") {
    group = "LibGDX"
    description = "Run SpriteBatch 2D matrix: JNI/FFM/GraalVM WGPU DEFAULT, JNI/FFM WGPU VULKAN/OPENGL/D3D12, DAWN DEFAULT, LWJGL3, and raw WGPU DEFAULT"
    dependsOn(
        matrixSprite2dJniDefault,
        matrixSprite2dJniVulkan,
        matrixSprite2dJniOpenGL,
        matrixSprite2dJniD3D12,
        matrixSprite2dJniDawnDefault,
        matrixSprite2dFfmDefault,
        matrixSprite2dFfmVulkan,
        matrixSprite2dFfmOpenGL,
        matrixSprite2dFfmD3D12,
        matrixSprite2dFfmDawnDefault,
        matrixSprite2dLwjgl3,
        matrixSprite2dGraalvmDefault,
        matrixSprite2dGraalvmFfmDefault,
        matrixSprite2dRawJniDefault,
        matrixSprite2dRawFfmDefault
    )

    doLast {
        val resultFile = matrixResultsFile.get().asFile
        val reportFile = matrixReportFile.get().asFile
        writeBenchmarkMarkdownReport(resultFile, reportFile)
        println("BENCH_MATRIX_DONE Report written to ${reportFile.absolutePath}")
    }
}
