package me.fornever.avaloniarider

import com.intellij.execution.configurations.GeneralCommandLine
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path

object AvaloniaPreviewer {
    fun getPreviewerCommandLine(
            runtime: DotNetRuntime,
            previewerBinary: Path,
            targetDir: Path,
            targetName: String,
            targetPath: Path,
            bsonPort: Int
    ): GeneralCommandLine {
        val runtimeConfig = targetDir.resolve("$targetName.runtimeconfig.json")
        val depsFile = targetDir.resolve("$targetName.deps.json")
        return when (runtime) {
            is DotNetCoreRuntime -> GeneralCommandLine().withExePath(runtime.cliExePath)
                    .withParameters(
                            "exec",
                            "--runtimeconfig",
                            runtimeConfig.toAbsolutePath().toString(),
                            "--depsfile",
                            depsFile.toAbsolutePath().toString(),
                            previewerBinary.toAbsolutePath().toString(),
                            "--transport",
                            "tcp-bson://127.0.0.1:$bsonPort/",
                            targetPath.toAbsolutePath().toString()
                    )
            else -> GeneralCommandLine().withExePath(previewerBinary.toAbsolutePath().toString())
                    .withParameters(
                            "--transport",
                            "tcp-bson://127.0.0.1:$bsonPort/",
                            targetPath.toAbsolutePath().toString()
                    )
        }
    }

    fun getAvaloniaPreviewerPathKey(runtime: DotNetRuntime): String = when (runtime) {
        is DotNetCoreRuntime -> "AvaloniaPreviewerNetCoreToolPath"
        else -> "AvaloniaPreviewerNetFullToolPath"
    }
}
