plugins {
    id("java")
    id("maven-publish")
    id("signing")
}

buildscript {
    repositories {
        mavenCentral()
        google()
    }

    val kotlinVersion = "2.1.10"

    dependencies {
        classpath("com.android.tools.build:gradle:8.3.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}


allprojects()  {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("http://teavm.org/maven/repository/")
            isAllowInsecureProtocol = true
        }
    }

    configurations.configureEach {
        // Check for updates every sync
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}

var libProjects = mutableSetOf(
    project(":gdx-webgpu"),
    project(":backends:gdx-desktop-webgpu"),
    project(":backends:gdx-teavm-webgpu"),
)

configure(libProjects) {
    //apply(plugin = "signing")
    apply(plugin = "maven-publish")

    publishing {
        repositories {
            maven {
//                val isSnapshot = LibExt.libVersion.endsWith("-SNAPSHOT")
//                url = if (isSnapshot) {
//                    uri("https://central.sonatype.com/repository/maven-snapshots/")
//                } else {
//                    uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
//                }
//                if(isSnapshot) {
//                    credentials {
//                        username = System.getenv("CENTRAL_PORTAL_USERNAME")
//                        password = System.getenv("CENTRAL_PORTAL_PASSWORD")
//                    }
//                }
            }
        }
    }

    tasks.withType<Javadoc> {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }

    publishing.publications.configureEach {
        if (this is MavenPublication) {
            pom {
                name.set("gdx-webgpu")
                description.set("WebGPU extension for LibGDX")
                url.set("https://github.com/MonstrousSoftware/gdx-webgpu")
                developers {
                    developer {
                        id.set("MonstrousSoftware")
                        name.set("Monstrous-JoJo")
                    }
                }
//                scm {
//                    connection.set("scm:git:git://https://github.com/xpenatan/jWebGPU.git")
//                    developerConnection.set("scm:git:ssh://https://github.com/xpenatan/jWebGPU.git")
//                    url.set("http://https://github.com/xpenatan/jWebGPU/tree/master")
//                }
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }

//    val signingKey = System.getenv("SIGNING_KEY")
//    val signingPassword = System.getenv("SIGNING_PASSWORD")
//    if (signingKey != null && signingPassword != null) {
//        signing {
//            useInMemoryPgpKeys(signingKey, signingPassword)
//            publishing.publications.configureEach {
//                sign(this)
//            }
//        }
//    }
}


