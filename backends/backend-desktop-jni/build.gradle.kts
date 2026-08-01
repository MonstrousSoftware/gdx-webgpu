plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

val javaVersion = JavaVersion.toVersion(project.property("javaMain") as String)
val jWebGPUVersion = project.property("jWebGPUVVersion") as String
val jWebGPUGroup = "com.github.xpenatan.jWebGPU"
val jWebGPUDesktopArtifact = "webgpu-desktop-jni"
val desktopPlatforms = listOf("windows_x64", "linux_x64", "mac_x64", "mac_arm64")
val nativeBackends = listOf("wgpu", "dawn")

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    api(project(":backends:backend-desktop"))
    api("$jWebGPUGroup:$jWebGPUDesktopArtifact:$jWebGPUVersion")

    // Local project consumers (the test modules) can switch between both native backends.
    nativeBackends.forEach { nativeBackend ->
        desktopPlatforms.forEach { platform ->
            runtimeOnly(
                "$jWebGPUGroup:$jWebGPUDesktopArtifact-${nativeBackend}_$platform:$jWebGPUVersion"
            )
        }
    }
}

fun MavenPublication.configureNativeBackend(nativeBackend: String, platform: String? = null) {
    val platformSuffix = platform?.let { "_$it" }.orEmpty()
    artifactId = "backend-desktop-jni-$nativeBackend$platformSuffix"
    artifact(tasks.named("jar"))
    artifact(tasks.named("sourcesJar"))
    artifact(tasks.named("javadocJar"))

    pom.withXml {
        val dependenciesNode = asNode().appendNode("dependencies")

        fun addDependency(groupId: String, artifactId: String, version: String, scope: String) {
            val dependencyNode = dependenciesNode.appendNode("dependency")
            dependencyNode.appendNode("groupId", groupId)
            dependencyNode.appendNode("artifactId", artifactId)
            dependencyNode.appendNode("version", version)
            dependencyNode.appendNode("scope", scope)
        }

        addDependency(LibExt.groupId, "backend-desktop", LibExt.libVersion, "compile")
        addDependency(jWebGPUGroup, jWebGPUDesktopArtifact, jWebGPUVersion, "compile")
        val includedPlatforms = platform?.let { listOf(it) } ?: desktopPlatforms
        includedPlatforms.forEach { includedPlatform ->
            addDependency(
                jWebGPUGroup,
                "$jWebGPUDesktopArtifact-${nativeBackend}_$includedPlatform",
                jWebGPUVersion,
                "runtime"
            )
        }
    }
}

publishing {
    publications {
        nativeBackends.forEach { nativeBackend ->
            val backendName = nativeBackend.replaceFirstChar { it.uppercase() }
            create<MavenPublication>("maven$backendName") {
                configureNativeBackend(nativeBackend)
            }
            desktopPlatforms.forEach { platform ->
                val platformName = platform.split('_').joinToString("") { part ->
                    part.replaceFirstChar { it.uppercase() }
                }
                create<MavenPublication>("maven$backendName$platformName") {
                    configureNativeBackend(nativeBackend, platform)
                }
            }
        }
    }
}
