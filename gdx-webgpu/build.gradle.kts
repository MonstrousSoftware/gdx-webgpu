plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

val javaVersion = JavaVersion.toVersion(project.property("java") as String)

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

dependencies {
    val gdxVersion = project.property("gdxVersion") as String
    val jWebGPUVVersion = project.property("jWebGPUVVersion") as String

    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    api("com.github.xpenatan.jWebGPU:webgpu-core:$jWebGPUVVersion")

}

sourceSets["main"].resources.srcDirs(File("res"))

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "gdx-webgpu"
            from(components["java"])
        }
    }
}
