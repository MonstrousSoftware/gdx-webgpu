plugins {
    id("java")
}

val javaVersion = JavaVersion.toVersion(project.property("javaWeb") as String)

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

dependencies {
    val gdxTeaVMVersion = project.property("gdxTeaVMVersion") as String
    implementation("com.github.xpenatan.gdx-teavm:backend-web:$gdxTeaVMVersion")

    implementation(project(":gdx-webgpu"))
    implementation(project(":backends:backend-teavm"))
    implementation(project(":tests:gdx-webgpu-tests"))
}

val mainClassName = "com.monstrous.gdx.tests.webgpu.BuildTeaVM"

tasks.register<JavaExec>("gdx_webgpu_tests_run_teavm") {
    group = "LibGDX"
    description = "Build webgpu-tests"
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("gdx_webgpu_asset_manager_run_teavm") {
    group = "LibGDX"
    description = "Build AssetManager test"
    mainClass.set("com.monstrous.gdx.tests.webgpu.assetmanager.BuildAssetManagerTest")
    classpath = sourceSets["main"].runtimeClasspath
}
