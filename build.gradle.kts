buildscript {
    repositories {
        maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
    }

    // https://search.maven.org/artifact/com.jetbrains.rd/rd-gen
    dependencies {
        classpath("com.jetbrains.rd:rd-gen:2022.1.3")
    }
}

plugins {
    id("java")
    id("me.filippov.gradle.jvm.wrapper") version "0.10.0"
    id("org.jetbrains.kotlin.jvm") version "1.5.31"
    id("org.jetbrains.intellij") version "1.5.2"
}

apply {
    plugin("com.jetbrains.rdgen")
}

dependencies {
    implementation("de.undercouch:bson4jackson:2.13.1")

    testImplementation("org.testng:testng:7.1.0")
}

val dotNetPluginId = "AvaloniaRider.Plugin"
val intellijPluginId = "avalonia-rider"

val riderSdkVersion: String by project
val pluginVersionBase: String by project

val buildConfiguration = ext.properties["buildConfiguration"] ?: "Debug"
val buildNumber = (ext.properties["buildNumber"] as String?)?.toInt() ?: 0

val rdLibDirectory: () -> File = { file("${tasks.setupDependencies.get().idea.get().classes}/lib/rd") }
extra["rdLibDirectory"] = rdLibDirectory

val dotNetSrcDir = File(projectDir, "src/dotnet")

val dotNetSdkGeneratedPropsFile = File("build", "DotNetSdkPath.Generated.props")
val nuGetConfigFile = File("nuget.config")

version = "$pluginVersionBase.${2140933433 + buildNumber}" // TODO: 2140933433 here is to keep compatibility with previous versioning scheme

fun File.writeTextIfChanged(content: String) {
    val bytes = content.toByteArray()

    if (!exists() || !readBytes().contentEquals(bytes)) {
        println("Writing $path")
        writeBytes(bytes)
    }
}

repositories {
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
}

jvmWrapper {
    // https://confluence.jetbrains.com/display/JBR/Release+notes+and+builds
    linuxJvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-11_0_11-linux-x64-b1341.60.tar.gz"
    macJvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-11_0_11-osx-x64-b1341.60.tar.gz"
    windowsJvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-11_0_11-windows-x64-b1341.60.tar.gz"
}

sourceSets {
    main {
        java.srcDir("src/rider/main/kotlin")
        resources.srcDir("src/rider/main/resources")
    }
}

apply(plugin = "com.jetbrains.rdgen")

configure<com.jetbrains.rd.generator.gradle.RdGenExtension> {
    val modelDir = file("$projectDir/protocol/src/main/kotlin/model")
    val csOutput = file("$projectDir/src/dotnet/AvaloniaRider.Plugin/Model")
    val ktOutput = file("$projectDir/src/rider/main/kotlin/me/fornever/avaloniarider/model")

    verbose = true
    classpath({
        "${rdLibDirectory()}/rider-model.jar"
    })
    sources("$modelDir/rider")
    hashFolder = "$rootDir/build/rdgen/rider"
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

intellij {
    type.set("RD")
    version.set(riderSdkVersion)
    downloadSources.set(false)
    plugins.set(listOf("com.intellij.javafx:1.0.3", "com.jetbrains.xaml.previewer"))
}

tasks {
    wrapper {
        gradleVersion = "7.2"
        distributionType = Wrapper.DistributionType.ALL
        distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-${gradleVersion}-all.zip"
    }

    val riderSdkPath by lazy {
        val path = setupDependencies.get().idea.get().classes.resolve("lib/DotNetSdkForRdPlugins")
        if (!path.isDirectory) error("$path does not exist or not a directory")

        println("Rider SDK path: $path")
        return@lazy path
    }

    val generateDotNetSdkProperties by registering {
        doLast {
            dotNetSdkGeneratedPropsFile.writeTextIfChanged("""<Project>
  <PropertyGroup>
    <DotNetSdkPath>$riderSdkPath</DotNetSdkPath>
  </PropertyGroup>
</Project>
""")
        }
    }

    val generateNuGetConfig by registering {
        doLast {
            nuGetConfigFile.writeTextIfChanged("""<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <add key="rider-sdk" value="$riderSdkPath" />
  </packageSources>
</configuration>
""")
        }
    }

    val rdgen by existing

    register("prepare") {
        dependsOn(rdgen, generateDotNetSdkProperties, generateNuGetConfig)
    }

    val compileDotNet by registering {
        dependsOn(rdgen, generateDotNetSdkProperties, generateNuGetConfig)
        doLast {
            exec {
                executable("dotnet")
                args("build", "-c", buildConfiguration)
            }
        }
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        dependsOn(rdgen)
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
        }
    }

    buildPlugin {
        dependsOn(compileDotNet)
    }

    runIde {
        // For statistics:
        // jvmArgs("-Xmx1500m", "-Didea.is.internal=true", "-Dfus.internal.test.mode=true")
        jvmArgs("-Xmx1500m")
    }

    test {
        useTestNG()
        testLogging {
            showStandardStreams = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        environment["LOCAL_ENV_RUN"] = "true"
    }

    withType<org.jetbrains.intellij.tasks.PrepareSandboxTask> {
        dependsOn(compileDotNet)

        val reSharperPluginDesc = "fvnever.$intellijPluginId"
        from("src/extensions") { into("${rootProject.name}/dotnet/Extensions/$reSharperPluginDesc") }

        val outputFolder = file("$dotNetSrcDir/$dotNetPluginId/bin/${dotNetPluginId}/$buildConfiguration")
        val dllFiles = listOf(
            "$outputFolder/${dotNetPluginId}.dll",
            "$outputFolder/${dotNetPluginId}.pdb"
        )

        for (f in dllFiles) {
            from(f) { into("${rootProject.name}/dotnet") }
        }

        doLast {
            for (f in dllFiles) {
                val file = file(f)
                if (!file.exists()) throw RuntimeException("File \"$file\" does not exist")
            }
        }
    }
}
