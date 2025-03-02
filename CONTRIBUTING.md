Contributor Guide
-----------------

Build
-----

### Prerequisites

- OpenJDK-compatible JDK version 21 or later (will be downloaded automatically during the build),
- .NET SDK 8.0 or later.

### Build

To build the plugin, execute this shell command:

```console
$ ./gradlew buildPlugin
```

This action will use [Gradle JVM Wrapper][gradle-jvm-wrapper] to automatically
download the recommended JDK version that's used for builds, and will download a
required Gradle version. If this isn't necessary, you could use your own
versions of Gradle and JRE by running the build task with `gradle buildPlugin`.

After that, the plugin ZIP distribution will be created in the
`build/distributions` directory.

### Run IDE

The following command will build the plugin and run it using a sandboxed
instance of Rider (set the required version via `build.gradle`).

```console
$ ./gradlew runIde
```

### Test

Execute the following shell command:

```console
$ ./gradlew :check
```

Development
-----------

## IntelliJ IDEA Setup

After running `./gradlew` at least once, set up your project SDK to the following folder:

- `%LOCALAPPDATA%\gradle-jvm` (Windows),
- `${HOME}/.local/share/gradle-jvm` (Unix-based OS).

This JDK is guaranteed to contain all the components necessary to build the plugin.

## Architecture

This plugin consists of two parts: the backend one (written in C#) and the frontend one (written in Kotlin). Each part requires a corresponding IDE. To develop the backend, it's recommended to open `AvaloniaRider.sln` with JetBrains Rider. To develop a frontend, it's recommended to use IntelliJ IDEA (Community edition should be enough).

## Getting Started

If you just want to start developing the plugin and don't want to build it (yet), then execute this shell command:

```console
$ ./gradlew prepare
```

This will download the initial set of dependencies necessary for the plugin development and set up Rider SDK for .NET part of the project. After that, open either the frontend part of the plugin (the directory containing `build.gradle.kts`) using IntelliJ IDEA, or the `AvaloniaRider.sln` using Rider.

[gradle-jvm-wrapper]: https://github.com/mfilippov/gradle-jvm-wrapper
