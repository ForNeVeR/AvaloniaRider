import com.jetbrains.rd.generator.gradle.RdGenTask

plugins {
    alias(libs.plugins.kotlinJvm)
    id("com.jetbrains.rdgen") version libs.versions.rdGen
}

dependencies {
    implementation(libs.rdGen)
    implementation(libs.kotlinStdLib)
    implementation(
        project(
            mapOf(
                "path" to ":",
                "configuration" to "riderModel"
            )
        )
    )
}

rdgen {
    val csOutput = rootProject.projectDir.resolve("src/dotnet/AvaloniaRider.Plugin/Model")
    val ktOutput = rootProject.projectDir.resolve("src/rider/main/kotlin/me/fornever/avaloniarider/model")

    verbose = true
    packages = "model.rider"

    generator {
        language = "kotlin"
        transform = "asis"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        directory = "$ktOutput"
    }

    generator {
        language = "csharp"
        transform = "reversed"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        directory = "$csOutput"
    }
}

tasks.withType<RdGenTask> {
    val classPath = sourceSets["main"].runtimeClasspath
    dependsOn(classPath)
    classpath(classPath)
}
