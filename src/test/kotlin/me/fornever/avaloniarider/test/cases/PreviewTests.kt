package me.fornever.avaloniarider.test.cases

import com.jetbrains.rd.platform.diagnostics.RdLogTraceScenarios
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rdclient.util.idea.pumpMessages
import com.jetbrains.rider.model.PreviewPlatformKind
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.base.PerTestSolutionTestBase
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.scriptingApi.buildSolutionWithReSharperBuild
import com.jetbrains.rider.test.scriptingApi.getVirtualFileFromPath
import com.jetbrains.rider.xaml.core.XamlPreviewEditorExtension
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.idea.editor.AvaloniaPreviewerXamlEditorExtension
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.test.framework.correctTestSolutionDirectory
import org.testng.annotations.Test
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.div

@TestEnvironment(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
@Solution("AvaloniaMvvm")
class PreviewTests : PerTestSolutionTestBase() {

    override fun modifyOpenSolutionParams(params: OpenSolutionParams) {
        params.restoreNuGetPackages = true
        params.backendLoadedTimeout = Duration.ofMinutes(2L)
        super.modifyOpenSolutionParams(params)
    }

    override val traceScenarios = setOf(RdLogTraceScenarios.Commands)

    private val mainWindowFile
        get() = getVirtualFileFromPath("Views/MainWindow.axaml", correctTestSolutionDirectory.toFile())

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
            // not init the property, so that the session doesn't start before we handle the frame
            val projectFilePathProperty = OptProperty<Path>()
            AvaloniaPreviewerSessionController(project, lt, consoleView = null, mainWindowFile, projectFilePathProperty).apply {
                frame.advise(lt) {
                    frameMsg = it
                }
            }
            frameworkLogger.info("Listener initialized!")
            projectFilePathProperty.set(correctTestSolutionDirectory / "AvaloniaMvvm.csproj") // now the session starts

            pumpMessages(Duration.ofMinutes(1L)) {
                frameMsg != null
            }.shouldBeTrue()

            frameworkLogger.info("We are done!")
        }
    }
}
