plugins {
  // Applies the foojay-resolver plugin to allow automatic download of JDKs.
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
// A list of which subprojects to load as part of the same larger project.
// You can remove Strings from the list and reload the Gradle project
// if you want to temporarily disable a subproject.


include(":gdx-webgpu")

include(":backends")
include(":backends:backend-desktop")
include(":backends:backend-desktop-jni")
include(":backends:backend-desktop-ffm")
include(":backends:backend-teavm")
include(":backends:backend-android")

include(":tests")
include(":tests:gdx-webgpu-tests")
include(":tests:gdx-tests-desktop-jni")
include(":tests:gdx-tests-desktop-ffm")
include(":tests:gdx-tests-teavm")
include(":tests:gdx-tests-android")

include(":benchmark")
include(":benchmark:core")
include(":benchmark:lwjgl3")
include(":benchmark:webgpu")
include(":benchmark:webgpu:core")
include(":benchmark:webgpu:desktop-jni")
include(":benchmark:webgpu:desktop-ffm")
include(":benchmark:webgpu-raw")
include(":benchmark:webgpu-raw:core")
include(":benchmark:webgpu-raw:desktop-jni")
include(":benchmark:webgpu-raw:desktop-ffm")
