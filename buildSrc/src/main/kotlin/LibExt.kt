import java.io.File
import java.util.Properties

object LibExt {
    const val groupId = "com.monstrous.gdx-webgp"
    const val libName = "gdx-webgpu"
    var libVersion: String = ""
        get() {
            return getVersion()
        }
    var isRelease = false
}

private fun getVersion(): String {
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
