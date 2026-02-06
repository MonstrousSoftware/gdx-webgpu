plugins {
    id("java")
    id("maven-publish")
    id("com.diffplug.spotless")
}
val javaVersion = JavaVersion.toVersion(project.property("java") as String)

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

dependencies {
    val gdxVersion = project.property("gdxVersion") as String
    val gdxTeaVMVersion = project.property("gdxTeaVMVersion") as String
    val jWebGPUVVersion = project.property("jWebGPUVVersion") as String


    //implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.github.xpenatan.gdx-teavm:backend-web:$gdxTeaVMVersion")
    implementation("com.github.xpenatan.jWebGPU:webgpu-teavm:${jWebGPUVVersion}")
    implementation(project(":gdx-webgpu"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "backend-teavm"
            from(components["java"])
        }
    }
}

spotless {
    java {
        eclipse().configFile(rootProject.file("intellij-java-style.xml"))
    }
}
