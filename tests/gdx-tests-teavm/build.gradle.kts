plugins {
    id("java")
    id("org.gretty") version("3.1.0")
}

gretty {
    contextPath = "/"
    extraResourceBase("build/dist/webapp")
}

dependencies {
    val gdxVersion = project.property("gdxVersion") as String
    val gdxTeaVMVersion = project.property("gdxTeaVMVersion") as String
    val teaVMVersion = project.property("teaVMVersion") as String

    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.github.xpenatan.gdx-teavm:backend-teavm:$gdxTeaVMVersion")
    implementation("com.github.xpenatan.gdx-teavm:backend-teavm:$gdxTeaVMVersion:sources")

    implementation("org.teavm:teavm-classlib:${teaVMVersion}")
    implementation("org.teavm:teavm-core:${teaVMVersion}")
    implementation("org.teavm:teavm-jso-apis:${teaVMVersion}")
    implementation("org.teavm:teavm-jso-impl:${teaVMVersion}")
    implementation("org.teavm:teavm-jso:${teaVMVersion}")
    implementation("org.teavm:teavm-tooling:${teaVMVersion}")

    implementation(project(":gdx-webgpu"))
    implementation(project(":tests:gdx-webgpu-tests"))
    implementation(project(":backends:backend-teavm"))

    // Have Build errors
}

val mainClassName = "com.monstrous.gdx.tests.webgpu.BuildTeaVM"

tasks.register<JavaExec>("core-build") {
    group = "example-teavm"
    description = "Build teavm test example"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register("core-run-teavm") {
    group = "example-teavm"
    description = "Run Test Demo example"
    val list = listOf("core-build", "jettyRun")
    dependsOn(list)

    tasks.findByName("jettyRun")?.mustRunAfter("core-build")
}

tasks.register<JavaExec>("asset-manager-build") {
    group = "example-teavm"
    description = "Build AssetManager test"
    mainClass.set("com.monstrous.gdx.tests.webgpu.assetmanager.BuildAssetManagerTest")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register("asset-manager-teavm") {
    group = "example-teavm"
    description = "Run AssetManager test"
    val list = listOf("asset-manager-build", "jettyRun")
    dependsOn(list)

    tasks.findByName("jettyRun")?.mustRunAfter("asset-manager-build")
}
