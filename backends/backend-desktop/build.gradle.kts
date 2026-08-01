plugins {
    id("java")
    id("java-library")
}

val javaVersion = JavaVersion.toVersion(libs.versions.javaMain.get())

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

dependencies {
    implementation(libs.gdxCore)
    implementation(libs.gdxBackendLwjgl3)
    implementation(variantOf(libs.gdxPlatform) { classifier("natives-desktop") })
    implementation(project(":gdx-webgpu"))
    implementation(libs.lwjglGlfw)
    implementation(libs.lwjglCore)
}

java {
    withJavadocJar()
    withSourcesJar()
}
