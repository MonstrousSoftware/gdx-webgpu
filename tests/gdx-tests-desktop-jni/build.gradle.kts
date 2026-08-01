import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.jvm.tasks.Jar as JarTask

val mainClassName = "com.monstrous.gdx.tests.webgpu.WebGPUTestStarter"

plugins {
    id("java-library")
}

val javaVersion = libs.versions.javaMain.get()

sourceSets["main"].resources.srcDirs(File("../assets"))

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

dependencies {
    implementation(project(":gdx-webgpu"))
    implementation(project(":backends:backend-desktop-jni"))
    implementation(project(":tests:gdx-webgpu-tests"))
}

val currentDesktopOperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()

tasks.register<JavaExec>("gdx_webgpu_tests_desktop_jni_wgpu_run") {
    group = "LibGDX"
    description = "Run the WebGPU JNI tests with WGPU"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = File("../assets")
    setIgnoreExitValue(true)
    systemProperty("jwebgpu.backend", "WGPU")
    standardInput = System.`in`

    if(currentDesktopOperatingSystem.isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("gdx_webgpu_tests_desktop_jni_dawn_run") {
    group = "LibGDX"
    description = "Run the WebGPU JNI tests with Dawn"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = File("../assets")
    setIgnoreExitValue(true)
    systemProperty("jwebgpu.backend", "DAWN")
    standardInput = System.`in`

    if(currentDesktopOperatingSystem.isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("gdx_webgpu_tests_auto_desktop_jni_wgpu_run") {
    group = "LibGDX"
    description = "Run all WebGPU JNI tests automatically with WGPU"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = File("../assets")
    setIgnoreExitValue(true)
    args("auto")
    systemProperty("jwebgpu.backend", "WGPU")
    standardInput = System.`in`

    if(currentDesktopOperatingSystem.isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("gdx_webgpu_tests_auto_desktop_jni_dawn_run") {
    group = "LibGDX"
    description = "Run all WebGPU JNI tests automatically with Dawn"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = File("../assets")
    setIgnoreExitValue(true)
    args("auto")
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
    val runtimeClasspath = configurations.runtimeClasspath.get()
    dependsOn(runtimeClasspath)
    from({ runtimeClasspath.map { if (it.isDirectory) it else zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    with(tasks.jar.get() as JarTask)
}

tasks.register<JarTask>("dist_dawn") {
    dependsOn(tasks.classes)
    manifest { attributes("Main-Class" to mainClassName) }
    archiveClassifier.set("dawn")
    val runtimeClasspath = configurations.runtimeClasspath.get()
    dependsOn(runtimeClasspath)
    from({ runtimeClasspath.map { if (it.isDirectory) it else zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    with(tasks.jar.get() as JarTask)
}
