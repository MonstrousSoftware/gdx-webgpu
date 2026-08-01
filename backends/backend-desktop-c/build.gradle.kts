plugins {
    id("java-library")
}

val javaVersion = JavaVersion.toVersion(libs.versions.javaWeb.get())

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    api(project(":gdx-webgpu"))
    api(libs.gdxTeaVMBackendGlfw)
    api(libs.jWebGPUC)
}
