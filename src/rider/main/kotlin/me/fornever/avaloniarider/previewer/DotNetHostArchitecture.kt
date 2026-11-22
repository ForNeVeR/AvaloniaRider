package me.fornever.avaloniarider.previewer

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.system.CpuArch
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.RdFault
import com.jetbrains.rider.debugger.DebuggerHelperHost
import com.jetbrains.rider.ijent.extensions.toNioPathOrNull
import com.jetbrains.rider.model.debuggerHelper.AssemblyPEPlatform
import com.jetbrains.rider.model.dotNetActiveRuntimeModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists

internal sealed interface HostSelection {
    /**
     * The default (native) host should be used.
     */
    data object Native : HostSelection

    /**
     * A non-native host located at [cliExePath] should be used.
     */
    data class NonNative(val cliExePath: Path) : HostSelection

    /**
     * The assembly requires a host for [platform] that is not available.
     */
    data class Unavailable(val platform: AssemblyPEPlatform) : HostSelection
}

/**
 * Returns the architecture of the processes started by the default (native) .NET host on the current machine.
 */
private fun getNativeArchitecture(): CpuArch =
    if (CpuArch.isEmulated())
        CpuArch.ARM64
    else
        @Suppress("OPT_IN_USAGE")
        CpuArch.CURRENT

/**
 * Determines which .NET host should be used to start an assembly with the [platform] architecture.
 */
internal fun selectHostPath(
    platform: AssemblyPEPlatform,
    nativeArch: CpuArch,
    nonNativeX86CliPath: Path?,
    nonNativeX64CliPath: Path?
): HostSelection {
    fun nativeOrNonNative(requiredArch: CpuArch, nonNativePath: Path?): HostSelection = when {
        nativeArch == requiredArch -> HostSelection.Native
        nonNativePath != null -> HostSelection.NonNative(nonNativePath)
        else -> HostSelection.Unavailable(platform)
    }

    return when (platform) {
        // Prefer32Bit has no effect for assemblies started via dotnet exec.
        AssemblyPEPlatform.AnyCpu, AssemblyPEPlatform.AnyCpuPrefer32Bit, AssemblyPEPlatform.Unknown -> HostSelection.Native
        AssemblyPEPlatform.x86 -> nativeOrNonNative(CpuArch.X86, nonNativeX86CliPath)
        AssemblyPEPlatform.x64 -> nativeOrNonNative(CpuArch.X86_64, nonNativeX64CliPath)
        AssemblyPEPlatform.Arm -> nativeOrNonNative(CpuArch.ARM32, null)
        AssemblyPEPlatform.Arm64 -> nativeOrNonNative(CpuArch.ARM64, null)
    }
}

/**
 * Returns a runtime whose .NET host is able to start the [assembly], taking its architecture into account.
 */
suspend fun resolveRuntimeForAssembly(project: Project, runtime: DotNetRuntime, assembly: Path): DotNetRuntime {
    if (runtime !is DotNetCoreRuntime) return runtime
    if (!withContext(Dispatchers.IO) { assembly.exists() }) {
        logger.info("Assembly \"$assembly\" doesn't exist, using the default .NET host.")
        return runtime
    }

    val lifetime = LifetimeDefinition()
    try {
        val (platform, nonNativePaths) = try {
            withContext(Dispatchers.EDT) {
                val platform = DebuggerHelperHost.getInstance(project)
                    .getAssemblyPEPlatform(lifetime, assembly.toString(), detectNative = false)
                val activeRuntime = project.solution.dotNetActiveRuntimeModel.activeRuntime.valueOrNull
                platform to activeRuntime?.nonNativeDotNetCliExePaths
            }
        } catch (e: RdFault) {
            logger.warn("Cannot determine the platform of assembly \"$assembly\", using the default .NET host.", e)
            return runtime
        }
        val x86 = nonNativePaths?.x86?.toNioPathOrNull()
        val x64 = nonNativePaths?.x64?.toNioPathOrNull()
        val nativeArch = getNativeArchitecture()
        logger.info(
            "Assembly \"$assembly\" platform: $platform, native architecture: $nativeArch, " +
                "non-native hosts: x86 = $x86, x64 = $x64."
        )

        return when (val selection = selectHostPath(platform, nativeArch, x86, x64)) {
            HostSelection.Native -> runtime
            is HostSelection.NonNative -> {
                logger.info("Using non-native .NET host \"${selection.cliExePath}\" for assembly \"$assembly\".")
                DotNetCoreRuntime(selection.cliExePath, null, null, runtime.type)
            }
            is HostSelection.Unavailable -> {
                logger.warn("The startup assembly \"${assembly.fileName}\" targets the ${selection.platform} architecture, but no .NET host for this architecture is available. Fall back to default host runtime.")
                runtime
            }
        }
    } finally {
        lifetime.terminate()
    }
}

private val logger = logger<HostSelection>()
