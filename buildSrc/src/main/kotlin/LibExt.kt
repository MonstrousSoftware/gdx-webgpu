import java.io.File
import java.util.Properties

object LibExt {
    var overrideGroup: String? = null
    val groupId get() = overrideGroup ?: "io.github.monstroussoftware.gdx-webgpu"
    const val libName = "gdx-webgpu"
    var overrideVersion: String? = null
    var libVersion: String = ""
        get() {
            return overrideVersion ?: getVersion()
        }
    var isRelease = true
}

private fun getVersion(): String {
    val propVersion = System.getProperty("version")
    if (propVersion != null) {
        return propVersion
    }
    var libVersion = "-SNAPSHOT"
    val file = File("gradle.properties")
    if(file.exists()) {
        val properties = Properties()
        properties.load(file.inputStream())
        val version = properties.getProperty("gdxWebGPU")
        if(LibExt.isRelease) {
            libVersion = version
        }
    }
    else {
        if(LibExt.isRelease) {
            throw RuntimeException("properties should exist")
        }
    }
    return libVersion
}
