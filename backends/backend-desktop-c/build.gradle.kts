plugins {
    id("java-library")
    id("maven-publish")
}

val javaVersion = JavaVersion.toVersion(project.property("javaWeb") as String)

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    val gdxTeaVMVersion = project.property("gdxTeaVMVersion") as String
    val jWebGPUVVersion = project.property("jWebGPUVVersion") as String

    api(project(":gdx-webgpu"))
    api("com.github.xpenatan.gdx-teavm:backend-glfw:$gdxTeaVMVersion")
    api("com.github.xpenatan.jWebGPU:webgpu-c:$jWebGPUVVersion")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "backend-desktop-c"
            from(components["java"])
        }
    }
}
