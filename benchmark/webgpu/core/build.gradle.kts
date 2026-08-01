plugins {
    id("java-library")
}

val javaVersion = libs.versions.javaMain.get()

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

dependencies {
    implementation(project(":benchmark:core"))
    implementation(project(":gdx-webgpu"))
    implementation(project(":backends:backend-desktop"))
    compileOnly(libs.jWebGPUCore)
}
