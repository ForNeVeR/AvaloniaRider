# AvaloniaRider [![JetBrains Plugins Repository](https://img.shields.io/jetbrains/plugin/v/14839.svg?label=rider%20&colorB=0A7BBB&style=flat-square)](https://plugins.jetbrains.com/plugin/14839)

Install
-------

### Development releases (may be unstable)

To install a development release (automatically published for every push into a development branch), first add the custom plugin repository into your IDE (see [the documentation][ide.channels] on how to do that).

**Custom plugin repository address:**
`https://plugins.jetbrains.com/plugins/dev/14839`

Then, install the plugin as usual via the IDE plugin settings screen.

Build
-----

### Prerequisites

- OpenJDK-compatible JDK version 11 or later, should include JavaFX (will be
  downloaded automatically during build)
- .NET SDK 6.0 or later

### Build

To build from terminal, execute this command:

```console
$ ./gradlew buildPlugin
```

This action will use [Gradle JVM Wrapper][gradle-jvm-wrapper] to automatically
download the recommended JDK version that's used for builds, and will download a
required Gradle version. If this isn't necessary, you could use your own
versions of Gradle and JRE by running the build task with `gradle buildPlugin`.

After that, the plugin ZIP distribution will be created in the
`build/distributions` directory.

### Run

The following command will build the plugin and run it using a sandboxed
instance of Rider (set the required version via `build.gradle`).

```console
$ ./gradlew runIde
```

### Test

Execute the following shell command:

```console
$ ./gradlew test
```

Development
-----------

## Architecture

This plugin consists of two parts: the backend one (written in C#) and the
frontend one (written in Kotlin). Each part requires a corresponding IDE. To
develop a backend, it's recommended to open `AvaloniaRider.sln` with JetBrains
Rider. To develop a frontend, it's recommended to use IntelliJ IDEA (Community
edition should be enough).

## IDE Setup

After running `./gradlew` at least once, set up your project SDK to the folder
`build/gradle-jvm/<sdk-name>/<subdirectory>`. This JDK is guaranteed to contain
all the components necessary to build the plugin.

[gradle-jvm-wrapper]: https://github.com/mfilippov/gradle-jvm-wrapper
[ide.channels]: https://www.jetbrains.com/help/idea/managing-plugins.html#repos
