plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
}

val rdLibDirectory: () -> File by rootProject.extra

repositories {
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
    flatDir {
        dir(rdLibDirectory())
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation(group = "", name = "rd-gen")
    implementation(group = "", name = "rider-model")
}
