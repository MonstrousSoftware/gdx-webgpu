import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.jvm.tasks.Jar as JarTask

val mainClassName = "com.monstrous.gdx.tests.webgpu.WebGPUTestStarter"

val javaVersion = project.property("java") as String

plugins {
    application
    id("java-library")
}

application {
    mainClass = "com.monstrous.gdx.tests.webgpu.WebGPUTestStarter"
}


sourceSets["main"].resources.srcDirs(File("../assets"))

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(24)
    }
}

dependencies {
    implementation(project(":gdx-webgpu"))
    implementation(project(":backends:backend-desktop"))
    implementation(project(":tests:gdx-webgpu-tests"))

    val jWebGPUVVersion = project.property("jWebGPUVVersion") as String

    // Add natives
    val useFFM = true
    if(useFFM) {
        implementation("com.github.xpenatan.jWebGPU:webgpu-desktop-ffm:$jWebGPUVVersion")
        implementation("com.github.xpenatan.jWebGPU:webgpu-desktop-ffm:$jWebGPUVVersion:windows_64_dawn")
        implementation("com.github.xpenatan.jWebGPU:webgpu-desktop-ffm:$jWebGPUVVersion:windows_64_wgpu")
        implementation("com.github.xpenatan.jWebGPU:webgpu-desktop-ffm:$jWebGPUVVersion:linux_64_wgpu")
        implementation("com.github.xpenatan.jWebGPU:webgpu-desktop-ffm:$jWebGPUVVersion:mac_arm64_wgpu")
        implementation("com.github.xpenatan.jWebGPU:webgpu-desktop-ffm:$jWebGPUVVersion:mac_64_wgpu")
    }
    else {
        implementation("com.github.xpenatan.jWebGPU:webgpu-desktop-jni:$jWebGPUVVersion")
        implementation("com.github.xpenatan.jWebGPU:webgpu-desktop-jni:$jWebGPUVVersion:windows_64_dawn")
        implementation("com.github.xpenatan.jWebGPU:webgpu-desktop-jni:$jWebGPUVVersion:windows_64_wgpu")
        implementation("com.github.xpenatan.jWebGPU:webgpu-desktop-jni:$jWebGPUVVersion:linux_64_wgpu")
        implementation("com.github.xpenatan.jWebGPU:webgpu-desktop-jni:$jWebGPUVVersion:mac_arm64_wgpu")
        implementation("com.github.xpenatan.jWebGPU:webgpu-desktop-jni:$jWebGPUVVersion:mac_64_wgpu")
    }
}

tasks.register<JavaExec>("gdx_webgpu_tests_run_desktop") {
    group = "LibGDX"
    description = "Run the WebGPU tests"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = File("../assets")
    setIgnoreExitValue(true)
    standardInput = System.`in`

    if(DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
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
