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
    val gdxTeaVMVersion = project.property("gdxTeaVMVersion") as String
    val jWebGPUVVersion = project.property("jWebGPUVVersion") as String

    implementation("com.github.xpenatan.gdx-teavm:backend-web:$gdxTeaVMVersion")
    implementation("com.github.xpenatan.jWebGPU:webgpu-teavm:${jWebGPUVVersion}")
    implementation(project(":gdx-webgpu"))
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "backend-teavm"
            from(components["java"])
        }
    }
}
