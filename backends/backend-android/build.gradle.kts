plugins {
    alias(libs.plugins.androidLibrary)
}

val javaVersion = libs.versions.javaMain.get()

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
    implementation(project(":gdx-webgpu"))
    add("wgpuApi", libs.jWebGPUAndroidWgpu)
    add("dawnApi", libs.jWebGPUAndroidDawn)

    api(libs.gdxCore)
    api(libs.gdxBackendAndroid)
}

publishing {
    publications {
        create<MavenPublication>("mavenWgpu") {
            artifactId = "backend-android-wgpu"
            afterEvaluate {
                from(components["wgpuRelease"])
            }
        }
        create<MavenPublication>("mavenDawn") {
            artifactId = "backend-android-dawn"
            afterEvaluate {
                from(components["dawnRelease"])
            }
        }
    }
}
