import com.github.xpenatan.easypublishing.EasyPublishingExtension

plugins {
    id("java")
    alias(libs.plugins.easyPublishing) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
    }

    configurations.configureEach {
        // Check for updates every sync
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}

// The GraalVM plugin adds a self-project dependency while it is applied. Pre-apply Maven
// publishing to the affected hierarchy, then evaluate the native projects before
// EasyPublishing starts observing project dependencies.
val graalNativeProjects = listOf(
    ":benchmark:graalvm:desktop-jni",
    ":benchmark:graalvm:desktop-ffm",
)
val graalPublishingProjects = listOf(
    ":benchmark",
    ":benchmark:graalvm",
) + graalNativeProjects
graalPublishingProjects.forEach { projectPath ->
    project(projectPath).pluginManager.apply("maven-publish")
}
graalNativeProjects.forEach { projectPath ->
    evaluationDependsOn(projectPath)
}

apply(plugin = "com.github.xpenatan.easy-publishing")

extensions.configure<EasyPublishingExtension> {
    modules(
        ":gdx-webgpu",
        ":backends:backend-desktop",
        ":backends:backend-desktop-jni",
        ":backends:backend-desktop-ffm",
        ":backends:backend-desktop-c",
        ":backends:backend-teavm",
        ":backends:backend-android",
    )

    groupId.set(libs.versions.gdxWebGPUGroup)
    releaseVersion.set(libs.versions.gdxWebGPURelease)
    snapshotVersion.set(libs.versions.gdxWebGPUSnapshot)

    snapshotRepositoryUrl.set("https://central.sonatype.com/repository/maven-snapshots/")
    releaseRepositoryUrl.set("https://central.sonatype.com")
    username.set(providers.environmentVariable("CENTRAL_PORTAL_USERNAME"))
    password.set(providers.environmentVariable("CENTRAL_PORTAL_PASSWORD"))
    signingKey.set(providers.environmentVariable("SIGNING_KEY"))
    signingPassword.set(providers.environmentVariable("SIGNING_PASSWORD"))

    pomName.set(libs.versions.gdxWebGPUName)
    pomDescription.set("WebGPU extension for LibGDX")
    projectUrl.set("https://github.com/MonstrousSoftware/gdx-webgpu")

    developerId.set("MonstrousSoftware")
    developerName.set("MonstrousSoftware")

    scmUrl.set("https://github.com/MonstrousSoftware/gdx-webgpu/tree/master")
    scmConnection.set("scm:git:https://github.com/MonstrousSoftware/gdx-webgpu.git")
    scmDeveloperConnection.set("scm:git:ssh://git@github.com/MonstrousSoftware/gdx-webgpu.git")
}
