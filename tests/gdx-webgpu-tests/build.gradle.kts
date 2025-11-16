/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

plugins {
    id("com.diffplug.spotless")
}

val javaVersion = project.property("java") as String

dependencies {
    implementation(project(":gdx-webgpu"))
}


if (JavaVersion.current().isJava9Compatible) {
    tasks.withType<JavaCompile> {
        options.release.set(javaVersion.toInt())
    }
}

spotless {
    java {
        eclipse().configFile(rootProject.file("intellij-java-style.xml"))
    }
}


// note: do not add dependencies here on platform specific modules

