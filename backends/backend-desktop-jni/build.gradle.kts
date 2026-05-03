plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

val javaVersion = JavaVersion.toVersion(project.property("javaMain") as String)

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    val jWebGPUVVersion = project.property("jWebGPUVVersion") as String

    api(project(":backends:backend-desktop"))
    api("com.github.xpenatan.jWebGPU:webgpu-jni:$jWebGPUVVersion")
    api("com.github.xpenatan.jWebGPU:webgpu-jni:$jWebGPUVVersion:desktop")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "backend-desktop-jni"
            from(components["java"])
        }
    }
}

