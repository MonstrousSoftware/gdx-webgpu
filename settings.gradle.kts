plugins {
  // Applies the foojay-resolver plugin to allow automatic download of JDKs.
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
// A list of which subprojects to load as part of the same larger project.
// You can remove Strings from the list and reload the Gradle project
// if you want to temporarily disable a subproject.


include(":gdx-webgpu")

include(":backends")
include(":backends:gdx-desktop-webgpu")
include(":backends:gdx-teavm-webgpu")

include(":tests")
include(":tests:gdx-webgpu-tests")
include(":tests:gdx-tests-desktop")
include(":tests:gdx-tests-teavm")
include(":tests:gdx-tests-android")
