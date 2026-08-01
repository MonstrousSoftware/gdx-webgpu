plugins {
    alias(libs.plugins.androidApplication)
}

val javaVersion = libs.versions.javaMain.get()

android {
    namespace = "com.monstrous.gdx.tests.webgpu"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.monstrous.gdx.tests.webgpu"
        minSdk = 29
        versionCode = 1
        versionName = "0.0.1"
    }

    flavorDimensions += "webgpuBackend"
    productFlavors {
        create("wgpu") {
            dimension = "webgpuBackend"
        }
        create("dawn") {
            dimension = "webgpuBackend"
        }
    }

    sourceSets {
        named("main") {
            assets.srcDirs(project.file("../assets"))
            jniLibs.srcDirs("libs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaVersion)
        targetCompatibility = JavaVersion.toVersion(javaVersion)
    }
}

val natives: Configuration by configurations.creating

dependencies {
    implementation(project(":backends:backend-android"))
    implementation(project(":tests:gdx-webgpu-tests"))

    natives(variantOf(libs.gdxPlatform) { classifier("natives-armeabi-v7a") })
    natives(variantOf(libs.gdxPlatform) { classifier("natives-arm64-v8a") })
    natives(variantOf(libs.gdxPlatform) { classifier("natives-x86_64") })
    natives(variantOf(libs.gdxPlatform) { classifier("natives-x86") })
}

tasks.register("copyAndroidNatives") {
    group = "basic-android"
    doFirst {
        natives.files.forEach { jar ->
            val outputDir = file("libs/" + jar.nameWithoutExtension.substringAfterLast("natives-"))
            outputDir.mkdirs()
            copy {
                from(zipTree(jar))
                into(outputDir)
                include("*.so")
            }
        }
    }
}

tasks.whenTaskAdded {
    if ("package" in name) {
        dependsOn("copyAndroidNatives")
    }
}
