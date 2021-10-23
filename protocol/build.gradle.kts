plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
    flatDir {
        @Suppress("UNCHECKED_CAST") val rdLibDirectory = rootProject.extra["rdLibDirectory"] as () -> File
        dir(rdLibDirectory())
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation(group = "", name = "rd-gen")
    implementation(group = "", name = "rider-model")
}
