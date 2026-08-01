import org.gradle.api.GradleException
import org.teavm.gradle.api.OptimizationLevel

plugins {
    id("java-library")
    alias(libs.plugins.gdxTeaVM)
}

val javaVersion = JavaVersion.toVersion(libs.versions.javaWeb.get())
val webgpuCBackend = providers.gradleProperty("webgpuCBackend")
    .orElse("wgpu")
    .map { it.lowercase() }
    .get()
val webgpuCMSVCRuntime = providers.gradleProperty("webgpuCMSVCRuntime")
    .orElse("MultiThreadedDLL")
    .get()
if(webgpuCBackend !in setOf("wgpu", "dawn")) {
    throw GradleException("Unsupported webgpuCBackend '$webgpuCBackend'. Expected 'wgpu' or 'dawn'.")
}

val hostOs = System.getProperty("os.name").lowercase()
val hostArch = System.getProperty("os.arch").lowercase()
val isX64 = hostArch in setOf("amd64", "x86_64", "x64")
val isArm64 = hostArch in setOf("aarch64", "arm64")
val webgpuCPlatform = when {
    (hostOs.contains("mac") || hostOs.contains("darwin")) && isX64 -> "mac_x64"
    (hostOs.contains("mac") || hostOs.contains("darwin")) && isArm64 -> "mac_arm64"
    hostOs.startsWith("windows") && isX64 -> "windows_x64"
    hostOs.contains("linux") && isX64 -> "linux_x64"
    else -> throw GradleException("Unsupported desktop TeaVM C host: os.name='$hostOs', os.arch='$hostArch'")
}

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

dependencies {
    implementation(project(":backends:backend-desktop-c"))
    implementation(project(":tests:gdx-webgpu-tests"))
    implementation(
        "${libs.versions.jWebGPUGroup.get()}:" +
            "webgpu-desktop-c-${webgpuCBackend}_$webgpuCPlatform:${libs.versions.jWebGPU.get()}"
    )
}

gdxTeaVM {
    assets.from(file("../assets"))
    reflection(
        "com.monstrous.gdx.tests.webgpu.*",
        "com.badlogic.gdx.graphics.Texture",
        "com.badlogic.gdx.graphics.g3d.Model",
        "com.badlogic.gdx.graphics.g3d.particles.ResourceData",
        "com.badlogic.gdx.graphics.g3d.particles.ParticleEffect",
        "com.badlogic.gdx.graphics.g3d.particles.ParticleController",
        "com.badlogic.gdx.graphics.g3d.particles.batches.BillboardParticleBatch\$Config",
        "com.badlogic.gdx.graphics.g3d.particles.emitters.Emitter",
        "com.badlogic.gdx.graphics.g3d.particles.emitters.RegularEmitter",
        "com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer",
        "com.badlogic.gdx.graphics.g3d.particles.influencers.ColorInfluencer",
        "com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsInfluencer",
        "com.badlogic.gdx.graphics.g3d.particles.influencers.DynamicsModifier",
        "com.badlogic.gdx.graphics.g3d.particles.influencers.ModelInfluencer",
        "com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer",
        "com.badlogic.gdx.graphics.g3d.particles.influencers.ScaleInfluencer",
        "com.badlogic.gdx.graphics.g3d.particles.influencers.SimpleInfluencer",
        "com.badlogic.gdx.graphics.g3d.particles.influencers.SpawnInfluencer",
        "com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderer",
        "com.badlogic.gdx.graphics.g3d.particles.renderers.BillboardRenderer",
        "com.badlogic.gdx.graphics.g3d.particles.renderers.ModelInstanceRenderer",
        "com.badlogic.gdx.graphics.g3d.particles.renderers.PointSpriteRenderer",
        "com.badlogic.gdx.graphics.g3d.particles.values.ParticleValue",
        "com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue",
        "com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue",
        "com.badlogic.gdx.graphics.g3d.particles.values.GradientColorValue",
        "com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue",
        "com.badlogic.gdx.graphics.g3d.particles.values.PrimitiveSpawnShapeValue",
        "com.badlogic.gdx.graphics.g3d.particles.values.EllipseSpawnShapeValue",
        "com.badlogic.gdx.graphics.g3d.particles.values.PointSpawnShapeValue",
    )

    glfw {
        mainClass.set("com.monstrous.gdx.tests.webgpu.TeaVMCLauncher")
        outputDir.set(layout.buildDirectory.dir("dist/$webgpuCBackend/$webgpuCPlatform"))
        targetFileName.set("gdx_webgpu_tests")
        optimization.set(OptimizationLevel.BALANCED)
        debugInformation.set(false)
        obfuscated.set(false)
        minHeapSizeMb.set(64)
        maxHeapSizeMb.set(512)
        buildType.set("Release")
        consoleLog.set(true)
        if(hostOs.startsWith("windows")) {
            cmakeDefinition("CMAKE_MSVC_RUNTIME_LIBRARY", "MultiThreaded")
            cmakeDefinition("JPARSER_JWEBGPU_TEAVMC_LINKAGE", "SHARED_LINKED")
        }
    }
}
