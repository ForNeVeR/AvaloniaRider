import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.changelog.exceptions.MissingVersionException
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kotlin.io.path.absolutePathString
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
        rider(libs.versions.riderSdk) {
            useInstaller = false
        }
        jetbrainsRuntime()

        bundledModule("intellij.rider")
        bundledPlugins("com.jetbrains.xaml.previewer")

        pluginVerifier(libs.intellij.plugin.verifier.cli.map { it.version })

        testFramework(TestFrameworkType.Bundled)
    }

    implementation(libs.bson4Jackson)

    testImplementation(libs.openTest4J)
    testImplementation(libs.junit)
}

val buildConfiguration = ext.properties["buildConfiguration"] as String? ?: "Debug"
val buildNumber = (ext.properties["buildNumber"] as String?)?.toInt() ?: 0

val dotNetSrcDir = File(projectDir, "src/dotnet")

val dotNetSdkGeneratedPropsFile = File(projectDir, "build/DotNetSdkPath.Generated.props")
val nuGetConfigFile = File(projectDir, "nuget.config")

version =
    if (buildRelease.equals("true", ignoreCase = true) || buildRelease == "1") pluginVersionBase
    else "$pluginVersionBase.$buildNumber"

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
                create(IntelliJPlatformType.Rider, version) {
                    useInstaller = false
                }
            }
            rider(libs.versions.riderSdk)
            if (libs.versions.riderSdk.get() != libs.versions.riderSdkPreview.get()) {
                rider(libs.versions.riderSdkPreview)
            }
        }
        freeArgs.addAll("-mute", "TemplateWordInPluginName")
        failureLevel.add(VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES)
    }
}

tasks {
    val riderDotNetSdk = run {
        val path = lazy {
            val path = intellijPlatform.platformPath.resolve("lib/DotNetSdkForRdPlugins")
            if (!path.isDirectory()) error("$path does not exist or not a directory")

            logger.info("Rider .NET SDK path: $path")
            path
        }
        provider { path.value }
    }

    val generateDotNetSdkProperties by registering {
        dependsOn(Constants.Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)
        inputs.property("riderDotNetSdk", riderDotNetSdk.map { it.absolutePathString() })
        outputs.file(dotNetSdkGeneratedPropsFile)
        doLast {
            val riderSdkPath = riderDotNetSdk.get().absolutePathString()
            dotNetSdkGeneratedPropsFile.writeText("""<Project>
  <PropertyGroup>
    <DotNetSdkPath>$riderSdkPath</DotNetSdkPath>
  </PropertyGroup>
</Project>
""")
        }
    }

    val generateNuGetConfig by registering {
        dependsOn(Constants.Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)
        inputs.property("riderDotNetSdk", riderDotNetSdk.map { it.absolutePathString() })
        outputs.file(nuGetConfigFile)
        doLast {
            val riderSdkPath = riderDotNetSdk.get().absolutePathString()
            nuGetConfigFile.writeText("""<?xml version="1.0" encoding="utf-8"?>
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

    val dotNetPluginFiles = run {
        val outputFolder = dotNetSrcDir.resolve("$dotNetPluginId/bin/${dotNetPluginId}/$buildConfiguration")
        listOf(
            outputFolder.resolve("$dotNetPluginId.dll"),
            outputFolder.resolve("$dotNetPluginId.pdb")
        )
    }

    val compileDotNet by registering(Exec::class) {
        dependsOn(rdGen, generateDotNetSdkProperties, generateNuGetConfig)

        inputs.files(
            "AvaloniaRider.sln",
            dotNetSdkGeneratedPropsFile,
            nuGetConfigFile,
            riderDotNetSdk,
            fileTree("src/dotnet") {
                exclude("**/bin/**", "**/obj/**")
            }
        )
        inputs.property("buildConfiguration", buildConfiguration)
        outputs.files(dotNetPluginFiles)

        executable("dotnet")
        args("build", "-consoleLoggerParameters:ErrorsOnly", "--configuration", buildConfiguration)
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
        useTestNG()
        testLogging {
            showStandardStreams = true
            exceptionFormat = TestExceptionFormat.FULL
        }
        environment["LOCAL_ENV_RUN"] = "true"
    }

    withType<PrepareSandboxTask> {
        dependsOn(compileDotNet)

        val reSharperPluginDesc = "fvnever.$intellijPluginId"
        from("src/extensions") { into("${rootProject.name}/dotnet/Extensions/$reSharperPluginDesc") }

        for (f in dotNetPluginFiles) {
            from(f) { into("${rootProject.name}/dotnet") }
        }

        doFirst {
            for (file in dotNetPluginFiles) {
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
        dependsOn(
            testRiderPreview,
            verifyPlugin
        )
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
