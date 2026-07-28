plugins {
    id("com.android.library")
}

val javaVersion = project.property("javaMain") as String

android {
    namespace = "io.github.monstroussoftware.gdx.webgpu"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    flavorDimensions += "webgpuBackend"
    productFlavors {
        create("wgpu") {
            dimension = "webgpuBackend"
            buildConfigField("String", "JWEBGPU_BACKEND", "\"WGPU\"")
        }
        create("dawn") {
            dimension = "webgpuBackend"
            buildConfigField("String", "JWEBGPU_BACKEND", "\"DAWN\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaVersion)
        targetCompatibility = JavaVersion.toVersion(javaVersion)
    }

    publishing {
        singleVariant("wgpuRelease") {
            withSourcesJar()
        }
        singleVariant("dawnRelease") {
            withSourcesJar()
        }
    }
}

dependencies {
    val gdxVersion = project.property("gdxVersion") as String
    val jWebGPUVVersion = project.property("jWebGPUVVersion") as String

    implementation(project(":gdx-webgpu"))
    add("wgpuApi", "com.github.xpenatan.jWebGPU:webgpu-android-wgpu:$jWebGPUVVersion")
    add("dawnApi", "com.github.xpenatan.jWebGPU:webgpu-android-dawn:$jWebGPUVVersion")

    api("com.badlogicgames.gdx:gdx:${gdxVersion}")
    api("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
}

publishing {
    publications {
        create<MavenPublication>("mavenWgpu") {
            artifactId = "backend-android-wgpu"
            groupId = LibExt.groupId
            version = LibExt.libVersion
            afterEvaluate {
                from(components["wgpuRelease"])
            }
        }
        create<MavenPublication>("mavenDawn") {
            artifactId = "backend-android-dawn"
            groupId = LibExt.groupId
            version = LibExt.libVersion
            afterEvaluate {
                from(components["dawnRelease"])
            }
        }
    }
}
