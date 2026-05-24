import java.util.concurrent.TimeUnit

plugins {
    id("java-library")
    id("org.graalvm.buildtools.native") version "1.1.0"
}

val mainClassName = "com.monstrous.gdx.benchmarks.graalvm.GraalVMWebGPUBenchmarkLauncher"
val nativeImageName = "benchmark-webgpu-graalvm-jni"
val assetsDir = file("../../../tests/assets")
val benchmarkTexture = file("../../../tests/assets/data/badlogicsmall.jpg")
val gdxVersion = project.property("gdxVersion") as String
val javaVersion = project.property("javaMain") as String
val lwjglVersion = "3.3.3"
val graalvmJavaVersion = JavaVersion.current().majorVersion.toInt()
val nativeImageThreads = providers.gradleProperty("nativeImageThreads").orElse("2")
val nativeImageBuilderMaxHeap = providers.gradleProperty("nativeImageBuilderMaxHeap").orElse("4g")
val graalVmHomeEnv = providers.environmentVariable("GRAALVM_HOME").orElse(providers.environmentVariable("JAVA_HOME"))

fun benchmarkProperty(name: String, defaultValue: String): String {
    return (findProperty(name) as String?) ?: defaultValue
}

fun benchmarkArgs(): List<String> {
    return listOf(
        "--test=${benchmarkProperty("benchTest", "sprite2d")}",
        "--sprites=${benchmarkProperty("benchSprites", "8191")}",
        "--seconds=${benchmarkProperty("benchSeconds", "10")}",
        "--warmup=${benchmarkProperty("benchWarmup", "2")}",
        "--width=${benchmarkProperty("benchWidth", "640")}",
        "--height=${benchmarkProperty("benchHeight", "480")}",
        "--rotate=${benchmarkProperty("benchRotate", "true")}",
        "--scale=${benchmarkProperty("benchScale", "true")}",
        "--webgpu=${benchmarkProperty("webgpu", "WGPU")}",
        "--backend=${benchmarkProperty("nativeBackend", "DEFAULT")}",
        "--samples=${benchmarkProperty("webgpuSamples", "1")}",
        "--binding=graalvm-jni"
    )
}

fun benchmarkTimeoutSeconds(): Long {
    return benchmarkProperty("benchWarmup", "2").toLong() + benchmarkProperty("benchSeconds", "10").toLong() + 30
}

fun lwjglNativesClassifier(): String {
    val os = org.gradle.internal.os.OperatingSystem.current()
    val arch = System.getProperty("os.arch").lowercase()
    val isArm64 = arch == "aarch64" || arch == "arm64"
    val isX86 = arch == "x86" || arch == "i386"

    return when {
        os.isWindows -> if(isX86) "natives-windows-x86" else "natives-windows"
        os.isMacOsX -> if(isArm64) "natives-macos-arm64" else "natives-macos"
        os.isLinux -> if(isArm64) "natives-linux-arm64" else "natives-linux"
        else -> throw GradleException("Unsupported LWJGL native platform: ${System.getProperty("os.name")} $arch")
    }
}

fun nativeLibraryIncludePatterns(): List<String> {
    val os = org.gradle.internal.os.OperatingSystem.current()
    return when {
        os.isWindows -> listOf("*.dll", "**/*.dll")
        os.isMacOsX -> listOf("*.dylib", "**/*.dylib")
        os.isLinux -> listOf("*.so", "**/*.so")
        else -> throw GradleException("Unsupported native library platform: ${System.getProperty("os.name")}")
    }
}

fun gdxWindowsNativeLibraryName(): String {
    val arch = System.getProperty("os.arch").lowercase()
    val isX86 = arch == "x86" || arch == "i386"
    return if(isX86) "gdx.dll" else "gdx64.dll"
}

fun nativeExecutableName(imageName: String): String {
    return if(org.gradle.internal.os.OperatingSystem.current().isWindows) "$imageName.exe" else imageName
}

val lwjglNatives = lwjglNativesClassifier()

