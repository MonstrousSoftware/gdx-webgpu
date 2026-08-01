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
    api(project(":benchmark:core"))
    api(project(":gdx-webgpu"))
}
