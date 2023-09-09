package me.fornever.avaloniarider.test.cases

import com.jetbrains.rd.platform.diagnostics.RdLogTraceScenarios
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rdclient.util.idea.pumpMessages
import com.jetbrains.rider.model.PreviewPlatformKind
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.base.BaseTestWithSolution
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.scriptingApi.buildSolutionWithReSharperBuild
import com.jetbrains.rider.test.scriptingApi.getVirtualFileFromPath
import com.jetbrains.rider.xaml.core.XamlPreviewEditorExtension
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.idea.editor.AvaloniaPreviewerXamlEditorExtension
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.test.framework.correctTestSolutionDirectory
import org.testng.annotations.Test
import java.time.Duration

@TestEnvironment(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
class PreviewTests : BaseTestWithSolution() {
    override fun getSolutionDirectoryName() = "AvaloniaMvvm"
    override val restoreNuGetPackages = true
    override val backendLoadedTimeout: Duration = Duration.ofMinutes(2L)
    override val traceScenarios = setOf(RdLogTraceScenarios.Commands)

    private val mainWindowFile
        get() = getVirtualFileFromPath("Views/MainWindow.xaml", correctTestSolutionDirectory.toFile())

    private val projectFilePathProperty
        get() = OptProperty(correctTestSolutionDirectory.resolve("AvaloniaMvvm.csproj"))

    @Test
    fun previewEditorProviderShouldHandleTheXamlFile() {
        val provider = XamlPreviewEditorExtension.EP_NAME
            .extensionList
            .filterIsInstance<AvaloniaPreviewerXamlEditorExtension>()
            .single()
        provider.accepts(mainWindowFile, PreviewPlatformKind.AVALONIA).shouldBeTrue()
    }

    @Test
    fun previewControllerShouldRenderTheFrame() {
        buildSolutionWithReSharperBuild(timeout = Duration.ofMinutes(1L))
        var frameMsg: FrameMessage? = null
        Lifetime.using { lt ->
            AvaloniaPreviewerSessionController(project, lt, consoleView = null, mainWindowFile, projectFilePathProperty).apply {
                frame.advise(lt) { frameMsg = it }
            }
            pumpMessages(Duration.ofMinutes(1L)) { frameMsg != null }.shouldBeTrue()
        }
    }
}
