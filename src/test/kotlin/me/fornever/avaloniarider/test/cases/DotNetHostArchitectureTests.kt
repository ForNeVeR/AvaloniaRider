package me.fornever.avaloniarider.test.cases

import com.intellij.util.system.CpuArch
import com.jetbrains.rider.model.debuggerHelper.AssemblyPEPlatform
import com.jetbrains.rider.test.asserts.shouldBe
import me.fornever.avaloniarider.previewer.HostSelection
import me.fornever.avaloniarider.previewer.selectHostPath
import org.junit.jupiter.api.Test
import java.nio.file.Path

class DotNetHostArchitectureTests {

    private val x86Host = Path.of("x86", "dotnet")
    private val x64Host = Path.of("x64", "dotnet")

    @Test
    fun anyCpuUsesNativeHost() {
        for (platform in listOf(
            AssemblyPEPlatform.AnyCpu,
            AssemblyPEPlatform.AnyCpuPrefer32Bit,
            AssemblyPEPlatform.Unknown
        )) {
            selectHostPath(platform, CpuArch.X86_64, x86Host, x64Host).shouldBe(HostSelection.Native)
            selectHostPath(platform, CpuArch.ARM64, x86Host, x64Host).shouldBe(HostSelection.Native)
        }
    }

    @Test
    fun matchingArchitectureUsesNativeHost() {
        selectHostPath(AssemblyPEPlatform.x64, CpuArch.X86_64, x86Host, null).shouldBe(HostSelection.Native)
        selectHostPath(AssemblyPEPlatform.Arm64, CpuArch.ARM64, null, x64Host).shouldBe(HostSelection.Native)
    }

    @Test
    fun x86OnX64UsesNonNativeHost() {
        selectHostPath(AssemblyPEPlatform.x86, CpuArch.X86_64, x86Host, null)
            .shouldBe(HostSelection.NonNative(x86Host))
    }

    @Test
    fun x86OnX64WithoutHostIsUnavailable() {
        selectHostPath(AssemblyPEPlatform.x86, CpuArch.X86_64, null, null)
            .shouldBe(HostSelection.Unavailable(AssemblyPEPlatform.x86))
    }

    @Test
    fun x64OnArm64UsesNonNativeHost() {
        selectHostPath(AssemblyPEPlatform.x64, CpuArch.ARM64, null, x64Host)
            .shouldBe(HostSelection.NonNative(x64Host))
    }

    @Test
    fun x64OnArm64WithoutHostIsUnavailable() {
        selectHostPath(AssemblyPEPlatform.x64, CpuArch.ARM64, x86Host, null)
            .shouldBe(HostSelection.Unavailable(AssemblyPEPlatform.x64))
    }

    @Test
    fun arm64OnX64IsUnavailable() {
        selectHostPath(AssemblyPEPlatform.Arm64, CpuArch.X86_64, x86Host, x64Host)
            .shouldBe(HostSelection.Unavailable(AssemblyPEPlatform.Arm64))
    }
}
