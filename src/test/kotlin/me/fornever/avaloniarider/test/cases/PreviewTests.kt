package me.fornever.avaloniarider.test.cases

import com.intellij.openapi.util.registry.Registry
import com.jetbrains.rd.platform.diagnostics.RdLogTraceScenarios
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rdclient.util.idea.pumpMessages
import com.jetbrains.rider.model.BuildResultKind
import com.jetbrains.rider.model.PreviewPlatformKind
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.base.PerTestSolutionTestBase
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.scriptingApi.buildSolutionWithConsoleBuild
import com.jetbrains.rider.test.scriptingApi.getVirtualFileFromPath
import com.jetbrains.rider.xaml.core.XamlPreviewEditorExtension
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.idea.editor.AvaloniaPreviewerXamlEditorExtension
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.test.framework.correctTestSolutionDirectory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.div
import kotlin.test.assertTrue

@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
@Solution("AvaloniaMvvm")
class PreviewTests : PerTestSolutionTestBase() {

    // TODO[#524]: The manipulation of disable.winp here is a workaround for the following situation:
    //             during the project termination, OSProcessUtil.killProcess might be triggered. If disable.winp is
    //             false (default), Rider will try using winp, and winp is not available during project termination.
    //             So, it will work, but will log an error. Let's disable this by suppressing the whole winp
    //             mechanism.
    @BeforeMethod fun registrySetUp() { Registry.get("disable.winp").setValue(true) }
    @AfterMethod fun registryTearDown() { Registry.get("disable.winp").resetToDefault() }

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
        val buildResult = buildSolutionWithConsoleBuild(timeout = Duration.ofMinutes(1L))
        assertTrue(
            buildResult.buildResultKind == BuildResultKind.Successful
                || buildResult.buildResultKind == BuildResultKind.HasWarnings,
            "Build should be successful."
        )
        var frameMsg: FrameMessage? = null
        Lifetime.using { lt ->
            // not init the property, so that the session doesn't start before we handle the frame
            val projectFilePathProperty = OptProperty<Path>()
            AvaloniaPreviewerSessionController(
                project,
                lt,
                consoleView = null,
                mainWindowFile,
                projectFilePathProperty
            ).apply {
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
