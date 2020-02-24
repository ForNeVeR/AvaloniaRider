# AvaloniaRider

[![Rider](https://img.shields.io/jetbrains/plugin/v/RIDER_PLUGIN_ID.svg?label=rider%20&colorB=0A7BBB&style=flat-square&logo=%20data%3Aimage%2Fpng%3Bbase64%2CiVBORw0KGgoAAAANSUhEUgAAADAAAAAsCAYAAAAjFjtnAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAAZdEVYdFNvZnR3YXJlAHBhaW50Lm5ldCA0LjAuMjHxIGmVAAAEEklEQVRoQ%2B2ZS0hUURjHLSoKominVIta1aLaFYhBES1t1bLFRCAREbQKaiHpqgeEpjDjRhIFHWwzIyo6oWXqwmfkW0evr9TxNT7GcZxX%2FzPzzZk53jsyc%2B91I%2FcHB4a5%2F%2B%2B79z%2Fn3O98RzOOBKFQ6BeGlOrw%2B%2F2S0%2Bn83dXV9a6srOwSpUlKfX19yvnn5%2BelwcFBW09Pz8uamporlOJgEOgMq2R7ezuAm33Cx%2BOUTgYMqMq%2Ft7cX7O3t7QbXKJUyWgzEWFhYaMONTlJKAbUGYvh8vjBymy0Wi2J%2BXQwwxsbGvlFKAa0GGHjG8Nra2nd8lM%2B0kgGPx9OK76sSx9LSkgNrfyMQCOySTAAxodLS0uuUlqNkAPnG9%2BdfWVlxLC8vs%2FxBkskYHx%2B3Udo4CJbdoKqqKpcuy8DLmzU3N%2FeTpAJTU1MfSMZRMoBCYKHLMhwOx12YacBzkVpkeHjYRNIo6RpgsPXodrtdJOdgvdpJwknXQIyRkZE3mA2KiIP7LqJCnSGZOgMM3MBOcg5y6WaAsb6%2B%2Fp5CBDo7O5%2BSRL0BTOWhG2hpaTnt9Xr%2FURhndna2lSTqDWxtbUkk50xOTupqgDE9PV1AYZzV1VVXXl5etKyma6C8vDwTJa2GpALY1J6QjKPVAGY6l8I4mJVwR0dH9D1QMtDW1ubCjaXE0dDQIA0MDEj45f0kE0DlmMeUn4gkTUCrgYqKCpkBxszMTHID6YJq4cVU50QS7kOrAZvNJjOAZ9bPAHoWT3Nz84tIMgW0GsC%2B84rCOFjCYV5K1RpgPQo2tKbi4uLbkURJ0GoALUo3hXHQRP7BpWMRgZIBrGcPpmgjNjY3N2XtA16knb6%2BvouRJAegxUB1dXVuMCjvLCYmJspIomwA3wlVqL29PXt3d1e2t6MeD%2BTn58te3ETUGkDByMTyHKMQDmL9drv9FslSM8DAzvuRLgtgjX4miSJqDOCdugfNKMkFJElqIlmUVA0w0PdPkISDmQk3NjZmk0SGkgF0tq2VlZWm2ECpNFmtVtPi4uJzLN8fuD8pRdBa%2BIaGhrIodZR0DKCk5WBafSTj4IEmMeWnSCagZEANmBEPVsEDShsnHQOM%2Fv7%2BLyQTQPKvJBHQwwCqjhet%2Bn1KKZKuAfQg51GH%2F5KUw5bS6OjoHZJxtBjAr876q14ssxuUTk66Bhg4dNzECYzUcbAv7KCPukCyCGoMIPcaGjYrK6NIEa33ycBb%2FRhtgClx4EEu0%2BWkoPo83B%2FHBo59QqzZbH4LE7UHjbq6ulpsjM9QJEwFBQWPlHoqAwMDAwMDAwODo0hJScnZoqKic3oOHPST%2Fs9Md1wulxMjrNfAuZYdbK5S%2BsNH6UCjBfZ3HJgwDKQMDLzGKNRrwECh2%2B0WjpUGimRk%2FAdgThdOY4UJ9QAAAABJRU5ErkJggg%3D%3D)](https://plugins.jetbrains.com/plugin/RIDER_PLUGIN_ID)

Build
-----

### Prerequisites

- OpenJDK-compatible JDK version 8 or later (will be downloaded automatically)
- .NET Core SDK 3.1 or later

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

Development
-----------

This plugin consists of two parts: the backend one (written in C#) and the
frontend one (written in Kotlin). Each part requires a corresponding IDE. To
develop a backend, it's recommended to open `AvaloniaRider.sln` with JetBrains
Rider. To develop a frontend, it's recommended to use IntelliJ IDEA (Community
edition should be enough).

[gradle-jvm-wrapper]: https://github.com/mfilippov/gradle-jvm-wrapper
