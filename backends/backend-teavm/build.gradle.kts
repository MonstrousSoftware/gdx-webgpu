plugins {
    id("java")
}
val javaVersion = JavaVersion.toVersion(libs.versions.javaWeb.get())

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

dependencies {
    implementation(libs.gdxTeaVMBackendWeb)
    implementation(libs.jWebGPUWeb)
    implementation(libs.jWebGPUWebWasm)
    implementation(project(":gdx-webgpu"))
}

java {
    withJavadocJar()
    withSourcesJar()
}
