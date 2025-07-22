import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.jvm.tasks.Jar as JarTask

val mainClassName = "com.monstrous.gdx.tests.webgpu.WebGPUTestStarter"

val javaVersion = project.property("java") as String

sourceSets["main"].resources.srcDirs(File("../assets"))

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

dependencies {
    implementation(project(":gdx-webgpu"))
    implementation(project(":backends:gdx-desktop-webgpu"))
    implementation(project(":tests:gdx-webgpu-tests"))
}

tasks.register<JavaExec>("launchTestsWebGPU") {
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
