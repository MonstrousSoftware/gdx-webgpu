plugins {
    id("java")
    id("org.gretty") version("3.1.0")
}

gretty {
    contextPath = "/"
    extraResourceBase("build/dist/webapp")
}

dependencies {
    val gdxTeaVMVersion = project.property("gdxTeaVMVersion") as String
//    val gdxVersion = project.property("gdxVersion") as String
//    val teaVMVersion = project.property("teaVMVersion") as String

    //implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.github.xpenatan.gdx-teavm:backend-teavm:$gdxTeaVMVersion")
    implementation("com.github.xpenatan.gdx-teavm:backend-teavm:$gdxTeaVMVersion:sources")

//    implementation("org.teavm:teavm-classlib:${teaVMVersion}")
//    implementation("org.teavm:teavm-core:${teaVMVersion}")
//    implementation("org.teavm:teavm-jso-apis:${teaVMVersion}")
//    implementation("org.teavm:teavm-jso-impl:${teaVMVersion}")
//    implementation("org.teavm:teavm-jso:${teaVMVersion}")
//    implementation("org.teavm:teavm-tooling:${teaVMVersion}")

    implementation(project(":gdx-webgpu"))
    implementation(project(":backends:backend-teavm"))
    //implementation(project("backends:backend-teavm:sources"))
    implementation(project(":tests:gdx-webgpu-tests"))

}

val mainClassName = "com.monstrous.gdx.tests.webgpu.BuildTeaVM"

tasks.register<JavaExec>("gdx_webgpu_tests_build_teavm") {
    group = "LibGDX"
    description = "Build webgpu-tests"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register("gdx_webgpu_tests_run_teavm") {
    group = "LibGDX"
    description = "Run webgpu-tests"
    val list = listOf("gdx_webgpu_tests_build_teavm", "jettyRun")
    dependsOn(list)

    tasks.findByName("jettyRun")?.mustRunAfter("gdx_webgpu_tests_build_teavm")
}

tasks.register<JavaExec>("gdx_webgpu_asset_manager_build_teavm") {
    group = "LibGDX"
    description = "Build AssetManager test"
    mainClass.set("com.monstrous.gdx.tests.webgpu.assetmanager.BuildAssetManagerTest")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register("gdx_webgpu_asset_manager_run_teavm") {
    group = "LibGDX"
    description = "Run AssetManager test"
    val list = listOf("gdx_webgpu_asset_manager_build_teavm", "jettyRun")
    dependsOn(list)

    tasks.findByName("jettyRun")?.mustRunAfter("gdx_webgpu_asset_manager_build_teavm")
}
