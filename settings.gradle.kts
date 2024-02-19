// Affects the repositories used to resolve the plugins { } block
pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

rootProject.name = "avaloniarider"

include(":protocol")
