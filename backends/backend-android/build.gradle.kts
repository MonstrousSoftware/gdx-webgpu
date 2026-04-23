plugins {
    id("com.android.library")
}

val javaVersion = project.property("java") as String

android {
    namespace = "io.github.monstroussoftware.gdx.webgpu"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
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
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    val gdxVersion = project.property("gdxVersion") as String
    val jWebGPUVVersion = project.property("jWebGPUVVersion") as String

    implementation(project(":gdx-webgpu"))
    api("com.github.xpenatan.jWebGPU:webgpu-android:$jWebGPUVVersion")

    api("com.badlogicgames.gdx:gdx:${gdxVersion}")
    api("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "backend-android"
            group = LibExt.groupId
            version = LibExt.libVersion
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
