import org.gradle.api.GradleException
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.jvm.tasks.Jar as JarTask

val mainClassName = "com.monstrous.gdx.tests.webgpu.WebGPUTestStarter"

plugins {
    id("java-library")
}

val javaVersion = project.property("javaFFM") as String

sourceSets["main"].resources.srcDirs(File("../assets"))

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

dependencies {
    implementation(project(":gdx-webgpu"))
    implementation(project(":backends:backend-desktop-ffm"))
    implementation(project(":tests:gdx-webgpu-tests"))
}

val currentDesktopOperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
val currentDesktopArchitecture = DefaultNativePlatform.getCurrentArchitecture().name.lowercase()
val currentDesktopPlatform = when {
    currentDesktopOperatingSystem.isWindows -> "windows_x64"
    currentDesktopOperatingSystem.isLinux -> "linux_x64"
    currentDesktopOperatingSystem.isMacOsX &&
        (currentDesktopArchitecture.contains("aarch64") || currentDesktopArchitecture.contains("arm64")) -> "mac_arm64"
    currentDesktopOperatingSystem.isMacOsX -> "mac_x64"
    else -> throw GradleException(
        "Unsupported desktop platform: ${currentDesktopOperatingSystem.name} $currentDesktopArchitecture"
    )
}
val jWebGPUVersion = project.property("jWebGPUVVersion") as String

tasks.register<JavaExec>("gdx_webgpu_tests_desktop_ffm_wgpu_run") {
    group = "LibGDX"
    description = "Run the WebGPU FFM tests with WGPU"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath + configurations.detachedConfiguration(
        dependencies.create(
            "com.github.xpenatan.jWebGPU:webgpu-desktop-ffm-wgpu_$currentDesktopPlatform:$jWebGPUVersion"
        )
    )
    workingDir = File("../assets")
    setIgnoreExitValue(true)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("jwebgpu.backend", "WGPU")
    standardInput = System.`in`

    if(currentDesktopOperatingSystem.isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("gdx_webgpu_tests_desktop_ffm_dawn_run") {
    group = "LibGDX"
    description = "Run the WebGPU FFM tests with Dawn"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath + configurations.detachedConfiguration(
        dependencies.create(
            "com.github.xpenatan.jWebGPU:webgpu-desktop-ffm-dawn_$currentDesktopPlatform:$jWebGPUVersion"
        )
    )
    workingDir = File("../assets")
    setIgnoreExitValue(true)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("jwebgpu.backend", "DAWN")
    standardInput = System.`in`

    if(currentDesktopOperatingSystem.isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("gdx_webgpu_tests_auto_desktop_ffm_wgpu_run") {
    group = "LibGDX"
    description = "Run all WebGPU FFM tests automatically with WGPU"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath + configurations.detachedConfiguration(
        dependencies.create(
            "com.github.xpenatan.jWebGPU:webgpu-desktop-ffm-wgpu_$currentDesktopPlatform:$jWebGPUVersion"
        )
    )
    workingDir = File("../assets")
    setIgnoreExitValue(true)
    args("auto")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("jwebgpu.backend", "WGPU")
    standardInput = System.`in`

    if(currentDesktopOperatingSystem.isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("gdx_webgpu_tests_auto_desktop_ffm_dawn_run") {
    group = "LibGDX"
    description = "Run all WebGPU FFM tests automatically with Dawn"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath + configurations.detachedConfiguration(
        dependencies.create(
            "com.github.xpenatan.jWebGPU:webgpu-desktop-ffm-dawn_$currentDesktopPlatform:$jWebGPUVersion"
        )
    )
    workingDir = File("../assets")
    setIgnoreExitValue(true)
    args("auto")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("jwebgpu.backend", "DAWN")
    standardInput = System.`in`

    if(currentDesktopOperatingSystem.isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JarTask>("dist_wgpu") {
    dependsOn(tasks.classes)
    manifest { attributes("Main-Class" to mainClassName) }
    archiveClassifier.set("wgpu")
    val runtimeClasspath = configurations.runtimeClasspath.get() + configurations.detachedConfiguration(
        dependencies.create(
            "com.github.xpenatan.jWebGPU:webgpu-desktop-ffm-wgpu_$currentDesktopPlatform:$jWebGPUVersion"
        )
    )
    dependsOn(runtimeClasspath)
    from({ runtimeClasspath.map { if (it.isDirectory) it else zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    with(tasks.jar.get() as JarTask)
}

tasks.register<JarTask>("dist_dawn") {
    dependsOn(tasks.classes)
    manifest { attributes("Main-Class" to mainClassName) }
    archiveClassifier.set("dawn")
    val runtimeClasspath = configurations.runtimeClasspath.get() + configurations.detachedConfiguration(
        dependencies.create(
            "com.github.xpenatan.jWebGPU:webgpu-desktop-ffm-dawn_$currentDesktopPlatform:$jWebGPUVersion"
        )
    )
    dependsOn(runtimeClasspath)
    from({ runtimeClasspath.map { if (it.isDirectory) it else zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    with(tasks.jar.get() as JarTask)
}
