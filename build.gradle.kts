import org.jetbrains.changelog.exceptions.MissingVersionException
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kotlin.io.path.absolute
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

plugins {
    alias(libs.plugins.changelog)
    alias(libs.plugins.gradleJvmWrapper)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.kotlinJvm)
}

jvmWrapper {
    linuxAarch64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.3_linux-aarch64_bin.tar.gz"
    linuxX64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.3_linux-x64_bin.tar.gz"
    macAarch64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.3_macos-aarch64_bin.tar.gz"
    macX64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.3_macos-x64_bin.tar.gz"
    windowsX64JvmUrl = "https://download.oracle.com/java/21/archive/jdk-21.0.3_windows-x64_bin.zip"
}

allprojects {
    repositories {
        mavenCentral()
    }
}

repositories {
    intellijPlatform {
        defaultRepositories()
        jetbrainsRuntime()
    }
}

val dotNetPluginId = "AvaloniaRider.Plugin"
val intellijPluginId = "avalonia-rider"

val pluginVersionBase: String by project
val buildRelease: String by project

dependencies {
    intellijPlatform {
        rider(libs.versions.riderSdk, useInstaller = false)
        jetbrainsRuntime()

        bundledModule("intellij.rider")
        bundledPlugins("com.jetbrains.xaml.previewer")

        pluginVerifier()

        testFramework(TestFrameworkType.Bundled)
    }

    implementation(libs.bson4Jackson)

    testImplementation(libs.openTest4J)
}

val buildConfiguration = ext.properties["buildConfiguration"] ?: "Debug"
val buildNumber = (ext.properties["buildNumber"] as String?)?.toInt() ?: 0

val dotNetSrcDir = File(projectDir, "src/dotnet")

val dotNetSdkGeneratedPropsFile = File(projectDir, "build/DotNetSdkPath.Generated.props")
val nuGetConfigFile = File(projectDir, "nuget.config")

version =
    if (buildRelease.equals("true", ignoreCase = true) || buildRelease == "1") pluginVersionBase
    else "$pluginVersionBase.$buildNumber"

fun File.writeTextIfChanged(content: String) {
    val bytes = content.toByteArray()

    if (!exists() || !readBytes().contentEquals(bytes)) {
        println("Writing $path")
        parentFile.mkdirs()
        writeBytes(bytes)
    }
}

sourceSets {
    main {
        kotlin.srcDir("src/rider/main/kotlin")
        resources.srcDir("src/rider/main/resources")
    }
}

intellijPlatform {
    pluginVerification {
        ides {
            fun rider(version: Provider<String>) {
                ide(provider { IntelliJPlatformType.Rider }, version, useInstaller = false)
            }
            rider(libs.versions.riderSdk)
            if (libs.versions.riderSdk.get() != libs.versions.riderSdkPreview.get()) {
                rider(libs.versions.riderSdkPreview)
            }
        }
        freeArgs.addAll("-mute", "TemplateWordInPluginName")
    }
}

tasks {
    wrapper {
        gradleVersion = "8.13"
        distributionType = Wrapper.DistributionType.ALL
    }

    val riderSdkPath by lazy {
        val path = intellijPlatform.platformPath.resolve("lib/DotNetSdkForRdPlugins").absolute()
        if (!path.isDirectory()) error("$path does not exist or not a directory")

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

    val rdGen = ":protocol:rdgen"

    register("prepare") {
        dependsOn(rdGen, generateDotNetSdkProperties, generateNuGetConfig)
    }

    val compileDotNet by registering(Exec::class) {
        dependsOn(rdGen, generateDotNetSdkProperties, generateNuGetConfig)
        executable("dotnet")
        args("build", "-c", buildConfiguration)
    }

    withType<KotlinCompile> {
        dependsOn(rdGen)
        compilerOptions {
            // TODO[#416]: Enable this after https://github.com/JetBrains/rd/issues/492 gets resolved.
            // allWarningsAsErrors = true
        }
    }

    patchPluginXml {
        val latestChangelog = try {
            changelog.getUnreleased()
        } catch (_: MissingVersionException) {
            changelog.getLatest()
        }
        changeNotes.set(provider {
            changelog.renderItem(
                latestChangelog
                    .withHeader(false)
                    .withEmptySections(false),
                org.jetbrains.changelog.Changelog.OutputType.HTML
            )
        })
    }

    buildPlugin {
        dependsOn(compileDotNet)
    }

    runIde {
        jvmArgs("-Xmx1500m")
    }

    withType<Test> {
        classpath -= classpath.filter {
            (it.name.startsWith("localization-") && it.name.endsWith(".jar")) // TODO[#478]: https://youtrack.jetbrains.com/issue/IJPL-178084/External-plugin-tests-break-due-to-localization-issues
                || it.name == "cwm-plugin.jar" // TODO[#479]: Check after 251 EAP5 release
        }

        useTestNG()
        testLogging {
            showStandardStreams = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        environment["LOCAL_ENV_RUN"] = "true"
    }

    withType<PrepareSandboxTask> {
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

    val testRiderPreview by intellijPlatformTesting.testIde.registering {
        version = libs.versions.riderSdkPreview
        useInstaller = false
        task {
            enabled = libs.versions.riderSdk.get() != libs.versions.riderSdkPreview.get()
        }
    }

    check {
        dependsOn(testRiderPreview, verifyPlugin)
    }
}

val riderModel: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(riderModel.name, provider {
        intellijPlatform.platformPath.resolve("lib/rd/rider-model.jar").also {
            check(it.isRegularFile()) {
                "rider-model.jar is not found at \"$it\"."
            }
        }
    }) {
        builtBy(Constants.Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)
    }
}
