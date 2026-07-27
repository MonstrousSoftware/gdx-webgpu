import org.teavm.gradle.api.OptimizationLevel

plugins {
    id("java")
    id("com.github.xpenatan.gdx-teavm")
}

val javaVersion = JavaVersion.toVersion(project.property("javaWeb") as String)

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

dependencies {
    implementation(project(":gdx-webgpu"))
    implementation(project(":backends:backend-teavm"))
    implementation(project(":tests:gdx-webgpu-tests"))
}

gdxTeaVM {
    assets.from(file("../assets"))
    reflection("com.badlogic.gdx.graphics.g3d.particles.**")

    js {
        mainClass.set("com.monstrous.gdx.tests.webgpu.TeaVMTestLauncher")
        htmlTitle.set("gdx-webgpu tests JS")
        logoPath.set("webgpu-preload.png")
        optimization.set(OptimizationLevel.BALANCED)
        debugInformation.set(true)
        sourceMap.set(true)
        obfuscated.set(false)

        devServer {
            enabled.set(true)
            autoBuild.set(true)
            autoReload.set(true)
            processMemory.set(2048)
        }
    }

    wasm {
        mainClass.set("com.monstrous.gdx.tests.webgpu.TeaVMTestLauncher")
        htmlTitle.set("gdx-webgpu tests WASM")
        logoPath.set("webgpu-preload.png")
        serverPort.set(8081)
        optimization.set(OptimizationLevel.BALANCED)
        obfuscated.set(false)

        devServer {
            enabled.set(true)
            autoBuild.set(true)
            autoReload.set(true)
            processMemory.set(2048)
        }
    }
}
