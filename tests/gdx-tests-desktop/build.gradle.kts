import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.jvm.tasks.Jar as JarTask

val mainClassName = "com.monstrous.gdx.tests.webgpu.WebGPUTestStarter"

val javaVersion = project.property("java") as String
val desktopTestArgs = (findProperty("desktopTestArgs") as String?)?.trim()
val backendDesktopFlavor = (findProperty("backendDesktopFlavor") as String?)?.trim()?.lowercase() ?: "jni"
val currentGradleJavaMajor = JavaVersion.current().majorVersion
val desktopFfmJava = (findProperty("desktopFfmJava") as String?)?.trim() ?: "24"

if (backendDesktopFlavor == "ffm") {
    configurations.configureEach {
        exclude(group = "com.github.xpenatan.jWebGPU", module = "webgpu-core")
    }
}

fun JavaExec.configureDesktopRunner() {
    group = "LibGDX"
    description = "Run the WebGPU tests"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = File("../assets")
    setIgnoreExitValue(true)
    standardInput = System.`in`
    systemProperty("gdx.webgpu.desktop.flavor", backendDesktopFlavor)

    if (!desktopTestArgs.isNullOrEmpty()) {
        args(desktopTestArgs.split("\\s+".toRegex()))
    }

    if(DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

plugins {
    application
}

application {
    mainClass = "com.monstrous.gdx.tests.webgpu.WebGPUTestStarter"
}


sourceSets["main"].resources.srcDirs(File("../assets"))

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

dependencies {
    implementation(project(":gdx-webgpu")) {
        if (backendDesktopFlavor == "ffm") {
            exclude(group = "com.github.xpenatan.jWebGPU", module = "webgpu-core")
        }
    }
    implementation(project(":backends:backend-desktop"))
    implementation(project(":tests:gdx-webgpu-tests"))
}

tasks.register<JavaExec>("gdx_webgpu_tests_run_desktop") {
    configureDesktopRunner()
}

tasks.register<GradleBuild>("gdx_webgpu_tests_run_desktop_jni") {
    group = "LibGDX"
    description = "Run the WebGPU desktop tests forcing JNI backend flavor"

    dir = rootProject.projectDir
    tasks = listOf(":tests:gdx-tests-desktop:gdx_webgpu_tests_run_desktop")
    startParameter.projectProperties = gradle.startParameter.projectProperties + buildMap {
        put("backendDesktopFlavor", "jni")
        if (!desktopTestArgs.isNullOrEmpty()) put("desktopTestArgs", desktopTestArgs)
    }
}

tasks.register<GradleBuild>("gdx_webgpu_tests_run_desktop_ffm") {
    group = "LibGDX"
    description = "Run the WebGPU desktop tests forcing FFM backend flavor"

    dir = rootProject.projectDir
    tasks = listOf(":tests:gdx-tests-desktop:gdx_webgpu_tests_run_desktop")
    startParameter.projectProperties = gradle.startParameter.projectProperties + buildMap {
        put("backendDesktopFlavor", "ffm")
        put("java", desktopFfmJava)
        if (!desktopTestArgs.isNullOrEmpty()) put("desktopTestArgs", desktopTestArgs)
    }
    doFirst {
        val currentJava = JavaVersion.current().majorVersion.toInt()
        val requiredJava = desktopFfmJava.toInt()
        if (currentJava < requiredJava) {
            throw GradleException(
                "FFM desktop mode needs Gradle JVM $requiredJava+. Current Gradle JVM is $currentJava."
            )
        }
    }
}

tasks.register<JarTask>("dist") {
    dependsOn(tasks.classes)
    manifest { attributes("Main-Class" to mainClassName) }
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    with(tasks.jar.get() as JarTask)
}
