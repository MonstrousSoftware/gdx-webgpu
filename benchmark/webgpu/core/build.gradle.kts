plugins {
    id("java-library")
}

val javaVersion = project.property("javaMain") as String

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

dependencies {
    val jWebGPUVVersion = project.property("jWebGPUVVersion") as String

    implementation(project(":benchmark:core"))
    implementation(project(":gdx-webgpu"))
    implementation(project(":backends:backend-desktop"))
    compileOnly("com.github.xpenatan.jWebGPU:webgpu-core:$jWebGPUVVersion")
}
