plugins {
    id("com.android.application")
    id("kotlin-android")
}

val javaVersion = project.property("java") as String

android {
    namespace = "com.monstrous.gdx.tests.webgpu"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.monstrous.gdx.tests.webgpu"
        minSdk = 26
        versionCode = 1
        versionName = "0.0.1"
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

    val gdxVersion = project.property("gdxVersion") as String
    natives("com.badlogicgames.gdx:gdx-platform:${gdxVersion}:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:${gdxVersion}:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:${gdxVersion}:natives-x86_64")
    natives("com.badlogicgames.gdx:gdx-platform:${gdxVersion}:natives-x86")
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
