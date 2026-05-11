plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

val javaVersion = JavaVersion.toVersion(project.property("javaFFM") as String)

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    val jWebGPUVVersion = project.property("jWebGPUVVersion") as String

    api(project(":backends:backend-desktop"))
    api("com.github.xpenatan.jWebGPU:webgpu-ffm:$jWebGPUVVersion")
    api("com.github.xpenatan.jWebGPU:webgpu-ffm_desktop:$jWebGPUVVersion")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "backend-desktop-ffm"
            from(components["java"])
        }
    }
}

