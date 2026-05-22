plugins {
    id("java-library")
}

val javaVersion = project.property("javaMain") as String
val gdxVersion = project.property("gdxVersion") as String

if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

dependencies {
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
}