if(JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

dependencies {
    implementation(project(":benchmark:webgpu:core"))
    implementation(project(":backends:backend-desktop-jni"))
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")

    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-jemalloc:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-openal:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjglVersion:$lwjglNatives")
}

tasks.register<JavaExec>("benchmarkJvm") {
    group = "benchmark"
    description = "Run WebGPU benchmark through JNI on a GraalVM JVM"
    dependsOn("classes")
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = assetsDir
    args(benchmarkArgs())
    standardInput = System.`in`

    if(org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

graalvmNative {
    toolchainDetection.set(false)
    metadataRepository {
        enabled.set(false)
    }
    binaries {
        named("main") {
            imageName.set(nativeImageName)
            mainClass.set(mainClassName)
            if(!graalVmHomeEnv.isPresent) {
                javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(graalvmJavaVersion))
                })
            }
            fallback.set(false)
            resources.autodetect()
            classpath(sourceSets["main"].runtimeClasspath)
            jvmArgs("-Xmx${nativeImageBuilderMaxHeap.get()}")
            buildArgs.addAll(
                "-H:+ReportExceptionStackTraces",
                "--parallelism=${nativeImageThreads.get()}"
            )
        }
        create("release") {
            imageName.set("$nativeImageName-release")
            mainClass.set(mainClassName)
            if(!graalVmHomeEnv.isPresent) {
                javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(graalvmJavaVersion))
                })
            }
            fallback.set(false)
            resources.autodetect()
            classpath(sourceSets["main"].runtimeClasspath)
            jvmArgs("-Xmx${nativeImageBuilderMaxHeap.get()}")
            buildArgs.addAll(
                "-H:+ReportExceptionStackTraces",
                "--parallelism=${nativeImageThreads.get()}",
                "-O3",
                "-march=native"
            )
        }
    }
    agent {
        enabled.set(false)
        metadataCopy {
            inputTaskNames.add("benchmarkJvm")
            outputDirectories.add("src/main/resources/META-INF/native-image/gdx-webgpu/benchmark-graalvm")
            mergeWithExisting.set(true)
        }
    }
}

val releaseNativeImageName = "$nativeImageName-release"
val releaseNativeExecutableName = nativeExecutableName(releaseNativeImageName)
val releaseNativeOutputDir = layout.buildDirectory.dir("native/nativeReleaseCompile")
val releaseNativeExecutable = releaseNativeOutputDir.map { it.file(releaseNativeExecutableName) }

val copyNativeLibrariesToReleaseNativeCompile = tasks.register<Copy>("copyNativeLibrariesToReleaseNativeCompile") {
    dependsOn("nativeReleaseCompile")
    group = "benchmark"
    description = "Copy native libraries next to the GraalVM WebGPU JNI benchmark executable"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    includeEmptyDirs = false
    doFirst {
        delete(fileTree(releaseNativeOutputDir) {
            include("*.dll", "*.so", "*.dylib")
        })
    }
    from({
        sourceSets["main"].runtimeClasspath.files
            .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
            .map { zipTree(it) }
    }) {
        nativeLibraryIncludePatterns().forEach { include(it) }
        exclude("native/**")
        exclude("runtime*.dll", "libruntime*.so", "libruntime*.dylib")
        exclude {
            val fileName = it.file.name
            (fileName == "gdx.dll" || fileName == "gdx64.dll") && fileName != gdxWindowsNativeLibraryName()
        }
        eachFile {
            path = name
        }
    }
    into(releaseNativeOutputDir)
}

val copyBenchmarkAssetsToReleaseNativeCompile = tasks.register<Copy>("copyBenchmarkAssetsToReleaseNativeCompile") {
    dependsOn("nativeReleaseCompile", copyNativeLibrariesToReleaseNativeCompile)
    group = "benchmark"
    description = "Copy benchmark assets next to the GraalVM WebGPU JNI benchmark executable"
    from(benchmarkTexture)
    into(releaseNativeOutputDir.map { it.dir("data") })
}

tasks.register("buildRelease") {
    group = "benchmark"
    description = "Build the optimized GraalVM WebGPU JNI benchmark executable and copy runtime files"
    dependsOn(copyBenchmarkAssetsToReleaseNativeCompile)
}

tasks.register("benchmarkRelease") {
    group = "benchmark"
    description = "Build and run the optimized GraalVM WebGPU JNI benchmark executable"
    dependsOn(copyBenchmarkAssetsToReleaseNativeCompile)

    doLast {
        val outputDir = releaseNativeOutputDir.get().asFile
        val executable = releaseNativeExecutable.get().asFile
        if(!executable.isFile) {
            throw GradleException("Expected GraalVM WebGPU JNI benchmark executable was not built: ${executable.absolutePath}")
        }

        val processBuilder = ProcessBuilder(listOf(executable.absolutePath) + benchmarkArgs())
            .directory(outputDir)
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
        if(!completed) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
            outputThread.join(1000)
            throw GradleException("GraalVM WebGPU JNI benchmark timed out after ${timeoutSeconds}s")
        }
        outputThread.join(1000)
        if(process.exitValue() != 0) {
            throw GradleException("GraalVM WebGPU JNI benchmark failed with exit code ${process.exitValue()}")
        }
    }
}
