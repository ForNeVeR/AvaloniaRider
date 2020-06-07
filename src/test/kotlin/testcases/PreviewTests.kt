package me.fornever.avaloniarider.testcases

import com.intellij.openapi.fileEditor.FileEditorProvider
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rdclient.util.idea.pumpMessages
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.asserts.shouldNotBeNull
import com.jetbrains.rider.test.base.BaseTestWithSolution
import com.jetbrains.rider.test.enums.CoreVersion
import com.jetbrains.rider.test.scriptingApi.buildSolutionWithReSharperBuild
import com.jetbrains.rider.test.scriptingApi.getVirtualFileFromPath
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.idea.editor.AvaloniaPreviewEditorProvider
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import org.testng.annotations.Test
import java.time.Duration

@TestEnvironment(coreVersion = CoreVersion.DEFAULT)
class PreviewTests : BaseTestWithSolution() {
    override fun getSolutionDirectoryName() = "AvaloniaMvvm"
    override val restoreNuGetPackages = true

    private val mainWindowFile
        get() = getVirtualFileFromPath("Views/MainWindow.xaml", activeSolutionDirectory)

    @Test
    fun previewEditorProviderShouldHandleTheXamlFile() {
        val provider = FileEditorProvider.EP_FILE_EDITOR_PROVIDER
            .findExtension(AvaloniaPreviewEditorProvider::class.java).shouldNotBeNull()
        provider.accept(project, mainWindowFile).shouldBeTrue()
    }

    @Test
    fun previewControllerShouldRenderTheFrame() {
        buildSolutionWithReSharperBuild(Duration.ofMinutes(1L))
        var frameMsg: FrameMessage? = null
        Lifetime.using { lt ->
            val controller = AvaloniaPreviewerSessionController(project, lt, mainWindowFile).apply {
                frame.advise(lt) { frameMsg = it }
            }
            controller.start()
            pumpMessages { frameMsg != null }
        }
    }
}
