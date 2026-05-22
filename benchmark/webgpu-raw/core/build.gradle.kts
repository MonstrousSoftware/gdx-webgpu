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
    api(project(":benchmark:core"))
    api(project(":gdx-webgpu"))
}
