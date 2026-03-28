import groovy.util.Node
import groovy.util.NodeList

plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

val javaVersionString = project.property("java") as String
val javaVersion = JavaVersion.toVersion(javaVersionString)
val javaVersionInt = javaVersionString.toIntOrNull()
    ?: throw GradleException("Invalid 'java' property: '$javaVersionString'")
val backendDesktopFlavor =
    (findProperty("backendDesktopFlavor") as String?)?.trim()?.lowercase() ?: "jni"

require(backendDesktopFlavor == "jni" || backendDesktopFlavor == "ffm") {
    "Invalid backendDesktopFlavor='$backendDesktopFlavor'. Expected 'jni' or 'ffm'."
}

val jWebGPUDesktopArtifact = "webgpu-desktop-$backendDesktopFlavor"

if (backendDesktopFlavor == "ffm") {
    configurations.configureEach {
        exclude(group = "com.github.xpenatan.jWebGPU", module = "webgpu-core")
    }
}

if (backendDesktopFlavor == "ffm" && javaVersionInt < 24) {
    throw GradleException(
        "backendDesktopFlavor=ffm requires java>=24, but current 'java' property is $javaVersionInt."
    )
}

fun MavenPublication.rewriteDesktopArtifactInPom(desktopArtifactId: String) {
    pom.withXml {
        val root = asNode()
        val dependenciesNode = ((root.get("dependencies") as? NodeList)?.firstOrNull() as? Node) ?: return@withXml

        dependenciesNode.children()
            .filterIsInstance<Node>()
            .forEach { dependencyNode ->
                val groupId = ((dependencyNode.get("groupId") as? NodeList)?.firstOrNull() as? Node)?.text()
                val artifactNode = ((dependencyNode.get("artifactId") as? NodeList)?.firstOrNull() as? Node) ?: return@forEach
                val artifactId = artifactNode.text()

                if (groupId == "com.github.xpenatan.jWebGPU" && artifactId.startsWith("webgpu-desktop-")) {
                    artifactNode.setValue(desktopArtifactId)
                }
            }
    }
}

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
    implementation(project(":gdx-webgpu")) {
        if (backendDesktopFlavor == "ffm") {
            exclude(group = "com.github.xpenatan.jWebGPU", module = "webgpu-core")
        }
    }
    implementation("org.lwjgl:lwjgl-glfw:3.3.3")
    implementation("org.lwjgl:lwjgl:3.3.3")

    // Needed for IDLLoader at runtime with recent jParser snapshots.
    api("com.github.xpenatan.jParser:idl-helper-core:$jWebGPUVVersion")
    api("com.github.xpenatan.jParser:idl-core:$jWebGPUVVersion")
    api("com.github.xpenatan.jParser:loader-core:$jWebGPUVVersion")

    // FFM bindings live in webgpu-desktop-ffm main jar; JNI bindings live in webgpu-core.
    if (backendDesktopFlavor == "ffm") {
        api("com.github.xpenatan.jWebGPU:webgpu-desktop-ffm:$jWebGPUVVersion")
    } else {
        api("com.github.xpenatan.jWebGPU:webgpu-core:$jWebGPUVVersion")
    }

    api("com.github.xpenatan.jWebGPU:$jWebGPUDesktopArtifact:$jWebGPUVVersion:windows_64_dawn")
    api("com.github.xpenatan.jWebGPU:$jWebGPUDesktopArtifact:$jWebGPUVVersion:windows_64_wgpu")
    api("com.github.xpenatan.jWebGPU:$jWebGPUDesktopArtifact:$jWebGPUVVersion:linux_64_wgpu")
    api("com.github.xpenatan.jWebGPU:$jWebGPUDesktopArtifact:$jWebGPUVVersion:mac_arm64_wgpu")
    api("com.github.xpenatan.jWebGPU:$jWebGPUDesktopArtifact:$jWebGPUVVersion:mac_64_wgpu")
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJni") {
            artifactId = "backend-desktop-jni"
            from(components["java"])
            rewriteDesktopArtifactInPom("webgpu-desktop-jni")
        }

        create<MavenPublication>("mavenFfm") {
            artifactId = "backend-desktop-ffm"
            from(components["java"])
            rewriteDesktopArtifactInPom("webgpu-desktop-ffm")
        }
    }
}
