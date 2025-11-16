buildscript {
    repositories {
        mavenCentral()
        google()
    }

    val kotlinVersion = "2.1.10"

    dependencies {
        classpath("com.android.tools.build:gradle:8.3.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
    }
}

plugins {
    id("com.diffplug.spotless") version "6.25.0" apply false
}


allprojects()  {
    repositories {
        mavenCentral()
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
    }

    configurations.configureEach {
        // Check for updates every sync
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}

apply(plugin = "publish")
