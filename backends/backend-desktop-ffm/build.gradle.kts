plugins {
    id("java")
    id("java-library")
}

val javaVersion = JavaVersion.toVersion(libs.versions.javaFFM.get())
val jWebGPUVersion = libs.versions.jWebGPU.get()
val jWebGPUGroup = libs.versions.jWebGPUGroup.get()
val jWebGPUDesktopArtifact = "webgpu-desktop-ffm"
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
    api(libs.jWebGPUDesktopFFM)

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
    artifactId = "backend-desktop-ffm-$nativeBackend$platformSuffix"

    pom.withXml {
        val dependenciesNode = asNode().appendNode("dependencies")

        fun addDependency(groupId: String, artifactId: String, version: String, scope: String) {
            val dependencyNode = dependenciesNode.appendNode("dependency")
            dependencyNode.appendNode("groupId", groupId)
            dependencyNode.appendNode("artifactId", artifactId)
            dependencyNode.appendNode("version", version)
            dependencyNode.appendNode("scope", scope)
        }

        addDependency(
            libs.versions.gdxWebGPUGroup.get(),
            "backend-desktop",
            project.version.toString(),
            "compile"
        )
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
