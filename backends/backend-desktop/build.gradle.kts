
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

    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation(project(":gdx-webgpu"))
    implementation("org.lwjgl:lwjgl-glfw:3.3.3")
    implementation("org.lwjgl:lwjgl:3.3.3")

    api("com.github.xpenatan.jWebGPU:webgpu-core:$jWebGPUVVersion")
    implementation("com.github.xpenatan.jWebGPU:webgpu-desktop:$jWebGPUVVersion:windows_64_dawn")
    implementation("com.github.xpenatan.jWebGPU:webgpu-desktop:$jWebGPUVVersion:windows_64_wgpu")
    implementation("com.github.xpenatan.jWebGPU:webgpu-desktop:$jWebGPUVVersion:linux_64_wgpu")
    implementation("com.github.xpenatan.jWebGPU:webgpu-desktop:$jWebGPUVVersion:mac_arm64_wgpu")
    implementation("com.github.xpenatan.jWebGPU:webgpu-desktop:$jWebGPUVVersion:mac_64_wgpu")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "backend-desktop"
            from(components["java"])
        }
    }
}
