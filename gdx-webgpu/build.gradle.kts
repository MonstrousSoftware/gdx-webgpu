plugins {
    id("java")
    id("java-library")
}

val javaVersion = JavaVersion.toVersion(libs.versions.javaMain.get())

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

dependencies {
    api(libs.gdxCore)
    compileOnlyApi(libs.jWebGPUCore)
}

sourceSets["main"].resources.srcDirs(File("res"))
