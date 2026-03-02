import java.io.File
import java.util.Properties

object LibExt {
    var overrideGroup: String? = null
    val groupId get() = overrideGroup?.takeIf { it.isNotBlank() } ?: "io.github.monstroussoftware.gdx-webgpu"
    const val libName = "gdx-webgpu"
    var overrideVersion: String? = null
    var libVersion: String = ""
        get() {
            return overrideVersion?.takeIf { it.isNotBlank() && it != "unspecified" } ?: getVersion()
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
            if (version == null || version == "unspecified") {
                throw RuntimeException("Version not set properly in gradle.properties. Set gdxWebGPU to a valid version.")
            }
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
