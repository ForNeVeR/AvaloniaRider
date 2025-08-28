rootProject.name = "avaloniarider"
include(":protocol")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.jetbrains.rdgen") {
                useModule("com.jetbrains.rd:rd-gen:${requested.version}")
            }
        }
    }
}
