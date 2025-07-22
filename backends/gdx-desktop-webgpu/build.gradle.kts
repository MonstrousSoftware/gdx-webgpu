plugins {
    id("java")
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

    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation(project(":gdx-webgpu"))
    implementation("org.lwjgl:lwjgl-glfw:3.3.3")
    implementation("org.lwjgl:lwjgl:3.3.3")
    implementation("com.github.xpenatan.jWebGPU:webgpu-desktop:$jWebGPUVVersion")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "gdx-desktop-webgpu"
            group = LibExt.groupId
            version = LibExt.libVersion
            from(components["java"])
        }
    }
}
