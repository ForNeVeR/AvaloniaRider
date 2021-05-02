package me.fornever.avaloniarider.testcases

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rdclient.util.idea.pumpMessages
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.base.BaseTestWithSolution
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.scriptingApi.buildSolutionWithReSharperBuild
import com.jetbrains.rider.test.scriptingApi.getVirtualFileFromPath
import com.jetbrains.rider.xaml.XamlPreviewEditorExtension
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.idea.editor.AvaloniaPreviewerXamlEditorExtension
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import org.testng.annotations.Test
import java.time.Duration

@TestEnvironment(coreVersion = CoreVersion.DEFAULT)
class PreviewTests : BaseTestWithSolution() {
    override fun getSolutionDirectoryName() = "AvaloniaMvvm"
    override val restoreNuGetPackages = true
    override val backendLoadedTimeout: Duration = Duration.ofMinutes(2L)

    private val mainWindowFile
        get() = getVirtualFileFromPath("Views/MainWindow.xaml", activeSolutionDirectory)

    private val projectFilePathProperty
        get() = OptProperty(solutionSourceRootDirectory.resolve("AvaloniaMvvm.csproj").toPath())

    @Test
    fun previewEditorProviderShouldHandleTheXamlFile() {
        val provider = XamlPreviewEditorExtension.EP_NAME
            .getExtensionList(project)
            .filterIsInstance<AvaloniaPreviewerXamlEditorExtension>()
            .single()
        provider.accepts(mainWindowFile).shouldBeTrue()
    }

    @Test
    fun previewControllerShouldRenderTheFrame() {
        buildSolutionWithReSharperBuild(Duration.ofMinutes(1L))
        var frameMsg: FrameMessage? = null
        Lifetime.using { lt ->
            AvaloniaPreviewerSessionController(project, lt, mainWindowFile, projectFilePathProperty).apply {
                frame.advise(lt) { frameMsg = it }
            }
            pumpMessages { frameMsg != null }
        }
    }
}
